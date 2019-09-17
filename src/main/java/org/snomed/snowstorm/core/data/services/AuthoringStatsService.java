package org.snomed.snowstorm.core.data.services;

import ch.qos.logback.classic.Level;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.AuthoringStatsSummary;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.VersionControlHelper.LARGE_PAGE;
import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class AuthoringStatsService {

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private ConceptService conceptService;

	public AuthoringStatsSummary getStats(String branch) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);

		TimerUtil timer = new TimerUtil("Authoring stats", Level.INFO, 5);

		AuthoringStatsSummary authoringStatsSummary = new AuthoringStatsSummary(new Date());
		authoringStatsSummary.setTitle("Authoring changes since last release");

		// New concepts
		PageRequest pageOfOne = PageRequest.of(0, 1);
		Page<Concept> newConceptsPage = elasticsearchOperations.queryForPage(getNewConceptCriteria(branchCriteria)
				.withPageable(pageOfOne)
				.build(), Concept.class);
		timer.checkpoint("new concepts");
		authoringStatsSummary.setNewConceptsCount(newConceptsPage.getTotalElements());

		// Inactivated concepts
		Page<Concept> inactivatedConceptsPage = elasticsearchOperations.queryForPage(getInactivatedConceptsCriteria(branchCriteria)
				.withPageable(pageOfOne)
				.build(), Concept.class);
		timer.checkpoint("inactivated concepts");
		authoringStatsSummary.setInactivatedConceptsCount(inactivatedConceptsPage.getTotalElements());

		// Reactivated concepts
		Page<Concept> reactivatedConceptsPage = elasticsearchOperations.queryForPage(getReactivatedConceptsCriteria(branchCriteria)
				.withPageable(pageOfOne)
				.build(), Concept.class);
		timer.checkpoint("reactivated concepts");
		authoringStatsSummary.setReactivatedConceptsCount(reactivatedConceptsPage.getTotalElements());

		// Changed FSNs
		Page<Description> changedFSNsPage = elasticsearchOperations.queryForPage(getChangedFSNsCriteria(branchCriteria)
				.withPageable(pageOfOne)
				.build(), Description.class);
		timer.checkpoint("changed FSNs");
		authoringStatsSummary.setChangedFsnCount(changedFSNsPage.getTotalElements());

		// Inactivated synonyms
		Page<Description> inactivatedSynonyms = elasticsearchOperations.queryForPage(getInactivatedSynonymCriteria(branchCriteria)
				.withFields(Description.Fields.CONCEPT_ID)
				.withPageable(pageOfOne)
				.build(), Description.class);
		timer.checkpoint("inactivated descriptions");
		authoringStatsSummary.setInactivatedSynonymsCount(inactivatedSynonyms.getTotalElements());

		// New synonyms for existing concepts
		Page<Description> newSynonymsForExistingConcepts = elasticsearchOperations.queryForPage(getNewSynonymsOnExistingConceptsCriteria(branchCriteria, timer)
				.withFields(Description.Fields.DESCRIPTION_ID, Description.Fields.CONCEPT_ID)
				.withPageable(pageOfOne)
				.build(), Description.class);
		timer.checkpoint("new synonyms for existing concepts");
		authoringStatsSummary.setNewSynonymsForExistingConceptsCount(newSynonymsForExistingConcepts.getTotalElements());

		// Reactivated synonyms
		Page<Description> reactivatedSynonyms = elasticsearchOperations.queryForPage(getReactivatedSynonymsCriteria(branchCriteria)
				.withFields(Description.Fields.CONCEPT_ID)
				.withPageable(pageOfOne)
				.build(), Description.class);
		timer.checkpoint("reactivated descriptions");
		authoringStatsSummary.setReactivatedSynonymsCount(reactivatedSynonyms.getTotalElements());

		return authoringStatsSummary;
	}

	private NativeSearchQueryBuilder getNewSynonymsOnExistingConceptsCriteria(BranchCriteria branchCriteria, TimerUtil timer) {
		Set<Long> newSynonymConceptIds = new LongOpenHashSet();
		try (CloseableIterator<Description> stream = elasticsearchOperations.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Description.class))
						.must(termQuery(Description.Fields.TYPE_ID, Concepts.SYNONYM))
						.must(termQuery(Concept.Fields.ACTIVE, true))
						.must(termQuery(Concept.Fields.RELEASED, "false")))
				.withFields(Description.Fields.CONCEPT_ID)
				.withPageable(LARGE_PAGE)
				.build(), Description.class)) {
			stream.forEachRemaining(description -> newSynonymConceptIds.add(parseLong(description.getConceptId())));
		}
		if (timer != null) timer.checkpoint("new synonym concept ids");

		Set<Long> existingConceptsWithNewSynonyms = new LongOpenHashSet();
		try (CloseableIterator<Concept> stream = elasticsearchOperations.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termQuery(Concept.Fields.RELEASED, "true"))
						.filter(termsQuery(Concept.Fields.CONCEPT_ID, newSynonymConceptIds))
				)
				.withFields(Concept.Fields.CONCEPT_ID)
				.withPageable(LARGE_PAGE)
				.build(), Concept.class)) {
			stream.forEachRemaining(concept -> existingConceptsWithNewSynonyms.add(concept.getConceptIdAsLong()));
		}
		if (timer != null) timer.checkpoint("existing concepts with new synonyms");

		return new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Description.class))
						.must(termQuery(Description.Fields.TYPE_ID, Concepts.SYNONYM))
						.must(termQuery(Description.Fields.ACTIVE, true))
						.mustNot(existsQuery(Concept.Fields.EFFECTIVE_TIME))
						.must(termQuery(Description.Fields.RELEASED, "false"))
						.filter(termsQuery(Description.Fields.CONCEPT_ID, existingConceptsWithNewSynonyms))
				);
	}

	public List<ConceptMicro> getNewConcepts(String branch, List<String> languageCodes) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);

		List<Long> conceptIds = new LongArrayList();
		try (CloseableIterator<Concept> stream = elasticsearchOperations.stream(getNewConceptCriteria(branchCriteria).withPageable(LARGE_PAGE).build(), Concept.class)) {
			stream.forEachRemaining(concept -> conceptIds.add(concept.getConceptIdAsLong()));
		}
		return getConceptMicros(conceptIds, languageCodes, branchCriteria);
	}

	public List<ConceptMicro> getInactivatedConcepts(String branch, List<String> languageCodes) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);

		List<Long> conceptIds = new LongArrayList();
		try (CloseableIterator<Concept> stream = elasticsearchOperations.stream(getInactivatedConceptsCriteria(branchCriteria).withPageable(LARGE_PAGE).build(), Concept.class)) {
			stream.forEachRemaining(concept -> conceptIds.add(concept.getConceptIdAsLong()));
		}
		return getConceptMicros(conceptIds, languageCodes, branchCriteria);
	}

	public List<ConceptMicro> getReactivatedConcepts(String branch, List<String> languageCodes) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);

		List<Long> conceptIds = new LongArrayList();
		try (CloseableIterator<Concept> stream = elasticsearchOperations.stream(getReactivatedConceptsCriteria(branchCriteria).withPageable(LARGE_PAGE).build(), Concept.class)) {
			stream.forEachRemaining(concept -> conceptIds.add(concept.getConceptIdAsLong()));
		}
		return getConceptMicros(conceptIds, languageCodes, branchCriteria);
	}

	public List<ConceptMicro> getChangedFSNs(String branch, List<String> languageCodes) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);

		List<Long> conceptIds = new LongArrayList();
		try (CloseableIterator<Description> stream = elasticsearchOperations.stream(getChangedFSNsCriteria(branchCriteria).withPageable(LARGE_PAGE).build(), Description.class)) {
			stream.forEachRemaining(description -> conceptIds.add(parseLong(description.getConceptId())));
		}
		return getConceptMicros(conceptIds, languageCodes, branchCriteria);
	}

	public List<ConceptMicro> getInactivatedSynonyms(String branch) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		return getDescriptionResults(getInactivatedSynonymCriteria(branchCriteria));
	}

	public List<ConceptMicro> getNewSynonymsOnExistingConcepts(String branch) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		return getDescriptionResults(getNewSynonymsOnExistingConceptsCriteria(branchCriteria, null));
	}

	public List<ConceptMicro> getReactivatedSynonyms(String branch) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		return getDescriptionResults(getReactivatedSynonymsCriteria(branchCriteria));
	}

	private List<ConceptMicro> getDescriptionResults(NativeSearchQueryBuilder criteria) {
		List<ConceptMicro> micros = new ArrayList<>();
		try (CloseableIterator<Description> stream = elasticsearchOperations.stream(criteria.withPageable(LARGE_PAGE).build(), Description.class)) {
			stream.forEachRemaining(description -> micros.add(new ConceptMicro(description.getConceptId(), description.getTerm())));
		}
		micros.sort(Comparator.comparing(ConceptMicro::getTerm));
		return micros;
	}

	private NativeSearchQueryBuilder getNewConceptCriteria(BranchCriteria branchCriteria) {
		return new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termQuery(Concept.Fields.ACTIVE, "true"))
						.mustNot(existsQuery(Concept.Fields.EFFECTIVE_TIME))
						.must(termQuery(Concept.Fields.RELEASED, "false")))
				.withFields(Concept.Fields.CONCEPT_ID);
	}

	private NativeSearchQueryBuilder getInactivatedConceptsCriteria(BranchCriteria branchCriteria) {
		return new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termQuery(Concept.Fields.ACTIVE, "false"))
						.must(termQuery(Concept.Fields.RELEASED, "true"))
						.mustNot(existsQuery(Concept.Fields.EFFECTIVE_TIME))
				)
				.withFields(Concept.Fields.CONCEPT_ID);
	}

	private NativeSearchQueryBuilder getReactivatedConceptsCriteria(BranchCriteria branchCriteria) {
		return new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termQuery(Concept.Fields.ACTIVE, "true"))
						.mustNot(existsQuery(Concept.Fields.EFFECTIVE_TIME))
						.must(termQuery(Concept.Fields.RELEASED, "true"))
						// Previously released as active=false
						.must(prefixQuery(Concept.Fields.RELEASE_HASH, "false"))
				)
				.withFields(Concept.Fields.CONCEPT_ID);
	}

	private NativeSearchQueryBuilder getChangedFSNsCriteria(BranchCriteria branchCriteria) {
		return new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Description.class))
						// To get the concepts with changed FSNs
						// just select published FSNs which have been changed.
						// This will cover: minor changes to term, change to case significance and replaced FSNs.
						.must(termQuery(Description.Fields.TYPE_ID, Concepts.FSN))
						.mustNot(existsQuery(Concept.Fields.EFFECTIVE_TIME))
						.must(termQuery(Concept.Fields.RELEASED, "true")))
				.withFields(Description.Fields.CONCEPT_ID);
	}

	private NativeSearchQueryBuilder getInactivatedSynonymCriteria(BranchCriteria branchCriteria) {
		return new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Description.class))
						.must(termQuery(Description.Fields.TYPE_ID, Concepts.SYNONYM))
						.must(termQuery(Concept.Fields.ACTIVE, false))
						.mustNot(existsQuery(Concept.Fields.EFFECTIVE_TIME))
						.must(termQuery(Concept.Fields.RELEASED, "true")));
	}

	private NativeSearchQueryBuilder getReactivatedSynonymsCriteria(BranchCriteria branchCriteria) {
		return new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Description.class))
						.must(termQuery(Description.Fields.TYPE_ID, Concepts.SYNONYM))
						.must(termQuery(Description.Fields.ACTIVE, true))
						.mustNot(existsQuery(Concept.Fields.EFFECTIVE_TIME))
						// Previously released as active=false
						.must(prefixQuery(Description.Fields.RELEASE_HASH, "false"))
						.must(termQuery(Description.Fields.RELEASED, "true")));
	}

	private List<ConceptMicro> getConceptMicros(List<Long> conceptIds, List<String> languageCodes, BranchCriteria branchCriteria) {
		return conceptService.findConceptMinis(branchCriteria, conceptIds, languageCodes).getResultsMap().values().stream()
				.map(ConceptMicro::new).sorted(Comparator.comparing(ConceptMicro::getTerm)).collect(Collectors.toList());
	}
}

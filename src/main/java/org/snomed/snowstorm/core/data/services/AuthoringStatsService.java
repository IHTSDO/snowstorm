package org.snomed.snowstorm.core.data.services;

import ch.qos.logback.classic.Level;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.AuthoringStatsSummary;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;
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
		SearchHits<Concept> newConceptsPage = elasticsearchOperations.search(withTotalHitsTracking(getNewConceptCriteria(branchCriteria)
				.withPageable(pageOfOne)
				.build()), Concept.class);
		timer.checkpoint("new concepts");
		authoringStatsSummary.setNewConceptsCount(newConceptsPage.getTotalHits());

		// Inactivated concepts
		SearchHits<Concept> inactivatedConceptsPage = elasticsearchOperations.search(withTotalHitsTracking(getInactivatedConceptsCriteria(branchCriteria)
				.withPageable(pageOfOne)
				.build()), Concept.class);
		timer.checkpoint("inactivated concepts");
		authoringStatsSummary.setInactivatedConceptsCount(inactivatedConceptsPage.getTotalHits());

		// Reactivated concepts
		SearchHits<Concept> reactivatedConceptsPage = elasticsearchOperations.search(withTotalHitsTracking(getReactivatedConceptsCriteria(branchCriteria)
				.withPageable(pageOfOne)
				.build()), Concept.class);
		timer.checkpoint("reactivated concepts");
		authoringStatsSummary.setReactivatedConceptsCount(reactivatedConceptsPage.getTotalHits());

		// Changed FSNs
		SearchHits<Description> changedFSNsPage = elasticsearchOperations.search(withTotalHitsTracking(getChangedFSNsCriteria(branchCriteria)
				.withPageable(pageOfOne)
				.build()), Description.class);
		timer.checkpoint("changed FSNs");
		authoringStatsSummary.setChangedFsnCount(changedFSNsPage.getTotalHits());

		// Inactivated synonyms
		SearchHits<Description> inactivatedSynonyms = elasticsearchOperations.search(withTotalHitsTracking(getInactivatedSynonymCriteria(branchCriteria)
				.withFields(Description.Fields.CONCEPT_ID)
				.withPageable(pageOfOne)
				.build()), Description.class);
		timer.checkpoint("inactivated descriptions");
		authoringStatsSummary.setInactivatedSynonymsCount(inactivatedSynonyms.getTotalHits());

		// New synonyms for existing concepts
		SearchHits<Description> newSynonymsForExistingConcepts = elasticsearchOperations.search(withTotalHitsTracking(getNewSynonymsOnExistingConceptsCriteria(branchCriteria, timer)
				.withFields(Description.Fields.DESCRIPTION_ID, Description.Fields.CONCEPT_ID)
				.withPageable(pageOfOne)
				.build()), Description.class);
		timer.checkpoint("new synonyms for existing concepts");
		authoringStatsSummary.setNewSynonymsForExistingConceptsCount(newSynonymsForExistingConcepts.getTotalHits());

		// Reactivated synonyms
		SearchHits<Description> reactivatedSynonyms = elasticsearchOperations.search(withTotalHitsTracking(getReactivatedSynonymsCriteria(branchCriteria)
				.withFields(Description.Fields.CONCEPT_ID)
				.withPageable(pageOfOne)
				.build()), Description.class);
		timer.checkpoint("reactivated descriptions");
		authoringStatsSummary.setReactivatedSynonymsCount(reactivatedSynonyms.getTotalHits());

		return authoringStatsSummary;
	}

	private NativeSearchQueryBuilder getNewSynonymsOnExistingConceptsCriteria(BranchCriteria branchCriteria, TimerUtil timer) {
		Set<Long> newSynonymConceptIds = new LongOpenHashSet();
		try (SearchHitsIterator<Description> stream = elasticsearchOperations.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Description.class))
						.must(termQuery(Description.Fields.TYPE_ID, Concepts.SYNONYM))
						.must(termQuery(Concept.Fields.ACTIVE, true))
						.must(termQuery(Concept.Fields.RELEASED, "false")))
				.withFields(Description.Fields.CONCEPT_ID)
				.withPageable(LARGE_PAGE)
				.build(), Description.class)) {
			stream.forEachRemaining(hit -> newSynonymConceptIds.add(parseLong(hit.getContent().getConceptId())));
		}
		if (timer != null) timer.checkpoint("new synonym concept ids");

		Set<Long> existingConceptsWithNewSynonyms = new LongOpenHashSet();
		try (SearchHitsIterator<Concept> stream = elasticsearchOperations.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termQuery(Concept.Fields.RELEASED, "true"))
						.filter(termsQuery(Concept.Fields.CONCEPT_ID, newSynonymConceptIds))
				)
				.withFields(Concept.Fields.CONCEPT_ID)
				.withPageable(LARGE_PAGE)
				.build(), Concept.class)) {
			stream.forEachRemaining(hit -> existingConceptsWithNewSynonyms.add(hit.getContent().getConceptIdAsLong()));
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

	public List<ConceptMicro> getNewConcepts(String branch, List<LanguageDialect> languageDialects) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);

		List<Long> conceptIds = new LongArrayList();
		try (SearchHitsIterator<Concept> stream = elasticsearchOperations.searchForStream(getNewConceptCriteria(branchCriteria).withPageable(LARGE_PAGE).build(), Concept.class)) {
			stream.forEachRemaining(hit -> conceptIds.add(hit.getContent().getConceptIdAsLong()));
		}
		return getConceptMicros(conceptIds, languageDialects, branchCriteria);
	}

	public List<ConceptMicro> getInactivatedConcepts(String branch, List<LanguageDialect> languageDialects) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);

		List<Long> conceptIds = new LongArrayList();
		try (SearchHitsIterator<Concept> stream = elasticsearchOperations.searchForStream(getInactivatedConceptsCriteria(branchCriteria).withPageable(LARGE_PAGE).build(), Concept.class)) {
			stream.forEachRemaining(hit -> conceptIds.add(hit.getContent().getConceptIdAsLong()));
		}
		return getConceptMicros(conceptIds, languageDialects, branchCriteria);
	}

	public List<ConceptMicro> getReactivatedConcepts(String branch, List<LanguageDialect> languageDialects) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);

		List<Long> conceptIds = new LongArrayList();
		try (SearchHitsIterator<Concept> stream = elasticsearchOperations.searchForStream(getReactivatedConceptsCriteria(branchCriteria).withPageable(LARGE_PAGE).build(), Concept.class)) {
			stream.forEachRemaining(hit -> conceptIds.add(hit.getContent().getConceptIdAsLong()));
		}
		return getConceptMicros(conceptIds, languageDialects, branchCriteria);
	}

	public List<ConceptMicro> getChangedFSNs(String branch, List<LanguageDialect> languageDialects) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);

		List<Long> conceptIds = new LongArrayList();
		try (SearchHitsIterator<Description> stream = elasticsearchOperations.searchForStream(getChangedFSNsCriteria(branchCriteria).withPageable(LARGE_PAGE).build(), Description.class)) {
			stream.forEachRemaining(hit -> conceptIds.add(parseLong(hit.getContent().getConceptId())));
		}
		return getConceptMicros(conceptIds, languageDialects, branchCriteria);
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
		try (SearchHitsIterator<Description> stream = elasticsearchOperations.searchForStream(criteria.withPageable(LARGE_PAGE).build(), Description.class)) {
			stream.forEachRemaining(hit -> micros.add(new ConceptMicro(hit.getContent().getConceptId(), hit.getContent().getTerm())));
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

	private List<ConceptMicro> getConceptMicros(List<Long> conceptIds, List<LanguageDialect> languageDialects, BranchCriteria branchCriteria) {
		return conceptService.findConceptMinis(branchCriteria, conceptIds, languageDialects).getResultsMap().values().stream()
				.map(ConceptMicro::new).sorted(Comparator.comparing(ConceptMicro::getTerm)).collect(Collectors.toList());
	}

	private Query withTotalHitsTracking(Query query) {
		query.setTrackTotalHits(true);
		return query;
	}
}

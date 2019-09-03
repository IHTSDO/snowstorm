package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.SearchLanguagesConfiguration;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.ConceptRepository;
import org.snomed.snowstorm.core.data.repositories.DescriptionRepository;
import org.snomed.snowstorm.core.data.repositories.ReferenceSetMemberRepository;
import org.snomed.snowstorm.core.data.repositories.RelationshipRepository;
import org.snomed.snowstorm.core.util.DescriptionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.core.query.UpdateQueryBuilder;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class AdminOperationsService {

	@Autowired
	private SearchLanguagesConfiguration searchLanguagesConfiguration;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ConceptRepository conceptRepository;

	@Autowired
	private DescriptionRepository descriptionRepository;

	@Autowired
	private RelationshipRepository relationshipRepository;

	@Autowired
	private ReferenceSetMemberRepository referenceSetMemberRepository;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public void reindexDescriptionsForLanguage(String languageCode) throws IOException {
		Map<String, Set<Character>> charactersNotFoldedSets = searchLanguagesConfiguration.getCharactersNotFoldedSets();
		Set<Character> foldedCharacters = charactersNotFoldedSets.getOrDefault(languageCode, Collections.emptySet());
		logger.info("Reindexing all description documents in version control with language code '{}' using {} folded characters.", languageCode, foldedCharacters.size());
		AtomicLong descriptionCount = new AtomicLong();
		AtomicLong descriptionUpdateCount = new AtomicLong();
		try (CloseableIterator<Description> descriptionsOnAllBranchesStream = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
						.withQuery(termQuery(Description.Fields.LANGUAGE_CODE, languageCode))
						.withSort(SortBuilders.fieldSort("internalId"))
						.withPageable(LARGE_PAGE)
						.build(),
				Description.class)) {

			List<UpdateQuery> updateQueries = new ArrayList<>();
			AtomicReference<IOException> exceptionThrown = new AtomicReference<>();
			descriptionsOnAllBranchesStream.forEachRemaining(description -> {
				if (exceptionThrown.get() == null) {

					String newFoldedTerm = DescriptionHelper.foldTerm(description.getTerm(), foldedCharacters);
					descriptionCount.incrementAndGet();
					if (!newFoldedTerm.equals(description.getTermFolded())) {
						UpdateRequest updateRequest = new UpdateRequest();
						try {
							updateRequest.doc(jsonBuilder()
									.startObject()
									.field(Description.Fields.TERM_FOLDED, newFoldedTerm)
									.endObject());
						} catch (IOException e) {
							exceptionThrown.set(e);
						}

						updateQueries.add(new UpdateQueryBuilder()
								.withClass(Description.class)
								.withId(description.getInternalId())
								.withUpdateRequest(updateRequest)
								.build());
						descriptionUpdateCount.incrementAndGet();
					}
					if (updateQueries.size() == 10_000) {
						logger.info("Bulk update {}", descriptionUpdateCount.get());
						elasticsearchTemplate.bulkUpdate(updateQueries);
						updateQueries.clear();
					}
				}
			});
			if (exceptionThrown.get() != null) {
				throw exceptionThrown.get();
			}
			if (!updateQueries.isEmpty()) {
				logger.info("Bulk update {}", descriptionUpdateCount.get());
				elasticsearchTemplate.bulkUpdate(updateQueries);
			}
		} finally {
			elasticsearchTemplate.refresh(Description.class);
		}
		logger.info("Completed reindexing of description documents with language code '{}'. Of the {} documents found {} were updated due to a character folding change.",
				languageCode, descriptionCount.get(), descriptionUpdateCount.get());
	}

	public Map<Class, Set<String>> findAndEndDonatedContent(String branch) {
		if (PathUtil.isRoot(branch)) {
			throw new IllegalArgumentException("Donated content should be ended on extension branch, not MAIN.");
		}

		logger.info("Finding and fixing donated content on {}.", branch);

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);

		Map<Class, Set<String>> fixesApplied = new HashMap<>();
		findAndEndDonatedComponents(branch, branchCriteria, Concept.class, Concept.Fields.CONCEPT_ID, conceptRepository, fixesApplied);
		findAndEndDonatedComponents(branch, branchCriteria, Description.class, Description.Fields.DESCRIPTION_ID, descriptionRepository, fixesApplied);
		findAndEndDonatedComponents(branch, branchCriteria, Relationship.class, Relationship.Fields.RELATIONSHIP_ID, relationshipRepository, fixesApplied);
		findAndEndDonatedComponents(branch, branchCriteria, ReferenceSetMember.class, ReferenceSetMember.Fields.MEMBER_ID, referenceSetMemberRepository, fixesApplied);

		logger.info("Completed donated content fixing on {}.", branch);
		return fixesApplied;
	}

	private void findAndEndDonatedComponents(String branch, BranchCriteria branchCriteria, Class<? extends SnomedComponent> clazz, String idField, ElasticsearchCrudRepository repository, Map<Class, Set<String>> fixesApplied) {
		logger.info("Searching for duplicate {} records on {}", clazz.getSimpleName(), branch);
		BoolQueryBuilder entityBranchCriteria = branchCriteria.getEntityBranchCriteria(clazz);

		// Find components on extension branch
		Set<String> ids = new HashSet<>();
		try (CloseableIterator<? extends SnomedComponent> conceptStream = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery().must(entityBranchCriteria)
						.must(termQuery("path", branch)))
				.withPageable(LARGE_PAGE)
				.withFields(idField).build(), clazz)) {
			conceptStream.forEachRemaining(c -> ids.add(c.getId()));
		}

		// Find donated components which are still active
		Set<String> duplicateIds = new HashSet<>();
		try (CloseableIterator<? extends SnomedComponent> conceptStream = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery().must(entityBranchCriteria)
						.mustNot(termQuery("path", branch)))
				.withFilter(termsQuery(idField, ids))
				.withPageable(LARGE_PAGE)
				.withFields(idField).build(), clazz)) {
			conceptStream.forEachRemaining(c -> {
				if(ids.contains(c.getId())) {
					duplicateIds.add(c.getId());
				}
			});
		}

		logger.info("Found {} duplicate {} records: {}", duplicateIds.size(), clazz.getSimpleName(), duplicateIds);

		// End duplicate components using the commit timestamp of the donated content
		for (String duplicateId : duplicateIds) {
			List<? extends SnomedComponent> intVersionList = elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder()
					.withQuery(boolQuery().must(entityBranchCriteria)
							.must(termQuery(idField, duplicateId))
							.mustNot(termQuery("path", branch)))
					.build(), clazz);
			if (intVersionList.size() != 1) {
				throw new IllegalStateException(String.format("During fix stage expecting 1 int version but found %s for id %s", intVersionList.size(), clazz));
			}
			SnomedComponent intVersion = intVersionList.get(0);
			Date donatedVersionCommitTimepoint = intVersion.getStart();

			List<? extends SnomedComponent> extensionVersionList = elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder()
					.withQuery(boolQuery().must(entityBranchCriteria)
							.must(termQuery(idField, duplicateId))
							.must(termQuery("path", branch)))
					.build(), clazz);
			if (extensionVersionList.size() != 1) {
				throw new IllegalStateException(String.format("During fix stage expecting 1 extension version but found %s for id %s", extensionVersionList.size(), clazz));
			}
			SnomedComponent extensionVersion = extensionVersionList.get(0);
			extensionVersion.setEnd(donatedVersionCommitTimepoint);
			repository.save(extensionVersion);
			logger.info("Ended {} on {} at timepoint {} to match {} version start date.", duplicateId, branch, donatedVersionCommitTimepoint, intVersion.getPath());

			fixesApplied.put(clazz, duplicateIds);
		}
	}

}

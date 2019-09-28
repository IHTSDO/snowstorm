package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.search.sort.SortBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.SearchLanguagesConfiguration;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.util.DescriptionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.core.query.UpdateQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Service
public class AdminOperationsService {

	@Autowired
	private SearchLanguagesConfiguration searchLanguagesConfiguration;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private DomainEntityConfiguration domainEntityConfiguration;

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
		branchMergeService.findAndEndDonatedComponentsOfAllTypes(branch, branchCriteria, fixesApplied);

		logger.info("Completed donated content fixing on {}.", branch);
		return fixesApplied;
	}

	public void rollbackCommit(String branchPath, long timepoint) {
		Branch branchVersion = branchService.findAtTimepointOrThrow(branchPath, new Date(timepoint));
		if (branchVersion.getEnd() != null) {
			throw new IllegalStateException(String.format("Branch %s at timepoint %s is already ended, it's not the latest commit.", branchPath, timepoint));
		}
		branchService.rollbackCompletedCommit(branchVersion, new ArrayList<>(domainEntityConfiguration.getAllDomainEntityTypes()));
	}
}

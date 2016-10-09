package com.kaicube.snomed.elasticsnomed.services;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Sets;
import com.kaicube.elasticversioncontrol.api.BranchService;
import com.kaicube.elasticversioncontrol.api.VersionControlHelper;
import com.kaicube.elasticversioncontrol.domain.Branch;
import com.kaicube.snomed.elasticsnomed.domain.Concept;
import com.kaicube.snomed.elasticsnomed.domain.Description;
import com.kaicube.snomed.elasticsnomed.domain.LanguageReferenceSetMember;
import com.kaicube.snomed.elasticsnomed.domain.Relationship;
import com.kaicube.snomed.elasticsnomed.domain.review.BranchReview;
import com.kaicube.snomed.elasticsnomed.domain.review.BranchReviewConceptChanges;
import com.kaicube.snomed.elasticsnomed.domain.review.BranchState;
import com.kaicube.snomed.elasticsnomed.domain.review.ReviewStatus;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.kaicube.elasticversioncontrol.api.ComponentService.LARGE_PAGE;
import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Service
public class BranchReviewService {

	@Autowired
	private BranchService branchService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	final Cache<String, BranchReview> reviewStore = CacheBuilder.newBuilder()
			.expireAfterWrite(48, TimeUnit.HOURS)
			.build();

	public BranchReview createReview(String source, String target) {
		final Branch sourceBranch = branchService.findBranchOrThrow(source);
		final Branch targetBranch = branchService.findBranchOrThrow(target);
		final boolean sourceIsParent = sourceBranch.isParent(targetBranch);

		// Validate arguments
		if (!sourceIsParent && !targetBranch.isParent(sourceBranch)) {
			throw new IllegalArgumentException("The source or target branch must be the direct parent of the other.");
		}

		// Create review
		final BranchReview branchReview = new BranchReview(UUID.randomUUID().toString(), new Date(), ReviewStatus.CURRENT,
				new BranchState(sourceBranch), new BranchState(targetBranch), sourceIsParent);

		reviewStore.put(branchReview.getId(), branchReview);
		return branchReview;
	}

	public BranchReview getBranchReview(String reviewId) {
		final BranchReview branchReview = reviewStore.getIfPresent(reviewId);

		if (branchReview != null) {
			branchReview.setStatus(
					branchService.isBranchStateCurrent(branchReview.getSource())
							&& branchService.isBranchStateCurrent(branchReview.getTarget()) ? ReviewStatus.CURRENT : ReviewStatus.STALE);
		}

		return branchReview;
	}

	public BranchReviewConceptChanges getBranchReviewConceptChanges(String reviewId) {
		final BranchReview review = getBranchReview(reviewId);

		if (review == null) {
			return null;
		}

		if (review.getStatus() != ReviewStatus.CURRENT) {
			throw new IllegalStateException("Branch review is not current.");
		}

		if (review.getChanges() == null) {
			synchronized (review) {
				// Still null after we acquire the lock?
				if (review.getChanges() == null) {
					final Branch source = branchService.findBranchOrThrow(review.getSource().getPath());
					final Branch target = branchService.findBranchOrThrow(review.getTarget().getPath());

					// source = A
					// target = A/B
					// start = target base

					// source = A/B
					// target = A
					// start = source lastPromotion

					Date start;
					if (review.isSourceIsParent()) {
						start = target.getBase();
					} else {
						start = source.getLastPromotion();
					}
					review.setChanges(createConceptChangeReportOnBranchForTimeRange(source.getFatPath(), start, source.getHead(), review.isSourceIsParent()));
				}
			}
		}
		return review.getChanges();
	}

	public BranchReviewConceptChanges createConceptChangeReportOnBranchForTimeRange(String path, Date start, Date end, boolean sourceIsParent) {

		// Find components of each type that are on the target branch and have been ended on the source branch
		final Set<Long> conceptsWithEndedVersions = new HashSet<>();
		final Set<Long> conceptsWithNewVersions = new HashSet<>();
		final Set<Long> conceptsWithComponentChange = new HashSet<>();
		if (!sourceIsParent) {
			// Technique: Iterate child's 'versionsReplaced' set
			final Set<String> versionsReplaced = branchService.findBranchOrThrow(path).getVersionsReplaced();
			try (final CloseableIterator<Concept> stream = elasticsearchTemplate.stream(componentsReplacedCriteria(versionsReplaced), Concept.class)) {
				stream.forEachRemaining(concept -> conceptsWithEndedVersions.add(parseLong(concept.getConceptId())));
			}
			try (final CloseableIterator<Description> stream = elasticsearchTemplate.stream(componentsReplacedCriteria(versionsReplaced), Description.class)) {
				stream.forEachRemaining(description -> conceptsWithComponentChange.add(parseLong(description.getConceptId())));
			}
			try (final CloseableIterator<Relationship> stream = elasticsearchTemplate.stream(componentsReplacedCriteria(versionsReplaced), Relationship.class)) {
				stream.forEachRemaining(relationship -> conceptsWithComponentChange.add(parseLong(relationship.getSourceId())));
			}
			Set<Long> descriptionIds = new HashSet<>();
			try (final CloseableIterator<LanguageReferenceSetMember> stream =
						 elasticsearchTemplate.stream(componentsReplacedCriteria(versionsReplaced), LanguageReferenceSetMember.class)) {
				stream.forEachRemaining(member -> descriptionIds.add(parseLong(member.getReferencedComponentId())));
			}
			try (final CloseableIterator<Description> stream =
						 elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
								 .withQuery(boolQuery()
										 .must(versionControlHelper.getBranchCriteria(path))
										 .must(termsQuery("descriptionId", descriptionIds)))
								 .withPageable(LARGE_PAGE)
								 .build(), Description.class)) {
				stream.forEachRemaining(description -> conceptsWithComponentChange.add(parseLong(description.getConceptId())));
			}
		}

		// Find new versions of each component type and collect the conceptId they relate to
		final BoolQueryBuilder branchUpdatesCriteria = versionControlHelper.getUpdatesOnBranchDuringRangeCriteria(path, start, end);
		try (final CloseableIterator<Concept> stream =
					 elasticsearchTemplate.stream(newSearchQuery(branchUpdatesCriteria), Concept.class)) {
			stream.forEachRemaining(concept -> {
				final long conceptId = parseLong(concept.getConceptId());
				if (concept.getEnd() == null) {
					conceptsWithNewVersions.add(conceptId);
				} else {
					conceptsWithEndedVersions.add(conceptId);
				}
			});
		}
		try (final CloseableIterator<Description> stream =
					 elasticsearchTemplate.stream(newSearchQuery(branchUpdatesCriteria), Description.class)) {
			stream.forEachRemaining(description -> conceptsWithComponentChange.add(parseLong(description.getConceptId())));
		}
		try (final CloseableIterator<Relationship> stream =
					 elasticsearchTemplate.stream(newSearchQuery(branchUpdatesCriteria), Relationship.class)) {
			stream.forEachRemaining(relationship -> conceptsWithComponentChange.add(parseLong(relationship.getSourceId())));
		}
		Set<Long> descriptionIds = new HashSet<>();
		try (final CloseableIterator<LanguageReferenceSetMember> stream =
					 elasticsearchTemplate.stream(newSearchQuery(branchUpdatesCriteria), LanguageReferenceSetMember.class)) {
			stream.forEachRemaining(member -> descriptionIds.add(parseLong(member.getReferencedComponentId())));
		}
		// Fetch descriptions from any visible branch to get the lang refset's concept id.
		try (final CloseableIterator<Description> stream =
					 elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(versionControlHelper.getBranchCriteria(path))
						.must(termsQuery("descriptionId", descriptionIds)))
				.withPageable(LARGE_PAGE)
				.build(), Description.class)) {
			stream.forEachRemaining(description -> conceptsWithComponentChange.add(parseLong(description.getConceptId())));
		}

		final Sets.SetView<Long> conceptsDeleted = Sets.difference(conceptsWithEndedVersions, conceptsWithNewVersions);
		final Sets.SetView<Long> conceptsCreated = Sets.difference(conceptsWithNewVersions, conceptsWithEndedVersions);
		final Sets.SetView<Long> conceptsModified = Sets.difference(Sets.difference(conceptsWithComponentChange, conceptsCreated), conceptsDeleted);

		return new BranchReviewConceptChanges(null, conceptsCreated, conceptsModified, conceptsDeleted);
	}

	private NativeSearchQuery componentsReplacedCriteria(Set<String> versionsReplaced) {
		return new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(termsQuery("_id", versionsReplaced)))
				.withPageable(LARGE_PAGE)
				.build();
	}

	private NativeSearchQuery newSearchQuery(BoolQueryBuilder branchUpdatesCriteria) {
		return new NativeSearchQueryBuilder()
					.withQuery(boolQuery().must(branchUpdatesCriteria))
					.withPageable(LARGE_PAGE)
					.build();
	}
}

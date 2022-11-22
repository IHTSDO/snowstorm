package org.snomed.snowstorm.ecl;

import ch.qos.logback.classic.Level;
import io.kaicode.elasticvc.api.BranchCriteria;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.langauges.ecl.ECLException;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.Operator;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.RuntimeServiceException;
import org.snomed.snowstorm.core.util.SearchAfterPage;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;
import org.snomed.snowstorm.ecl.validation.ECLPreprocessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.LongPredicate;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;

/**
 * Highest level service for executing ECL.
 */
@Service
public class ECLQueryService {

	@Autowired
	private ECLQueryBuilder eclQueryBuilder;

	@Autowired
	private ECLPreprocessingService eclPreprocessingService;

	@Autowired
	private ECLContentService eclContentService;

	@Value("${timer.ecl.duration-threshold}")
	private int eclDurationLoggingThreshold;

	@Value("${cache.ecl.enabled}")
	private boolean eclCacheEnabled;

	private final ECLResultsCache resultsCache;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ECLQueryService() {
		resultsCache = new ECLResultsCache();
	}

	public Page<Long> selectConceptIds(String ecl, BranchCriteria branchCriteria, boolean stated, PageRequest pageRequest) throws ECLException {
		return selectConceptIds(ecl, branchCriteria, stated, null, pageRequest);
	}

	public Page<Long> selectConceptIds(String ecl, BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIdFilter) throws ECLException {
		return selectConceptIds(ecl, branchCriteria, stated, conceptIdFilter, null);
	}

	public Page<Long> selectConceptIds(String ecl, BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIdFilter,
			PageRequest pageRequest) throws ECLException {
		return selectConceptIds(ecl, branchCriteria, stated, conceptIdFilter, pageRequest, false);
	}


		final SExpressionConstraint sExpressionConstraint =
				eclPreprocessingService.replaceIncorrectConcreteAttributeValue((SExpressionConstraint) eclQueryBuilder.createQuery(ecl), path);
		return doSelectConceptIds(ecl, branchCriteria, path, stated, conceptIdFilter, pageRequest, sExpressionConstraint);


	public Page<Long> selectConceptIds(String ecl, BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIdFilter,
			PageRequest pageRequest, boolean skipEclPreprocessing) throws ECLException {

		SExpressionConstraint expressionConstraint = (SExpressionConstraint) eclQueryBuilder.createQuery(ecl);

		if (isMemberFieldsSearch(expressionConstraint)) {
			throw new RuntimeServiceException("ECL requesting specific refset member fields, can not return concept ids.");
		}
		if (!skipEclPreprocessing) {
			expressionConstraint = eclPreprocessingService.replaceIncorrectConcreteAttributeValue(expressionConstraint, branchCriteria.getBranchPath());
		}
		return doSelectConceptIds(expressionConstraint, branchCriteria, stated, conceptIdFilter, pageRequest);
	}

	public static boolean isMemberFieldsSearch(SExpressionConstraint expressionConstraint) {
		if (expressionConstraint instanceof SSubExpressionConstraint) {
			SSubExpressionConstraint constraint = (SSubExpressionConstraint) expressionConstraint;
			return constraint.getOperator() == Operator.memberOf && (constraint.isReturnAllMemberFields() || constraint.getMemberFieldsToReturn() != null);
		}
		return false;
	}

	public SearchAfterPage<ReferenceSetMember> findReferenceSetMembersWithSpecificFields(String memberECLWithSpecificFields, boolean stated, BranchCriteria branchCriteria, PageRequest pageRequest) {
		SExpressionConstraint query = (SExpressionConstraint) eclQueryBuilder.createQuery(memberECLWithSpecificFields);
		SSubExpressionConstraint constraint = (SSubExpressionConstraint) eclPreprocessingService.replaceIncorrectConcreteAttributeValue(query, branchCriteria.getBranchPath());

		List<Long> conceptIdFilter = null;
		if (constraint.getHistorySupplement() != null) {
			throw new IllegalArgumentException("History supplement can not be used when returning specific fields of a reference set member.");
		} else if (constraint.getConceptFilterConstraints() != null || constraint.getDescriptionFilterConstraints() != null) {
			// Run standard ECL first, then select member rows
			conceptIdFilter = doSelectConceptIds(constraint, branchCriteria, stated, null, null).getContent();
		}
		// Select member rows, apply member filters (maybe for the second time but that's okay)
		List<String> memberFieldsToReturn = constraint.getMemberFieldsToReturn();
		Collection<Long> refsets = null;
		if (!constraint.isWildcard()) {
			if (constraint.getConceptId() != null) {
				refsets = Collections.singleton(parseLong(constraint.getConceptId()));
			} else {
				SExpressionConstraint nestedExpressionConstraint = (SExpressionConstraint) constraint.getNestedExpressionConstraint();
				SExpressionConstraint constrainedRefsetSelector = (SExpressionConstraint) eclQueryBuilder.createQuery(
						String.format("<< 900000000000455006 |Reference set (foundation metadata concept)| AND (%s)", nestedExpressionConstraint.toEclString()));
				refsets = doSelectConceptIds(constrainedRefsetSelector, branchCriteria, stated, null, null).getContent();
			}
		}

		return eclContentService.findReferenceSetMembers(refsets, constraint.getMemberFilterConstraints(), memberFieldsToReturn, conceptIdFilter,
				stated, branchCriteria, pageRequest, eclContentService);
	}

	public Page<Long> doSelectConceptIds(SExpressionConstraint expressionConstraint, BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIdFilter,
			PageRequest pageRequest) {

		// - Optimisation idea -
		// Changing something like "(id) AND (<<id OR >>id)"  to  "(id AND <<id) OR (id AND >>id)" will run in a fraction of the time because there will be no large fetches

		TimerUtil eclSlowQueryTimer = getEclSlowQueryTimer();
		String ecl = expressionConstraint.toEclString();
		String path = branchCriteria.getBranchPath();

		logger.info("ECL on path {}, \"{}\"", path, expressionConstraint.toEclString());

		Optional<Page<Long>> pageOptional;
		if (eclCacheEnabled) {
			BranchVersionECLCache branchVersionCache = resultsCache.getOrCreateBranchVersionCache(path, branchCriteria.getTimepoint());

			PageRequest queryPageRequest = pageRequest;
			LongPredicate filter = null;
			if (conceptIdFilter != null) {
				// Fetch all, without conceptIdFilter or paging. Apply filter and paging afterwards.
				// This may be expensive, but it's the only way to allow the cache to help with this sort of query.
				queryPageRequest = null;
				final LongOpenHashSet fastSet = new LongOpenHashSet(conceptIdFilter);
				filter = fastSet::contains;
			}

			Page<Long> cachedPage = branchVersionCache.get(ecl, stated, queryPageRequest);
			if (cachedPage != null) {
				final int pageNumber = pageRequest != null ? pageRequest.getPageNumber() : 0;
				final int pageSize = pageRequest != null ? pageRequest.getPageSize() : -1;
				logger.info("ECL cache hit {}@{} \"{}\" {}:{}", path, branchCriteria.getTimepoint().getTime(), ecl, pageNumber, pageSize);
				branchVersionCache.recordHit();

				pageOptional = Optional.of(cachedPage);
			} else {
				// Select 1
				// When is pageRequest null?
				pageOptional = expressionConstraint.select(branchCriteria, stated, null, queryPageRequest, eclContentService, true);
				if (pageOptional.isPresent()) {
					// Cache results
					final Page<Long> page = pageOptional.get();
					branchVersionCache.put(ecl, stated, queryPageRequest, page);
					eclSlowQueryTimer.checkpoint(String.format("ecl:'%s', with %s results in this page, now cached for this branch/commit/page.", ecl,
							pageOptional.get().getNumberOfElements()));
				}
			}

			if (pageOptional.isPresent()) {
				// Filter results
				if (filter != null) {
					final List<Long> filteredList = pageOptional.get().get().filter(filter::test).collect(Collectors.toList());
					pageOptional = Optional.of(ConceptSelectorHelper.getPage(pageRequest, filteredList));
				}
			}
		} else {
			// Select 2
			pageOptional = expressionConstraint.select(branchCriteria, stated, conceptIdFilter, pageRequest, eclContentService, true);
			if (pageOptional.isPresent()) {
				eclSlowQueryTimer.checkpoint(String.format("ecl:'%s', with %s results in this page, cache not enabled.", ecl, pageOptional.get().getNumberOfElements()));
			}
		}

		if (pageOptional.isEmpty()) {
			// We only do this at the top level. Nested wildcards do not fetch all concepts.
			return getWildcardPage(branchCriteria, stated, conceptIdFilter, pageRequest);
		}

		return pageOptional.get();
	}

	private Page<Long> getWildcardPage(BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest) {
		// Wildcard expression. Grab a page of concepts with no criteria.
		BoolQueryBuilder query = ConceptSelectorHelper.getBranchAndStatedQuery(branchCriteria.getEntityBranchCriteria(QueryConcept.class), stated);
		return ConceptSelectorHelper.fetchWildcardIds(query, conceptIdFilter, pageRequest, eclContentService);
	}

	private TimerUtil getEclSlowQueryTimer() {
		return new TimerUtil(String.format("ECL took more than %s seconds.", eclDurationLoggingThreshold), Level.INFO, eclDurationLoggingThreshold);
	}

	public ExpressionConstraint createQuery(String ecl) {
		return eclQueryBuilder.createQuery(ecl);
	}

	public ECLResultsCache getResultsCache() {
		return resultsCache;
	}

	public void clearCache() {
		resultsCache.clearCache();
	}

	public void setEclCacheEnabled(boolean eclCacheEnabled) {
		this.eclCacheEnabled = eclCacheEnabled;
	}
}

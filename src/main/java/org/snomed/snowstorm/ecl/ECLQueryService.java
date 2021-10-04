package org.snomed.snowstorm.ecl;

import ch.qos.logback.classic.Level;
import io.kaicode.elasticvc.api.BranchCriteria;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.langauges.ecl.ECLException;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraint;
import org.snomed.snowstorm.ecl.validation.ECLPreprocessingService;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;

@Service
public class ECLQueryService {

	@Autowired
	private ECLQueryBuilder eclQueryBuilder;

	@Autowired
	private ECLPreprocessingService eclPreprocessingService;

	@Autowired
	private QueryService queryService;

	@Value("${timer.ecl.duration-threshold}")
	private int eclDurationLoggingThreshold;

	private final ECLResultsCache resultsCache;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ECLQueryService() {
		resultsCache = new ECLResultsCache();
	}

	public Page<Long> selectConceptIds(String ecl, BranchCriteria branchCriteria, String path, boolean stated, PageRequest pageRequest) throws ECLException {
		return selectConceptIds(ecl, branchCriteria, path, stated, null, pageRequest);
	}

	public Page<Long> selectConceptIds(String ecl, BranchCriteria branchCriteria, String path, boolean stated, Collection<Long> conceptIdFilter) throws ECLException {
		return selectConceptIds(ecl, branchCriteria, path, stated, conceptIdFilter, null);
	}

	public Page<Long> selectConceptIds(String ecl, BranchCriteria branchCriteria, String path, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest) throws ECLException {
		final SExpressionConstraint sExpressionConstraint = eclPreprocessingService.replaceIncorrectConcreteAttributeValue((SExpressionConstraint) eclQueryBuilder.createQuery(ecl), path, pageRequest);
		return doSelectConceptIds(ecl, branchCriteria, path, stated, conceptIdFilter, pageRequest, sExpressionConstraint);
	}

	public Page<Long> doSelectConceptIds(String ecl, BranchCriteria branchCriteria, String path, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest, SExpressionConstraint expressionConstraint) {

		BranchVersionECLCache branchVersionCache = null;
		if (conceptIdFilter == null) {
			branchVersionCache = resultsCache.getOrCreateBranchVersionCache(path, branchCriteria.getTimepoint());
			Page<Long> cachedPage = branchVersionCache.get(ecl, stated, pageRequest);
			if (cachedPage != null) {
				final int pageNumber = pageRequest != null ? pageRequest.getPageNumber() : 0;
				final int pageSize = pageRequest != null ? pageRequest.getPageSize() : -1;
				logger.debug("ECL cache hit {}@{} \"{}\" {}:{}", path, branchCriteria.getTimepoint().getTime(), ecl, pageNumber, pageSize);
				return cachedPage;
			}
		}

		TimerUtil eclSlowQueryTimer = getEclSlowQueryTimer();

		if (expressionConstraint == null) {
			expressionConstraint = (SExpressionConstraint) eclQueryBuilder.createQuery(ecl);
		}

		// - Optimisation idea -
		// Changing something like "(id) AND (<<id OR >>id)"  to  "(id AND <<id) OR (id AND >>id)" will run in a fraction of the time because there will be no large fetches

		Optional<Page<Long>> pageOptional = expressionConstraint.select(path, branchCriteria, stated, conceptIdFilter, pageRequest, queryService);
		if (pageOptional.isPresent()) {
			final Page<Long> page = pageOptional.get();
			if (branchVersionCache != null) {
				branchVersionCache.put(ecl, stated, pageRequest, page);
			}
			String cacheWording = branchVersionCache != null ? "now cached for this branch/commit/page" : "can not be cached because of conceptIdFilter";
			eclSlowQueryTimer.checkpoint(() -> String.format("ecl:'%s', with %s results in this page, %s.", ecl, page.getNumberOfElements(), cacheWording));
		}

		return pageOptional.orElseGet(() -> {
			BoolQueryBuilder query = ConceptSelectorHelper.getBranchAndStatedQuery(branchCriteria.getEntityBranchCriteria(QueryConcept.class), stated);
			return ConceptSelectorHelper.fetchIds(query, conceptIdFilter, null, pageRequest, queryService);
		});
	}

	public Boolean hasAnyResults (String ecl, String branch, BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIdFilter) {
		
		SExpressionConstraint expressionConstraint = (SExpressionConstraint) eclQueryBuilder.createQuery(ecl);
		Optional<Page<Long>> pageOptional = expressionConstraint.select(branch, branchCriteria, stated, conceptIdFilter, ControllerHelper.getPageRequest(0,1), queryService);
		
		return pageOptional.get().hasContent();
	}
	
	private TimerUtil getEclSlowQueryTimer() {
		return new TimerUtil(String.format("ECL took more than %s seconds.", eclDurationLoggingThreshold), Level.INFO, eclDurationLoggingThreshold);
	}

}

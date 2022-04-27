package org.snomed.snowstorm.validation;

import io.kaicode.elasticvc.api.BranchCriteria;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.util.SearchAfterPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;

public class DisposableQueryService {

    private final QueryService queryService;
    private final String branchPath;

    private final Map<QueryService.ConceptQueryBuilder, Page<Long>> searchCache = Collections.synchronizedMap(new HashMap<>());
    private final Map<QueryService.ConceptQueryBuilder, Boolean> anyResultsCache = Collections.synchronizedMap(new HashMap<>());
    private final BranchCriteria branchCriteria;

    public DisposableQueryService(QueryService queryService, String branchPath, BranchCriteria branchCriteria) {
        this.queryService = queryService;
        this.branchPath = branchPath;
        this.branchCriteria = branchCriteria;
    }

    public QueryService.ConceptQueryBuilder createQueryBuilder(boolean stated) {
        return queryService.createQueryBuilder(stated);
    }

    public Set<String> searchForIdStrings(QueryService.ConceptQueryBuilder queryBuilder) {
        return searchForIds(queryBuilder).stream().map(Object::toString).collect(Collectors.toSet());
    }

    public Page<Long> searchForIds(QueryService.ConceptQueryBuilder queryBuilder) {
        if (!searchCache.containsKey(queryBuilder)) {
            SearchAfterPage<Long> ids = queryService.searchForIds(queryBuilder, branchCriteria, LARGE_PAGE);
            searchCache.put(queryBuilder, ids);
        }
        return searchCache.get(queryBuilder);
    }

    public Set<String> findAncestorIds(boolean stated, String conceptId) {
        return searchForIdStrings(queryService.createQueryBuilder(stated).ecl(">" + conceptId));
    }

    public boolean isAnyResults(QueryService.ConceptQueryBuilder queryBuilder) {
        if (!anyResultsCache.containsKey(queryBuilder)) {
            SearchAfterPage<Long> page = queryService.searchForIds(queryBuilder, branchCriteria, PageRequest.of(0, 1));
            anyResultsCache.put(queryBuilder, !page.isEmpty());
        }
        return anyResultsCache.get(queryBuilder);
    }
}

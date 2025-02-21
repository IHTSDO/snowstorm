package org.snomed.snowstorm.ecl;

import io.kaicode.elasticvc.api.BranchCriteria;
import org.snomed.snowstorm.core.data.domain.RefsetConceptsLookup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static io.kaicode.elasticvc.helper.QueryHelper.termQuery;

@Service
public class RefsetConceptsLookupService {

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    public List<RefsetConceptsLookup> getRefsetConceptsLookups(BranchCriteria branchCriteria, Long refsetId) {
        NativeQueryBuilder queryBuilder = new NativeQueryBuilder()
                .withQuery(bool(b -> b
                        .must(branchCriteria.getEntityBranchCriteria(RefsetConceptsLookup.class))
                        .must(termQuery(RefsetConceptsLookup.Fields.REFSETID, refsetId))
                ))
                // Exclude conceptIds from the response as it can be a large list
                .withSourceFilter(new FetchSourceFilter(new String[]{"_id"}, null))
                .withPageable(Pageable.ofSize(100));
        SearchHits<RefsetConceptsLookup> refsetConceptsLookupSearchHits = elasticsearchOperations.search(queryBuilder.build(), RefsetConceptsLookup.class);
        List<RefsetConceptsLookup> results = new ArrayList<>();
        refsetConceptsLookupSearchHits.forEach(hit -> results.add(hit.getContent()));
        return results;
    }
}

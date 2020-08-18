package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.domain.Branch;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
// Snowstorm branch service has some methods in addition to the ElasticVC library service.
public class SBranchService {

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;

	public Page<Branch> findAllVersionsAfterOrEqualToTimestamp(String path, Date timestamp, Pageable pageable) {
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(QueryBuilders.termQuery("path", path))
						.must(QueryBuilders.rangeQuery("start").gte(timestamp.getTime())))
				.withSort(SortBuilders.fieldSort("start"))
				.withPageable(pageable);
		SearchHits<Branch> searchHits = elasticsearchTemplate.search(queryBuilder.build(), Branch.class);
		return new PageImpl<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()),
				pageable, searchHits.getTotalHits());
	}

	public List<Branch> findByPathAndBaseTimepoint(Set<String> path, Date baseTimestamp) {
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(termsQuery("path", path))
						.must(termQuery("base", baseTimestamp.getTime())))
				.withSort(SortBuilders.fieldSort("start"))
				.withPageable(PageRequest.of(0, path.size()));
		return elasticsearchTemplate.search(queryBuilder.build(), Branch.class)
				.stream().map(SearchHit::getContent).collect(Collectors.toList());
	}

}

package org.snomed.snowstorm;

import com.google.common.base.Strings;
import io.kaicode.elasticvc.domain.Branch;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.snomed.snowstorm.config.elasticsearch.IndexConfig;
import org.snomed.snowstorm.config.elasticsearch.SnowstormElasticsearchMappingContext;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

import static java.lang.String.format;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

/**
 * Local utility for exploring content within Snowstorm indices.
 */
public class CommitExplorer {

	private final ElasticsearchRestTemplate template;
	private final String elasticsearchClusterHost;
	private final String indexNamePrefix;

	private void run() {
		listCommits("MAIN");
		listRecentVersions("255083005", Concept.class);
		listRecentVersions("646067016", Description.class);
		listRecentVersions("04591202-b9cb-45a5-8533-aa1f9e0f8644", ReferenceSetMember.class);
	}

	public static void main(String[] args) {
		if (args.length == 0) {
			System.err.println("Please provide 1 or 2 arguments: elasticsearchHost indexPrefix?");
			System.exit(1);
		}
		String elasticsearchClusterHost = args[0];
		if (elasticsearchClusterHost.endsWith("/")) {
			elasticsearchClusterHost = elasticsearchClusterHost.substring(0, elasticsearchClusterHost.length() - 1);
		}
		String indexPrefix = "";
		if (args.length >= 2) {
			indexPrefix = args[1];
		}
		new CommitExplorer(indexPrefix, elasticsearchClusterHost).run();
		System.exit(0);
	}

	private CommitExplorer(String indexPrefix, String elasticsearchClusterHost) {
		indexNamePrefix = indexPrefix;
		this.elasticsearchClusterHost = elasticsearchClusterHost;
		template = elasticsearchTemplate(indexNamePrefix, this.elasticsearchClusterHost);
	}

	/**
	 * List the last x versions of the specified SNOMED CT component across branches.
	 * Listing includes:
	 * 	- the start time of the commit (both timestamp and human readable)
	 * 	- the branch path
	 * 	- active and released states
	 * 	- component version ended state
	 * 	- the component version id and a link to the document in Elasticsearch
	 * @param id Component identifier
	 * @param componentClass Component class
	 */
	private <T extends SnomedComponent> void listRecentVersions(String id, Class<T> componentClass) {
		int size = 20;
		System.out.println();
		SearchHits<T> searchHits = template.search(new NativeSearchQueryBuilder()
				.withQuery(termQuery(getIdField(componentClass), id))
				.withSort(SortBuilders.fieldSort("start").order(SortOrder.DESC))
				.withPageable(PageRequest.of(0, size))
				.build(), componentClass);
		System.out.println(format("Latest %s versions of %s '%s'", searchHits.getTotalHits(), componentClass.getSimpleName(), id));
		for (SearchHit<T> hit : searchHits) {
			T componentVersion = hit.getContent();
			System.out.println(format("%s (%s) - %s - %s,%s - %s - %s",
					componentVersion.getStartDebugFormat(),
					componentVersion.getStart().getTime(),
					componentVersion.getPath(),
					componentVersion.isActive() ? "Active" : "Inactive",
					componentVersion.isReleased() ? "Released" : "",
					componentVersion.getEnd() == null ? "Current version on branch" : format("Ended @ %s (%s)", componentVersion.getEndDebugFormat(), componentVersion.getEnd().getTime()),
					format("ID:%s %s/%s/%s", componentVersion.getInternalId(), elasticsearchClusterHost, getTypeMapping(componentClass), componentVersion.getInternalId())
			));
		}
		System.out.println(format("%s total", searchHits.getTotalHits()));
	}

	private <T extends SnomedComponent> String getTypeMapping(Class<T> componentClass) {
		Document annotation = componentClass.getAnnotation(Document.class);
		String indexName = annotation.indexName();
		return format("%s%s", indexNamePrefix, indexName);
	}

	/**
	 * List commits on a branch.
	 * @param path The branch path.
	 */
	private void listCommits(String path) {
		int size = 20;
		System.out.println();
		SearchHits<Branch> branchVersions = template.search(new NativeSearchQueryBuilder()
				.withQuery(termQuery("path", path))
				.withSort(SortBuilders.fieldSort("start").order(SortOrder.DESC))
				.withPageable(PageRequest.of(0, size))
				.build(), Branch.class);
		System.out.println(format("Latest %s commits on %s", branchVersions.getTotalHits(), path));
		for (SearchHit<Branch> hit : branchVersions) {
			Branch branchVersion = hit.getContent();
			System.out.println(format("%s (%s)", branchVersion.getStartDebugFormat(), branchVersion.getStart().getTime()));
		}
		System.out.println(format("%s total", branchVersions.getTotalHits()));
	}

	private <T extends SnomedComponent> String getIdField(Class<T> componentClass) {
		try {
			return componentClass.newInstance().getIdField();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new RuntimeException(format("Not able to create instance of %s", componentClass.getSimpleName()), e);
		}
	}

	public ElasticsearchRestTemplate elasticsearchTemplate(String indexNamePrefix, String host) {
		short indexShards = 3;// Assuming this won't be used because the indices already exist.
		SimpleElasticsearchMappingContext mappingContext = new SnowstormElasticsearchMappingContext(new IndexConfig(indexNamePrefix, indexShards, indexShards));
		return new ElasticsearchRestTemplate(
				RestClients.create(ClientConfiguration.create(host)).rest(),
				new MappingElasticsearchConverter(mappingContext)
		);
	}

}

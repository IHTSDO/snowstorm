package org.snomed.snowstorm.commitexplorer;

import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.helper.SortBuilders;
import org.snomed.snowstorm.config.ElasticsearchConfig;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.snomed.snowstorm.core.util.AggregationUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.client.elc.Aggregation;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;
import static io.kaicode.elasticvc.helper.QueryHelper.*;

/**
 * Local utility for exploring content within Snowstorm indices.
 */
public class CommitExplorer {

	private final ElasticsearchOperations operations;
	private final String elasticsearchClusterHost;
	private final String indexNamePrefix;

	private void run() {
		//listCommits("MAIN");
		//listRecentVersions("209889006", Concept.class);
		//listRecentVersions("890431008", Concept.class);
		listRecentVersions("12392023020", Relationship.class);
		//listRecentVersions("646067016", Description.class);
		//listRecentVersions("84fd3311-705d-4ab0-ab84-989eaa048839", ReferenceSetMember.class);
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
		ApplicationContext applicationContext = SpringApplication.run(Config.class, "--elasticsearch.urls=" + elasticsearchClusterHost,
				"--elasticsearch.index.prefix=" + indexPrefix, "--server.port=8099");
		operations = applicationContext.getBean(ElasticsearchOperations.class);
		this.indexNamePrefix = indexPrefix;
		this.elasticsearchClusterHost = elasticsearchClusterHost;
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
		SearchHits<T> searchHits = operations.search(new NativeQueryBuilder()
				.withQuery(termQuery(getIdField(componentClass), id))
				.withSort(SortBuilders.fieldSortDesc("start"))
				.withPageable(PageRequest.of(0, size))
				.build(), componentClass);
		System.out.printf("Latest %s versions of %s '%s'%n", searchHits.getTotalHits(), componentClass.getSimpleName(), id);
		for (SearchHit<T> hit : searchHits) {
			T componentVersion = hit.getContent();
			System.out.printf("%s (%s) - %s - %s,%s - %s - %s%n",
					componentVersion.getStartDebugFormat(),
					componentVersion.getStart().getTime(),
					componentVersion.getPath(),
					componentVersion.isActive() ? "Active" : "Inactive",
					componentVersion.isReleased() ? "Released" : "",
					componentVersion.getEnd() == null ? "Current version on branch" : format("Ended @ %s (%s)", componentVersion.getEndDebugFormat(), componentVersion.getEnd().getTime()),
					format("ID:%s %s/%s/%s", componentVersion.getInternalId(), elasticsearchClusterHost, getTypeMapping(componentClass), componentVersion.getInternalId())
			);
		}
		System.out.printf("%s total%n", searchHits.getTotalHits());
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
		int size = 5;
		System.out.println();
		SearchHits<Branch> branchVersions = operations.search(new NativeQueryBuilder()
				.withQuery(termQuery("path", path))
				.withSort(SortBuilders.fieldSortDesc("start"))
				.withPageable(PageRequest.of(0, size))
				.build(), Branch.class);
		System.out.printf("Latest %s commits on %s%n", branchVersions.getSearchHits().size(), path);
		for (SearchHit<Branch> hit : branchVersions) {
			Branch branchVersion = hit.getContent();
			System.out.printf("%s (%s, base %s) versions replaced %s%n", branchVersion.getStartDebugFormat(), branchVersion.getStart().getTime(),
					branchVersion.getBaseTimestamp(), branchVersion.getVersionsReplacedCounts());
			final SearchHits<SnomedComponent> results = operations.search(new NativeQueryBuilder()
					.withQuery(bool(b -> b
							.must(termQuery("path", path))
							.must(termQuery("start", branchVersion.getHead())))
					)
					.withAggregation("types", AggregationBuilders.terms().field("_type").build()._toAggregation())
					.build(), SnomedComponent.class);

			final SearchHits<Branch> childBranches = operations.search(new NativeQueryBuilder()
					.withQuery(
							bool(b -> b
									.must(prefixQuery("path", path + "/"))
									.must(termQuery("base", branchVersion.getHead()))
									.mustNot(existsQuery("end")))
					).build(), Branch.class);
			final Set<String> childPaths = childBranches.getSearchHits().stream().map(childHit -> childHit.getContent().getPath()).collect(Collectors.toSet());
			System.out.printf(" > %s children with this base: %s%n", childPaths.size(), childPaths);
			if (results.hasAggregations()) {
				final List<Aggregation> types = AggregationUtils.getAggregations(results.getAggregations(),"types");
				for (Aggregation aggregation : types) {
					aggregation.getAggregate().sterms().buckets().array().forEach(bucket -> System.out.printf(" - %s %s\n", bucket.docCount(), bucket.key().stringValue()));
				}
			}

		}
		System.out.printf("%s total%n", branchVersions.getTotalHits());
	}

	private <T extends SnomedComponent> String getIdField(Class<T> componentClass) {
		try {
			return componentClass.getDeclaredConstructor().newInstance().getIdField();
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(format("Not able to create instance of %s", componentClass.getSimpleName()), e);
		}
	}

	@TestConfiguration
	@SpringBootApplication(
			exclude = {
					ElasticsearchDataAutoConfiguration.class,
					ElasticsearchRestClientAutoConfiguration.class,
			}
	)
	public static class Config extends ElasticsearchConfig {

	}

}

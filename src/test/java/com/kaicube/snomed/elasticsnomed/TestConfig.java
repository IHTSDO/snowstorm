package com.kaicube.snomed.elasticsnomed;

import com.kaicube.elasticversioncontrol.domain.Branch;
import com.kaicube.snomed.elasticsnomed.domain.*;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;

import java.net.UnknownHostException;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

@Configuration
public class TestConfig {

	@Bean
	public ElasticsearchTemplate elasticsearchTemplate() throws UnknownHostException {
		final ElasticsearchTemplate elasticsearchTemplate = Config.getElasticsearchTemplate(elasticSearchClient());
		elasticsearchTemplate.deleteIndex(Concept.class);
		elasticsearchTemplate.deleteIndex(Description.class);
		elasticsearchTemplate.deleteIndex(Relationship.class);
		elasticsearchTemplate.deleteIndex(ReferenceSetMember.class);
		elasticsearchTemplate.deleteIndex(QueryIndexConcept.class);
		elasticsearchTemplate.deleteIndex(Branch.class);
		return elasticsearchTemplate;
	}

	@Bean // Use embedded Elastic search Server
	public Client elasticSearchClient() throws UnknownHostException {
		return nodeBuilder().local(true).settings(Settings.builder().put("path.home", "target/data").build()).node().client();
	}

}

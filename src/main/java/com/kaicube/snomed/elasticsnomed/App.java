package com.kaicube.snomed.elasticsnomed;

import com.kaicube.snomed.elasticsnomed.services.BranchService;
import org.elasticsearch.common.settings.Settings;
import com.kaicube.snomed.elasticsnomed.services.ConceptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

@Configuration
@EnableElasticsearchRepositories(basePackages = "com.kaicube.snomed.elasticsnomed.repositories")
public class App {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	final Logger logger = LoggerFactory.getLogger(getClass());

	@Bean
	public ElasticsearchOperations elasticsearchTemplate() {
		return new ElasticsearchTemplate(nodeBuilder().local(true).settings(Settings.builder().put("path.home", "target/data").build()).node().client());
	}

	@Bean
	public ConceptService getConceptService() {
		return new ConceptService();
	}

	@Bean
	public BranchService getBranchService() {
		return new BranchService();
	}

	public static void main(String[] args) throws Exception {
		new AnnotationConfigApplicationContext(App.class).getBean(App.class).run(args);
	}

	public void run(String... strings) throws Exception {
	}

}
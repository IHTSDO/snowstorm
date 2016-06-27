package com.kaicube.snomed.elasticsnomed;

import com.kaicube.snomed.elasticsnomed.rf2import.ImportService;
import com.kaicube.snomed.elasticsnomed.services.BranchService;
import com.kaicube.snomed.elasticsnomed.services.ConceptService;
import org.elasticsearch.common.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

import javax.annotation.PostConstruct;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

@SpringBootApplication
@EnableElasticsearchRepositories(basePackages = "com.kaicube.snomed.elasticsnomed.repositories")
public class App {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ImportService importService;

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

	@Bean
	public ImportService getImportService() {
		return new ImportService();
	}

	public static void main(String[] args) {
		SpringApplication.run(App.class, args);
	}

	@PostConstruct
	public void run() throws Exception {
		conceptService.deleteAll();
		branchService.deleteAll();

		branchService.create("MAIN");
		importService.importSnapshot(getClass().getResource("/MiniCT_INT_GB_20140131").getPath(), "MAIN");
	}

}

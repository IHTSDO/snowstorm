package com.kaicube.snomed.elasticsnomed;

import com.kaicube.snomed.elasticsnomed.rf2import.ImportService;
import com.kaicube.snomed.elasticsnomed.services.BranchService;
import com.kaicube.snomed.elasticsnomed.services.ConceptService;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.annotation.PostConstruct;

@SpringBootApplication
@EnableElasticsearchRepositories(basePackages = "com.kaicube.snomed.elasticsnomed.repositories")
public class App {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	@Autowired
	private ImportService importService;

	final Logger logger = LoggerFactory.getLogger(getClass());

	@Bean // Use standalone Elasticsearch Server on localhost
	public Client client() throws UnknownHostException {
		final InetAddress localhost = InetAddress.getByName("localhost");
		return TransportClient.builder().build()
				.addTransportAddress(new InetSocketTransportAddress(localhost, 9300));
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
		// Import international edition at startup
		conceptService.deleteAll();
		branchService.deleteAll();
		branchService.create("MAIN");
		importService.importSnapshot("release/SnomedCT_RF2Release_INT_20160131", "MAIN");
	}

}

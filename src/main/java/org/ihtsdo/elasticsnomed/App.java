package org.ihtsdo.elasticsnomed;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.rf2import.ImportService;
import org.ihtsdo.elasticsnomed.services.ConceptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import javax.annotation.PostConstruct;

@EnableSwagger2
public class App extends Config {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	@Autowired
	private ImportService importService;

	final Logger logger = LoggerFactory.getLogger(getClass());

	public static void main(String[] args) {
		System.setProperty("org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "true"); // Swagger encodes the slash in branch paths
		SpringApplication.run(App.class, args);
	}

	@PostConstruct
	public void run() throws Exception {
		// Uncomment to import the international edition from disk at startup
//		conceptService.deleteAll();
//		branchService.deleteAll();
//		branchService.create("MAIN");
//		importService.importSnapshot("release/xSnomedCT_RF2Release_INT_20170131", "MAIN");
//		 or
//		importService.importFull("release/SnomedCT_RF2Release_INT_20160131", "MAIN");
	}

}

package com.kaicube.snomed.elasticsnomed;

import org.springframework.boot.SpringApplication;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import javax.annotation.PostConstruct;

@EnableSwagger2
public class App extends Config {

	public static void main(String[] args) {
		System.setProperty("org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "true"); // Swagger encodes the slash in branch paths
		SpringApplication.run(App.class, args);
	}

	@PostConstruct
	public void run() throws Exception {
		// Import international edition at startup
//		conceptService.deleteAll();
//		branchService.deleteAll();
//		branchService.create("MAIN");
//		importService.importSnapshot("release/SnomedCT_RF2Release_INT_20160731", "MAIN");
//		importService.importFull("release/SnomedCT_RF2Release_INT_20160131", "MAIN");
	}

}

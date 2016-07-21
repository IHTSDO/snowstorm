package com.kaicube.snomed.elasticsnomed.rest;

import com.kaicube.snomed.elasticsnomed.rf2import.ImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
public class ImportController {

	@Autowired
	private ImportService importService;

	private final ExecutorService executorService = Executors.newCachedThreadPool();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@RequestMapping("/imports")
	@ResponseBody
	public void importArchive(@RequestParam String releaseDirPath, @RequestParam String branchPath,
			@RequestParam(required = false) String stopImportAfterEffectiveTime) {
		executorService.submit((Runnable) () -> {
			try {
				importService.importFull(releaseDirPath, branchPath, stopImportAfterEffectiveTime);
			} catch (Exception e) {
				logger.error("Import failed.", e);
			}
		});
	}

}

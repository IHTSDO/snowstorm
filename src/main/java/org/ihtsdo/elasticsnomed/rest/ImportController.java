package org.ihtsdo.elasticsnomed.rest;

import org.ihtsdo.elasticsnomed.core.rf2.rf2import.ImportJob;
import org.ihtsdo.elasticsnomed.core.rf2.rf2import.ImportService;
import org.ihtsdo.elasticsnomed.core.rf2.RF2Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.ihtsdo.elasticsnomed.rest.ControllerHelper.requiredParam;

@RestController
@RequestMapping(value = "/imports", produces = "application/json")
public class ImportController {

	@Autowired
	private ImportService importService;

	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Void> createImportJob(@RequestBody ImportConfiguration importConfiguration) {
		// TODO: What is the Spring way to do this validation?
		requiredParam(importConfiguration.type, "type");
		requiredParam(importConfiguration.branchPath, "branchPath");

		String id = importService.createJob(importConfiguration.type, importConfiguration.branchPath);
		return ControllerHelper.getCreatedResponse(id);
	}

	@RequestMapping(value = "/{importId}", method = RequestMethod.GET)
	public ImportJob getImportJob(@PathVariable String importId) {
		return importService.getImportJobOrThrow(importId);
	}

	@RequestMapping(value = "/{importId}/archive", method = RequestMethod.POST, consumes = "multipart/form-data")
	public void uploadImportRf2Archive(@PathVariable String importId, @RequestParam MultipartFile file) {
		try {
			importService.importArchiveAsync(importId, file.getInputStream());
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to open uploaded archive file.");
		}
	}

	public static final class ImportConfiguration {

		private RF2Type type;
		private String branchPath;

		public void setType(RF2Type type) {
			this.type = type;
		}

		public void setBranchPath(String branchPath) {
			this.branchPath = branchPath;
		}

	}

}

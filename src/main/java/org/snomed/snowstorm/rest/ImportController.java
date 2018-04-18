package org.snomed.snowstorm.rest;

import org.snomed.snowstorm.core.rf2.rf2import.ImportJob;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.snomed.snowstorm.core.rf2.rf2import.RF2ImportConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping(value = "/imports", produces = "application/json")
public class ImportController {

	@Autowired
	private ImportService importService;

	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Void> createImportJob(@RequestBody RF2ImportConfiguration importConfiguration) {
		ControllerHelper.requiredParam(importConfiguration.getType(), "type");
		ControllerHelper.requiredParam(importConfiguration.getBranchPath(), "branchPath");

		String id = importService.createJob(importConfiguration);
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

}

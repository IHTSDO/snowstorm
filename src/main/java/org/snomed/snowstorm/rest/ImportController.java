package org.snomed.snowstorm.rest;

import io.swagger.annotations.Api;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportJob;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.snomed.snowstorm.core.rf2.rf2import.RF2ImportConfiguration;
import org.snomed.snowstorm.rest.pojo.ImportCreationRequest;
import org.snomed.snowstorm.rest.pojo.ImportPatchCreationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;

@RestController
@Api(tags = "Import", description = "RF2")
@RequestMapping(value = "/imports", produces = "application/json")
public class ImportController {

	@Autowired
	private ImportService importService;

	@RequestMapping(method = RequestMethod.POST)
	public ResponseEntity<Void> createImportJob(@RequestBody ImportCreationRequest importRequest) {
		ControllerHelper.requiredParam(importRequest.getType(), "type");
		ControllerHelper.requiredParam(importRequest.getBranchPath(), "branchPath");

		RF2ImportConfiguration importConfiguration = new RF2ImportConfiguration(importRequest.getType(), importRequest.getBranchPath());
		importConfiguration.setCreateCodeSystemVersion(importRequest.getCreateCodeSystemVersion());
		String id = importService.createJob(importConfiguration);
		return ControllerHelper.getCreatedResponse(id);
	}

	@RequestMapping(value = "/release-patch", method = RequestMethod.POST)
	public ResponseEntity<Void> createReleasePatchImportJob(@RequestBody ImportPatchCreationRequest importPatchRequest) {
		ControllerHelper.requiredParam(importPatchRequest.getBranchPath(), "branchPath");
		ControllerHelper.requiredParam(importPatchRequest.getPatchReleaseVersion(), "patchReleaseVersion");

		RF2ImportConfiguration importConfiguration = new RF2ImportConfiguration(RF2Type.DELTA, importPatchRequest.getBranchPath());
		importConfiguration.setPatchReleaseVersion(importPatchRequest.getPatchReleaseVersion());
		String id = importService.createJob(importConfiguration);
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setLocation(ServletUriComponentsBuilder.fromCurrentContextPath().path("/imports/{id}")
				.buildAndExpand(id).toUri());
		return new ResponseEntity<>(httpHeaders, HttpStatus.CREATED);
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

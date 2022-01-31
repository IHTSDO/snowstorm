package org.snomed.snowstorm.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.rf2.rf2import.ImportJob;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.snomed.snowstorm.core.rf2.rf2import.RF2ImportConfiguration;
import org.snomed.snowstorm.rest.pojo.ImportCreationRequest;
import org.snomed.snowstorm.rest.pojo.ImportPatchCreationRequest;
import org.snomed.snowstorm.rest.pojo.LocalFileImportCreationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

@RestController
@Api(tags = "Import", description = "RF2")
@RequestMapping(value = "/imports", produces = "application/json")
public class ImportController {

	@Autowired
	private ImportService importService;

	public static final String CODE_SYSTEM_INTERNAL_RELEASE_FLAG_README = "The 'internalRelease' flag is optional, " +
			"it can be used to hide a version from the code system versions listing and prevent it being chosen as the code system 'latestRelease'. ";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@ApiOperation(value = "Create an import job.",
	notes = "Creates an import job ready for an archive to be uploaded. " +
					CODE_SYSTEM_INTERNAL_RELEASE_FLAG_README +
					"The 'location' response header contain the URL, including the identifier, of the new resource. " +
					"Use the upload archive function next.")
	@RequestMapping(method = RequestMethod.POST)
	@PreAuthorize("hasPermission('AUTHOR', #importRequest.branchPath)")
	public ResponseEntity<Void> createImportJob(@RequestBody ImportCreationRequest importRequest) {
		ControllerHelper.requiredParam(importRequest.getType(), "type");
		ControllerHelper.requiredParam(importRequest.getBranchPath(), "branchPath");

		RF2ImportConfiguration importConfiguration = new RF2ImportConfiguration(importRequest.getType(), importRequest.getBranchPath());
		importConfiguration.setCreateCodeSystemVersion(importRequest.getCreateCodeSystemVersion());
		importConfiguration.setInternalRelease(importRequest.isInternalRelease());
		String id = importService.createJob(importConfiguration);
		return ControllerHelper.getCreatedResponse(id);
	}

	@ApiOperation(value = "Create and start a local file import.",
			notes = "Creates and starts an import using a file on the filesystem local to the server. " +
					"PLEASE NOTE this is an asynchronous call, this function starts the import but does not wait for it to complete. " +
					"The 'internalRelease' flag hides a version, by default, from the code system versions listing " +
					"and prevents it being chosen as the code system 'latestRelease'. " +
					"The 'location' header has the identifier of the new resource. Use this to check the status of the import until it is COMPLETED or FAILED.")
	@RequestMapping(value = "start-local-file-import", method = RequestMethod.POST)
	@PreAuthorize("hasPermission('AUTHOR', #importRequest.branchPath)")
	public ResponseEntity<Void> createAndStartLocalFileImport(@RequestBody LocalFileImportCreationRequest importRequest) {
		ControllerHelper.requiredParam(importRequest.getType(), "type");
		ControllerHelper.requiredParam(importRequest.getBranchPath(), "branchPath");
		String filePath = importRequest.getFilePath();
		ControllerHelper.requiredParam(filePath, "filePath");

		File localFile = new File(filePath);// lgtm [java/path-injection]
		if (!localFile.isFile()) {
			handleFileNotFound(filePath, localFile);
		}

		RF2ImportConfiguration importConfiguration = new RF2ImportConfiguration(importRequest.getType(), importRequest.getBranchPath());
		importConfiguration.setCreateCodeSystemVersion(importRequest.getCreateCodeSystemVersion());
		importConfiguration.setInternalRelease(importRequest.isInternalRelease());

		String id = importService.createJob(importConfiguration);

		try {
			importService.importArchiveAsync(id, importRequest.getBranchPath(), new FileInputStream(localFile));
		} catch (FileNotFoundException e) {
			handleFileNotFound(filePath, localFile);
		}

		return ControllerHelper.getCreatedResponse(id, "/start-local-file-import");
	}

	private void handleFileNotFound(String filePath, File localFile) {
		// Absolute path only revealed in log for security.
		logger.warn(String.format("File with absolute path '%s' not found on local filesystem.", localFile.getAbsolutePath()));
		throw new NotFoundException(String.format("File '%s' not found on local filesystem.", filePath));
	}

	@ApiOperation(value = "Apply a release patch.",
			notes = "This endpoint is only used to support the International authoring process. " +
					"Small content changes and additions gathered during the Beta Feedback process can be applied to content after it has been versioned and before the release is published. " +
					"PLEASE NOTE this function does not support content deletions.")
	@RequestMapping(value = "/release-patch", method = RequestMethod.POST)
	@PreAuthorize("hasPermission('AUTHOR', #importPatchRequest.branchPath)")
	public ResponseEntity<Void> createReleasePatchImportJob(@RequestBody ImportPatchCreationRequest importPatchRequest) {
		ControllerHelper.requiredParam(importPatchRequest.getType(), "type");
		ControllerHelper.requiredParam(importPatchRequest.getBranchPath(), "branchPath");
		ControllerHelper.requiredParam(importPatchRequest.getPatchReleaseVersion(), "patchReleaseVersion");

		RF2ImportConfiguration importConfiguration = new RF2ImportConfiguration(importPatchRequest.getType(), importPatchRequest.getBranchPath());
		importConfiguration.setPatchReleaseVersion(importPatchRequest.getPatchReleaseVersion());
		String id = importService.createJob(importConfiguration);
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setLocation(ServletUriComponentsBuilder.fromCurrentContextPath().path("/imports/{id}")
				.buildAndExpand(id).toUri());
		return new ResponseEntity<>(httpHeaders, HttpStatus.CREATED);
	}

	@ApiOperation(value = "Retrieve an import job.",
			notes = "Retrieves the latest state of an import job. Used to view the import configuration and check its status.")
	@RequestMapping(value = "/{importId}", method = RequestMethod.GET)
	public ImportJob getImportJob(@PathVariable String importId) {
		return importService.getImportJobOrThrow(importId);
	}

	@ApiOperation(value = "Upload SNOMED CT release archive.",
			notes = "Uploads a SNOMED CT RF2 release archive for an import job. The import job must already exist and have a status of WAITING_FOR_FILE. " +
					"PLEASE NOTE this is an asynchronous call, this function starts the import but does not wait for it to complete. " +
					"Retrieve the import to check the status until it is COMPLETED or FAILED.")
	@RequestMapping(value = "/{importId}/archive", method = RequestMethod.POST, consumes = "multipart/form-data")
	public void uploadImportRf2Archive(@PathVariable String importId, @RequestParam MultipartFile file) {
		ImportJob importJob = importService.getImportJobOrThrow(importId);
		try {
			importService.importArchiveAsync(importId, importJob.getBranchPath(), file.getInputStream());
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to open uploaded archive file.");
		}
	}

}

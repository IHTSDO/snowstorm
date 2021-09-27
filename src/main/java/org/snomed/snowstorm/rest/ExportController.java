package org.snomed.snowstorm.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.jobs.ExportConfiguration;
import org.snomed.snowstorm.core.data.services.ModuleDependencyService;
import org.snomed.snowstorm.core.rf2.export.ExportService;
import org.snomed.snowstorm.rest.pojo.ExportRequestView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonView;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Set;

@RestController
@Api(tags = "Export", description = "RF2")
@RequestMapping(value = "/exports", produces = "application/json")
public class ExportController {

	@Autowired
	private ExportService exportService;
	
	@Autowired
	private ModuleDependencyService moduleDependencyService;

	@ApiOperation(value = "Create an export job.",
			notes = "Create a job to export an RF2 archive. " +
					"The 'location' response header contain the URL, including the identifier, of the new resource.")
	@RequestMapping(method = RequestMethod.POST)
	public ResponseEntity<Void> createExportJob(@Valid @RequestBody ExportRequestView exportConfiguration) {
		String id = exportService.createJob(exportConfiguration);
		return ControllerHelper.getCreatedResponse(id);
	}

	@ApiOperation(value = "Retrieve an export job.")
	@RequestMapping(value = "/{exportId}", method = RequestMethod.GET)
	public ExportConfiguration getExportJob(@PathVariable String exportId) {
		return exportService.getExportJobOrThrow(exportId);
	}

	@ApiOperation(value = "Download the RF2 archive from an export job.",
			notes = "NOT SUPPORTED IN SWAGGER UI. Instead open the URL in a new browser tab or make a GET request another way. " +
					"This endpoint can only be called once per exportId.")
	@RequestMapping(value = "/{exportId}/archive", method = RequestMethod.GET, produces="application/zip")
	public void downloadRf2Archive(@PathVariable String exportId, HttpServletResponse response) throws IOException {
		ExportConfiguration exportConfiguration = exportService.getExportJobOrThrow(exportId);

		String filename = exportService.getFilename(exportConfiguration);
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
		exportService.exportRF2Archive(exportConfiguration, response.getOutputStream());
	}
	
	@ApiOperation(value = "View a preview of the module dependency refset that would be generated for export")
	@RequestMapping(value = "/module-dependency-preview", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public List<ReferenceSetMember> generateModuleDependencyPreview (
			@RequestParam String branchPath,
			@RequestParam String effectiveDate,
			@RequestParam(required = false) Set<String> moduleFilter) {
		return moduleDependencyService.generateModuleDependencies(branchPath, effectiveDate, moduleFilter, false);
	}

}

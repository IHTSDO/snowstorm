package org.snomed.snowstorm.rest;

import io.swagger.annotations.Api;
import org.snomed.snowstorm.core.data.domain.jobs.ExportConfiguration;
import org.snomed.snowstorm.core.rf2.export.ExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;

@RestController
@Api(tags = "Export", description = "RF2")
@RequestMapping(value = "/exports", produces = "application/json")
public class ExportController {

	@Autowired
	private ExportService exportService;

	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Void> createExportJob(@Valid @RequestBody ExportConfiguration exportConfiguration) {
		String id = exportService.createJob(exportConfiguration);
		return ControllerHelper.getCreatedResponse(id);
	}

	@RequestMapping(value = "/{exportId}", method = RequestMethod.GET)
	public ExportConfiguration getExportJob(@PathVariable String exportId) {
		return exportService.getExportJobOrThrow(exportId);
	}

	@RequestMapping(value = "/{exportId}/archive", method = RequestMethod.GET, produces="application/zip")
	public void downloadRf2Archive(@PathVariable String exportId, HttpServletResponse response) throws IOException {
		ExportConfiguration exportConfiguration = exportService.getExportJobOrThrow(exportId);

		String filename = exportService.getFilename(exportConfiguration);
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
		exportService.exportRF2Archive(exportConfiguration, response.getOutputStream());
	}

}

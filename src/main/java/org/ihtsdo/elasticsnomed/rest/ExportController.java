package org.ihtsdo.elasticsnomed.rest;

import org.ihtsdo.elasticsnomed.core.data.domain.jobs.ExportConfiguration;
import org.ihtsdo.elasticsnomed.core.rf2.export.ExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;

import static org.ihtsdo.elasticsnomed.rest.ControllerHelper.requiredParam;

@RestController
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

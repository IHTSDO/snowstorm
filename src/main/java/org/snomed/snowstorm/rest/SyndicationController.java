package org.snomed.snowstorm.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.snowstorm.syndication.InstallationTask;
import org.snomed.snowstorm.syndication.SyndicationService;
import org.snomed.snowstorm.syndication.SyndicationDerivativeOption;
import org.snomed.snowstorm.syndication.SyndicationSnomedEdition;
import org.snomed.snowstorm.syndication.dto.InstallEditionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@Tag(name = "Syndication", description = "-")
@RequestMapping(value = "/syndication", produces = "application/json")
public class SyndicationController {

	private final SyndicationService syndicationService;

	public SyndicationController(SyndicationService syndicationService) {
		this.syndicationService = syndicationService;
	}

	@GetMapping("snomed-editions")
	public List<SyndicationSnomedEdition> getSnomedEditions() throws IOException {
		return syndicationService.getSnomedEditions();
	}

	@GetMapping("edition-derivatives")
	public ResponseEntity<List<SyndicationDerivativeOption>> listEditionDerivatives(
			@RequestParam String editionId,
			@RequestParam String version) throws IOException {
		if (editionId == null || editionId.isBlank() || version == null || !version.matches("\\d{8}")) {
			return ResponseEntity.badRequest().build();
		}
		try {
			return ResponseEntity.ok(syndicationService.listRefsetDerivatives(editionId, version));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().build();
		}
	}

	@Operation(summary = "List active syndication installations", description = "Installation tasks with status PENDING or IN_PROGRESS (used to resume dashboard UI after refresh).")
	@GetMapping("active-installations")
	public List<InstallationTask> listActiveInstallations() {
		return syndicationService.getActiveInstallationTasks();
	}

	@Operation(summary = "Install a SNOMED CT edition", description = "Queues an installation task to download and import a SNOMED CT edition from the syndication feed")
	@PostMapping("install")
	public ResponseEntity<InstallationTask> installEdition(@RequestBody InstallEditionRequest request) {
		if (request.getEditionId() == null || request.getVersion() == null || !request.getVersion().matches("\\d{8}")) {
			return ResponseEntity.badRequest().build();
		}
		String taskId = syndicationService.installEdition(
				request.getEditionId(), request.getVersion(), request.getDerivativeContentItemVersions());
		InstallationTask task = syndicationService.getInstallationTask(taskId);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(task);
	}

	@Operation(summary = "Get installation task status", description = "Retrieves the status of an installation task")
	@GetMapping("install/{taskId}")
	public ResponseEntity<InstallationTask> getInstallationTask(@PathVariable String taskId) {
		InstallationTask task = syndicationService.getInstallationTask(taskId);
		if (task == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(task);
	}

}

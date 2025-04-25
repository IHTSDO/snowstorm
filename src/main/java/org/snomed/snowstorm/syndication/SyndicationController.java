package org.snomed.snowstorm.syndication;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.models.requestDto.SyndicationImportRequest;
import org.snomed.snowstorm.syndication.models.data.SyndicationImport;
import org.snomed.snowstorm.syndication.services.importstatus.SyndicationImportStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@Tag(name = "Syndication", description = "-")
@RequestMapping(value = "/syndication", produces = "application/json")
public class SyndicationController {

    @Autowired
    private SyndicationImportStatusService importStatusService;

    @Value("${SYNDICATION_SECRET:empty}")
    private String syndicationSecret;

    @GetMapping(value = "/status")
    public List<SyndicationImport> getSyndicationStatuses() {
        return importStatusService.getAllImportStatuses();
    }

    @Operation(
            summary = "Start a new syndication import job",
            description = """
        This endpoint imports a terminology or updates its version.

        Supported terminologies:
        - **snomed**
        - **loinc**
        - **hl7**

        **Request parameters (SyndicationImportRequest):**
        - `terminologyName`: Name of the terminology to import. Valid values: `snomed`, `loinc`, `hl7`.
        - `version`: Version of the terminology to import. Valid values per terminology:
            - **LOINC**: `latest`, `local`, `2.80`, `2.79`, `2.78`, ...
            - **HL7**: `latest`, `local`, `6.2.0`, `6.1.0`, ...
            - **SNOMED**: `local`, `http://snomed.info/sct/11000172109/`, `http://snomed.info/sct/11000172109/version/20250315`, ...
        - `extensionName` _(optional)_: Specific to SNOMED, e.g. `"BE"` for the Belgian extension.
        """
    )
    @PutMapping(value = "/import")
    public ResponseEntity<String> updateTerminology(@RequestBody SyndicationImportRequest request) throws IOException, InterruptedException, ServiceException {
        if (!syndicationSecret.equals(request.syndicationSecret())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid syndication secret");
        }
        if (importStatusService.isImportRunning()) {
            return ResponseEntity.badRequest().body("An import process is still running");
        }
        boolean alreadyImported = importStatusService.updateTerminology(request);
        return ResponseEntity.ok(alreadyImported ? "The specified terminology version has already been imported" : "Update started");
    }
}

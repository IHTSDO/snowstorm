package org.snomed.snowstorm.syndication.importstatus;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.snowstorm.syndication.data.SyndicationImport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Syndication", description = "-")
@RequestMapping(value = "/syndication", produces = "application/json")
public class SyndicationController {

    @Autowired
    private SyndicationImportService importStatusService;

    @GetMapping(value = "/status")
    public List<SyndicationImport> getSyndicationStatuses() {
        return importStatusService.getAllImportStatuses();
    }
}

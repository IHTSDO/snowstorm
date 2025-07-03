package org.snomed.snowstorm.release;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "release.import.enabled", havingValue = "true")
public class ScheduledReleaseImportService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private boolean initialised = false;

    private volatile boolean running;

    private final CodeSystemService codeSystemService;

    private final ReleaseImportService releaseImportService;

    @PostConstruct
    public void init() {
        initialised = true;
        logger.info("Release import is enabled.");
    }


    public ScheduledReleaseImportService(CodeSystemService codeSystemService, ReleaseImportService releaseImportService) {
        this.codeSystemService = codeSystemService;
        this.releaseImportService = releaseImportService;
    }

    @Scheduled(fixedDelayString = "${release.import.schedule}", initialDelay = 60_000)
    public void scheduledReleaseImport() {
        if (!initialised) {
            return;
        }
        logger.info("Start release import...");
        if (!running) {
            synchronized (this) {
                if (!running) {
                    running = true;
                    try {
                        List<CodeSystem> codeSystems = codeSystemService.findAll();
                        for (CodeSystem codeSystem : codeSystems) {
                            try {
                                releaseImportService.performScheduledImport(codeSystem);
                            } catch (Exception e) {
                                logger.error("Failed to import the release package for code system {}", codeSystem.getShortName(), e);
                            }
                        }
                    } finally {
                        running = false;
                    }
                }
            }
        } else {
            logger.info("Release import is already running.");
        }
    }
}

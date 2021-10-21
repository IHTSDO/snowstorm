package org.snomed.snowstorm.dailybuild;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "daily-build.delta-import.enabled", havingValue = "true")
public class ScheduledDailyBuildImportService {

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private DailyBuildService dailyBuildService;

	private boolean initialised = false;

	private static int SCHEDULER_LOGGING_INTERVAL_IN_MINUTES = 30;

	private boolean running;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private Instant lastLogged;

	@PostConstruct
	public void init() {
		initialised = true;
		logger.info("Daily build import is enabled.");
	}

	@Scheduled(fixedDelayString = "${daily-build.delta-import.schedule}", initialDelay = 30_000)
	public void scheduledDailyBuildDeltaImport() {
		if (!initialised) {
			return;
		}
		if (lastLogged == null || Duration.between(lastLogged, Instant.now()).toMinutes() >= SCHEDULER_LOGGING_INTERVAL_IN_MINUTES) {
			logger.info("Start scheduled daily build import...");
			lastLogged = Instant.now();
		}

		if (!running) {
			synchronized (this) {
				if (!running) {
					running = true;
					try {
						List<CodeSystem> codeSystems = codeSystemService.findAll().stream()
								.filter(CodeSystem::isDailyBuildAvailable).collect(Collectors.toList());
						for (CodeSystem codeSystem : codeSystems) {
							try {
								dailyBuildService.performScheduledImport(codeSystem);
							} catch (Exception e) {
								logger.error("Failed to import daily build for code system {}", codeSystem.getShortName(), e);
							}
						}
					} finally {
						running = false;
					}
				}
			}
		} else {
			logger.info("Daily build import is already running.");
		}
	}
}

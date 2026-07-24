package org.snomed.snowstorm.syndication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.snomed.snowstorm.syndication.client.SyndicationClient;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyndicationServiceQueueTest {

	@Mock
	private SyndicationClient syndicationClient;

	@Mock
	private ImportService importService;

	@Mock
	private CodeSystemService codeSystemService;

	private SyndicationService syndicationService;

	@BeforeEach
	void setUp() {
		syndicationService = new SyndicationService(syndicationClient, importService, codeSystemService);
	}

	@Test
	void concurrentInstallsDoNotLeavePendingTasks() throws Exception {
		when(syndicationClient.getFeed()).thenThrow(new IOException("syndication unavailable"));

		int taskCount = 40;
		ExecutorService callers = Executors.newFixedThreadPool(8);
		List<Future<String>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < taskCount; i++) {
				futures.add(callers.submit(() -> syndicationService.installEdition(
						"http://snomed.info/sct/900000000000207008",
						"20250301",
						null,
						"user",
						"secret")));
			}
			List<String> taskIds = new ArrayList<>();
			for (Future<String> future : futures) {
				taskIds.add(future.get(30, TimeUnit.SECONDS));
			}

			assertTrue(awaitTerminal(taskIds, 30, TimeUnit.SECONDS),
					"all queued installs should reach a terminal status (lost-wakeup would leave PENDING)");

			for (String taskId : taskIds) {
				InstallationTask task = syndicationService.getInstallationTask(taskId);
				assertNotNull(task);
				assertEquals(InstallationTask.InstallationStatus.FAILED, task.getStatus());
				assertNull(task.getUsername());
				assertNull(task.getPassword());
				assertNull(task.getSecurityContext());
			}
		} finally {
			callers.shutdownNow();
		}
	}

	@Test
	void purgeFinishedTasksEvictsStaleTerminalTasks() throws Exception {
		when(syndicationClient.getFeed()).thenThrow(new IOException("syndication unavailable"));

		String taskId = syndicationService.installEdition(
				"http://snomed.info/sct/900000000000207008",
				"20250301",
				null,
				"user",
				"secret");

		assertTrue(awaitTerminal(List.of(taskId), 10, TimeUnit.SECONDS));
		InstallationTask task = syndicationService.getInstallationTask(taskId);
		assertNotNull(task);
		assertEquals(InstallationTask.InstallationStatus.FAILED, task.getStatus());

		task.setCompletedAt(new Date(System.currentTimeMillis() - SyndicationService.COMPLETED_TASK_RETENTION_MS - 1_000L));
		syndicationService.purgeFinishedTasks();

		assertNull(syndicationService.getInstallationTask(taskId));
	}

	@Test
	void clearSensitiveDataRemovesCredentials() {
		InstallationTask task = new InstallationTask(
				"http://snomed.info/sct/900000000000207008",
				"20250301",
				null,
				"user",
				"secret",
				SecurityContextHolder.getContext());
		assertEquals("user", task.getUsername());
		assertEquals("secret", task.getPassword());
		assertNotNull(task.getSecurityContext());

		task.clearSensitiveData();

		assertNull(task.getUsername());
		assertNull(task.getPassword());
		assertNull(task.getSecurityContext());
	}

	private boolean awaitTerminal(List<String> taskIds, long timeout, TimeUnit unit) throws InterruptedException {
		long deadline = System.nanoTime() + unit.toNanos(timeout);
		while (System.nanoTime() < deadline) {
			boolean allDone = true;
			for (String taskId : taskIds) {
				InstallationTask task = syndicationService.getInstallationTask(taskId);
				if (task == null) {
					allDone = false;
					break;
				}
				InstallationTask.InstallationStatus status = task.getStatus();
				if (status != InstallationTask.InstallationStatus.COMPLETED
						&& status != InstallationTask.InstallationStatus.FAILED) {
					allDone = false;
					break;
				}
			}
			if (allDone) {
				return true;
			}
			Thread.sleep(20);
		}
		return false;
	}
}

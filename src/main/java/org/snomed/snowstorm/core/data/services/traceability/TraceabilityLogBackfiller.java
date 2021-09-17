package org.snomed.snowstorm.core.data.services.traceability;

import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.repositories.CodeSystemVersionRepository;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class TraceabilityLogBackfiller {

	@Autowired
	private TraceabilityLogService traceabilityLogService;

	@Autowired
	private CodeSystemVersionRepository codeSystemVersionRepository;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void run() {
		logger.info("Starting Traceability Backfilling process...");

		// Send the commit date of all code system versions to the traceability service.
		final Page<CodeSystemVersion> allVersions = codeSystemVersionRepository.findAll(PageRequest.of(0, 1000, Sort.by("importDate")));
		logger.info("Sending {} code system version dates to traceability.", allVersions.getTotalElements());
		for (CodeSystemVersion codeSystemVersion : allVersions) {
			final Commit commit = new DummyCommitWithSpecificTimepoint(codeSystemVersion.getParentBranchPath(), Commit.CommitType.CONTENT, codeSystemVersion.getImportDate());
			traceabilityLogService.logActivity("snowstorm", commit, new PersistedComponents(), Activity.ActivityType.CREATE_CODE_SYSTEM_VERSION);
		}

		logger.info("Completed Traceability Backfilling process.");
	}

	private static final class DummyCommitWithSpecificTimepoint extends Commit {

		private final Date timepoint;

		public DummyCommitWithSpecificTimepoint(String branchPath, CommitType commitType, Date timepoint) {
			super(new Branch(branchPath), commitType, null, null);
			this.timepoint = timepoint;
		}

		@Override
		public Date getTimepoint() {
			return timepoint;
		}
	}
}

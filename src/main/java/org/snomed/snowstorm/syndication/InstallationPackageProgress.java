package org.snomed.snowstorm.syndication;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Tracks UI-facing progress for one RF2 package in a syndication install (download + time-based import estimate).
 */
public class InstallationPackageProgress {

	public static final String PHASE_PENDING = "PENDING";
	public static final String PHASE_DOWNLOADING = "DOWNLOADING";
	public static final String PHASE_WAITING_IMPORT = "WAITING_IMPORT";
	public static final String PHASE_IMPORTING = "IMPORTING";
	public static final String PHASE_DONE = "DONE";

	private final String contentItemVersion;
	private final String title;
	private final long declaredSizeBytes;

	private volatile String phase = PHASE_PENDING;
	private volatile int downloadPercent;
	private volatile int installCompletePercent;
	private volatile long importStartedAtMillis;
	private volatile long estimatedImportDurationMillis;

	public InstallationPackageProgress(String contentItemVersion, String title, long declaredSizeBytes) {
		this.contentItemVersion = contentItemVersion != null ? contentItemVersion : "";
		this.title = title != null ? title : "";
		this.declaredSizeBytes = declaredSizeBytes;
	}

	public String getContentItemVersion() {
		return contentItemVersion;
	}

	public String getTitle() {
		return title;
	}

	public long getDeclaredSizeBytes() {
		return declaredSizeBytes;
	}

	public String getPhase() {
		return phase;
	}

	public void setPhase(String phase) {
		this.phase = phase;
	}

	public int getDownloadPercent() {
		return downloadPercent;
	}

	public void setDownloadPercent(int downloadPercent) {
		this.downloadPercent = Math.min(100, Math.max(0, downloadPercent));
	}

	/**
	 * Time-based estimate while {@link #PHASE_IMPORTING}, capped until import completes.
	 */
	public int getInstallPercent() {
		if (PHASE_DONE.equals(phase) || installCompletePercent >= 100) {
			return 100;
		}
		if (!PHASE_IMPORTING.equals(phase)) {
			return Math.min(installCompletePercent, 100);
		}
		long start = importStartedAtMillis;
		if (start <= 0) {
			return 0;
		}
		long elapsed = System.currentTimeMillis() - start;
		long est = estimatedImportDurationMillis;
		if (est <= 0) {
			est = 1;
		}
		return (int) Math.min(99, (elapsed * 100 / est));
	}

	@JsonIgnore
	public long getImportStartedAtMillis() {
		return importStartedAtMillis;
	}

	public void beginImportEstimate(long estimatedImportDurationMillis) {
		this.estimatedImportDurationMillis = estimatedImportDurationMillis;
		this.importStartedAtMillis = System.currentTimeMillis();
		this.phase = PHASE_IMPORTING;
	}

	public void markImportComplete() {
		this.installCompletePercent = 100;
		this.phase = PHASE_DONE;
	}

	@JsonIgnore
	public long getEstimatedImportDurationMillis() {
		return estimatedImportDurationMillis;
	}
}

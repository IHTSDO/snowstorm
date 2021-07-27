package org.snomed.snowstorm.core.util;

import ch.qos.logback.classic.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.function.Supplier;

public class TimerUtil {

	private final String timerName;
	private final long start;
	private long lastCheck;
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final Level loggingLevel;
	private final float durationLoggingThreshold;
	private final TimerUtil childTimer;

	public TimerUtil(String timerName) {
		this(timerName, Level.INFO);
	}

	public TimerUtil(String timerName, Level loggingLevel) {
		this(timerName, loggingLevel, 0);
	}

	/**
	 * Creates a timer which will make log entries at the specified logging level.
	 * Logs a message when the checkpoint or finish methods are called.
	 * A duration threshold can be used to suppress making a log entry if the number of seconds since
	 * the last checkpoint is too small.
	 *
	 * @param timerName Name of the timer - used in the log message.
	 * @param loggingLevel Level used to log messages.
	 * @param durationLoggingThreshold Minimum number of seconds required to make a log message.
	 */
	public TimerUtil(String timerName, Level loggingLevel, float durationLoggingThreshold) {
		this(timerName, loggingLevel, durationLoggingThreshold, null);
	}

	public TimerUtil(String timerName, Level loggingLevel, float durationLoggingThreshold, TimerUtil childTimer) {
		this.loggingLevel = loggingLevel;
		this.timerName = timerName;
		this.start = new Date().getTime();
		lastCheck = start;
		this.durationLoggingThreshold = durationLoggingThreshold;
		this.childTimer = childTimer;
	}

	public static String secondsSince(Date startTime) {
		return String.format("%,d", (new Date().getTime() - startTime.getTime()) / 1_000);
	}

	public void checkpoint(String name) {
		checkpoint(() -> name);
	}

	public void checkpoint(Supplier nameSupplier) {
		final long now = new Date().getTime();
		float secondsTaken = getDurationSeconds(lastCheck, now);
		lastCheck = now;
		if (secondsTaken >= durationLoggingThreshold) {
			log("Timer {}: {} took {} seconds", timerName, nameSupplier.get(), secondsTaken);
		}
		if (childTimer != null) {
			childTimer.checkpoint(nameSupplier);
		}
	}

	public float finish() {
		final long now = new Date().getTime();
		float secondsTaken = getDurationSeconds(start, now);
		if (secondsTaken >= durationLoggingThreshold) {
			log("Timer {}: total took {} seconds", timerName, secondsTaken);
		}
		if (childTimer != null) {
			childTimer.finish();
		}
		return secondsTaken;
	}

	public static float getDurationSeconds(long startMilliseconds, long endMilliseconds) {
		float millisTaken = endMilliseconds - startMilliseconds;
		return millisTaken / 1000f;
	}

	private void log(String s, Object... o) {
		switch (loggingLevel.toString()) {
			case "TRACE":
				logger.trace(s, o);
				break;
			case "DEBUG":
				logger.debug(s, o);
				break;
			case "WARN":
				logger.warn(s, o);
				break;
			case "ERROR":
				logger.error(s, o);
				break;
			case "OFF":
				break;
			default:
				logger.info(s, o);
		}
	}

}

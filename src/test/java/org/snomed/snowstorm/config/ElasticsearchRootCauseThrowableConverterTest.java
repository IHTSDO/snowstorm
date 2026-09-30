package org.snomed.snowstorm.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.config.ElasticsearchRootCauseThrowableConverter.ROOT_CAUSE_PREFIX;
import static org.snomed.snowstorm.core.util.ElasticsearchErrorTestData.*;

class ElasticsearchRootCauseThrowableConverterTest {

	private final LoggerContext loggerContext = new LoggerContext();

	@AfterEach
	void tearDown() {
		loggerContext.stop();
	}

	@Test
	void addsRootCauseAboveStackTrace() {
		String output = convert(new RuntimeException("Rebase failed", searchPhaseFailure()));

		String expectedLine = ROOT_CAUSE_PREFIX + "[query_shard_exception] " + ROOT_CAUSE_REASON;
		assertTrue(output.contains(expectedLine), output);
		assertEquals(output.indexOf(expectedLine), output.lastIndexOf(expectedLine), "Duplicate root causes should be logged once");
		assertTrue(output.indexOf(expectedLine) < output.indexOf("java.lang.RuntimeException: Rebase failed"), output);
		assertTrue(output.contains("all shards failed"), "Original stack trace should be kept: " + output);
	}

	@Test
	void leavesOtherExceptionsUnchanged() {
		ILoggingEvent event = event(new RuntimeException("plain"));
		ExtendedWhitespaceThrowableProxyConverter bootConverter = new ExtendedWhitespaceThrowableProxyConverter();
		bootConverter.setContext(loggerContext);
		bootConverter.start();

		assertEquals(bootConverter.convert(event), convert(event));
		assertFalse(convert(failureWithoutRootCause()).contains(ROOT_CAUSE_PREFIX));
		assertEquals("", convert(event(null)));
	}

	@Test
	void snowstormLogbackConfigUsesConverterInsteadOfBootDefault() throws Exception {
		// Boot publishes this as a system property, so logback.xml must still take precedence over it
		String previous = System.setProperty("LOG_EXCEPTION_CONVERSION_WORD", "%wEx");
		try {
			JoranConfigurator configurator = new JoranConfigurator();
			configurator.setContext(loggerContext);
			configurator.doConfigure(getClass().getResource("/logback.xml"));
		} finally {
			if (previous == null) {
				System.clearProperty("LOG_EXCEPTION_CONVERSION_WORD");
			} else {
				System.setProperty("LOG_EXCEPTION_CONVERSION_WORD", previous);
			}
		}

		OutputStreamAppender<ILoggingEvent> console = (OutputStreamAppender<ILoggingEvent>)
				loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE");
		byte[] encoded = ((PatternLayoutEncoder) console.getEncoder()).encode(event(searchPhaseFailure()));

		assertTrue(new String(encoded, StandardCharsets.UTF_8).contains(ROOT_CAUSE_PREFIX + "[query_shard_exception] " + ROOT_CAUSE_REASON));
	}

	private String convert(Throwable throwable) {
		return convert(event(throwable));
	}

	private String convert(ILoggingEvent event) {
		ElasticsearchRootCauseThrowableConverter converter = new ElasticsearchRootCauseThrowableConverter();
		converter.setContext(loggerContext);
		converter.start();
		return converter.convert(event);
	}

	private ILoggingEvent event(Throwable throwable) {
		return new LoggingEvent(getClass().getName(), loggerContext.getLogger(getClass()), Level.ERROR, "Something failed", throwable, null);
	}
}

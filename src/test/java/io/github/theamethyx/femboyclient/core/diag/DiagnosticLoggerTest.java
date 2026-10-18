package io.github.theamethyx.femboyclient.core.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class DiagnosticLoggerTest {
	@Test
	void repeatedMessagesAreCountedNotRepeated() {
		DiagnosticLogger log = new DiagnosticLogger();
		for (int i = 0; i < 50; i++) {
			log.error("Config", "Could not save", null);
		}

		log.info("Config", "Saved");
		List<DiagnosticLogger.Entry> entries = log.snapshot();

		assertEquals(3, entries.size(), entries.toString());
		assertEquals("Could not save", entries.get(0).message());
		assertTrue(entries.get(1).message().contains("repeated 49 more time"), entries.get(1).message());
		assertEquals("Saved", entries.get(2).message());
		assertEquals(1, log.errorCount(), "counted once");
	}

	@Test
	void differentMessagesAreAllKept() {
		DiagnosticLogger log = new DiagnosticLogger();
		log.info("A", "one");
		log.info("A", "two");
		log.info("B", "one");
		assertEquals(3, log.snapshot().size());
	}

	@Test
	void timingsAddUpTimeAndAllocations() {
		PerformanceMonitor monitor = new PerformanceMonitor();
		monitor.record("x/tick", 1_000, 64);
		monitor.record("x/tick", 3_000, 0);
		PerformanceMonitor.Timing timing = monitor.timing("x/tick");
		assertEquals(2, timing.calls());
		assertEquals(4_000, timing.totalNanos());
		assertEquals(64, timing.totalBytes());
	}
}

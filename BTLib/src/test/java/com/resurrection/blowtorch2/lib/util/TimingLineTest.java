package com.resurrection.blowtorch2.lib.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TimingLineTest {

	@Test
	public void formatKeepsTheClockAndTheKind() {
		String line = TimingLine.format("2026-10-10 19:45:01.123", 4200L, 7,
				"ui", "resume", "phase=enter awayMs=800");
		assertTrue(line.startsWith("2026-10-10 19:45:01.123 "));
		assertTrue(line.contains(" seq=7"));
		assertTrue(line.contains(" up=4200"));
		assertTrue(line.contains(" proc=ui"));
		assertTrue(line.contains(" kind=resume"));
		assertTrue(line.contains(" phase=enter awayMs=800"));
	}

	@Test
	public void plainTokenStaysBare() {
		assertEquals("world-a", TimingLine.token("world-a"));
		assertEquals("-", TimingLine.token(null));
		assertEquals("-", TimingLine.token(""));
	}

	@Test
	public void spacedNameIsQuoted() {
		assertEquals("\"world a\"", TimingLine.token("world a"));
		assertEquals("\"say \\\"hi\\\"\"", TimingLine.token("say \"hi\""));
	}
}

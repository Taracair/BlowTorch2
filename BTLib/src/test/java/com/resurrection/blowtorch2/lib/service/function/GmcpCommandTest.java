package com.resurrection.blowtorch2.lib.service.function;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.GmcpModuleRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Characterization for package-visible GMCP supports normalization
 * (full command needs Connection / Android).
 */
public class GmcpCommandTest {

	@Test
	public void normalizeSupportsPassthroughQuotedList() {
		String quoted = "\"Char 1\", \"Room 1\"";
		assertEquals(quoted, GmcpCommand.normalizeSupports(quoted));
	}

	@Test
	public void normalizeSupportsBareNamesAddsVersionOne() {
		String out = GmcpCommand.normalizeSupports("char room");
		assertEquals("\"char 1\", \"room 1\"", out);
	}

	@Test
	public void normalizeSupportsBareCommaSeparated() {
		String out = GmcpCommand.normalizeSupports("Core, Char.Login");
		assertEquals("\"Core 1\", \"Char.Login 1\"", out);
	}

	@Test
	public void normalizeSupportsEmptyFallsBackToDefault() {
		assertEquals(GmcpModuleRegistry.DEFAULT_SUPPORTS,
				GmcpCommand.normalizeSupports("   "));
	}

	@Test
	public void normalizeSupportsPreservesExplicitVersionOnBareToken() {
		// lastIndexOf space on a single token without quotes — rare path
		String out = GmcpCommand.normalizeSupports("Room");
		assertTrue(out.contains("\"Room 1\""));
	}
}

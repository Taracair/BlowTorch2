package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProbeCatalogueTest {

	@Test
	public void namesEveryProbe() {
		String cat = ProbeCommand.catalogue();
		assertTrue(cat.contains("connection"));
		assertTrue(cat.contains("bleed"));
		assertTrue(cat.contains("sensors"));
		assertTrue(cat.contains("truecolor"));
		assertTrue(cat.contains("osc8"));
		assertTrue(cat.contains("mxp"));
		assertTrue(cat.contains("protocols"));
	}

	@Test
	public void bareProbeIsTheListNotTheChunkDump() {
		String cat = ProbeCommand.catalogue();
		assertTrue(cat.contains("Type .probe to see this list"));
		assertFalse(cat.contains("Chunk probe has not been run"));
		assertFalse(cat.contains("plain .probe"));
		assertFalse(cat.contains("(or plain .probe)"));
	}

	@Test
	public void connectionBeforeLinesOrChunk() {
		String cat = ProbeCommand.catalogue();
		int connection = cat.indexOf("connection");
		assertTrue(connection >= 0);
		int lines = cat.indexOf("lines");
		if (lines >= 0) {
			assertTrue(connection < lines);
		}
		int chunk = indexOfIgnoreCase(cat, "chunk");
		if (chunk >= 0) {
			assertTrue(connection < chunk);
		}
	}

	private static int indexOfIgnoreCase(final String haystack, final String needle) {
		return haystack.toLowerCase(java.util.Locale.US)
				.indexOf(needle.toLowerCase(java.util.Locale.US));
	}
}

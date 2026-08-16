package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProbeTruecolorSampleTest {

	@Test
	public void sampleContainsSemicolonTruecolorAnd256() {
		String sample = ProbeCommand.truecolorSample();
		assertTrue(sample.contains("38;2;255;128;0"));
		assertTrue(sample.contains("38;5;"));
		assertTrue(sample.contains("38;2;255;0;0") || sample.contains("38;2;255;"));
	}
}

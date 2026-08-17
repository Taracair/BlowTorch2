package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProbeOsc8SampleTest {

	@Test
	public void sampleContainsBelAndDisplayNotUri() {
		String sample = ProbeCommand.osc8Sample();
		assertTrue(sample.contains("]8;;https://example.com/real-path"));
		assertTrue(sample.contains("click here"));
		assertTrue(sample.contains("javascript:alert(1)"));
		assertTrue(sample.contains("mailto:nobody@example.com"));
	}
}

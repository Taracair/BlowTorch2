package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProbeMxpSampleTest {

	@Test
	public void sampleIsAlreadyInterpreted() {
		byte[] sample = ProbeCommand.mxpSampleBytes();
		String s = new String(sample, java.nio.charset.StandardCharsets.UTF_8);
		assertTrue(s.contains("mxp-send:") || s.contains("mxp-menu:")
				|| s.contains("mxp-prompt:"));
		assertTrue(s.contains("The Main Temple"));
		assertTrue(s.contains("fountain"));
		assertFalse("raw tags must not reach the window", s.contains("<SEND"));
		assertFalse(s.contains("<Item>"));
	}

	@Test
	public void markupDefinesTempleElements() {
		String markup = ProbeCommand.mxpSampleMarkup();
		assertTrue(markup.contains("<!ELEMENT Ex"));
		assertTrue(markup.contains("&text;"));
		assertTrue(markup.contains("prompt"));
	}
}

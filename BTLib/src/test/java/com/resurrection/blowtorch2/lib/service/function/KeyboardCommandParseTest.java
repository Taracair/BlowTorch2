package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Guards .kb first-token parsing — no Connection, no binder. */
public class KeyboardCommandParseTest {

	@Test
	public void stepuAndStepdAreHistoryOps() {
		assertEquals("stepu", KeyboardCommand.operationOf(" stepu"));
		assertEquals("stepd", KeyboardCommand.operationOf("stepd"));
	}

	@Test
	public void prevAndNextAreNotOpsSoTheyDoNotStealPreview() {
		assertEquals("", KeyboardCommand.operationOf(" prev"));
		assertEquals("", KeyboardCommand.operationOf(" next"));
		assertEquals("", KeyboardCommand.operationOf("preview"));
	}

	@Test
	public void lineuAndLinedAreSeparateFromHistory() {
		assertEquals("lineu", KeyboardCommand.operationOf("lineu"));
		assertEquals("lined", KeyboardCommand.operationOf(" lined "));
	}

	@Test
	public void existingCaretOpsStillParse() {
		assertEquals("stepf", KeyboardCommand.operationOf("stepf"));
		assertEquals("stepb", KeyboardCommand.operationOf("stepb"));
		assertEquals("start", KeyboardCommand.operationOf("start"));
		assertEquals("end", KeyboardCommand.operationOf("end"));
	}
}

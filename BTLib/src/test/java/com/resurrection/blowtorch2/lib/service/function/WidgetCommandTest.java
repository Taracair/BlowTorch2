package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.gauge.WidgetCommandParser;

public class WidgetCommandTest {

	@Test
	public void commandNameAndAlias() {
		WidgetCommand cmd = new WidgetCommand();
		assertEquals("widget", cmd.commandName);
		assertEquals("gauge", WidgetCommand.ALIAS_NAME);
	}

	@Test
	public void usageMatchesParser() {
		assertEquals(WidgetCommandParser.usage(), WidgetCommand.usage());
		String u = WidgetCommand.usage();
		assertTrue(u.contains("list"));
		assertTrue(u.contains("add"));
		assertTrue(u.contains("source"));
		assertTrue(u.contains("set"));
		assertTrue(u.startsWith("Usage: .widget"));
	}
}

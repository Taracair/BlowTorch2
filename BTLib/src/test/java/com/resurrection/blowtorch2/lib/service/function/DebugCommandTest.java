package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DebugCommandTest {

	@Test
	public void bareAndRendererAreStatus() {
		assertEquals(DebugCommand.KIND_STATUS, DebugCommand.parse(null).kind);
		assertEquals(DebugCommand.KIND_STATUS, DebugCommand.parse("").kind);
		assertEquals(DebugCommand.KIND_STATUS, DebugCommand.parse("   ").kind);
		assertEquals(DebugCommand.KIND_STATUS, DebugCommand.parse("renderer").kind);
		assertEquals(DebugCommand.KIND_STATUS, DebugCommand.parse("Renderer").kind);
	}

	@Test
	public void showOnOffAndToggle() {
		DebugCommand.Parsed on = DebugCommand.parse("renderer show on");
		assertEquals(DebugCommand.KIND_SET, on.kind);
		assertEquals(DebugCommand.FLAG_ON, on.flag);
		assertEquals(DebugCommand.FLAG_OFF, DebugCommand.parse("renderer show off").flag);
		assertEquals(DebugCommand.FLAG_TOGGLE,
				DebugCommand.parse("renderer show toggle").flag);
		assertEquals(DebugCommand.FLAG_ON,
				DebugCommand.parse("  Renderer   Show   ON  ").flag);
	}

	@Test
	public void aTrailingWordIsUsage() {
		assertEquals(DebugCommand.KIND_USAGE, DebugCommand.parse("renderer show on please").kind);
		assertEquals(DebugCommand.KIND_USAGE, DebugCommand.parse("renderer show").kind);
		assertEquals(DebugCommand.KIND_USAGE, DebugCommand.parse("renderer on").kind);
		assertEquals(DebugCommand.KIND_USAGE, DebugCommand.parse("show on").kind);
		assertEquals(DebugCommand.KIND_USAGE, DebugCommand.parse("renderer show true").kind);
		assertEquals(DebugCommand.KIND_USAGE, DebugCommand.parse("renderer show 1").kind);
		assertEquals(DebugCommand.KIND_USAGE, DebugCommand.parse("foo").kind);
	}
}

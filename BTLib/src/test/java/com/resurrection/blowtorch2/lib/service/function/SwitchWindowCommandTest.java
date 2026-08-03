package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class SwitchWindowCommandTest {

	@Test
	public void formatOpenListSortsAndIndents() {
		List<String> open = Arrays.asList("Zebra", "Alpha", "Middle");
		assertEquals("  Alpha\n  Middle\n  Zebra",
				SwitchWindowCommand.formatOpenList(open));
	}

	@Test
	public void usageHintWithOpenSessionsNamesExample() {
		String hint = SwitchWindowCommand.usageHint(
				Arrays.asList("Discworld", "Aardwolf"));
		assertTrue(hint.contains("Discworld") || hint.contains("Aardwolf"));
		assertTrue(hint.contains("Example: .switch "));
		assertTrue(hint.contains("exact display name"));
	}

	@Test
	public void usageHintWhenNoneOpen() {
		String hint = SwitchWindowCommand.usageHint(Collections.<String>emptyList());
		assertTrue(hint.contains("Connect another world first"));
	}

	@Test
	public void usageMessageListsCurrent() {
		String msg = SwitchWindowCommand.usageMessage(
				Arrays.asList("World B", "World A"), "World A");
		assertTrue(msg.contains(".switch <display name>"));
		assertTrue(msg.contains("Open connections:"));
		assertTrue(msg.contains("  World A"));
		assertTrue(msg.contains("  World B"));
		assertTrue(msg.contains("Currently viewing: World A"));
	}
}

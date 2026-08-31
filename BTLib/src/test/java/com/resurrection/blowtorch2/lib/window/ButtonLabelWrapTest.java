package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ButtonLabelWrapTest {

	@Test
	public void wrapOffKeepsASingleLine() {
		assertFalse(ButtonLabelWrap.usesWrappedLayout(false, "LOOK NORTH"));
		assertFalse(ButtonLabelWrap.usesWrappedLayout(false, null));
		assertFalse(ButtonLabelWrap.usesWrappedLayout(false, ""));
	}

	@Test
	public void wrapOnUsesLayoutEvenWithoutABreak() {
		assertTrue(ButtonLabelWrap.usesWrappedLayout(true, "LOOK NORTH"));
		assertTrue(ButtonLabelWrap.usesWrappedLayout(true, null));
	}

	@Test
	public void aNewlineIsAHardBreakWithoutTheCheckbox() {
		assertTrue(ButtonLabelWrap.usesWrappedLayout(false, "LOOK\nNORTH"));
	}

	@Test
	public void aTypedBackslashNIsNotABreak() {
		assertFalse(ButtonLabelWrap.usesWrappedLayout(false, "LOOK\\nNORTH"));
		assertFalse(ButtonLabelWrap.usesWrappedLayout(false, "C:\\notes"));
	}
}

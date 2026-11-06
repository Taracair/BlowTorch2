package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The form records a hardware key. A virtual key stays with the text field. */
public class KeyboardShortcutCaptureTest {

	private static final int DOWN = 0;
	private static final int UP = 1;
	private static final int A = 29;
	private static final int ALT_RIGHT = 58;

	@Test
	public void anUnarmedKeyIsLeftAlone() {
		KeyboardShortcutCapture capture = new KeyboardShortcutCapture();
		assertFalse(capture.onKey(DOWN, A, 0, 30, 4));
		assertEquals(KeyboardShortcutCapture.NONE, capture.keyCode());
		assertEquals("nothing", capture.label());
	}

	@Test
	public void aVirtualKeyTypes() {
		KeyboardShortcutCapture capture = new KeyboardShortcutCapture();
		capture.arm();
		assertFalse(capture.onKey(DOWN, A, 0, 0, -1));
		assertTrue(capture.armed());
		assertEquals("Press a key", capture.label());
	}

	@Test
	public void rightAltARecordsTheAltBit() {
		KeyboardShortcutCapture capture = new KeyboardShortcutCapture();
		capture.arm();
		assertTrue(capture.onKey(DOWN, ALT_RIGHT, 0x22, 100, 4));
		assertTrue(capture.armed());
		assertTrue(capture.onKey(DOWN, A, 0x22, 30, 4));
		assertFalse(capture.armed());
		assertEquals(A, capture.keyCode());
		assertEquals(KeyboardShortcuts.MOD_ALT, capture.modifiers());
		assertEquals("Alt+A", capture.label());
	}

	@Test
	public void backLeavesTheForm() {
		KeyboardShortcutCapture capture = new KeyboardShortcutCapture();
		capture.arm();
		assertFalse(capture.onKey(DOWN, 4, 0, 158, 4));
		assertTrue(capture.armed());
		assertEquals(KeyboardShortcutCapture.NONE, capture.keyCode());
	}

	@Test
	public void aModifierAloneIsTheChord() {
		KeyboardShortcutCapture capture = new KeyboardShortcutCapture();
		capture.arm();
		assertTrue(capture.onKey(UP, ALT_RIGHT, 0, 100, 4));
		assertEquals(ALT_RIGHT, capture.keyCode());
		assertEquals(0, capture.modifiers());
		assertEquals("Alt", capture.label());
	}
}

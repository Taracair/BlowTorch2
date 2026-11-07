package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The form keeps listening until the row is dismissed. A virtual key is a chord only then. */
public class KeyboardShortcutCaptureTest {

	private static final int DOWN = 0;
	private static final int UP = 1;
	private static final int A = 29;
	private static final int SHIFT_LEFT = 59;
	private static final int ALT_RIGHT = 58;
	private static final int CTRL_LEFT = 113;
	private static final int DPAD_UP = 19;

	@Test
	public void anUnarmedKeyIsLeftAlone() {
		KeyboardShortcutCapture capture = new KeyboardShortcutCapture();
		assertFalse(capture.onKey(DOWN, A, 0, 30, 4));
		assertEquals(KeyboardShortcutCapture.NONE, capture.keyCode());
		assertEquals("nothing", capture.label());
	}

	@Test
	public void anUnarmedVirtualKeyTypes() {
		KeyboardShortcutCapture capture = new KeyboardShortcutCapture();
		assertFalse(capture.onKey(DOWN, A, 0, 0, -1));
		assertEquals(KeyboardShortcutCapture.NONE, capture.keyCode());
	}

	@Test
	public void listeningKeepsTheLatestKey() {
		KeyboardShortcutCapture capture = new KeyboardShortcutCapture();
		capture.arm();
		assertTrue(capture.onKey(DOWN, ALT_RIGHT, 0x22, 100, 4));
		assertTrue(capture.armed());
		assertEquals("Press a key: Alt", capture.label());
		assertTrue(capture.onKey(DOWN, A, 0x22, 30, 4));
		assertTrue(capture.armed());
		assertEquals(A, capture.keyCode());
		assertEquals(KeyboardShortcuts.MOD_ALT, capture.modifiers());
		assertEquals("Press a key: Alt+A", capture.label());
		capture.disarm();
		assertFalse(capture.armed());
		assertEquals("Alt+A", capture.label());
		assertEquals(A, capture.keyCode());
	}

	@Test
	public void shiftUpDoesNotEraseTheLetter() {
		KeyboardShortcutCapture capture = new KeyboardShortcutCapture();
		capture.arm();
		assertTrue(capture.onKey(DOWN, SHIFT_LEFT, 0, 42, 4));
		assertEquals(SHIFT_LEFT, capture.keyCode());
		assertTrue(capture.onKey(DOWN, A, KeyboardShortcuts.MOD_SHIFT, 0, -1));
		assertEquals(A, capture.keyCode());
		assertEquals(KeyboardShortcuts.MOD_SHIFT, capture.modifiers());
		assertTrue(capture.onKey(UP, SHIFT_LEFT, 0, 42, 4));
		assertEquals(A, capture.keyCode());
		assertEquals(KeyboardShortcuts.MOD_SHIFT, capture.modifiers());
		assertTrue(capture.armed());
		assertEquals("Press a key: Shift+A", capture.label());
	}

	@Test
	public void ctrlUpWithNoKeyDownStillRecordsTheArrow() {
		KeyboardShortcutCapture capture = new KeyboardShortcutCapture();
		capture.arm();
		assertTrue(capture.onKey(DOWN, CTRL_LEFT, 0, 29, 4));
		assertEquals(CTRL_LEFT, capture.keyCode());
		assertTrue(capture.onKey(UP, DPAD_UP, KeyboardShortcuts.MOD_CTRL, 103, 4));
		assertEquals(DPAD_UP, capture.keyCode());
		assertEquals(KeyboardShortcuts.MOD_CTRL, capture.modifiers());
		assertEquals("Press a key: Ctrl+Up", capture.label());
		assertTrue(capture.armed());
	}

	@Test
	public void aLaterKeyUpDoesNotDropTheModifierFromKeyDown() {
		KeyboardShortcutCapture capture = new KeyboardShortcutCapture();
		capture.arm();
		assertTrue(capture.onKey(DOWN, DPAD_UP, KeyboardShortcuts.MOD_CTRL, 103, 4));
		assertTrue(capture.onKey(UP, DPAD_UP, 0, 103, 4));
		assertEquals(DPAD_UP, capture.keyCode());
		assertEquals(KeyboardShortcuts.MOD_CTRL, capture.modifiers());
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
	public void aModifierAloneStaysTheChord() {
		KeyboardShortcutCapture capture = new KeyboardShortcutCapture();
		capture.arm();
		assertTrue(capture.onKey(UP, ALT_RIGHT, 0, 100, 4));
		assertEquals(ALT_RIGHT, capture.keyCode());
		assertEquals(0, capture.modifiers());
		assertTrue(capture.armed());
		assertEquals("Press a key: Alt", capture.label());
	}
}

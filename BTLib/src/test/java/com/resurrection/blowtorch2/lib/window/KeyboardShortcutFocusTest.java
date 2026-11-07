package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Expected results are the 6 Nov capture and the focus rule. Numpad 8's
 * second event is DPAD_UP scan 72 flags 0x408. The real arrow is scan 103
 * with no fallback flag. Numpad Enter's twin is ENTER scan 96, not a
 * direction key. Numpad 2 sometimes arrives as the arrow key-up only.
 */
public class KeyboardShortcutFocusTest {

	private static final int DOWN = KeyboardShortcutMatcher.ACTION_DOWN;
	private static final int UP = KeyboardShortcutMatcher.ACTION_UP;
	private static final int DPAD_UP = 19;
	private static final int DPAD_DOWN = 20;
	private static final int DPAD_LEFT = 21;
	private static final int DPAD_CENTER = 23;
	private static final int ENTER = 66;
	private static final int NUMPAD_8 = 152;
	private static final int SCAN_PAD8 = 72;
	private static final int SCAN_PAD2 = 80;
	private static final int SCAN_PAD5 = 76;
	private static final int SCAN_ARROW = 103;
	private static final int SCAN_PAD_ENTER = 96;
	/** FLAG_FALLBACK 0x400 plus FLAG_FROM_SYSTEM 0x8, as captured. */
	private static final int TWIN = 0x408;
	private static final int FROM_SYSTEM = 0x8;
	private static final int DEV = 11;

	@Test
	public void twinKeyDownDoesNotWalkAndAFocusedBarStillSeesTheKeyUp() {
		KeyboardShortcutFocus focus = new KeyboardShortcutFocus();
		assertTrue(focus.consume(DOWN, DPAD_UP, TWIN, SCAN_PAD8, DEV, 0, true));
		assertFalse(focus.focusBar());
		assertTrue(focus.consume(DOWN, DPAD_UP, TWIN, SCAN_PAD8, DEV, 1, false));
		assertFalse(focus.focusBar());
		assertFalse(focus.consume(UP, DPAD_UP, TWIN, SCAN_PAD8, DEV, 0, true));
		assertFalse(focus.focusBar());
	}

	@Test
	public void twinWhileTheBarIsUnfocusedMovesTheCursorAndDoesNotStepHistory() {
		KeyboardShortcutFocus focus = new KeyboardShortcutFocus();
		assertTrue(focus.consume(DOWN, DPAD_UP, TWIN, SCAN_PAD8, DEV, 0, false));
		assertTrue(focus.focusBar());
		assertTrue(focus.consume(UP, DPAD_UP, TWIN, SCAN_PAD8, DEV, 0, true));
		assertTrue(focus.focusBar());
		assertTrue(focus.consume(UP, DPAD_CENTER, TWIN, SCAN_PAD5, DEV, 0, false));
		assertTrue(focus.focusBar());
	}

	@Test
	public void unpairedArrowUpFollowsWhetherTheBarIsFocusedNow() {
		KeyboardShortcutFocus focused = new KeyboardShortcutFocus();
		assertFalse(focused.consume(UP, DPAD_DOWN, TWIN, SCAN_PAD2, DEV, 0, true));
		assertFalse(focused.focusBar());
		KeyboardShortcutFocus away = new KeyboardShortcutFocus();
		assertTrue(away.consume(UP, DPAD_DOWN, TWIN, SCAN_PAD2, DEV, 0, false));
		assertTrue(away.focusBar());
	}

	@Test
	public void aRealArrowTheEnterTwinAndCtrlLeftAreNotConsumed() {
		KeyboardShortcutFocus focus = new KeyboardShortcutFocus();
		assertFalse(focus.consume(DOWN, DPAD_UP, FROM_SYSTEM, SCAN_ARROW, DEV, 0, true));
		assertFalse(focus.consume(UP, DPAD_UP, FROM_SYSTEM, SCAN_ARROW, DEV, 0, true));
		assertFalse(focus.consume(DOWN, DPAD_LEFT, FROM_SYSTEM, SCAN_ARROW, DEV, 0, true));
		assertFalse(focus.consume(DOWN, ENTER, TWIN, SCAN_PAD_ENTER, DEV, 0, true));
		assertFalse(focus.consume(UP, ENTER, TWIN, SCAN_PAD_ENTER, DEV, 0, true));
		assertFalse(focus.consume(DOWN, NUMPAD_8, FROM_SYSTEM, SCAN_PAD8, DEV, 0, true));
		assertFalse(focus.consume(DOWN, DPAD_UP, TWIN, SCAN_PAD8, -1, 0, true));
		assertFalse(focus.consume(DOWN, DPAD_UP, TWIN, 0, DEV, 0, false));
		assertFalse(focus.focusBar());
	}
}

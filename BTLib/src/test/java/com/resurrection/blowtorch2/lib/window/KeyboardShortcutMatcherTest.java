package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.speedwalk.DirectionData;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcutMatcher.Key;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcutMatcher.Outcome;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcuts.Row;

/**
 * Expected results are the step-1 rules. Key numbers are the ones captured
 * from a hardware keyboard: right Alt+a is meta 0x22 and U+0105, Ctrl+Alt+a
 * is meta 0x3012 with no meta-unicode, numpad Enter is 160 scan 96.
 */
public class KeyboardShortcutMatcherTest {

	private static final int DOWN = KeyboardShortcutMatcher.ACTION_DOWN;
	private static final int UP = KeyboardShortcutMatcher.ACTION_UP;
	private static final int PRE = KeyboardShortcutMatcher.HOOK_PRE_IME;
	private static final int ACT = KeyboardShortcutMatcher.HOOK_ACTIVITY;
	private static final int A = 29;
	private static final int ENTER = 66;
	private static final int NUMPAD_ENTER = 160;
	private static final int NUMPAD_8 = 152;
	private static final int DPAD_UP = 19;
	private static final int ALT_LEFT = 57;
	private static final int DEV = 4;
	private static final int SCAN_A = 30;
	private static final int SCAN_PAD8 = 72;
	private static final int SCAN_PAD_ENTER = 96;
	private static final int SCAN_ENTER = 28;
	private static final int SCAN_ARROW = 103;
	/** FLAG_FALLBACK 0x400 plus FLAG_FROM_SYSTEM 0x8, as captured. */
	private static final int TWIN = 0x408;

	@Test
	public void delaysMatchTheButtonHoldAndTheShortcutRepeat() {
		assertEquals(0x400, KeyboardShortcutMatcher.FLAG_FALLBACK);
		assertEquals(450, KeyboardShortcutMatcher.HOLD_DELAY_MS);
		assertEquals(200, KeyboardShortcutMatcher.REPEAT_MS);
	}

	@Test
	public void masterOffConsumesNothingAndSendsNothing() {
		KeyboardShortcutMatcher matcher = ready(Row.numpadEnter());
		matcher.setShortcuts(KeyboardShortcuts.disabled(Row.numpadEnter()));
		Outcome down = matcher.onKey(key(DOWN, NUMPAD_ENTER, 0, 0, 0, SCAN_PAD_ENTER, 0, 0, DEV, PRE));
		assertFalse(down.consumed);
		assertFalse(down.sendInput);
		assertNull(down.send);
		Outcome twin = matcher.onKey(key(DOWN, ENTER, 0, 0, 0, SCAN_PAD_ENTER, TWIN, 0, DEV, PRE));
		assertFalse(twin.consumed);
		assertFalse(twin.sendInput);
		Outcome letter = matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE));
		assertFalse(letter.consumed);
		assertNull(letter.send);
	}

	@Test
	public void unboundKeyTypes() {
		KeyboardShortcutMatcher matcher = ready(Row.numpadEnter());
		Outcome down = matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE));
		assertFalse(down.consumed);
		assertNull(down.send);
		assertFalse(down.sendInput);
		Outcome repeat = matcher.onKey(letter(DOWN, 0, 'a', 'a', 1, PRE));
		assertFalse(repeat.consumed);
	}

	@Test
	public void boundTapSendsOnceAndIgnoresSystemRepeat() {
		KeyboardShortcutMatcher matcher = ready(line(A, 0, "look", ""));
		Outcome down = matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE));
		assertTrue(down.consumed);
		assertEquals("look", down.send);
		assertFalse(down.quiet);
		assertEquals(0, down.scheduleHoldMs);
		assertEquals(0, down.scheduleRepeatMs);
		Outcome repeat = matcher.onKey(letter(DOWN, 0, 'a', 'a', 1, PRE));
		assertTrue(repeat.consumed);
		assertNull(repeat.send);
		assertEquals(0, repeat.scheduleRepeatMs);
		Outcome up = matcher.onKey(letter(UP, 0, 'a', 'a', 0, PRE));
		assertTrue(up.consumed);
		assertNull(up.send);
	}

	@Test
	public void holdSendsTheTapBefore450AndTheHoldLineAt450() {
		KeyboardShortcutMatcher early = ready(line(A, 0, "tap", "held"));
		Outcome down = early.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE));
		assertTrue(down.consumed);
		assertNull(down.send);
		assertEquals(450, down.scheduleHoldMs);
		assertEquals(0, down.scheduleRepeatMs);
		assertTrue(down.timerToken > 0);
		Outcome up = early.onKey(letter(UP, 0, 'a', 'a', 0, PRE));
		assertTrue(up.consumed);
		assertEquals("tap", up.send);
		assertNull(early.onHoldFired(down.timerToken).send);

		KeyboardShortcutMatcher held = ready(line(A, 0, "tap", "held"));
		Outcome armed = held.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE));
		Outcome system = held.onKey(letter(DOWN, 0, 'a', 'a', 1, PRE));
		assertTrue(system.consumed);
		assertNull(system.send);
		Outcome fired = held.onHoldFired(armed.timerToken);
		assertEquals("held", fired.send);
		assertFalse(fired.quiet);
		assertNull(held.onHoldFired(armed.timerToken).send);
		Outcome release = held.onKey(letter(UP, 0, 'a', 'a', 0, PRE));
		assertTrue(release.consumed);
		assertNull(release.send);
	}

	@Test
	public void repeatSendsImmediatelyAndIgnoresSystemRepeat() {
		KeyboardShortcutMatcher matcher = ready(Row.of(A, 0, KeyboardShortcuts.ACTION_LINE,
				true, true, "go", ""));
		Outcome down = matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE));
		assertEquals("go", down.send);
		assertEquals(200, down.scheduleRepeatMs);
		assertEquals(0, down.scheduleHoldMs);
		Outcome system = matcher.onKey(letter(DOWN, 0, 'a', 'a', 3, ACT));
		assertTrue(system.consumed);
		assertNull(system.send);
		assertEquals(0, system.scheduleRepeatMs);
		Outcome tick = matcher.onRepeatFired(down.timerToken);
		assertEquals("go", tick.send);
		assertEquals(200, tick.scheduleRepeatMs);
		assertTrue(matcher.onKey(letter(UP, 0, 'a', 'a', 0, PRE)).consumed);
		assertNull(matcher.onRepeatFired(down.timerToken).send);
		assertEquals(0, matcher.onRepeatFired(down.timerToken).scheduleRepeatMs);
	}

	@Test
	public void modifierUpStopsRepeatAndHoldWithoutSendingTheTap() {
		KeyboardShortcutMatcher repeating = ready(Row.of(A, KeyboardShortcuts.MOD_ALT,
				KeyboardShortcuts.ACTION_LINE, true, true, "go", ""));
		Outcome down = repeating.onKey(letter(DOWN, 0x12, 'a', 0, 0, PRE));
		assertEquals("go", down.send);
		Outcome altUp = repeating.onKey(key(UP, ALT_LEFT, 0, 0, 0, 56, 0, 0, DEV, PRE));
		assertFalse(altUp.consumed);
		assertNull(altUp.send);
		assertNull(repeating.onRepeatFired(down.timerToken).send);
		Outcome letterUp = repeating.onKey(letter(UP, 0, 'a', 0, 0, PRE));
		assertTrue(letterUp.consumed);
		assertNull(letterUp.send);

		Row held = Row.of(A, KeyboardShortcuts.MOD_ALT,
				KeyboardShortcuts.ACTION_LINE, true, true, "tap", "held");
		assertFalse(held.repeat);
		KeyboardShortcutMatcher holding = ready(held);
		Outcome armed = holding.onKey(letter(DOWN, 0x22, 'a', 0x105, 0, PRE));
		assertNull(armed.send);
		assertEquals(450, armed.scheduleHoldMs);
		holding.onKey(key(UP, ALT_LEFT, 0, 0, 0, 56, 0, 0, DEV, PRE));
		assertNull(holding.onHoldFired(armed.timerToken).send);
		Outcome up = holding.onKey(letter(UP, 0, 'a', 0, 0, PRE));
		assertNull(up.send);
		assertTrue(up.consumed);
	}

	@Test
	public void shiftUpDoesNotStopACtrlAltRepeat() {
		KeyboardShortcutMatcher matcher = ready(Row.of(A,
				KeyboardShortcuts.MOD_CTRL | KeyboardShortcuts.MOD_ALT,
				KeyboardShortcuts.ACTION_LINE, true, true, "go", ""));
		Outcome down = matcher.onKey(letter(DOWN, 0x3012, 'a', 0, 0, PRE));
		assertEquals("go", down.send);
		matcher.onKey(key(UP, 60, 0, 0, 0, 54, 0, 0, DEV, PRE));
		assertEquals("go", matcher.onRepeatFired(down.timerToken).send);
	}

	@Test
	public void masterOffTypingForcedDialogAndWalkingStopTheTimer() {
		assertStopped(new Stop() {
			@Override
			public void stop(KeyboardShortcutMatcher matcher) {
				matcher.setShortcuts(KeyboardShortcuts.empty());
			}
		});
		assertStopped(new Stop() {
			@Override
			public void stop(KeyboardShortcutMatcher matcher) {
				matcher.setTypingForced(true);
			}
		});
		assertStopped(new Stop() {
			@Override
			public void stop(KeyboardShortcutMatcher matcher) {
				matcher.setDialogOpen(true);
			}
		});
		assertStopped(new Stop() {
			@Override
			public void stop(KeyboardShortcutMatcher matcher) {
				matcher.setWalkingSuspended(true);
			}
		});
		assertStopped(new Stop() {
			@Override
			public void stop(KeyboardShortcutMatcher matcher) {
				matcher.setLatch(true);
				matcher.setLatch(false);
			}
		});
	}

	@Test
	public void dialogAndTypingForcedDoNotTakeTheKey() {
		KeyboardShortcutMatcher dialog = ready(line(A, 0, "look", ""));
		dialog.setDialogOpen(true);
		Outcome blocked = dialog.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE));
		assertFalse(blocked.consumed);
		assertNull(blocked.send);

		KeyboardShortcutMatcher masked = ready(Row.numpadEnter());
		masked.setTypingForced(true);
		Outcome enter = masked.onKey(padEnter(DOWN, 0, PRE));
		assertFalse(enter.consumed);
		assertFalse(enter.sendInput);
	}

	@Test
	public void walkingSuspendedStillMatchesANewKey() {
		KeyboardShortcutMatcher matcher = ready(Row.numpadEnter());
		matcher.setWalkingSuspended(true);
		Outcome enter = matcher.onKey(padEnter(DOWN, 0, PRE));
		assertTrue(enter.sendInput);
		assertTrue(enter.consumed);
	}

	@Test
	public void rightAltATypesUnlessThatChordIsBound() {
		KeyboardShortcutMatcher open = ready(Row.numpadEnter());
		Outcome typed = open.onKey(letter(DOWN, 0x22, 'a', 0x105, 0, PRE));
		assertFalse(typed.consumed);
		assertNull(typed.send);
		assertFalse(typed.sendInput);

		KeyboardShortcutMatcher bound = ready(Row.of(A, KeyboardShortcuts.MOD_ALT,
				KeyboardShortcuts.ACTION_LINE, true, false, "bound", ""));
		Outcome taken = bound.onKey(letter(DOWN, 0x22, 'a', 0x105, 0, PRE));
		assertTrue(taken.consumed);
		assertEquals("bound", taken.send);
	}

	@Test
	public void ctrlAltWithNoMetaUnicodeTypesUnlessThatChordIsBound() {
		KeyboardShortcutMatcher open = ready(Row.of(A, KeyboardShortcuts.MOD_ALT,
				KeyboardShortcuts.ACTION_LINE, true, false, "alt", ""));
		Outcome typed = open.onKey(letter(DOWN, 0x3012, 'a', 0, 0, PRE));
		assertFalse(typed.consumed);
		assertNull(typed.send);

		KeyboardShortcutMatcher bound = ready(Row.of(A,
				KeyboardShortcuts.MOD_CTRL | KeyboardShortcuts.MOD_ALT,
				KeyboardShortcuts.ACTION_LINE, true, false, "both", ""));
		Outcome taken = bound.onKey(letter(DOWN, 0x3012, 'a', 0, 0, PRE));
		assertTrue(taken.consumed);
		assertEquals("both", taken.send);
	}

	@Test
	public void leftAltAIsANormalAltChord() {
		KeyboardShortcutMatcher open = ready(Row.numpadEnter());
		Outcome typed = open.onKey(letter(DOWN, 0x12, 'a', 0, 0, PRE));
		assertFalse(typed.consumed);
		assertNull(typed.send);

		KeyboardShortcutMatcher bound = ready(Row.of(A, KeyboardShortcuts.MOD_ALT,
				KeyboardShortcuts.ACTION_LINE, true, false, "alt", ""));
		Outcome left = bound.onKey(letter(DOWN, 0x12, 'a', 0, 0, PRE));
		assertEquals("alt", left.send);
		Outcome right = bound.onKey(key(DOWN, A, 0x22, 'a', 0x105, SCAN_A, 0, 0, DEV + 1, PRE));
		assertEquals("alt", right.send);
	}

	@Test
	public void shiftLetterDoesNotMatchABareLetter() {
		KeyboardShortcutMatcher matcher = ready(line(A, 0, "look", ""));
		Outcome shifted = matcher.onKey(letter(DOWN, 0x81, 'A', 'A', 0, PRE));
		assertFalse(shifted.consumed);
		assertNull(shifted.send);
	}

	@Test
	public void onePressAtBothHooksSendsOnce() {
		KeyboardShortcutMatcher matcher = ready(Row.numpadEnter());
		Outcome first = matcher.onKey(padEnter(DOWN, 0, PRE));
		assertTrue(first.sendInput);
		Outcome second = matcher.onKey(padEnter(DOWN, 0, ACT));
		assertTrue(second.consumed);
		assertFalse(second.sendInput);
		assertNull(second.send);
		assertTrue(matcher.onKey(padEnter(UP, 0, PRE)).consumed);
		assertFalse(matcher.onKey(padEnter(UP, 0, ACT)).sendInput);
		Outcome again = matcher.onKey(padEnter(DOWN, 0, PRE));
		assertTrue(again.sendInput);
	}

	@Test
	public void aLaterDownFromTheSameHookIsANewPress() {
		KeyboardShortcutMatcher matcher = ready(line(A, 0, "look", ""));
		assertEquals("look", matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE)).send);
		assertNull(matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, ACT)).send);
		assertEquals("look", matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE)).send);
	}

	@Test
	public void consumedNumpadKeyAlsoConsumesTheSameScanFallback() {
		KeyboardShortcutMatcher matcher = ready(line(NUMPAD_8, 0, "n8", ""));
		Outcome pad = matcher.onKey(key(DOWN, NUMPAD_8, 0, 0, 0, SCAN_PAD8, 0, 0, DEV, PRE));
		assertEquals("n8", pad.send);
		Outcome twin = matcher.onKey(key(DOWN, DPAD_UP, 0, 0, 0, SCAN_PAD8, TWIN, 0, DEV, ACT));
		assertTrue(twin.consumed);
		assertNull(twin.send);
		assertFalse(twin.sendInput);
		Outcome twinUp = matcher.onKey(key(UP, DPAD_UP, 0, 0, 0, SCAN_PAD8, TWIN, 0, DEV, ACT));
		assertTrue(twinUp.consumed);
		assertNull(twinUp.send);
		Outcome real = matcher.onKey(key(DOWN, DPAD_UP, 0, 0, 0, SCAN_ARROW, 0, 0, DEV, ACT));
		assertFalse(real.consumed);
		assertNull(real.send);
	}

	@Test
	public void unconsumedNumpadKeyLeavesTheFallbackAlone() {
		KeyboardShortcutMatcher matcher = ready(Row.numpadEnter());
		Outcome pad = matcher.onKey(key(DOWN, NUMPAD_8, 0, 0, 0, SCAN_PAD8, 0, 0, DEV, PRE));
		assertFalse(pad.consumed);
		Outcome twin = matcher.onKey(key(DOWN, DPAD_UP, 0, 0, 0, SCAN_PAD8, TWIN, 0, DEV, ACT));
		assertFalse(twin.consumed);
		assertNull(twin.send);
	}

	@Test
	public void fallbackDoesNotFireABindingOfTheTwinKey() {
		KeyboardShortcutMatcher matcher = ready(line(DPAD_UP, 0, "up", ""));
		Outcome twin = matcher.onKey(key(DOWN, DPAD_UP, 0, 0, 0, SCAN_PAD8, TWIN, 0, DEV, PRE));
		assertFalse(twin.consumed);
		assertNull(twin.send);
		Outcome real = matcher.onKey(key(DOWN, DPAD_UP, 0, 0, 0, SCAN_ARROW, 0, 0, DEV, PRE));
		assertEquals("up", real.send);
	}

	@Test
	public void numpadEnterSendsOnceAndConsumesTheSameScanEnterTwin() {
		KeyboardShortcutMatcher matcher = ready(Row.numpadEnter());
		Outcome down = matcher.onKey(padEnter(DOWN, 0x200000, PRE));
		assertTrue(down.consumed);
		assertTrue(down.sendInput);
		assertNull(down.send);
		Outcome repeat = matcher.onKey(key(DOWN, NUMPAD_ENTER, 0x200000, 0, 0,
				SCAN_PAD_ENTER, 0, 1, DEV, PRE));
		assertTrue(repeat.consumed);
		assertFalse(repeat.sendInput);
		Outcome twin = matcher.onKey(key(DOWN, ENTER, 0, 0, 0, SCAN_PAD_ENTER, TWIN, 0, DEV, ACT));
		assertTrue(twin.consumed);
		assertFalse(twin.sendInput);
		assertNull(twin.send);
		Outcome main = matcher.onKey(key(DOWN, ENTER, 0, '\n', 0, SCAN_ENTER, 0, 0, DEV, ACT));
		assertFalse(main.consumed);
		assertFalse(main.sendInput);
		assertTrue(matcher.onKey(padEnter(UP, 0, PRE)).consumed);
		assertFalse(matcher.onKey(padEnter(UP, 0, PRE)).sendInput);
	}

	@Test
	public void softKeyboardFlagDoesNotDecide() {
		KeyboardShortcutMatcher matcher = ready(Row.numpadEnter());
		Outcome hardware = matcher.onKey(key(DOWN, NUMPAD_ENTER, 0, 0, 0,
				SCAN_PAD_ENTER, 0x2, 0, DEV, PRE));
		assertTrue(hardware.sendInput);
		Outcome zero = matcher.onKey(key(DOWN, NUMPAD_ENTER, 0, 0, 0,
				SCAN_PAD_ENTER, 0x2, 0, 0, ACT));
		assertTrue(zero.consumed);
		assertTrue(zero.sendInput);
	}

	@Test
	public void virtualKeyboardIsNotAShortcut() {
		KeyboardShortcutMatcher matcher = ready(line(A, 0, "look", ""));
		Outcome injected = matcher.onKey(key(DOWN, A, 0, 'a', 'a', SCAN_A, 0, 0,
				KeyboardShortcutMatcher.VIRTUAL_DEVICE_ID, PRE));
		assertFalse(injected.consumed);
		assertNull(injected.send);
		Outcome noScan = matcher.onKey(key(DOWN, A, 0, 'a', 'a', 0, 0, 0, DEV, PRE));
		assertFalse(noScan.consumed);
		Outcome pad = matcher.onKey(key(DOWN, NUMPAD_ENTER, 0, 0, 0, 0, 0x2, 0, -1, PRE));
		assertFalse(pad.consumed);
		assertFalse(pad.sendInput);
	}

	@Test
	public void disabledRowIsNotTaken() {
		KeyboardShortcutMatcher matcher = ready(Row.of(A, 0, KeyboardShortcuts.ACTION_LINE,
				false, false, "look", ""));
		Outcome down = matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE));
		assertFalse(down.consumed);
		assertNull(down.send);
	}

	@Test
	public void stepBackSendsTheSameReverseTwiceAndIgnoresLook() {
		KeyboardShortcutMatcher matcher = ready(Row.of(A, 0, KeyboardShortcuts.ACTION_STEP,
				true, false, "", ""));
		Outcome none = matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE));
		assertTrue(none.consumed);
		assertNull(none.send);
		assertFalse(none.sendInput);
		matcher.onKey(letter(UP, 0, 'a', 'a', 0, PRE));

		matcher.setReverse("north", "south");
		matcher.noteRoseWalk("go north");
		Outcome ignored = matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE));
		assertNull(ignored.send);
		matcher.onKey(letter(UP, 0, 'a', 'a', 0, PRE));

		matcher.noteRoseWalk("north");
		matcher.noteLook("in");
		assertEquals("south", matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE)).send);
		matcher.onKey(letter(UP, 0, 'a', 'a', 0, PRE));
		matcher.noteLook("east");
		assertEquals("south", matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE)).send);
		matcher.onKey(letter(UP, 0, 'a', 'a', 0, PRE));

		matcher.setReverse("north", " ");
		Outcome blank = matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE));
		assertTrue(blank.consumed);
		assertEquals("south", blank.send);
		matcher.onKey(letter(UP, 0, 'a', 'a', 0, PRE));
		matcher.noteRoseWalk("xyzzy");
		Outcome unknown = matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE));
		assertTrue(unknown.consumed);
		assertNull(unknown.send);
	}

	@Test
	public void walkMarkFollowsLatchAndMasterAndShortcutsOffFollowsHardware() {
		KeyboardShortcutMatcher matcher = new KeyboardShortcutMatcher();
		matcher.setHardwareKeyboard(true);
		assertEquals("Shortcuts off", matcher.mark());
		matcher.setShortcuts(KeyboardShortcuts.enabled(Row.numpadEnter()));
		assertNull(matcher.mark());
		matcher.setLatch(true);
		assertEquals("Walk", matcher.mark());
		matcher.setTypingForced(true);
		assertTrue(matcher.isLatchOn());
		assertNull(matcher.mark());
		matcher.setTypingForced(false);
		assertEquals("Walk", matcher.mark());
		matcher.setLatch(false);
		assertFalse(matcher.isLatchOn());
		assertNull(matcher.mark());
		matcher.setShortcuts(KeyboardShortcuts.disabled(Row.numpadEnter()));
		assertEquals("Shortcuts off", matcher.mark());
		matcher.setHardwareKeyboard(false);
		assertNull(matcher.mark());
	}

	@Test
	public void quietHoldStaysQuiet() {
		KeyboardShortcutMatcher held = ready(Row.of(A, 0, KeyboardShortcuts.ACTION_LINE,
				true, false, "tap", "held", "q", true));
		Outcome armed = held.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE));
		assertNull(armed.send);
		Outcome fired = held.onHoldFired(armed.timerToken);
		assertEquals("held", fired.send);
		assertTrue(fired.quiet);
	}

	@Test
	public void sendTheCurrentLineDoesNotKeepAHiddenHold() {
		Row row = Row.of(A, 0, KeyboardShortcuts.ACTION_INPUT, true, true, "north", "held", "", true);
		assertEquals("", row.tap);
		assertEquals("", row.hold);
		assertFalse(row.repeat);
		assertFalse(row.quiet);
		KeyboardShortcutMatcher matcher = ready(row);
		Outcome down = matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE));
		assertTrue(down.sendInput);
		assertEquals(0, down.scheduleHoldMs);
		assertEquals(0, down.scheduleRepeatMs);
		assertFalse(down.quiet);
	}

	@Test
	public void quietRowIsMarkedAndStillSends() {
		KeyboardShortcutMatcher matcher = ready(Row.of(A, 0, KeyboardShortcuts.ACTION_LINE,
				true, false, "north", "", "walk", true));
		Outcome down = matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE));
		assertTrue(down.consumed);
		assertEquals("north", down.send);
		assertTrue(down.quiet);
	}

	@Test
	public void latchWalksOnTheBareNumberAndTheModifierAloneTurnsItOff() {
		Row north = Row.of(NUMPAD_8, KeyboardShortcuts.MOD_ALT, KeyboardShortcuts.ACTION_LINE,
				true, false, "north", "", "", true);
		KeyboardShortcutMatcher matcher = new KeyboardShortcutMatcher();
		matcher.setShortcuts(KeyboardShortcuts.enabled()
				.withPad(Arrays.asList(north), KeyboardShortcuts.MOD_ALT, ALT_LEFT));
		matcher.onKey(key(DOWN, ALT_LEFT, KeyboardShortcuts.MOD_ALT, 0, 0, 56, 0, 0, DEV, PRE));
		Outcome latched = matcher.onKey(key(UP, ALT_LEFT, 0, 0, 0, 56, 0, 0, DEV, PRE));
		assertTrue(latched.consumed);
		assertTrue(matcher.isLatchOn());
		assertEquals("Walk", matcher.mark());
		Outcome walk = matcher.onKey(key(DOWN, NUMPAD_8, 0, 0, 0, SCAN_PAD8, 0, 0, DEV, PRE));
		assertEquals("north", walk.send);
		assertTrue(walk.quiet);
		matcher.onKey(key(UP, NUMPAD_8, 0, 0, 0, SCAN_PAD8, 0, 0, DEV, PRE));
		matcher.onKey(key(DOWN, ALT_LEFT, KeyboardShortcuts.MOD_ALT, 0, 0, 56, 0, 0, DEV, PRE));
		matcher.onKey(key(UP, ALT_LEFT, 0, 0, 0, 56, 0, 0, DEV, PRE));
		assertFalse(matcher.isLatchOn());
		assertNull(matcher.mark());
	}

	@Test
	public void padOptionsLetTheDigitTypeAndKeepNumpadEnter() {
		Row north = Row.of(NUMPAD_8, 0, KeyboardShortcuts.ACTION_LINE, true, false, "n", "");
		Row edited = Row.of(A, KeyboardShortcuts.MOD_CTRL, KeyboardShortcuts.ACTION_LINE,
				true, false, "select", "");
		KeyboardShortcutMatcher matcher = new KeyboardShortcutMatcher();
		matcher.setShortcuts(KeyboardShortcuts.enabled(Row.numpadEnter(), edited)
				.withPad(Arrays.asList(north), 0, -1));
		matcher.setPadPolicy(false, false, false, true);
		Outcome typed = matcher.onKey(key(DOWN, NUMPAD_8, 0, 0, 0, SCAN_PAD8, 0, 0, DEV, PRE));
		assertFalse(typed.consumed);
		matcher.onKey(key(UP, NUMPAD_8, 0, 0, 0, SCAN_PAD8, 0, 0, DEV, PRE));
		Outcome enter = matcher.onKey(padEnter(DOWN, 0, PRE));
		assertTrue(enter.sendInput);

		matcher.setPadPolicy(true, true, true, true);
		matcher.setBarIdle(false);
		Outcome editing = matcher.onKey(key(DOWN, NUMPAD_8, 0, 0, 0, SCAN_PAD8, 0, 0, DEV, PRE));
		assertFalse(editing.consumed);
		Outcome topWhileEditing = matcher.onKey(key(DOWN, 15, 0, 0, 0, 9, 0, 0, DEV, PRE));
		assertFalse(topWhileEditing.consumed);
		matcher.onKey(key(UP, 15, 0, 0, 0, 9, 0, 0, DEV, PRE));
		matcher.setBarIdle(true);
		Outcome row = matcher.onKey(key(DOWN, 15, 0, 0, 0, 9, 0, 0, DEV, PRE));
		assertEquals("n", row.send);
		matcher.onKey(key(UP, 15, 0, 0, 0, 9, 0, 0, DEV, PRE));

		Outcome kept = matcher.onKey(letter(DOWN, KeyboardShortcuts.MOD_CTRL, 0, 0, 0, PRE));
		assertFalse(kept.consumed);
		matcher.onKey(letter(UP, KeyboardShortcuts.MOD_CTRL, 0, 0, 0, PRE));
		matcher.setPadPolicy(true, false, false, false);
		Outcome taken = matcher.onKey(letter(DOWN, KeyboardShortcuts.MOD_CTRL, 0, 0, 0, PRE));
		assertEquals("select", taken.send);
	}

	@Test
	public void aPadWalkSetsTheStepAndLookDoesNot() {
		Row north = Row.of(NUMPAD_8, 0, KeyboardShortcuts.ACTION_LINE, true, false, "north", "");
		Row look = Row.of(149, 0, KeyboardShortcuts.ACTION_LINE, true, false, "look", "");
		Row layer = Row.of(NUMPAD_8, KeyboardShortcuts.MOD_ALT, KeyboardShortcuts.ACTION_LINE,
				true, false, "look north", "");
		Row step = Row.of(A, 0, KeyboardShortcuts.ACTION_STEP, true, false, "", "");
		KeyboardShortcutMatcher matcher = new KeyboardShortcutMatcher();
		matcher.setShortcuts(KeyboardShortcuts.enabled(step)
				.withPad(Arrays.asList(north, look, layer), 0, -1));
		matcher.onKey(key(DOWN, NUMPAD_8, 0, 0, 0, SCAN_PAD8, 0, 0, DEV, PRE));
		matcher.onKey(key(UP, NUMPAD_8, 0, 0, 0, SCAN_PAD8, 0, 0, DEV, PRE));
		assertEquals("south", matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE)).send);
		matcher.onKey(letter(UP, 0, 'a', 'a', 0, PRE));
		matcher.onKey(key(DOWN, 149, 0, 0, 0, 76, 0, 0, DEV, PRE));
		matcher.onKey(key(UP, 149, 0, 0, 0, 76, 0, 0, DEV, PRE));
		assertEquals("south", matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE)).send);
		matcher.onKey(letter(UP, 0, 'a', 'a', 0, PRE));
		matcher.onKey(key(DOWN, NUMPAD_8, KeyboardShortcuts.MOD_ALT, 0, 0, SCAN_PAD8, 0, 0, DEV, PRE));
		matcher.onKey(key(UP, NUMPAD_8, 0, 0, 0, SCAN_PAD8, 0, 0, DEV, PRE));
		assertEquals("south", matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE)).send);

		Map<String, DirectionData> map = new HashMap<String, DirectionData>();
		DirectionData data = new DirectionData("n", "north");
		data.setReverse("n");
		map.put("n", data);
		matcher.setDirections(map);
		matcher.setReverse("north", "back");
		matcher.onKey(letter(UP, 0, 'a', 'a', 0, PRE));
		assertEquals("back", matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE)).send);
	}

	private static void assertStopped(final Stop stop) {
		KeyboardShortcutMatcher matcher = ready(line(A, 0, "tap", "held"));
		Outcome armed = matcher.onKey(letter(DOWN, 0, 'a', 'a', 0, PRE));
		assertEquals(450, armed.scheduleHoldMs);
		stop.stop(matcher);
		assertNull(matcher.onHoldFired(armed.timerToken).send);
		Outcome up = matcher.onKey(letter(UP, 0, 'a', 'a', 0, PRE));
		assertNull(up.send);
	}

	private interface Stop {
		void stop(KeyboardShortcutMatcher matcher);
	}

	private static KeyboardShortcutMatcher ready(final Row row) {
		KeyboardShortcutMatcher matcher = new KeyboardShortcutMatcher();
		matcher.setShortcuts(KeyboardShortcuts.enabled(row));
		return matcher;
	}

	private static Row line(final int keyCode, final int modifiers, final String tap, final String hold) {
		return Row.of(keyCode, modifiers, KeyboardShortcuts.ACTION_LINE, true, false, tap, hold);
	}

	private static Key letter(final int action, final int meta, final int plain, final int metaUnicode,
			final int repeat, final int hook) {
		return key(action, A, meta, plain, metaUnicode, SCAN_A, 0, repeat, DEV, hook);
	}

	private static Key padEnter(final int action, final int meta, final int hook) {
		return key(action, NUMPAD_ENTER, meta, 0, 0, SCAN_PAD_ENTER, 0, 0, DEV, hook);
	}

	private static Key key(final int action, final int code, final int meta, final int plain,
			final int metaUnicode, final int scan, final int flags, final int repeat,
			final int device, final int hook) {
		return new Key(action, code, meta, plain, metaUnicode, scan, flags, repeat, device, hook);
	}
}

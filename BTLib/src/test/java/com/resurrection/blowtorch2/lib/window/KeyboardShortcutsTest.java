package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.window.KeyboardShortcuts.Change;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcuts.Row;

/** The saved switch. Expected rows come from the step-1 rules, not from a previous run. */
public class KeyboardShortcutsTest {

	@Test
	public void noticeIsTheThreeSentences() {
		assertEquals(
				"A hardware key follows the keyboard app you selected, the same app that shows the on-screen keys. "
						+ "The same key can type with one app and do something else with another. "
						+ "A shortcut is read before that app changes the key.",
				KeyboardShortcuts.NOTICE);
	}

	@Test
	public void emptyStoreIsOffAndIsNotWritten() {
		assertEquals("", KeyboardShortcuts.parse(null).format());
		assertEquals("", KeyboardShortcuts.parse("").format());
		assertEquals("", KeyboardShortcuts.parse("  ").format());
		assertFalse(KeyboardShortcuts.empty().isOn());
		assertFalse(KeyboardShortcuts.empty().isIntroduced());
		assertEquals(0, KeyboardShortcuts.empty().rows().size());
	}

	@Test
	public void firstOnSeedsOnlyNumpadEnterAndPrintsTheNotice() {
		Change change = KeyboardShortcuts.empty().apply("on");
		assertTrue(change.changed);
		assertTrue(change.printNotice);
		assertFalse(change.usage);
		assertTrue(change.next.isOn());
		assertTrue(change.next.isIntroduced());
		assertEquals(1, change.next.rows().size());
		Row row = change.next.rows().get(0);
		assertEquals(160, row.keyCode);
		assertEquals(0, row.modifiers);
		assertEquals(KeyboardShortcuts.ACTION_INPUT, row.action);
		assertTrue(row.enabled);
		assertFalse(row.repeat);
		assertEquals("", row.tap);
		assertEquals("", row.hold);
	}

	@Test
	public void secondOnDoesNotRepeatTheNoticeOrTheRow() {
		Change first = KeyboardShortcuts.empty().apply("on");
		Change again = first.next.apply("on");
		assertFalse(again.changed);
		assertFalse(again.printNotice);
		assertTrue(again.next.isOn());
		assertEquals(1, again.next.rows().size());
		assertEquals(160, again.next.rows().get(0).keyCode);
	}

	@Test
	public void offThenOnKeepsTheRowAndSkipsTheNotice() {
		Change first = KeyboardShortcuts.empty().apply("ON");
		Change off = first.next.apply("off");
		assertTrue(off.changed);
		assertFalse(off.next.isOn());
		assertTrue(off.next.isIntroduced());
		assertFalse(off.printNotice);
		assertEquals(1, off.next.rows().size());
		Change back = off.next.apply("on");
		assertTrue(back.next.isOn());
		assertFalse(back.printNotice);
		assertEquals(1, back.next.rows().size());
		assertEquals(160, back.next.rows().get(0).keyCode);
	}

	@Test
	public void introducedWithNoRowsDoesNotReseed() {
		KeyboardShortcuts stored = KeyboardShortcuts.parse("on=0;introduced=1");
		Change on = stored.apply("on");
		assertTrue(on.changed);
		assertFalse(on.printNotice);
		assertTrue(on.next.rows().isEmpty());
	}

	@Test
	public void bareCommandDoesNotFlip() {
		Change on = KeyboardShortcuts.enabled(Row.numpadEnter()).apply("");
		assertFalse(on.changed);
		assertFalse(on.usage);
		assertTrue(on.next.isOn());
		Change off = KeyboardShortcuts.empty().apply(null);
		assertFalse(off.changed);
		assertFalse(off.next.isOn());
		Change spaced = KeyboardShortcuts.empty().apply("   ");
		assertFalse(spaced.changed);
		assertFalse(spaced.next.isOn());
	}

	@Test
	public void unknownWordsDoNotChangeState() {
		KeyboardShortcuts start = KeyboardShortcuts.enabled(Row.numpadEnter());
		Change edit = start.apply("edit");
		assertTrue(edit.usage);
		assertFalse(edit.changed);
		assertTrue(edit.next.isOn());
		assertEquals(1, edit.next.rows().size());
		Change extra = KeyboardShortcuts.empty().apply("on please");
		assertTrue(extra.usage);
		assertFalse(extra.next.isOn());
		assertEquals(0, extra.next.rows().size());
		assertTrue(KeyboardShortcuts.empty().apply("true").usage);
		assertTrue(KeyboardShortcuts.empty().apply("1").usage);
		assertTrue(KeyboardShortcuts.empty().apply("yes").usage);
	}

	@Test
	public void toggleUsesTheSameFirstOnRules() {
		Change on = KeyboardShortcuts.empty().apply("toggle");
		assertTrue(on.printNotice);
		assertTrue(on.next.isOn());
		assertEquals(1, on.next.rows().size());
		assertEquals(160, on.next.rows().get(0).keyCode);
		Change off = on.next.apply("Toggle");
		assertFalse(off.next.isOn());
		assertFalse(off.printNotice);
		assertEquals(1, off.next.rows().size());
	}

	@Test
	public void onlyOneMeansOn() {
		assertFalse(KeyboardShortcuts.parse("on=true;introduced=1").isOn());
		assertTrue(KeyboardShortcuts.parse("on=true;introduced=1").isIntroduced());
		assertFalse(KeyboardShortcuts.parse("on=yes;introduced=yes").isOn());
		assertFalse(KeyboardShortcuts.parse("on=yes;introduced=yes").isIntroduced());
		assertTrue(KeyboardShortcuts.parse("on=1;introduced=1").isOn());
		assertTrue(KeyboardShortcuts.parse("on=1;introduced=1").isIntroduced());
	}

	@Test
	public void modifierBitsCollapseLeftAndRightAndIgnoreLocks() {
		assertEquals(KeyboardShortcuts.MOD_ALT, KeyboardShortcuts.modifiers(0x12));
		assertEquals(KeyboardShortcuts.MOD_ALT, KeyboardShortcuts.modifiers(0x22));
		assertEquals(KeyboardShortcuts.MOD_CTRL | KeyboardShortcuts.MOD_ALT,
				KeyboardShortcuts.modifiers(0x3012));
		assertEquals(KeyboardShortcuts.MOD_SHIFT, KeyboardShortcuts.modifiers(0x81));
		assertEquals(0, KeyboardShortcuts.modifiers(0x200000));
	}

	@Test
	public void roundTripKeepsACommandLine() {
		Row row = Row.of(29, KeyboardShortcuts.MOD_CTRL, KeyboardShortcuts.ACTION_LINE,
				true, false, ".kb insert say hello", "north;look");
		KeyboardShortcuts parsed = KeyboardShortcuts.parse(KeyboardShortcuts.enabled(row).format());
		assertTrue(parsed.isOn());
		assertEquals(1, parsed.rows().size());
		Row back = parsed.rows().get(0);
		assertEquals(29, back.keyCode);
		assertEquals(KeyboardShortcuts.MOD_CTRL, back.modifiers);
		assertEquals(".kb insert say hello", back.tap);
		assertEquals("north;look", back.hold);
		assertFalse(back.repeat);
	}

	@Test
	public void holdLineWinsOverRepeat() {
		Row row = Row.of(29, 0, KeyboardShortcuts.ACTION_LINE, true, true, "tap", "held");
		assertFalse(row.repeat);
		assertEquals("tap", row.tap);
		assertEquals("held", row.hold);
		KeyboardShortcuts parsed = KeyboardShortcuts.parse(
				KeyboardShortcuts.enabled(row).format());
		assertFalse(parsed.rows().get(0).repeat);
		assertEquals("held", parsed.rows().get(0).hold);
	}

	@Test
	public void unknownAndMalformedRowsAreSkipped() {
		KeyboardShortcuts parsed = KeyboardShortcuts.parse(
				"on=1;introduced=1;nope;zz|0|line|1|0||;29|0|line|1|0|hi|");
		assertTrue(parsed.isOn());
		assertEquals(1, parsed.rows().size());
		assertEquals("hi", parsed.rows().get(0).tap);
	}

	@Test
	public void storedModifiersKeepOnlyTheOnBits() {
		KeyboardShortcuts parsed = KeyboardShortcuts.parse(
				"on=1;introduced=1;29|18|line|1|0|go|");
		assertEquals(KeyboardShortcuts.MOD_ALT, parsed.rows().get(0).modifiers);
	}
}

package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InputHyphenationTest {

	@Test
	public void offOrSingleLineOrPasswordDrawsNoHyphen() {
		assertEquals(InputHyphenation.Frequency.NONE,
				InputHyphenation.frequency(false, true, false, false));
		assertEquals(InputHyphenation.Frequency.NONE,
				InputHyphenation.frequency(true, false, false, true));
		assertEquals(InputHyphenation.Frequency.NONE,
				InputHyphenation.frequency(true, true, true, true));
	}

	@Test
	public void fullAllowsAShorterPieceThanSparing() {
		assertEquals(5, InputHyphenation.minLead(InputHyphenation.Frequency.NORMAL));
		assertEquals(3, InputHyphenation.minTail(InputHyphenation.Frequency.NORMAL));
		assertEquals(2, InputHyphenation.minLead(InputHyphenation.Frequency.FULL));
		assertEquals(2, InputHyphenation.minTail(InputHyphenation.Frequency.FULL));
	}

	@Test
	public void onAndGrowingPicksSparingOrDense() {
		assertEquals(InputHyphenation.Frequency.NORMAL,
				InputHyphenation.frequency(true, true, false, false));
		assertEquals(InputHyphenation.Frequency.FULL,
				InputHyphenation.frequency(true, true, false, true));
	}

	@Test
	public void oneDictionaryAtATimeAndNoneLeavesThePhoneLocale() {
		assertEquals("en", InputHyphenation.localeTag(
				InputHyphenation.Frequency.NORMAL, InputHyphenation.LANG_EN));
		assertEquals("pl", InputHyphenation.localeTag(
				InputHyphenation.Frequency.FULL, InputHyphenation.LANG_PL));
		assertNull(InputHyphenation.localeTag(
				InputHyphenation.Frequency.NORMAL, InputHyphenation.LANG_PHONE));
		assertNull(InputHyphenation.localeTag(
				InputHyphenation.Frequency.NONE, InputHyphenation.LANG_PL));
		assertEquals("en", InputHyphenation.localeTag(
				InputHyphenation.Frequency.NORMAL, 99));
	}

	@Test
	public void highQualityBreakOnlyWhileHyphenatingASimpleStrategy() {
		assertFalse(InputHyphenation.forceHighQualityBreak(
				InputHyphenation.Frequency.NONE, true));
		assertFalse(InputHyphenation.forceHighQualityBreak(
				InputHyphenation.Frequency.NORMAL, false));
		assertTrue(InputHyphenation.forceHighQualityBreak(
				InputHyphenation.Frequency.NORMAL, true));
	}

	@Test
	public void parseStatusOnOffLanguageAndDensity() {
		assertEquals(InputHyphenation.Action.STATUS,
				InputHyphenation.parse(null).action);
		assertEquals(InputHyphenation.Action.STATUS,
				InputHyphenation.parse("  ").action);

		InputHyphenation.Parsed on = InputHyphenation.parse("ON");
		assertEquals(InputHyphenation.Action.ENABLED, on.action);
		assertTrue(on.on);

		InputHyphenation.Parsed off = InputHyphenation.parse("no");
		assertEquals(InputHyphenation.Action.ENABLED, off.action);
		assertFalse(off.on);

		InputHyphenation.Parsed pl = InputHyphenation.parse("lang polish");
		assertEquals(InputHyphenation.Action.LANG, pl.action);
		assertEquals(InputHyphenation.LANG_PL, pl.lang);

		InputHyphenation.Parsed phone = InputHyphenation.parse("device");
		assertEquals(InputHyphenation.Action.LANG, phone.action);
		assertEquals(InputHyphenation.LANG_PHONE, phone.lang);

		InputHyphenation.Parsed full = InputHyphenation.parse("full off");
		assertEquals(InputHyphenation.Action.FULL, full.action);
		assertFalse(full.on);

		assertEquals(InputHyphenation.Action.BAD,
				InputHyphenation.parse("lang").action);
		assertEquals(InputHyphenation.Action.BAD,
				InputHyphenation.parse("full maybe").action);
		assertEquals(InputHyphenation.Action.BAD,
				InputHyphenation.parse("on full").action);
	}
}

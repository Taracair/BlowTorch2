package com.resurrection.blowtorch2.lib.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Characterization for Colorizer pure lookup helpers (no Context).
 * Deprecated getColorCode needs Android span classes (available via unit-test stubs).
 * Chrome / window-inset math lives in Launcher/MainWindow UI listeners — not covered here.
 */
public class ColorizerCharacterizationTest {

	@Test
	public void ansiEscapeFlavorStrings() {
		assertTrue(Colorizer.getRedColor().contains("[1;31m"));
		assertTrue(Colorizer.getWhiteColor().contains("[0;37m"));
		assertTrue(Colorizer.getGreenColor().contains("[1;34m"));
		assertTrue(Colorizer.getBrightCyanColor().contains("[1;36m"));
		assertTrue(Colorizer.getBrightYellowColor().contains("[1;33m"));
		assertTrue(Colorizer.getTeloptStartColor().contains("[1;43;30m"));
		assertEquals("\u001b[0m", Colorizer.getResetColor());
		assertNotNull(Colorizer.getResetColor());
	}

	@Test
	public void getColorTypeClassifiesAnsiCodes() {
		assertEquals(Colorizer.COLOR_TYPE.ZERO_CODE, Colorizer.getColorType(0));
		assertEquals(Colorizer.COLOR_TYPE.BRIGHT_CODE, Colorizer.getColorType(1));
		assertEquals(Colorizer.COLOR_TYPE.DIM_CODE, Colorizer.getColorType(2));
		assertEquals(Colorizer.COLOR_TYPE.FOREGROUND, Colorizer.getColorType(30));
		assertEquals(Colorizer.COLOR_TYPE.FOREGROUND, Colorizer.getColorType(31));
		assertEquals(Colorizer.COLOR_TYPE.BACKGROUND, Colorizer.getColorType(40));
		assertEquals(Colorizer.COLOR_TYPE.BACKGROUND, Colorizer.getColorType(41));
		assertEquals(Colorizer.COLOR_TYPE.DEFAULT_FOREGROUND, Colorizer.getColorType(39));
		assertEquals(Colorizer.COLOR_TYPE.DEFAULT_BACKGROUND, Colorizer.getColorType(49));
		assertEquals(Colorizer.COLOR_TYPE.XTERM_256_FG_START, Colorizer.getColorType(38));
		assertEquals(Colorizer.COLOR_TYPE.XTERM_256_BG_START, Colorizer.getColorType(48));
		assertEquals(Colorizer.COLOR_TYPE.XTERM_256_FIVE, Colorizer.getColorType(5));
		assertEquals(Colorizer.COLOR_TYPE.FOREGROUND, Colorizer.getColorType(90));
		assertEquals(Colorizer.COLOR_TYPE.FOREGROUND, Colorizer.getColorType(91));
		assertEquals(Colorizer.COLOR_TYPE.BACKGROUND, Colorizer.getColorType(101));
		assertEquals(Colorizer.COLOR_TYPE.NOT_A_COLOR, Colorizer.getColorType(999));
	}

	@Test
	public void getColorTypeFromCharSequence() {
		assertEquals(Colorizer.COLOR_TYPE.FOREGROUND, Colorizer.getColorType("31"));
		assertEquals(Colorizer.COLOR_TYPE.NOT_A_COLOR, Colorizer.getColorType("nope"));
	}

	@Test
	public void getColorValueAnsiRedAndBright() {
		int black = Colorizer.getColorValue(0, 30, false);
		int normalRed = Colorizer.getColorValue(0, 31, false);
		int brightRed = Colorizer.getColorValue(1, 31, false);
		assertEquals(0xFFBB0000, normalRed);
		assertEquals(0xFFFF5555, brightRed);
		assertTrue(black != normalRed);
		assertTrue(normalRed != brightRed);
	}

	@Test
	public void getColorValueAixtermBrightMapsToBrightPalette() {
		int aixRed = Colorizer.getColorValue(0, 91, false);
		assertEquals(Colorizer.getColorValue(1, 31, false), aixRed);
	}

	@Test
	public void get256ColorValueKnownCodes() {
		assertEquals(0xFF000000, Colorizer.get256ColorValue(0));
		assertEquals(0xFFFFFFFF, Colorizer.get256ColorValue(15));
		assertEquals(0xFF00005F, Colorizer.get256ColorValue(17));
		int c16 = Colorizer.get256ColorValue(16);
		int c231 = Colorizer.get256ColorValue(231);
		int c232 = Colorizer.get256ColorValue(232);
		int c255 = Colorizer.get256ColorValue(255);
		assertTrue(c16 != c231);
		assertTrue(c232 != c255);
	}

	@Test
	public void getColorValueCharSequenceDelegates() {
		int c = Colorizer.getColorValue("0", "31", false);
		assertEquals(0xFFBB0000, c);
	}

	@Test
	public void stripAnsiEscapesRemovesSgrSequences() {
		assertEquals("hello", Colorizer.stripAnsiEscapes("\u001b[1;31mhello\u001b[0m"));
		assertEquals("", Colorizer.stripAnsiEscapes(null));
		assertEquals("plain", Colorizer.stripAnsiEscapes("plain"));
	}

	/** Production leak: CSI reset between {@code [chatnet]} and the body broke {@code \[chatnet\] (.+)}. */
	@Test
	public void stripAnsiEscapesChatnetDiaLine() {
		String raw = "7:32 am [chatnet]\u001b[0m Dia says, \"...huh, also while i'm rambling\"";
		String plain = Colorizer.stripAnsiEscapes(raw);
		assertEquals("7:32 am [chatnet] Dia says, \"...huh, also while i'm rambling\"", plain);
		assertFalse(plain.contains("\u001b"));
		assertTrue(java.util.regex.Pattern.compile("\\[chatnet\\] (.+)").matcher(plain).find());
	}

	@Test
	public void stripAnsiEscapesXterm256WhoLine() {
		String raw = "$ Dia                  \u001b[38;5;171madmin  !  pvp 52m    Distracted";
		assertEquals("$ Dia                  admin  !  pvp 52m    Distracted",
				Colorizer.stripAnsiEscapes(raw));
	}

	@Test
	public void stripAnsiEscapesNonSgrCsi() {
		assertEquals("room", Colorizer.stripAnsiEscapes("\u001b[2J\u001b[Hroom"));
		assertEquals("x", Colorizer.stripAnsiEscapes("\u001b[1A\u001b[Kx"));
	}

	@Test
	public void stripAnsiEscapesConcurrentStress() throws Exception {
		final int threads = 8;
		final int iters = 2000;
		final String sample = "pre\u001b[38;5;171mMID\u001b[0m [chatnet]\u001b[0m Dia\u001b[2J";
		final String expect = "preMID [chatnet] Dia";
		final java.util.concurrent.atomic.AtomicInteger failures =
				new java.util.concurrent.atomic.AtomicInteger();
		Thread[] ts = new Thread[threads];
		for (int t = 0; t < threads; t++) {
			ts[t] = new Thread(new Runnable() {
				@Override
				public void run() {
					for (int i = 0; i < iters; i++) {
						String out = Colorizer.stripAnsiEscapes(sample);
						if (!expect.equals(out) || out.indexOf('\u001b') >= 0) {
							failures.incrementAndGet();
						}
					}
				}
			});
			ts[t].start();
		}
		for (Thread t : ts) {
			t.join();
		}
		assertEquals(0, failures.get());
	}

	@Test
	public void getColorCodeReturnsNonNullForBasic() {
		Object code = Colorizer.getColorCode(0, 31);
		assertNotNull(code);
	}
}

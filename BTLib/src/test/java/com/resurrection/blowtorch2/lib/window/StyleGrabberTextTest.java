package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StyleGrabberTextTest {

	private static final StyleGrabberText.Widths UNIT = new StyleGrabberText.Widths() {
		@Override
		public float measure(final String s) {
			return s == null ? 0f : s.length();
		}
	};

	@Test
	public void shortTextIsUnchanged() {
		assertEquals("golden cat",
				StyleGrabberText.ellipsize("golden cat", 80f, UNIT));
	}

	@Test
	public void exactFitIsUnchanged() {
		assertEquals("abc", StyleGrabberText.ellipsize("abc", 3f, UNIT));
	}

	@Test
	public void longPhraseCutsWithThreeDots() {
		assertEquals("You see a golden ca...",
				StyleGrabberText.ellipsize("You see a golden cat sitting", 22f,
						UNIT));
	}

	@Test
	public void narrowerThanDotsIsJustDots() {
		assertEquals("...", StyleGrabberText.ellipsize("golden cat", 2f, UNIT));
	}

	@Test
	public void nullIsEmpty() {
		assertEquals("", StyleGrabberText.ellipsize(null, 10f, UNIT));
	}

	@Test
	public void emptyStaysEmpty() {
		assertEquals("", StyleGrabberText.ellipsize("", 10f, UNIT));
	}
}

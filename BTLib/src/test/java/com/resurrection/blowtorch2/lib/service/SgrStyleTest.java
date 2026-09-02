package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * SGR 3/4/7/9/21 and their offs. 5 and 2 must not land here — they are
 * {@code 38;5;n} / truecolor / faint via {@link Colorizer.COLOR_TYPE}.
 */
public class SgrStyleTest {

	@Test
	public void italicOnOff() {
		SgrStyle s = new SgrStyle();
		s.apply(3);
		assertTrue(s.italic());
		s.apply(23);
		assertFalse(s.italic());
	}

	@Test
	public void underlineIncludesDoubleUnderline21() {
		SgrStyle s = new SgrStyle();
		s.apply(4);
		assertTrue(s.underline());
		s.apply(24);
		assertFalse(s.underline());
		s.apply(21);
		assertTrue(s.underline());
	}

	@Test
	public void strikeAndReverse() {
		SgrStyle s = new SgrStyle();
		s.apply(9);
		s.apply(7);
		assertTrue(s.strike());
		assertTrue(s.reverse());
		s.apply(29);
		s.apply(27);
		assertFalse(s.strike());
		assertFalse(s.reverse());
	}

	@Test
	public void fiveAndTwoAreNotStyleCodes() {
		assertFalse(SgrStyle.isCode(5));
		assertFalse(SgrStyle.isCode(2));
		assertFalse(SgrStyle.isCode(1));
		assertFalse(SgrStyle.isCode(22));
		assertFalse(SgrStyle.isCode(0));
		SgrStyle s = new SgrStyle();
		s.apply(5);
		s.apply(2);
		s.apply(22);
		assertEquals(0, s.bits());
	}

	@Test
	public void isCodeOnlyThePaintedAttributes() {
		assertTrue(SgrStyle.isCode(3));
		assertTrue(SgrStyle.isCode(4));
		assertTrue(SgrStyle.isCode(7));
		assertTrue(SgrStyle.isCode(9));
		assertTrue(SgrStyle.isCode(21));
		assertTrue(SgrStyle.isCode(23));
		assertTrue(SgrStyle.isCode(24));
		assertTrue(SgrStyle.isCode(27));
		assertTrue(SgrStyle.isCode(29));
	}

	@Test
	public void zeroClearsViaClearNotApply() {
		SgrStyle s = new SgrStyle();
		s.apply(3);
		s.apply(4);
		s.apply(7);
		s.apply(9);
		s.setFaint(true);
		s.apply(0);
		assertTrue(s.italic());
		s.clear();
		assertEquals(0, s.bits());
	}

	@Test
	public void faintIsASeparateKnobFromBright() {
		SgrStyle s = new SgrStyle();
		s.setFaint(true);
		assertTrue(s.faint());
		s.apply(3);
		assertTrue(s.italic());
		s.clearFaint();
		assertFalse(s.faint());
		assertTrue(s.italic());
	}

	@Test
	public void setBitsRoundTrip() {
		SgrStyle s = new SgrStyle();
		s.apply(3);
		s.setFaint(true);
		int stored = s.bits();
		SgrStyle other = new SgrStyle();
		other.setBits(stored);
		assertTrue(other.italic());
		assertTrue(other.faint());
		assertFalse(other.underline());
	}
}

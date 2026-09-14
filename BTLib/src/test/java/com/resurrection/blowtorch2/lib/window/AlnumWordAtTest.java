package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;

public class AlnumWordAtTest {

	private static final String SAMPLE = "a rusty iron-helmet (glowing)";

	@Test
	public void hyphenSplitsAndParensAreNotWords() {
		List<AlnumWordAt.Span> all = AlnumWordAt.all(SAMPLE);
		assertEquals(5, all.size());
		assertEquals("a", all.get(0).text);
		assertEquals("rusty", all.get(1).text);
		assertEquals("iron", all.get(2).text);
		assertEquals("helmet", all.get(3).text);
		assertEquals("glowing", all.get(4).text);
	}

	@Test
	public void aFingerOnALetterReturnsThatWord() {
		assertEquals("rusty", AlnumWordAt.at(SAMPLE, SAMPLE.indexOf("rusty")));
		assertEquals("iron", AlnumWordAt.at(SAMPLE, SAMPLE.indexOf("iron")));
		assertEquals("helmet", AlnumWordAt.at(SAMPLE, SAMPLE.indexOf("helmet")));
		assertEquals("glowing", AlnumWordAt.at(SAMPLE, SAMPLE.indexOf("glowing")));
	}

	@Test
	public void aFingerOnHyphenOrSpaceOrParenMisses() {
		assertNull(AlnumWordAt.at(SAMPLE, SAMPLE.indexOf('-')));
		assertNull(AlnumWordAt.at(SAMPLE, SAMPLE.indexOf(' ')));
		assertNull(AlnumWordAt.at(SAMPLE, SAMPLE.indexOf('(')));
	}

	@Test
	public void outOfRangeMisses() {
		assertNull(AlnumWordAt.at(SAMPLE, -1));
		assertNull(AlnumWordAt.at(SAMPLE, SAMPLE.length()));
		assertNull(AlnumWordAt.at(null, 0));
		assertNull(AlnumWordAt.at("", 0));
	}

	@Test
	public void digitsStayInTheWord() {
		assertEquals("2h", AlnumWordAt.at("wait 2h then", 5));
		assertEquals("hp12", AlnumWordAt.at("hp12 left", 0));
		List<AlnumWordAt.Span> all = AlnumWordAt.all("key2 2h");
		assertEquals(2, all.size());
		assertEquals("key2", all.get(0).text);
		assertEquals("2h", all.get(1).text);
	}

	@Test
	public void unicodeLettersCount() {
		assertEquals("hełm", AlnumWordAt.at("hełm rusty", 2));
	}

	@Test
	public void apostropheSplits() {
		assertEquals("O", AlnumWordAt.at("O'Brien", 0));
		assertEquals("Brien", AlnumWordAt.at("O'Brien", 2));
		assertNull(AlnumWordAt.at("O'Brien", 1));
	}
}

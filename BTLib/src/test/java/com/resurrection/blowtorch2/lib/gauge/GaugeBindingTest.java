package com.resurrection.blowtorch2.lib.gauge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class GaugeBindingTest {

	private static final String VITALS = "{\"hp\":80,\"maxhp\":100}";

	@Test
	public void parseNumber_intFloatAndPair() {
		assertEquals(Double.valueOf(80), GaugeBinding.parseNumber("80"));
		assertEquals(Double.valueOf(80.5), GaugeBinding.parseNumber("80.5"));
		assertEquals(Double.valueOf(80), GaugeBinding.parseNumber("80/100"));
		assertEquals(Double.valueOf(80), GaugeBinding.parseNumber(" 80 / 100 "));
		assertNull(GaugeBinding.parseNumber(null));
		assertNull(GaugeBinding.parseNumber(""));
		assertNull(GaugeBinding.parseNumber("hp"));
	}

	@Test
	public void parsePair_valueAndMax() {
		double[] pair = GaugeBinding.parsePair("80/100");
		assertNotNull(pair);
		assertEquals(80.0, pair[0], 0.0001);
		assertEquals(100.0, pair[1], 0.0001);
		double[] spaced = GaugeBinding.parsePair(" 12.5 / 30 ");
		assertNotNull(spaced);
		assertEquals(12.5, spaced[0], 0.0001);
		assertEquals(30.0, spaced[1], 0.0001);
		assertNull(GaugeBinding.parsePair("80"));
		assertNull(GaugeBinding.parsePair(null));
		assertNull(GaugeBinding.parsePair("80/"));
	}

	@Test
	public void numberFromGmcpJson_stripsModulePrefix() {
		Double hp = GaugeBinding.numberFromGmcpJson("Char.Vitals", VITALS,
				"Char.Vitals.hp");
		assertEquals(Double.valueOf(80), hp);
		Double max = GaugeBinding.numberFromGmcpJson("Char.Vitals", VITALS,
				"char.vitals.maxhp");
		assertEquals(Double.valueOf(100), max);
	}

	@Test
	public void numberFromGmcpJson_bareKey() {
		assertEquals(Double.valueOf(80),
				GaugeBinding.numberFromGmcpJson("Char.Vitals", VITALS, "hp"));
		assertEquals(Double.valueOf(100),
				GaugeBinding.numberFromGmcpJson("Char.Vitals", VITALS, "maxhp"));
	}

	@Test
	public void numberFromGmcpJson_nested() {
		String body = "{\"inner\":{\"hp\":80}}";
		assertEquals(Double.valueOf(80),
				GaugeBinding.numberFromGmcpJson("Char.Vitals", body, "inner.hp"));
		assertEquals(Double.valueOf(80),
				GaugeBinding.numberFromGmcpJson("Room", body, "Room.inner.hp"));
	}

	@Test
	public void numberFromGmcpJson_stringPairInBody() {
		String body = "{\"hp\":\"80/100\"}";
		assertEquals(Double.valueOf(80),
				GaugeBinding.numberFromGmcpJson("Char.Vitals", body, "hp"));
	}

	@Test
	public void numberFromGmcpJson_missingIsNull() {
		assertNull(GaugeBinding.numberFromGmcpJson("Char.Vitals", VITALS, "mp"));
		assertNull(GaugeBinding.numberFromGmcpJson("Char.Vitals", VITALS,
				"Char.Vitals.mp"));
		assertNull(GaugeBinding.numberFromGmcpJson("Char.Vitals", "{not-json}", "hp"));
		assertNull(GaugeBinding.numberFromGmcpJson("Char.Vitals", VITALS, null));
		assertNull(GaugeBinding.numberFromGmcpJson("Char.Vitals", null, "hp"));
	}

	@Test
	public void numberFromObject_numbersAndString() {
		assertEquals(Double.valueOf(80), GaugeBinding.numberFromObject(Integer.valueOf(80)));
		assertEquals(Double.valueOf(80), GaugeBinding.numberFromObject(Long.valueOf(80L)));
		assertEquals(Double.valueOf(80.5), GaugeBinding.numberFromObject(Double.valueOf(80.5)));
		assertEquals(Double.valueOf(80), GaugeBinding.numberFromObject("80/100"));
		assertNull(GaugeBinding.numberFromObject(null));
		assertNull(GaugeBinding.numberFromObject(Boolean.TRUE));
	}

	private static final String HELLMOO_VISIBLE =
			"hp: 60 maxhp: 60 thirst: 62.81 hunger: 1 stress: 0";

	@Test
	public void numbersFromRegexLine_hellmooVisibleKeys() {
		double[] hp = GaugeBinding.numbersFromRegexLine(HELLMOO_VISIBLE,
				"hp:\\s*([\\d.]+)", null);
		assertNotNull(hp);
		assertEquals(1, hp.length);
		assertEquals(60.0, hp[0], 0.0001);
		double[] maxhp = GaugeBinding.numbersFromRegexLine(HELLMOO_VISIBLE,
				"maxhp:\\s*([\\d.]+)", null);
		assertNotNull(maxhp);
		assertEquals(60.0, maxhp[0], 0.0001);
		double[] thirst = GaugeBinding.numbersFromRegexLine(HELLMOO_VISIBLE,
				"thirst:\\s*([\\d.]+)", null);
		assertNotNull(thirst);
		assertEquals(62.81, thirst[0], 0.0001);
		double[] both = GaugeBinding.numbersFromRegexLine(HELLMOO_VISIBLE,
				"hp:\\s*([\\d.]+)", "maxhp:\\s*([\\d.]+)");
		assertNotNull(both);
		assertEquals(2, both.length);
		assertEquals(60.0, both[0], 0.0001);
		assertEquals(60.0, both[1], 0.0001);
	}

	@Test
	public void numbersFromRegexLine_twoGroupsAreValueAndMax() {
		double[] pair = GaugeBinding.numbersFromRegexLine("HP: 80/100",
				"HP:\\s*([\\d.]+)/([\\d.]+)", "");
		assertNotNull(pair);
		assertEquals(2, pair.length);
		assertEquals(80.0, pair[0], 0.0001);
		assertEquals(100.0, pair[1], 0.0001);
	}

	@Test
	public void numbersFromRegexLine_invalidPatternDoesNotThrow() {
		assertNull(GaugeBinding.compileRegex("[unterminated"));
		assertNull(GaugeBinding.numbersFromRegexLine("HP: 80/100", "[unterminated", null));
		assertNull(GaugeBinding.numbersFromRegexLine("no match", "hp:\\s*([\\d.]+)", null));
		assertNull(GaugeBinding.numbersFromRegexLine("hp: foo", "hp:\\s*([a-z]+)", null));
		assertNull(GaugeBinding.numbersFromRegexLine(null, "hp:\\s*([\\d.]+)", null));
	}
}

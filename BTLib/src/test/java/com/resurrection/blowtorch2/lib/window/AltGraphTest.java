package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AltGraphTest {

	@Test
	public void rightAltTypesThePolishLetterWhenTheEventHasNone() {
		assertEquals(0x0105, AltGraph.character(0x22, 29, 'a', 0));
		assertEquals(0x0104, AltGraph.character(0x23, 29, 'A', 0));
		assertEquals(0x0142, AltGraph.character(0x22, 40, 'l', 0));
		assertEquals(0x017C, AltGraph.character(0x22, 54, 'z', 'z'));
		assertEquals(0, AltGraph.character(0x22, 29, 'a', 0x0105));
		assertEquals(0, AltGraph.character(0x12, 29, 'a', 0));
		assertEquals(0, AltGraph.character(0x3012, 29, 'a', 0));
		assertEquals(0, AltGraph.character(0x22, 30, 'b', 0));
	}
}

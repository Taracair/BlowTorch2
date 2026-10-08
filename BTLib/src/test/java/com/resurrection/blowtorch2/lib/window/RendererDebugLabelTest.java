package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RendererDebugLabelTest {

	@Test
	public void allThreeNamesStayWithAZero() {
		assertEquals("typeset 12 · tiles 0 · bake 0 · hw",
				RendererDebugLabel.format(0, 0, 12, true));
		assertEquals("typeset 0 · tiles 40 · bake 0 · hw",
				RendererDebugLabel.format(40, 0, 0, true));
		assertEquals("typeset 0 · tiles 0 · bake 8 · sw",
				RendererDebugLabel.format(0, 8, 0, false));
	}

	@Test
	public void aMixKeepsEachCount() {
		assertEquals("typeset 0 · tiles 30 · bake 4 · hw",
				RendererDebugLabel.format(30, 4, 0, true));
		assertEquals("typeset 6 · tiles 10 · bake 2 · sw",
				RendererDebugLabel.format(10, 2, 6, false));
	}

	@Test
	public void nothingDrawnIsZeros() {
		assertEquals("typeset 0 · tiles 0 · bake 0 · hw",
				RendererDebugLabel.format(0, 0, 0, true));
		assertEquals("typeset 0 · tiles 0 · bake 0 · sw",
				RendererDebugLabel.format(-1, 0, 0, false));
	}

	@Test
	public void textShrinksToTheChipAndStopsAtTheFloor() {
		assertEquals(16f, RendererDebugLabel.fitTextSize(16f, 100f, 200f, 11f), 0.01f);
		assertEquals(8f, RendererDebugLabel.fitTextSize(16f, 200f, 100f, 4f), 0.01f);
		assertEquals(11f, RendererDebugLabel.fitTextSize(16f, 400f, 100f, 11f), 0.01f);
		assertEquals(16f, RendererDebugLabel.fitTextSize(16f, 0f, 100f, 11f), 0.01f);
	}
}

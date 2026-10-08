package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SelectionTriggerSeedTest {

	@Test
	public void phraseBecomesPatternAndName() {
		SelectionTriggerSeed.Seed seed = SelectionTriggerSeed.from("troll hits you");
		assertEquals("troll hits you", seed.pattern);
		assertEquals("troll hits you", seed.name);
	}

	@Test
	public void blankIsRefused() {
		assertNull(SelectionTriggerSeed.from(null));
		assertNull(SelectionTriggerSeed.from(""));
		assertNull(SelectionTriggerSeed.from("  \n\t"));
	}

	@Test
	public void newlinesStayInThePatternAndTheNameIsTheFirstLine() {
		SelectionTriggerSeed.Seed seed = SelectionTriggerSeed.from(
				"  You are hungry.\nEat something.  ");
		assertEquals("You are hungry.\nEat something.", seed.pattern);
		assertEquals("You are hungry.", seed.name);
	}

	@Test
	public void aLongFirstLineIsCutForTheName() {
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < 60; i++) {
			line.append('a');
		}
		SelectionTriggerSeed.Seed seed = SelectionTriggerSeed.from(line.toString());
		assertEquals(60, seed.pattern.length());
		assertEquals(SelectionTriggerSeed.NAME_LIMIT, seed.name.length());
	}
}

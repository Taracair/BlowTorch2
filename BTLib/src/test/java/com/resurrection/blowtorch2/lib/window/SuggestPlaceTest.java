package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SuggestPlaceTest {

	@Test
	public void nextStepsFloatingThenBarThenListThenOff() {
		assertEquals(WordSuggestions.WHERE_BAR,
				WordSuggestions.cycleWhere(WordSuggestions.WHERE_FLOATING));
		assertEquals(WordSuggestions.WHERE_LIST,
				WordSuggestions.cycleWhere(WordSuggestions.WHERE_BAR));
		assertEquals(WordSuggestions.WHERE_NONE,
				WordSuggestions.cycleWhere(WordSuggestions.WHERE_LIST));
		assertEquals(WordSuggestions.WHERE_FLOATING,
				WordSuggestions.cycleWhere(WordSuggestions.WHERE_NONE));
	}

	@Test
	public void listIsAPlaceAndNextIsNot() {
		assertEquals(Integer.valueOf(WordSuggestions.WHERE_LIST),
				WordSuggestions.place("list"));
		assertEquals(Integer.valueOf(WordSuggestions.WHERE_FLOATING),
				WordSuggestions.place("float"));
		assertEquals(Integer.valueOf(WordSuggestions.WHERE_BAR),
				WordSuggestions.place("bar"));
		assertEquals(Integer.valueOf(WordSuggestions.WHERE_NONE),
				WordSuggestions.place("off"));
		assertNull(WordSuggestions.place("next"));
		assertNull(WordSuggestions.place("window"));
	}
}

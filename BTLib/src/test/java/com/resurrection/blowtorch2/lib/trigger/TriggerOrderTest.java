package com.resurrection.blowtorch2.lib.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class TriggerOrderTest {

	private static TriggerData named(final String name, final int sequence) {
		TriggerData t = new TriggerData();
		t.setName(name);
		t.setSequence(sequence);
		t.setPattern("x");
		return t;
	}

	@Test
	public void smallerSequenceRunsFirst() {
		TriggerData channel = named("_channel", 10);
		TriggerData word = named("_opens", 20);
		assertTrue(TriggerOrder.compare(channel, word) < 0);
		assertTrue(TriggerOrder.compare(word, channel) > 0);
	}

	@Test
	public void equalSequenceBreaksTiesByNameIgnoreCase() {
		TriggerData channel = named("_channel", 10);
		TriggerData opens = named("_opens", 10);
		assertTrue(TriggerOrder.compare(channel, opens) < 0);
		List<TriggerData> list = new ArrayList<TriggerData>(
				Arrays.asList(opens, channel));
		Collections.sort(list, TriggerOrder.COMPARATOR);
		assertEquals("_channel", list.get(0).getName());
		assertEquals("_opens", list.get(1).getName());
	}

	@Test
	public void emptyEditorFieldIsDefaultTenNotZero() {
		assertEquals(TriggerData.DEFAULT_SEQUENCE, TriggerOrder.parseSequence(""));
		assertEquals(TriggerData.DEFAULT_SEQUENCE, TriggerOrder.parseSequence("  "));
		assertEquals(TriggerData.DEFAULT_SEQUENCE, TriggerOrder.parseSequence(null));
		assertEquals(TriggerData.DEFAULT_SEQUENCE, TriggerOrder.parseSequence("nope"));
		assertEquals(20, TriggerOrder.parseSequence("20"));
		assertEquals(0, TriggerOrder.parseSequence("0"));
	}

	@Test
	public void neighborsNameTheTriggersOnEitherSide() {
		List<TriggerData> set = Arrays.asList(
				named("_channel", 10),
				named("_opens", 20),
				named("_says", 30));
		assertEquals("Fires after _channel, before _says.",
				TriggerOrder.describeNeighbors(set, "_opens", "_opens", 20));
		assertEquals("Fires first in this set, before _opens.",
				TriggerOrder.describeNeighbors(set, "_channel", "_channel", 10));
		assertEquals("Fires last in this set, after _opens.",
				TriggerOrder.describeNeighbors(set, "_says", "_says", 30));
	}

	@Test
	public void neighborsWarnWhenSeveralShareANumber() {
		List<TriggerData> set = Arrays.asList(
				named("_channel", 10),
				named("_opens", 10),
				named("_says", 10));
		String text = TriggerOrder.describeNeighbors(set, "_opens", "_opens", 10);
		assertTrue(text.contains("Same sequence as"));
		assertTrue(text.contains("_channel"));
		assertTrue(text.contains("split the numbers"));
	}

	@Test
	public void neighborsNeedAName() {
		assertEquals("Give this trigger a name to see where it sits.",
				TriggerOrder.describeNeighbors(Collections.<TriggerData>emptyList(),
						null, "  ", 10));
	}

	@Test
	public void onlyTriggerInTheSet() {
		assertEquals("Only trigger in this set.",
				TriggerOrder.describeNeighbors(Collections.<TriggerData>emptyList(),
						null, "_channel", 10));
	}
}

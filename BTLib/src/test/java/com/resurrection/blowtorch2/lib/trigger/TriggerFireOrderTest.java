package com.resurrection.blowtorch2.lib.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;

public class TriggerFireOrderTest {

	@Test
	public void earlierLineBeatsAnEarlierListIndex() {
		String source = "Round 2\n@sk hit\n";
		int round = source.indexOf("Round");
		int sk = source.indexOf("@sk");
		ArrayList<TriggerFireOrder.Placed> hits = new ArrayList<TriggerFireOrder.Placed>();
		hits.add(new TriggerFireOrder.Placed(0, sk));
		hits.add(new TriggerFireOrder.Placed(1, round));
		TriggerFireOrder.sort(source, hits);
		assertEquals(1, hits.get(0).entryIndex);
		assertEquals(round, hits.get(0).start);
		assertEquals(0, hits.get(1).entryIndex);
		assertEquals(sk, hits.get(1).start);
	}

	@Test
	public void lowerIndexBeatsALeftwardStartOnTheSameLine() {
		String source = "Name earned $70 for a vermin.";
		int price = source.indexOf("earned");
		int vermin = source.indexOf("for a vermin");
		assertTrue(vermin > price);
		ArrayList<TriggerFireOrder.Placed> hits = new ArrayList<TriggerFireOrder.Placed>();
		hits.add(new TriggerFireOrder.Placed(1, price));
		hits.add(new TriggerFireOrder.Placed(0, vermin));
		TriggerFireOrder.sort(source, hits);
		assertEquals(0, hits.get(0).entryIndex);
		assertEquals(vermin, hits.get(0).start);
		assertEquals(1, hits.get(1).entryIndex);
		assertEquals(price, hits.get(1).start);
	}

	@Test
	public void sameIndexSameLineUsesSmallerStart() {
		String source = "earned earned";
		int first = source.indexOf("earned");
		int second = source.indexOf("earned", first + 1);
		assertEquals(0, TriggerFireOrder.lineOf(source, first));
		assertEquals(0, TriggerFireOrder.lineOf(source, second));
		ArrayList<TriggerFireOrder.Placed> hits = new ArrayList<TriggerFireOrder.Placed>();
		hits.add(new TriggerFireOrder.Placed(0, second));
		hits.add(new TriggerFireOrder.Placed(0, first));
		TriggerFireOrder.sort(source, hits);
		assertEquals(first, hits.get(0).start);
		assertEquals(second, hits.get(1).start);
	}

	@Test
	public void spanStartingOnLineZeroStaysLineZeroWhenTheMatchContainsANewline() {
		String source = "gold: 12\nsilver: 40\n";
		String matched = "gold: 12\nsilver: 40";
		assertTrue(matched.indexOf('\n') >= 0);
		int silver = source.indexOf("silver");
		assertEquals(0, TriggerFireOrder.lineOf(source, 0));
		assertEquals(1, TriggerFireOrder.lineOf(source, silver));
		ArrayList<TriggerFireOrder.Placed> hits = new ArrayList<TriggerFireOrder.Placed>();
		hits.add(new TriggerFireOrder.Placed(0, silver));
		hits.add(new TriggerFireOrder.Placed(1, 0));
		TriggerFireOrder.sort(source, hits);
		assertEquals(1, hits.get(0).entryIndex);
		assertEquals(0, hits.get(0).start);
		assertEquals(0, hits.get(1).entryIndex);
		assertEquals(silver, hits.get(1).start);
	}
}

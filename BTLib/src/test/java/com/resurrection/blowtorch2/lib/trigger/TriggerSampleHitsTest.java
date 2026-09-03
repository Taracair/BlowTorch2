package com.resurrection.blowtorch2.lib.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class TriggerSampleHitsTest {

	private static TriggerData line(final String name, final String pattern,
			final boolean regex) {
		TriggerData t = new TriggerData();
		t.setName(name);
		t.setPattern(pattern);
		t.setInterpretAsRegex(regex);
		t.setEnabled(true);
		return t;
	}

	@Test
	public void emptySampleDoesNotWalk() {
		TriggerData channel = line("_channel", "CORPCHAT:", true);
		List<TriggerSampleHits.Candidate> cands =
				new ArrayList<TriggerSampleHits.Candidate>();
		cands.add(TriggerSampleHits.Candidate.tryCreate(TriggerSampleHits.MAIN_PLUGIN, channel));
		assertTrue(TriggerSampleHits.matchingLabels("", cands, null, null, 8)
				.isEmpty());
	}

	@Test
	public void sampleLineListsOtherHitsAndSkipsSelf() {
		TriggerData channel = line("_channel", "CORPCHAT:", true);
		TriggerData opens = line("_opens", "opens", true);
		TriggerData unused = line("_combat", "You hit", true);
		List<TriggerSampleHits.Candidate> cands =
				new ArrayList<TriggerSampleHits.Candidate>();
		cands.add(TriggerSampleHits.Candidate.tryCreate(TriggerSampleHits.MAIN_PLUGIN, channel));
		cands.add(TriggerSampleHits.Candidate.tryCreate(TriggerSampleHits.MAIN_PLUGIN, opens));
		cands.add(TriggerSampleHits.Candidate.tryCreate(TriggerSampleHits.MAIN_PLUGIN, unused));
		List<String> hits = TriggerSampleHits.matchingLabels(
				"CORPCHAT: Name opens north", cands, TriggerSampleHits.MAIN_PLUGIN,
				Collections.singletonList("_opens"), 8);
		assertEquals(1, hits.size());
		assertEquals("_channel (10)", hits.get(0));
		assertEquals("Also matches: _channel (10)", TriggerSampleHits.formatHits(hits));
	}

	@Test
	public void renameStillSkipsTheSavedRow() {
		TriggerData opens = line("_opens", "opens", true);
		TriggerData channel = line("_channel", "CORPCHAT:", true);
		List<TriggerSampleHits.Candidate> cands =
				new ArrayList<TriggerSampleHits.Candidate>();
		cands.add(TriggerSampleHits.Candidate.tryCreate(TriggerSampleHits.MAIN_PLUGIN, channel));
		cands.add(TriggerSampleHits.Candidate.tryCreate(TriggerSampleHits.MAIN_PLUGIN, opens));
		List<String> hits = TriggerSampleHits.matchingLabels(
				"CORPCHAT: Name opens north", cands, TriggerSampleHits.MAIN_PLUGIN,
				Arrays.asList("_opens_v2", "_opens"), 8);
		assertEquals(Collections.singletonList("_channel (10)"), hits);
	}

	@Test
	public void skipsDisabledEmptyGmcpMcpAndGestures() {
		TriggerData off = line("_off", "opens", true);
		off.setEnabled(false);
		assertFalse(TriggerSampleHits.isLineTrigger(off));
		assertFalse(TriggerSampleHits.isLineTrigger(line("", "opens", true)));
		TriggerData gmcp = line("_gmcp", "%Char.Vitals", false);
		assertFalse(TriggerSampleHits.isLineTrigger(gmcp));
		TriggerData mcp = line("_mcp", "@dns-org-mud-moo-simpleedit", false);
		assertFalse(TriggerSampleHits.isLineTrigger(mcp));
		TriggerData wave = line("_wave", "!wave", false);
		assertFalse(TriggerSampleHits.isLineTrigger(wave));
		assertNull(TriggerSampleHits.Candidate.tryCreate(TriggerSampleHits.MAIN_PLUGIN, gmcp));
	}

	@Test
	public void capsTheList() {
		List<TriggerSampleHits.Candidate> cands =
				new ArrayList<TriggerSampleHits.Candidate>();
		for (int i = 0; i < 5; i++) {
			cands.add(TriggerSampleHits.Candidate.tryCreate(TriggerSampleHits.MAIN_PLUGIN,
					line("_t" + i, "opens", true)));
		}
		List<String> hits = TriggerSampleHits.matchingLabels(
				"Name opens north", cands, null, null, 2);
		assertEquals(3, hits.size());
		assertEquals("and 3 more", hits.get(2));
	}

	@Test
	public void pluginPrefixOnLabel() {
		TriggerData t = line("_opens", "opens", true);
		TriggerSampleHits.Candidate c = TriggerSampleHits.Candidate.tryCreate(
				"pack-a", t);
		assertEquals("pack-a: _opens (10)", c.label());
	}

	@Test
	public void labelsIncludeSequenceSoReplaceVsColourIsVisible() {
		TriggerData channel = line("_channel", "CORPCHAT:", true);
		channel.setSequence(10);
		TriggerData colour = line("_colour", "opens", true);
		colour.setSequence(11);
		List<TriggerSampleHits.Candidate> cands =
				new ArrayList<TriggerSampleHits.Candidate>();
		cands.add(TriggerSampleHits.Candidate.tryCreate(TriggerSampleHits.MAIN_PLUGIN, colour));
		cands.add(TriggerSampleHits.Candidate.tryCreate(TriggerSampleHits.MAIN_PLUGIN, channel));
		List<String> hits = TriggerSampleHits.matchingLabels(
				"CORPCHAT: Name opens north", cands, null, null, 8);
		assertEquals(Arrays.asList("_channel (10)", "_colour (11)"), hits);
	}
}

package com.resurrection.blowtorch2.lib.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UserManualMarkdownTest {

	@Test
	public void boldAndCodeMarkersBecomeRunsNotGlyphs() {
		UserManualMarkdown.Result r = UserManualMarkdown.render(
				"The star is **Add to favorites** and `.chat` opens Chat.");
		assertEquals("The star is Add to favorites and .chat opens Chat.", r.text);
		assertEquals(2, r.runs.size());
		assertEquals(UserManualMarkdown.KIND_BOLD, r.runs.get(0).kind);
		assertEquals("Add to favorites",
				r.text.substring(r.runs.get(0).start, r.runs.get(0).end));
		assertEquals(UserManualMarkdown.KIND_CODE, r.runs.get(1).kind);
		assertEquals(".chat", r.text.substring(r.runs.get(1).start, r.runs.get(1).end));
	}

	@Test
	public void httpLinkKeepsLabelAndHref() {
		UserManualMarkdown.Result r = UserManualMarkdown.render(
				"report on [GitHub Issues](https://github.com/Taracair/BlowTorch2/issues).");
		assertEquals("report on GitHub Issues.", r.text);
		assertEquals(1, r.runs.size());
		assertEquals(UserManualMarkdown.KIND_LINK, r.runs.get(0).kind);
		assertEquals("https://github.com/Taracair/BlowTorch2/issues", r.runs.get(0).href);
	}

	@Test
	public void unmatchedMarkersAreDropped() {
		UserManualMarkdown.Result r = UserManualMarkdown.render("cost is **five and `x");
		assertEquals("cost is five and x", r.text);
		assertTrue(r.runs.isEmpty());
	}

	@Test
	public void h3LineIsBoldWithoutHashes() {
		UserManualMarkdown.Result r = UserManualMarkdown.render(
				"### 1. A shortcut that takes an argument\n\nType kk goblin.");
		assertTrue(r.text.startsWith("1. A shortcut that takes an argument"));
		assertTrue(r.text.contains("Type kk goblin."));
		assertEquals(UserManualMarkdown.KIND_BOLD, r.runs.get(0).kind);
		assertEquals("1. A shortcut that takes an argument",
				r.text.substring(r.runs.get(0).start, r.runs.get(0).end));
	}

	@Test
	public void fencedBlockIsCodeAndFenceLinesGoAway() {
		UserManualMarkdown.Result r = UserManualMarkdown.render(
				"before\n```\n.suggest on\n```\nafter");
		assertEquals("before\n\n.suggest on\n\nafter", r.text);
		boolean found = false;
		for (int i = 0; i < r.runs.size(); i++) {
			UserManualMarkdown.Run run = r.runs.get(i);
			if (run.kind == UserManualMarkdown.KIND_CODE
					&& ".suggest on".equals(r.text.substring(run.start, run.end))) {
				found = true;
			}
		}
		assertTrue(found);
	}

	@Test
	public void h4LineIsBoldWithoutHashes() {
		UserManualMarkdown.Result r = UserManualMarkdown.render("#### Route A\nGo north.");
		assertTrue(r.text.startsWith("Route A"));
		assertEquals("Route A", r.text.substring(r.runs.get(0).start, r.runs.get(0).end));
	}

	@Test
	public void emptyIsEmpty() {
		assertEquals("", UserManualMarkdown.render(null).text);
		assertEquals("", UserManualMarkdown.render("").text);
	}

	@Test
	public void hardWrappedParagraphBecomesOneLine() {
		UserManualMarkdown.Result r = UserManualMarkdown.render(
				"**Options, Help, Chat, and the rest** live behind the three dots (⋮)\n"
						+ "at the\n"
						+ "top of the game window. Tap ⋮ → **Options** for settings.");
		assertEquals(
				"Options, Help, Chat, and the rest live behind the three dots (⋮) "
						+ "at the top of the game window. Tap ⋮ → Options for settings.",
				r.text);
		assertTrue(!r.text.contains("\n"));
		assertEquals("Options, Help, Chat, and the rest",
				r.text.substring(r.runs.get(0).start, r.runs.get(0).end));
	}

	@Test
	public void blankLineKeepsTwoParagraphs() {
		UserManualMarkdown.Result r = UserManualMarkdown.render(
				"First paragraph here.\nSecond line of first.\n\nSecond paragraph.");
		assertEquals("First paragraph here. Second line of first.\n\nSecond paragraph.",
				r.text);
	}

	@Test
	public void indentedExampleKeepsNewlines() {
		UserManualMarkdown.Result r = UserManualMarkdown.render(
				"Intro line\n    .suggest on\n    .suggest where");
		assertTrue(r.text.contains("\n    .suggest on\n    .suggest where"));
	}

	@Test
	public void listItemsStayOnSeparateLines() {
		UserManualMarkdown.Result r = UserManualMarkdown.render("- aaa\n- bbb");
		assertEquals("- aaa\n- bbb", r.text);
	}

	@Test
	public void wrappedListItemJoinsContinuation() {
		UserManualMarkdown.Result r = UserManualMarkdown.render(
				"- `.suggest where floating` floats them over the game text,\n"
						+ "  resting on the top edge of the input bar.");
		assertEquals(
				"- .suggest where floating floats them over the game text, "
						+ "resting on the top edge of the input bar.",
				r.text);
	}

	@Test
	public void wrappedBlockquoteJoins() {
		UserManualMarkdown.Result r = UserManualMarkdown.render(
				"> This used to be suggest short. It was renamed because\n"
						+ "> the name promised something else.");
		assertEquals(
				"> This used to be suggest short. It was renamed because "
						+ "the name promised something else.",
				r.text);
	}

	@Test
	public void indentedFenceUnderAListItemStaysAFence() {
		UserManualMarkdown.Result r = UserManualMarkdown.render(
				"2. Make one trigger:\n   ```\n   /EnableTriggerGroup(\"combat\", true)\n   ```\nafter");
		assertTrue(r.text.contains("2. Make one trigger:"));
		assertTrue(r.text.contains("/EnableTriggerGroup(\"combat\", true)"));
		assertTrue(!r.text.contains("```"));
		assertTrue(r.text.contains("\n"));
		boolean found = false;
		for (int i = 0; i < r.runs.size(); i++) {
			UserManualMarkdown.Run run = r.runs.get(i);
			if (run.kind == UserManualMarkdown.KIND_CODE
					&& r.text.substring(run.start, run.end)
							.contains("EnableTriggerGroup")) {
				found = true;
			}
		}
		assertTrue(found);
	}

	@Test
	public void tableRowsStayOnSeparateLines() {
		UserManualMarkdown.Result r = UserManualMarkdown.render("| a |\n| b |");
		assertEquals("| a |\n| b |", r.text);
	}
}

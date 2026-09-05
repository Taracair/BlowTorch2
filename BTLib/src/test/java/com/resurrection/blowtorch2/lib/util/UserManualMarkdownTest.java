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
}

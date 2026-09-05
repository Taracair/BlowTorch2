package com.resurrection.blowtorch2.lib.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/** Fixture-only parse/filter for the in-app Help accordion. */
public class UserManualIndexTest {

	private static final String FIXTURE =
			"# BlowTorch User Manual\n"
					+ "\n"
					+ "Source of truth. This preamble is dropped.\n"
					+ "\n"
					+ "## Before you start\n"
					+ "\n"
					+ "Star a world on the list.\n"
					+ "\n"
					+ "## Encrypted connections (TLS)\n"
					+ "\n"
					+ "Turn TLS on only when the world offers a TLS port.\n"
					+ "\n"
					+ "## Recipes\n"
					+ "\n"
					+ "### 1. A shortcut that takes an argument\n"
					+ "\n"
					+ "Type kk goblin.\n"
					+ "\n"
					+ "## A heading nobody mapped\n"
					+ "\n"
					+ "Lands in Other.\n";

	@Test
	public void dropsPreambleAndSplitsOnH2() {
		List<UserManualIndex.Section> sections = UserManualIndex.parse(FIXTURE);
		assertEquals(4, sections.size());
		assertEquals("Before you start", sections.get(0).title);
		assertEquals("Star a world on the list.", sections.get(0).body);
		assertEquals(UserManualIndex.CATEGORY_START, sections.get(0).category);
	}

	@Test
	public void knownTitlesLandInHelpCommandGroups() {
		List<UserManualIndex.Section> sections = UserManualIndex.parse(FIXTURE);
		assertEquals(UserManualIndex.CATEGORY_PLAYING, sections.get(1).category);
		assertEquals(UserManualIndex.CATEGORY_RECIPES, sections.get(2).category);
	}

	@Test
	public void h3StaysInsideTheParentBody() {
		List<UserManualIndex.Section> sections = UserManualIndex.parse(FIXTURE);
		assertTrue(sections.get(2).body.contains("### 1. A shortcut that takes an argument"));
		assertTrue(sections.get(2).body.contains("Type kk goblin."));
	}

	@Test
	public void unknownHeadingGoesToOther() {
		List<UserManualIndex.Section> sections = UserManualIndex.parse(FIXTURE);
		assertEquals("A heading nobody mapped", sections.get(3).title);
		assertEquals(UserManualIndex.CATEGORY_OTHER, sections.get(3).category);
	}

	@Test
	public void shortQueryKeepsEverySection() {
		List<UserManualIndex.Section> all = UserManualIndex.parse(FIXTURE);
		assertEquals(all.size(), UserManualIndex.filter(all, "").size());
		assertEquals(all.size(), UserManualIndex.filter(all, "t").size());
	}

	@Test
	public void filterMatchesTitleOrBody() {
		List<UserManualIndex.Section> all = UserManualIndex.parse(FIXTURE);
		List<UserManualIndex.Section> tls = UserManualIndex.filter(all, "tls");
		assertEquals(1, tls.size());
		assertEquals("Encrypted connections (TLS)", tls.get(0).title);
		List<UserManualIndex.Section> goblin = UserManualIndex.filter(all, "goblin");
		assertEquals(1, goblin.size());
		assertEquals("Recipes", goblin.get(0).title);
	}

	@Test
	public void highlightIsCaseInsensitiveAndNonOverlapping() {
		List<UserManualIndex.Hit> hits = UserManualIndex.highlightRanges(
				"Turn TLS on. tls later.", "TLS");
		assertEquals(2, hits.size());
		assertEquals(5, hits.get(0).start);
		assertEquals(8, hits.get(0).end);
	}

	@Test
	public void emptyInputIsEmptyList() {
		assertTrue(UserManualIndex.parse("").isEmpty());
		assertTrue(UserManualIndex.parse(null).isEmpty());
	}

	@Test
	public void shippedManualHeadingsAreMapped() throws Exception {
		File f = new File("res/raw/user_manual.txt");
		if (!f.isFile()) {
			f = new File("BTLib/res/raw/user_manual.txt");
		}
		assertTrue("packaged user_manual.txt", f.isFile());
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(f), "UTF-8"));
		StringBuilder sb = new StringBuilder();
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append('\n');
			}
		} finally {
			reader.close();
		}
		List<UserManualIndex.Section> sections = UserManualIndex.parse(sb.toString());
		assertTrue(sections.size() > 10);
		boolean sawServerList = false;
		boolean sawPadding = false;
		boolean sawSemicolon = false;
		boolean sawChatDrawer = false;
		boolean sawChatLogsHeading = false;
		for (int i = 0; i < sections.size(); i++) {
			UserManualIndex.Section s = sections.get(i);
			if ("The server list".equals(s.title)) {
				sawServerList = true;
				assertEquals(UserManualIndex.CATEGORY_START, s.category);
				assertTrue(s.body.contains("On the server list"));
			}
			if ("Before you start".equals(s.title)) {
				assertTrue(!s.body.contains("On the server list"));
				assertTrue(!s.body.contains("Session logs"));
				assertTrue(s.body.contains("Edit buttons"));
				assertTrue(s.body.contains("LOOK"));
			}
			if ("Chat, logs, and Options search".equals(s.title)) {
				sawChatLogsHeading = true;
			}
			if ("Several commands on one line (`;`)".equals(s.title)) {
				sawSemicolon = true;
				assertEquals(UserManualIndex.CATEGORY_PLAYING, s.category);
				assertTrue(s.body.contains("#;"));
			}
			if ("Chat drawer".equals(s.title)) {
				sawChatDrawer = true;
				assertEquals(UserManualIndex.CATEGORY_WINDOW, s.category);
			}
			if ("Extra text windows".equals(s.title)) {
				assertTrue(s.body.contains("not the chat drawer"));
			}
			if ("Newest text at top".equals(s.title)) {
				assertTrue(!s.body.contains("Top padding (px)"));
			}
			if ("Padding, notch, and the keyboard".equals(s.title)) {
				sawPadding = true;
				assertEquals(UserManualIndex.CATEGORY_WINDOW, s.category);
				assertTrue(s.body.contains("Top padding (px)"));
			}
			if (UserManualIndex.CATEGORY_OTHER.equals(s.category)
					&& !"Related docs".equals(s.title)) {
				fail("unmapped Help heading: " + s.title);
			}
		}
		assertTrue("The server list heading", sawServerList);
		assertTrue("Padding heading", sawPadding);
		assertTrue("semicolon heading", sawSemicolon);
		assertTrue("Chat drawer heading", sawChatDrawer);
		assertTrue("Chat, logs heading must be gone", !sawChatLogsHeading);
	}

	@Test
	public void firstOpenExpandsBeforeYouStartAndServerList() {
		java.util.HashSet<String> cats = new java.util.HashSet<String>();
		java.util.HashSet<String> leaves = new java.util.HashSet<String>();
		UserManualIndex.seedFirstOpen(cats, leaves);
		assertEquals(1, cats.size());
		assertTrue(cats.contains(UserManualIndex.CATEGORY_START));
		assertTrue(leaves.contains("Before you start"));
		assertTrue(leaves.contains("The server list"));
		assertEquals(2, leaves.size());
	}

	@Test
	public void hitCountIsTitlePlusBody() {
		UserManualIndex.Section s = new UserManualIndex.Section(
				UserManualIndex.CATEGORY_TRIGGERS, "Aliases and triggers",
				"A trigger fires. Another trigger.");
		assertEquals(3, UserManualIndex.hitCount(s, "trigger"));
	}

	@Test
	public void hitCountUsesRenderedBodyNotMarkers() {
		UserManualIndex.Section s = new UserManualIndex.Section(
				UserManualIndex.CATEGORY_TRIGGERS, "Aliases",
				"A **trigger** fires.");
		assertEquals(1, UserManualIndex.hitCount(s, "trigger"));
		assertEquals(0, UserManualIndex.hitCount(s, "**"));
	}

	@Test
	public void paintOrderFollowsCategoriesNotFileOrder() {
		List<UserManualIndex.Section> fileOrder = new ArrayList<UserManualIndex.Section>();
		fileOrder.add(new UserManualIndex.Section(
				UserManualIndex.CATEGORY_INPUT, "Suggestions", "chip"));
		fileOrder.add(new UserManualIndex.Section(
				UserManualIndex.CATEGORY_PLAYING, "Dot commands", "north"));
		List<UserManualIndex.Section> painted = new ArrayList<UserManualIndex.Section>();
		java.util.LinkedHashMap<String, List<UserManualIndex.Section>> byCat =
				UserManualIndex.groupByCategory(fileOrder);
		for (java.util.Map.Entry<String, List<UserManualIndex.Section>> e : byCat.entrySet()) {
			painted.addAll(e.getValue());
		}
		assertEquals("Dot commands", painted.get(0).title);
		assertEquals("Suggestions", painted.get(1).title);
	}
}

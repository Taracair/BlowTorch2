package com.resurrection.blowtorch2.lib.service.plugin.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.WindowToken;

/**
 * Options search index. Display-only: breadcrumbs and jump paths, no mutation.
 */
public class OptionsDialogSearchTest {

	@Test
	public void gmcpFindsUseGmcpUnderServiceProtocols() {
		SettingsGroup root = new SettingsGroup();
		root.setTitle("Program Settings");
		SettingsGroup service = new SettingsGroup();
		service.setTitle("Service");
		SettingsGroup protocols = new SettingsGroup();
		protocols.setTitle("Protocols");
		BooleanOption gmcp = new BooleanOption();
		gmcp.setTitle("Use GMCP?");
		gmcp.setKey("use_gmcp");
		gmcp.setValue(true);
		protocols.addOption(gmcp);
		service.addOption(protocols);
		root.addOption(service);

		ArrayList<OptionsDialog.SearchHit> hits = OptionsDialog.searchHits(root, "gmcp");
		assertEquals(1, hits.size());
		assertEquals("Use GMCP?", hits.get(0).title);
		assertEquals("Service › Protocols", hits.get(0).breadcrumb);
		assertEquals(1, hits.get(0).path.size());
		assertSame(service, hits.get(0).path.get(0));
		assertSame(gmcp, hits.get(0).option);
	}

	@Test
	public void ghostFindsTheSuggestionsPageOption() {
		SettingsGroup root = new SettingsGroup();
		root.setTitle("Program Settings");
		SettingsGroup input = new SettingsGroup();
		input.setTitle("Input");
		SettingsGroup suggestions = new SettingsGroup();
		suggestions.setTitle("Suggestions");
		BooleanOption ghost = new BooleanOption();
		ghost.setTitle("Ghost after the cursor");
		ghost.setKey("word_complete_ghost");
		ghost.setValue(true);
		suggestions.addOption(ghost);
		input.addOption(suggestions);
		root.addOption(input);

		ArrayList<OptionsDialog.SearchHit> hits = OptionsDialog.searchHits(root, "ghost");
		assertEquals(1, hits.size());
		assertEquals("Ghost after the cursor", hits.get(0).title);
		assertEquals("Input › Suggestions", hits.get(0).breadcrumb);
		assertEquals(2, hits.get(0).path.size());
		assertSame(input, hits.get(0).path.get(0));
		assertSame(suggestions, hits.get(0).path.get(1));
	}

	@Test
	public void hiddenEditorKeysStayOutOfTheIndex() {
		SettingsGroup page = new SettingsGroup();
		page.setTitle("Window");
		BooleanOption owned = new BooleanOption();
		owned.setTitle("Show gesture hints");
		owned.setKey("show_gesture_hints");
		owned.setValue(true);
		page.addOption(owned);
		BooleanOption keep = new BooleanOption();
		keep.setTitle("Enable Hyperlinks?");
		keep.setKey("hyperlinks_enabled");
		keep.setValue(true);
		page.addOption(keep);

		ArrayList<OptionsDialog.SearchHit> hits = OptionsDialog.searchHits(page, "gesture",
				new HashSet<String>(Collections.singleton("show_gesture_hints")));
		assertEquals(0, hits.size());
		assertNotNull(page.findOptionByKey("show_gesture_hints"));
		assertFalse(OptionsDialog.searchHits(page, "hyperlink").isEmpty());
	}

	@Test
	public void windowTokenAndroidFlingIsSearchable() {
		SettingsGroup window = new WindowToken().getSettings();
		ArrayList<OptionsDialog.SearchHit> hits = OptionsDialog.searchHits(window, "android fling");
		assertTrue(named(hits, "Android fling?") != null);
	}

	@Test
	public void windowTokenScrollDatesIsSearchable() {
		SettingsGroup window = new WindowToken().getSettings();
		ArrayList<OptionsDialog.SearchHit> hits = OptionsDialog.searchHits(window, "scroll date");
		assertTrue(named(hits, "Scroll dates?") != null);
		assertTrue(named(hits, "Scroll date opacity (%)") != null);
	}

	@Test
	public void emptyQueryReturnsNothing() {
		assertTrue(OptionsDialog.searchHits(new WindowToken().getSettings(), "  ").isEmpty());
		assertTrue(OptionsDialog.searchHits(new WindowToken().getSettings(), null).isEmpty());
	}

	private static OptionsDialog.SearchHit named(ArrayList<OptionsDialog.SearchHit> hits,
			String title) {
		for (int i = 0; i < hits.size(); i++) {
			if (title.equals(hits.get(i).title)) {
				return hits.get(i);
			}
		}
		return null;
	}
}

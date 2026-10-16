package com.resurrection.blowtorch2.lib.service.plugin.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.WindowToken;

/**
 * Options search index: breadcrumbs, jump paths, and which control a hit shows.
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
		assertEquals(2, hits.get(0).path.size());
		assertSame(service, hits.get(0).path.get(0));
		assertSame(protocols, hits.get(0).path.get(1));
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

	@Test
	public void highlightKeyIsTheHitOptionKey() {
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
		assertEquals("use_gmcp", OptionsDialog.highlightKey(hits.get(0)));
		assertNull(OptionsDialog.highlightKey(null));
		assertNull(OptionsDialog.highlightKey(
				new OptionsDialog.SearchHit("x", "", new ArrayList<SettingsGroup>(), null)));
	}

	@Test
	public void inlinedSearchHitIsTheRowIndexOfOptionFinds() {
		SettingsGroup root = new SettingsGroup();
		root.setTitle("Program Settings");
		SettingsGroup service = new SettingsGroup();
		service.setTitle("Service");
		SettingsGroup protocols = new SettingsGroup();
		protocols.setTitle("Where they appear");
		BooleanOption gmcp = new BooleanOption();
		gmcp.setTitle("Use GMCP?");
		gmcp.setKey("use_gmcp");
		gmcp.setValue(true);
		protocols.addOption(gmcp);
		service.addOption(protocols);
		root.addOption(service);

		ArrayList<OptionsDialog.SearchHit> hits = OptionsDialog.searchHits(root, "gmcp");
		assertEquals(1, hits.get(0).path.size());
		assertSame(service, hits.get(0).path.get(0));
		ArrayList<OptionsDialog.PageRow> rows = OptionsDialog.pageRows(service);
		int at = OptionsDialog.indexOfOption(rows, hits.get(0).option);
		assertTrue(at >= 0);
		assertFalse(rows.get(at).isHeader());
		assertSame(gmcp, rows.get(at).option);
		assertEquals(-1, OptionsDialog.indexOfOption(rows, protocols));

		BooleanOption sameKey = new BooleanOption();
		sameKey.setKey("use_gmcp");
		assertEquals(at, OptionsDialog.indexOfOption(rows, sameKey));
		assertTrue(OptionsDialog.optionMatchesHighlight(gmcp, sameKey));
		assertFalse(OptionsDialog.optionMatchesHighlight(gmcp, null));
	}

	@Test
	public void searchRowControlIsCheckboxNumberOrNone() {
		SettingsGroup window = new WindowToken().getSettings();
		OptionsDialog.SearchHit padding = named(
				OptionsDialog.searchHits(window, "bottom padding"), "Bottom padding (px)");
		assertNotNull(padding);
		assertTrue(padding.option instanceof IntegerOption);
		assertEquals(OptionsDialog.SearchControl.NUMBER, OptionsDialog.searchRowControl(padding));

		IntegerOption opacity = new IntegerOption();
		opacity.setTitle("Overflow button opacity (%)");
		opacity.setKey("overflow_button_opacity");
		opacity.setValue(5);
		assertEquals(OptionsDialog.SearchControl.NUMBER,
				OptionsDialog.searchRowControl(hit(opacity)));

		OptionsDialog.SearchHit fling = named(
				OptionsDialog.searchHits(window, "android fling"), "Android fling?");
		assertNotNull(fling);
		assertTrue(fling.option instanceof BooleanOption);
		assertEquals(OptionsDialog.SearchControl.CHECKBOX, OptionsDialog.searchRowControl(fling));

		OptionsDialog.SearchHit sensitivity = named(
				OptionsDialog.searchHits(window, "scroll sensitivity"), "Scroll sensitivity");
		assertNotNull(sensitivity);
		assertTrue(sensitivity.option instanceof ListOption);
		assertEquals(OptionsDialog.SearchControl.NONE,
				OptionsDialog.searchRowControl(sensitivity));

		OptionsDialog.SearchHit font = named(OptionsDialog.searchHits(window, "font"), "Font");
		assertNotNull(font);
		assertTrue(font.option instanceof FileOption);
		assertEquals(OptionsDialog.SearchControl.NONE, OptionsDialog.searchRowControl(font));

		OptionsDialog.SearchHit color = named(
				OptionsDialog.searchHits(window, "hyperlink color"), "Hyperlink Color");
		assertNotNull(color);
		assertTrue(color.option instanceof ColorOption);
		assertEquals(OptionsDialog.SearchControl.NONE, OptionsDialog.searchRowControl(color));

		OptionsDialog.SearchHit layout = named(
				OptionsDialog.searchHits(window, "layout"), "Layout");
		assertNotNull(layout);
		assertEquals(Option.TYPE.GROUP, layout.option.type);
		assertEquals(OptionsDialog.SearchControl.NONE, OptionsDialog.searchRowControl(layout));

		assertEquals(OptionsDialog.SearchControl.NONE,
				OptionsDialog.searchRowControl(hit(new StringOption())));
		assertEquals(OptionsDialog.SearchControl.NONE,
				OptionsDialog.searchRowControl(hit(new EncodingOption())));
		assertEquals(OptionsDialog.SearchControl.NONE,
				OptionsDialog.searchRowControl(hit(new CallbackOption())));
		assertEquals(OptionsDialog.SearchControl.NONE, OptionsDialog.searchRowControl(null));
		assertEquals(OptionsDialog.SearchControl.NONE, OptionsDialog.searchRowControl(
				new OptionsDialog.SearchHit("x", "", new ArrayList<SettingsGroup>(), null)));
	}

	private static OptionsDialog.SearchHit hit(Option option) {
		return new OptionsDialog.SearchHit(
				option == null ? "" : option.getTitle(),
				"",
				new ArrayList<SettingsGroup>(),
				option);
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

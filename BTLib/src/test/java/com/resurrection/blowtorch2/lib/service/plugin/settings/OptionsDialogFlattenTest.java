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
 * Display flattening in OptionsAdapter. The SettingsGroup tree must stay
 * nested (serialisation walks it); only the list rows change.
 */
public class OptionsDialogFlattenTest {

	@Test
	public void windowDrillsIntoTextLayoutLinksAndInputBar() {
		SettingsGroup window = new WindowToken().getSettings();
		assertEquals("Window", window.getTitle());
		assertEquals(4, window.getOptions().size());

		SettingsGroup text = (SettingsGroup) childNamed(window, "Text");
		SettingsGroup layout = (SettingsGroup) childNamed(window, "Layout");
		SettingsGroup links = (SettingsGroup) childNamed(window, "Links");
		SettingsGroup inputBar = (SettingsGroup) childNamed(window, "Input bar");
		assertNotNull(text);
		assertNotNull(layout);
		assertNotNull(links);
		assertNotNull(inputBar);
		assertEquals("hyperlinks_options", links.getKey());

		ArrayList<OptionsDialog.PageRow> rows = OptionsDialog.pageRows(window);
		assertEquals(4, rows.size());
		assertSame(text, rows.get(0).option);
		assertSame(layout, rows.get(1).option);
		assertSame(links, rows.get(2).option);
		assertSame(inputBar, rows.get(3).option);
		assertFalse(hasHeader(rows, "Links"));
		assertFalse(hasHeader(rows, "Text"));
		assertNull(optionNamed(rows, "Font"));
		assertNull(optionNamed(rows, "Use OSC 8?"));

		assertEquals("osc8_links", links.findOptionByKey("osc8_links").getKey());
		assertEquals("hyperlinks_enabled",
				links.findOptionByKey("hyperlinks_enabled").getKey());
		assertSame(window.findOptionByKey("font_path"), text.findOptionByKey("font_path"));
		assertSame(window.findOptionByKey("top_padding"),
				layout.findOptionByKey("top_padding"));
		assertSame(window.findOptionByKey("input_bar_show_edit"),
				inputBar.findOptionByKey("input_bar_show_edit"));
		assertTrue(text.findOptionByKey("font_path") instanceof FileOption);
		for (WindowToken.OPTION_KEY key : WindowToken.OPTION_KEY.values()) {
			assertNotNull(key.name(), window.findOptionByKey(key.name()));
		}
	}

	@Test
	public void windowFontPickerDoesNotDumpSystemFonts() {
		SettingsGroup window = new WindowToken().getSettings();
		Option found = window.findOptionByKey("font_path");
		assertTrue(found instanceof FileOption);
		FileOption font = (FileOption) found;
		assertFalse("listing /system/fonts/ is why the picker was a wall of Noto cuts",
				font.paths.contains("/system/fonts/"));
		assertTrue(font.getItems().contains("fonts/DejaVuSansMono.ttf"));
		assertFalse(font.getItems().contains("fonts/VeraMono.ttf"));
		assertTrue(font.extensions.contains(".otf"));
	}

	@Test
	public void windowStillFindsNestedKeysForUpdate() {
		SettingsGroup window = new WindowToken().getSettings();
		Option found = window.findOptionByKey("hyperlinks_enabled");
		assertNotNull(found);
		assertTrue(found instanceof BooleanOption);
		window.updateBoolean("hyperlinks_enabled", false);
		assertEquals(Boolean.FALSE, ((BooleanOption) found).getValue());
	}

	@Test
	public void extraTextWindowsDrillInUnderPanes() {
		SettingsGroup panes = new SettingsGroup();
		panes.setTitle("Panes");
		SettingsGroup extra = new SettingsGroup();
		extra.setTitle("Extra text windows");
		extra.setKey("extra_text_group");
		BooleanOption enabled = new BooleanOption();
		enabled.setTitle("Enable Extra Text Windows?");
		enabled.setKey("extra_text_windows_enabled");
		enabled.setValue(true);
		extra.addOption(enabled);
		panes.addOption(extra);

		ArrayList<OptionsDialog.PageRow> rows = OptionsDialog.pageRows(panes);
		assertEquals(1, rows.size());
		assertFalse(rows.get(0).isHeader());
		assertSame(extra, rows.get(0).option);
		assertFalse(hasHeader(rows, "Extra text windows"));
		assertNull(optionNamed(rows, "Enable Extra Text Windows?"));
		assertSame(enabled, panes.findOptionByKey("extra_text_windows_enabled"));
	}

	@Test
	public void protocolsPageDrillsIntoGmcpMcpAndTelnet() {
		SettingsGroup protocols = new SettingsGroup();
		protocols.setTitle("Protocols");
		BooleanOption useGmcp = new BooleanOption();
		useGmcp.setTitle("Use GMCP?");
		useGmcp.setKey("use_gmcp");
		useGmcp.setValue(false);
		protocols.addOption(useGmcp);
		SettingsGroup gmcp = namedGroup("GMCP", "log_gmcp", "Log GMCP?");
		SettingsGroup mcp = namedGroup("MCP", "log_mcp", "Log MCP?");
		SettingsGroup telnet = namedGroup("Telnet", "use_mtts", "Use MTTS?");
		protocols.addOption(gmcp);
		protocols.addOption(mcp);
		protocols.addOption(telnet);

		ArrayList<OptionsDialog.PageRow> rows = OptionsDialog.pageRows(protocols);
		assertSame(useGmcp, optionNamed(rows, "Use GMCP?"));
		assertSame(gmcp, optionNamed(rows, "GMCP"));
		assertSame(mcp, optionNamed(rows, "MCP"));
		assertSame(telnet, optionNamed(rows, "Telnet"));
		assertFalse(hasHeader(rows, "GMCP"));
		assertFalse(hasHeader(rows, "MCP"));
		assertFalse(hasHeader(rows, "Telnet"));
		assertNull(optionNamed(rows, "Log GMCP?"));
		assertNull(optionNamed(rows, "Use MTTS?"));
		assertSame(useGmcp, protocols.findOptionByKey("use_gmcp"));
		assertSame(gmcp.findOptionByKey("log_gmcp"),
				protocols.findOptionByKey("log_gmcp"));
	}

	@Test
	public void unnamedNestedGroupStillDrillsIn() {
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

		ArrayList<OptionsDialog.PageRow> rows = OptionsDialog.pageRows(input);
		assertEquals(1, rows.size());
		assertFalse(rows.get(0).isHeader());
		assertEquals(Option.TYPE.GROUP, rows.get(0).option.type);
		assertEquals("Suggestions", rows.get(0).option.getTitle());
		assertNull(optionNamed(rows, "Ghost after the cursor"));
	}

	@Test
	public void pluginGroupAtRootStaysADrillInRow() {
		SettingsGroup root = new SettingsGroup();
		root.setTitle("Program Settings");
		SettingsGroup display = new SettingsGroup();
		display.setTitle("Display");
		root.addOption(display);
		SettingsGroup plugin = new SettingsGroup();
		plugin.setTitle("A Plugin");
		BooleanOption opt = new BooleanOption();
		opt.setTitle("Plugin switch");
		opt.setKey("plugin_switch");
		opt.setValue(true);
		plugin.addOption(opt);
		root.addOption(plugin);

		ArrayList<OptionsDialog.PageRow> rows = OptionsDialog.pageRows(root);
		assertEquals(2, rows.size());
		assertEquals("Display", rows.get(0).option.getTitle());
		assertEquals("A Plugin", rows.get(1).option.getTitle());
		assertEquals(1, rows.get(1).sourceIndex);
		assertNull(optionNamed(rows, "Plugin switch"));
	}

	@Test
	public void suggestionsPageInlinesSpellingWhereAndOrderSections() {
		SettingsGroup suggestions = new SettingsGroup();
		suggestions.setTitle("Suggestions");
		BooleanOption master = new BooleanOption();
		master.setTitle("Suggest game words");
		master.setKey("word_complete");
		master.setValue(false);
		suggestions.addOption(master);
		suggestions.addOption(namedGroup("When spelling is inexact",
				"word_complete_typos", "Correct nearby misspellings"));
		suggestions.addOption(namedGroup("Whole names",
				"word_complete_phrases", "Offer whole names"));
		suggestions.addOption(namedGroup("Where they appear",
				"word_complete_ghost", "Ghost after the cursor"));
		suggestions.addOption(namedGroup("Order",
				"word_complete_rank", "Order by place in the line"));

		ArrayList<OptionsDialog.PageRow> rows = OptionsDialog.pageRows(suggestions);
		assertNotNull(optionNamed(rows, "Suggest game words"));
		assertTrue(hasHeader(rows, "When spelling is inexact"));
		assertTrue(hasHeader(rows, "Whole names"));
		assertTrue(hasHeader(rows, "Where they appear"));
		assertTrue(hasHeader(rows, "Order"));
		assertNull(optionNamed(rows, "When spelling is inexact"));
		assertEquals("word_complete_typos",
				optionNamed(rows, "Correct nearby misspellings").getKey());
		assertSame(suggestions.findOptionByKey("word_complete_typos"),
				optionNamed(rows, "Correct nearby misspellings"));
		assertSame(suggestions.findOptionByKey("word_complete_ghost"),
				optionNamed(rows, "Ghost after the cursor"));
	}

	@Test
	public void skipHeadAndWrongFirstAreNotLocked() {
		assertFalse(OptionsDialog.isRowLocked("word_complete_skip_head", false));
		assertFalse(OptionsDialog.isRowLocked("word_complete_wrong_first", false));
		assertFalse(OptionsDialog.isRowLocked("word_complete_skip_head", true));
		assertTrue(OptionsDialog.isRowLocked("scroll_sensitivity", true));
		assertFalse(OptionsDialog.isRowLocked("scroll_sensitivity", false));
		assertEquals("", OptionsDialog.lockedRequiresSuffix("word_complete_skip_head"));
		assertEquals("", OptionsDialog.lockedRequiresSuffix("scroll_sensitivity"));
	}

	@Test
	public void hiddenEditorKeysAreOmittedEvenWhenInlined() {
		SettingsGroup page = new SettingsGroup();
		page.setTitle("Window");
		SettingsGroup hyper = new SettingsGroup();
		hyper.setTitle("Where they appear");
		BooleanOption owned = new BooleanOption();
		owned.setTitle("Show gesture hints");
		owned.setKey("show_gesture_hints");
		owned.setValue(true);
		hyper.addOption(owned);
		BooleanOption keep = new BooleanOption();
		keep.setTitle("Enable Hyperlinks?");
		keep.setKey("hyperlinks_enabled");
		keep.setValue(true);
		hyper.addOption(keep);
		page.addOption(hyper);

		ArrayList<OptionsDialog.PageRow> rows = OptionsDialog.pageRows(page,
				new HashSet<String>(Collections.singleton("show_gesture_hints")));
		assertTrue(hasHeader(rows, "Where they appear"));
		assertNull(optionNamed(rows, "Show gesture hints"));
		assertNotNull(optionNamed(rows, "Enable Hyperlinks?"));
		assertNotNull("the key stays in the tree for the editor to write",
				hyper.findOptionByKey("show_gesture_hints"));
	}

	@Test
	public void mapperGmcpDetailKeysStayHiddenAndStillSave() {
		SettingsGroup mapper = new SettingsGroup();
		mapper.setTitle("Mapper");
		mapper.addOption(bool("Configure Room Sync…", "manage_mapper_gmcp"));
		mapper.addOption(bool("GMCP Sync Policy", "mapper_gmcp_policy"));
		mapper.addOption(bool("GMCP: Match by room number?", "mapper_gmcp_use_num"));
		mapper.addOption(bool("GMCP: Use absolute coordinates?", "mapper_gmcp_use_coords"));
		mapper.addOption(bool("GMCP: Auto-grow map?", "mapper_gmcp_grow"));
		mapper.addOption(bool("GMCP: Create exit neighbors?", "mapper_gmcp_create_exits"));

		ArrayList<OptionsDialog.PageRow> rows = OptionsDialog.pageRows(mapper);
		assertEquals(1, rows.size());
		assertEquals("manage_mapper_gmcp", rows.get(0).option.getKey());
		assertNull(optionNamed(rows, "GMCP Sync Policy"));
		assertNotNull(mapper.findOptionByKey("mapper_gmcp_policy"));
		assertNotNull(mapper.findOptionByKey("mapper_gmcp_use_num"));
		assertNotNull(mapper.findOptionByKey("mapper_gmcp_use_coords"));
		assertNotNull(mapper.findOptionByKey("mapper_gmcp_grow"));
		assertNotNull(mapper.findOptionByKey("mapper_gmcp_create_exits"));
	}

	@Test
	public void sensorCallbackKeysAreUnchanged() {
		SettingsGroup device = new SettingsGroup();
		device.setTitle("Device");
		device.addOption(callback("Sensors…", "device_sensors"));
		device.addOption(callback("Calibrate shake…", "calibrate_shake"));
		device.addOption(callback("Calibrate light…", "calibrate_light"));
		device.addOption(callback("Battery low threshold…", "battery_threshold"));

		ArrayList<OptionsDialog.PageRow> rows = OptionsDialog.pageRows(device);
		assertEquals(4, rows.size());
		assertEquals("device_sensors", rows.get(0).option.getKey());
		assertEquals("calibrate_shake", rows.get(1).option.getKey());
		assertEquals("calibrate_light", rows.get(2).option.getKey());
		assertEquals("battery_threshold", rows.get(3).option.getKey());
		assertEquals(Option.TYPE.CALLBACK, rows.get(0).option.type);
	}

	private static SettingsGroup namedGroup(String title, String key, String optionTitle) {
		SettingsGroup g = new SettingsGroup();
		g.setTitle(title);
		BooleanOption o = new BooleanOption();
		o.setTitle(optionTitle);
		o.setKey(key);
		o.setValue(false);
		g.addOption(o);
		return g;
	}

	private static BooleanOption bool(String title, String key) {
		BooleanOption o = new BooleanOption();
		o.setTitle(title);
		o.setKey(key);
		o.setValue(false);
		return o;
	}

	private static CallbackOption callback(String title, String key) {
		CallbackOption o = new CallbackOption();
		o.setTitle(title);
		o.setKey(key);
		o.setValue(key);
		return o;
	}

	private static Option childNamed(SettingsGroup group, String title) {
		ArrayList<Option> options = group.getOptions();
		for (int i = 0; i < options.size(); i++) {
			Option o = options.get(i);
			if (o != null && title.equals(o.getTitle())) {
				return o;
			}
		}
		return null;
	}

	private static boolean hasHeader(ArrayList<OptionsDialog.PageRow> rows, String title) {
		for (int i = 0; i < rows.size(); i++) {
			OptionsDialog.PageRow row = rows.get(i);
			if (row.isHeader() && title.equals(row.header)) {
				return true;
			}
		}
		return false;
	}

	private static Option optionNamed(ArrayList<OptionsDialog.PageRow> rows, String title) {
		for (int i = 0; i < rows.size(); i++) {
			OptionsDialog.PageRow row = rows.get(i);
			if (!row.isHeader() && row.option != null && title.equals(row.option.getTitle())) {
				return row.option;
			}
		}
		return null;
	}
}

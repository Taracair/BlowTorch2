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
	public void windowInlinesHyperlinkSettingsAndKeepsTheNestedGroup() {
		SettingsGroup window = new WindowToken().getSettings();
		assertEquals("Window", window.getTitle());

		Option nested = childNamed(window, "Hyperlink Settings");
		assertNotNull(nested);
		assertEquals(Option.TYPE.GROUP, nested.type);

		ArrayList<OptionsDialog.PageRow> rows = OptionsDialog.pageRows(window);
		assertNull("Hyperlink Settings is a header, not a drill-in row",
				optionNamed(rows, "Hyperlink Settings"));
		assertTrue(hasHeader(rows, "Hyperlink Settings"));

		Option osc8 = optionNamed(rows, "Use OSC 8?");
		assertNotNull(osc8);
		assertEquals("osc8_links", osc8.getKey());
		assertNull("Use OSC 8? is a Window sibling, not inside Hyperlink Settings",
				((SettingsGroup) nested).findOptionByKey("osc8_links"));
		assertSame(window.findOptionByKey("osc8_links"), osc8);

		Option enabled = optionNamed(rows, "Enable Hyperlinks?");
		assertNotNull(enabled);
		assertEquals("hyperlinks_enabled", enabled.getKey());
		assertSame(((SettingsGroup) nested).findOptionByKey("hyperlinks_enabled"),
				enabled);

		assertNotNull("Font is a FileOption on Window, not a nested group",
				optionNamed(rows, "Font"));
		assertFalse(hasHeader(rows, "Font"));
	}

	@Test
	public void windowStillFindsInlinedKeysForUpdate() {
		SettingsGroup window = new WindowToken().getSettings();
		Option found = window.findOptionByKey("hyperlinks_enabled");
		assertNotNull(found);
		assertTrue(found instanceof BooleanOption);
		window.updateBoolean("hyperlinks_enabled", false);
		assertEquals(Boolean.FALSE, ((BooleanOption) found).getValue());
	}

	@Test
	public void extraTextWindowsInlinesWithoutMovingTheGroup() {
		SettingsGroup window = new WindowToken().getSettings();
		int before = window.getOptions().size();

		SettingsGroup extra = new SettingsGroup();
		extra.setTitle("Extra text windows");
		BooleanOption enabled = new BooleanOption();
		enabled.setTitle("Enable Extra Text Windows?");
		enabled.setKey("extra_text_windows_enabled");
		enabled.setValue(true);
		extra.addOption(enabled);
		window.addOption(extra);

		assertEquals(before + 1, window.getOptions().size());
		assertSame(extra, window.getOptions().get(before));

		ArrayList<OptionsDialog.PageRow> rows = OptionsDialog.pageRows(window);
		assertTrue(hasHeader(rows, "Extra text windows"));
		assertNull(optionNamed(rows, "Extra text windows"));
		Option row = optionNamed(rows, "Enable Extra Text Windows?");
		assertNotNull(row);
		assertSame(enabled, row);
		assertEquals("extra_text_windows_enabled",
				window.findOptionByKey("extra_text_windows_enabled").getKey());
	}

	@Test
	public void serviceInlinesProtocolsGmcpMcpAndTelnet() {
		SettingsGroup service = new SettingsGroup();
		service.setTitle("Service");
		BooleanOption log = new BooleanOption();
		log.setTitle("Log Session to File?");
		log.setKey("log_session");
		log.setValue(false);
		service.addOption(log);
		service.addOption(namedGroup("Protocols", "use_gmcp", "Use GMCP?"));
		service.addOption(namedGroup("GMCP", "log_gmcp", "Log GMCP?"));
		service.addOption(namedGroup("MCP", "log_mcp", "Log MCP?"));
		service.addOption(namedGroup("Telnet", "use_mtts", "Use MTTS?"));

		ArrayList<OptionsDialog.PageRow> rows = OptionsDialog.pageRows(service);
		assertNotNull(optionNamed(rows, "Log Session to File?"));
		assertTrue(hasHeader(rows, "Protocols"));
		assertTrue(hasHeader(rows, "GMCP"));
		assertTrue(hasHeader(rows, "MCP"));
		assertTrue(hasHeader(rows, "Telnet"));
		assertNull(optionNamed(rows, "Protocols"));
		assertNull(optionNamed(rows, "GMCP"));
		assertNull(optionNamed(rows, "MCP"));
		assertNull(optionNamed(rows, "Telnet"));
		assertEquals("use_gmcp", optionNamed(rows, "Use GMCP?").getKey());
		assertEquals("log_gmcp", optionNamed(rows, "Log GMCP?").getKey());
		assertEquals("use_mtts", optionNamed(rows, "Use MTTS?").getKey());
		assertSame(service.findOptionByKey("use_gmcp"),
				optionNamed(rows, "Use GMCP?"));
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
	public void hiddenEditorKeysAreOmittedEvenWhenInlined() {
		SettingsGroup page = new SettingsGroup();
		page.setTitle("Window");
		SettingsGroup hyper = new SettingsGroup();
		hyper.setTitle("Hyperlink Settings");
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
		assertTrue(hasHeader(rows, "Hyperlink Settings"));
		assertNull(optionNamed(rows, "Show gesture hints"));
		assertNotNull(optionNamed(rows, "Enable Hyperlinks?"));
		assertNotNull("the key stays in the tree for the editor to write",
				hyper.findOptionByKey("show_gesture_hints"));
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

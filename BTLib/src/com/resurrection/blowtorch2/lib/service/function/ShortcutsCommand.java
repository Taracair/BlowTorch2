package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcuts;

/** {@code .shortcuts} prints on or off. {@code on}, {@code off}, and {@code toggle} are the whole argument. */
public class ShortcutsCommand extends SpecialCommand {

	public ShortcutsCommand() {
		this.commandName = "shortcuts";
	}

	public Object execute(Object o, Connection c) {
		if (c == null) {
			return null;
		}
		BaseOption opt = findOption(c);
		if (opt == null) {
			c.sendDataToWindow(getErrorMessage("Shortcuts command error",
					"Hardware shortcuts are not available yet."));
			return null;
		}
		String stored = opt.getValue() == null ? "" : String.valueOf(opt.getValue());
		String arg = o == null ? "" : String.valueOf(o);
		KeyboardShortcuts.Change change = KeyboardShortcuts.parse(stored).apply(arg);
		if (change.usage) {
			c.sendDataToWindow(getErrorMessage("Shortcuts command usage:",
					".shortcuts\n"
							+ ".shortcuts on\n"
							+ ".shortcuts off\n"
							+ ".shortcuts toggle\n"
							+ "No argument prints on or off and does not change it."));
			return null;
		}
		if (change.changed) {
			c.updateStringSetting(KeyboardShortcuts.SETTING_KEY, change.next.format());
		}
		StringBuilder msg = new StringBuilder();
		msg.append("\n").append(Colorizer.getWhiteColor());
		msg.append(change.next.isOn() ? "on" : "off").append("\n");
		if (change.printNotice) {
			msg.append(KeyboardShortcuts.NOTICE).append("\n");
		}
		c.sendDataToWindow(msg.toString());
		return null;
	}

	private static BaseOption findOption(final Connection c) {
		if (c.getSettings() == null) {
			return null;
		}
		return (BaseOption) c.getSettings().findOptionByKey(KeyboardShortcuts.SETTING_KEY);
	}
}

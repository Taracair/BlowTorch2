package com.resurrection.blowtorch2.lib.service.function;

import java.util.HashMap;
import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

/**
 * Player-friendly trigger enable/disable CLI for main settings triggers.
 *
 * <pre>
 * .trigger
 * .trigger on|off|toggle &lt;name&gt;
 * .trigger status [name]
 * .trigger group on|off|toggle &lt;group&gt;
 * .trigger all on|off
 * </pre>
 *
 * Plugin triggers stay in the UI / Lua ({@code EnableTrigger}, etc.).
 */
public class TriggerCommand extends SpecialCommand {

	public TriggerCommand() {
		this.commandName = "trigger";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		if (arg.length() == 0 || arg.equalsIgnoreCase("help") || arg.equals("?")) {
			c.sendDataToWindow(helpText());
			return null;
		}

		String[] parts = arg.split("\\s+", 2);
		String sub = parts[0].toLowerCase(Locale.US);
		String rest = parts.length > 1 ? parts[1].trim() : "";

		switch (sub) {
		case "on":
		case "enable":
			return doNamed(c, rest, true, false);
		case "off":
		case "disable":
			return doNamed(c, rest, false, false);
		case "toggle":
			return doNamed(c, rest, false, true);
		case "status":
			return doStatus(c, rest);
		case "group":
			return doGroup(c, rest);
		case "all":
			return doAll(c, rest);
		default:
			c.sendDataToWindow(getErrorMessage("Trigger usage",
					"Unknown subcommand '" + sub + "'.\n" + shortUsage()));
			return null;
		}
	}

	private Object doNamed(Connection c, String name, boolean enable, boolean toggle) {
		if (name.length() == 0) {
			c.sendDataToWindow(getErrorMessage("Trigger",
					".trigger " + (toggle ? "toggle" : (enable ? "on" : "off")) + " <name>"));
			return null;
		}
		TriggerData data = c.getTriggers().get(name);
		if (data == null) {
			c.sendDataToWindow(getErrorMessage("Trigger",
					"No main-settings trigger named \"" + name + "\".\n"
							+ "Plugin triggers: manage in the Trigger UI or via Lua."));
			return null;
		}
		boolean next;
		if (toggle) {
			Boolean result = c.toggleTriggerEnabled(name);
			if (result == null) {
				c.sendDataToWindow(getErrorMessage("Trigger",
						"No main-settings trigger named \"" + name + "\"."));
				return null;
			}
			next = result.booleanValue();
		} else {
			c.setTriggerEnabled(enable, name);
			next = enable;
		}
		echo(c, "Trigger \"" + name + "\" " + (next ? "enabled" : "disabled") + ".");
		return null;
	}

	private Object doStatus(Connection c, String name) {
		HashMap<String, TriggerData> triggers = c.getTriggers();
		if (name.length() == 0) {
			int on = 0;
			int off = 0;
			for (TriggerData t : triggers.values()) {
				if (t.isEnabled()) {
					on++;
				} else {
					off++;
				}
			}
			echo(c, "Triggers: " + on + " enabled, " + off + " disabled"
					+ " (total " + (on + off) + ").\n"
					+ "Main settings only — plugin triggers: Trigger UI / Lua.");
			return null;
		}
		TriggerData data = triggers.get(name);
		if (data == null) {
			c.sendDataToWindow(getErrorMessage("Trigger status",
					"No main-settings trigger named \"" + name + "\"."));
			return null;
		}
		String group = data.getGroup();
		String groupLabel = (group == null || group.length() == 0) ? "(default)" : group;
		echo(c, "Trigger \"" + name + "\": "
				+ (data.isEnabled() ? "enabled" : "disabled")
				+ "  group=" + groupLabel);
		return null;
	}

	private Object doGroup(Connection c, String rest) {
		if (rest.length() == 0) {
			c.sendDataToWindow(getErrorMessage("Trigger group",
					".trigger group on|off|toggle <group>"));
			return null;
		}
		String[] parts = rest.split("\\s+", 2);
		String action = parts[0].toLowerCase(Locale.US);
		// Rest of line is the group name; missing → "" (DEFAULT_GROUP), same
		// exact-string match as Lua EnableTriggerGroup.
		String group = parts.length > 1 ? parts[1] : "";
		if (!action.equals("on") && !action.equals("off") && !action.equals("toggle")
				&& !action.equals("enable") && !action.equals("disable")) {
			c.sendDataToWindow(getErrorMessage("Trigger group",
					".trigger group on|off|toggle <group>"));
			return null;
		}

		int n;
		boolean nextEnabled = false;
		boolean toggling = false;
		if (action.equals("toggle")) {
			toggling = true;
			n = c.toggleTriggerGroupEnabled(group);
		} else {
			nextEnabled = action.equals("on") || action.equals("enable");
			n = c.setTriggerGroupEnabled(group, nextEnabled);
		}

		String groupLabel = group.length() == 0 ? "(default)" : "\"" + group + "\"";
		if (n == 0) {
			c.sendDataToWindow(getErrorMessage("Trigger group",
					"No main-settings triggers in group " + groupLabel + "."));
			return null;
		}
		if (toggling) {
			echo(c, "Group " + groupLabel + ": toggled " + n + " trigger(s).");
		} else {
			echo(c, "Group " + groupLabel + ": "
					+ (nextEnabled ? "enabled" : "disabled") + " " + n + " trigger(s).");
		}
		return null;
	}

	private Object doAll(Connection c, String rest) {
		String action = rest.trim().toLowerCase(Locale.US);
		if (!action.equals("on") && !action.equals("off")
				&& !action.equals("enable") && !action.equals("disable")) {
			c.sendDataToWindow(getErrorMessage("Trigger all",
					".trigger all on | .trigger all off"));
			return null;
		}
		boolean enable = action.equals("on") || action.equals("enable");
		int n = c.setAllTriggersEnabled(enable);
		if (enable) {
			echo(c, "ALL triggers enabled (" + n + ")");
		} else {
			echo(c, "ALL triggers disabled (" + n + ")");
		}
		return null;
	}

	private static void echo(Connection c, String msg) {
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor() + msg
				+ Colorizer.getWhiteColor() + "\n");
	}

	private static String helpText() {
		return "\n" + Colorizer.getWhiteColor()
				+ "Enable / disable main-settings triggers (same scope as the Trigger editor).\n"
				+ "Plugin triggers: Trigger UI or Lua EnableTrigger / EnableTriggerGroup.\n\n"
				+ shortUsage();
	}

	private static String shortUsage() {
		return "Usage:\n"
				+ "  .trigger                      - this help\n"
				+ "  .trigger on <name>            - enable trigger\n"
				+ "  .trigger off <name>           - disable trigger\n"
				+ "  .trigger toggle <name>        - toggle trigger\n"
				+ "  .trigger status [name]        - status (or enabled/disabled counts)\n"
				+ "  .trigger group on <group>     - enable group\n"
				+ "  .trigger group off <group>    - disable group\n"
				+ "  .trigger group toggle <group> - toggle each in group\n"
				+ "  .trigger all on               - enable ALL main triggers\n"
				+ "  .trigger all off              - disable ALL main triggers\n";
	}
}

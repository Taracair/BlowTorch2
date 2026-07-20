package com.resurrection.blowtorch2.lib.service.function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.Plugin;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

/**
 * Player-friendly trigger enable/disable CLI for main and plugin triggers.
 *
 * <pre>
 * .trigger
 * .trigger on|off|toggle &lt;name&gt;
 * .trigger on|off|toggle &lt;plugin&gt;:&lt;name&gt;
 * .trigger status [name]
 * .trigger group on|off|toggle &lt;group&gt;
 * .trigger all on|off
 * .trigger plugin &lt;plugin&gt; all on|off
 * </pre>
 *
 * Unqualified names resolve main settings first, then a unique plugin match.
 * Use {@code plugin:name} when names collide across plugins.
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
		case "plugin":
			return doPlugin(c, rest);
		default:
			c.sendDataToWindow(getErrorMessage("Trigger usage",
					"Unknown subcommand '" + sub + "'.\n" + shortUsage()));
			return null;
		}
	}

	private Object doNamed(Connection c, String name, boolean enable, boolean toggle) {
		if (name.length() == 0) {
			c.sendDataToWindow(getErrorMessage("Trigger",
					".trigger " + (toggle ? "toggle" : (enable ? "on" : "off"))
							+ " <name|plugin:name>"));
			return null;
		}
		TriggerRef ref = resolveTrigger(c, name);
		if (ref == null) {
			return null;
		}
		boolean next;
		if (toggle) {
			Boolean result;
			if (ref.plugin == null) {
				result = c.toggleTriggerEnabled(ref.name);
			} else {
				result = c.togglePluginTriggerEnabled(ref.plugin, ref.name);
			}
			if (result == null) {
				c.sendDataToWindow(getErrorMessage("Trigger",
						"No trigger named \"" + name + "\"."));
				return null;
			}
			next = result.booleanValue();
		} else {
			if (ref.plugin == null) {
				c.setTriggerEnabled(enable, ref.name);
			} else {
				c.setPluginTriggerEnabled(ref.plugin, enable, ref.name);
			}
			next = enable;
		}
		echo(c, "Trigger " + formatRef(ref) + " "
				+ (next ? "enabled" : "disabled") + ".");
		return null;
	}

	private Object doStatus(Connection c, String name) {
		if (name.length() == 0) {
			int mainOn = 0;
			int mainOff = 0;
			for (TriggerData t : c.getTriggers().values()) {
				if (t.isEnabled()) {
					mainOn++;
				} else {
					mainOff++;
				}
			}
			int plugOn = 0;
			int plugOff = 0;
			int plugSets = 0;
			for (Plugin p : c.getPlugins()) {
				if (p == null) {
					continue;
				}
				HashMap<String, TriggerData> map = p.getSettings().getTriggers();
				if (map == null || map.isEmpty()) {
					continue;
				}
				plugSets++;
				for (TriggerData t : map.values()) {
					if (t.isEnabled()) {
						plugOn++;
					} else {
						plugOff++;
					}
				}
			}
			echo(c, "Main: " + mainOn + " enabled, " + mainOff + " disabled"
					+ " (total " + (mainOn + mainOff) + ").\n"
					+ "Plugins: " + plugOn + " enabled, " + plugOff + " disabled"
					+ " across " + plugSets + " plugin(s)"
					+ " (total " + (plugOn + plugOff) + ").\n"
					+ "Use .trigger status <name|plugin:name> for one trigger.");
			return null;
		}
		TriggerRef ref = resolveTrigger(c, name);
		if (ref == null) {
			return null;
		}
		String group = ref.data.getGroup();
		String groupLabel = (group == null || group.length() == 0) ? "(default)" : group;
		echo(c, "Trigger " + formatRef(ref) + ": "
				+ (ref.data.isEnabled() ? "enabled" : "disabled")
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
			n = c.toggleTriggerGroupEnabledEverywhere(group);
		} else {
			nextEnabled = action.equals("on") || action.equals("enable");
			n = c.setTriggerGroupEnabledEverywhere(group, nextEnabled);
		}

		String groupLabel = group.length() == 0 ? "(default)" : "\"" + group + "\"";
		if (n == 0) {
			c.sendDataToWindow(getErrorMessage("Trigger group",
					"No triggers in group " + groupLabel
							+ " (main + plugins)."));
			return null;
		}
		if (toggling) {
			echo(c, "Group " + groupLabel + ": toggled " + n
					+ " trigger(s) (main + plugins).");
		} else {
			echo(c, "Group " + groupLabel + ": "
					+ (nextEnabled ? "enabled" : "disabled") + " " + n
					+ " trigger(s) (main + plugins).");
		}
		return null;
	}

	private Object doAll(Connection c, String rest) {
		String action = rest.trim().toLowerCase(Locale.US);
		if (!action.equals("on") && !action.equals("off")
				&& !action.equals("enable") && !action.equals("disable")) {
			c.sendDataToWindow(getErrorMessage("Trigger all",
					".trigger all on | .trigger all off\n"
							+ "(main settings only; for one plugin use "
							+ ".trigger plugin <plugin> all on|off)"));
			return null;
		}
		boolean enable = action.equals("on") || action.equals("enable");
		int n = c.setAllTriggersEnabled(enable);
		if (enable) {
			echo(c, "ALL main triggers enabled (" + n + ")");
		} else {
			echo(c, "ALL main triggers disabled (" + n + ")");
		}
		return null;
	}

	private Object doPlugin(Connection c, String rest) {
		if (rest.length() == 0) {
			c.sendDataToWindow(getErrorMessage("Trigger plugin",
					".trigger plugin <plugin> all on|off"));
			return null;
		}
		String[] parts = rest.split("\\s+", 3);
		if (parts.length < 3 || !parts[1].equalsIgnoreCase("all")) {
			c.sendDataToWindow(getErrorMessage("Trigger plugin",
					".trigger plugin <plugin> all on|off"));
			return null;
		}
		String plugin = parts[0];
		String action = parts[2].toLowerCase(Locale.US);
		if (!action.equals("on") && !action.equals("off")
				&& !action.equals("enable") && !action.equals("disable")) {
			c.sendDataToWindow(getErrorMessage("Trigger plugin",
					".trigger plugin <plugin> all on|off"));
			return null;
		}
		if (c.getPluginTriggers(plugin) == null) {
			c.sendDataToWindow(getErrorMessage("Trigger plugin",
					"No loaded plugin named \"" + plugin + "\"."));
			return null;
		}
		boolean enable = action.equals("on") || action.equals("enable");
		int n = c.setAllPluginTriggersEnabled(plugin, enable);
		echo(c, "Plugin \"" + plugin + "\": "
				+ (enable ? "enabled" : "disabled") + " " + n + " trigger(s).");
		return null;
	}

	/**
	 * Resolve {@code name} or {@code plugin:name}. Unqualified names try main
	 * first, then require a unique plugin match.
	 */
	private TriggerRef resolveTrigger(Connection c, String raw) {
		int colon = raw.indexOf(':');
		if (colon > 0) {
			String plugin = raw.substring(0, colon).trim();
			String name = raw.substring(colon + 1).trim();
			if (plugin.length() == 0 || name.length() == 0) {
				c.sendDataToWindow(getErrorMessage("Trigger",
						"Use plugin:name (both sides required)."));
				return null;
			}
			HashMap<String, TriggerData> map = c.getPluginTriggers(plugin);
			if (map == null) {
				c.sendDataToWindow(getErrorMessage("Trigger",
						"No loaded plugin named \"" + plugin + "\"."));
				return null;
			}
			TriggerData data = map.get(name);
			if (data == null) {
				c.sendDataToWindow(getErrorMessage("Trigger",
						"No trigger \"" + name + "\" in plugin \"" + plugin + "\"."));
				return null;
			}
			return new TriggerRef(plugin, name, data);
		}

		TriggerData main = c.getTriggers().get(raw);
		if (main != null) {
			return new TriggerRef(null, raw, main);
		}

		ArrayList<TriggerRef> hits = new ArrayList<TriggerRef>();
		for (Plugin p : c.getPlugins()) {
			if (p == null) {
				continue;
			}
			HashMap<String, TriggerData> map = p.getSettings().getTriggers();
			if (map == null) {
				continue;
			}
			TriggerData data = map.get(raw);
			if (data != null) {
				hits.add(new TriggerRef(p.getName(), raw, data));
			}
		}
		if (hits.size() == 1) {
			return hits.get(0);
		}
		if (hits.size() > 1) {
			StringBuilder sb = new StringBuilder();
			sb.append("Ambiguous trigger \"").append(raw)
					.append("\" — found in multiple plugins:\n");
			for (TriggerRef r : hits) {
				sb.append("  ").append(r.plugin).append(':').append(r.name).append('\n');
			}
			sb.append("Use .trigger … plugin:name");
			c.sendDataToWindow(getErrorMessage("Trigger", sb.toString()));
			return null;
		}
		c.sendDataToWindow(getErrorMessage("Trigger",
				"No trigger named \"" + raw + "\" (main or plugins).\n"
						+ "Tip: .trigger status  or  plugin:name"));
		return null;
	}

	private static String formatRef(TriggerRef ref) {
		if (ref.plugin == null) {
			return "\"" + ref.name + "\" (main)";
		}
		return "\"" + ref.plugin + ":" + ref.name + "\"";
	}

	private static void echo(Connection c, String msg) {
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor() + msg
				+ Colorizer.getWhiteColor() + "\n");
	}

	private static String helpText() {
		return "\n" + Colorizer.getWhiteColor()
				+ "Enable / disable triggers (main settings and plugins).\n"
				+ "Unqualified names: main first, then unique plugin match.\n"
				+ "Use plugin:name when names collide. Groups apply main+plugins.\n\n"
				+ shortUsage();
	}

	private static String shortUsage() {
		return "Usage:\n"
				+ "  .trigger                           - this help\n"
				+ "  .trigger on <name|plugin:name>     - enable trigger\n"
				+ "  .trigger off <name|plugin:name>    - disable trigger\n"
				+ "  .trigger toggle <name|plugin:name> - toggle trigger\n"
				+ "  .trigger status [name]             - status / counts (main+plugins)\n"
				+ "  .trigger group on <group>          - enable group (main+plugins)\n"
				+ "  .trigger group off <group>         - disable group (main+plugins)\n"
				+ "  .trigger group toggle <group>      - toggle group (main+plugins)\n"
				+ "  .trigger all on|off                - ALL main triggers only\n"
				+ "  .trigger plugin <plugin> all on|off - ALL triggers in one plugin\n";
	}

	private static final class TriggerRef {
		final String plugin; // null = main
		final String name;
		final TriggerData data;

		TriggerRef(String plugin, String name, TriggerData data) {
			this.plugin = plugin;
			this.name = name;
			this.data = data;
		}
	}
}

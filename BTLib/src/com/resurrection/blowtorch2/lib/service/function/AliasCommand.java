package com.resurrection.blowtorch2.lib.service.function;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.resurrection.blowtorch2.lib.alias.AliasData;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.Plugin;

/**
 * Enable and disable aliases from the input bar, the way {@code .trigger} and
 * {@code .timer} already work.
 *
 * <pre>
 * .alias
 * .alias on|off|toggle &lt;name&gt;
 * .alias on|off|toggle &lt;plugin&gt;:&lt;name&gt;
 * .alias status [name]
 * .alias list
 * .alias all on|off
 * </pre>
 *
 * <p>There was Lua {@code EnableAlias} and nothing to type, which broke the
 * pattern every other kind of thing in this client follows.
 *
 * <p>Unqualified names resolve main settings first, then a unique plugin match;
 * use {@code plugin:name} when the same name exists in more than one place.
 */
public class AliasCommand extends SpecialCommand {

	public AliasCommand() {
		this.commandName = "alias";
	}

	/** One resolved alias plus where it came from. */
	private static final class AliasRef {
		private final String plugin;
		private final String name;
		private final AliasData data;

		private AliasRef(String plugin, String name, AliasData data) {
			this.plugin = plugin;
			this.name = name;
			this.data = data;
		}
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

		if (sub.equals("on") || sub.equals("enable")) {
			return doNamed(c, rest, true, false);
		}
		if (sub.equals("off") || sub.equals("disable")) {
			return doNamed(c, rest, false, false);
		}
		if (sub.equals("toggle")) {
			return doNamed(c, rest, false, true);
		}
		if (sub.equals("status") || sub.equals("state")) {
			return doStatus(c, rest);
		}
		if (sub.equals("list")) {
			return doList(c);
		}
		if (sub.equals("all")) {
			return doAll(c, rest);
		}
		c.sendDataToWindow(getErrorMessage("Alias usage",
				"Unknown subcommand '" + sub + "'.\n" + shortUsage()));
		return null;
	}

	private Object doNamed(Connection c, String name, boolean enable, boolean toggle) {
		if (name.length() == 0) {
			c.sendDataToWindow(getErrorMessage("Alias",
					".alias " + (toggle ? "toggle" : (enable ? "on" : "off"))
							+ " <name|plugin:name>"));
			return null;
		}
		AliasRef ref = resolve(c, name);
		if (ref == null) {
			return null;
		}
		boolean next = toggle ? !ref.data.isEnabled() : enable;
		if (ref.plugin == null) {
			c.setAliasEnabled(next, ref.name);
		} else {
			c.setPluginAliasEnabled(ref.plugin, next, ref.name);
		}
		echo(c, "Alias " + format(ref) + " " + (next ? "enabled" : "disabled") + ".");
		return null;
	}

	private Object doStatus(Connection c, String name) {
		if (name.length() == 0) {
			int on = 0;
			int off = 0;
			for (AliasData a : c.getAliases().values()) {
				if (a != null && a.isEnabled()) {
					on++;
				} else {
					off++;
				}
			}
			int pon = 0;
			int poff = 0;
			for (Plugin p : c.getPlugins()) {
				HashMap<String, AliasData> map = aliasesOf(p);
				if (map == null) {
					continue;
				}
				for (AliasData a : map.values()) {
					if (a != null && a.isEnabled()) {
						pon++;
					} else {
						poff++;
					}
				}
			}
			echo(c, "Main: " + on + " enabled, " + off + " disabled.\n"
					+ "Plugins: " + pon + " enabled, " + poff + " disabled.\n"
					+ "Use .alias list to see them, or .alias status <name>.");
			return null;
		}
		AliasRef ref = resolve(c, name);
		if (ref == null) {
			return null;
		}
		echo(c, "Alias " + format(ref) + ": "
				+ (ref.data.isEnabled() ? "enabled" : "disabled")
				+ "  echo:" + ref.data.getLocalEcho().toInspectToken()
				+ "  →  " + ref.data.getPost());
		return null;
	}

	private Object doList(Connection c) {
		// Sorted, because an unordered dump of every alias is not a list anyone
		// can read.
		TreeMap<String, AliasData> sorted = new TreeMap<String, AliasData>();
		for (Map.Entry<String, AliasData> e : c.getAliases().entrySet()) {
			sorted.put(e.getKey(), e.getValue());
		}
		StringBuilder sb = new StringBuilder();
		if (sorted.isEmpty()) {
			sb.append("No aliases in main settings.");
		} else {
			sb.append("Aliases (main):");
			for (Map.Entry<String, AliasData> e : sorted.entrySet()) {
				appendAliasLine(sb, e.getKey(), e.getValue(), null);
			}
		}
		for (Plugin p : c.getPlugins()) {
			HashMap<String, AliasData> map = aliasesOf(p);
			if (map == null || map.isEmpty()) {
				continue;
			}
			sb.append("\nAliases (").append(p.getName()).append("):");
			for (Map.Entry<String, AliasData> e : new TreeMap<String, AliasData>(map).entrySet()) {
				appendAliasLine(sb, e.getKey(), e.getValue(), p.getName());
			}
		}
		echo(c, sb.toString());
		return null;
	}

	private static void appendAliasLine(StringBuilder sb, String key, AliasData a,
			String plugin) {
		sb.append("\n  ").append(a != null && a.isEnabled() ? "[on ] " : "[off] ");
		if (plugin != null) {
			sb.append(plugin).append(':');
		}
		sb.append(key);
		if (a != null) {
			sb.append("  echo:").append(a.getLocalEcho().toInspectToken())
					.append("  →  ").append(a.getPost());
		}
	}

	private Object doAll(Connection c, String rest) {
		String action = rest.toLowerCase(Locale.US).trim();
		boolean enable;
		if (action.equals("on") || action.equals("enable")) {
			enable = true;
		} else if (action.equals("off") || action.equals("disable")) {
			enable = false;
		} else {
			c.sendDataToWindow(getErrorMessage("Alias all", ".alias all on|off"));
			return null;
		}
		int n = 0;
		for (String key : c.getAliases().keySet()) {
			c.setAliasEnabled(enable, key);
			n++;
		}
		echo(c, "Main aliases " + (enable ? "enabled" : "disabled") + ": " + n + ".");
		return null;
	}

	private static HashMap<String, AliasData> aliasesOf(Plugin p) {
		if (p == null || p.getSettings() == null) {
			return null;
		}
		return p.getSettings().getAliases();
	}

	/** Main settings first, then a unique plugin match. {@code plugin:name} is explicit. */
	private AliasRef resolve(Connection c, String raw) {
		String name = raw.trim();
		int colon = name.indexOf(':');
		if (colon > 0) {
			String plugin = name.substring(0, colon).trim();
			String key = name.substring(colon + 1).trim();
			AliasData d = c.getPluginAlias(plugin, key);
			if (d == null) {
				c.sendDataToWindow(getErrorMessage("Alias",
						"No alias \"" + key + "\" in plugin \"" + plugin + "\"."));
				return null;
			}
			return new AliasRef(plugin, key, d);
		}
		AliasData main = c.getAliases().get(name);
		if (main != null) {
			return new AliasRef(null, name, main);
		}
		AliasRef found = null;
		int matches = 0;
		for (Plugin p : c.getPlugins()) {
			HashMap<String, AliasData> map = aliasesOf(p);
			if (map == null) {
				continue;
			}
			AliasData d = map.get(name);
			if (d != null) {
				matches++;
				found = new AliasRef(p.getName(), name, d);
			}
		}
		if (matches == 1) {
			return found;
		}
		if (matches > 1) {
			c.sendDataToWindow(getErrorMessage("Alias",
					"\"" + name + "\" exists in " + matches
							+ " plugins. Use plugin:name."));
			return null;
		}
		c.sendDataToWindow(getErrorMessage("Alias", "No alias named \"" + name + "\"."));
		return null;
	}

	private static String format(AliasRef ref) {
		return ref.plugin == null ? "\"" + ref.name + "\""
				: "\"" + ref.plugin + ":" + ref.name + "\"";
	}

	private void echo(Connection c, String message) {
		c.sendDataToWindow("\n" + message + "\n");
	}

	private static String shortUsage() {
		return ".alias on|off|toggle <name> · .alias status [name] · .alias list"
				+ " · .alias all on|off";
	}

	private String helpText() {
		return "\nAlias commands:\n"
				+ "  .alias list                    all aliases and their state\n"
				+ "  .alias status|state [name]      counts, or one alias\n"
				+ "  .alias on|off|toggle <name>    turn one on or off\n"
				+ "  .alias all on|off              every alias in main settings\n"
				+ "Use plugin:name when the same name exists in more than one plugin.\n"
				+ "A disabled alias stops matching straight away.\n";
	}
}

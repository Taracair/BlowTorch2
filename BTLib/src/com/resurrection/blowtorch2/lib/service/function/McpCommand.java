package com.resurrection.blowtorch2.lib.service.function;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.McpEngine;
import com.resurrection.blowtorch2.lib.service.McpPackageRegistry;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;

/**
 * MCP helper command (Mud Client Protocol 2.1).
 *
 * <pre>
 * .mcp
 * .mcp ask | handshake | status
 * .mcp packages | modules
 * .mcp enable|disable &lt;pkgs…&gt;
 * .mcp renegotiate
 * .mcp sniff [on|off|tail N]
 * .mcp feed [on|off]
 * .mcp dump [status|recent]
 * .mcp send &lt;raw line or name key: val…&gt;
 * </pre>
 */
public class McpCommand extends SpecialCommand {

	public static final String OPT_USE = "use_mcp";
	public static final String OPT_PACKAGES = "mcp_packages";
	public static final String OPT_LOG = "log_mcp";
	public static final String OPT_FEED = "mcp_feed";

	public McpCommand() {
		this.commandName = "mcp";
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
		case "sniff":
		case "log":
			return doSniff(c, rest);
		case "feed":
		case "echo":
		case "show":
			return doFeed(c, rest);
		case "status":
			return doStatus(c);
		case "ask":
		case "handshake":
			return doAsk(c);
		case "packages":
		case "package":
		case "modules":
		case "module":
			return doPackages(c);
		case "enable":
		case "add":
			return doEnableDisable(c, rest, true);
		case "disable":
		case "remove":
			return doEnableDisable(c, rest, false);
		case "renegotiate":
		case "renego":
		case "negotiate":
			return doRenegotiate(c);
		case "dump":
			return doDump(c, rest);
		case "send":
			return doSend(c, rest);
		case "vitals":
		case "bar":
		case "hellmoo":
			return doVitals(c);
		default:
			c.sendDataToWindow(getErrorMessage("MCP usage",
					"Unknown subcommand '" + sub + "'.\n" + shortUsage()));
			return null;
		}
	}

	private Object doAsk(Connection c) {
		McpEngine eng = c.getMcpEngine();
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		sb.append("MCP handshake / discovery:\n");
		sb.append("  Use MCP? ").append(boolOpt(c, OPT_USE, false) ? "on" : "off").append("\n");
		sb.append("  Packages: ").append(stringOpt(c, OPT_PACKAGES,
				McpPackageRegistry.DEFAULT_PACKAGES)).append("\n");
		if (eng != null) {
			sb.append("  Live: ").append(eng.statusReport()).append("\n");
			sb.append("  Enabled: ").append(join(eng.getRegistry().enabledTokens())).append("\n");
			ArrayList<String> seen = eng.getRegistry().seenPackages();
			sb.append("  Seen: ").append(seen.isEmpty() ? "(none)" : join(seen)).append("\n");
		} else {
			sb.append("  Engine: (not ready)\n");
		}
		sb.append("  Note: MCP is in-band #$# lines (not telnet GMCP).\n");
		sb.append("  Manage: Options → Service → MCP Options.\n");
		c.sendDataToWindow(sb.toString());
		return null;
	}

	private Object doPackages(Connection c) {
		McpEngine eng = c.getMcpEngine();
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		if (eng == null) {
			sb.append("MCP packages: ").append(stringOpt(c, OPT_PACKAGES,
					McpPackageRegistry.DEFAULT_PACKAGES)).append("\n");
		} else {
			sb.append("Enabled: ").append(join(eng.getRegistry().enabledTokens())).append("\n");
			ArrayList<String> seen = eng.getRegistry().seenPackages();
			sb.append("Seen:    ").append(seen.isEmpty() ? "(none)" : join(seen)).append("\n");
		}
		c.sendDataToWindow(sb.toString());
		return null;
	}

	private Object doEnableDisable(Connection c, String rest, boolean enable) {
		if (rest.length() == 0) {
			c.sendDataToWindow(getErrorMessage("MCP " + (enable ? "enable" : "disable"),
					".mcp " + (enable ? "enable" : "disable") + " <package> [package…]"));
			return null;
		}
		String[] toks = rest.split("[,\\s]+");
		McpPackageRegistry reg = McpPackageRegistry.fromPackagesOption(
				stringOpt(c, OPT_PACKAGES, McpPackageRegistry.DEFAULT_PACKAGES));
		McpEngine eng = c.getMcpEngine();
		if (eng != null) {
			reg = eng.getRegistry();
		}
		ArrayList<String> ids = new ArrayList<String>();
		for (String t : toks) {
			if (t.length() > 0) {
				reg.setEnabled(t, enable);
				ids.add(t);
			}
		}
		String packages = reg.toPackagesString();
		c.updateStringSetting(OPT_PACKAGES, packages);
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "MCP " + (enable ? "enabled" : "disabled") + ": " + join(ids) + "\n"
				+ "Packages now: " + packages + "\n"
				+ "Use .mcp renegotiate if already connected.\n");
		return null;
	}

	private Object doRenegotiate(Connection c) {
		if (!boolOpt(c, OPT_USE, false)) {
			c.sendDataToWindow(getErrorMessage("MCP renegotiate", "Use MCP? is off."));
			return null;
		}
		McpEngine eng = c.getMcpEngine();
		if (eng == null) {
			c.sendDataToWindow(getErrorMessage("MCP renegotiate", "Not connected."));
			return null;
		}
		eng.renegotiate();
		return null;
	}

	private Object doStatus(Connection c) {
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		sb.append("MCP use=").append(boolOpt(c, OPT_USE, false) ? "on" : "off");
		sb.append("  sniff=").append(boolOpt(c, OPT_LOG, false) ? "on" : "off");
		sb.append("  feed=").append(boolOpt(c, OPT_FEED, false) ? "on" : "off").append("\n");
		McpEngine eng = c.getMcpEngine();
		if (eng != null) {
			sb.append(eng.statusReport()).append("\n");
			Map<String, String> vitals = eng.getStatusCache();
			if (vitals.containsKey("summary")) {
				sb.append("Vitals: ").append(vitals.get("summary")).append("\n");
			}
		}
		sb.append("Packages: ").append(stringOpt(c, OPT_PACKAGES,
				McpPackageRegistry.DEFAULT_PACKAGES)).append("\n");
		sb.append("Options → Service → MCP Options.  ").append(sniffHint(c));
		c.sendDataToWindow(sb.toString());
		return null;
	}

	private Object doVitals(Connection c) {
		McpEngine eng = c.getMcpEngine();
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor()).append("MCP vitals cache:\n");
		if (eng == null || eng.getStatusCache().isEmpty()) {
			sb.append("(empty — enable Use MCP? and dns-org-hellmoo-status on a HellMOO-family world)\n");
		} else {
			for (Map.Entry<String, String> e : eng.getStatusCache().entrySet()) {
				sb.append("  ").append(e.getKey()).append("=").append(e.getValue()).append("\n");
			}
		}
		c.sendDataToWindow(sb.toString());
		return null;
	}

	private Object doDump(Connection c, String rest) {
		String what = rest.toLowerCase(Locale.US);
		if (what.length() == 0 || what.equals("status") || what.equals("vitals")) {
			return doVitals(c);
		}
		if (what.equals("recent") || what.equals("tail")) {
			McpEngine eng = c.getMcpEngine();
			StringBuilder sb = new StringBuilder();
			sb.append("\n").append(Colorizer.getWhiteColor()).append("MCP recent:\n");
			if (eng == null) {
				sb.append("(no engine)\n");
			} else {
				for (String line : eng.getRecentMessages()) {
					sb.append("  ").append(line).append("\n");
				}
			}
			c.sendDataToWindow(sb.toString());
			return null;
		}
		c.sendDataToWindow(getErrorMessage("MCP dump", ".mcp dump [status|recent]"));
		return null;
	}

	private Object doSend(Connection c, String rest) {
		if (rest.length() == 0) {
			c.sendDataToWindow(getErrorMessage("MCP send",
					".mcp send #$#…   or   .mcp send <name> key: val …"));
			return null;
		}
		McpEngine eng = c.getMcpEngine();
		if (eng == null || !boolOpt(c, OPT_USE, false)) {
			c.sendDataToWindow(getErrorMessage("MCP send", "MCP off or not connected."));
			return null;
		}
		if (rest.startsWith("#$#")) {
			eng.sendRaw(rest);
		} else {
			// name key: val …
			String[] parts = rest.split("\\s+", 2);
			String name = parts[0];
			LinkedHashMap<String, String> args = new LinkedHashMap<String, String>();
			if (parts.length > 1) {
				java.util.regex.Matcher m = java.util.regex.Pattern
						.compile("([A-Za-z0-9_.*-]+):\\s*(?:\"([^\"]*)\"|(\\S+))")
						.matcher(parts[1]);
				while (m.find()) {
					args.put(m.group(1), m.group(2) != null ? m.group(2) : m.group(3));
				}
			}
			eng.sendMessage(name, args);
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor() + "MCP sent.\n");
		return null;
	}

	private Object doFeed(Connection c, String rest) {
		BooleanOption opt = findBool(c, OPT_FEED);
		if (opt == null) {
			c.sendDataToWindow(getErrorMessage("MCP feed", "mcp_feed option missing."));
			return null;
		}
		boolean current = ((Boolean) opt.getValue()).booleanValue();
		if (rest.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "MCP window feed is " + (current ? "on" : "off") + ".\n"
					+ "Usage: .mcp feed on | off\n");
			return null;
		}
		Boolean desired = parseOnOff(rest.split("\\s+")[0]);
		if (desired == null) {
			c.sendDataToWindow(getErrorMessage("MCP feed", ".mcp feed on | off"));
			return null;
		}
		c.updateBooleanSetting(OPT_FEED, desired.booleanValue());
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "MCP window feed " + (desired.booleanValue() ? "on" : "off") + ".\n");
		return null;
	}

	private Object doSniff(Connection c, String rest) {
		BooleanOption opt = findBool(c, OPT_LOG);
		if (opt == null) {
			c.sendDataToWindow(getErrorMessage("MCP sniff", "log_mcp option missing."));
			return null;
		}
		boolean current = ((Boolean) opt.getValue()).booleanValue();
		if (rest.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "MCP sniff/log is " + (current ? "on" : "off") + ".\n"
					+ "Usage: .mcp sniff on | off | tail [0-100]\n" + sniffHint(c));
			return null;
		}
		String[] toks = rest.split("\\s+");
		String first = toks[0].toLowerCase(Locale.US);
		if (first.equals("tail")) {
			int lines = 40;
			if (toks.length > 1) {
				try {
					lines = Integer.parseInt(toks[1]);
				} catch (NumberFormatException e) {
					c.sendDataToWindow(getErrorMessage("MCP sniff tail", ".mcp sniff tail [0-100]"));
					return null;
				}
			}
			if (lines < 0) {
				lines = 0;
			}
			if (lines > 100) {
				lines = 100;
			}
			McpEngine eng = c.getMcpEngine();
			StringBuilder out = new StringBuilder();
			out.append("\n").append(Colorizer.getWhiteColor()).append("MCP sniff tail:\n");
			if (eng != null) {
				ArrayList<String> recent = eng.getRecentMessages();
				int from = Math.max(0, recent.size() - lines);
				for (int i = from; i < recent.size(); i++) {
					out.append(recent.get(i)).append("\n");
				}
			}
			java.io.File logFile = BlowTorchLogger.getLogFile(c.getContext());
			if (logFile != null) {
				out.append("(also ").append(logFile.getAbsolutePath()).append(")\n");
			}
			c.sendDataToWindow(out.toString());
			return null;
		}
		Boolean desired = parseOnOff(first);
		if (desired == null) {
			c.sendDataToWindow(getErrorMessage("MCP sniff", ".mcp sniff on | off | tail [N]"));
			return null;
		}
		c.updateBooleanSetting(OPT_LOG, desired.booleanValue());
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "MCP sniff/log " + (desired.booleanValue() ? "on" : "off") + ".\n"
				+ (desired.booleanValue() ? sniffHint(c) : ""));
		return null;
	}

	private static String helpText() {
		return "\n" + Colorizer.getWhiteColor()
				+ "MCP (Mud Client Protocol) — in-band #$# messages (not GMCP).\n"
				+ shortUsage()
				+ "Enable: Options → Service → MCP Options → Use MCP?\n"
				+ "Samsara/HellMOO: enable dns-org-hellmoo-status for vitals.\n";
	}

	private static String shortUsage() {
		return "  .mcp ask|status|packages|vitals\n"
				+ "  .mcp enable|disable <pkg…>\n"
				+ "  .mcp renegotiate\n"
				+ "  .mcp sniff [on|off|tail N]   .mcp feed [on|off]\n"
				+ "  .mcp dump [status|recent]    .mcp send …\n";
	}

	private static String sniffHint(Connection c) {
		java.io.File f = BlowTorchLogger.getLogFile(c.getContext());
		return f != null ? ("Log: " + f.getAbsolutePath() + "\n") : "";
	}

	private static Boolean parseOnOff(String s) {
		String t = s.toLowerCase(Locale.US);
		if ("on".equals(t) || "1".equals(t) || "true".equals(t) || "yes".equals(t)) {
			return Boolean.TRUE;
		}
		if ("off".equals(t) || "0".equals(t) || "false".equals(t) || "no".equals(t)) {
			return Boolean.FALSE;
		}
		return null;
	}

	private static boolean boolOpt(Connection c, String key, boolean def) {
		BooleanOption o = findBool(c, key);
		if (o == null || !(o.getValue() instanceof Boolean)) {
			return def;
		}
		return ((Boolean) o.getValue()).booleanValue();
	}

	private static String stringOpt(Connection c, String key, String def) {
		try {
			Object o = c.getSettings().findOptionByKey(key);
			if (o instanceof StringOption && ((StringOption) o).getValue() != null) {
				return ((StringOption) o).getValue().toString();
			}
		} catch (Exception ignored) {
		}
		return def;
	}

	private static BooleanOption findBool(Connection c, String key) {
		try {
			Object o = c.getSettings().findOptionByKey(key);
			if (o instanceof BooleanOption) {
				return (BooleanOption) o;
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static String join(ArrayList<String> list) {
		if (list == null || list.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (String s : list) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(s);
		}
		return sb.toString();
	}
}

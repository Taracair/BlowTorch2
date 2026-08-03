package com.resurrection.blowtorch2.lib.service.function;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.StellarService;

/**
 * Switch the foreground UI to another already-open connection by display name.
 *
 * <pre>
 * .switch
 * .switch &lt;display name&gt;
 * </pre>
 *
 * Bare {@code .switch} (or an unknown name) prints usage and the open sessions —
 * it must not move the clutch, or the UI rebuilds against a missing connection
 * and the screen goes black.
 */
public class SwitchWindowCommand extends SpecialCommand {
	public SwitchWindowCommand() {
		this.commandName = "switch";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String name = o == null ? "" : o.toString().trim();
		StellarService service = c.getService();
		List<String> open = service.getOpenConnectionDisplays();

		if (name.length() == 0 || name.equals("?") || name.equalsIgnoreCase("help")) {
			c.sendDataToWindow(usageMessage(open, c.getDisplay()));
			return null;
		}

		if (!service.hasOpenConnection(name)) {
			c.sendDataToWindow(getErrorMessage(
					"No open connection named \"" + name + "\".",
					usageHint(open)));
			return null;
		}

		if (name.equals(c.getService().getClutch())) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Already viewing \"" + name + "\".\n");
			return null;
		}

		// Do not call setClutch here — switchTo owns the clutch and refuses
		// unknown names. A bare setClutch used to black-screen the UI.
		c.switchTo(name);
		return null;
	}

	/** Player-facing help for bare {@code .switch} / {@code .switch help}. */
	static String usageMessage(List<String> open, String current) {
		StringBuilder sb = new StringBuilder();
		sb.append(getErrorMessage(
				"Switch special command usage:",
				".switch <display name>\n"
						+ "Switches the foreground UI to another already-open\n"
						+ "connection. Does not disconnect anyone. The name must\n"
						+ "match a session that is already running."));
		sb.append(listOpenBlock(open, current));
		return sb.toString();
	}

	/** Shorter hint after a bad name. */
	static String usageHint(List<String> open) {
		if (open == null || open.isEmpty()) {
			return "No other sessions are open. Connect another world first,\n"
					+ "then .switch <its display name>.";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("Use the exact display name of an open session:\n");
		sb.append(formatOpenList(open));
		sb.append("\nExample: .switch ").append(open.get(0));
		return sb.toString();
	}

	static String listOpenBlock(List<String> open, String current) {
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		if (open == null || open.isEmpty()) {
			sb.append("No open connections.\n");
			return sb.toString();
		}
		sb.append("Open connections:\n");
		sb.append(formatOpenList(open));
		if (current != null && current.length() > 0) {
			sb.append("\nCurrently viewing: ").append(current);
		}
		sb.append("\n");
		return sb.toString();
	}

	/** One name per line, sorted for stable output. */
	static String formatOpenList(List<String> open) {
		List<String> sorted = new ArrayList<String>(open);
		Collections.sort(sorted);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < sorted.size(); i++) {
			if (i > 0) {
				sb.append('\n');
			}
			sb.append("  ").append(sorted.get(i));
		}
		return sb.toString();
	}
}

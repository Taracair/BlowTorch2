package com.resurrection.blowtorch2.lib.service.function;

import java.util.ArrayList;
import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.WindowToken;
import com.resurrection.blowtorch2.lib.window.ExtraTextSlot;
import com.resurrection.blowtorch2.lib.window.ExtraTextSlotsStore;

/**
 * Extra text window CLI.
 *
 * <pre>
 * .window list
 * .window show|hide|clear &lt;slot&gt;
 * .window create &lt;slot&gt; [title…]
 * .window destroy &lt;slot&gt;
 * .window opacity &lt;slot&gt; [40-100]
 * </pre>
 */
public class WindowCommand extends SpecialCommand {

	public WindowCommand() {
		this.commandName = "window";
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
		case "list":
		case "ls":
			return doList(c);
		case "show":
			return doShow(c, rest, true);
		case "hide":
			return doShow(c, rest, false);
		case "clear":
			return doClear(c, rest);
		case "create":
		case "add":
			return doCreate(c, rest);
		case "destroy":
		case "delete":
		case "remove":
		case "rm":
			return doDestroy(c, rest);
		case "opacity":
		case "alpha":
			return doOpacity(c, rest);
		default:
			c.sendDataToWindow(getErrorMessage("Window usage",
					"Unknown subcommand '" + sub + "'.\n" + shortUsage()));
			return null;
		}
	}

	private Object doList(Connection c) {
		ArrayList<ExtraTextSlot> slots = c.getExtraTextSlots();
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getBrightCyanColor())
				.append("Extra text windows")
				.append(Colorizer.getWhiteColor()).append(" (")
				.append(c.isExtraTextWindowsEnabled() ? "enabled" : "disabled")
				.append("):\n");
		if (slots == null || slots.isEmpty()) {
			sb.append("  (none — use .window create <name> [title])\n");
		} else {
			for (int i = 0; i < slots.size(); i++) {
				ExtraTextSlot s = slots.get(i);
				if (s == null) {
					continue;
				}
				sb.append("  ").append(s.getName())
						.append(" — ").append(s.getTitle())
						.append(" [").append(s.getMode().toJsonValue()).append("]")
						.append(" ").append(s.getOpacity()).append("%")
						.append(s.isVisible() ? "" : " (hidden)")
						.append(s.isCollapsed() ? " (collapsed)" : "")
						.append("\n");
			}
		}
		c.sendDataToWindow(sb.toString());
		return null;
	}

	private Object doShow(Connection c, String name, boolean visible) {
		if (name.length() == 0) {
			c.sendDataToWindow(getErrorMessage("Window",
					".window " + (visible ? "show" : "hide") + " <slot>"));
			return null;
		}
		ExtraTextSlot slot = c.findExtraTextSlot(name);
		if (slot == null) {
			c.sendDataToWindow(getErrorMessage("Window",
					"No extra text slot named \"" + name + "\"."));
			return null;
		}
		slot.setVisible(visible);
		if (!c.upsertExtraTextSlot(slot)) {
			c.sendDataToWindow(getErrorMessage("Window", "Failed to update slot."));
			return null;
		}
		echo(c, "Window " + slot.getName() + (visible ? " shown." : " hidden."));
		return null;
	}

	private Object doClear(Connection c, String name) {
		if (name.length() == 0) {
			c.sendDataToWindow(getErrorMessage("Window", ".window clear <slot>"));
			return null;
		}
		String target = name;
		if ("main".equalsIgnoreCase(name)) {
			target = "mainDisplay";
		}
		WindowToken tok = c.getWindowByName(target);
		if (tok == null) {
			c.sendDataToWindow(getErrorMessage("Window",
					"No window named \"" + name + "\"."));
			return null;
		}
		if (tok.getBuffer() != null) {
			tok.getBuffer().empty();
		}
		c.getHandler().sendMessage(c.getHandler().obtainMessage(
				Connection.MESSAGE_INVALIDATEWINDOWTEXT, target));
		echo(c, "Cleared window " + target + ".");
		return null;
	}

	private Object doCreate(Connection c, String rest) {
		if (rest.length() == 0) {
			c.sendDataToWindow(getErrorMessage("Window",
					".window create <slot> [title…]"));
			return null;
		}
		String[] parts = rest.split("\\s+", 2);
		String name = parts[0];
		String title = parts.length > 1 ? parts[1].trim() : null;
		String normalized = ExtraTextSlotsStore.normalizeName(name);
		if (normalized == null) {
			c.sendDataToWindow(getErrorMessage("Window",
					"Invalid slot name \"" + name
							+ "\". Use lowercase [a-z0-9_], length 1–24; not main/mainDisplay/button_window."));
			return null;
		}
		ExtraTextSlot slot = c.findExtraTextSlot(normalized);
		if (slot == null) {
			slot = new ExtraTextSlot(normalized);
		}
		if (title != null && title.length() > 0) {
			slot.setTitle(title);
		}
		if (!c.upsertExtraTextSlot(slot)) {
			c.sendDataToWindow(getErrorMessage("Window",
					"Could not create slot (max " + ExtraTextSlotsStore.MAX_SLOTS
							+ " or invalid name)."));
			return null;
		}
		echo(c, "Window slot \"" + slot.getName() + "\" ready"
				+ (slot.getTitle() != null ? " (" + slot.getTitle() + ")" : "") + ".");
		return null;
	}

	private Object doDestroy(Connection c, String name) {
		if (name.length() == 0) {
			c.sendDataToWindow(getErrorMessage("Window", ".window destroy <slot>"));
			return null;
		}
		if (!c.removeExtraTextSlot(name)) {
			c.sendDataToWindow(getErrorMessage("Window",
					"No extra text slot named \"" + name + "\"."));
			return null;
		}
		echo(c, "Destroyed window slot \"" + name + "\".");
		return null;
	}

	private Object doOpacity(Connection c, String rest) {
		if (rest.length() == 0) {
			c.sendDataToWindow(getErrorMessage("Window",
					".window opacity <slot> [40-100]"));
			return null;
		}
		String[] parts = rest.split("\\s+", 2);
		String name = parts[0];
		ExtraTextSlot slot = c.findExtraTextSlot(name);
		if (slot == null) {
			c.sendDataToWindow(getErrorMessage("Window",
					"No extra text slot named \"" + name + "\"."));
			return null;
		}
		if (parts.length < 2) {
			echo(c, "Window " + slot.getName() + " opacity: " + slot.getOpacity() + "%");
			return null;
		}
		try {
			int op = Integer.parseInt(parts[1].trim());
			slot.setOpacity(op);
		} catch (Exception e) {
			c.sendDataToWindow(getErrorMessage("Window",
					"Opacity must be an integer 40–100."));
			return null;
		}
		if (!c.upsertExtraTextSlot(slot)) {
			c.sendDataToWindow(getErrorMessage("Window", "Failed to update slot."));
			return null;
		}
		echo(c, "Window " + slot.getName() + " opacity: " + slot.getOpacity() + "%");
		return null;
	}

	private static void echo(Connection c, String msg) {
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor() + msg
				+ Colorizer.getWhiteColor() + "\n");
	}

	private static String shortUsage() {
		return ".window list|show|hide|clear|create|destroy|opacity …";
	}

	private static String helpText() {
		return "\n" + Colorizer.getBrightCyanColor() + ".window" + Colorizer.getWhiteColor() + "\n"
				+ "  list                     — list extra text slots\n"
				+ "  show <slot>              — show overlay\n"
				+ "  hide <slot>              — hide overlay\n"
				+ "  clear <slot>             — clear buffer\n"
				+ "  create <slot> [title…]   — add/update slot (max 8)\n"
				+ "  destroy <slot>           — remove slot\n"
				+ "  opacity <slot> [40-100]  — get/set overlay opacity\n"
				+ "Also: Options → Extra text windows → Manage windows…\n"
				+ "Lua: CreateTextWindow, NoteToWindow, AppendLineToWindow, …\n";
	}
}

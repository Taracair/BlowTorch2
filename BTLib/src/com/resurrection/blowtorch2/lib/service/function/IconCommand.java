package com.resurrection.blowtorch2.lib.service.function;

import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.resurrection.blowtorch2.lib.button.IconBuiltinCatalog;
import com.resurrection.blowtorch2.lib.button.IconCommandParser;
import com.resurrection.blowtorch2.lib.button.IconFaceResolver;
import com.resurrection.blowtorch2.lib.button.IconFolder;
import com.resurrection.blowtorch2.lib.button.IconReports;
import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

import android.content.Context;

/**
 * {@code .icon} sets the picture on one button in the set that is on screen.
 */
public class IconCommand extends SpecialCommand {

	public IconCommand() {
		this.commandName = "icon";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : String.valueOf(o);
		IconCommandParser.Result parsed = IconCommandParser.parse(arg);
		if (parsed.error != null) {
			String title = parsed.error.equals(IconCommandParser.usage())
					? "Icon command usage:" : "Icon command";
			c.sendDataToWindow(getErrorMessage(title, parsed.error));
			return null;
		}
		if (IconCommandParser.ACTION_HELP.equals(parsed.action)) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ IconCommandParser.usage() + "\n");
			return null;
		}
		Context context = c.getContext();
		if (context == null) {
			c.sendDataToWindow(getErrorMessage("Icon command error", "No connection."));
			return null;
		}
		if (IconCommandParser.ACTION_STATUS.equals(parsed.action)) {
			IconFolder.Snapshot snap = IconFolder.snapshot(context);
			say(c, IconReports.status(snap.configured ? snap.line() : ""));
			return null;
		}
		if (IconCommandParser.ACTION_FOLDER.equals(parsed.action)) {
			say(c, IconFolder.snapshot(context).line());
			if (c.getService() != null) {
				c.getService().doRunUiAction("icon:folder");
			}
			return null;
		}
		Map<String, List<String>> catalog = IconBuiltinCatalog.load(context.getAssets());
		if (IconCommandParser.ACTION_LIST.equals(parsed.action)) {
			say(c, IconReports.sets(catalog, IconFolder.snapshot(context).line()));
			return null;
		}
		if (IconCommandParser.ACTION_LIST_FILES.equals(parsed.action)) {
			listFiles(c, context);
			return null;
		}
		if (IconCommandParser.ACTION_LIST_SET.equals(parsed.action)) {
			listSet(c, catalog, parsed.listName);
			return null;
		}
		if (IconCommandParser.ACTION_SET.equals(parsed.action)) {
			apply(c, context, catalog, parsed);
		}
		return null;
	}

	private static void listFiles(final Connection c, final Context context) {
		IconFolder.Snapshot snap = IconFolder.snapshot(context);
		if (!snap.configured) {
			say(c, "No icon folder.\n.icon folder chooses one.\n");
			return;
		}
		if (!snap.readable) {
			c.sendDataToWindow(getErrorMessage("Icon command", "Cannot read the icon folder."));
			return;
		}
		say(c, IconReports.files(snap.displayName, snap.names, snap.folders));
	}

	private static void listSet(final Connection c, final Map<String, List<String>> catalog,
			final String typed) {
		String want = typed == null ? "" : typed;
		String found = null;
		for (String set : catalog.keySet()) {
			if (set.equalsIgnoreCase(want)) {
				found = set;
				break;
			}
		}
		if (found == null) {
			StringBuilder names = new StringBuilder();
			for (String set : catalog.keySet()) {
				if (names.length() > 0) {
					names.append(", ");
				}
				names.append(set);
			}
			String have = names.length() == 0 ? "(none)" : names.toString();
			c.sendDataToWindow(getErrorMessage("Icon command",
					"No icon set " + want + ". Sets: " + have + "."));
			return;
		}
		say(c, IconReports.oneSet(found, catalog.get(found)));
	}

	private static void apply(final Connection c, final Context context,
			final Map<String, List<String>> catalog, final IconCommandParser.Result parsed) {
		String spec;
		String name;
		if (parsed.clear) {
			spec = "";
			if (IconCommandParser.SLOT_HELD.equals(parsed.slot)) {
				name = "darken";
			} else if (IconCommandParser.SLOT_FLIP.equals(parsed.slot)) {
				name = "same";
			} else {
				name = "off";
			}
		} else {
			IconFaceResolver.Outcome outcome = IconFaceResolver.resolve(parsed.token, catalog,
					IconFolder.snapshot(context).folder());
			if (outcome.error != null) {
				c.sendDataToWindow(getErrorMessage("Icon command", outcome.error));
				return;
			}
			spec = outcome.spec;
			if (spec.startsWith("builtin:")) {
				name = spec.substring("builtin:".length());
			} else if (spec.startsWith("named:")) {
				name = spec.substring("named:".length());
				String why = IconFolder.rejection(context, name);
				if (why != null) {
					c.sendDataToWindow(getErrorMessage("Icon command", why));
					return;
				}
			} else {
				name = spec;
			}
		}
		JSONObject json = new JSONObject();
		try {
			json.put("label", parsed.label);
			json.put("slot", parsed.slot == null ? IconCommandParser.SLOT_MAIN : parsed.slot);
			json.put("spec", spec);
			json.put("name", name);
		} catch (JSONException e) {
			c.sendDataToWindow(getErrorMessage("Icon command error", "Could not build that request."));
			return;
		}
		if (c.getService() == null) {
			c.sendDataToWindow(getErrorMessage("Icon command error", "No connection."));
			return;
		}
		c.getService().doRunUiAction("icon:apply:" + json.toString());
	}

	private static void say(final Connection c, final String text) {
		String body = text == null ? "" : text;
		if (!body.endsWith("\n")) {
			body = body + "\n";
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor() + body);
	}
}

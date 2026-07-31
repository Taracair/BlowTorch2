package com.resurrection.blowtorch2.lib.window;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;

public class BetterPluginSelectionDialog extends StandardSelectionDialog implements BaseSelectionDialog.UtilityToolbarListener,BaseSelectionDialog.OptionItemClickListener, PluginSelectorDialog.OnPluginLoadListener {

	ArrayList<String> items = new ArrayList<String>();
	
	public BetterPluginSelectionDialog(Context context,
			IConnectionBinder service) {
		super(context, service);
		
		this.setToolbarListener(this);
		populateFromService();
		
		this.setNewButtonLabel("Load");

		this.setTitle("PLUGINS");

		// The only thing this screen has to say beyond the list is "how do I write
		// one", so the "=" menu carries a single row and promoteHelp() turns the
		// button into "?" that opens it directly — no menu to open first.
		//
		// Both paths report through mOptionItemClickListener, which nothing set
		// here before: the button drew and did nothing at all.
		this.setOptionItemClickListener(this);
		this.addOptionItem(OPTION_AUTHORING_GUIDE_LABEL, true);
		this.promoteHelp();
	}

	private static final String OPTION_AUTHORING_GUIDE_LABEL = "How to write a plugin";

	/** Where the full reference lives. Needs `docs/` pushed to `main` to resolve. */
	private static final String GUIDE_URL =
			"https://github.com/Taracair/BlowTorch2/blob/main/docs/plugin-authoring.md";

	/**
	 * Enough to start on the phone, and a way to the rest.
	 *
	 * <p>Short on purpose: the full guide is a 650-line reference with tables,
	 * which is a bad read on a phone screen and a file that would have to be kept
	 * in step with the markdown by hand. What is here is what a plugin is, where
	 * the file goes, how to load it, and the one warning that matters.
	 */
	private static final String GUIDE_SUMMARY =
			"A plugin is one XML file with Lua inside, kept in\n"
			+ "/sdcard/BlowTorch/plugins/.\n\n"
			+ "Load it with the Load button on this screen: pick the .xml, then "
			+ "Install. Edit the file on a computer and Load it again to reload — "
			+ "there is no editor here.\n\n"
			+ "Each plugin runs its own Lua 5.1 VM in the connection service. It can "
			+ "own triggers, aliases, timers, options and windows, talk to the MUD "
			+ "(text, GMCP, MCP), and call other plugins.\n\n"
			+ "Skeleton:\n\n"
			+ "<blowtorch xmlversion=\"2\">\n"
			+ " <plugins>\n"
			+ "  <plugin name=\"hello\" id=\"90001\">\n"
			+ "   <script name=\"bootstrap\" execute=\"true\"><![CDATA[\n"
			+ "     function sayHi(arg) Note(\"hi\\n\") end\n"
			+ "     RegisterSpecialCommand(\"hello\", \"sayHi\")\n"
			+ "   ]]></script>\n"
			+ "  </plugin>\n"
			+ " </plugins>\n"
			+ "</blowtorch>\n\n"
			+ "Then type .hello in the input bar.\n\n"
			+ "Worth knowing: option and XML values arrive in Lua as strings; there "
			+ "are 8 extra text window slots; .zip packages are not supported; Lua "
			+ "errors appear as red text in the game window.\n\n"
			+ "A plugin is NOT sandboxed. It runs with this app's full privileges — "
			+ "files, network, everything. Only install plugins you trust.\n\n"
			+ "The full reference — every Lua function, the XML schema, the process "
			+ "and threading rules — is on GitHub.";

	/** Rebuild the visible list from {@link IConnectionBinder#getPluginList()} without dismissing. */
	private void populateFromService() {
		HashMap<String,String> plist = null;
		try {
			plist = (HashMap<String,String>)service.getPluginList();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		
		items.clear();
		this.clearListItems();
		if (plist == null) {
			plist = new HashMap<String, String>();
		}
		List<String> sortedSet = new ArrayList<String>(plist.keySet());
		Collections.sort(sortedSet,String.CASE_INSENSITIVE_ORDER);
		for(String key : sortedSet) {
			String info = plist.get(key);
			String title = displayTitleForPluginKey(key, info);
			boolean enabled = true;
			if (info == null || !info.startsWith("MISSING")) {
				try {
					enabled = service.isPluginEnabled(key);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
			// No state badge: the row dims and the toggle changes colour instead.
			items.add(key);
			this.addListItem(key, title, info, 0, enabled);
		}
		this.invalidateList();
	}

	/**
	 * Failed/orphan links are keyed by relative path; show a short name with the path in extras.
	 * Loaded plugins keep their real plugin name as both key and title.
	 */
	private static String displayTitleForPluginKey(String key, String info) {
		if (info != null && info.startsWith("MISSING")) {
			String name = key;
			int slash = name.lastIndexOf('/');
			if (slash >= 0 && slash + 1 < name.length()) {
				name = name.substring(slash + 1);
			}
			if (name.toLowerCase().endsWith(".xml")) {
				name = name.substring(0, name.length() - 4);
			}
			return name;
		}
		return key;
	}
	
	@Override
	public void onCreate(Bundle b) {
		super.onCreate(b);
		setRefreshButtonVisible(true);
		setRefreshButtonListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				populateFromService();
			}
		});
	}
	
	/**
	 * There is no inline editor for a plugin — it is an XML file with Lua inside,
	 * edited on a computer — so this used to be an icon that did nothing at all.
	 * It now answers the questions the icon invites: what is this, where does it
	 * live, and what does it bring with it.
	 */
	@Override
	public void onButtonPressed(View v, int row, int index) {
		String plugin = getItemKey(row);
		if (plugin == null) {
			if (row < 0 || row >= items.size()) {
				return;
			}
			plugin = items.get(row);
		}
		showPluginInfo(plugin);
	}

	/**
	 * A read-only description of one plugin.
	 *
	 * <p>Every call here is a UI → service binder hop, and those are synchronous:
	 * they run on this thread. That is fine for one tap on a dialog row — they
	 * are map reads with no Lua behind them — but it is why this is built on
	 * demand rather than while the list is populated.
	 */
	private void showPluginInfo(String plugin) {
		String info = null;
		try {
			HashMap<String, String> plist = (HashMap<String, String>) service.getPluginList();
			if (plist != null) {
				info = plist.get(plugin);
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		boolean missing = info != null && info.startsWith("MISSING");

		StringBuilder sb = new StringBuilder();
		if (missing) {
			sb.append("The file this row points at is gone.\n\n");
			sb.append("Path: ").append(info.length() > "MISSING".length()
					? info.substring("MISSING".length()).trim() : plugin).append("\n\n");
			sb.append("Nothing is loaded, so it cannot be enabled or inspected. ");
			sb.append("Delete the row to clear it, or put the file back and use Load.");
			showInfoDialog(plugin, sb.toString());
			return;
		}

		if (com.resurrection.blowtorch2.lib.service.Connection.isBuiltInPlugin(plugin)) {
			sb.append("Ships with BlowTorch. Can be disabled, not deleted.\n\n");
		}

		boolean enabled = true;
		try {
			enabled = service.isPluginEnabled(plugin);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		sb.append("Enabled: ").append(enabled ? "yes" : "no").append("\n");

		String path = null;
		try {
			path = service.getPluginPath(plugin);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		sb.append("File: ").append(path == null || path.length() == 0
				? "(not on disk)" : path).append("\n");

		sb.append("Brings: ").append(describeContents(plugin)).append("\n");

		if (info != null && info.length() > 0) {
			sb.append("\n").append(info).append("\n");
		}

		sb.append("\nA plugin is an XML file with Lua inside. There is no editor for one ");
		sb.append("here: change the file, then use Load to bring it back in. Any settings ");
		sb.append("it declares appear in Options under its own heading.");
		showInfoDialog(plugin, sb.toString());
	}

	/** Counts of what a plugin adds, or a plain sentence when it adds none. */
	private String describeContents(String plugin) {
		int triggers = pluginMapSize(PluginPart.TRIGGERS, plugin);
		int aliases = pluginMapSize(PluginPart.ALIASES, plugin);
		int timers = pluginMapSize(PluginPart.TIMERS, plugin);
		if (triggers < 0 && aliases < 0 && timers < 0) {
			return "(could not ask the service)";
		}
		StringBuilder sb = new StringBuilder();
		appendCount(sb, triggers, "trigger", "triggers");
		appendCount(sb, aliases, "alias", "aliases");
		appendCount(sb, timers, "timer", "timers");
		return sb.length() == 0 ? "no triggers, aliases or timers of its own" : sb.toString();
	}

	private enum PluginPart { TRIGGERS, ALIASES, TIMERS }

	/** @return the entry count, or -1 when the service could not be asked. */
	private int pluginMapSize(PluginPart part, String plugin) {
		try {
			java.util.Map<?, ?> map;
			switch (part) {
			case TRIGGERS:
				map = service.getPluginTriggerData(plugin);
				break;
			case ALIASES:
				map = service.getPluginAliases(plugin);
				break;
			default:
				map = service.getPluginTimers(plugin);
				break;
			}
			return map == null ? 0 : map.size();
		} catch (RemoteException e) {
			e.printStackTrace();
			return -1;
		}
	}

	private static void appendCount(StringBuilder sb, int n, String one, String many) {
		if (n <= 0) {
			return;
		}
		if (sb.length() > 0) {
			sb.append(", ");
		}
		sb.append(n).append(" ").append(n == 1 ? one : many);
	}

	private void showInfoDialog(String plugin, String body) {
		new AlertDialog.Builder(getContext())
				.setTitle(plugin)
				.setMessage(body)
				.setPositiveButton("OK", null)
				.show();
	}

	@Override
	public void onButtonStateChanged(ImageButton v, int row, int index, boolean state) {
		String plugin = getItemKey(row);
		if (plugin == null) {
			if (row < 0 || row >= items.size()) {
				return;
			}
			plugin = items.get(row);
		}
		String info = null;
		try {
			HashMap<String, String> plist = (HashMap<String, String>) service.getPluginList();
			if (plist != null) {
				info = plist.get(plugin);
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		if (info != null && info.startsWith("MISSING")) {
			Toast.makeText(getContext(), "Cannot toggle a missing plugin link — delete it instead.",
					Toast.LENGTH_SHORT).show();
			return;
		}

		boolean currentlyEnabled = true;
		try {
			currentlyEnabled = service.isPluginEnabled(plugin);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		boolean next = !currentlyEnabled;

		if (!next && "button_window".equals(plugin)) {
			Toast.makeText(getContext(),
					"Cannot disable button_window — it provides the on-screen buttons.",
					Toast.LENGTH_LONG).show();
			applyToggleTint(v, true);
			this.setItemEnabled(row, true);
			return;
		}

		boolean applied = false;
		try {
			applied = service.setPluginEnabled(plugin, next);
		} catch (RemoteException e) {
			e.printStackTrace();
		}

		if (!applied) {
			// Service refused (e.g. required plugin) — keep UI showing current state.
			applyToggleTint(v, currentlyEnabled);
			this.setItemEnabled(row, currentlyEnabled);
			return;
		}

		if (next) {
			applyToggleTint(v, true);
			this.setItemEnabled(row, true);
			Toast.makeText(getContext(), "Enabled " + plugin, Toast.LENGTH_SHORT).show();
		} else {
			applyToggleTint(v, false);
			this.setItemEnabled(row, false);
			String msg = "Disabled " + plugin;
			if ("starter_tutorial".equals(plugin)) {
				msg = "Disabled starter_tutorial — .tutorial commands will stop until re-enabled.";
			}
			Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public void onItemDeleted(int row) {
		String plugin = mLastDeletedKey;
		if (plugin == null) {
			if (row < 0 || row >= items.size()) {
				return;
			}
			plugin = items.get(row);
		}
		items.remove(plugin);
		mLastDeletedKey = null;
		
		try {
			service.deletePlugin(plugin);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onNewPressed(View v) {
		try {
			String extDir = Environment.getExternalStorageDirectory().getAbsolutePath();
			File plugfile = new File(extDir + "/BlowTorch/plugins");
			if (!plugfile.exists()) {
				plugfile.mkdirs();
			}
			PluginSelectorDialog loader = new PluginSelectorDialog(v.getContext(), service, this);
			loader.show();
		} catch (Exception e) {
			Log.e("BlowTorch", "Failed to open plugin loader", e);
			new AlertDialog.Builder(v.getContext())
					.setTitle("Plugins")
					.setMessage("Could not open the plugin folder.\n\n" + e.getMessage())
					.setPositiveButton("OK", null)
					.show();
		}
	}

	@Override
	public void onDonePressed(View v) {
		try {
			service.saveSettings();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	/**
	 * One row: the authoring guide. With {@code promoteHelp()} the "?" button
	 * reports row 0 without the menu ever being shown, so there is nothing to
	 * hide in that case — {@link #hideOptionsMenu()} is harmless either way.
	 */
	@Override
	public void onOptionItemClicked(int row) {
		this.hideOptionsMenu();
		if (row == 0) {
			showAuthoringGuide();
		}
	}

	/** The summary, with a button out to the full reference. */
	private void showAuthoringGuide() {
		AlertDialog dialog = new AlertDialog.Builder(getContext())
				.setTitle("Writing plugins")
				.setMessage(GUIDE_SUMMARY)
				.setPositiveButton("Full guide (GitHub)", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface d, int which) {
						openGuideInBrowser();
					}
				})
				.setNegativeButton("Close", null)
				.show();
		TextView body = (TextView) dialog.findViewById(android.R.id.message);
		if (body != null) {
			// The skeleton is indented XML; proportional text turns it into a mess.
			body.setTypeface(Typeface.MONOSPACE);
			body.setTextSize(12f);
			body.setTextIsSelectable(true);
		}
	}

	/**
	 * No browser is a possibility on a stripped device, and an unhandled
	 * {@link ActivityNotFoundException} here would take the dialog down with it.
	 */
	private void openGuideInBrowser() {
		try {
			Intent browse = new Intent(Intent.ACTION_VIEW, Uri.parse(GUIDE_URL));
			browse.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			getContext().startActivity(browse);
		} catch (ActivityNotFoundException e) {
			new AlertDialog.Builder(getContext())
					.setTitle("No browser")
					.setMessage("Nothing here opens web links. The guide is at:\n\n" + GUIDE_URL)
					.setPositiveButton("OK", null)
					.show();
		}
	}

	@Override
	public void willShowToolbar(LinearLayout toolbar, int row) {
		String plugin = getItemKey(row);
		if (plugin == null || toolbar.getChildCount() == 0) {
			return;
		}
		boolean enabled = true;
		try {
			enabled = service.isPluginEnabled(plugin);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		applyToggleTint((ImageButton) toolbar.getChildAt(0), enabled);
	}

	@Override
	public void willHideToolbar(LinearLayout v, int row) {
		// no-op
	}
	
	@Override
	public void onPluginLoad() {
		this.dismiss();
	}

}

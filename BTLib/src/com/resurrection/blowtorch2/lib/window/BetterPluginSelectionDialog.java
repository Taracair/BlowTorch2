package com.resurrection.blowtorch2.lib.window;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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
	}

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
			int icon = enabled ? R.drawable.toolbar_mini_enabled : R.drawable.toolbar_mini_disabled;
			items.add(key);
			this.addListItem(key, title, info, icon, enabled);
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
	
	@Override
	public void onButtonPressed(View v, int row, int index) {
		// Plugins have no inline editor; toggle/delete cover enable and unload.
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
			if (v != null) {
				v.setImageResource(R.drawable.toolbar_toggleon_button);
			}
			this.setItemMiniIcon(row, R.drawable.toolbar_mini_enabled);
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
			if (v != null) {
				v.setImageResource(currentlyEnabled
						? R.drawable.toolbar_toggleon_button
						: R.drawable.toolbar_toggleoff_button);
			}
			this.setItemMiniIcon(row, currentlyEnabled
					? R.drawable.toolbar_mini_enabled
					: R.drawable.toolbar_mini_disabled);
			return;
		}

		if (next) {
			if (v != null) {
				v.setImageResource(R.drawable.toolbar_toggleon_button);
			}
			this.setItemMiniIcon(row, R.drawable.toolbar_mini_enabled);
			Toast.makeText(getContext(), "Enabled " + plugin, Toast.LENGTH_SHORT).show();
		} else {
			if (v != null) {
				v.setImageResource(R.drawable.toolbar_toggleoff_button);
			}
			this.setItemMiniIcon(row, R.drawable.toolbar_mini_disabled);
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

	@Override
	public void onOptionItemClicked(int row) {
		Log.e("Foo","Option Item " + row + " clicked.");
		this.hideOptionsMenu();
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
		ImageButton b = (ImageButton) toolbar.getChildAt(0);
		if (enabled) {
			b.setImageResource(R.drawable.toolbar_toggleon_button);
		} else {
			b.setImageResource(R.drawable.toolbar_toggleoff_button);
		}
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

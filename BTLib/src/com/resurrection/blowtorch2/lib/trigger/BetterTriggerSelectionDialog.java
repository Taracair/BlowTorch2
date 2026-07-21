package com.resurrection.blowtorch2.lib.trigger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.window.PluginFilterSelectionDialog;
import com.resurrection.blowtorch2.lib.window.BaseSelectionDialog;

public class BetterTriggerSelectionDialog extends PluginFilterSelectionDialog implements BaseSelectionDialog.UtilityToolbarListener {

	HashMap<String,TriggerData> dataMap;
	String[] sortedKeys;
	private boolean mShowWarning = true;

	public BetterTriggerSelectionDialog(Context context,
			IConnectionBinder service,boolean showWarning) {
		super(context, service);
		setGroupFilterEnabled(true);
		refreshGroupNamesFromService();
		refreshGroupSpinner();
		buildList();
		this.setToolbarListener(this);
		this.setTitle("TRIGGERS");
		mShowWarning = showWarning;
	}

	@Override
	protected String getEnableAllLabel() {
		return "Enable all triggers (current list)";
	}

	@Override
	protected String getDisableAllLabel() {
		return "Disable ALL triggers (current list)";
	}

	@Override
	protected void rebuildFilteredList() {
		refreshGroupNamesFromService();
		refreshGroupSpinner();
		buildList();
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void refreshGroupNamesFromService() {
		TreeSet<String> set = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		try {
			if (ALL_SETTINGS.equals(currentPlugin)) {
				collectTriggerGroups(set, (Map<String, TriggerData>) service.getTriggerData());
				if (pluginList != null) {
					for (String p : pluginList) {
						collectTriggerGroups(set,
								(Map<String, TriggerData>) service.getPluginTriggerData(p));
					}
				}
			} else if (MAIN_SETTINGS.equals(currentPlugin)) {
				collectTriggerGroups(set, (Map<String, TriggerData>) service.getTriggerData());
			} else {
				collectTriggerGroups(set,
						(Map<String, TriggerData>) service.getPluginTriggerData(currentPlugin));
			}
		} catch (RemoteException e) {
			// keep empty
		}
		groupNames = set.toArray(new String[set.size()]);
	}

	private static void collectTriggerGroups(TreeSet<String> set, Map<String, TriggerData> map) {
		if (map == null) {
			return;
		}
		for (TriggerData t : map.values()) {
			if (t == null) {
				continue;
			}
			String g = t.getGroup();
			if (g != null && g.length() > 0 && !TriggerData.DEFAULT_GROUP.equals(g)) {
				set.add(g);
			}
		}
	}

	@Override
	public void onButtonPressed(View v, int row, int index) {
		String key = getItemKey(row);
		TriggerData d = dataMap.get(key);
		Log.e("Trigger","trigger item selected for modification: "+d.getName());

		TriggerEditorDialog editor = new TriggerEditorDialog(BetterTriggerSelectionDialog.this.getContext(),d,service,triggerEditorDoneHandler,getSourcePlugin(key),mShowWarning);
		editor.show();
	}

	@Override
	public void onButtonStateChanged(ImageButton v, int row, int index, boolean statea) {
		String key = getItemKey(row);
		TriggerData d = dataMap.get(key);
		boolean state = !d.isEnabled();
		d.setEnabled(state);
		String src = getSourcePlugin(key);
		try {
			if(MAIN_SETTINGS.equals(src)) {
				service.setTriggerEnabled(state, d.getName());
			} else {
				service.setPluginTriggerEnabled(src, state, d.getName());
			}
		} catch (RemoteException e) {

		}
		if(state) {
			v.setImageResource(R.drawable.toolbar_toggleon_button);
			this.setItemMiniIcon(row, R.drawable.toolbar_mini_enabled);
		} else {
			v.setImageResource(R.drawable.toolbar_toggleoff_button);
			this.setItemMiniIcon(row, R.drawable.toolbar_mini_disabled);
		}
		Log.e("Trigger","trigger item selected for enable/disable: "+d.getName());
	}

	@Override
	public void onItemDeleted(int row) {
		String key = getItemKey(row);
		TriggerData d = dataMap.get(key);
		String src = getSourcePlugin(key);

		try {
			if(MAIN_SETTINGS.equals(src)) {
				service.deleteTrigger(d.getName());
			} else {
				service.deletePluginTrigger(src, d.getName());
			}
		} catch (RemoteException e) {

		}
		Log.e("Trigger","trigger item selected for delete: "+d.getName());
	}

	@Override
	public void onNewPressed(View v) {
		TriggerEditorDialog editor = new TriggerEditorDialog(BetterTriggerSelectionDialog.this.getContext(),null,service,triggerEditorDoneHandler,getEditorPlugin(),mShowWarning);
		editor.show();
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
	public void onHelp() {
	}

	@Override
	public void onEnableAll() {
		setAllTriggersEnabled(true);
	}

	@Override
	public void onDisableAll() {
		final String filter = getCurrentFilterLabel();
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle("Disable ALL triggers?");
		builder.setMessage("This disables ALL triggers in the current filter ("
				+ filter + "). Continue?");
		builder.setPositiveButton("Disable all", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				setAllTriggersEnabled(false);
			}
		});
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.create().show();
	}

	private void setAllTriggersEnabled(boolean enabled) {
		if (sortedKeys == null || sortedKeys.length == 0) {
			Toast.makeText(getContext(), "No triggers in current list", Toast.LENGTH_SHORT).show();
			return;
		}
		int count = 0;
		try {
			for (String key : sortedKeys) {
				TriggerData d = dataMap.get(key);
				if (d == null) {
					continue;
				}
				d.setEnabled(enabled);
				String src = getSourcePlugin(key);
				if (MAIN_SETTINGS.equals(src)) {
					service.setTriggerEnabled(enabled, d.getName());
				} else {
					service.setPluginTriggerEnabled(src, enabled, d.getName());
				}
				count++;
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		buildList();
		String verb = enabled ? "Enabled" : "Disabled";
		Toast.makeText(getContext(),
				verb + " " + count + " trigger" + (count == 1 ? "" : "s")
						+ " (" + getCurrentFilterLabel() + ")",
				Toast.LENGTH_SHORT).show();
	}

	@SuppressWarnings("unchecked")
	private void buildList() {
		try {
			dataMap = new HashMap<String, TriggerData>();
			loadScopedData(dataMap, new ScopedMapLoader<TriggerData>() {
				@Override
				public Map<String, TriggerData> loadMain() throws RemoteException {
					return (Map<String, TriggerData>) service.getTriggerData();
				}

				@Override
				public Map<String, TriggerData> loadPlugin(String plugin) throws RemoteException {
					return (Map<String, TriggerData>) service.getPluginTriggerData(plugin);
				}
			});
		} catch (RemoteException e) {
			if (dataMap == null) {
				dataMap = new HashMap<String, TriggerData>();
			}
		}

		ArrayList<String> keys = new ArrayList<String>();
		for (String key : dataMap.keySet()) {
			TriggerData data = dataMap.get(key);
			if (matchesGroupFilter(data)) {
				keys.add(key);
			}
		}
		sortedKeys = keys.toArray(new String[keys.size()]);
		Arrays.sort(sortedKeys, new Comparator<String>() {
			@Override
			public int compare(String a, String b) {
				TriggerData da = dataMap.get(a);
				TriggerData db = dataMap.get(b);
				String ga = groupKey(da);
				String gb = groupKey(db);
				int gcmp = ga.compareToIgnoreCase(gb);
				if (gcmp != 0) {
					return gcmp;
				}
				return displayNameForKey(a).compareToIgnoreCase(displayNameForKey(b));
			}
		});
		clearListItems();
		for(int i=0;i<sortedKeys.length;i++) {
			TriggerData data = dataMap.get(sortedKeys[i]);
			int resource = 0;
			if(data.isEnabled()) {
				resource = R.drawable.toolbar_mini_enabled;
			} else {
				resource = R.drawable.toolbar_mini_disabled;
			}
			String title = data.getName();
			if (ALL_SETTINGS.equals(currentPlugin)) {
				String src = getSourcePlugin(sortedKeys[i]);
				if (!MAIN_SETTINGS.equals(src)) {
					title = src + ": " + title;
				}
			}
			this.addListItem(sortedKeys[i], title, formatExtra(data), resource, data.isEnabled());
		}

		invalidateList();

	}

	private boolean matchesGroupFilter(TriggerData data) {
		if (currentGroupFilter == null) {
			return true;
		}
		return currentGroupFilter.equals(groupKey(data));
	}

	private static String groupKey(TriggerData data) {
		if (data == null || data.getGroup() == null) {
			return TriggerData.DEFAULT_GROUP;
		}
		return data.getGroup();
	}

	/** Pattern subtitle; prefix with [group] when a non-default group is set. */
	private static String formatExtra(TriggerData data) {
		String pattern = data.getPattern() != null ? data.getPattern() : "";
		String group = data.getGroup();
		if (group != null && group.length() > 0
				&& !TriggerData.DEFAULT_GROUP.equals(group)) {
			return "[" + group + "] " + pattern;
		}
		return pattern;
	}

	@Override
	public List<String> getPluginList() throws RemoteException {
		List<String> foo = (List<String>)service.getPluginsWithTriggers();
		return foo;
	}

	@Override
	public void willShowToolbar(LinearLayout toolbar, int row) {
		TriggerData data = dataMap.get(getItemKey(row));
		if (data == null || toolbar.getChildCount() == 0) {
			return;
		}
		ImageButton b = (ImageButton) toolbar.getChildAt(0);
		if (data.isEnabled()) {
			b.setImageResource(R.drawable.toolbar_toggleon_button);
		} else {
			b.setImageResource(R.drawable.toolbar_toggleoff_button);
		}
	}

	private final Handler triggerEditorDoneHandler = new Handler() {

		public void handleMessage(Message msg) {
			switch(msg.what) {
			case 100:
				TriggerData d = (TriggerData)msg.obj;
				BetterTriggerSelectionDialog.this.refreshGroupNamesFromService();
				BetterTriggerSelectionDialog.this.refreshGroupSpinner();
				BetterTriggerSelectionDialog.this.buildList();
				BetterTriggerSelectionDialog.this.scrollToSelection(d.getName());
				break;
			}

		}
	};

	@Override
	public void willHideToolbar(LinearLayout v, int row) {
	}

}

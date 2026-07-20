package com.resurrection.blowtorch2.lib.trigger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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

	/**
	 * {@code null} = all groups; {@code ""} = default/ungrouped;
	 * otherwise exact group name match.
	 */
	private String currentGroupFilter = null;
	/** Named (non-default) groups for the current plugin filter, sorted. */
	private String[] groupNames = new String[0];

	public BetterTriggerSelectionDialog(Context context,
			IConnectionBinder service,boolean showWarning) {
		super(context, service);
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
	protected String getCurrentFilterLabel() {
		String base = super.getCurrentFilterLabel();
		if (currentGroupFilter == null) {
			return base;
		}
		String g = currentGroupFilter.length() == 0 ? "(default)" : currentGroupFilter;
		return base + " / " + g;
	}

	@Override
	protected void addPluginFilterOptions() {
		if (pluginList == null) {
			pluginList = new String[0];
		}
		this.addOptionItem(getEnableAllLabel(), true);
		this.addOptionItem(getDisableAllLabel(), true);
		this.addOptionDivider("Filter by plugin", false);
		this.addOptionItem("Main", false);
		for (int i = 0; i < pluginList.length; i++) {
			this.addOptionItem(pluginList[i], false);
		}
		refreshGroupNamesFromService();
		this.addOptionDivider("Filter by group", false);
		this.addOptionItem("All groups", false);
		this.addOptionItem("(default)", false);
		for (int i = 0; i < groupNames.length; i++) {
			this.addOptionItem(groupNames[i], false);
		}
	}

	private void rebuildOptionsMenu() {
		this.clearOptionItems();
		addPluginFilterOptions();
		this.notifyOptionItemsChanged();
	}

	@SuppressWarnings("unchecked")
	private void refreshGroupNamesFromService() {
		HashMap<String, TriggerData> map = null;
		try {
			if (MAIN_SETTINGS.equals(currentPlugin)) {
				map = (HashMap<String, TriggerData>) service.getTriggerData();
			} else {
				map = (HashMap<String, TriggerData>) service.getPluginTriggerData(currentPlugin);
			}
		} catch (RemoteException e) {
			map = null;
		}
		TreeSet<String> set = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		if (map != null) {
			for (TriggerData t : map.values()) {
				if (t == null) {
					continue;
				}
				String g = t.getGroup();
				if (g != null && g.length() > 0
						&& !TriggerData.DEFAULT_GROUP.equals(g)) {
					set.add(g);
				}
			}
		}
		groupNames = set.toArray(new String[set.size()]);
	}

	/** First options row index for the group-filter divider. */
	private int groupFilterDividerIndex() {
		return OPTION_MAIN + 1 + (pluginList == null ? 0 : pluginList.length);
	}

	@Override
	public void onButtonPressed(View v, int row, int index) {
		TriggerData d = dataMap.get(getItemKey(row));
		Log.e("Trigger","trigger item selected for modification: "+d.getName());

		TriggerEditorDialog editor = new TriggerEditorDialog(BetterTriggerSelectionDialog.this.getContext(),d,service,triggerEditorDoneHandler,currentPlugin,mShowWarning);
		editor.show();
	}

	@Override
	public void onButtonStateChanged(ImageButton v, int row, int index, boolean statea) {
		TriggerData d = dataMap.get(getItemKey(row));
		boolean state = !d.isEnabled();
		d.setEnabled(state);
		try {
			if(currentPlugin.equals(MAIN_SETTINGS)) {
				service.setTriggerEnabled(state, d.getName());
			} else {
				service.setPluginTriggerEnabled(currentPlugin, state, d.getName());
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
		TriggerData d = dataMap.get(getItemKey(row));

		try {
			if(currentPlugin.equals(MAIN_SETTINGS)) {
				service.deleteTrigger(d.getName());
			} else {
				service.deletePluginTrigger(currentPlugin, d.getName());
			}
		} catch (RemoteException e) {

		}
		Log.e("Trigger","trigger item selected for delete: "+d.getName());
	}

	@Override
	public void onNewPressed(View v) {
		TriggerEditorDialog editor = new TriggerEditorDialog(BetterTriggerSelectionDialog.this.getContext(),null,service,triggerEditorDoneHandler,currentPlugin,mShowWarning);
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
				if (currentPlugin.equals(MAIN_SETTINGS)) {
					service.setTriggerEnabled(enabled, d.getName());
				} else {
					service.setPluginTriggerEnabled(currentPlugin, enabled, d.getName());
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
			if(currentPlugin.equals(MAIN_SETTINGS)) {
				dataMap = (HashMap<String, TriggerData>) service.getTriggerData();
			} else {
				dataMap = (HashMap<String, TriggerData>) service.getPluginTriggerData(currentPlugin);
			}
		} catch (RemoteException e) {

		}

		if (dataMap == null) {
			dataMap = new HashMap<String, TriggerData>();
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
				return a.compareToIgnoreCase(b);
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
			this.addListItem(data.getName(), formatExtra(data), resource, data.isEnabled());
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
	public void onOptionItemClicked(int row) {
		this.hideOptionsMenu();
		if (row == OPTION_ENABLE_ALL) {
			onEnableAll();
			return;
		}
		if (row == OPTION_DISABLE_ALL) {
			onDisableAll();
			return;
		}
		if (row == OPTION_FILTER_DIVIDER) {
			return;
		}

		final int groupDivider = groupFilterDividerIndex();
		if (row == OPTION_MAIN) {
			currentPlugin = MAIN_SETTINGS;
			currentGroupFilter = null;
			rebuildOptionsMenu();
			buildList();
			return;
		}
		if (row > OPTION_MAIN && row < groupDivider) {
			int pluginIndex = row - (OPTION_MAIN + 1);
			if (pluginIndex >= 0 && pluginIndex < pluginList.length) {
				currentPlugin = pluginList[pluginIndex];
			}
			currentGroupFilter = null;
			rebuildOptionsMenu();
			buildList();
			return;
		}
		if (row == groupDivider) {
			return;
		}
		if (row == groupDivider + 1) {
			currentGroupFilter = null; // All groups
		} else if (row == groupDivider + 2) {
			currentGroupFilter = TriggerData.DEFAULT_GROUP; // (default)
		} else {
			int gi = row - (groupDivider + 3);
			if (gi >= 0 && gi < groupNames.length) {
				currentGroupFilter = groupNames[gi];
			}
		}
		buildList();
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
				BetterTriggerSelectionDialog.this.rebuildOptionsMenu();
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

package com.resurrection.blowtorch2.lib.timer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.window.PluginFilterSelectionDialog;
import com.resurrection.blowtorch2.lib.window.BaseSelectionDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

public class BetterTimerSelectionDialog extends PluginFilterSelectionDialog implements BaseSelectionDialog.UtilityToolbarListener {

	HashMap<String,TimerData> dataMap;
	String[] sortedKeys;

	public BetterTimerSelectionDialog(Context context,
			IConnectionBinder service) {
		super(context, service);
		setGroupFilterEnabled(true);
		refreshGroupNamesFromService();
		refreshGroupSpinner();
		buildList();
		this.setToolbarListener(this);

		this.clearToolbarButtons();
		this.addToolbarButton(R.drawable.toolbar_play_button,0);
		this.addToolbarButton(R.drawable.toolbar_stop_button,1);
		this.addToolbarButton(R.drawable.toolbar_modify_button,2);
		this.addToolbarDeleteButton(R.drawable.toolbar_delete_button,3);

		this.setTitle("TIMERS");
	}

	/** Timers use play/pause — no enable/disable bulk actions. */
	@Override
	protected void addPluginFilterOptions() {
		// Empty options menu hides the "=" button.
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
				collectTimerGroups(set, (Map<String, TimerData>) service.getTimers());
				if (pluginList != null) {
					for (String p : pluginList) {
						collectTimerGroups(set,
								(Map<String, TimerData>) service.getPluginTimers(p));
					}
				}
			} else if (MAIN_SETTINGS.equals(currentPlugin)) {
				collectTimerGroups(set, (Map<String, TimerData>) service.getTimers());
			} else {
				collectTimerGroups(set,
						(Map<String, TimerData>) service.getPluginTimers(currentPlugin));
			}
		} catch (RemoteException e) {
			// keep empty
		}
		groupNames = set.toArray(new String[set.size()]);
	}

	private static void collectTimerGroups(TreeSet<String> set, Map<String, TimerData> map) {
		if (map == null) {
			return;
		}
		for (TimerData t : map.values()) {
			if (t == null) {
				continue;
			}
			String g = t.getGroup();
			if (g != null && g.length() > 0 && !TimerData.DEFAULT_GROUP.equals(g)) {
				set.add(g);
			}
		}
	}

	@Override
	public void onButtonPressed(View v, int row, int index) {
		String key = getItemKey(row);
		TimerData d = dataMap.get(key);
		String src = getSourcePlugin(key);

		String action = "";
		int icon = 0;
		switch(index) {
		case 0:
			if(d.isPlaying()) {
				icon = R.drawable.toolbar_mini_pause;
				ImageButton b = (ImageButton)v;
				b.setImageResource(R.drawable.toolbar_pause_button);
				try {
					if(MAIN_SETTINGS.equals(src)) {
						service.pauseTimer(d.getName());
					} else {
						service.pausePluginTimer(d.getName(),src);
					}

				} catch (RemoteException e) {
					e.printStackTrace();
				}
			} else {
				icon = R.drawable.toolbar_mini_play;
				ImageButton b = (ImageButton)v;
				b.setImageResource(R.drawable.toolbar_play_button);

				try {
					if(MAIN_SETTINGS.equals(src)) {
						service.startTimer(d.getName());
					} else {
						service.startPluginTimer(d.getName(),src);
					}

				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
			d.setPlaying(!d.isPlaying());
			action = "play/pause";
			break;
		case 1:
			action = "stop";
			icon = R.drawable.toolbar_mini_stop;
			try {
				if(MAIN_SETTINGS.equals(src)) {
					service.stopTimer(d.getName());
				} else {
					service.stopPluginTimer(d.getName(),src);
				}

			} catch (RemoteException e) {
				e.printStackTrace();
			}
			break;
		case 2:
			action = "mod";
			TimerEditorDialog editor = new TimerEditorDialog(BetterTimerSelectionDialog.this.getContext(),src,d,service,triggerEditorDoneHandler);
			editor.show();
			break;
		}
		Log.e("Trigger","timer item selected for "+action+": "+d.getName());

		this.setItemMiniIcon(row, icon);
	}

	@Override
	public void onButtonStateChanged(ImageButton v, int row, int index, boolean statea) {
	}

	@Override
	public void onItemDeleted(int row) {
		String key = getItemKey(row);
		TimerData d = dataMap.get(key);
		String src = getSourcePlugin(key);

		try {
			if(MAIN_SETTINGS.equals(src)) {
				service.deleteTimer(d.getName());
			} else {
				service.deletePluginTimer(src, d.getName());
			}
		} catch (RemoteException e) {

		}
		Log.e("Trigger","trigger item selected for delete: "+d.getName());
	}

	@Override
	public void onNewPressed(View v) {
		TimerEditorDialog editor = new TimerEditorDialog(BetterTimerSelectionDialog.this.getContext(),getEditorPlugin(),null,service,triggerEditorDoneHandler);
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
	}

	@Override
	public void onDisableAll() {
	}

	@SuppressWarnings("unchecked")
	private void buildList() {
		try {
			dataMap = new HashMap<String, TimerData>();
			loadScopedData(dataMap, new ScopedMapLoader<TimerData>() {
				@Override
				public Map<String, TimerData> loadMain() throws RemoteException {
					return (Map<String, TimerData>) service.getTimers();
				}

				@Override
				public Map<String, TimerData> loadPlugin(String plugin) throws RemoteException {
					return (Map<String, TimerData>) service.getPluginTimers(plugin);
				}
			});
		} catch (RemoteException e) {
			if (dataMap == null) {
				dataMap = new HashMap<String, TimerData>();
			}
		}

		ArrayList<String> keys = new ArrayList<String>();
		for (String key : dataMap.keySet()) {
			TimerData data = dataMap.get(key);
			if (matchesGroupFilter(data)) {
				keys.add(key);
			}
		}
		sortedKeys = keys.toArray(new String[keys.size()]);
		Arrays.sort(sortedKeys, new Comparator<String>() {
			@Override
			public int compare(String a, String b) {
				TimerData da = dataMap.get(a);
				TimerData db = dataMap.get(b);
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
		String tag = "";
		for(int i=0;i<sortedKeys.length;i++) {
			TimerData data = dataMap.get(sortedKeys[i]);
			int resource = 0;
			if(data.isPlaying()) {
				resource = R.drawable.toolbar_mini_play;
				tag = " Running.";
			} else {
				if(data.getRemainingTime() != data.getSeconds()) {
					resource = R.drawable.toolbar_mini_pause;
					tag = " Paused, " + data.getRemainingTime() +" seconds remaining.";
				} else {
					resource = R.drawable.toolbar_mini_stop;
					tag = " Stopped.";
				}
			}
			String title = data.getName();
			if (ALL_SETTINGS.equals(currentPlugin)) {
				String src = getSourcePlugin(sortedKeys[i]);
				if (!MAIN_SETTINGS.equals(src)) {
					title = src + ": " + title;
				}
			}
			this.addListItem(sortedKeys[i], title, formatExtra(data, tag), resource, true);
		}

		invalidateList();

	}

	private boolean matchesGroupFilter(TimerData data) {
		if (currentGroupFilter == null) {
			return true;
		}
		return currentGroupFilter.equals(groupKey(data));
	}

	private static String groupKey(TimerData data) {
		if (data == null || data.getGroup() == null) {
			return TimerData.DEFAULT_GROUP;
		}
		return data.getGroup();
	}

	private static String formatExtra(TimerData data, String statusTag) {
		String base = data.getSeconds() + " Seconds." + statusTag;
		String group = data.getGroup();
		if (group != null && group.length() > 0
				&& !TimerData.DEFAULT_GROUP.equals(group)) {
			return "[" + group + "] " + base;
		}
		return base;
	}

	@Override
	public List<String> getPluginList() throws RemoteException {
		List<String> foo = (List<String>)service.getPluginsWithTimers();
		return foo;
	}

	@Override
	public void willShowToolbar(LinearLayout toolbar, int row) {
		TimerData data = dataMap.get(getItemKey(row));
		if (data == null || toolbar.getChildCount() == 0) {
			return;
		}
		ImageButton play = (ImageButton) toolbar.getChildAt(0);
		if (data.isPlaying()) {
			play.setImageResource(R.drawable.toolbar_pause_button);
		} else {
			play.setImageResource(R.drawable.toolbar_play_button);
		}
	}

	@Override
	public void willHideToolbar(LinearLayout toolbar,int row) {

	}

	private final Handler triggerEditorDoneHandler = new Handler() {

		public void handleMessage(Message msg) {
			switch(msg.what) {
			case 100:
				TimerData d = (TimerData)msg.obj;
				BetterTimerSelectionDialog.this.refreshGroupNamesFromService();
				BetterTimerSelectionDialog.this.refreshGroupSpinner();
				BetterTimerSelectionDialog.this.buildList();
				BetterTimerSelectionDialog.this.scrollToSelection(d.getName());
				break;
			}

		}
	};

}

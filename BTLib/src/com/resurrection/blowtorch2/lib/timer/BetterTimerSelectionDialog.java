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

public class BetterTimerSelectionDialog extends PluginFilterSelectionDialog implements BaseSelectionDialog.UtilityToolbarListener {

	/** The ? beside New / More / Done; see {@link com.resurrection.blowtorch2.lib.window.EditorHelp}. */
	@Override
	protected String getHelpTitle() {
		return "Timers";
	}

	@Override
	protected String getHelpBody() {
		return com.resurrection.blowtorch2.lib.window.EditorHelp.TIMERS;
	}


	HashMap<String,TimerData> dataMap;
	String[] sortedKeys;

	/** Options (=) row: soft-clear the Group filter's named label. */
	private static final int OPTION_CLEAR_GROUP = 0;

	public BetterTimerSelectionDialog(Context context,
			IConnectionBinder service) {
		super(context, service);
		setGroupFilterEnabled(true);
		refreshGroupNamesFromService();
		refreshGroupSpinner();
		buildList();
		this.setToolbarListener(this);

		this.clearToolbarButtons();
		this.addToolbarButton(R.drawable.ic_row_play,0);
		this.addToolbarButton(R.drawable.ic_row_stop,1);
		this.addToolbarButton(R.drawable.ic_row_edit,2);
		this.addToolbarDeleteButton(R.drawable.ic_row_delete,3);
		// Play is first here, so a row tap would otherwise start the timer instead of
		// opening its editor.
		this.setRowTapButtonId(2);

		this.setTitle("TIMERS");
	}

	/** Timers use play/pause — no enable/disable bulk actions. */
	@Override
	protected void addPluginFilterOptions() {
		this.addOptionItem("Clear group label (keeps timers)", true);
	}

	@Override
	public void onOptionItemClicked(int row) {
		if (row == OPTION_CLEAR_GROUP) {
			hideOptionsMenu();
			confirmClearGroupLabel();
			return;
		}
		super.onOptionItemClicked(row);
	}

	private void confirmClearGroupLabel() {
		if (currentGroupFilter == null || currentGroupFilter.length() == 0) {
			String which = currentGroupFilter == null ? "All" : "(default)";
			new AlertDialog.Builder(getContext())
					.setTitle("Nothing cleared")
					.setMessage(which + " is not a group label. Nothing is cleared and nothing is deleted. Pick a named group in the Group filter first.")
					.setPositiveButton("OK", null)
					.show();
			return;
		}
		final String group = currentGroupFilter;
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle("Clear group label?");
		builder.setMessage("This removes the group name \"" + group
				+ "\" from every timer in the current filter ("
				+ getCurrentFilterLabel()
				+ "). The timers stay. Continue?");
		builder.setPositiveButton("Clear label", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				clearGroupLabel(group);
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

	private void clearGroupLabel(String group) {
		if (sortedKeys == null || sortedKeys.length == 0) {
			Toast.makeText(getContext(), "No timers in current list", Toast.LENGTH_SHORT).show();
			return;
		}
		int count = 0;
		try {
			for (String key : sortedKeys) {
				TimerData d = dataMap.get(key);
				if (d == null || !group.equals(groupKey(d))) {
					continue;
				}
				TimerData from = d.copy();
				TimerData to = d.copy();
				to.setGroup(TimerData.DEFAULT_GROUP);
				String src = getSourcePlugin(key);
				if (MAIN_SETTINGS.equals(src)) {
					service.updateTimer(from, to);
				} else {
					service.updatePluginTimer(src, from, to);
				}
				count++;
			}
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"BetterTimerSelectionDialog.clear group label", e);
		}
		currentGroupFilter = null;
		com.resurrection.blowtorch2.lib.util.SettingsSaver.saveInBackground(service);
		refreshGroupNamesFromService();
		refreshGroupSpinner();
		buildList();
		Toast.makeText(getContext(),
				"Cleared group label from " + count
						+ " timer" + (count == 1 ? "" : "s"),
				Toast.LENGTH_SHORT).show();
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
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("BetterTimerSelectionDialog.load timers", e);
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
		if (key == null) {
			return;
		}
		TimerData d = dataMap.get(key);
		if (d == null) {
			return;
		}
		String src = getSourcePlugin(key);

		String action = "";
		int icon = 0;
		switch(index) {
		case 0:
			if(d.isPlaying()) {
				icon = R.drawable.ic_mini_pause;
				// A row tap reaches here with no view — only the row's own button has one.
				if (v instanceof ImageButton) {
					((ImageButton) v).setImageResource(R.drawable.ic_row_pause);
				}
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
				icon = R.drawable.ic_mini_play;
				if (v instanceof ImageButton) {
					((ImageButton) v).setImageResource(R.drawable.ic_row_play);
				}

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
			buildList();
			action = "play/pause";
			break;
		case 1:
			action = "stop";
			icon = R.drawable.ic_mini_stop;
			try {
				if(MAIN_SETTINGS.equals(src)) {
					service.stopTimer(d.getName());
				} else {
					service.stopPluginTimer(d.getName(),src);
				}

			} catch (RemoteException e) {
				e.printStackTrace();
			}
			buildList();
			break;
		case 2:
			action = "mod";
			TimerEditorDialog editor = new TimerEditorDialog(BetterTimerSelectionDialog.this.getContext(),src,d,service,triggerEditorDoneHandler);
			editor.show();
			break;
		}
		Log.e("Trigger","timer item selected for "+action+": "+d.getName());

		// Opening the editor picks no icon; buildList() already refreshed play/pause/stop rows.
		if (icon != 0 && index != 0 && index != 1) {
			this.setItemMiniIcon(row, icon);
		}
	}

	@Override
	public void onButtonStateChanged(ImageButton v, int row, int index, boolean statea) {
	}

	@Override
	public void onItemDeleted(int row) {
		String key = getItemKey(row);
		if (key == null) {
			return;
		}
		TimerData d = dataMap.get(key);
		if (d == null) {
			return;
		}
		String src = getSourcePlugin(key);

		try {
			if(MAIN_SETTINGS.equals(src)) {
				service.deleteTimer(d.getName());
			} else {
				service.deletePluginTimer(src, d.getName());
			}
		} catch (RemoteException e) {

			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("BetterTimerSelectionDialog.delete timer", e);
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
		// Off the UI thread. The timers are already in the service: the editor
		// pushed each one through updateTimer before this list was shown again.
		com.resurrection.blowtorch2.lib.util.SettingsSaver.saveInBackground(service);
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
				resource = R.drawable.ic_mini_play;
				tag = " Running.";
			} else {
				if(data.getRemainingTime() != seconds(data)) {
					resource = R.drawable.ic_mini_pause;
					tag = " Paused, " + TimerDuration.format(data.getRemainingTime()) + " left.";
				} else {
					resource = R.drawable.ic_mini_stop;
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

	/** Stored duration; the field is a boxed Integer and old settings can leave it unset. */
	private static int seconds(TimerData data) {
		Integer s = data.getSeconds();
		return s != null ? s.intValue() : 0;
	}

	private static String groupKey(TimerData data) {
		if (data == null || data.getGroup() == null) {
			return TimerData.DEFAULT_GROUP;
		}
		return data.getGroup();
	}

	private static String formatExtra(TimerData data, String statusTag) {
		String base = "Every " + TimerDuration.format(seconds(data)) + "." + statusTag;
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
			play.setImageResource(R.drawable.ic_row_pause);
		} else {
			play.setImageResource(R.drawable.ic_row_play);
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

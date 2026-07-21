package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.resurrection.blowtorch2.lib.service.IConnectionBinder;

import android.content.Context;
import android.os.RemoteException;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

public class PluginFilterSelectionDialog extends BaseSelectionDialog implements BaseSelectionDialog.OptionItemClickListener {

	protected IConnectionBinder service;
	public final static String MAIN_SETTINGS = "bt_main_settings";
	/** Aggregate view of Main + every plugin that has items. */
	public final static String ALL_SETTINGS = "bt_all_settings";
	protected String currentPlugin = ALL_SETTINGS;

	/** Separator for scoped list keys when {@link #ALL_SETTINGS} is active. */
	protected static final char SCOPE_SEP = '\u0001';

	/** Option list row indices for bulk enable/disable only. */
	protected static final int OPTION_ENABLE_ALL = 0;
	protected static final int OPTION_DISABLE_ALL = 1;
	protected static final int OPTION_FILTER_DIVIDER = 2;
	protected static final int OPTION_MAIN = 3;

	protected String[] pluginList;

	/**
	 * {@code null} = all groups; {@code ""} = default/ungrouped;
	 * otherwise exact group name match.
	 */
	protected String currentGroupFilter = null;
	/** Named (non-default) groups for the current plugin filter, sorted. */
	protected String[] groupNames = new String[0];

	private boolean mGroupFilterEnabled = false;
	private boolean mSuppressFilterToast = true;
	private boolean mFiltersWired = false;

	public PluginFilterSelectionDialog(Context context,IConnectionBinder service) {
		super(context);
		this.service = service;
		setOptionItemClickListener(this);
		try {
			List<String> rawList = this.getPluginList();
			if(rawList == null) {
				pluginList = new String[0];
			} else {
				pluginList = new String[rawList.size()];
				pluginList = rawList.toArray(pluginList);
				java.util.Arrays.sort(pluginList);
			}
		} catch (RemoteException e) {
			pluginList = new String[0];
			e.printStackTrace();
		}

		this.clearOptionItems();
		this.addPluginFilterOptions();
	}

	protected String getEnableAllLabel() {
		return "Enable all (current list)";
	}

	protected String getDisableAllLabel() {
		return "Disable ALL (current list)";
	}

	/** Human-readable name of the active Main/plugin (+ optional group) filter. */
	protected String getCurrentFilterLabel() {
		String base;
		if (ALL_SETTINGS.equals(currentPlugin)) {
			base = "All";
		} else if (MAIN_SETTINGS.equals(currentPlugin)) {
			base = "Main";
		} else {
			base = currentPlugin;
		}
		if (currentGroupFilter == null || !mGroupFilterEnabled) {
			return base;
		}
		String g = currentGroupFilter.length() == 0 ? "(default)" : currentGroupFilter;
		return base + " / " + g;
	}

	/**
	 * "=" menu: Enable/Disable all only. Plugin/group filtering lives on the
	 * visible spinners under search.
	 */
	protected void addPluginFilterOptions() {
		this.addOptionItem(getEnableAllLabel(), true);
		this.addOptionItem(getDisableAllLabel(), true);
	}

	/** Subclasses with group support call this before the dialog is shown. */
	protected void setGroupFilterEnabled(boolean enabled) {
		mGroupFilterEnabled = enabled;
		showFilterBar(true, enabled);
	}

	@Override
	protected void onSelectionDialogReady() {
		showFilterBar(true, mGroupFilterEnabled);
		refreshPluginSpinner();
		if (mGroupFilterEnabled) {
			refreshGroupNamesFromService();
			refreshGroupSpinner();
		}
		wireFilterSpinners();
		mSuppressFilterToast = false;
	}

	private void wireFilterSpinners() {
		if (mFiltersWired) {
			return;
		}
		Spinner pluginSpinner = getPluginFilterSpinner();
		if (pluginSpinner != null) {
			pluginSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					String next = pluginKeyForSpinnerIndex(position);
					if (next.equals(currentPlugin)) {
						return;
					}
					currentPlugin = next;
					currentGroupFilter = null;
					if (mGroupFilterEnabled) {
						refreshGroupNamesFromService();
						refreshGroupSpinner();
					}
					rebuildFilteredList();
					toastCurrentFilter();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});
		}
		Spinner groupSpinner = getGroupFilterSpinner();
		if (groupSpinner != null) {
			groupSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					String next = groupFilterForSpinnerIndex(position);
					boolean same = (next == null && currentGroupFilter == null)
							|| (next != null && next.equals(currentGroupFilter));
					if (same) {
						return;
					}
					currentGroupFilter = next;
					rebuildFilteredList();
					toastCurrentFilter();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});
		}
		mFiltersWired = true;
	}

	private void toastCurrentFilter() {
		if (mSuppressFilterToast) {
			return;
		}
		Toast.makeText(getContext(), "Filter: " + getCurrentFilterLabel(), Toast.LENGTH_SHORT).show();
	}

	protected void refreshPluginSpinner() {
		Spinner spinner = getPluginFilterSpinner();
		if (spinner == null) {
			return;
		}
		ArrayList<String> labels = new ArrayList<String>();
		labels.add("All");
		labels.add("Main");
		if (pluginList != null) {
			for (String p : pluginList) {
				labels.add(p);
			}
		}
		ArrayAdapter<String> adapter = makeFilterSpinnerAdapter(labels);
		setPluginFilterAdapter(adapter);
		int index = 0;
		if (MAIN_SETTINGS.equals(currentPlugin)) {
			index = 1;
		} else if (!ALL_SETTINGS.equals(currentPlugin) && pluginList != null) {
			for (int i = 0; i < pluginList.length; i++) {
				if (pluginList[i].equals(currentPlugin)) {
					index = i + 2;
					break;
				}
			}
		}
		spinner.setSelection(index, false);
	}

	protected void refreshGroupSpinner() {
		Spinner spinner = getGroupFilterSpinner();
		if (spinner == null || !mGroupFilterEnabled) {
			return;
		}
		ArrayList<String> labels = new ArrayList<String>();
		labels.add("All groups");
		labels.add("(default)");
		if (groupNames != null) {
			for (String g : groupNames) {
				labels.add(g);
			}
		}
		ArrayAdapter<String> adapter = makeFilterSpinnerAdapter(labels);
		setGroupFilterAdapter(adapter);
		int index = 0;
		if (currentGroupFilter != null) {
			if (currentGroupFilter.length() == 0) {
				index = 1;
			} else if (groupNames != null) {
				for (int i = 0; i < groupNames.length; i++) {
					if (groupNames[i].equals(currentGroupFilter)) {
						index = i + 2;
						break;
					}
				}
			}
		}
		spinner.setSelection(index, false);
	}

	/** Override to refresh {@link #groupNames} from the service for the current plugin scope. */
	protected void refreshGroupNamesFromService() {
		groupNames = new String[0];
	}

	/** Rebuild the item list after a filter change. */
	protected void rebuildFilteredList() {
	}

	private String pluginKeyForSpinnerIndex(int position) {
		if (position <= 0) {
			return ALL_SETTINGS;
		}
		if (position == 1) {
			return MAIN_SETTINGS;
		}
		int pluginIndex = position - 2;
		if (pluginList != null && pluginIndex >= 0 && pluginIndex < pluginList.length) {
			return pluginList[pluginIndex];
		}
		return ALL_SETTINGS;
	}

	private String groupFilterForSpinnerIndex(int position) {
		if (position <= 0) {
			return null;
		}
		if (position == 1) {
			return "";
		}
		int gi = position - 2;
		if (groupNames != null && gi >= 0 && gi < groupNames.length) {
			return groupNames[gi];
		}
		return null;
	}

	@Override
	public void onOptionItemClicked(int row) {
		switch(row) {
		case OPTION_ENABLE_ALL:
			onEnableAll();
			hideOptionsMenu();
			break;
		case OPTION_DISABLE_ALL:
			onDisableAll();
			hideOptionsMenu();
			break;
		default:
			hideOptionsMenu();
			break;
		}
	}

	public void onHelp() {

	}

	public void onEnableAll() {

	}

	public void onDisableAll() {

	}

	public List<String> getPluginList() throws RemoteException {
		return null;
	}

	/** Plugin that owns a scoped list key (Main when unscoped). */
	protected String getSourcePlugin(String key) {
		if (key != null) {
			int sep = key.indexOf(SCOPE_SEP);
			if (sep >= 0) {
				return key.substring(0, sep);
			}
		}
		if (ALL_SETTINGS.equals(currentPlugin)) {
			return MAIN_SETTINGS;
		}
		return currentPlugin;
	}

	/** Bare item name from a scoped list key. */
	protected String displayNameForKey(String key) {
		if (key == null) {
			return "";
		}
		int sep = key.indexOf(SCOPE_SEP);
		if (sep >= 0) {
			return key.substring(sep + 1);
		}
		return key;
	}

	/** Plugin target for New: Main when viewing All. */
	protected String getEditorPlugin() {
		if (ALL_SETTINGS.equals(currentPlugin)) {
			return MAIN_SETTINGS;
		}
		return currentPlugin;
	}

	protected String scopedKey(String plugin, String name) {
		if (plugin == null || MAIN_SETTINGS.equals(plugin)) {
			return name;
		}
		return plugin + SCOPE_SEP + name;
	}

	/** Loader for Main vs per-plugin maps used by selection dialogs. */
	protected interface ScopedMapLoader<T> {
		Map<String, T> loadMain() throws RemoteException;
		Map<String, T> loadPlugin(String plugin) throws RemoteException;
	}

	/**
	 * Fill {@code out} for the current plugin filter. Under {@link #ALL_SETTINGS},
	 * Main entries keep bare keys; plugin entries use {@link #scopedKey}.
	 */
	protected <T> void loadScopedData(Map<String, T> out, ScopedMapLoader<T> loader)
			throws RemoteException {
		if (out == null || loader == null) {
			return;
		}
		if (ALL_SETTINGS.equals(currentPlugin)) {
			Map<String, T> main = loader.loadMain();
			if (main != null) {
				out.putAll(main);
			}
			if (pluginList != null) {
				for (String p : pluginList) {
					Map<String, T> map = loader.loadPlugin(p);
					if (map == null) {
						continue;
					}
					for (Map.Entry<String, T> e : map.entrySet()) {
						out.put(scopedKey(p, e.getKey()), e.getValue());
					}
				}
			}
		} else if (MAIN_SETTINGS.equals(currentPlugin)) {
			Map<String, T> main = loader.loadMain();
			if (main != null) {
				out.putAll(main);
			}
		} else {
			Map<String, T> map = loader.loadPlugin(currentPlugin);
			if (map != null) {
				out.putAll(map);
			}
		}
	}

}

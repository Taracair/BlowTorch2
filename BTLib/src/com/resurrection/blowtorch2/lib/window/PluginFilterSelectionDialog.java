package com.resurrection.blowtorch2.lib.window;

import java.util.List;

import com.resurrection.blowtorch2.lib.service.IConnectionBinder;

import android.content.Context;
import android.os.RemoteException;

public class PluginFilterSelectionDialog extends BaseSelectionDialog implements BaseSelectionDialog.OptionItemClickListener {

	protected IConnectionBinder service;
	public final static String MAIN_SETTINGS = "bt_main_settings";
	protected String currentPlugin = MAIN_SETTINGS;

	/** Option list row indices for bulk actions + plugin filter. */
	protected static final int OPTION_ENABLE_ALL = 0;
	protected static final int OPTION_DISABLE_ALL = 1;
	protected static final int OPTION_FILTER_DIVIDER = 2;
	protected static final int OPTION_MAIN = 3;

	protected String[] pluginList;

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

	/** Human-readable name of the active Main/plugin filter. */
	protected String getCurrentFilterLabel() {
		if (MAIN_SETTINGS.equals(currentPlugin)) {
			return "Main";
		}
		return currentPlugin;
	}

	protected void addPluginFilterOptions() {
		if (pluginList == null) {
			pluginList = new String[0];
		}
		this.addOptionItem(getEnableAllLabel(), true);
		this.addOptionItem(getDisableAllLabel(), true);
		this.addOptionDivider("Filter by plugin",false);
		this.addOptionItem("Main", false);
		for(int i=0;i<pluginList.length;i++) {
			this.addOptionItem(pluginList[i],false);
		}
	}

	@Override
	public void onOptionItemClicked(int row) {
		switch(row) {
		case OPTION_ENABLE_ALL:
			onEnableAll();
			break;
		case OPTION_DISABLE_ALL:
			onDisableAll();
			break;
		case OPTION_FILTER_DIVIDER:
			break;
		case OPTION_MAIN:
			currentPlugin = MAIN_SETTINGS;
			break;
		default:
			int pluginIndex = row - (OPTION_MAIN + 1);
			if (pluginIndex >= 0 && pluginIndex < pluginList.length) {
				currentPlugin = pluginList[pluginIndex];
			}
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

}

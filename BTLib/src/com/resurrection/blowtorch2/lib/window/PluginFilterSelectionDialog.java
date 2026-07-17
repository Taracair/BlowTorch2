package com.resurrection.blowtorch2.lib.window;

import java.util.List;

import com.resurrection.blowtorch2.lib.service.IConnectionBinder;

import android.content.Context;
import android.os.RemoteException;
import android.widget.LinearLayout;
import android.widget.Toast;

public class PluginFilterSelectionDialog extends BaseSelectionDialog implements BaseSelectionDialog.OptionItemClickListener {

	protected IConnectionBinder service;
	public final static String MAIN_SETTINGS = "bt_main_settings";
	protected String currentPlugin = MAIN_SETTINGS;
	
	String[] pluginList;
	
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

	protected void addPluginFilterOptions() {
		if (pluginList == null) {
			pluginList = new String[0];
		}
		this.addOptionDivider("Filter by plugin",false);
		this.addOptionItem("Main", false);
		for(int i=0;i<pluginList.length;i++) {
			this.addOptionItem(pluginList[i],false);
		}
	}

	@Override
	public void onOptionItemClicked(int row) {
		// TODO Auto-generated method stub
		switch(row) {
		case 0:
			//divier
			break;
		case 1:
			currentPlugin = MAIN_SETTINGS;
			break;
		default:
			currentPlugin = pluginList[row-2];
			break;
		}
	}
	
	public void onHelp() {
		
	}
	
	public void onEnableAll() {
		
	}
	
	
	
	public List<String> getPluginList() throws RemoteException {
		//List<String> foo = (List<String>)service.getPluginsWithTriggers();
		return null;
	}
	
}



package com.resurrection.blowtorch2.lib.alias;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

public class BetterAliasSelectionDialog extends PluginFilterSelectionDialog implements BaseSelectionDialog.UtilityToolbarListener,AliasEditorDialogDoneListener {

	HashMap<String,AliasData> dataMap;
	String[] sortedKeys;

	public BetterAliasSelectionDialog(Context context,
			IConnectionBinder service) {
		super(context, service);
		buildList();
		this.setToolbarListener(this);
		this.setTitle("ALIASES");
	}

	@Override
	protected void rebuildFilteredList() {
		buildList();
	}

	@Override
	public void onButtonPressed(View v, int row, int index) {
		String key = getItemKey(row);
		AliasData d = dataMap.get(key);
		Log.e("Trigger","trigger item selected for modification: "+d.getPre());

		AliasEditorDialog editor = new AliasEditorDialog(BetterAliasSelectionDialog.this.getContext(),BetterAliasSelectionDialog.this,d.getPre(),d.getPost(),row,d,service,computeNames(d.getPre()),getSourcePlugin(key));
		editor.show();
	}

	@Override
	public void onButtonStateChanged(ImageButton v, int row, int index, boolean statea) {
		String key = getItemKey(row);
		AliasData d = dataMap.get(key);
		boolean state = !d.isEnabled();
		d.setEnabled(state);
		String src = getSourcePlugin(key);
		try {
			if(MAIN_SETTINGS.equals(src)) {
				service.setAliasEnabled(state, d.getPre());
			} else {
				service.setPluginAliasEnabled(src, state, d.getPre());
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
		Log.e("Alias","alias item selected for enable/disable: "+d.getPre());
	}

	@Override
	public void onItemDeleted(int row) {
		String key = getItemKey(row);
		AliasData d = dataMap.get(key);
		String src = getSourcePlugin(key);

		try {
			if(MAIN_SETTINGS.equals(src)) {
				service.deleteAlias(d.getPre());
			} else {
				service.deletePluginAlias(src, d.getPre());
			}
		} catch (RemoteException e) {

		}
		Log.e("Trigger","alias item selected for delete: "+d.getPre());
	}

	@Override
	public void onNewPressed(View v) {
		AliasEditorDialog editor = new AliasEditorDialog(BetterAliasSelectionDialog.this.getContext(),BetterAliasSelectionDialog.this,service,computeNames(""),getEditorPlugin());
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
	protected String getEnableAllLabel() {
		return "Enable all aliases (current list)";
	}

	@Override
	protected String getDisableAllLabel() {
		return "Disable ALL aliases (current list)";
	}

	@Override
	public void onEnableAll() {
		setAllAliasesEnabled(true);
	}

	@Override
	public void onDisableAll() {
		final String filter = getCurrentFilterLabel();
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle("Disable ALL aliases?");
		builder.setMessage("This disables ALL aliases in the current filter ("
				+ filter + "). Continue?");
		builder.setPositiveButton("Disable all", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				setAllAliasesEnabled(false);
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

	private void setAllAliasesEnabled(boolean enabled) {
		if (sortedKeys == null || sortedKeys.length == 0) {
			Toast.makeText(getContext(), "No aliases in current list", Toast.LENGTH_SHORT).show();
			return;
		}
		int count = 0;
		try {
			for (String key : sortedKeys) {
				AliasData d = dataMap.get(key);
				if (d == null) {
					continue;
				}
				d.setEnabled(enabled);
				String src = getSourcePlugin(key);
				if (MAIN_SETTINGS.equals(src)) {
					service.setAliasEnabled(enabled, d.getPre());
				} else {
					service.setPluginAliasEnabled(src, enabled, d.getPre());
				}
				count++;
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		buildList();
		String verb = enabled ? "Enabled" : "Disabled";
		Toast.makeText(getContext(),
				verb + " " + count + " alias" + (count == 1 ? "" : "es")
						+ " (" + getCurrentFilterLabel() + ")",
				Toast.LENGTH_SHORT).show();
	}

	@SuppressWarnings("unchecked")
	private void buildList() {
		try {
			dataMap = new HashMap<String, AliasData>();
			loadScopedData(dataMap, new ScopedMapLoader<AliasData>() {
				@Override
				public Map<String, AliasData> loadMain() throws RemoteException {
					return (Map<String, AliasData>) service.getAliases();
				}

				@Override
				public Map<String, AliasData> loadPlugin(String plugin) throws RemoteException {
					return (Map<String, AliasData>) service.getPluginAliases(plugin);
				}
			});
		} catch (RemoteException e) {
			if (dataMap == null) {
				dataMap = new HashMap<String, AliasData>();
			}
		}

		sortedKeys = new String[dataMap.size()];
		sortedKeys = dataMap.keySet().toArray(sortedKeys);
		Arrays.sort(sortedKeys, new java.util.Comparator<String>() {
			@Override
			public int compare(String a, String b) {
				return displayNameForKey(a).compareToIgnoreCase(displayNameForKey(b));
			}
		});
		clearListItems();
		for(int i=0;i<sortedKeys.length;i++) {
			AliasData data = dataMap.get(sortedKeys[i]);
			int resource = 0;
			if(data.isEnabled()) {
				resource = R.drawable.toolbar_mini_enabled;
			} else {
				resource = R.drawable.toolbar_mini_disabled;
			}
			String title = displayNameForKey(sortedKeys[i]);
			if (ALL_SETTINGS.equals(currentPlugin)) {
				String src = getSourcePlugin(sortedKeys[i]);
				if (!MAIN_SETTINGS.equals(src)) {
					title = src + ": " + title;
				}
			}
			this.addListItem(sortedKeys[i], title, data.getPost(), resource, data.isEnabled());
		}

		invalidateList();

	}

	@Override
	public List<String> getPluginList() throws RemoteException {
		List<String> foo = (List<String>)service.getPluginsWithAliases();
		return foo;
	}

	@Override
	public void willShowToolbar(LinearLayout toolbar, int row) {
		AliasData data = dataMap.get(getItemKey(row));
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

	private final Handler aliasEditorDoneHandler = new Handler() {

		public void handleMessage(Message msg) {
			switch(msg.what) {
			case 100:
				AliasData d = (AliasData)msg.obj;
				BetterAliasSelectionDialog.this.buildList();
				BetterAliasSelectionDialog.this.scrollToSelection(d.getPre());
				break;
			}

		}
	};
	private ArrayList<String> names = new ArrayList<String>();

	private List<String> computeNames(String name) {

		names.clear();

		if(name.startsWith("^")) name = name.substring(1,name.length());
		if(name.endsWith("$")) name = name.substring(0,name.length()-1);

		if(dataMap != null) {
			for(String key : dataMap.keySet()) {
				String display = displayNameForKey(key);
				if(!display.equals(name)) {
					names.add(display);
				}
			}
		}

		return names;
	}

	public void newAliasDialogDone(String pre, String post,boolean enabled) {
		try {
			AliasData newAlias = new AliasData();
			newAlias.setPost(post);
			newAlias.setPre(pre);
			newAlias.setEnabled(enabled);
			String newKey = newAlias.getPre();
			if(newKey.startsWith("^")) newKey = newKey.substring(1,newKey.length());
			if(newKey.endsWith("$")) newKey = newKey.substring(0,newKey.length()-1);

			String target = getEditorPlugin();
			HashMap<String, AliasData> map;
			if (MAIN_SETTINGS.equals(target)) {
				map = (HashMap<String, AliasData>) service.getAliases();
			} else {
				map = (HashMap<String, AliasData>) service.getPluginAliases(target);
			}
			if (map == null) {
				map = new HashMap<String, AliasData>();
			}
			map.put(newKey, newAlias);
			if(MAIN_SETTINGS.equals(target)) {
				service.setAliases(map);
			} else {
				service.setPluginAliases(target, map);
			}

			aliasEditorDoneHandler.sendMessageDelayed(aliasEditorDoneHandler.obtainMessage(100,newAlias),10);

		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public void editAliasDialogDone(String pre,String post,boolean enabled,int pos,AliasData orig) {
		try {
			String target = getSourcePlugin(getItemKey(pos));
			HashMap<String, AliasData> map;
			if (MAIN_SETTINGS.equals(target)) {
				map = (HashMap<String, AliasData>) service.getAliases();
			} else {
				map = (HashMap<String, AliasData>) service.getPluginAliases(target);
			}
			if (map == null) {
				map = new HashMap<String, AliasData>();
			}

			String oldKey = orig.getPre();
			if(oldKey.startsWith("^")) oldKey = oldKey.substring(1,oldKey.length());
			if(oldKey.endsWith("$")) oldKey = oldKey.substring(0,oldKey.length()-1);
			map.remove(oldKey);

			String newKey = pre;
			if(newKey.startsWith("^")) newKey = newKey.substring(1,newKey.length());
			if(newKey.endsWith("$")) newKey = newKey.substring(0,newKey.length()-1);
			AliasData newAlias = new AliasData();
			newAlias.setPre(pre);
			newAlias.setPost(post);
			newAlias.setEnabled(enabled);
			map.put(newKey, newAlias);
			if(MAIN_SETTINGS.equals(target)) {
				service.setAliases(map);
			} else {
				service.setPluginAliases(target, map);
			}

			aliasEditorDoneHandler.sendMessageDelayed(aliasEditorDoneHandler.obtainMessage(100,newAlias),10);

		} catch (RemoteException e) {
			e.printStackTrace();
		}


	}

	@Override
	public void willHideToolbar(LinearLayout v, int row) {
	}

}

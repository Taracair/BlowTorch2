package com.resurrection.blowtorch2.lib.button;

import java.util.ArrayList;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.window.MainWindow;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ViewFlipper;

public class ButtonSetSelectorDialog extends Dialog {

	ArrayList<ButtonEntry> entries = new ArrayList<ButtonEntry>();
	Handler dispater = null;
	String selected_set;
	HashMap<String,Integer> data;
	ConnectionAdapter adapter;
	IConnectionBinder service;
	ListView list = null;
	public ButtonSetSelectorDialog(Context context,Handler reportto,HashMap<String,Integer> datai,String selectedset,IConnectionBinder the_service) {
		super(context);
		dispater = reportto;
		selected_set = selectedset;
		data = datai;
		service = the_service;
	}
	
	private boolean noSets = false;
	
	@SuppressWarnings("unchecked")
	public void buildList() {
		entries.clear();
		ListView lv = (ListView) findViewById(R.id.buttonset_list);
		
		//try {
			//data = (HashMap<String, Integer>) service.getButtonSetListInfo();
			//selected_set = service.getLastSelectedSet();
		//} catch (RemoteException e) {
			// TODO Auto-generated catch block
		//	e.printStackTrace();
		//	return;
		//}
		
		for(String key : data.keySet()) {
			ButtonEntry tmp = new ButtonEntry(key,data.get(key));
			try {
				tmp.locked = service.isButtonSetLocked(key);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			entries.add(tmp);
		}
		
		if(data.size() == 0) {
			noSets = true;
		}
		
		adapter = new ConnectionAdapter(this.getContext(),R.layout.buttonset_selection_list_row,entries);
		adapter.sort(new EntryCompare());
		
		lv.setAdapter(adapter);
		lv.setTextFilterEnabled(true);
		adapter.notifyDataSetInvalidated();
		//Button
		//lv.setSelection(entries.indexOf(new ButtonEntry(selected_set,data.get(selected_set))));
		
		//lv.b
	}
	
	int selectedIndex = 0;
	
	/*public void onStart() {
		lv.setSelection(selectedIndex);
	}*/
	
	public void onCreate(Bundle b) {
		super.onCreate(b);
		this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		
		setContentView(R.layout.buttonset_selection_dialog);
		
		
		
		ListView lv = (ListView) findViewById(R.id.buttonset_list);

		lv.setScrollbarFadingEnabled(false);
		
		buildList();
		//build list.
		/*for(String key : data.keySet()) {
			entries.add(new ButtonEntry(key,data.get(key)));
		}
		
		if(data.size() == 0) {
			noSets = true;
		}
		adapter = new ConnectionAdapter(lv.getContext(),R.layout.buttonset_selection_list_row,entries);
		adapter.sort(new EntryCompare());*/
		//lv.setAdapter(adapter);
		//lv.setTextFilterEnabled(true);
		
		//lv.setSelection(entries.indexOf(new ButtonEntry(selected_set,data.get(selected_set))));
		
		Button newbutton = (Button)findViewById(R.id.new_buttonset_button);
		Button cancel = (Button)findViewById(R.id.cancel_buttonset_button);
		
		newbutton.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				NewButtonSetEntryDialog diag = new NewButtonSetEntryDialog(ButtonSetSelectorDialog.this.getContext(),dispater,service);
				diag.setTitle("New Button Set:");
				diag.show();
				ButtonSetSelectorDialog.this.dismiss();
			}
		});
		
		cancel.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				if(setSettingsHaveChanged) {
					//ListView lv = (ListView)ButtonSetSelectorDialog.this.findViewById(R.id.buttonset_list);
					//ButtonEntry item = adapter.getItem(lv.getSelectedItemPosition());
					Message reloadbuttonset = null;
					//try {
						//reloadbuttonset = dispater.obtainMessage(MainWindow.MESSAGE_CHANGEBUTTONSET,service.getLastSelectedSet());
					//} catch (RemoteException e) {
					//	throw new RuntimeException(e);
					//}
					dispater.sendMessage(reloadbuttonset);
				}
				ButtonSetSelectorDialog.this.dismiss();
			}
		});
		
		/*lv.setOnItemClickListener(new OnItemClickListener() {

			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {

				
				ButtonEntry item = entries.get(arg2);
				Message changebuttonset = dispater.obtainMessage(MainWindow.MESSAGE_CHANGEBUTTONSET,item.name);
				dispater.sendMessage(changebuttonset);
				ButtonSetSelectorDialog.this.dismiss();
				
			}
			
		});*/
		
		//lv.setOnItemLongClickListener(new ButtonSetEditorOpener());
		// The row controls are ordinary focusable buttons now, so there is nothing
		// left to hand focus to by hand. This used to chase a hidden tab view --
		// the handle that slid the controls into view -- around the visible rows.
		lv.setOnFocusChangeListener(null);
		list = lv;
		
	}
	
	public void onStart() {
		super.onStart();
		if(noSets) {
			Toast t = Toast.makeText(ButtonSetSelectorDialog.this.getContext(), "No button sets loaded. Click below to create new Button Sets.", Toast.LENGTH_LONG);
			t.show();
		}
		adapter.notifyDataSetInvalidated();
		//selectedIndex = entries.indexOf(new ButtonEntry(selected_set,data.get(selected_set)));
		for(int i=0;i<adapter.getCount();i++) {
			if(adapter.getItem(i).name.equals(selected_set)) {
				selectedIndex = i;
			}
		}
		
		Log.e("VIEW","Attempting to set selection to:" + selectedIndex);
		((ListView)findViewById(R.id.buttonset_list)).setSelection(selectedIndex);

	}
	
	public void onBackPressed() {
		if(setSettingsHaveChanged) {
			//ListView lv = (ListView)ButtonSetSelectorDialog.this.findViewById(R.id.buttonset_list);
			//ButtonEntry item = adapter.getItem(lv.getSelectedItemPosition());
			Message reloadbuttonset = null;
			//try {
			//	reloadbuttonset = dispater.obtainMessage(MainWindow.MESSAGE_CHANGEBUTTONSET,service.getLastSelectedSet());
			//} catch (RemoteException e) {
			//	throw new RuntimeException(e);
			//}
			dispater.sendMessage(reloadbuttonset);
		}
		this.dismiss();
	}
	
	private class ModifySetDefaultsListener implements DialogInterface.OnClickListener {

		Integer picked = null;
		public ModifySetDefaultsListener(int input) {
			picked = input;
		}
		
		public void onClick(DialogInterface dialog, int which) {
			ButtonEntry entry = adapter.getItem(picked);
			ButtonSetEditor editor = new ButtonSetEditor(ButtonSetSelectorDialog.this.getContext(),service,entry.name,editordonelistenr);
			editor.show();
		}
		
	}
	
	private class DeleteSetListener implements DialogInterface.OnClickListener {

		Integer picked = null;
		public DeleteSetListener(int input) {
			picked = input;
		}
		
		public void onClick(DialogInterface dialog, int which) {
			AlertDialog.Builder confirm = new AlertDialog.Builder(ButtonSetSelectorDialog.this.getContext());
			
			//default button set can not be deleted.
			if(entries.get(picked).name.equals("default")) {
				confirm.setTitle("Cannot Delete Default Set");			
				confirm.setMessage("This set can not be removed. It can be cleared.");
				//confirm.setPositiveButton("Yes, Delete.",new ReallyDeleteSetListener(picked));
				confirm.setNeutralButton("Clear Buttons", new ClearSetListener(picked));
				confirm.setNegativeButton("Cancel.", new DialogInterface.OnClickListener() {
					
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});
				
				AlertDialog dlg = confirm.create();
				dlg.show();
				dialog.dismiss();
				ButtonSetSelectorDialog.this.dismiss();				
			} else {
			
				confirm.setTitle("Really Delete Button Set?");			
				confirm.setMessage("The set can be cleared of buttons if desired.");
				confirm.setPositiveButton("Delete",new ReallyDeleteSetListener(picked));
				confirm.setNeutralButton("Clear", new ClearSetListener(picked));
				confirm.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});
				
				AlertDialog dlg = confirm.create();
				dlg.show();
				dialog.dismiss();
				ButtonSetSelectorDialog.this.dismiss();
			}
		}
		
	}
	
	private class ReallyDeleteSetListener implements DialogInterface.OnClickListener {

		Integer picked = null;
		public ReallyDeleteSetListener(int input) {
			picked = input;
		}
		
		public void onClick(DialogInterface dialog, int which) {
			Message delset = dispater.obtainMessage(MainWindow.MESSAGE_DELETEBUTTONSET);
			delset.obj = (entries.get(picked)).name;
			dispater.sendMessage(delset);
		}
		
	}
	
	private class ClearSetListener implements DialogInterface.OnClickListener {

		Integer picked = null;
		public ClearSetListener(int input) {
			picked = input;
		}
		
		public void onClick(DialogInterface dialog, int which) {
			Message delset = dispater.obtainMessage(MainWindow.MESSAGE_CLEARBUTTONSET);
			delset.obj = (entries.get(picked)).name;
			dispater.sendMessage(delset);
		}
		
	}
	
	private class ConnectionAdapter extends ArrayAdapter<ButtonEntry> {

		private List<ButtonEntry> items;
		
		public ConnectionAdapter(Context context, int textViewResourceId,
				List<ButtonEntry> objects) {
			super(context, textViewResourceId, objects);
			this.items = objects;
		}
		
		public View getView(int pos, View convertView,ViewGroup parent) {
			View v = convertView;
			if(v == null) {
				LayoutInflater li = (LayoutInflater)this.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = li.inflate(R.layout.better_list_row,null);
			}
			
			ButtonEntry e = items.get(pos);
			
			if(e != null) {
				
				//set up the view
				RelativeLayout root = (RelativeLayout)v.findViewById(R.id.root);
				root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
				
				ImageView icon = (ImageView) v.findViewById(R.id.icon);
				if(e.locked) {
					icon.setImageResource(R.drawable.ic_mini_locked);
					icon.setVisibility(View.VISIBLE);
				} else {
					icon.setVisibility(View.GONE);
				}
				
				ImageButton load = new ImageButton(ButtonSetSelectorDialog.this.getContext());
				ImageButton lock = new ImageButton(ButtonSetSelectorDialog.this.getContext());
				ImageButton modify = new ImageButton(ButtonSetSelectorDialog.this.getContext());
				ImageButton delete = new ImageButton(ButtonSetSelectorDialog.this.getContext());
				
				LinearLayout.LayoutParams params = (new LinearLayout.LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
				params.setMargins(0, 0, 0, 0);
				
				load.setLayoutParams(params);
				lock.setLayoutParams(params);
				modify.setLayoutParams(params);
				delete.setLayoutParams(params);
				
				final int pad = Math.round(6f * getContext().getResources().getDisplayMetrics().density);
				load.setPadding(pad,pad,pad,pad);
				lock.setPadding(pad,pad,pad,pad);
				modify.setPadding(pad,pad,pad,pad);
				delete.setPadding(pad,pad,pad,pad);
				
				load.setImageResource(R.drawable.ic_row_load);
				if(e.locked) {
					lock.setImageResource(R.drawable.ic_row_lock);
				} else {
					lock.setImageResource(R.drawable.ic_row_unlock);
				}
				modify.setImageResource(R.drawable.ic_row_edit);
				delete.setImageResource(R.drawable.ic_row_delete);
				
				load.setBackgroundColor(0);
				lock.setBackgroundColor(0);
				modify.setBackgroundColor(0);
				delete.setBackgroundColor(0);
				
				load.setOnKeyListener(theButtonKeyListener);
				lock.setOnKeyListener(theButtonKeyListener);
				modify.setOnKeyListener(theButtonKeyListener);
				delete.setOnKeyListener(theButtonKeyListener);
				
				LinearLayout holder = (LinearLayout)v.findViewById(R.id.button_holder);
				holder.removeAllViews();
				holder.addView(load);
				holder.addView(lock);
				holder.addView(modify);
				holder.addView(delete);
				
				load.setOnClickListener(new LoadButtonListener(pos));
				lock.setOnClickListener(new LockButtonListener(pos,icon));
				modify.setOnClickListener(new ModifyButtonListener(pos));
				delete.setOnClickListener(new DeleteButtonListener(pos, v));
				
				TextView label = (TextView)v.findViewById(R.id.infoTitle);
				TextView extra = (TextView)v.findViewById(R.id.infoExtended);
				
				label.setText(e.name);
				extra.setText(e.entries + (e.entries == 1 ? " button" : " buttons"));
				RelativeLayout r = (RelativeLayout)v.findViewById(R.id.root);
				r.setBackgroundColor(e.name.equals(selected_set) ? 0xFF262C34 : 0xFF16181C);
			}
			
			return v;
			
		}
		
	}
	
	public class LoadButtonListener implements View.OnClickListener {

		private int index = -1;
		public LoadButtonListener(int index) {
			this.index = index;
		}
		
		public void onClick(View v) {
			ButtonEntry item = entries.get(index);
			Message changebuttonset = dispater.obtainMessage(MainWindow.MESSAGE_CHANGEBUTTONSET,item.name);
			dispater.sendMessage(changebuttonset);
			ButtonSetSelectorDialog.this.dismiss();
		}
		
	}
	
	public class LockButtonListener implements View.OnClickListener {
		private int index = -1;
		private ImageView icon = null;
		public LockButtonListener(int index,ImageView icon) {
			this.index = index;
			this.icon = icon;
		}
		public void onClick(View v) {
			ButtonEntry item = entries.get(index);
			//TODO: actually lock the set.
			
			if(item.locked) {
				//unlock
				try {
					service.setButtonSetLocked(false, item.name);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				ImageView iv = (ImageView)v;
				iv.setImageResource(R.drawable.toolbar_unlocked_button);
				icon.setVisibility(View.INVISIBLE);
				item.locked = false;
			} else {
				//lock
				try {
					service.setButtonSetLocked(true, item.name);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				ImageView iv = (ImageView)v;
				iv.setImageResource(R.drawable.toolbar_locked_button);
				icon.setVisibility(View.VISIBLE);
				icon.setImageResource(R.drawable.toolbar_mini_locked);
				item.locked = true;
			}
		}
		
	}
	
	public class ModifyButtonListener implements View.OnClickListener {
		private int index = -1;
		public ModifyButtonListener(int index) {
			this.index = index;
		}
		
		public void onClick(View v) {
			ButtonEntry entry = adapter.getItem(index);
			ButtonSetEditor editor = new ButtonSetEditor(ButtonSetSelectorDialog.this.getContext(),service,entry.name,editordonelistenr);
			editor.show();
		}
		
	}
	
	public class DeleteButtonListener implements View.OnClickListener {

		private int entry = -1;
		View row = null;
		public DeleteButtonListener(int element, View row) {
			this.entry = element;
			this.row = row;
		}
		
		public void onClick(View v) {
			AlertDialog.Builder builder = new AlertDialog.Builder(ButtonSetSelectorDialog.this.getContext());
			builder.setTitle("Delete Button Set");
			builder.setMessage("Confirm Delete?");
			builder.setPositiveButton("Delete", new ReallyDeleteTriggerListener(row,entry));
			builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			builder.setIcon(android.R.drawable.ic_dialog_alert);
			AlertDialog d = builder.create();
			d.show();
		}
		
	}
	
	public class ReallyDeleteTriggerListener implements DialogInterface.OnClickListener {
		View row = null;
		int animateDistance = 0;
		int entry = -1;
		public ReallyDeleteTriggerListener(View row,int entry) {
			this.row = row;
			this.animateDistance = animateDistance;
			this.entry = entry;
		}
		public void onClick(DialogInterface dialog, int which) {
			// TODO Auto-generated method stub
			dialog.dismiss();
			// Slide the row itself out. This used to flip the row's ViewFlipper and
			// hang the delete off that animation; the flipper existed only to hide
			// the controls, so the row carries the animation now.
			Animation a = new TranslateAnimation(0, row != null ? row.getWidth() : 0, 0, 0);
			a.setDuration(300);
			a.setAnimationListener(new DeleteAnimationListener(entry));
			if (row != null) {
				row.startAnimation(a);
			} else {
				new DeleteAnimationListener(entry).onAnimationEnd(null);
			}
		}
		
	}
	
	public class DeleteAnimationListener implements Animation.AnimationListener {

		int entry = -1;
		public DeleteAnimationListener(int entry) {
			this.entry = entry;
		}
		
		public void onAnimationEnd(Animation animation) {
			list.setOnFocusChangeListener(null);
			list.setFocusable(false);
			//try {
			//	service.deleteButtonSet(entries.get(entry).name);
			//} catch (RemoteException e) {
			//	throw new RuntimeException(e);
			//}
			/*Message delset = dispater.obtainMessage(MainWindow.MESSAGE_DELETEBUTTONSET);
			delset.obj = (entries.get(picked)).name;
			dispater.sendMessage(delset);*/
			editordonelistenr.sendMessageDelayed(editordonelistenr.obtainMessage(104), 10);
		}

		public void onAnimationRepeat(Animation animation) {
			// TODO Auto-generated method stub
			
		}

		public void onAnimationStart(Animation animation) {
			// TODO Auto-generated method stub
			
		}
		
	}
	
	public ToolBarButtonKeyListener theButtonKeyListener = new ToolBarButtonKeyListener();
	
	public class ToolBarButtonKeyListener implements View.OnKeyListener {

		public boolean onKey(View v, int keyCode, KeyEvent event) {
			if(keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
				return true;
			}
			return false;
		}
		
	}
	
	private int lastSelectedIndex = -1;
	
	boolean setSettingsHaveChanged = false;
	private Handler editordonelistenr = new Handler() {
		public void handleMessage(Message msg) {
			switch(msg.what) {
			case 104:
				finishDelete();
				break;
			case 100:
				//entry no name change;
				//int index = lastSelectedIndex;
				setSettingsHaveChanged = true;
				ButtonSetSelectorDialog.this.buildList();
				break;
			case 101:
				//edited entry;
				
				setSettingsHaveChanged = true;
				ButtonSetSelectorDialog.this.buildList();
				
				break;
			}
			//handle the thing comin back;
			//if we got this, it means some settings have changed, and we should reload the button set when we are done regardless if it is the one already selected, or cancelled.

			//Log.e("EDITOR","REBUILDING LIST");
		}
	};
	
	protected void finishDelete() {
		buildList();
		list.setFocusable(true);
	}
	
	private class EntryCompare implements Comparator<ButtonEntry> {

		public int compare(ButtonEntry a, ButtonEntry b) {
			return a.name.compareToIgnoreCase(b.name);
		}


		
	}
	
	private class ButtonEntry {
		public String name;
		public Integer entries;
		boolean locked = false;
		
		//public ButtonEntry() {
		//	name = "";
		//	entries = 0;
		//}
		
		public ButtonEntry(String n,Integer e) {
			name = n;
			entries = e;
			locked = false;
		}
		
		public boolean equals(Object test) {
			if(this == test) {
				return true;
			}
			
			if(!(test instanceof ButtonEntry)) {
				return false;
			}
			ButtonEntry bt = (ButtonEntry)test;
			
			boolean retval = true;
			if(!(this.name.equals(bt.name))) retval = false;
			if(this.entries.intValue() != bt.entries.intValue()) retval = false;
			if(this.locked != bt.locked) retval = false;
			return retval;
		}
	}
	

}

package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.LinearLayout.LayoutParams;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.ViewParent;
import android.view.Window;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.TranslateAnimation;

import com.resurrection.blowtorch2.lib.R;

public class BaseSelectionDialog extends Dialog {

	private String mTitle;
	private ListView mList;
	private ListView mOptionsList;
	private boolean mOptionsListToggle;
	private ArrayAdapter<ItemEntry> mAdapter;
	protected Button mOptionsButton;
	
	private int mLastSelectedIndex = -1;
	/** Key of the row most recently confirmed for deletion (set before adapter remove). */
	protected String mLastDeletedKey = null;
	private Context mContext;

	private TextView mTitlebar;
	private CharSequence mNewTitle = "New";
	
	public BaseSelectionDialog(Context context) {
		super(context, R.style.BlowTorch_Dialog_FullScreen);
		// TODO Auto-generated constructor stub
		mContext = context;
		mToolbarButtons = new ArrayList<UtilityButton>();
		
		UtilityButton delete = new UtilityButton();
		delete.action = UTILITY_BUTTON_ACTION.DELETE;
		delete.imageResource = R.drawable.toolbar_delete_button;
		
		UtilityButton toggle = new UtilityButton();
		toggle.action = UTILITY_BUTTON_ACTION.TOGGLE;
		toggle.imageResource = R.drawable.toolbar_toggleon_button;
		
		UtilityButton edit = new UtilityButton();
		edit.action = UTILITY_BUTTON_ACTION.NORMAL;
		edit.imageResource = R.drawable.toolbar_modify_button;
		
		mToolbarButtons.add(toggle);
		mToolbarButtons.add(edit);
		mToolbarButtons.add(delete);
		
		
		
		//mToolbarButtons.
		
		//miniIcons = new ArrayList<Drawable>();
		
		//miniIcons.add(context.getResources().getDrawable(R.drawable.toolbar_mini_disabled));
		//miniIcons.add(context.getResources().getDrawable(R.drawable.toolbar_mini_enabled));
		
		//mListItems = new ArrayList<ItemEntry>();
		/*ItemEntry first = new ItemEntry();
		ItemEntry second = new ItemEntry();
		ItemEntry third = new ItemEntry();
		
		first.title = "first item";
		first.extra = "sskdfjasdffsdf";
		
		second.title = "second item";
		second.extra = "sfsafdsafd";
		
		third.title = "third item";
		third.extra = "sdfasdf";
		
		mListItems.add(first);
		mListItems.add(second);
		mListItems.add(third);
		
		OptionItem help = new OptionItem();
		help.title = "Help";
		help.centered = true;
		
		OptionItem enableAll = new OptionItem();
		enableAll.title = "Enable All";
		enableAll.centered = true;
		
		DividerItem divider = new DividerItem();
		divider.title = "Filter By Plugin";
		
		OptionItem foo = new OptionItem();
		foo.title = "foo";
		OptionItem bar = new OptionItem();
		bar.title = "bar";
		OptionItem baz = new OptionItem();
		baz.title = "baz";
		
		optionItems.add(help);
		optionItems.add(enableAll);
		optionItems.add(divider);
		optionItems.add(foo);
		optionItems.add(bar);
		optionItems.add(baz);*/
	}
	
	@Override
	public void onCreate(Bundle b) {
		
		this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		MainWindow w = (MainWindow)mContext;
		if(w.isStatusBarHidden()) {
			this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		this.setCanceledOnTouchOutside(false);
		setContentView(R.layout.editor_selection_dialog);
		Window window = this.getWindow();
		if (window != null) {
			window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
			WindowManager.LayoutParams attrs = window.getAttributes();
			attrs.width = WindowManager.LayoutParams.MATCH_PARENT;
			attrs.height = WindowManager.LayoutParams.MATCH_PARENT;
			attrs.gravity = Gravity.FILL;
			window.setAttributes(attrs);
		}

		final View root = findViewById(R.id.root);
		if (root != null) {
			androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
				androidx.core.graphics.Insets sys =
						insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
				view.setPadding(view.getPaddingLeft(), sys.top,
						view.getPaddingRight(), sys.bottom);
				return insets;
			});
			androidx.core.view.ViewCompat.requestApplyInsets(root);
		}
		
		TextView tst = (TextView) this.findViewById(R.id.titlebar);
		tst.setText(mTitle);
		
		//initialize the list view
		mList = (ListView)findViewById(R.id.list);
		
		mList.setScrollbarFadingEnabled(false);
		
		//mList.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
		mList.setOnItemSelectedListener(new DpadSelectionListener());
		//mList.setOnFocusChangeListener(new ListFocusFixerListener());
		//mList.setSelector(R.drawable.filter_selection_selector);
		mList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				openItemEditor(position);
			}
		});
	
		//list.setOnFocusChangeListener(new ListFocusFixerListener());
		
		mList.setSelector(R.drawable.blue_frame_nomargin_nobackground);
		
		
		
		mList.setEmptyView(findViewById(R.id.empty));
		
		EditText searchField = (EditText) findViewById(R.id.search_field);
		if (searchField != null) {
			if (!mSearchVisible) {
				searchField.setVisibility(View.GONE);
			} else {
				searchField.addTextChangedListener(new TextWatcher() {
					@Override
					public void beforeTextChanged(CharSequence s, int start, int count, int after) {
					}

					@Override
					public void onTextChanged(CharSequence s, int start, int before, int count) {
					}

					@Override
					public void afterTextChanged(Editable s) {
						mSearchQuery = s.toString();
						applySearchFilter();
						if (mAdapter != null) {
							mAdapter.notifyDataSetChanged();
						}
					}
				});
			}
		}
		
		buildRawList();
		
		Button newbutton = (Button)findViewById(R.id.add);
		newbutton.setText(mNewTitle );
		
		newbutton.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View arg0) {
				if(mToolbarListener != null) {
					mToolbarListener.onNewPressed(arg0);
				}
				//TriggerEditorDialog editor = new TriggerEditorDialog(TriggerSelectionDialog.this.getContext(),null,service,triggerEditorDoneHandler,currentPlugin);
				//editor.show();
			}
		});
		
		Button cancelbutton = (Button)findViewById(R.id.done);
		
		cancelbutton.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View arg0) {
				if(mToolbarListener != null) {
					mToolbarListener.onDonePressed(arg0);
				}
				
				BaseSelectionDialog.this.dismiss();
			}
			
		});
	
		//gett he plugin list.
		//try {
			//List<String> pluginList = (List<String>)service.getPluginsWithTriggers();
			String[] pluginList = new String[] { "foo","bar","baz" };
			
			
			plugins = new String[pluginList.length+3];
			plugins[0] = "Help";
			//plugins[1] = "Disable All";
			plugins[1] = "divider";
			plugins[2] = "Main";
			
			//String[] tmp = new String[pluginList.size()];
			//tmp = pluginList.toArray(tmp);
			//String 
			java.util.Arrays.sort(pluginList);
			for(int i=0;i<pluginList.length;i++) {
				plugins[i+3] = pluginList[i];
				
			}
			//java.util.Arrays.sort(plugins);
			
		//} catch (RemoteException e) {
			// TODO Auto-generated catch block
		//	e.printStackTrace();
		//}
		
		mOptionAdapter = new OptionListAdapter(this.getContext(),0,optionItems);

		mOptionsList =(ListView) this.findViewById(R.id.optionslist);
		mOptionsButton = (Button)this.findViewById(R.id.optionsbutton);
		wireOptionsMenu();

		mTitlebar = (TextView) this.findViewById(R.id.titlebar);

		if (mTitlebar != null) {
			ViewParent parent = mTitlebar.getParent();
			if (parent != null) {
				parent.bringChildToFront(mTitlebar);
				if (mOptionsButton != null && mOptionsButton.getVisibility() == View.VISIBLE) {
					parent.bringChildToFront(mOptionsButton);
				}
			}
		}

	}

	/** Show "=" options when option items exist; otherwise keep controls hidden. */
	private void wireOptionsMenu() {
		if (mOptionsList == null || mOptionsButton == null) {
			return;
		}
		mOptionsList.setAdapter(mOptionAdapter);
		mOptionsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
				if (mOptionItemClickListener != null) {
					mOptionItemClickListener.onOptionItemClicked(pos);
				}
			}
		});
		mOptionsList.setVerticalFadingEdgeEnabled(false);
		mOptionsList.setVisibility(View.INVISIBLE);
		mOptionsListToggle = false;

		if (optionItems.isEmpty()) {
			mOptionsButton.setVisibility(View.GONE);
			return;
		}

		mOptionsButton.setVisibility(View.VISIBLE);
		mOptionsButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mPromoteHelp) {
					if (mOptionItemClickListener != null) {
						mOptionItemClickListener.onOptionItemClicked(0);
					}
					return;
				}
				if (mOptionsListToggle) {
					mOptionsListToggle = false;
					Animation outAnimation = new TranslateAnimation(0, 0, 0, -mOptionsList.getHeight());
					outAnimation.setDuration(300);
					outAnimation.setAnimationListener(new AnimationListener() {
						@Override
						public void onAnimationEnd(Animation animation) {
							mOptionsList.setVisibility(View.INVISIBLE);
						}
						@Override
						public void onAnimationRepeat(Animation animation) {
						}
						@Override
						public void onAnimationStart(Animation animation) {
						}
					});
					mOptionsList.startAnimation(outAnimation);
				} else {
					mOptionsListToggle = true;
					mOptionsList.setVisibility(View.VISIBLE);
					mOptionsList.bringToFront();
					if (mTitlebar != null) {
						mTitlebar.bringToFront();
					}
					mOptionsButton.bringToFront();
					mOptionsList.invalidate();
					Animation inAnimation = new TranslateAnimation(0, 0, -mOptionsList.getHeight(), 0);
					inAnimation.setDuration(300);
					mOptionsList.startAnimation(inAnimation);
				}
			}
		});

		if (mPromoteHelp) {
			mOptionsButton.setText("?");
		}
	}
	
	private class DpadSelectionListener implements AdapterView.OnItemSelectedListener {

		@Override
		public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,
				long arg3) {
			if(arg1 != null && (arg1.getTop() < 0 || arg1.getBottom() > mList.getHeight())) {
				mList.smoothScrollToPosition(arg2,100);
			}
		}

		@Override
		public void onNothingSelected(AdapterView<?> arg0) {
		}
		
	}

	/** Tap row → open the modify/edit action immediately (no expand-to-reveal toolbar). */
	private void openItemEditor(int row) {
		if (mToolbarListener == null || row < 0) {
			return;
		}
		mLastSelectedIndex = row;
		UtilityButton modify = null;
		UtilityButton firstNormal = null;
		for (int i = 0; i < mToolbarButtons.size(); i++) {
			UtilityButton b = mToolbarButtons.get(i);
			if (b.action != UTILITY_BUTTON_ACTION.NORMAL) {
				continue;
			}
			if (firstNormal == null) {
				firstNormal = b;
			}
			if (b.imageResource == R.drawable.toolbar_modify_button) {
				modify = b;
				break;
			}
		}
		UtilityButton target = modify != null ? modify : firstNormal;
		if (target != null) {
			mToolbarListener.onButtonPressed(null, row, target.id);
		}
	}
	
	public void setNewButtonLabel(String str) {
		mNewTitle = str;
	}

	/** Show or hide the optional Refresh control (used by Plugins). */
	public void setRefreshButtonVisible(boolean visible) {
		Button refresh = (Button) findViewById(R.id.refresh);
		if (refresh != null) {
			refresh.setVisibility(visible ? View.VISIBLE : View.GONE);
		}
	}

	/** Wire a click handler for the optional Refresh control. */
	public void setRefreshButtonListener(View.OnClickListener listener) {
		Button refresh = (Button) findViewById(R.id.refresh);
		if (refresh != null) {
			refresh.setOnClickListener(listener);
		}
	}

	public void addListItem(String key, String title, String extra, int mini_icon, boolean enabled) {
		ItemEntry newEntry = new ItemEntry();
		newEntry.title = title;
		newEntry.extra = extra;
		newEntry.enabled = enabled;
		newEntry.mini_icon = mini_icon;
		newEntry.key = key;
		mAllListItems.add(newEntry);
	}
	
	@Override
	public void setTitle(CharSequence title) {
		//((TextView) this.findViewById(R.id.titlebar)).setText(title);
		mTitle = (String)title;
		//this.setTitle(title);
		super.setTitle(title);
	}
	
	protected ArrayList<ItemEntry> mListItems = new ArrayList<ItemEntry>();
	protected ArrayList<ItemEntry> mAllListItems = new ArrayList<ItemEntry>();
	private String mSearchQuery = "";
	private boolean mSearchVisible = true;

	/** Hide the search row (e.g. Speedwalk directions has nothing useful to filter). */
	public void setSearchVisible(boolean visible) {
		mSearchVisible = visible;
		EditText searchField = (EditText) findViewById(R.id.search_field);
		if (searchField != null) {
			searchField.setVisibility(visible ? View.VISIBLE : View.GONE);
		}
	}
	
	public void addListItem(String name,String extra,int mini_icon,boolean enabled) {
		addListItem(name, name, extra, mini_icon, enabled);
	}
	
	public void clearListItems() {
		this.mAllListItems.clear();
		this.mListItems.clear();
	}
	
	protected String getItemKey(int row) {
		if (mAdapter == null || row < 0 || row >= mAdapter.getCount()) {
			return null;
		}
		ItemEntry entry = mAdapter.getItem(row);
		return entry != null ? entry.key : null;
	}
	
	private void applySearchFilter() {
		mListItems.clear();
		String query = mSearchQuery.trim().toLowerCase(Locale.getDefault());
		if (query.length() == 0) {
			mListItems.addAll(mAllListItems);
		} else {
			for (ItemEntry entry : mAllListItems) {
				if (matchesSearch(entry, query)) {
					mListItems.add(entry);
				}
			}
		}
	}
	
	private boolean matchesSearch(ItemEntry entry, String query) {
		if (entry.title != null && entry.title.toLowerCase(Locale.getDefault()).contains(query)) {
			return true;
		}
		return entry.extra != null && entry.extra.toLowerCase(Locale.getDefault()).contains(query);
	}
	
	public void clearOptionItems() {
		this.optionItems.clear();
	}

	/** Refresh the options (=) list after {@link #clearOptionItems()} / re-adding rows. */
	public void notifyOptionItemsChanged() {
		if (mOptionAdapter != null) {
			mOptionAdapter.notifyDataSetChanged();
		}
	}
	
	@Override
	public void onBackPressed() {
		if(mToolbarListener!=null) {
			mToolbarListener.onDonePressed(null);
		}
		this.dismiss();
	}
	
	public void invalidateList() {
		applySearchFilter();
		if(mAdapter == null) return;
		this.mAdapter.notifyDataSetChanged();
		this.mAdapter.notifyDataSetInvalidated();
	}
	//@SuppressWarnings("unchecked")
	private void buildRawList() {
		applySearchFilter();
		if(mAdapter == null) {
			mAdapter = new ItemAdapter(BaseSelectionDialog.this.getContext(), R.layout.editor_selection_list_row, mListItems);
			mList.setAdapter(mAdapter);
		}
		//list.setOnFocusChangeListener(new ListFocusFixerListener());
		//mAdapter.sort(new ItemSorter());
		mAdapter.notifyDataSetInvalidated();
	}
	

	private class ItemAdapter extends ArrayAdapter<ItemEntry> {

		public ItemAdapter(Context context, int textViewResourceId,
				List<ItemEntry> objects) {
			super(context, textViewResourceId, objects);
			// TODO Auto-generated constructor stub
		}
		
		@Override
		public View getView(int pos,View convertView,ViewGroup parent) {
			View v = convertView;
			if(convertView == null) {
				LayoutInflater li = (LayoutInflater)this.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = li.inflate(R.layout.editor_selection_list_row,null);
			}
			
			LinearLayout holder = (LinearLayout)v.findViewById(R.id.toolbarholder);
			holder.removeAllViews();
			attachRowActions(holder, pos);
			
			v.setId(pos*157);
			
			ItemEntry e = this.getItem(pos);
			
			if(e != null) {
				TextView label = (TextView)v.findViewById(R.id.infoTitle);
				TextView extra = (TextView)v.findViewById(R.id.infoExtended);
				
				label.setText(e.title);
				extra.setText(e.extra);
				
				label.setBackgroundColor(0x00000000);
				extra.setBackgroundColor(0x00000000);
				
			}
			
			ImageView iv = (ImageView) v.findViewById(R.id.icon);
			if(e.mini_icon == 0) {
				iv.setVisibility(View.GONE);
			} else {
				iv.setVisibility(View.VISIBLE);
				iv.setImageResource(e.mini_icon);
			}
			return v;
		}
		
	}

	/** Build always-visible enable/edit/delete (etc.) controls for a list row. */
	private void attachRowActions(LinearLayout holder, int row) {
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		params.setMargins(0, 0, 0, 0);
		for (int i = 0; i < mToolbarButtons.size(); i++) {
			UtilityButton b = mToolbarButtons.get(i);
			ImageButton tmp = new ImageButton(mContext);
			tmp.setLayoutParams(params);
			tmp.setPadding(0, 0, 0, 0);
			tmp.setImageResource(b.imageResource);
			tmp.setBackgroundColor(0);
			tmp.setFocusable(false);
			tmp.setFocusableInTouchMode(false);
			RowActionTag tag = new RowActionTag();
			tag.row = row;
			tag.utilityIndex = i;
			tmp.setTag(tag);
			tmp.setOnKeyListener(theButtonKeyListener);
			switch (b.action) {
			case DELETE:
				tmp.setOnClickListener(new DeleteButtonListener());
				break;
			case NORMAL:
				tmp.setOnClickListener(new UtilityButtonListener(i));
				break;
			case TOGGLE:
				tmp.setOnClickListener(new ToggleButtonListener(i));
				break;
			}
			holder.addView(tmp);
		}
		if (mToolbarListener != null) {
			mToolbarListener.willShowToolbar(holder, row);
		}
	}

	private static class RowActionTag {
		int row;
		int utilityIndex;
	}
	
	public class ItemEntry {
		public String title;
		public String extra;
		public String key;
		public boolean selected;
		public boolean enabled;
		public int mini_icon;
		//public int mini_icon_on;
		//public int mini_icon_off;
	}
	
	public class ItemSorter implements Comparator<ItemEntry>{

		public int compare(ItemEntry a, ItemEntry b) {
			return a.title.compareToIgnoreCase(b.title);
		}
		
	}
	
	String[] plugins = new String[]{"foo","bar","baz","zip","zop","woobity","flip","flop"};
	OptionListAdapter mOptionAdapter = null;
	class OptionListAdapter extends ArrayAdapter<BaseOptionItem> {

		public OptionListAdapter(Context context,
				int textViewResourceId, ArrayList<BaseOptionItem> objects) {
			super(context, textViewResourceId, objects);
			// TODO Auto-generated constructor stub
		}
		
		@Override
		public int getViewTypeCount() {
			return 2;
		}
		
		@Override
		public int getItemViewType(int pos) {
			BaseOptionItem item = this.getItem(pos);
			if(item instanceof DividerItem) {
				return 1;
			} else {
				return 0;
			}
		}
		
		@Override
		public boolean areAllItemsEnabled() {
			return true;
		}
		
		@Override
		public boolean isEnabled(int pos) {
			BaseOptionItem item = this.getItem(pos);
			
			if(item instanceof DividerItem) {
				return false;
			} else {
				return true;
			}
		}
		
		@Override
		public View getView(int pos,View convertView,ViewGroup parent) {
			
			BaseOptionItem item = this.getItem(pos);
			
			if(item instanceof DividerItem) {
				//need to do the special text view.
				View tmp = convertView;
				if(tmp == null) {
					LayoutInflater li = (LayoutInflater) BaseSelectionDialog.this.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					
					tmp = li.inflate(R.layout.editor_selection_filter_divider_row, null);
					String dividerTitle = item.title != null ? item.title : "Filter by plugin";
					((TextView)tmp).setText(dividerTitle);
					return tmp;
				} else {
					((TextView)tmp).setText(item.title != null ? item.title : "");
					return tmp;
				}
			}
			
			TextView retView = null;
			if(convertView == null) {
				LayoutInflater li = (LayoutInflater) BaseSelectionDialog.this.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				
				retView = (TextView) li.inflate(R.layout.editor_selection_filter_list_row, null);
				
				//retView = new TextView(TriggerSelectionDialog.this.getContext());
				//AbsListView.LayoutParams params = new AbsListView.LayoutParams(200,60);
				//retView.setLayoutParams(params);
				//retView = v;
				

			} else {
				retView = (TextView)convertView;
			}
			//retView.setTextSize(26);
			//retView.setBackgroundColor(0xFF444444);
			//retView.setTextColor(0xFFAAAAAA);
			//Log.e("TRIG","LOADING: "+this.getItem(pos));
			
			
			retView.setText(item.title);
			
			if(item.centered) {
				retView.setGravity(Gravity.CENTER);
			} else {
				retView.setGravity(Gravity.LEFT);
			}
			return retView;
		}
		
	
		
	}
	
	public ToolBarButtonKeyListener theButtonKeyListener = new ToolBarButtonKeyListener();
	
	public class ToolBarButtonKeyListener implements View.OnKeyListener {

		public boolean onKey(View v, int keyCode, KeyEvent event) {
			return false;
		}
		
	}
	
	public ArrayList<UtilityButton> mToolbarButtons;
	
	private int rowFromTag(View v) {
		Object tag = v.getTag();
		if (tag instanceof RowActionTag) {
			return ((RowActionTag) tag).row;
		}
		return mLastSelectedIndex;
	}
	
	public class ToggleButtonListener implements View.OnClickListener {

		int utilityIndex = -1;
		public ToggleButtonListener(int index) {
			utilityIndex = index;
		}
		
		public void onClick(View v) {
			int index = rowFromTag(v);
			mLastSelectedIndex = index;
			UtilityButton entry = mToolbarButtons.get(utilityIndex);
			
			entry.toggle = !entry.toggle;
			if(mToolbarListener != null) {
				mToolbarListener.onButtonStateChanged((ImageButton)v, index, entry.id, entry.toggle);
			}
			
		}
		
	}
	
	public class UtilityButtonListener implements View.OnClickListener {

		int utilityIndex = -1;
		public UtilityButtonListener(int index) {
			utilityIndex = index;
		}
		
		public void onClick(View v) {
			int index = rowFromTag(v);
			mLastSelectedIndex = index;
			UtilityButton entry = mToolbarButtons.get(utilityIndex);
			
			if(mToolbarListener != null) {
				mToolbarListener.onButtonPressed(v, index, entry.id);
			}
			
		}
		
	}
	
	
	public class DeleteButtonListener implements View.OnClickListener {

		public DeleteButtonListener() {
		}
		
		public void onClick(View v) {
			mLastSelectedIndex = rowFromTag(v);
			
			AlertDialog.Builder builder = new AlertDialog.Builder(BaseSelectionDialog.this.getContext());
			builder.setTitle("Delete Item");
			builder.setMessage("Confirm Delete?");
			builder.setPositiveButton("Delete", new ReallyDeleteTriggerListener());
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
		public ReallyDeleteTriggerListener() {
		}
		public void onClick(DialogInterface dialog, int which) {
			dialog.dismiss();
			if (mAdapter == null || mLastSelectedIndex < 0 || mLastSelectedIndex >= mAdapter.getCount()) {
				return;
			}
			ItemEntry deleted = mAdapter.getItem(mLastSelectedIndex);
			mLastDeletedKey = deleted != null ? deleted.key : null;
			if(mToolbarListener != null) {
				mToolbarListener.onItemDeleted(mLastSelectedIndex);
			}
			if (deleted != null) {
				mAdapter.remove(deleted);
				for (int i = mAllListItems.size() - 1; i >= 0; i--) {
					ItemEntry entry = mAllListItems.get(i);
					if (entry != null && mLastDeletedKey != null && mLastDeletedKey.equals(entry.key)) {
						mAllListItems.remove(i);
					}
				}
			}
			mAdapter.notifyDataSetInvalidated();
			mLastSelectedIndex = -1;
		}
		
	}
	
	public class UtilityButton {
		UTILITY_BUTTON_ACTION action;
		int id;
		ImageButton view;
		boolean toggle;
		int imageResource;
		public UtilityButton() {
			action = UTILITY_BUTTON_ACTION.NORMAL;
			id = 0;
			view = new ImageButton(BaseSelectionDialog.this.getContext());
			toggle = false;
		}
	}
	
	public enum UTILITY_BUTTON_ACTION {
		NORMAL,
		TOGGLE,
		DELETE
	}
	
	public interface UtilityToolbarListener {
		public void onButtonPressed(View v,int row,int index);
		public void onButtonStateChanged(ImageButton v,int row,int index, boolean state);
		public void onItemDeleted(int row);
		public void onNewPressed(View v);
		public void onDonePressed(View v);	
		public void willShowToolbar(LinearLayout v, int row);
		public void willHideToolbar(LinearLayout v, int row);
	}
	
	public interface OptionItemClickListener {
		public void onOptionItemClicked(int row);
	}
	
	private OptionItemClickListener mOptionItemClickListener;
	
	public void setOptionItemClickListener(OptionItemClickListener listener) {
		mOptionItemClickListener = listener;
	}
	
	private UtilityToolbarListener mToolbarListener;
	
	public void setToolbarListener(UtilityToolbarListener listener) {
		mToolbarListener = listener;
	}
	
	private class BaseOptionItem {
		String title;
		boolean centered;
		
		public BaseOptionItem() {
			title = null;
			centered = false;
		}
	}
	
	private class OptionItem extends BaseOptionItem {
		
	}
	
	private class DividerItem extends BaseOptionItem {
		
	}
	
	ArrayList<BaseOptionItem> optionItems = new ArrayList<BaseOptionItem>();
	private boolean mPromoteHelp = false;
	
	public void hideOptionsMenu() {
		if(mOptionsList != null && mOptionsButton != null
				&& mOptionsList.getVisibility() == View.VISIBLE) {
			mOptionsButton.performClick();
		}
	}
	
	public void addOptionItem(String name,boolean centered) {
		OptionItem item = new OptionItem();
		item.title = name;
		item.centered = centered;
		this.optionItems.add(item);
	}
	
	public void addOptionDivider(String name,boolean centered) {
		DividerItem divider = new DividerItem();
		divider.title = name;
		divider.centered = centered;
		this.optionItems.add(divider);
	}
	
	public void setItemMiniIcon(int row,int resource) {
		ItemEntry entry = mAdapter.getItem(row);
		
		entry.mini_icon = resource;
		if (mList != null && row >= mList.getFirstVisiblePosition()
				&& row <= mList.getLastVisiblePosition()) {
			RelativeLayout root = (RelativeLayout)mList.getChildAt(row - mList.getFirstVisiblePosition());
			if (root != null) {
				ImageView icon = (ImageView)root.findViewById(R.id.icon);
				if (icon != null) {
					if (resource == 0) {
						icon.setVisibility(View.GONE);
					} else {
						icon.setVisibility(View.VISIBLE);
						icon.setImageResource(entry.mini_icon);
					}
				}
			}
		} else {
			mAdapter.notifyDataSetChanged();
		}
	}
	
	public void rebuildList() {
		mList.setFocusable(false);
		mList.setOnFocusChangeListener(null);
		buildRawList();
		//mList.setOnFocusChangeListener(new ListFocusFixerListener());
		mList.setFocusable(true);
	}
	
	public void scrollToSelection(String str) {
		for(int i=0;i<mAdapter.getCount();i++) {
			ItemEntry foo = mAdapter.getItem(i);
			if(str.equals(foo.title)) {
				mList.setSelection(i);
				return;
			}
		}
	}
	
	
	
	public void addToolbarButton(int drawable,int id) {
		UtilityButton edit = new UtilityButton();
		edit.id = id;
		edit.action = UTILITY_BUTTON_ACTION.NORMAL;
		edit.imageResource = drawable;
		
		mToolbarButtons.add(edit);
	}
	
	public void addToolbarDeleteButton(int drawable,int id) {
		UtilityButton edit = new UtilityButton();
		edit.id = id;
		edit.action = UTILITY_BUTTON_ACTION.DELETE;
		edit.imageResource = drawable;
		
		mToolbarButtons.add(edit);
	}
	
	public void clearToolbarButtons() {
		mToolbarButtons.clear();
	}
	
	protected void promoteHelp() {
		//promotes the help button to the filter selection dialog.
		mPromoteHelp = true;
		if(mOptionsButton != null) {
			mOptionsButton.setText("?");
			mOptionsButton.invalidate();
		}
	}
	
}

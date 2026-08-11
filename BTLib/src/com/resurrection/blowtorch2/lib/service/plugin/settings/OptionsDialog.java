package com.resurrection.blowtorch2.lib.service.plugin.settings;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.button.ColorPickerDialog;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.util.SettingsSaver;
import com.resurrection.blowtorch2.lib.window.MainWindow;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.widget.Toast;
import android.app.Dialog;
import android.app.AlertDialog.Builder;
import android.app.FragmentManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class OptionsDialog extends Dialog {

	BackPressedListener backListener = null;
	//ListView primeList;
	//ListView altList;
	IConnectionBinder service;
	//SettingsGroup mRoot;
	//SettingsGroup mCurrent;
	//OptionsAdapter primeAdapter;
	//OptionsAdapter altAdapter;
	SettingsGroup mCurrent;
	String selectedPlugin;
	String[] mEncodings = null;
	//FragmentManager mFragementManager;
	
	/**
	 * Settings the button editor owns, hidden from this dialog.
	 *
	 * <p>Both are global switches with a checkbox in the editor's Swipe tab,
	 * which is where you can see what they do. They still need a row in the
	 * profile — that row <em>is</em> where the value is stored, and without it
	 * the editor's checkbox has nowhere to write and resets on every launch —
	 * so the row stays and only the duplicate switch here goes away.
	 *
	 * <p>Per-button settings are a different thing and are not listed here:
	 * neither of these has a per-button override.
	 */
	private static final java.util.HashSet<String> EDITOR_OWNED_KEYS =
			new java.util.HashSet<String>(java.util.Arrays.asList(
					"show_gesture_hints", "show_swipe_preview"));

	HashMap<Integer,String> pluginSettingsMap = new HashMap<Integer,String>();
	boolean toggle = true;
	
	Stack<SettingsGroup> backStack = new Stack<SettingsGroup>();

	/** Keys for StringOption values that represent directories (SAF Browse…). */
	private static boolean isDirectoryOptionKey(String key) {
		return "default_settings_directory".equals(key)
				|| "session_log_directory".equals(key);
	}

	private EditText activeDirectoryEditText;
	private StringOption activeDirectoryOption;
	
	public OptionsDialog(Context context,IConnectionBinder service,String plugin) {
		super(context, R.style.BlowTorch_Dialog_SlideFromRight);
		this.selectedPlugin = plugin;
		this.service = service;
		//this.mFragementManager = fragmentManager;
	}

	/** Apply a path/URI from MainWindow's SAF folder picker into the open string editor. */
	public void applyPickedDirectory(String path) {
		if (path == null) {
			return;
		}
		if (activeDirectoryEditText != null) {
			activeDirectoryEditText.setText(path);
		}
		if (activeDirectoryOption != null) {
			persistStringOption(activeDirectoryOption, path);
		}
	}

	private void persistStringOption(StringOption option, String text) {
		try {
			option.setValue(text);
			if (selectedPlugin.equals("main")) {
				service.updateStringSetting(option.getKey(), text);
			} else {
				service.updatePluginStringSetting(selectedPlugin, option.getKey(), text);
			}
			if (mCurrent != null && mCurrent.findOptionByKey(option.getKey()) != null) {
				mCurrent.updateString(option.getKey(), text);
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void show() {
		Window window = getWindow();
		final boolean hideBars = shouldHideSystemBars();
		logProbeR3("beforeShow");
		if (window != null && hideBars) {
			// The flash the maintainer kept seeing is this window taking focus
			// with the bars in their default state, one frame before anything
			// here has asked for them to go. A window that cannot take focus
			// cannot bring the status bar back with it, so it is shown that way
			// and made focusable again once the bars have been told.
			window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
					WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
		}
		super.show();
		if (window == null) {
			return;
		}
		if (hideBars) {
			hideSystemBars(window);
			window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
		}
		logProbeR3("afterShow");
		window.setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
		WindowManager.LayoutParams params = window.getAttributes();
		params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
		params.width = Math.min(screenWidth, (int) (screenWidth * 0.88f));
		params.height = WindowManager.LayoutParams.MATCH_PARENT;
		window.setAttributes(params);
	}

	/** Is the game hiding the status bar, so this dialog has to as well? */
	private boolean shouldHideSystemBars() {
		return getContext() instanceof MainWindow
				&& ((MainWindow) getContext()).isStatusBarHidden();
	}

	/** Match HelpDialog when the status bar is hidden. */
	private void applyFullscreenIfNeeded() {
		Window win = getWindow();
		if (win == null || !shouldHideSystemBars()) {
			return;
		}
		win.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
	}

	/**
	 * R3 probe: what the status bar is actually doing while Options opens.
	 *
	 * <p>Two fixes for the flash have missed, so this stops guessing and asks
	 * the three questions the guesses assumed answers to: does this dialog
	 * believe it should hide the bar at all, does the flag it copies from the
	 * game window actually exist there, and is the bar reported visible to this
	 * window at each step. Temporary; comes out with the fix.
	 */
	private void logProbeR3(final String where) {
		StringBuilder sb = new StringBuilder(where);
		sb.append(" shouldHide=").append(shouldHideSystemBars());
		if (getContext() instanceof MainWindow) {
			Window game = ((MainWindow) getContext()).getWindow();
			sb.append(" gameFullscreenFlag=").append(game != null
					&& (game.getAttributes().flags
							& WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0);
		}
		Window win = getWindow();
		if (win != null) {
			int flags = win.getAttributes().flags;
			sb.append(" dialogFullscreenFlag=")
					.append((flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0);
			sb.append(" notFocusable=")
					.append((flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0);
			View decor = win.peekDecorView();
			sb.append(" decor=").append(decor != null);
			if (decor != null) {
				WindowInsetsCompat in =
						androidx.core.view.ViewCompat.getRootWindowInsets(decor);
				sb.append(" statusVisible=").append(in == null ? "?"
						: String.valueOf(in.isVisible(WindowInsetsCompat.Type.statusBars())));
			}
		}
		android.util.Log.i("BT_PROBE_R3", sb.toString());
	}

	@Override
	public void onAttachedToWindow() {
		super.onAttachedToWindow();
		logProbeR3("onAttachedToWindow");
	}

	/**
	 * Ask for the status bar to stay away over this dialog.
	 *
	 * <p>{@code FLAG_FULLSCREEN} is what the other dialogs use and what this
	 * one had, and it is deprecated: this app targets SDK 36, where the way to
	 * say it is the insets controller. The flag is left in place for the older
	 * phones this still runs on, and this is the one that is expected to do the
	 * work on a current one. Called after {@code super.show()} so that
	 * {@code onCreate}'s {@code requestFeature} is not preceded by anything
	 * that installs the decor.
	 */
	private void hideSystemBars(final Window win) {
		WindowInsetsControllerCompat insets =
				WindowCompat.getInsetsController(win, win.getDecorView());
		if (insets == null) {
			return;
		}
		insets.setSystemBarsBehavior(
				WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
		insets.hide(WindowInsetsCompat.Type.statusBars());
	}

	public void onCreate(Bundle b) {
		super.onCreate(b);
		pluginSettingsMap.clear();
		Vector<String> items = new Vector<String>();
		for(Charset set : Charset.availableCharsets().values()) {
			items.add(set.displayName());
		}
		
		mEncodings = null;
		if(items.size() > 0) {
			mEncodings = new String[items.size()];
			for(int z=0;z<items.size();z++) {
				mEncodings[z] = items.get(z);
			}
		}
		
		LayoutInflater li = (LayoutInflater) this.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		RelativeLayout tmp = new RelativeLayout(this.getContext());
		
		View root = (RelativeLayout) li.inflate(R.layout.options_dialog, tmp);
		//RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(800,400);
		
		//params.width = RelativeLayout.LayoutParams.FILL_PARENT;
		//root.setLayoutParams(params);
		
		/*Button optionsbutton = (Button) root.findViewById(R.id.optionsbutton);
		optionsbutton.setVisibility(View.GONE);
		
		Button newbutton = (Button)root.findViewById(R.id.add);
		newbutton.setVisibility(View.GONE);
		
		Button donebutton = (Button)root.findViewById(R.id.done);
		donebutton.setVisibility(View.GONE);*/
		
		this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		applyFullscreenIfNeeded();
		
		backListener = new BackPressedListener();
		
		RelativeLayout content = (RelativeLayout) li.inflate(R.layout.options_dialog_content, null);
		
		//LinearLayout prime = (LinearLayout) root.findViewById(R.id.primelistholder);
		
		
		//LinearLayout alt = (LinearLayout) root.findViewById(R.id.altlistholder);
		//altList = (ListView) alt.findViewById(R.id.list);
		
		ListView list = (ListView) content.findViewById(R.id.list);
		TextView title = (TextView) content.findViewById(R.id.title);
		
		content.findViewById(R.id.back).setOnClickListener(backListener);
		//View empty = root.findViewById(R.id.empty);
		//list.setEmptyView(empty);
		
		try {
			mCurrent = service.getSettings();
			//mCurrent = mRoot;
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			HashMap<String,String> map = (HashMap<String, String>) service.getPluginList();
			for(String plugin : map.keySet()) {
				String info = map.get(plugin);
				if (info != null && info.startsWith("MISSING")) {
					continue;
				}
				int pos = mCurrent.getOptions().size();
				SettingsGroup settings = service.getPluginSettings(plugin);
				if(settings != null && settings.getOptions().size() > 0) {
					pluginSettingsMap.put(pos, plugin);
					mCurrent.addOption(settings);
				}
			}
			
		} catch(RemoteException e) {
		
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("OptionsDialog.apply option", e);
		}
		
		
		title.setText(mCurrent.getTitle());
		
		ViewFlipper flipper = (ViewFlipper) root.findViewById(R.id.flipper);
		//flipper.removeAllViews();
		flipper.addView(content);
		
		OptionsAdapter opt = new OptionsAdapter(mCurrent);
		list.setAdapter(opt);
		opt.notifyDataSetInvalidated();
		//primeList.invalidate();
		
		
		this.setContentView(root);
		
		//adapter.
		//list.re
	}
	
	class OptionsAdapter extends BaseAdapter {

		SettingsGroup group;

		/**
		 * The rows actually shown, and for each one its index in the group.
		 *
		 * <p>The index has to be carried: pluginSettingsMap keys the plugin name
		 * by position in the group, so a hidden row above a plugin's group would
		 * otherwise open that group as the wrong plugin.
		 */
		private final ArrayList<Option> visible = new ArrayList<Option>();
		private final ArrayList<Integer> sourceIndex = new ArrayList<Integer>();

		public OptionsAdapter(SettingsGroup sg) {
			this.group = sg;
			ArrayList<Option> all = sg.getOptions();
			for(int i = 0;i < all.size();i++) {
				Option o = all.get(i);
				if(o == null) {
					continue;
				}
				if(o.getKey() != null && EDITOR_OWNED_KEYS.contains(o.getKey())) {
					continue;
				}
				visible.add(o);
				sourceIndex.add(Integer.valueOf(i));
			}
		}

		/** Index in the group of the row drawn at this adapter position. */
		int sourcePosition(int position) {
			if(position < 0 || position >= sourceIndex.size()) {
				return position;
			}
			return sourceIndex.get(position).intValue();
		}

		@Override
		public int getCount() {
			return visible.size();
		}

		@Override
		public Object getItem(int position) {
			return visible.get(position);
		}

		@Override
		public long getItemId(int position) {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
			if(v == null) {
				LayoutInflater li = (LayoutInflater) OptionsDialog.this.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = li.inflate(R.layout.options_list_row, null);
				//android.R.layout.
			}
			
			ImageView iv = (ImageView) v.findViewById(R.id.icon);
			LinearLayout ivl = (LinearLayout) iv.getParent();
			ivl.setVisibility(View.GONE);
			
			Option o = (Option) this.getItem(position);
			
			TextView title = (TextView) v.findViewById(R.id.infoTitle);
			TextView ext = (TextView) v.findViewById(R.id.infoExtended);
			
			title.setText(o.getTitle());
			ext.setText(o.getDescription());
			
			LinearLayout widget = (LinearLayout) v.findViewById(R.id.widget_frame);
			//widget.setVisibility(View.GONE);
			
			//v.setOnClickListener(l)
			v.setTag(null);
			
			widget.removeAllViews();
			switch(o.type) {
			case BOOLEAN:
				CheckBox cb = new CheckBox(OptionsDialog.this.getContext());
				LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
				params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
				//params.setMargins(40, 10, 10, 10);
				cb.setLayoutParams(params);
				//widget.setPadding(left, top, right, bottom)
				//cb.setPadding(10, 10, 10, 10);
				BooleanOption bo = (BooleanOption)o;
				if(((Boolean)bo.getValue()).booleanValue() == false) {
					cb.setChecked(false);
				} else {
					cb.setChecked(true);
				}
				cb.setTag(o);
				cb.setOnCheckedChangeListener(new BooleanCheckChangeListener());
				widget.addView(cb);
				
				//must set up the on checkchange listener.
				break;
			case GROUP:
				v.setTag(o);
				v.setOnClickListener(new GroupClickedListener(sourcePosition(position)));
				break;
			case LIST:
				//set up list dialog clicker.
				v.setTag(o);
				v.setOnClickListener(new ListOptionClickedListener());
				break;
			case ENCODING:
				v.setTag(o);
				v.setOnClickListener(new EncodingOptionClickedListener());
				break;
			case INTEGER:
				
				v.setTag(o);
				IntegerOption integerOption = (IntegerOption)o;
				TextView indicator = new TextView(OptionsDialog.this.getContext());
				indicator.setTextSize(26);
				indicator.setTextColor(0xFFAAAAAA);
				LinearLayout.LayoutParams iparam = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
				iparam.gravity = Gravity.CENTER;
				indicator.setText(Integer.toString((Integer)integerOption.getValue()));
				indicator.setLayoutParams(iparam);
				//indicator.setFocusable(false);
				//indicator.setFocusableInTouchMode(false);
				//widget.setFocusable(flase)
				widget.addView(indicator);
				widget.setTag(o);
				
				v.setOnClickListener(new IntegerOptionClickedListener(indicator));
				widget.setOnClickListener(new IntegerOptionClickedListener(indicator));
				//widget.setOnClickListener(new IntegerOptionClickedListener());
				
				break;
				
			case COLOR:
				v.setTag(o);
				ColorOption co = (ColorOption)o;
				LayoutInflater li = (LayoutInflater)OptionsDialog.this.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				View color_swatch_layout = li.inflate(R.layout.colorswatch_widget, null);
				Button swatch = (Button) color_swatch_layout.findViewById(R.id.colorswatch);
				//swatch.setTag(co);
				swatch.setBackgroundColor((Integer)co.getValue());
				
				v.setOnClickListener(new ColorOptionClickedListener(swatch));
				swatch.setTag(co);
				swatch.setOnClickListener(new ColorOptionClickedListener(swatch));
				
				widget.addView(color_swatch_layout);
				break;
			case FILE:
				v.setTag(o);
				FileOption fileOption = (FileOption) o;
				TextView fileIndicator = new TextView(OptionsDialog.this.getContext());
				fileIndicator.setTextSize(14);
				fileIndicator.setTextColor(0xFFAAAAAA);
				fileIndicator.setMaxLines(1);
				fileIndicator.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
				String fileVal = fileOption.getValue() == null ? "" : fileOption.getValue().toString();
				fileIndicator.setText(displayNameForFontPath(fileVal));
				LinearLayout.LayoutParams fparam = new LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
				fparam.gravity = Gravity.CENTER;
				fileIndicator.setLayoutParams(fparam);
				widget.addView(fileIndicator);
				v.setOnClickListener(new FileOptionClickedListener(fileIndicator));
				break;
			case STRING:
				v.setTag(o);
				v.setOnClickListener(new StringOptionClickedListener());
				break;
			case CALLBACK:
				v.setTag(o);
				if ("manage_gmcp_modules".equals(o.getKey()) && service != null) {
					try {
						String st = service.getGmcpModuleStatus();
						if (st != null && st.length() > 0) {
							ext.setText("Status: " + st + "\n"
									+ (o.getDescription() != null ? o.getDescription() : ""));
						}
					} catch (RemoteException ignored) {
					}
				}
				if ("manage_mcp_packages".equals(o.getKey()) && service != null) {
					try {
						String st = service.getMcpStatusHint();
						if (st != null && st.length() > 0) {
							ext.setText("Status: " + st + "\n"
									+ (o.getDescription() != null ? o.getDescription() : ""));
						}
					} catch (RemoteException ignored) {
					}
				}
				v.setOnClickListener(new CallbackOptionClickedListener());
				break;
			}
			
			return v;
			
		}


		
	}
	
	/** Unwrap Dialog / ContextThemeWrapper to find MainWindow or Activity. */
	private MainWindow findMainWindowHost() {
		Context ctx = getContext();
		while (ctx instanceof ContextWrapper) {
			if (ctx instanceof MainWindow) {
				return (MainWindow) ctx;
			}
			ctx = ((ContextWrapper) ctx).getBaseContext();
		}
		Activity owner = getOwnerActivity();
		if (owner instanceof MainWindow) {
			return (MainWindow) owner;
		}
		return null;
	}

	private Activity findHostActivity() {
		MainWindow mw = findMainWindowHost();
		if (mw != null) {
			return mw;
		}
		Activity owner = getOwnerActivity();
		if (owner != null) {
			return owner;
		}
		Context ctx = getContext();
		while (ctx instanceof ContextWrapper) {
			if (ctx instanceof Activity) {
				return (Activity) ctx;
			}
			ctx = ((ContextWrapper) ctx).getBaseContext();
		}
		return null;
	}

	private class CallbackOptionClickedListener implements View.OnClickListener {

		@Override
		public void onClick(View v) {
			CallbackOption option = (CallbackOption)v.getTag();
			String key = option.getKey();
			// Built-in Options actions — never route through Lua callPluginFunction.
			if ("request_storage_access".equals(key)) {
				MainWindow mw = findMainWindowHost();
				if (mw != null) {
					mw.requestStorageAccessFromOptions();
				}
				return;
			}
			if ("reset_settings".equals(key)) {
				MainWindow mwr = findMainWindowHost();
				if (mwr != null) {
					dismiss();
					mwr.resetSettingsFromOptions();
				}
				return;
			}
			if ("export_settings".equals(key)) {
				MainWindow mwe = findMainWindowHost();
				if (mwe != null) {
					dismiss();
					mwe.exportSettingsFromOptions();
				}
				return;
			}
			if ("import_settings".equals(key)) {
				MainWindow mwi = findMainWindowHost();
				if (mwi != null) {
					dismiss();
					mwi.importSettingsFromOptions();
				}
				return;
			}
			if ("calibrate_light".equals(key)) {
				MainWindow mwl = findMainWindowHost();
				if (mwl != null) {
					mwl.openLightCalibrationFromOptions();
				}
				return;
			}
			if ("calibrate_shake".equals(key)) {
				MainWindow mwc = findMainWindowHost();
				if (mwc != null) {
					mwc.openShakeCalibrationFromOptions();
				}
				return;
			}
			if ("device_sensors".equals(key)) {
				MainWindow mwg = findMainWindowHost();
				if (mwg != null) {
					mwg.openGestureListFromOptions();
				}
				return;
			}
			if ("battery_optimization".equals(key)) {
				Activity activity = findHostActivity();
				if (activity != null) {
					com.resurrection.blowtorch2.lib.util.BatteryOptimizationHelper.promptNow(activity);
				}
				return;
			}
			if ("manage_gmcp_modules".equals(key)) {
				openGmcpModulesDialog();
				return;
			}
			if ("manage_mapper_gmcp".equals(key)) {
				openMapperGmcpDialog();
				return;
			}
			if ("manage_mcp_packages".equals(key)) {
				openMcpPackagesDialog();
				return;
			}
			if ("manage_extra_text_windows".equals(key)) {
				openExtraTextWindowsDialog();
				return;
			}
			// The Lua callback puts its own dialog up on the activity (the button
			// layout wizard does), so this one has to get out of the way first —
			// same as the built-in actions above. Dismiss before the call: the
			// wizard is a service→window round trip and can land either side of it.
			if ("layout_wizard_open".equals(key)) {
				dismiss();
			}
			try {
				service.callPluginFunction(selectedPlugin, (String)option.getValue());
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	
	}

	private void openMapperGmcpDialog() {
		final Context ctx = getContext();
		MapperGmcpDialog.show(ctx, new MapperGmcpDialog.Host() {
			private boolean readBool(String key, boolean def) {
				try {
					SettingsGroup sg = service.getSettings();
					if (sg != null) {
						Object o = sg.findOptionByKey(key);
						if (o instanceof BooleanOption) {
							Object val = ((BooleanOption) o).getValue();
							if (val instanceof Boolean) {
								return ((Boolean) val).booleanValue();
							}
							if (val != null) {
								return Boolean.parseBoolean(val.toString());
							}
						}
					}
				} catch (Exception ignored) {
				}
				return def;
			}

			private String readString(String key, String def) {
				try {
					SettingsGroup sg = service.getSettings();
					if (sg != null) {
						Object o = sg.findOptionByKey(key);
						if (o instanceof StringOption) {
							Object val = ((StringOption) o).getValue();
							if (val instanceof String) {
								return (String) val;
							}
							if (val != null) {
								return val.toString();
							}
						}
					}
				} catch (Exception ignored) {
				}
				return def;
			}

			@Override
			public boolean getUseGmcp() {
				return readBool("mapper_use_gmcp", true);
			}

			@Override
			public String getPolicy() {
				return readString("mapper_gmcp_policy",
						readBool("mapper_gmcp_grow", true) ? "sync" : "follow");
			}

			@Override
			public boolean getGrow() {
				return readBool("mapper_gmcp_grow", true);
			}

			@Override
			public boolean getUseNum() {
				return readBool("mapper_gmcp_use_num", true);
			}

			@Override
			public boolean getUseCoords() {
				return readBool("mapper_gmcp_use_coords", false);
			}

			@Override
			public boolean getCreateExits() {
				return readBool("mapper_gmcp_create_exits", true);
			}

			@Override
			public String getHostHint() {
				try {
					String connected = service.getConnectedTo();
					if (connected != null && connected.trim().length() > 0) {
						return connected.trim();
					}
				} catch (Exception ignored) {
				}
				return null;
			}

			@Override
			public Context getAppContext() {
				return ctx.getApplicationContext() != null
						? ctx.getApplicationContext() : ctx;
			}

			@Override
			public void apply(boolean useGmcp, String policy, boolean useNum,
					boolean useCoords, boolean createExits) {
				try {
					String pol = policy != null ? policy : "sync";
					boolean grow = !"follow".equalsIgnoreCase(pol);
					service.updateBooleanSetting("mapper_use_gmcp", useGmcp);
					service.updateStringSetting("mapper_gmcp_policy", pol);
					service.updateBooleanSetting("mapper_gmcp_grow", grow);
					service.updateBooleanSetting("mapper_gmcp_use_num", useNum);
					service.updateBooleanSetting("mapper_gmcp_use_coords", useCoords);
					service.updateBooleanSetting("mapper_gmcp_create_exits", createExits);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
		});
	}

	private void openGmcpModulesDialog() {
		final Context ctx = getContext();
		GmcpModulesDialog.show(ctx, new GmcpModulesDialog.Host() {
			@Override
			public IConnectionBinder getService() {
				return service;
			}

			@Override
			public String getSupportsString() {
				try {
					SettingsGroup sg = service.getSettings();
					if (sg != null) {
						Object o = sg.findOptionByKey("gmcp_supports");
						if (o instanceof StringOption) {
							Object val = ((StringOption) o).getValue();
							if (val != null) {
								return val.toString();
							}
						}
					}
				} catch (Exception ignored) {
				}
				return com.resurrection.blowtorch2.lib.service.GmcpModuleRegistry.DEFAULT_SUPPORTS;
			}

			@Override
			public void applySupportsString(String supports, boolean renegotiate) {
				try {
					service.updateStringSetting("gmcp_supports", supports);
					if (renegotiate) {
						service.renegotiateGmcp();
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}

			@Override
			@SuppressWarnings("unchecked")
			public ArrayList<String> getSeenModules() {
				try {
					java.util.List list = service.getGmcpSeenModules();
					if (list == null) {
						return new ArrayList<String>();
					}
					ArrayList<String> out = new ArrayList<String>();
					for (Object o : list) {
						if (o != null) {
							out.add(o.toString());
						}
					}
					return out;
				} catch (RemoteException e) {
					return new ArrayList<String>();
				}
			}

			@Override
			public String getStatusHint() {
				try {
					return service.getGmcpModuleStatus();
				} catch (RemoteException e) {
					return "";
				}
			}
		});
	}

	private void openMcpPackagesDialog() {
		final Context ctx = getContext();
		McpPackagesDialog.show(ctx, new McpPackagesDialog.Host() {
			@Override
			public String getPackagesString() {
				try {
					SettingsGroup sg = service.getSettings();
					if (sg != null) {
						Object o = sg.findOptionByKey("mcp_packages");
						if (o instanceof StringOption) {
							Object val = ((StringOption) o).getValue();
							if (val != null) {
								return val.toString();
							}
						}
					}
				} catch (Exception ignored) {
				}
				return com.resurrection.blowtorch2.lib.service.McpPackageRegistry.DEFAULT_PACKAGES;
			}

			@Override
			public void applyPackagesString(String packages, boolean renegotiate) {
				try {
					service.updateStringSetting("mcp_packages", packages);
					if (renegotiate) {
						service.renegotiateMcp();
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}

			@Override
			@SuppressWarnings("unchecked")
			public ArrayList<String> getSeenPackages() {
				try {
					java.util.List list = service.getMcpSeenPackages();
					if (list == null) {
						return new ArrayList<String>();
					}
					ArrayList<String> out = new ArrayList<String>();
					for (Object o : list) {
						if (o != null) {
							out.add(o.toString());
						}
					}
					return out;
				} catch (RemoteException e) {
					return new ArrayList<String>();
				}
			}

			@Override
			public String getStatusHint() {
				try {
					return service.getMcpStatusHint();
				} catch (RemoteException e) {
					return "";
				}
			}
		});
	}

	private void openExtraTextWindowsDialog() {
		final Context ctx = getContext();
		ExtraTextWindowsDialog.show(ctx, new ExtraTextWindowsDialog.Host() {
			@Override
			public IConnectionBinder getService() {
				return service;
			}

			@Override
			public String getSlotsJson() {
				try {
					SettingsGroup sg = service.getSettings();
					if (sg != null) {
						Object o = sg.findOptionByKey(
								com.resurrection.blowtorch2.lib.window.ExtraTextSlotsStore.SETTING_KEY);
						if (o instanceof StringOption) {
							Object val = ((StringOption) o).getValue();
							if (val != null) {
								return val.toString();
							}
						}
					}
				} catch (Exception ignored) {
				}
				return "[]";
			}

			@Override
			public boolean isEnabled() {
				try {
					SettingsGroup sg = service.getSettings();
					if (sg != null) {
						Object o = sg.findOptionByKey(
								com.resurrection.blowtorch2.lib.window.ExtraTextSlotsStore.ENABLED_KEY);
						if (o instanceof BooleanOption) {
							Object val = ((BooleanOption) o).getValue();
							if (val instanceof Boolean) {
								return ((Boolean) val).booleanValue();
							}
						}
					}
				} catch (Exception ignored) {
				}
				return true;
			}

			@Override
			public void applySlotsJson(String json, boolean enabled) {
				try {
					service.updateStringSetting(
							com.resurrection.blowtorch2.lib.window.ExtraTextSlotsStore.SETTING_KEY,
							json != null ? json : "[]");
					service.updateBooleanSetting(
							com.resurrection.blowtorch2.lib.window.ExtraTextSlotsStore.ENABLED_KEY,
							enabled);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onSlotsChanged() {
				// Settings update triggers Connection.ensureExtraTextSlots + UI notify.
			}

			@Override
			public boolean isGmcpEnabled() {
				try {
					SettingsGroup sg = service.getSettings();
					if (sg != null) {
						Object o = sg.findOptionByKey("use_gmcp");
						if (o instanceof BooleanOption) {
							Object val = ((BooleanOption) o).getValue();
							if (val instanceof Boolean) {
								return ((Boolean) val).booleanValue();
							}
						}
					}
				} catch (Exception ignored) {
				}
				return true;
			}

			@Override
			public java.util.ArrayList<String> getSeenGmcpModules() {
				java.util.ArrayList<String> out = new java.util.ArrayList<String>();
				try {
					java.util.List list = service.getGmcpSeenModules();
					if (list != null) {
						for (int i = 0; i < list.size(); i++) {
							Object o = list.get(i);
							if (o != null) {
								out.add(o.toString());
							}
						}
					}
				} catch (Exception ignored) {
				}
				return out;
			}
		});
	}
	
	/** Human-readable label for a font path / built-in font key. */
	static String displayNameForFontPath(String path) {
		if (path == null || path.length() == 0) {
			return "";
		}
		if ("monospace".equals(path)) {
			return "monospace";
		}
		if ("sans serif".equals(path) || "sans serrif".equals(path)) {
			return "sans serif";
		}
		if ("default".equals(path) || "none".equals(path)) {
			return path;
		}
		String name = path;
		int slash = path.lastIndexOf('/');
		if (slash >= 0 && slash < path.length() - 1) {
			name = path.substring(slash + 1);
		}
		if (name.endsWith(".ttf") || name.endsWith(".TTF")) {
			name = name.substring(0, name.length() - 4);
		}
		if ("DejaVuSansMono".equals(name)) {
			return "DejaVu Sans Mono";
		}
		if ("LiberationMono-Regular".equals(name)) {
			return "Liberation Mono";
		}
		if ("VeraMono".equals(name)) {
			return "Bitstream Vera Sans Mono";
		}
		if ("NotoSansMono-Regular".equals(name)) {
			return "Noto Sans Mono";
		}
		if ("DroidSansMono".equals(name)) {
			return "Droid Sans Mono";
		}
		if ("RobotoMono-Regular".equals(name)) {
			return "Roboto Mono";
		}
		// Soften CamelCase / hyphenated file names for system fonts
		return name.replace('-', ' ').replace('_', ' ');
	}

	private class FileOptionClickedListener implements View.OnClickListener {

		private final TextView indicator;

		FileOptionClickedListener(TextView indicator) {
			this.indicator = indicator;
		}

		@Override
		public void onClick(View v) {
			FileOption o = (FileOption)v.getTag();
			
			//this is tricky. we have to build the list. in the right order.
			//first build up the actual file matches, sort them and insert the "items" at the top.
			//ArrayList<String> paths = new ArrayList<String>();
			StringBuilder str = new StringBuilder();
			ArrayList<String> extensions = o.extensions;
			for(int i=0;i<extensions.size();i++) {
				str.append("(^.+(\\Q"+extensions.get(i)+"\\E))");
				if(i != extensions.size()-1) {
					str.append("|");
				}
			}
			
			Pattern p = Pattern.compile(str.toString());
			Matcher m = p.matcher("");
			
			PatternFileNameFilter filter = new PatternFileNameFilter(m);
			
			ArrayList<String> foundFilePaths = new ArrayList<String>();
			ArrayList<String> foundFileNames = new ArrayList<String>();
			
			ArrayList<String> items = o.items;
			for(int i=0;i<items.size();i++) {
				String item = items.get(i);
				foundFilePaths.add(item);
				foundFileNames.add(displayNameForFontPath(item));
			}
			ArrayList<String> paths = o.paths;
			for(int i=0;i<paths.size();i++) {
				String path = paths.get(i);
				
				if(path.startsWith("/")) {
					//use it directly
					File file = new File(path);
					File[] listed = file.isDirectory() ? file.listFiles(filter) : null;
					if (listed != null) {
						for(File found : listed) {
							foundFilePaths.add(found.getPath());
							foundFileNames.add(displayNameForFontPath(found.getPath()));
						}
					}
				} else {
					//get it from sdcard.
					String sdstate = Environment.getExternalStorageState();
					if(Environment.MEDIA_MOUNTED.equals(sdstate) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(sdstate)) {
						File tmp = Environment.getExternalStorageDirectory();
						File file = new File(tmp,"/"+path);
						File[] listed = file.isDirectory() ? file.listFiles(filter) : null;
						if (listed != null) {
							for(File found : listed) {
								foundFilePaths.add(found.getPath());
								foundFileNames.add(displayNameForFontPath(found.getPath()));
							}
						}
					}
				}
			}
			
			String[] entries = new String[foundFileNames.size()];
			entries = foundFileNames.toArray(entries);
			
			int selectedIndex = -1;
			for(int i=0;i<foundFilePaths.size();i++) {
				String path = foundFilePaths.get(i);
				if(path.equals((String)o.getValue())) {
					selectedIndex = i;
					i=foundFilePaths.size();
				}
			}
			
			AlertDialog.Builder builder = new AlertDialog.Builder(OptionsDialog.this.getContext());
			builder.setTitle(o.getTitle());
			builder.setSingleChoiceItems(entries, selectedIndex,new FileOptionItemClickListener((FileOption)o,foundFilePaths,foundFileNames,indicator));

			AlertDialog dialog = builder.create();
			dialog.show();
			
		}
		
	}
	
	private class FileOptionItemClickListener implements DialogInterface.OnClickListener {

		private ArrayList<String> paths;
		private ArrayList<String> names;
		private FileOption option;
		private TextView indicator;
		
		public FileOptionItemClickListener(FileOption option,ArrayList<String> paths,ArrayList<String> names, TextView indicator) {
			this.paths = paths;
			this.names = names;
			this.option = option;
			this.indicator = indicator;
		}
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			String path = paths.get(which);
			option.setValue(path);
			if (indicator != null) {
				String label = (names != null && which < names.size())
						? names.get(which)
						: displayNameForFontPath(path);
				indicator.setText(label);
			}
			// Nested groups (e.g. Window → Font) live on mCurrent; update that group so listeners fire.
			if (mCurrent != null && mCurrent.findOptionByKey(option.getKey()) != null) {
				mCurrent.updateString(option.getKey(), path);
			}
			if(selectedPlugin.equals("main")) {
				try {
					service.updateStringSetting(option.getKey(), path);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				try {
					service.updatePluginStringSetting(selectedPlugin, option.getKey(), path);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			dialog.dismiss();
		}
		
	}
	
	private class PatternFileNameFilter implements FilenameFilter {

		Matcher m;
		public PatternFileNameFilter(Matcher matcher) {
			m = matcher;
		}
		
		@Override
		public boolean accept(File dir, String filename) {
			m.reset(filename);
			if(m.matches()) {
				return true;
			} else {
				return false;
			}
		}

	}
	
	private class ColorOptionClickedListener implements View.OnClickListener,ColorPickerDialog.OnColorChangedListener {

		private ColorOption option;
		Button widget;
		
		public ColorOptionClickedListener(Button widget) {
			this.widget = widget;
		}
		
		@Override
		public void onClick(View v) {
			option = (ColorOption) v.getTag();
			
			ColorPickerDialog dialog = new ColorPickerDialog(OptionsDialog.this.getContext(),this,(Integer)option.getValue());
			dialog.show();
		}

		@Override
		public void colorChanged(int color) {
			option.setValue(color);
			widget.setBackgroundColor(color);
			widget.invalidate();
			if(selectedPlugin.equals("main")) {
				try {
					service.updateIntegerSetting(option.getKey(), color);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				try {
					service.updatePluginIntegerSetting(selectedPlugin, option.getKey(), color);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
	}
	
	private class IntegerOptionClickedListener implements View.OnClickListener {

		private TextView widget;
		
		public IntegerOptionClickedListener(TextView widget) {
			this.widget = widget;
		}
		
		@Override
		public void onClick(View v) {
			IntegerOption o = (IntegerOption) v.getTag();
			
			AlertDialog.Builder builder = new AlertDialog.Builder(OptionsDialog.this.getContext());
			
			builder.setTitle(o.getTitle());
			EditText input = new EditText(OptionsDialog.this.getContext());
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
			input.setLayoutParams(params);
			input.setTextSize(26);
			input.setText(Integer.toString((Integer)o.getValue()));
			input.setInputType(InputType.TYPE_CLASS_NUMBER);
			input.setGravity(Gravity.RIGHT);
			builder.setView(input);
			
			//builder.setView(input);
			
			builder.setPositiveButton("Done", new IntegerOptionFinishedListener(o,input,widget));
			builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			
			AlertDialog dialog = builder.create();
			dialog.show();
			
		}
		
	}
	
	private class IntegerOptionFinishedListener implements DialogInterface.OnClickListener {

		private IntegerOption option;
		EditText input;
		TextView widget;
		
		
		public IntegerOptionFinishedListener(IntegerOption option,EditText input,TextView widget) {
			this.option = option;
			this.input = input;
			this.widget = widget;
		}
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			
			
			String text = input.getText().toString();
			
			try{
				Integer number = Integer.parseInt(text);
				option.setValue(number);
				widget.setText(text);
				if(selectedPlugin.equals("main")) {
					service.updateIntegerSetting(option.getKey(), number);
				} else {
					service.updatePluginIntegerSetting(selectedPlugin,option.getKey(), number);
				}
			
			} catch(NumberFormatException ignored) {
				// Typed something that is not a number: leave the option alone.
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			dialog.dismiss();
		}
		
	}
	
	private class StringOptionClickedListener implements View.OnClickListener {

		public StringOptionClickedListener() {
		}
		
		@Override
		public void onClick(View v) {
			final StringOption o = (StringOption) v.getTag();
			final boolean directoryOption = isDirectoryOptionKey(o.getKey());
			
			AlertDialog.Builder builder = new AlertDialog.Builder(OptionsDialog.this.getContext());
			
			builder.setTitle(o.getTitle());
			final EditText input = new EditText(OptionsDialog.this.getContext());
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
			input.setLayoutParams(params);
			input.setTextSize(directoryOption ? 16 : 26);
			Object cur = o.getValue();
			input.setText(cur == null ? "" : cur.toString());
			input.setInputType(InputType.TYPE_CLASS_TEXT);
			input.setGravity(Gravity.LEFT);
			if (directoryOption) {
				input.setHint("Absolute path or content:// URI");
			}
			builder.setView(input);
			
			builder.setPositiveButton("Done", new StringOptionFinishedListener(o,input));
			builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					activeDirectoryEditText = null;
					activeDirectoryOption = null;
					dialog.dismiss();
				}
			});
			if (directoryOption) {
				builder.setNeutralButton("Browse…", null);
			}
			
			final AlertDialog dialog = builder.create();
			if (directoryOption) {
				dialog.setOnShowListener(new DialogInterface.OnShowListener() {
					@Override
					public void onShow(DialogInterface d) {
						activeDirectoryEditText = input;
						activeDirectoryOption = o;
						Button browse = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
						if (browse != null) {
							browse.setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View view) {
									MainWindow mw = findMainWindowHost();
									if (mw != null) {
										mw.pickDirectoryForOption();
									} else {
										android.widget.Toast.makeText(
												OptionsDialog.this.getContext(),
												"Folder picker unavailable (no host activity).",
												android.widget.Toast.LENGTH_LONG).show();
									}
								}
							});
						}
					}
				});
			}
			dialog.show();
			
		}
		
	}
	
	private class StringOptionFinishedListener implements DialogInterface.OnClickListener {

		private StringOption option;
		EditText input;
		
		
		public StringOptionFinishedListener(StringOption option,EditText input) {
			this.option = option;
			this.input = input;
		}
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			String text = input.getText().toString();
			persistStringOption(option, text);
			activeDirectoryEditText = null;
			activeDirectoryOption = null;
			dialog.dismiss();
		}
		
	}
	
	private class EncodingOptionClickedListener implements View.OnClickListener {

		@Override
		public void onClick(View v) {
			EncodingOption o = (EncodingOption)v.getTag();
			
			int selected = -1;
			String current = (String) o.getValue();
			for(int i=0;i<mEncodings.length;i++) {
				String str = mEncodings[i];
				if(str.equals(current)) {
					selected = i;
					i = mEncodings.length;
				}
			}
			
			AlertDialog.Builder builder = new AlertDialog.Builder(OptionsDialog.this.getContext());
			builder.setTitle("Select Encoding:");
			
			builder.setSingleChoiceItems(mEncodings, selected, new EncodingItemClickListener(o));
			
			AlertDialog dialog = builder.create();
			//GenericDialogFragment gdf = new GenericDialogFragment(dialog);
			//gdf.showWithTag("encoding_dialog");
			//gdf.show(mFragementManager, "encoding_dialog");
			//dialog.show();
			dialog.show();
		}
		
	}
	
	private class EncodingItemClickListener implements DialogInterface.OnClickListener {

		private EncodingOption option;
		
		public EncodingItemClickListener(EncodingOption option) {
			this.option = option;
		}
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			
			String encoding = mEncodings[which];
			String key = option.getKey();
			option.setValue(encoding);
			
			if(selectedPlugin.equals("main")) {
				try {
					service.updateStringSetting(key, encoding);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				try {
					service.updatePluginStringSetting(selectedPlugin, key, encoding);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			dialog.dismiss();
		}
		
	}
	
	private class GroupClickedListener implements View.OnClickListener {

		private int pos = -1;
		public GroupClickedListener(int pos) {
			this.pos = pos;
		}
		
		@Override
		public void onClick(View v) {
			//get the tag for this view, it will be the key.
			if(backStack.size() == 0) {
				//we need to switch the current plugin so the appropriate settingsgroup gets updated.
				if(pluginSettingsMap.containsKey(this.pos)) {
					OptionsDialog.this.selectedPlugin = pluginSettingsMap.get(this.pos);
				}
				//proceed as usual.
			}
			
			Option key = (Option) v.getTag();
			backStack.push(mCurrent);
			mCurrent = (SettingsGroup) key;
			
			LayoutInflater li = (LayoutInflater) OptionsDialog.this.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			
			RelativeLayout newContent = (RelativeLayout) li.inflate(R.layout.options_dialog_content, null);
			ListView list = (ListView) newContent.findViewById(R.id.list);
			
			OptionsAdapter newAdapt = new OptionsAdapter(mCurrent);
			list.setAdapter(newAdapt);
			newAdapt.notifyDataSetInvalidated();
			
			//LinearLayout group = (LinearLayout) altList.getParent();
			TextView title = (TextView) newContent.findViewById(R.id.title);
			title.setText(key.getTitle());
			ViewFlipper f = (ViewFlipper) OptionsDialog.this.findViewById(R.id.flipper);
			newContent.findViewById(R.id.back).setOnClickListener(backListener);
			f.addView(newContent);
			//int amount = altList.getWidth();
			//int amount = 600;
			//TranslateAnimation outAnim = new TranslateAnimation(0,-amount,0,0);
			//TranslateAnimation inAnim = new TranslateAnimation(amount,0,0,0);
			TranslateAnimation outAnim = new TranslateAnimation(Animation.RELATIVE_TO_SELF,0.0f,Animation.RELATIVE_TO_SELF,-1.0f,Animation.RELATIVE_TO_SELF,0.0f,Animation.RELATIVE_TO_SELF,0.0f);
			TranslateAnimation inAnim  = new TranslateAnimation(Animation.RELATIVE_TO_SELF,1.0f,Animation.RELATIVE_TO_SELF,0.0f,Animation.RELATIVE_TO_SELF,0.0f,Animation.RELATIVE_TO_SELF,0.0f);
			outAnim.setDuration(PAGE_SLIDE_MS);
			inAnim.setDuration(PAGE_SLIDE_MS);
			f.setInAnimation(inAnim);
			f.setOutAnimation(outAnim);

			// Draw each page into a texture for the length of the slide. A
			// TranslateAnimation redraws what it moves on every frame, and what
			// is moving here is a ListView whose rows build a CheckBox or a
			// swatch as they bind — so the deeper pages, which have more rows,
			// stutter while the top level does not. Cached, the slide is one
			// texture moving.
			liftPagesForSlide(f, newContent, inAnim);

			f.showNext();
		}
		
	}
	
	private class ListOptionClickedListener implements View.OnClickListener {

		@Override
		public void onClick(View v) {
			ListOption o = (ListOption)v.getTag();
			
			ArrayList<String> items = o.getItems();
			String[] foo = new String[items.size()];
			
			foo = items.toArray(foo);
			
			
			AlertDialog.Builder builder = new AlertDialog.Builder(OptionsDialog.this.getContext());
			
			builder.setTitle(o.getTitle());
			//builder.setSin
			builder.setSingleChoiceItems(foo, ((Integer)o.getValue()).intValue(),new ListItemClickListener(o));
			
			AlertDialog d = builder.create();
			d.show();
			
		}
		
	}
	
	private class ListItemClickListener implements DialogInterface.OnClickListener {

		private ListOption option;
		
		public ListItemClickListener(ListOption option) {
			this.option = option;
		}
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			String picked = option.getItems().get(which);
			
			option.setValue(which);
			
			if(selectedPlugin.equals("main")) {
				try {
					service.updateIntegerSetting(option.getKey(),which);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				//service.updatePluginSetting(
				try {
					service.updatePluginIntegerSetting(selectedPlugin,option.getKey(),which);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			//service.updateSetting
			
			
			dialog.dismiss();
		}
		
	}
	
	private class BooleanCheckChangeListener implements CompoundButton.OnCheckedChangeListener {

		@Override
		public void onCheckedChanged(CompoundButton v,
				boolean isChecked) {
			BooleanOption o = (BooleanOption) v.getTag();
			o.setValue(isChecked);
			if(selectedPlugin.equals("main")) {
				try {
					service.updateBooleanSetting(o.getKey(),isChecked);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				try {
					service.updatePluginBooleanSetting(selectedPlugin,o.getKey(),isChecked);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
	}
	
	/**
	 * Closing this dialog saves, whichever way it was closed.
	 *
	 * <p>Back used to be the only exit that saved. A profile closed through Lua's
	 * {@code CloseOptionsDialog()} -- which reaches
	 * {@code MainWindow.closeOptionsDialog}, a bare {@code dismiss()} -- threw
	 * away whatever had just been changed, and any exit added later would have
	 * inherited the same hole. Persisting is this dialog's business, not each
	 * caller's, so it lives at the one point they all go through.
	 *
	 * <p>Off the UI thread on purpose: {@code saveSettings} is a synchronous
	 * binder call that writes and fsyncs, measured at 276-285 ms, and it was
	 * landing on this dialog's own dismiss animation. The service already holds
	 * every edit -- they go across as they are made -- so nothing here is waiting
	 * to be collected. {@code SettingsSaver} drops a second request that arrives
	 * before the first has started, so a double dismiss does not write twice.
	 */
	@Override
	public void dismiss() {
		SettingsSaver.saveInBackground(service);
		super.dismiss();
	}

	@Override
	public void onBackPressed() {
		if(backStack.size() == 0) {
			// The save lives in dismiss() now, so every way out gets it.
			this.dismiss();
		} else {
			SettingsGroup key = backStack.pop();
			if(backStack.size() == 0) {
				selectedPlugin = "main";
			}
			//Option key = (Option) v.getTag();
			//backStack.push(mCurrent);
			mCurrent = (SettingsGroup) key;
			/*primeAdapter = new OptionsAdapter(mCurrent);
			primeList.setAdapter(primeAdapter);
			primeAdapter.notifyDataSetInvalidated();
			
			LinearLayout group = (LinearLayout) primeList.getParent();
			TextView title = (TextView) group.findViewById(R.id.title);
			title.setText(key.getTitle());*/
			ViewFlipper f = (ViewFlipper) OptionsDialog.this.findViewById(R.id.flipper);
			
			//int amount = altList.getWidth();
			//int amount = 600;
			//TranslateAnimation outAnim = new TranslateAnimation(0,-amount,0,0);
			//TranslateAnimation inAnim = new TranslateAnimation(amount,0,0,0);
			TranslateAnimation outAnim = new TranslateAnimation(Animation.RELATIVE_TO_SELF,0.0f,Animation.RELATIVE_TO_SELF,-1.0f,Animation.RELATIVE_TO_SELF,0.0f,Animation.RELATIVE_TO_SELF,0.0f);
			TranslateAnimation inAnim  = new TranslateAnimation(Animation.RELATIVE_TO_SELF,1.0f,Animation.RELATIVE_TO_SELF,0.0f,Animation.RELATIVE_TO_SELF,0.0f,Animation.RELATIVE_TO_SELF,0.0f);
			liftPagesForSlide(f, null, inAnim);

			outAnim.setAnimationListener(new AnimationListener() {

				@Override
				public void onAnimationEnd(Animation animation) {
					ViewFlipper f = (ViewFlipper) OptionsDialog.this.findViewById(R.id.flipper);
					f.removeViewAt(f.getChildCount()-1);
				}

				@Override
				public void onAnimationRepeat(Animation animation) {
					// TODO Auto-generated method stub
					
				}

				@Override
				public void onAnimationStart(Animation animation) {
					// TODO Auto-generated method stub
					
				}
				
			});
			outAnim.setDuration(PAGE_SLIDE_MS);
			inAnim.setDuration(PAGE_SLIDE_MS);
			f.setInAnimation(inAnim);
			f.setOutAnimation(outAnim);
			
			f.showPrevious();
		}
	}
	
	/**
	 * How long a page takes to slide in or out.
	 *
	 * <p>Was half a second. That is long enough to notice as slowness on its own,
	 * and every frame of it is a frame that can be dropped.
	 */
	private static final int PAGE_SLIDE_MS = 200;

	/**
	 * Put the flipper's pages in hardware layers for the length of one slide.
	 *
	 * @param f the flipper.
	 * @param incoming the page sliding in, when it is not a child yet.
	 * @param inAnim the animation to hang the "put them back" on. Both pages are
	 *        restored together: leaving a ListView in a layer costs memory and
	 *        stops it redrawing when its rows change.
	 */
	private void liftPagesForSlide(final ViewFlipper f, final View incoming,
			Animation inAnim) {
		final java.util.ArrayList<View> lifted = new java.util.ArrayList<View>(2);
		for (int i = 0; i < f.getChildCount(); i++) {
			lifted.add(f.getChildAt(i));
		}
		if (incoming != null && !lifted.contains(incoming)) {
			lifted.add(incoming);
		}
		for (View v : lifted) {
			v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
		}
		inAnim.setAnimationListener(new AnimationListener() {
			@Override
			public void onAnimationEnd(Animation animation) {
				for (View v : lifted) {
					v.setLayerType(View.LAYER_TYPE_NONE, null);
				}
			}

			@Override
			public void onAnimationRepeat(Animation animation) {
			}

			@Override
			public void onAnimationStart(Animation animation) {
			}
		});
	}

	private class BackPressedListener implements View.OnClickListener {

		@Override
		public void onClick(View v) {
			OptionsDialog.this.onBackPressed();
		}
		
	}
	
}

package com.resurrection.blowtorch2.lib.window;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.keplerproject.luajava.JavaFunction;
import org.keplerproject.luajava.LuaException;
import org.keplerproject.luajava.LuaState;
import org.keplerproject.luajava.LuaStateFactory;


import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog.Builder;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import android.text.InputType;
import android.util.Log;
//import android.util.Log;
//import android.util.Log;
import android.view.ActionMode;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;

import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationSet;
import android.view.animation.LayoutAnimationController;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.service.IConnectionBinderCallback;
import com.resurrection.blowtorch2.lib.alias.AliasData;
import com.resurrection.blowtorch2.lib.alias.BetterAliasSelectionDialog;
import com.resurrection.blowtorch2.lib.button.ButtonEditorDialog;
import com.resurrection.blowtorch2.lib.button.ButtonSetSelectorDialog;
import com.resurrection.blowtorch2.lib.button.SlickButton;
import com.resurrection.blowtorch2.lib.button.SlickButtonData;
import com.resurrection.blowtorch2.lib.service.*;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.OptionsDialog;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsGroup;
import com.resurrection.blowtorch2.lib.settings.ColorSetSettings;
import com.resurrection.blowtorch2.lib.settings.ConfigurationLoader;
import com.resurrection.blowtorch2.lib.settings.HyperSettings;
import com.resurrection.blowtorch2.lib.settings.HyperSettingsActivity;
import com.resurrection.blowtorch2.lib.speedwalk.BetterSpeedWalkConfigurationDialog;
import com.resurrection.blowtorch2.lib.speedwalk.SpeedWalkConfigurationDialog;
import com.resurrection.blowtorch2.lib.timer.BetterTimerSelectionDialog;
import com.resurrection.blowtorch2.lib.trigger.BetterTriggerSelectionDialog;
import com.resurrection.blowtorch2.lib.ui.SDCardUtils;
import com.resurrection.blowtorch2.lib.ui.PermissionHelper;
import com.resurrection.blowtorch2.lib.mapper.MapperController;
import com.resurrection.blowtorch2.lib.mapper.MapperOverlayController;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;

public class MainWindow extends AppCompatActivity implements MainWindowCallback,ActivityCompat.OnRequestPermissionsResultCallback {
	
	public static String TEST_MODE = "blowTorchTestMode";
	public static String NORMAL_MODE = "blowTorchNormalMode";

	private static final int RP_INFO = 5000;
	private static final int RP_EXPORT = 5001;
	private static final int RP_IMPORT = 5002;
	private static final int RP_NOTIFICATIONS = 5003;
	private static final int REQUEST_PICK_DIRECTORY = 2103;
	
	//public static final String PREFS_NAME = "CONDIALOG_SETTINGS";
	//public String PREFS_NAME;
	private int MAIN_WINDOW_ID = -1;
	protected static final int MESSAGE_HTMLINC = 110;
	protected static final int MESSAGE_RAWINC = 111;
	protected static final int MESSAGE_BUFFINC = 112;
	protected static final int MESSAGE_PROCESS = 102;
	protected static final int MESSAGE_PROCESSED = 104;
	public static final int MESSAGE_SENDDATAOUT = 105;
	protected static final int MESSAGE_RESETINPUTWINDOW = 106;
	protected static final int MESSAGE_PROCESSINPUTWINDOW = 107;
	protected static final int MESSAGE_LOADSETTINGS = 200;
	protected static final int MESSAGE_ADDBUTTON = 201;
	public static final int MESSAGE_MODIFYBUTTON = 202;
	public static final int MESSAGE_NEWBUTTONSET = 205;
	public static final int MESSAGE_CHANGEBUTTONSET = 206;
	public static final int MESSAGE_RELOADBUTTONSET = 208;
	protected static final int MESSAGE_BUTTONREQUESTINGSETCHANGE = 207;
	protected static final int MESSAGE_XMLERROR = 397;
	protected static final int MESSAGE_SAVEERROR = 3993;
	protected static final int MESSAGE_PLUGINSAVEERROR = 3994;
	protected static final int MESSAGE_COLORDEBUG = 675;
	protected static final int MESSAGE_DIRTYEXITNOW = 943;
	protected static final int MESSAGE_DOHAPTICFEEDBACK = 856;
	public static final int MESSAGE_DELETEBUTTONSET = 867;
	public static final int MESSAGE_CLEARBUTTONSET = 868;
	protected static final int MESSAGE_SHOWTOAST = 869;
	protected static final int MESSAGE_SHOWDIALOG = 870;
	public static final int MESSAGE_HFPRESS = 871;
	public static final int MESSAGE_HFFLIP = 872;
	public static final int MESSAGE_LOCKUNDONE = 873;
	public static final int MESSAGE_BUTTONFIT = 874;
	protected static final int MESSAGE_BELLTOAST = 876;
	protected static final int MESSAGE_DOSCREENMODE = 877;
	protected static final int MESSAGE_KEYBOARD = 878;
	protected static final int MESSAGE_DODISCONNECT = 879;
	public static final int MESSAGE_SENDBUTTONDATA = 880;
	private static final int MESSAGE_LINEBREAK = 881;
	public static final int MESSAGE_HIDEKEYBOARD =882;
	protected static final int MESSAGE_CLEARINPUTWINDOW = 883;
	//protected static final int MESSAGE_BUTTONRELOAD = 882;
	protected static final int MESSAGE_CLOSEINPUTWINDOW = 884;
	private static final int MESSAGE_RENAWS = 885;
	/** Connect only after the first live NAWS measurement (avoids wrong size + startup races). */
	private static final int MESSAGE_CONNECT_WHEN_READY = 8851;
	private boolean mPendingInitialConnect = false;
	public final static int MESSAGE_LAUNCHURL = 886;
	protected static final int MESSAGE_CLEARALLBUTTONS = 887;
	/** MCP displayurl — open Intent.ACTION_VIEW with obj as URL string. */
	private static final int MESSAGE_MCP_LAUNCHURL = 8862;
	/** MCP simpleedit — Bundle with reference/title/type/content. */
	private static final int MESSAGE_MCP_SIMPLEEDIT = 8863;
	protected static final int MESSAGE_MAXVITALS = 100000;
	//protected static final int MESSAGE_VITALS = 1000001;
	//protected static final int MESSAGE_ENEMYHP = 1000002;
	//protected static final int MESSAGE_VITALS2 = 1000003;
	protected static final int MESSAGE_TESTLUA = 100004;
	protected static final int MESSAGE_TRIGGERSTR = 100005;
	protected static final int MESSAGE_SWITCH = 888;
	/** below is deprecated, remove. */
	protected static final int MESSAGE_RELOADBUFFER = 889;
	protected static final int MESSAGE_INITIALIZEWINDOWS = 890;
	public static final int MESSAGE_ADDOPTIONCALLBACK = 891;
	public static final int MESSAGE_PLUGINXCALLS = 892;
	public static final int MESSAGE_WINDOWBUFFERMAXCHANGED = 893;
	protected static final int MESSAGE_MARKWINDOWSDIRTY = 894;
	protected static final int MESSAGE_MARKSETTINGSDIRTY = 895;
	//private TextTree tree = new TextTree();
	protected static final int MESSAGE_SETKEEPLAST = 896;
	public static final int MESSAGE_PUSHMENUSTACK = 897;
	public static final int MESSAGE_POPMENUSTACK = 898;
	public static final int MESSAGE_DISPLAYLUAERROR = 899;
	protected static final int MESSAGE_USESUGGESTIONS = 900;
	protected static final int MESSAGE_USEFULLSCREENEDITOR = 901;
	protected static final int MESSAGE_SETKEEPSCREENON = 902;
	protected static final int MESSAGE_SETORIENTATION = 903;
	protected static final int MESSAGE_USECOMPATIBILITYMODE = 904;
	protected static final int MESSAGE_DORESETSETTINGS = 905;
	protected static final int MESSAGE_EXPORTSETTINGS = 906;
	public static final int MESSAGE_CLOSEOPTIONSDIALOG = 907;
	public static final int MESSAGE_SHOWREGEXWARNING = 908;
	protected static final int MESSAGE_INPUT_SELECT_ALL = 909;
	protected static final int MESSAGE_INPUT_COPY = 910;
	protected static final int MESSAGE_INPUT_PASTE = 911;
	protected static final int MESSAGE_INPUT_CURSOR_START = 912;
	protected static final int MESSAGE_INPUT_CURSOR_END = 913;
	protected static final int MESSAGE_SCROLLBACK_SEARCH = 914;
	protected static final int MESSAGE_SCROLLBACK_SEARCH_NAV = 915;
	public static final int MESSAGE_GROW_INPUT_BAR = 916;
	protected static final int MESSAGE_INPUT_CUT = 917;
	protected static final int MESSAGE_INPUT_CURSOR_STEP = 918;
	protected static final int MESSAGE_INPUT_CURSOR_VERTICAL = 919;
	/** Raise the named game window above on-screen buttons while text-selecting. */
	public static final int MESSAGE_TEXTSELECTION_FOCUS = 920;
	/** Restore button_window above game windows after text selection ends. */
	public static final int MESSAGE_TEXTSELECTION_RELEASE = 921;
	protected static final int MESSAGE_MAPPER_UI = 922;
	/** Re-apply IME chrome lift after Window → Keep text still with keyboard? changes. */
	public static final int MESSAGE_REFRESH_IME_LIFT = 923;
	/** Extra text overlays: sync after Connection slot mutate / settings change. */
	protected static final int MESSAGE_EXTRA_TEXT_UI = 924;
	protected boolean settingsDialogRun = false;
	boolean mHideIcons = true;
	
	private BetterEditText mInputBox = null;

	private View mScrollbackSearchBar = null;
	private EditText mScrollbackSearchQuery = null;
	private CheckBox mScrollbackSearchCase = null;
	private TextView mScrollbackSearchCount = null;
	private TextView mScrollbackSearchPreview = null;
	private final java.util.ArrayList<Integer> mScrollbackSearchHits = new java.util.ArrayList<Integer>();
	private int mScrollbackSearchIndex = -1;
	private static final int SCROLLBACK_SEARCH_MAX = 500;
	
	private boolean autoLaunch = true;
	private String overrideHF = "auto";
	private String overrideHFFlip = "auto";
	private String overrideHFPress = "auto";
	
	private ChromeController chrome;
	private MainWindowSettingsTransfer settingsTransfer;
	private MapperOverlayController mapperOverlay;
	private MapperController mapperController;
	private ExtraTextOverlayController extraTextOverlay;
	/** Cached extra-text slots from settings (UI process; Connection holds service copy). */
	private final java.util.ArrayList<ExtraTextSlot> extraTextSlotsCache =
			new java.util.ArrayList<ExtraTextSlot>();
	private boolean extraTextWindowsEnabled = true;
	private boolean extraTextPushMain = true;
	
	private boolean windowShowing = false;
	private RelativeLayout mRootView = null;
	String host;
	int port;
	
	HashMap<String,com.resurrection.blowtorch2.lib.window.Window> windowMap = null;
	
	Handler myhandler = null;
	//boolean servicestarted = false;
	
	IConnectionBinder service = null;
	Processor the_processor = null;
	//GestureDetector gestureDetector = null;
	OnTouchListener gestureListener = null;
	//ByteView screen2 = null;
	CommandKeeper history = null;
	ImageButton test_button = null;
	ImageButton up_button_c = null;
	ImageButton down_button_c = null;
	ImageButton enter_button_c  = null;
	boolean input_controls_expanded = false;
	boolean isBound = false;
	boolean isKeepLast = false; //for keeping last
	boolean historyWidgetKept = false;
	Boolean settingsLoaded = false; //synchronize or try to mitigate failures of writing button data, or failures to read data
	Boolean serviceConnected = false;
	Boolean isResumed = false;
	WindowToken[] mWindows = null;
	//VitalsView vitals = null;
	boolean landscape = false;
	ArrayList<ScriptOptionCallback> scriptCallbacks = new ArrayList<ScriptOptionCallback>();
	private View mFoldoutBar = null;
	private RelativeLayout.LayoutParams mOriginalInputBarLayoutParams = null;
	private RelativeLayout.LayoutParams mOriginalDividerLayoutParams = null;
	
	private class ScriptOptionCallback {
		private String window;
		private String title;
		private String callback;
		private Drawable drawable;
		
		public ScriptOptionCallback() 
		{
			setWindow("");
			setTitle("");
			setCallback("");
			setDrawable(null);
		}
		
		public ScriptOptionCallback(String pWin,String title,String callback,Drawable res) {
			setWindow(pWin);
			setTitle(title);
			setCallback(callback);
			setDrawable(res);
		}
		
		public void setWindow(String window) {
			this.window = window;
		}
		public String getWindow() {
			return window;
		}
		public String getTitle() {
			return title;
		}
		public void setTitle(String title) {
			this.title = title; 
		}
		public void setCallback(String callback) {
			this.callback = callback;
		}
		public String getCallback() {
			return callback;
		}
		public void setDrawable(Drawable drawable) {
			this.drawable = drawable;
		}
		public Drawable getDrawable() {
			return drawable;
		}
	}
	
	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName arg0, IBinder arg1) {
			//Log.e("window","starting onServiceConnected");
			service = IConnectionBinder.Stub.asInterface(arg1); //turn the binder into something useful
			
			//register callback
			try {
				String display = MainWindow.this.getConnectionDisplay();
				String host = MainWindow.this.getConnectionHost();
				int port = MainWindow.this.getConnectionPort();
				service.registerCallback(the_callback, host, port, display);
				// Bind live mapper engine from the Connection (same process).
				MapperController live = MapperController.forDisplay(display);
				if (live != null) {
					MainWindow.this.setMapperController(live);
				}
				// Reopen after process death: socket gone but connection object remains.
				// Skip on first launch — INITIALIZEWINDOWS→initXfer starts the pump;
				// a parallel reconnect was killing that first socket.
				if (windowsInitialized && !service.isConnected() && service.isConnectedTo(display)) {
					service.reconnect(display);
				}
			} catch (RemoteException e) {
				//do nothing here, as there isn't much we can do
			}
			synchronized(serviceConnected) {
				//Log.e("WINDOW","SERVICE CONNECTED, SENDING NOTIFICATION");
				serviceConnected.notify();
				serviceConnected = true;
			}
			MainWindow.this.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					ensureMapperOverlay();
					ensureExtraTextOverlays();
				}
			});
			//finishInitializiation();
			//loadSettings();
			//Log.e("window","ending onServiceConnected()");
		}

		public void onServiceDisconnected(ComponentName arg0) {
			try {
				//Log.e("WINDOW","Attempting to unregister the callback due to unbinding");
				if(service != null) service.unregisterCallback(the_callback);
			} catch (RemoteException e) {
				//do nothing here, as there isn't much we can do
			}
			
			service = null;
			
			synchronized(serviceConnected) {
				serviceConnected.notify();
				serviceConnected = false;
			}
		}
		
	};
	
	//private LayerManager mLayers = null;
	public void onCreate(Bundle icicle) {
		//Log.e("Window","start onCreate");
		//Debug.startMethodTracing("window");
		super.onCreate(icicle);
		windowMap = new HashMap<String,com.resurrection.blowtorch2.lib.window.Window>(0);
		chrome = new ChromeController(this);
		settingsTransfer = new MainWindowSettingsTransfer(this);

		
		//this.requestWindowFeature(Window.FEATURE_ACTION_MODE_OVERLAY);
		//this
		if(ConfigurationLoader.isTestMode(this)) {
			//Thread.setDefaultUncaughtExceptionHandler(new com.resurrection.blowtorch2.lib.crashreport.CrashReporter(this.getApplicationContext()));
		}
		
		chrome.loadHeightsFromPrefs();
		setContentView(R.layout.window_layout);
		assignLegacyChromeIds();
		saveConnectionExtras(getIntent());
		com.resurrection.blowtorch2.lib.service.LuaLibraryHelper.ensureCurrentVersion(this);
		getWindow().getDecorView().setBackgroundColor(Color.TRANSPARENT);
		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			getWindow().setStatusBarColor(Color.TRANSPARENT);
			getWindow().setNavigationBarColor(Color.BLACK);
		}
		androidx.core.view.WindowInsetsControllerCompat insetsController =
				WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
		if (insetsController != null) {
			insetsController.setAppearanceLightStatusBars(false);
		}

		androidx.appcompat.widget.Toolbar myToolbar = (androidx.appcompat.widget.Toolbar) findViewById(R.id.my_toolbar);
		setSupportActionBar(myToolbar);
		if (getSupportActionBar() != null) {
			getSupportActionBar().hide();
		}
		chrome.configureGameplayToolbar(myToolbar);

		chrome.bindGameplayFabControls();

		final View chromeRoot = findViewById(R.id.window_container);
		ViewCompat.setOnApplyWindowInsetsListener(chromeRoot, (view, windowInsets) ->
				chrome.onApplyWindowInsets(view, windowInsets));
		chrome.layoutGameplayChrome((RelativeLayout) findViewById(R.id.window_container));
		chrome.updateMenuChrome();

		getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				// Edge-back may leave Lua buttons visually pressed without ACTION_UP.
				MainWindow.this.windowCall("button_window", "cancelTouchGesture", "");
				MainWindow.this.showBackgroundExitDialog();
			}
		});

		history = new CommandKeeper(75);
		history.load(this, getConnectionDisplay());



        //screen2 = (ByteView)findViewById(R.id.slickview);
        //RelativeLayout l = (RelativeLayout)findViewById(R.id.slickholder);
        //screen2.setParentLayout(l);
        //View fill2 = (View)findViewById(R.id.filler2);
       // fill2.setFocusable(false);
        //fill2.setClickable(false);
        //screen2.setNewTextIndicator(fill2);
        
        //Animation alphaout = new AlphaAnimation(1.0f,0.0f);
        //alphaout.setDuration(100);
       // alphaout.setFillBefore(true);
        //alphaout.setFillAfter(true);
        //fill2.startAnimation(alphaout);
        
        //screen2.setZOrderOnTop(false);
        //screen2.setOnTouchListener(gestureListener);
        
        //vitals = (VitalsView) this.findViewById(R.id.vitals);
        
        //TODO: init lua
        
		
        //health = (Bar)vitals.findViewById(R.id.health);
        //mana = (Bar)vitals.findViewById(R.id.mana);
        //enemy = (Bar)vitals.findViewById(R.id.enemy);
        //health.setColor(0xFF00FF00);
        //mana.setColor(0xFF0000FF);
        /*SharedPreferences vc = this.getSharedPreferences("VITALS_CONF", Context.MODE_PRIVATE);
        boolean run = vc.getBoolean("HASRUN", false);
        if(!run) {
        	SharedPreferences.Editor ed = vc.edit();
        	ed.putBoolean("HASRUN", true);
        	vitals.autoPosition();
        	vitals.savePosition(ed);
        	ed.commit();
        	
        } else {
        	int left = vc.getInt("LEFT", 0);
        	int right = vc.getInt("RIGHT", 150);;
        	int top = vc.getInt("TOP", 0);
        	int bottom = vc.getInt("BOTTOM", 0);
        	vitals.setRect(left,right,top,bottom);
        }*/
        //enemy.setValue(10);
        //mana.setValue(90);
        //health.setValue(10);
		
        mInputBox.setFocusable(true);
        //mInputBox.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			
		//	@Override
		//	public void onFocusChange(View v, boolean hasFocus) {
		//		Log.e("Selection","Setting selection for focus.");
		//		if(hasFocus) {
					
		//			((EditText)v).selectAll();
		//		}
		//	}
		//});
        
//        mInputBox.setOnClickListener(new View.OnClickListener() {
//			
//			@Override
//			public void onClick(View v) {
//				
//				myhandler.sendEmptyMessageDelayed(MESSAGE_RESETINPUTWINDOW, 3000);
//			}
//		});
//        
		mInputBox.setOnKeyListener(new TextView.OnKeyListener() {

			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if(event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP && event.getAction() == KeyEvent.ACTION_UP) {
					applyInputHistoryStep(true);
					return true;
				} else if(event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_UP) {
					applyInputHistoryStep(false);
					return true;
				} else if(event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER && event.getAction() == KeyEvent.ACTION_UP) {
					myhandler.sendEmptyMessage(MainWindow.MESSAGE_PROCESSINPUTWINDOW);
					return true;
				} else if(event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
					return true;
				}
				
				return false;
			}
   
        });

        mInputBox.setDrawingCacheEnabled(true);
        mInputBox.setVisibility(View.VISIBLE);
        mInputBox.setEnabled(true);
        
        mInputBox.setOnBackPressedListener(new BetterEditText.BackPressedListener() {
			
			@Override
			public void onBackPressed() {
				Log.e("log","intercepting back press");
				
				mInputBox.setOnTouchListener(mEditBoxTouchListener);
			}
		});
        //TextView filler = (TextView)findViewById(R.id.filler);
        //filler.setFocusable(false);
        //filler.setClickable(false);
        
        mInputBox.setOnTouchListener(mEditBoxTouchListener);
        
        
        mInputBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
        

        
		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		
				//EditText input_box = (EditText)findViewById(R.id.textinput);
				
				if(actionId == EditorInfo.IME_ACTION_SEND) {
					myhandler.sendEmptyMessage(MainWindow.MESSAGE_PROCESSINPUTWINDOW);
					return true;
				} 
				if(event == null) return true;
				if((((event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER) && event.getAction() == KeyEvent.ACTION_UP))) {
					myhandler.sendEmptyMessage(MainWindow.MESSAGE_PROCESSINPUTWINDOW);
					return true;
				} else if(event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP && event.getAction() == KeyEvent.ACTION_UP) {
					String cmd = history.getNext();
					mInputBox.setText(cmd);
					mInputBox.setSelection(cmd.length());
					if(actionId == EditorInfo.IME_ACTION_DONE) {

						//	return false;
							return true;
							
					} else { return true; }
				} else {
					return true;
				}
				//return false;
			}
		});
        
		
		//assign my handler
		myhandler = new Handler() {
			public void handleMessage(Message msg) {
				//EditText input_box = (EditText)findViewById(R.id.textinput);
				switch(msg.what) {
				case MESSAGE_SHOWREGEXWARNING:
					mShowRegexWarning = (msg.arg1 == 1) ? true : false;
					break;
				case MESSAGE_CLOSEOPTIONSDIALOG:
					closeOptionsDialog();
					break;
				case MESSAGE_EXPORTSETTINGS:
					MainWindow.this.doExportSettings((String)msg.obj);
					break;
				case MESSAGE_DORESETSETTINGS:
					MainWindow.this.doResetSettings();
					break;
				case MESSAGE_USECOMPATIBILITYMODE:
					MainWindow.this.setUseCompatibilityMode((msg.arg1 == 1) ? true : false);
					break;
				case MESSAGE_USESUGGESTIONS:
					MainWindow.this.setUseSuggestions( (msg.arg1 == 1) ? true : false);
					break;
				case MESSAGE_USEFULLSCREENEDITOR:
					MainWindow.this.setUseFullscreenEditor((msg.arg1 == 1) ? true : false);
					break;
				case MESSAGE_SETKEEPSCREENON:
					MainWindow.this.setKeepScreenOn((msg.arg1 == 1) ? true : false);
					break;
				case MESSAGE_SETORIENTATION:
					MainWindow.this.setOrientation(msg.arg1);
					break;
				case MESSAGE_DISPLAYLUAERROR:
					MainWindow.this.dispatchLuaError((String)msg.obj);
					break;
				case MESSAGE_POPMENUSTACK:
					MainWindow.this.popMenuStack();
					break;
				case MESSAGE_PUSHMENUSTACK:
					MainWindow.this.pushMenuStack((String)msg.obj,msg.getData().getString("CALLBACK"));
					break;
				case MESSAGE_SETKEEPLAST:
					MainWindow.this.setKeepLast((msg.arg1 == 1) ? true : false);
					break;
				case MESSAGE_GROW_INPUT_BAR:
					MainWindow.this.applyGrowInputBar(msg.arg1 == 1);
					break;
				case MESSAGE_MAPPER_UI:
					MainWindow.this.handleMapperUiAction(msg.arg1);
					break;
				case MESSAGE_EXTRA_TEXT_UI:
					MainWindow.this.handleExtraTextUiAction(msg.arg1);
					break;
				case MESSAGE_MARKSETTINGSDIRTY:
					MainWindow.this.markSettingsDirty();
					break;
				case MESSAGE_MARKWINDOWSDIRTY:
					MainWindow.this.markWindowsDirty();
					break;
				case MESSAGE_WINDOWBUFFERMAXCHANGED:
					String pluginl = msg.getData().getString("PLUGIN");
					String window = msg.getData().getString("WINDOW");
					int amount = msg.arg1;
					try {
						service.updateWindowBufferMaxValue(pluginl,window,amount);
					} catch (RemoteException e3) {
						// TODO Auto-generated catch block
						e3.printStackTrace();
					}
					break;
				case MESSAGE_PLUGINXCALLS:
					//Map map = (Map)msg.obj;
					String plugin = msg.getData().getString("PLUGIN");
					String function = msg.getData().getString("FUNCTION");
					try {
						service.pluginXcallS(plugin,function,(String)msg.obj);
					} catch (RemoteException e9) {
						// TODO Auto-generated catch block
						e9.printStackTrace();
					}
					break;
				case MESSAGE_ADDOPTIONCALLBACK:
					Bundle datab = msg.getData();
//					String pWin,String title,String callback,Drawable res
					ScriptOptionCallback cb = null;
					if(msg.obj instanceof Drawable) {
						cb = new ScriptOptionCallback(datab.getString("window"),
								datab.getString("title"),
								datab.getString("funcName"),
								(Drawable)msg.obj);
					} else {
						cb = new ScriptOptionCallback(datab.getString("window"),
								datab.getString("title"),
								datab.getString("funcName"),
								null);
					}
					scriptCallbacks.add(0, cb);
					//if(supportsActionBar()) {
						MainWindow.this.invalidateOptionsMenu();
					//}
					break;
				case MESSAGE_INITIALIZEWINDOWS:
					//Log.e("WINDOW","INITIALIZE WINDOWS CALLED");
					//windowsInitialized = false;
					scriptCallbacks.clear();
					//if(supportsActionBar()) {
						MainWindow.this.invalidateOptionsMenu();
					//}
					
					loadSettings();
					MainWindow.this.initiailizeWindows();
					windowsInitialized = true;
					// Defer TCP until mainDisplay has a real cell grid for NAWS.
					mPendingInitialConnect = true;
					myhandler.removeMessages(MESSAGE_RENAWS);
					myhandler.removeMessages(MESSAGE_CONNECT_WHEN_READY);
					myhandler.sendEmptyMessageDelayed(MESSAGE_RENAWS, 80);
					myhandler.sendEmptyMessageDelayed(MESSAGE_CONNECT_WHEN_READY, 200);
					break;
				case MESSAGE_SWITCH:
					//mConnection.
					MainWindow.this.unbindService(mConnection);
					
					//MainWindow.this.bin
					String serviceBindAction = ConfigurationLoader.getConfigurationValue("serviceBindAction", MainWindow.this);
					MainWindow.this.saveConnectionExtras(MainWindow.this.getIntent());
					MainWindow.this.bindService(new Intent(serviceBindAction, null, MainWindow.this.getApplicationContext(), StellarService.class),mConnection, 0);
					//MainWindow.this.bindService(n, conn, flags)
					
					break;
				case MESSAGE_TRIGGERSTR:
					
					break;
				case MESSAGE_TESTLUA:
					//LuaState exist = LuaStateFactory.getExistingState(msg.arg1);
					//exist.LdoString("Note(\"Fooooooo\")");
					break;
				case MESSAGE_LAUNCHURL:
					Pattern urlPattern = Pattern.compile(TextTree.urlFinderString);
					Matcher urlMatcher = urlPattern.matcher((String)msg.obj);
					if(urlMatcher.find()) {
						String url = "";
						if(urlMatcher.group(1) == null || urlMatcher.group(1).equals("")) {
							if(urlMatcher.group(2) == null || !urlMatcher.group(2).equals("")) {
								url = "http://"+urlMatcher.group(2);
							}
						} else {
							url = urlMatcher.group(1);
						}
						if(!url.equals("")) {
							Intent web_help = new Intent(Intent.ACTION_VIEW,Uri.parse(url));
							startActivity(web_help);
						}
					}
					break;
				case MESSAGE_MCP_LAUNCHURL:
					if (msg.obj instanceof String) {
						String mcpUrl = ((String) msg.obj).trim();
						if (mcpUrl.length() > 0) {
							if (!mcpUrl.contains("://")) {
								mcpUrl = "http://" + mcpUrl;
							}
							try {
								startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(mcpUrl)));
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
					break;
				case MESSAGE_MCP_SIMPLEEDIT:
					if (msg.getData() != null) {
						Bundle se = msg.getData();
						com.resurrection.blowtorch2.lib.service.plugin.settings.McpSimpleEditDialog.show(
								MainWindow.this,
								service,
								se.getString("reference"),
								se.getString("title"),
								se.getString("type"),
								se.getString("content"));
					}
					break;
				case MESSAGE_RENAWS:
					reportLiveNawsToService();
					break;
				case MESSAGE_CONNECT_WHEN_READY:
					tryConnectAfterNaws();
					break;
				case MESSAGE_CLEARINPUTWINDOW:
					ClearKeyboard();
					break;
				case MESSAGE_CLOSEINPUTWINDOW:
				case MESSAGE_HIDEKEYBOARD:
					HideKeyboard();
					break;
				case MESSAGE_TEXTSELECTION_FOCUS:
					raiseWindowAboveButtons(msg.obj);
					break;
				case MESSAGE_TEXTSELECTION_RELEASE:
					restoreButtonsAboveWindows();
					break;
				case MESSAGE_REFRESH_IME_LIFT: {
					View chromeRootRefresh = findViewById(R.id.window_container);
					if (chromeRootRefresh != null) {
						ViewCompat.requestApplyInsets(chromeRootRefresh);
					}
					break;
				}
				case MESSAGE_LINEBREAK:
					//screen2.setLineBreaks((Integer)msg.obj);
					break;
				case MESSAGE_SENDBUTTONDATA:
					
					try {
						if (service == null) {
							break;
						}
						String enc = service.getEncoding();
						if (enc == null || enc.length() == 0) {
							enc = "UTF-8";
						}
						service.sendData(((String)msg.obj).getBytes(enc));
						
					} catch (RemoteException e) {
						e.printStackTrace();
					} catch (UnsupportedEncodingException e) {
						
						e.printStackTrace();
					} catch (NullPointerException e) {
						// Service died mid-message (e.g. :stellar crash) — don't kill UI.
						Log.e("BlowTorch", "send button data: no connection", e);
					}
					//screen2.jumpToZero();
					break;
				case MESSAGE_DODISCONNECT:
					//Log.e("WINDOW","SHOW MESSAGE");
					DoDisconnectMessage((String)msg.obj);
					break;
				case MESSAGE_KEYBOARD:
					boolean add = (msg.arg2 > 0) ? true : false;
					boolean popup = (msg.arg1 > 0) ? true : false;
					String text = (String)msg.obj;
					
					if(!add) {
						//reset text
						mInputBox.setText(text);
						mInputBox.setSelection(mInputBox.getText().toString().length());
					} else {
						//append text
						mInputBox.setText(mInputBox.getText().toString() + text);
						mInputBox.setSelection(mInputBox.getText().toString().length());
					}
					
					if(popup) {
						InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
						mgr.showSoftInput(mInputBox, InputMethodManager.SHOW_FORCED);
						mInputBox.setOnTouchListener(null);
					}
				
					break;
				case MESSAGE_DOSCREENMODE:
					boolean fullscreen = false;
					if(msg.arg1 == 1) {
						fullscreen = true;
					}
					boolean needschange = false;
					if(fullscreen && !chrome.isFullScreen()) {
						//switch to fullscreen.
						
							//service.setFullScreen(true);
						chrome.setFullScreen(true);
					    MainWindow.this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
					    MainWindow.this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
					    needschange = true;
						
					}
					
					if(!fullscreen && chrome.isFullScreen()) {
						//switch to non full screen.
						
						//service.setFullScreen(false);
						chrome.setFullScreen(false);
						MainWindow.this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
						MainWindow.this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
					
						//MainWindow.this.findViewById(R.id.window_container).requestLayout();
						needschange = true;
						
					}
					
					if(needschange) {
						refreshGameChrome();
						final View chromeRootRefresh = findViewById(R.id.window_container);
						if (chromeRootRefresh != null) {
							ViewCompat.requestApplyInsets(chromeRootRefresh);
						}
					}
					
					//try {
					//	this.sendMessage(this.obtainMessage(MESSAGE_CHANGEBUTTONSET,service.getLastSelectedSet()));
					//} catch (RemoteException e5) {
					//	throw new RuntimeException(e5);
					//}
					
					
					break;
				case MESSAGE_BELLTOAST:
					Toast belltoast = Toast.makeText(MainWindow.this, "No actual message.", Toast.LENGTH_LONG);
					//t.setView(view);
					
					
					LayoutInflater li = (LayoutInflater) MainWindow.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					View v = li.inflate(R.layout.bell_toast, null);
					//TextView tv = (TextView) v.findViewById(R.id.message);
					//tv.setText(message);
					
					belltoast.setView(v);
					float density = MainWindow.this.getResources().getDisplayMetrics().density;
					belltoast.setGravity(Gravity.TOP|Gravity.RIGHT, (int)(40*density), (int)(30*density));
					belltoast.setDuration(Toast.LENGTH_SHORT);
					belltoast.show();
					break;
				case MESSAGE_LOCKUNDONE:
					//MainWindow.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
					//screen2.forceDraw();
					//screen2.invalidate();
					//Log.e("WINDOW","ATTEMPTING TO FORCE REDRAW THE SCREEN");
					break;
				case MESSAGE_HFPRESS:
					DoHapticFeedbackPress();
					break;
				case MESSAGE_HFFLIP:
					DoHapticFeedbackFlip();
					break;
				case MESSAGE_SHOWDIALOG:
					AlertDialog.Builder dbuilder = new AlertDialog.Builder(MainWindow.this);
					dbuilder.setTitle("ERROR");
					dbuilder.setMessage((String)msg.obj);
					dbuilder.setCancelable(true);
					//dbuilder.set
					dbuilder.setPositiveButton("Close Window", new DialogInterface.OnClickListener() {
						
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
							cleanExit();
							MainWindow.this.finish();
							
						}
					});
					
					AlertDialog dlg = dbuilder.create();
					dlg.show();
					
					break;
				case MESSAGE_SHOWTOAST:
					//Toast t = null;
					//if(msg.arg1 == 1) {
					//	t = Toast.makeText(MainWindow.this, (String)msg.obj, Toast.LENGTH_LONG);
					//} else {
					//	t = Toast.makeText(MainWindow.this, (String)msg.obj, Toast.LENGTH_SHORT);
					//}
					//t.show();

					Snackbar bar = Snackbar.make(findViewById(R.id.window_container), (String)msg.obj,
							Snackbar.LENGTH_INDEFINITE)
							.setAction(android.R.string.ok,new View.OnClickListener() {
								@Override
								public void onClick(View view) {

								}});

					View snackbarView = bar.getView();
					TextView textView = (TextView) snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
					textView.setMaxLines(5);  // show multiple line
					bar.show();
					break;

				case MESSAGE_DOHAPTICFEEDBACK:
					DoHapticFeedback();
					break;
				case MESSAGE_DIRTYEXITNOW:
					//the service via an entered command ".closewindow" or something, to bypass the window asking if you want to close
					dirtyExit();
					MainWindow.this.finish();
					break;
				case MESSAGE_COLORDEBUG:
					//execute color debug.
					//screen2.setColorDebugMode(msg.arg1);
					//TODO: COLOR DEBUG MODE
					break;
				case MESSAGE_XMLERROR:
					//got an xml error, need to display it.
					String xmlerror = com.resurrection.blowtorch2.lib.util.BlowTorchLogger.humanizeError((String)msg.obj);
					AlertDialog.Builder builder = new AlertDialog.Builder(MainWindow.this);
					builder.setPositiveButton("Acknowledge.", new DialogInterface.OnClickListener() {
						
						public void onClick(DialogInterface arg0, int arg1) {
							arg0.dismiss();
						}
					});
					
					builder.setMessage(xmlerror + "\n\nSettings have not been loaded.");
					builder.setTitle("Problem with settings file");
					
					
					//tvtmp.setText("TESTING");
					//builder.setView(tvtmp);
					
					
					AlertDialog error = builder.create();
					error.show();
					TextView tvtmp = (TextView)error.findViewById(android.R.id.message);
					tvtmp.setTypeface(Typeface.MONOSPACE);
					
					break;
				case MESSAGE_SAVEERROR:
					String saveerror = com.resurrection.blowtorch2.lib.util.BlowTorchLogger.humanizeError((String)msg.obj);
					AlertDialog.Builder sbuilder = new AlertDialog.Builder(MainWindow.this);
					sbuilder.setPositiveButton("Dismiss", new DialogInterface.OnClickListener() {
						
						public void onClick(DialogInterface arg0, int arg1) {
							arg0.dismiss();
						}
					});
					
					sbuilder.setMessage(saveerror + "\n\nSettings have not been saved.");
					sbuilder.setTitle("Error Saving Settings");
					
					
					//tvtmp.setText("TESTING");
					//builder.setView(tvtmp);
					
					
					AlertDialog serror = sbuilder.create();
					serror.show();
					TextView stvtmp = (TextView)serror.findViewById(android.R.id.message);
					stvtmp.setTypeface(Typeface.MONOSPACE);
					break;
				case MESSAGE_PLUGINSAVEERROR:
					String pserror = com.resurrection.blowtorch2.lib.util.BlowTorchLogger.humanizeError((String)msg.obj);
					
					AlertDialog.Builder psbuilder = new AlertDialog.Builder(MainWindow.this);
					psbuilder.setPositiveButton("Dismiss", new DialogInterface.OnClickListener() {
						
						public void onClick(DialogInterface arg0, int arg1) {
							arg0.dismiss();
						}
					});
					
					psbuilder.setMessage(pserror + "\n\nPlugin has not been saved.");
					psbuilder.setTitle("Error Saving Plugin");
					
					
					//tvtmp.setText("TESTING");
					//builder.setView(tvtmp);
					
					
					AlertDialog pserrord = psbuilder.create();
					pserrord.show();
					TextView pstvtmp = (TextView)pserrord.findViewById(android.R.id.message);
					pstvtmp.setTypeface(Typeface.MONOSPACE);
					break;
				case MESSAGE_LOADSETTINGS:
					//the service is connected at this point, so the service is alive and settings are loaded
					//Log.e("WINDOW","CALLBACK INDICATED RELOADING OF SETTINGS");
					loadSettings();
					break;
				case MESSAGE_PROCESSINPUTWINDOW:
					
					//input_box.debug(5);
					
					String pdata = mInputBox.getText().toString();
					history.addCommand(pdata);
					history.save(MainWindow.this, getConnectionDisplay());
					Character cr = new Character((char)13);
					Character lf = new Character((char)10);
					String crlf = cr.toString() + lf.toString();
					pdata = pdata.concat(crlf);
					//ByteBuffer buf = ByteBuffer.allocate(pdata.length());
					ByteBuffer buf = null;
					try {
						String enc = service.getEncoding();
						if(enc == null) {
							Log.e("uh oh","null pointer incoming");
						}
						
						buf = ByteBuffer.allocate(pdata.getBytes(service.getEncoding()).length);
					} catch (UnsupportedEncodingException e2) {
						throw new RuntimeException(e2);
					} catch (RemoteException e2) {
						throw new RuntimeException(e2);
					}
					
					
					try {
						buf.put(pdata.getBytes(service.getEncoding()));
					} catch (UnsupportedEncodingException e) {
						
						e.printStackTrace();
					} catch (RemoteException e) {
						
						e.printStackTrace();
					}
				
					buf.rewind();
				
					byte[] buffbytes = buf.array();

					try {
						service.sendData(buffbytes);
					} catch (RemoteException e) {
						e.printStackTrace();
					}
					myhandler.sendEmptyMessage(MainWindow.MESSAGE_RESETINPUTWINDOW);
					break;
				case MESSAGE_RESETINPUTWINDOW:
					//Log.e("WINDOW","Attempting to reset input bar.");
					
					//try {
						if(isKeepLast) {
							mInputBox.setSelection(0, mInputBox.getText().length());
							mInputBox.selectAll();
							historyWidgetKept = true;
						} else {
							mInputBox.clearComposingText();
							mInputBox.setText("");
						}
						
						com.resurrection.blowtorch2.lib.window.Window w = (com.resurrection.blowtorch2.lib.window.Window) MainWindow.this.findViewById(MAIN_WINDOW_ID);
						if(w != null) {
							w.jumpToStart();
						}
						//} catch (RemoteException e1) {
					//	throw new RuntimeException(e1);
					//}
					break;
				case MESSAGE_RAWINC:
					
					//screen2.addBytes((byte[])msg.obj, false);
					
					break;
				case MESSAGE_BUFFINC:
					
					//screen2.addBytes((byte[])msg.obj,true);
					break;
				case MESSAGE_SENDDATAOUT:
					try {
						service.sendData((byte[])msg.obj);
						
					} catch (RemoteException e) {
						e.printStackTrace();
					}
					//screen2.jumpToZero();
					
					
					break;
				case MESSAGE_CLEARALLBUTTONS:
					MainWindow.this.windowCall("button_window", "clearButtons", "");
					break;
				case MESSAGE_CHANGEBUTTONSET:
					if (msg.obj != null && service != null) {
						try {
							service.pluginXcallS("button_window", "loadButtonSet", (String) msg.obj);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					}
					break;
				case MESSAGE_INPUT_SELECT_ALL:
					inputSelectAll();
					break;
				case MESSAGE_INPUT_COPY:
					inputCopy();
					break;
				case MESSAGE_INPUT_PASTE:
					inputPaste();
					break;
				case MESSAGE_INPUT_CURSOR_START:
					inputCursorToStart();
					break;
				case MESSAGE_INPUT_CURSOR_END:
					inputCursorToEnd();
					break;
				case MESSAGE_INPUT_CUT:
					inputCut();
					break;
				case MESSAGE_INPUT_CURSOR_STEP:
					inputCursorStep(msg.arg1);
					break;
				case MESSAGE_INPUT_CURSOR_VERTICAL:
					inputCursorVertical(msg.arg1);
					break;
				case MESSAGE_SCROLLBACK_SEARCH:
					openScrollbackSearchBar(msg.obj == null ? "" : msg.obj.toString());
					break;
				case MESSAGE_SCROLLBACK_SEARCH_NAV:
					scrollbackSearchNav(msg.arg1);
					break;
				default:
					break;
				}
			}

			
		};
		
		//EditText input_box = (EditText)findViewById(R.id.textinput);
		//BetterEditText bet = (BetterEditText)input_box;
		//bet.setListener(mInputBarAnimationListener);
		
		// Legacy blue >>>> foldout (history up/down/enter) removed; Edit strip replaces it.
		test_button = null;
		input_controls_expanded = false;
		mFoldoutBar = null;
		
		//screen2.setDispatcher(myhandler);
		//screen2.setButtonHandler(myhandler);
		//screen2.setInputType(input_box);
		//input_box.bringToFront();
		//icicile is out, prefs are in
		
		synchronized(settingsLoaded) {
		//Log.e("WINDOW","CHECKING SETTINGS FROM: " + PREFS_NAME);
		//SharedPreferences prefs = this.getSharedPreferences(PREFS_NAME,0);
		
		//servicestarted = prefs.getBoolean("CONNECTED",false);
		//finishStart = prefs.getBoolean("FINISHSTART", true);
		
		
		
		//int count = prefs.getInt("BUTTONCOUNT", 0);
		//for(int i = 0;i<count;i++) {
		//	//get button string
		//	String data = prefs.getString("BUTTON"+i, "");
//
		//	Message msg = screen2.buttonaddhandler.obtainMessage(103, data);
		//	screen2.buttonaddhandler.sendMessage(msg);
			
			
		//}
		
		//settingsLoaded.notify();
		//settingsLoaded = true;
		} 
		//if(icicle != null) {
		//	CharSequence seq = icicle.getCharSequence("BUFFER");
		//	if(seq != null) {
		//		screen2.setBuffer((new StringBuffer(seq).toString()));
		//	} else {
		//	}
		//} else {
		//}
		
		if(!isServiceRunning()) {
			String serviceBindAction = ConfigurationLoader.getConfigurationValue("serviceBindAction", this);
			Intent startAction = new Intent(this,StellarService.class);
			startAction.setPackage(this.getPackageName());
			Intent mine = getIntent();

			startAction.putExtra("DISPLAY", getConnectionDisplay());
			startAction.putExtra("PORT", Integer.toString(getConnectionPort()));
			startAction.putExtra("HOST", getConnectionHost());
			
			androidx.core.content.ContextCompat.startForegroundService(this, startAction);
		}
		
		//register screenlock thingie.
		//IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
		//filter.addAction(Intent.ACTION_SCREEN_OFF);
		///filter.addAction(Intent.ACTION_USER_PRESENT);
		//BroadcastReceiver mReceiver = new ScreenState(myhandler);
		//registerReceiver(mReceiver, filter);
		
		
		//give it some time to launch
		synchronized(this) {
			try {
				this.wait(5);
			} catch (InterruptedException e) {
			}
		}
		
		
		mInputBarAnimationListener = null;
		mInputBox.setListener(null);
		mRootView = (RelativeLayout)this.findViewById(R.id.window_container);


		if (getSupportActionBar() != null) {
			getSupportActionBar().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			getSupportActionBar().setDisplayOptions(0, androidx.appcompat.app.ActionBar.DISPLAY_SHOW_HOME);
			getSupportActionBar().setDisplayOptions(0, androidx.appcompat.app.ActionBar.DISPLAY_SHOW_TITLE);
		}
		chrome.configureGameplayToolbar((androidx.appcompat.widget.Toolbar) findViewById(R.id.my_toolbar));



		Button b = new Button(this);
		b.setBackgroundColor(0x00000000);
		//b.setBackgroundColor(0x33FF0000);
		androidx.appcompat.app.ActionBar.LayoutParams tmp2 = new androidx.appcompat.app.ActionBar.LayoutParams(androidx.appcompat.app.ActionBar.LayoutParams.MATCH_PARENT,androidx.appcompat.app.ActionBar.LayoutParams.WRAP_CONTENT);

		LinearLayout.LayoutParams tmp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		b.setLayoutParams(tmp);
		b.setEnabled(false);
		//b.setClickable(false);
		//b.setFocusable(false);
		b.setOnTouchListener(new View.OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				//if(v.isEnabled()) {
				//mRootView.dispatchTouchEvent(event);
				//}

				//if (v.getParent() != mRootView && mRootView != null) {
				//if(v )
				//return mRootView.dispatchTouchEvent(event);
				return false;
				//} else {
				//	return true;
				//}
				//super.onTouchEvent(e);
				//return false;
			}
				//return true; //digest this event.
		});

		//this.getSupportActionBar().setCustomView(b,tmp2);
		//this.getSupportActionBar().setDisplayOptions(androidx.appcompat.app.ActionBar.DISPLAY_SHOW_CUSTOM);
		//this.getSupportActionBar().setDisplayShowCustomEnabled(true);
		//b.setEnabled(true);
		//this.getSupportActionBar().setContent
		//androidx.appcompat.widget.Toolbar parent =(androidx.appcompat.widget.Toolbar) customView.getParent();
		//parent.setContentInsetsAbsolute(0,0);

		//Log.e("Window","End on create");
	}
	
	View.OnTouchListener mEditBoxTouchListener = new View.OnTouchListener() {
		
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			switch(event.getAction()) {
			case MotionEvent.ACTION_UP:
				InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		        imm.showSoftInput(mInputBox, InputMethodManager.SHOW_FORCED);
		        mInputBox.setOnTouchListener(null);
				break;
			}
			return true;
		}
	};
	
	protected void doExportSettings(String path) {
		try {
			service.exportSettingsToPath(path);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void doResetSettings() {
		try {
			service.resetSettings();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void setUseCompatibilityMode(boolean value) {
		
		mInputBox.setBackSpaceBugFix(value);
		setupEditor(fullscreenEditor, value);
		InputMethodManager imm = (InputMethodManager) mInputBox.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.restartInput(mInputBox);
	}

	protected void setUseFullscreenEditor(boolean value) {
		fullscreenEditor = value;
		setupEditor(fullscreenEditor,useSuggestions);
		InputMethodManager imm = (InputMethodManager) mInputBox.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.restartInput(mInputBox);
	}

	protected void setUseSuggestions(boolean value) {
		useSuggestions = value;
		setupEditor(fullscreenEditor,useSuggestions);
		InputMethodManager imm = (InputMethodManager) mInputBox.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.restartInput(mInputBox);
	}

	protected void setKeepScreenOn(boolean value) {
		mInputBox.setKeepScreenOn(value);
	}

	protected void setOrientation(int arg1) {
		doSetOrientiation(arg1);
	}

	protected void dispatchLuaError(String obj) {
		try {
			service.dispatchLuaError(obj);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void dispatchLuaText(String obj) {
		try {
			service.dispatchLuaText(obj);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void popMenuStack() {
		menuStack.pop();
		//if(supportsActionBar()) {
			this.invalidateOptionsMenu();
		//}
		chrome.updateMenuChrome();
	}

	Stack<MenuStackItem> menuStack = new Stack<MenuStackItem>();
	protected void pushMenuStack(String obj,String callback) {
		MenuStackItem tmp = new MenuStackItem(obj,callback);
		menuStack.push(tmp);
		//if(supportsActionBar()) {
			this.invalidateOptionsMenu();
		//}
		chrome.updateMenuChrome();
	}
	
	private class MenuStackItem {
		String window;
		String callback;
		public MenuStackItem(String window,String callback) {
			this.window = window;
			this.callback = callback;
		}
	}


	protected void setKeepLast(boolean b) {
		this.isKeepLast = b;
	}

	protected void markSettingsDirty() {
		loadSettings();
	}

	protected void markWindowsDirty() {
		this.windowsInitialized = false;
	}

	ImageButton downButton = null;
	ImageButton upButton = null;
	ImageButton enterButton = null;
	RelativeLayout.LayoutParams enterOutParams = null;
	RelativeLayout.LayoutParams enterInParams = null;
	RelativeLayout.LayoutParams upOutParams = null;
	RelativeLayout.LayoutParams upInParams = null;
	RelativeLayout.LayoutParams downOutParams = null;
	RelativeLayout.LayoutParams downInParams = null;
	RelativeLayout.LayoutParams toggleOutParams = null;
	RelativeLayout.LayoutParams toggleInParams = null;
	
	
	protected void initVitals() {
		//RelativeLayout layout = (RelativeLayout) MainWindow.this.findViewById(R.id.vitals);
		
		//layout.addView(vitals);
		//layout.invalidate();
		
	}
	
	

	/*boolean showsettingsoptions = false;
	boolean settingsmenuclosed  = true;
	public boolean onPrepareOptionsMenu(Menu menu) {
		
		menu.clear();
		if(!showsettingsoptions) {
			menu.add(0,99,0,"Aliases");
			menu.add(0,100,0,"Triggers");
			menu.add(0,101,0,"Options");
			menu.add(0,102,0,"Button Sets");
		} else {
			menu.add(0,103,0,"Edit Settings");
			menu.add(0,104,0,"Import Settings");
			menu.add(0,105,0,"Export Settings");
		}
		
		return true;
	}*/
	
	private void DoDisconnectMessage(final String str) {
		AlertDialog.Builder err = new AlertDialog.Builder(this);
		err.setTitle("Disconnected");
		err.setMessage("Connection to "+str+ " has closed. Reconnect?");
		err.setPositiveButton("Reconnect", new DialogInterface.OnClickListener() {
			
			public void onClick(DialogInterface dialog, int which) {
				try {
					service.reconnect(str);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
		});
		
		err.setNegativeButton("Close", new DialogInterface.OnClickListener() {
			
			public void onClick(DialogInterface dialog, int which) {
				try {
					//if(service.getConnections().size() > 1) {
						service.closeConnection(str);
						//switch to the next one. service will do this for us.
						
					//} else {
					
						cleanExit();
						dialog.dismiss();
						MainWindow.this.finish();
					//}
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
		
		AlertDialog d = err.create();
		d.show();
	}
	
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		onCreateOptionsMenu(menu);
		return true;
	}
	
	public boolean onCreateOptionsMenu(Menu menu) {
		RelativeLayout rl = (RelativeLayout)this.findViewById(R.id.window_container);
		
		if(menuStack.size() > 0) {
			com.resurrection.blowtorch2.lib.window.Window tmp = (com.resurrection.blowtorch2.lib.window.Window)rl.findViewWithTag(menuStack.peek().window);
			tmp.populateMenu(menu);
			return true;
		}
		
		if(mWindows != null) {
			for(WindowToken w : mWindows) {
				com.resurrection.blowtorch2.lib.window.Window tmp = (com.resurrection.blowtorch2.lib.window.Window)rl.findViewWithTag(w.getName());
				tmp.populateMenu(menu);

			}
		}
		
		/*if(supportsActionBar()) {
			if(mHideIcons) {
				for(int i=0;i<menu.size();i++) {
					MenuItem m = menu.getItem(i);
					m.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
				}
			}
		}*/
		
		
		//MenuItem tmp = null;

			/*for(int i=1000;i<scriptCallbacks.size()+1000;i++) {
				MenuItem hurdur = menu.add(0,i,0,scriptCallbacks.get(i-1000).getTitle());
				if(scriptCallbacks.get(i-1000).getDrawable() != null) {
					hurdur.setIcon(scriptCallbacks.get(i-1000).getDrawable());
					hurdur.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
				} else {
					hurdur.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
				}
			}*/
			
//			Button b = new Button(this);
//			b.setText("YEA YAAAA");
//			LinearLayout.LayoutParams tmp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
//			b.setLayoutParams(tmp);
//			
//			int count = this.getActionBar().getTabCount();
//			for(int i = 0;i<count;i++) {
//				Log.e("menu tab","tab tab:"+this.getActionBar().getTabAt(i).getText());
//			}
			boolean hide = true;
			

			MenuItemCompat.setShowAsAction(menu.add(0,100,100,"Aliases").setIcon(R.drawable.ic_menu_alias),(hide==true) ? MenuItem.SHOW_AS_ACTION_NEVER : MenuItem.SHOW_AS_ACTION_ALWAYS);
		    MenuItemCompat.setShowAsAction(menu.add(0,200,200,"Triggers").setIcon(R.drawable.ic_menu_triggers),(hide==true) ? MenuItem.SHOW_AS_ACTION_NEVER : MenuItem.SHOW_AS_ACTION_ALWAYS);
		    MenuItemCompat.setShowAsAction(menu.add(0,300,300,"Timers").setIcon(R.drawable.ic_menu_timers),(hide==true) ? MenuItem.SHOW_AS_ACTION_NEVER : MenuItem.SHOW_AS_ACTION_ALWAYS);
		    MenuItemCompat.setShowAsAction(menu.add(0,400,400,"Options").setIcon(R.drawable.ic_menu_options),(hide==true) ? MenuItem.SHOW_AS_ACTION_NEVER : MenuItem.SHOW_AS_ACTION_ALWAYS);
			//menu.add(0,102,0,"Button Sets").setIcon(R.drawable.ic_menu_button_sets).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

		//SubMenu sm = menu.addSubMenu(0, 900, 0, "More");
		menu.add(0, 450, 450, "Edit buttons");
		menu.add(0, 500, 500 ,"Speedwalk Directions");
		menu.add(0, 520, 520, "Map");
		menu.add(0, 600, 600, "Plugins");
		menu.add(0, 700, 700, "Reconnect");
		menu.add(0, 800, 800, "Disconnect");
		menu.add(0, 900, 900, "Quit");
		menu.add(0, 1050, 1050, "Search scrollback");
		menu.add(0, 1100,1100,"Reload Settings");
		menu.add(0, 1200,1200,"Reset Settings");
		menu.add(0, 1300,1300,"Export Settings");
		menu.add(0, 1400,1400,"Import Settings");
		// Bottom of expandable menu: Crash report → About → Help
		menu.add(0, 1500, 1500, "Crash report");
		menu.add(0, 1600, 1600, "About");
		menu.add(0, 1700, 1700, "Help");
		// Storage access lives under Options → Miscellaneous.
		//menu.add(0, 1800,1800,"App Settings");

		if (menuStack.size() == 0) {
			suppressActionBarMenuIcons(menu);
		}
		
		return true;
		
	}

	private void suppressActionBarMenuIcons(Menu menu) {
		for (int i = 0; i < menu.size(); i++) {
			menu.getItem(i).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
		}
	}

	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		if (menuStack.size() == 0) {
			return false;
		}
		return super.onMenuOpened(featureId, menu);
	}
	
	//RotatableDialog d = null;
	OptionsDialog optdialog = null;
	
	private void closeOptionsDialog() {
		if(optdialog != null) {
			optdialog.dismiss();
		}
	}

	void showGameplayOptionsMenu(final View anchor) {
		// Editing uses the FAB strip (settings / done / cancel); ⋮ is hidden then.
		if (menuStack.size() > 0) {
			return;
		}
		// IME lift translates the FAB strip; ListPopupWindow used that mid-screen Y
		// as stretch height even after the keyboard was gone. Collapse IME + lift first.
		hideSoftInputForMenu();
		View windowContainer = findViewById(R.id.window_container);
		if (windowContainer instanceof RelativeLayout) {
			chrome.applyImeChromeLift((RelativeLayout) windowContainer, 0);
		}
		if (anchor != null) {
			anchor.post(new Runnable() {
				@Override
				public void run() {
					showGameplayOptionsMenuNow(anchor);
				}
			});
		} else {
			showGameplayOptionsMenuNow(null);
		}
	}

	private void hideSoftInputForMenu() {
		View focus = getCurrentFocus();
		if (focus == null) {
			focus = findViewById(R.id.textinput);
		}
		if (focus != null) {
			android.view.inputmethod.InputMethodManager imm =
					(android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm != null) {
				imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
			}
		}
	}

	private void showGameplayOptionsMenuNow(final View anchor) {
		Context themed = new ContextThemeWrapper(this, R.style.BlowTorch_Game_PopupMenu);
		final androidx.appcompat.view.menu.MenuBuilder menu =
				new androidx.appcompat.view.menu.MenuBuilder(themed);
		onCreateOptionsMenu(menu);

		final ArrayList<MenuItem> visibleItems = new ArrayList<MenuItem>();
		for (int i = 0; i < menu.size(); i++) {
			MenuItem item = menu.getItem(i);
			if (item.isVisible()) {
				visibleItems.add(item);
			}
		}

		CharSequence[] titles = new CharSequence[visibleItems.size()];
		for (int i = 0; i < visibleItems.size(); i++) {
			titles[i] = visibleItems.get(i).getTitle();
		}

		final float density = getResources().getDisplayMetrics().density;
		final androidx.appcompat.widget.ListPopupWindow popup =
				new androidx.appcompat.widget.ListPopupWindow(themed);
		View safeAnchor = anchor != null ? anchor : findViewById(R.id.overflow_menu);
		if (safeAnchor == null) {
			return;
		}
		popup.setAnchorView(safeAnchor);
		popup.setModal(true);
		popup.setAdapter(new ArrayAdapter<CharSequence>(
				themed, android.R.layout.simple_list_item_1, titles));
		popup.setPromptPosition(androidx.appcompat.widget.ListPopupWindow.POSITION_PROMPT_ABOVE);
		popup.setDropDownGravity(Gravity.END);
		popup.setBackgroundDrawable(androidx.core.content.ContextCompat.getDrawable(
				themed, R.drawable.dialog_window_crawler1));

		int[] loc = new int[2];
		safeAnchor.getLocationInWindow(loc);
		// Ignore residual translation on the FAB strip if any.
		View fabStrip = findViewById(R.id.gameplay_fab_strip);
		if (fabStrip != null) {
			loc[1] -= (int) fabStrip.getTranslationY();
		}
		int margin = (int) (4 * density);
		int height = Math.max(loc[1] - margin, (int) (160 * density));
		int screenH = getResources().getDisplayMetrics().heightPixels;
		height = Math.min(height, (int) (screenH * 0.85f));
		popup.setHeight(height);
		popup.setVerticalOffset(-height);
		popup.setOverlapAnchor(true);
		popup.setContentWidth(Math.min(
				getResources().getDisplayMetrics().widthPixels,
				(int) (280 * density)));

		popup.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(android.widget.AdapterView<?> parent, View view,
					int position, long id) {
				popup.dismiss();
				if (position >= 0 && position < visibleItems.size()) {
					MenuItem item = visibleItems.get(position);
					// ListPopupWindow does not call MenuItem.OnMenuItemClickListener.
					// Lua PopulateMenu (Button Sets, etc.) attaches listeners that must
					// run via MenuItemImpl.invoke() before the Java switch.
					if (item instanceof androidx.appcompat.view.menu.MenuItemImpl
							&& ((androidx.appcompat.view.menu.MenuItemImpl) item).invoke()) {
						return;
					}
					MainWindow.this.onOptionsItemSelected(item);
				}
			}
		});
		popup.show();
	}
	
	@SuppressWarnings("unchecked")
	public boolean onOptionsItemSelected(MenuItem item) {
//		if(item.getItemId() >= 1000) {
//			//script callback
//			ScriptOptionCallback callback = scriptCallbacks.get(item.getItemId()-1000);
//			callWindowScript(callback.getWindow(),callback.getCallback());
//			return true;
//		}
		
		switch(item.getItemId()) {
		case 1200:
			//reset
			settingsTransfer.doResetDialog();
			break;
		case 1300:
			SDCardUtils.hasPermissions(this, findViewById(R.id.window_container), RP_EXPORT, new Runnable() {
				@Override
				public void run() {
					settingsTransfer.doExportDialog();
				}
			});
			break;
		case 1400:
			SDCardUtils.hasPermissions(this, findViewById(R.id.window_container), RP_IMPORT, new Runnable() {
				@Override
				public void run() {
					settingsTransfer.doImportDialog(SDCardUtils.hasStoragePermissions(MainWindow.this));
				}
			});
			break;
		case 600:
			BetterPluginSelectionDialog pd = new BetterPluginSelectionDialog(this,service);
			pd.show();
			//PluginDialog pd = new PluginDialog(this,service);
			//pd.show();
			break;
		case 1100:
			try {
				service.reloadSettings();
			} catch (RemoteException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			break;
		case 1500: // Crash report
			new CrashReportDialog(this).show();
			break;
		case 1600: // About
			new AboutDialog(this).show();
			break;
		case 1700: // Help
			new HelpDialog(this).show();
			break;
		case 1050: // Search scrollback
			openScrollbackSearchBar("");
			break;
		case 450: // Edit buttons (same as long-press ⋮)
			windowCall("button_window", "doEdit", "");
			break;
		case 401: // Button Sets (Lua PopulateMenu; backup if invoke() did not run)
			try {
				if (service != null) {
					service.pluginXcallS("button_window", "getButtonSetList", "all");
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			break;
		case 500: //speedwalk config
			BetterSpeedWalkConfigurationDialog swDialog = new BetterSpeedWalkConfigurationDialog(this,service);
			swDialog.show();
			break;
		case 520: // Map overlay — always open (re-runs first-map intro when needed)
			handleMapperUiAction(1);
			break;
		case 900:
			this.cleanExit();
			this.finish();
			break;
		case 800:
			//myhandler.sendEmptyMessage(MESSAGE_DODISCONNECT);
			//service.
			try {
				service.endXfer();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;
		case 700:
			try {
				service.reconnect(service.getConnectedTo());
			} catch (RemoteException e1) {
				e1.printStackTrace();
			}
			break;
		case 300:
			BetterTimerSelectionDialog sel = new BetterTimerSelectionDialog(this,service);
			sel.show();
			break;
		case 100:
			BetterAliasSelectionDialog d = new BetterAliasSelectionDialog(this,service);
			d.setTitle("Edit Aliases:");
			d.show();
			break;
//		case 102:
//			//show the button set selector dialog
//			ButtonSetSelectorDialog buttoneditor = null;
//			try{
//				buttoneditor = new ButtonSetSelectorDialog(this,myhandler,(HashMap<String,Integer>)service.getButtonSetListInfo(),service.getLastSelectedSet(),service);
//				buttoneditor.setTitle("Select Button Set");
//				buttoneditor.show();
//			} catch(RemoteException e) {
//				e.printStackTrace();
//			}
//			break;
//		case 400:
//			
//			MainWindow.this.myhandler.postDelayed(new Runnable() { public void run() { openOptionsMenu();}}, 1);
//			
//			break;
		case 400:
			//enter new routine.
			/*SettingsGroup sg = null;
			try {
				sg = service.getSettings();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			//give up the list to the dialog.
			int size = sg.getOptions().size();*/
			optdialog = new OptionsDialog(this,service,"main");
			optdialog.show();
			//OptionsDialogFragment odf = new OptionsDialogFragment(service,"main",getFragmentManager());
			//odf.show(getFragmentManager(), "dialog");
			
			break;
			
			
			//OLD SETTINGS METHOD.
//			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainWindow.this);
//			SharedPreferences.Editor edit = prefs.edit();
//			
//			try {
//				edit.putBoolean("THROTTLE_BACKGROUND",service.isThrottleBackground());
//				edit.putBoolean("USE_EXTRACTUI", service.getUseExtractUI());
//				edit.putBoolean("PROCESS_PERIOD", service.isProcessPeriod());
//				edit.putBoolean("PROCESS_SEMI", service.isSemiNewline());
//				edit.putBoolean("WIFI_KEEPALIVE", service.isKeepWifiActive());
//				edit.putBoolean("USE_SUGGESTIONS", service.isAttemptSuggestions());
//				edit.putBoolean("BACKSPACE_BUGFIX", service.isBackSpaceBugFix());
//				edit.putBoolean("AUTOLAUNCH_EDITOR", service.isAutoLaunchEditor());
//				edit.putBoolean("DISABLE_COLOR",service.isDisableColor());
//				edit.putString("OVERRIDE_HAPTICFEEDBACK", service.HapticFeedbackMode());
//				edit.putString("HAPTIC_PRESS", service.getHFOnPress());
//				edit.putString("HAPTIC_FLIP", service.getHFOnFlip());
//				edit.putString("ENCODING", service.getEncoding());
//				edit.putInt("BREAK_AMOUNT", service.getBreakAmount());
//				edit.putInt("ORIENTATION", service.getOrientation());
//				edit.putBoolean("WORD_WRAP",service.isWordWrap());
//				edit.putBoolean("REMOVE_EXTRA_COLOR", service.isRemoveExtraColor());
//				edit.putBoolean("DEBUG_TELNET", service.isDebugTelnet());
//				edit.putBoolean("KEEPLAST", service.isKeepLast());
//				edit.putString("FONT_SIZE", Integer.toString((service.getFontSize())));
//				edit.putString("FONT_SIZE_EXTRA", Integer.toString(service.getFontSpaceExtra()));
//				edit.putString("MAX_LINES", Integer.toString(service.getMaxLines()));
//				edit.putString("FONT_NAME", service.getFontName());
//				edit.putBoolean("KEEP_SCREEN_ON",service.isKeepScreenOn());
//				edit.putBoolean("LOCAL_ECHO", service.isLocalEcho());
//				edit.putBoolean("BELL_VIBRATE", service.isVibrateOnBell());
//				edit.putBoolean("BELL_NOTIFY", service.isNotifyOnBell());
//				edit.putBoolean("BELL_DISPLAY", service.isDisplayOnBell());
//				edit.putBoolean("WINDOW_FULLSCREEN",service.isFullScreen());
//				edit.putBoolean("ROUND_BUTTONS",service.isRoundButtons());
//				edit.putBoolean("ECHO_ALIAS_UPDATE", service.isEchoAliasUpdate());
//				edit.putInt("HYPERLINK_COLOR", service.getHyperLinkColor());
//				edit.putString("HYPERLINK_MODE", service.getHyperLinkMode());
//				edit.putBoolean("HYPERLINK_ENABLED", service.isHyperLinkEnabled());
//			} catch (RemoteException e) {
//				throw new RuntimeException(e);
//			}
//			
//			edit.commit();
//			
//			Intent settingintent = new Intent(this,HyperSettingsActivity.class);
//			this.startActivityForResult(settingintent, 0);
//
//			//break;
		case 200:
			BetterTriggerSelectionDialog btsd = new BetterTriggerSelectionDialog(this,service,mShowRegexWarning);
			btsd.show();
			break;
		default:
			break;
		}
		return true;
	}
	
	/** Opens the system folder picker for Options directory StringOptions. */
	public void pickDirectoryForOption() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
				| Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				| Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
				| Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
		try {
			startActivityForResult(intent, REQUEST_PICK_DIRECTORY);
		} catch (Exception e) {
			Toast.makeText(this, "Folder picker unavailable: " + e.getMessage(),
					Toast.LENGTH_LONG).show();
		}
	}

	boolean actionBarTested = false;
	boolean supportsActionBar = false;
	private boolean supportsActionBar() {
		if(actionBarTested == true) {
			return supportsActionBar;
		}
		actionBarTested = true;
		//try {
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
		//	this.getClass().getMethod("getActionBar", null);
			supportsActionBar = true;
			return true;
		}
		//} catch(NoSuchMethodException e) {
			supportsActionBar = false;
			return false;
		//}
		//if(this.getClass().getM)
		//return false;
	}
	
//	private boolean supportsRotation() {
//		try {
//			android.view.Display.class.getMethod("getRotation", null);
//			return true;
//		} catch (NoSuchMethodException e) {
//			return false;
//		}
//		//return false;
//	}


	Handler extporthandler = new Handler() {
		public void handleMessage(Message msg) {
			//so we are kludging out the new button set dialog to just be a "string enterer" dialog.
			//should be a full path /sdcard/something.xml
			String filename = (String)msg.obj;
			try {
				//Log.e("WINDOW","TRYING TO GET SERVICE TO WRITE A FILE FOR ME!");
				service.exportSettingsToPath(filename);
			} catch (RemoteException e) {
				throw new RuntimeException(e);
			}
		}
	};
	
	private void restoreButtonsOnResume() {
		windowCall("button_window", "restoreButtons", "");
	}
	
	private void clearButtonsOnPause() {
		windowCall("button_window", "cancelTouchGesture", "");
		windowCall("button_window", "clearButtons", "");
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		boolean handled = super.dispatchTouchEvent(ev);
		// System edge-back often delivers CANCEL after a button already drew pressed.
		if (ev != null && ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
			windowCall("button_window", "cancelTouchGesture", "");
		}
		return handled;
	}
	
	private void inputSelectAll() {
		if (mInputBox == null) {
			return;
		}
		mInputBox.requestFocus();
		mInputBox.selectAll();
	}
	
	private void inputCursorToStart() {
		if (mInputBox == null) {
			return;
		}
		mInputBox.requestFocus();
		mInputBox.setSelection(0);
	}
	
	private void inputCursorToEnd() {
		if (mInputBox == null) {
			return;
		}
		mInputBox.requestFocus();
		mInputBox.setSelection(mInputBox.getText().length());
	}
	
	private void inputCopy() {
		if (mInputBox == null) {
			return;
		}
		mInputBox.requestFocus();
		mInputBox.onTextContextMenuItem(android.R.id.copy);
	}
	
	private void inputPaste() {
		if (mInputBox == null) {
			return;
		}
		mInputBox.requestFocus();
		mInputBox.onTextContextMenuItem(android.R.id.paste);
	}

	private void inputCut() {
		if (mInputBox == null) {
			return;
		}
		mInputBox.requestFocus();
		android.content.ClipboardManager cm =
				(android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
		int start = Math.max(0, mInputBox.getSelectionStart());
		int endSel = Math.max(start, mInputBox.getSelectionEnd());
		CharSequence selected;
		if (endSel > start) {
			selected = mInputBox.getText().subSequence(start, endSel);
			mInputBox.getText().delete(start, endSel);
		} else {
			selected = mInputBox.getText();
			mInputBox.setText("");
		}
		if (cm != null) {
			cm.setPrimaryClip(android.content.ClipData.newPlainText("input", selected));
		}
	}

	private void inputCursorStep(int delta) {
		if (mInputBox == null || delta == 0) {
			return;
		}
		mInputBox.requestFocus();
		int len = mInputBox.getText().length();
		int start = Math.max(0, Math.min(mInputBox.getSelectionStart(), mInputBox.getSelectionEnd()));
		int end = Math.max(mInputBox.getSelectionStart(), mInputBox.getSelectionEnd());
		int pos = delta < 0 ? start : end;
		pos = Math.max(0, Math.min(len, pos + delta));
		mInputBox.setSelection(pos);
	}

	private void inputCursorVertical(int lineDelta) {
		if (mInputBox == null || lineDelta == 0) {
			return;
		}
		mInputBox.requestFocus();
		android.text.Layout layout = mInputBox.getLayout();
		if (layout != null && layout.getLineCount() > 1) {
			int pos = Math.max(0, Math.min(mInputBox.getSelectionStart(), mInputBox.getSelectionEnd()));
			int line = layout.getLineForOffset(pos);
			int newLine = line + lineDelta;
			if (newLine >= 0 && newLine < layout.getLineCount()) {
				float horiz = layout.getPrimaryHorizontal(pos);
				int newPos = layout.getOffsetForHorizontal(newLine, horiz);
				mInputBox.setSelection(Math.max(0, Math.min(mInputBox.getText().length(), newPos)));
				return;
			}
		}
		// At the top/bottom of the field (or single line): same as keyboard ↑/↓ — command history.
		applyInputHistoryStep(lineDelta < 0);
	}

	/**
	 * Browse sent-command history like hardware DPAD up/down.
	 * @param older true = older command (↑ / stepu), false = newer / clear (↓ / stepd)
	 */
	private void applyInputHistoryStep(boolean older) {
		if (mInputBox == null || history == null) {
			return;
		}
		mInputBox.requestFocus();
		String cmd;
		if (older) {
			cmd = history.getNext();
			if (isKeepLast && historyWidgetKept) {
				cmd = history.getNext();
				historyWidgetKept = false;
			}
		} else {
			cmd = history.getPrev();
		}
		if (cmd == null) {
			cmd = "";
		}
		mInputBox.setText(cmd);
		mInputBox.setSelection(cmd.length());
	}
	
	private void requestNotificationPermissionIfNeeded() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			return;
		}
		View root = findViewById(R.id.window_container);
		PermissionHelper.ensurePermissions(this, root, RP_NOTIFICATIONS,
				PermissionHelper.getNotificationPermissions(),
				R.string.permission_feature_notifications, null);
	}
	
	public void onBackPressed() {
		showBackgroundExitDialog();
	}
	
	public void showBackgroundExitDialog() {
		if(menuStack.size() > 0) {
			MenuStackItem tmp = menuStack.peek();
			RelativeLayout rl = (RelativeLayout)this.findViewById(R.id.window_container);
			
			com.resurrection.blowtorch2.lib.window.Window w = (com.resurrection.blowtorch2.lib.window.Window)rl.findViewWithTag(tmp.window);
			w.callFunction(tmp.callback,null);
				
			
			return;
		}
		
		//show dialog
		AlertDialog.Builder builder = new AlertDialog.Builder(MainWindow.this);
		builder.setMessage("Keep connection running in background?");
		builder.setCancelable(true);
		builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                MainWindow.this.dirtyExit();
		                MainWindow.this.finish();
		           }
		       });
		builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                //dialog.cancel();
		        	   MainWindow.this.cleanExit();
		        	   MainWindow.this.finish();
		           }
		       });
		//AlertDialog alert = builder.create();
		builder.create();
		builder.show();
		//alert.show();
		
		//super.onBackPressed();
	}
	
	int OREINTATION = Configuration.ORIENTATION_LANDSCAPE;
	
	boolean keyboardShowing = false;
	
	public void onConfigurationChanged(Configuration newconfig) {
		//Log.e("WINDOW","CONFIGURATION CHANGING");
		super.onConfigurationChanged(newconfig);
		
		if(service == null) {
			super.onConfigurationChanged(newconfig);
			return;
		}
		
		
		if(newconfig.keyboardHidden == Configuration.KEYBOARDHIDDEN_YES) {
			if(keyboardShowing == true) {
				keyboardShowing = false;
				refreshGameChrome();
				return;
			}
		}
		
		if(newconfig.keyboardHidden == Configuration.KEYBOARDHIDDEN_NO) {
			if(keyboardShowing == false) {
				keyboardShowing = true;
				refreshGameChrome();
				return;
			}
		}
		//Log.e("WINDOW","CONFIGURATION CHANGED");
		//RelativeLayout container = (RelativeLayout)this.findViewById(R.id.window_container);
		//RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams)container.getLayoutParams();
		switch(newconfig.orientation) {
		case Configuration.ORIENTATION_PORTRAIT:
			
		//	container.requestLayout();
			//DoButtonPortraitMode(true);
			//OREINTATION = Configuration.ORIENTATION_PORTRAIT;
			myhandler.sendEmptyMessageDelayed(MESSAGE_HIDEKEYBOARD, 10);
			myhandler.sendEmptyMessageDelayed(MESSAGE_RENAWS, 80);
			
			if(orientation == 1) { //if we are selected as landscape
				newconfig.orientation = Configuration.ORIENTATION_LANDSCAPE;
				//HideKeyboard();
				//myhandler.sendEmptyMessageDelayed(MESSAGE_HIDEKEYBOARD, 1000);
				this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
				
			}
			
			break;
		case Configuration.ORIENTATION_LANDSCAPE:
		//	this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		//	container.requestLayout();
			//DoButtonPortraitMode(false);
			//OREINTATION = Configuration.ORIENTATION_LANDSCAPE;
			myhandler.sendEmptyMessageDelayed(MESSAGE_HIDEKEYBOARD, 10);
			myhandler.sendEmptyMessageDelayed(MESSAGE_RENAWS, 80);
			
			if(orientation == 2) { //if we are selected as landscape
				newconfig.orientation = Configuration.ORIENTATION_PORTRAIT;
				//HideKeyboard();
				//myhandler.sendEmptyMessageDelayed(MESSAGE_HIDEKEYBOARD, 1000);
				this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			}
			
			break;
		}
		
		refreshGameChrome();
		
	}
	
	private void ClearKeyboard() {
		//EditText input_box = (EditText)findViewById(R.id.textinput);
		mInputBox.setText("");
	}
	
	private void HideKeyboard() {
		InputMethodManager imm = (InputMethodManager)this.getSystemService(Context.INPUT_METHOD_SERVICE);
		//EditText input_box = (EditText)findViewById(R.id.textinput);
		imm.hideSoftInputFromWindow(mInputBox.getWindowToken(), 0);
		//Log.e("WINDOW","ATTEMPTING TO HIDE THE KEYBOARD");
		mInputBox.setOnTouchListener(mEditBoxTouchListener);
	}

	/**
	 * Options → Window → Keep text still with keyboard?
	 * When true, {@link ChromeController#applyImeChromeLift} leaves game text untranslated.
	 */
	boolean keepTextStillWithIme() {
		RelativeLayout rl = (RelativeLayout) findViewById(R.id.window_container);
		if (rl != null) {
			View main = rl.findViewWithTag("mainDisplay");
			if (main instanceof com.resurrection.blowtorch2.lib.window.Window) {
				return ((com.resurrection.blowtorch2.lib.window.Window) main).isImeKeepText();
			}
		}
		if (mWindows != null) {
			for (WindowToken tok : mWindows) {
				if (tok == null || !"mainDisplay".equals(tok.getName())) {
					continue;
				}
				Object opt = tok.getSettings().findOptionByKey("ime_keep_text");
				if (opt instanceof com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption) {
					return (Boolean) ((com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption) opt).getValue();
				}
			}
		}
		return false;
	}
	
	private void DoHapticFeedback() {
		if(overrideHF.equals("none")) {
			return;
		}
		
		int aflags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING;
		if(overrideHF.equals("always")) {
			aflags |= HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING;
		}
		
		//BetterEditText input_box = (BetterEditText) this.findViewById(R.id.textinput);
		mInputBox.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, aflags);
	}
	
	private void DoHapticFeedbackPress() {
		if(overrideHFPress.equals("none")) {
			return;
		}
		
		//Log.e("WINDOW","D")
		int aflags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING;
		if(overrideHFPress.equals("always")) {
			aflags |= HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING;
		}
		//Log.e("WINDOW","DISPATCHING HAPTIC FEEDBACK FOR PRESS!");
		//BetterEditText input_box = (BetterEditText) this.findViewById(R.id.textinput);
		mInputBox.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, aflags);
	}
	
	private void DoHapticFeedbackFlip() {
		if(overrideHFFlip.equals("none")) {
			return;
		}
		
		int aflags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING;
		if(overrideHFFlip.equals("always")) {
			aflags |= HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING;
		}
		
		//BetterEditText input_box = (BetterEditText) this.findViewById(R.id.textinput);
		mInputBox.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, aflags);
	}
	
	private boolean isServiceRunning() {
	
		ActivityManager activityManager = (ActivityManager)MainWindow.this.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);
		boolean found = false;
		String serviceProcessName = getApplicationContext().getPackageName() + ConfigurationLoader.getConfigurationValue("serviceProcessName", this);
		for(RunningServiceInfo service : services) {
			if(com.resurrection.blowtorch2.lib.service.StellarService.class.getName().equals(service.service.getClassName())) {
				if(service.process.equals(serviceProcessName)) found = true;
			}
		}
		return found;
	}
	
	private boolean isServiceConnected() {
		try {
			if(service.isConnected()) {
				return true;
			} else {
				return false;
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public void cleanExit() {
		//we want to kill the service when we go.
		cleanupWindows();
		//shut down the service
		
		try {
			String connected = service.getConnectedTo();
			if(connected != null) {
				service.closeConnection(connected);
			}
			
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(isBound) {
			try {
				if(service != null) {
					service.unregisterCallback(the_callback);
				}
			} catch (RemoteException e) {
				//e.printStackTrace();
			}
			
			unbindService(mConnection);
			
			
			
			isBound = false;
			//Log.e("WINDOW","Unbound connection at cleanExit");
		}
		

		
	}
	
	public void dirtyExit() {
		//we dont want to kill the service
		cleanupWindows();
		if(isBound) {
			
			try {
				if(service != null) {
					service.saveSettings();
					service.unregisterCallback(the_callback);
				}
			} catch (RemoteException e) {
				//e.printStackTrace();
			}
			
			unbindService(mConnection);
			isBound = false;
		}
	}
	
	public void onSaveInstanceState(Bundle data) {
		super.onSaveInstanceState(data);
	}
	
	public void onRestoreInstanceState(Bundle data) {
		super.onRestoreInstanceState(data);
	}
	
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == com.resurrection.blowtorch2.lib.responder.notification.NotificationResponderEditor.REQUEST_PICK_SOUND) {
			if (resultCode == RESULT_OK && data != null && data.getData() != null) {
				com.resurrection.blowtorch2.lib.responder.notification.NotificationResponderEditor.onSoundPicked(data.getData());
			}
			return;
		}
		if (requestCode == REQUEST_PICK_DIRECTORY && resultCode == RESULT_OK && data != null) {
			Uri uri = data.getData();
			if (uri != null) {
				String stored = SDCardUtils.persistDirectorySelection(this, uri, data.getFlags());
				if (optdialog != null) {
					optdialog.applyPickedDirectory(stored);
				}
				Toast.makeText(this, "Folder selected.", Toast.LENGTH_SHORT).show();
			}
			return;
		}
		if (settingsTransfer.handleSettingsTransferResult(requestCode, resultCode, data)) {
			return;
		}
		if (resultCode == RESULT_OK) {
			settingsDialogRun = true;
		}
	}
	
	//LuaWindow lwin = null;

	public void onStart() {
		//Log.e("Window","starting onStart");
		super.onStart();
		/*if("com.resurrection.blowtorch2.lib.window.MainWindow.NORMAL_MODE".equals(this.getIntent().getAction())) {
			mode = LAUNCH_MODE.FREE;
		} else if("com.resurrection.blowtorch2.lib.window.MainWindow.TEST_MODE".equals(this.getIntent().getAction())) {
			mode = LAUNCH_MODE.TEST;
		}*/
		//if(supportsActionBar()) {
			//int height = this.getActionBar().getHeight();
			//Log.e("ACFLSAFD","ACTION BAR HEIGHT(fg) IS :" + height);



		//}
		
		
		if(!isServiceRunning()) {
			String serviceBindAction = ConfigurationLoader.getConfigurationValue("serviceBindAction", this);
			Intent intent = new Intent(this, StellarService.class);
			intent.setPackage(this.getPackageName());
			intent.putExtra("DISPLAY", getConnectionDisplay());
			intent.putExtra("HOST", getConnectionHost());
			intent.putExtra("PORT", Integer.toString(getConnectionPort()));
			androidx.core.content.ContextCompat.startForegroundService(this, intent);
		}
		//Log.e("window","ending onStart");
		
	}
	public void onDestroy() {
		
		if(isBound) {
			
			try {
				//Log.e("WINDOW","SAVING BUFFER IN SERVICE");
				
				if(service != null) {
					//service.unregisterCallback(the_callback);
					
					service.unregisterCallback(the_callback);
					service.unregisterCallback(the_callback);
					
					unbindService(mConnection);
					
					//saveSettings();
				} else {
					//uh oh, pausing with a null service, this should not happen
					
				}
			} catch (RemoteException e) {
				e.printStackTrace();
				
			}
			isBound = false;
			
		} else {
			//calling pause without being bound, should not happen
			
		}

		isResumed = false;
		super.onDestroy();
		
		//this.finish();
	
	}
	
	public void onStop() {
		//Log.e("WINDOW","onStop()");
		super.onStop();
	}
	
	public void onPause() {
		//Log.e("WINDOW","onDestroy()");
		//windowShowing = false;
		if(service == null) { super.onPause(); return; };
		clearButtonsOnPause();
		try {
			service.windowShowing(false);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//screen2.pauseDrawing();
		//screen2.clearAllText();
		isResumed = false;
		super.onPause();
	}
	public void onResume() {
		super.onResume();
		//Log.e("window","start onResume()");
		//windowShowing = true;
		
		if(!isBound) {
			saveConnectionExtras(getIntent());
			String serviceBindAction = ConfigurationLoader.getConfigurationValue("serviceBindAction", this);
			this.bindService(new Intent(serviceBindAction,null,this,StellarService.class),mConnection, 0);
			
			isBound = true;
			isResumed = true;

		} else {
			//request buffer.
			try {
				if(service != null) {
					service.windowShowing(true);
					restoreButtonsOnResume();
				}
			} catch (RemoteException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			Intent i = this.getIntent();
			String display = getConnectionDisplay();
			
			try {
				if(service != null && display != null) {
				if(!service.getConnectedTo().equals(display)) {
					Log.e("LOG","ATTEMPTING TO SWITCH TO: " + display);
					//this.cleanupWindows();
					service.switchTo(display);
				}
				}
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
			//try {
				//loadSettings();
				//if(service.hasBuffer()) {
				//	setHyperLinkSettings();
				//	service.requestBuffer();
				//} else {
				//	
				//}
			//} catch (RemoteException e2) {
				
			//	e2.printStackTrace();
			//}
			//myhandler.sendEmptyMessage(MESSAGE_LOADSETTINGS);
		}
		
		//screen2.resumeDrawing();
		//screen2.doDelayedDraw(0);
		isResumed = true;
		requestNotificationPermissionIfNeeded();
		try {
			if (service != null && service.isConnected()) {
				com.resurrection.blowtorch2.lib.util.BatteryOptimizationHelper.maybePrompt(MainWindow.this);
			}
		} catch (RemoteException ignored) {
		}
	}
	
	public void onDestroy(Bundle saveInstance) {
		//Log.e("WINDOW","onDestroy()");
		super.onDestroy();
	}
	
	
	/*private void initLayers() {
		RelativeLayout holder = (RelativeLayout)MainWindow.this.findViewById(R.id.slickholder);
		initializeWindows();
		
		
	}*/
	
	private String mBorderTag = "BorderLayer";
	private void setHyperLinkSettings() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		//boolean enabled = prefs.getBoolean("HYPERLINKS_ENABLED", true);
		//int color = prefs.getInt("HYPERLINK_COLOR", HyperSettings.DEFAULT_HYPERLINK_COLOR);
		String hyperLinkMode = prefs.getString("HYPERLINK_MODE", "highlight_color_bland_only");
		int hyperLinkColor = prefs.getInt("HYPERLINK_COLOR", HyperSettings.DEFAULT_HYPERLINK_COLOR);
		//boolean fitmessage = prefs.getBoolean("FIT_MESSAGE", true);
		boolean hyperLinkEnabled = prefs.getBoolean("HYPERLINK_ENABLED", true);
		
	}
	
	private int orientation;
	private Boolean mShowRegexWarning;
	private void loadSettings() {
		//TODO: NEW LOAD SETTINGS PLACE
		//if(!isResumed || !screen2.loaded()) {
		if(!isResumed) {
			myhandler.sendEmptyMessageDelayed(MESSAGE_LOADSETTINGS, 50);
			return;
		}
		//attemppt to load button sets.
		@SuppressWarnings("unused")
		boolean fontSizeChanged = false;
		//boolean fullscreen_now = false;		
		
		try {
			//calculate80CharFontSize();
			//ByteView.LINK_MODE hyperLinkMode = ByteView.LINK_MODE.HIGHLIGHT_COLOR_ONLY_BLAND;
			
			
			//screen2.setLinkColor(service.getHyperLinkColor());
			
			//screen2.setLinksEnabled(service.isHyperLinkEnabled());
			//if(!service.isConnected()) { return; }
			SettingsGroup group = service.getSettings();
			
			if(group == null) return; //haven't fully loaded yet.
			if(group.getOptions().size() == 0) return;
			boolean fullscreen = (Boolean)((BaseOption)group.findOptionByKey("fullscreen")).getValue();
			if(fullscreen) {
			    MainWindow.this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			    MainWindow.this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
			} else {
				MainWindow.this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
				MainWindow.this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			}
			
			mShowRegexWarning = (Boolean)((BaseOption)(group.findOptionByKey("show_regex_warning"))).getValue();
			
			//

			MainWindow.this.findViewById(R.id.window_container).requestLayout();
			chrome.setFullScreen(fullscreen);
			refreshGameChrome();
			final View chromeRootRefresh = findViewById(R.id.window_container);
			if (chromeRootRefresh != null) {
				ViewCompat.requestApplyInsets(chromeRootRefresh);
			}
			//BetterEditText input_box = (BetterEditText)findViewById(R.id.textinput);
			mInputBox.setBackSpaceBugFix(true);
			
			boolean keep_screen_on = (Boolean)((BaseOption)group.findOptionByKey("screen_on")).getValue();
			
			mInputBox.setKeepScreenOn(keep_screen_on);
		
			
			//screen2.setEncoding(service.getEncoding());
			
			//screen2.setCullExtraneous(service.isRemoveExtraColor());
			
			//int or = MainWindow.this.getRequestedOrientation();
			orientation = (Integer)((BaseOption)group.findOptionByKey("orientation")).getValue();
			doSetOrientiation(orientation);
			
			
			//screen2.setFontSize(service.getFontSize());
			//screen2.setLineSpace(service.getFontSpaceExtra());
			//screen2.setCharacterSizes(service.getFontSize(), service.getFontSpaceExtra());
			//screen2.setMaxLines(service.getMaxLines());
			
			//get the font name 
			//String tmpname = service.getFontName();
			//Typeface font = loadFontFromName(tmpname);
			
			//screen2.setFont(loadFontFromName(tmpname));
			myhandler.sendEmptyMessageDelayed(MESSAGE_RENAWS, 120);
			
			//if(fontSizeChanged) {
			//	screen2.reBreakBuffer();
			//}
			
			boolean useExtractUI = (Boolean)((BaseOption)group.findOptionByKey("fullscreen_editor")).getValue();
			boolean sugtmp = (Boolean)((BaseOption)group.findOptionByKey("use_suggestions")).getValue();
			BaseOption growOpt = (BaseOption) group.findOptionByKey("grow_input_bar");
			if (growOpt != null && growOpt.getValue() instanceof Boolean) {
				mGrowInputBar = (Boolean) growOpt.getValue();
			}
			setupEditor(useExtractUI,sugtmp);
			fullscreenEditor = useExtractUI;
			useSuggestions = sugtmp;
			
			
			isKeepLast = (Boolean)((BaseOption)group.findOptionByKey("keep_last")).getValue();

			BaseOption histOpt = (BaseOption) group.findOptionByKey("input_history_size");
			if (histOpt != null && histOpt.getValue() instanceof Integer) {
				history.setMax((Integer) histOpt.getValue());
			}
			BaseOption sessionLogOpt = (BaseOption) group.findOptionByKey("session_log");
			if (sessionLogOpt != null && sessionLogOpt.getValue() instanceof Boolean) {
				com.resurrection.blowtorch2.lib.util.SessionLogger.setEnabled(
						MainWindow.this, (Boolean) sessionLogOpt.getValue());
			}
			BaseOption sessionLogDirOpt = (BaseOption) group.findOptionByKey("session_log_directory");
			if (sessionLogDirOpt != null && sessionLogDirOpt.getValue() instanceof String) {
				com.resurrection.blowtorch2.lib.util.SessionLogger.setCustomDirectory(
						MainWindow.this, (String) sessionLogDirOpt.getValue());
			}
			
			//orientation = (Integer)((BaseOption)group.findOptionByKey("orientation")).getValue();
			
			//if(service.isKeepLast()) {
			//	isKeepLast = true;
			//} else {
			//	isKeepLast = false;
			//}
			
			//handle auto launch
			///autoLaunch = service.isAutoLaunchEditor();
			//handle disable color
			//if(service.isDisableColor()) {
				//set the slick view debug mode to 3.
				//screen2.setColorDebugMode(3);
			//} else {
				//screen2.setColorDebugMode(0);
			//}
			///handle overridehf.
			//overrideHF = service.HapticFeedbackMode();
			
			//overrideHFPress = service.getHFOnPress();
			//overrideHFFlip = service.getHFOnFlip();
			
			boolean compatibility = (Boolean)((BaseOption)group.findOptionByKey("compatibility_mode")).getValue();
			
			if(compatibility) {
				//Log.e("WINDOW","APPLYING BACK SPACE BUG FIX");
				//BetterEditText tmp_bar = (BetterEditText)input_box;
				mInputBox.setBackSpaceBugFix(true);
			} else {
				//BetterEditText tmp_bar = (BetterEditText)input_box;
				mInputBox.setBackSpaceBugFix(false);
				//Log.e("WINDOW","NOT APPLYING BACK SPACE BUG FIX");
			}
			
			InputMethodManager imm = (InputMethodManager) mInputBox.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.restartInput(mInputBox);
			//imm.
			//im
			//get the rest of the window options that are necessary to function

			refreshExtraTextSlotsFromSettings(group);
			ensureExtraTextOverlays();
			
		} catch (RemoteException e1) {
			throw new RuntimeException(e1);
		}
		
		//initiailizeWindows();
		//int i = R.id.textinput;
	}

	public void doSetOrientiation(int orientation) {
		switch(orientation) {
		case 0:
			MainWindow.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
			break;
		case 1:
			MainWindow.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			break;
		case 2:
			MainWindow.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			break;
		default:
			break;
		}
	}

	private boolean fullscreenEditor = false;
	private boolean useSuggestions = false;
	public void setupEditor(boolean useExtractUI,boolean useSuggestions) {
		this.fullscreenEditor = useExtractUI;
		this.useSuggestions = useSuggestions;

		if (useExtractUI) {
			int current = mInputBox.getImeOptions();
			int wanted = current & (0xFFFFFFFF ^ EditorInfo.IME_FLAG_NO_EXTRACT_UI);
			wanted = wanted | EditorInfo.IME_ACTION_SEND;
			mInputBox.setImeOptions(wanted);
			mInputBox.setUseFullScreen(true);
		} else {
			int current = mInputBox.getImeOptions();
			int wanted = current | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_SEND;
			mInputBox.setImeOptions(wanted);
			mInputBox.setUseFullScreen(false);
		}
		// setInputType must include MULTI_LINE when growing — otherwise Android forces single-line.
		applyGrowInputBar(mGrowInputBar);
	}

	/** Apply Options → Input → Grow Input Bar? / {@code .wrap} to the input field. */
	private void applyGrowInputBar(boolean grow) {
		mGrowInputBar = grow;
		if (mInputBox == null) {
			return;
		}
		int type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE;
		if (!useSuggestions) {
			type |= InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
		}
		if (grow) {
			type |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
			mInputBox.setInputType(type);
			mInputBox.setSingleLine(false);
			mInputBox.setMaxLines(INPUT_GROW_MAX_LINES);
			mInputBox.setHorizontallyScrolling(false);
		} else {
			mInputBox.setInputType(type);
			mInputBox.setMaxLines(1);
			mInputBox.setSingleLine(true);
			mInputBox.setHorizontallyScrolling(true);
		}
		scheduleInputActionLayoutRefresh();
		refreshGameChrome();
	}
	
	private Typeface loadFontFromName(String name) {
		Typeface font = Typeface.MONOSPACE;
		//Log.e("WINDOW","FONT SELECTION IS:" + tmpname);
		if(name.contains("/")) {
			//string is a path
			if(name.contains(Environment.getExternalStorageDirectory().getPath())) {
				
				String sdstate = Environment.getExternalStorageState();
				if(Environment.MEDIA_MOUNTED.equals(sdstate) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(sdstate)) {
					font = Typeface.createFromFile(name);
				} else {
					font = Typeface.MONOSPACE;
				}
				
			} else {
				//path is a system path
				font = Typeface.createFromFile(name);
			}
			
		} else {
			if(name.equals("monospace")) {
				font = Typeface.MONOSPACE;
			} else if(name.equals("sans serif")) {
				font = Typeface.SANS_SERIF;
			} else if (name.equals("default")) {
				font = Typeface.DEFAULT;
			}
		}
		return font;
	}
	
	private BetterEditText.AnimationEndListener mInputBarAnimationListener = null;


	private IConnectionBinderCallback.Stub the_callback = new IConnectionBinderCallback.Stub() {

		public void dataIncoming(byte[] seq) throws RemoteException {
			Message msg = myhandler.obtainMessage(MESSAGE_PROCESS);
			Bundle b = new Bundle();
			b.putByteArray("SEQ", seq);
			msg.setData(b);
			myhandler.sendMessage(msg);
		}
		
		public boolean isWindowShowing() {
			return windowShowing;
		}

		public void processedDataIncoming(CharSequence seq) throws RemoteException {
			Message msg = myhandler.obtainMessage(MESSAGE_PROCESSED); 
			Bundle b = new Bundle();
			b.putCharSequence("SEQ", seq);
			msg.setData(b);
			myhandler.sendMessage(msg);
		}

		public void htmlDataIncoming(String html) throws RemoteException {
			Message msg = myhandler.obtainMessage(MESSAGE_HTMLINC);
			Bundle b = new Bundle();
			b.putString("HTML", html);
			msg.setData(b);
			myhandler.sendMessage(msg);
			
		}

		public void rawDataIncoming(byte[] raw) throws RemoteException {
			
			Message msg = myhandler.obtainMessage(MESSAGE_RAWINC,raw);
			//Log.e("WINDOW","RECIEVING RAW");
			myhandler.sendMessage(msg);
			
		}
		
		public void rawBufferIncoming(byte[] rawbuf) throws RemoteException {
			Message msg = myhandler.obtainMessage(MESSAGE_BUFFINC,rawbuf);
			myhandler.sendMessage(msg);
			//Log.e("WINDOW","RECEIVING BUFFER: " + rawbuf.length());
		}

		public void loadSettings() throws RemoteException {
			myhandler.sendEmptyMessage(MESSAGE_LOADSETTINGS);
		}

		public void displayXMLError(String error) throws RemoteException {
			Message xmlerror = myhandler.obtainMessage(MESSAGE_XMLERROR);
			xmlerror.obj = error;
			myhandler.sendMessage(xmlerror);
			
		}

		@Override
		public void displaySaveError(String error) throws RemoteException {
			Message saveerror = myhandler.obtainMessage(MESSAGE_SAVEERROR);
			saveerror.obj = error;
			myhandler.sendMessage(saveerror);
		}
		
		@Override
		public void displayPluginSaveError(String plugin, String error) throws RemoteException {
			Message saveerror = myhandler.obtainMessage(MESSAGE_SAVEERROR);
			saveerror.obj = error;
			saveerror.getData().putString("PLUGIN", plugin);
			myhandler.sendMessage(saveerror);
		}

		public void executeColorDebug(int arg) throws RemoteException {
			Message colordebug = myhandler.obtainMessage(MESSAGE_COLORDEBUG);
			colordebug.arg1 = arg;
			myhandler.sendMessage(colordebug);
		}

		public void invokeDirtyExit() throws RemoteException {
			myhandler.sendEmptyMessage(MESSAGE_DIRTYEXITNOW);
			
		}

		public void showMessage(String message,boolean longtime) throws RemoteException {
			Message showmessage = myhandler.obtainMessage(MESSAGE_SHOWTOAST);
			showmessage.obj = message;
			if(longtime) {
				showmessage.arg1 = 1;
			} else {
				showmessage.arg1 = 0;
			}
			myhandler.sendMessage(showmessage);
			
		}

		public void showDialog(String message) throws RemoteException {
			Message showdlg = myhandler.obtainMessage(MESSAGE_SHOWDIALOG);
			showdlg.obj = message;
			myhandler.sendMessage(showdlg);
		}

		public void launchUrl(String url) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_MCP_LAUNCHURL, url));
		}

		public void showMcpSimpleEdit(String reference, String title, String type, String content)
				throws RemoteException {
			Message msg = myhandler.obtainMessage(MESSAGE_MCP_SIMPLEEDIT);
			Bundle b = msg.getData();
			b.putString("reference", reference);
			b.putString("title", title);
			b.putString("type", type);
			b.putString("content", content);
			msg.setData(b);
			myhandler.sendMessage(msg);
		}

		public void doVisualBell() throws RemoteException {
			myhandler.sendEmptyMessage(MESSAGE_BELLTOAST);
		}

		public void setScreenMode(boolean fullscreen) throws RemoteException {
			Message doScreenMode = myhandler.obtainMessage(MESSAGE_DOSCREENMODE);
			if(fullscreen) {
				doScreenMode.arg1 = 1;
			} else {
				doScreenMode.arg1 = 0;
			}
			
			myhandler.sendMessage(doScreenMode);
		}

		public void showKeyBoard(String txt,boolean popup,boolean add,boolean flush,boolean clear,boolean close) throws RemoteException {
			if(flush) {
				myhandler.sendEmptyMessage(MESSAGE_PROCESSINPUTWINDOW);
				return;
			}
			
			if(clear) {
				myhandler.sendEmptyMessage(MESSAGE_CLEARINPUTWINDOW);
				return;
			}
			
			if(close) {
				myhandler.sendEmptyMessage(MESSAGE_CLOSEINPUTWINDOW);
				return;
			}
			int p = (popup) ? 1 : 0;
			int a = (add) ? 1 : 0;
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_KEYBOARD,p,a,txt));
		}
		
		public void inputBarSelectAll() throws RemoteException {
			myhandler.sendEmptyMessage(MESSAGE_INPUT_SELECT_ALL);
		}
		
		public void inputBarCopy() throws RemoteException {
			myhandler.sendEmptyMessage(MESSAGE_INPUT_COPY);
		}
		
		public void inputBarPaste() throws RemoteException {
			myhandler.sendEmptyMessage(MESSAGE_INPUT_PASTE);
		}

		public void inputBarCut() throws RemoteException {
			myhandler.sendEmptyMessage(MESSAGE_INPUT_CUT);
		}
		
		public void inputBarCursorToStart() throws RemoteException {
			myhandler.sendEmptyMessage(MESSAGE_INPUT_CURSOR_START);
		}
		
		public void inputBarCursorToEnd() throws RemoteException {
			myhandler.sendEmptyMessage(MESSAGE_INPUT_CURSOR_END);
		}

		public void inputBarCursorStep(int delta) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_INPUT_CURSOR_STEP, delta, 0));
		}

		public void inputBarCursorVertical(int delta) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_INPUT_CURSOR_VERTICAL, delta, 0));
		}

		public void openScrollbackSearch(String query) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_SCROLLBACK_SEARCH, query));
		}

		public void scrollbackSearchNav(int nav) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_SCROLLBACK_SEARCH_NAV, nav, 0));
		}

		public void doDisconnectNotice(String display) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_DODISCONNECT, display));
			
		}

		public void doLineBreak(int i) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_LINEBREAK,new Integer(i)));
		}

		public void reloadButtons(String setName) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_CHANGEBUTTONSET,setName));
		}
		
		public void clearAllButtons() throws RemoteException {
			myhandler.sendEmptyMessage(MESSAGE_CLEARALLBUTTONS);
		}
		
		public void updateMaxVitals(int hp, int mana, int moves) {
			Message msg = myhandler.obtainMessage(MESSAGE_MAXVITALS);
			Bundle b = msg.getData();
			b.putInt("maxhp", hp);
			b.putInt("maxmp", mana);
			b.putInt("maxmoves", moves);
			msg.setData(b);
			myhandler.sendMessage(msg);
		}
		public void updateVitals(int hp, int mana, int moves) {
			/*Message msg = myhandler.obtainMessage(MESSAGE_VITALS);
			Bundle b = msg.getData();
			b.putInt("hp", hp);
			b.putInt("mp", mana);
			b.putInt("moves", moves);
			msg.setData(b);
			myhandler.sendMessage(msg);*/
		}

		public void updateEnemy(int hp) throws RemoteException {
			//myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_ENEMYHP,hp,0));
		}

		public void updateVitals2(int hp, int mp, int maxhp, int maxmana,
				int enemy) throws RemoteException {
			/*Message m = myhandler.obtainMessage(MESSAGE_VITALS2);
			//if(this.get(list.data.MESSget(i))
			Bundle b = m.getData();
			b.putInt("HP", hp);
			b.putInt("MP", mp);
			b.putInt("MAXHP", maxhp);
			b.putInt("MAXMANA", maxmana);
			b.putInt("ENEMY",enemy);
			
			m.setData(b);
			myhandler.sendMessage(m);*/
		}
		
		public void luaOmg(int stateIndex) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_TESTLUA,stateIndex,0));
		}

		public void updateTriggerDebugString(String str) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_TRIGGERSTR,str));
		}

		public int getPort() throws RemoteException {
			return MainWindow.this.getConnectionPort();
		}

		public String getHost() throws RemoteException {
			return MainWindow.this.getConnectionHost();
		}

		public String getDisplay() throws RemoteException {
			return MainWindow.this.getConnectionDisplay();
		}

		public void switchTo(String connection) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_SWITCH,connection));
		}

		public void reloadBuffer() throws RemoteException {
			myhandler.sendEmptyMessage(MESSAGE_RELOADBUFFER);
		}

		public void loadWindowSettings() throws RemoteException {
			myhandler.sendEmptyMessage(MESSAGE_INITIALIZEWINDOWS);
		}
		
		public void markWindowsDirty() throws RemoteException {
			myhandler.sendEmptyMessage(MESSAGE_MARKWINDOWSDIRTY);
		}

		@Override
		public void markSettingsDirty() throws RemoteException {
			myhandler.sendEmptyMessage(MESSAGE_MARKSETTINGSDIRTY);
		}

		@Override
		public void setKeepLast(boolean keep) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_SETKEEPLAST, (keep==true) ? 1 : 0, 0));
		}

		@Override
		public void setGrowInputBar(boolean grow) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_GROW_INPUT_BAR, grow ? 1 : 0, 0));
		}

		@Override
		public void setOrientation(int orientation) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_SETORIENTATION,orientation,0));
		}

		@Override
		public void setKeepScreenOn(boolean value) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_SETKEEPSCREENON, (value == true) ? 1 : 0,0));
		}

		@Override
		public void setUseFullscreenEditor(boolean value)
				throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_USEFULLSCREENEDITOR,(value == true) ? 1 :0,0));
		}

		@Override
		public void setUseSuggestions(boolean value) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_USESUGGESTIONS,(value==true) ? 1 : 0,0));
		}

		@Override
		public void setCompatibilityMode(boolean value) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_USECOMPATIBILITYMODE,(value==true) ? 1 : 0,0));
		}

		@Override
		public void setRegexWarning(boolean value) throws RemoteException {
			// TODO Auto-generated method stub
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_SHOWREGEXWARNING,(value==true) ? 1 : 0,0));
		}

		@Override
		public void mapperUi(int action) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_MAPPER_UI, action, 0));
		}

		@Override
		public void extraTextUi(int action) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_EXTRA_TEXT_UI, action, 0));
		}
	};
	
	boolean windowsInitialized = false;
	//boolean landscape = false
	public void initiailizeWindows() {
		//ask the service for all the current windows for the connection.
		//List<WindowToken> windows =  null;
		//make windows in the order they are given, attach the callback and the view to the layout root.
		//mRootLayout.removeAllViews();
		
		//cleanupWindows();
		if(windowsInitialized == true) {
			//Log.e("WINDOW","ALREADY LOADED WINDOWS");
			return;
		}
		
		if(mWindows != null) {
			//Log.e("LUAWINDOW","cleaning up windows.");
		}
		cleanupWindows();
		
		Display display = ((WindowManager)this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		//if(supportsRotation()) {
		//	landscape = (display.getRotation() == Surface.ROTATION_180 || display.getRotation() == Surface.ROTATION_90) ? true : false;
		//} else {
			
		//}
		landscape = isLandscape();
		windowsInitialized = true;
		String displayname = "";
		
		try {
			mWindows = service.getWindowTokens();
			//displayname = service.getConnectedTo();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(mWindows == null || mWindows.length == 0) {
			//Exception e = new Exception("No windows to show.");
			//throw new RuntimeException(e);
			synchronized(this) {
				while(mWindows == null || mWindows.length == 0) {
					try {
						this.wait(300);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					boolean done = false;
					//while(!done) {
						try {
							mWindows = service.getWindowTokens();
							if(mWindows != null) {
								if(mWindows.length > 0) {
									done = true;
								}
							}
						} catch (RemoteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					//}
				}
			}
		} 
			ApplicationInfo ai = null;
			try {
				ai = this.getPackageManager().getApplicationInfo(this.getPackageName(), PackageManager.GET_META_DATA);
			} catch (NameNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			String dataDir = ai.dataDir;

			try {
				refreshExtraTextSlotsFromSettings(service.getSettings());
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		
			//initialize windows.
			for(Object x : mWindows) {
				WindowToken w = null;
				if(x instanceof WindowToken) {
					w = (WindowToken)x;
				} else {
					//err.
				}
				if(MAIN_WINDOW_ID == -1) {
					MAIN_WINDOW_ID = w.getId();
				}
				initWindow(w,dataDir);
				
				
			}
			RelativeLayout rl = (RelativeLayout)this.findViewById(R.id.window_container);
			
			for(Object x : mWindows) {
				WindowToken w = null;
				if(x instanceof WindowToken) {
					w = (WindowToken)x;
				}
				if (isExtraTextSlotWindow(w.getName())) {
					continue; // hosted by ExtraTextOverlayController, not RelativeLayout tags
				}
				com.resurrection.blowtorch2.lib.window.Window v = (com.resurrection.blowtorch2.lib.window.Window)rl.findViewWithTag(w.getName());
				if(v != null) {
					v.runScriptOnCreate();
				} else {
					Log.e("WARNING","Could not load window: "+w.getName());
				}
			}
			//mRootLayout.requestLayout();
		//}
			
		//if(supportsActionBar()) {
			this.invalidateOptionsMenu();
		//}

		androidx.appcompat.widget.Toolbar myToolbar = (androidx.appcompat.widget.Toolbar) findViewById(R.id.my_toolbar);
		if (myToolbar != null) {
			chrome.configureGameplayToolbar(myToolbar);
		}
			chrome.layoutGameplayChrome((RelativeLayout) findViewById(R.id.window_container));
		chrome.updateMenuChrome();
		ensureMapperOverlay();
		ensureExtraTextOverlays();
		//Debug.stopMethodTracing();
	}

	/** Bind mapper UI under window_container (chrome ⋮ stays above). */
	private void ensureMapperOverlay() {
		MapperController live = MapperController.forDisplay(getConnectionDisplay());
		if (live != null) {
			mapperController = live;
		}
		if (mapperOverlay == null) {
			mapperOverlay = new MapperOverlayController(new MapperOverlayController.Host() {
				@Override
				public MainWindow getMainWindow() {
					return MainWindow.this;
				}

				@Override
				public String getRecentBufferText(int maxLines) {
					return MainWindow.this.getRecentMainBufferText(maxLines);
				}

				@Override
				public void sendMapperPath(java.util.List<String> commands) {
					if (commands == null || commands.isEmpty() || service == null) {
						return;
					}
					try {
						String enc = service.getEncoding();
						// One sendData per step — Connection does not split on CR/LF.
						for (int i = 0; i < commands.size(); i++) {
							String step = commands.get(i);
							if (step == null) {
								continue;
							}
							step = step.trim();
							if (step.length() == 0) {
								continue;
							}
							service.sendData(step.getBytes(enc));
						}
					} catch (RemoteException e) {
						e.printStackTrace();
					} catch (java.io.UnsupportedEncodingException e) {
						e.printStackTrace();
					}
				}

				@Override
				public void insertMapperText(String text) {
					if (text == null || text.length() == 0 || mInputBox == null) {
						return;
					}
					final String insert = text;
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							if (mInputBox == null) {
								return;
							}
							String cur = mInputBox.getText() != null
									? mInputBox.getText().toString() : "";
							if (cur.length() > 0 && !cur.endsWith(" ")
									&& !cur.endsWith(";") && !insert.startsWith(";")) {
								mInputBox.setText(cur + ";" + insert);
							} else {
								mInputBox.setText(cur + insert);
							}
							mInputBox.setSelection(mInputBox.getText().toString().length());
						}
					});
				}

				@Override
				public String fetchMapperSnapshotJson() {
					if (service == null) {
						return "";
					}
					try {
						String json = service.getMapperSnapshotJson();
						return json != null ? json : "";
					} catch (RemoteException e) {
						return "";
					}
				}

				@Override
				public void runMapCommand(String args) {
					if (service == null) {
						return;
					}
					String line = ".map";
					if (args != null && args.trim().length() > 0) {
						line = ".map " + args.trim();
					}
					try {
						service.sendData(line.getBytes(service.getEncoding()));
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		}
		if (mapperController != null) {
			mapperOverlay.bind(mapperController);
		}
	}

	ChromeController getChromeController() {
		return chrome;
	}

	private boolean isExtraTextSlotWindow(String name) {
		if (name == null) {
			return false;
		}
		if (extraTextOverlay != null && extraTextOverlay.managesWindowName(name)) {
			return true;
		}
		for (int i = 0; i < extraTextSlotsCache.size(); i++) {
			ExtraTextSlot s = extraTextSlotsCache.get(i);
			if (s != null && name.equals(s.getName())) {
				return true;
			}
		}
		return false;
	}

	private void refreshExtraTextSlotsFromSettings(SettingsGroup group) {
		extraTextSlotsCache.clear();
		extraTextWindowsEnabled = true;
		extraTextPushMain = true;
		if (group == null) {
			return;
		}
		Object enabledOpt = group.findOptionByKey(ExtraTextSlotsStore.ENABLED_KEY);
		if (enabledOpt instanceof BaseOption) {
			Object v = ((BaseOption) enabledOpt).getValue();
			if (v instanceof Boolean) {
				extraTextWindowsEnabled = (Boolean) v;
			}
		}
		Object pushOpt = group.findOptionByKey(ExtraTextSlotsStore.PUSH_MAIN_KEY);
		if (pushOpt instanceof BaseOption) {
			Object v = ((BaseOption) pushOpt).getValue();
			if (v instanceof Boolean) {
				extraTextPushMain = (Boolean) v;
			}
		}
		Object raw = group.findOptionByKey(ExtraTextSlotsStore.SETTING_KEY);
		String json = "[]";
		if (raw instanceof BaseOption) {
			Object v = ((BaseOption) raw).getValue();
			if (v != null) {
				json = v.toString();
			}
		}
		java.util.ArrayList<ExtraTextSlot> parsed = ExtraTextSlotsStore.parse(json);
		ExtraTextSlotsStore.validate(parsed);
		extraTextSlotsCache.addAll(parsed);
	}

	/** Bind extra-text overlays under window_container (chrome ⋮ stays above). */
	private void ensureExtraTextOverlays() {
		if (extraTextOverlay == null) {
			extraTextOverlay = new ExtraTextOverlayController(new ExtraTextOverlayController.Host() {
				@Override
				public MainWindow getMainWindow() {
					return MainWindow.this;
				}

				@Override
				public java.util.List<ExtraTextSlot> getExtraTextSlots() {
					if (!extraTextWindowsEnabled) {
						return java.util.Collections.emptyList();
					}
					java.util.ArrayList<ExtraTextSlot> out =
							new java.util.ArrayList<ExtraTextSlot>();
					for (int i = 0; i < extraTextSlotsCache.size(); i++) {
						ExtraTextSlot s = extraTextSlotsCache.get(i);
						if (s != null) {
							out.add(s.copy());
						}
					}
					return out;
				}

				@Override
				public WindowToken findWindowToken(String name) {
					if (name == null || service == null) {
						return null;
					}
					try {
						WindowToken[] tokens = service.getWindowTokens();
						if (tokens == null) {
							return null;
						}
						for (int i = 0; i < tokens.length; i++) {
							WindowToken t = tokens[i];
							if (t != null && name.equals(t.getName())) {
								return t;
							}
						}
					} catch (RemoteException e) {
						e.printStackTrace();
					}
					return null;
				}

				@Override
				public void registerWindowCallback(WindowToken token,
						com.resurrection.blowtorch2.lib.window.Window window) {
					if (token == null || window == null || service == null) {
						return;
					}
					try {
						service.registerWindowCallback(token.getDisplayHost(),
								token.getName(), window.getCallback());
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}

				@Override
				public void unregisterWindowCallback(WindowToken token,
						com.resurrection.blowtorch2.lib.window.Window window) {
					if (window == null || service == null) {
						return;
					}
					try {
						String key = token != null ? token.getDisplayHost() : "";
						Object tag = window.getTag();
						if ((key == null || key.length() == 0) && tag != null) {
							key = tag.toString();
						}
						service.unregisterWindowCallback(key, window.getCallback());
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}

				@Override
				public String getDataDir() {
					try {
						ApplicationInfo ai = getPackageManager().getApplicationInfo(
								getPackageName(), PackageManager.GET_META_DATA);
						return ai != null ? ai.dataDir : "";
					} catch (NameNotFoundException e) {
						return "";
					}
				}

				@Override
				public android.os.Handler getUiHandler() {
					return myhandler;
				}

				@Override
				public void persistExtraTextSlots(java.util.List<ExtraTextSlot> slots) {
					extraTextSlotsCache.clear();
					if (slots != null) {
						java.util.ArrayList<ExtraTextSlot> next =
								new java.util.ArrayList<ExtraTextSlot>();
						for (int i = 0; i < slots.size(); i++) {
							ExtraTextSlot s = slots.get(i);
							if (s != null) {
								next.add(s.copy());
							}
						}
						ExtraTextSlotsStore.validate(next);
						extraTextSlotsCache.addAll(next);
					}
					String json = ExtraTextSlotsStore.toJson(extraTextSlotsCache);
					if (service == null) {
						return;
					}
					try {
						service.updateStringSetting(ExtraTextSlotsStore.SETTING_KEY, json);
						service.saveSettings();
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}

				@Override
				public boolean isPushMainTextEnabled() {
					return extraTextWindowsEnabled && extraTextPushMain;
				}

				@Override
				public void setMainTextDrawerPushAnchors(View topDrawer, View bottomDrawer) {
					MainWindow.this.applyMainTextDrawerPushAnchors(topDrawer, bottomDrawer);
				}
			});
		}
		if (extraTextSlotsCache.isEmpty() && service != null) {
			try {
				refreshExtraTextSlotsFromSettings(service.getSettings());
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		extraTextOverlay.sync();
	}

	/**
	 * Anchor mainDisplay below a top drawer and/or above a bottom drawer so game
	 * text is not covered. Leaves {@code button_window} full-bleed. Floating
	 * overlays never use this. Null anchors restore the default chrome layout
	 * (ALIGN_PARENT_TOP + ABOVE input bar).
	 */
	private void applyMainTextDrawerPushAnchors(View topDrawer, View bottomDrawer) {
		RelativeLayout rl = (RelativeLayout) findViewById(R.id.window_container);
		if (rl == null) {
			return;
		}
		View main = rl.findViewWithTag("mainDisplay");
		if (!(main instanceof com.resurrection.blowtorch2.lib.window.Window)) {
			return;
		}
		ViewGroup.LayoutParams glp = main.getLayoutParams();
		if (!(glp instanceof RelativeLayout.LayoutParams)) {
			return;
		}
		RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) glp;
		int wantBelow = (topDrawer != null) ? topDrawer.getId() : 0;
		int wantAbove = (bottomDrawer != null)
				? bottomDrawer.getId()
				: ChromeController.LEGACY_INPUT_BAR_ID;
		if (wantBelow != 0 && wantBelow == View.NO_ID) {
			return;
		}
		if (bottomDrawer != null && wantAbove == View.NO_ID) {
			return;
		}

		int curBelow = lp.getRule(RelativeLayout.BELOW);
		int curAbove = lp.getRule(RelativeLayout.ABOVE);
		boolean wantAlignTop = (topDrawer == null);
		boolean curAlignTop = lp.getRule(RelativeLayout.ALIGN_PARENT_TOP)
				== RelativeLayout.TRUE;
		boolean changed = curBelow != wantBelow
				|| curAbove != wantAbove
				|| curAlignTop != wantAlignTop
				|| lp.topMargin != 0
				|| lp.bottomMargin != 0;

		// Clear the broken margin-based approach from earlier builds.
		lp.topMargin = 0;
		lp.bottomMargin = 0;

		if (topDrawer != null) {
			lp.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
			lp.addRule(RelativeLayout.BELOW, wantBelow);
		} else {
			lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
			lp.removeRule(RelativeLayout.BELOW);
		}

		lp.addRule(RelativeLayout.ABOVE, wantAbove);

		if (changed) {
			main.setLayoutParams(lp);
			main.requestLayout();
			main.invalidate();
		}
	}

	private void handleExtraTextUiAction(int action) {
		// action is typically Connection.MESSAGE_EXTRA_TEXT_CHANGED (48).
		try {
			if (service != null) {
				refreshExtraTextSlotsFromSettings(service.getSettings());
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		ensureExtraTextOverlays();
	}

	private void handleMapperUiAction(int action) {
		ensureMapperOverlay();
		if (mapperOverlay == null) {
			return;
		}
		switch (action) {
		case 1:
			mapperOverlay.open();
			break;
		case 2:
			mapperOverlay.close();
			break;
		case 3:
			mapperOverlay.toggle();
			break;
		case 4:
			mapperOverlay.pullSnapshotFromService();
			break;
		case 5: {
			// Zoom request from service (.map zoom …)
			String zoomArg = null;
			try {
				if (service != null) {
					zoomArg = service.takeMapperUiArg();
				}
			} catch (Exception ignored) {
			}
			if (zoomArg != null && zoomArg.length() > 0) {
				mapperOverlay.zoomMap(zoomArg);
			}
			break;
		}
		default:
			break;
		}
	}

	private String getRecentMainBufferText(int maxLines) {
		com.resurrection.blowtorch2.lib.window.Window w = null;
		if (windowMap != null) {
			w = windowMap.get("mainDisplay");
		}
		if (w == null) {
			RelativeLayout rl = (RelativeLayout) findViewById(R.id.window_container);
			if (rl != null) {
				w = (com.resurrection.blowtorch2.lib.window.Window) rl.findViewWithTag("mainDisplay");
			}
		}
		if (w == null || w.getBuffer() == null) {
			return "";
		}
		try {
			String plain = w.getBuffer().dumpPlainText();
			if (plain == null || plain.length() == 0) {
				return "";
			}
			String[] lines = plain.split("\n", -1);
			int start = Math.max(0, lines.length - Math.max(1, maxLines));
			StringBuilder sb = new StringBuilder();
			for (int i = start; i < lines.length; i++) {
				if (sb.length() > 0) {
					sb.append('\n');
				}
				sb.append(lines[i]);
			}
			return sb.toString();
		} catch (Throwable t) {
			return "";
		}
	}

	/** Used when engine/service exposes a real MapperController. */
	public void setMapperController(MapperController controller) {
		mapperController = controller;
		ensureMapperOverlay();
		if (mapperOverlay != null) {
			mapperOverlay.bind(mapperController);
		}
	}

	public MapperController getMapperController() {
		return mapperController;
	}
	
	private void initWindow(WindowToken w,String dataDir) {
		if (w != null && isExtraTextSlotWindow(w.getName())) {
			// Overlay owns geometry — ExtraTextOverlayController hosts the Window view.
			return;
		}
		RelativeLayout rl = (RelativeLayout)this.findViewById(R.id.window_container);
		View v = rl.findViewWithTag(w.getName());
		if(v == null) {
			long start = System.currentTimeMillis();
			//if(w.getName().equals("chats")) {
			//	long sfs = System.currentTimeMillis();
			//	sfs = sfs + 10;
			//}
			Log.e("WINDOW","INITIALIZING WINDOW: " + w.getName() + " id:" + w.getId());
			com.resurrection.blowtorch2.lib.window.Window tmp = new com.resurrection.blowtorch2.lib.window.Window(dataDir,this,w.getName(),w.getPluginName(),myhandler,w.getSettings(),this);
			
			//determine the appropriate layout group to load.
			int screenLayout = this.getResources().getConfiguration().screenLayout;
			//boolean landscape = ((screenLayout & Configuration.SCREENLAYOUT_LONG_MASK) == Configuration.SCREENLAYOUT_LONG_NO) ? true : false;
			
			//int longyesno = screenLayout & m
			int screenSize = screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
			
			
			//RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT,RelativeLayout.LayoutParams.FILL_PARENT);
			//p.addRule(RelativeLayout.ALIGN_PARENT_TOP);
			//p.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) w.getLayout(screenSize, landscape);
			if(params == null) {
				params = (android.widget.RelativeLayout.LayoutParams) w.getLayout(screenSize, !landscape);
			}
			if (params != null) {
				params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
				chrome.anchorWindowAboveInputChrome(params, w.getName());
			}

			tmp.setLayoutParams(params);
			tmp.setTag(w.getName());
			tmp.setVisibility(View.GONE);
			tmp.setId(w.getId());
			rl.addView(tmp);
			
			windowMap.put(w.getName(), tmp);
			
			//RelativeLayout holder = new AnimatedRelativeLayout(mContext,tmp,this);
			//RelativeLayout.LayoutParams holderParams = new RelativeLayout.LayoutParams(w.getX()+w.getWidth(),w.getY()+w.getHeight());
			//holderParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
			//holderParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			//holder.setPadding(w.getX(), w.getY(), 0, 0);
			//holder.setId(w.getId());
			//holder.setLayoutParams();
			
			//holder.addView(tmp);
			
			try {
				String body = service.getScript(w.getPluginName(),w.getScriptName());
				//TODO: this needs to be much harderly error checked.
				tmp.loadScript(body);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			tmp.setBufferText(w.isBufferText());
			try {
				service.registerWindowCallback(w.getDisplayHost(),w.getName(),tmp.getCallback());
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			if(w.getBuffer() != null) {
				//tmp.addBytes(w.getBuffer().dumpToBytes(false), true);
				tmp.setBuffer(w.getBuffer());
			//construct border.
			}
			
			//attempt to construct a good-ly relative layout to hold the window and any children 
			
			tmp.setVisibility(View.VISIBLE);
			
			long dur = System.currentTimeMillis() - start;
			Log.e("WINDOW","Init Window ("+w.getName()+"): took:" + dur + " millis.");
		}
	}
	
	
	public void cleanupWindows() {
		if (extraTextOverlay != null) {
			extraTextOverlay.detach();
		}
		RelativeLayout rl = (RelativeLayout)this.findViewById(R.id.window_container);
		if(mWindows == null) return;
		for(Object x : mWindows) {
			if(x instanceof WindowToken) {
				WindowToken w = (WindowToken)x;
				if (isExtraTextSlotWindow(w.getName())) {
					continue;
				}
				View tmp = rl.findViewWithTag(w.getName());
				
				if(tmp instanceof com.resurrection.blowtorch2.lib.window.Window) {
					try {
						service.unregisterWindowCallback(w.getDisplayHost(), ((com.resurrection.blowtorch2.lib.window.Window)tmp).getCallback());
					} catch (RemoteException e) {
						e.printStackTrace();
					}
					Log.e("WINDOW","SHUTTING DOWN WINDOW " + w.getName());
					((com.resurrection.blowtorch2.lib.window.Window)tmp).shutdown();
					
					
				}
			}
		}
		
		for(Object x : mWindows) {
			if(x instanceof WindowToken) {
				WindowToken w = (WindowToken)x;
				if (isExtraTextSlotWindow(w.getName())) {
					continue;
				}
				View tmp = rl.findViewWithTag(w.getName());
				if(tmp instanceof com.resurrection.blowtorch2.lib.window.Window) {
					((com.resurrection.blowtorch2.lib.window.Window)tmp).closeLua();
					windowMap.remove(w.getName());
					rl.removeView(tmp);
					tmp = null;
					Log.e("WINDOW","SHUT DOWN WINDOW" + w.getName() + "SUCCESS");
				}
			}
		}
		
		int counter = 0;
		/*while(rl.getChildCount() > 2) {
			View v = rl.getChildAt(rl.getChildCount()-1);
			if(v.getId() != 10) {
				rl.removeView(v);
			} else {
				rl.removeViewAt(rl.getChildCount()-2);
			}
		}*/
		chrome.layoutGameplayChrome(rl);
	}
	
	private void saveConnectionExtras(Intent intent) {
		if (intent == null) {
			return;
		}
		SharedPreferences.Editor edit = getSharedPreferences("CONNECT_TO", Context.MODE_PRIVATE).edit();
		String display = intent.getStringExtra("DISPLAY");
		String host = intent.getStringExtra("HOST");
		String port = intent.getStringExtra("PORT");
		if (display != null) {
			edit.putString("CONNECT_TO", display);
		}
		if (host != null) {
			edit.putString("CONNECT_HOST", host);
		}
		if (port != null) {
			edit.putString("CONNECT_PORT", port);
		}
		edit.apply();
	}

	private String getConnectionDisplay() {
		Intent intent = getIntent();
		if (intent != null) {
			String display = intent.getStringExtra("DISPLAY");
			if (display != null && !display.isEmpty()) {
				return display;
			}
		}
		SharedPreferences prefs = getSharedPreferences("CONNECT_TO", Context.MODE_PRIVATE);
		String saved = prefs.getString("CONNECT_TO", null);
		if (saved != null && !saved.isEmpty()) {
			return saved;
		}
		return "Connection";
	}

	private String getConnectionHost() {
		Intent intent = getIntent();
		if (intent != null) {
			String host = intent.getStringExtra("HOST");
			if (host != null && !host.isEmpty()) {
				return host;
			}
		}
		SharedPreferences prefs = getSharedPreferences("CONNECT_TO", Context.MODE_PRIVATE);
		String saved = prefs.getString("CONNECT_HOST", null);
		if (saved != null && !saved.isEmpty()) {
			return saved;
		}
		return "localhost";
	}

	private int getConnectionPort() {
		Intent intent = getIntent();
		if (intent != null) {
			String port = intent.getStringExtra("PORT");
			if (port != null && !port.isEmpty()) {
				try {
					return Integer.parseInt(port);
				} catch (NumberFormatException ignored) {
				}
			}
		}
		SharedPreferences prefs = getSharedPreferences("CONNECT_TO", Context.MODE_PRIVATE);
		String saved = prefs.getString("CONNECT_PORT", null);
		if (saved != null && !saved.isEmpty()) {
			try {
				return Integer.parseInt(saved);
			} catch (NumberFormatException ignored) {
			}
		}
		return 23;
	}

	private void assignLegacyChromeIds() {
		View divider = findViewById(R.id.divider);
		if (divider != null) {
			// Divider is usually the top edge inside inputbar; only remap RelativeLayout siblings.
			if (divider.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
				RelativeLayout.LayoutParams dividerparams =
						(RelativeLayout.LayoutParams) divider.getLayoutParams();
				dividerparams.addRule(RelativeLayout.ABOVE, ChromeController.LEGACY_INPUT_BAR_ID);
				mOriginalDividerLayoutParams = new RelativeLayout.LayoutParams(dividerparams);
			}
			divider.setId(ChromeController.LEGACY_DIVIDER_ID);
		}

		View v = findViewById(R.id.textinput);
		mInputBox = (BetterEditText) v;
		mInputBox.setId(ChromeController.LEGACY_TEXT_INPUT_ID);

		View inputBar = findViewById(R.id.inputbar);
		mOriginalInputBarLayoutParams = new RelativeLayout.LayoutParams(inputBar.getLayoutParams());
		inputBar.setBackgroundColor(0xFF0A0A0A);
		inputBar.setId(ChromeController.LEGACY_INPUT_BAR_ID);

		setupInputEditStrip();
		setupScrollbackSearchBar();
	}

	private static final String PREFS_INPUT_EDIT = "INPUT_EDIT_STRIP";
	private static final String KEY_EDIT_EXPANDED = "expanded";
	/** Soft-wrap grow limit for the input bar (~thumb-reachable height on phones). */
	private static final int INPUT_GROW_MAX_LINES = 7;
	/** When true, input bar grows with multiline text (default / .wrap on). */
	private boolean mGrowInputBar = true;
	private ViewGroup mInputActionButtons = null;
	private Button mInputSendButton = null;
	/** True while Edit/Send are stacked vertically. */
	private boolean mActionsStacked = false;
	/** Side-by-side Edit/Send widths (thumb-friendly); column = sum + gap. */
	private int mActionEditWidthPx = 0;
	private int mActionSendWidthPx = 0;

	private void setupInputEditStrip() {
		final View tools = findViewById(R.id.input_edit_tools);
		stretchEditToolButtons(tools);
		final Button toggle = (Button) findViewById(R.id.input_edit_toggle);
		mInputActionButtons = (ViewGroup) findViewById(R.id.input_action_buttons);
		mInputSendButton = (Button) findViewById(R.id.input_send);
		View select = findViewById(R.id.input_btn_select);
		View cut = findViewById(R.id.input_btn_cut);
		View copy = findViewById(R.id.input_btn_copy);
		View paste = findViewById(R.id.input_btn_paste);
		View home = findViewById(R.id.input_btn_home);
		View left = findViewById(R.id.input_btn_left);
		View right = findViewById(R.id.input_btn_right);
		View end = findViewById(R.id.input_btn_end);
		View up = findViewById(R.id.input_btn_up);
		View down = findViewById(R.id.input_btn_down);

		if (mInputSendButton != null) {
			mInputSendButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					myhandler.sendEmptyMessage(MESSAGE_PROCESSINPUTWINDOW);
				}
			});
		}

		if (mInputBox != null) {
			// Defer until after layout so lineCount is accurate at the soft-wrap edge.
			mInputBox.addTextChangedListener(new android.text.TextWatcher() {
				@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
				@Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
				@Override
				public void afterTextChanged(android.text.Editable s) {
					scheduleInputActionLayoutRefresh();
				}
			});
		}

		ensureInputActionColumn();

		if (mInputBox == null || tools == null || toggle == null || select == null) {
			ensureInputActionColumn();
			return;
		}

		boolean expanded = getSharedPreferences(PREFS_INPUT_EDIT, Context.MODE_PRIVATE)
				.getBoolean(KEY_EDIT_EXPANDED, false);
		applyInputEditExpanded(tools, toggle, expanded);

		toggle.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				boolean nowExpanded = tools.getVisibility() != View.VISIBLE;
				applyInputEditExpanded(tools, toggle, nowExpanded);
				getSharedPreferences(PREFS_INPUT_EDIT, Context.MODE_PRIVATE)
						.edit()
						.putBoolean(KEY_EDIT_EXPANDED, nowExpanded)
						.apply();
				refreshGameChrome();
			}
		});

		select.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mInputBox.requestFocus();
				mInputBox.selectAll();
			}
		});
		cut.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inputCut();
				Toast.makeText(MainWindow.this, "Cut", Toast.LENGTH_SHORT).show();
			}
		});
		copy.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
				int start = Math.max(0, mInputBox.getSelectionStart());
				int endSel = Math.max(start, mInputBox.getSelectionEnd());
				CharSequence selected = endSel > start
						? mInputBox.getText().subSequence(start, endSel)
						: mInputBox.getText();
				cm.setPrimaryClip(android.content.ClipData.newPlainText("input", selected));
				Toast.makeText(MainWindow.this, "Copied", Toast.LENGTH_SHORT).show();
			}
		});
		paste.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inputPaste();
			}
		});
		home.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inputCursorToStart();
			}
		});
		left.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inputCursorStep(-1);
			}
		});
		right.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inputCursorStep(1);
			}
		});
		if (up != null) {
			up.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					inputCursorVertical(-1);
				}
			});
		}
		if (down != null) {
			down.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					inputCursorVertical(1);
				}
			});
		}
		end.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inputCursorToEnd();
			}
		});
		refreshInputActionLayout();
	}

	private void scheduleInputActionLayoutRefresh() {
		if (mInputBox == null) {
			return;
		}
		mInputBox.removeCallbacks(mRefreshInputActionLayoutRunnable);
		// Double-post: first afterTextChanged, then after the EditText reflows.
		mInputBox.post(mRefreshInputActionLayoutRunnable);
	}

	private final Runnable mRefreshInputActionLayoutRunnable = new Runnable() {
		@Override
		public void run() {
			if (mInputBox == null) {
				return;
			}
			mInputBox.post(new Runnable() {
				@Override
				public void run() {
					ensureInputActionColumn();
				}
			});
		}
	};

	/**
	 * Single-line: Edit | Send side-by-side (thumb-wide targets).
	 * Multi-line: Edit above Send (bottom-right).
	 * <p>
	 * Action column always reserves the side-by-side footprint so stacking never
	 * changes EditText wrap width (avoids flicker at the 1↔2 line boundary).
	 * EditText stays {@code WRAP_CONTENT} so the bar can grow up to
	 * {@link #INPUT_GROW_MAX_LINES} — {@code MATCH_PARENT} capped growth at ~2 button heights.
	 */
	private void ensureInputActionColumn() {
		if (!(mInputActionButtons instanceof LinearLayout) || mInputSendButton == null || mInputBox == null) {
			return;
		}
		LinearLayout actions = (LinearLayout) mInputActionButtons;
		Button edit = (Button) findViewById(R.id.input_edit_toggle);
		if (edit == null) {
			return;
		}

		float density = getResources().getDisplayMetrics().density;
		int gap = Math.max(1, (int) (2 * density + 0.5f));
		// Slightly wider than content — hard to hit with a thumb otherwise.
		int minTouch = Math.max(1, (int) (64 * density + 0.5f));

		if (mActionEditWidthPx <= 0 || mActionSendWidthPx <= 0) {
			// Measure true wrap sizes (ignore any previously forced width).
			int unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
			ViewGroup.LayoutParams elp0 = edit.getLayoutParams();
			ViewGroup.LayoutParams slp0 = mInputSendButton.getLayoutParams();
			int oldEw = elp0 != null ? elp0.width : ViewGroup.LayoutParams.WRAP_CONTENT;
			int oldSw = slp0 != null ? slp0.width : ViewGroup.LayoutParams.WRAP_CONTENT;
			if (elp0 != null) {
				elp0.width = ViewGroup.LayoutParams.WRAP_CONTENT;
			}
			if (slp0 != null) {
				slp0.width = ViewGroup.LayoutParams.WRAP_CONTENT;
			}
			edit.measure(unspec, unspec);
			mInputSendButton.measure(unspec, unspec);
			mActionEditWidthPx = Math.max(edit.getMeasuredWidth(), minTouch);
			mActionSendWidthPx = Math.max(mInputSendButton.getMeasuredWidth(), minTouch);
			if (elp0 != null) {
				elp0.width = oldEw;
			}
			if (slp0 != null) {
				slp0.width = oldSw;
			}
		}
		final int editNat = mActionEditWidthPx;
		final int sendNat = mActionSendWidthPx;
		final int colW = editNat + gap + sendNat;

		boolean stack = isInputMultiline();
		int wantedOri = stack ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL;

		boolean changed = false;
		if (actions.getOrientation() != wantedOri) {
			actions.setOrientation(wantedOri);
			changed = true;
		}
		if (mActionsStacked != stack) {
			mActionsStacked = stack;
			changed = true;
		}
		actions.setGravity(stack
				? (android.view.Gravity.BOTTOM | android.view.Gravity.END)
				: (android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END));

		ViewGroup.LayoutParams rawAlp = actions.getLayoutParams();
		if (rawAlp instanceof LinearLayout.LayoutParams) {
			LinearLayout.LayoutParams alp = (LinearLayout.LayoutParams) rawAlp;
			if (alp.width != colW
					|| alp.height != LinearLayout.LayoutParams.WRAP_CONTENT
					|| alp.gravity != android.view.Gravity.BOTTOM) {
				alp.width = colW;
				alp.height = LinearLayout.LayoutParams.WRAP_CONTENT;
				alp.gravity = android.view.Gravity.BOTTOM;
				actions.setLayoutParams(alp);
				changed = true;
			}
		}

		// Side-by-side: each keeps its own width (never average — that crushed Send).
		// Stacked: both stretch to the reserved column width.
		int editW = stack ? colW : editNat;
		int sendW = stack ? colW : sendNat;
		// Same height as the single-line input row (textinput minHeight 28dip).
		int btnH = Math.max(1, (int) (28 * density + 0.5f));
		LinearLayout.LayoutParams editLp = (edit.getLayoutParams() instanceof LinearLayout.LayoutParams)
				? (LinearLayout.LayoutParams) edit.getLayoutParams()
				: new LinearLayout.LayoutParams(editW, btnH);
		LinearLayout.LayoutParams sendLp = (mInputSendButton.getLayoutParams() instanceof LinearLayout.LayoutParams)
				? (LinearLayout.LayoutParams) mInputSendButton.getLayoutParams()
				: new LinearLayout.LayoutParams(sendW, btnH);
		editLp.width = editW;
		sendLp.width = sendW;
		editLp.height = btnH;
		sendLp.height = btnH;
		editLp.weight = 0f;
		sendLp.weight = 0f;
		if (stack) {
			editLp.setMargins(0, 0, 0, gap);
			sendLp.setMargins(0, 0, 0, 0);
		} else {
			editLp.setMargins(0, 0, gap, 0);
			sendLp.setMargins(0, 0, 0, 0);
		}
		edit.setLayoutParams(editLp);
		mInputSendButton.setLayoutParams(sendLp);
		edit.setMinWidth(editW);
		mInputSendButton.setMinWidth(sendW);
		edit.setMaxLines(1);
		mInputSendButton.setMaxLines(1);
		edit.setVisibility(View.VISIBLE);
		mInputSendButton.setVisibility(View.VISIBLE);

		// WRAP_CONTENT so soft-wrap can grow the row up to maxLines (not button-stack height).
		ViewGroup.LayoutParams etLp = mInputBox.getLayoutParams();
		if (etLp instanceof LinearLayout.LayoutParams) {
			LinearLayout.LayoutParams elp = (LinearLayout.LayoutParams) etLp;
			if (elp.height != LinearLayout.LayoutParams.WRAP_CONTENT
					|| elp.gravity != android.view.Gravity.BOTTOM) {
				elp.height = LinearLayout.LayoutParams.WRAP_CONTENT;
				elp.gravity = android.view.Gravity.BOTTOM;
				mInputBox.setLayoutParams(elp);
				changed = true;
			}
		}

		ViewParent parent = actions.getParent();
		if (parent instanceof LinearLayout) {
			((LinearLayout) parent).setGravity(android.view.Gravity.BOTTOM);
		}

		if (changed) {
			actions.requestLayout();
		}
		RelativeLayout rl = (RelativeLayout) findViewById(R.id.window_container);
		chrome.bringGameplayChromeToFront(rl);
	}

	private void refreshInputActionLayout() {
		ensureInputActionColumn();
	}

	private boolean isInputMultiline() {
		if (mInputBox == null) {
			return false;
		}
		// Prefer line count — height alone is noisy during IME / font metrics.
		try {
			if (mInputBox.getLineCount() > 1) {
				return true;
			}
		} catch (Exception ignored) {
		}
		CharSequence text = mInputBox.getText();
		if (text != null) {
			for (int i = 0; i < text.length(); i++) {
				if (text.charAt(i) == '\n') {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isInputBarTall() {
		return isInputMultiline();
	}

	private void applyInputEditExpanded(View tools, Button toggle, boolean expanded) {
		tools.setVisibility(expanded ? View.VISIBLE : View.GONE);
		toggle.setText(expanded ? "Hide" : "Edit");
	}

	/** Force equal weights so the Edit strip spans the full input bar width. */
	private void stretchEditToolButtons(View tools) {
		if (!(tools instanceof LinearLayout)) {
			return;
		}
		LinearLayout strip = (LinearLayout) tools;
		strip.setWeightSum(0f); // compute from children
		float sum = 0f;
		for (int i = 0; i < strip.getChildCount(); i++) {
			View child = strip.getChildAt(i);
			if (!(child instanceof Button)) {
				continue;
			}
			LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
			if (lp == null) {
				lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
			} else {
				lp.width = 0;
				lp.weight = 1f;
			}
			child.setLayoutParams(lp);
			sum += 1f;
		}
		if (sum > 0f) {
			strip.setWeightSum(sum);
		}
		strip.requestLayout();
	}

	private void setupScrollbackSearchBar() {
		mScrollbackSearchBar = findViewById(R.id.scrollback_search_bar);
		mScrollbackSearchQuery = (EditText) findViewById(R.id.scrollback_search_query);
		mScrollbackSearchCase = (CheckBox) findViewById(R.id.scrollback_search_case);
		mScrollbackSearchCount = (TextView) findViewById(R.id.scrollback_search_count);
		mScrollbackSearchPreview = (TextView) findViewById(R.id.scrollback_search_preview);
		if (mScrollbackSearchBar == null || mScrollbackSearchQuery == null) {
			return;
		}

		View findBtn = findViewById(R.id.scrollback_search_find);
		View closeBtn = findViewById(R.id.scrollback_search_close);
		View prevBtn = findViewById(R.id.scrollback_search_prev);
		View nextBtn = findViewById(R.id.scrollback_search_next);

		View.OnClickListener findListener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				runScrollbackSearchFromBar(true);
			}
		};
		if (findBtn != null) {
			findBtn.setOnClickListener(findListener);
		}
		mScrollbackSearchQuery.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_SEARCH
						|| (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
								&& event.getAction() == KeyEvent.ACTION_DOWN)) {
					runScrollbackSearchFromBar(true);
					return true;
				}
				return false;
			}
		});
		if (closeBtn != null) {
			closeBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					closeScrollbackSearchBar();
				}
			});
		}
		if (prevBtn != null) {
			prevBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					scrollbackSearchNav(-1);
				}
			});
		}
		if (nextBtn != null) {
			nextBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					scrollbackSearchNav(1);
				}
			});
		}
		if (mScrollbackSearchCase != null) {
			mScrollbackSearchCase.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (mScrollbackSearchQuery != null
							&& mScrollbackSearchQuery.getText().toString().trim().length() > 0) {
						runScrollbackSearchFromBar(true);
					}
				}
			});
		}
		updateScrollbackSearchUi();
	}

	private void openScrollbackSearchBar(String query) {
		if (mScrollbackSearchBar == null) {
			setupScrollbackSearchBar();
		}
		if (mScrollbackSearchBar == null) {
			return;
		}
		mScrollbackSearchBar.setVisibility(View.VISIBLE);
		chrome.bringGameplayChromeToFront((RelativeLayout) findViewById(R.id.window_container));
		String q = query == null ? "" : query;
		if (mScrollbackSearchQuery != null) {
			if (q.length() > 0) {
				mScrollbackSearchQuery.setText(q);
				mScrollbackSearchQuery.setSelection(q.length());
			}
			mScrollbackSearchQuery.requestFocus();
		}
		if (q.trim().length() > 0) {
			runScrollbackSearchFromBar(true);
		} else {
			updateScrollbackSearchUi();
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm != null && mScrollbackSearchQuery != null) {
				imm.showSoftInput(mScrollbackSearchQuery, InputMethodManager.SHOW_IMPLICIT);
			}
		}
		refreshGameChrome();
	}

	private void closeScrollbackSearchBar() {
		mScrollbackSearchHits.clear();
		mScrollbackSearchIndex = -1;
		com.resurrection.blowtorch2.lib.window.Window target = findScrollbackSearchWindow();
		if (target != null) {
			target.clearSearchHighlight();
		}
		if (mScrollbackSearchBar != null) {
			mScrollbackSearchBar.setVisibility(View.GONE);
		}
		if (mScrollbackSearchQuery != null) {
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm != null) {
				imm.hideSoftInputFromWindow(mScrollbackSearchQuery.getWindowToken(), 0);
			}
		}
		updateScrollbackSearchUi();
		refreshGameChrome();
	}

	/** nav: -1 previous (newer), 1 next (older), 0 close */
	private void scrollbackSearchNav(int nav) {
		if (nav == 0) {
			closeScrollbackSearchBar();
			return;
		}
		if (mScrollbackSearchBar == null || mScrollbackSearchBar.getVisibility() != View.VISIBLE) {
			openScrollbackSearchBar("");
			return;
		}
		if (mScrollbackSearchHits.isEmpty()) {
			runScrollbackSearchFromBar(true);
			return;
		}
		if (nav > 0) {
			mScrollbackSearchIndex = (mScrollbackSearchIndex + 1) % mScrollbackSearchHits.size();
		} else {
			mScrollbackSearchIndex = (mScrollbackSearchIndex - 1 + mScrollbackSearchHits.size())
					% mScrollbackSearchHits.size();
		}
		jumpToScrollbackSearchHit();
	}

	private void runScrollbackSearchFromBar(boolean jumpToFirst) {
		if (mScrollbackSearchQuery == null) {
			return;
		}
		String query = mScrollbackSearchQuery.getText().toString();
		if (query.trim().isEmpty()) {
			mScrollbackSearchHits.clear();
			mScrollbackSearchIndex = -1;
			updateScrollbackSearchUi();
			if (mScrollbackSearchPreview != null) {
				mScrollbackSearchPreview.setText("Enter a phrase and tap Find.");
			}
			return;
		}
		com.resurrection.blowtorch2.lib.window.Window target = findScrollbackSearchWindow();
		if (target == null) {
			Toast.makeText(this, "No game window to search.", Toast.LENGTH_SHORT).show();
			return;
		}
		boolean caseSensitive = mScrollbackSearchCase != null && mScrollbackSearchCase.isChecked();
		mScrollbackSearchHits.clear();
		mScrollbackSearchHits.addAll(target.findInScrollback(query.trim(), SCROLLBACK_SEARCH_MAX, caseSensitive));
		if (mScrollbackSearchHits.isEmpty()) {
			mScrollbackSearchIndex = -1;
			updateScrollbackSearchUi();
			if (mScrollbackSearchPreview != null) {
				mScrollbackSearchPreview.setText("No matches in scrollback.");
			}
			return;
		}
		if (jumpToFirst || mScrollbackSearchIndex < 0
				|| mScrollbackSearchIndex >= mScrollbackSearchHits.size()) {
			mScrollbackSearchIndex = 0;
		}
		jumpToScrollbackSearchHit();
	}

	private void jumpToScrollbackSearchHit() {
		com.resurrection.blowtorch2.lib.window.Window target = findScrollbackSearchWindow();
		if (target == null || mScrollbackSearchIndex < 0
				|| mScrollbackSearchIndex >= mScrollbackSearchHits.size()) {
			updateScrollbackSearchUi();
			return;
		}
		int broken = mScrollbackSearchHits.get(mScrollbackSearchIndex);
		String query = mScrollbackSearchQuery != null
				? mScrollbackSearchQuery.getText().toString().trim() : "";
		boolean caseSensitive = mScrollbackSearchCase != null && mScrollbackSearchCase.isChecked();
		target.scrollToBrokenLineFromBottom(broken);
		target.setSearchHighlight(query, broken, caseSensitive);
		String preview = target.getScrollbackLinePreview(broken, query, caseSensitive);
		if (mScrollbackSearchPreview != null) {
			if (preview.length() == 0) {
				mScrollbackSearchPreview.setText("(empty line)");
			} else {
				mScrollbackSearchPreview.setText("▶ " + preview);
			}
		}
		updateScrollbackSearchUi();
	}

	private void updateScrollbackSearchUi() {
		if (mScrollbackSearchCount != null) {
			if (mScrollbackSearchHits.isEmpty()) {
				mScrollbackSearchCount.setText("0 / 0");
			} else {
				mScrollbackSearchCount.setText((mScrollbackSearchIndex + 1) + " / "
						+ mScrollbackSearchHits.size()
						+ (mScrollbackSearchHits.size() >= SCROLLBACK_SEARCH_MAX ? "+" : ""));
			}
		}
	}

	private com.resurrection.blowtorch2.lib.window.Window findScrollbackSearchWindow() {
		RelativeLayout rl = (RelativeLayout) findViewById(R.id.window_container);
		if (rl == null) {
			return null;
		}
		com.resurrection.blowtorch2.lib.window.Window win =
				(com.resurrection.blowtorch2.lib.window.Window) rl.findViewWithTag("mainDisplay");
		if (win == null && mWindows != null) {
			for (WindowToken w : mWindows) {
				View v = rl.findViewWithTag(w.getName());
				if (v instanceof com.resurrection.blowtorch2.lib.window.Window) {
					win = (com.resurrection.blowtorch2.lib.window.Window) v;
					if ("mainDisplay".equals(w.getName())) {
						break;
					}
				}
			}
		}
		return win;
	}

	/** Hide on-screen buttons while selecting; keep the copy widget on the game window. */
	private void raiseWindowAboveButtons(final Object windowTag) {
		RelativeLayout rl = (RelativeLayout) findViewById(R.id.window_container);
		if (rl == null) {
			return;
		}
		View buttons = rl.findViewWithTag("button_window");
		if (buttons != null) {
			buttons.setVisibility(View.INVISIBLE);
		}
		// Do not bring game window / root chrome to front — that covered the widget.
	}

	/** Show button_window again after text selection ends. */
	private void restoreButtonsAboveWindows() {
		RelativeLayout rl = (RelativeLayout) findViewById(R.id.window_container);
		if (rl == null) {
			return;
		}
		View buttons = rl.findViewWithTag("button_window");
		if (buttons != null) {
			buttons.setVisibility(View.VISIBLE);
			buttons.bringToFront();
		}
		chrome.bringGameplayChromeToFront(rl);
	}

	public void callWindowScript(String window, String callback) {
		RelativeLayout rl = (RelativeLayout)this.findViewById(R.id.window_container);
		
		com.resurrection.blowtorch2.lib.window.Window lview = (com.resurrection.blowtorch2.lib.window.Window)rl.findViewWithTag(window);
		if(lview != null) {
			lview.callFunction(callback,null);
		}
	}
	
	public void shutdownWindow(com.resurrection.blowtorch2.lib.window.Window window) {
		try {
			service.unregisterWindowCallback(window.getName(), window.getCallback());
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private boolean isLandscape() {
	    Display getOrient = getWindowManager().getDefaultDisplay();
	    int orientation = Configuration.ORIENTATION_UNDEFINED;
	    if(getOrient.getWidth()==getOrient.getHeight()){
	        orientation = Configuration.ORIENTATION_SQUARE;
	    } else{ 
	        if(getOrient.getWidth() < getOrient.getHeight()){
	            orientation = Configuration.ORIENTATION_PORTRAIT;
	        }else { 
	             orientation = Configuration.ORIENTATION_LANDSCAPE;
	        }
	    }
	    if(orientation == Configuration.ORIENTATION_LANDSCAPE) {
	    	return true;
	    } else {
	    	return false;
	    }
	    
	}
	
	public double getStatusBarHeight() {
		return chrome.getStatusBarHeight();
	}
	
	public boolean isStatusBarHidden() {
		return chrome.isStatusBarHidden();
	}
	
	public double getTitleBarHeight() {
		return chrome.getTitleBarHeight();
	}

	int getEditorMenuStackSize() {
		return menuStack.size();
	}

	void scheduleRenawsAfterChromeRefresh() {
		if (myhandler != null) {
			myhandler.removeMessages(MESSAGE_RENAWS);
			myhandler.sendEmptyMessageDelayed(MESSAGE_RENAWS, 80);
		}
	}

	private int getActionBarHeightPx() {
		TypedValue tv = new TypedValue();
		if (getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)) {
			return TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
		}
		return (int) (48 * getResources().getDisplayMetrics().density);
	}

	private void refreshGameChrome() {
		chrome.refresh();
	}

	/** Tell the connection the real mainDisplay cell grid for NAWS. */
	private void reportLiveNawsToService() {
		if (service == null) {
			return;
		}
		try {
			RelativeLayout rl = (RelativeLayout) findViewById(R.id.window_container);
			if (rl == null) {
				return;
			}
			com.resurrection.blowtorch2.lib.window.Window main =
					(com.resurrection.blowtorch2.lib.window.Window) rl.findViewWithTag("mainDisplay");
			if (main == null) {
				return;
			}
			int cols = main.getCalculatedColumns();
			int rows = main.getCalculatedRows();
			if (cols < 1 || rows < 1) {
				return;
			}
			service.setDisplayDimensions(rows, cols);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	/** Start the socket only after NAWS was applied (or retry shortly). */
	private void tryConnectAfterNaws() {
		if (!mPendingInitialConnect || service == null) {
			return;
		}
		try {
			RelativeLayout rl = (RelativeLayout) findViewById(R.id.window_container);
			com.resurrection.blowtorch2.lib.window.Window main = rl == null ? null
					: (com.resurrection.blowtorch2.lib.window.Window) rl.findViewWithTag("mainDisplay");
			if (main == null || main.getCalculatedColumns() < 1) {
				myhandler.sendEmptyMessageDelayed(MESSAGE_CONNECT_WHEN_READY, 100);
				return;
			}
			// Ensure latest grid is on the processor before TCP/NAWS handshake.
			reportLiveNawsToService();
			mPendingInitialConnect = false;
			service.initXfer();
		} catch (RemoteException e) {
			e.printStackTrace();
			mPendingInitialConnect = false;
		}
	}

	@Override
	public void onNewIntent(Intent i) {
		//this is if the activity is currently open, and a new intent has been posted.
		Log.e("new intent","new intent : " + i.getStringExtra("DISPLAY"));
		
		this.setIntent(i);
		saveConnectionExtras(i);
		try {
			String display = i.getStringExtra("DISPLAY");
			if (service != null && display != null) {
				if (!service.getConnectedTo().equals(display)) {
					service.switchTo(display);
				}
				// Notification tap while disconnected: reconnect the still-tracked session.
				if (!service.isConnected() && service.isConnectedTo(display)) {
					service.reconnect(display);
				}
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	public String getPathForPlugin(String mOwner) {
		try {
			String path = service.getPluginPath(mOwner);
			return path;
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}

	@Override
	public Activity getActivity() {
		// TODO Auto-generated method stub
		return (Activity)this;
	}

	@Override
	public boolean isPluginInstalled(String desired) throws RemoteException {
		boolean ret = service.isPluginInstalled(desired);
		return ret;
	}

	@Override
	public boolean checkWindowSupports(String desired, String function) {
		com.resurrection.blowtorch2.lib.window.Window window = windowMap.get(desired);
		if(window != null) {
			return window.checkSupports(function);
		}
		return false;
	}

	@Override
	public void windowCall(String desired, String function, String data) {
		com.resurrection.blowtorch2.lib.window.Window window = windowMap.get(desired);
		if(window != null) {
			window.callFunction(function,data);
		}
	}
	
	@Override
	public void windowBroadcast(String function, String data) {
		for(com.resurrection.blowtorch2.lib.window.Window window : windowMap.values()) {
			if(window.checkSupports(function)) {
				window.callFunction(function, data);
			}
		}
	}

	@Override
	public String getPluginOption(String plugin, String value) throws RemoteException {
		String ret = service.getPluginOption(plugin,value);
		return ret;
	}

	/** Options → Miscellaneous → Manage Storage Access. */
	public void requestStorageAccessFromOptions() {
		if (SDCardUtils.needsAllFilesAccessPrompt()) {
			SDCardUtils.openAllFilesAccessSettings(this);
			Toast.makeText(this,
					"Grant \"All files access\" for BlowTorch, then tap Manage Storage Access again to create /BlowTorch/.",
					Toast.LENGTH_LONG).show();
			return;
		}
		View root = findViewById(R.id.window_container);
		if (root == null) {
			root = mRootView;
		}
		SDCardUtils.hasPermissions(this, root, RP_INFO, new Runnable() {
			@Override
			public void run() {
				settingsTransfer.showPermissionsMessage(SDCardUtils.hasStoragePermissions(MainWindow.this));
			}
		});
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions,
										   int[] grantResults) {
		final View root = findViewById(R.id.window_container);
		final boolean external = SDCardUtils.hasStoragePermissions(this);
		final int featureRes = PermissionHelper.featureMessageForRequestCode(requestCode);

		switch(requestCode) {
			case RP_INFO:
				PermissionHelper.handlePermissionResult(this, root, requestCode, RP_INFO, permissions,
						grantResults, featureRes, new Runnable() {
					@Override
					public void run() {
						settingsTransfer.showPermissionsMessage(external);
					}
				}, null);
				break;
			case RP_EXPORT:
				PermissionHelper.handlePermissionResult(this, root, requestCode, RP_EXPORT, permissions,
						grantResults, featureRes, new Runnable() {
					@Override
					public void run() {
						settingsTransfer.doExportDialog();
					}
				}, null);
				break;
			case RP_IMPORT:
				PermissionHelper.handlePermissionResult(this, root, requestCode, RP_IMPORT, permissions,
						grantResults, featureRes, new Runnable() {
					@Override
					public void run() {
						settingsTransfer.doImportDialog(external);
					}
				}, null);
				break;
			case RP_NOTIFICATIONS:
				PermissionHelper.handlePermissionResult(this, root, requestCode, RP_NOTIFICATIONS, permissions,
						grantResults, featureRes, null, null);
				break;
			default:
				break;
		}
	}
}

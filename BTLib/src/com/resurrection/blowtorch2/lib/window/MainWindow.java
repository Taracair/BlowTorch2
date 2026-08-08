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
	private static final int MESSAGE_RETRYWINDOWTOKENS = 925;
	private static final int MESSAGE_REBINDSERVICE = 926;
	/** The framework schedules a crashed foreground service to restart in about a
	 * second, so there is no point asking sooner; a handful of tries covers a slow
	 * restart without turning into a permanent poll. */
	private static final int REBIND_DELAY_MS = 1200;
	private static final int MAX_REBIND_ATTEMPTS = 5;
	private int mRebindAttempts = 0;
	private boolean mPendingInitialConnect = false;
	public final static int MESSAGE_LAUNCHURL = 886;
	/** A tappable word was tapped; obj is the command to send. */
	public final static int MESSAGE_TAPWORDCOMMAND = 8887;
	/** Re-read tappable-word rules from the trigger list. */
	public final static int MESSAGE_REFRESHTAPRULES = 8888;
	/**
	 * A tappable word with more than one command was tapped: obj is the String[]
	 * of commands, arg1/arg2 are the centre and top of the word in the game
	 * window, so the menu can point at it.
	 */
	public final static int MESSAGE_TAPWORDMENU = 8889;
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
	/** Main window scroll sensitivity changed; overlays set to inherit must follow. */
	public static final int MESSAGE_REFRESH_EXTRA_TEXT_SCROLL = 927;
	/** Extra text overlays: sync after Connection slot mutate / settings change. */
	protected static final int MESSAGE_EXTRA_TEXT_UI = 924;
	/** mudstd.frame events are waiting in the service; collect and apply them. */
	protected static final int MESSAGE_FRAME_UI = 928;
	/** arg1: 0=toggle, 1=on, 2=off — Edit tools strip above the input row. */
	protected static final int MESSAGE_INPUT_EDIT_TOOLS = 929;
	/** Re-layout Edit/Send after Options → Window show/hide prefs change. */
	public static final int MESSAGE_REFRESH_INPUT_ACTIONS = 930;
	/** obj: the word to drop into the input bar at the caret. */
	protected static final int MESSAGE_INPUT_INSERT_WORD = 931;
	/** obj: incoming text, for the word completer's vocabulary. */
	protected static final int MESSAGE_VOCABULARY_TEXT = 932;
	/** obj: the world's prompt for the prompt bar; empty hides it. */
	protected static final int MESSAGE_PROMPT_LINE = 933;
	protected static final int MESSAGE_VOCABULARY_RESET = 934;
	protected static final int MESSAGE_PICK_COMPLETION = 935;
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
	/** Floating button copies over the game; see ensureFloatingButtons(). */
	private FloatingButtonController floatingButtons;
	/** Options → Input master switch; default on. */
	private boolean floatingButtonsEnabled = true;
	/** Windows for mudstd.frame image frames; built on demand, see ensureFrameOverlays(). */
	private FrameOverlayController frameOverlay;
	/** Cached extra-text slots from settings (UI process; Connection holds service copy). */
	private final java.util.ArrayList<ExtraTextSlot> extraTextSlotsCache =
			new java.util.ArrayList<ExtraTextSlot>();
	private boolean extraTextWindowsEnabled = true;
	
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
	/** Profile flag: Options → Input → Standard keyboard input. */
	private boolean mCompatibilityMode = false;
	boolean historyWidgetKept = false;
	/** Length of the kept line; first keystroke should replace it, not append. */
	private int keepLastReplaceLength = 0;
	private android.text.TextWatcher keepLastTextWatcher;
	/** What the keystroke typed, waiting for afterTextChanged to apply it. */
	private String keepLastPendingReplace = null;
	/** True while this watcher is the one editing the text. */
	private boolean keepLastSuppress = false;
	/** Was the kept line still selected when this edit started? Read in
	 * beforeTextChanged, where the selection is the one from before the edit. */
	private boolean keepLastWasSelected = false;
	Boolean settingsLoaded = false; //synchronize or try to mitigate failures of writing button data, or failures to read data
	/** Whether the service binding is currently up.
	 *
	 * <p>A plain boolean on purpose. This used to be a Boolean guarded by
	 * synchronized(serviceConnected) with a notify() inside — three problems in one
	 * line: autoboxed Booleans are interned, so that locked an object shared with
	 * every other class in the process; the assignment inside the block changed which
	 * object the next block would lock; and nothing anywhere ever wait()ed for the
	 * notify. It only ever ran on the UI thread, so a field is all it needs to be. */
	boolean serviceConnected = false;
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
				service.registerCallback(the_callback, host, port,
						MainWindow.this.getConnectionTls(), display);
				// Do NOT windowShowing(true) here. After a UI process kill the
				// windows are not registered yet — saying "showing" clears the
				// hold and pushes lines at a null/dead callback. finishInitializeWindows
				// says showing once the new Windows own the binders. onResume's
				// already-bound branch still covers Keep-in-background return.
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
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.reconnect", e);
			}
			serviceConnected = true;
			MainWindow.this.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					ensureMapperOverlay();
					ensureExtraTextOverlays();
					ensureFloatingButtons();
					restoreOpenFrames();
					raiseFloatingButtons();
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
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor("MainWindow.unregister callback", e);
			}
			
			service = null;
			// The binding died with the process, so this activity is not bound any more
			// whatever isBound says. Leaving it true was the whole bug: onResume only
			// rebinds when !isBound, so coming back to the app took the "already bound"
			// branch, service stayed null, and every call quietly did nothing while the
			// UI still looked alive.
			isBound = false;
			// Every window's callback was registered with the Connection that just
			// died, so they are all talking to nothing. Leaving windowsInitialized
			// true made initiailizeWindows() return at its guard when the new service
			// asked for window settings, so the windows were never rebuilt and never
			// re-registered — the service came back and the text still went nowhere.
			markWindowsDirty();
			mRebindAttempts = 0;
			scheduleServiceRebind();

			serviceConnected = false;
		}
		
	};
	
	//private LayerManager mLayers = null;
	public void onCreate(Bundle icicle) {
		//Log.e("Window","start onCreate");
		//Debug.startMethodTracing("window");
		com.resurrection.blowtorch2.lib.util.StartupProbe.mark("window.enter");
		super.onCreate(icicle);
		com.resurrection.blowtorch2.lib.util.StartupProbe.mark("window.super");
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
		com.resurrection.blowtorch2.lib.util.StartupProbe.mark("window.setContentView");
		assignLegacyChromeIds();
		saveConnectionExtras(getIntent());
		com.resurrection.blowtorch2.lib.service.LuaLibraryHelper.ensureCurrentVersion(this);
		com.resurrection.blowtorch2.lib.util.StartupProbe.mark("window.lua libs");
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

		com.resurrection.blowtorch2.lib.util.StartupProbe.mark("window.chrome");
		history = new CommandKeeper(75);
		history.load(this, getConnectionDisplay());
		com.resurrection.blowtorch2.lib.util.StartupProbe.mark("window.history load");



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
				case MESSAGE_FRAME_UI:
					MainWindow.this.handleFrameUiAction();
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
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onCreate", e3);
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
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onCreate", e9);
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
					// Service clutch moved (e.g. .switch). Keep the Activity
					// Intent/prefs on the same display — do not unbind/rebind.
					// The old rebind left DISPLAY on the launch world, so the
					// next onResume switched the clutch back after screen-off.
					if (msg.obj instanceof String) {
						MainWindow.this.rememberForegroundConnection((String) msg.obj);
					}
					break;
				case MESSAGE_TRIGGERSTR:
					
					break;
				case MESSAGE_TESTLUA:
					//LuaState exist = LuaStateFactory.getExistingState(msg.arg1);
					//exist.LdoString("Note(\"Fooooooo\")");
					break;
				case MESSAGE_LAUNCHURL:
					if (msg.obj instanceof String) {
						String raw = (String) msg.obj;
						String extracted = TextTree.extractUrl(raw);
						String url = TextTree.normalizeUrl(
								extracted != null ? extracted : raw);
						if (url != null && url.length() > 0) {
							try {
								startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
							} catch (Exception e) {
								com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onCreate", e);
							}
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
								com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onCreate", e);
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
				case MESSAGE_RETRYWINDOWTOKENS:
					retryWindowTokens();
					break;
				case MESSAGE_REBINDSERVICE:
					rebindServiceAfterDeath();
					break;
				case MESSAGE_RENAWS:
					reportLiveNawsToService();
					break;
				case MESSAGE_REFRESHTAPRULES:
					refreshTapRules();
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
				case MESSAGE_REFRESH_INPUT_ACTIONS:
					scheduleInputActionLayoutRefresh();
					break;
				case MESSAGE_REFRESH_EXTRA_TEXT_SCROLL:
					if (extraTextOverlay != null) {
						extraTextOverlay.refreshScrollSpeeds();
					}
					break;
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
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onCreate", e);
					} catch (UnsupportedEncodingException e) {
						
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor("MainWindow.onCreate", e);
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
					// A masked line is a password. The history is written to disk and
					// comes back on ↑ after a restart, so it must not go in at all.
					if (!mLocalEchoOff) {
						history.addCommand(pdata);
						history.save(MainWindow.this, getConnectionDisplay());
					}
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
						
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor("MainWindow.onCreate", e);
					} catch (RemoteException e) {
						
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onCreate", e);
					}
				
					buf.rewind();
				
					byte[] buffbytes = buf.array();

					try {
						service.sendData(buffbytes);
					} catch (RemoteException e) {
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onCreate", e);
					}
					myhandler.sendEmptyMessage(MainWindow.MESSAGE_RESETINPUTWINDOW);
					break;
				case MESSAGE_RESETINPUTWINDOW:
					//Log.e("WINDOW","Attempting to reset input bar.");
					
					//try {
					if(isKeepLast && !mLocalEchoOff) {
						keepLastReplaceLength = mInputBox.getText().length();
						// Only when the bar really is holding a kept command.
						// This flag makes the next ↑ skip one entry, on the
						// grounds that the first one back is already on screen.
						// Claiming it for an empty bar therefore skips a command
						// that was never shown — and nothing cleared it in that
						// case, because the watcher below only clears while
						// keepLastReplaceLength > 0. That is the "sometimes it
						// jumps two commands back" report.
						historyWidgetKept = keepLastReplaceLength > 0;
						if (keepLastReplaceLength > 0) {
							mInputBox.post(new Runnable() {
								@Override
								public void run() {
									if (mInputBox == null || keepLastReplaceLength <= 0) {
										return;
									}
									mInputBox.requestFocus();
									mInputBox.setSelection(0, keepLastReplaceLength);
								}
							});
						}
					} else {
						keepLastReplaceLength = 0;
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
				case MESSAGE_TAPWORDCOMMAND:
					// Same road as a typed line: CRLF and out through the
					// service, so aliases and logging behave the same way.
					try {
						String tapCmd = (String) msg.obj;
						if (tapCmd != null && tapCmd.length() > 0 && service != null) {
							service.sendData((tapCmd + "\r\n").getBytes(service.getEncoding()));
						}
					} catch (Exception e) {
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
								"MainWindow.tapWordCommand", e);
					}
					break;
				case MESSAGE_TAPWORDMENU:
					showTapWordMenu((String[]) msg.obj, msg.arg1, msg.arg2);
					break;
				case MESSAGE_SENDDATAOUT:
					try {
						service.sendData((byte[])msg.obj);
						
					} catch (RemoteException e) {
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onCreate", e);
					}
					//screen2.jumpToZero();
					
					
					break;
				case MESSAGE_CLEARALLBUTTONS:
					MainWindow.this.windowCall("button_window", "clearButtons", "");
					break;
				case MESSAGE_CHANGEBUTTONSET:
					if (msg.obj != null && service != null) {
						try {
							com.resurrection.blowtorch2.lib.util.StartupProbe.mark("loadButtonSet call");
							service.pluginXcallS("button_window", "loadButtonSet", (String) msg.obj);
							com.resurrection.blowtorch2.lib.util.StartupProbe.mark("loadButtonSet returned");
						} catch (RemoteException e) {
							com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onCreate", e);
						}
					}
					break;
				case MESSAGE_INPUT_INSERT_WORD:
					inputInsertWord((String) msg.obj);
					break;
				case MESSAGE_VOCABULARY_TEXT:
					mWordSuggestions.learn((String) msg.obj);
					mWordSuggestionsOn = true;
					refreshWordSuggestions();
					break;
				case MESSAGE_VOCABULARY_RESET:
					mWordSuggestions.clear();
					refreshWordSuggestions();
					break;
				case MESSAGE_PICK_COMPLETION:
					pickWordSuggestion(msg.arg1);
					break;
				case MESSAGE_PROMPT_LINE:
					showPromptBar((String) msg.obj);
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
				case MESSAGE_INPUT_EDIT_TOOLS:
					applyInputEditToolsMessage(msg.arg1);
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
			startAction.putExtra("TLS", getConnectionTls());

			androidx.core.content.ContextCompat.startForegroundService(this, startAction);
		}
		
		//register screenlock thingie.
		//IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
		//filter.addAction(Intent.ACTION_SCREEN_OFF);
		///filter.addAction(Intent.ACTION_USER_PRESENT);
		//BroadcastReceiver mReceiver = new ScreenState(myhandler);
		//registerReceiver(mReceiver, filter);
		
		
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
		com.resurrection.blowtorch2.lib.util.StartupProbe.mark("window.leave");
		getWindow().getDecorView().post(new Runnable() {
			@Override
			public void run() {
				com.resurrection.blowtorch2.lib.util.StartupProbe.mark("window.first post");
			}
		});
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
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.doExportSettings", e);
		}
	}

	protected void doResetSettings() {
		try {
			service.resetSettings();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.doResetSettings", e);
		}
	}

	protected void setUseCompatibilityMode(boolean value) {
		mCompatibilityMode = value;
		applyInputConnectionMode();
	}

	/** Standard Android {@link InputConnection} when keep-last or IME fix is on. */
	private void applyInputConnectionMode() {
		if (mInputBox == null) {
			return;
		}
		boolean useStandard = mCompatibilityMode || isKeepLast;
		mInputBox.setBackSpaceBugFix(useStandard);
		InputMethodManager imm = (InputMethodManager) mInputBox.getContext()
				.getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null) {
			imm.restartInput(mInputBox);
		}
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
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.dispatchLuaError", e);
		}
	}
	
	public void dispatchLuaText(String obj) {
		try {
			service.dispatchLuaText(obj);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.dispatchLuaText", e);
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
		applyInputConnectionMode();
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
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.DoDisconnectMessage", e);
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
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.DoDisconnectMessage", e);
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
		if (rl == null) {
			return true;
		}

		if(menuStack.size() > 0) {
			com.resurrection.blowtorch2.lib.window.Window tmp =
					(com.resurrection.blowtorch2.lib.window.Window) rl.findViewWithTag(menuStack.peek().window);
			if (tmp != null) {
				tmp.populateMenu(menu);
			}
			return true;
		}

		if(mWindows != null) {
			for(WindowToken w : mWindows) {
				if (w == null || isExtraTextSlotWindow(w.getName())) {
					// Extra text Views live in overlays, not under window_container tags.
					continue;
				}
				com.resurrection.blowtorch2.lib.window.Window tmp =
						(com.resurrection.blowtorch2.lib.window.Window) rl.findViewWithTag(w.getName());
				if (tmp == null && windowMap != null) {
					tmp = windowMap.get(w.getName());
				}
				if (tmp != null) {
					tmp.populateMenu(menu);
				}
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
		// Reset Settings moved to Options → Miscellaneous, with Export and
		// Import. It throws away every alias, trigger, timer and button in the
		// world, and it sat one tap from Reconnect and Disconnect. The id stays
		// wired below for the Options entry.
		// Export/Import Settings moved to Options → Miscellaneous, next to the
		// storage settings they depend on. This menu was sixteen items long and
		// these two are setup-and-migration jobs, not things you reach for mid
		// session. The menu ids stay wired for the Options entries.
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

	/**
	 * The word that was tapped carries more than one command, so ask which one.
	 * Same ListPopupWindow and same themed background as the gameplay ⋮ menu —
	 * a second kind of in-game menu would look like a different app.
	 *
	 * @param commands what to offer, $word already filled in.
	 * @param wordCenterX centre of the tapped word inside the game window.
	 * @param wordTop top of the tapped word inside the game window.
	 */
	void showTapWordMenu(final String[] commands, final int wordCenterX, final int wordTop) {
		if (commands == null || commands.length == 0) {
			return;
		}
		try {
			RelativeLayout rl = (RelativeLayout) findViewById(R.id.window_container);
			View gameWindow = rl != null ? rl.findViewWithTag("mainDisplay") : null;
			View anchor = gameWindow != null ? gameWindow : rl;
			if (anchor == null) {
				return;
			}
			Context themed = new ContextThemeWrapper(this, R.style.BlowTorch_Game_PopupMenu);
			final float density = getResources().getDisplayMetrics().density;
			final androidx.appcompat.widget.ListPopupWindow popup =
					new androidx.appcompat.widget.ListPopupWindow(themed);
			popup.setAnchorView(anchor);
			popup.setModal(true);
			// What each row shows. A command can be as long as the player likes
			// ("put all artifacts in 2.trail;…"), and a menu that grows to fit
			// one of those covers the text it is about — so a long one is cut
			// and ends in (...). The command that is sent is never the cut one.
			final String[] shown = new String[commands.length];
			for (int i = 0; i < commands.length; i++) {
				shown[i] = shortenForMenu(commands[i]);
			}
			popup.setAdapter(new ArrayAdapter<String>(
					themed, android.R.layout.simple_list_item_1, shown) {
				@Override
				public View getView(int position, View convertView, android.view.ViewGroup parent) {
					View row = super.getView(position, convertView, parent);
					if (row instanceof android.widget.TextView) {
						android.widget.TextView label = (android.widget.TextView) row;
						// One line, cut with an ellipsis if the row is still too
						// narrow: belt and braces over shortenForMenu, which
						// cannot know the font.
						label.setSingleLine(true);
						label.setEllipsize(android.text.TextUtils.TruncateAt.END);
						label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
						int padX = Math.round(10 * density);
						int padY = Math.round(4 * density);
						label.setPadding(padX, padY, padX, padY);
						label.setMinimumHeight(Math.round(TAP_MENU_ROW_DIP * density));
					}
					return row;
				}
			});
			// The same rounded plate the suggestion bar uses. This menu lands in
			// the middle of moving text, and a square panel there reads as the
			// display having broken rather than as something being offered.
			popup.setBackgroundDrawable(androidx.core.content.ContextCompat.getDrawable(
					themed, R.drawable.suggestion_panel_bg));
			popup.setAnimationStyle(android.R.style.Animation_Dialog);
			// Deliberately narrower than the ⋮ menu: this one points at a word in
			// the middle of the text and has to leave that word readable.
			int width = Math.min(
					Math.round(getResources().getDisplayMetrics().widthPixels * 0.5f),
					(int) (168 * density));
			popup.setContentWidth(width);
			// A word can carry a lot of commands, and a menu as tall as the screen
			// buries the text it is about. Past this it scrolls instead of growing.
			if (commands.length > TAP_MENU_MAX_ROWS) {
				popup.setHeight(Math.round(
						(TAP_MENU_MAX_ROWS + 0.5f) * TAP_MENU_ROW_DIP * density));
			}
			// The anchor is the whole game window, so the offsets carry the word
			// position. Keep the menu on screen: it hangs below the word unless
			// there is no room, and never starts left of the window edge.
			int maxX = Math.max(0, anchor.getWidth() - width);
			popup.setHorizontalOffset(Math.max(0, Math.min(wordCenterX - width / 2, maxX)));
			popup.setVerticalOffset(wordTop - anchor.getHeight() + (int) (4 * density));
			popup.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(android.widget.AdapterView<?> parent, View view,
						int position, long id) {
					popup.dismiss();
					if (position >= 0 && position < commands.length) {
						myhandler.sendMessage(myhandler.obtainMessage(
								MESSAGE_TAPWORDCOMMAND, commands[position]));
					}
				}
			});
			popup.show();
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"MainWindow.showTapWordMenu", e);
		}
	}

	/** Longest command text a menu row shows before it is cut. */
	static final int TAP_MENU_MAX_CHARS = 18;

	/** Row height. Small, because this menu covers the text it is about. */
	static final int TAP_MENU_ROW_DIP = 30;

	/** Past this the menu scrolls rather than growing over the game. */
	static final int TAP_MENU_MAX_ROWS = 6;

	/**
	 * Menu label for a command: the whole thing when it is short, otherwise the
	 * front of it and {@code (...)}. Only what is displayed — the command sent
	 * is always the full one.
	 */
	static String shortenForMenu(final String command) {
		if (command == null) {
			return "";
		}
		String s = command.trim();
		if (s.length() <= TAP_MENU_MAX_CHARS) {
			return s;
		}
		// Cut on a space when there is one close to the limit, so the row ends
		// on a word rather than mid-word.
		int cut = TAP_MENU_MAX_CHARS;
		int space = s.lastIndexOf(' ', TAP_MENU_MAX_CHARS);
		if (space >= TAP_MENU_MAX_CHARS / 2) {
			cut = space;
		}
		return s.substring(0, cut).trim() + "(...)";
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
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onOptionsItemSelected", e2);
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
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onOptionsItemSelected", e);
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
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onOptionsItemSelected", e);
			}
			break;
		case 700:
			try {
				service.reconnect(service.getConnectedTo());
			} catch (RemoteException e1) {
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onOptionsItemSelected", e1);
			}
			break;
		case 300:
			BetterTimerSelectionDialog sel = new BetterTimerSelectionDialog(this,service);
			sel.show();
			break;
		case 100:
			BetterAliasSelectionDialog d = new BetterAliasSelectionDialog(this,service);
			d.setTitle("Edit Aliases:");
			// No dismiss listener here, unlike the trigger list: an alias whose
			// text a trigger's pattern names changes what that trigger matches,
			// and the service says so itself now -- buildTriggerSystem ends in
			// tapRulesChanged(). One mechanism, and it also covers `.name
			// newtext` typed in the input bar, which never opens a dialog.
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
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onOptionsItemSelected", e);
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
			// The trigger list is a Dialog on this activity, so closing it does not
			// resume anything — and the resume was the only place tap rules were
			// re-read. That is why a tappable word added in the editor did nothing
			// until the app was left and reopened.
			btsd.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
				@Override
				public void onDismiss(android.content.DialogInterface dialog) {
					scheduleTapRulesRefresh();
				}
			});
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
	
	/** Words the game has used, for completion. Empty until .complete on. */
	private final WordSuggestions mWordSuggestions = new WordSuggestions();
	/** True once the service has started feeding the completer. */
	private boolean mWordSuggestionsOn = false;
	/** How many completions fit on the strip without it becoming a wall. */
	private static final int MAX_WORD_SUGGESTIONS = WordSuggestions.MAX_ON_STRIP;
	/**
	 * What the strip is offering right now, in the order it shows them, so
	 * {@code .complete 3} means the same thing as tapping the third chip.
	 */
	private final java.util.List<String> mWordSuggestionList =
			new java.util.ArrayList<String>();
	/**
	 * Where the chips go: {@link WordSuggestions#WHERE_FLOATING},
	 * {@code WHERE_BAR} or {@code WHERE_NONE}.
	 */
	private int mWordSuggestionsWhere = WordSuggestions.DEFAULT_WHERE;
	/**
	 * Draw the chips over the game text instead of inside the input chrome.
	 *
	 * <p>Derived from {@link #mWordSuggestionsWhere} rather than read from its
	 * own setting: everything that picks between the two views asks this, and
	 * with no bar at all nothing gets that far — {@link #refreshWordSuggestions}
	 * returns first.
	 */
	private boolean mWordSuggestionsOverlay = true;
	/** How solid the chips are, 10–100 percent. */
	private int mWordSuggestionsOpacity = WordSuggestions.DEFAULT_OPACITY;
	/** Set once the overlay copy is following the input bar's top edge. */
	private boolean mWordSuggestionsOverlayTracked = false;
	/** Draw the top suggestion after the caret in dimmed type. */
	private boolean mWordSuggestionsGhost = false;

	/**
	 * Rebuild the completion strip for whatever is half-typed in the input bar.
	 *
	 * <p>Cheap enough to run on every keystroke: a prefix scan over a bounded
	 * map, and at most six small Buttons. The strip hides itself when there is
	 * nothing to offer, so it costs no height the rest of the time.
	 */
	private void refreshWordSuggestions() {
		// Both exist in the layout; the player's choice decides which one is used
		// and the other stays gone. Two views rather than one that is re-parented:
		// moving a view between an in-layout parent and the overlay mid-session is
		// the kind of thing that works until the first configuration change.
		View inline = findViewById(R.id.input_word_suggestions);
		View floating = findViewById(R.id.input_word_suggestions_float);
		if (mWordSuggestionsWhere == WordSuggestions.WHERE_NONE) {
			// No bar in either place. The suggestions themselves are still worked
			// out, because they are what the ghost draws and what .suggest N picks
			// — the bar is one way of showing them, not the feature. This is the
			// whole reason the work below the early return had to move into
			// recomputeSuggestions: hiding the bar here used to mean skipping it.
			cancelWordSuggestionHide();
			if (inline != null) {
				inline.setVisibility(View.GONE);
			}
			if (floating != null) {
				floating.setVisibility(View.GONE);
			}
			recomputeSuggestions();
			return;
		}
		View strip = mWordSuggestionsOverlay ? floating : inline;
		LinearLayout row = (LinearLayout) findViewById(mWordSuggestionsOverlay
				? R.id.input_word_suggestions_float_row
				: R.id.input_word_suggestions_row);
		View unused = mWordSuggestionsOverlay ? inline : floating;
		if (unused != null) {
			unused.setVisibility(View.GONE);
		}
		if (strip == null || row == null) {
			return;
		}
		if (mWordSuggestionsOverlay) {
			trackInputBarForOverlay();
			bindSuggestionGrip();
		} else {
			// The in-layout strip takes height, so the only way to stop the game
			// window jumping is for that height never to change. Held at one row
			// whether or not there is anything in it — the chips are shorter than
			// this, so the strip is this tall always and nothing above it moves.
			strip.setMinimumHeight(mWordSuggestionsPersist
					? persistentBarMinHeight() : 0);
		}
		// Clears the list before anything hides: a pending delayed hide re-reads
		// it to decide whether the panel is still wanted.
		java.util.List<String> words = recomputeSuggestions();
		if (!mWordSuggestionsOn || mInputBox == null) {
			cancelWordSuggestionHide();
			strip.setVisibility(View.GONE);
			return;
		}
		// Collapsed is about the chips, not about completion. The suggestions are
		// still worked out — the ghost still draws, .complete N still picks — and
		// the panel stays up showing only the grip that folded it, so there is
		// something left to tap to unfold. Pretending there were no suggestions
		// instead would take the panel away with the grip inside it, and nothing
		// short of reopening Options would bring it back.
		if (mWordSuggestionsCollapsed && mWordSuggestionsOverlay) {
			cancelWordSuggestionHide();
			hideAllChips(row);
			// Collapsed is meant to be small: the grip and nothing else. That is
			// the difference between "folded away" and "empty but still here".
			strip.setMinimumWidth(0);
			applyStripOpacity(strip);
			strip.setVisibility(View.VISIBLE);
			return;
		}
		if (words.isEmpty()) {
			// Persistent: the panel is a fixed thing you can aim at, not something
			// that appears under your thumb. Empty it still shows its grip, which
			// is why the grip is drawn as an object and not as a decoration.
			if (mWordSuggestionsPersist) {
				cancelWordSuggestionHide();
				hideAllChips(row);
				// Floating: the panel is wrap_content, so an empty one shrinks to
				// its grip — a thumbnail-sized dark blob that reads as "the bar is
				// gone", which is the one thing this option exists to prevent.
				// Held at a bar's width it stays something you can aim at.
				//
				// In-layout: the width is already match_parent and it is the
				// *height* that has to hold, which it does from the minimum set
				// above. Either way the bar stays put.
				if (mWordSuggestionsOverlay) {
					strip.setMinimumWidth(persistentBarMinWidth());
				}
				applyStripOpacity(strip);
				strip.setVisibility(View.VISIBLE);
				return;
			}
			hideWordSuggestionsSoon(strip, row);
			return;
		}
		cancelWordSuggestionHide();
		// With chips in it the panel sizes itself; a minimum from the empty
		// state left behind would pad the last chip out to nothing. Height is
		// not touched: for the in-layout strip that minimum is what keeps the
		// game window still, and it has to hold whether the strip is full or not.
		if (mWordSuggestionsOverlay) {
			strip.setMinimumWidth(0);
		}
		for (int i = 0; i < words.size(); i++) {
			final String word = words.get(i);
			TextView chip = chipAt(row, i);
			chip.setText(numberedChipLabel(i + 1, word));
			chip.setTag(word);
			chip.setVisibility(View.VISIBLE);
		}
		// Spare chips are hidden, not removed: the next keystroke almost always
		// wants them back, and removing and re-inflating on every letter is what
		// made the strip flicker.
		for (int i = words.size(); i < row.getChildCount(); i++) {
			row.getChildAt(i).setVisibility(View.GONE);
		}
		applyStripOpacity(strip);
		strip.setVisibility(View.VISIBLE);
		View scroller = findViewById(R.id.input_word_suggestions_float_scroll);
		if (mWordSuggestionsOverlay && scroller != null) {
			scroller.scrollTo(0, 0);
		} else if (!mWordSuggestionsOverlay) {
			strip.scrollTo(0, 0);
		}
	}

	/**
	 * Work out what is suggested for the half-typed word, and draw the ghost.
	 *
	 * <p>Separate from the bar because it has to happen whether or not there is
	 * a bar: {@link #mWordSuggestionList} is what {@code .suggest 3} picks from,
	 * and the ghost is a suggestion shown without one. With the completer off
	 * the ghost is cleared rather than left behind, which is what turning it off
	 * has to mean.
	 *
	 * @return the suggestions, best first — the live list, not a copy.
	 */
	private java.util.List<String> recomputeSuggestions() {
		mWordSuggestionList.clear();
		if (!mWordSuggestionsOn || mInputBox == null) {
			updateGhostCompletion(null, mWordSuggestionList);
			return mWordSuggestionList;
		}
		String text = mInputBox.getText() == null ? "" : mInputBox.getText().toString();
		int caret = Math.max(mInputBox.getSelectionStart(), 0);
		String prefix = WordSuggestions.wordBefore(text, caret);
		mWordSuggestionList.addAll(
				mWordSuggestions.suggest(prefix, MAX_WORD_SUGGESTIONS));
		updateGhostCompletion(prefix, mWordSuggestionList);
		return mWordSuggestionList;
	}

	/**
	 * How wide an empty persistent bar stays: half the screen, and never so wide
	 * that it covers the game text it floats over.
	 */
	private int persistentBarMinWidth() {
		int screen = getResources().getDisplayMetrics().widthPixels;
		float d = getResources().getDisplayMetrics().density;
		return Math.min(screen / 2, (int) (220 * d));
	}

	/**
	 * One row of chips, which is the height the in-layout strip holds while it
	 * is persistent. A little more than a chip needs, so a chip never grows it.
	 */
	private int persistentBarMinHeight() {
		return (int) (34 * getResources().getDisplayMetrics().density);
	}

	private void hideAllChips(final LinearLayout row) {
		for (int i = 0; i < row.getChildCount(); i++) {
			row.getChildAt(i).setVisibility(View.GONE);
		}
	}

	/**
	 * The i-th chip, made once and kept.
	 *
	 * <p>The click listener reads the word off the view's tag rather than
	 * closing over it, so the listener survives the chip being reused for a
	 * different word — which is the whole point of keeping it.
	 */
	private TextView chipAt(final LinearLayout row, final int i) {
		if (i < row.getChildCount()) {
			return (TextView) row.getChildAt(i);
		}
		float d = getResources().getDisplayMetrics().density;
		// A TextView, not a Button. Button carries a minimum touch size and an
		// inset background of its own, which is why the strip was taller than the
		// words in it and the chips sat far apart. The row is still finger-sized
		// because the panel sits on the input bar, not in the middle of the text.
		TextView chip = new TextView(this);
		chip.setTextSize(13);
		chip.setTextColor(0xFFE6EAEE);
		chip.setSingleLine(true);
		chip.setBackgroundResource(R.drawable.suggestion_chip_bg);
		chip.setPadding((int) (10 * d), (int) (5 * d), (int) (10 * d), (int) (5 * d));
		chip.setClickable(true);
		chip.setFocusable(false);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		lp.leftMargin = (int) (3 * d);
		chip.setLayoutParams(lp);
		chip.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Object word = v.getTag();
				if (word instanceof String) {
					acceptWordSuggestion((String) word);
				}
			}
		});
		row.addView(chip);
		return chip;
	}

	/** Panel collapsed to its grip by a tap on that grip. */
	private boolean mWordSuggestionsCollapsed = false;
	/** Keep the panel up even with nothing to suggest. */
	private boolean mWordSuggestionsPersist = false;
	/** Set once the grip has its gesture handling. */
	private boolean mSuggestionGripBound = false;

	/**
	 * The panel's one handle: tap collapses it, a long press picks it up.
	 *
	 * <p>On the grip and not on the panel, because the panel is a scrolling
	 * container and a long press inside one fights the scroller for every touch
	 * — the same reason a gesture on a word in the game text is a bad idea. The
	 * grip is a sibling of the scroll view, so the two never see the same event.
	 *
	 * <p>Collapsed the panel keeps its grip and loses its chips, so the thing you
	 * tapped is still under your thumb to tap again. That is also what makes the
	 * persistent-and-empty panel look deliberate rather than like a smudge.
	 */
	private void bindSuggestionGrip() {
		if (mSuggestionGripBound) {
			return;
		}
		final View grip = findViewById(R.id.input_word_suggestions_grip);
		final View panel = findViewById(R.id.input_word_suggestions_float);
		if (grip == null || panel == null) {
			return;
		}
		mSuggestionGripBound = true;
		final int slop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
		grip.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent e) {
				switch (e.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					mGripDownRawX = e.getRawX();
					mGripDownRawY = e.getRawY();
					mGripMoved = false;
					// Consumed, so this view keeps the rest of the gesture.
					return true;
				case MotionEvent.ACTION_MOVE: {
					float dx = e.getRawX() - mGripDownRawX;
					float dy = e.getRawY() - mGripDownRawY;
					if (!mSuggestionDragging) {
						// Straight into the drag once the finger has actually
						// moved. Holding first was the wrong price for a thing
						// whose whole purpose is to be moved — you grab the
						// grip and it comes with you. The tap still exists
						// because a gesture that never passed the slop is a tap.
						if (Math.abs(dx) < slop && Math.abs(dy) < slop) {
							return true;
						}
						mGripMoved = true;
						if (!beginSuggestionPanelDrag(panel)) {
							return true;
						}
					}
					moveSuggestionPanelTo(panel, dx, dy);
					return true;
				}
				case MotionEvent.ACTION_UP:
					if (mSuggestionDragging) {
						endSuggestionPanelDrag(panel);
					} else if (!mGripMoved) {
						v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
						mWordSuggestionsCollapsed = !mWordSuggestionsCollapsed;
						refreshWordSuggestions();
					}
					return true;
				case MotionEvent.ACTION_CANCEL:
					if (mSuggestionDragging) {
						endSuggestionPanelDrag(panel);
					}
					return true;
				default:
					return false;
				}
			}
		});
	}

	/** Did this touch on the grip travel far enough to be a drag rather than a tap? */
	private boolean mGripMoved = false;

	/** Where the finger went down on the grip, in screen coordinates. */
	private float mGripDownRawX = 0f;
	private float mGripDownRawY = 0f;
	/** Panel margins when the drag started, which the drag is a delta on. */
	private int mDragStartLeft = 0;
	private int mDragStartBottom = 0;
	private boolean mSuggestionDragging = false;
	/** True once the player has put the panel somewhere of their own. */
	private boolean mSuggestionPanelPlaced = false;
	private int mSuggestionPanelLeft = 0;
	private int mSuggestionPanelBottom = 0;
	/** Drop this close to where it started and it goes back to following the bar. */
	private static final int SUGGESTION_SNAP_BACK_DIP = 28;

	private static final String PREFS_SUGGESTION_PANEL = "SUGGESTION_PANEL_POS";

	/** Has the player dragged the suggestion panel to a place of their own? */
	boolean isSuggestionPanelPlaced() {
		return mSuggestionPanelPlaced;
	}

	private boolean beginSuggestionPanelDrag(final View panel) {
		ViewGroup.LayoutParams lp = panel.getLayoutParams();
		if (!(lp instanceof android.widget.FrameLayout.LayoutParams)) {
			return false;
		}
		android.widget.FrameLayout.LayoutParams flp =
				(android.widget.FrameLayout.LayoutParams) lp;
		mDragStartLeft = flp.leftMargin;
		mDragStartBottom = flp.bottomMargin;
		mSuggestionDragging = true;
		// Say it has been picked up: a panel that only moves once you have
		// already dragged it leaves you guessing whether the press worked.
		panel.performHapticFeedback(
				android.view.HapticFeedbackConstants.LONG_PRESS);
		panel.setAlpha(0.85f);
		return true;
	}

	/**
	 * Move the panel by the drag so far.
	 *
	 * <p>A delta on the margins it started with, deliberately — the panel also
	 * carries a {@code translationY} for the keyboard lift, and working in
	 * deltas means the panel follows the finger whether the keyboard is up or
	 * down, and keeps following the bar afterwards.
	 */
	private void moveSuggestionPanelTo(final View panel, final float dx, final float dy) {
		ViewGroup parent = (ViewGroup) panel.getParent();
		ViewGroup.LayoutParams lp = panel.getLayoutParams();
		if (parent == null || !(lp instanceof android.widget.FrameLayout.LayoutParams)) {
			return;
		}
		android.widget.FrameLayout.LayoutParams flp =
				(android.widget.FrameLayout.LayoutParams) lp;
		int left = mDragStartLeft + (int) dx;
		// The margin is from the bottom, so up the screen is a bigger margin.
		int bottom = mDragStartBottom - (int) dy;
		int maxLeft = Math.max(0, parent.getWidth() - panel.getWidth());
		int maxBottom = Math.max(0, parent.getHeight() - panel.getHeight());
		flp.leftMargin = Math.max(0, Math.min(left, maxLeft));
		flp.bottomMargin = Math.max(0, Math.min(bottom, maxBottom));
		panel.setLayoutParams(flp);
	}

	private void endSuggestionPanelDrag(final View panel) {
		mSuggestionDragging = false;
		panel.setAlpha(1f);
		ViewGroup.LayoutParams lp = panel.getLayoutParams();
		if (!(lp instanceof android.widget.FrameLayout.LayoutParams)) {
			return;
		}
		android.widget.FrameLayout.LayoutParams flp =
				(android.widget.FrameLayout.LayoutParams) lp;
		float d = getResources().getDisplayMetrics().density;
		int snap = (int) (SUGGESTION_SNAP_BACK_DIP * d);

		// Absorb the keyboard lift into the margin before storing anything.
		// While the panel was following the bar it carried translationY = -lift,
		// so what the player sees is margin + lift. Storing the bare margin and
		// then dropping the translation — which a placed panel does not take —
		// would move the panel down by the height of the keyboard at the moment
		// they let go. Adding the lift in makes the stored number the position
		// they actually chose.
		int lift = Math.round(-panel.getTranslationY());
		if (lift != 0) {
			flp.bottomMargin += lift;
			panel.setTranslationY(0f);
		}

		// Dropped back roughly where it lives by default: forget the placement
		// rather than store one that is almost the default. Without this there
		// is no way back to "follows the input bar" except an option. The bar's
		// own position is lifted too, so compare against where it is now.
		if (flp.leftMargin <= snap
				&& Math.abs(flp.bottomMargin - (defaultSuggestionBottomMargin() + lift))
						<= snap) {
			mSuggestionPanelPlaced = false;
			saveSuggestionPanelPosition();
			positionWordSuggestionOverlay();
			return;
		}
		mSuggestionPanelPlaced = true;
		mSuggestionPanelLeft = flp.leftMargin;
		mSuggestionPanelBottom = flp.bottomMargin;
		panel.setLayoutParams(flp);
		saveSuggestionPanelPosition();
	}

	/**
	 * Where the panel sits when it has not been placed: on the input bar's top
	 * edge, plus the navigation bar the overlay does not have padded away.
	 */
	private int defaultSuggestionBottomMargin() {
		View bar = findInputBar();
		int navPad = 0;
		View container = findViewById(R.id.window_container);
		if (container != null) {
			navPad = container.getPaddingBottom();
		}
		return (bar == null ? 0 : bar.getHeight()) + navPad;
	}

	/**
	 * Per world and per orientation.
	 *
	 * <p>Per world because that is what was asked for, and per orientation
	 * because a position chosen in portrait is off the screen in landscape —
	 * the mistake button coordinates already made once, which is why they grew
	 * xLand/yLand.
	 */
	private String suggestionPanelKey() {
		boolean land = getResources().getConfiguration().orientation
				== Configuration.ORIENTATION_LANDSCAPE;
		return getConnectionDisplay() + "|" + (land ? "land" : "port");
	}

	private void saveSuggestionPanelPosition() {
		SharedPreferences.Editor e =
				getSharedPreferences(PREFS_SUGGESTION_PANEL, Context.MODE_PRIVATE).edit();
		String key = suggestionPanelKey();
		if (!mSuggestionPanelPlaced) {
			e.remove(key + "|left").remove(key + "|bottom");
		} else {
			e.putInt(key + "|left", mSuggestionPanelLeft);
			e.putInt(key + "|bottom", mSuggestionPanelBottom);
		}
		e.apply();
	}

	private void loadSuggestionPanelPosition() {
		SharedPreferences p =
				getSharedPreferences(PREFS_SUGGESTION_PANEL, Context.MODE_PRIVATE);
		String key = suggestionPanelKey();
		mSuggestionPanelLeft = p.getInt(key + "|left", -1);
		mSuggestionPanelBottom = p.getInt(key + "|bottom", -1);
		mSuggestionPanelPlaced = mSuggestionPanelLeft >= 0 && mSuggestionPanelBottom >= 0;
	}

	/**
	 * How long an empty strip stays up before it goes.
	 *
	 * <p>Typing walks through prefixes that match nothing on the way to one that
	 * does — {@code gri} matches, {@code griz} may not until the next letter.
	 * Hiding on the first empty result means the panel blinks on and off under
	 * the thumb, which is what made it unbearable. Long enough to ride out a
	 * keystroke, short enough that a panel over the game text does not linger.
	 */
	private static final long WORD_SUGGESTION_HIDE_DELAY_MS = 400;

	private Runnable mWordSuggestionHide = null;

	private void hideWordSuggestionsSoon(final View strip, final LinearLayout row) {
		if (strip.getVisibility() != View.VISIBLE) {
			return;
		}
		if (mWordSuggestionHide != null) {
			return;
		}
		mWordSuggestionHide = new Runnable() {
			@Override
			public void run() {
				mWordSuggestionHide = null;
				// Re-check: the delay is long enough for the player to have typed
				// a letter that matches again.
				if (!mWordSuggestionList.isEmpty()) {
					return;
				}
				strip.setVisibility(View.GONE);
				for (int i = 0; i < row.getChildCount(); i++) {
					row.getChildAt(i).setVisibility(View.GONE);
				}
			}
		};
		strip.postDelayed(mWordSuggestionHide, WORD_SUGGESTION_HIDE_DELAY_MS);
	}

	private void cancelWordSuggestionHide() {
		if (mWordSuggestionHide == null) {
			return;
		}
		// Both, not the one in use: the hide was posted on whichever view was
		// showing when it was scheduled, and the player may have changed where the
		// bar lives since. Removing a callback from a view that never had it does
		// nothing.
		View floating = findViewById(R.id.input_word_suggestions_float);
		View inline = findViewById(R.id.input_word_suggestions);
		if (floating != null) {
			floating.removeCallbacks(mWordSuggestionHide);
		}
		if (inline != null) {
			inline.removeCallbacks(mWordSuggestionHide);
		}
		mWordSuggestionHide = null;
	}

	/**
	 * Marks a ghost that is a correction rather than a continuation, so a
	 * forgiven typo does not read as letters you are about to have appended.
	 */
	private static final String GHOST_CORRECTION_MARK = " → ";

	/**
	 * Put the top suggestion after the caret, in dimmed type.
	 *
	 * <p>Two shapes, because there are two kinds of suggestion. When the word
	 * continues what was typed, the ghost is just the rest of it: {@code gri}
	 * with {@code grizzled} behind it. When the typo forgiver found it, the
	 * letters have to change rather than grow, so the ghost shows the whole
	 * word behind an arrow — {@code grzld → grizzled}. Both are tapped the same
	 * way and both replace the half-typed word, because
	 * {@link #acceptWordSuggestion} goes through
	 * {@link WordSuggestions#complete}, which replaces rather than appends.
	 *
	 * @param prefix what the player has typed of this word.
	 * @param words the suggestions, best first.
	 */
	private void updateGhostCompletion(final String prefix,
			final java.util.List<String> words) {
		if (mInputBox == null) {
			return;
		}
		if (!mWordSuggestionsGhost || words.isEmpty() || prefix == null
				|| prefix.length() == 0) {
			mInputBox.setGhostCompletion(null, null, 0);
			return;
		}
		String top = words.get(0);
		boolean continues = top.length() > prefix.length()
				&& top.toLowerCase(java.util.Locale.US)
						.startsWith(prefix.toLowerCase(java.util.Locale.US));
		if (continues) {
			mInputBox.setGhostCompletion(top.substring(prefix.length()), top, 1);
			return;
		}
		if (top.equalsIgnoreCase(prefix)) {
			// Already typed in full. Nothing to show and nothing to take.
			mInputBox.setGhostCompletion(null, null, 0);
			return;
		}
		mInputBox.setGhostCompletion(GHOST_CORRECTION_MARK + top, top, 1);
	}

	/** Taking the ghost is taking the first suggestion — the same word. */
	private void bindGhostTap() {
		if (mInputBox == null) {
			return;
		}
		mInputBox.setGhostTapListener(new BetterEditText.GhostTapListener() {
			@Override
			public void onGhostTapped(final String word) {
				acceptWordSuggestion(word);
			}
		});
	}

	/**
	 * Keep the floating strip sitting on the input bar's top edge.
	 *
	 * <p>The input bar is not a fixed height — it grows with a multi-line
	 * command, and the Edit tools row appears and disappears underneath it — so
	 * a margin measured once is wrong within a minute of play. Attached once and
	 * left attached: it is a layout listener on a view that is laid out anyway,
	 * and detaching it would mean deciding when, which is the bug.
	 */
	/**
	 * The input bar, whichever id it is answering to right now.
	 *
	 * <p>Setup calls {@code inputBar.setId(ChromeController.LEGACY_INPUT_BAR_ID)}
	 * so that profiles saying {@code above="10"} keep working. After that,
	 * {@code findViewById(R.id.inputbar)} returns null — which is why
	 * {@link ChromeController#findGameplayInputBar} exists, and why anything that
	 * looks the bar up by its layout id alone silently does nothing. That is what
	 * the completion chips did: the margin was not wrong, it was never applied.
	 */
	private View findInputBar() {
		View bar = findViewById(ChromeController.LEGACY_INPUT_BAR_ID);
		return bar != null ? bar : findViewById(R.id.inputbar);
	}

	private void trackInputBarForOverlay() {
		if (mWordSuggestionsOverlayTracked) {
			return;
		}
		final View bar = findInputBar();
		final View floating = findViewById(R.id.input_word_suggestions_float);
		if (bar == null || floating == null) {
			return;
		}
		mWordSuggestionsOverlayTracked = true;
		loadSuggestionPanelPosition();
		bar.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View v, int l, int t, int r, int b,
					int ol, int ot, int or, int ob) {
				positionWordSuggestionOverlay();
			}
		});
		positionWordSuggestionOverlay();
	}

	/**
	 * Rest the chips on the input bar's top edge.
	 *
	 * <p>The margin is the bar's height <b>plus the navigation bar</b>. The chips
	 * live in {@code gameplay_chrome_overlay}, which reaches the bottom of the
	 * screen; the input bar lives in {@code window_container}, which is padded up
	 * by the system bar inset. Measuring only the bar's height put the chips that
	 * inset too low, so they sat across the bottom of the input bar instead of on
	 * top of it. The FAB strip in the same overlay has always done this — see
	 * {@code ChromeController.placeGameplayFabStrip}, which is where the padding
	 * is read from.
	 *
	 * <p>The keyboard is a translation, not a margin: under {@code adjustNothing}
	 * nothing is resized, and {@code applyImeChromeLift} moves the bar, the FAB
	 * strip and these chips together.
	 */
	private void positionWordSuggestionOverlay() {
		View bar = findInputBar();
		View floating = findViewById(R.id.input_word_suggestions_float);
		if (bar == null || floating == null) {
			return;
		}
		ViewGroup.LayoutParams lp = floating.getLayoutParams();
		if (!(lp instanceof android.widget.FrameLayout.LayoutParams)) {
			return;
		}
		if (mSuggestionDragging) {
			// The finger is the authority while it is down.
			return;
		}
		android.widget.FrameLayout.LayoutParams flp =
				(android.widget.FrameLayout.LayoutParams) lp;
		int wantedBottom = mSuggestionPanelPlaced
				? mSuggestionPanelBottom : defaultSuggestionBottomMargin();
		int wantedLeft = mSuggestionPanelPlaced ? mSuggestionPanelLeft : 0;
		if (flp.bottomMargin != wantedBottom || flp.leftMargin != wantedLeft) {
			flp.bottomMargin = wantedBottom;
			flp.leftMargin = wantedLeft;
			floating.setLayoutParams(flp);
		}
		// A panel that follows the input bar follows it under the keyboard too:
		// the layout listener fires while the keyboard is up, and one left at
		// translation 0 would drop back under it until the next inset dispatch.
		//
		// A *placed* panel does not take the lift at all. The drag stores a
		// margin, and the visible position of a lifted panel is margin + lift —
		// so a panel dropped while the keyboard was up would fall by the height
		// of the keyboard the moment it closed. The player picked a spot on the
		// screen; it stays there, and if the keyboard covers it they can pick
		// another. Predictable beats clever.
		floating.setTranslationY(mSuggestionPanelPlaced ? 0f : bar.getTranslationY());
	}

	/**
	 * Make the strip's own backing as see-through as the player asked.
	 *
	 * <p>Background only, never {@link View#setAlpha}: fading the whole view
	 * fades the words too, and a suggestion you cannot read over the game text
	 * is worse than no suggestion. The text stays fully opaque at every setting.
	 */
	private void applyStripOpacity(final View strip) {
		applyChipOpacity(strip);
	}

	private void applyChipOpacity(final View view) {
		android.graphics.drawable.Drawable bg = view.getBackground();
		if (bg == null) {
			return;
		}
		// mutate(), or every Button sharing the cached default background would be
		// dimmed with it — including Send, which is in the same activity.
		android.graphics.drawable.Drawable own = bg.mutate();
		own.setAlpha(opacityToAlpha());
		if (own != bg) {
			view.setBackground(own);
		}
	}

	private int opacityToAlpha() {
		// Only the floating chips. In the strip below the game window there is
		// nothing behind them worth seeing — just the input chrome — and fading
		// them there is a change nobody asked for.
		if (!mWordSuggestionsOverlay) {
			return 255;
		}
		int pct = mWordSuggestionsOpacity;
		if (pct < WordSuggestions.MIN_OPACITY) {
			pct = WordSuggestions.MIN_OPACITY;
		}
		if (pct > 100) {
			pct = 100;
		}
		return pct * 255 / 100;
	}

	/**
	 * The chip's label: a small dim number, then the word.
	 *
	 * <p>The number is what makes {@code .complete 3} usable — and with it, a
	 * super button over the keyboard, which is the only way to take a completion
	 * without moving your thumb off the keys. Drawn smaller and dimmer than the
	 * word so it reads as a label on the chip rather than part of the word.
	 *
	 * @param n which chip this is, counting from 1.
	 * @param word the completion itself.
	 * @return the styled label.
	 */
	private CharSequence numberedChipLabel(final int n, final String word) {
		String label = n + " " + word;
		android.text.SpannableString out = new android.text.SpannableString(label);
		int end = String.valueOf(n).length();
		out.setSpan(new android.text.style.RelativeSizeSpan(0.7f), 0, end,
				android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		out.setSpan(new android.text.style.ForegroundColorSpan(0xFF888888), 0, end,
				android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		return out;
	}

	/**
	 * Take the n-th completion currently on the strip — {@code .complete 3}.
	 *
	 * @param index counting from 1, as the chips are labelled. Out of range does
	 *        nothing: the strip may have changed between reading it and pressing
	 *        the button, and inserting the wrong word is worse than inserting none.
	 */
	private void pickWordSuggestion(final int index) {
		if (index < 1 || index > mWordSuggestionList.size()) {
			return;
		}
		acceptWordSuggestion(mWordSuggestionList.get(index - 1));
	}

	/** Put the chosen completion in place of what was half-typed. */
	private void acceptWordSuggestion(final String word) {
		if (mInputBox == null) {
			return;
		}
		String text = mInputBox.getText() == null ? "" : mInputBox.getText().toString();
		int caret = Math.max(mInputBox.getSelectionStart(), 0);
		WordSuggestions.Completion c = WordSuggestions.complete(text, caret, word);
		mInputBox.setText(c.text());
		mInputBox.setSelection(Math.min(c.caret(), mInputBox.getText().length()));
		mInputBox.requestFocus();
		refreshWordSuggestions();
	}

	/**
	 * Show the world's prompt above the input bar, or hide the bar when the
	 * prompt is empty (which is what .prompt off sends).
	 *
	 * @param text the prompt.
	 */
	private void showPromptBar(final String text) {
		TextView bar = (TextView) findViewById(R.id.input_prompt_bar);
		if (bar == null) {
			return;
		}
		if (text == null || text.length() == 0) {
			bar.setVisibility(View.GONE);
			bar.setText("");
			return;
		}
		bar.setText(text);
		bar.setVisibility(View.VISIBLE);
	}

	/**
	 * Drop a word into the input bar at the caret and send nothing. This is what
	 * a tappable word bound to {@code .kb insert $word} ends up doing, so the
	 * player can build a command out of names the game just printed instead of
	 * spelling them out on a phone keyboard.
	 *
	 * @param word The text to insert.
	 */
	private void inputInsertWord(final String word) {
		if (mInputBox == null) {
			return;
		}
		String current = mInputBox.getText() == null
				? "" : mInputBox.getText().toString();
		InputWordInsert.Result r = InputWordInsert.apply(current,
				mInputBox.getSelectionStart(), mInputBox.getSelectionEnd(), word);
		mInputBox.setText(r.text());
		mInputBox.setSelection(Math.min(r.caret(), mInputBox.getText().length()));
		mInputBox.requestFocus();
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
				// The bar already shows the newest entry, so step past it.
				cmd = history.getNext();
			}
		} else {
			cmd = history.getPrev();
		}
		// Either direction leaves the bar showing a history entry rather than
		// the kept command, so the skip above must not apply again. Only the
		// ↑ branch used to clear this, which meant ↓ then ↑ skipped an entry.
		historyWidgetKept = false;
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
			if (rl == null || tmp == null || tmp.window == null) {
				return;
			}
			com.resurrection.blowtorch2.lib.window.Window w =
					(com.resurrection.blowtorch2.lib.window.Window) rl.findViewWithTag(tmp.window);
			if (w != null) {
				w.callFunction(tmp.callback, null);
			}
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

		// The suggestion panel's placement is stored per orientation. Re-read it
		// here rather than carrying the portrait one into landscape, where it can
		// be off the side of the screen.
		loadSuggestionPanelPosition();
		positionWordSuggestionOverlay();

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
		// Floating buttons keep one stored position per orientation. Post it
		// rather than doing it here: the branches above can ask for the other
		// orientation outright (the "force landscape" profile option), and the
		// window has not been re-measured yet at this point either.
		if (floatingButtons != null) {
			myhandler.postDelayed(new Runnable() {
				public void run() {
					if (floatingButtons != null) {
						floatingButtons.onOrientationChanged();
					}
				}
			}, 120);
		}

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
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.isServiceConnected", e);
		}
		return false;
	}
	
	public void cleanExit() {
		//we want to kill the service when we go.
		cleanupWindows();
		//shut down the service
		if (service == null) {
			isBound = false;
			return;
		}
		
		// Closing the connection makes the service write the whole settings file
		// and sync it — measured at about two seconds on a 285 KB profile. It
		// used to run on the binder call this thread waited for, so answering
		// "No" to the background prompt froze the app between the tap and the
		// window closing. StrictMode reported it back here, at cleanExit, with
		// "via Binder call" above the dialog's click handler.
		//
		// Handing it to a thread only stops *us* waiting; the work is unchanged
		// and still happens in :stellar. That process keeps going without us:
		// it is a started foreground service, nothing overrides onUnbind, and
		// stopSelf is reached only from the explicit doShutdown path.
		final IConnectionBinder pendingService = service;
		Thread closer = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					String connected = pendingService.getConnectedTo();
					if (connected != null) {
						pendingService.closeConnection(connected);
					}
				} catch (RemoteException e) {
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
							"MainWindow.cleanExit", e);
				}
			}
		}, "bt-clean-exit");
		// Not a daemon: this activity is finishing, and the settings write must
		// outlive it rather than be cut short by the last other thread ending.
		closer.setDaemon(false);
		closer.start();
		
		if(isBound) {
			try {
				service.unregisterCallback(the_callback);
			} catch (RemoteException e) {
				//e.printStackTrace();
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor("MainWindow.unregister callback", e);
			}
			
			try {
				unbindService(mConnection);
			} catch (IllegalArgumentException ignored) {
				// Already unbound during teardown.
			}
			
			
			
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
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor("MainWindow.unregister callback", e);
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
			intent.putExtra("TLS", getConnectionTls());
			androidx.core.content.ContextCompat.startForegroundService(this, intent);
		}
		//Log.e("window","ending onStart");
		
	}
	public void onDestroy() {

		// The trigger action speaks in :stellar, but the responder editor's
		// preview speaks here, which opens a second engine in this process.
		// Give it back with the window.
		com.resurrection.blowtorch2.lib.util.SpeechEngine.release();

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
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onDestroy", e);
				
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
		// Before the early return below: an overlay window left up would float
		// over whatever the player opened next, including other apps.
		//
		// This order is load-bearing, not incidental. onPause() clears `resumed`,
		// and clearButtonsOnPause() below now makes Lua notify the floating
		// layer (see buttonwindow.clearButtons). rebuildOverlay returns early on
		// !resumed, which is what keeps that notify from putting overlay windows
		// back up on the way out of the app. Today a second thing also saves it
		// — revertButtonData carries no `floating` field, so the cleared set
		// yields an empty model list — but that is a property of the BACK
		// button, not a guarantee. Do not swap these two.
		if (floatingButtons != null) {
			floatingButtons.onPause();
		}
		if(service == null) { super.onPause(); return; };
		clearButtonsOnPause();
		try {
			service.windowShowing(false);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onPause", e);
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
		if (floatingButtons != null) {
			// Re-checks the overlay grant too: it can be revoked while we live.
			floatingButtons.onResume();
		}
		// Coming back from another app or from the options screen lands here.
		// Editing a trigger does not — see scheduleTapRulesRefresh.
		scheduleTapRulesRefresh();

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
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onResume", e1);
			}
			try {
				if (service != null) {
					String connected = service.getConnectedTo();
					String display = getConnectionDisplay();
					if (connected != null && !connected.isEmpty()) {
						// Clutch is authoritative while the service is up.
						// Never switchTo(display) here — that undid .switch after
						// screen-off when the launch Intent still named the first
						// world. If they differ (session closed in background,
						// etc.), rebuild onto the clutch.
						if (!connected.equals(display)) {
							Log.i("BlowTorch", "onResume: rebuild onto clutch "
									+ connected + " (Intent was " + display + ")");
							service.switchTo(connected);
						}
					} else if (display != null && !display.isEmpty()
							&& service.isConnectedTo(display)) {
						Log.i("BlowTorch", "onResume: clutch empty, switchTo "
								+ display);
						service.switchTo(display);
					}
				}
			} catch (RemoteException e) {
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onResume", e);
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
		com.resurrection.blowtorch2.lib.util.StartupProbe.mark("ui.loadSettings enter");
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
			com.resurrection.blowtorch2.lib.util.StartupProbe.mark("ui.getSettings parcel");

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
			applyOverflowAppearance(group);
			refreshGameChrome();
			final View chromeRootRefresh = findViewById(R.id.window_container);
			if (chromeRootRefresh != null) {
				ViewCompat.requestApplyInsets(chromeRootRefresh);
			}
			//BetterEditText input_box = (BetterEditText)findViewById(R.id.textinput);
			
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

			BaseOption floatBtnOpt = (BaseOption) group.findOptionByKey("floating_buttons_enabled");
			if (floatBtnOpt != null && floatBtnOpt.getValue() instanceof Boolean) {
				floatingButtonsEnabled = (Boolean) floatBtnOpt.getValue();
			} else {
				floatingButtonsEnabled = true;
			}
			ensureFloatingButtons();
			if (floatingButtons != null) {
				floatingButtons.onMasterSwitchChanged(floatingButtonsEnabled);
			}

			// The completer lives here, in the UI process, so the option has to be
			// read here too. Off drops the vocabulary rather than leaving stale
			// names on the strip until something else happens to refresh it.
			BaseOption completeOpt = (BaseOption) group.findOptionByKey("word_complete");
			boolean completeOn = completeOpt != null
					&& completeOpt.getValue() instanceof Boolean
					&& (Boolean) completeOpt.getValue();
			if (!completeOn && mWordSuggestionsOn) {
				mWordSuggestions.clear();
			}
			mWordSuggestionsOn = completeOn;
			BaseOption completeLinesOpt =
					(BaseOption) group.findOptionByKey("word_complete_lines");
			if (completeLinesOpt != null && completeLinesOpt.getValue() instanceof Integer) {
				int lines = (Integer) completeLinesOpt.getValue();
				// Clamped here: nothing between the Options dialog and this
				// enforces the range in the description.
				if (lines < 0) {
					lines = 0;
				}
				if (lines > WordSuggestions.MAX_LINES) {
					lines = WordSuggestions.MAX_LINES;
				}
				mWordSuggestions.setMaxLines(lines);
			}
			BaseOption ghostOpt = (BaseOption) group.findOptionByKey("word_complete_ghost");
			mWordSuggestionsGhost = ghostOpt != null
					&& ghostOpt.getValue() instanceof Boolean
					&& (Boolean) ghostOpt.getValue();
			BaseOption looseOpt = (BaseOption) group.findOptionByKey("word_complete_loose");
			mWordSuggestions.setLooseMatching(looseOpt != null
					&& looseOpt.getValue() instanceof Boolean
					&& (Boolean) looseOpt.getValue());
			BaseOption whereOpt =
					(BaseOption) group.findOptionByKey("word_complete_where");
			mWordSuggestionsWhere = whereOpt != null
					&& whereOpt.getValue() instanceof Integer
					? (Integer) whereOpt.getValue() : WordSuggestions.DEFAULT_WHERE;
			if (mWordSuggestionsWhere < WordSuggestions.WHERE_FLOATING
					|| mWordSuggestionsWhere > WordSuggestions.WHERE_NONE) {
				mWordSuggestionsWhere = WordSuggestions.DEFAULT_WHERE;
			}
			mWordSuggestionsOverlay =
					mWordSuggestionsWhere == WordSuggestions.WHERE_FLOATING;
			// A folded bar is a floating-bar state. Leaving it set while the bar
			// is elsewhere or gone means the grip comes back folded when the
			// player floats it again, with nothing having been tapped.
			if (!mWordSuggestionsOverlay) {
				mWordSuggestionsCollapsed = false;
			}
			BaseOption opacityOpt =
					(BaseOption) group.findOptionByKey("word_complete_opacity");
			if (opacityOpt != null && opacityOpt.getValue() instanceof Integer) {
				mWordSuggestionsOpacity = (Integer) opacityOpt.getValue();
			}
			BaseOption persistOpt =
					(BaseOption) group.findOptionByKey("word_complete_persist");
			mWordSuggestionsPersist = persistOpt != null
					&& persistOpt.getValue() instanceof Boolean
					&& (Boolean) persistOpt.getValue();
			// Turning the bar off is also the way out of a collapsed one, so a
			// player cannot end up with a grip and no way to read what it hides.
			if (!mWordSuggestionsPersist) {
				mWordSuggestionsCollapsed = false;
			}
			refreshWordSuggestions();

			BaseOption histOpt = (BaseOption) group.findOptionByKey("input_history_size");
			if (histOpt != null && histOpt.getValue() instanceof Integer) {
				history.setMax((Integer) histOpt.getValue());
			}
			// Session log enable/directory are owned by :stellar
			// (Connection → ConnectionSessionLog). Do not mirror them into the
			// UI process's SessionLogger statics — those caches exist twice and
			// never see each other (audit finding 7).
			
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
			
			mCompatibilityMode = (Boolean)((BaseOption)group.findOptionByKey("compatibility_mode")).getValue();
			applyInputConnectionMode();
			
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
		com.resurrection.blowtorch2.lib.util.StartupProbe.mark("ui.loadSettings leave");

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
		if (mInputBox != null) {
			mInputBox.setAllowSuggestions(useSuggestions);
		}

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

	/** True while the server echoes for us (telnet ECHO) — the input bar is masked. */
	private boolean mLocalEchoOff = false;

	@Override
	public void setLocalEchoOff(final boolean off) {
		if (off == mLocalEchoOff) {
			return;
		}
		mLocalEchoOff = off;
		// Reuse the one place that owns the input field's type flags rather than
		// setting them from two directions.
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
		if (mLocalEchoOff) {
			// Server said IAC WILL ECHO — it is taking over echoing, which on a MUD
			// means a password prompt. Hide the characters and keep the keyboard from
			// learning them. PasswordTransformationMethod does the masking; we deliberately
			// do not set TYPE_TEXT_VARIATION_PASSWORD — that advertises a credential field
			// to Autofill / password managers (Bitwarden). BetterEditText returns
			// AUTOFILL_TYPE_NONE (Android 14+) and, while suggestions are off here,
			// IME_FLAG_NO_PERSONALIZED_LEARNING so the keyboard does not store the password.
			type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			mInputBox.setAllowSuggestions(false);
			mInputBox.setInputType(type);
			mInputBox.setMaxLines(1);
			mInputBox.setSingleLine(true);
			// After setSingleLine, which installs SingleLineTransformationMethod and
			// would otherwise replace this one and un-hide the typed line.
			mInputBox.setTransformationMethod(
					android.text.method.PasswordTransformationMethod.getInstance());
			mInputBox.setHorizontallyScrolling(true);
			restartInputConnection();
			scheduleInputActionLayoutRefresh();
			refreshGameChrome();
			return;
		}
		mInputBox.setTransformationMethod(null);
		mInputBox.setAllowSuggestions(useSuggestions);
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
		restartInputConnection();
		scheduleInputActionLayoutRefresh();
		refreshGameChrome();
	}

	/** The IME copies inputType into EditorInfo when the connection is made, so a
	 * type change with the keyboard already open needs the connection rebuilt. */
	private void restartInputConnection() {
		try {
			InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
			if (imm != null && mInputBox != null) {
				imm.restartInput(mInputBox);
			}
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"MainWindow.restartInputConnection", e);
		}
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
		
		public void inputBarInsertWord(String word) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_INPUT_INSERT_WORD, word));
		}

		public void vocabularyText(String text) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_VOCABULARY_TEXT, text));
		}

		public void vocabularyReset() throws RemoteException {
			myhandler.sendEmptyMessage(MESSAGE_VOCABULARY_RESET);
		}

		public void pickCompletion(int index) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_PICK_COMPLETION, index, 0));
		}

		public void promptLine(String text) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_PROMPT_LINE, text));
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

		public void inputBarEditTools(int mode) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_INPUT_EDIT_TOOLS, mode, 0));
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

		@Override
		public void frameUi(int action) throws RemoteException {
			myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_FRAME_UI, action, 0));
		}
	};
	
	boolean windowsInitialized = false;
	/** Retries left before we stop asking the service for windows. ~6 s in total. */
	private static final int MAX_WINDOW_TOKEN_ATTEMPTS = 20;
	private static final int WINDOW_TOKEN_RETRY_MS = 300;
	private int mWindowTokenAttempts = 0;
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

		myhandler.removeMessages(MESSAGE_RETRYWINDOWTOKENS);
		mWindowTokenAttempts = 0;
		if(!fetchWindowTokens()) {
			// The connection may still be loading its plugins; loadPlugins() empties
			// mWindows before refilling it, so an empty answer here is normal. Come
			// back on the handler instead of blocking the UI thread on it.
			scheduleWindowTokenRetry();
			return;
		}
		finishInitializeWindows();
	}

	private void scheduleServiceRebind() {
		if (myhandler == null) {
			return;
		}
		myhandler.removeMessages(MESSAGE_REBINDSERVICE);
		myhandler.sendEmptyMessageDelayed(MESSAGE_REBINDSERVICE, REBIND_DELAY_MS);
	}

	/** Reconnect to StellarService after its process died under us.
	 *
	 * <p>bindService was called with flag 0, which does not keep the service alive and
	 * does not bring the binding back by itself. The system does restart the service —
	 * it is a started foreground service — but nothing reattaches this activity to it,
	 * so without this the app sits there with a null service until it is killed and
	 * relaunched by hand. */
	void rebindServiceAfterDeath() {
		if (isFinishing() || isBound || service != null) {
			return;
		}
		mRebindAttempts++;
		boolean asked = false;
		try {
			String serviceBindAction = ConfigurationLoader.getConfigurationValue("serviceBindAction", this);
			asked = this.bindService(new Intent(serviceBindAction, null, this, StellarService.class),
					mConnection, 0);
		} catch (Exception e) {
			Log.e("BlowTorch", "rebind to StellarService failed", e);
		}
		if (asked) {
			// onServiceConnected takes it from here: it re-registers the callback and,
			// when the connection object outlived the socket, asks for a reconnect.
			isBound = true;
			return;
		}
		if (mRebindAttempts >= MAX_REBIND_ATTEMPTS) {
			String why = "Could not rebind to the connection service after "
					+ mRebindAttempts + " tries; the app is running without one.";
			Log.e("BlowTorch", why);
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logError(this, "rebindService", why);
			return;
		}
		scheduleServiceRebind();
	}

	/** Ask the service for the window list. False when it has none to give yet. */
	private boolean fetchWindowTokens() {
		if(service == null) {
			// onServiceDisconnected() nulls this; a queued INITIALIZEWINDOWS can land after.
			mWindows = null;
			return false;
		}
		try {
			mWindows = service.getWindowTokens();
		} catch (RemoteException e) {
			Log.e("BlowTorch","getWindowTokens failed", e);
			mWindows = null;
		}
		return mWindows != null && mWindows.length > 0;
	}

	private void scheduleWindowTokenRetry() {
		myhandler.removeMessages(MESSAGE_RETRYWINDOWTOKENS);
		myhandler.sendEmptyMessageDelayed(MESSAGE_RETRYWINDOWTOKENS, WINDOW_TOKEN_RETRY_MS);
	}

	/** Handler side of the retry. Replaces a wait() loop that used to run on the UI thread. */
	void retryWindowTokens() {
		if(mWindows != null && mWindows.length > 0) {
			return;
		}
		mWindowTokenAttempts++;
		if(fetchWindowTokens()) {
			finishInitializeWindows();
			return;
		}
		if(mWindowTokenAttempts >= MAX_WINDOW_TOKEN_ATTEMPTS) {
			// Loading failed in :stellar, or the binder is dead. Leave the UI alive
			// and let the next markWindowsDirty()/loadWindowSettings() start over.
			String why = "Gave up waiting for window tokens after "
					+ mWindowTokenAttempts + " tries; the connection has no windows.";
			Log.e("BlowTorch", why);
			// Also to the on-device log: this is the sort of silence worth a record.
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logError(this, "initiailizeWindows", why);
			windowsInitialized = false;
			mPendingInitialConnect = false;
			myhandler.removeMessages(MESSAGE_CONNECT_WHEN_READY);
			return;
		}
		scheduleWindowTokenRetry();
	}

	/** Everything that needs a populated mWindows. */
	private void finishInitializeWindows() {
			ApplicationInfo ai = null;
			try {
				ai = this.getPackageManager().getApplicationInfo(this.getPackageName(), PackageManager.GET_META_DATA);
			} catch (NameNotFoundException e) {
				// TODO Auto-generated catch block
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor("MainWindow.finishInitializeWindows", e);
			}
			String dataDir = ai.dataDir;

			com.resurrection.blowtorch2.lib.util.StartupProbe.mark("connect.finishInit enter");
			try {
				refreshExtraTextSlotsFromSettings(service.getSettings());
			} catch (RemoteException e) {
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.finishInitializeWindows", e);
			}
			com.resurrection.blowtorch2.lib.util.StartupProbe.mark("connect.getSettings+slots");

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
				com.resurrection.blowtorch2.lib.util.StartupProbe.mark(
						"connect.initWindow " + w.getName());

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
					com.resurrection.blowtorch2.lib.util.StartupProbe.mark(
							"connect.onCreate lua " + w.getName());
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
		com.resurrection.blowtorch2.lib.util.StartupProbe.mark("connect.chrome");
		ensureMapperOverlay();
		com.resurrection.blowtorch2.lib.util.StartupProbe.mark("connect.mapper overlay");
		ensureExtraTextOverlays();
		com.resurrection.blowtorch2.lib.util.StartupProbe.mark("connect.extra text");
		ensureFloatingButtons();
		raiseFloatingButtons();
		com.resurrection.blowtorch2.lib.util.StartupProbe.mark("connect.floating buttons");
		// Windows (and extra-text slots) now have live binders. End the hold that
		// onPause / a recents kill left behind — not earlier in onServiceConnected,
		// which raced ahead of registerWindowCallback and dropped the hand-over.
		try {
			if (service != null) {
				service.windowShowing(true);
			}
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"MainWindow.finishInitializeWindows", e);
		}
		// Window tokens (and Options → Window prefs) are live — re-layout Edit/Send.
		scheduleInputActionLayoutRefresh();
		com.resurrection.blowtorch2.lib.util.StartupProbe.mark("connect.finishInit leave");
		//Debug.stopMethodTracing();
	}

	/**
	 * Push Options → Miscellaneous → Overflow button … onto the ⋮.
	 *
	 * <p>Read fresh on every settings apply; missing options mean an older
	 * profile, which keeps the look the drawable always had.
	 *
	 * @param group Program settings.
	 */
	private void applyOverflowAppearance(final SettingsGroup group) {
		if (group == null || chrome == null) {
			return;
		}
		int opacity = com.resurrection.blowtorch2.lib.service.plugin
				.ConnectionSettingsPlugin.OVERFLOW_OPACITY_DEFAULT;
		boolean background = true;
		boolean border = true;
		BaseOption opacityOpt = (BaseOption) group.findOptionByKey("overflow_button_opacity");
		if (opacityOpt != null && opacityOpt.getValue() instanceof Integer) {
			opacity = (Integer) opacityOpt.getValue();
		}
		BaseOption bgOpt = (BaseOption) group.findOptionByKey("overflow_button_background");
		if (bgOpt != null && bgOpt.getValue() instanceof Boolean) {
			background = (Boolean) bgOpt.getValue();
		}
		BaseOption borderOpt = (BaseOption) group.findOptionByKey("overflow_button_border");
		if (borderOpt != null && borderOpt.getValue() instanceof Boolean) {
			border = (Boolean) borderOpt.getValue();
		}
		chrome.setOverflowAppearance(opacity, background, border);
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
				public String getConnectionHost() {
					return MainWindow.this.getConnectionHost();
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
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.ensureMapperOverlay", e);
					} catch (java.io.UnsupportedEncodingException e) {
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor("MainWindow.ensureMapperOverlay", e);
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
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.ensureMapperOverlay", e);
					}
				}
			});
		}
		if (mapperController != null) {
			mapperOverlay.bind(mapperController);
		}
		// Put the map back if that is how the player left it. Closing it was
		// never meant to be a decision they have to repeat on every connect.
		mapperOverlay.restoreVisibility();
		raiseFloatingButtons();
	}

	ChromeController getChromeController() {
		return chrome;
	}

	/**
	 * UI Lua ({@code buttonwindow.notifyFloatingButtonsChanged}) pushes a JSON
	 * snapshot of floating buttons. Keep this on the Activity so GetActivity()
	 * can call it without a separate bridge object.
	 */
	public void onFloatingButtonsChanged(final String json) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ensureFloatingButtons();
				if (floatingButtons != null) {
					floatingButtons.onButtonsChanged(json);
				}
			}
		});
	}

	/** Chrome IME lift — Mode A floaters sit above the keyboard; Mode B stay put. */
	void onFloatingButtonsImeLift(int liftPx) {
		if (floatingButtons != null) {
			floatingButtons.onImeLiftChanged(liftPx);
		}
	}

	private void ensureFloatingButtons() {
		if (floatingButtons == null) {
			floatingButtons = new FloatingButtonController(new FloatingButtonController.Host() {
				@Override
				public MainWindow getMainWindow() {
					return MainWindow.this;
				}

				@Override
				public Handler getUiHandler() {
					return myhandler;
				}

				@Override
				public void sendCommand(String text) {
					if (text == null || text.length() == 0 || service == null) {
						return;
					}
					try {
						service.sendData(text.getBytes(service.getEncoding()));
					} catch (RemoteException e) {
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
								"MainWindow.floatingButtons.sendCommand", e);
					} catch (java.io.UnsupportedEncodingException e) {
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
								"MainWindow.floatingButtons.sendCommand", e);
					}
				}

				@Override
				public void loadButtonSet(String name) {
					if (name == null || name.length() == 0 || service == null) {
						return;
					}
					try {
						service.pluginXcallS("button_window", "loadButtonSet", name);
					} catch (RemoteException e) {
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
								"MainWindow.floatingButtons.loadButtonSet", e);
					}
				}

				@Override
				public void persistFloatPosition(int buttonIndex, int floatX, int floatY,
						int gridX, int gridY) {
					// Write the pair for the orientation the drag happened in, so
					// the other one keeps whatever the player set there.
					boolean land = getResources().getConfiguration().orientation
							== android.content.res.Configuration.ORIENTATION_LANDSCAPE;
					String payload = "return {index=" + buttonIndex
							+ (land ? ",floatXLand=" : ",floatX=") + floatX
							+ (land ? ",floatYLand=" : ",floatY=") + floatY
							// Same drop as a grid position, so the button on the grid is
							// where its floating copy was left — and the next rebuild,
							// which places from the grid, puts it back in that spot.
							+ (gridX != Integer.MIN_VALUE
									? ",gridX=" + gridX + ",gridY=" + gridY : "")
							+ "}";
					windowCall("button_window", "applyFloatPosition", payload);
					windowCall("button_window", "persistFloatingButtons", "");
				}

				@Override
				public boolean isFloatingButtonsEnabled() {
					return floatingButtonsEnabled;
				}

				@Override
				public boolean showGestureHints() {
					return true;
				}

				@Override
				public boolean hapticPressEnabled() {
					return true;
				}

				@Override
				public boolean hapticFlipEnabled() {
					return true;
				}

				@Override
				public int getImeLiftPx() {
					return chrome != null ? chrome.getImeLiftPx() : 0;
				}

				@Override
				public int refreshImeLiftPx() {
					// The value the insets listener stored, not a recomputation.
					return chrome != null ? chrome.getImeLiftPx() : 0;
				}
			});
		}
		floatingButtons.bringUnderChrome();
	}

	/** Re-raise floaters above frames/extra text/mapper, still under ⋮ (D4). */
	void raiseFloatingButtons() {
		if (floatingButtons != null) {
			floatingButtons.bringUnderChrome();
		}
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
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.ensureExtraTextOverlays", e);
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
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.ensureExtraTextOverlays", e);
					}
				}

				@Override
				public void unregisterWindowCallback(WindowToken token,
						com.resurrection.blowtorch2.lib.window.Window window) {
					if (window == null || service == null) {
						return;
					}
					try {
						String key = token != null ? token.getDisplayHost() : null;
						if (key == null || key.length() == 0) {
							key = getConnectionDisplay();
						}
						if (key == null || key.length() == 0) {
							return;
						}
						service.unregisterWindowCallback(key, window.getCallback());
					} catch (RemoteException e) {
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.ensureExtraTextOverlays", e);
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
						// The slots are in the service after the call above; the
						// file write need not finish before this returns.
						com.resurrection.blowtorch2.lib.util.SettingsSaver.saveInBackground(service);
					} catch (RemoteException e) {
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.ensureExtraTextOverlays", e);
					}
				}
			});
		}
		if (extraTextSlotsCache.isEmpty() && service != null) {
			try {
				refreshExtraTextSlotsFromSettings(service.getSettings());
			} catch (RemoteException e) {
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.ensureExtraTextOverlays", e);
			}
		}
		extraTextOverlay.sync();
		raiseFloatingButtons();
	}

	/**
	 * Build the frame overlay controller if it is not up yet.
	 *
	 * <p>Nothing here costs anything until a server opens a frame, which most
	 * never will — {@code mudstd.frame} is off unless the player enables it.
	 */
	private void ensureFrameOverlays() {
		if (frameOverlay != null) {
			return;
		}
		frameOverlay = new FrameOverlayController(new FrameOverlayController.Host() {
			@Override
			public MainWindow getMainWindow() {
				return MainWindow.this;
			}

			@Override
			public void closeFrameOnServer(String id) {
				try {
					if (service != null) {
						service.closeFrameByUser(id);
					}
				} catch (RemoteException e) {
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
							"MainWindow.closeFrameOnServer", e);
				}
			}

			@Override
			public void reportFrameSize(String id, int widthPx, int heightPx) {
				try {
					if (service != null) {
						service.reportFrameSize(id, widthPx, heightPx);
					}
				} catch (RemoteException e) {
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
							"MainWindow.reportFrameSize", e);
				}
			}
		});
	}

	/**
	 * Collect the frame events the service is holding and put them on screen.
	 *
	 * <p>The service sends a nudge, not the events themselves, so several
	 * packets that arrived close together are drained in one pass — a server
	 * opening a frame and filling it sends {@code open} then {@code image} back
	 * to back, and laying out twice for that is work for nothing.
	 */
	private void handleFrameUiAction() {
		ensureFrameOverlays();
		String json = null;
		try {
			if (service != null) {
				json = service.takeFrameEvents();
			}
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"MainWindow.handleFrameUiAction", e);
			return;
		}
		frameOverlay.apply(com.resurrection.blowtorch2.lib.service.FrameEvent.parse(json));
	}

	/**
	 * Put back frames the server still believes are open.
	 *
	 * <p>The activity is destroyed and rebuilt on a rotation or a return from
	 * the launcher while the service and its connection carry straight on. The
	 * server was told the frame opened and has had no reason to send it again,
	 * so without this the window would be gone and only the server would think
	 * otherwise.
	 */
	private void restoreOpenFrames() {
		ensureFrameOverlays();
		String json = null;
		try {
			if (service != null) {
				json = service.getOpenFramesJson();
			}
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"MainWindow.restoreOpenFrames", e);
			return;
		}
		frameOverlay.apply(com.resurrection.blowtorch2.lib.service.FrameEvent.parse(json));
		raiseFloatingButtons();
	}

	private void handleExtraTextUiAction(int action) {
		// action is typically Connection.MESSAGE_EXTRA_TEXT_CHANGED (48).
		try {
			if (service != null) {
				refreshExtraTextSlotsFromSettings(service.getSettings());
			}
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.handleExtraTextUiAction", e);
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
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"MainWindow.getRecentMainBufferText", e);
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
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.initWindow", e);
			}
			tmp.setBufferText(w.isBufferText());
			// Adopt the parceled buffer *before* registering. registerWindowCallback
			// may resetWithRawDataIncoming when the previous UI process died; that
			// call is posted to the Window handler, so landing it on the tree we
			// already adopted (rather than the empty constructor buffer) avoids a
			// race where setBuffer later swapped the tree out from under the replay.
			if(w.getBuffer() != null) {
				tmp.setBuffer(w.getBuffer());
			}
			try {
				service.registerWindowCallback(w.getDisplayHost(),w.getName(),tmp.getCallback());
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.initWindow", e);
			}
			
			//attempt to construct a good-ly relative layout to hold the window and any children 
			
			tmp.setVisibility(View.VISIBLE);
			
			long dur = System.currentTimeMillis() - start;
			Log.e("WINDOW","Init Window ("+w.getName()+"): took:" + dur + " millis.");
		}
		// Overlays set to "same as main window" read their speed off the main
		// Window, which only just entered windowMap. Any sync() that ran before
		// this point resolved against the default and would otherwise stay there
		// for the whole session.
		if (extraTextOverlay != null) {
			extraTextOverlay.refreshScrollSpeeds();
		}
	}
	
	
	public void cleanupWindows() {
		if (extraTextOverlay != null) {
			extraTextOverlay.detach();
		}
		if (floatingButtons != null) {
			floatingButtons.detach();
			floatingButtons = null;
		}
		if (frameOverlay != null) {
			frameOverlay.detach();
			frameOverlay = null;
		}
		RelativeLayout rl = (RelativeLayout)this.findViewById(R.id.window_container);
		if(mWindows == null || rl == null) return;
		for(Object x : mWindows) {
			if(x instanceof WindowToken) {
				WindowToken w = (WindowToken)x;
				if (isExtraTextSlotWindow(w.getName())) {
					continue;
				}
				View tmp = rl.findViewWithTag(w.getName());
				
				if(tmp instanceof com.resurrection.blowtorch2.lib.window.Window) {
					try {
						if (service != null) {
							service.unregisterWindowCallback(w.getDisplayHost(), ((com.resurrection.blowtorch2.lib.window.Window)tmp).getCallback());
						}
					} catch (RemoteException e) {
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.cleanupWindows", e);
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
		// Only when the Intent carries it. Writing false for an Intent that
		// simply has no opinion would turn TLS off for a world that had it on,
		// which is the silent downgrade this whole path is written to avoid.
		if (intent.hasExtra("TLS")) {
			edit.putBoolean("CONNECT_TLS", intent.getBooleanExtra("TLS", false));
		}
		edit.apply();
	}

	/**
	 * Keep Activity Intent + CONNECT_TO prefs on the service clutch after
	 * {@code .switch} / notification / resume. {@link #getConnectionDisplay()}
	 * prefers Intent extras, so prefs alone are not enough.
	 */
	private void rememberForegroundConnection(String display) {
		if (display == null || display.isEmpty()) {
			return;
		}
		SharedPreferences prefs = getSharedPreferences("CONNECT_TO", Context.MODE_PRIVATE);
		// Prefer host/port only when prefs already name this same display
		// (StellarService.persistForegroundConnection writes them together).
		String host = null;
		String port = null;
		if (display.equals(prefs.getString("CONNECT_TO", null))) {
			host = prefs.getString("CONNECT_HOST", null);
			port = prefs.getString("CONNECT_PORT", null);
		}
		if (host == null || host.isEmpty()) {
			host = getConnectionHost();
		}
		if (port == null || port.isEmpty()) {
			port = Integer.toString(getConnectionPort());
		}
		// TLS is resolved from the same source as host and port above, so a
		// switch cannot pair one world's host with another world's answer here.
		boolean tls;
		if (display.equals(prefs.getString("CONNECT_TO", null))) {
			tls = prefs.getBoolean("CONNECT_TLS", false);
		} else {
			tls = getConnectionTls();
		}
		Intent base = getIntent();
		Intent next = base != null ? new Intent(base) : new Intent();
		next.putExtra("DISPLAY", display);
		next.putExtra("HOST", host);
		next.putExtra("PORT", port);
		next.putExtra("TLS", tls);
		setIntent(next);
		saveConnectionExtras(next);
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

	/**
	 * Whether this world connects over TLS, resolved the same way as host and
	 * port: Intent first, then the CONNECT_TO prefs that survive a process
	 * death. It has to follow exactly the same road as those two — a resume
	 * that recovered the host but forgot this would reconnect in plain text
	 * without saying so.
	 *
	 * @return True to use TLS.
	 */
	private boolean getConnectionTls() {
		Intent intent = getIntent();
		if (intent != null && intent.hasExtra("TLS")) {
			return intent.getBooleanExtra("TLS", false);
		}
		// The stored answer belongs to whichever world was opened last, so it is
		// only usable when this is that same world. An Intent naming world B
		// while the preferences still describe world A must not inherit A's
		// answer — that is how a plain world would try TLS, or worse, an
		// encrypted one quietly go in the clear.
		SharedPreferences prefs = getSharedPreferences("CONNECT_TO", Context.MODE_PRIVATE);
		String wanted = intent != null ? intent.getStringExtra("DISPLAY") : null;
		String stored = prefs.getString("CONNECT_TO", null);
		if (wanted != null && stored != null && !wanted.equals(stored)) {
			return false;
		}
		return prefs.getBoolean("CONNECT_TLS", false);
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
		bindGhostTap();
		// loadSettings can arrive before this runs, and refreshWordSuggestions
		// gives up with the panel hidden while mInputBox is null. Nothing else
		// asks again until the first keystroke — so a bar told to stay put would
		// not be there until you typed something, which is the one thing it is
		// for. Posted, so it happens after this view has been laid out.
		mInputBox.post(new Runnable() {
			@Override
			public void run() {
				refreshWordSuggestions();
			}
		});

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

	/** Attach gesture handling to the chrome around the game view.
	 *
	 * The listeners sit alongside the existing click handling and only claim an
	 * event once a swipe or hold actually fired, so tapping Send, typing in the
	 * input bar and long-pressing the overflow all behave as before. They read
	 * the bindings from ChromeGestures.current() at gesture time, so edits take
	 * effect without re-attaching anything.
	 */
	private void attachChromeGestureListeners() {
		float density = getResources().getDisplayMetrics().density;
		ChromeGestureTouchListener.CommandSink sink =
				new ChromeGestureTouchListener.CommandSink() {
			@Override
			public void runChromeCommand(final String command) {
				runChromeGestureCommand(command);
			}
		};
		attachChromeGesture(findViewById(R.id.input_edit_toggle),
				ChromeGestures.TARGET_EDIT, density, sink);
		attachChromeGesture(findViewById(R.id.input_send),
				ChromeGestures.TARGET_SEND, density, sink);
		attachChromeGesture(findViewById(R.id.overflow_menu),
				ChromeGestures.TARGET_OVERFLOW, density, sink);
	}

	private void attachChromeGesture(final View view, final String target,
			final float density, final ChromeGestureTouchListener.CommandSink sink) {
		if (view == null) {
			return;
		}
		view.setOnTouchListener(new ChromeGestureTouchListener(target, density, sink));
	}

	/** Run a command a chrome gesture resolved to.
	 *
	 * Goes down the same path as a button press, so dot commands work exactly as
	 * they do from a button — which is the point of the feature.
	 */
	private void runChromeGestureCommand(final String command) {
		if (command == null || command.length() == 0) {
			return;
		}
		myhandler.sendMessage(myhandler.obtainMessage(MESSAGE_SENDBUTTONDATA, command));
	}

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

		attachChromeGestureListeners();

		if (mInputBox != null) {
			keepLastTextWatcher = new android.text.TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
					if (keepLastSuppress || mInputBox == null) {
						return;
					}
					// The selection here is still the pre-edit one, which is the only
					// place it can be read: by onTextChanged the IME has already moved
					// the cursor. The branch below means "the IME appended instead of
					// replacing the selection", so it needs to know there *was* a
					// selection. Tapping the word to deselect leaves start == end, and
					// then a space is an ordinary keystroke at the end, not a replace.
					keepLastWasSelected = mInputBox.getSelectionStart() == 0
							&& mInputBox.getSelectionEnd() == keepLastReplaceLength
							&& keepLastReplaceLength > 0;
				}
				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
					if (keepLastSuppress) {
						return;
					}
					if (!isKeepLast || keepLastReplaceLength <= 0 || count <= 0) {
						return;
					}
					int oldLen = s.length() - count + before;
					if (keepLastWasSelected && before == 0 && start == oldLen && oldLen == keepLastReplaceLength) {
						// IME appended at the end instead of replacing the selection.
						// Note it here, apply it in afterTextChanged: this callback
						// runs while TextView is still walking its watcher list, and
						// editing the text from inside it re-enters that walk.
						keepLastPendingReplace = s.subSequence(start, start + count).toString();
						keepLastReplaceLength = 0;
						historyWidgetKept = false;
						return;
					}
					// Anything else is the player editing the line by hand: either
					// inside it, or at the end after deselecting it. Nothing left to
					// replace, so drop the trap and the "↑ skips one" flag with it.
					keepLastReplaceLength = 0;
					historyWidgetKept = false;
				}
				@Override
				public void afterTextChanged(android.text.Editable s) {
					if (keepLastSuppress || keepLastPendingReplace == null) {
						return;
					}
					final String typed = keepLastPendingReplace;
					keepLastPendingReplace = null;
					// A flag rather than removeTextChangedListener: TextView walks
					// its watchers by index with the count taken up front, so a
					// watcher that unregisters itself mid-dispatch shifts the rest
					// down and the one after it never sees the event. Here that is
					// the watcher which re-measures the input bar, so the bar would
					// not grow on the keystroke that replaced the kept line.
					keepLastSuppress = true;
					try {
						s.replace(0, s.length(), typed);
						mInputBox.setSelection(typed.length());
					} finally {
						keepLastSuppress = false;
					}
				}
			};
			mInputBox.addTextChangedListener(keepLastTextWatcher);
			// Its own watcher rather than a branch inside the one above: that one
			// is the Keep Last selection dance and has a suppress flag it must
			// honour, and completion has nothing to do with any of it.
			mInputBox.addTextChangedListener(new android.text.TextWatcher() {
				@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
				@Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
				@Override
				public void afterTextChanged(android.text.Editable s) {
					if (mWordSuggestionsOn) {
						refreshWordSuggestions();
					}
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
		setInputEditToolsExpanded(expanded, false);

		toggle.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				View t = findViewById(R.id.input_edit_tools);
				boolean nowExpanded = t == null || t.getVisibility() != View.VISIBLE;
				setInputEditToolsExpanded(nowExpanded, true);
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
		up.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inputCursorVertical(-1);
			}
		});
		down.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inputCursorVertical(1);
			}
		});
		end.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inputCursorToEnd();
			}
		});
		refreshInputActionLayout();
	}

	private void refreshInputActionLayout() {
		ensureInputActionColumn();
	}

	private void scheduleInputActionLayoutRefresh() {
		if (mInputBox == null) {
			View actions = findViewById(R.id.input_action_buttons);
			if (actions != null) {
				actions.post(new Runnable() {
					@Override
					public void run() {
						ensureInputActionColumn();
					}
				});
			}
			return;
		}
		mInputBox.removeCallbacks(mRefreshInputActionLayoutRunnable);
		mInputBox.post(mRefreshInputActionLayoutRunnable);
	}

	private final Runnable mRefreshInputActionLayoutRunnable = new Runnable() {
		@Override
		public void run() {
			ensureInputActionColumn();
		}
	};

	/**
	 * Options → Window controls which of Edit / Send appear.
	 * Both shown: side-by-side. One shown: that button only. Both off: full-width
	 * input ({@code input_action_buttons} GONE). Visibility is preference-only —
	 * no multiline auto-hide.
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

		boolean showEdit = showInputEditButtonPref();
		boolean showSend = showInputSendButtonPref();

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
			int oldEditVis = edit.getVisibility();
			int oldSendVis = mInputSendButton.getVisibility();
			// Measure with VISIBLE so GONE buttons still report a real width.
			edit.setVisibility(View.VISIBLE);
			mInputSendButton.setVisibility(View.VISIBLE);
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
			edit.setVisibility(oldEditVis);
			mInputSendButton.setVisibility(oldSendVis);
		}
		final int editNat = mActionEditWidthPx;
		final int sendNat = mActionSendWidthPx;

		boolean changed = false;
		if (!showEdit && !showSend) {
			if (actions.getVisibility() != View.GONE) {
				actions.setVisibility(View.GONE);
				changed = true;
			}
			edit.setVisibility(View.GONE);
			mInputSendButton.setVisibility(View.GONE);
			if (changed) {
				actions.requestLayout();
			}
			RelativeLayout rlGone = (RelativeLayout) findViewById(R.id.window_container);
			chrome.bringGameplayChromeToFront(rlGone);
			refreshGameChrome();
			return;
		}
		if (actions.getVisibility() != View.VISIBLE) {
			actions.setVisibility(View.VISIBLE);
			changed = true;
		}

		final int colW;
		if (showEdit && showSend) {
			colW = editNat + gap + sendNat;
		} else if (showSend) {
			colW = sendNat;
		} else {
			colW = editNat;
		}

		if (actions.getOrientation() != LinearLayout.HORIZONTAL) {
			actions.setOrientation(LinearLayout.HORIZONTAL);
			changed = true;
		}
		actions.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);

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

		// Same height as the single-line input row (textinput minHeight 28dip).
		int btnH = Math.max(1, (int) (28 * density + 0.5f));
		LinearLayout.LayoutParams editLp = (edit.getLayoutParams() instanceof LinearLayout.LayoutParams)
				? (LinearLayout.LayoutParams) edit.getLayoutParams()
				: new LinearLayout.LayoutParams(editNat, btnH);
		LinearLayout.LayoutParams sendLp = (mInputSendButton.getLayoutParams() instanceof LinearLayout.LayoutParams)
				? (LinearLayout.LayoutParams) mInputSendButton.getLayoutParams()
				: new LinearLayout.LayoutParams(sendNat, btnH);
		editLp.width = editNat;
		sendLp.width = sendNat;
		editLp.height = btnH;
		sendLp.height = btnH;
		editLp.weight = 0f;
		sendLp.weight = 0f;
		editLp.setMargins(0, 0, showEdit && showSend ? gap : 0, 0);
		sendLp.setMargins(0, 0, 0, 0);
		edit.setLayoutParams(editLp);
		mInputSendButton.setLayoutParams(sendLp);
		edit.setMinWidth(editNat);
		mInputSendButton.setMinWidth(sendNat);
		edit.setMaxLines(1);
		mInputSendButton.setMaxLines(1);
		int editVis = showEdit ? View.VISIBLE : View.GONE;
		int sendVis = showSend ? View.VISIBLE : View.GONE;
		if (edit.getVisibility() != editVis) {
			edit.setVisibility(editVis);
			changed = true;
		}
		if (mInputSendButton.getVisibility() != sendVis) {
			mInputSendButton.setVisibility(sendVis);
			changed = true;
		}
		if (!showEdit) {
			View tools = findViewById(R.id.input_edit_tools);
			if (tools != null && tools.getVisibility() == View.VISIBLE) {
				setInputEditToolsExpanded(false, true);
			}
		}

		// WRAP_CONTENT so soft-wrap can grow the row up to maxLines.
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
		refreshGameChrome();
	}

	/**
	 * Options → Window → Show Edit button? Default on.
	 * When off, use {@code .editpanel on|off} for the tools strip.
	 */
	private boolean showInputEditButtonPref() {
		return readMainWindowBooleanOption("input_bar_show_edit", true);
	}

	/**
	 * Options → Window → Show Send button? Default on.
	 * When off, send with IME Send/Enter or {@code .kb flush}.
	 */
	private boolean showInputSendButtonPref() {
		return readMainWindowBooleanOption("input_bar_show_send", true);
	}

	private boolean readMainWindowBooleanOption(String key, boolean defaultValue) {
		if (mWindows != null) {
			for (WindowToken tok : mWindows) {
				if (tok == null || !"mainDisplay".equals(tok.getName())) {
					continue;
				}
				Object opt = tok.getSettings().findOptionByKey(key);
				if (opt instanceof com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption) {
					return (Boolean) ((com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption) opt)
							.getValue();
				}
			}
		}
		return defaultValue;
	}

	/** Handler for {@link #MESSAGE_INPUT_EDIT_TOOLS}: arg1 0=toggle, 1=on, 2=off. */
	private void applyInputEditToolsMessage(int mode) {
		View tools = findViewById(R.id.input_edit_tools);
		boolean expanded;
		if (mode == com.resurrection.blowtorch2.lib.service.StellarService.INPUT_EDIT_TOOLS_ON) {
			expanded = true;
		} else if (mode == com.resurrection.blowtorch2.lib.service.StellarService.INPUT_EDIT_TOOLS_OFF) {
			expanded = false;
		} else {
			expanded = tools == null || tools.getVisibility() != View.VISIBLE;
		}
		setInputEditToolsExpanded(expanded, true);
	}

	/**
	 * Expand or collapse the Edit tools strip (Sel/Cut/… pad above the input row).
	 * Shared by the Edit button and {@code .editpanel}.
	 */
	private void setInputEditToolsExpanded(boolean expanded, boolean persist) {
		View tools = findViewById(R.id.input_edit_tools);
		Button toggle = (Button) findViewById(R.id.input_edit_toggle);
		if (tools == null || toggle == null) {
			return;
		}
		applyInputEditExpanded(tools, toggle, expanded);
		if (persist) {
			getSharedPreferences(PREFS_INPUT_EDIT, Context.MODE_PRIVATE)
					.edit()
					.putBoolean(KEY_EDIT_EXPANDED, expanded)
					.apply();
		}
		refreshGameChrome();
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
		// Extra-text overlays sit in window_container; bring that overlay above
		// siblings so the copy widget is not under another pane. Do not raise
		// chrome over the widget (same rule as the main game window).
		if (windowTag instanceof String && extraTextOverlay != null
				&& extraTextOverlay.managesWindowName((String) windowTag)) {
			View overlay = rl.findViewWithTag("extra_text_overlay:" + windowTag);
			if (overlay != null) {
				overlay.bringToFront();
			}
		}
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
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.shutdownWindow", e);
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

	/**
	 * Collect every enabled trigger carrying a TapAction and hand the rules to
	 * the game window, which matches them while it draws.
	 *
	 * <p>The rule travels rather than the mark: a "this is tappable" flag has no
	 * byte to ride on through the binder the way an ANSI colour does, so the
	 * window has to do the matching on its own side. Cheap enough — this reads
	 * the trigger list once, not once per line.
	 */
	/**
	 * Re-read tappable-word rules soon, coalescing repeats.
	 *
	 * <p>Delayed because the caller is usually a dialog that has just handed an
	 * edited trigger to the service over the binder, and the rules have to be
	 * read back after the service has it.
	 */
	public void scheduleTapRulesRefresh() {
		tapRulesRetries = 0;
		myhandler.removeMessages(MESSAGE_REFRESHTAPRULES);
		myhandler.sendEmptyMessageDelayed(MESSAGE_REFRESHTAPRULES, 250);
	}

	/**
	 * Attempts left while the pieces are not there yet. On a cold start the
	 * resume happens long before the service is bound and the game window is
	 * built, and the old code simply returned — so the rules were never read at
	 * all until something resumed the activity a second time.
	 */
	private int tapRulesRetries;
	private static final int TAP_RULES_MAX_RETRIES = 20;

	private boolean retryTapRulesLater() {
		if (tapRulesRetries >= TAP_RULES_MAX_RETRIES) {
			return false;
		}
		tapRulesRetries++;
		myhandler.removeMessages(MESSAGE_REFRESHTAPRULES);
		myhandler.sendEmptyMessageDelayed(MESSAGE_REFRESHTAPRULES, 500);
		return true;
	}

	/**
	 * True while a background read of the rules is out.
	 *
	 * <p>Only touched on the UI thread — set before the thread starts, cleared
	 * when its result is posted back — so a burst of notifications costs one
	 * read rather than one thread each.
	 */
	private boolean tapRulesFetchInFlight;

	/**
	 * Read the tap rules from the service and hand them to the game window.
	 *
	 * <p>The read happens on a background thread. This used to be a synchronous
	 * {@code getTriggerData()} on the UI thread, which was tolerable when only
	 * an editor did it once, and stopped being tolerable when a trigger rebuild
	 * started asking for it: the service's own thread may be busy with a packet
	 * for tens of milliseconds, and the caller of a synchronous binder call
	 * waits for it — here, the thread that draws the game.
	 *
	 * <p>The patterns are compiled on that background thread too, and only the
	 * finished rule list comes back to the UI thread.
	 */
	public void refreshTapRules() {
		if (service == null) {
			retryTapRulesLater();
			return;
		}
		RelativeLayout rl = (RelativeLayout) findViewById(R.id.window_container);
		if (rl == null) {
			retryTapRulesLater();
			return;
		}
		final com.resurrection.blowtorch2.lib.window.Window main =
				(com.resurrection.blowtorch2.lib.window.Window) rl.findViewWithTag("mainDisplay");
		if (main == null) {
			retryTapRulesLater();
			return;
		}
		if (tapRulesFetchInFlight) {
			// A read is already on its way and it will see the current state of
			// the service. Asking again would only queue a second binder call.
			return;
		}
		final com.resurrection.blowtorch2.lib.service.IConnectionBinder binder = service;
		tapRulesFetchInFlight = true;
		new Thread(new Runnable() {
			public void run() {
				java.util.List<com.resurrection.blowtorch2.lib.window.Window.TapRule> rules =
						new java.util.ArrayList<
								com.resurrection.blowtorch2.lib.window.Window.TapRule>();
				try {
					java.util.List<com.resurrection.blowtorch2.lib.responder.tap.TapRuleData> raw =
							binder.getTapRules();
					if (raw != null) {
						for (com.resurrection.blowtorch2.lib.responder.tap.TapRuleData r : raw) {
							if (r == null || r.getPattern() == null
									|| r.getPattern().length() == 0) {
								continue;
							}
							java.util.regex.Pattern p;
							try {
								// Already the compiled form of the trigger — quoted
								// for a literal, alias resolved — so no flags and no
								// quoting here. A throw would mean the service and
								// this process disagree about regex, so skip the one
								// rule rather than lose the rest.
								p = java.util.regex.Pattern.compile(r.getPattern());
							} catch (Exception bad) {
								com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
										"MainWindow.refreshTapRules: rule pattern would not"
										+ " compile", bad);
								continue;
							}
							rules.add(new com.resurrection.blowtorch2.lib.window.Window.TapRule(
									p, r.getCommands(), r.isUnderline(), r.isBold(),
									r.isFrame(), r.getGroup()));
						}
					}
				} catch (Exception e) {
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
							"MainWindow.refreshTapRules", e);
					// Nothing read, so nothing to say. Leaving the window's current
					// rules alone beats replacing them with an empty list, which
					// would silently unmark every tappable word on screen.
					myhandler.post(new Runnable() {
						public void run() {
							tapRulesFetchInFlight = false;
						}
					});
					return;
				}
				final java.util.List<com.resurrection.blowtorch2.lib.window.Window.TapRule> done =
						rules;
				myhandler.post(new Runnable() {
					public void run() {
						tapRulesFetchInFlight = false;
						main.setTapRules(done);
					}
				});
			}
		}, "tap-rules").start();
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
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.reportLiveNawsToService", e);
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
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.tryConnectAfterNaws", e);
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
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.onNewIntent", e);
		}
	}

	public String getPathForPlugin(String mOwner) {
		try {
			String path = service.getPluginPath(mOwner);
			return path;
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("MainWindow.getPathForPlugin", e);
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
	/** Options → Miscellaneous → Reset Settings. Same confirm dialog as before. */
	public void resetSettingsFromOptions() {
		settingsTransfer.doResetDialog();
	}

	/** Options → Miscellaneous → Export Settings. Same path as the old menu item. */
	public void exportSettingsFromOptions() {
		SDCardUtils.hasPermissions(this, findViewById(R.id.window_container), RP_EXPORT,
				new Runnable() {
					@Override
					public void run() {
						settingsTransfer.doExportDialog();
					}
				});
	}

	/** Options → Miscellaneous → Import Settings. Same path as the old menu item. */
	public void importSettingsFromOptions() {
		SDCardUtils.hasPermissions(this, findViewById(R.id.window_container), RP_IMPORT,
				new Runnable() {
					@Override
					public void run() {
						settingsTransfer.doImportDialog(
								SDCardUtils.hasStoragePermissions(MainWindow.this));
					}
				});
	}

	public void requestStorageAccessFromOptions() {
		// The grant may have changed since the root was last worked out, and the
		// cached answer would keep everything in app storage forever.
		SDCardUtils.invalidateRootCache();
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

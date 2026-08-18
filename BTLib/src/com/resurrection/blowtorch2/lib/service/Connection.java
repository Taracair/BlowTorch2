/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.SAXException;

import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;
import com.resurrection.blowtorch2.lib.util.ConnectionDuration;
import com.resurrection.blowtorch2.lib.util.SessionLogger;
import com.resurrection.blowtorch2.lib.launcher.BuiltinTutorial;
import com.resurrection.blowtorch2.lib.responder.IteratorModifiedException;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.gag.GagAction;
import com.resurrection.blowtorch2.lib.responder.script.ScriptResponder;
import com.resurrection.blowtorch2.lib.script.ScriptData;
import com.resurrection.blowtorch2.lib.trigger.condition.ConditionEvaluator;
import com.resurrection.blowtorch2.lib.trigger.condition.SessionVariableStore;
import com.resurrection.blowtorch2.lib.service.function.BellCommand;
import com.resurrection.blowtorch2.lib.service.function.ClearButtonCommand;
import com.resurrection.blowtorch2.lib.service.function.ColorDebugCommand;
import com.resurrection.blowtorch2.lib.service.function.NoteCommand;
import com.resurrection.blowtorch2.lib.service.function.ProbeCommand;
import com.resurrection.blowtorch2.lib.service.function.AliasCommand;
import com.resurrection.blowtorch2.lib.service.function.TriggerCommand;
import com.resurrection.blowtorch2.lib.service.function.DirtyExitCommand;
import com.resurrection.blowtorch2.lib.service.function.DisconnectCommand;
import com.resurrection.blowtorch2.lib.service.function.EditButtonCommand;
import com.resurrection.blowtorch2.lib.service.function.EditPanelCommand;
import com.resurrection.blowtorch2.lib.service.function.SendButtonCommand;
import com.resurrection.blowtorch2.lib.service.function.FullScreenCommand;
import com.resurrection.blowtorch2.lib.service.function.FunctionCallbackCommand;
import com.resurrection.blowtorch2.lib.service.function.FrameCommand;
import com.resurrection.blowtorch2.lib.service.function.GmcpCommand;
import com.resurrection.blowtorch2.lib.service.function.McpCommand;
import com.resurrection.blowtorch2.lib.service.function.ProtocolsCommand;
import com.resurrection.blowtorch2.lib.service.function.ProtocolSurveyCommand;
import com.resurrection.blowtorch2.lib.service.function.KeyboardCommand;
import com.resurrection.blowtorch2.lib.service.function.LoadButtonsCommand;
import com.resurrection.blowtorch2.lib.service.function.MapCommand;
import com.resurrection.blowtorch2.lib.service.function.ReconnectCommand;
import com.resurrection.blowtorch2.lib.service.function.SearchCommand;
import com.resurrection.blowtorch2.lib.service.function.SpecialCommand;
import com.resurrection.blowtorch2.lib.service.function.SpeedwalkCommand;
import com.resurrection.blowtorch2.lib.service.function.SwitchWindowCommand;
import com.resurrection.blowtorch2.lib.service.function.TimerCommand;
import com.resurrection.blowtorch2.lib.service.function.SettingsCommand;
import com.resurrection.blowtorch2.lib.service.function.OptionsCommand;
import com.resurrection.blowtorch2.lib.service.function.WindowCommand;
import com.resurrection.blowtorch2.lib.service.function.WidgetCommand;
import com.resurrection.blowtorch2.lib.service.function.WrapCommand;
import com.resurrection.blowtorch2.lib.gauge.WidgetCommandParser;
import com.resurrection.blowtorch2.lib.mapper.MapperController;
import com.resurrection.blowtorch2.lib.mapper.MapStore;
import com.resurrection.blowtorch2.lib.service.plugin.ConnectionSettingsPlugin;
import com.resurrection.blowtorch2.lib.service.plugin.Plugin;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.IntegerOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.Option;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsGroup;
import com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption;
import com.resurrection.blowtorch2.lib.speedwalk.DirectionData;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerPattern;
import com.resurrection.blowtorch2.lib.window.ExtraTextSlot;
import com.resurrection.blowtorch2.lib.window.ExtraTextSlotsStore;
import com.resurrection.blowtorch2.lib.window.TextTree;
import com.resurrection.blowtorch2.lib.window.TextTree.Line;
import com.resurrection.blowtorch2.lib.alias.AliasData;
import com.resurrection.blowtorch2.lib.alias.AliasLocalEcho;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;

import android.util.Log;
import android.util.SparseArray;
//import android.util.Log;
import android.util.Xml;
import android.view.Gravity;
import android.widget.Toast;

/** Connection class implementation. */
public class Connection implements SettingsChangedListener, ConnectionPluginCallback {
	
	/** Initiates the connection with the server. */
	public static final int MESSAGE_STARTUP = 1;
	
	/** Bridge between Processor and DataPumper to initiate MCCP compression. */
	public static final int MESSAGE_STARTCOMPRESS = 2;
	
	/** Sent by various objects to throw text to the window bypassing the trigger parse routine. */
	public static final int MESSAGE_PROCESSORWARNING = 3;
	
	/** Sent from the Processor to send data to the DataPumper's output thread. */
	public static final int MESSAGE_SENDOPTIONDATA = 4;
	
	/** Sent from the Processor indicating the bell character has been recieved. */
	public static final int MESSAGE_BELLINC = 5;
	
	/** Not sure where this is sent from, I think the Xml parser or the datapumper. 
	 ** Used to put up an alert dialog with some text to the foreground window if connected. */
	public static final int MESSAGE_DODIALOG = 6;
	
	/** Sent from Processor, contains the non-telnet related data in the incoming transmission. */
	public static final int MESSAGE_PROCESS = 7;
	
	/** Sent from the DataPumper when the connection has been lost. */
	public static final int MESSAGE_DISCONNECTED = 8;
	
	/** Sent from the foreground window with data to be sent to the server. */
	public static final int MESSAGE_SENDDATA_BYTES = 9;
	
	/** Sent from Plugin.LineToWindowFunction contains a 
	 ** TextTree.Line object to send to a specific window. */
	public static final int MESSAGE_LINETOWINDOW = 10;
	
	/** Sent from Plugin.NoteFunction with text to send to the output window. */
	public static final int MESSAGE_LUANOTE = 11;
	
	/** Sent from Plugin.DrawWindowFunction, I think this is no longer used and deprecated. */
	public static final int MESSAGE_DRAWINDOW = 12;
	
	/** Sent from Plugin.NewWindowFucntion used to create a new 
	 * miniwindow with then given configuration. */
	public static final int MESSAGE_NEWWINDOW = 13;
	
	/** Sent from Plugin.WindowBufferFunction and sets the buffering 
	 * option of a window. I think this is deprecated. */
	public static final int MESSAGE_WINDOWBUFFER = 15;
	
	/** Sent from Plugin.RegisterSpecialCommandFunction and registers a .command callback. */
	public static final int MESSAGE_ADDFUNCTIONCALLBACK = 16;
	
	/** Sent from Plugin.WindowXCallSFunction, calls an anonymous global function
	 *  in the target window with data. */
	public static final int MESSAGE_WINDOWXCALLS = 17;
	
	/** Sent from plugin functions, used to redraw a target window. */
	public static final int MESSAGE_INVALIDATEWINDOWTEXT = 18;
	
	/** Sent from Processor indicating that gmcp has triggered. */
	public static final int MESSAGE_GMCPTRIGGERED = 19;

	/** MCP Lua trigger fired (mirrors MESSAGE_GMCPTRIGGERED). */
	public static final int MESSAGE_MCPTRIGGERED = 43;

	/** Raw MCP line to the socket (no alias processing / no in-band quoting). */
	public static final int MESSAGE_SENDMCPRAW = 44;
	public static final int MESSAGE_SET_VARIABLE = 45;
	public static final int MESSAGE_UNSET_VARIABLE = 46;
	/** Native GMCP Room.* → mapper sync (obj = JSON body String; module in Bundle). */
	public static final int MESSAGE_MAPPER_ROOM = 47;

	/** Extra text window slots changed — UI should sync overlays (via {@link #requestExtraTextUi}). */
	public static final int MESSAGE_EXTRA_TEXT_CHANGED = 48;

	/**
	 * Route one inbound GMCP packet into matching extra text slots
	 * (obj = JSON body String; module name in Bundle {@code MODULE}).
	 */
	public static final int MESSAGE_GMCP_EXTRA_TEXT = 49;

	/**
	 * Gauge widget list changed — UI should rebuild overlays.
	 * Passed through {@link #requestGaugeWidgetUi}; not a ConnectionHandler
	 * {@code what}. 51 collided with {@link #MESSAGE_TIMERDURATION}.
	 */
	public static final int MESSAGE_GAUGE_WIDGET_CHANGED = 60;

	/**
	 * One {@code mudstd.frame} event on its way to the UI process
	 * (obj = JSON from {@link FrameEvent#toJson}).
	 *
	 * <p>It goes through the handler rather than straight from {@link Processor}
	 * so that the queue that owns everything else about this connection owns
	 * this too: the connection thread is the one reading the socket, and a frame
	 * is not worth blocking it for.
	 */
	public static final int MESSAGE_FRAME_EVENT = 50;

	/** Sent from various sources, containing a string to be sent to 
	 * the server in the selected encoding. */
	public static final int MESSAGE_SENDDATA_STRING = 20;
	
	/** Sent from various sources, initates the settings loader routine. */
	public static final int MESSAGE_SAVESETTINGS = 21;
	
	/** Sent from either the foreground window or the save settings routine, 
	 * exports settings to a given path. */
	public static final int MESSAGE_EXPORTFILE = 22;
	
	/** Sent from either the foreground window or the settings loader routing, 
	 * imports settings from a given location into this active connection. */
	public static final int MESSAGE_IMPORTFILE = 23;
	
	/** Sent from Plugin.SendGMCPDataFunction, sends the given data to the server using gmcp. */
	public static final int MESSAGE_SENDGMCPDATA = 24;
	
	/** Sent from Plugin.WindowXCallB, same as WindowXCallS only great care is taken
	 *  to preserve the "bytes". */
	public static final int MESSAGE_WINDOWXCALLB = 25;
	
	/** Sent from Plugin, indicating an error condition from a script entry point. */
	public static final int MESSAGE_PLUGINLUAERROR = 26;

	/** Sent from DataPumper indicating that the tcp connection to the server has started. */
	public static final int MESSAGE_CONNECTED = 30;
	
	/** Sent from AckWithResponder when a trigger executes script code that results in error. */
	public static final int MESSAGE_TRIGGER_LUA_ERROR = 32;
	
	/** Sent from the foreground window, initiates the settings reloading process. */
	public static final int MESSAGE_RELOADSETTINGS = 33;
	
	/** Sent from various sources, indicates that triggers need to be rebuilt, 
	 * I believe this is deprecated. */
	public static final int MESSAGE_SETTRIGGERSDIRTY = 34;
	
	/** Sent from the DataPumper indicating the orderly shutdown of the tcp connection. */
	public static final int MESSAGE_TERMINATED_BY_PEER = 41;

	/** CHARSET negotiation selected a new encoding (obj is String charset name). */
	public static final int MESSAGE_CHARSET = 42;
	
	/** Sent from the foreground window indicating that the DataPumper
	 *  should re-establish the tcp connection to the server. */
	static final int MESSAGE_RECONNECT = 31;
	
	/** Sent from the foreground window, initates a settings reset. */
	private static final int MESSAGE_DORESETSETTINGS = 27;
	
	/** Sent from the foreground window, adds an external plugin at the given path. */
	private static final int MESSAGE_ADDLINK = 28;
	
	/** Sent from the foreground window, deletes and removes a plugin. */
	private static final int MESSAGE_DELETEPLUGIN = 29;

	/** Sent from Plugin.CallPlugin calls an anonymous global function in the target
	 *  plugin with arguments. */
	private static final int MESSAGE_CALLPLUGIN = 35;
	
	/** Sent from the timer command. */
	public static final int MESSAGE_TIMERINFO = 36;
	
	/** Sent from the timer command. */
	public static final int MESSAGE_TIMERSTART = 37;
	
	/** Sent from the timer command. */
	public static final int MESSAGE_TIMERPAUSE = 38;
	
	/** Sent from the timer command. */
	public static final int MESSAGE_TIMERRESET = 39;
	
	/** Sent from the timer command. */
	public static final int MESSAGE_TIMERSTOP = 40;

	/** Sent from the timer command — arg1 = new duration in seconds. */
	public static final int MESSAGE_TIMERDURATION = 51;

	/** Sent from the DataPumper when MCCP decompression failed and the stream is lost.
	 ** Was 9 until 2026-08-01, which collided with MESSAGE_SENDDATA_BYTES: the report
	 ** landed in sendToServer(null), which null-checks and returns, so nothing ever
	 ** handled an MCCP failure. */
	public static final int MESSAGE_MCCPFATALERROR = 52;

	/** Sent from the Processor when telnet ECHO changes hands. arg1 1 = local echo on. */
	public static final int MESSAGE_LOCALECHO = 53;
	/**
	 * The half-line held by {@link #mLineHoldover} has waited long enough and
	 * goes out as it stands. This is what makes a prompt appear: nothing ever
	 * completes one, so only the timer releases it.
	 */
	public static final int MESSAGE_FLUSH_LINE_HOLDOVER = 54;

	/** Toast message offset from the top of the screen. */
	private static final double TOAST_MESSAGE_TOP_OFFSET = 50.0;
	/** Very large value. */
	private static final int TEN_MILLION = 10000000;
	
	/** Medium large value. */
	private static final int TEN_THOUSAND = 10000;
	
	/** 3 seconds. */
	static final int THREE_THOUSAND_MILLIS = 3000;
	
	/** 20 seconds. */
	private static final int TWENTY_THOUSAND_MILLIS = 20000;

	
	/** Status bar height holder. */
	private static final int STATUS_BAR_DEFAULT_SIZE = 25;
	
	
	/** Generic "match a line" pattern. */
	private static final Pattern LINE_PATTERN = Pattern.compile("^.*$", Pattern.MULTILINE);
	
	/** Line matching matcher. */
	private static final Matcher LINE_MATCHER = LINE_PATTERN.matcher("");
	


	
	/** The configurable character denoting that the input to follow should be executed as a script. */
	private static String mScriptBlock = "/";
	
	/** String name of the default output window. */
	private static final String MAIN_WINDOW = "mainDisplay";
	
	/** Constant indicating that one or more triggers is invalid and the trigger system should be rebuilt
	 * on the next pass of dispatch().
	 *
	 * Per connection, not per process: there is one Connection per open world,
	 * and every other piece of trigger state here is an instance field. While
	 * this was static, world B rebuilding its own triggers cleared the flag world
	 * A had just set, and A's deferred rebuild was dropped.
	 */
	private boolean triggersDirty = false;
	/** This variable is used in conjunction with mWindowCallbackMap to track IWindowCallback aidl connections
	 * window names.
	 */
	private boolean mCallbacksStarted = false;
	/** String builder used by the alias parsing routine. */
	private final StringBuffer mDataToServer = new StringBuffer();
	/** String builder used by the alias parsing routine. */
	private final StringBuffer mDataToWindow = new StringBuffer();
	/** Semicolon matching pattern. */
	private final Pattern mSemicolon = Pattern.compile(";");
	/** Semicolon matcher. */
	private final Matcher mSemiMatcher = mSemicolon.matcher("");
	/** String builder used by the alias parsing routine. */
	private final StringBuffer mCommandBuilder = new StringBuffer();
	/** Used by the trigger processor to map line start/end to line number. */
	private final TreeSet<Range> mLineMap = new TreeSet<Range>(new RangeComparator());

	/** A utiltiy object to keep track of the order of triggers. */
	private final SparseArray<TriggerData> mSortedTriggerMap = new SparseArray<TriggerData>(0);
	/** A utiltity object to keep track of the sorted order of plugins. */
	private final SparseArray<Plugin> mTriggerPluginMap = new SparseArray<Plugin>(0);
	/** Remote window callback map. Reduces overhead for needing to communicate with windows. */
	private final RemoteCallbackList<IWindowCallback> mWindowCallbacks = new RemoteCallbackList<IWindowCallback>();
	/** The list of window tokens in loaded order. */
	ArrayList<WindowToken> mWindows;
	/** Extra text window slots for this connection (drawer/float overlays). */
	private final ConnectionExtraText mExtraText = new ConnectionExtraText(this);
	/** Overlay gauges (HP/mana/timer). Filled by ConnectionGaugeWidgets when present. */
	ConnectionGaugeWidgets mGauges;
	/** The auto reconnect limit helper varialbe. */
	/** Auto-reconnect / persistent-connection state and scheduling. */
	private final ConnectionReconnect mReconnect = new ConnectionReconnect(this);

	/** The amalgamated trigger string. Very long in most cases. */
	private String mMassiveTriggerString = null;
	/** The amalgamated trigger string pattern object. */
	private Pattern mMassivePattern = null;
	/** The amalgamated trigger string matcher object. */
	private Matcher mMassiveMatcher = null;
	/**
	 * Null unless the player ran {@code .probe lines on}. Null-checked rather
	 * than flagged so that the ordinary path costs one reference comparison per
	 * chunk and nothing else.
	 */
	private ChunkStats mChunkStats = null;
	/** Kept across an off/on so a reading is not lost by pausing the probe. */
	private ChunkStats mChunkStatsHeld = null;
	/**
	 * The half-line at the end of a chunk waits here for the rest of itself, so
	 * that triggers, gags and the display only ever see finished lines.
	 */
	private final IncomingLineHoldover mLineHoldover = new IncomingLineHoldover();
	/** {@code .prompt on}: the unfinished last line goes to its own bar. */
	private boolean mPromptBar = false;
	/** {@code .complete on}: incoming text feeds the UI's word completer. */
	private boolean mWordComplete = false;

	/** Unfinished lines flushed this connection; see {@link #getPromptsSeen()}. */
	private int mPromptsSeen = 0;
	/** The main looper handler for this "foreground" thread, although I'm not sure
	 *  if service processes get "foreground threads". */
	Handler mHandler = null;
	/** Global handler for the speedwalk command, useful for changing the settings. */
	private SpeedwalkCommand mSpeedwalkCommand = null;
	
	/** Main tracker for plugins, generic ordered list of plugins in the order they were loaded. */
	ArrayList<Plugin> mPlugins = null;
	
	/** Global map for handling the capture transformation for triggers and aliases. */
	private HashMap<String, String> mCaptureMap = new HashMap<String, String>();
	private final SessionVariableStore mSessionVariables = new SessionVariableStore();
	
	/** The DataPumper instance for this connection. */
	DataPumper mPump = null;
	
	/** The Processor instance for this connection. */
	Processor mProcessor = null;
	/** MCP 2.1 engine (in-band #$#). */
	private McpEngine mMcpEngine = null;
	//TextTree buffer = null;
	
	/** TextTree instance used for trigger parsing input text. */
	private TextTree mWorking = null;
	
	/** TextTree instance used for trigger parsing input text. */
	private TextTree mFinished = null;

	/** Whether a colour trigger's colour is still running, across dispatches. */
	private final TriggerColorState mTriggerColor = new TriggerColorState();

	/** Mapping of link paths to plugin names. */
	HashMap<String, ArrayList<String>> mLinkMap = new HashMap<String, ArrayList<String>>();

	/**
	 * Links referenced in settings that failed to load (missing file, parse error, empty).
	 * Key is the relative link path stored in settings; value is a short failure reason.
	 */
	private HashMap<String, String> mFailedLinks = new HashMap<String, String>();
	
	/** Mapping of plugin names to plugin objects. */
	HashMap<String, Plugin> mPluginMap = new HashMap<String, Plugin>(0);
	
	/** Not really sure what this is. */
	private boolean mLoaded = false;
	
	/** Launcher display name for this Connection. */
	String mDisplay;
	
	/** Host name for this connection. */
	String mHost;
	
	/** Port indication for this connection. */
	private int mPort;
	/**
	 * TLS for this world. Fixed for the life of the Connection: it identifies
	 * the endpoint, so a reconnect must use the same answer the player chose
	 * rather than quietly falling back to plain text.
	 */
	private boolean mUseTls = false;
	
	/** Synchronization target to manage window loading/unloading. */
	private Object mWindowSynch = new Object();
	
	/** Mapping of window names to IWindowCallback aidl bridge connections. */
	private HashMap<String, IWindowCallback> mWindowCallbackMap = 
			new HashMap<String, IWindowCallback>();

	/** Timer CRUD / .timer command actions. */
	private final ConnectionTimers mTimers = new ConnectionTimers(this);

	/** GMCP settings / status / message handlers. */
	private final ConnectionGmcp mGmcp = new ConnectionGmcp(this);

	/** Settings export/import/load orchestration. */
	private final ConnectionSettingsIO mSettingsIO = new ConnectionSettingsIO(this);

	/** Session file-log option wrappers. */
	private final ConnectionSessionLog mSessionLog = new ConnectionSessionLog(this);

	/** Alias CRUD and keyboard alias replacement. */
	private final ConnectionAliases mAliases = new ConnectionAliases(this);

	/** Trigger CRUD and enable/disable/toggle handling. */
	private final ConnectionTriggers mTriggers = new ConnectionTriggers(this);

	/** Per-connection mapper engine (recording, path, GMCP room sync). */
	private MapperController mMapper;
	
	/** Instance of our parent service. This is bad. */
	StellarService mService = null;
	
	/** A simple holder for if we are connected or not. */
	private boolean mIsConnected = false;
	/** elapsedRealtime when the current (or last finished) connection became up. */
	private long mConnectedAtElapsed = 0L;
	/** Duration of the most recently completed connection attempt, ms. */
	private long mLastDurationMs = 0L;
	
	/** The main settings wad/plugin. */
	ConnectionSettingsPlugin mSettings = null;
	
	/** The keyboard command instance, not sure why this is here. */
	private KeyboardCommand mKeyboardCommand;
	
	/** Value of CRLF. */
	private String mCRLF = "\r\n";

	/** The pattern for the .command. */
	private Pattern mCommandPattern = Pattern.compile("^.(\\w+)\\s*(.*)$");
	
	/** The matcher for the .command. */
	private Matcher mCommandMatcher = mCommandPattern.matcher("");
	
	/** The map of special commands. */
	private HashMap<String, SpecialCommand> mSpecialCommands = new HashMap<String, SpecialCommand>();

	/** Constant for the status bar height, useful for plugins, hard to get. */
	private int mStatusBarHeight;
	
	/** Constant for the title bar height, useful for plugins, hard to get. */
	private int mTitleBarHeight;
	
	/** Public constructor for the Connection.
	* @param display The display name.
	* @param host The host name.
	* @param port The port number.
	* @param service Parent that initated this connection.
	*/
	public Connection(final String display, final String host, final int port,
			final boolean useTls, final StellarService service) {
		
		ColorDebugCommand colordebug = new ColorDebugCommand();
		DirtyExitCommand dirtyexit = new DirtyExitCommand();
		TimerCommand timercmd = new TimerCommand();
		BellCommand bellcmd = new BellCommand();
		FullScreenCommand fscmd = new FullScreenCommand();
		mKeyboardCommand = new KeyboardCommand();
		DisconnectCommand dccmd = new DisconnectCommand();
		ReconnectCommand rccmd = new ReconnectCommand();
		mSpeedwalkCommand = new SpeedwalkCommand(null, new Data());
		LoadButtonsCommand lbcmd = new LoadButtonsCommand();
		ClearButtonCommand cbcmd = new ClearButtonCommand();
		NoteCommand notecmd = new NoteCommand();
		WrapCommand wrapcmd = new WrapCommand();
		EditPanelCommand editpanelcmd = new EditPanelCommand();
		EditButtonCommand editbtncmd = new EditButtonCommand();
		SendButtonCommand sendbtncmd = new SendButtonCommand();
		mSpecialCommands.put(colordebug.commandName, colordebug);
		mSpecialCommands.put(dirtyexit.commandName, dirtyexit);
		mSpecialCommands.put(timercmd.commandName, timercmd);
		mSpecialCommands.put(bellcmd.commandName, bellcmd);
		mSpecialCommands.put(fscmd.commandName, fscmd);
		mSpecialCommands.put(mKeyboardCommand.commandName, mKeyboardCommand);
		mSpecialCommands.put("kb", mKeyboardCommand);
		mSpecialCommands.put(dccmd.commandName, dccmd);
		mSpecialCommands.put(rccmd.commandName, rccmd);
		mSpecialCommands.put(mSpeedwalkCommand.commandName, mSpeedwalkCommand);
		mSpecialCommands.put(lbcmd.commandName, lbcmd);
		mSpecialCommands.put(cbcmd.commandName, cbcmd);
		mSpecialCommands.put(notecmd.commandName, notecmd);
		ProbeCommand probecmd = new ProbeCommand();
		mSpecialCommands.put(probecmd.commandName, probecmd);
		com.resurrection.blowtorch2.lib.service.function.PromptBarCommand promptcmd =
				new com.resurrection.blowtorch2.lib.service.function.PromptBarCommand();
		mSpecialCommands.put(promptcmd.commandName, promptcmd);
		com.resurrection.blowtorch2.lib.service.function.CompleteCommand completecmd =
				new com.resurrection.blowtorch2.lib.service.function.CompleteCommand();
		mSpecialCommands.put(completecmd.commandName, completecmd);
		// The same command under the word that describes it. ".complete" stays
		// registered because it is in profiles, buttons and notes already, and
		// breaking those to rename a command would be a poor trade.
		mSpecialCommands.put(
				com.resurrection.blowtorch2.lib.service.function.CompleteCommand.ALIAS_NAME,
				completecmd);
		mSpecialCommands.put(
				com.resurrection.blowtorch2.lib.service.function.CompleteCommand.LONG_ALIAS_NAME,
				completecmd);
		com.resurrection.blowtorch2.lib.service.function.SoundCommand soundcmd =
				new com.resurrection.blowtorch2.lib.service.function.SoundCommand();
		mSpecialCommands.put(soundcmd.commandName, soundcmd);
		com.resurrection.blowtorch2.lib.service.function.SensorCommand sensorcmd =
				new com.resurrection.blowtorch2.lib.service.function.SensorCommand();
		mSpecialCommands.put(sensorcmd.commandName, sensorcmd);
		com.resurrection.blowtorch2.lib.service.function.HelpCommand helpcmd =
				new com.resurrection.blowtorch2.lib.service.function.HelpCommand();
		mSpecialCommands.put(helpcmd.commandName, helpcmd);
		mSpecialCommands.put(
				com.resurrection.blowtorch2.lib.service.function.HelpCommand.ALIAS_NAME,
				helpcmd);
		com.resurrection.blowtorch2.lib.service.function.TapMenuCommand tapmenucmd =
				new com.resurrection.blowtorch2.lib.service.function.TapMenuCommand();
		mSpecialCommands.put(tapmenucmd.commandName, tapmenucmd);
		mSpecialCommands.put(wrapcmd.commandName, wrapcmd);
		com.resurrection.blowtorch2.lib.service.function.DimRepeatCommand dimrepeatcmd =
				new com.resurrection.blowtorch2.lib.service.function.DimRepeatCommand();
		mSpecialCommands.put(dimrepeatcmd.commandName, dimrepeatcmd);
		com.resurrection.blowtorch2.lib.service.function.Osc8Command osc8cmd =
				new com.resurrection.blowtorch2.lib.service.function.Osc8Command();
		mSpecialCommands.put(osc8cmd.commandName, osc8cmd);
		com.resurrection.blowtorch2.lib.service.function.TutorialCommand tutorialcmd =
				new com.resurrection.blowtorch2.lib.service.function.TutorialCommand(
						"tutorial", "tutorialCommand");
		mSpecialCommands.put(tutorialcmd.commandName, tutorialcmd);
		com.resurrection.blowtorch2.lib.service.function.TutorialCommand tipscmd =
				new com.resurrection.blowtorch2.lib.service.function.TutorialCommand(
						"tips", "tipsCommand");
		mSpecialCommands.put(tipscmd.commandName, tipscmd);
		mSpecialCommands.put(editpanelcmd.commandName, editpanelcmd);
		mSpecialCommands.put(editbtncmd.commandName, editbtncmd);
		mSpecialCommands.put(sendbtncmd.commandName, sendbtncmd);
		com.resurrection.blowtorch2.lib.service.function.FontCommand fontcmd =
				new com.resurrection.blowtorch2.lib.service.function.FontCommand();
		mSpecialCommands.put(fontcmd.commandName, fontcmd);
		com.resurrection.blowtorch2.lib.service.function.CanvasWidthCommand widthcmd =
				new com.resurrection.blowtorch2.lib.service.function.CanvasWidthCommand();
		mSpecialCommands.put(widthcmd.commandName, widthcmd);
		SwitchWindowCommand swdcmd = new SwitchWindowCommand();
		mSpecialCommands.put(swdcmd.commandName, swdcmd);
		SearchCommand searchcmd = new SearchCommand();
		mSpecialCommands.put(searchcmd.commandName, searchcmd);
		GmcpCommand gmcpcmd = new GmcpCommand();
		mSpecialCommands.put(gmcpcmd.commandName, gmcpcmd);
		FrameCommand framecmd = new FrameCommand();
		mSpecialCommands.put(framecmd.commandName, framecmd);
		TriggerCommand triggercmd = new TriggerCommand();
		mSpecialCommands.put(triggercmd.commandName, triggercmd);
		AliasCommand aliascmd = new AliasCommand();
		mSpecialCommands.put(aliascmd.commandName, aliascmd);
		McpCommand mcpcmd = new McpCommand();
		mSpecialCommands.put(mcpcmd.commandName, mcpcmd);
		ProtocolsCommand msspcmd = new ProtocolsCommand(false);
		mSpecialCommands.put(msspcmd.commandName, msspcmd);
		ProtocolsCommand msdpcmd = new ProtocolsCommand(true);
		mSpecialCommands.put(msdpcmd.commandName, msdpcmd);
		ProtocolSurveyCommand protocolscmd = new ProtocolSurveyCommand();
		mSpecialCommands.put(protocolscmd.commandName, protocolscmd);
		com.resurrection.blowtorch2.lib.service.function.MxpCommand mxpcmd =
				new com.resurrection.blowtorch2.lib.service.function.MxpCommand();
		mSpecialCommands.put(mxpcmd.commandName, mxpcmd);
		com.resurrection.blowtorch2.lib.service.function.EchoCommand echocmd =
				new com.resurrection.blowtorch2.lib.service.function.EchoCommand();
		mSpecialCommands.put(echocmd.commandName, echocmd);
		MapCommand mapcmd = new MapCommand();
		mSpecialCommands.put(mapcmd.commandName, mapcmd);
		WindowCommand windowcmd = new WindowCommand();
		mSpecialCommands.put(windowcmd.commandName, windowcmd);
		WidgetCommand widgetcmd = new WidgetCommand();
		mSpecialCommands.put(widgetcmd.commandName, widgetcmd);
		mSpecialCommands.put(WidgetCommand.ALIAS_NAME, widgetcmd);
		SettingsCommand settingscmd = new SettingsCommand();
		mSpecialCommands.put(settingscmd.commandName, settingscmd);
		OptionsCommand optionscmd = new OptionsCommand();
		mSpecialCommands.put(optionscmd.commandName, optionscmd);
		
		this.mDisplay = display;
		this.mHost = host;
		this.mPort = port;
		this.mUseTls = useTls;
		this.mService = service;

		mMapper = new MapperController(this);
		mGauges = new ConnectionGaugeWidgets(this);
		
		mPlugins = new ArrayList<Plugin>();
		mHandler = new Handler(new ConnectionHandler());

		mWorking = new TextTree();
		mWorking.setLinkify(false);
		mWorking.setLineBreakAt(TEN_MILLION);
		mWorking.setMaxLines(TEN_THOUSAND);
		
		mFinished = new TextTree();
		mFinished.setLinkify(false);
		mFinished.setLineBreakAt(TEN_MILLION);
		mFinished.setMaxLines(TEN_THOUSAND);

		mWindows = new ArrayList<WindowToken>();

		ensureMcpEngine();
		
		SharedPreferences sprefs = this.getContext().getSharedPreferences("STATUS_BAR_HEIGHT", 0);
		mStatusBarHeight = sprefs.getInt("STATUS_BAR_HEIGHT", (int) (STATUS_BAR_DEFAULT_SIZE * this.getContext().getResources().getDisplayMetrics().density));
		mTitleBarHeight = sprefs.getInt("TITLE_BAR_HEIGHT", 0);

		mLoaded = true;
		
		//fish out the window.

	}
	
	/** The connection handler message queue. Coordinates multithreaded efforts from the DataPumper and foreground window via the Service. */
	private class ConnectionHandler implements Handler.Callback {

		public boolean handleMessage(final Message msg) {
			try {
				return handleMessageUnsafe(msg);
			} catch (Throwable t) {
				// Keep the :stellar connection process alive; show the error in-game.
				// Catch Throwable so StackOverflowError / OOM during settings load
				// become a window error instead of killing the process.
				reportRuntimeError("connection handler (msg " + msg.what + ")", t);
				return true;
			}
		}

		@SuppressWarnings("unchecked")
		private boolean handleMessageUnsafe(final Message msg) {
			switch(msg.what) {
			case MESSAGE_TERMINATED_BY_PEER:
				clearStartupInProgress();
				killNetThreads(true);
				// Default: peer closed → no auto-reconnect. Persistent: treat like a network flap.
				doDisconnect(!mReconnect.isPersistent());
				mIsConnected = false;
				break;
			case MESSAGE_CHARSET:
				if (msg.obj instanceof String) {
					doUpdateEncoding((String) msg.obj);
				}
				break;
			case MESSAGE_LOCALECHO:
				doSetTelnetEcho(msg.arg1 == 1);
				break;
			case MESSAGE_FLUSH_LINE_HOLDOVER:
				try {
					flushLineHoldover();
				} catch (UnsupportedEncodingException bad) {
					// The profile's encoding is unusable; dropping the fragment
					// is better than losing the handler to it.
					mLineHoldover.clear();
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
							"Connection.flushLineHoldover", bad);
				}
				break;
			case MESSAGE_TIMERSTOP:
			case MESSAGE_TIMERSTART:
			case MESSAGE_TIMERRESET:
			case MESSAGE_TIMERINFO:
			case MESSAGE_TIMERPAUSE:
			case MESSAGE_TIMERDURATION:
				mTimers.handleTimerMessage(msg);
				break;
			case MESSAGE_CALLPLUGIN:
				String ptmp = msg.getData().getString("PLUGIN");
				String ftmp = msg.getData().getString("FUNCTION");
				String dtmp = msg.getData().getString("DATA");
				doCallPlugin(ptmp, ftmp, dtmp);
				break;
			case MESSAGE_SETTRIGGERSDIRTY:
				setTriggersDirty();
				break;
			case MESSAGE_RELOADSETTINGS:
				reloadSettings();
				break;
			case MESSAGE_TRIGGER_LUA_ERROR:
				dispatchLuaError((String) msg.obj);
				break;
			case MESSAGE_RECONNECT:
				doReconnect();
				break;
			case MESSAGE_CONNECTED:
				clearStartupInProgress();
				mReconnect.onConnected();
				mIsConnected = true;
				mConnectedAtElapsed = SystemClock.elapsedRealtime();
				mSessionLog.onConnected();
				applyInputAssistSettings();
				if (mProcessor != null) {
					mProcessor.setLogProfile(mDisplay);
					mGmcp.applyGmcpLogSetting();
					applyMcpSettings();
					if (mLiveCols > 0 && mLiveRows > 0) {
						mProcessor.setDisplayDimensions(mLiveRows, mLiveCols);
						mProcessor.disaptchNawsString();
						mLastSentNawsCols = mLiveCols;
						mLastSentNawsRows = mLiveRows;
					}
				} else {
					applyMcpSettings();
				}
				maybeShowTerminalSizeHint();
				break;
			case MESSAGE_SEND_NAWS:
				if (mIsConnected && mProcessor != null && mLiveCols > 0 && mLiveRows > 0) {
					if (mLiveCols != mLastSentNawsCols || mLiveRows != mLastSentNawsRows) {
						mProcessor.setDisplayDimensions(mLiveRows, mLiveCols);
						mProcessor.disaptchNawsString();
						mLastSentNawsCols = mLiveCols;
						mLastSentNawsRows = mLiveRows;
						Log.i("BlowTorch", "NAWS sent " + mLiveCols + "x" + mLiveRows);
					}
				}
				break;
			case MESSAGE_DELETEPLUGIN:
				doDeletePlugin((String) msg.obj);
				break;
			case MESSAGE_ADDLINK:
				doAddLink((String) msg.obj);
				break;
			case MESSAGE_DORESETSETTINGS:
				doResetSettings();
				break;
			case MESSAGE_PLUGINLUAERROR:
				dispatchLuaError((String) msg.obj);
				break;
			case MESSAGE_EXPORTFILE:
				exportSettings((String) msg.obj);
				break;
			case MESSAGE_IMPORTFILE:
				Connection.this.mService.markWindowsDirty();
				mSettingsIO.importSettings((String) msg.obj, true, false);
				break;
			case MESSAGE_SAVESETTINGS:
				String changedplugin = (String) msg.obj;
				Connection.this.saveDirtyPlugin(changedplugin);
				break;
			case MESSAGE_GMCPTRIGGERED:
				mGmcp.handleGmcpTriggered(msg);
				break;
			case MESSAGE_MCPTRIGGERED:
				String mplugin = msg.getData().getString("TARGET");
				String mcallback = msg.getData().getString("CALLBACK");
				HashMap<String, Object> mdata = (HashMap<String, Object>) msg.obj;
				Plugin mp = mPluginMap.get(mplugin);
				if (mp != null) {
					mp.handleGMCPCallback(mcallback, mdata);
				}
				break;
			case MESSAGE_SENDMCPRAW:
				if (msg.obj instanceof String) {
					sendMcpRawToPump((String) msg.obj);
				}
				break;
			case MESSAGE_SET_VARIABLE:
				if (msg.obj instanceof String[]) {
					String[] pair = (String[]) msg.obj;
					if (pair.length >= 2) {
						mSessionVariables.set(pair[0], pair[1]);
						if (mGauges != null) {
							mGauges.onSessionVar(pair[0], pair[1]);
						}
					}
				}
				break;
			case MESSAGE_UNSET_VARIABLE:
				if (msg.obj instanceof String) {
					mSessionVariables.unset((String) msg.obj);
					if (mGauges != null) {
						mGauges.onSessionVar((String) msg.obj, null);
					}
				}
				break;
			case MESSAGE_MAPPER_ROOM:
				if (mMapper != null && msg.obj instanceof String) {
					String module = msg.getData() != null
							? msg.getData().getString("MODULE") : null;
					mMapper.onGmcpRoomRaw(module, (String) msg.obj);
				}
				break;
			case MESSAGE_GMCP_EXTRA_TEXT:
				if (msg.obj instanceof String) {
					String module = msg.getData() != null
							? msg.getData().getString("MODULE") : null;
					String body = (String) msg.obj;
					routeGmcpToExtraWindows(module, body);
					if (mGauges != null) {
						mGauges.onGmcp(module, body);
					}
				}
				break;
			case MESSAGE_FRAME_EVENT:
				if (msg.obj instanceof String) {
					queueFrameEvents((String) msg.obj);
				}
				break;
			case MESSAGE_INVALIDATEWINDOWTEXT:
				String wname = (String) msg.obj;
				try {
					doInvalidateWindowText(wname);
				} catch (RemoteException e4) {
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("Connection", e4);
				}
				break;
			case MESSAGE_WINDOWXCALLS:
				Object o = msg.obj;
				if (o == null) {
					o = "";
				}
				String token = msg.getData().getString("TOKEN");
				String function = msg.getData().getString("FUNCTION");
				try {
					Connection.this.windowXCallS(token, function, o);
				} catch (RemoteException e3) {
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("Connection", e3);
				}
				break;
			case MESSAGE_WINDOWXCALLB:
				byte[] bytesa = (byte[]) msg.obj;
				String tokens = msg.getData().getString("TOKEN");
				String functions = msg.getData().getString("FUNCTION");
				try {
					Connection.this.windowXCallB(tokens, functions, bytesa);
				} catch (RemoteException e3) {
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("Connection", e3);
				}
				break;
			case MESSAGE_ADDFUNCTIONCALLBACK:
				Bundle data = msg.getData();
				String id = data.getString("ID");
				String command = data.getString("COMMAND");
				String callback = data.getString("CALLBACK");
				int pid = -1;
				for (int i = 0; i < mPlugins.size(); i++) {
					Plugin p = mPlugins.get(i);
					if (p.getName().equals(id)) {
						pid = i;
					}
				}
				if (pid != -1) {
					FunctionCallbackCommand fcc = new FunctionCallbackCommand(pid, command, callback);
					mSpecialCommands.put(fcc.commandName, fcc);
				}
				break;
			case MESSAGE_WINDOWBUFFER:
				boolean set = (msg.arg1 == 0) ? false : true;
				
				String name = (String) msg.obj;
				
				for (WindowToken tok : mWindows) {
					if (tok.getName().equals(name)) {
						tok.setBufferText(set);
					}
				}
				break;
			case MESSAGE_NEWWINDOW:
				WindowToken tok = (WindowToken) msg.obj;
				mWindows.add(tok);
				break;
			case MESSAGE_DRAWINDOW:
				Connection.this.redrawWindow((String) msg.obj);
				break;
			case MESSAGE_LUANOTE:
				String str = (String) msg.obj;
					if(str != null) {
					try {
						dispatchNoProcess(str.getBytes(mSettings.getEncoding()));
					} catch (UnsupportedEncodingException e1) {
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor("Connection", e1);
					}
				}
				break;
			case MESSAGE_LINETOWINDOW:
				Object line = msg.obj;
				String target = msg.getData().getString("TARGET");
				try {
					Connection.this.lineToWindow(target, line);
				} catch (RemoteException e3) {
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("Connection", e3);
				}
				break;
			case MESSAGE_SENDDATA_STRING:
				try {
					byte[] bytes = ((String) msg.obj).getBytes(mSettings.getEncoding());
					sendToServer(bytes);
				} catch (UnsupportedEncodingException e1) {
					reportRuntimeError("outbound encoding", e1);
				} catch (Exception e1) {
					reportRuntimeError("outbound command", e1);
				}
				break;
			case MESSAGE_SENDDATA_BYTES:
				try {
					sendToServer((byte[]) msg.obj);
				} catch (Exception e1) {
					reportRuntimeError("outbound command", e1);
				}
				break;
			case MESSAGE_SENDGMCPDATA:
				mGmcp.handleSendGmcpData(msg);
				break;
			case MESSAGE_STARTUP:
				doStartup();
				break;
			case MESSAGE_STARTCOMPRESS:
				mPump.getHandler().sendMessage(mPump.getHandler().obtainMessage(DataPumper.MESSAGE_COMPRESS, msg.obj));
				break;
			case MESSAGE_MCCPFATALERROR:
				handleMccpFailure();
				break;
			case MESSAGE_SENDOPTIONDATA:
				Bundle b = msg.getData();
				byte[] obytes = b.getByteArray("THE_DATA");
				String message = b.getString("DEBUG_MESSAGE");
				if (message != null) {
					sendDataToWindow(message);
				}

				if (mPump != null) {
					mPump.sendData(obytes);
				}
				break;
			case MESSAGE_PROCESSORWARNING:
				sendDataToWindow((String) msg.obj);
				break;
			case MESSAGE_BELLINC:
				if (mSettings.isVibrateOnBell()) {
					Connection.this.mService.doVibrateBell();
				}
				if (mSettings.isNotifyOnBell()) {
					Connection.this.mService.doNotifyBell(Connection.this.mDisplay, Connection.this.mHost, Connection.this.mPort);
				}
				if (mSettings.isDisplayOnBell()) {
					Connection.this.mService.doDisplayBell();
				}
				break;
			case MESSAGE_DODIALOG:
				dispatchDialog((String) msg.obj);
				break;
			case MESSAGE_PROCESS:
				try {
					dispatch((byte[]) msg.obj);
				} catch (UnsupportedEncodingException e) {
					reportRuntimeError("incoming text encoding", e);
				} catch (Exception e) {
					reportRuntimeError("incoming text / triggers", e);
				}
				break;
			case MESSAGE_DISCONNECTED:
				clearStartupInProgress();
				killNetThreads(true);
				doDisconnect(false);
				mIsConnected = false;
				break;
			default:
				break;
			}
			return true;
		}
		
	}

	/** Quick frontend for dispatchNoProcess(...) for sending a lua error message.
	 * 
	 * @param message The message to show.
	 */
	protected final void dispatchLuaError(final String message) {
		BlowTorchLogger.logError(mService.getApplicationContext(), mDisplay, message);
		String human = BlowTorchLogger.humanizeError(message);
		try {
			String red = Colorizer.getRedColor();
			String white = Colorizer.getWhiteColor();
			String shown = red + human + white + "\n";
			String encoding = "UTF-8";
			if (mSettings != null) {
				try {
					String enc = mSettings.getEncoding();
					if (enc != null && enc.length() > 0) {
						encoding = enc;
					}
				} catch (Exception ignored) {
				}
			}
			if (mWindows == null || mWindows.isEmpty() || mWindows.get(0) == null
					|| mWindows.get(0).getBuffer() == null) {
				Log.e("BlowTorch", "Lua error (no window yet): " + human);
				return;
			}
			dispatchNoProcess(shown.getBytes(encoding));
		} catch (UnsupportedEncodingException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor("Connection.dispatchLuaError", e);
		} catch (Exception e) {
			// Never crash the connection while reporting a Lua error (e.g. mSettings
			// still null during early import / alignDefaultButtons).
			Log.e("BlowTorch", "dispatchLuaError failed: " + message, e);
		}
	}

	/**
	 * Show a runtime failure in the game window and log it, without killing the
	 * connection process. Used for trigger/alias/timer responders and the
	 * connection message loop.
	 */
	@Override
	public final void reportRuntimeError(final String where, final Throwable error) {
		String place = where != null && where.length() > 0 ? where : "runtime";
		String detail;
		if (error == null) {
			detail = "(no exception)";
		} else {
			String msg = error.getMessage();
			if (msg == null || msg.length() == 0) {
				msg = error.getClass().getSimpleName();
			}
			detail = error.getClass().getSimpleName() + ": " + msg;
		}
		String line = "BlowTorch error [" + place + "]: " + detail
				+ "\n(Connection kept running; check log for details.)";
		Log.e("BlowTorch", line, error);
		try {
			dispatchLuaError(line);
		} catch (Exception e) {
			Log.e("BlowTorch", "reportRuntimeError failed to display: " + line, e);
		}
	}

	/** Saves dirty plugins or the main settings wad.
	 * 
	 * @param changedplugin The name of the plugin, "" will save the main settings.
	 * @note This doesn't work as far as I know.
	 */
	protected final void saveDirtyPlugin(final String changedplugin) {
		if (changedplugin == null || changedplugin.equals("")) {
			saveMainSettings();
		} else {
			Plugin p = mPluginMap.get(changedplugin);
			if (p != null) {
				if (p.getStorageType().equals("INTERNAL")) {
					saveMainSettings();
				} else {
					if(p.getSettings().isDirty()) {
						saveMainSettings(); //ugly, need to be able to save plugins individually.
					}
				}
			}
		}
	}

	/**
	 * Forget a window whose binder has died.
	 *
	 * <p>{@code mWindowCallbackMap} is a snapshot taken from the
	 * {@link RemoteCallbackList} at registration; it does not prune itself when
	 * the far side goes away. {@link #notifyMainWindow} always cleared its own
	 * entry, but every other call into a window binder only logged the
	 * {@code DeadObjectException} and left the corpse in the map — so each new
	 * line of game text tried the dead binder again. An overnight session on
	 * 31 July logged {@code [Connection] DeadObjectException} from 22:20 to
	 * 01:01 doing exactly that.
	 *
	 * <p>Re-registration puts a live binder back (see
	 * {@code registerWindowCallback}), so dropping it here costs nothing when
	 * the window comes back.
	 */
	private void dropDeadWindowCallback(final String name) {
		if (name == null) {
			return;
		}
		Log.w("BlowTorch", "Window binder dead; clearing callback for " + name);
		synchronized (mWindowSynch) {
			mWindowCallbackMap.remove(name);
		}
	}

	/**
	 * Forget every window binder for this connection.
	 *
	 * <p>Used when a new UI process attaches to a Connection that outlived the
	 * previous one (recents swipe). {@code dirtyExit} unregisters each callback
	 * before finish; a kill does not, and the corpses poison
	 * {@link #mWindowCallbackMap} on the next register. Clearing here matches
	 * that clean leave.
	 */
	public final void purgeAllWindowCallbacks() {
		synchronized (mWindowSynch) {
			if (mCallbacksStarted) {
				mWindowCallbacks.finishBroadcast();
			}
			int n = mWindowCallbacks.beginBroadcast();
			java.util.ArrayList<IWindowCallback> all =
					new java.util.ArrayList<IWindowCallback>(n);
			for (int i = 0; i < n; i++) {
				all.add(mWindowCallbacks.getBroadcastItem(i));
			}
			mWindowCallbacks.finishBroadcast();
			for (int i = 0; i < all.size(); i++) {
				mWindowCallbacks.unregister(all.get(i));
			}
			mWindowCallbackMap.clear();
			mCallbacksStarted = false;
		}
		Log.i("BlowTorch", "Purged all window callbacks for " + mDisplay);
	}

	/** Guards every field below, and the whole of the hand-over in
	 *  {@link #flushTextHeldWhileHidden()}.
	 *
	 *  <p>Its own lock rather than mWindowSynch: the text threads reach it on
	 *  every line, and mWindowSynch is a coarse lock around the whole callback
	 *  map. The ordering that exists is mHeldSynch then mWindowSynch — the
	 *  flush reaches dropDeadWindowCallback — and never the reverse, because
	 *  holdWhileHidden has released this lock long before notifyMainWindow's
	 *  catch block takes the other one. */
	private final Object mHeldSynch = new Object();
	/** Whether text is being held because the game window is off screen.
	 *
	 *  <p>A Connection-local copy of the service's flag rather than a read
	 *  through mService, so that clearing it and delivering what was held
	 *  happen inside one critical section. Reading the service's field here
	 *  instead would leave a window between "now visible" and "held text
	 *  delivered" in which a dispatch thread pushes a new line straight past
	 *  the older ones — text out of order on the very resume this exists for.
	 *
	 *  <p>False by default, so a Connection built without a service — offline
	 *  and test paths — never holds anything. */
	private boolean mHoldingForHiddenUi = false;
	/** Per window, the text its UI copy has not been given yet. */
	private final HashMap<String, java.io.ByteArrayOutputStream> mHeldWhileHidden =
			new HashMap<String, java.io.ByteArrayOutputStream>();
	/** Windows that went past {@link #MAX_REPLAY_BYTES} and want a whole-buffer reset instead. */
	private final java.util.HashSet<String> mHeldOverflowed = new java.util.HashSet<String>();

	/** Start holding text: the game window has gone off screen.
	 *
	 * <p>Idempotent. Called from {@link StellarService#setWindowShowing(boolean)}.
	 */
	public final void holdTextWhileHidden() {
		synchronized (mHeldSynch) {
			mHoldingForHiddenUi = true;
		}
	}

	/** Keep text for a window whose UI is not on screen, instead of pushing it.
	 *
	 * <p>Both callers write the same bytes into the window's own service-side
	 * buffer *before* calling here, so nothing is lost by not delivering: this
	 * only exists so the UI's in-process copy can be caught up on return.
	 *
	 * <p>Why hold at all, now that IWindowCallback is oneway and can no longer
	 * kill a frozen process: a one-way transaction to a frozen process sits in
	 * that process's async binder buffer, which is roughly half a megabyte and
	 * shared process-wide. A chatty MUD and a long idle would fill it, and the
	 * overflow arrives as a plain RemoteException — text dropped, with the UI
	 * copy silently short of the buffer it is supposed to mirror.
	 *
	 * <p>Past {@link #MAX_REPLAY_BYTES} we stop accumulating and mark the window
	 * for a full reset instead. That bounds what an overnight idle costs in the
	 * service, and by then almost everything on screen is new text anyway.
	 *
	 * @param window Name of the target window.
	 * @param data The bytes that would have been pushed.
	 * @return true when the bytes were held and must not be delivered now.
	 */
	private boolean holdWhileHidden(final String window, final byte[] data) {
		if (window == null || data == null || data.length == 0) {
			return false;
		}
		synchronized (mHeldSynch) {
			if (!mHoldingForHiddenUi) {
				return false;
			}
			if (mHeldOverflowed.contains(window)) {
				return true;
			}
			java.io.ByteArrayOutputStream held = mHeldWhileHidden.get(window);
			if (held == null) {
				held = new java.io.ByteArrayOutputStream();
				mHeldWhileHidden.put(window, held);
			}
			if (held.size() + data.length > MAX_REPLAY_BYTES) {
				mHeldWhileHidden.remove(window);
				mHeldOverflowed.add(window);
				return true;
			}
			held.write(data, 0, data.length);
		}
		return true;
	}

	/** Stop holding, and hand every window the text it missed.
	 *
	 * <p>Called from {@link StellarService#setWindowShowing(boolean)} when the
	 * game window comes back. Appending what was missed keeps the UI's existing
	 * scrollback; a window that overflowed gets the whole buffer back instead,
	 * which is the state a UI process that had been killed and restarted comes
	 * up in anyway. Idempotent — MainWindow says "showing" from both
	 * onServiceConnected and onResume.
	 *
	 * <p>Delivery happens with {@link #mHeldSynch} held, and the flag is
	 * cleared last. A dispatch thread arriving mid-hand-over blocks on the lock
	 * and is still holding when it gets there, so its line queues behind the
	 * older ones instead of overtaking them. That is affordable only because
	 * IWindowCallback is oneway now: these calls no longer wait for the UI
	 * process, so the lock is held for a queue append and nothing more.
	 */
	public final void flushTextHeldWhileHidden() {
		synchronized (mHeldSynch) {
			// Skip dead binders — a UI process killed from recents leaves corpses
			// in mWindowCallbackMap, and a oneway call at them is a silent no-op.
			// Always clear the hold at the end: keeping mHoldingForHiddenUi true
			// for "undelivered" entries would freeze every live window's updates
			// until the last missing slot registered. Text for dead windows is
			// still in the WindowToken buffer; registerWindowCallback replays it.
			for (String name : mHeldOverflowed) {
				IWindowCallback c = mWindowCallbackMap.get(name);
				if (!windowCallbackAlive(c)) {
					if (c != null) {
						dropDeadWindowCallback(name);
					}
					continue;
				}
				try {
					doInvalidateWindowText(name);
				} catch (RemoteException e) {
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
							"Connection.flushTextHeldWhileHidden", e);
				}
			}
			for (java.util.Map.Entry<String, java.io.ByteArrayOutputStream> e : mHeldWhileHidden.entrySet()) {
				IWindowCallback c = mWindowCallbackMap.get(e.getKey());
				if (!windowCallbackAlive(c)) {
					if (c != null) {
						dropDeadWindowCallback(e.getKey());
					}
					continue;
				}
				try {
					c.rawDataIncoming(e.getValue().toByteArray());
				} catch (android.os.DeadObjectException dead) {
					dropDeadWindowCallback(e.getKey());
				} catch (RemoteException re) {
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
							"Connection.flushTextHeldWhileHidden", re);
				}
			}
			mHeldWhileHidden.clear();
			mHeldOverflowed.clear();
			mHoldingForHiddenUi = false;
		}
	}

	/** True when the far side of this binder is still a live process. */
	private static boolean windowCallbackAlive(final IWindowCallback c) {
		if (c == null) {
			return false;
		}
		try {
			android.os.IBinder b = c.asBinder();
			return b != null && b.isBinderAlive();
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Drop any hold queued for {@code name}; the window is about to get a full replay. */
	private void discardHeldTextForWindow(final String name) {
		if (name == null) {
			return;
		}
		synchronized (mHeldSynch) {
			mHeldWhileHidden.remove(name);
			mHeldOverflowed.remove(name);
			if (mHeldWhileHidden.isEmpty() && mHeldOverflowed.isEmpty()) {
				mHoldingForHiddenUi = false;
			}
		}
	}

	/** Work horse method for plugins to invalidate a target window's text.
	 *
	 * @param name Name of the window that should invalidate it's text.
	 * @throws RemoteException Thrown when there is a problem with the aidl bridge.
	 */
	protected final void doInvalidateWindowText(final String name) throws RemoteException {

		IWindowCallback callback = mWindowCallbackMap.get(name);
	
		if (callback == null) {
			return;
		}
		
		WindowToken w = null;
		for (int i = 0; i < mWindows.size(); i++) {
			WindowToken tmp = mWindows.get(i);
			if (tmp.getName().equals(name)) {
				w = tmp;
			}
		}
		// A registered callback for a window that is not in mWindows is possible
		// while the window set is being rebuilt; it used to be an NPE one line
		// down. Now that flushTextHeldWhileHidden can arrive here with any name,
		// it is worth not being one.
		if (w == null || w.getBuffer() == null) {
			return;
		}

		TextTree buffer = w.getBuffer();

		try {
			// Trimmed for the same reason replayBufferToExtraTextWindow trims: a
			// full buffer can be megabytes, the binder ceiling is about one, and
			// going over arrives as a plain RemoteException that would leave the
			// window blank — the opposite of what a reset is for.
			callback.resetWithRawDataIncoming(
					trimToNewestLines(buffer.dumpToBytes(true), MAX_REPLAY_BYTES));
		} catch (android.os.DeadObjectException e) {
			dropDeadWindowCallback(name);
		}
	}

	/** Work horse method for WindowXCallS Lua function.
	 * 
	 * @param name Name of the target window.
	 * @param function Name of the anonymous global function to call
	 * @param o String argument to provide to @param function
	 * @throws RemoteException Thrown when there is a problem with the aidl bridge.
	 */
	public final void windowXCallS(final String name, final String function, final Object o) throws RemoteException {

		IWindowCallback c = mWindowCallbackMap.get(name);

		if (c != null) {
			try {
				c.xcallS(function, (String) o);
			} catch (android.os.DeadObjectException e) {
				dropDeadWindowCallback(name);
			}
		}

	}

	/** Work horse method for WindowXCallB Lua function.
	 * 
	 * @param name Name of the target window.
	 * @param functions Name of the anonymous global function to call.
	 * @param bytes Bytes to provide as an argument to @param function
	 * @throws RemoteException Thrown when there is a problem with the aidl bridge.
	 */
	protected final void windowXCallB(final String name, final String functions, final byte[] bytes) throws RemoteException {
		IWindowCallback c = mWindowCallbackMap.get(name);
		if (c != null) {
			try {
				c.xcallB(functions, bytes);
			} catch (android.os.DeadObjectException e) {
				dropDeadWindowCallback(name);
			}
		}
	}
	
	/** Work horse method for the CallPlugin Lua function.
	 * 
	 * @param plugin Name of the plugin to call.
	 * @param function Name of the anonymous global function to call.
	 * @param data String argument to provide to @param function.
	 */
	private void doCallPlugin(final String plugin, final String function, final String data) {
		Plugin p = mPluginMap.get(plugin);
		if (p != null) {
			p.callFunction(function, data);
		} else {
			this.dispatchLuaText("\n" + Colorizer.getRedColor() + "No plugin named: " + plugin + Colorizer.getRedColor() + "\n");
		}
	}

	/** Calling this method will reload the connection settings and all plugins. */
	public final void reloadSettings() {

		for (IWindowCallback c : mWindowCallbackMap.values()) {
			try {
				c.shutdown();
			} catch (RemoteException e) {
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("Connection.reloadSettings", e);
			}
		}
		
		mWindowCallbackMap.clear();
		mService.markWindowsDirty();
		mSettingsIO.loadInternalSettings();
		
	}
	
	/** Shuts down all running plugins and clears associated structures.*/
	void shutdownPlugins() {
		for (Plugin p : mPlugins) {
			p.shutdown();
			p = null;
		}
		mPlugins.clear();
	}
	
	/** Loads plugins and sets up internal data structures.
	 * 
	 * @param tmpPlugs The array of already loaded plugins.
	 * @param summary A holder string fo what happened during the internal loading process.
	 */
	void loadPlugins(final ArrayList<Plugin> tmpPlugs, final String summary) {
		
		HashMap<String, TextTree> bufferSaves = new HashMap<String, TextTree>();
		
		TextTree buffer = null;
		if (mWindows.size() > 0) {
			buffer = mWindows.get(0).getBuffer();
			while (mWindows.size() > 0) {
				WindowToken t = mWindows.remove(mWindows.size() - 1);
				bufferSaves.put(t.getName(), t.getBuffer());
			}
		} 
		if (mSettings != null) {
			mSettings.shutdown();
			
		}
		mSettings = null;
			
		mSettings = (ConnectionSettingsPlugin) tmpPlugs.get(0);
		mSettings.sortTriggers();
		mSettings.initTimers();
		// Entering a world the service is still connected to sends no
		// MESSAGE_CONNECTED, so anything hung off the connect path would work
		// only the first time. Settings are loaded on every way in, so this is
		// the honest hook.
		if (mService != null) {
			mService.refreshDeviceState();
		}
		for (WindowToken tmpw : mSettings.getSettings().getWindows().values()) {
			tmpw.setDisplayHost(mDisplay);
		}
		
		mWindows.add(0, mSettings.getSettings().getWindows().get(MAIN_WINDOW));
		if (buffer == null) {
			buffer = mWindows.get(0).getBuffer();
		} else {
			buffer.addString("\n\n");
		}
		
		buffer.addString(summary);
		tmpPlugs.remove(0);
		
		mPluginMap.clear();
		mLinkMap.clear();
		mFailedLinks.clear();
		
		mPlugins.addAll(tmpPlugs);
		
		
		for (Plugin p : mPlugins) {
			for (WindowToken tmpw : p.getSettings().getWindows().values()) {
				tmpw.setDisplayHost(mDisplay);
			}
			p.initTimers();
			mPluginMap.put(p.getName(), p);
			p.sortTriggers();
			if (p.getSettings().getWindows().size() > 0) {
				mWindows.addAll(p.getSettings().getWindows().values());
			}
			
			p.pushOptionsToLua();
		}
		
		if (mSettings.getDirections().size() == 0) {
			HashMap<String, DirectionData> tmp = new HashMap<String, DirectionData>();
			tmp.put("n", new DirectionData("n", "n"));
			tmp.put("e", new DirectionData("e", "e"));
			tmp.put("s", new DirectionData("s", "s"));
			tmp.put("w", new DirectionData("w", "w"));
			tmp.put("h", new DirectionData("h", "nw"));
			tmp.put("j", new DirectionData("j", "ne"));
			tmp.put("k", new DirectionData("k", "sw"));
			tmp.put("l", new DirectionData("l", "se"));
			mSettings.setDirections(tmp);
			mSpeedwalkCommand.setDirections(tmp);
		} else {
			mSpeedwalkCommand.setDirections(mSettings.getDirections());
		}
		
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			for (String link : mSettings.getLinks()) {
				buffer.addString(Colorizer.getWhiteColor() + "Loading plugin file: " + link);
				File pluginFile = resolveExternalPluginFile(link);
				String filename = pluginFile.getAbsolutePath();
				ArrayList<Plugin> tmplist = new ArrayList<Plugin>();
				PluginParser parse = new PluginParser(filename, link, mService.getApplicationContext(), tmplist, mHandler, this);
				
				try {
					if (!pluginFile.exists()) {
						throw new FileNotFoundException(filename);
					}
					ArrayList<Plugin> group = parse.load();
					if (group == null || group.isEmpty()) {
						String reason = "no plugins in file";
						mFailedLinks.put(link, reason);
						buffer.addString(Colorizer.getRedColor() + " " + reason + "."
								+ Colorizer.getWhiteColor() + "\n");
						continue;
					}
					for (Plugin p : group) {
						mPluginMap.put(p.getName(), p);
						if (mLinkMap.get(link) == null) {
							ArrayList<String> vals = new ArrayList<String>();
							vals.add(p.getName());
							mLinkMap.put(link, vals);
						} else {
							ArrayList<String> vals = mLinkMap.get(link);
							vals.add(p.getName());
						}
						
						for (WindowToken tmpw : p.getSettings().getWindows().values()) {
							tmpw.setDisplayHost(mDisplay);
						}
						
						if (p.getSettings().getWindows().size() > 0) {
							mWindows.addAll(p.getSettings().getWindows().values());
						}
						
						p.pushOptionsToLua();
					}
					
					mPlugins.addAll(group);
					
					buffer.addString(Colorizer.getWhiteColor() + ", success." + Colorizer.getWhiteColor() + "\n");
				} catch (FileNotFoundException e) {
					mFailedLinks.put(link, "file not found");
					buffer.addString(Colorizer.getRedColor() + " file not found." + Colorizer.getWhiteColor() + "\n");
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("Connection.reloadSettings", e);
				} catch (IOException e) {
					mFailedLinks.put(link, "read error");
					buffer.addString(Colorizer.getRedColor() + " read error." + Colorizer.getWhiteColor() + "\n");
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("Connection.reloadSettings", e);
				} catch (SAXException e) {
					String detail = e.getLocalizedMessage();
					if (detail == null || detail.length() == 0) {
						detail = "XML parse error";
					}
					mFailedLinks.put(link, detail);
					buffer.addString(Colorizer.getRedColor() + " XML Parse error.\n" + detail + Colorizer.getWhiteColor() + "\n");
				}
			}
		} else {
			for (String link : mSettings.getLinks()) {
				mFailedLinks.put(link, "storage not mounted");
			}
		}
	
		
		//so now that we have all the plugins, we need to build up the processor's gmcpTriggerTables.
		//loop through all the plugins, looking for literal triggers starting
		//with the gmcpTriggerChar.
		
		if (bufferSaves != null) {
			for (WindowToken w : mWindows) {
				if (w != null) {
					
					if (bufferSaves.get(w.getName()) != null) {
						w.setBuffer(bufferSaves.get(w.getName()));
					}
				}
			}
		}
		
		mSettingsIO.buildSettingsPage();
		syncLegacyLineSizeWithFont();
		clampExcessiveFontSizeFromBadFit();
		undoAggressiveMapDefaults();
		if (mMapper != null) {
			mMapper.applySettingsFromConnection();
			// Per world, not one shared "default" for every MUD you connect to.
			mMapper.openMapForHost(getHost());
		}
		ensureExtraTextSlots(false);
		if (mGauges != null) {
			mGauges.reloadFromSettings();
			requestGaugeWidgetUi();
			requestGaugeWidgetValues();
		}
		mService.reloadWindows();
		
	}

	/**
	 * Profiles created with {@code Math.max(20, calculate80CharFontSize())} got
	 * ~40–50px fonts on modern phones. Cap once back to a readable default.
	 */
	private void clampExcessiveFontSizeFromBadFit() {
		if (mWindows == null || mWindows.isEmpty() || mSettings == null) {
			return;
		}
		try {
			WindowToken main = mWindows.get(0);
			if (main == null || main.getSettings() == null) {
				return;
			}
			Object opt = main.getSettings().findOptionByKey("font_size");
			if (!(opt instanceof IntegerOption)) {
				return;
			}
			int fontSize = (Integer) ((IntegerOption) opt).getValue();
			// 80-col fit on a ~2400px edge lands around 40–55; real prefs are usually ≤28.
			if (fontSize <= 36) {
				return;
			}
			int use = WindowToken.DEFAULT_FONT_SIZE;
			String fontStr = Integer.toString(use);
			main.getSettings().setOption("font_size", fontStr);
			mSettings.setLineSize(use);
			if (mSettings.getSettings() != null && mSettings.getSettings().getOptions() != null) {
				mSettings.getSettings().getOptions().setOption("font_size", fontStr);
			}
			IWindowCallback cb = mWindowCallbackMap.get(main.getName());
			if (cb != null) {
				try {
					cb.updateSetting("font_size", fontStr);
				} catch (RemoteException e) {
					Log.w("BlowTorch", "clamp font_size UI update failed", e);
				}
			}
			Log.i("BlowTorch", "Clamped excessive font_size " + fontSize + " → " + use);
			mHandler.obtainMessage(MESSAGE_SAVESETTINGS, "").sendToTarget();
		} catch (Exception e) {
			Log.w("BlowTorch", "clampExcessiveFontSizeFromBadFit failed", e);
		}
	}

	/**
	 * v244 forced word_wrap=false / line_extra=0 on every profile. Restore once per
	 * install via a SharedPreferences flag — never on every connection load.
	 */
	private void undoAggressiveMapDefaults() {
		if (mWindows == null || mWindows.isEmpty() || mService == null) {
			return;
		}
		try {
			android.content.SharedPreferences prefs =
					mService.getSharedPreferences("BT_MIGRATIONS", android.content.Context.MODE_PRIVATE);
			if (prefs.getBoolean("undo_map_clamp_v245", false)) {
				return;
			}
			boolean dirty = false;
			for (WindowToken w : mWindows) {
				if (w == null || w.getSettings() == null) {
					continue;
				}
				Object wrap = w.getSettings().findOptionByKey("word_wrap");
				if (wrap instanceof BooleanOption && Boolean.FALSE.equals(((BooleanOption) wrap).getValue())) {
					((BooleanOption) wrap).setValue(true);
					dirty = true;
				}
				Object extra = w.getSettings().findOptionByKey("line_extra");
				if (extra instanceof IntegerOption) {
					Object val = ((IntegerOption) extra).getValue();
					if (val instanceof Integer && (Integer) val == 0) {
						((IntegerOption) extra).setValue(2);
						dirty = true;
					}
				}
			}
			prefs.edit().putBoolean("undo_map_clamp_v245", true).apply();
			if (dirty) {
				Log.i("BlowTorch", "Restored word_wrap=true, line_extra=2 (undo map-only clamp)");
				mHandler.obtainMessage(MESSAGE_SAVESETTINGS, "").sendToTarget();
			}
		} catch (Exception ignored) {
		}
	}

	/**
	 * Legacy XML still stores {@code lineSize} on {@code <window>}, while the UI uses
	 * {@code font_size} on the mainDisplay token. Keep them equal so a save/reload
	 * does not flash between 10 and 20 and change the NAWS column count mid-session.
	 */
	private void syncLegacyLineSizeWithFont() {
		if (mSettings == null || mWindows == null || mWindows.isEmpty()) {
			return;
		}
		try {
			WindowToken main = mWindows.get(0);
			if (main == null || main.getSettings() == null) {
				return;
			}
			Object opt = main.getSettings().findOptionByKey("font_size");
			if (opt instanceof IntegerOption) {
				int fontSize = (Integer) ((IntegerOption) opt).getValue();
				if (fontSize > 0) {
					mSettings.setLineSize(fontSize);
					// Keep the connection-level options copy in sync when present.
					if (mSettings.getSettings() != null && mSettings.getSettings().getOptions() != null) {
						mSettings.getSettings().getOptions().setOption("font_size", Integer.toString(fontSize));
					}
				}
			}
		} catch (Exception ignored) {
		}
	}
	
	/** Whether a trigger belongs in the combined pattern at all.
	 *
	 * A literal trigger whose text starts with "%" or the MCP prefix is a
	 * command for another subsystem, not something to match against game text.
	 *
	 * @param t The trigger to test.
	 * @return true when it should be added to the alternation.
	 */
	private static boolean isMatchableTrigger(final TriggerData t) {
		if (t == null || !t.isEnabled() || t.getPattern() == null) {
			return false;
		}
		if (!t.isInterpretAsRegex() && (t.getPattern().startsWith("%")
				|| t.getPattern().startsWith(McpEngine.TRIGGER_CHAR))) {
			return false;
		}
		// A device gesture is a trigger whose source is the phone, not the game.
		// Same shape as the two above, and deliberately narrow: only a reserved
		// prefix followed by a gesture this build knows is taken out, so a
		// literal trigger watching for "!!!" keeps matching text.
		if (com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.isGesturePattern(
				t.getPattern(), !t.isInterpretAsRegex())) {
			return false;
		}
		return true;
	}

	/** Work horse function to rebuild the trigger system.
	 *
	 * I think this is called from a number of placed, but it should really be called from dispatch()
	 * when triggers are dirty.
	 *
	 * The joining and the group arithmetic moved to TriggerPattern, which has
	 * tests, for the same reasons the alias half moved to AliasPattern: the
	 * alternation is built from the sanitised matcher rather than the raw
	 * pattern field, so there is one sanitisation point; Pattern.quote replaces
	 * the hand-built \Q...\E span; and the join is compiled inside a try, since
	 * this runs out of binder methods that have no catch of their own.
	 */
	public final void buildTriggerSystem() {
		if (mSettings == null) {
			return;
		}
		mSortedTriggerMap.clear();
		mTriggerPluginMap.clear();
		TriggerPattern combined = new TriggerPattern();
		// A trigger may name an alias in its pattern -- $alias{name} -- and this
		// is where the alias's text is pasted in. Here rather than in the
		// parser, because a trigger has to follow the alias it names: every
		// alias edit already rebuilds the trigger system through
		// ConnectionAliases, so the next line of game text is matched against
		// the alias as it is now.
		//
		// One table for everyone, and it is the player's. A plugin's trigger
		// writing $alias{x} therefore resolves against the player's aliases,
		// not the plugin's own -- which is the way round that cannot surprise
		// the player: the alternative lets a plugin decide what a name means
		// without the player being able to see it in their own alias list.
		java.util.Map<String, String> aliasBodies =
				com.resurrection.blowtorch2.lib.trigger.TriggerAliasReference.bodies(getAliases());
		ArrayList<TriggerData> tmp = mSettings.getSortedTriggers();
		if (tmp == null) {
			mSettings.sortTriggers();
			tmp = mSettings.getSortedTriggers();
		}
		if (tmp != null) {
			for (int i = 0; i < tmp.size(); i++) {
				TriggerData t = tmp.get(i);
				t.resolveAliases(aliasBodies);
				if (isMatchableTrigger(t)) {
					int group = combined.add(t);
					if (group > 0) {
						mSortedTriggerMap.put(group, t);
						mTriggerPluginMap.put(group, mSettings);
					}
				}
			}
		}

		for (Plugin p : mPlugins) {
			if (p == null || !p.isEnabled() || p == mSettings) {
				continue;
			}
			tmp = p.getSortedTriggers();
			if (tmp == null) {
				p.sortTriggers();
				tmp = p.getSortedTriggers();
			}
			if (tmp != null) {
				for (int i = 0; i < tmp.size(); i++) {
					TriggerData t = tmp.get(i);
					t.resolveAliases(aliasBodies);
					if (isMatchableTrigger(t)) {
						int group = combined.add(t);
						if (group > 0) {
							mSortedTriggerMap.put(group, t);
							mTriggerPluginMap.put(group, p);
						}
					}
				}
			}

		}
		// Every path that changes a trigger comes through here — the editor over
		// the binder, .sensor, .trigger, and the Lua NewTrigger/DeleteTrigger/
		// EnableTrigger functions. So this is where a gesture starts or stops
		// being listened for, rather than in whichever of those paths someone
		// remembered to touch.
		refreshDeviceGestures();
		mMassiveTriggerString = combined.regex();
		try {
			mMassivePattern = combined.compile(Pattern.MULTILINE);
		} catch (java.util.regex.PatternSyntaxException bad) {
			// Every alternative compiled on its own, so this is the rare join-only
			// failure -- two triggers declaring the same named group is one. The
			// maps have to be emptied along with the pattern: leaving them
			// populated while matching against something else would attribute a
			// match to a trigger that is not there, which is worse than matching
			// nothing.
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"Connection.buildTriggerSystem: combined trigger pattern would not compile,"
					+ " no trigger will fire until the set changes", bad);
			mSortedTriggerMap.clear();
			mTriggerPluginMap.clear();
			mMassiveTriggerString = "";
			mMassivePattern = Pattern.compile("");
		}
		mMassiveMatcher = mMassivePattern.matcher("");
		triggersDirty = false;
		if (mMcpEngine != null) {
			loadMcpTriggers();
		}
		rebuildTapRules();
	}

	/**
	 * The tappable-word rules as the window will use them, rebuilt with the
	 * trigger system and kept here.
	 *
	 * <p>Built in the service rather than in the activity because the activity
	 * building them meant dragging every trigger, responder and condition
	 * across the binder — on the UI thread, while the player was playing.
	 */
	private volatile java.util.List<com.resurrection.blowtorch2.lib.responder.tap.TapRuleData>
			mTapRules =
			new java.util.ArrayList<com.resurrection.blowtorch2.lib.responder.tap.TapRuleData>();

	/** False until the first rebuild; see {@link #getTapRules}. */
	private volatile boolean tapRulesBuilt;

	/**
	 * What the window is holding, read on a binder thread.
	 *
	 * <p>Volatile rather than locked: this hands back a list the service thread
	 * has finished with and then replaces wholesale, never one it edits in
	 * place, so the reader either sees the old complete list or the new one.
	 *
	 * <p>The build on the first call is for the cold start. The window asks as
	 * soon as it is up, which can be before the first line of game text has
	 * made the trigger system rebuild, and without this it would be told there
	 * are no tappable words and would believe it until the next trigger edit.
	 */
	public java.util.List<com.resurrection.blowtorch2.lib.responder.tap.TapRuleData>
			getTapRules() {
		if (!tapRulesBuilt) {
			try {
				mTapRules = buildTapRules();
				tapRulesBuilt = true;
			} catch (Exception e) {
				// The trigger map belongs to the service thread; the worst case
				// here is catching it mid-edit. Answering with what we have is
				// right — the rebuild that edit ends with pushes the real list.
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
						"Connection.getTapRules", e);
			}
		}
		return mTapRules;
	}

	/**
	 * Work out the tap rules again and tell the window only if they changed.
	 *
	 * <p>The gate is the point. {@code buildTriggerSystem} runs from inside
	 * dispatch — a trigger that enables or disables another trigger rebuilds
	 * the system on the line that fired it — so during a fight this can run
	 * several times a second. Almost none of those rebuilds change which words
	 * are tappable, and an unchanged list means nothing crosses the binder and
	 * the window recompiles nothing.
	 */
	private void rebuildTapRules() {
		java.util.List<com.resurrection.blowtorch2.lib.responder.tap.TapRuleData> next =
				buildTapRules();
		boolean same = tapRulesBuilt && next.equals(mTapRules);
		mTapRules = next;
		tapRulesBuilt = true;
		if (same) {
			return;
		}
		notifyTapRulesChanged();
	}

	/**
	 * One rule per enabled trigger carrying a tap action.
	 *
	 * <p>One per trigger, not per action: nothing stops a player putting two
	 * tappable actions on one trigger, and that would mark the same word twice
	 * and stack two hit boxes on it, so which command a tap sent would depend
	 * on which box was found last. {@code TapAction.merge} folds them into one.
	 */
	private java.util.List<com.resurrection.blowtorch2.lib.responder.tap.TapRuleData>
			buildTapRules() {
		java.util.List<com.resurrection.blowtorch2.lib.responder.tap.TapRuleData> out =
				new java.util.ArrayList<
						com.resurrection.blowtorch2.lib.responder.tap.TapRuleData>();
		java.util.HashMap<String, TriggerData> triggers = getTriggers();
		if (triggers == null) {
			return out;
		}
		for (TriggerData t : triggers.values()) {
			if (t == null || !t.isEnabled() || t.getResponders() == null) {
				continue;
			}
			java.util.List<com.resurrection.blowtorch2.lib.responder.tap.TapAction> taps = null;
			for (com.resurrection.blowtorch2.lib.responder.TriggerResponder r
					: t.getResponders()) {
				if (r instanceof com.resurrection.blowtorch2.lib.responder.tap.TapAction) {
					if (taps == null) {
						taps = new java.util.ArrayList<
								com.resurrection.blowtorch2.lib.responder.tap.TapAction>();
					}
					taps.add((com.resurrection.blowtorch2.lib.responder.tap.TapAction) r);
				}
			}
			if (taps == null) {
				continue;
			}
			com.resurrection.blowtorch2.lib.responder.tap.TapAction tap =
					com.resurrection.blowtorch2.lib.responder.tap.TapAction.merge(taps);
			if (tap == null) {
				continue;
			}
			// The trigger's own compiled pattern, not a fresh compile of the raw
			// text: buildData already quotes a literal trigger and has already
			// pasted in any alias the pattern names. Compiling the raw text made
			// a literal trigger behave as a regex, so a pattern like
			// "[ 9 | -4 | 1 ]" — a real one in this profile — was a character
			// class marking single characters all over the screen.
			java.util.regex.Pattern p = t.getCompiledPattern();
			if (p == null) {
				continue;
			}
			out.add(new com.resurrection.blowtorch2.lib.responder.tap.TapRuleData(
					p.pattern(), tap.getCommands().toArray(new String[0]),
					tap.isTapSendsFirst(), tap.isUnderline(), tap.isBold(),
					tap.isFrame(), tap.getGroup()));
		}
		return out;
	}

	/**
	 * Tell the UI its tappable-word rules are stale.
	 *
	 * <p>Here, at the end of the rebuild, because this is the one place every
	 * cause passes through: a trigger edited, a trigger enabled, an alias whose
	 * text a trigger's pattern names edited in the dialog or set with
	 * {@code .name newtext} from the input bar. Hooking the causes one at a
	 * time is how the input-bar one was missed -- the frame appeared for the
	 * alias's old text and nothing said why.
	 *
	 * <p>The activity coalesces these, so a burst during a profile load costs
	 * one read of the rules, not one per call.
	 */
	private void notifyTapRulesChanged() {
		IWindowCallback w = mWindowCallbackMap.get(MAIN_WINDOW);
		if (w == null) {
			return;
		}
		try {
			w.tapRulesChanged();
		} catch (RemoteException e) {
			// oneway, so this is a dead window rather than a failed call. The
			// rules are re-read when the activity resumes anyway.
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"Connection.notifyTapRulesChanged", e);
		}
	}
	
	/** end of the line of the DrawWindow function. I don't think this is used.
	 * 
	 * @param win Name of the window to redraw.
	 */
	protected final void redrawWindow(final String win) {

			IWindowCallback w = mWindowCallbackMap.get(win);
			if (w == null) {
				return;
			}
			try {
					w.redraw();
			} catch (RemoteException e) {
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("Connection.redrawWindow", e);
			}

	}

	/** Actual working method for the LineToWindow Lua function.
	 * 
	 * @param target Name of the window to recieve the line.
	 * @param line The TextTree.Line to send to @param target
	 * @throws RemoteException Thrown when there is a problem with the aidl bridge.
	 */
	protected final void lineToWindow(final String target, final Object line) throws RemoteException {
		String resolved = target;
		if (target != null && "main".equals(target)) {
			resolved = MAIN_WINDOW;
		}
		for (WindowToken w : mWindows) {
			if (w.getName().equals(resolved)) {
				TextTree tmp = new TextTree();
				tmp.setEncoding(mSettings.getEncoding());
				if (line instanceof TextTree.Line) {
					tmp.appendLine((TextTree.Line) line);
				} else if (line instanceof String) {
					try {
						tmp.addBytesImpl(((String) line).getBytes(mSettings.getEncoding()));
					} catch (UnsupportedEncodingException e) {
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor("Connection.lineToWindow", e);
					}
				}
				tmp.updateMetrics();
				byte[] lol = tmp.dumpToBytes(false);
				
				try {
					w.getBuffer().addBytesImpl(lol);
				} catch (UnsupportedEncodingException e) {
					
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor("Connection.lineToWindow", e);
				}

					if (!holdWhileHidden(resolved, lol)) {
						IWindowCallback c = mWindowCallbackMap.get(resolved);
						if (c != null) {
							try {
								c.rawDataIncoming(lol);
							} catch (android.os.DeadObjectException e) {
								dropDeadWindowCallback(resolved);
							}
						}
					}
			}
		}
	}

	/**
	 * Fan out one inbound GMCP packet to extra text slots whose {@code gmcp}
	 * patterns match {@code module}. Does not dump all GMCP — only configured routes.
	 */
	protected final void routeGmcpToExtraWindows(final String module, final String bodyJson) {
		if (module == null || module.length() == 0) {
			return;
		}
		if (mExtraText.isEmpty()) {
			return;
		}
		// Read-only view: this runs per received GMCP message, so it avoids copying.
		java.util.List<ExtraTextSlot> slots = mExtraText.peekSlots();
		boolean any = false;
		for (int i = 0; i < slots.size(); i++) {
			ExtraTextSlot s = slots.get(i);
			if (s != null && s.matchesGmcpModule(module)) {
				any = true;
				break;
			}
		}
		if (!any) {
			return;
		}
		String safe = bodyJson != null ? bodyJson : "";
		if (module.toLowerCase(java.util.Locale.US).contains("char.login.credentials")
				|| safe.toLowerCase(java.util.Locale.US).contains("\"password\"")) {
			safe = safe.replaceAll("(?i)(\"password\"\\s*:\\s*\")([^\"]*)(\")", "$1***$3");
		}
		if (safe.length() > 2000) {
			safe = safe.substring(0, 2000) + "…";
		}
		String line = "\n" + Colorizer.getTeloptStartColor()
				+ "[GMCP] " + module + (safe.length() > 0 ? (" " + safe) : "")
				+ Colorizer.getResetColor() + "\n";
		for (int i = 0; i < slots.size(); i++) {
			ExtraTextSlot s = slots.get(i);
			if (s == null || !s.matchesGmcpModule(module)) {
				continue;
			}
			try {
				lineToWindow(s.getName(), line);
			} catch (RemoteException e) {
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("Connection.routeGmcpToExtraWindows", e);
			}
		}
	}
	
	/** Called from the aidl bridge housing in StellarService when the foreground window has started a new
	 * window and needs to let the Connection know that a new window is open for it.
	 * 
	 * @param name The name of the new window.
	 * @param callback The IWindowCallback aidl conenction object associated with the window.
	 */
	public final void registerWindowCallback(final String name, final IWindowCallback callback) {
		final boolean replacedDeadOrMissing;
		synchronized (mWindowSynch) {
		Log.e("LOG","REGISTERING WINDOW "+name + " mCallbacksStarte="+mCallbacksStarted);
		if (mCallbacksStarted) {
			mWindowCallbacks.finishBroadcast();
		}
		Log.e("LOG","REGISTERING " + name);
		IWindowCallback previous = mWindowCallbackMap.get(name);
		replacedDeadOrMissing = !windowCallbackAlive(previous);
		// Kill-from-recents leaves the old IWindowCallback in RemoteCallbackList.
		// dirtyExit/cleanupWindows unregisters it; a swipe-kill does not. Rebuilding
		// the name→callback map from the list then let a later corpse with the same
		// cookie overwrite the live binder — replay reached the new Window (history
		// looked fine) but notifyMainWindow kept calling the dead one, so the game
		// stayed "dead" until the player left via Keep-in-background (which cleans
		// up) and came back. Cull every dead binder for this name before register.
		if (previous != null && replacedDeadOrMissing) {
			mWindowCallbacks.unregister(previous);
			mWindowCallbackMap.remove(name);
		}
		int sweep = mWindowCallbacks.beginBroadcast();
		java.util.ArrayList<IWindowCallback> corpses =
				new java.util.ArrayList<IWindowCallback>();
		for (int i = 0; i < sweep; i++) {
			Object cookie = mWindowCallbacks.getBroadcastCookie(i);
			IWindowCallback w = mWindowCallbacks.getBroadcastItem(i);
			if (name.equals(cookie) && !windowCallbackAlive(w)) {
				corpses.add(w);
			}
		}
		mWindowCallbacks.finishBroadcast();
		for (int i = 0; i < corpses.size(); i++) {
			mWindowCallbacks.unregister(corpses.get(i));
		}
		// The name is the cookie. It used to come back from callback.getName(),
		// a synchronous binder call into the UI process for every registered
		// window on every register — and a synchronous call is what kills a
		// frozen UI process. The caller already passed the name in; keeping it
		// here is what let IWindowCallback become oneway.
		mWindowCallbacks.register(callback, name);

		int n = mWindowCallbacks.beginBroadcast();
		for (int i = 0; i < n; i++) {
			IWindowCallback w = mWindowCallbacks.getBroadcastItem(i);
			Object cookie = mWindowCallbacks.getBroadcastCookie(i);
			if (!(cookie instanceof String)) {
				continue;
			}
			// Only live binders into the map — a dead one must not win the put.
			if (windowCallbackAlive(w)) {
				mWindowCallbackMap.put((String) cookie, w);
			}
		}
		// This registration always owns its name, even if isBinderAlive races.
		mWindowCallbackMap.put(name, callback);
		mCallbacksStarted = true;
		}
		// Outside the lock on purpose: resetWithRawDataIncoming posts into the UI
		// process, and holding mWindowSynch across it would let a busy UI thread
		// stall every other window's routing.
		//
		// Extra-text slots always need a replay (they never parcel a buffer into
		// initWindow). mainDisplay only needs one when the previous binder is
		// gone — UI process killed from recents — because a live re-register
		// already has the in-process tree. resetWithRawDataIncoming replaces,
		// it does not append, so this cannot stack a second copy of the session.
		if (replacedDeadOrMissing) {
			discardHeldTextForWindow(name);
			replayBufferToWindow(name, callback, true);
		} else {
			replayBufferToWindow(name, callback, false);
		}
		// Telnet ECHO is service-side state, like the buffer: a window that arrives
		// after the server took echoing over — UI process killed from recents, or a
		// window registering after the negotiation — would otherwise show the
		// password in the clear.
		if (!mLocalEcho) {
			try {
				callback.setLocalEcho(false);
			} catch (RemoteException e) {
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
						"Connection.registerWindowCallback.setLocalEcho", e);
			}
		}
	}

	/** Largest replay we will hand to a single binder transaction.
	 * The limit is about 1 MB and shared process-wide, and a full chat slot can get
	 * close, so stay well under it: TransactionTooLargeException arrives as a plain
	 * RemoteException and would leave the window silently blank — the very thing this
	 * replay exists to prevent. */
	private static final int MAX_REPLAY_BYTES = 128 * 1024;

	/** Hand a newly attached window the text in its service-side token buffer.
	 *
	 * <p>{@link #lineToWindow} / {@link #dispatch} always write the WindowToken
	 * buffer first, then notify the callback if one is registered, so a hidden
	 * or dead UI has been collecting all along. Extra-text slots never got that
	 * history on open (they do not parcel a buffer into {@code initWindow}).
	 * {@code mainDisplay} does parcel one, but after a UI process kill the
	 * hand-over raced {@code windowShowing(true)} ahead of registration and
	 * left a blank window; replaying here is the barrier that comment on
	 * {@link #flushTextHeldWhileHidden} already promised.
	 *
	 * @param name The window being registered.
	 * @param callback Its fresh callback.
	 * @param includeMain When true, also replay {@code mainDisplay}; when false,
	 *     only extra-text slots (the historical path).
	 */
	private void replayBufferToWindow(final String name, final IWindowCallback callback,
			final boolean includeMain) {
		if (name == null || callback == null) {
			return;
		}
		final boolean extra = mExtraText.find(name) != null;
		final boolean main = includeMain && MAIN_WINDOW.equals(name);
		if (!extra && !main) {
			return;
		}
		WindowToken token = getWindowByName(name);
		if (token == null || token.getBuffer() == null) {
			return;
		}
		// keep == true: this is a replay, not a handover. dumpToBytes(false) empties the
		// tree, which would make the window blank again the second time it was opened.
		byte[] history = token.getBuffer().dumpToBytes(true);
		if (history == null || history.length == 0) {
			return;
		}
		// Before the replay is trimmed: the vocabulary wants the same text and its
		// own, smaller budget.
		if (main) {
			seedVocabularyFromHistory(history);
		}
		history = trimToNewestLines(history, MAX_REPLAY_BYTES);
		try {
			callback.resetWithRawDataIncoming(history);
		} catch (RemoteException e) {
			Log.w("BlowTorch", "Could not replay history to window " + name, e);
		}
	}

	/** Largest vocabulary seed we will send after a UI process death.
	 *
	 * <p>Much smaller than {@link #MAX_REPLAY_BYTES} because this is not history the
	 * player reads, it is words the completer offers, and {@code WordSuggestions}
	 * prunes to its own line window the moment it has learned them. Sending more
	 * than that window holds is work on the UI thread whose result is thrown away.
	 *
	 * <p>Sized to the default 300-line window at a typical MUD line length. A
	 * probe on 08.08 measured a 3553-character seed costing 5 ms in {@code learn}
	 * on the main thread; this cap is where that stays in the tens of
	 * milliseconds rather than growing with however long the session ran. The
	 * cost at the cap itself was not measured. */
	private static final int MAX_VOCABULARY_SEED_BYTES = 24 * 1024;

	/** Teach the completer the text that was already on screen.
	 *
	 * <p>The vocabulary lives in the UI process, so a UI process death empties it,
	 * and nothing refills it: the window adopts a parceled {@code TextTree}, while
	 * {@link WordSuggestions#learn} is fed only by freshly arriving packets in
	 * {@link #addBytes}. Kill the app, re-enter a world that happens to be quiet,
	 * and the game text is right there on screen with not one word of it offered
	 * back — until the world says something new. On a busy MUD the first line
	 * hides it, which is why this went unnoticed.
	 *
	 * <p>Only on the path that already knows the UI died, so a window re-attaching
	 * with its vocabulary intact does not learn the same session twice.
	 *
	 * @param history The untrimmed buffer dump, oldest byte first.
	 */
	private void seedVocabularyFromHistory(final byte[] history) {
		// Same gate as the live path in addBytes: a player with the completer off
		// pays no binder traffic for it.
		if (!mWordComplete || history == null || history.length == 0) {
			return;
		}
		byte[] recent = trimToNewestLines(history, MAX_VOCABULARY_SEED_BYTES);
		String text;
		try {
			text = Colorizer.stripAnsiEscapes(new String(recent, mSettings.getEncoding()));
		} catch (java.io.UnsupportedEncodingException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"Connection.seedVocabularyFromHistory", e);
			return;
		}
		if (text.length() == 0) {
			return;
		}
		// The dump ends wherever the world stopped talking, which is usually a
		// prompt with no newline. learn() would hold that tail in `pending` and
		// glue it to the front of the first live packet, teaching a word nobody
		// wrote, and would let a phrase run from the last seeded word into it.
		// A closing newline is what ends both.
		if (text.charAt(text.length() - 1) != '\n') {
			text = text + "\n";
		}
		mService.doVocabularyText(mDisplay, text);
	}

	/** Cut a dump down to its newest bytes without starting mid-line.
	 *
	 * <p>dumpToBytes walks oldest to newest, so the newest text is at the end and the
	 * tail is what we want to keep. Advancing past the first newline costs at most one
	 * partial line and avoids opening the window on half a sentence.
	 *
	 * @param data The full dump.
	 * @param budget Maximum bytes to return.
	 * @return data itself when it already fits, otherwise its tail.
	 */
	static byte[] trimToNewestLines(final byte[] data, final int budget) {
		if (data == null || data.length <= budget) {
			return data;
		}
		final int rawCut = data.length - budget;
		int start = rawCut;
		for (int i = rawCut; i < data.length; i++) {
			if (data[i] == '\n') {
				start = i + 1;
				break;
			}
		}
		if (start >= data.length) {
			// The only newline in the window was the last byte. Tidiness is not worth
			// handing back nothing and blanking the window we came here to fill.
			start = rawCut;
		}
		byte[] out = new byte[data.length - start];
		System.arraycopy(data, start, out, 0, out.length);
		return out;
	}

	/** Called from the aidl bridge housing in StellarService when the foreground window has stopped and destroyed a
	 * window and needs to let the Connection know that the IWindowCallback is invalid.
	 * 
	 * @param callback The IWindowCallback aidl connection object of the destroyed window.
	 */
	public final void unregisterWindowCallback(final IWindowCallback callback) {
		synchronized (mWindowSynch) {
		Log.e("LOG","UNREGISTERING WINDOW "+" mCallbacksStarted="+mCallbacksStarted);
		if (mCallbacksStarted) {
			mWindowCallbacks.finishBroadcast();
			//mCallbacksStarted = false;
		}
		mWindowCallbacks.unregister(callback);

		mWindowCallbackMap.clear();
		int n = mWindowCallbacks.beginBroadcast();
		for (int i = 0; i < n; i++) {
			IWindowCallback w = mWindowCallbacks.getBroadcastItem(i);
			Object cookie = mWindowCallbacks.getBroadcastCookie(i);
			// Same rule as registerWindowCallback: a swipe-killed binder still in
			// the list must not reclaim the map entry for a live window.
			if (cookie instanceof String && windowCallbackAlive(w)) {
				mWindowCallbackMap.put((String) cookie, w);
			}
		}

		mCallbacksStarted = true;
		}
	}
	
	/** Called from the DataPumper when the net threads have been shut down.
	 * 
	 * @param override Indicates weather the auto reconnect should be overridden.
	 */
	protected final void doDisconnect(final boolean override) {
		if (mHandler == null) {
			return;
		}
		// A dead connection cannot be echoing for us; a masked input bar that never
		// unmasks would look like a broken keyboard.
		restoreLocalEcho();
		if (!override) {
			int remaining = mReconnect.consumeAttempt(THREE_THOUSAND_MILLIS);
			if (remaining >= 0) {
				String message = "\n" + Colorizer.getRedColor() + "Network connection disconnected.\n"
								 + "Attempting reconnect"
								 + mReconnect.describeNextAttempt(" in 3 seconds.")
								 + " " + remaining + " tries remaining."
								 + mReconnect.persistentNote()
								 + Colorizer.getWhiteColor() + "\n";
				mHandler.sendMessage(mHandler.obtainMessage(Connection.MESSAGE_PROCESSORWARNING, message));
				return;
			}
		}

		mReconnect.clearNetworkWait();
		markConnectionEnded();
		// Here and not in markConnectionEnded: that runs from killNetThreads too,
		// which fires on every network flap that still has retries left. Writing
		// the map there would put a serialize and a file write back on the thread
		// carrying game text, once per flap, which is what 0d10e705 took off it.
		// Past the retry check above, the session really is over.
		flushMapperSaves();
		mService.doDisconnect(this);
	}
	
	/** Called from various sources to kill the DataPumper and all of its threads.
	 * 
	 * @param noreconnect true if there should be no reconnect attempt made.
	 */
	/** How long to give a net thread to stop before moving on without it. */
	private static final int PUMP_SHUTDOWN_WAIT_MS = 2000;

	protected final void killNetThreads(final boolean noreconnect) {
		
		if (mPump == null) {
			clearStartupInProgress();
			return;
		}
		Log.w("BlowTorch", "killNetThreads(noreconnect=" + noreconnect + ")", new RuntimeException("killNetThreads caller"));
		// Show the half-line the connection died holding rather than swallowing
		// it — the last thing a server says before dropping you is often the
		// reason it dropped you, and it frequently has no newline after it.
		mHandler.removeMessages(MESSAGE_FLUSH_LINE_HOLDOVER);
		if (mLineHoldover.hasHeld()) {
			try {
				flushLineHoldover();
			} catch (UnsupportedEncodingException bad) {
				mLineHoldover.clear();
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
						"Connection.killNetThreads: holdover flush", bad);
			}
		}
		markConnectionEnded();
		if (noreconnect) {
			mReconnect.clearNetworkWait();
		}
		if (mPump != null) {
			if (mPump.getHandler() != null) {
				mPump.closeSocket();
				//mPump.getHandler().removeMessages(DataPumper.MESSAGE_RETRIEVE);
				mPump.getHandler().removeCallbacksAndMessages(null);
				mPump.getHandler().sendEmptyMessage(DataPumper.MESSAGE_END);
			
			
				try {
					// Bounded, like the branch below already is. This runs on the
					// connection handler — the thread that processes every command,
					// reconnect and window update for this session. Waiting forever
					// for a pump stuck in a read on a half-open socket would freeze
					// the whole connection while the service still looked healthy.
					mPump.join(PUMP_SHUTDOWN_WAIT_MS);
					if (mPump.isAlive()) {
						mPump.interrupt();
						mPump.join(PUMP_SHUTDOWN_WAIT_MS);
					}
					if (mPump.isAlive()) {
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
								"Connection.killNetThreads",
								new IllegalStateException("data pump did not stop within "
										+ (2 * PUMP_SHUTDOWN_WAIT_MS) + "ms; carrying on without it"));
					}
				} catch (InterruptedException e) {
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor("Connection.killNetThreads", e);
				}
			} else {
				// Handler not ready yet (still in init) — force the thread down.
				try {
					mPump.closeSocket();
					mPump.interrupt();
					mPump.join(2000);
				} catch (InterruptedException e) {
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor("Connection.killNetThreads", e);
				}
			}
		}
		
		if (mProcessor != null) {
			mProcessor.releaseGmcpHelpers();
		}
		if (mMcpEngine != null) {
			mMcpEngine.resetSession();
		}
		mProcessor = null;
		
		if (noreconnect) {
			if (mHandler != null) {
				mHandler.removeMessages(MESSAGE_RECONNECT);
			}
		}
		
		mPump = null;
		clearStartupInProgress();
		mLastSentNawsCols = -1;
		mLastSentNawsRows = -1;
	}
	
	/** Sends a byte array to the default output window. Does not invoke trigger processing.
	 * 
	 * @param data The data to send.
	 */
	public final void dispatchNoProcess(final byte[] data) {
		mWindows.get(0).getBuffer().addBytesImplSimple(data);
		// notifyMainWindow, not sendBytesToWindow: the buffer write is on the
		// line above, and sendBytesToWindow now does one of its own.
		notifyMainWindow(data);
	}
	
	/** Utility class used for trigger processing. Maps a start and end value to a line number.	 */
	private class Range {
		/** Start of the line. */
		private int mStart;
		/** End of the line. */
		private int mEnd;
		/** Line number. */
		private int mLine;
		/** Generic assignment constructor.
		 * 
		 * @param start Start of line.
		 * @param end End of line.
		 * @param line Line number.
		 */
		public Range(final int start, final int end, final int line) { 
			this.mStart = start; 
			this.mEnd = end; 
			this.mLine = line;
		}
		/** Line number getter.
		 * 
		 * @return The line number.
		 */
		public int getLine() { return mLine; }
		/** Range start getter.
		 * 
		 * @return index position of the start of the line.
		 */
		public int getStart() { return mStart; }
		/** Range end getter.
		 * 
		 * @return index position of the end of the line.
		 */
		public int getEnd() { return mEnd; }
		
	}
	
	/** Range class comparator.	 */
	private class RangeComparator implements Comparator<Range> {

		@Override
		public int compare(final Range a, final Range b) {
			if (b.mStart > a.mEnd && b.mEnd > a.mEnd) {
				return -1;
			}

			if (b.mStart < a.mStart && b.mEnd < a.mEnd) {
				return 1;
			}

			return 0;
		}

	}
	

	
	/** Setter for triggersDirty. */
	public final void setTriggersDirty() {
		triggersDirty = true;
	}

	/** THE INCOMING DATA DISPATCH ROUTINE! Unicorns and puppies and all kinds of good things live here.
	 * 
	 * @param data The data to process.
	 * @throws UnsupportedEncodingException Thrown when a string<==>byte[] conversion has a bad encoding provided.
	 */
	private void dispatch(final byte[] data) throws UnsupportedEncodingException {

		if (mProcessor == null) {
			// Bytes that were already queued when the connection was torn down.
			// killNetThreads nulls the processor, and anything still in the
			// handler queue arrives after that, so this is a normal race rather
			// than a fault -- there is simply nothing left to decode them with.
			// Every other user of mProcessor already checks; this one did not,
			// and threw NullPointerException into the connection thread on
			// disconnect. Seen on the phone, in the error log, as
			// "Processor.rawProcess on a null object reference".
			return;
		}
		byte[] raw = mProcessor.rawProcess(data);
		if (raw == null) { 
			return; 
		}
		mProcessor.noteInboundOsc8(raw);
		ensureMcpEngine();
		if (mMcpEngine != null && !mMcpEngine.isUse()) {
			mMcpEngine.noteHelloIfPresent(raw);
		}
		raw = mProcessor.filterMxp(raw);
		if (raw == null || raw.length == 0) {
			return;
		}
		ensureMcpEngine();
		if (mMcpEngine != null && mMcpEngine.isUse()) {
			raw = mMcpEngine.filterIncoming(raw);
			if (raw == null || raw.length == 0) {
				return;
			}
		}

		// Whole lines only from here down. Roughly one chunk in ten ends in the
		// middle of a line (measured: 11 of 105 on samsaramoo), and everything
		// below this point assumes the line it is looking at is finished — a gag
		// deletes a matched line out of mWorking before it is ever drawn, so a
		// pattern matching half a line took the head off screen and left the
		// tail to arrive alone in the next chunk.
		byte[] ready = mLineHoldover.accept(raw);
		if (mLineHoldover.hasHeld()) {
			armLineHoldoverFlush();
		} else {
			mHandler.removeMessages(MESSAGE_FLUSH_LINE_HOLDOVER);
		}
		if (ready.length == 0) {
			return;
		}
		dispatchWholeLines(ready);
	}

	/**
	 * Release the half-line that has been waiting, because nothing came to
	 * finish it. Overwhelmingly this is a prompt.
	 */
	private void flushLineHoldover() throws UnsupportedEncodingException {
		byte[] held = mLineHoldover.flush();
		if (held.length == 0) {
			return;
		}
		// Whatever comes out here is, by construction, a line the world never
		// finished — which on a MUD means the prompt. That is why the prompt bar
		// can exist at all: the holdover already knows which line it is, without
		// any guessing at its shape.
		String text = Colorizer.stripAnsiEscapes(
				new String(held, mSettings.getEncoding())).trim();
		// Counted whether or not the bar is on: the question this answers is
		// "does this world send a prompt at all", and a player asks it precisely
		// because the bar showed them nothing. Whitespace-only flushes do not
		// count, or a world that dribbles blank lines would look talkative.
		if (text.length() > 0) {
			mPromptsSeen++;
		}
		if (mPromptBar && text.length() > 0) {
			mService.doPromptLine(mDisplay, text);
			return;
		}
		dispatchWholeLines(held);
	}

	/** {@code .prompt on|off}: prompt to its own bar instead of the game window. */
	public final void setPromptBar(final boolean on) {
		mPromptBar = on;
		if (!on) {
			mService.doPromptLine(mDisplay, "");
		}
	}

	public final boolean isPromptBar() {
		return mPromptBar;
	}

	/**
	 * How many unfinished lines this connection has flushed — prompts, near
	 * enough. Zero after a while of play means the world sends none, which is the
	 * only honest answer to "the prompt bar shows nothing".
	 */
	public final int getPromptsSeen() {
		return mPromptsSeen;
	}

	/** {@code .complete on|off}: feed the UI's word completer. */
	public final void setWordComplete(final boolean on) {
		mWordComplete = on;
	}

	public final boolean isWordComplete() {
		return mWordComplete;
	}

	/**
	 * Take the n-th completion off the strip — {@code .complete 3}.
	 *
	 * <p>One-way into the UI, which is the only side that knows what is currently
	 * offered. Out of range does nothing there rather than reporting back: the
	 * caller is usually a super button pressed while looking at the strip.
	 *
	 * @param index counting from 1.
	 */
	public final void pickCompletion(final int index) {
		mService.doPickCompletion(mDisplay, index);
	}

	/**
	 * Restart the clock on the held fragment. Removing first matters: each new
	 * chunk that leaves something held should get the full wait, or a steady
	 * trickle of packets would flush a fragment mid-line anyway.
	 */
	private void armLineHoldoverFlush() {
		mHandler.removeMessages(MESSAGE_FLUSH_LINE_HOLDOVER);
		mHandler.sendEmptyMessageDelayed(MESSAGE_FLUSH_LINE_HOLDOVER,
				IncomingLineHoldover.DEFAULT_FLUSH_MS);
	}

	/**
	 * Everything that was {@code dispatch} below the telnet layer, now reached
	 * both by an arriving chunk and by the holdover timer.
	 *
	 * @param raw complete lines, or a fragment the timer gave up on.
	 * @throws UnsupportedEncodingException from the settings encoding.
	 */
	private void dispatchWholeLines(final byte[] raw)
			throws UnsupportedEncodingException {
		TextTree buffer = null;
		for (WindowToken w : mWindows) {
			if (w.getName().equals(MAIN_WINDOW)) {
				buffer = w.getBuffer();
			}
		}

		TextTree.Color tmpcolor = buffer.getBleedColor();
		mWorking.setBleedColor(tmpcolor);
		mFinished.setBleedColor(tmpcolor);

		mWorking.addBytesImpl(raw);
	
		mWorking.setModCount(0);
		
		// Strip for triggers + session log. Display parsing (TextTree holdover) can
		// reassemble CSI split across TCP packets; this path cannot — incomplete
		// ESC[… at a chunk boundary can still break a pattern until the next packet.
		String stripped = Colorizer.stripAnsiEscapes(new String(raw, mSettings.getEncoding()));
		SessionLogger.appendIncoming(mService.getApplicationContext(), mDisplay, stripped);
		// Measured here rather than anywhere else on purpose: this is the exact
		// string the combined trigger pattern is matched against, so it is the
		// only place that can answer whether a pattern could span lines.
		if (mChunkStats != null) {
			mChunkStats.record(stripped);
		}
		// Only while the completer is on, so a player not using it pays no
		// binder traffic at all.
		if (mWordComplete) {
			mService.doVocabularyText(mDisplay, stripped);
		}
		
		if (triggersDirty) {
			buildTriggerSystem();
		}

		ListIterator<TextTree.Line> it = mWorking.getLines().listIterator(mWorking.getLines().size());
		mLineMap.clear();
		LINE_MATCHER.reset(stripped);
		boolean found = false;
		int lineNumber = mWorking.getLines().size() - 1;
		while (LINE_MATCHER.find()) {
			found = true;
			mLineMap.add(new Range(LINE_MATCHER.start(), LINE_MATCHER.end(), lineNumber));
			lineNumber = lineNumber - 1;
		}
		boolean keepEvaluating = true;
		lineNumber = mWorking.getLines().size() - 1;
		Line l = null;
		if (it.hasPrevious()) {
			l = it.previous();
		} else {
			return;
		}
		if (found) {
			boolean done = false;
			while (!done) {
				done = true;
				boolean rebuildTriggers = false;
				boolean replaceGagged = false;
				int gagloc = -1;
				mMassiveMatcher.reset(stripped);
				while (keepEvaluating && mMassiveMatcher.find()) {
					int s = mMassiveMatcher.start();
					int e = mMassiveMatcher.end() - 1;
					String matched = mMassiveMatcher.group();
					Range r = new Range(s, e, 0);
					SortedSet<Range> tmp = mLineMap.tailSet(r);
	
					int tmpline = tmp.first().getLine();
					int tmpstart = s - tmp.first().getStart();
					int tmpend = (e - 1) - tmp.first().getStart();
					gagloc = tmp.first().getEnd();
					
					int index = -1;
					for (int i = 1; i <= mMassiveMatcher.groupCount(); i++) {
						if (mMassiveMatcher.group(i) != null) {
							index = i;
							i = mMassiveMatcher.groupCount();
						}
					}
					
					if (index > 0) {
						//we have found a trigger. advance the line number to
						
						TriggerData t = mSortedTriggerMap.get(index);
						Plugin p = mTriggerPluginMap.get(index);

						boolean gagged = false;
						if (lineNumber > tmpline) {
							int amount = lineNumber - tmpline;
							
							for (int i = 0; i < amount; i++) {
								if (it.hasPrevious()) {
								l = it.previous();
								}
							}
							mWorking.setModCount(0);
							lineNumber = tmpline;
							if (it.hasNext()) {
								lineNumber = tmpline;	
							}
						} else if (tmpline > lineNumber) {
							gagged = true;
						}
						if (t != null && t.isEnabled() && !gagged) {
							// Prefer the live map entry so editor/toggle mutations are
							// what the gate sees even if the matcher still holds an older ref.
							// Look in the owning plugin (main settings or a real plugin) —
							// never fall through to getTriggers() alone, or a same-named
							// main trigger would steal a plugin trigger's conditions.
							TriggerData gate = t;
							if (t.getName() != null && p != null && p.getSettings() != null) {
								TriggerData live = p.getSettings().getTriggers().get(t.getName());
								if (live != null) {
									gate = live;
								}
							}
							if (ConditionEvaluator.evaluate(gate, Connection.this)) {
							mCaptureMap.clear();
							for (int i = index; i <= (t.getMatcher().groupCount() + index); i++) {
								
								mCaptureMap.put(Integer.toString(i - index), mMassiveMatcher.group(i));
							}
							for (TriggerResponder responder : t.getResponders()) {
								if (responder instanceof GagAction) {
									replaceGagged = true;
								}
								try {
									responder.doResponse(mService.getApplicationContext(), 
																	   mWorking, 
																	   lineNumber, 
																	   it, 
																	   l, 
																	   tmpstart,
																	   tmpend,
																	   matched, 
																	   t, 
																	   mDisplay,
																	   mHost,
																	   mPort, 
																	   StellarService.getNotificationId(), 
																	   mService.isWindowConnected(), 
																	   mHandler, 
																	   mCaptureMap, 
																	   p.getLuaState(), 
																	   t.getName(), 
																	   mSettings.getEncoding());
									
									if (triggersDirty) {
										keepEvaluating = false;
										rebuildTriggers = true;
									}
								} catch (IteratorModifiedException e1) {
									it = e1.getIterator();
									mWorking.setModCount(0);
									lineNumber = it.previousIndex();
									if (it.hasPrevious()) {
										l = it.previous();
									} else {
										keepEvaluating = false;
									}
									
								} catch (Exception eResp) {
									String tname = t.getName() != null ? t.getName() : "?";
									String rname = responder != null
											? responder.getClass().getSimpleName() : "?";
									reportRuntimeError("trigger \"" + tname + "\" / " + rname,
											eResp);
								}
								if (mWorking.getLines().size() == 0) {
									keepEvaluating = false;
								}
							}
							}
						}
					}
					if (rebuildTriggers) {
						break;
					}
				}
				if (rebuildTriggers) {
					mWorking.setModCount(0);
					done = false;
					keepEvaluating = true;
					int e = mMassiveMatcher.end();

					if (e != stripped.length()) {
						if (replaceGagged) {
							stripped = stripped.substring(gagloc + 1, stripped.length());
						} else {
							stripped = stripped.substring(e + 1, stripped.length());
						}	
					}
					
					if (lineNumber <= mWorking.getLines().size() - 1) {
						while (mWorking.getLines().size() - 1 > lineNumber) {

							Line tmp = mWorking.getLines().get(mWorking.getLines().size() - 1);
							mWorking.getLines().remove(mWorking.getLines().size() - 1);
							mFinished.appendLine(tmp);
						}
						
					}
					
					buildTriggerSystem();
					
					mLineMap.clear();
					LINE_MATCHER.reset(stripped);
					found = false;

					lineNumber = mWorking.getLines().size() - 1;
					while (LINE_MATCHER.find()) {
						found = true;
						mLineMap.add(new Range(LINE_MATCHER.start(), LINE_MATCHER.end(), lineNumber));
						lineNumber = lineNumber - 1;
					}
					
					lineNumber = mWorking.getLines().size() - 1;
					if (lineNumber == -1) {
						keepEvaluating = false;
						done = true;
					} else {
						it = mWorking.getLines().listIterator(lineNumber + 1);
						l = it.previous();
					}
					
				}
				
				
			}
		}

		ListIterator<TextTree.Line> finisher = mWorking.getLines().listIterator(mWorking.getLines().size());
		while (finisher.hasPrevious()) {
			mFinished.appendLine(finisher.previous());
		}
		
		mWorking.drainLines();

		// A colour trigger whose match reached the end of the text that had
		// arrived leaves its colour running on purpose, so the rest of the line
		// keeps it when the next packet brings it. Close it at that line's end —
		// what goes out below is a byte stream, and a colour code in a stream
		// runs until the next one.
		mTriggerColor.closeAtLineEnds(mFinished);

		mFinished.updateMetrics();
		
		byte[] proc = mFinished.dumpToBytes(false);
		
		buffer.addBytesImplSimple(proc);
		// notifyMainWindow, not sendBytesToWindow: buffer is this window's own
		// TextTree and the line above already holds the text.
		notifyMainWindow(proc);
		
	}
	
	/** Called from a few places I think. Triggers the network disconnected dialog in the foreground window.
	 * Unless the auto reconnect is set.
	 * 
	 * @param str The message fro the dialog.
	 */
	protected final void dispatchDialog(final String str) {
		if (mHandler == null || str == null) { return; }
		// killNetThreads(true) has to run before the attempt is scheduled, so the
		// budget check is separate from consuming it here.
		if (mReconnect.canAttempt()) {
			killNetThreads(true);
			int remaining = mReconnect.consumeAttempt(TWENTY_THOUSAND_MILLIS);
			if (remaining >= 0) {
				String message = "\n" + Colorizer.getRedColor() + "Network Error: " + str + "\n"
						+ "Attempting reconnect"
						+ mReconnect.describeNextAttempt(" in 20 seconds.")
						+ " " + remaining + " tries remaining."
						+ Colorizer.getWhiteColor() + "\n";
				mHandler.sendMessage(mHandler.obtainMessage(Connection.MESSAGE_PROCESSORWARNING, message));
				return;
			}
		}
		mReconnect.clearNetworkWait();
		// Connect failed without scheduling reconnect — MESSAGE_DISCONNECTED used to
		// perform this cleanup; DataPumper skips it when MESSAGE_DODIALOG was sent.
		killNetThreads(true);
		mIsConnected = false;
		mService.dispatchDialog(str);
		doDisconnect(true);
	}

	/** Sends a string to the main output window.
	 * 
	 * @param message The string to send.
	 */
	/**
	 * Turn the chunk probe on or off. Turning it off keeps what was measured,
	 * so a reading survives being paused mid-session.
	 *
	 * @param on True to measure.
	 */
	public final void setChunkProbe(final boolean on) {
		if (on) {
			if (mChunkStatsHeld == null) {
				mChunkStatsHeld = new ChunkStats();
			}
			mChunkStats = mChunkStatsHeld;
		} else {
			mChunkStats = null;
		}
	}

	/** Clear the reading, whether the probe is running or not. */
	public final void resetChunkProbe() {
		if (mChunkStatsHeld != null) {
			mChunkStatsHeld.reset();
		}
	}

	/**
	 * The probe's reading as text for the game window.
	 *
	 * @return The report, or an invitation to turn it on.
	 */
	public final String chunkProbeReport() {
		if (mChunkStatsHeld == null) {
			return "\nChunk probe has not been run. Start it with .probe lines on\n";
		}
		String body = mChunkStatsHeld.report();
		if (mChunkStats == null) {
			body = body + "(probe is currently off — .probe lines on to resume)\n";
		}
		// Also into the session log. A measurement that can only be read off the
		// screen cannot leave the phone, and the whole point of this one is to
		// be carried back to whoever is deciding what to build. The session log
		// is a file the player already knows how to export.
		SessionLogger.appendIncoming(mService.getApplicationContext(), mDisplay, body);
		return body;
	}

	/**
	 * Run a starter-tutorial Lua callback by plugin name, not by the index
	 * {@code RegisterSpecialCommand} stored at load. Worlds that never grew
	 * the plugin, or that disabled it, get an English explanation instead of
	 * "not a recognized alias or command".
	 */
	public final void runStarterTutorialCommand(final String callback, final String args) {
		Plugin tutorial = mPluginMap.get("starter_tutorial");
		if (tutorial == null) {
			sendDataToWindow("\n" + Colorizer.getRedColor()
					+ ".tutorial is missing from this world's plugins. "
					+ "Reconnect once — the client grafts it into older profiles — "
					+ "or add Starter Tutorial from a new world's plugin list."
					+ Colorizer.getWhiteColor() + "\n");
			return;
		}
		if (!tutorial.isEnabled()) {
			sendDataToWindow("\n" + Colorizer.getRedColor()
					+ "starter_tutorial is disabled. Re-enable it in Options → Plugins, "
					+ "then .tutorial and .tips work again."
					+ Colorizer.getWhiteColor() + "\n");
			return;
		}
		if (!tutorial.checkPluginSupports(callback)) {
			sendDataToWindow("\n" + Colorizer.getRedColor()
					+ "Starter Tutorial Lua did not load, so ."
					+ ("tipsCommand".equals(callback) ? "tips" : "tutorial")
					+ " has nothing to run. Look for a red Bootstrap error above."
					+ Colorizer.getWhiteColor() + "\n");
			return;
		}
		tutorial.execute(callback, args == null ? "" : args);
	}

	public final void sendDataToWindow(final String message) {
		
		try {
			sendBytesToWindow(message.getBytes(mSettings.getEncoding()));
		} catch (UnsupportedEncodingException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor("Connection.sendDataToWindow", e);
		}
	}
	
	/** Sends bytes to the main output window, and keeps them.
	 *
	 * <p><b>The buffer write is the point.</b> This used to hand the bytes
	 * straight to the live callback and nothing else, so everything written
	 * through here existed only in the UI process's copy of the text: the yellow
	 * GMCP notices, reconnect countdowns, MCP notices, Lua errors, and every
	 * {@code .command} reply. The main window's content is not kept by the UI —
	 * {@code MainWindow.initWindow} does {@code tmp.setBuffer(w.getBuffer())},
	 * i.e. it adopts the service-side {@link WindowToken} buffer wholesale. So
	 * anything that skipped that buffer vanished the moment the window was
	 * rebuilt, which is what switching between two live worlds does
	 * ({@code StellarService.switchTo} → {@code loadWindowSettings}). Reported as
	 * "switching worlds wipes the yellow GMCP notices"; the cause was one missing
	 * line and the notices were only the most visible casualty.
	 *
	 * <p>The raw bytes go to the buffer, not a re-encoded copy: {@code dispatch}
	 * writes the same bytes the same way ({@code addBytesImplSimple}), and
	 * routing them through a temporary TextTree first — as {@code lineToWindow}
	 * does — would re-normalise what the UI receives and could move colour or
	 * line breaks.
	 *
	 * <p>Callers that have already written to the buffer themselves must use
	 * {@link #notifyMainWindow} instead, or the text is added twice.
	 *
	 * @param data The bytes to send.
	 */
	public final void sendBytesToWindow(final byte[] data) {
		if (data == null || data.length == 0) {
			return;
		}
		// Before the callback, and regardless of it: a window that is not
		// currently attached is exactly the case this buffer exists for.
		TextTree buffer = getMainWindowBuffer();
		if (buffer != null) {
			buffer.addBytesImplSimple(data);
		}
		notifyMainWindow(data);
	}

	/** The main window's service-side scrollback, or null before windows exist. */
	private TextTree getMainWindowBuffer() {
		for (WindowToken w : mWindows) {
			if (MAIN_WINDOW.equals(w.getName())) {
				return w.getBuffer();
			}
		}
		return null;
	}

	/** Hand bytes to the live main window without keeping them.
	 *
	 * <p>Only for callers that have already put the same bytes in the main
	 * window's buffer — {@link #dispatch} and {@link #dispatchNoProcess}.
	 * Everything else wants {@link #sendBytesToWindow}.
	 *
	 * @param data The bytes to send.
	 */
	private void notifyMainWindow(final byte[] data) {

		if (holdWhileHidden(MAIN_WINDOW, data)) {
			return;
		}
		try {

			IWindowCallback c = mWindowCallbackMap.get(MAIN_WINDOW);
			if (c != null) {
				c.rawDataIncoming(data);
			}
		} catch (android.os.DeadObjectException e) {
			// UI process died; drop the stale binder so we do not keep spamming and
			// leave the socket half-alive until auto-reconnect papers over it.
			Log.w("BlowTorch", "Main window binder dead; clearing callback", e);
			synchronized (mWindowSynch) {
				mWindowCallbackMap.remove(MAIN_WINDOW);
			}
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("Connection.notifyMainWindow", e);
		}
	}

	/** Meat of the startup sequence. Starts the net threads after the settings have been loaded. */
	private final Object mStartupLock = new Object();
	private boolean mStartupInProgress = false;
	private void doStartup() {
		synchronized (mStartupLock) {
			if (isOfflineMode()) {
				doOfflineStartupLocked();
				return;
			}
			// Skip only when the TCP session is live. isAlive() alone is wrong: a failed
			// connect leaves a Looper thread that blocked every later initXfer.
			if (mPump != null && mPump.isConnected()) {
				Log.i("BlowTorch", "doStartup skipped — already connected");
				applyTerminalNaws();
				return;
			}
			if (mStartupInProgress) {
				Log.i("BlowTorch", "doStartup skipped — startup already in progress");
				return;
			}
			Log.i("BlowTorch", "doStartup begin", new RuntimeException("doStartup caller"));

			killNetThreads(true);
			mStartupInProgress = true;

			mService.updateForegroundNotification(mDisplay,
					mService.getString(com.resurrection.blowtorch2.lib.R.string.notification_status_connecting, mHost, mPort));
			
			mPump = new DataPumper(mHost, mPort, mUseTls, mHandler);
			
			mProcessor = new Processor(mHandler, mSettings.getEncoding(), mService.getApplicationContext());
			mProcessor.setDisplayName(mDisplay);
			loadLoginCredentialsIntoProcessor();
			attachMxpListener();

			initSettings();
			// Before mPump.start(), so the profile — and the MCCP session override —
			// are in the Processor before any IAC WILL can be answered. Ordering was
			// already safe via handler FIFO (DataPumper posts MESSAGE_CONNECTED before
			// its read handler exists, and MESSAGE_CONNECTED reaches these flags through
			// ConnectionGmcp.applyGmcpLogSetting), but that is a long chain to rely on.
			// Note this is an addition: applyGmcpLogSetting still calls it too, and a
			// second application is harmless on a Processor built ten lines above.
			applyMudProtocolFlags();
			applyTerminalNaws();
			mExtraText.syncGmcpRoutes();
			mPump.start();
			mGmcp.loadGMCPTriggers();
			loadMcpTriggers();
			// mIsConnected / session "connected" marker wait for MESSAGE_CONNECTED
			// (DataPumper finished the TCP handshake).
			mService.showConnectionNotification(mDisplay, mHost, mPort);
			mService.noteConnectionStarted(mDisplay);
			// Handshaking flag clears on MESSAGE_CONNECTED / disconnect / pump death.
		}
	}

	/**
	 * Let the Starter Tutorial answer a typed line as if it were a MUD.
	 *
	 * @param command The line, after alias replacement.
	 * @return true when the tutorial handled it, so nothing else should reply.
	 */
	private boolean offerToOfflineWorld(final String command) {
		if (command == null || command.length() == 0) {
			return false;
		}
		try {
			Plugin tutorial = mPluginMap.get("starter_tutorial");
			if (tutorial == null) {
				return false;
			}
			String reply = tutorial.callFunctionResult("OnOfflineCommand", command);
			if (reply == null || reply.length() == 0) {
				return false;
			}
			// Posted as MESSAGE_PROCESS rather than written to the window
			// directly. sendBytesToWindow hands bytes straight to the main
			// window and skips dispatch(), which is where triggers, colouring,
			// MCP and the mapper live — so text sent that way would look right
			// and do nothing. Going through the queue also keeps us out of our
			// own re-entry: this runs while the handler is processing the
			// player's outgoing line.
			mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_PROCESS,
					reply.getBytes(mSettings.getEncoding())));
			feedMapperFromOfflineWorld(tutorial);
			return true;
		} catch (Exception e) {
			// A broken practice world must not stop the player typing.
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"Connection.offerToOfflineWorld", e);
			return false;
		}
	}

	/**
	 * Tell the mapper where the practice world just put the player.
	 *
	 * <p>The mapper has no text-based room detection — {@code onGmcpRoom} is its
	 * only way in — so without this the tutorial rooms would be walked but never
	 * drawn. Calling it directly is the same entry point a real Room.Info takes,
	 * so the tutorial exercises the mapper the player will actually use.
	 *
	 * @param tutorial The tutorial plugin, already known to be present.
	 */
	private void feedMapperFromOfflineWorld(final Plugin tutorial) {
		if (mMapper == null) {
			return;
		}
		String info = tutorial.callFunctionResult("OnOfflineRoomInfo", "");
		if (info == null) {
			return;
		}
		// num <TAB> title <TAB> comma separated exits. Tabs because room titles
		// contain spaces and exits do not need anything richer.
		String[] parts = info.split("\t", -1);
		if (parts.length < 3) {
			return;
		}
		java.util.List<String> exits = new java.util.ArrayList<String>();
		if (parts[2].length() > 0) {
			for (String exit : parts[2].split(",")) {
				String trimmed = exit.trim();
				if (trimmed.length() > 0) {
					exits.add(trimmed);
				}
			}
		}
		mMapper.onGmcpRoom(parts[1], parts[0], null, null, null, exits,
				new java.util.HashMap<String, String>());
	}

	/** True for the built-in Starter Tutorial (host {@code offline}). */
	private boolean isOfflineMode() {
		return BuiltinTutorial.isTutorialHost(mHost);
	}

	/**
	 * Open a local-only session: settings/plugins/buttons work, no TCP, no reconnect loop.
	 * Must hold {@link #mStartupLock}.
	 */
	private void doOfflineStartupLocked() {
		if (mIsConnected) {
			Log.i("BlowTorch", "doOfflineStartup skipped — already open");
			clearStartupInProgress();
			return;
		}
		if (mStartupInProgress) {
			Log.i("BlowTorch", "doOfflineStartup skipped — startup already in progress");
			return;
		}
		Log.i("BlowTorch", "doOfflineStartup begin");
		mStartupInProgress = true;
		killNetThreads(true);
		mHandler.removeMessages(MESSAGE_RECONNECT);
		mReconnect.disableForSession();

		mService.updateForegroundNotification(mDisplay, "Offline · Starter Tutorial");

		mProcessor = new Processor(mHandler, mSettings.getEncoding(), mService.getApplicationContext());
		mProcessor.setDisplayName(mDisplay);
		attachMxpListener();
		initSettings();
		mExtraText.syncGmcpRoutes();
		applyOfflinePresentationDefaults();
		// Window/button layer may bind slightly later — re-apply once more.
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				applyOfflinePresentationDefaults();
			}
		}, 350);
		applyTerminalNaws();
		mGmcp.loadGMCPTriggers();
		loadMcpTriggers();

		mIsConnected = true;
		mConnectedAtElapsed = SystemClock.elapsedRealtime();
		clearStartupInProgress();

		mService.showConnectionNotification(mDisplay, mHost, mPort);
		mService.noteConnectionStarted(mDisplay);

		// Brief offline banner, then open lesson 1 (welcome). Do not stop at a
		// nav-only blurb: that left currentIndex at welcome while the user only
		// saw "Walk lessons with .tutorial next", so NEXT skipped to lesson 2.
		// Say up front that this is a place, not just a wall of text. Without
		// this nobody knew there was anything to type at, and the first lesson
		// arrived talking about "commands" with no idea where they went.
		sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ "Starter Tutorial — offline session (no network).\n"
				+ Colorizer.getWhiteColor()
				+ "\nThis one is interactive: you are standing in a practice yard, and\n"
				+ "there is a tutor here who will check the things you build.\n\n"
				+ Colorizer.getGreenColor() + ">> look" + Colorizer.getWhiteColor()
				+ "                    see where you are\n"
				+ Colorizer.getGreenColor() + ">> ask bex about lessons"
				+ Colorizer.getWhiteColor() + "  start the lessons\n"
				+ Colorizer.getGreenColor() + ">> commands"
				+ Colorizer.getWhiteColor() + "                everything this yard understands\n"
				+ "\nAnything shown in green is something you can type.\n"
				+ "The reading tour below still works: .tutorial next\n\n");
		try {
			Plugin tutorial = mPluginMap.get("starter_tutorial");
			if (tutorial != null) {
				tutorial.callFunction("starterTutorialBegin", "");
			}
		} catch (Exception e) {
			Log.w("BlowTorch", "doOfflineStartup starterTutorialBegin", e);
		}
	}

	/** Readable font + starter button layout for the offline tutorial profile. */
	private void applyOfflinePresentationDefaults() {
		try {
			if (mWindows != null && !mWindows.isEmpty() && mWindows.get(0) != null
					&& mWindows.get(0).getSettings() != null) {
				mWindows.get(0).getSettings().setOption("font_size", "20");
				mSettings.setLineSize(20);
				IWindowCallback cb = mWindowCallbackMap.get(mWindows.get(0).getName());
				if (cb != null) {
					cb.updateSetting("font_size", "20");
				}
			}
		} catch (Exception e) {
			Log.w("BlowTorch", "applyOfflinePresentationDefaults font", e);
		}
		try {
			Plugin buttons = mPluginMap.get("button_window");
			if (buttons != null) {
				buttons.callFunction("installStarterButtonLayout", "");
			}
		} catch (Exception e) {
			Log.w("BlowTorch", "applyOfflinePresentationDefaults buttons", e);
		}
	}

	/** Allow a new doStartup after connect success or failure. */
	private void clearStartupInProgress() {
		synchronized (mStartupLock) {
			mStartupInProgress = false;
		}
	}
	
	/** Literal triggers starting with {@link McpEngine#TRIGGER_CHAR} ({@code @}) fire on MCP messages. */
	private void loadMcpTriggers() {
		ensureMcpEngine();
		mMcpEngine.clearWatchers();
		for (int i = 0; i < mPlugins.size(); i++) {
			Plugin p = mPlugins.get(i);
			if (p == null || !p.isEnabled()) {
				continue;
			}
			HashMap<String, TriggerData> triggers = p.getSettings().getTriggers();
			for (TriggerData t : triggers.values()) {
				if (!t.isInterpretAsRegex() && t.getPattern().startsWith(McpEngine.TRIGGER_CHAR)) {
					for (TriggerResponder r : t.getResponders()) {
						if (r instanceof ScriptResponder) {
							ScriptResponder s = (ScriptResponder) r;
							String callback = s.getFunction();
							String msg = t.getPattern().substring(1);
							mMcpEngine.addWatcher(msg, p.getName(), callback);
						}
					}
				}
			}
		}
	}

	/** Resolve launcher ServerAccount login/password for Char.Login auto-auth. */
	private void loadLoginCredentialsIntoProcessor() {
		if (mProcessor == null || mService == null) {
			return;
		}
		try {
			com.resurrection.blowtorch2.lib.launcher.LauncherSAXParser parser =
					new com.resurrection.blowtorch2.lib.launcher.LauncherSAXParser(
							"blowtorch_launcher_list.xml", mService.getApplicationContext());
			com.resurrection.blowtorch2.lib.launcher.LauncherSettings settings = parser.load();
			if (settings == null || settings.getList() == null) {
				return;
			}
			com.resurrection.blowtorch2.lib.launcher.MudConnection mud =
					settings.getList().get(mDisplay);
			if (mud == null) {
				return;
			}
			com.resurrection.blowtorch2.lib.launcher.ServerAccount acc = mud.primaryAccount();
			if (acc != null) {
				mProcessor.setLoginCredentials(acc.getLogin(), acc.getPassword());
			}
		} catch (Exception e) {
			Log.w("BlowTorch", "Char.Login credential load failed", e);
		}
	}


	
	/** Alias parsing and special command handling routine.
	 * 
	 * @param data The data on its way to the server in need of processing.
	 * @return A Data object containing the string for the server and the string to the window.
	 * @throws UnsupportedEncodingException Problem with the String<==>byte[] conversion indicating a bad encoding option.
	 */
	private Data processOutputData(final String data) throws UnsupportedEncodingException {
		mDataToServer.setLength(0);
		mDataToWindow.setLength(0);
		String out = data;
		// Two characters were taken off for a one-character newline. Everything
		// in the app sends "\r\n", so it was right by accident and wrong for
		// anything that sends a bare "\n" — which ate the last character of the
		// command instead: ".sensor fire facedown" arrived as "facedow", and a
		// calibration of 14.5 was stored as 14.
		if (out.endsWith("\r\n")) {
			out = out.substring(0, out.length() - 2);
		} else if (out.endsWith("\n") || out.endsWith("\r")) {
			out = out.substring(0, out.length() - 1);
		}
		
		if (out.equals("")) {
			Data enter = new Data();
			enter.mCmdString = "";
			enter.mVisString = null;
			return enter;
		}
		
		if (out.equals(";;")) {
			Data enter = new Data();
			enter.mCmdString = ";" + mCRLF;
			// Same inherit path as a plain typed line — respect global local echo.
			if (AliasLocalEcho.shouldDisplay(mSettings.isLocalEcho(), mLocalEcho,
					AliasLocalEcho.INHERIT)) {
				enter.mVisString = ";";
			} else {
				enter.mVisString = "";
			}
			return enter;
		}
		List<String> list = null;
		
		if (mSettings.isSemiIsNewLine()) {
			//commands = semicolon.split(out);
			list = splitSemicolonSafe(out);
			
		} else {
			list = new ArrayList<String>();
			list.add(out);
		}
		// #5 north -> five segments, before alias replacement so the multiplier
		// counts what the player typed rather than what it expanded into.
		CommandRepeat.Result repeated = CommandRepeat.expand(list);
		list = repeated.segments();
		if (repeated.warning() != null) {
			sendDataToWindow("\n" + Colorizer.getRedColor() + repeated.warning()
					+ Colorizer.getWhiteColor());
		}
		StringBuffer holdover = new StringBuffer();
		// First-match local-echo policy for reinserted expansion products.
		// Locals only — not a Connection field — so overlapping sends cannot
		// leak policy across batches.
		ArrayDeque<AliasLocalEcho> inheritedEcho = new ArrayDeque<AliasLocalEcho>();
		// Policy for a ~ holdover chain. ~ segments must still consume their
		// queued policy or the deque desyncs against later commands.
		AliasLocalEcho holdoverPolicy = null;
		
		ListIterator<String> iterator = list.listIterator();
		while (iterator.hasNext()) {
			String cmd = iterator.next();
			// Case-sensitive worlds: Look → look. Skip while telnet ECHO holds the
			// password mask so a capital in a password is not rewritten.
			cmd = CommandCase.softenForSend(cmd,
					readBoolOption("lowercase_command_start", false), mLocalEcho);
			
			if (cmd.endsWith("~")) {
				if (!inheritedEcho.isEmpty()) {
					AliasLocalEcho p = inheritedEcho.removeFirst();
					if (holdoverPolicy == null) {
						holdoverPolicy = p;
					}
				}
				holdover.append(cmd.substring(0, cmd.length() - 1) + ";");
			} else {
				if (holdover.length() > 0) {
					cmd = holdover.toString() + cmd;
					holdover.setLength(0);
				}
				boolean hadInherited = !inheritedEcho.isEmpty();
				boolean fromHoldover = holdoverPolicy != null;
				AliasLocalEcho segmentPolicy;
				if (fromHoldover) {
					// Keep the deque aligned with the finishing segment.
					if (hadInherited) {
						inheritedEcho.removeFirst();
					}
					segmentPolicy = holdoverPolicy;
					holdoverPolicy = null;
				} else {
					segmentPolicy = hadInherited
							? inheritedEcho.removeFirst() : AliasLocalEcho.INHERIT;
				}
				boolean stickyPolicy = fromHoldover || hadInherited;
				//2.5 run command through the global lua state
				Data d = null;
				
				if (cmd.startsWith(mScriptBlock)) {
					mSettings.runLuaString(cmd.substring(mScriptBlock.length(), cmd.length()));
				} else {
					d = processCommand(cmd);
				}
				//3 - do special command processing.
				
				//4 - handle command processing output
				
				if (d != null) {
					boolean m = false;
					if (d.mCmdString != null && d.mVisString != null) {
						if (d.mCmdString.equals(d.mVisString)) {
							m = true; //aliases & regular commands will always have the same cmdString and visString
						}
					}
					
					//5 - alias replacement				
					if (d.mCmdString != null && !d.mCmdString.equals("")) {
						boolean didReplace = false;
						byte[] tmp = null;
						AliasLocalEcho matchedPolicy = AliasLocalEcho.INHERIT;
						for (int i = 0; i < mPlugins.size() + 1; i++) {
							Plugin p = null;
							if (i == 0) {
								p = mSettings;
							} else {
								p = mPlugins.get(i - 1);
							}
							if (p != mSettings && (p == null || !p.isEnabled())) {
								continue;
							}
							if (p.getSettings().getAliases().size() > 0) {
								Boolean reprocess = true;
								tmp = p.doAliasReplacement(d.mCmdString.getBytes(mSettings.getEncoding()), reprocess);
								String tmpstr = new String(tmp, mSettings.getEncoding());
								if (!d.mCmdString.equals(tmpstr)) {
									//alias replaced, needs to be processed
									matchedPolicy = p.getLastReplacementLocalEcho();
									
									List<String> aliasCommands = null;
									if (mSettings.isSemiIsNewLine()) {
										aliasCommands = splitSemicolonSafe(tmpstr);
									} else {
										aliasCommands = new ArrayList<String>(1);  
										aliasCommands.add(tmpstr);
									}
									// Outer typed match owns echo for the whole
									// expansion chain; nested reprocess keeps it.
									AliasLocalEcho forChildren = stickyPolicy
											? segmentPolicy : matchedPolicy;
									for (String acmd : aliasCommands) {
										iterator.add(acmd);
										inheritedEcho.addLast(forChildren);
									}
									if (reprocess) {
										for (int ax = 0; ax < aliasCommands.size(); ax++) {
											iterator.previous();
										}
									}
									didReplace = true;
									i = mPlugins.size();
								}
							}
						}
							
						if (!didReplace) {
							if (tmp != null) {
								if (m) {
									String srv = new String(tmp, mSettings.getEncoding()) + mCRLF;
									mDataToServer.append(new String(srv));
									appendVisIfAllowed(
											new String(tmp, mSettings.getEncoding()) + ";",
											segmentPolicy);
								} else {
									String srv = new String(tmp, mSettings.getEncoding()) + mCRLF;
									mDataToServer.append(new String(srv));
								}
							} else {
								mDataToServer.append(d.mCmdString + mCRLF);
								appendVisIfAllowed(d.mCmdString, segmentPolicy);
							}
						}
							
					}
					
						//dataToServer.append(d.cmdString + crlf);
					if (d.mVisString != null && !d.mVisString.equals("")) {
						if (!m) {
							AliasLocalEcho visPolicy = d.mVisEchoPolicy != null
									? d.mVisEchoPolicy : segmentPolicy;
							appendVisIfAllowed(d.mVisString + ";", visPolicy);
						}
					}
				}
			

			}
		}
		//7 - return Data packet with commands to send to server, and data to send to window.
		Data d = new Data();
		d.mCmdString = mDataToServer.toString();
		d.mVisString = mDataToWindow.toString();
		
		if (d.mVisString.length() == 0) {
			// Global off + INHERIT (or FORCE_OFF) left nothing to show. Do not
			// invent a bare CRLF — that would defeat FORCE_ON filtering once the
			// final gate only enforces telnet ECHO.
			return d;
		}
		if (d.mVisString.endsWith(";")) {
			d.mVisString = d.mVisString.substring(0, d.mVisString.length() - 1);
		}
		if (!d.mVisString.endsWith(mCRLF)) {
			d.mVisString = d.mVisString + mCRLF;
		}
		return d;
	}

	/**
	 * Append local-echo text when the segment's policy allows it. Telnet ECHO
	 * masking and the global Local Echo? option are both applied here so a
	 * FORCE_ON alias can still appear when the global option is off.
	 */
	private void appendVisIfAllowed(final String text, final AliasLocalEcho policy) {
		if (text == null || text.length() == 0) {
			return;
		}
		if (AliasLocalEcho.shouldDisplay(mSettings.isLocalEcho(), mLocalEcho, policy)) {
			mDataToWindow.append(text);
		}
	}
	
	/** Semicolon splitting routine that looks for ;; smartly.
	 * 
	 * @param string The string to process.
	 * @return The resulting list of strings.
	 */
	private List<String> splitSemicolonSafe(final String string) {
		List<String> list = new ArrayList<String>();
		mSemiMatcher.reset(string);
		boolean matched = false;
		boolean append = false;
		boolean firstSemi = true;
		//int lastLength = -1;
		while (mSemiMatcher.find()) {
			matched = true;
			mCommandBuilder.setLength(0);
			
			mSemiMatcher.appendReplacement(mCommandBuilder, "");
			if (mCommandBuilder.length() == 0) {
				append = true;
				if (list.size() == 0) {
					if (!firstSemi) {
						list.add(";");
					} else {
						firstSemi = false; //don't add the first one, but add subsequent ones.
					}
				} else {
					list.add(list.remove(list.size() - 1) + ";");
				}
			} else {
				if (append) {
					if (list.size() == 0) {
						list.add(";");
					} else {
						list.add(list.remove(list.size() - 1) + mCommandBuilder.toString());
					}
					append = false;
				} else {
					list.add(mCommandBuilder.toString());
				}
				
			}
		} 
		
		if (!matched) {
			list.add(string);
		} else {
			mCommandBuilder.setLength(0);
			mSemiMatcher.appendTail(mCommandBuilder);
			if (append) {
				if(list.size() != 0) {
					list.add(list.remove(list.size() - 1) + mCommandBuilder.toString());
				}
			} else {
				list.add(mCommandBuilder.toString());
			}
		}
		
		mCommandBuilder.setLength(0);
		return list;
	}
	
	/** Utility class for alias replacement and special command parsing routine. */
	public class Data {
		/** The string to send to the server. */
		private String mCmdString;
		/** The string to echo back to the input window. */
		private String mVisString;
		/**
		 * Optional echo policy for {@link #mVisString} when it is not a normal
		 * command/alias expansion (e.g. {@code .name} alias-update echo). Null
		 * → use the segment's inherited policy in {@link #processOutputData}.
		 */
		private AliasLocalEcho mVisEchoPolicy;
		/** Generic constructor. */
		public Data() {
			mCmdString = "";
			mVisString = "";
			mVisEchoPolicy = null;
		}
		/** Cmd string getter. 
		 * 
		 * @return The string.
		 */
		public final String getCmdString() {
			return mCmdString;
		}
		/** Vis string getter.
		 * 
		 * @return The string.
		 */
		public final String getVisString() {
			return mVisString;
		}
		/** Vis string setter. 
		 * 
		 * @param vis Desired string.
		 */
		public final void setVisString(final String vis) {
			this.mVisString = vis;
		}
		/** Cmd string setter. 
		 * 
		 * @param cmd Desired string.
		 */
		public final void setCmdString(final String cmd) {
			this.mCmdString = cmd;
		}
	}
	
	/** Data generator for outside package use of the Data class.
	 * 
	 * @return A new data
	 */
	/*public static Data makeData() {
		return new Data();
	}*/
	
	/** Generic command processor. This looks for "." commands.
	 * 
	 * @param cmd The input string to parse.
	 * @return The Data object containing the string to return to the server and the string to return to the window.
	 */
	public final Data processCommand(final String cmd) {
		Data data = new Data();
		// Button-friendly form: /search 'phrase' (same as .search)
		String slashSearch = cmd == null ? "" : cmd.trim();
		if (slashSearch.regionMatches(true, 0, "/search", 0, 7)
				&& (slashSearch.length() == 7 || Character.isWhitespace(slashSearch.charAt(7)))) {
			String arg = SearchCommand.argumentFromSlashCommand(slashSearch);
			mSpecialCommands.get("search").execute(arg, this);
			return null;
		}
		if (cmd.equals(".." + "\n") || cmd.equals("..")) {
			synchronized (mSettings) {
				String outputmsg = "\n" + Colorizer.getRedColor() + "Dot command processing ";
				if (mSettings.isProcessPeriod()) {
					//the_settings.setProcessPeriod(false);
					overrideProcessPeriods(false);
					outputmsg = outputmsg.concat("disabled.");
				} else {
					//the_settings.setProcessPeriod(true);
					overrideProcessPeriods(true);
					outputmsg = outputmsg.concat("enabled.");
				}
				outputmsg = outputmsg.concat(Colorizer.getWhiteColor() + "\n");
				try {
					sendBytesToWindow(outputmsg.getBytes(mSettings.getEncoding()));
				} catch (UnsupportedEncodingException e) {
					throw new RuntimeException(e);
				}
			}
			
			return null;
		}
		
		
		if (cmd.startsWith(".") && mSettings.isProcessPeriod()) {
			
			if (cmd.startsWith("..")) {
				data.mCmdString = cmd.replace("..", ".");
				data.mVisString = cmd.replace("..", ".");
				return data;
			}
			
			
			mCommandMatcher.reset(cmd);
			if (mCommandMatcher.find()) {
				synchronized (mSettings) {
					
					//string should be of the form .aliasname |settarget can have whitespace|

						String alias = mCommandMatcher.group(1);
						String argument = mCommandMatcher.group(2);
						
						
						if (mSettings.getSettings().getAliases().containsKey(alias)) {
							//real argument
							if (!argument.equals("")) {
								AliasData mod = mSettings.getSettings().getAliases().remove(alias);
								mod.setPost(argument);
								mSettings.getSettings().getAliases().put(alias, mod);
								// A trigger's pattern may be this alias's name, so
								// its text has just changed what that trigger
								// watches for. The dialog path rebuilds through
								// ConnectionAliases; this one is the input bar and
								// went nowhere near it.
								buildTriggerSystem();
								data.mCmdString = "";
								// `.name newtext` updates With; echo is not an alias
								// expansion. Honor per-alias Local echo (Always show /
								// Always hide) and Echo Alias Updates? for Inherit.
								AliasLocalEcho policy = mod.getLocalEcho();
								data.mVisEchoPolicy = policy;
								if (AliasLocalEcho.shouldEchoAliasUpdate(
										mSettings.isEchoAliasUpdates(), policy)) {
									data.mVisString = "[" + alias + "=>" + argument + "]";
								} else {
									data.mVisString = "";
								}
								return data;
							} else {
								//display error message
								String noargMessage = "\n" + Colorizer.getRedColor() + " Alias \"" + alias + "\" can not be set to nothing. Acceptable format is \"."
													+ alias + " replacetext\"" + Colorizer.getWhiteColor() + "\n";
								try {
									sendBytesToWindow(noargMessage.getBytes(mSettings.getEncoding()));
								} catch (UnsupportedEncodingException e) {
									throw new RuntimeException(e);
								}
								return null;
							}
						} else if (mSpecialCommands.containsKey(alias)) {
							//Log.e("SERVICE","SERVICE FOUND SPECIAL COMMAND: " + alias);
							SpecialCommand command = mSpecialCommands.get(alias);
							data = (Data) command.execute(argument, this);
							offerTutorialTip(alias);
							return data;
						} else {
							//format error message.
							
							String error = Colorizer.getRedColor() + "[*][*][*][*][*][*][*][*][*][*][*][*][*][*][*][*][*][*][*][*][*]\n";
							error += "  \"" + alias + "\" is not a recognized alias or command.\n";
							error += "   No data has been sent to the server. If you intended\n";
							error += "   this to be done, please type \".." + alias + "\"\n";
							error += "   To toggle command processing, input \"..\" with no arguments\n";
							error += "[*][*][*][*][*][*][*][*][*][*][*][*][*][*][*][*][*][*][*][*][*][*]" + Colorizer.getWhiteColor() + "\n";  
							
							try {
								sendBytesToWindow(error.getBytes(mSettings.getEncoding()));
							} catch (UnsupportedEncodingException e) {
								throw new RuntimeException(e);
							}
							return null;
						}
					}
			}
			return data;
		} else {
			data.mCmdString = cmd;
			data.mVisString = cmd;
			return data;
		}
		
	}

	/**
	 * After a {@code .command} other than {@code .tutorial}, ask the starter
	 * tutorial plugin for a short reminder. No-ops when the plugin is off or
	 * the Lua function is missing. Must not break the command that just ran.
	 */
	private void offerTutorialTip(final String commandName) {
		if (commandName == null || commandName.length() == 0
				|| "tutorial".equals(commandName)
				|| "tips".equals(commandName)) {
			return;
		}
		try {
			Plugin tutorial = mPluginMap.get("starter_tutorial");
			if (tutorial == null || !tutorial.isEnabled()
					|| !tutorial.checkPluginSupports("OnCommandTip")) {
				return;
			}
			tutorial.callFunction("OnCommandTip", commandName);
		} catch (Exception ignored) {
		}
	}
	
	/** Overrides the process special commands setting and sets a new value.
	 * 
	 * @param value The new value for the process periods command.
	 */
	private void overrideProcessPeriods(final boolean value) {
		synchronized (mSettings) { //not sure why this is here.
			mSettings.setProcessPeriod(value);
		}
	}
	
	/** Switches to another open connection.
	 * 
	 * @param connection Name of the connection to switch to.
	 */
	public final void switchTo(final String connection) {
		mService.switchTo(connection);
	}
	
	/** Gets the current window token list in loaded order as an array.
	 * 
	 * @return The array of window tokens in loaded order.
	 */
	public final WindowToken[] getWindows() {
		if (mLoaded) {
			WindowToken[] tmp = new WindowToken[mWindows.size()];
			tmp = mWindows.toArray(tmp);
			return tmp;
		} else {
			return null;
		}
	}

	/** Called from the foreground window. This method fetches a named script body from a plugin.
	 * 
	 * @param plugin The plugin to look in.
	 * @param name The name of the script to fetch.
	 * @return The script body.
	 */
	public final String getScript(final String plugin, final String name) {
		for (Plugin p : mPlugins) {
			if (p.getSettings().getName().equals(plugin)) {
				if (p.getSettings().getScripts().containsKey(name)) {
					ScriptData d = p.getSettings().getScripts().get(name);
					return d.getData();
				} else {
					return "";
				}
			}
		}
		
		if (mSettings.getSettings().getScripts().containsKey(name)) {
			ScriptData d = mSettings.getSettings().getScripts().get(name);
			return d.getData();
		} else {
			return "";
		}
	}

	/** Calls an anonymous global function in the target plugin with arguments.
	 * 
	 * @param id The ID of the plugin to target.
	 * @param callback The name of the desired function to execute.
	 * @param args The data to supply to @param callback.
	 */
	public final void executeFunctionCallback(final int id, final String callback, final String args) {
		if (id < 0 || id >= mPlugins.size()) {
			return;
		}
		Plugin p = mPlugins.get(id);
		if (p == null || !p.isEnabled()) {
			return;
		}
		p.execute(callback, args);
	}

	/** The reciever of the foreground window PluginXCallS Lua function.
	 * 
	 * @param plugin The name of the plugin to look in.
	 * @param function The name of the anonymous global function to call.
	 * @param str The argument to pass to <b>function</b>.
	 */
	public final void pluginXcallS(final String plugin, final String function, final String str) {
		for (Plugin p : mPlugins) {
			if (p.getName().equals(plugin) && p.isEnabled()) {
				p.xcallS(function, str);
			}
		}
	}

	/** Helper method for reverse mapping R.java constants from name to id.
	 * 
	 * @param variableName The desired field name e.g. "alias_dialog".
	 * @param context THe current application context to use.
	 * @param c The class to search, this is usually R.layout or R.drawable.
	 * @return The integer id of <b>variableName</b> or -1 if the class does not have a field named <b>variableName</b>.
	 */
	public static int getResId(final String variableName, final Context context, final Class<?> c) {

	    try {
	        Field idField = c.getDeclaredField(variableName);
	        return idField.getInt(idField);
	    } catch (Exception e) {
	        com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("Connection.getResId", e);
	        return -1;
	    } 
	}

	/** Helper function to get a window by name.
	 * 
	 * @param desired The name of the window to look up.
	 * @return The WindowToken for the corresponding window name.
	 */
	public final WindowToken getWindowByName(final String desired) {
		for (int i = 0; i < mWindows.size(); i++) {
			WindowToken t = mWindows.get(i);
			if (t.getName().equals(desired)) {
				return t;
			}
		}
		return null;
	}

	/** Helper function to get the triggers for the main conenction settings.
	 *
	 * @return the triggers for the main connection settings.
	 */
	public final HashMap<String, TriggerData> getTriggers() {
		return mTriggers.getTriggers();
	}

	/**
	 * Whether this world wants the phone's own state as {@code device.*}
	 * session variables. Off by default; nothing is registered while it is off.
	 */
	public final boolean isDeviceStateVariables() {
		try {
			Object opt = mSettings.getSettings().getOptions()
					.findOptionByKey("device_state_variables");
			if (opt instanceof com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption) {
				Object val = ((com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption) opt)
						.getValue();
				return (val instanceof Boolean) && ((Boolean) val).booleanValue();
			}
		} catch (Exception ignored) {
			// A world whose settings are half-loaded simply does not want it yet.
		}
		return false;
	}

	/**
	 * Run every trigger set up for this device gesture.
	 *
	 * <p>Called by the gesture detectors and by {@code .sensor fire}. A gesture
	 * has no text and no capture groups, so this uses the calling convention
	 * timers already use — no buffer, no line, nothing matched — and its own
	 * capture map rather than the one the text path clears per match.
	 *
	 * <p>Safe to call from any thread: the work is posted to the connection's
	 * own handler, which is the thread that owns the trigger system and the
	 * text buffer.
	 *
	 * @param gestureId a gesture name from {@code GestureCatalog}.
	 */
	public final void fireDeviceGesture(final String gestureId) {
		final com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture g =
				com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.byId(gestureId);
		if (g == null || mHandler == null) {
			return;
		}
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				runDeviceGesture(g.getPattern());
			}
		});
	}

	/** How many triggers answered, for the report {@code .sensor fire} prints. */
	private int runDeviceGesture(final String pattern) {
		int fired = 0;
		if (mSettings == null) {
			return 0;
		}
		fired += runGestureIn(mSettings, pattern);
		for (Plugin p : mPlugins) {
			if (p != null && p.isEnabled() && p != mSettings) {
				fired += runGestureIn(p, pattern);
			}
		}
		return fired;
	}

	private int runGestureIn(final Plugin owner, final String pattern) {
		int fired = 0;
		HashMap<String, TriggerData> triggers = owner.getSettings().getTriggers();
		if (triggers == null) {
			return 0;
		}
		// A copy: a script responder may add or remove a trigger while this runs,
		// and the text path has been bitten by exactly that before.
		ArrayList<TriggerData> matching = new ArrayList<TriggerData>();
		for (TriggerData t : triggers.values()) {
			if (t != null && t.isEnabled() && !t.isInterpretAsRegex()
					&& pattern.equals(t.getPattern())) {
				matching.add(t);
			}
		}
		for (TriggerData t : matching) {
			if (t.isFireOnce() && t.isFired()) {
				continue;
			}
			if (!ConditionEvaluator.evaluate(t, this)) {
				continue;
			}
			if (t.isFireOnce()) {
				t.setFired(true);
			}
			fired++;
			HashMap<String, String> captures = new HashMap<String, String>();
			for (TriggerResponder responder : t.getResponders()) {
				try {
					responder.doResponse(mService.getApplicationContext(), null, 0, null,
							null, 0, 0, "", t, mDisplay, mHost, mPort,
							StellarService.getNotificationId(), mService.isWindowConnected(),
							mHandler, captures, owner.getLuaState(), t.getName(),
							mSettings.getEncoding());
				} catch (Exception e) {
					String rname = responder != null
							? responder.getClass().getSimpleName() : "?";
					reportRuntimeError("sensor \"" + t.getName() + "\" / " + rname, e);
				}
			}
		}
		return fired;
	}

	/**
	 * Fire a gesture now and say what answered, for {@code .sensor fire}.
	 *
	 * <p>The reply matters as much as the firing: "nothing is set up for this"
	 * and "it fired and did nothing visible" look identical from the outside,
	 * and the first is the far more common mistake.
	 */
	public final String fireDeviceGestureAndReport(final String gestureId) {
		// Runs the responders on the calling thread rather than posting, so it
		// can count them for the reply. Safe because the only caller is a dot
		// command, and processCommand is reached from sendToServer, which runs
		// only from the MESSAGE_SENDDATA_* cases of this connection's handler —
		// the same looper the posting path targets.
		com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture g =
				com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.byId(gestureId);
		if (g == null) {
			return "\nThere is no sensor reading called \"" + gestureId + "\". Try .sensor.\n";
		}
		int fired = runDeviceGesture(g.getPattern());
		if (fired == 0) {
			return "\nNothing is set up for " + g.getId() + ". Give it something to do"
					+ " with\n.sensor " + g.getId() + " <command>, or in the Triggers"
					+ " editor.\n";
		}
		return "\nFired " + g.getId() + ": " + fired
				+ (fired == 1 ? " trigger answered." : " triggers answered.") + "\n";
	}

	/**
	 * Gesture names this world has at least one enabled trigger for.
	 *
	 * <p>The watcher registers a sensor only when something is waiting for it, so
	 * this is what decides whether the proximity sensor is listening at all.
	 */
	public final java.util.Set<String> enabledGestureIds() {
		java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<String>();
		if (mSettings == null) {
			return ids;
		}
		collectGestureIds(mSettings, ids);
		for (Plugin p : mPlugins) {
			if (p != null && p.isEnabled() && p != mSettings) {
				collectGestureIds(p, ids);
			}
		}
		return ids;
	}

	private void collectGestureIds(final Plugin owner, final java.util.Set<String> into) {
		HashMap<String, TriggerData> triggers = owner.getSettings().getTriggers();
		if (triggers == null) {
			return;
		}
		for (TriggerData t : triggers.values()) {
			if (t == null || !t.isEnabled()) {
				continue;
			}
			com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture g =
					com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.fromPattern(
							t.getPattern(), !t.isInterpretAsRegex());
			if (g != null) {
				into.add(g.getId());
			}
		}
	}

	/**
	 * Tell the watcher to pick up or release sensors after a gesture changed.
	 *
	 * <p>Adding the first gesture trigger is what makes the sensor worth
	 * listening to; removing the last one is what makes it waste.
	 */
	public final void refreshDeviceGestures() {
		if (mService != null) {
			mService.refreshDeviceSensors();
		}
	}

	/**
	 * Whether a gesture may fire into this world right now.
	 *
	 * <p>Reported on 9 Aug: a shake sent its command with the screen off and with
	 * the app swiped into Recents — a phone knocked about in a pocket talking to
	 * the game. Movement is what needs the gate; the system events do not have it
	 * at all, because hushing speech when the headphones come out is a thing that
	 * has to work precisely when nobody is looking at the screen.
	 *
	 * @param gestureId the gesture about to fire.
	 * @return true when it should be allowed through.
	 */
	public final boolean allowsGestureNow(final String gestureId) {
		com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture g =
				com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.byId(gestureId);
		if (g == null) {
			return false;
		}
		if (g.getProviders().contains(
				com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.BY_SYSTEM)) {
			return true;
		}
		if (!flagOption("sensor_background", false) && !isUiInFront()) {
			return false;
		}
		if (!flagOption("sensor_screen_off", false) && !isScreenInteractive()) {
			return false;
		}
		return true;
	}

	/**
	 * Whether there is a game window in front of the player right now.
	 *
	 * <p>Two things have to agree, and that is deliberate. The service's flag is
	 * told to it by {@code MainWindow.onResume}/{@code onPause}, which is exact
	 * while the UI is alive and stale the moment it is not: it starts life
	 * {@code true}, and a UI process that dies without pausing — which this
	 * project has watched happen — leaves it saying "showing" for ever. A
	 * registered window callback is a fact rather than a remembered assertion, so
	 * a gesture is only treated as foreground when both hold.
	 */
	private boolean isUiInFront() {
		if (mService == null || !mService.isWindowConnected()) {
			return false;
		}
		return mWindowCallbackMap != null && !mWindowCallbackMap.isEmpty();
	}

	/** Ask the system, rather than trusting a broadcast we may have missed. */
	private boolean isScreenInteractive() {
		try {
			Object power = getContext().getSystemService(Context.POWER_SERVICE);
			if (power instanceof android.os.PowerManager) {
				return ((android.os.PowerManager) power).isInteractive();
			}
		} catch (Exception ignored) {
			// Cannot tell: treat the phone as awake rather than silently
			// swallowing every gesture the player set up.
		}
		return true;
	}

	/** One boolean option by key, with a default when the settings are not up yet. */
	private boolean flagOption(final String key, final boolean fallback) {
		try {
			Object opt = mSettings.getSettings().getOptions().findOptionByKey(key);
			if (opt instanceof com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption) {
				Object val = ((com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption) opt)
						.getValue();
				return (val instanceof Boolean) ? ((Boolean) val).booleanValue() : fallback;
			}
		} catch (Exception ignored) {
		}
		return fallback;
	}

	/**
	 * Turn the device.* variables on or off from the input bar.
	 *
	 * <p>The setting is what makes a condition on the phone mean anything, and a
	 * player who has just picked "phone is face down" in the condition editor
	 * should not have to hunt through Options to make it true.
	 */
	public final void setDeviceStateVariables(final boolean on) {
		try {
			Object opt = mSettings.getSettings().getOptions()
					.findOptionByKey("device_state_variables");
			if (opt instanceof com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption) {
				((com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption) opt)
						.setValue(Boolean.valueOf(on));
				saveMainSettings();
				refreshDeviceGestures();
				if (mService != null) {
					mService.refreshDeviceState();
				}
			}
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"Connection.setDeviceStateVariables", e);
		}
	}

	/** Put a freshly calibrated threshold to work; see the watcher's retune. */
	public final void retuneDeviceSensors() {
		if (mService != null) {
			mService.retuneDeviceSensors();
		}
	}

	/** The device.* reading, for {@code .probe sensors state}. */
	public final String deviceStateReport() {
		if (!isDeviceStateVariables()) {
			return "\nDevice state is off for this world. Settings → Device →\n"
					+ "\"Device state as variables\". Nothing is registered while it is off.\n";
		}
		return mService.deviceStateReport();
	}

	public final SessionVariableStore getSessionVariables() {
		return mSessionVariables;
	}

	/** Helper function to get the triggers for a given plugin.
	 *
	 * @param name The name of the plugin to interrogate.
	 * @return The triggers of the given plugin, null if <b>name</b> does not correspond to a loaded plugin.
	 */
	public final HashMap<String, TriggerData> getPluginTriggers(final String name) {
		return mTriggers.getPluginTriggers(name);
	}

	/** Adds a trigger into the main settings plugin.
	 *
	 * @param data The trigger to add.
	 */
	public final void addTrigger(final TriggerData data) {
		mTriggers.addTrigger(data);
	}

	/** Updates a trigger in the main settings plugin.
	 *
	 * @param from Old trigger.
	 * @param to New trigger.
	 */
	public final void updateTrigger(final TriggerData from, final TriggerData to) {
		mTriggers.updateTrigger(from, to);
	}

	/** Updates a trigger in the target plugin.
	 *
	 * @param selectedPlugin Name of the plugin to work in.
	 * @param from Old plugin.
	 * @param to New plugin.
	 */
	public final void updatePluginTrigger(final String selectedPlugin, final TriggerData from,
			final TriggerData to) {
		mTriggers.updatePluginTrigger(selectedPlugin, from, to);
	}

	/** Adds a new trigger in the target plugin.
	 *
	 * @param selectedPlugin Target plugin for the new trigger.
	 * @param data The new trigger.
	 */
	public final void newPluginTrigger(final String selectedPlugin, final TriggerData data) {
		mTriggers.newPluginTrigger(selectedPlugin, data);
	}

	/** Gets a trigger in the target plugin.
	 *
	 * @param selectedPlugin Name of the plugin to look in.
	 * @param pattern Name of the desired trigger.
	 * @return The trigger, <b>null</b> if it does not exist.
	 */
	public final TriggerData getPluginTrigger(final String selectedPlugin, final String pattern) {
		return mTriggers.getPluginTrigger(selectedPlugin, pattern);
	}

	/** Gets a trigger from the main settings plugin.
	 *
	 * @param pattern Name of the trigger to get.
	 * @return The trigger, <b>null</b> if it does not exist.
	 */
	public final TriggerData getTrigger(final String pattern) {
		return mTriggers.getTrigger(pattern);
	}

	/** Sets the enabled state of a trigger in the target plugin.
	 *
	 * @param selectedPlugin Name of the target plugin to affect.
	 * @param enabled Desired state of the trigger.
	 * @param key The name of the trigger to affect.
	 */
	public final void setPluginTriggerEnabled(final String selectedPlugin, final boolean enabled,
			final String key) {
		mTriggers.setPluginTriggerEnabled(selectedPlugin, enabled, key);
	}

	/** Sets the enabled state of a trigger in the main settings plugin.
	 *
	 * @param enabled Desired state of the target trigger.
	 * @param key Name of the trigger to affect.
	 */
	public final void setTriggerEnabled(final boolean enabled, final String key) {
		mTriggers.setTriggerEnabled(enabled, key);
	}

	/**
	 * Toggles enabled state of a main-settings trigger.
	 *
	 * @param key Trigger name.
	 * @return New enabled state, or {@code null} if the trigger does not exist.
	 */
	public final Boolean toggleTriggerEnabled(final String key) {
		return mTriggers.toggleTriggerEnabled(key);
	}

	/**
	 * Sets enabled state for all main-settings triggers whose
	 * {@link TriggerData#getGroup()} equals {@code group} (exact match,
	 * same as Lua {@code EnableTriggerGroup}).
	 *
	 * @param group Group name (empty string = default group).
	 * @param enabled Desired state.
	 * @return Number of triggers updated.
	 */
	public final int setTriggerGroupEnabled(final String group, final boolean enabled) {
		return mTriggers.setTriggerGroupEnabled(group, enabled);
	}

	/**
	 * Toggles each main-settings trigger in {@code group} (exact
	 * {@link TriggerData#getGroup()} match).
	 *
	 * @param group Group name (empty string = default group).
	 * @return Number of triggers toggled.
	 */
	public final int toggleTriggerGroupEnabled(final String group) {
		return mTriggers.toggleTriggerGroupEnabled(group);
	}

	/**
	 * Enables or disables every trigger in the main settings plugin.
	 *
	 * @param enabled Desired state.
	 * @return Number of triggers updated.
	 */
	public final int setAllTriggersEnabled(final boolean enabled) {
		return mTriggers.setAllTriggersEnabled(enabled);
	}

	/**
	 * Toggles enabled state of a trigger in the target plugin.
	 *
	 * @param selectedPlugin Plugin name.
	 * @param key Trigger name.
	 * @return New enabled state, or {@code null} if missing.
	 */
	public final Boolean togglePluginTriggerEnabled(final String selectedPlugin, final String key) {
		return mTriggers.togglePluginTriggerEnabled(selectedPlugin, key);
	}

	/**
	 * Sets enabled state for all triggers in {@code selectedPlugin} whose
	 * {@link TriggerData#getGroup()} equals {@code group}.
	 *
	 * @return Number of triggers updated.
	 */
	public final int setPluginTriggerGroupEnabled(final String selectedPlugin,
			final String group, final boolean enabled) {
		return mTriggers.setPluginTriggerGroupEnabled(selectedPlugin, group, enabled);
	}

	/**
	 * Toggles each trigger in {@code selectedPlugin} matching {@code group}.
	 *
	 * @return Number of triggers toggled.
	 */
	public final int togglePluginTriggerGroupEnabled(final String selectedPlugin,
			final String group) {
		return mTriggers.togglePluginTriggerGroupEnabled(selectedPlugin, group);
	}

	/**
	 * Enables or disables every trigger in the target plugin.
	 *
	 * @return Number of triggers updated.
	 */
	public final int setAllPluginTriggersEnabled(final String selectedPlugin,
			final boolean enabled) {
		return mTriggers.setAllPluginTriggersEnabled(selectedPlugin, enabled);
	}

	/**
	 * Sets group enabled state across main settings and every loaded plugin.
	 *
	 * @return Total triggers updated.
	 */
	public final int setTriggerGroupEnabledEverywhere(final String group,
			final boolean enabled) {
		return mTriggers.setTriggerGroupEnabledEverywhere(group, enabled);
	}

	/**
	 * Toggles group across main settings and every loaded plugin.
	 *
	 * @return Total triggers toggled.
	 */
	public final int toggleTriggerGroupEnabledEverywhere(final String group) {
		return mTriggers.toggleTriggerGroupEnabledEverywhere(group);
	}

	/** Removes a trigger from the target plugin.
	 *
	 * @param selectedPlugin Name of the plugin to search in.
	 * @param which Name of the trigger to remove.
	 */
	public final void deletePluginTrigger(final String selectedPlugin, final String which) {
		mTriggers.deletePluginTrigger(selectedPlugin, which);
	}

	/** Removes a trigger from the main settings plugin.
	 *
	 * @param which Name of the trigger to remove.
	 */
	public final void deleteTrigger(final String which) {
		mTriggers.deleteTrigger(which);
	}

	/** Sets the aliases for the main settings plugin. This comes from the foreground window in one glob.
	 * 
	 * @param map The new alias map (HashMap<String, AliasData>).
	 */
	public final void setAliases(final HashMap<String, AliasData> map) {
		mAliases.setAliases(map);
	}
	
	/** Sets the aliases for a given plugin. This comes from the foreground window in one glob.
	 * 
	 * @param plugin Name of the target plugin to affect.
	 * @param map The new alias map (HashMap<String, AliasData>)
	 */
	public final void setPluginAliases(final String plugin, final HashMap<String, AliasData> map) {
		mAliases.setPluginAliases(plugin, map);
	}
	
	/** Gets an alias for a target plugin.
	 * 
	 * @param plugin Name of the plugin to search.
	 * @param key The pre part of the alias.
	 * @return The AliasData associated with <b>key</b>.
	 */
	public final AliasData getPluginAlias(final String plugin, final String key) {
		return mAliases.getPluginAlias(plugin, key);
	}
	
	/** Gets an alias from the main settings plugin.
	 * 
	 * @param key The pre part of the alias.
	 * @return The AliasData associated with <b>key</b>
	 */
	public final AliasData getAlias(final String key) {
		return mAliases.getAlias(key);
	}
	
	/** Removes an alias form the main settings plugin.
	 * 
	 * @param key The pre part of the alias to delete.
	 */
	public final void deleteAlias(final String key) {
		mAliases.deleteAlias(key);
	}
	
	/** Removes an alias from the target plugin.
	 * 
	 * @param plugin The name of the plugin to affect.
	 * @param key The pre part of the alias to remove.
	 */
	public final void deletePluginAlias(final String plugin, final String key) {
		mAliases.deletePluginAlias(plugin, key);
	}
	
	/** Gets the alias map for the main settings plugin.
	 * 
	 * @return The alais map for the main settings plugin.
	 */
	public final HashMap<String, AliasData> getAliases() {
		return mAliases.getAliases();
	}
	
	/** Gets the alias map for a target plugin.
	 * 
	 * @param plugin The desired plugin to interrogate.
	 * @return The alias map for <b>plugin</b>.
	 */
	public final HashMap<String, AliasData> getPluginAliases(final String plugin) {
		return mAliases.getPluginAliases(plugin);
	}
	
	/** Gets the list of all the installed system commands.
	 * 
	 * @return The system command list.
	 */
	public final ArrayList<String> getSystemCommands() {
		ArrayList<String> list = new ArrayList<String>();
		Set<String> keys = mSpecialCommands.keySet();
		for (String key : keys) {
			list.add(key);
		}
		return list;
	}

	/** Sets the enabled state of an alias in the target plugin.
	 * 
	 * @param plugin Name of the target plugin.
	 * @param enabled Desired state of the alias.
	 * @param key The pre part of the alias to affect.
	 */
	public final void setPluginAliasEnabled(final String plugin, final boolean enabled, final String key) {
		mAliases.setPluginAliasEnabled(plugin, enabled, key);
	}

	/** Sets the enabled state of an alias in the main settings plugin.
	 * 
	 * @param enabled Desired state of the alias.
	 * @param key The pre part of the alias to affect.
	 */
	public final void setAliasEnabled(final boolean enabled, final String key) {
		mAliases.setAliasEnabled(enabled, key);
	}

	/** Helper function for the keyboard command. Does an alias replacment in a special kind of way.
	 * 
	 * @param bytes Bytes to process.
	 * @param reprocess Weather to do recursive alias replacement.
	 * @return The processed command bytes.
	 */
	public final byte[] doKeyboardAliasReplace(final byte[] bytes, final Boolean reprocess) {
		return mAliases.doKeyboardAliasReplace(bytes, reprocess);
	}

	/** User-initiated disconnect (e.g. {@code .disconnect}), same effect as overflow Disconnect. */
	public final void disconnectByUser() {
		killNetThreads(true);
		doDisconnect(true);
	}

	/** Hard-stop Client.Media audio (keep connection). Used when the task is swiped away. */
	public final void stopGmcpMedia() {
		if (mProcessor != null) {
			mProcessor.stopGmcpMedia();
		}
	}

	/** Put any unwritten map on disk now. For teardown paths only. */
	public final void flushMapperSaves() {
		if (mMapper != null) {
			mMapper.flushPendingSaves();
		}
	}

	/** Helper method that kicks off the reconnection sequence. */
	public final void startReconnect() {
		mHandler.sendEmptyMessage(MESSAGE_RECONNECT);
	}
	
	/** Helper method to initiate a reconnect right now. */
	public final void doReconnect() {
		synchronized (mStartupLock) {
			if (mPump != null) {
				if (mPump.getHandler() != null) {
					mPump.closeSocket();
					mPump.getHandler().removeCallbacksAndMessages(null);
					mPump.getHandler().sendEmptyMessage(DataPumper.MESSAGE_END);
					try {
						mPump.join(2000);
					} catch (InterruptedException e) {
						com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor("Connection.doReconnect", e);
					}
				}
				mPump = null;
			}
		}
		doStartup();
	}

	/** Removes a timer from the target plugin.
	 * 
	 * @param plugin Name of the target plugin.
	 * @param name Name of the timer to remove.
	 */
	public final void deletePluginTimer(final String plugin, final String name) {
		mTimers.deletePluginTimer(plugin, name);
	}

	/** Gets a timer from the main settings plugin.
	 * 
	 * @param name Name of the timer to get.
	 * @return The timer associated with <b>name</b>.
	 */
	public final TimerData getTimer(final String name) {
		return mTimers.getTimer(name);
	}

	/** Removes a timer from the main settings plugin.
	 * 
	 * @param name Name of the trigger to remove.
	 */
	public final void deleteTimer(final String name) {
		mTimers.deleteTimer(name);
	}

	/** Gets a timer from the target plugin.
	 * 
	 * @param plugin Name of the target plugin.
	 * @param name Name of the trigger to get.
	 * @return The trigger associated with <b>name</b>.
	 */
	public final TimerData getPluginTimer(final String plugin, final String name) {
		return mTimers.getPluginTimer(plugin, name);
	}

	/** Adds a timer to the target plugin.
	 * 
	 * @param plugin Name of the target plugin.
	 * @param newtimer New timer data.
	 */
	public final void addPluginTimer(final String plugin, final TimerData newtimer) {
		mTimers.addPluginTimer(plugin, newtimer);
	}

	/** Updates a timer in the target plugin.
	 * 
	 * @param plugin Name of the target plugin.
	 * @param old Old timer data.
	 * @param newtimer New timer data.
	 */
	public final void updatePluginTimer(final String plugin, final TimerData old,
		final TimerData newtimer) {
		mTimers.updatePluginTimer(plugin, old, newtimer);
	}

	/** Updates a timer in the main settings plugin.
	 * 
	 * @param old Old timer data.
	 * @param newtimer New timer data.
	 */
	public final void updateTimer(final TimerData old, final TimerData newtimer) {
		mTimers.updateTimer(old, newtimer);
	}

	/** Gets the timer map for the main settings plugin.
	 * 
	 * @return The timer map.
	 */
	public final HashMap<String, TimerData> getTimers() {
		return mTimers.getTimers();
	}

	/** Gets the timer map for a target plugin.
	 * 
	 * @param plugin Name of the target plugin.
	 * @return The tier map.
	 */
	public final HashMap<String, TimerData> getPluginTimers(final String plugin) {
		return mTimers.getPluginTimers(plugin);
	}

	/** Adds a new timer into the main settings plugin.
	 * 
	 * @param newtimer New timer to add.
	 */
	public final void addTimer(final TimerData newtimer) {
		mTimers.addTimer(newtimer);
	}

	/** Helper method to see if the window is currently being shown.
	 * 
	 * @return visibility state of the foreground window.
	 */
	public final boolean isWindowShowing() {
		return mService.isWindowConnected();
	}
	
	/** Getter method for mDisplay.
	 * 
	 * @return the display name for this connection.
	 */
	/** The service's context, for the few things that need one out here. */
	public final android.content.Context getServiceContext() {
		return mService == null ? null : mService.getApplicationContext();
	}

	/**
	 * Drop the completer's vocabulary and reload what this world has taught.
	 *
	 * <p>The same message the connect path sends. Exposed so {@code .suggest
	 * clear} can empty the bag without knowing how the UI is reached.
	 */
	public final void resetVocabulary() {
		mService.doVocabularyReset(mDisplay);
	}

	/**
	 * Tell the UI to apply a surgical edit to the live suggestion bag.
	 *
	 * <p>The UI holds the bag. A file snapshot can be ten seconds behind, so
	 * this must not load-edit-save from disk. {@code spec} is
	 * {@code forget word}, {@code unpair verb target}, or
	 * {@code weight verb target n}.
	 */
	public final void forgetVocabulary(final String spec) {
		mService.doVocabularyForget(mDisplay, spec == null ? "" : spec);
	}

	public final String getDisplayName() {
		return mDisplay;
	}
	
	/** Helper method for getting the application context out here in the desert of the Service.
	 * 
	 * @return The application context.
	 */
	public final Context getContext() {
		return mService.getApplicationContext();
	}
	
	/** Starts a timer in the main settings plugin with the target name.
	 * 
	 * @param key Name of the timer to start.
	 */
	public final void playTimer(final String key) {
		mTimers.playTimer(key);
	}
	
	/** Starts a timer in the target plugin.
	 * 
	 * @param plugin Name of the target plugin.
	 * @param timer Name of the timer to start.
	 */
	public final void playPluginTimer(final String plugin, final String timer) {
		mTimers.playPluginTimer(plugin, timer);
	}
	
	/** Pauses a timer in the main settings plugin.
	 * 
	 * @param key Name of the plugin to pause.
	 */
	public final void pauseTimer(final String key) {
		mTimers.pauseTimer(key);
	}
	
	/** Pauses a timer in the target plugin.
	 * 
	 * @param plugin Name of the target plugin.
	 * @param timer Name of the timer to pause.
	 */
	public final void pausePluginTimer(final String plugin, final String timer) {
		mTimers.pausePluginTimer(plugin, timer);
	}
	
	/** Stops a timer in the main settings plugin.
	 * 
	 * @param key Name of the timer to stop.
	 */
	public final void stopTimer(final String key) {
		mTimers.stopTimer(key);
	}
	
	/** Stops a timer in the target plugin.
	 * 
	 * @param plugin Name of the target plugin.
	 * @param key Name of the timer to stop.
	 */
	public final void stopPluginTimer(final String plugin, final String key) {
		mTimers.stopPluginTimer(plugin, key);
	}

	/** Gets the settings object for the main settings plugin.
	 * 
	 * @return The settings for the main settings plugin.
	 */
	public final SettingsGroup getSettings() {
		if (mSettings == null) {
			return new SettingsGroup();
		}
		return mSettings.getSettings().getOptions();
	}
	
	/** Gets the settings object for a target plugin.
	 * 
	 * @param plugin Name of the target plugin.
	 * @return The settings for the target plugin. Returns null if <b>name</b> is not a loaded plugin.
	 */
	public final SettingsGroup getPluginSettings(final String plugin) {
		Plugin p = mPluginMap.get(plugin);
		if (p != null) {
			return p.getSettings().getOptions();
		} else {
			return null;
		}
	}

	/**
	 * Reads an integer option off the main game window, or {@code fallback} when
	 * there is no window yet or the key is not an integer option.
	 */
	public final int getMainWindowIntegerOption(final String key, final int fallback) {
		try {
			if (mWindows == null || mWindows.isEmpty() || mWindows.get(0) == null
					|| mWindows.get(0).getSettings() == null) {
				return fallback;
			}
			Object opt = mWindows.get(0).getSettings().findOptionByKey(key);
			if (opt instanceof IntegerOption) {
				return (Integer) ((IntegerOption) opt).getValue();
			}
		} catch (Exception e) {
			Log.w("BlowTorch", "getMainWindowIntegerOption " + key, e);
		}
		return fallback;
	}

	/**
	 * Sets an integer option on the main game window and makes it take effect at
	 * once: the window's own settings, the UI over the callback, and a save.
	 * Window options do not live in the connection settings plugin, so
	 * {@link #updateIntegerSetting} alone changes nothing the player can see —
	 * this is the path clampExcessiveFontSizeFromBadFit already uses.
	 *
	 * @return false when there is no window to change yet.
	 */
	public final boolean updateMainWindowIntegerOption(final String key, final int value) {
		try {
			if (mWindows == null || mWindows.isEmpty() || mWindows.get(0) == null
					|| mWindows.get(0).getSettings() == null) {
				return false;
			}
			WindowToken main = mWindows.get(0);
			String text = Integer.toString(value);
			main.getSettings().setOption(key, text);
			if ("font_size".equals(key) && mSettings != null) {
				// The colouriser wraps at this size; leaving it stale breaks wrapping.
				mSettings.setLineSize(value);
			}
			IWindowCallback cb = mWindowCallbackMap.get(main.getName());
			if (cb != null) {
				cb.updateSetting(key, text);
			}
			mHandler.obtainMessage(MESSAGE_SAVESETTINGS, "").sendToTarget();
			return true;
		} catch (Exception e) {
			Log.w("BlowTorch", "updateMainWindowIntegerOption " + key, e);
			return false;
		}
	}

	/**
	 * Reads a boolean option off the main game window, or {@code fallback} when
	 * there is no window yet or the key is not a boolean option.
	 */
	public final boolean getMainWindowBooleanOption(final String key, final boolean fallback) {
		try {
			if (mWindows == null || mWindows.isEmpty() || mWindows.get(0) == null
					|| mWindows.get(0).getSettings() == null) {
				return fallback;
			}
			Object opt = mWindows.get(0).getSettings().findOptionByKey(key);
			if (opt instanceof BooleanOption) {
				return ((Boolean) ((BooleanOption) opt).getValue()).booleanValue();
			}
		} catch (Exception e) {
			Log.w("BlowTorch", "getMainWindowBooleanOption " + key, e);
		}
		return fallback;
	}

	/**
	 * Sets a boolean option on the main game window and makes it take effect at
	 * once: the window's own settings, the UI over the callback, and a save.
	 * Same reason as {@link #updateMainWindowIntegerOption}: window options do
	 * not live in the connection settings plugin.
	 *
	 * @return false when there is no window to change yet.
	 */
	public final boolean updateMainWindowBooleanOption(final String key, final boolean value) {
		try {
			if (mWindows == null || mWindows.isEmpty() || mWindows.get(0) == null
					|| mWindows.get(0).getSettings() == null) {
				return false;
			}
			WindowToken main = mWindows.get(0);
			String text = Boolean.toString(value);
			main.getSettings().setOption(key, text);
			IWindowCallback cb = mWindowCallbackMap.get(main.getName());
			if (cb != null) {
				cb.updateSetting(key, text);
			}
			mHandler.obtainMessage(MESSAGE_SAVESETTINGS, "").sendToTarget();
			return true;
		} catch (Exception e) {
			Log.w("BlowTorch", "updateMainWindowBooleanOption " + key, e);
			return false;
		}
	}

	/** Updates a boolean setting in the main settings plugin.
	 *
	 * @param key id of the setting to affect.
	 * @param value new value for setting <b>key</b>
	 */
	public final void updateBooleanSetting(final String key, final boolean value) {
		// Options → Window nests WindowToken keys under the connection tree for
		// the menu only. Writing them through the connection plugin leaves the
		// profile's <window> section stale (.editbutton looked app-wide).
		if (WindowTokenParser.isWindowOptionKey(key)) {
			updateMainWindowBooleanOption(key, value);
			return;
		}
		mSettings.updateBooleanSetting(key, value);
		// SettingsGroup only notifies Lua OnOptionChanged; also run Connection KEYS handlers
		// (keep_last, grow_input_bar, log_gmcp, …) so the UI/service actually apply the change.
		updateSetting(key, Boolean.toString(value));
	}
	
	/** Updates a boolean setting in the target plugin.
	 * 
	 * @param plugin Name of the target plugin.
	 * @param key key id of the setting to affect.
	 * @param value the value to use.
	 */
	public final void updatePluginBooleanSetting(final String plugin, final String key, final boolean value) {
		Plugin p = mPluginMap.get(plugin);
		if (p != null) {
			p.updateBooleanSetting(key, value);
		}
	}
	
	/** Updates a string setting in the main settings plugin.
	 * 
	 * @param key key id of the setting to affect.
	 * @param value the value to use.
	 */
	public final void updateStringSetting(final String key, final String value) {
		mSettings.updateStringSetting(key, value);
		// SettingsGroup notifies via listener when wired; also apply Connection.KEYS
		// handlers so extra_text_windows / encoding-style keys always live-update.
		updateSetting(key, value);
	}
	
	/** Updates a string setting in the target plugin.
	 * 
	 * @param plugin Name of the target plugin.
	 * @param key key id of the setting to affect.
	 * @param value the value to use.
	 */
	public final void updatePluginStringSetting(final String plugin, final String key, final String value) {
		Plugin p = mPluginMap.get(plugin);
		if (p != null) {
			p.updateStringSetting(key, value);
		}
	}
	
	/** Udpates an integer setting in the main settings plugin.
	 * 
	 * @param key key id of the setting to affect.
	 * @param value the value to use.
	 */
	public final void updateIntegerSetting(final String key, final int value) {
		mSettings.updateIntegerSetting(key, value);
	}
	
	/** Updates an integer setting in the target plugin.
	 * 
	 * @param plugin Name of the target plugin.
	 * @param key key id of the setting to affect.
	 * @param value the value to use.
	 */
	public final void updatePluginIntegerSetting(final String plugin, final String key, final int value) {
		Plugin p = mPluginMap.get(plugin);
		if (p != null) {
			p.updateIntegerSetting(key, value);
		}
	}
	
	/** Updates a float setting in the main settings plugin.
	 * 
	 * @param key key id of the setting to update.
	 * @param value the value to use.
	 */
	public final void updateFloatSetting(final String key, final float value) {
		mSettings.updateFloatSetting(key, value);
	}
	
	/** Updates a float setting in the target plugin.
	 * 
	 * @param plugin Name of the target plugin.
	 * @param key key id of the setting to affect.
	 * @param value the value to use.
	 */
	public final void updatePluginFloatSetting(final String plugin, final String key, final float value) {
		Plugin p = mPluginMap.get(plugin);
		if (p != null) {
			p.updateFloatSetting(key, value);
		}
	}
	
	/** Utility class for tracking changes ot the main window settings. */
	class WindowSettingsChangedListener implements SettingsChangedListener {
		/** Name of the window that this listener is watching. */
		private String mWindow;
		
		/** Constructor.
		 * 
		 * @param window Name of the window to watch.
		 */
		public WindowSettingsChangedListener(final String window) {
			this.mWindow = window;
		}
		
		@Override
		public void updateSetting(final String key, final String value) {
			Connection.this.handleWindowSettingsChanged(mWindow, key, value);
		}
		
	}
	
	/** Work horse of the main window settings change listener.
	 * 
	 * @param window Name of the window that was affected.
	 * @param key Name of the key that changed.
	 * @param value The value that it was changed to.
	 */
	public final void handleWindowSettingsChanged(final String window, final String key, final String value) {

			IWindowCallback callback = mWindowCallbackMap.get(window);
			if (callback == null) {
				return;
			}
			try {
				callback.updateSetting(key, value);
			} catch (RemoteException e) {
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("Connection.handleWindowSettingsChanged", e);
			}
			// Keep legacy lineSize in sync so the next save/reload cannot resurrect
			// a stale giant font from the first-profile 80-col fit.
			if ("font_size".equals(key) && value != null && mSettings != null) {
				try {
					int fontSize = Integer.parseInt(value.trim());
					if (fontSize > 0) {
						mSettings.setLineSize(fontSize);
					}
				} catch (NumberFormatException ignored) {
				}
			}
	}

	@Override
	public final void updateSetting(final String key, final String value) {
		if (mSettings == null) {
			return; //this is for when the settings are first being loaded.
		}
		BaseOption o = (BaseOption) mSettings.getSettings().getOptions().findOptionByKey(key);
		KEYS tmp;
		try {
			tmp = KEYS.valueOf(key);
		} catch (IllegalArgumentException e) {
			// Window/plugin-only keys (font_size, buffer_size, …) are not Connection.KEYS.
			return;
		}
		switch (tmp) {
			case process_semicolon:
				mSettings.setSemiIsNewLine((Boolean) o.getValue());
				break;
			case debug_telnet:
				if (mProcessor != null) {
					mProcessor.setDebugTelnet((Boolean) o.getValue());
				}
				break;
			case encoding:
				this.doUpdateEncoding((String) o.getValue());
				break;
			case terminal_width:
			case terminal_height:
				applyTerminalNaws();
				break;
			case terminal_size_hint:
				// When user turns the tip off, never show again for this profile.
				if (o.getValue() instanceof Boolean && !((Boolean) o.getValue()).booleanValue()) {
					markNawsTipDone();
				}
				break;
			case orientation:
				mService.doExecuteSetOrientation((Integer) o.getValue());
				break;
			case screen_on:
				mService.doExecuteKeepScreenOn((Boolean) o.getValue());
				break;
			case fullscreen:
				mService.doExecuteFullscreen((Boolean) o.getValue());
				break;
			case fullscreen_editor:
				mService.doExecuteFullscreenEditor((Boolean) o.getValue());
				break;
			case use_suggestions:
				mService.doExecuteUseSuggestions((Boolean) o.getValue());
				break;
			case floating_buttons_enabled:
				// MainWindow.loadSettings reads floatingButtonsEnabled.
				mService.doExecuteRequestLoadSettings();
				break;
			case keep_last:
				this.doSetKeepLast((Boolean) o.getValue());
				break;
			case word_complete:
				this.doSetWordComplete((Boolean) o.getValue());
				break;
			case sensor_screen_off:
			case sensor_background:
				// Read at the moment a reading fires, so there is nothing to
				// apply here — but the watcher may need to pick a sensor up or
				// let it go, and that is decided by what is enabled, not by this.
				break;
			case device_state_variables:
				// Applied here rather than asked of the UI: the watcher and the
				// session variables both live in this process, and the whole
				// point of the measurement on 8 Aug was that they can.
				mService.refreshDeviceState();
				break;
			case speak_quiet_typing:
				// The engine lives in this process, so this one is applied here
				// rather than asked of the UI.
				com.resurrection.blowtorch2.lib.util.SpeechEngine.setQuietWhileTyping(
						(Boolean) o.getValue());
				break;
			case word_complete_lines:
			case word_complete_loose:
			case word_complete_phrases:
			case word_complete_short_first:
			case word_complete_shorter_first:
			case word_complete_ghost:
			case word_complete_ghost_lines:
			case word_complete_show:
			case word_complete_persist:
			case word_complete_rank:
			case word_complete_pairs:
			case word_complete_where:
			case word_complete_opacity:
				// MainWindow.loadSettings is what reaches WordSuggestions and the
				// strip; ask the UI to re-read rather than adding a binder call per
				// setting. Every one of these is the UI's business only.
				mService.doExecuteRequestLoadSettings();
				break;
			case prompt_bar:
				this.doSetPromptBar((Boolean) o.getValue());
				break;
			case grow_input_bar:
				this.doSetGrowInputBar((Boolean) o.getValue());
				break;
			case lowercase_command_start:
				// Wire transform reads the option tree; IME half reloads Input flags.
				mService.doExecuteRequestLoadSettings();
				break;
			case compatibility_mode:
				mService.doExecuteCompatibilityMode((Boolean) o.getValue());
				break;
			case local_echo:
				this.doSetLocalEcho((Boolean) o.getValue());
				break;
			case process_system_commands:
				this.doSetProcessSystemCommands((Boolean) o.getValue());
				break;
			case echo_alias_updates:
				this.doSetAliasUpdates((Boolean) o.getValue());
				break;
			case keep_wifi_alive:
				this.doSetKeepWifiAlive((Boolean) o.getValue());
				break;
			case auto_reconnect:
				mReconnect.setAutoReconnect((Boolean) o.getValue());
				break;
			case auto_reconnect_limit:
				mReconnect.setLimit((Integer) o.getValue());
				break;
			case persistent_connection:
				mReconnect.setPersistent((Boolean) o.getValue());
				break;
			case overflow_button_opacity:
			case overflow_button_background:
			case overflow_button_border:
				// The ⋮ lives in the UI process; MainWindow.loadSettings reads these
				// three and hands them to ChromeController. Without this the value
				// was stored and only picked up on the next profile load, which is
				// why the options looked as though they needed a restart.
				mService.doExecuteRequestLoadSettings();
				break;
			case cull_extraneous_color:
				this.doSetCullExtraneousColor((Boolean) o.getValue());
				break;
			case debug_telent:
				this.doSetDebugTelnet((Boolean) o.getValue());
				break;
			case bell_vibrate:
				this.doSetBellVibrate((Boolean) o.getValue());
				break;
			case tap_menu_opacity:
				// Drawn by the UI process and nowhere else.
				mService.doExecuteRequestLoadSettings();
				break;
			case trigger_sound_stream:
			case trigger_sound_warn_silent:
				// Trigger sounds are played in whichever process the responder runs
				// in, so both have to be told. This process applies it directly;
				// the UI re-reads for its own copy, which is what the editor's
				// test button uses.
				applyTriggerSoundSettings();
				mService.doExecuteRequestLoadSettings();
				break;
			case bell_notification:
				this.doSetBellNotify((Boolean) o.getValue());
				break;
			case bell_display:
				this.doSeBellDisplay((Boolean) o.getValue());
				break;
			case show_regex_warning:
				doSetRegexWarning((Boolean) o.getValue());
				break;
			case use_gmcp:
				mGmcp.doSetUseGMCP((Boolean) o.getValue());
				break;
			case gmcp_supports:
				mGmcp.doSetGMCPSupports((String) o.getValue());
				break;
			case log_gmcp:
				mGmcp.doSetLogGMCP((Boolean) o.getValue());
				break;
			case gmcp_feed:
				mGmcp.doSetGmcpFeed((Boolean) o.getValue());
				break;
			case gmcp_suggest_modules:
				mGmcp.doSetGmcpSuggestModules((Boolean) o.getValue());
				break;
			case frame_image_placement:
				mGmcp.doSetFrameImagePlacement((Integer) o.getValue());
				break;
			case frame_image_lines:
				mGmcp.doSetFrameImageLines((Integer) o.getValue());
				break;
			case use_mcp:
				this.doSetUseMCP((Boolean) o.getValue());
				break;
			case mcp_packages:
				this.doSetMcpPackages((String) o.getValue());
				break;
			case log_mcp:
				this.doSetLogMCP((Boolean) o.getValue());
				break;
			case mcp_feed:
				this.doSetMcpFeed((Boolean) o.getValue());
				break;
			case mcp_omit_output:
				this.doSetMcpOmit((Boolean) o.getValue());
				break;
			case mcp_auto_negotiate:
				this.doSetMcpAutoNegotiate((Boolean) o.getValue());
				break;
			case use_mtts:
				this.doSetUseMTTS((Boolean) o.getValue());
				break;
			case use_msdp:
				this.doSetUseMSDP((Boolean) o.getValue());
				break;
			case use_mssp:
				this.doSetUseMSSP((Boolean) o.getValue());
				break;
			case use_mccp:
				this.doSetUseMCCP((Boolean) o.getValue());
				break;
			case use_mxp:
				this.doSetUseMXP((Boolean) o.getValue());
				break;
			case log_mxp:
				this.doSetLogMXP((Boolean) o.getValue());
				break;
			case mxp_feed:
				break;
			case session_log:
				mSessionLog.doSetSessionLog((Boolean) o.getValue());
				break;
			case session_log_directory:
				mSessionLog.doSetSessionLogDirectory((String) o.getValue());
				break;
			case default_settings_directory:
				// Path is read when importing/exporting; nothing live to apply.
				break;
			case mapper_enabled:
				if (mMapper != null) {
					mMapper.setEnabled((Boolean) o.getValue());
				}
				break;
			case mapper_recording_default:
				// Seed only; live toggle is .map record
				break;
			case mapper_follow:
				if (mMapper != null) {
					mMapper.setFollow((Boolean) o.getValue());
				}
				break;
			case mapper_float:
				if (mMapper != null) {
					mMapper.setPreferFloat((Boolean) o.getValue());
				}
				break;
			case mapper_opacity:
				if (mMapper != null) {
					int op = 85;
					try {
						op = (Integer) o.getValue();
					} catch (Exception ignored) {
					}
					if (op < 40) {
						op = 40;
						((IntegerOption) o).setValue(40);
					} else if (op > 100) {
						op = 100;
						((IntegerOption) o).setValue(100);
					}
					// Apply without re-entering settings persistence.
					mMapper.applyOpacityFromSettings(op);
				}
				break;
			case mapper_path_auto_send:
				if (mMapper != null) {
					mMapper.setPathAutoSend((Boolean) o.getValue());
				}
				break;
			case mapper_echo_window:
				if (mMapper != null) {
					mMapper.setEchoWindow((Boolean) o.getValue());
				}
				break;
			case mapper_use_gmcp:
				if (mMapper != null) {
					mMapper.setUseGmcp((Boolean) o.getValue());
				}
				break;
			case mapper_gmcp_use_num:
				if (mMapper != null) {
					mMapper.setGmcpUseNum((Boolean) o.getValue());
				}
				break;
			case mapper_gmcp_use_coords:
				if (mMapper != null) {
					mMapper.setGmcpUseCoords((Boolean) o.getValue());
				}
				break;
			case mapper_gmcp_create_exits:
				if (mMapper != null) {
					mMapper.setGmcpCreateExits((Boolean) o.getValue());
				}
				break;
			case mapper_gmcp_grow:
				if (mMapper != null) {
					mMapper.setGmcpGrow((Boolean) o.getValue());
				}
				break;
			case mapper_gmcp_policy:
				if (mMapper != null) {
					mMapper.setGmcpPolicy((String) o.getValue());
				}
				break;
			case mapper_auto_reverse_link:
				if (mMapper != null) {
					mMapper.setAutoReverse((Boolean) o.getValue());
				}
				break;
			case mapper_accept_one_way_specials:
				if (mMapper != null) {
					mMapper.setAcceptOneWaySpecials((Boolean) o.getValue());
				}
				break;
			case mapper_toolbar_actions:
				if (mMapper != null) {
					mMapper.applySettingsFromConnection();
				}
				break;
			case mapper_capture_title_regex:
				if (mMapper != null) {
					mMapper.setCaptureTitleRegex((String) o.getValue());
				}
				break;
			case mapper_capture_exits_regex:
				if (mMapper != null) {
					mMapper.setCaptureExitsRegex((String) o.getValue());
				}
				break;
			case mapper_level_up_commands:
				if (mMapper != null) {
					mMapper.setLevelUpCommands((String) o.getValue());
				}
				break;
			case mapper_level_down_commands:
				if (mMapper != null) {
					mMapper.setLevelDownCommands((String) o.getValue());
				}
				break;
			case mapper_move_effects:
				if (mMapper != null) {
					mMapper.setMoveEffectsString((String) o.getValue());
				}
				break;
			case extra_text_windows_enabled:
				requestExtraTextUi();
				break;
			case extra_text_windows:
				ensureExtraTextSlots(true);
				break;
			case gauge_widgets_enabled:
				if (mGauges != null) {
					requestGaugeWidgetUi();
				}
				break;
			case gauge_widgets:
				if (mGauges != null) {
					mGauges.reloadFromSettings();
					requestGaugeWidgetUi();
					requestGaugeWidgetValues();
				}
				break;
			default:
				break;
			}
	}

	public final String getGmcpModuleStatus() {
		return mGmcp.getGmcpModuleStatus();
	}

	@SuppressWarnings("rawtypes")
	public final java.util.List getGmcpSeenModules() {
		return mGmcp.getGmcpSeenModules();
	}

	public final void renegotiateGmcp() {
		mGmcp.renegotiateGmcp();
	}

	public final void applyGmcpSupportsFromUi(final String supports, final boolean renegotiate) {
		mGmcp.applyGmcpSupportsFromUi(supports, renegotiate);
	}

	private void doSetUseMTTS(final Boolean value) {
		if (mProcessor != null) {
			mProcessor.setUseMTTS(value != null && value.booleanValue());
		}
	}

	private void doSetUseMSDP(final Boolean value) {
		if (mProcessor != null) {
			mProcessor.setUseMSDP(value != null && value.booleanValue());
		}
	}

	private void doSetUseMSSP(final Boolean value) {
		if (mProcessor != null) {
			mProcessor.setUseMSSP(value != null && value.booleanValue());
		}
	}

	private void doSetUseMCCP(final Boolean value) {
		boolean on = value != null && value.booleanValue();
		// initSettings() replays the whole profile through here on every doStartup(),
		// so a replay must not read as "the player turned compression back on" —
		// that re-enabled MCCP on the reconnect the failure had just triggered.
		if (mReplayingSettings.get().booleanValue()) {
			mMccp.applyProfileValue(on);
		} else {
			mMccp.applyPlayerToggle(on);
		}
		if (mProcessor != null) {
			mProcessor.setUseMCCP(mMccp.isEnabled());
		}
	}

	private void doSetUseMXP(final Boolean value) {
		if (mProcessor != null) {
			mProcessor.setUseMXP(value != null && value.booleanValue());
		}
	}

	private void doSetLogMXP(final Boolean value) {
		if (mProcessor != null) {
			mProcessor.setLogMXP(value != null && value.booleanValue());
		}
	}

	private void attachMxpListener() {
		if (mProcessor == null) {
			return;
		}
		mProcessor.getMxp().setListener(new com.resurrection.blowtorch2.lib.service.mxp.MxpEngine.Listener() {
			@Override
			public void sendToMud(final String text) {
				if (text == null || text.length() == 0 || mPump == null) {
					return;
				}
				try {
					byte[] bytes = text.getBytes(mSettings.getEncoding());
					Message sbm = mHandler.obtainMessage(MESSAGE_SENDOPTIONDATA);
					Bundle b = sbm.getData();
					b.putByteArray("THE_DATA", bytes);
					if (readBoolOption("mxp_feed", false)) {
						b.putString("DEBUG_MESSAGE", Colorizer.getTeloptStartColor()
								+ "MXP OUT: " + text.trim() + Colorizer.getResetColor() + "\n");
					}
					sbm.setData(b);
					mHandler.sendMessage(sbm);
				} catch (Exception e) {
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
							"Connection.mxp.sendToMud", e);
				}
			}

			@Override
			public void expire(final String group) {
				TextTree buf = getMainWindowBuffer();
				if (buf != null) {
					buf.expireMxpLinks(group);
				}
				if (readBoolOption("mxp_feed", false)) {
					sendDataToWindow("\n" + Colorizer.getTeloptStartColor()
							+ "MXP expire " + group + Colorizer.getResetColor() + "\n");
				}
			}

			@Override
			public void setVariable(final String name, final String value) {
				getSessionVariables().set(name, value);
				if (mGauges != null) {
					mGauges.onSessionVar(name, value);
				}
			}

			@Override
			public void destOutput(final String window, final byte[] data) {
				if (window == null || data == null || data.length == 0) {
					return;
				}
				WindowToken tok = findWindowIgnoreCase(window);
				if (tok == null || tok.getBuffer() == null) {
					sendBytesToWindow(data);
					return;
				}
				try {
					tok.getBuffer().addBytesImpl(data);
				} catch (Exception e) {
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
							"Connection.mxp.destOutput", e);
					return;
				}
				if (!holdWhileHidden(tok.getName(), data)) {
					IWindowCallback c = mWindowCallbackMap.get(tok.getName());
					if (c != null) {
						try {
							c.rawDataIncoming(data);
						} catch (RemoteException e) {
							com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
									"Connection.mxp.destOutput.notify", e);
						}
					}
				}
			}

			@Override
			public void playSound(final com.resurrection.blowtorch2.lib.service.mxp.MxpSound.Request req) {
				if (mProcessor != null) {
					mProcessor.playMxpSound(req);
				}
			}

			@Override
			public void onFlag(final String flag, final String text) {
				if (flag != null && flag.toLowerCase(java.util.Locale.US).startsWith("set ")) {
					return;
				}
				if (flag != null && text != null) {
					getSessionVariables().set("mxp." + flag.replace(' ', '_'), text);
				}
			}
		});
	}

	private WindowToken findWindowIgnoreCase(final String name) {
		if (name == null) {
			return null;
		}
		WindowToken exact = getWindowByName(name);
		if (exact != null) {
			return exact;
		}
		for (WindowToken w : mWindows) {
			if (w.getName() != null && w.getName().equalsIgnoreCase(name)) {
				return w;
			}
		}
		return null;
	}

	private void ensureMcpEngine() {
		if (mMcpEngine != null) {
			return;
		}
		mMcpEngine = new McpEngine(new McpEngine.Sink() {
			@Override
			public void sendNetworkLine(String line) {
				if (mHandler != null && line != null) {
					mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_SENDMCPRAW, line));
				}
			}

			@Override
			public void notifyWindow(String message) {
				if (message != null) {
					sendDataToWindow(message);
				}
			}

			@Override
			public String getEncoding() {
				try {
					return mSettings.getEncoding();
				} catch (Exception e) {
					return "UTF-8";
				}
			}

			@Override
			public android.content.Context getContext() {
				return Connection.this.getContext();
			}

			@Override
			public String getDisplayName() {
				return mDisplay;
			}

			@Override
			public void openUrl(String url) {
				mService.launchUrl(url);
			}

			@Override
			public void openSimpleEdit(String reference, String title, String type, String content) {
				mService.showMcpSimpleEdit(reference, title, type, content);
			}

			@Override
			public void fireMcpTrigger(String messageName, HashMap<String, Object> data) {
				if (mHandler == null || data == null) {
					return;
				}
				Object pluginObj = data.get("_plugin");
				Object cbObj = data.get("_callback");
				if (!(pluginObj instanceof String) || !(cbObj instanceof String)) {
					return;
				}
				Message msg = mHandler.obtainMessage(MESSAGE_MCPTRIGGERED, data);
				Bundle b = msg.getData();
				b.putString("TARGET", (String) pluginObj);
				b.putString("CALLBACK", (String) cbObj);
				msg.setData(b);
				mHandler.sendMessage(msg);
			}

			@Override
			public String getClientName() {
				return "BlowTorch";
			}

			@Override
			public String getClientVersion() {
				try {
					return mService.getPackageManager()
							.getPackageInfo(mService.getPackageName(), 0).versionName;
				} catch (Exception e) {
					return "2.1";
				}
			}

			@Override
			public int getDisplayCols() {
				return mLiveCols;
			}

			@Override
			public int getDisplayRows() {
				return mLiveRows;
			}
		}, mHandler);
		if (mGauges != null) {
			mMcpEngine.setStatusCacheListener(new Runnable() {
				@Override
				public void run() {
					if (mGauges != null) {
						mGauges.onMcpStatus();
					}
				}
			});
		}
	}

	private void sendMcpRawToPump(final String line) {
		if (line == null || mPump == null || !mPump.isConnected()) {
			return;
		}
		try {
			String enc = mSettings != null ? mSettings.getEncoding() : "UTF-8";
			String out = line.endsWith("\n") ? line : (line + "\n");
			mPump.sendData(out.getBytes(enc));
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("Connection.sendMcpRawToPump", e);
		}
	}

	public final void sendMcpSimpleEditSet(final String reference, final String type,
			final String content) {
		ensureMcpEngine();
		mMcpEngine.sendSimpleEditSet(reference, type, content);
	}

	public final McpEngine getMcpEngine() {
		ensureMcpEngine();
		return mMcpEngine;
	}

	private void doSetUseMCP(final Boolean value) {
		ensureMcpEngine();
		boolean on = value != null && value.booleanValue();
		mMcpEngine.setUse(on);
		if (!on) {
			mMcpEngine.resetSession();
		}
	}

	private void doSetMcpPackages(final String value) {
		ensureMcpEngine();
		mMcpEngine.setPackagesFromOption(value != null ? value : McpPackageRegistry.DEFAULT_PACKAGES);
	}

	private void doSetLogMCP(final Boolean value) {
		ensureMcpEngine();
		mMcpEngine.setLog(value != null && value.booleanValue());
	}

	private void doSetMcpFeed(final Boolean value) {
		ensureMcpEngine();
		mMcpEngine.setFeed(value != null && value.booleanValue());
	}

	private void doSetMcpOmit(final Boolean value) {
		ensureMcpEngine();
		mMcpEngine.setOmitFromOutput(value == null || value.booleanValue());
	}

	private void doSetMcpAutoNegotiate(final Boolean value) {
		ensureMcpEngine();
		mMcpEngine.setAutoNegotiate(value == null || value.booleanValue());
	}

	public final void applyMcpPackagesFromUi(final String packages, final boolean renegotiate) {
		updateStringSetting("mcp_packages",
				packages != null ? packages : McpPackageRegistry.DEFAULT_PACKAGES);
		if (renegotiate) {
			ensureMcpEngine();
			mMcpEngine.renegotiate();
		}
	}

	public final ArrayList<String> getMcpSeenPackages() {
		ensureMcpEngine();
		return mMcpEngine.getRegistry().seenPackages();
	}

	public final String getMcpStatusHint() {
		ensureMcpEngine();
		return mMcpEngine.statusReport();
	}

	/**
	 * Seed the two input-assist flags from the saved profile.
	 *
	 * <p>They are plain fields read on every incoming chunk, so they cannot be
	 * looked up in the option tree each time. Without this they stayed false until
	 * the player toggled something — a setting that saves, restores and then does
	 * nothing until touched.
	 */
	/** Push the trigger-sound settings into this process's player.
	 *
	 * <p>{@link com.resurrection.blowtorch2.lib.util.TriggerSounds} is per process
	 * and the responder runs here, so reading the option in the UI alone would
	 * leave every live trigger on the default. */
	private void applyTriggerSoundSettings() {
		com.resurrection.blowtorch2.lib.util.TriggerSounds.setStream(
				readIntOption("trigger_sound_stream",
					com.resurrection.blowtorch2.lib.util.TriggerSounds.DEFAULT_STREAM));
		com.resurrection.blowtorch2.lib.util.TriggerSounds.setWarnWhenSilent(
				readBooleanOption("trigger_sound_warn_silent", true));
	}

	private void applyInputAssistSettings() {
		mWordComplete = readBooleanOption("word_complete", false);
		applyTriggerSoundSettings();
		// The vocabulary lives in the UI process for the life of that process, so
		// without this a second world is offered the first one's mob names.
		mService.doVocabularyReset(mDisplay);
		// Through the setter, not the field: setPromptBar(false) is what tells the
		// UI to clear the bar. Assigning raw would leave a prompt from the previous
		// connection pinned there with nothing left to clear it.
		setPromptBar(readBooleanOption("prompt_bar", false));
		// Per connection: "has this world ever sent a prompt" is a question about
		// this session, and a stale count from the last one would answer it wrong.
		mPromptsSeen = 0;
	}

	/** A boolean from the connection's own options, or {@code fallback}. */
	private boolean readBooleanOption(final String key, final boolean fallback) {
		try {
			Object opt = mSettings.getSettings().getOptions().findOptionByKey(key);
			if (opt instanceof BooleanOption) {
				Object val = ((BooleanOption) opt).getValue();
				if (val instanceof Boolean) {
					return ((Boolean) val).booleanValue();
				}
			}
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"Connection.readBooleanOption", e);
		}
		return fallback;
	}

	/** An integer or list index from the connection's own options.
	 *
	 * @param key The option key.
	 * @param fallback What to use when it is missing or is not a number.
	 * @return The stored value, or {@code fallback}.
	 */
	private int readIntOption(final String key, final int fallback) {
		try {
			Object opt = mSettings.getSettings().getOptions().findOptionByKey(key);
			if (opt instanceof BaseOption) {
				Object val = ((BaseOption) opt).getValue();
				if (val instanceof Integer) {
					return ((Integer) val).intValue();
				}
			}
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"Connection.readIntOption", e);
		}
		return fallback;
	}

	private void applyMcpSettings() {
		ensureMcpEngine();
		try {
			Object opt = mSettings.getSettings().getOptions().findOptionByKey("use_mcp");
			boolean on = false;
			if (opt instanceof BooleanOption) {
				Object val = ((BooleanOption) opt).getValue();
				on = (val instanceof Boolean) && ((Boolean) val).booleanValue();
			}
			mMcpEngine.setUse(on);
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"Connection.applyMcpSettings.use_mcp", e);
		}
		try {
			Object opt = mSettings.getSettings().getOptions().findOptionByKey("mcp_packages");
			String pkgs = McpPackageRegistry.DEFAULT_PACKAGES;
			if (opt instanceof StringOption && ((StringOption) opt).getValue() != null) {
				pkgs = ((StringOption) opt).getValue().toString();
			}
			mMcpEngine.setPackagesFromOption(pkgs);
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"Connection.applyMcpSettings.mcp_packages", e);
		}
		try {
			Object opt = mSettings.getSettings().getOptions().findOptionByKey("log_mcp");
			boolean on = false;
			if (opt instanceof BooleanOption) {
				Object val = ((BooleanOption) opt).getValue();
				on = (val instanceof Boolean) && ((Boolean) val).booleanValue();
			}
			mMcpEngine.setLog(on);
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"Connection.applyMcpSettings.log_mcp", e);
		}
		try {
			Object opt = mSettings.getSettings().getOptions().findOptionByKey("mcp_feed");
			boolean on = false;
			if (opt instanceof BooleanOption) {
				Object val = ((BooleanOption) opt).getValue();
				on = (val instanceof Boolean) && ((Boolean) val).booleanValue();
			}
			mMcpEngine.setFeed(on);
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"Connection.applyMcpSettings.mcp_feed", e);
		}
		try {
			Object opt = mSettings.getSettings().getOptions().findOptionByKey("mcp_omit_output");
			boolean on = true;
			if (opt instanceof BooleanOption) {
				Object val = ((BooleanOption) opt).getValue();
				on = !(val instanceof Boolean) || ((Boolean) val).booleanValue();
			}
			mMcpEngine.setOmitFromOutput(on);
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"Connection.applyMcpSettings.mcp_omit_output", e);
		}
		try {
			Object opt = mSettings.getSettings().getOptions().findOptionByKey("mcp_auto_negotiate");
			boolean on = true;
			if (opt instanceof BooleanOption) {
				Object val = ((BooleanOption) opt).getValue();
				on = !(val instanceof Boolean) || ((Boolean) val).booleanValue();
			}
			mMcpEngine.setAutoNegotiate(on);
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"Connection.applyMcpSettings.mcp_auto_negotiate", e);
		}
	}

	/** Apply optional MTTS/MSDP/MSSP/MCCP/MXP flags from profile. */
	void applyMudProtocolFlags() {
		if (mProcessor == null || mSettings == null) {
			return;
		}
		try {
			mProcessor.setUseMTTS(readBoolOption("use_mtts", true));
			mProcessor.setUseMSDP(readBoolOption("use_msdp", false));
			mProcessor.setUseMSSP(readBoolOption("use_mssp", false));
			mProcessor.setUseMXP(readBoolOption("use_mxp", true));
			mProcessor.setLogMXP(readBoolOption("log_mxp", false));
			// applyProfileValue, not applyPlayerToggle: a load or a settings replay
			// must not clear a fallback the player never asked to clear.
			mMccp.applyProfileValue(readBoolOption("use_mccp", true));
			mProcessor.setUseMCCP(mMccp.isEnabled());
		} catch (Exception ignored) {
		}
	}

	/** Whether this connection accepts MCCP2, and whether the fallback has fired. */
	private final MccpFallbackState mMccp = new MccpFallbackState();

	/** Last local-echo state pushed to the windows, so a reconnect can restore it. */
	private volatile boolean mLocalEcho = true;

	/** Telnet ECHO changed hands: tell every window whether to mask the input bar.
	 *
	 * @param enabled true when we echo locally (normal typing), false while the
	 *        server echoes — on a MUD, a password prompt.
	 */
	private void doSetTelnetEcho(final boolean enabled) {
		mLocalEcho = enabled;
		for (IWindowCallback w : mWindowCallbackMap.values()) {
			try {
				w.setLocalEcho(enabled);
			} catch (RemoteException e) {
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
						"Connection.doSetLocalEcho", e);
			}
		}
	}

	/** {@code .echo on|off} — manual override for a server that takes telnet ECHO
	 ** and never hands it back. The next WILL/WONT from the server wins. */
	public final void setTelnetEchoFromCommand(final boolean enabled) {
		if (mHandler != null) {
			mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_LOCALECHO, enabled ? 1 : 0, 0));
		}
	}

	/** @return true when the input bar shows what is typed. */
	public final boolean isTelnetEchoLocal() {
		return mLocalEcho;
	}

	/** A dropped connection cannot be holding echo; never leave the bar masked.
	 ** Posted, never called inline: doDisconnect runs on binder threads too, and
	 ** walking mWindowCallbackMap there races registerWindowCallback — a
	 ** ConcurrentModificationException thrown inside a synchronous binder
	 ** transaction kills the UI process. */
	private void restoreLocalEcho() {
		if (!mLocalEcho && mHandler != null) {
			mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_LOCALECHO, 1, 0));
		}
	}

	/** MCCP decompression died, so everything the server still sends is unreadable.
	 ** Tell the player, remember not to accept COMPRESS2 again on this connection,
	 ** and reconnect plain. One shot — a second failure reports and stops rather
	 ** than looping reconnects. */
	private void handleMccpFailure() {
		boolean first = mMccp.onFailure();
		// Both paths: the negotiator must stop honouring COMPRESS2, including the
		// subnegotiation that actually switches the stream.
		if (mProcessor != null) {
			mProcessor.setUseMCCP(false);
		}
		if (!first) {
			sendDataToWindow("\n" + Colorizer.getRedColor()
					+ "MCCP failed again — compression stays off. Reconnect manually if the session is stuck."
					+ Colorizer.getWhiteColor() + "\n");
			return;
		}
		Log.w("BlowTorch", "MCCP failed for " + mHost + ":" + mPort
				+ " — reconnecting with COMPRESS2 refused");
		sendDataToWindow("\n" + Colorizer.getBrightYellowColor()
				+ "MCCP compression failed — reconnecting without it."
				+ Colorizer.getWhiteColor() + "\n");
		// Directly, not via MESSAGE_RECONNECT: we are already on the handler thread,
		// and three places call removeMessages(MESSAGE_RECONNECT). A cancelled
		// reconnect would leave the pump dropping every byte with nothing said.
		// Drops a queued MESSAGE_RECONNECT. It does not cancel ConnectionReconnect's
		// own postDelayed watchdog, which posts the message itself — harmless, since
		// doStartup skips while connected.
		mHandler.removeMessages(MESSAGE_RECONNECT);
		doReconnect();
	}

	private boolean readBoolOption(final String key, final boolean def) {
		try {
			Object opt = mSettings.getSettings().getOptions().findOptionByKey(key);
			if (opt instanceof BooleanOption) {
				Object val = ((BooleanOption) opt).getValue();
				if (val instanceof Boolean) {
					return ((Boolean) val).booleanValue();
				}
			}
		} catch (Exception ignored) {
		}
		return def;
	}

	/** Impelementation of the bell vibrate settings handler.
	 * 
	 * @param value New value to use.
	 */
	private void doSetBellVibrate(final Boolean value) {
		mSettings.setVibrateOnBell(value);		
	}
	
	/** Impelemntation of the bell notify settings handler.
	 * 
	 * @param value New value to use.
	 */
	private void doSetBellNotify(final Boolean value) {
		mSettings.setNotifyOnBell(value);
	}
	
	/** Impelemntation of the bell toast settings handler.
	 * 
	 * @param value New value to use.
	 */
	private void doSeBellDisplay(final Boolean value) {
		mSettings.setDisplayOnBell(value);
	}

	/** Impelemntation of the set debug telnet settings handler.
	 * 
	 * @param value New value to use.
	 */
	private void doSetDebugTelnet(final Boolean value) {
		mSettings.setDebugTelnet(value);
		if (mProcessor != null) {
			mProcessor.setDebugTelnet(value);
		}
	}

	/** Impelemntation of the cull extraneous colors settings handler.
	 * 
	 * @param value New value to use.
	 */
	private void doSetCullExtraneousColor(final Boolean value) {
		mSettings.setRemoveExtraColor(value);
		mWindows.get(0).getBuffer().setCullExtraneous(value);
	}

	/** Impelemntation of the keep wifi alive settings handler.
	 * 
	 * @param value New value to use.
	 */
	private void doSetKeepWifiAlive(final Boolean value) {
		mSettings.setKeepWifiActive(value);
		if (value) {
			mService.enableWifiKeepAlive();
		} else {
			mService.disableWifiKeepAlive();
		}
	}

	/** Impelemntation of the echo alias update settings handler.
	 * 
	 * @param value New value to use.
	 */
	private void doSetAliasUpdates(final Boolean value) {
		mSettings.setEchoAliasUpdates(value);
	}

	/** Impelemntation of the process system commands settings handler.
	 * 
	 * @param value New value to use.
	 */
	private void doSetProcessSystemCommands(final Boolean value) {
		mSettings.setProcessPeriod(value);
	}

	/** Impelemntation of the local echo settings handler.
	 * 
	 * @param value New value to use.
	 */
	private void doSetLocalEcho(final Boolean value) {
		mSettings.setLocalEcho(value);
	}

	/** Word completion on or off.
	 *
	 * <p>The UI holds the vocabulary, so it has to be told: off should take the
	 * strip away and drop what was learned, not leave stale names sitting there
	 * until something else happens to refresh it.
	 *
	 * @param value New value to use.
	 */
	private void doSetWordComplete(final Boolean value) {
		mWordComplete = value != null && value.booleanValue();
		mService.doExecuteRequestLoadSettings();
	}

	/** Prompt bar on or off.
	 *
	 * @param value New value to use.
	 */
	private void doSetPromptBar(final Boolean value) {
		setPromptBar(value != null && value.booleanValue());
	}

	/** Impelemntation of the keep last settings handler.
	 * 
	 * @param value New value to use.
	 */
	private void doSetKeepLast(final Boolean value) {
		mService.dispatchKeepLast(value);
	}

	/** Implementation of the grow input bar settings handler.
	 *
	 * @param value True to grow with multiline text, false for single line.
	 */
	private void doSetGrowInputBar(final Boolean value) {
		mService.dispatchGrowInputBar(value);
	}
	
	/** Impelemntation of the show regex warning handler.
	 * 
	 * @param value New value to use.
	 */
	private void doSetRegexWarning(final Boolean value) {
		mService.dispatchShowRegexWarning(value);
	}
	

	/** Impelemntation of the system encoding settings handler.
	 * 
	 * @param value New value to use.
	 */
	private void doUpdateEncoding(final String value) {
		if (mProcessor == null) { return; }
		mProcessor.setEncoding(value);
		//this.encoding = value;
		mSettings.setEncoding(value);
		this.mWorking.setEncoding(value);
		this.mFinished.setEncoding(value);
		if (mProcessor != null) {
			this.mProcessor.setEncoding(value);
		}
		for (int i = 0; i < mWindows.size(); i++) {
			WindowToken w = mWindows.get(i);
			w.getBuffer().setEncoding(value);
		}
		
		for (IWindowCallback w : mWindowCallbackMap.values()) {
			//IWindowCallback w = mWindowCallbacks.getBroadcastItem(i);
			try {
				w.setEncoding(value);
			} catch (RemoteException e) {
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("Connection.doUpdateEncoding", e);
			}
		}
		
		for (int i = 0; i < mPlugins.size(); i++) {
			Plugin p = mPlugins.get(i);
			p.setEncoding(value);
		}
		
		//handle the keyboard command callback.
		mKeyboardCommand.setEncoding(value); 
		
		//may want to go through and activate the settings changed handler for plugins.
		//the chat window would want to re-construct it's buffers. But for proper operation
		//it may not be out of the question to make encoding change requrie a restart.
		//everything that doesn't use TextTree's directly to make multi-buffers, will work fine.		
	}

	/** Apply configured terminal width/height to NAWS (server-side map sizing).
	 * Prefer last live UI measurement when available. */
	private void applyTerminalNaws() {
		if (mProcessor == null) {
			return;
		}
		if (mLiveCols > 0 && mLiveRows > 0) {
			mProcessor.setDisplayDimensions(mLiveRows, mLiveCols);
			if (mIsConnected) {
				mProcessor.disaptchNawsString();
			}
			return;
		}
		if (mSettings == null || mSettings.getSettings() == null) {
			return;
		}
		int cols = 0;
		int rows = 0;
		try {
			BaseOption w = (BaseOption) mSettings.getSettings().getOptions().findOptionByKey("terminal_width");
			BaseOption h = (BaseOption) mSettings.getSettings().getOptions().findOptionByKey("terminal_height");
			if (w != null && w.getValue() instanceof Integer) {
				cols = (Integer) w.getValue();
			}
			if (h != null && h.getValue() instanceof Integer) {
				rows = (Integer) h.getValue();
			}
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"Connection.applyTerminalNaws", e);
		}
		if (cols <= 0 && rows <= 0) {
			return;
		}
		int useCols = cols > 0 ? cols : 40;
		int useRows = rows > 0 ? rows : 20;
		if (useCols < 20) {
			useCols = 20;
		}
		if (useCols > 200) {
			useCols = 200;
		}
		if (useRows < 5) {
			useRows = 5;
		}
		if (useRows > MAX_NAWS_ROWS) {
			useRows = MAX_NAWS_ROWS;
		}
		mProcessor.setDisplayDimensions(useRows, useCols);
		if (mIsConnected) {
			mProcessor.disaptchNawsString();
		}
	}

	/**
	 * Live window size from the UI. Never report more columns/rows than the screen
	 * can show. Rows are capped — absurd heights (100+) have dropped Eden links.
	 * Columns follow the real screen so ANSI maps match the draw grid.
	 */
	/** Soft ceiling for NAWS rows (tall phones exceed the old hard 24). */
	private static final int MAX_NAWS_ROWS = 100;
	private int mLiveCols = 0;
	private int mLiveRows = 0;
	public final void applyLiveDisplayDimensions(final int rows, final int cols) {
		if (rows < 1 || cols < 1) {
			return;
		}
		int cfgCols = 0;
		int cfgRows = 0;
		try {
			if (mSettings != null && mSettings.getSettings() != null) {
				BaseOption w = (BaseOption) mSettings.getSettings().getOptions().findOptionByKey("terminal_width");
				BaseOption h = (BaseOption) mSettings.getSettings().getOptions().findOptionByKey("terminal_height");
				if (w != null && w.getValue() instanceof Integer) {
					cfgCols = (Integer) w.getValue();
				}
				if (h != null && h.getValue() instanceof Integer) {
					cfgRows = (Integer) h.getValue();
				}
			}
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"Connection.applyLiveDisplayDimensions", e);
		}
		int useCols = cols;
		int useRows = rows;
		// Tiny fixed heights (e.g. corrupt terminal_height=5) make MUDs look "frozen".
		if (cfgRows > 0 && cfgRows < 10) {
			Log.w("BlowTorch", "Ignoring corrupt terminal_height=" + cfgRows + " (using screen)");
			try {
				BaseOption h = (BaseOption) mSettings.getSettings().getOptions().findOptionByKey("terminal_height");
				if (h != null) {
					h.setValue(0);
					mHandler.obtainMessage(MESSAGE_SAVESETTINGS, "").sendToTarget();
				}
			} catch (Exception e) {
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
						"Connection.resetCorruptTerminalHeight", e);
			}
			cfgRows = 0;
		}
		if (cfgCols > 0) {
			useCols = Math.min(cfgCols, cols);
		}
		if (cfgRows > 0) {
			useRows = Math.min(cfgRows, rows);
		}
		// Match real screen columns so ANSI maps are not pre-wrapped for a different width.
		// Only cap rows — absurd heights (100+) have been observed to drop Eden links.
		if (useCols < 20) {
			useCols = Math.max(20, Math.min(cols, 40));
		}
		if (useCols > 200) {
			useCols = 200;
		}
		if (useRows > MAX_NAWS_ROWS) {
			useRows = MAX_NAWS_ROWS;
		}
		if (useRows < 5) {
			useRows = Math.max(5, Math.min(rows, MAX_NAWS_ROWS));
		}
		mLiveCols = useCols;
		mLiveRows = useRows;
		Log.i("BlowTorch", "NAWS live " + useCols + "x" + useRows
				+ " (screen " + cols + "x" + rows
				+ ", cfg " + cfgCols + "x" + cfgRows + ")");
		if (mProcessor == null) {
			return;
		}
		final boolean changed = (useCols != mLastSentNawsCols) || (useRows != mLastSentNawsRows);
		mProcessor.setDisplayDimensions(useRows, useCols);
		if (mIsConnected && changed) {
			// Debounce: layout/IME fires this many times per second; NAWS floods freeze Eden.
			if (mHandler != null) {
				mHandler.removeMessages(MESSAGE_SEND_NAWS);
				mHandler.sendEmptyMessageDelayed(MESSAGE_SEND_NAWS, 350);
			} else {
				mProcessor.disaptchNawsString();
				mLastSentNawsCols = useCols;
				mLastSentNawsRows = useRows;
			}
		}
	}

	private int mLastSentNawsCols = -1;
	private int mLastSentNawsRows = -1;
	private static final int MESSAGE_SEND_NAWS = 8842;

	/** One-time tip for new profiles: set NAWS width/height for ANSI maps. */
	private static final String PREFS_NAWS_TIP = "NAWS_SIZE_TIP";

	private String nawsTipPrefsKey() {
		String display = (mDisplay != null && mDisplay.length() > 0) ? mDisplay : "default";
		return "naws_size_tip_done_" + display;
	}

	private void markNawsTipDone() {
		try {
			if (mService == null) {
				return;
			}
			mService.getSharedPreferences(PREFS_NAWS_TIP, Context.MODE_PRIVATE)
					.edit()
					.putBoolean(nawsTipPrefsKey(), true)
					.apply();
		} catch (Exception ignored) {
		}
	}

	private boolean isNawsTipDone() {
		try {
			if (mService == null) {
				return false;
			}
			return mService.getSharedPreferences(PREFS_NAWS_TIP, Context.MODE_PRIVATE)
					.getBoolean(nawsTipPrefsKey(), false);
		} catch (Exception e) {
			return false;
		}
	}

	private void maybeShowTerminalSizeHint() {
		if (mSettings == null || mSettings.getSettings() == null) {
			return;
		}
		try {
			BaseOption hint = (BaseOption) mSettings.getSettings().getOptions().findOptionByKey("terminal_size_hint");
			if (hint == null || !(hint.getValue() instanceof Boolean) || !((Boolean) hint.getValue())) {
				return;
			}
			if (isNawsTipDone()) {
				// Prefs already consumed — keep option off and persist.
				if (((Boolean) hint.getValue()).booleanValue()) {
					hint.setValue(false);
					mHandler.obtainMessage(MESSAGE_SAVESETTINGS, "").sendToTarget();
				}
				return;
			}
			// Mark consumed first — Connection.dispatchDialog is for network errors and
			// kills the socket + schedules a 20s reconnect when auto_reconnect is on.
			markNawsTipDone();
			hint.setValue(false);
			mHandler.obtainMessage(MESSAGE_SAVESETTINGS, "").sendToTarget();
			mService.dispatchToast(
					"Tip: Terminal Width/Height 0 = match screen (best for ANSI maps).",
					true);
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"Connection.maybeShowTerminalSizeHint", e);
		}
	}
	private enum KEYS {
		/** Semicolon processing. */
		process_semicolon,
		/** Debug telnet. */
		debug_telnet,
		/** System encoding. */
		encoding, 
		/** Window orientation. */
		orientation, 
		/** Keep screen on. */
		screen_on, 
		/** Hide notification bar. */
		fullscreen, 
		/** Use fullscreen editor. */
		fullscreen_editor,
		/** Make editor use suggestions. */
		use_suggestions,
		/** Master switch for floating button copies over the game. */
		floating_buttons_enabled,
		/** Keep last entered. */
		keep_last,
		/** Complete words the world just used. */
		word_complete,
		/** How many recent lines the completer counts as fresh. */
		word_complete_lines,
		/** Forgive typos when the exact prefix finds nothing. */
		word_complete_loose,
		word_complete_phrases,
		/** Put the plain word before the whole name built on it. */
		word_complete_short_first,
		/** Order every suggestion by length rather than by what was said last. */
		word_complete_shorter_first,
		/** Keep device.* session variables up to date from the phone itself. */
		device_state_variables,
		/** Let movement readings fire while the display is asleep. */
		sensor_screen_off,
		/** Let movement readings fire while the app is in the background. */
		sensor_background,
		/** Draw the rest of the top suggestion after the caret. */
		word_complete_ghost,
		/** How many suggestions the ghost lists, growing the bar to fit them. */
		word_complete_ghost_lines,
		/** How many suggestions the completer offers at once. */
		word_complete_show,
		/** Keep the floating suggestion bar up even when it is empty. */
		word_complete_persist,
		/** Let the caret's place in the line reorder the suggestions. */
		word_complete_rank,
		/** Let what usually follows a verb lead, after that verb. */
		word_complete_pairs,
		/** Which audio stream a trigger's sound action plays on. */
		trigger_sound_stream,
		/** Warn when that stream is turned all the way down. */
		trigger_sound_warn_silent,
		/** How solid the menu a tapped word opens is. */
		tap_menu_opacity,
		/** Where the chips go: floating, in a strip below the game, or nowhere. */
		word_complete_where,
		/** Triggers that speak keep quiet while a command is being composed. */
		speak_quiet_typing,
		/** How solid those chips are. */
		word_complete_opacity,
		/** Prompt on its own bar above the input line. */
		prompt_bar,
		/** Grow input bar with multiline text. */
		grow_input_bar,
		/** Soften first letter of sent commands for case-sensitive MUDs. */
		lowercase_command_start,
		/** Input compatibility mode. */
		compatibility_mode,
		/** Local echo. */
		local_echo,
		/** Process period commands. */
		process_system_commands,
		/** Echo alias updates. */
		echo_alias_updates,
		/** Keep wifi alive. */
		keep_wifi_alive,
		/** Cull extraneous color codes. */
		cull_extraneous_color,
		/** Debug telnet data. */
		debug_telent,
		/** Bell vibrates. */
		bell_vibrate,
		/** Bell notifies. */
		bell_notification,
		/** Bell toasts. */
		bell_display, 
		/** Auto reconnect. */
		auto_reconnect, 
		/** Auto reconnect limit. */
		auto_reconnect_limit,
		/** Patient reconnect through brief network loss / VPN flaps. */
		persistent_connection,
		/** How solid the gameplay ⋮ is drawn (percent). */
		overflow_button_opacity,
		/** Draw the disc behind the gameplay ⋮. */
		overflow_button_background,
		/** Draw the ring around the gameplay ⋮. */
		overflow_button_border,
		/** Use GMCP. */
		use_gmcp, 
		/** GMCP Supports string. */
		gmcp_supports,
		/** Log GMCP packets to file. */
		log_gmcp,
		/** Echo GMCP packets into the game window. */
		gmcp_feed,
		/** Toast when an unseen module arrives (opt-in). */
		gmcp_suggest_modules,
		/** Where a mudstd.frame picture is drawn: 0 its own window, 1 the game text. */
		frame_image_placement,
		/** How many lines of game text a picture takes when drawn there. */
		frame_image_lines,
		/** Use Mud Client Protocol (#$#). */
		use_mcp,
		/** MCP packages string for negotiate. */
		mcp_packages,
		/** Log MCP packets. */
		log_mcp,
		/** Echo MCP into game window. */
		mcp_feed,
		/** Hide #$# lines from output. */
		mcp_omit_output,
		/** Auto send mcp-negotiate-can after handshake. */
		mcp_auto_negotiate,
		/** Announce MTTS capabilities in TTYPE. */
		use_mtts,
		/** Negotiate MSDP (option 69). */
		use_msdp,
		/** Negotiate MSSP (option 70). */
		use_mssp,
		/** Negotiate MCCP2 compression (option 86). */
		use_mccp,
		/** Negotiate MXP (option 91). */
		use_mxp,
		/** Log MXP handshake/replies. */
		log_mxp,
		/** Echo MXP events into the game window. */
		mxp_feed,
		/** Show Regex Warning. */
		show_regex_warning,
		/** Append game output to session .txt log. */
		session_log,
		/** Custom session log directory (blank = /BlowTorch/session_logs). */
		session_log_directory,
		/** Default import/export settings directory. */
		default_settings_directory,
		/** NAWS columns reported to the server. */
		terminal_width,
		/** NAWS rows reported to the server. */
		terminal_height,
		/** Show one-time terminal size tip on connect. */
		terminal_size_hint,
		/** Mapper module enable. */
		mapper_enabled,
		mapper_recording_default,
		mapper_follow,
		mapper_float,
		mapper_opacity,
		mapper_path_auto_send,
		mapper_echo_window,
		mapper_use_gmcp,
		mapper_gmcp_use_num,
		mapper_gmcp_use_coords,
		mapper_gmcp_create_exits,
		mapper_gmcp_grow,
		mapper_gmcp_policy,
		mapper_auto_reverse_link,
		mapper_accept_one_way_specials,
		mapper_toolbar_actions,
		mapper_capture_title_regex,
		mapper_capture_exits_regex,
		mapper_level_up_commands,
		mapper_level_down_commands,
		mapper_move_effects,
		/** Extra text windows master switch. */
		extra_text_windows_enabled,
		/** Extra text windows JSON slot list. */
		extra_text_windows,
		/** Overlay gauges master switch. */
		gauge_widgets_enabled,
		/** Overlay gauges JSON list. */
		gauge_widgets
	}
	
	/** Work horse function of sending data to the server, this initiates all levels of processing.
	 * 
	 * @param bytes Input to process.
	 */
	private void sendToServer(final byte[] bytes) {
		if (bytes == null || mSettings == null) {
			return;
		}
		Data d = null;
		try {
			d = processOutputData(new String(bytes, mSettings.getEncoding()));
		} catch (UnsupportedEncodingException e2) {
			reportRuntimeError("outbound encoding", e2);
			return;
		} catch (Exception e2) {
			reportRuntimeError("alias / special command", e2);
			return;
		}
		
		if (d == null) {
			return;
		}
		
		if (d.mCmdString.equals("") && (d.mVisString != null && d.mVisString.replaceAll("\\s", "").equals(""))) {
			return;
		}
		
		String nosemidata = null;
		try {
			
			if (d.mCmdString != null && !d.mCmdString.equals("")) {
				nosemidata = d.mCmdString;
				if (mMcpEngine != null && mMcpEngine.isUse()) {
					nosemidata = mMcpEngine.quoteOutboundInBand(nosemidata);
				}
				byte[] sendtest = nosemidata.getBytes(mSettings.getEncoding());
				ByteBuffer buf = ByteBuffer.allocate(sendtest.length * 2); //just in case EVERY byte is the IAC
				int count = 0;
				for (int i = 0; i < sendtest.length; i++) {
					if (sendtest[i] == TC.IAC) {
						buf.put(TC.IAC);
						buf.put(TC.IAC);
						count += 2;
					} else {
						buf.put(sendtest[i]);
						count++;
					}
				}
				
				byte[] tosend = new byte[count];
				buf.rewind();
				buf.get(tosend, 0, count);
				
				if (mPump != null && mPump.isConnected()) {
					mPump.sendData(tosend);
				} else if (isOfflineMode()) {
					// Offer the line to the tutorial's practice world first. It
					// answers through sendBytesToWindow, which is the same path
					// real server output takes — so the player's own triggers
					// fire on it, the mapper follows it, and colouring applies.
					// That is the whole point: a lesson about triggers should be
					// a trigger going off, not a description of one.
					if (!offerToOfflineWorld(nosemidata)) {
						sendBytesToWindow(new String(Colorizer.getBrightYellowColor()
								+ "\n[offline tutorial — not sent to a MUD]\n"
								+ Colorizer.getWhiteColor()).getBytes("UTF-8"));
					}
				} else {
					sendBytesToWindow(new String(Colorizer.getRedColor() + "\nDisconnected.\n" + Colorizer.getWhiteColor()).getBytes("UTF-8"));
				}
				// Record / follow movement for mapper (after aliases; never sends).
				if (mMapper != null && nosemidata != null) {
					mMapper.onPlayerCommand(nosemidata);
				}
			} else {
				if (d.mCmdString.equals("") && d.mVisString == null) {
					// Pressing Enter on an empty line sends a bare CRLF. Unlike
					// the branch above, this one never checked for a pump, so
					// doing it with no connection — the offline tutorial, or
					// after a disconnect — threw a NullPointerException out of
					// sendToServer and printed "outbound command" at the player.
					// The blank line is still echoed either way.
					if (mPump != null && mPump.isConnected()) {
						mPump.sendData(mCRLF.getBytes(mSettings.getEncoding()));
					}
					if (AliasLocalEcho.shouldDisplay(mSettings.isLocalEcho(), mLocalEcho,
							AliasLocalEcho.INHERIT)) {
						d.mVisString = "\n";
					} else {
						d.mVisString = "";
					}
				}
			}
			if (d.mVisString != null && !d.mVisString.equals("")) {
				// Per-segment filtering already applied global local_echo and
				// per-alias FORCE_ON/OFF while building mVisString. Telnet ECHO
				// (password masking) remains absolute here — FORCE_ON must not
				// put a password in the scrollback or session log.
				if (mLocalEcho) {
					mWindows.get(0).getBuffer().addBytesImplSimple(d.mVisString.getBytes(mSettings.getEncoding()));
					sendBytesToWindow(d.mVisString.getBytes(mSettings.getEncoding()));
				}
			}
		} catch (IOException e) {
			mHandler.sendEmptyMessage(MESSAGE_DISCONNECTED);
		}
	}

	/** Possibly Deprecated. Sets the buffer size for a target window in a target plugin.
	 * 
	 * @param plugin The target plugin.
	 * @param window The target window.
	 * @param amount The new buffer size value.
	 */
	public final void updateWindowBufferMaxValue(final String plugin, final String window, final int amount) {
		for (WindowToken w : mWindows) {
			if (w.getName().equals(window)) {
				//WindowToken w = mWindows.get(0);
				w.setBufferSize(amount);
			}
		} 
	}
	
	/** The main starting point for the save settings routine. This is called for a few different locations. */
	public final void saveMainSettings() {
		mSettingsIO.saveMainSettings();
	}

	/** One line about the kept copy of this connection's settings, for .settings.
	 *
	 * @return what exists on disk and when it was last written.
	 */
	public final String describeSettingsBackup() {
		return mSettingsIO.describeMainSettingsBackup();
	}

	/** Put the kept copy of the settings back and reload from it.
	 *
	 * @return a message for the player saying what happened.
	 */
	public final String restoreSettingsBackup() {
		String message = mSettingsIO.restoreMainSettingsBackup();
		if (message != null && message.startsWith("Restored")) {
			// Restoring the file is only half of it — everything live was built from
			// the copy we just replaced.
			reloadSettings();
		}
		return message;
	}
	
	/** Export settings routine. Called from either the main settings save routine or the export settings dialog.
	 * 
	 * @param path Absolute filesystem path, or a bare file name (resolved under the default settings directory).
	 */
	public final void exportSettings(final String path) {
		mSettingsIO.exportSettings(path);
	}

	
	/** Access point for the foreground window to initate a custom export action with the provided path.
	 * 
	 * @param path Path to save settings to, this must be absolute from the root directory (?)
	 */
	public final void startExportSequence(final String path) {
		mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_EXPORTFILE, path));
	}
	
	
	

	
	/** Attatches a WindowSettingsChangedListener to the given WindowToken.
	 * 
	 * @param w The window to attatch a new settings changed listener to.
	 */
	public final void attatchWindowSettingsChangedListener(final WindowToken w) {
		w.getSettings().setListener(new WindowSettingsChangedListener(w.getName()));
	}

	/** Target for the foreground window to check if the keep last setting is set.
	 * 
	 * @return value of the keep list settings.
	 */
	public final boolean isKeepLast() {
		return (Boolean) ((BooleanOption) mSettings.getSettings().getOptions().findOptionByKey("keep_last")).getValue();
	}

	/** Target for the foreground window to check if the full screen settings is set.
	 * 
	 * @return the value of the full screen option.
	 */
	public final boolean isFullScren() {
		return (Boolean) ((BooleanOption) mSettings.getSettings().getOptions().findOptionByKey("fullscreen")).getValue();
	}

	/** Getter for mHost.
	 * 
	 * @return mHost;
	 */
	public final String getHostName() {
		return mHost;
	}
	
	/** Getter for this connection's port value.
	 * 
	 * @return the port number this connection is using.
	 */
	public final int getPort() {
		return mPort;
	}
	
	
	/** Starts the recursive settings initialization routine to set all the settings loaded from the serialized settings file. */
	private void initSettings() {
		// The replay hands every option back to updateSetting() as if the player had
		// just changed it. Anything that treats "player turned this on" as a decision
		// — see doSetUseMCCP — has to be able to tell the two apart.
		mReplayingSettings.set(Boolean.TRUE);
		try {
			initSetting(mSettings.getSettings().getOptions());
		} finally {
			mReplayingSettings.set(Boolean.FALSE);
		}
	}

	/** True while this thread is replaying the profile into updateSetting().
	 ** Thread-scoped, not an instance flag: doStartup() also runs on binder threads
	 ** (ConnectionBinderFacade.doReconnect), so an instance flag would misread a
	 ** genuine player toggle arriving during a replay as part of that replay. */
	private final ThreadLocal<Boolean> mReplayingSettings = new ThreadLocal<Boolean>() {
		@Override
		protected Boolean initialValue() {
			return Boolean.FALSE;
		}
	};
	
	/** Recursive settings initializations routine. 
	 * 
	 * @param s the SettingsGroup to dump.
	 */
	private void initSetting(final SettingsGroup s) {
		for (Option o : s.getOptions()) {
			if (o instanceof SettingsGroup) {
				initSetting((SettingsGroup) o);
			} else {
				BaseOption tmp = (BaseOption) o;
				this.updateSetting(o.getKey(), tmp.getValue().toString());
			}
		}
	}

	/** Entry point for the foreground window to reset the settings for this connection. */
	public final void resetSettings() {
		this.mHandler.sendEmptyMessage(MESSAGE_DORESETSETTINGS);
	}
	
	/** Work horse routine that actually resets the settings. */
	public final void doResetSettings() {
		for (IWindowCallback c : mWindowCallbackMap.values()) {
			try {
				c.shutdown();
			} catch (RemoteException e) {
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("Connection.doResetSettings", e);
			}
		}
		mService.markWindowsDirty();
		mSettingsIO.importSettings(null, true, true);
	}

	/** Entry point for the foreground window to import a custom settings file at the given location.
	 * 
	 * @param path Path of the settings to load.
	 */
	public final void startLoadSettingsSequence(final String path) {
		mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_IMPORTFILE, path));
	}
	
	/** Work horse for the foreground window to add an external plugin and reload the settings.
	 * 
	 * @param path The location of the external settings file.
	 */
	public final void doAddLink(final String path) {
		mSettings.getLinks().add(path);
		saveMainSettings();
		reloadSettings();
	}
	
	/** Entry point for the foreground window to add an external plugin and reload the settings.
	 * 
	 * @param path The location of the external settings file.
	 */
	public final void addLink(final String path) {
		mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_ADDLINK, path));
	}

	/** Work horse routine for removing a plugin. 
	 * 
	 * @param plugin The name of the plugin to remove, or a relative link path for a failed/orphan link.
	 */
	private void doDeletePlugin(final String plugin) {
		Plugin p = mPluginMap.remove(plugin);

		// Orphan / failed link: settings still reference the file but nothing loaded into mPluginMap.
		if (p == null) {
			if (removeLinkReference(plugin)) {
				saveMainSettings();
			}
			return;
		}
		
		String remove = null;
		if (p.getStorageType().equals("EXTERNAL")) {
			for (String path : mSettings.getLinks()) {
				if (p.getFullPath() != null && p.getFullPath().contains(path)) {
					remove = path;
				}
			}
		}
		if (remove != null) { 
			mSettings.getLinks().remove(remove);
			mLinkMap.remove(remove);
			mFailedLinks.remove(remove);
		}
		
		mPlugins.remove(p);
		saveMainSettings();
		reloadSettings();
	}

	/** Remove a settings link by exact path or by matching a loaded-plugin name / basename. */
	private boolean removeLinkReference(final String key) {
		if (key == null) {
			return false;
		}
		if (mSettings.getLinks().remove(key)) {
			mLinkMap.remove(key);
			mFailedLinks.remove(key);
			return true;
		}
		// Match failed-link display keys and short names against relative paths.
		String remove = null;
		for (String path : mSettings.getLinks()) {
			if (path.equals(key) || path.endsWith("/" + key) || path.endsWith("/" + key + ".xml")) {
				remove = path;
				break;
			}
			String base = path;
			int slash = base.lastIndexOf('/');
			if (slash >= 0) {
				base = base.substring(slash + 1);
			}
			if (base.equalsIgnoreCase(key) || base.equalsIgnoreCase(key + ".xml")) {
				remove = path;
				break;
			}
		}
		if (remove != null) {
			mSettings.getLinks().remove(remove);
			mLinkMap.remove(remove);
			mFailedLinks.remove(remove);
			return true;
		}
		return false;
	}

	/**
	 * Resolve an external plugin link against classic /BlowTorch and app external-files roots.
	 * Prefer an existing file when multiple candidates exist.
	 */
	private File resolveExternalPluginFile(final String link) {
		File classic = new File(Environment.getExternalStorageDirectory(), "BlowTorch/" + link);
		if (classic.exists()) {
			return classic;
		}
		File appRoot = mService.getApplicationContext().getExternalFilesDir(null);
		if (appRoot != null) {
			File appFile = new File(appRoot, link);
			if (appFile.exists()) {
				return appFile;
			}
			File appBlowTorch = new File(appRoot, "BlowTorch/" + link);
			if (appBlowTorch.exists()) {
				return appBlowTorch;
			}
		}
		return classic;
	}
	
	/** Entry poit routine for removing a plugin.
	 * 
	 * @param plugin The name of the plugin to remove.
	 */
	/**
	 * Plugins the client cannot work without.
	 *
	 * <p>The button pad, the settings root and the tutorial are shipped with the
	 * app rather than installed by the player, and deleting one leaves a client
	 * with no buttons and no way to get them back. The plugin list offers a trash
	 * icon on every row, so the guard belongs here, where every route to deletion
	 * passes, rather than in one dialog.
	 */
	private static final java.util.Set<String> UNDELETABLE_PLUGINS =
			new java.util.HashSet<String>(java.util.Arrays.asList(
					"button_window", "starter_tutorial", "connection_settings"));

	/**
	 * True for a plugin that ships with the app rather than one the player
	 * installed.
	 *
	 * <p>Public because the plugin list in the UI process needs the same answer,
	 * and two hand-kept copies of this list would drift. It is an immutable
	 * constant, so the usual warning about {@code static} existing twice does not
	 * bite: both processes compute the same answer from the same literals.
	 */
	public static boolean isBuiltInPlugin(final String plugin) {
		return plugin != null && UNDELETABLE_PLUGINS.contains(
				plugin.trim().toLowerCase(java.util.Locale.US));
	}

	/**
	 * @param plugin Name of the plugin to unload and forget.
	 * @return true when the deletion was accepted; false for a built-in one.
	 */
	public final boolean deletePlugin(final String plugin) {
		if (isBuiltInPlugin(plugin)) {
			mService.dispatchToast("\"" + plugin + "\" ships with BlowTorch and cannot be"
					+ " deleted. Disable it instead.", true);
			return false;
		}
		mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_DELETEPLUGIN, plugin));
		return true;
	}

	/** Sets a plugin enabled (or disabled). Persists via {@code enabled="false"} on
	 * the plugin element in settings XML. Does not unload the plugin from the list.
	 *
	 * @param plugin Name of the plugin to affect.
	 * @param enabled Desired state of the plugin.
	 * @return {@code true} if applied; {@code false} if refused or missing.
	 */
	public final boolean setPluginEnabled(final String plugin, final boolean enabled) {
		if (plugin == null || plugin.length() == 0) {
			return false;
		}
		// button_window owns the on-screen pad — never allow disable.
		if (!enabled && "button_window".equals(plugin)) {
			mService.dispatchToast(
					"Cannot disable button_window — it provides the on-screen buttons.",
					true);
			return false;
		}
		Plugin p = mPluginMap.get(plugin);
		if (p == null) {
			return false;
		}
		p.setEnabled(enabled);
		if (p.getSettings() != null) {
			p.getSettings().setDirty(true);
		}
		if (enabled) {
			p.buildAliases();
		}
		saveMainSettings();
		setTriggersDirty();
		buildTriggerSystem();
		loadMcpTriggers();
		return true;
	}

	/** Whether a loaded plugin is currently enabled. */
	public final boolean isPluginEnabled(final String plugin) {
		Plugin p = mPluginMap.get(plugin);
		return p != null && p.isEnabled();
	}

	/** Gets the direction data from the main settings plugin.
	 * 
	 * @return The direction map.
	 */
	public final HashMap<String, DirectionData> getDirectionData() {
		return mSettings.getDirections();
	}

	/** Sets the directio data for the main settings plugin. This is supplied from the foreground window.
	 * 
	 * @param data Diretion data wad from to use for the main settings wad.
	 */
	public final void setDirectionData(final HashMap<String, DirectionData> data) {
		mSettings.setDirections(data);
		mSpeedwalkCommand.setDirections(data);
	}

	/** Getter for the title bar height. 
	 * 
	 * @return the title bar height.
	 */
	public final int getTitleBarHeight() {
		return mTitleBarHeight;
	}

	/** Getter for the status bar height. 
	 * 
	 * @return the status bar height.
	 */
	public final int getStatusBarHeight() {
		return mStatusBarHeight;
	}

	/** Utility method to test to see if a link has been loaded.
	 * 
	 * @param link The path of the link to test. Relative to the BlowTorch sd card root.
	 * @return The state of the target plugin. true = loaded, false = unloaded.
	 */
	public final boolean isLinkLoaded(final String link) {
		String foo = Environment.getExternalStorageDirectory() + "/BlowTorch/";
		String bar = link.replace(foo, "");
		File appDir = mService.getApplicationContext().getExternalFilesDir(null);
		if (appDir != null) {
			String appRoot = appDir.getAbsolutePath();
			if (!appRoot.endsWith("/")) {
				appRoot = appRoot + "/";
			}
			if (bar.startsWith(appRoot)) {
				bar = bar.substring(appRoot.length());
			}
			String appBt = appRoot + "BlowTorch/";
			if (bar.startsWith(appBt)) {
				bar = bar.substring(appBt.length());
			}
		}
		
		boolean ret = mLinkMap.containsKey(bar);
		return ret;
	}

	/** Kicks off the loadInternalSettings() routine. */
	public final void initWindows() {
		mSettingsIO.loadInternalSettings();
	}

	/** Immediatly shuts down this connection and all associated data structures. */
	public final void shutdown() {
		this.saveMainSettings();
		// Does not go through doDisconnect, so it needs its own: this is the
		// other way a session ends for good.
		this.flushMapperSaves();
		this.killNetThreads(true);
		for (Plugin p : mPlugins) {
			p.shutdown();
			p = null;
		}
		mSettings.shutdown();
		mSettings = null;
		mHandler.removeMessages(MESSAGE_RECONNECT);
		mHandler = null;
		mService.removeConnectionNotification(mDisplay);
	}

	/** Gets the path for a plugin.
	 * 
	 * @param plugin Name of the plugin to interrogate.
	 * @return The full path of the plugin.
	 */
	public final String getPluginPath(final String plugin) {
		// A row in the plugin list can name a link whose file is gone, and asking
		// that row where it lives is exactly what a player does next. This used to
		// dereference null and take the binder call down with it.
		Plugin p = mPluginMap.get(plugin);
		return p == null ? null : p.getFullPath();
	}

	/** Entry point for Plugins to send data to the foreground window without trigger parsing.
	 * 
	 * @param str The string to send.
	 */
	public final void dispatchLuaText(final String str) {
		mHandler.sendMessage(mHandler.obtainMessage(Connection.MESSAGE_LUANOTE, str));
	}

	/** Calls an anonymous global function in the target plugin. Does not provide an arugment.
	 * 
	 * @param plugin Name of the target plugin.
	 * @param function Name of the function to call.
	 */
	public final void callPluginFunction(final String plugin, final String function) {
		Plugin p = mPluginMap.get(plugin);
		if (p != null) {
			if (!p.isEnabled()) {
				this.dispatchLuaText("\n" + Colorizer.getRedColor() + "Plugin disabled: " + plugin
						+ Colorizer.getWhiteColor() + "\n");
				return;
			}
			p.callFunction(function);
		} else {
			this.dispatchLuaText("\n" + Colorizer.getRedColor() + "No plugin named: " + plugin + Colorizer.getRedColor() + "\n");
		}
	}

	@Override
	public final SettingsChangedListener getSettingsListener() {
		return (SettingsChangedListener) this;
	}

	@Override
	public final void callPlugin(final String plugin, final String function, final String data) {
		Message m = mHandler.obtainMessage(MESSAGE_CALLPLUGIN);
		m.getData().putString("PLUGIN", plugin);
		m.getData().putString("FUNCTION", function);
		m.getData().putString("DATA", data);
		mHandler.sendMessage(m);
	}

	@Override
	public final boolean pluginSupports(final String plugin, final String function) {
		Plugin p = mPluginMap.get(plugin);
		if (p != null) {
			return p.checkPluginSupports(function);
		}
		return false;
	}

	@Override
	public final java.util.Map getMcpStatusCache() {
		ensureMcpEngine();
		return mMcpEngine.getStatusCache();
	}

	@Override
	public final void sendMcpPacket(final String payload) {
		if (payload == null || payload.length() == 0) {
			return;
		}
		ensureMcpEngine();
		if (mHandler != null) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					mMcpEngine.sendFromCommand(payload.trim());
				}
			});
		} else {
			mMcpEngine.sendFromCommand(payload.trim());
		}
	}

	/** Test to see of a plugin is installed. 
	 * 
	 * @param desired Name of the desired plugin.
	 * @return is it loaded or not.
	 */
	public final boolean isPluginInstalled(final String desired) {
		return mPluginMap.containsKey(desired);
	}
	
	/** Utility method for putting up a generic toast message.
	 * 
	 * @param str The string to use for the toast message.
	 */
	void toast(final String str) {
		Context c = this.getContext();
		Toast t = Toast.makeText(c, str, Toast.LENGTH_SHORT);
		float density = c.getResources().getDisplayMetrics().density;
		t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, (int) (TOAST_MESSAGE_TOP_OFFSET * density));
		t.show();
	}
	
	/** Getter for mHandler.
	 * 
	 * @return The handler associated with this connection.
	 */
	public final Handler getHandler() {
		return mHandler;
	}
	
	/** Getter for the plugin list.
	 * 
	 * @return The plugin list in loaded order.
	 */
	public final ArrayList<Plugin> getPlugins() {
		return mPlugins;
	}

	/**
	 * Links that remain in settings but failed to load into {@link #mPlugins}.
	 * Key is the relative link path; value is a short failure reason.
	 */
	public final HashMap<String, String> getFailedLinks() {
		return mFailedLinks;
	}
	
	/** Getter for mPump.
	 * 
	 * @return the data pump for this connection.
	 */
	public final DataPumper getPump() {
		return mPump;
	}
	
	/** Getter for mProcessor.
	 * 
	 * @return the processor associated with this connection.
	 */
	public final Processor getProcessor() {
		return mProcessor;
	}

	/** Per-connection mapper engine. */
	public final MapperController getMapper() {
		return mMapper;
	}

	/** JSON snapshot for the UI process (Connection runs in :stellar). */
	public final String getMapperSnapshotJson() {
		if (mMapper == null || mMapper.getMap() == null) {
			return "";
		}
		try {
			org.json.JSONObject root = MapStore.toJson(mMapper.getMap());
			root.put("recording", mMapper.isRecording());
			root.put("follow", mMapper.isFollowPlayer());
			root.put("preferFloat", mMapper.isPreferFloat());
			root.put("opacity", mMapper.getOpacity());
			root.put("editMode", mMapper.isEditMode());
			root.put("acceptOneWaySpecials", mMapper.isAcceptOneWaySpecials());
			root.put("useGmcp", mMapper.isUseGmcp());
			root.put("gmcpGrow", mMapper.isGmcpGrow());
			root.put("echoWindow", mMapper.isEchoWindow());
			root.put("gmcpPolicy", mMapper.getGmcpPolicy() != null
					? mMapper.getGmcpPolicy() : "sync");
			MapperController.PendingGmcpConflict pending =
					mMapper.getPendingGmcpConflict();
			if (pending != null) {
				org.json.JSONObject pc = new org.json.JSONObject();
				pc.put("tileId", pending.tileId != null ? pending.tileId : "");
				pc.put("kind", pending.kind != null ? pending.kind : "");
				pc.put("message", pending.message != null ? pending.message : "");
				if (pending.proposedTitle != null) {
					pc.put("proposedTitle", pending.proposedTitle);
				}
				if (pending.proposedX != null) {
					pc.put("proposedX", pending.proposedX.intValue());
				}
				if (pending.proposedY != null) {
					pc.put("proposedY", pending.proposedY.intValue());
				}
				if (pending.proposedLevelId != null) {
					pc.put("proposedLevelId", pending.proposedLevelId);
				}
				root.put("pendingGmcpConflict", pc);
			}
			root.put("toolbar", mMapper.getToolbarActions() != null
					? mMapper.getToolbarActions() : "");
			root.put("moveEffects", mMapper.getCombinedMoveEffectsDisplay());
			return root.toString();
		} catch (Exception e) {
			Log.w("BlowTorch", "getMapperSnapshotJson failed", e);
			return "";
		}
	}

	/** Ask the UI process to show/hide/refresh the mapper overlay. */
	public final void requestMapperUi(final int action) {
		if (mService != null) {
			mService.notifyMapperUi(action);
		}
	}

	/**
	 * Ask the UI process to sync extra text overlays after slot list / enable changes.
	 * Action is typically {@link #MESSAGE_EXTRA_TEXT_CHANGED}.
	 */
	public final void requestExtraTextUi() {
		requestExtraTextUi(MESSAGE_EXTRA_TEXT_CHANGED);
	}

	/** Queue a settings save on the connection handler, if one is running. */
	void requestSettingsSave() {
		if (mHandler != null) {
			mHandler.obtainMessage(MESSAGE_SAVESETTINGS, "").sendToTarget();
		}
	}

	/** Ask the UI process to sync extra text overlays with an explicit action code. */
	public final void requestExtraTextUi(final int action) {
		if (mService != null) {
			mService.notifyExtraTextUi(action);
		}
	}

	/**
	 * Frame events waiting for the UI process to collect them.
	 *
	 * <p>Written on the connection handler thread and drained on a binder
	 * thread, so it is synchronized on itself and nothing else touches it.
	 */
	private final ArrayList<FrameEvent> mPendingFrameEvents = new ArrayList<FrameEvent>();

	/**
	 * How many events we will hold for a UI that is not collecting them.
	 *
	 * <p>The UI is told about every batch, so a queue this long means nobody is
	 * listening — the activity is gone, or it never bound. Growing without a
	 * bound in that case would turn a server that pushes map images into a slow
	 * leak in the service process, which outlives the window.
	 */
	private static final int MAX_PENDING_FRAME_EVENTS = 64;

	/** Queue a batch from {@link Processor} and prod the UI. */
	private void queueFrameEvents(final String json) {
		ArrayList<FrameEvent> batch = FrameEvent.parse(json);
		if (batch.isEmpty()) {
			return;
		}
		synchronized (mPendingFrameEvents) {
			mPendingFrameEvents.addAll(batch);
			while (mPendingFrameEvents.size() > MAX_PENDING_FRAME_EVENTS) {
				// Oldest first: a stale open matters less than the image that
				// was meant to fill it.
				mPendingFrameEvents.remove(0);
			}
		}
		if (mService != null) {
			mService.notifyFrameUi(MESSAGE_FRAME_EVENT);
		}
	}

	/**
	 * Hand the queued frame events to the UI process and forget them.
	 *
	 * @return JSON for {@link FrameEvent#parse}; never null.
	 */
	public final String takeFrameEvents() {
		ArrayList<FrameEvent> batch;
		synchronized (mPendingFrameEvents) {
			if (mPendingFrameEvents.isEmpty()) {
				return "[]";
			}
			batch = new ArrayList<FrameEvent>(mPendingFrameEvents);
			mPendingFrameEvents.clear();
		}
		return FrameEvent.toJson(batch);
	}

	/**
	 * The player closed a frame's window.
	 *
	 * <p>Same path as {@code .frame close <id>} — see
	 * {@link Processor#closeFrameByUser} — so the server sees the one event the
	 * specification defines for this, whichever way the player did it.
	 */
	public final boolean closeFrameByUser(final String id) {
		Processor p = getProcessor();
		if (p == null) {
			return false;
		}
		return p.closeFrameByUser(id);
	}

	/** The frame's window measured itself — see {@link Processor#reportFrameSize}. */
	public final void reportFrameSize(final String id, final int widthPx, final int heightPx) {
		Processor p = getProcessor();
		if (p != null) {
			p.reportFrameSize(id, widthPx, heightPx);
		}
	}

	/**
	 * Every frame currently open, as an {@code open} event each.
	 *
	 * <p>The UI activity can be destroyed and rebuilt (a rotation, a return from
	 * the launcher) while the service and its connection carry on. Without this
	 * the rebuilt UI would show nothing until the server happened to send
	 * another frame, and the player would have lost a window the server still
	 * believes is on screen.
	 */
	public final String getOpenFramesJson() {
		Processor p = getProcessor();
		if (p == null) {
			return "[]";
		}
		return FrameEvent.toJson(p.describeOpenFrames());
	}

	/** Snapshot of configured extra text slots (never null; may be empty). */
	public final ArrayList<ExtraTextSlot> getExtraTextSlots() {
		return mExtraText.getSlots();
	}

	/** Persisted gauge widget JSON. Never null. */
	public final String getGaugeWidgetsJson() {
		if (mGauges == null) {
			return "[]";
		}
		return mGauges.toPersistedJson();
	}

	/** Live gauge values JSON. Never null. */
	public final String getGaugeWidgetValuesJson() {
		if (mGauges == null) {
			return "[]";
		}
		return mGauges.toValuesJson();
	}

	/** Apply a parsed {@code .widget} result. Sets {@code result.error} on failure. */
	public final String applyGaugeWidget(final WidgetCommandParser.Result result) {
		if (mGauges == null) {
			if (result != null) {
				result.error = "Widgets are not ready.";
			}
			return null;
		}
		return mGauges.apply(result);
	}

	/** One line per widget for {@code .widget list}. Never null. */
	public final String formatGaugeWidgetList() {
		if (mGauges == null) {
			return "  (none — use .widget add <id> [shape])\n";
		}
		return mGauges.listDump();
	}

	public final boolean areGaugeWidgetsEnabled() {
		return mGauges != null && mGauges.isEnabled();
	}

	final void requestGaugeWidgetUi() {
		if (mService != null) {
			mService.notifyGaugeWidgetUi(MESSAGE_GAUGE_WIDGET_CHANGED);
		}
	}

	final void requestGaugeWidgetValues() {
		if (mService == null || mGauges == null) {
			return;
		}
		mService.notifyGaugeWidgetValues(mDisplay, mGauges.toValuesJson());
	}

	@Override
	public void notifyGaugeSessionVar(final String name, final String value) {
		if (mHandler == null) {
			if (mGauges != null) {
				mGauges.onSessionVar(name, value);
			}
			return;
		}
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mGauges != null) {
					mGauges.onSessionVar(name, value);
				}
			}
		});
	}

	/** Whether extra text overlays are enabled (default true). */
	public final boolean isExtraTextWindowsEnabled() {
		return mExtraText.isEnabled();
	}

	/**
	 * Ensure each configured slot has a {@link WindowToken} in {@link #mWindows}
	 * (buffer + default window options; no LayoutGroup). Reloads slots from the
	 * {@code extra_text_windows} setting first. Notifies UI.
	 */
	public final void ensureExtraTextSlots() {
		mExtraText.ensureSlots(true);
	}

	/**
	 * Same as {@link #ensureExtraTextSlots()} with optional UI notify
	 * (skip during {@link #loadPlugins} — {@code reloadWindows} follows).
	 */
	public final void ensureExtraTextSlots(final boolean notify) {
		mExtraText.ensureSlots(notify);
	}

	/**
	 * Find a slot by name (normalized). Returns a copy, or null.
	 */
	public final ExtraTextSlot findExtraTextSlot(final String name) {
		return mExtraText.find(name);
	}

	/**
	 * Insert or update a slot by name. Validates name / max 8; persists JSON;
	 * ensures WindowToken; notifies UI.
	 *
	 * @return true if accepted
	 */
	public final boolean upsertExtraTextSlot(final ExtraTextSlot slot) {
		return mExtraText.upsert(slot);
	}

	/**
	 * Remove a slot by name. Persists, drops matching WindowToken if present, notifies UI.
	 *
	 * @return true if a slot was removed
	 */
	public final boolean removeExtraTextSlot(final String name) {
		return mExtraText.remove(name);
	}

	/** Optional string payload for {@link #requestMapperUi} (e.g. zoom action). */
	private volatile String mMapperUiArg;

	public final void setMapperUiArg(final String arg) {
		mMapperUiArg = arg;
	}

	public final String takeMapperUiArg() {
		String a = mMapperUiArg;
		mMapperUiArg = null;
		return a;
	}

	/** Ask UI to run a mapper action with a string arg (5 = zoom). */
	public final void requestMapperUiArg(final int action, final String arg) {
		setMapperUiArg(arg);
		requestMapperUi(action);
	}
	
	/** Getter for the display name.
	 * 
	 * @return the launcher display name for this connection.
	 */
	public final String getDisplay() {
		return mDisplay;
	}
	
	/** Getter for the host name.
	 * 
	 * @return the host name this connection uses.
	 */
	/**
	 * Whether this connection is (or will be) encrypted.
	 *
	 * <p>Exists so that every Intent carrying HOST can carry this beside it.
	 * With two worlds open, one TLS and one plain, an Intent that named the
	 * host but left this to a stored preference would hand the second world the
	 * first world's answer.
	 *
	 * @return True when the socket uses TLS.
	 */
	public final boolean isUseTls() {
		return mUseTls;
	}

	public final String getHost() {
		return mHost;
	}
	
	/** getter for mIsConnected.
	 * 
	 * @return the connected state of this connection.
	 */
	public final boolean isConnected() {
		return mIsConnected;
	}

	/** Marks the end of the current connection interval and records duration. */
	public final void markConnectionEnded() {
		if (mConnectedAtElapsed > 0L && mIsConnected) {
			mLastDurationMs = SystemClock.elapsedRealtime() - mConnectedAtElapsed;
			mService.noteConnectionEnded(mDisplay, mLastDurationMs);
		}
		mIsConnected = false;
		mSessionLog.onDisconnected();
	}

	public final long getConnectedAtElapsed() {
		return mConnectedAtElapsed;
	}

	public final long getLastDurationMs() {
		return mLastDurationMs;
	}

	/** Current uptime if connected, else last completed duration (may be 0). */
	public final long getDisplayDurationMs() {
		if (mIsConnected && mConnectedAtElapsed > 0L) {
			return SystemClock.elapsedRealtime() - mConnectedAtElapsed;
		}
		return mLastDurationMs;
	}

	public final String getDurationLabel() {
		long ms = getDisplayDurationMs();
		if (ms <= 0L && !mIsConnected) {
			return "";
		}
		return ConnectionDuration.formatElapsed(ms);
	}
	
	/** Getter for mService. This is really ugly and should be fixed immediatly.
	 * 
	 * @return the service that initated this connection.
	 */
	public final StellarService getService() {
		return mService;
	}



	public String getPluginOptionValue(String plugin, String key) {
		Plugin p = mPluginMap.get(plugin);
		if(p == null) return "Plugin " + plugin + " does not exist.";
		return p.getOptionValue(key);
	}

	/**
	 * Replace slot list, write JSON setting, ensure tokens, optionally save.
	 * Used by overlay geometry persist and Options / Lua.
	 */
	public final void replaceExtraTextSlots(final java.util.List<ExtraTextSlot> slots,
			final boolean save) {
		mExtraText.replaceAll(slots, save);
	}
	
}

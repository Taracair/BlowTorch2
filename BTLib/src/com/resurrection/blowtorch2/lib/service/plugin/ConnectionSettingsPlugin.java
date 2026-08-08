package com.resurrection.blowtorch2.lib.service.plugin;

import java.util.ArrayList;
import java.util.HashMap;

import org.keplerproject.luajava.LuaException;
import org.xmlpull.v1.XmlSerializer;


import android.os.Handler;

import com.resurrection.blowtorch2.lib.mapper.MapDirections;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.ConnectionPluginCallback;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.CallbackOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.EncodingOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.IntegerOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.ListOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginSettings;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsGroup;
import com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption;
import com.resurrection.blowtorch2.lib.settings.HyperSettings;
import com.resurrection.blowtorch2.lib.speedwalk.DirectionData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

public class ConnectionSettingsPlugin extends Plugin {
	/** Extra text options; nested under Window in {@code buildSettingsPage}. */
	private SettingsGroup mExtraTextOptions;

	public ConnectionSettingsPlugin(Handler h,ConnectionPluginCallback parent,String dataDir) throws LuaException {
		super(h,parent,null,dataDir);
		init();
	}
	
	public ConnectionSettingsPlugin(PluginSettings settings,Handler h,ConnectionPluginCallback parent,String dataDir) throws LuaException {
		super(settings,h,parent,null,dataDir);
		init();
	}
	
	private void init() {
		SettingsGroup sg = new SettingsGroup();
		sg.setTitle("Program Settings");
		sg.setListener(parent.getSettingsListener());

		SettingsGroup display = new SettingsGroup();
		display.setTitle("Display");
		display.setDescription("Orientation, fullscreen, and terminal size reported to the server (NAWS).");
		display.setKey("display_group");

		ListOption orientation = new ListOption();
		orientation.setTitle("Orientation");
		orientation.setDescription("Sets the layout mode for the application. Automatic will switch the layout when the device rotates.");
		orientation.setKey("orientation");
		orientation.setValue(new Integer(0));
		orientation.addItem("Automatic");
		orientation.addItem("Landscape");
		orientation.addItem("Portrait");
		display.addOption(orientation);
		
		BooleanOption screen_on = new BooleanOption();
		screen_on.setTitle("Keep Screen On?");
		screen_on.setDescription("Keep the screen on while the window is active.");
		screen_on.setKey("screen_on");
		screen_on.setValue(true);
		display.addOption(screen_on);
		
		BooleanOption fullscreen = new BooleanOption();
		fullscreen.setTitle("Use Fullscreen Window?");
		fullscreen.setDescription("Hides the notification bar. This can be toggled by typing .togglefullscreen");
		fullscreen.setKey("fullscreen");
		fullscreen.setValue(true);
		display.addOption(fullscreen);

		IntegerOption terminalWidth = new IntegerOption();
		terminalWidth.setTitle("Terminal Width (NAWS)");
		terminalWidth.setDescription("Columns reported to the server. 0 = match screen (recommended on phones). If set higher than the real width, the screen width is used so ANSI maps do not wrap.");
		terminalWidth.setKey("terminal_width");
		terminalWidth.setValue(0);
		display.addOption(terminalWidth);

		IntegerOption terminalHeight = new IntegerOption();
		terminalHeight.setTitle("Terminal Height (NAWS)");
		terminalHeight.setDescription("Rows reported to the server. 0 = match screen (recommended).");
		terminalHeight.setKey("terminal_height");
		terminalHeight.setValue(0);
		display.addOption(terminalHeight);

		BooleanOption terminalHint = new BooleanOption();
		terminalHint.setTitle("Show Terminal Size Tip?");
		terminalHint.setDescription("One-shot toast on connect: Width/Height 0 matches the screen. Off by default; turn on only if you want the reminder once.");
		terminalHint.setKey("terminal_size_hint");
		terminalHint.setValue(false);
		display.addOption(terminalHint);

		sg.addOption(display);
		
		SettingsGroup input = new SettingsGroup();
		input.setTitle("Input");
		input.setDescription("Options that deal with the input box and editors.");
		
		BooleanOption fullscreen_editor = new BooleanOption();
		fullscreen_editor.setTitle("Allow fullscreen keyboard editor");
		fullscreen_editor.setDescription("On some older keyboards, opens a full-screen typing view instead of the strip above the keys. Many modern keyboards ignore this — if nothing changes when you toggle it, yours does not support it.");
		fullscreen_editor.setKey("fullscreen_editor");
		fullscreen_editor.setValue(false);
		input.addOption(fullscreen_editor);
		
		BooleanOption use_suggestions = new BooleanOption();
		use_suggestions.setTitle("Keyboard word suggestions");
		use_suggestions.setDescription("Ask the soft keyboard to show autocomplete and spelling suggestions in the input field. Off is usually better for MUD commands. SwiftKey, Gboard, and similar keyboards may still show their own prediction row — that is controlled by the keyboard app, not BlowTorch.");
		use_suggestions.setKey("use_suggestions");
		use_suggestions.setValue(false);
		input.addOption(use_suggestions);

		BooleanOption floating_buttons_enabled = new BooleanOption();
		floating_buttons_enabled.setTitle("Floating buttons over the game");
		floating_buttons_enabled.setDescription("Show floating copies of buttons marked \"Float over the game\" in the button editor. Turn off to hide them all without editing each button.");
		floating_buttons_enabled.setKey("floating_buttons_enabled");
		floating_buttons_enabled.setValue(true);
		input.addOption(floating_buttons_enabled);
		
		BooleanOption keep_last = new BooleanOption();
		keep_last.setTitle("Keep last command after send");
		keep_last.setDescription("After you send, leave that line in the input bar and select it so you can edit or resend. Off clears the bar. Typing replaces the kept line.");
		keep_last.setKey("keep_last");
		keep_last.setValue(false);
		input.addOption(keep_last);

		BooleanOption grow_input_bar = new BooleanOption();
		grow_input_bar.setTitle("Grow Input Bar?");
		grow_input_bar.setDescription("When on, the input bar grows with multiline text. When off, input stays a single non-growing line. Toggle with .wrap on/off.");
		grow_input_bar.setKey("grow_input_bar");
		grow_input_bar.setValue(true);
		input.addOption(grow_input_bar);
		
		BooleanOption compatilibility_mode = new BooleanOption();
		compatilibility_mode.setTitle("Standard keyboard input (IME fix)");
		compatilibility_mode.setDescription("Use Android's normal input connection. Turn on if backspace is wrong or typing appends instead of replacing selected text. Keep last command turns this on automatically.");
		compatilibility_mode.setKey("compatibility_mode");
		compatilibility_mode.setValue(false);
		input.addOption(compatilibility_mode);

		// Seven keys about one feature made Input a wall to scroll through, so
		// they live in their own section. Safe because SettingsGroup's
		// updateOptionsMap recurses into child GROUPs and flattens their keys
		// into the parent's map — findOptionByKey is a flat lookup and does not
		// recurse, so MainWindow's group.findOptionByKey("word_complete_*")
		// still resolves. What that costs is an ordering rule: every option must
		// be added to this group BEFORE the group is added to input, or its key
		// never reaches input's map and the option silently stops being read.
		SettingsGroup suggestions = new SettingsGroup();
		suggestions.setTitle("Suggestions");
		suggestions.setDescription("Completing words the game has just used, and where those suggestions are shown.");

		BooleanOption word_complete = new BooleanOption();
		word_complete.setTitle("Suggest game words");
		word_complete.setDescription("Type two letters of a name the world just used and it appears; tap to take it. The keyboard cannot know these names and corrects them into English. Master switch: off, nothing below does anything. .suggest on/off");
		word_complete.setKey("word_complete");
		word_complete.setValue(false);
		suggestions.addOption(word_complete);

		IntegerOption word_complete_lines = new IntegerOption();
		word_complete_lines.setTitle("Remember (lines)");
		word_complete_lines.setDescription("How many recent lines count as fresh, 0–5000. Lower means roughly what is still on screen; 0 means the whole session. .suggest lines N");
		word_complete_lines.setKey("word_complete_lines");
		word_complete_lines.setValue(
				com.resurrection.blowtorch2.lib.window.WordSuggestions.DEFAULT_MAX_LINES);
		suggestions.addOption(word_complete_lines);

		BooleanOption word_complete_loose = new BooleanOption();
		word_complete_loose.setTitle("Forgive typos");
		word_complete_loose.setDescription("When the exact spelling finds nothing, take your letters in order with gaps: grzld finds grizzled. Only after an exact match found nothing, so typing accurately never gets a different answer. .suggest loose on/off");
		word_complete_loose.setKey("word_complete_loose");
		word_complete_loose.setValue(false);
		suggestions.addOption(word_complete_loose);

		BooleanOption word_complete_phrases = new BooleanOption();
		word_complete_phrases.setTitle("Offer whole names");
		word_complete_phrases.setDescription("Offer the words that followed too, up to three: after a grizzled cave troll walks in, typing gri offers \"grizzled cave troll\" above plain \"grizzled\". Off, you get single words only, which is what this has always done. .suggest phrases on/off");
		word_complete_phrases.setKey("word_complete_phrases");
		// Off by default: on, the top suggestion for a prefix stops being a word
		// and becomes a phrase, and the ghost draws it. Change this and the
		// comparison in ConnectionSetttingsParser together.
		word_complete_phrases.setValue(false);
		suggestions.addOption(word_complete_phrases);

		BooleanOption word_complete_ghost = new BooleanOption();
		word_complete_ghost.setTitle("Ghost after the cursor");
		word_complete_ghost.setDescription("Draw the top suggestion after the cursor in dim type; tap it to take it. Works on its own — with the bar set to Nowhere, this is all you get. Drawn only: you always send exactly what you typed. .suggest ghost on/off");
		word_complete_ghost.setKey("word_complete_ghost");
		word_complete_ghost.setValue(false);
		suggestions.addOption(word_complete_ghost);

		BooleanOption word_complete_short_first = new BooleanOption();
		word_complete_short_first.setTitle("Plain word before the whole name");
		word_complete_short_first.setDescription("With whole names on, offer explosive before explosive crates instead of the other way round. Four letters typed is not yet a request for the long form. Only ever changes a word against its own name — it does not order one word against another, which is what \"Shorter suggestions first\" does. Does nothing with whole names off. Off by default. .suggest plain on/off");
		word_complete_short_first.setKey("word_complete_short_first");
		word_complete_short_first.setValue(false);
		suggestions.addOption(word_complete_short_first);

		BooleanOption word_complete_shorter_first = new BooleanOption();
		word_complete_shorter_first.setTitle("Shorter suggestions first");
		word_complete_shorter_first.setDescription("Order every suggestion by length, shortest first, instead of by what the world said most recently. Type cr and you get crate before crime-and-punishment. \"Order by place in the line\" still decides which group of words leads; this decides the order inside each group, and nothing is ever dropped. Off by default. .suggest short on/off");
		word_complete_shorter_first.setKey("word_complete_shorter_first");
		// Off by default: newest-first is what the app has always done, and a
		// player who never opens this must keep it. Change this and
		// ConnectionSetttingsParser's comparison together, or the parser quietly
		// stops saving the value the player chose.
		word_complete_shorter_first.setValue(false);
		suggestions.addOption(word_complete_shorter_first);

		IntegerOption word_complete_ghost_lines = new IntegerOption();
		word_complete_ghost_lines.setTitle("Suggestions under the line");
		word_complete_ghost_lines.setDescription("How many rows the input bar may grow by to show the other suggestions, 1 to 6. At 1 it is just the single word drawn after the cursor, as before. Above that the rest are listed under what you are typing, side by side rather than one per line, each numbered and tappable. It takes only the rows it needs and gives them back the moment they are not needed. Needs the ghost to be on. .suggest ghostlines N");
		word_complete_ghost_lines.setKey("word_complete_ghost_lines");
		word_complete_ghost_lines.setValue(1);
		suggestions.addOption(word_complete_ghost_lines);

		ListOption word_complete_where = new ListOption();
		word_complete_where.setTitle("Bar of suggestions");
		word_complete_where.setDescription("One place, so picking one puts the other away. Floating: over the game text, on the input bar, with a grip to drag or fold it. Below the game: a strip in the layout, which takes height, so the text jumps unless you also keep it in place. Nowhere: no bar at all — the ghost above still works. .suggest where floating|bar|off");
		word_complete_where.setKey("word_complete_where");
		// Added in this order: the values are indices into this list, and they are
		// what lands in the profile. Anything inserted in the middle renames every
		// saved choice after it.
		word_complete_where.addItem("Floating over the game");
		word_complete_where.addItem("Below the game window");
		word_complete_where.addItem("Nowhere (ghost only)");
		// Floating by default. The strip below the game window takes height while
		// it shows, so the text jumps under the thumb on every letter. Change this
		// and ConnectionSetttingsParser's comparison together, or the parser
		// quietly stops saving the value the player chose.
		word_complete_where.setValue(
				com.resurrection.blowtorch2.lib.window.WordSuggestions.DEFAULT_WHERE);
		suggestions.addOption(word_complete_where);

		BooleanOption word_complete_rank = new BooleanOption();
		word_complete_rank.setTitle("Order by place in the line");
		word_complete_rank.setDescription("At the start of a line, lift the words you have used as commands; after it, lift the words you have used as targets. Learned from what you type, so it knows nothing on a world you have just started. It only changes the order — every suggestion you get today you still get. Off by default. .suggest rank on/off");
		word_complete_rank.setKey("word_complete_rank");
		word_complete_rank.setValue(false);
		suggestions.addOption(word_complete_rank);

		BooleanOption word_complete_pairs = new BooleanOption();
		word_complete_pairs.setTitle("Learn what goes with what");
		word_complete_pairs.setDescription("After a command word, offer what you have aimed that command at before: kill offers what you have killed, wear what you have worn. Needs Order by place in the line to be on, and knows nothing until you have played a while. It only changes the order. Off by default. .suggest pairs on/off");
		word_complete_pairs.setKey("word_complete_pairs");
		word_complete_pairs.setValue(false);
		suggestions.addOption(word_complete_pairs);

		BooleanOption word_complete_persist = new BooleanOption();
		word_complete_persist.setTitle("Keep the bar in place");
		word_complete_persist.setDescription("Leave the bar up even with nothing to suggest, instead of it coming and going as you type. Below the game this is the one that matters: it holds its height, so the game text stops jumping. Floating, it holds a bar's width and shows its grip. .suggest persist on/off");
		word_complete_persist.setKey("word_complete_persist");
		word_complete_persist.setValue(false);
		suggestions.addOption(word_complete_persist);

		IntegerOption word_complete_opacity = new IntegerOption();
		word_complete_opacity.setTitle("Chip opacity (%)");
		word_complete_opacity.setDescription("How solid the chips are, 10-100. Lower lets more game text through behind them; the words stay fully readable either way. Floating chips only. .suggest opacity N");
		word_complete_opacity.setKey("word_complete_opacity");
		word_complete_opacity.setValue(
				com.resurrection.blowtorch2.lib.window.WordSuggestions.DEFAULT_OPACITY);
		suggestions.addOption(word_complete_opacity);

		// After every addOption above, never before one of them.
		input.addOption(suggestions);

		BooleanOption speak_quiet_typing = new BooleanOption();
		speak_quiet_typing.setTitle("Quiet while you type");
		speak_quiet_typing.setDescription("Triggers that speak drop anything they would have said between the first letter of a command and sending it. Speech already under way is not cut short. Off — the default — they speak whenever they fire. Worth turning on if you write long lines while a chatty trigger reads the screen at you; leave it off if speech is an alert, because you type most in a fight and that is when it would go quiet.");
		speak_quiet_typing.setKey("speak_quiet_typing");
		// Off by default: speaking whenever a trigger fires is what the app did
		// before this existed, and a player who never opens this option must get
		// that. On it silences alerts during exactly the busiest moments, which
		// is not a thing to hand anybody without their asking. Change this and
		// ConnectionSetttingsParser's comparison together, or the parser quietly
		// stops saving the value the player chose.
		speak_quiet_typing.setValue(false);
		input.addOption(speak_quiet_typing);

		BooleanOption prompt_bar = new BooleanOption();
		prompt_bar.setTitle("Prompt on its own bar");
		prompt_bar.setDescription("A MUD prompt is the line the world never finishes — your health and mana line, resent after every command. On, it sits in one fixed place above the input bar instead of repeating down the game window. Worlds that send no prompt show nothing; .prompt says how many have been seen. Toggle with .prompt on/off.");
		prompt_bar.setKey("prompt_bar");
		prompt_bar.setValue(false);
		input.addOption(prompt_bar);

		IntegerOption input_history = new IntegerOption();
		input_history.setTitle("Input History Size");
		input_history.setDescription("How many previous commands to keep (per profile, 10–100).");
		input_history.setKey("input_history_size");
		input_history.setValue(75);
		input.addOption(input_history);
		
		sg.addOption(input);

		// The phone itself, as something triggers can read. Its own group
		// because it is not an input setting and not a display one, and because
		// this is where anything else sensor-shaped will go.
		SettingsGroup device = new SettingsGroup();
		device.setTitle("Device");
		device.setDescription("What the phone knows about itself, and what the game may do with it.");

		BooleanOption device_state_variables = new BooleanOption();
		device_state_variables.setTitle("Device state as variables");
		device_state_variables.setDescription("Keep device.headphones, device.charging, device.battery, device.screen and device.covered up to date as session variables, so a trigger or timer can be gated on them in its Conditions tab and Lua can read them with GetVariable. A name this phone cannot know is left unset, and a condition testing it is false rather than true. Nothing is registered while this is off. Off by default. .probe sensors state shows the current values");
		device_state_variables.setKey("device_state_variables");
		// Off by default: the app did nothing of the sort before this existed.
		// Change this and ConnectionSetttingsParser's comparison together, or
		// the parser quietly stops saving the value the player chose.
		device_state_variables.setValue(false);
		device.addOption(device_state_variables);

		CallbackOption device_gestures = new CallbackOption();
		device_gestures.setTitle("Gestures\u2026");
		device_gestures.setDescription("What your phone can feel — waving a hand over the screen, putting it face down, shaking it, the headphones coming out — and what each one should do. Tap to see the list, which says which ones this phone has the hardware for.");
		device_gestures.setKey("device_gestures");
		device.addOption(device_gestures);

		BooleanOption gesture_screen_off = new BooleanOption();
		gesture_screen_off.setTitle("Movement gestures with the screen off");
		gesture_screen_off.setDescription("Off by default, and off means a shake, a wave or the phone going face down does nothing while the display is asleep — so a phone jolted about in a pocket or a bag cannot send commands to the game. Turn it on if you want to shake your way out of a fight without waking the phone first. IMPORTANT: a gesture is not aimed at one world. It fires in EVERY world you have open, including ones running in the background, so with two MUDs connected one shake sends the command twice. The headphone, charger and screen events are not affected by this setting — muting speech when the jack comes out has to work with the screen off, which is the whole point of it.");
		gesture_screen_off.setKey("gesture_screen_off");
		gesture_screen_off.setValue(false);
		device.addOption(gesture_screen_off);

		BooleanOption gesture_background = new BooleanOption();
		gesture_background.setTitle("Movement gestures while the app is in the background");
		gesture_background.setDescription("Off by default: with BlowTorch swiped away into Recents or another app on top, a shake or a wave is almost certainly you doing something else with your phone, not playing. Turn it on to keep gestures live while you use another app. As above, this covers movement gestures only — the headphone, charger and screen events keep working — and a gesture reaches every open world, not only the one you were last looking at.");
		gesture_background.setKey("gesture_background");
		gesture_background.setValue(false);
		device.addOption(gesture_background);

		sg.addOption(device);

		
		
		SettingsGroup servOptions = new SettingsGroup();
		servOptions.setTitle("Service");
		servOptions.setDescription("Options for the background service and data processing.");

		EncodingOption enc = new EncodingOption();
		enc.setTitle("System Encoding");
		enc.setDescription("Specifies the encoding used to process incoming text.");
		enc.setKey("encoding");
		enc.setValue("UTF-8");
		servOptions.addOption(enc);
		
		BooleanOption session_log = new BooleanOption();
		session_log.setTitle("Log Session to File?");
		session_log.setDescription("Append incoming game text live to a .txt under /BlowTorch/session_logs/ (or custom folder). Flushes about every 0.75s / 4KB and on disconnect — not only when you quit. Requires All files access.");
		session_log.setKey("session_log");
		session_log.setValue(false);
		servOptions.addOption(session_log);

		StringOption session_log_directory = new StringOption();
		session_log_directory.setTitle("Session Log Directory");
		session_log_directory.setDescription("Leave blank for /BlowTorch/session_logs/. Browse… for SAF, or enter an absolute path.");
		session_log_directory.setKey("session_log_directory");
		session_log_directory.setValue("");
		servOptions.addOption(session_log_directory);
		
		BooleanOption local_echo = new BooleanOption();
		local_echo.setTitle("Local Echo?");
		local_echo.setDescription("Will the service echo data sent to the server?");
		local_echo.setKey("local_echo");
		local_echo.setValue(true);
		servOptions.addOption(local_echo);
		
		BooleanOption process_system_commands = new BooleanOption();
		process_system_commands.setTitle("Process System Commands?");
		process_system_commands.setDescription("Perform system functions for input beginning with the specified system command marker.");
		process_system_commands.setKey("process_system_commands");
		process_system_commands.setValue(true);
		servOptions.addOption(process_system_commands);
		
		BooleanOption echo_alias_updates = new BooleanOption();
		echo_alias_updates.setTitle("Echo Alias Updates?");
		echo_alias_updates.setDescription("Local echo system command updates to aliases.");
		echo_alias_updates.setKey("echo_alias_updates");
		echo_alias_updates.setValue(true);
		servOptions.addOption(echo_alias_updates);
		
		BooleanOption process_semi = new BooleanOption();
		process_semi.setTitle("Process Semicolons?");
		process_semi.setDescription("Semicolons will be replaces with a newline character.");
		process_semi.setKey("process_semicolon");
		process_semi.setValue(true);
		servOptions.addOption(process_semi);
		
		BooleanOption keep_wifi_alive = new BooleanOption();
		keep_wifi_alive.setTitle("Keep Wifi Alive?");
		keep_wifi_alive.setDescription("Attempt to keep WiFi radio active while connected.");
		keep_wifi_alive.setKey("keep_wifi_alive");
		keep_wifi_alive.setValue(true);
		servOptions.addOption(keep_wifi_alive);
		
		BooleanOption auto_reconnect = new BooleanOption();
		auto_reconnect.setTitle("Auto Reconnect?");
		auto_reconnect.setDescription("Automatically reconnect when disconnected.");
		auto_reconnect.setKey("auto_reconnect");
		auto_reconnect.setValue(true);
		servOptions.addOption(auto_reconnect);
		
		IntegerOption auto_reconnect_limit = new IntegerOption();
		auto_reconnect_limit.setTitle("Auto Reconnect Tries");
		auto_reconnect_limit.setDescription("Hard limit of how many times reconnection will be attempted.");
		auto_reconnect_limit.setKey("auto_reconnect_limit");
		auto_reconnect_limit.setValue(new Integer(5));
		servOptions.addOption(auto_reconnect_limit);
		
		//auto_reconnect,
		//auto_reconnect_limit,
		
		BooleanOption cull_extraneous = new BooleanOption();
		cull_extraneous.setTitle("Cull Extraneous Colors?");
		cull_extraneous.setDescription("Removes extraneous color codes.");
		cull_extraneous.setKey("cull_extraneous_color");
		cull_extraneous.setValue(true);
		servOptions.addOption(cull_extraneous);
		
		BooleanOption debug_telnet = new BooleanOption();
		debug_telnet.setTitle("Debug Telnet?");
		debug_telnet.setDescription("Shows data involving telnet option transactions in the window.");
		debug_telnet.setKey("debug_telnet");
		debug_telnet.setValue(false);
		servOptions.addOption(debug_telnet);
		
		BooleanOption show_regex_warning = new BooleanOption();
		show_regex_warning.setTitle("Regular Expression Warning?");
		show_regex_warning.setDescription("Show the warning message about regular expressions in the trigger editor.");
		show_regex_warning.setKey("show_regex_warning");
		show_regex_warning.setValue(true);
		servOptions.addOption(show_regex_warning);
		
		
		SettingsGroup gmcpOptions = new SettingsGroup();
		gmcpOptions.setTitle("GMCP Options");
		gmcpOptions.setDescription("Options for the GMCP out of band communication channel.");
		
		BooleanOption use_gmcp = new BooleanOption();
		use_gmcp.setTitle("Use GMCP?");
		use_gmcp.setDescription("Enable or disable GMCP (out-of-band telnet channel for structured game data). On by default for new profiles.");
		use_gmcp.setKey("use_gmcp");
		use_gmcp.setValue(true);
		gmcpOptions.addOption(use_gmcp);

		CallbackOption manage_gmcp = new CallbackOption();
		manage_gmcp.setTitle("Manage modules…");
		manage_gmcp.setDescription("Checkbox picker for Supports.Set. Built-in, seen this session, and catalog — nothing auto-enables from traffic.");
		manage_gmcp.setKey("manage_gmcp_modules");
		manage_gmcp.setValue("manage_gmcp_modules");
		gmcpOptions.addOption(manage_gmcp);
	
		StringOption gmcp_supports = new StringOption();
		gmcp_supports.setTitle("Supports String (advanced)");
		gmcp_supports.setDescription("Raw Core.Supports.Set list. Prefer Manage modules…. Example: \"Char 1\", \"Room 1\".");
		gmcp_supports.setKey("gmcp_supports");
		gmcp_supports.setValue("\"Char 1\", \"Room 1\", \"Core 1\", \"Char.Login 1\", \"Client.Media 1\"");
		gmcpOptions.addOption(gmcp_supports);

		BooleanOption log_gmcp = new BooleanOption();
		log_gmcp.setTitle("Log GMCP?");
		log_gmcp.setDescription("Write the GMCP handshake and every packet to logs/gmcp.log (its own file, so it cannot bury the crash log; also the session log if that is on). Use it to see exactly what a world sends. Also: .gmcp sniff on");
		log_gmcp.setKey("log_gmcp");
		log_gmcp.setValue(false);
		gmcpOptions.addOption(log_gmcp);

		BooleanOption gmcp_feed = new BooleanOption();
		gmcp_feed.setTitle("Show GMCP in game window?");
		gmcp_feed.setDescription("Live IN/OUT GMCP feed in the mud window (noisy). Off by default. Also: .gmcp feed on|off");
		gmcp_feed.setKey("gmcp_feed");
		gmcp_feed.setValue(false);
		gmcpOptions.addOption(gmcp_feed);

		BooleanOption gmcp_suggest = new BooleanOption();
		gmcp_suggest.setTitle("Suggest modules when seen?");
		gmcp_suggest.setDescription("Tell me when the server sends a package family I have not declared in Supports.Set, and once on connect if the server advertises a supports list of its own. Submodules of enabled parents (e.g. Char.Base under Char) do not trigger. Nothing is ever enabled for you. On by default; turn it off here.");
		gmcp_suggest.setKey("gmcp_suggest_modules");
		gmcp_suggest.setValue(true);
		gmcpOptions.addOption(gmcp_suggest);

		ListOption frame_images = new ListOption();
		frame_images.setTitle("Pictures the server sends");
		frame_images.setDescription("Where a mudstd.frame image frame is drawn. In a floating separate window it can be moved, resized and closed, and it stays put while the text scrolls. In the game text it scrolls away with the room it belongs to, which suits a map of where you are standing. Switching between the two takes effect at once, on frames already open. Needs mudstd.frame in Manage modules….");
		frame_images.setKey("frame_image_placement");
		frame_images.setValue(new Integer(0));
		frame_images.addItem("In a floating separate window");
		frame_images.addItem("In the game text");
		gmcpOptions.addOption(frame_images);

		IntegerOption frame_image_lines = new IntegerOption();
		frame_image_lines.setTitle("Picture height in the text (lines)");
		frame_image_lines.setDescription("How many lines of the game text a picture takes up when it is drawn there. The picture keeps its proportions inside that height. Only used when pictures go in the game text.");
		frame_image_lines.setKey("frame_image_lines");
		frame_image_lines.setValue(new Integer(12));
		gmcpOptions.addOption(frame_image_lines);

		SettingsGroup mcpOptions = new SettingsGroup();
		mcpOptions.setTitle("MCP Options");
		mcpOptions.setDescription("Mud Client Protocol (#$# in-band). Used by some MOOs — different from GMCP. Off by default.");

		BooleanOption use_mcp = new BooleanOption();
		use_mcp.setTitle("Use MCP?");
		use_mcp.setDescription("Enable MCP 2.1 handshake and package negotiation. Strip #$# lines from the game window when Omit is on. Off by default — reconnect or wait for server #$#mcp after enabling.");
		use_mcp.setKey("use_mcp");
		use_mcp.setValue(false);
		mcpOptions.addOption(use_mcp);

		CallbackOption manage_mcp = new CallbackOption();
		manage_mcp.setTitle("Manage packages…");
		manage_mcp.setDescription("Checkbox picker for mcp-negotiate-can packages. Built-in, seen this session, and catalog.");
		manage_mcp.setKey("manage_mcp_packages");
		manage_mcp.setValue("manage_mcp_packages");
		mcpOptions.addOption(manage_mcp);

		StringOption mcp_packages = new StringOption();
		mcp_packages.setTitle("Packages String (advanced)");
		mcp_packages.setDescription("Raw package list for negotiate. Prefer Manage packages…. Example: \"mcp-negotiate 1.0 2.0\", \"dns-org-hellmoo-status 1.0\".");
		mcp_packages.setKey("mcp_packages");
		mcp_packages.setValue(com.resurrection.blowtorch2.lib.service.McpPackageRegistry.DEFAULT_PACKAGES);
		mcpOptions.addOption(mcp_packages);

		BooleanOption log_mcp = new BooleanOption();
		log_mcp.setTitle("Log MCP?");
		log_mcp.setDescription("Write MCP handshake and packets to the app log (also session log if enabled). Also: .mcp sniff on");
		log_mcp.setKey("log_mcp");
		log_mcp.setValue(false);
		mcpOptions.addOption(log_mcp);

		BooleanOption mcp_feed = new BooleanOption();
		mcp_feed.setTitle("Show MCP in game window?");
		mcp_feed.setDescription("Live IN/OUT MCP feed in the mud window (noisy). Off by default. Also: .mcp feed on|off");
		mcp_feed.setKey("mcp_feed");
		mcp_feed.setValue(false);
		mcpOptions.addOption(mcp_feed);

		BooleanOption mcp_omit = new BooleanOption();
		mcp_omit.setTitle("Omit MCP lines from output?");
		mcp_omit.setDescription("Hide #$# out-of-band lines from the game window (recommended). Off = show raw MCP in the scrollback.");
		mcp_omit.setKey("mcp_omit_output");
		mcp_omit.setValue(true);
		mcpOptions.addOption(mcp_omit);

		BooleanOption mcp_auto_neg = new BooleanOption();
		mcp_auto_neg.setTitle("Auto-negotiate packages?");
		mcp_auto_neg.setDescription("After MCP handshake, automatically send mcp-negotiate-can for enabled packages. On by default.");
		mcp_auto_neg.setKey("mcp_auto_negotiate");
		mcp_auto_neg.setValue(true);
		mcpOptions.addOption(mcp_auto_neg);

		SettingsGroup protocolOptions = new SettingsGroup();
		protocolOptions.setTitle("MUD Protocols");
		protocolOptions.setDescription("Optional telnet capabilities next to GMCP. MTTS and MCCP are on by default; MSDP and MSSP are off — leave those disabled unless your MUD needs them.");
		protocolOptions.setKey("mud_protocols_group");

		BooleanOption use_mtts = new BooleanOption();
		use_mtts.setTitle("Use MTTS?");
		use_mtts.setDescription("When on, TTYPE announces ANSI+UTF-8+256 colors as MTTS 13. When off, still sends a standards-compliant MTTS cycle but only ANSI (MTTS 1). Reconnect after changing.");
		use_mtts.setKey("use_mtts");
		use_mtts.setValue(true);
		protocolOptions.addOption(use_mtts);

		BooleanOption use_msdp = new BooleanOption();
		use_msdp.setTitle("Use MSDP?");
		use_msdp.setDescription("MUD Server Data Protocol (option 69). Alternative out-of-band channel used by some MUDs (e.g. Aardwolf). Off by default. Corrupt packets are ignored.");
		use_msdp.setKey("use_msdp");
		use_msdp.setValue(false);
		protocolOptions.addOption(use_msdp);

		BooleanOption use_mssp = new BooleanOption();
		use_mssp.setTitle("Use MSSP?");
		use_mssp.setDescription("MUD Server Status Protocol (option 70). Server listing info (name, players, …). Off by default. Useful for diagnostics; .mssp dump");
		use_mssp.setKey("use_mssp");
		use_mssp.setValue(false);
		protocolOptions.addOption(use_mssp);

		BooleanOption use_mccp = new BooleanOption();
		use_mccp.setTitle("Use MCCP?");
		use_mccp.setDescription("MUD Client Compression Protocol v2 (option 86). On by default; saves bandwidth. If decompression fails the client turns it off and reconnects by itself. Reconnect after changing.");
		use_mccp.setKey("use_mccp");
		use_mccp.setValue(true);
		protocolOptions.addOption(use_mccp);

		CallbackOption battery_opt = new CallbackOption();
		battery_opt.setTitle("Battery optimization…");
		battery_opt.setDescription("Ask Android not to kill BlowTorch in the background (helps keep connections alive).");
		battery_opt.setKey("battery_optimization");
		battery_opt.setValue("battery_optimization");
		servOptions.addOption(battery_opt);
		
		servOptions.addOption(gmcpOptions);
		servOptions.addOption(mcpOptions);
		servOptions.addOption(protocolOptions);
		
		sg.addOption(servOptions);

		SettingsGroup mapperOptions = new SettingsGroup();
		mapperOptions.setTitle("Mapper");
		mapperOptions.setDescription("Built-in MUD map recorder, pathfinding, and overlay.");

		BooleanOption mapper_enabled = new BooleanOption();
		mapper_enabled.setTitle("Enable Mapper?");
		mapper_enabled.setDescription("Master switch for recording, GMCP room sync, and .map commands engine.");
		mapper_enabled.setKey("mapper_enabled");
		mapper_enabled.setValue(true);
		mapperOptions.addOption(mapper_enabled);

		BooleanOption mapper_recording_default = new BooleanOption();
		mapper_recording_default.setTitle("Record by Default?");
		mapper_recording_default.setDescription("Start recording movement when a session loads. Toggle live with .map record.");
		mapper_recording_default.setKey("mapper_recording_default");
		mapper_recording_default.setValue(false);
		mapperOptions.addOption(mapper_recording_default);

		BooleanOption mapper_follow = new BooleanOption();
		mapper_follow.setTitle("Follow Player?");
		mapper_follow.setDescription("Keep the map view centered on the current room when it changes.");
		mapper_follow.setKey("mapper_follow");
		mapper_follow.setValue(true);
		mapperOptions.addOption(mapper_follow);

		BooleanOption mapper_float = new BooleanOption();
		mapper_float.setTitle("Prefer Floating Window?");
		mapper_float.setDescription("Open the map as a floating overlay instead of fullscreen (tablets).");
		mapper_float.setKey("mapper_float");
		mapper_float.setValue(true);
		mapperOptions.addOption(mapper_float);

		IntegerOption mapper_opacity = new IntegerOption();
		mapper_opacity.setTitle("Overlay Opacity (40–100)");
		mapper_opacity.setDescription("Floating map opacity percent. Clamped to 40–100.");
		mapper_opacity.setKey("mapper_opacity");
		mapper_opacity.setValue(85);
		mapperOptions.addOption(mapper_opacity);

		BooleanOption mapper_path_auto_send = new BooleanOption();
		mapper_path_auto_send.setTitle("Auto-Send Path?");
		mapper_path_auto_send.setDescription("When using .map goto, send path commands to the MUD. Off = print path only.");
		mapper_path_auto_send.setKey("mapper_path_auto_send");
		mapper_path_auto_send.setValue(false);
		mapperOptions.addOption(mapper_path_auto_send);

		BooleanOption mapper_echo_window = new BooleanOption();
		mapper_echo_window.setTitle("Echo mapper status to game window?");
		mapper_echo_window.setDescription("When on, .map / overlay toggles print status lines into the scrollback. Off = keep feedback in the map overlay only (More → Window echo).");
		mapper_echo_window.setKey("mapper_echo_window");
		mapper_echo_window.setValue(true);
		mapperOptions.addOption(mapper_echo_window);

		BooleanOption mapper_use_gmcp = new BooleanOption();
		mapper_use_gmcp.setTitle("Use GMCP Room Sync?");
		mapper_use_gmcp.setDescription("Apply Room.* GMCP to the map (title, room num, coords, exits). Needs GMCP on + Room in Manage modules…. Prefer Configure Room Sync… below. Independent of Capture regex.");
		mapper_use_gmcp.setKey("mapper_use_gmcp");
		mapper_use_gmcp.setValue(true);
		mapperOptions.addOption(mapper_use_gmcp);

		CallbackOption mapper_gmcp_cfg = new CallbackOption();
		mapper_gmcp_cfg.setTitle("Configure Room Sync…");
		mapper_gmcp_cfg.setDescription("Sync policy (follow/sync/strict), room number matching, absolute coordinates, exit neighbors, and per-host layout presets.");
		mapper_gmcp_cfg.setKey("manage_mapper_gmcp");
		mapper_gmcp_cfg.setValue("manage_mapper_gmcp");
		mapperOptions.addOption(mapper_gmcp_cfg);

		StringOption mapper_gmcp_policy = new StringOption();
		mapper_gmcp_policy.setTitle("GMCP Sync Policy");
		mapper_gmcp_policy.setDescription("follow = jump only; sync = create/grow + prompt on title conflicts (default); strict = always overwrite unlocked titles. Also in Configure Room Sync… / More radial grow toggle.");
		mapper_gmcp_policy.setKey("mapper_gmcp_policy");
		mapper_gmcp_policy.setValue("sync");
		mapperOptions.addOption(mapper_gmcp_policy);

		BooleanOption mapper_gmcp_use_num = new BooleanOption();
		mapper_gmcp_use_num.setTitle("GMCP: Match by room number?");
		mapper_gmcp_use_num.setDescription("Use Room.Info num/id/vnum as stable tile identity (recommended). Also in Configure Room Sync….");
		mapper_gmcp_use_num.setKey("mapper_gmcp_use_num");
		mapper_gmcp_use_num.setValue(true);
		mapperOptions.addOption(mapper_gmcp_use_num);

		BooleanOption mapper_gmcp_use_coords = new BooleanOption();
		mapper_gmcp_use_coords.setTitle("GMCP: Use absolute coordinates?");
		mapper_gmcp_use_coords.setDescription("Place at coords/coord x,y only when adjacent (≤1 cell). Off (default) = grow beside previous room — better for sparse world coordinates. Also in Configure Room Sync….");
		mapper_gmcp_use_coords.setKey("mapper_gmcp_use_coords");
		mapper_gmcp_use_coords.setValue(false);
		mapperOptions.addOption(mapper_gmcp_use_coords);

		BooleanOption mapper_gmcp_grow = new BooleanOption();
		mapper_gmcp_grow.setTitle("GMCP: Auto-grow map?");
		mapper_gmcp_grow.setDescription("Derived from Sync Policy (off = follow). Create rooms/exits from Room.Info when on. Also in Configure Room Sync… / More radial.");
		mapper_gmcp_grow.setKey("mapper_gmcp_grow");
		mapper_gmcp_grow.setValue(true);
		mapperOptions.addOption(mapper_gmcp_grow);

		BooleanOption mapper_gmcp_create_exits = new BooleanOption();
		mapper_gmcp_create_exits.setTitle("GMCP: Create exit neighbors?");
		mapper_gmcp_create_exits.setDescription("Create/link missing exits from Room.Info (vnum stubs when given). Does not delete exits. Also in Configure Room Sync….");
		mapper_gmcp_create_exits.setKey("mapper_gmcp_create_exits");
		mapper_gmcp_create_exits.setValue(true);
		mapperOptions.addOption(mapper_gmcp_create_exits);

		BooleanOption mapper_auto_reverse = new BooleanOption();
		mapper_auto_reverse.setTitle("Auto Reverse Links?");
		mapper_auto_reverse.setDescription("When recording n/s/e/w (etc.), also create the opposite exit on the destination tile.");
		mapper_auto_reverse.setKey("mapper_auto_reverse_link");
		mapper_auto_reverse.setValue(true);
		mapperOptions.addOption(mapper_auto_reverse);

		BooleanOption mapper_one_way = new BooleanOption();
		mapper_one_way.setTitle("Accept One-Way Specials?");
		mapper_one_way.setDescription("When ON, recording out/enter/leave always places a new nearby tile. When OFF (default), if exactly one room already leads into Here, link the special back there (e.g. freezer out → hallway). Toggle also in map Edit radial (1-way specials).");
		mapper_one_way.setKey("mapper_accept_one_way_specials");
		mapper_one_way.setValue(false);
		mapperOptions.addOption(mapper_one_way);

		StringOption mapper_toolbar = new StringOption();
		mapper_toolbar.setTitle("Toolbar Actions (CSV)");
		mapper_toolbar.setDescription("Left-side map buttons (CSV): record,follow,level-,level+,find,undo,center,close,capture. Links, Paths/Pack, Draw, Here, Edit, Save are always added.");
		mapper_toolbar.setKey("mapper_toolbar_actions");
		mapper_toolbar.setValue("record,follow,level-,level+,find,undo,center,close");
		mapperOptions.addOption(mapper_toolbar);

		StringOption mapper_capture_title = new StringOption();
		mapper_capture_title.setTitle("Capture Title Regex");
		mapper_capture_title.setDescription("Regex for .map capture and the Capture dialog title field. Group 1 is used when present; otherwise the whole match. Default matches a capitalized line.");
		mapper_capture_title.setKey("mapper_capture_title_regex");
		mapper_capture_title.setValue("^([A-Z].*)$");
		mapperOptions.addOption(mapper_capture_title);

		StringOption mapper_capture_exits = new StringOption();
		mapper_capture_exits.setTitle("Capture Exits Regex");
		mapper_capture_exits.setDescription("Regex for .map capture and the Capture dialog exits field. Group 1 is used when present (e.g. text after Exits:). Case-insensitive by default.");
		mapper_capture_exits.setKey("mapper_capture_exits_regex");
		mapper_capture_exits.setValue("(?i)exits?:\\s*(.*)");
		mapperOptions.addOption(mapper_capture_exits);

		StringOption mapper_level_up = new StringOption();
		mapper_level_up.setTitle("Level-Up Commands (CSV)");
		mapper_level_up.setDescription("While recording, these moves create a higher floor (+1). Default: u,up,climb,ascend. Clear both Up and Down to never auto-create levels (place as special neighbors instead).");
		mapper_level_up.setKey("mapper_level_up_commands");
		mapper_level_up.setValue(MapDirections.DEFAULT_LEVEL_UP_COMMANDS);
		mapperOptions.addOption(mapper_level_up);

		StringOption mapper_level_down = new StringOption();
		mapper_level_down.setTitle("Level-Down Commands (CSV)");
		mapper_level_down.setDescription("While recording, these moves create a lower floor (−1). Default: d,down,descend. Example: put enter in Up and leave in Down for vertical portals. Also editable via map Edit → Moves.");
		mapper_level_down.setKey("mapper_level_down_commands");
		mapper_level_down.setValue(MapDirections.DEFAULT_LEVEL_DOWN_COMMANDS);
		mapperOptions.addOption(mapper_level_down);

		StringOption mapper_moves = new StringOption();
		mapper_moves.setTitle("Move Effects (advanced)");
		mapper_moves.setDescription("Raw table for power users. Prefer map overlay Edit → Moves (friendly list). Format: n=grid:0:-1;out=special. Levels also use Level-Up/Down CSV. Empty = built-in defaults.");
		mapper_moves.setKey("mapper_move_effects");
		mapper_moves.setValue(MapDirections.defaultMoveEffectsString());
		mapperOptions.addOption(mapper_moves);

		sg.addOption(mapperOptions);

		// Nested under Options → Window by ConnectionSettingsIO.buildSettingsPage().
		mExtraTextOptions = new SettingsGroup();
		mExtraTextOptions.setTitle("Extra text windows");
		mExtraTextOptions.setKey("extra_text_group");
		mExtraTextOptions.setDescription(
				"Top drawer or floating panes (chat, tells, combat). Overlay owns geometry; lines target the slot name.");

		BooleanOption extra_text_enabled = new BooleanOption();
		extra_text_enabled.setTitle("Enable Extra Text Windows?");
		extra_text_enabled.setDescription("Master switch for extra text overlays. Slot definitions are kept when off.");
		extra_text_enabled.setKey("extra_text_windows_enabled");
		extra_text_enabled.setValue(true);
		mExtraTextOptions.addOption(extra_text_enabled);

		CallbackOption manage_extra_text = new CallbackOption();
		manage_extra_text.setTitle("Manage windows…");
		manage_extra_text.setDescription(
				"Add, remove, or edit extra text windows (drawer_top / float, height, opacity, GMCP modules). "
				+ "GMCP routes need Use GMCP? enabled under Service → GMCP Options.");
		manage_extra_text.setKey("manage_extra_text_windows");
		manage_extra_text.setValue("manage_extra_text_windows");
		mExtraTextOptions.addOption(manage_extra_text);

		StringOption extra_text_windows = new StringOption();
		extra_text_windows.setTitle("Windows JSON");
		extra_text_windows.setDescription("Persisted slot list (JSON array). Prefer Manage windows…; edit raw JSON only if needed.");
		extra_text_windows.setKey("extra_text_windows");
		extra_text_windows.setValue("[]");
		mExtraTextOptions.addOption(extra_text_windows);

		// Register on Program Settings so XML load/save + findOptionByKey work before
		// buildSettingsPage nests this group under Window.
		sg.addOption(mExtraTextOptions);

		SettingsGroup miscOptions = new SettingsGroup();
		miscOptions.setTitle("Miscellaneous");
		miscOptions.setDescription("Storage paths, permissions, and other app-wide helpers.");

		StringOption default_settings_directory = new StringOption();
		default_settings_directory.setTitle("Default Settings Directory");
		default_settings_directory.setDescription("Default folder for Import/Export Settings. Leave blank for /BlowTorch/settings/. Browse… for SAF, or enter an absolute path.");
		default_settings_directory.setKey("default_settings_directory");
		default_settings_directory.setValue("");
		miscOptions.addOption(default_settings_directory);

		CallbackOption export_settings = new CallbackOption();
		export_settings.setTitle("Export Settings");
		export_settings.setDescription("Write this world's settings to a file you choose. Sits beside the storage settings it uses; a setup job rather than something you reach for mid-session.");
		export_settings.setKey("export_settings");
		export_settings.setValue("export_settings");
		miscOptions.addOption(export_settings);

		CallbackOption import_settings = new CallbackOption();
		import_settings.setTitle("Import Settings");
		import_settings.setDescription("Load settings from a file, replacing this world's current settings.");
		import_settings.setKey("import_settings");
		import_settings.setValue("import_settings");
		miscOptions.addOption(import_settings);

		CallbackOption reset_settings = new CallbackOption();
		reset_settings.setTitle("Reset Settings");
		reset_settings.setDescription("Throw away this world's settings and start from the defaults — every alias, trigger, timer and button. Asks first.");
		reset_settings.setKey("reset_settings");
		reset_settings.setValue("reset_settings");
		miscOptions.addOption(reset_settings);

		CallbackOption request_storage = new CallbackOption();
		request_storage.setTitle("Manage Storage Access");
		request_storage.setDescription("Grant All files access so BlowTorch can use /BlowTorch/ (settings, backups, launcher, session_logs, logs) outside Android/data. Shows the effective root path.");
		request_storage.setKey("request_storage_access");
		request_storage.setValue("request_storage_access");
		miscOptions.addOption(request_storage);

		IntegerOption overflow_opacity = new IntegerOption();
		IntegerOption tap_menu_opacity = new IntegerOption();
		tap_menu_opacity.setTitle("Tapped-word menu opacity (%)");
		tap_menu_opacity.setDescription("How solid the little menu is that opens when you tap a word with more than one action, 20-100. It opens on top of the text it is about, so lower lets more of the game through behind it. Only the backing fades — the commands stay fully readable either way. .tapmenu opacity N");
		tap_menu_opacity.setKey("tap_menu_opacity");
		tap_menu_opacity.setValue(
				com.resurrection.blowtorch2.lib.window.MainWindow.DEFAULT_TAP_MENU_OPACITY);
		miscOptions.addOption(tap_menu_opacity);

		overflow_opacity.setTitle("Overflow button opacity (%)");
		overflow_opacity.setDescription("How solid the ⋮ button in the bottom corner is drawn "
				+ "(" + OVERFLOW_OPACITY_MIN + "–100). Lower it when it sits "
				+ "over text you want to read. It never goes fully invisible on purpose: the "
				+ "button keeps its whole tap area whatever it looks like, and an unseen ⋮ is a "
				+ "corner of the screen that quietly eats taps.");
		overflow_opacity.setKey("overflow_button_opacity");
		overflow_opacity.setValue(OVERFLOW_OPACITY_DEFAULT);
		miscOptions.addOption(overflow_opacity);

		BooleanOption overflow_background = new BooleanOption();
		overflow_background.setTitle("Overflow button background?");
		overflow_background.setDescription("Draw the dark disc behind the ⋮. On, it stays "
				+ "findable over a floating window or the map; off, only the three dots show "
				+ "and the game text behind them is uncovered.");
		overflow_background.setKey("overflow_button_background");
		overflow_background.setValue(true);
		miscOptions.addOption(overflow_background);

		BooleanOption overflow_border = new BooleanOption();
		overflow_border.setTitle("Overflow button ring?");
		overflow_border.setDescription("Draw the thin circle around the ⋮. Independent of the "
				+ "background, so you can keep an outline with no fill, or a fill with no "
				+ "outline. Both off leaves the bare glyph.");
		overflow_border.setKey("overflow_button_border");
		overflow_border.setValue(true);
		miscOptions.addOption(overflow_border);

		BooleanOption persistent_connection = new BooleanOption();
		persistent_connection.setTitle("Persistent Connection?");
		persistent_connection.setDescription("After brief network loss (VPN/Wi-Fi flaps), keep retrying longer without the disconnect dialog, and wait for connectivity before reconnecting. Cannot keep a dead TCP socket alive — the session is re-established when the network returns.");
		persistent_connection.setKey("persistent_connection");
		persistent_connection.setValue(false);
		miscOptions.addOption(persistent_connection);

		// The update-check toggle used to live here. It never belonged: this
		// screen is a connection profile, so "check for updates" read as a
		// per-world setting when the answer is a property of the install. It is
		// now in the launcher's overflow menu, next to the check itself.

		sg.addOption(miscOptions);
		
		SettingsGroup bellOptions = new SettingsGroup();
		bellOptions.setTitle("Bell");
		bellOptions.setDescription("Options for what happens when the bell character is recieved.");
		
		BooleanOption bell_vibrate = new BooleanOption();
		bell_vibrate.setTitle("Vibrate?");
		bell_vibrate.setDescription("Plays a short vibrate pattern when the bell is recieved.");
		bell_vibrate.setKey("bell_vibrate");
		bell_vibrate.setValue(true);
		bellOptions.addOption(bell_vibrate);
		
		ListOption trigger_sound_stream = new ListOption();
		trigger_sound_stream.setTitle("Trigger sounds play on");
		trigger_sound_stream.setDescription("Which volume a trigger's Play a Sound action uses. Media is the phone's game and video volume — the one the side buttons reach for — and is the default because the notification volume follows the ringer, so a silenced ringer silences your triggers. Alarm is the loudest and usually survives Do Not Disturb. .sound stream media|notification|alarm");
		trigger_sound_stream.setKey("trigger_sound_stream");
		// Added in this order: the values are indices into this list and they are
		// what lands in the profile. Nothing may be inserted in the middle.
		trigger_sound_stream.addItem("Media volume");
		trigger_sound_stream.addItem("Notification volume");
		trigger_sound_stream.addItem("Alarm volume");
		trigger_sound_stream.setValue(
				com.resurrection.blowtorch2.lib.util.TriggerSounds.DEFAULT_STREAM);
		bellOptions.addOption(trigger_sound_stream);

		BooleanOption trigger_sound_warn = new BooleanOption();
		trigger_sound_warn.setTitle("Say when a sound cannot be heard");
		trigger_sound_warn.setDescription("Show a short message when a trigger plays a sound while that volume is turned all the way down. Without it the failure has no symptom at all: the trigger fires, the sound plays, and nothing comes out. At most one message every thirty seconds. .sound warn on|off");
		trigger_sound_warn.setKey("trigger_sound_warn_silent");
		trigger_sound_warn.setValue(true);
		bellOptions.addOption(trigger_sound_warn);

		BooleanOption bell_notification = new BooleanOption();
		bell_notification.setTitle("Generate Notification?");
		bell_notification.setDescription("Spawns a new notification when bell is recieved.");
		bell_notification.setKey("bell_notification");
		bell_notification.setValue(false);
		bellOptions.addOption(bell_notification);
		
		BooleanOption bell_display = new BooleanOption();
		bell_display.setTitle("Display Bell?");
		bell_display.setDescription("Displays a small alert on the screen when the bell character is recieved.");
		bell_display.setKey("bell_display");
		bell_display.setValue(false);
		bellOptions.addOption(bell_display);
		
		sg.addOption(bellOptions);
		
		this.getSettings().setOptions(sg);
	}

	/** Extra text windows settings group (may be nested under Window after buildSettingsPage). */
	public SettingsGroup getExtraTextOptionsGroup() {
		return mExtraTextOptions;
	}

	public static enum LINK_MODE {
		BACKGROUND ( "background"),
		HIGHLIGHT ("highlight"),
		HIGHLIGHT_COLOR ("highlight_color"),
		HIGHLIGHT_COLOR_ONLY_BLAND ( "highlight_color_bland_only"),
		NONE ( "none");
		
		private final String mode;  
		LINK_MODE(String str) {
			mode = str;
		}
		
		public String getValue() {
			return mode;
		}
	}
	
public final static int DEFAULT_HYPERLINK_COLOR = 0xFF66CCFF;

	/** Default ⋮ opacity percent (fully opaque, as the drawable always was). */
	public static final int OVERFLOW_OPACITY_DEFAULT = 100;
	/**
	 * Floor for ⋮ opacity.
	 *
	 * <p>Not zero on purpose. The button keeps its full 48dp touch box however
	 * faint it is drawn, so an invisible ⋮ would be a corner of the game text
	 * that swallows taps with nothing on screen to explain it. 15% still leaves
	 * a smudge you can aim at.
	 */
	public static final int OVERFLOW_OPACITY_MIN = 15;
	
	private int LineSize = 18;
	private int LineSpaceExtra = 2;
	private int MaxLines = 300;
	private String FontName = "monospace";
	private String FontPath = "none";
	private boolean AutoLaunchButtonEdtior = true;
	private boolean DisableColor = false;
	//private boolean OverrideHapticFeedback = false;
	private String hapticFeedbackMode = "auto";
	private String hapticFeedbackOnPress = "auto";
	private String hapticFeedbackOnFlip = "none";
	private boolean roundButtons = true;
	
	private boolean keepScreenOn = true;
	private boolean vibrateOnBell = true;
	private boolean notifyOnBell = false;
	private boolean displayOnBell = false;
	private boolean localEcho = true;
	private boolean fullScreen = true;
	private boolean echoAliasUpdates = true;
	
	private String gmcpTriggerChar = "%";
	private boolean wordWrap = true;
	private int breakAmount = 0; //0 is automatic
	private int orientation = 0; //0 is automatic
	
	private boolean UseExtractUI = false;
	private boolean AttemptSuggestions = false;
	
	private String encoding = "UTF-8";
	
	private boolean SemiIsNewLine = true;
	private boolean ProcessPeriod = true;
	private boolean ThrottleBackground = false;
	private boolean KeepWifiActive = true;
	private boolean KeepLast = false;
	private boolean backspaceBugFix = true;
	
	
	private boolean debugTelnet = false;
	private boolean removeExtraColor = true;
	
	private LINK_MODE hyperLinkMode = LINK_MODE.HIGHLIGHT_COLOR_ONLY_BLAND;
	private int hyperLinkColor = DEFAULT_HYPERLINK_COLOR;
	private boolean hyperLinkEnabled = true;
	
	private HashMap<String,DirectionData> Directions = new HashMap<String,DirectionData>();
	private ArrayList<String> links = new ArrayList<String>();
	
	
	private String lastSelected = "default";
	enum WRAP_MODE {
		NONE,
		BREAK,
		WORD
	}
	
	private WRAP_MODE WrapMode = WRAP_MODE.BREAK;

	public int getLineSize() {
		return LineSize;
	}

	public void setLineSize(int lineSize) {
		LineSize = lineSize;
	}

	public int getLineSpaceExtra() {
		return LineSpaceExtra;
	}

	public void setLineSpaceExtra(int lineSpaceExtra) {
		LineSpaceExtra = lineSpaceExtra;
	}

	public int getMaxLines() {
		return MaxLines;
	}

	public void setMaxLines(int maxLines) {
		MaxLines = maxLines;
	}

	public String getFontName() {
		return FontName;
	}

	public void setFontName(String fontName) {
		FontName = fontName;
	}

	public String getFontPath() {
		return FontPath;
	}

	public void setFontPath(String fontPath) {
		FontPath = fontPath;
	}

	public boolean isAutoLaunchButtonEdtior() {
		return AutoLaunchButtonEdtior;
	}

	public void setAutoLaunchButtonEdtior(boolean autoLaunchButtonEdtior) {
		AutoLaunchButtonEdtior = autoLaunchButtonEdtior;
	}

	public boolean isDisableColor() {
		return DisableColor;
	}

	public void setDisableColor(boolean disableColor) {
		DisableColor = disableColor;
	}

	public String getHapticFeedbackMode() {
		return hapticFeedbackMode;
	}

	public void setHapticFeedbackMode(String hapticFeedbackMode) {
		this.hapticFeedbackMode = hapticFeedbackMode;
	}

	public String getHapticFeedbackOnPress() {
		return hapticFeedbackOnPress;
	}

	public void setHapticFeedbackOnPress(String hapticFeedbackOnPress) {
		this.hapticFeedbackOnPress = hapticFeedbackOnPress;
	}

	public String getHapticFeedbackOnFlip() {
		return hapticFeedbackOnFlip;
	}

	public void setHapticFeedbackOnFlip(String hapticFeedbackOnFlip) {
		this.hapticFeedbackOnFlip = hapticFeedbackOnFlip;
	}

	public boolean isRoundButtons() {
		return roundButtons;
	}

	public void setRoundButtons(boolean roundButtons) {
		this.roundButtons = roundButtons;
	}

	public boolean isKeepScreenOn() {
		return keepScreenOn;
	}

	public void setKeepScreenOn(boolean keepScreenOn) {
		this.keepScreenOn = keepScreenOn;
	}

	public boolean isVibrateOnBell() {
		return vibrateOnBell;
	}

	public void setVibrateOnBell(boolean vibrateOnBell) {
		this.vibrateOnBell = vibrateOnBell;
	}

	public boolean isNotifyOnBell() {
		return notifyOnBell;
	}

	public void setNotifyOnBell(boolean notifyOnBell) {
		this.notifyOnBell = notifyOnBell;
	}

	public boolean isDisplayOnBell() {
		return displayOnBell;
	}

	public void setDisplayOnBell(boolean displayOnBell) {
		this.displayOnBell = displayOnBell;
	}

	public boolean isLocalEcho() {
		return localEcho;
	}

	public void setLocalEcho(boolean localEcho) {
		this.localEcho = localEcho;
	}

	public boolean isFullScreen() {
		return fullScreen;
	}

	public void setFullScreen(boolean fullScreen) {
		this.fullScreen = fullScreen;
	}

	public boolean isEchoAliasUpdates() {
		return echoAliasUpdates;
	}

	public void setEchoAliasUpdates(boolean echoAliasUpdates) {
		this.echoAliasUpdates = echoAliasUpdates;
	}

	public boolean isWordWrap() {
		return wordWrap;
	}

	public void setWordWrap(boolean wordWrap) {
		this.wordWrap = wordWrap;
	}

	public int getBreakAmount() {
		return breakAmount;
	}

	public void setBreakAmount(int breakAmount) {
		this.breakAmount = breakAmount;
	}

	public int getOrientation() {
		return orientation;
	}

	public void setOrientation(int orientation) {
		this.orientation = orientation;
	}

	public boolean isUseExtractUI() {
		return UseExtractUI;
	}

	public void setUseExtractUI(boolean useExtractUI) {
		UseExtractUI = useExtractUI;
	}

	public boolean isAttemptSuggestions() {
		return AttemptSuggestions;
	}

	public void setAttemptSuggestions(boolean attemptSuggestions) {
		AttemptSuggestions = attemptSuggestions;
	}

	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public boolean isSemiIsNewLine() {
		return SemiIsNewLine;
	}

	public void setSemiIsNewLine(boolean semiIsNewLine) {
		SemiIsNewLine = semiIsNewLine;
	}

	public boolean isProcessPeriod() {
		return ProcessPeriod;
	}

	public void setProcessPeriod(boolean processPeriod) {
		ProcessPeriod = processPeriod;
	}

	public boolean isThrottleBackground() {
		return ThrottleBackground;
	}

	public void setThrottleBackground(boolean throttleBackground) {
		ThrottleBackground = throttleBackground;
	}

	public boolean isKeepLast() {
		return KeepLast;
	}

	public void setKeepLast(boolean keepLast) {
		KeepLast = keepLast;
	}

	public boolean isKeepWifiActive() {
		return KeepWifiActive;
	}

	public void setKeepWifiActive(boolean keepWifiActive) {
		KeepWifiActive = keepWifiActive;
	}

	public boolean isBackspaceBugFix() {
		return backspaceBugFix;
	}

	public void setBackspaceBugFix(boolean backspaceBugFix) {
		this.backspaceBugFix = backspaceBugFix;
	}

	public boolean isDebugTelnet() {
		return debugTelnet;
	}

	public void setDebugTelnet(boolean debugTelnet) {
		this.debugTelnet = debugTelnet;
	}

	public boolean isRemoveExtraColor() {
		return removeExtraColor;
	}

	public void setRemoveExtraColor(boolean removeExtraColor) {
		this.removeExtraColor = removeExtraColor;
	}

	public LINK_MODE getHyperLinkMode() {
		return hyperLinkMode;
	}

	public void setHyperLinkMode(LINK_MODE hyperLinkMode) {
		this.hyperLinkMode = hyperLinkMode;
	}

	public int getHyperLinkColor() {
		return hyperLinkColor;
	}

	public void setHyperLinkColor(int hyperLinkColor) {
		this.hyperLinkColor = hyperLinkColor;
	}

	public boolean isHyperLinkEnabled() {
		return hyperLinkEnabled;
	}

	public void setHyperLinkEnabled(boolean hyperLinkEnabled) {
		this.hyperLinkEnabled = hyperLinkEnabled;
	}

	public HashMap<String,DirectionData> getDirections() {
		return Directions;
	}

	public void setDirections(HashMap<String,DirectionData> directions) {
		Directions = directions;
	}

	public String getLastSelected() {
		return lastSelected;
	}

	public void setLastSelected(String lastSelected) {
		this.lastSelected = lastSelected;
	}

	public WRAP_MODE getWrapMode() {
		return WrapMode;
	}

	public void setWrapMode(WRAP_MODE wrapMode) {
		WrapMode = wrapMode;
	}

	public void outputXMLInternal(XmlSerializer out) {
		//this is where we take our normal data and 
	}

	public void importV1Settings(HyperSettings oldSettings) {
		//
		this.getSettings().setAliases(oldSettings.getAliases());
		this.getSettings().setTriggers(oldSettings.getTriggers());
		this.getSettings().setTimers(oldSettings.getTimers());
		
		//somehow handle buttons.
		this.setDirections(oldSettings.getDirections());
		
		//this.setWrapMode(oldSettings.getWrapMode());
		this.setKeepLast(oldSettings.isKeepLast());
		this.setRemoveExtraColor(oldSettings.isRemoveExtraColor());
		this.setDebugTelnet(oldSettings.isDebugTelnet());
		this.setAttemptSuggestions(oldSettings.isAttemptSuggestions());
		this.setEncoding(oldSettings.getEncoding());
		this.setDisplayOnBell(oldSettings.isDisplayOnBell());
		this.setNotifyOnBell(oldSettings.isNotifyOnBell());
		this.setVibrateOnBell(oldSettings.isVibrateOnBell());
		this.setFullScreen(oldSettings.isFullScreen());
		this.setKeepScreenOn(oldSettings.isKeepScreenOn());
		this.setProcessPeriod(oldSettings.isProcessPeriod());
		this.setOrientation(oldSettings.getOrientation());
		this.setEchoAliasUpdates(oldSettings.isEchoAliasUpdates());
		this.setUseExtractUI(oldSettings.isUseExtractUI());
		this.setSemiIsNewLine(oldSettings.isSemiIsNewLine());
		this.setLocalEcho(oldSettings.isLocalEcho());
		
		this.getSettings().getOptions().setOption("keep_last", Boolean.toString(oldSettings.isKeepLast()));
		this.getSettings().getOptions().setOption("cull_extraneous_color", Boolean.toString(oldSettings.isRemoveExtraColor()));
		this.getSettings().getOptions().setOption("debug_telnet", Boolean.toString(oldSettings.isDebugTelnet()));
		this.getSettings().getOptions().setOption("use_suggestions", Boolean.toString(oldSettings.isAttemptSuggestions()));
		this.getSettings().getOptions().setOption("encoding", oldSettings.getEncoding());
		this.getSettings().getOptions().setOption("bell_vibrate", Boolean.toString(oldSettings.isVibrateOnBell()));
		this.getSettings().getOptions().setOption("bell_notification", Boolean.toString(oldSettings.isNotifyOnBell()));
		this.getSettings().getOptions().setOption("bell_display", Boolean.toString(oldSettings.isDisplayOnBell()));
		this.getSettings().getOptions().setOption("fullscreen", Boolean.toString(oldSettings.isFullScreen()));
		this.getSettings().getOptions().setOption("screen_on", Boolean.toString(oldSettings.isKeepScreenOn()));
		this.getSettings().getOptions().setOption("process_system_commands", Boolean.toString(oldSettings.isProcessPeriod()));
		this.getSettings().getOptions().setOption("orientation", Integer.toString(oldSettings.getOrientation()));
		this.getSettings().getOptions().setOption("echo_alias_update", Boolean.toString(oldSettings.isEchoAliasUpdates()));
		this.getSettings().getOptions().setOption("fullscreen_editor", Boolean.toString(oldSettings.isUseExtractUI()));
		this.getSettings().getOptions().setOption("local_echo", Boolean.toString(oldSettings.isLocalEcho()));
		this.getSettings().getOptions().setOption("keep_wifi_alive", Boolean.toString(oldSettings.isKeepWifiActive()));
		this.getSettings().getOptions().setOption("compatibility_mode", Boolean.toString(oldSettings.isBackspaceBugFix()));
		this.getSettings().getOptions().setOption("process_semicolon", Boolean.toString(oldSettings.isSemiIsNewLine()));
		
		//set window token settings.
		this.getSettings().getOptions().setOption("hyperlinks_enabled", Boolean.toString(oldSettings.isHyperLinkEnabled()));
		switch(oldSettings.getHyperLinkMode()) {
		case BACKGROUND:
			this.getSettings().getOptions().setOption("hyperlink_mode", Integer.toString(4));
			break;
		case NONE:
			this.getSettings().getOptions().setOption("hyperlink_mode", Integer.toString(0));
			break;
		case HIGHLIGHT_COLOR_ONLY_BLAND:
			this.getSettings().getOptions().setOption("hyperlink_mode", Integer.toString(3));
			break;
		case HIGHLIGHT_COLOR:
			this.getSettings().getOptions().setOption("hyperlink_mode", Integer.toString(2));
			break;
		case HIGHLIGHT:
			this.getSettings().getOptions().setOption("hyperlink_mode", Integer.toString(1));
			break;
		}
		
		this.getSettings().getOptions().setOption("hyperlink_color", Integer.toString(oldSettings.getHyperLinkColor()));
		this.getSettings().getOptions().setOption("word_wrap", Boolean.toString(oldSettings.isWordWrap()));
		this.getSettings().getOptions().setOption("color_option", Integer.toString((oldSettings.isDisableColor() == true) ? 1 : 0));
		this.getSettings().getOptions().setOption("font_size", Integer.toString(oldSettings.getLineSize()));
		this.getSettings().getOptions().setOption("line_extra", Integer.toString(oldSettings.getLineSpaceExtra()));
		this.getSettings().getOptions().setOption("buffer_size", Integer.toString(oldSettings.getMaxLines()));
		if(oldSettings.getFontName().equals("")) {
			this.getSettings().getOptions().setOption("font_path", oldSettings.getFontPath());
		} else {
			this.getSettings().getOptions().setOption("font_path", oldSettings.getFontName());
		}
		
		
		
		//this.set
		
	}

	public void setLinks(ArrayList<String> links) {
		this.links = links;
	}

	public ArrayList<String> getLinks() {
		return links;
	}

	public void setGMCPTriggerChar(String gmcpTriggerChar) {
		this.gmcpTriggerChar = gmcpTriggerChar;
	}

	public String getGMCPTriggerChar() {
		return gmcpTriggerChar;
	}


}

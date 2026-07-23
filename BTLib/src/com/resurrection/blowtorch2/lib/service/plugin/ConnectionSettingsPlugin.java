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
		fullscreen_editor.setTitle("Fullscreen Editor?");
		fullscreen_editor.setDescription("Show the full screen editor when the input bar is clicked.");
		fullscreen_editor.setKey("fullscreen_editor");
		fullscreen_editor.setValue(false);
		input.addOption(fullscreen_editor);
		
		BooleanOption use_suggestions = new BooleanOption();
		use_suggestions.setTitle("Use Suggestions?");
		use_suggestions.setDescription("Attempt suggestions if the full screen editor is not used.");
		use_suggestions.setKey("use_suggestions");
		use_suggestions.setValue(false);
		input.addOption(use_suggestions);
		
		BooleanOption keep_last = new BooleanOption();
		keep_last.setTitle("Keep Last Entered?");
		keep_last.setDescription("Keeps the last text entered in the window and highights after sending.");
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
		compatilibility_mode.setTitle("Enable Compatibility Mode?");
		compatilibility_mode.setDescription("Enable this if you have problems with bascpace not workin in the non-full screen editor.");
		compatilibility_mode.setKey("compatibility_mode");
		compatilibility_mode.setValue(false);
		input.addOption(compatilibility_mode);

		IntegerOption input_history = new IntegerOption();
		input_history.setTitle("Input History Size");
		input_history.setDescription("How many previous commands to keep (per profile, 10–100).");
		input_history.setKey("input_history_size");
		input_history.setValue(75);
		input.addOption(input_history);
		
		sg.addOption(input);

		
		
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
		log_gmcp.setDescription("Write GMCP handshake and packets to the app error log (files/logs/blowtorch2.log; also session log if enabled). Also: .gmcp sniff on");
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
		gmcp_suggest.setDescription("Optional toast when the server sends a package family you have not declared in Supports.Set. Submodules of enabled parents (e.g. Char.Base under Char) do not trigger. Off by default.");
		gmcp_suggest.setKey("gmcp_suggest_modules");
		gmcp_suggest.setValue(false);
		gmcpOptions.addOption(gmcp_suggest);

		SettingsGroup mcpOptions = new SettingsGroup();
		mcpOptions.setTitle("MCP Options");
		mcpOptions.setDescription("Mud Client Protocol (#$# in-band). Used by HellMOO / SamsaraMoo and some MOOs — different from GMCP. Off by default.");

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
		protocolOptions.setDescription("Optional telnet capabilities next to GMCP. All off by default — leave disabled unless your MUD needs them.");
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

		CallbackOption request_storage = new CallbackOption();
		request_storage.setTitle("Manage Storage Access");
		request_storage.setDescription("Grant All files access so BlowTorch can use /BlowTorch/ (settings, backups, launcher, session_logs, logs) outside Android/data. Shows the effective root path.");
		request_storage.setKey("request_storage_access");
		request_storage.setValue("request_storage_access");
		miscOptions.addOption(request_storage);

		BooleanOption persistent_connection = new BooleanOption();
		persistent_connection.setTitle("Persistent Connection?");
		persistent_connection.setDescription("After brief network loss (VPN/Wi-Fi flaps), keep retrying longer without the disconnect dialog, and wait for connectivity before reconnecting. Cannot keep a dead TCP socket alive — the session is re-established when the network returns.");
		persistent_connection.setKey("persistent_connection");
		persistent_connection.setValue(false);
		miscOptions.addOption(persistent_connection);

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

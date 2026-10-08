package com.resurrection.blowtorch2.lib.service.plugin;

import java.util.ArrayList;
import java.util.HashMap;

import org.keplerproject.luajava.LuaException;
import org.xmlpull.v1.XmlSerializer;


import android.os.Handler;

import com.resurrection.blowtorch2.lib.gauge.GaugeWidgetsStore;
import com.resurrection.blowtorch2.lib.mapper.MapDirections;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.ConnectionPluginCallback;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.CallbackOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.EncodingOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.IntegerOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.ListOption;
import com.resurrection.blowtorch2.lib.window.GlobalGestures;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginSettings;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsGroup;
import com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption;
import com.resurrection.blowtorch2.lib.settings.HyperSettings;
import com.resurrection.blowtorch2.lib.speedwalk.DirectionData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

public class ConnectionSettingsPlugin extends Plugin {
	/** Extra text options; nested under Panes. */
	private SettingsGroup mExtraTextOptions;
	/** Overlay gauge options; nested under Panes. */
	private SettingsGroup mGaugeWidgetsOptions;

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
		input.setTitle("Typing");
		input.setDescription("The input bar: keyboard, last command, accents, hyphens. Edit and Send are under Window, Input bar.");
		
		BooleanOption fullscreen_editor = new BooleanOption();
		fullscreen_editor.setTitle("Allow fullscreen keyboard editor");
		fullscreen_editor.setDescription("On some older keyboards, opens a full-screen typing view instead of the strip above the keys.");
		fullscreen_editor.setKey("fullscreen_editor");
		fullscreen_editor.setValue(false);
		input.addOption(fullscreen_editor);
		
		BooleanOption use_suggestions = new BooleanOption();
		use_suggestions.setTitle("Keyboard word suggestions");
		use_suggestions.setDescription("Ask the keyboard for its own word list in the input bar; off is the usual choice for game commands.");
		use_suggestions.setKey("use_suggestions");
		use_suggestions.setValue(false);
		input.addOption(use_suggestions);

		BooleanOption floating_buttons_enabled = new BooleanOption();
		floating_buttons_enabled.setTitle("Floating buttons over the game");
		floating_buttons_enabled.setDescription("Show floating copies of buttons marked \"Float over the game\" in the button editor, or hide them all at once.");
		floating_buttons_enabled.setKey("floating_buttons_enabled");
		floating_buttons_enabled.setValue(true);
		
		BooleanOption keep_last = new BooleanOption();
		keep_last.setTitle("Keep last command after send");
		keep_last.setDescription("After you send, leave that line in the input bar and select it so you can edit or resend.");
		keep_last.setKey("keep_last");
		keep_last.setValue(false);
		input.addOption(keep_last);

		BooleanOption grow_input_bar = new BooleanOption();
		grow_input_bar.setTitle("Grow Input Bar?");
		grow_input_bar.setDescription("The input bar grows to show a pasted block of lines, and Enter still sends.");
		grow_input_bar.setKey("grow_input_bar");
		grow_input_bar.setValue(true);
		input.addOption(grow_input_bar);

		BooleanOption lowercase_command_start = new BooleanOption();
		lowercase_command_start.setTitle("Lowercase start of sent commands");
		lowercase_command_start.setDescription(
				"Lowercase only the first letter of each command you send, so Look goes out as look.");
		lowercase_command_start.setKey("lowercase_command_start");
		lowercase_command_start.setValue(false);
		input.addOption(lowercase_command_start);

		BooleanOption unaccent_send = new BooleanOption();
		unaccent_send.setTitle("Strip accents when sending");
		unaccent_send.setDescription(
				"When sending, fold letters with marks into plain letters; the input bar still shows what you typed.");
		unaccent_send.setKey("unaccent_send");
		unaccent_send.setValue(false);
		input.addOption(unaccent_send);

		BooleanOption input_hyphenate = new BooleanOption();
		input_hyphenate.setTitle("Hyphenate long words?");
		input_hyphenate.setDescription(
				"A word that does not fit is drawn with a hyphen and continues underneath, and what you send is still the whole word.");
		input_hyphenate.setKey("input_hyphenate");
		input_hyphenate.setValue(false);
		input.addOption(input_hyphenate);

		ListOption input_hyphen_lang = new ListOption();
		input_hyphen_lang.setTitle("Hyphenation language");
		input_hyphen_lang.setDescription(
				"Which syllable rules the drawn hyphen follows; the word you send stays whole.");
		input_hyphen_lang.setKey("input_hyphen_lang");
		input_hyphen_lang.addItem("English");
		input_hyphen_lang.addItem("Polish");
		input_hyphen_lang.addItem("Phone language");
		input_hyphen_lang.setValue(Integer.valueOf(0));
		input.addOption(input_hyphen_lang);

		BooleanOption input_hyphen_full = new BooleanOption();
		input_hyphen_full.setTitle("Hyphenate more often?");
		input_hyphen_full.setDescription(
				"Allow a drawn hyphen after two letters instead of five, and only while hyphenation is on.");
		input_hyphen_full.setKey("input_hyphen_full");
		input_hyphen_full.setValue(false);
		input.addOption(input_hyphen_full);

		BooleanOption compatilibility_mode = new BooleanOption();
		compatilibility_mode.setTitle("Standard keyboard input (IME fix)");
		compatilibility_mode.setDescription("Use Android's normal input connection, so backspace and replacing a selected letter follow the keyboard.");
		compatilibility_mode.setKey("compatibility_mode");
		// On by default. Parser comparison is != true. An omitted key used to
		// mean off, so a profile that never stored this now loads as on.
		compatilibility_mode.setValue(true);
		input.addOption(compatilibility_mode);

		// Own root page. Inner section titles stay in INLINE_GROUP_TITLES.
		// Add each option to its section before that section is added here.
		SettingsGroup suggestions = new SettingsGroup();
		suggestions.setTitle("Suggestions");
		suggestions.setDescription("Suggest words the game just used, and where those suggestions appear.");

		BooleanOption word_complete = new BooleanOption();
		word_complete.setTitle("Suggest game words");
		word_complete.setDescription("Offer a name the game just used after you type the start of it; off, nothing below does anything.");
		word_complete.setKey("word_complete");
		word_complete.setValue(false);
		suggestions.addOption(word_complete);

		IntegerOption word_complete_lines = new IntegerOption();
		word_complete_lines.setTitle("Remember (lines)");
		word_complete_lines.setDescription("How many recent lines count as fresh, from 0 to 5000; 0 means the whole session.");
		word_complete_lines.setKey("word_complete_lines");
		word_complete_lines.setValue(
				com.resurrection.blowtorch2.lib.window.WordSuggestions.DEFAULT_MAX_LINES);
		suggestions.addOption(word_complete_lines);

		BooleanOption word_complete_typos = new BooleanOption();
		word_complete_typos.setTitle("Correct nearby misspellings");
		word_complete_typos.setDescription("If what you typed is not the start of a recent word, offer a close misspelling of one the game just used.");
		word_complete_typos.setKey("word_complete_typos");
		word_complete_typos.setValue(true);

		BooleanOption word_complete_loose = new BooleanOption();
		word_complete_loose.setTitle("Skipped letters in order");
		word_complete_loose.setDescription("When the exact spelling finds nothing, match your letters in order with gaps.");
		word_complete_loose.setKey("word_complete_loose");
		word_complete_loose.setValue(false);

		BooleanOption word_complete_skip_head = new BooleanOption();
		word_complete_skip_head.setTitle("Skip the start of a long name");
		word_complete_skip_head.setDescription("When the exact start finds nothing, a long name can match from its tail.");
		word_complete_skip_head.setKey("word_complete_skip_head");
		word_complete_skip_head.setValue(true);

		BooleanOption word_complete_wrong_first = new BooleanOption();
		word_complete_wrong_first.setTitle("Two mistakes may change the first letter");
		word_complete_wrong_first.setDescription("Two mistakes on a longer word may change the first letter.");
		word_complete_wrong_first.setKey("word_complete_wrong_first");
		word_complete_wrong_first.setValue(false);

		SettingsGroup spelling = new SettingsGroup();
		spelling.setTitle("When spelling is inexact");
		spelling.addOption(word_complete_typos);
		spelling.addOption(word_complete_loose);
		spelling.addOption(word_complete_skip_head);
		spelling.addOption(word_complete_wrong_first);
		suggestions.addOption(spelling);

		BooleanOption word_complete_phrases = new BooleanOption();
		word_complete_phrases.setTitle("Offer whole names");
		word_complete_phrases.setDescription("Also offer the words that followed, up to three, while you are still typing the first word.");
		word_complete_phrases.setKey("word_complete_phrases");
		// Off by default: on, the top suggestion for a prefix stops being a word
		// and becomes a phrase, and the ghost draws it. Change this and the
		// comparison in ConnectionSetttingsParser together.
		word_complete_phrases.setValue(false);

		BooleanOption word_complete_short_first = new BooleanOption();
		word_complete_short_first.setTitle("Plain word before the whole name");
		word_complete_short_first.setDescription("With whole names on, offer the plain word before the longer name.");
		word_complete_short_first.setKey("word_complete_short_first");
		word_complete_short_first.setValue(false);

		SettingsGroup wholeNames = new SettingsGroup();
		wholeNames.setTitle("Whole names");
		wholeNames.addOption(word_complete_phrases);
		wholeNames.addOption(word_complete_short_first);
		suggestions.addOption(wholeNames);

		BooleanOption word_complete_next = new BooleanOption();
		word_complete_next.setTitle("Suggest the next word");
		word_complete_next.setDescription("After a finished word and a space, offer the one word that followed it in the game.");
		word_complete_next.setKey("word_complete_next");
		// On by default: this is what the client already does, and turning it
		// off is the choice. Change this and ConnectionSetttingsParser's
		// comparison together, or a saved off is dropped on the next load.
		word_complete_next.setValue(true);
		suggestions.addOption(word_complete_next);

		ListOption word_complete_where = new ListOption();
		word_complete_where.setTitle("Bar of suggestions");
		word_complete_where.setDescription("Where suggestions sit: floating over the game, in a strip below it, in a list window, or nowhere so only the ghost remains.");
		word_complete_where.setKey("word_complete_where");
		// Added in this order: the values are indices into this list, and they are
		// what lands in the profile. Anything inserted in the middle renames every
		// saved choice after it.
		word_complete_where.addItem("Floating over the game");
		word_complete_where.addItem("Below the game window");
		word_complete_where.addItem("Nowhere (ghost only)");
		word_complete_where.addItem("In a list window");
		// Floating by default. The strip below the game window takes height while
		// it shows, so the text jumps under the thumb on every letter. Change this
		// and ConnectionSetttingsParser's comparison together, or the parser
		// quietly stops saving the value the player chose.
		word_complete_where.setValue(
				com.resurrection.blowtorch2.lib.window.WordSuggestions.DEFAULT_WHERE);

		ListOption word_complete_order = new ListOption();
		word_complete_order.setTitle("Chip order");
		word_complete_order.setDescription("Put the first suggestion on the left chip or the right chip.");
		word_complete_order.setKey("word_complete_order");
		word_complete_order.addItem("First on the left");
		word_complete_order.addItem("First on the right");
		word_complete_order.setValue(Integer.valueOf(
				com.resurrection.blowtorch2.lib.window.WordSuggestions.DEFAULT_ORDER));

		BooleanOption word_complete_ghost = new BooleanOption();
		word_complete_ghost.setTitle("Ghost after the cursor");
		word_complete_ghost.setDescription("Draw the top suggestion in dim type after the cursor, and a tap takes it.");
		word_complete_ghost.setKey("word_complete_ghost");
		word_complete_ghost.setValue(false);

		BooleanOption word_complete_split = new BooleanOption();
		word_complete_split.setTitle("Also split suggestions");
		word_complete_split.setDescription("Separate dimmed suggestions on the same line with a space.");
		word_complete_split.setKey("word_complete_split");
		word_complete_split.setValue(false);

		BooleanOption word_complete_caret = new BooleanOption();
		word_complete_caret.setTitle("Complete at the cursor");
		word_complete_caret.setDescription("Suggestions follow the cursor when you edit in the middle of a line, not only at the end.");
		word_complete_caret.setKey("word_complete_caret");
		word_complete_caret.setValue(false);

		IntegerOption word_complete_ghost_lines = new IntegerOption();
		word_complete_ghost_lines.setTitle("Suggestions under the line");
		word_complete_ghost_lines.setDescription("How many extra rows, from 1 to 6, the input bar may grow by when suggestions do not fit on the line.");
		word_complete_ghost_lines.setKey("word_complete_ghost_lines");
		word_complete_ghost_lines.setValue(1);

		IntegerOption word_complete_show = new IntegerOption();
		word_complete_show.setTitle("Suggestions shown at once");
		word_complete_show.setDescription("How many suggestions the bar and ghost may offer at once, from 1 to 8.");
		word_complete_show.setKey("word_complete_show");
		word_complete_show.setValue(8);

		BooleanOption word_complete_persist = new BooleanOption();
		word_complete_persist.setTitle("Keep the bar in place");
		word_complete_persist.setDescription("Leave the suggestion bar up even with nothing to suggest, so the game text does not jump.");
		word_complete_persist.setKey("word_complete_persist");
		word_complete_persist.setValue(false);

		IntegerOption word_complete_opacity = new IntegerOption();
		word_complete_opacity.setTitle("Chip opacity (%)");
		word_complete_opacity.setDescription("How solid the floating suggestion chips are, from 10 to 100.");
		word_complete_opacity.setKey("word_complete_opacity");
		word_complete_opacity.setValue(
				com.resurrection.blowtorch2.lib.window.WordSuggestions.DEFAULT_OPACITY);

		SettingsGroup whereShown = new SettingsGroup();
		whereShown.setTitle("Where they appear");
		whereShown.addOption(word_complete_where);
		whereShown.addOption(word_complete_order);
		whereShown.addOption(word_complete_ghost);
		whereShown.addOption(word_complete_split);
		whereShown.addOption(word_complete_caret);
		whereShown.addOption(word_complete_ghost_lines);
		whereShown.addOption(word_complete_show);
		whereShown.addOption(word_complete_persist);
		whereShown.addOption(word_complete_opacity);
		suggestions.addOption(whereShown);

		BooleanOption word_complete_rank = new BooleanOption();
		word_complete_rank.setTitle("Order by place in the line");
		word_complete_rank.setDescription("At the start of a line, put command words first; after a command, put the targets you have used first.");
		word_complete_rank.setKey("word_complete_rank");
		word_complete_rank.setValue(true);

		BooleanOption word_complete_pairs = new BooleanOption();
		word_complete_pairs.setTitle("Learn what goes with what");
		word_complete_pairs.setDescription("After a command word, offer what you have aimed that command at before.");
		word_complete_pairs.setKey("word_complete_pairs");
		word_complete_pairs.setValue(false);

		BooleanOption word_complete_shorter_first = new BooleanOption();
		word_complete_shorter_first.setTitle("Shorter suggestions first");
		word_complete_shorter_first.setDescription("Order suggestions shortest first, inside each group, and drop nothing.");
		word_complete_shorter_first.setKey("word_complete_shorter_first");
		// Off by default: newest-first is what the app has always done, and a
		// player who never opens this must keep it. Change this and
		// ConnectionSetttingsParser's comparison together, or the parser quietly
		// stops saving the value the player chose.
		word_complete_shorter_first.setValue(false);

		SettingsGroup order = new SettingsGroup();
		order.setTitle("Order");
		order.addOption(word_complete_rank);
		order.addOption(word_complete_pairs);
		order.addOption(word_complete_shorter_first);
		suggestions.addOption(order);

		BooleanOption speak_quiet_typing = new BooleanOption();
		speak_quiet_typing.setTitle("Quiet while you type");
		speak_quiet_typing.setDescription("Triggers that speak stay quiet from the first letter of a command until you send it.");
		speak_quiet_typing.setKey("speak_quiet_typing");
		// Off by default: speaking whenever a trigger fires is what the app did
		// before this existed, and a player who never opens this option must get
		// that. On it silences alerts during exactly the busiest moments, which
		// is not a thing to hand anybody without their asking. Change this and
		// ConnectionSetttingsParser's comparison together, or the parser quietly
		// stops saving the value the player chose.
		speak_quiet_typing.setValue(false);

		BooleanOption prompt_bar = new BooleanOption();
		prompt_bar.setTitle("Prompt on its own bar");
		prompt_bar.setDescription("The game's unfinished prompt line sits above the input bar instead of repeating down the window.");
		prompt_bar.setKey("prompt_bar");
		prompt_bar.setValue(false);

		IntegerOption input_history = new IntegerOption();
		input_history.setTitle("Input History Size");
		input_history.setDescription("How many previous commands to keep (per profile, 10–100).");
		input_history.setKey("input_history_size");
		input_history.setValue(75);
		input.addOption(input_history);

		CallbackOption show_last_bar = new CallbackOption();
		show_last_bar.setTitle("Recent command bar…");
		show_last_bar.setDescription(
				"Settings for the recent-command chips above the input row: on or off, how many, and how much of each command shows.");
		show_last_bar.setKey("show_last_bar");
		show_last_bar.setValue("show_last_bar");
		input.addOption(show_last_bar);

		SettingsGroup globalGestures = new SettingsGroup();
		globalGestures.setTitle("Gestures");
		globalGestures.setKey("global_gestures_group");
		globalGestures.setDescription("Screen-wide swipes on the game text, and which fingers scroll or send a command.");

		ListOption gestureMode = new ListOption();
		gestureMode.setTitle("Gesture mode");
		gestureMode.setDescription("Classic, one finger, two fingers, or both, one mode at a time.");
		gestureMode.setKey("global_gesture_mode");
		gestureMode.setValue(Integer.valueOf(0));
		gestureMode.addItem("Classic");
		gestureMode.addItem("One finger");
		gestureMode.addItem("Two fingers");
		gestureMode.addItem("Both");

		BooleanOption gestureShow = new BooleanOption();
		gestureShow.setTitle("Show the current mode");
		gestureShow.setDescription("A label near the top-right of the game text showing the gesture mode.");
		gestureShow.setKey("global_gesture_show_mode");
		gestureShow.setValue(false);

		BooleanOption gestureArrow = new BooleanOption();
		gestureArrow.setTitle("Show the direction marker");
		gestureArrow.setDescription("Eight slices while a gesture is on the way, with the active one filled. A second finger cancels a one-finger gesture. A third cancels a two-finger gesture. Lifting without a direction sends nothing.");
		gestureArrow.setKey("global_gesture_show_arrow");
		gestureArrow.setValue(true);

		BooleanOption gestureCommand = new BooleanOption();
		gestureCommand.setTitle("Show the command");
		gestureCommand.setDescription("The command that will send, drawn above the finger.");
		gestureCommand.setKey("global_gesture_show_command");
		gestureCommand.setValue(true);

		ListOption gestureScroll = new ListOption();
		gestureScroll.setTitle("Scrolling");
		gestureScroll.setDescription("In One finger and Both, whether a swipe scrolls, waits for a hold, or only sends a command.");
		gestureScroll.setKey("global_gesture_scroll");
		gestureScroll.setValue(Integer.valueOf(1));
		gestureScroll.addItem(GlobalGestures.SCROLL_CHOICES[0]);
		gestureScroll.addItem(GlobalGestures.SCROLL_CHOICES[1]);
		gestureScroll.addItem(GlobalGestures.SCROLL_CHOICES[2]);

		IntegerOption gestureHold = new IntegerOption();
		gestureHold.setTitle("Hold before a one-finger gesture (ms)");
		gestureHold.setDescription("How long the finger stays still before a one-finger gesture can start, when Hold, then gesture is selected. 80–800. 280 is the default.");
		gestureHold.setKey("global_gesture_hold_ms");
		gestureHold.setValue(Integer.valueOf(280));

		BooleanOption gestureTwoDir = new BooleanOption();
		gestureTwoDir.setTitle("One finger stays put, the other moves");
		gestureTwoDir.setDescription("Runs the two-finger command for that direction. Used in Two fingers, and in Both unless Scrolling is With two fingers.");
		gestureTwoDir.setKey("global_gesture_two_dir");
		gestureTwoDir.setValue(true);

		BooleanOption gestureTwoCopy = new BooleanOption();
		gestureTwoCopy.setTitle("A short two-finger tap copies text");
		gestureTwoCopy.setDescription("Used in Two fingers and Both. In One finger, only while Scrolling is With two fingers. Classic still copies as soon as the second finger lands.");
		gestureTwoCopy.setKey("global_gesture_two_copy");
		gestureTwoCopy.setValue(true);

		BooleanOption gestureTwoScroll = new BooleanOption();
		gestureTwoScroll.setTitle("Both fingers moving together scroll the text");
		gestureTwoScroll.setDescription("Used in Both while Scrolling is Hold, then gesture or Off. Grey when Scrolling is With two fingers, because two fingers already scroll.");
		gestureTwoScroll.setKey("global_gesture_two_scroll");
		gestureTwoScroll.setValue(false);

		CallbackOption editGlobalGestures = new CallbackOption();
		editGlobalGestures.setTitle("Edit global gestures");
		editGlobalGestures.setDescription("Eight directions for one finger, and eight for two fingers; a blank direction sends no command.");
		editGlobalGestures.setKey("global_gesture_bindings");
		editGlobalGestures.setValue("");

		globalGestures.addOption(gestureMode);
		globalGestures.addOption(gestureShow);
		globalGestures.addOption(gestureArrow);
		globalGestures.addOption(gestureCommand);
		globalGestures.addOption(gestureScroll);
		globalGestures.addOption(gestureHold);
		globalGestures.addOption(gestureTwoDir);
		globalGestures.addOption(gestureTwoCopy);
		globalGestures.addOption(gestureTwoScroll);
		globalGestures.addOption(editGlobalGestures);

		// The phone itself, as something triggers can read. Its own group
		// because it is not an input setting and not a display one, and because
		// this is where anything else sensor-shaped will go.
		SettingsGroup device = new SettingsGroup();
		device.setTitle("Device");
		device.setDescription("Shake, light, battery, and variables triggers can read.");

		BooleanOption device_state_variables = new BooleanOption();
		device_state_variables.setTitle("Device state as variables");
		device_state_variables.setDescription("Keep device.battery, device.screen, device.headphones and the other device values updated, so a trigger or GetVariable can read them.");
		device_state_variables.setKey("device_state_variables");
		// Off by default: the app did nothing of the sort before this existed.
		// Change this and ConnectionSetttingsParser's comparison together, or
		// the parser quietly stops saving the value the player chose.
		device_state_variables.setValue(false);
		device.addOption(device_state_variables);

		CallbackOption device_sensors = new CallbackOption();
		device_sensors.setTitle("Sensors\u2026");
		device_sensors.setDescription("The readings this phone can deliver, and the trigger each one currently drives.");
		device_sensors.setKey("device_sensors");
		device.addOption(device_sensors);

		CallbackOption calibrate_shake = new CallbackOption();
		calibrate_shake.setTitle("Calibrate shake\u2026");
		calibrate_shake.setDescription("Measure a shake and a walk, and keep a threshold that catches the shake without catching the walk.");
		calibrate_shake.setKey("calibrate_shake");
		device.addOption(calibrate_shake);

		CallbackOption calibrate_light = new CallbackOption();
		calibrate_light.setTitle("Calibrate light\u2026");
		calibrate_light.setDescription("Tap once somewhere dark and once somewhere bright, so dark means this room on this phone.");
		calibrate_light.setKey("calibrate_light");
		device.addOption(calibrate_light);

		CallbackOption battery_threshold = new CallbackOption();
		battery_threshold.setTitle("Battery low threshold\u2026");
		battery_threshold.setDescription("The charge percent that counts as low, and the higher percent that counts as recovered.");
		battery_threshold.setKey("battery_threshold");
		device.addOption(battery_threshold);

		BooleanOption sensor_screen_off = new BooleanOption();
		sensor_screen_off.setTitle("Movement sensors with the screen off");
		sensor_screen_off.setDescription("A shake or a hand over the screen can fire a trigger while the display is asleep, in every world you have open.");
		sensor_screen_off.setKey("sensor_screen_off");
		sensor_screen_off.setValue(false);
		device.addOption(sensor_screen_off);

		BooleanOption sensor_background = new BooleanOption();
		sensor_background.setTitle("Movement sensors while the app is in the background");
		sensor_background.setDescription("A shake or a hand over the screen can fire a trigger while BlowTorch is behind another app, in every world you have open.");
		sensor_background.setKey("sensor_background");
		sensor_background.setValue(false);
		device.addOption(sensor_background);

		// Drawn on the Sensors list, hidden from this menu. The row is the
		// storage: without it the switch has nowhere to write.
		BooleanOption sensors_enabled = new BooleanOption();
		sensors_enabled.setTitle("Sensors in this world");
		sensors_enabled.setDescription("Off silences every reading on the Sensors list in this world. Other worlds keep their own. The switches on the rows stay as they were.");
		sensors_enabled.setKey("sensors_enabled");
		sensors_enabled.setValue(true);
		device.addOption(sensors_enabled);

		BooleanOption sensor_my_shakes = new BooleanOption();
		sensor_my_shakes.setTitle("Use my shakes");
		sensor_my_shakes.setDescription("On: shake left, right, up, down and letter patterns stay quiet in this world, and a shake you recorded may fire. Shake the phone keeps its own switch. The shape stays on this phone.");
		sensor_my_shakes.setKey("sensor_my_shakes");
		sensor_my_shakes.setValue(false);
		device.addOption(sensor_my_shakes);

		
		
		SettingsGroup servOptions = new SettingsGroup();
		servOptions.setTitle("Connection");
		servOptions.setDescription("Reconnect, Wi-Fi, encoding, echo, and the session log.");

		EncodingOption enc = new EncodingOption();
		enc.setTitle("System Encoding");
		enc.setDescription("Which character set turns the server's bytes into letters, UTF-8 unless you see the wrong characters.");
		enc.setKey("encoding");
		enc.setValue("UTF-8");
		servOptions.addOption(enc);
		
		BooleanOption session_log = new BooleanOption();
		session_log.setTitle("Log Session to File?");
		session_log.setDescription("Append incoming game text to a file, one file per world per day.");
		session_log.setKey("session_log");
		session_log.setValue(false);
		servOptions.addOption(session_log);

		BooleanOption session_log_echo = new BooleanOption();
		session_log_echo.setTitle("Include Local Echo in Session Log?");
		session_log_echo.setDescription("Also append what local echo paints, when the session log is on.");
		session_log_echo.setKey("session_log_echo");
		session_log_echo.setValue(false);
		servOptions.addOption(session_log_echo);

		StringOption session_log_directory = new StringOption();
		session_log_directory.setTitle("Session Log Directory");
		session_log_directory.setDescription("Folder for session logs; blank uses /BlowTorch/session_logs/.");
		session_log_directory.setKey("session_log_directory");
		session_log_directory.setValue("");
		servOptions.addOption(session_log_directory);
		
		BooleanOption local_echo = new BooleanOption();
		local_echo.setTitle("Local Echo?");
		local_echo.setDescription("Show what you just sent in the game window; off, you only see the server's reply.");
		local_echo.setKey("local_echo");
		local_echo.setValue(true);
		servOptions.addOption(local_echo);
		
		BooleanOption process_system_commands = new BooleanOption();
		process_system_commands.setTitle("Process System Commands?");
		process_system_commands.setDescription("A line that starts with a dot, such as .help, runs in the client and is not sent to the game.");
		process_system_commands.setKey("process_system_commands");
		process_system_commands.setValue(true);
		servOptions.addOption(process_system_commands);
		
		BooleanOption echo_alias_updates = new BooleanOption();
		echo_alias_updates.setTitle("Echo Alias Updates?");
		echo_alias_updates.setDescription("When a dot-command changes an alias, print that change in the game window.");
		echo_alias_updates.setKey("echo_alias_updates");
		echo_alias_updates.setValue(true);
		servOptions.addOption(echo_alias_updates);
		
		BooleanOption process_semi = new BooleanOption();
		process_semi.setTitle("Process Semicolons?");
		process_semi.setDescription("A semicolon in what you send becomes a new line, so look;inventory goes out as two commands.");
		process_semi.setKey("process_semicolon");
		process_semi.setValue(true);
		servOptions.addOption(process_semi);
		
		BooleanOption keep_wifi_alive = new BooleanOption();
		keep_wifi_alive.setTitle("Keep Wifi Alive?");
		keep_wifi_alive.setDescription("Hold the Wi-Fi radio while connected. Does nothing on mobile data. CPU keep-alive is a separate option.");
		keep_wifi_alive.setKey("keep_wifi_alive");
		keep_wifi_alive.setValue(true);
		servOptions.addOption(keep_wifi_alive);

		BooleanOption keep_cpu_awake = new BooleanOption();
		keep_cpu_awake.setTitle("Keep CPU Awake?");
		keep_cpu_awake.setDescription("Leave the CPU awake while connected, so waits, triggers, and timers still fire with the screen off.");
		keep_cpu_awake.setKey("keep_cpu_awake");
		keep_cpu_awake.setValue(true);
		servOptions.addOption(keep_cpu_awake);

		ListOption notification_grouping = new ListOption();
		notification_grouping.setTitle("Notification stack");
		notification_grouping.setDescription("One notification stack for the connection, alerts, and chat, or a separate bar for each.");
		notification_grouping.setKey("notification_grouping");
		// Index 0 is the default and is what lands in the profile. Do not insert in the middle.
		notification_grouping.addItem("One stack");
		notification_grouping.addItem("Separate bars");
		notification_grouping.setValue(0);
		servOptions.addOption(notification_grouping);
		
		BooleanOption auto_reconnect = new BooleanOption();
		auto_reconnect.setTitle("Auto Reconnect?");
		auto_reconnect.setDescription("Reconnect automatically when the connection drops.");
		auto_reconnect.setKey("auto_reconnect");
		auto_reconnect.setValue(true);
		servOptions.addOption(auto_reconnect);
		
		IntegerOption auto_reconnect_limit = new IntegerOption();
		auto_reconnect_limit.setTitle("Auto Reconnect Tries");
		auto_reconnect_limit.setDescription("How many times reconnection will be attempted.");
		auto_reconnect_limit.setKey("auto_reconnect_limit");
		auto_reconnect_limit.setValue(new Integer(5));
		servOptions.addOption(auto_reconnect_limit);
		
		//auto_reconnect,
		//auto_reconnect_limit,
		
		BooleanOption cull_extraneous = new BooleanOption();
		cull_extraneous.setTitle("Cull Extraneous Colors?");
		cull_extraneous.setDescription("Drop colour codes that do not change the colour, so leftover codes do not sit in the text.");
		cull_extraneous.setKey("cull_extraneous_color");
		cull_extraneous.setValue(true);
		servOptions.addOption(cull_extraneous);
		
		BooleanOption debug_telnet = new BooleanOption();
		debug_telnet.setTitle("Debug Telnet?");
		debug_telnet.setDescription("Show telnet option negotiations in the game window.");
		debug_telnet.setKey("debug_telnet");
		debug_telnet.setValue(false);

		BooleanOption sgr1_weight = new BooleanOption();
		sgr1_weight.setTitle("Heavier MUD bold (SGR 1)?");
		sgr1_weight.setDescription("Also draw the game's bold letters heavier; off, bold is only the bright colour.");
		sgr1_weight.setKey("sgr1_weight");
		sgr1_weight.setValue(false);
		servOptions.addOption(sgr1_weight);
		
		BooleanOption show_regex_warning = new BooleanOption();
		show_regex_warning.setTitle("Regular Expression Warning?");
		show_regex_warning.setDescription("Show the warning message about regular expressions in the trigger editor.");
		show_regex_warning.setKey("show_regex_warning");
		show_regex_warning.setValue(true);
		servOptions.addOption(show_regex_warning);
		
		
		SettingsGroup protocolSwitches = new SettingsGroup();
		protocolSwitches.setTitle("Protocols");
		protocolSwitches.setDescription("GMCP, MCP, MXP, and telnet, and whether this world speaks them.");
		protocolSwitches.setKey("protocol_switches_group");

		BooleanOption use_gmcp = new BooleanOption();
		use_gmcp.setTitle("Use GMCP?");
		use_gmcp.setDescription("Let this world speak GMCP, and reconnect after changing it.");
		use_gmcp.setKey("use_gmcp");
		use_gmcp.setValue(false);
		protocolSwitches.addOption(use_gmcp);

		BooleanOption use_mcp = new BooleanOption();
		use_mcp.setTitle("Use MCP?");
		use_mcp.setDescription("Let this world speak MCP, and reconnect after changing it.");
		use_mcp.setKey("use_mcp");
		use_mcp.setValue(false);
		protocolSwitches.addOption(use_mcp);

		BooleanOption use_mxp = new BooleanOption();
		use_mxp.setTitle("Use MXP?");
		use_mxp.setDescription("Let this world speak MXP, and reconnect after changing it.");
		use_mxp.setKey("use_mxp");
		use_mxp.setValue(true);
		protocolSwitches.addOption(use_mxp);
		protocolSwitches.addOption(debug_telnet);

		SettingsGroup gmcpOptions = new SettingsGroup();
		gmcpOptions.setTitle("GMCP");
		gmcpOptions.setDescription("Modules, logging, and pictures this world receives over GMCP.");

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
		log_gmcp.setDescription("Write the GMCP handshake and every packet to logs/gmcp.log, and to the session log if that is on.");
		log_gmcp.setKey("log_gmcp");
		log_gmcp.setValue(false);
		gmcpOptions.addOption(log_gmcp);

		BooleanOption gmcp_feed = new BooleanOption();
		gmcp_feed.setTitle("Show GMCP in game window?");
		gmcp_feed.setDescription("Show each GMCP packet in the game window.");
		gmcp_feed.setKey("gmcp_feed");
		gmcp_feed.setValue(false);
		gmcpOptions.addOption(gmcp_feed);

		BooleanOption gmcp_suggest = new BooleanOption();
		gmcp_suggest.setTitle("Suggest modules when seen?");
		gmcp_suggest.setDescription("Tell you when the server sends a module you have not declared, and never turn one on for you.");
		gmcp_suggest.setKey("gmcp_suggest_modules");
		gmcp_suggest.setValue(true);
		gmcpOptions.addOption(gmcp_suggest);

		ListOption frame_images = new ListOption();
		frame_images.setTitle("Pictures the server sends");
		frame_images.setDescription("Draw a picture the server sends in a floating window, or in the game text where it scrolls away.");
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
		mcpOptions.setTitle("MCP");
		mcpOptions.setDescription("Packages, logging, and #$# lines for MCP.");

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
		log_mcp.setDescription("Write the MCP handshake and packets into the session log.");
		log_mcp.setKey("log_mcp");
		log_mcp.setValue(false);
		mcpOptions.addOption(log_mcp);

		BooleanOption mcp_feed = new BooleanOption();
		mcp_feed.setTitle("Show MCP in game window?");
		mcp_feed.setDescription("Show each MCP packet in the game window.");
		mcp_feed.setKey("mcp_feed");
		mcp_feed.setValue(false);
		mcpOptions.addOption(mcp_feed);

		BooleanOption mcp_omit = new BooleanOption();
		mcp_omit.setTitle("Omit MCP lines from output?");
		mcp_omit.setDescription("Hide #$# out-of-band lines from the game window (recommended), including when Use MCP? is off. Off = show raw MCP in the scrollback.");
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
		protocolOptions.setTitle("Telnet");
		protocolOptions.setDescription("Terminal type, compression, and the other telnet options beside GMCP and MCP.");
		protocolOptions.setKey("mud_protocols_group");

		BooleanOption use_mtts = new BooleanOption();
		use_mtts.setTitle("Use MTTS?");
		use_mtts.setDescription("Announce colour and UTF-8 to the server, and reconnect after changing it.");
		use_mtts.setKey("use_mtts");
		use_mtts.setValue(true);
		protocolOptions.addOption(use_mtts);

		BooleanOption use_msdp = new BooleanOption();
		use_msdp.setTitle("Use MSDP?");
		use_msdp.setDescription("Let the server send MSDP; corrupt packets are ignored.");
		use_msdp.setKey("use_msdp");
		use_msdp.setValue(false);
		protocolOptions.addOption(use_msdp);

		BooleanOption use_mssp = new BooleanOption();
		use_mssp.setTitle("Use MSSP?");
		use_mssp.setDescription("Let the server send its listing info, such as name and player count.");
		use_mssp.setKey("use_mssp");
		use_mssp.setValue(false);
		protocolOptions.addOption(use_mssp);

		BooleanOption use_mccp = new BooleanOption();
		use_mccp.setTitle("Use MCCP?");
		use_mccp.setDescription("Accept compressed text from the server, and reconnect after changing it.");
		use_mccp.setKey("use_mccp");
		use_mccp.setValue(true);
		protocolOptions.addOption(use_mccp);

		BooleanOption log_mxp = new BooleanOption();
		log_mxp.setTitle("Log MXP?");
		log_mxp.setDescription("Write MXP handshake notes into the session log.");
		log_mxp.setKey("log_mxp");
		log_mxp.setValue(false);
		protocolOptions.addOption(log_mxp);

		BooleanOption mxp_feed = new BooleanOption();
		mxp_feed.setTitle("Show MXP in game window?");
		mxp_feed.setDescription("Show MXP handshake and expire events in the game window.");
		mxp_feed.setKey("mxp_feed");
		mxp_feed.setValue(false);
		protocolOptions.addOption(mxp_feed);

		CallbackOption battery_opt = new CallbackOption();
		battery_opt.setTitle("Battery optimization…");
		battery_opt.setDescription("Ask Android not to kill BlowTorch in the background.");
		battery_opt.setKey("battery_optimization");
		battery_opt.setValue("battery_optimization");
		servOptions.addOption(battery_opt);

		protocolSwitches.addOption(gmcpOptions);
		protocolSwitches.addOption(mcpOptions);
		protocolSwitches.addOption(protocolOptions);

		SettingsGroup mapperOptions = new SettingsGroup();
		mapperOptions.setTitle("Mapper");
		mapperOptions.setDescription("Built-in MUD map recorder, pathfinding, and overlay.");

		BooleanOption mapper_enabled = new BooleanOption();
		mapper_enabled.setTitle("Enable Mapper?");
		mapper_enabled.setDescription("Master switch for recording and room sync.");
		mapper_enabled.setKey("mapper_enabled");
		mapper_enabled.setValue(true);
		mapperOptions.addOption(mapper_enabled);

		BooleanOption mapper_recording_default = new BooleanOption();
		mapper_recording_default.setTitle("Record by Default?");
		mapper_recording_default.setDescription("Start recording movement when a session loads.");
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
		mapper_path_auto_send.setDescription("When you ask the map for a path, send those commands to the game; off, only print the path.");
		mapper_path_auto_send.setKey("mapper_path_auto_send");
		mapper_path_auto_send.setValue(false);
		mapperOptions.addOption(mapper_path_auto_send);

		BooleanOption mapper_echo_window = new BooleanOption();
		mapper_echo_window.setTitle("Echo mapper status to game window?");
		mapper_echo_window.setDescription("Print mapper status lines in the game window; off, they stay in the map overlay.");
		mapper_echo_window.setKey("mapper_echo_window");
		mapper_echo_window.setValue(true);
		mapperOptions.addOption(mapper_echo_window);

		BooleanOption mapper_auto_reverse = new BooleanOption();
		mapper_auto_reverse.setTitle("Auto Reverse Links?");
		mapper_auto_reverse.setDescription("When recording a compass move, also create the opposite exit on the destination tile.");
		mapper_auto_reverse.setKey("mapper_auto_reverse_link");
		mapper_auto_reverse.setValue(true);
		mapperOptions.addOption(mapper_auto_reverse);

		BooleanOption mapper_one_way = new BooleanOption();
		mapper_one_way.setTitle("Accept One-Way Specials?");
		mapper_one_way.setDescription("Recording out, enter, or leave always places a new nearby tile; off, link back when exactly one room already leads here.");
		mapper_one_way.setKey("mapper_accept_one_way_specials");
		mapper_one_way.setValue(false);
		mapperOptions.addOption(mapper_one_way);

		BooleanOption mapper_use_gmcp = new BooleanOption();
		mapper_use_gmcp.setTitle("Use GMCP Room Sync?");
		mapper_use_gmcp.setDescription("Apply room data the server sends over GMCP to the map.");
		mapper_use_gmcp.setKey("mapper_use_gmcp");
		mapper_use_gmcp.setValue(true);
		mapperOptions.addOption(mapper_use_gmcp);

		CallbackOption mapper_gmcp_cfg = new CallbackOption();
		mapper_gmcp_cfg.setTitle("Configure Room Sync…");
		mapper_gmcp_cfg.setDescription("Choose how room data from the server updates the map.");
		mapper_gmcp_cfg.setKey("manage_mapper_gmcp");
		mapper_gmcp_cfg.setValue("manage_mapper_gmcp");
		mapperOptions.addOption(mapper_gmcp_cfg);

		StringOption mapper_gmcp_policy = new StringOption();
		mapper_gmcp_policy.setTitle("GMCP Sync Policy");
		mapper_gmcp_policy.setDescription("Follow only jumps; sync grows the map; strict overwrites unlocked room titles.");
		mapper_gmcp_policy.setKey("mapper_gmcp_policy");
		mapper_gmcp_policy.setValue("sync");
		mapperOptions.addOption(mapper_gmcp_policy);

		BooleanOption mapper_gmcp_use_num = new BooleanOption();
		mapper_gmcp_use_num.setTitle("GMCP: Match by room number?");
		mapper_gmcp_use_num.setDescription("Match a room by the number the server sends.");
		mapper_gmcp_use_num.setKey("mapper_gmcp_use_num");
		mapper_gmcp_use_num.setValue(true);
		mapperOptions.addOption(mapper_gmcp_use_num);

		BooleanOption mapper_gmcp_use_coords = new BooleanOption();
		mapper_gmcp_use_coords.setTitle("GMCP: Use absolute coordinates?");
		mapper_gmcp_use_coords.setDescription("Place a room at the coordinates the server sends only when it is next to the previous one.");
		mapper_gmcp_use_coords.setKey("mapper_gmcp_use_coords");
		mapper_gmcp_use_coords.setValue(false);
		mapperOptions.addOption(mapper_gmcp_use_coords);

		BooleanOption mapper_gmcp_grow = new BooleanOption();
		mapper_gmcp_grow.setTitle("GMCP: Auto-grow map?");
		mapper_gmcp_grow.setDescription("Create rooms and exits from the room data the server sends.");
		mapper_gmcp_grow.setKey("mapper_gmcp_grow");
		mapper_gmcp_grow.setValue(true);
		mapperOptions.addOption(mapper_gmcp_grow);

		BooleanOption mapper_gmcp_create_exits = new BooleanOption();
		mapper_gmcp_create_exits.setTitle("GMCP: Create exit neighbors?");
		mapper_gmcp_create_exits.setDescription("Create missing exits from the room data the server sends, and do not delete exits.");
		mapper_gmcp_create_exits.setKey("mapper_gmcp_create_exits");
		mapper_gmcp_create_exits.setValue(true);
		mapperOptions.addOption(mapper_gmcp_create_exits);

		StringOption mapper_toolbar = new StringOption();
		mapper_toolbar.setTitle("Toolbar Actions (CSV)");
		mapper_toolbar.setDescription("Which buttons sit on the left of the map, as a comma-separated list.");
		mapper_toolbar.setKey("mapper_toolbar_actions");
		mapper_toolbar.setValue("record,follow,level-,level+,find,undo,center,close");
		mapperOptions.addOption(mapper_toolbar);

		StringOption mapper_capture_title = new StringOption();
		mapper_capture_title.setTitle("Capture Title Regex");
		mapper_capture_title.setDescription("The pattern that picks a room title out of the game text.");
		mapper_capture_title.setKey("mapper_capture_title_regex");
		mapper_capture_title.setValue("^([A-Z].*)$");
		mapperOptions.addOption(mapper_capture_title);

		StringOption mapper_capture_exits = new StringOption();
		mapper_capture_exits.setTitle("Capture Exits Regex");
		mapper_capture_exits.setDescription("The pattern that picks the exits line out of the game text.");
		mapper_capture_exits.setKey("mapper_capture_exits_regex");
		mapper_capture_exits.setValue("(?i)exits?:\\s*(.*)");
		mapperOptions.addOption(mapper_capture_exits);

		StringOption mapper_level_up = new StringOption();
		mapper_level_up.setTitle("Level-Up Commands (CSV)");
		mapper_level_up.setDescription("While recording, these commands create a higher floor.");
		mapper_level_up.setKey("mapper_level_up_commands");
		mapper_level_up.setValue(MapDirections.DEFAULT_LEVEL_UP_COMMANDS);
		mapperOptions.addOption(mapper_level_up);

		StringOption mapper_level_down = new StringOption();
		mapper_level_down.setTitle("Level-Down Commands (CSV)");
		mapper_level_down.setDescription("While recording, these commands create a lower floor.");
		mapper_level_down.setKey("mapper_level_down_commands");
		mapper_level_down.setValue(MapDirections.DEFAULT_LEVEL_DOWN_COMMANDS);
		mapperOptions.addOption(mapper_level_down);

		StringOption mapper_moves = new StringOption();
		mapper_moves.setTitle("Move Effects (advanced)");
		mapper_moves.setDescription("How each move changes the map; empty uses the built-in defaults.");
		mapper_moves.setKey("mapper_move_effects");
		mapper_moves.setValue(MapDirections.defaultMoveEffectsString());
		mapperOptions.addOption(mapper_moves);

		mExtraTextOptions = new SettingsGroup();
		mExtraTextOptions.setTitle("Extra text windows");
		mExtraTextOptions.setKey("extra_text_group");
		mExtraTextOptions.setDescription(
				"Extra panes for lines you send to a named slot.");

		BooleanOption extra_text_enabled = new BooleanOption();
		extra_text_enabled.setTitle("Enable Extra Text Windows?");
		extra_text_enabled.setDescription("Show the extra text windows; turning this off keeps their definitions.");
		extra_text_enabled.setKey("extra_text_windows_enabled");
		extra_text_enabled.setValue(true);
		mExtraTextOptions.addOption(extra_text_enabled);

		CallbackOption manage_extra_text = new CallbackOption();
		manage_extra_text.setTitle("Manage windows…");
		manage_extra_text.setDescription(
				"Add, remove, or edit extra text windows. A GMCP route needs Use GMCP on under Protocols.");
		manage_extra_text.setKey("manage_extra_text_windows");
		manage_extra_text.setValue("manage_extra_text_windows");
		mExtraTextOptions.addOption(manage_extra_text);

		CallbackOption show_last_list = new CallbackOption();
		show_last_list.setTitle("Recent commands…");
		show_last_list.setDescription(
				"Settings for this world's command-history window: how many lines, and how they are drawn.");
		show_last_list.setKey("show_last_list");
		show_last_list.setValue("show_last_list");
		mExtraTextOptions.addOption(show_last_list);

		StringOption extra_text_windows = new StringOption();
		extra_text_windows.setTitle("Windows JSON");
		extra_text_windows.setDescription("The saved list of extra text windows; Manage windows… is the usual way to change it.");
		extra_text_windows.setKey("extra_text_windows");
		extra_text_windows.setValue("[]");
		mExtraTextOptions.addOption(extra_text_windows);

		mGaugeWidgetsOptions = new SettingsGroup();
		mGaugeWidgetsOptions.setTitle("Widgets");
		mGaugeWidgetsOptions.setKey("gauge_widgets_group");
		mGaugeWidgetsOptions.setDescription(
				"HP, mana and timer gauges over the game.");

		BooleanOption gauge_widgets_enabled = new BooleanOption();
		gauge_widgets_enabled.setTitle("Enable overlay gauges?");
		gauge_widgets_enabled.setDescription(
				"Show the overlay gauges; turning this off keeps their definitions.");
		gauge_widgets_enabled.setKey(GaugeWidgetsStore.ENABLED_KEY);
		gauge_widgets_enabled.setValue(true);
		mGaugeWidgetsOptions.addOption(gauge_widgets_enabled);

		CallbackOption manage_gauge_widgets = new CallbackOption();
		manage_gauge_widgets.setTitle("Manage widgets…");
		manage_gauge_widgets.setDescription(
				"Add, remove, or edit overlay gauges (hbar / vbar / ring / timer; "
				+ "manual, GMCP, MCP, variable, or .timer source).");
		manage_gauge_widgets.setKey("manage_gauge_widgets");
		manage_gauge_widgets.setValue("manage_gauge_widgets");
		mGaugeWidgetsOptions.addOption(manage_gauge_widgets);

		StringOption gauge_widgets = new StringOption();
		gauge_widgets.setTitle("Widgets JSON");
		gauge_widgets.setDescription(
				"The saved list of overlay gauges; Manage widgets… is the usual way to change it.");
		gauge_widgets.setKey(GaugeWidgetsStore.SETTING_KEY);
		gauge_widgets.setValue("[]");
		mGaugeWidgetsOptions.addOption(gauge_widgets);

		SettingsGroup miscOptions = new SettingsGroup();
		miscOptions.setTitle("Files");
		miscOptions.setDescription("Import, export, reset, and where settings files go.");

		StringOption default_settings_directory = new StringOption();
		default_settings_directory.setTitle("Default Settings Directory");
		default_settings_directory.setDescription("Folder for import and export; blank uses /BlowTorch/settings/.");
		default_settings_directory.setKey("default_settings_directory");
		default_settings_directory.setValue("");
		miscOptions.addOption(default_settings_directory);

		CallbackOption export_settings = new CallbackOption();
		export_settings.setTitle("Export Settings");
		export_settings.setDescription("Write this world's settings to a file you choose.");
		export_settings.setKey("export_settings");
		export_settings.setValue("export_settings");
		miscOptions.addOption(export_settings);

		CallbackOption import_settings = new CallbackOption();
		import_settings.setTitle("Import Settings");
		import_settings.setDescription("Replace this world's saved profile with the file you pick, as soon as you pick it, with no second confirm.");
		import_settings.setKey("import_settings");
		import_settings.setValue("import_settings");
		miscOptions.addOption(import_settings);

		CallbackOption reset_settings = new CallbackOption();
		reset_settings.setTitle("Reset Settings");
		reset_settings.setDescription("Throw away this world's settings and start from the defaults, after a confirm.");
		reset_settings.setKey("reset_settings");
		reset_settings.setValue("reset_settings");
		miscOptions.addOption(reset_settings);

		CallbackOption request_storage = new CallbackOption();
		request_storage.setTitle("Manage Storage Access");
		request_storage.setDescription("Grant All files access so BlowTorch can use /BlowTorch/ outside the app's private folder.");
		request_storage.setKey("request_storage_access");
		request_storage.setValue("request_storage_access");
		miscOptions.addOption(request_storage);

		IntegerOption overflow_opacity = new IntegerOption();
		IntegerOption tap_menu_opacity = new IntegerOption();
		tap_menu_opacity.setTitle("Tapped-word menu opacity (%)");
		tap_menu_opacity.setDescription("How solid the menu is when a tapped word has more than one action, from 20 to 100.");
		tap_menu_opacity.setKey("tap_menu_opacity");
		tap_menu_opacity.setValue(
				com.resurrection.blowtorch2.lib.window.MainWindow.DEFAULT_TAP_MENU_OPACITY);
		miscOptions.addOption(tap_menu_opacity);

		ListOption overflow_corner = new ListOption();
		overflow_corner.setTitle("Overflow button corner");
		overflow_corner.setDescription("Which corner the ⋮ sits in.");
		overflow_corner.setKey("overflow_button_corner");
		// Added in this order: the values are indices into this list, and they are
		// what lands in the profile. Anything inserted in the middle renames every
		// saved choice after it.
		overflow_corner.addItem("Bottom right");
		overflow_corner.addItem("Bottom left");
		overflow_corner.addItem("Top right");
		overflow_corner.addItem("Top left");
		overflow_corner.setValue(
				com.resurrection.blowtorch2.lib.window.OverflowButtonCorner.DEFAULT);
		miscOptions.addOption(overflow_corner);

		overflow_opacity.setTitle("Overflow button opacity (%)");
		overflow_opacity.setDescription("How solid the ⋮ is drawn ("
				+ OVERFLOW_OPACITY_MIN + "–100).");
		overflow_opacity.setKey("overflow_button_opacity");
		overflow_opacity.setValue(OVERFLOW_OPACITY_DEFAULT);
		miscOptions.addOption(overflow_opacity);

		BooleanOption overflow_background = new BooleanOption();
		overflow_background.setTitle("Overflow button background?");
		overflow_background.setDescription("Draw the dark disc behind the ⋮.");
		overflow_background.setKey("overflow_button_background");
		overflow_background.setValue(true);
		miscOptions.addOption(overflow_background);

		BooleanOption overflow_border = new BooleanOption();
		overflow_border.setTitle("Overflow button ring?");
		overflow_border.setDescription("Draw the thin circle around the ⋮.");
		overflow_border.setKey("overflow_button_border");
		overflow_border.setValue(true);
		miscOptions.addOption(overflow_border);

		BooleanOption persistent_connection = new BooleanOption();
		persistent_connection.setTitle("Persistent Connection?");
		persistent_connection.setDescription("After a brief network drop, wait for connectivity before retrying, and treat a closed socket as that kind of drop.");
		persistent_connection.setKey("persistent_connection");
		persistent_connection.setValue(false);
		servOptions.addOption(persistent_connection);

		// Update checks live in the launcher overflow, not in a connection profile.

		// Dump persist comparisons in ConnectionSetttingsParser.dumpOptions must
		// match these defaults. A missing switch case leaves dooutput false and
		// the key is silently never saved.
		SettingsGroup chatOptions = new SettingsGroup();
		chatOptions.setTitle("Chat");
		chatOptions.setDescription("Unread mark, a line in the game window, and Android notifications when a thread gets new messages.");

		BooleanOption chat_unread_dot = new BooleanOption();
		chat_unread_dot.setTitle("Unread mark on ⋮");
		chat_unread_dot.setDescription("Show a disc on the overflow button while a conversation has unread messages and the drawer is closed.");
		chat_unread_dot.setKey("chat_unread_dot");
		chat_unread_dot.setValue(true);
		chatOptions.addOption(chat_unread_dot);

		ListOption chat_announce = new ListOption();
		chat_announce.setTitle("New-message line in the game window");
		chat_announce.setDescription("Off, a line for every new message, or one digest line after the interval.");
		chat_announce.setKey("chat_announce");
		// Added in this order: the values are indices into this list and they are
		// what lands in the profile. Nothing may be inserted in the middle.
		chat_announce.addItem("Off");
		chat_announce.addItem("Every message");
		chat_announce.addItem("Digest");
		chat_announce.setValue(0);
		chatOptions.addOption(chat_announce);

		IntegerOption chat_announce_seconds = new IntegerOption();
		chat_announce_seconds.setTitle("Digest interval (seconds)");
		chat_announce_seconds.setDescription("How long to wait between digest lines, in seconds.");
		chat_announce_seconds.setKey("chat_announce_seconds");
		chat_announce_seconds.setValue(60);
		chatOptions.addOption(chat_announce_seconds);

		BooleanOption chat_android_notify = new BooleanOption();
		chat_android_notify.setTitle("Android notification for new chat");
		chat_android_notify.setDescription("A phone notification when a conversation gets new lines, and a tap opens that chat.");
		chat_android_notify.setKey("chat_android_notify");
		chat_android_notify.setValue(false);
		chatOptions.addOption(chat_android_notify);

		IntegerOption chat_max_messages = new IntegerOption();
		chat_max_messages.setTitle("Keep at most this many chat messages");
		chat_max_messages.setDescription("Oldest lines are dropped first, across every conversation in this world. Default 4000. 0 means no practical limit (still stops at 50000 so the phone does not run out of memory).");
		chat_max_messages.setKey("chat_max_messages");
		chat_max_messages.setValue(4000);
		chatOptions.addOption(chat_max_messages);

		SettingsGroup bellOptions = new SettingsGroup();
		bellOptions.setTitle("Sound");
		bellOptions.setDescription("The bell, trigger sounds, and silence while you type.");
		
		BooleanOption bell_vibrate = new BooleanOption();
		bell_vibrate.setTitle("Vibrate?");
		bell_vibrate.setDescription("A short vibration when the bell character arrives.");
		bell_vibrate.setKey("bell_vibrate");
		bell_vibrate.setValue(true);
		bellOptions.addOption(bell_vibrate);
		
		ListOption trigger_sound_stream = new ListOption();
		trigger_sound_stream.setTitle("Trigger sounds play on");
		trigger_sound_stream.setDescription("Which volume a trigger's Play a Sound action uses.");
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
		trigger_sound_warn.setDescription("Show a short message when a trigger plays a sound while that volume is all the way down.");
		trigger_sound_warn.setKey("trigger_sound_warn_silent");
		trigger_sound_warn.setValue(true);
		bellOptions.addOption(trigger_sound_warn);

		BooleanOption bell_notification = new BooleanOption();
		bell_notification.setTitle("Generate Notification?");
		bell_notification.setDescription("A phone notification when the bell character arrives.");
		bell_notification.setKey("bell_notification");
		bell_notification.setValue(false);
		bellOptions.addOption(bell_notification);
		
		BooleanOption bell_display = new BooleanOption();
		bell_display.setTitle("Display Bell?");
		bell_display.setDescription("A small alert on the screen when the bell character arrives.");
		bell_display.setKey("bell_display");
		bell_display.setValue(false);
		bellOptions.addOption(bell_display);
		bellOptions.addOption(speak_quiet_typing);

		SettingsGroup panes = new SettingsGroup();
		panes.setTitle("Panes");
		panes.setKey("panes_group");
		panes.setDescription("Extra text windows, gauges, the prompt line, floating buttons.");
		panes.addOption(mExtraTextOptions);
		panes.addOption(mGaugeWidgetsOptions);
		panes.addOption(prompt_bar);
		panes.addOption(floating_buttons_enabled);

		sg.addOption(input);
		sg.addOption(suggestions);
		sg.addOption(globalGestures);
		sg.addOption(panes);
		sg.addOption(chatOptions);
		sg.addOption(bellOptions);
		sg.addOption(servOptions);
		sg.addOption(protocolSwitches);
		sg.addOption(mapperOptions);
		sg.addOption(device);
		sg.addOption(miscOptions);

		this.getSettings().setOptions(sg);
	}

	/** Extra text windows settings group (nested under Panes). */
	public SettingsGroup getExtraTextOptionsGroup() {
		return mExtraTextOptions;
	}

	/** Overlay gauge settings group (nested under Panes). */
	public SettingsGroup getGaugeWidgetsOptionsGroup() {
		return mGaugeWidgetsOptions;
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

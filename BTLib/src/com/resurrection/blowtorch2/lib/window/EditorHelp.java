package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.R;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

/**
 * {@code ?} help for the alias / trigger / timer lists. Keep in step with
 * {@code docs/user-manual.md}; this is the short form.
 */
public final class EditorHelp {

	private EditorHelp() {
	}

	/**
	 * Show one of these texts, or an editor's own, in a scrollable box.
	 *
	 * @param context Dialog context.
	 * @param title Heading, e.g. "Triggers".
	 * @param body The text.
	 */
	public static void show(final Context context, final String title, final String body) {
		if (context == null) {
			return;
		}
		final Dialog dialog = new Dialog(context, EditorDialogChrome.dialogTheme());
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		Window window = dialog.getWindow();
		if (window != null) {
			window.setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		}

		float density = context.getResources().getDisplayMetrics().density;
		int titleHeight = Math.round(42 * density);
		int barPad = Math.round(6 * density);
		int bodyPad = Math.round(16 * density);
		int minButton = Math.round(44 * density);

		LinearLayout shell = new LinearLayout(context);
		shell.setOrientation(LinearLayout.VERTICAL);
		shell.setBackgroundColor(ContextCompat.getColor(context, R.color.chrome_body));

		TextView titleView = new TextView(context);
		titleView.setText(title);
		titleView.setAllCaps(true);
		titleView.setTextColor(ContextCompat.getColor(context, R.color.chrome_title_text));
		titleView.setBackgroundColor(ContextCompat.getColor(context, R.color.chrome_title_bar));
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		titleView.setTypeface(Typeface.DEFAULT_BOLD);
		titleView.setGravity(Gravity.CENTER);
		shell.addView(titleView, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, titleHeight));

		TextView bodyView = new TextView(context);
		bodyView.setPadding(bodyPad, bodyPad, bodyPad, bodyPad);
		bodyView.setTextIsSelectable(true);
		bodyView.setText(emphasize(body));
		bodyView.setTextColor(ContextCompat.getColor(context, R.color.chrome_title_text));
		bodyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);

		ScrollView scroll = new ScrollView(context);
		scroll.addView(bodyView);
		LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
		shell.addView(scroll, scrollLp);

		LinearLayout footer = new LinearLayout(context);
		footer.setOrientation(LinearLayout.HORIZONTAL);
		footer.setGravity(Gravity.CENTER);
		footer.setPadding(barPad, barPad, barPad, barPad);
		footer.setBackgroundColor(ContextCompat.getColor(context, R.color.chrome_title_bar));

		Button close = new Button(context);
		close.setText("Close");
		close.setMinHeight(minButton);
		close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		footer.addView(close, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		shell.addView(footer, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		dialog.setContentView(shell, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		dialog.setCanceledOnTouchOutside(true);
		EditorDialogChrome.applyFloatingWrapContentHeight(dialog);
		dialog.show();
	}

	/** A **word** is the option. The lines under it are the note. Markers are not shown. */
	static final class Emphasis {
		final String text;
		final int[] starts;
		final int[] ends;

		private Emphasis(final String text, final int[] starts, final int[] ends) {
			this.text = text;
			this.starts = starts;
			this.ends = ends;
		}
	}

	static Emphasis emphasis(final String body) {
		if (body == null || body.indexOf("**") < 0) {
			return new Emphasis(body == null ? "" : body, new int[0], new int[0]);
		}
		StringBuilder text = new StringBuilder();
		int[] starts = new int[4];
		int[] ends = new int[4];
		int count = 0;
		int i = 0;
		while (i < body.length()) {
			int open = body.indexOf("**", i);
			if (open < 0) {
				text.append(body.substring(i));
				break;
			}
			text.append(body.substring(i, open));
			int close = body.indexOf("**", open + 2);
			if (close < 0) {
				text.append(body.substring(open));
				break;
			}
			int start = text.length();
			text.append(body.substring(open + 2, close));
			if (count == starts.length) {
				starts = grow(starts);
				ends = grow(ends);
			}
			starts[count] = start;
			ends[count] = text.length();
			count++;
			i = close + 2;
		}
		return new Emphasis(text.toString(), copy(starts, count), copy(ends, count));
	}

	private static int[] grow(final int[] values) {
		int[] next = new int[values.length * 2];
		System.arraycopy(values, 0, next, 0, values.length);
		return next;
	}

	private static int[] copy(final int[] values, final int count) {
		int[] out = new int[count];
		System.arraycopy(values, 0, out, 0, count);
		return out;
	}

	static CharSequence emphasize(final String body) {
		Emphasis mark = emphasis(body);
		if (mark.starts.length == 0) {
			return mark.text;
		}
		SpannableStringBuilder out = new SpannableStringBuilder(mark.text);
		for (int i = 0; i < mark.starts.length; i++) {
			out.setSpan(new StyleSpan(Typeface.BOLD), mark.starts[i], mark.ends[i],
					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		return out;
	}

	public static final String HOLD_AND_REPEAT =
			"A hold command and Repeat cannot both be on. Typing a hold command turns Repeat off. "
					+ "Turning Repeat on clears the hold command.";

	public static final String QUIET_CAPTION =
			"Show this key's line, including when Local Echo? is off. Off, the game still receives it.";

	public static final String PICKER_TITLE = "Follow a button";

	public static final String USE_BUTTON =
			"Follow a button that already exists on your screen";

	public static final String SHORTCUTS =
			"A shortcut sends a line when you press a hardware key. The keys on the screen do not.\n\n"
			+ "**Partially experimental**\n"
			+ "Android does not read every keyboard the same way. What a key does also depends on "
			+ "the keyboard app installed on the phone.\n\n"
			+ "**Switches**\n"
			+ "Options → Typing → Hardware keyboard holds Hardware keyboard shortcuts, "
			+ "Walk with the numeric keypad, Number row walks too, "
			+ "Direction keys type while a line is being edited, and Keep Ctrl editing keys. "
			+ "Up finds a command that starts with what you typed stays on Typing.\n\n"
			+ "**Name**\n"
			+ "What this list shows. Leave it blank and the list shows the key.\n\n"
			+ "**Row switch**\n"
			+ "The switch on a row in the list turns that shortcut off. The row stays. "
			+ "That key then types as usual. You can replace the key or the line.\n\n"
			+ "**Press a key**\n"
			+ "Tap Key to start recording. Tap that row again when the key on it is the one you want. "
			+ "The name can be typed before that. It stays nothing when the phone ate that key.\n\n"
			+ "**Send a line**\n"
			+ "Uses the tap line on a short press. Ctrl+K can send kill goblin. "
			+ "A tap line of .kb insert say puts say in the bar. F1 starts empty. "
			+ "A tap line of .kb flush sends the bar. .stepback sends the reverse of the last number-pad walk. "
			+ "Until that pad has walked, .stepback sends nothing.\n\n"
			+ "**Hold command**\n"
			+ "Waits 450 ms, then sends the hold line once.\n\n"
			+ "**Repeat**\n"
			+ "Sends the tap line immediately and again every 200 ms until you let go. "
			+ HOLD_AND_REPEAT + "\n\n"
			+ "**Clear the bar**\n"
			+ "Escape puts a recalled line's draft back. With .prefix on, type ki, "
			+ "Up shows kill goblin, and Escape puts ki back. Escape again clears the bar. "
			+ "An empty bar hides the keyboard. A dialog keeps Escape.\n\n"
			+ "**Local echo**\n"
			+ QUIET_CAPTION + " A password prompt still hides it.\n\n"
			+ "**" + USE_BUTTON + "**\n"
			+ "The key keeps sending that button's current command and its hold line. "
			+ "Changing the button changes this key. "
			+ "The list shows the id, and the search box matches it. "
			+ "It also matches the words on the button, its name, its command, its hold line, "
			+ "and the set name. Every set starts closed. Tap the set to open it.\n\n"
			+ "**Number pad**\n"
			+ "The pinned row at the top. Done writes those rows. "
			+ "Compass words is a preset you apply. It fills 8 with north. Until Done, 8 still types. "
			+ "None walks on the bare number. Alt walks on Alt plus the number, and the bare number types. "
			+ "Ctrl walks on Ctrl plus the number, and the bare number types. "
			+ "Look is a second layer. With Alt walking, Ctrl+8 sends look north. "
			+ "The walk-lock button sends .shortcuts toggle. The bar shows Pad walks.\n\n"
			+ "**Up**\n"
			+ ".prefix is off at the start. Up steps through every command. "
			+ ".prefix on: type ki and Up finds kill goblin.\n\n"
			+ "**Ctrl editing**\n"
			+ "Ctrl+Left moves back one word. Keep Ctrl editing keys, off, lets a shortcut on that chord run.\n\n"
			+ "**Typing**\n"
			+ ".shortcuts off sends keys into the bar. Right Alt+a still inserts ą. "
			+ "Ctrl and Alt together are not a shortcut unless that exact chord was bound.\n\n"
			+ "**Scrolling**\n"
			+ ".scrollnew jumps to the newest text. Ctrl+Enter and Ctrl+End start as that line. "
			+ "Enter still sends the line. "
			+ ".scrollv 10 moves toward the text that was above. .scrollh 10 moves to the right. "
			+ ".kb start and .kb end move the caret.\n\n"
			+ "**.shortcuts**\n"
			+ ".shortcuts on turns them on. .shortcuts off turns them off. .shortcuts with no "
			+ "argument prints on or off and does not change it. .shortcuts edit opens this list.\n\n"
			+ KeyboardShortcuts.NOTICE;

	/** The same explanation as {@link #SHORTCUTS}, without the bold marks. */
	public static String shortcutsPlain() {
		return SHORTCUTS.replace("**", "");
	}

	public static final String KEYBOARD =
			"**Pad walks**\n"
			+ "The number pad sends directions, the words from the Number pad form. "
			+ "The bar shows Pad walks. Alt or Ctrl alone turns that on when the pad's modifier is that key. "
			+ "The same key turns it off. It is not a second list of shortcuts.\n\n"
			+ "**Arrows**\n"
			+ "Up and Down stay in the input bar. They step through what you sent. "
			+ ".prefix on keeps them on commands that start with the bar. "
			+ "They do not move to Send, Edit, or the overflow button.\n\n"
			+ "**Newest text**\n"
			+ ".scrollnew jumps there. Put that line on a key. "
			+ "Ctrl+Enter and Ctrl+End start as that line. Enter still sends what you typed.\n\n"
			+ "**Local echo**\n"
			+ QUIET_CAPTION + " A password prompt still hides it.\n\n"
			+ "**A button**\n"
			+ "Choosing one follows that button. The key sends whatever the button sends now. "
			+ "The list shows the id. Sets start closed. Tap a set to open it.\n";

	public static final String NUMBER_PAD =
			"**Nothing is saved until Done.**\n\n"
			+ "**Compass words**\n"
			+ "Fills north, northwest, and the rest.\n\n"
			+ "**Short steps**\n"
			+ "Fills n, nw, and the rest.\n\n"
			+ "**Fill from Speedwalk Directions**\n"
			+ "Uses the command set for each direction. "
			+ "A letter that only shortens a run is not that command.\n\n"
			+ "Those three leave * and / empty, and they leave Enter alone.\n\n"
			+ "**Modifier**\n"
			+ "None walks on the bare number. Alt walks on Alt plus the number, and the bare number types. "
			+ "Ctrl walks on Ctrl plus the number, and the bare number types.\n\n"
			+ "**Look**\n"
			+ KeyboardShortcutPad.lookCaption(0) + " When the walk modifier is Alt, "
			+ KeyboardShortcutPad.lookCaption(KeyboardShortcuts.MOD_ALT) + " Look sends once.\n\n"
			+ "**Cells**\n"
			+ "Tap a cell. The field under the grid is that key. "
			+ "A cell shows its face and its command. An empty command is not a shortcut. "
			+ "* and / start empty. Enter shows " + KeyboardShortcutPad.SEND_CURRENT_LINE
			+ ". Clearing it removes that row.\n\n"
			+ "**Walk lock**\n"
			+ KeyboardShortcutPad.latchCaption(0) + " "
			+ KeyboardShortcutPad.latchCaption(KeyboardShortcuts.MOD_ALT) + " "
			+ KeyboardShortcutPad.latchCaption(KeyboardShortcuts.MOD_CTRL) + "\n\n"
			+ "**When the pad walks**\n"
			+ "Options → Typing → Hardware keyboard, Walk with the numeric keypad.";

	public static final String ALIASES =
			"An alias rewrites what YOU type, before it leaves the phone. It never "
			+ "sees anything the game says -- that is a trigger.\n\n"
			+ "THE TWO FIELDS\n"
			+ "Replace: what you type. With: what is sent instead.\n\n"
			+ "    Replace  k\n"
			+ "    With     kill\n"
			+ "    You type: k goblin   Sent: kill goblin\n\n"
			+ "START AND END OF LINE\n"
			+ "The two checkboxes add ^ and $ to the pattern, and they change how the "
			+ "captures work:\n"
			+ "  neither: the pattern is matched as a word anywhere in the line\n"
			+ "  both:    $1 is the first (…) group of your pattern\n"
			+ "    Replace  ^cast (.+)$    With  c $1\n"
			+ "    You type: cast fireball   Sent: c fireball\n"
			+ "  only ^:  the line is split on spaces -- $0 is the first word, $1 the "
			+ "next, and so on\n\n"
			+ "CHANGING ONE WHILE YOU PLAY\n"
			+ "For a simple name (letters, digits, _) type the name with a dot and the "
			+ "new text:\n"
			+ "    .k kill                 sets alias k to send kill\n"
			+ "A pattern with spaces or ^…$ has to be edited here instead.\n\n"
			+ "LOCAL ECHO\n"
			+ "Whether the expanded line is shown back to you. Use client setting "
			+ "follows Options; Always show and Always hide override it for this one "
			+ "alias. A password prompt still hides everything, whatever this says.\n\n"
			+ "SET VARIABLE\n"
			+ "Same action as on a trigger. It runs when the alias matches, in addition "
			+ "to With. Set / Add number / Subtract / Append / Unset, and Keep after "
			+ "restart. $1 is what you typed, not a line from the game.\n\n"
			+ "    Replace  ^kk           With  kill $1\n"
			+ "    Set Variable  target = $1,  kills  Add 1\n"
			+ "    You type: kk goblin   Sent: kill goblin\n"
			+ "    and target is goblin, kills goes up by one.\n\n"
			+ "ENABLED\n"
			+ "An alias switched off stops rewriting what you type. A trigger whose "
			+ "pattern borrows this alias's text still works -- it is only borrowing "
			+ "the words.\n\n"
			+ "A trigger can use an alias's text as its pattern, so one alias is the "
			+ "single place a word is written down. See the ? in the trigger editor.";

	/**
	 * Order / Who else fires. Shared by the Triggers list {@code ?} and the
	 * trigger editor {@code ?}, which is the screen that has the Order box.
	 */
	public static final String TRIGGER_ORDER =
			"ORDER\n"
			+ "Smaller number runs first. 10 is the default for every new trigger, "
			+ "so a replace you never numbered is at 10. 9 is not \"on top\": it "
			+ "runs before that replace, and the replace can wipe the colour. A "
			+ "colour at 11 already runs after a default replace; 20 is only a gap "
			+ "so you can slot something in between. A channel rewrite at 10 and a "
			+ "word colour at 11: CORPCHAT: Name opens north keeps the channel "
			+ "colour except opens is yellow. Two triggers with the same number run "
			+ "A to Z by name, and the editor says when they share it — split the "
			+ "numbers if that order is wrong. Keep going? does not set this order. "
			+ "Lines that arrive together run from the top. On each line, this "
			+ "number decides which trigger runs.\n\n"
			+ "The Triggers list shows the number on each row. Options (=) can sort "
			+ "by sequence instead of group, name. Sorting All by sequence is not "
			+ "the same as fire order (on each line, main runs, then each plugin). The Order "
			+ "sentence is inside one plugin — Fires first means first in that set.\n\n"
			+ "WHO ELSE FIRES\n"
			+ "Type a game line under Order. Other enabled triggers that also match "
			+ "that line are listed with their numbers, so you can see the replace "
			+ "is at 10 and put the colour at 11. It tries one line. It does not "
			+ "compare patterns.\n\n";

	public static final String TRIGGERS =
			"A trigger matches a line the GAME sent, and runs its actions. It never "
			+ "sees what you type -- that is an alias.\n\n"
			+ "PATTERN\n"
			+ "Literal? on: plain text, matched exactly. Off: a regular expression, "
			+ "where (…) captures become $1, $2 in the actions.\n\n"
			+ "    Pattern  You hit (.+) for (\\d+)\n"
			+ "    Ack      emote crushed $1 ($2 dmg)\n\n"
			+ "    Pattern  A (.+) appears\n"
			+ "    Ack      kill $1\n\n"
			+ "The preview under the pattern box says what your pattern will really "
			+ "do, and the ? in the trigger editor explains the box in full -- "
			+ "including using an alias's text as the pattern.\n\n"
			+ "TWO PATTERNS ON ONE LINE\n"
			+ "Conditions → Only if trigger matches this line looks at another "
			+ "trigger's Pattern and Match style, without running its actions. A gag "
			+ "on -- can require that [chan]: would also match.\n\n"
			+ "MORE THAN ONE LINE\n"
			+ "Write \\n where the line break is, and one trigger matches a block:\n\n"
			+ "    Pattern  You see (.+) here\\.\\nIt looks (\\w+)\n"
			+ "    Ack      get $1\n"
			+ "    ($1 comes from the first line, $2 from the second)\n\n"
			+ "Two rules. A dot never crosses a line break, so .+ stops at the end of "
			+ "its line and every break has to be written out -- that is what stops a "
			+ "greedy pattern eating the screen. And ^ $ bind to each line, which is "
			+ "the readable way to write a block:\n\n"
			+ "    Pattern  ^\\+-+\\+$\\n^\\| (.+) \\|$\\n^\\+-+\\+$\n"
			+ "    Gag\n"
			+ "    (a three-line box, gone in one trigger, $1 is what was inside)\n\n"
			+ "A Gag on a multi-line pattern takes the whole block, and Send to window "
			+ "forwards all of it. Color still marks only the first line.\n\n"
			+ "ACTIONS\n"
			+ "    Ack             send a command back to the game\n"
			+ "    Replace / Gag   change or hide the line\n"
			+ "    Color           256 grid or RGB wheel; text and background off separately; italic/underline\n"
			+ "                    Bold is heavier glyphs (cells stay put), not MUD [1m bright.\n"
			+ "                    Options → Window → Text → Heavier MUD bold (SGR 1)? also redraws the world's [1m heavier\n"
			+ "    Tappable Word   make the match pressable\n"
			+ "    Toast / Notification   tell the phone; the shade title is the world name, then your title\n"
			+ "    Set Variable    remember something for later\n\n"
			+ "CONDITIONS\n"
			+ "An extra gate after the pattern matches, not a replacement for it. "
			+ "Empty means always fire. Example: Only if trigger is ON, pointed at a "
			+ "trigger called combat_mode, so a set of triggers turns on and off "
			+ "together. Only if trigger matches this line looks at another trigger's "
			+ "Pattern and Match style, without running its "
			+ "actions — so a gag on -- can require that [chan]: would also match.\n\n"
			+ "GROUP\n"
			+ "A label like combat. The list shows and sorts by it, and\n"
			+ "    .trigger group off combat\n"
			+ "moves the whole set at once. One trigger at a time:\n"
			+ "    .trigger on|off|toggle <name>\n\n"
			+ "FIRE\n"
			+ "Every time: each match. Once, until enabled: first match, then quiet "
			+ "until you turn the trigger off and on. Once, until I send: first match "
			+ "after you send from the input bar, then quiet until you send again. "
			+ "Ack from this trigger does not count as you sending.\n\n"
			+ "KEEP GOING\n"
			+ "On by default. Every trigger that matches the line still runs, so one "
			+ "can rewrite a channel tag and another can gag the spam inside it. "
			+ "Untick Keep going? on a trigger to stop later ones on that line.\n\n"
			+ TRIGGER_ORDER
			+ "A DOLLAR IN THE GAME TEXT\n"
			+ "In a regular expression $ is end of line, not a price. earned $70 "
			+ "needs earned \\$70.\n\n"
			+ "TRYING ONE WITHOUT THE GAME\n"
			+ "    .note some text\n"
			+ "prints a line into the window and sends nothing to the server, so a "
			+ "colour or a tappable word can be checked on a line you wrote.\n\n"
			+ "MATCH STYLE\n"
			+ "Require or forbid colour and SGR flags on the matched glyphs. The "
			+ "pattern may be blank when a style is set. Pattern is the line matcher; "
			+ "Run text is an extra layer on that match, not a second pattern. "
			+ ".grabber copies layers from a "
			+ "finger into a new trigger. Colour you paint with a Color action is not "
			+ "the world's style.";

	/**
	 * The CONDITIONS essay that used to sit on the trigger editor canvas.
	 * The editor {@code ?} appends this after the pattern help.
	 */
	public static final String TRIGGER_EDITOR_CONDITIONS =
			"CONDITIONS\n"
			+ "An extra gate after the pattern matches, not a replacement for it. "
			+ "Empty means always fire. Example: Only if trigger is ON + pick "
			+ "combat_mode → responders run only while the combat_mode trigger is "
			+ "enabled. Only if trigger is OFF does the opposite. Only if trigger "
			+ "matches this line looks at that trigger's Pattern and Match style "
			+ "(not its actions, not its conditions). Off or "
			+ "missing is closed. Variables are "
			+ "session sticky notes (Set Variable / ${name}), not pattern syntax. "
			+ "Variable is below / above compare as numbers (30 below 30 is false).\n\n"
			+ "OPEN AND CLOSED\n"
			+ "Each action has Open and Closed. Open runs it while the game window "
			+ "is on screen. Closed runs it while the window is in the background "
			+ "(Keep connection in background). Tick both to always run. Tick "
			+ "neither and that action never fires.";

	public static final String TIMERS =
			"A timer waits, then runs its actions. Nothing has to happen in the game "
			+ "and nothing has to be typed -- that is what makes it neither a trigger "
			+ "nor an alias.\n\n"
			+ "EVERY\n"
			+ "Hours, minutes and seconds are added up rather than range-checked, so "
			+ "90 in the seconds box is the same as 1m 30s. The line underneath shows "
			+ "the total that will be used.\n\n"
			+ "    Timer   heal\n"
			+ "    Every   0h 0m 15s, Repeat on\n"
			+ "    Ack     drink health\n\n"
			+ "REPEAT\n"
			+ "Off: fires once and stops. On: starts again the moment it fires. A "
			+ "repeating 1-second timer sends its command every second.\n\n"
			+ "ACTIONS\n"
			+ "The same list as a trigger, minus the ones that need a matched line: "
			+ "there is no $1 here, because nothing was matched.\n\n"
			+ "CONDITIONS\n"
			+ "Checked when the timer fires, not while it counts down. A timer whose "
			+ "condition is false does nothing that time round and, if it repeats, "
			+ "comes back. It is a gate, not a pause. Leave a healing timer running "
			+ "for good and gate it on a variable your combat triggers set. Only if "
			+ "trigger matches this line stays closed here — a timer has no game line.\n\n"
			+ "FROM THE INPUT BAR, BY NAME\n"
			+ "    .timer play heal        start it\n"
			+ "    .timer pause heal       hold it where it is\n"
			+ "    .timer reset heal       back to full duration\n"
			+ "    .timer stop heal        stop and reset\n"
			+ "    .timer info heal        status: running/paused/stopped, set for,\n"
			+ "                           elapsed, remaining, whether it repeats\n"
			+ "    .timer info heal window  same text in the game window\n"
			+ "    .timer dump heal       same as info … window; dump with no name lists all\n"
			+ "    .timer duration heal   same as info\n"
			+ "    .timer duration heal 30 change how long it runs\n"
			+ "Add silent as a last word to suppress the toast.\n\n"
			+ "Changing the duration does not stop the timer: one that was running "
			+ "keeps running on the new length, from now.\n\n"
			+ "GROUP\n"
			+ "A label the list shows, sorts and filters by. For timers it is for "
			+ "finding them -- there is no .timer group command, unlike triggers.\n\n"
			+ "WHILE THE PHONE SLEEPS\n"
			+ "Timers keep counting in the connection's own process while the game "
			+ "window is in the background. Android can still delay a long one on a "
			+ "sleeping phone; a timer is not an alarm clock.";

	/**
	 * Chat ⚙ submenu: My lines and Reply. Worlds print chat differently; this
	 * is what to type, not a prefix from one world.
	 */
	public static final String CHAT_MY_LINES =
			"MY LINES\n"
			+ "This field marks which copied lines are yours, so they get your bubble.\n\n"
			+ "WHAT TO TYPE\n"
			+ "Look at one of your lines in this conversation. Type only the name "
			+ "the world prints for you — usually your character name. Not the "
			+ "channel, not the whole line.\n\n"
			+ "    In the thread:  [ooc] Ada says, \"hello\"\n"
			+ "    My lines:       Ada\n\n"
			+ "    In the thread:  Ada tells you, \"hi\"\n"
			+ "    My lines:       Ada\n\n"
			+ "    In the thread:  You say, \"hello\"\n"
			+ "    My lines:       You say\n\n"
			+ "A one-word name already matches Ada says and Ada asks (you as "
			+ "speaker). Ada says as a phrase does not match Ada asks.\n\n"
			+ "SEVERAL FORMS\n"
			+ "One form per line, or join them with a semicolon:\n\n"
			+ "    Ada says\n"
			+ "    Ada asks\n\n"
			+ "    Ada says; Ada asks\n\n"
			+ "Same thing either way. | on one form is still regex "
			+ "(]: Ada|You say).\n\n"
			+ "WHY NOT THE CHANNEL TAG\n"
			+ "A tag like [ooc] sits on every message here. Paste it and everyone "
			+ "looks like you.\n\n"
			+ "NOT A MENTION\n"
			+ "Your name in someone else's sentence is not you speaking:\n\n"
			+ "    [ooc] Bob says, \"hi Ada\"   ← Bob's line\n\n"
			+ "WORLDS PRINT CHAT DIFFERENTLY\n"
			+ "Some put the name at the start of the line. Some put it after a ] "
			+ "or ). Some use You say. There is no one prefix to copy. The name "
			+ "as it appears when you speak is the thing that works. If a one-word "
			+ "name is wrong, type the unique bit of your line (Ada says), still "
			+ "not the channel tag — and add Ada asks on the next line if the "
			+ "world uses that too.\n\n"
			+ "COLOUR\n"
			+ "The chips in this submenu are this conversation only.\n\n"
			+ "REPLY\n"
			+ "The command Send uses. $text is whatever you type in the reply box "
			+ "at the bottom of this conversation.\n\n"
			+ "    Reply:    tell Bob $text\n"
			+ "    You type: hello\n"
			+ "    World:    tell Bob hello\n\n"
			+ "    Reply:    ooc $text\n"
			+ "    You type: hello\n"
			+ "    World:    ooc hello\n\n"
			+ "    Reply:    $text\n"
			+ "    You type: look\n"
			+ "    World:    look\n\n"
			+ "A lone $1 is treated as $text (a common mix-up):\n\n"
			+ "    Reply:    C $1\n"
			+ "    You type: hello\n"
			+ "    World:    C hello\n\n"
			+ "SEND TO THREAD\n"
			+ "The trigger action's Reply field is allowed to use $1 — that is a "
			+ "capture from the matched line (tell $1 $text becomes tell Bob $text "
			+ "when the conversation is created). Chat Send does not fill $1. After "
			+ "the thread exists, ⚙ Reply needs the name already in the template "
			+ "(tell Bob $text), or $text only.\n\n"
			+ "NOT A SEND TEMPLATE\n"
			+ "In Chat ⚙ Reply, tell $1 $text is the leftover capture form. Send "
			+ "refuses it: filling $text still leaves $1. Put the name in the "
			+ "template (tell Bob $text), or use $text only.";
}

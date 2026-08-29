package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.R;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
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
 * The {@code ?} beside the buttons: what this list is for, in the words the
 * manual uses.
 *
 * <p>Three of the things a player has to keep apart are told apart by which
 * way they face, and nothing on the screen says so. An <em>alias</em> rewrites
 * a line you type before it leaves the phone. A <em>trigger</em> matches a line
 * the game sent. A <em>timer</em> waits and then acts, with nothing having
 * happened at all. The three lists look alike, so the difference has to be
 * written down where the lists are.
 *
 * <p>These texts are the Aliases / Triggers / Timers sections of
 * {@code docs/user-manual.md} boiled down to what fits on a phone, with the
 * same examples, so a player who reads one and then the other is not told two
 * different things. When one changes, change the other: the manual is the long
 * form and this is the reminder.
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
		bodyView.setText(body);
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
			+ "    Color           tint the matching text\n"
			+ "    Tappable Word   make the match pressable\n"
			+ "    Toast / Notification   tell the phone\n"
			+ "    Set Variable    remember something for later\n\n"
			+ "CONDITIONS\n"
			+ "An extra gate after the pattern matches, not a replacement for it. "
			+ "Empty means always fire. Example: Only if trigger is ON, pointed at a "
			+ "trigger called combat_mode, so a set of triggers turns on and off "
			+ "together.\n\n"
			+ "GROUP\n"
			+ "A label like combat. The list shows and sorts by it, and\n"
			+ "    .trigger group off combat\n"
			+ "moves the whole set at once. One trigger at a time:\n"
			+ "    .trigger on|off|toggle <name>\n\n"
			+ "FIRE ONCE\n"
			+ "Fires the first time and then stays quiet until the trigger is enabled "
			+ "again.\n\n"
			+ "TRYING ONE WITHOUT THE GAME\n"
			+ "    .note some text\n"
			+ "prints a line into the window and sends nothing to the server, so a "
			+ "colour or a tappable word can be checked on a line you wrote.";

	/**
	 * The CONDITIONS essay that used to sit on the trigger editor canvas.
	 * The editor {@code ?} appends this after the pattern help.
	 */
	public static final String TRIGGER_EDITOR_CONDITIONS =
			"CONDITIONS\n"
			+ "An extra gate after the pattern matches, not a replacement for it. "
			+ "Empty means always fire. Example: Only if trigger is ON + pick "
			+ "combat_mode → responders run only while the combat_mode trigger is "
			+ "enabled. Only if trigger is OFF does the opposite. Variables are "
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
			+ "for good and gate it on a variable your combat triggers set.\n\n"
			+ "FROM THE INPUT BAR, BY NAME\n"
			+ "    .timer play heal        start it\n"
			+ "    .timer pause heal       hold it where it is\n"
			+ "    .timer reset heal       back to full duration\n"
			+ "    .timer stop heal        stop and reset\n"
			+ "    .timer info heal        how long is left\n"
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

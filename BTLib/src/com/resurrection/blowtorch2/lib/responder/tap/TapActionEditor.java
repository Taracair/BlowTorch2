package com.resurrection.blowtorch2.lib.responder.tap;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.TriggerResponderEditorDoneListener;

/**
 * Editor for {@link TapAction}. Built in code rather than from a layout so it
 * stays one file — it is a list of commands and three switches.
 */
public class TapActionEditor extends Dialog {

	private final TriggerResponder original;
	private final TriggerResponderEditorDoneListener finishWith;

	/** One row per command; the first one is what a plain tap sends. */
	private final java.util.ArrayList<EditText> commandBoxes =
			new java.util.ArrayList<EditText>();
	private LinearLayout commandRows;
	private CheckBox underlineBox;
	private CheckBox boldBox;
	private CheckBox frameBox;
	private EditText groupBox;

	public TapActionEditor(Context context, TriggerResponder original,
			TriggerResponderEditorDoneListener listener) {
		super(context);
		this.original = original;
		this.finishWith = listener;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle("Tappable word");

		final Context c = getContext();
		final float d = c.getResources().getDisplayMetrics().density;
		final int pad = Math.round(12 * d);

		LinearLayout root = new LinearLayout(c);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(pad, pad, pad, pad);

		TextView help = new TextView(c);
		help.setText("What the trigger matches becomes tappable, and tapping it sends the "
				+ "command below. " + TapAction.WORD_TOKEN + " is the text that was "
				+ "tapped, $0 the whole match, $1 to $9 the bracketed parts of the "
				+ "pattern. Add more commands and a tap asks which one you meant "
				+ "instead of sending straight away — the first one stays on top of "
				+ "that menu.");
		root.addView(help);

		commandRows = new LinearLayout(c);
		commandRows.setOrientation(LinearLayout.VERTICAL);
		root.addView(commandRows);

		Button addCommand = new Button(c);
		addCommand.setText("Add another command");
		addCommand.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				addCommandRow("");
			}
		});
		root.addView(addCommand);

		TextView groupLabel = new TextView(c);
		groupLabel.setText("Tappable part: 0 = the whole match, 1-9 = that bracket");
		root.addView(groupLabel);

		groupBox = new EditText(c);
		groupBox.setSingleLine(true);
		groupBox.setInputType(InputType.TYPE_CLASS_NUMBER);
		groupBox.setHint("0");
		root.addView(groupBox);

		TextView groupHelp = new TextView(c);
		groupHelp.setText("Example: pattern  You see (.+) lying here  with 1 here lights up "
				+ "just the thing on the floor, not the whole sentence, and \"get $1\" "
				+ "picks it up.");
		root.addView(groupHelp);

		underlineBox = addCheck(c, root, "Underline");
		boldBox = addCheck(c, root, "Bold");
		frameBox = addCheck(c, root, "Frame around the word");
		// No colour here on purpose: a Colour action on the same trigger already
		// paints what the pattern matched, and two ways to colour one word is
		// one too many. The marks below use the colour the text already has.

		TapAction start = original instanceof TapAction ? (TapAction) original : new TapAction();
		for (String cmd : start.getCommands()) {
			addCommandRow(cmd);
		}
		underlineBox.setChecked(start.isUnderline());
		boldBox.setChecked(start.isBold());
		frameBox.setChecked(start.isFrame());
		groupBox.setText(Integer.toString(start.getGroup()));

		// Cancel, ?, Done — the way out on the left, the way on with it on the
		// right, and the explanation between them where a thumb finds it.
		LinearLayout buttons = new LinearLayout(c);
		buttons.setOrientation(LinearLayout.HORIZONTAL);

		Button cancel = new Button(c);
		cancel.setText("Cancel");
		cancel.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				TapActionEditor.this.dismiss();
			}
		});
		buttons.addView(cancel);

		Button helpButton = new Button(c);
		helpButton.setText("?");
		helpButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				showHelp();
			}
		});
		buttons.addView(helpButton);

		Button ok = new Button(c);
		ok.setText("Done");
		ok.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				finish();
			}
		});
		buttons.addView(ok);
		root.addView(buttons);

		ScrollView scroll = new ScrollView(c);
		scroll.addView(root, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		setContentView(scroll);
	}

	/**
	 * One command line with an X beside it. The X removes the row rather than
	 * blanking it, and the last remaining row keeps its X — clearing every
	 * command is caught in {@link #finish()}, which falls back to the default.
	 */
	private void addCommandRow(final String text) {
		final Context c = getContext();
		final LinearLayout row = new LinearLayout(c);
		row.setOrientation(LinearLayout.HORIZONTAL);

		final EditText box = new EditText(c);
		box.setSingleLine(true);
		box.setInputType(InputType.TYPE_CLASS_TEXT);
		box.setHint(TapAction.DEFAULT_COMMAND);
		box.setText(text);
		row.addView(box, new LinearLayout.LayoutParams(0,
				ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		Button remove = new Button(c);
		remove.setText("X");
		remove.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				commandBoxes.remove(box);
				commandRows.removeView(row);
			}
		});
		row.addView(remove, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		commandBoxes.add(box);
		commandRows.addView(row);
	}

	/**
	 * What the whole thing does, with worked examples. The manual has this too,
	 * but the player configuring a trigger on a phone is not going to go and
	 * read the manual — and this action needs more explaining than a command
	 * box and three checkboxes look like they do.
	 */
	private void showHelp() {
		android.widget.TextView body = new android.widget.TextView(getContext());
		final float d = getContext().getResources().getDisplayMetrics().density;
		int pad = Math.round(16 * d);
		body.setPadding(pad, pad, pad, pad);
		body.setTextIsSelectable(true);
		body.setText(HELP_TEXT);

		ScrollView scroll = new ScrollView(getContext());
		scroll.addView(body);

		new android.app.AlertDialog.Builder(getContext())
				.setTitle("Tappable word")
				.setView(scroll)
				.setPositiveButton("Close", null)
				.show();
	}

	static final String HELP_TEXT =
			"What the trigger matches becomes pressable in the game text. Pressing it "
			+ "sends a command.\n\n"
			+ "TAPPABLE PART\n"
			+ "0 = the whole match lights up.\n"
			+ "1-9 = only that bracket of the pattern lights up. A pattern usually "
			+ "needs the rest of the line to recognise it, and you rarely want the "
			+ "whole sentence pressable.\n\n"
			+ "IN THE COMMAND\n"
			+ "$word = the text that was pressed\n"
			+ "$0 = the whole match\n"
			+ "$1..$9 = the bracketed parts of the pattern\n"
			+ "A bracket the pattern does not have becomes empty, not a literal $7.\n\n"
			+ "EXAMPLES (Literal? off — these are regexes)\n\n"
			+ "1) Pick something up\n"
			+ "   Pattern:  You see (.+) lying here\n"
			+ "   Part:     1\n"
			+ "   Command:  get $1\n"
			+ "   \"You see a rusty sword lying here\" - only \"a rusty sword\" lights "
			+ "up, and pressing it sends: get a rusty sword\n\n"
			+ "2) One word, two things to do with the line\n"
			+ "   Pattern:  (\\w+) drops (\\w+)\n"
			+ "   Part:     2\n"
			+ "   Commands: get $2\n"
			+ "             kill $1\n"
			+ "   \"Goblin drops sword\" - \"sword\" lights up; pressing it opens a "
			+ "menu with \"get sword\" and \"kill Goblin\". A command may use any part "
			+ "of the match, not only the part that was pressed.\n\n"
			+ "3) A menu on a monster\n"
			+ "   Pattern:  (\\w+) the (\\w+) is standing here\n"
			+ "   Part:     0\n"
			+ "   Commands: kill $1\n"
			+ "             consider $1\n"
			+ "             look $word\n\n"
			+ "4) Numbers only\n"
			+ "   Pattern:  \\b(\\d+) (?:gold|credits)\\b\n"
			+ "   Part:     1\n"
			+ "   Command:  get $1 gold\n"
			+ "   The currency word is only there to recognise the line.\n\n"
			+ "5) Answer someone in chat\n"
			+ "   Pattern:  \\[(\\w+)\\] (\\w+):\n"
			+ "   Part:     2\n"
			+ "   Commands: tell $2\n"
			+ "             ignore $2\n\n"
			+ "GOOD TO KNOW\n"
			+ "- More than one command turns a press into a small menu at the word; "
			+ "one command sends straight away. The first command is on top of the "
			+ "menu, and a long one is shortened there with (...) — the whole command "
			+ "is still what gets sent.\n"
			+ "- Several matches on one line are each pressable.\n"
			+ "- A match may cross a colour change: matching runs on the whole line, "
			+ "and each coloured piece is marked in its own colour.\n"
			+ "- The word stays pressable while the line is in the buffer, so you can "
			+ "scroll back and press something from earlier.\n"
			+ "- Colour is not here on purpose: put a Color action on the same trigger "
			+ "and the marks follow the colour the text has.\n"
			+ "- Two Tappable Word actions on one trigger behave as one word offering "
			+ "both sets of commands; the look comes from the first one.\n"
			+ "- A disabled trigger, or one whose Conditions are false, marks nothing.";

	/** Anything that is not 0-9 means the whole match, which is the default. */
	static int parseGroup(String text) {
		if (text == null) {
			return 0;
		}
		String t = text.trim();
		if (t.length() != 1 || t.charAt(0) < '0' || t.charAt(0) > '9') {
			return 0;
		}
		return t.charAt(0) - '0';
	}

	private static CheckBox addCheck(Context c, LinearLayout parent, String label) {
		CheckBox box = new CheckBox(c);
		box.setText(label);
		parent.addView(box);
		return box;
	}

	private void finish() {
		TapAction action = new TapAction();
		java.util.ArrayList<String> cmds = new java.util.ArrayList<String>();
		for (EditText box : commandBoxes) {
			cmds.add(box.getText().toString().trim());
		}
		// Blank rows and an empty list are handled there: the action never ends
		// up with nothing to send.
		action.setCommands(cmds);
		action.setUnderline(underlineBox.isChecked());
		action.setBold(boldBox.isChecked());
		action.setFrame(frameBox.isChecked());
		action.setGroup(parseGroup(groupBox.getText().toString()));

		if (original != null) {
			finishWith.editTriggerResponder(action, original);
		} else {
			finishWith.newTriggerResponder(action);
		}
		this.dismiss();
	}

}

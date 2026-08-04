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
		help.setText("What the trigger matches becomes tappable. Tapping it sends the "
				+ "command below, with " + TapAction.WORD_TOKEN + " replaced by the text "
				+ "that was tapped. Add more commands and a tap asks which one you "
				+ "meant instead of sending straight away — the first one stays on top "
				+ "of that menu.");
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

		LinearLayout buttons = new LinearLayout(c);
		buttons.setOrientation(LinearLayout.HORIZONTAL);

		Button ok = new Button(c);
		ok.setText("Done");
		ok.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				finish();
			}
		});
		buttons.addView(ok);

		Button cancel = new Button(c);
		cancel.setText("Cancel");
		cancel.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				TapActionEditor.this.dismiss();
			}
		});
		buttons.addView(cancel);
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

		if (original != null) {
			finishWith.editTriggerResponder(action, original);
		} else {
			finishWith.newTriggerResponder(action);
		}
		this.dismiss();
	}

}

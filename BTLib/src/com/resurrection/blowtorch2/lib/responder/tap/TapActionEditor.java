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
 * stays one file — it is a command box, four switches and a colour.
 */
public class TapActionEditor extends Dialog {

	private final TriggerResponder original;
	private final TriggerResponderEditorDoneListener finishWith;

	private EditText commandBox;
	private CheckBox underlineBox;
	private CheckBox boldBox;
	private CheckBox frameBox;
	private CheckBox recolorBox;
	private EditText colorBox;

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
				+ "that was tapped.");
		root.addView(help);

		commandBox = new EditText(c);
		commandBox.setSingleLine(true);
		commandBox.setInputType(InputType.TYPE_CLASS_TEXT);
		commandBox.setHint("look " + TapAction.WORD_TOKEN);
		root.addView(commandBox);

		underlineBox = addCheck(c, root, "Underline");
		boldBox = addCheck(c, root, "Bold");
		frameBox = addCheck(c, root, "Frame around the word");
		recolorBox = addCheck(c, root, "Use own colour");

		TextView colorLabel = new TextView(c);
		colorLabel.setText("Colour (#RRGGBB)");
		root.addView(colorLabel);

		colorBox = new EditText(c);
		colorBox.setSingleLine(true);
		colorBox.setInputType(InputType.TYPE_CLASS_TEXT);
		colorBox.setHint("#66CCFF");
		root.addView(colorBox);

		TapAction start = original instanceof TapAction ? (TapAction) original : new TapAction();
		commandBox.setText(start.getCommand());
		underlineBox.setChecked(start.isUnderline());
		boldBox.setChecked(start.isBold());
		frameBox.setChecked(start.isFrame());
		recolorBox.setChecked(start.isRecolor());
		colorBox.setText(String.format("#%06X", 0xFFFFFF & start.getColor()));

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

	private static CheckBox addCheck(Context c, LinearLayout parent, String label) {
		CheckBox box = new CheckBox(c);
		box.setText(label);
		parent.addView(box);
		return box;
	}

	private void finish() {
		TapAction action = new TapAction();
		String cmd = commandBox.getText().toString().trim();
		action.setCommand(cmd.length() > 0 ? cmd : "look " + TapAction.WORD_TOKEN);
		action.setUnderline(underlineBox.isChecked());
		action.setBold(boldBox.isChecked());
		action.setFrame(frameBox.isChecked());
		action.setRecolor(recolorBox.isChecked());
		action.setColor(parseColor(colorBox.getText().toString(), 0xFF66CCFF));

		if (original != null) {
			finishWith.editTriggerResponder(action, original);
		} else {
			finishWith.newTriggerResponder(action);
		}
		this.dismiss();
	}

	/** Accepts #RRGGBB or RRGGBB; anything else keeps the default. */
	static int parseColor(String text, int fallback) {
		if (text == null) {
			return fallback;
		}
		String s = text.trim();
		if (s.startsWith("#")) {
			s = s.substring(1);
		}
		if (s.length() != 6) {
			return fallback;
		}
		try {
			return 0xFF000000 | Integer.parseInt(s, 16);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}

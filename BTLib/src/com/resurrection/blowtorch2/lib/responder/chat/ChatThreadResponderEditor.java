package com.resurrection.blowtorch2.lib.responder.chat;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.responder.TriggerResponderEditorDoneListener;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Small editor for {@link ChatThreadResponder}: thread, optional title/body,
 * optional reply template. Built in code so this change does not need a new
 * layout resource.
 */
public class ChatThreadResponderEditor extends Dialog {

	private ChatThreadResponder theResponder;
	private ChatThreadResponder original;
	private TriggerResponderEditorDoneListener finishWith;
	private boolean isEditor = false;

	private EditText threadField;
	private EditText titleField;
	private EditText bodyField;
	private EditText replyField;
	private android.widget.CheckBox mineBox;

	public ChatThreadResponderEditor(Context context, ChatThreadResponder input,
			TriggerResponderEditorDoneListener listener) {
		super(context);
		finishWith = listener;
		if (input == null) {
			theResponder = new ChatThreadResponder();
		} else {
			theResponder = input.copy();
			original = input.copy();
			isEditor = true;
		}
	}

	public void onCreate(Bundle b) {
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);

		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (8.0f * density);

		LinearLayout root = new LinearLayout(getContext());
		root.setOrientation(LinearLayout.VERTICAL);
		root.setMinimumWidth((int) (300.0f * density));

		TextView titlebar = new TextView(getContext());
		titlebar.setText("SEND TO THREAD");
		titlebar.setTextColor(getContext().getResources().getColor(R.color.chrome_title_text));
		titlebar.setBackgroundColor(getContext().getResources().getColor(R.color.chrome_title_bar));
		titlebar.setTextSize(14);
		titlebar.setGravity(android.view.Gravity.CENTER);
		titlebar.setPadding(pad, pad, pad, pad);
		root.addView(titlebar, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				(int) (42.0f * density)));

		ScrollView scroller = new ScrollView(getContext());
		LinearLayout form = new LinearLayout(getContext());
		form.setOrientation(LinearLayout.VERTICAL);
		form.setPadding(pad, pad, pad, pad);

		threadField = addField(form, "Thread:", "e.g. vermin or $1",
				theResponder.getThreadId(), true);
		titleField = addField(form, "Title (optional):", "blank = the thread id",
				theResponder.getTitle(), true);
		bodyField = addField(form, "Body (optional):", "blank = the matched line",
				theResponder.getBody(), false);
		replyField = addField(form, "Reply template (optional):",
				"e.g. tell $1 $text  ($text is the reply box, not a capture)",
				theResponder.getReplyTemplate(), false);

		mineBox = new android.widget.CheckBox(getContext());
		mineBox.setText("These are my lines (own bubble)");
		mineBox.setTextColor(0xFFE8E8E8);
		mineBox.setChecked(theResponder.isMine());
		LinearLayout.LayoutParams mineLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		mineLp.topMargin = (int) (10.0f * density);
		form.addView(mineBox, mineLp);

		scroller.addView(form, new ScrollView.LayoutParams(
				ScrollView.LayoutParams.MATCH_PARENT,
				ScrollView.LayoutParams.WRAP_CONTENT));
		root.addView(scroller, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

		LinearLayout buttons = new LinearLayout(getContext());
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		Button cancel = new Button(getContext());
		cancel.setText("Cancel");
		cancel.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				dismiss();
			}
		});
		Button done = new Button(getContext());
		done.setText("Done");
		done.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				theResponder.setThreadId(threadField.getText().toString());
				theResponder.setTitle(titleField.getText().toString());
				theResponder.setBody(bodyField.getText().toString());
				theResponder.setReplyTemplate(replyField.getText().toString());
				theResponder.setMine(mineBox != null && mineBox.isChecked());
				if (isEditor) {
					finishWith.editTriggerResponder(theResponder, original);
				} else {
					finishWith.newTriggerResponder(theResponder);
				}
				dismiss();
			}
		});
		LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		buttons.addView(cancel, half);
		buttons.addView(done, half);
		root.addView(buttons);

		setContentView(root);
	}

	private EditText addField(LinearLayout form, String label, String hint,
			String value, boolean singleLine) {
		int labelColor = 0xFFE8E8E8;
		TextView caption = new TextView(getContext());
		caption.setText(label);
		caption.setTextColor(labelColor);
		if (form.getChildCount() > 0) {
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			lp.topMargin = (int) (8.0f * getContext().getResources().getDisplayMetrics().density);
			form.addView(caption, lp);
		} else {
			form.addView(caption);
		}
		EditText field = new EditText(getContext());
		field.setHint(hint);
		field.setText(value);
		field.setSingleLine(singleLine);
		form.addView(field);
		return field;
	}
}

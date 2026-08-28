package com.resurrection.blowtorch2.lib.responder.chat;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.responder.TriggerResponderEditorDoneListener;
import com.resurrection.blowtorch2.lib.window.EditorHelp;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
		int titleHeight = (int) (42.0f * density);
		int titleInk = getContext().getResources().getColor(R.color.chrome_title_text);
		int bar = getContext().getResources().getColor(R.color.chrome_title_bar);
		int desc = getContext().getResources().getColor(R.color.chrome_description);

		LinearLayout root = new LinearLayout(getContext());
		root.setOrientation(LinearLayout.VERTICAL);
		root.setMinimumWidth((int) (300.0f * density));

		LinearLayout titleRow = new LinearLayout(getContext());
		titleRow.setOrientation(LinearLayout.HORIZONTAL);
		titleRow.setGravity(Gravity.CENTER_VERTICAL);
		titleRow.setBackgroundColor(bar);
		titleRow.setPadding(pad, 0, (int) (4.0f * density), 0);

		TextView titlebar = new TextView(getContext());
		titlebar.setText("SEND TO THREAD");
		titlebar.setAllCaps(true);
		titlebar.setTextColor(titleInk);
		titlebar.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		titlebar.setGravity(Gravity.CENTER_VERTICAL);
		titleRow.addView(titlebar, new LinearLayout.LayoutParams(
				0, titleHeight, 1f));

		TextView help = new TextView(getContext());
		help.setText("?");
		help.setTextColor(desc);
		help.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
		help.setGravity(Gravity.CENTER);
		help.setClickable(true);
		help.setFocusable(true);
		help.setContentDescription("How to fill My lines and Reply");
		help.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				EditorHelp.show(getContext(), "My lines and Reply",
						EditorHelp.CHAT_MY_LINES);
			}
		});
		titleRow.addView(help, new LinearLayout.LayoutParams(
				(int) (36.0f * density), titleHeight));
		root.addView(titleRow, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, titleHeight));

		ScrollView scroller = new ScrollView(getContext());
		LinearLayout form = new LinearLayout(getContext());
		form.setOrientation(LinearLayout.VERTICAL);
		form.setPadding(pad, pad, pad, pad);

		threadField = addField(form, "Thread", "conversation key, or $1",
				theResponder.getThreadId(), true);
		titleField = addField(form, "Title (optional)", "blank = the thread id",
				theResponder.getTitle(), true);
		bodyField = addField(form, "Body (optional)", "blank = the matched line",
				theResponder.getBody(), false);
		replyField = addField(form, "Reply", "tell Bob $text",
				theResponder.getReplyTemplate(), true);

		TextView replyNote = new TextView(getContext());
		replyNote.setText("$text is the chat reply box. $1 here is a trigger capture (tell $1 $text becomes tell Bob $text when the thread is created). Send uses Chat → ⚙ → Reply, which needs the name already filled.");
		replyNote.setTextColor(desc);
		replyNote.setTextSize(12);
		LinearLayout.LayoutParams replyNoteLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		replyNoteLp.topMargin = (int) (4.0f * density);
		form.addView(replyNote, replyNoteLp);

		TextView mineNote = new TextView(getContext());
		mineNote.setText("Own bubble: Chat → ⚙ → tap My lines. A name like Ada already matches says and asks. For verb phrases, one form per line or Ada says; Ada asks. Tap ? for examples.");
		mineNote.setTextColor(desc);
		mineNote.setTextSize(12);
		LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		noteLp.topMargin = (int) (10.0f * density);
		form.addView(mineNote, noteLp);

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

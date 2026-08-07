package com.resurrection.blowtorch2.lib.responder.speak;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.responder.TriggerResponderEditorDoneListener;
import com.resurrection.blowtorch2.lib.util.SpeechEngine;
import com.resurrection.blowtorch2.lib.validator.Validator;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

public class SpeakResponderEditor extends Dialog {

	private SpeakResponder the_responder;
	private SpeakResponder original;

	boolean isEditor = false;

	TriggerResponderEditorDoneListener finish_with;

	public SpeakResponderEditor(Context context, SpeakResponder input,
			TriggerResponderEditorDoneListener doneListener) {
		super(context);
		finish_with = doneListener;
		if (input != null) {
			original = input.copy();
			the_responder = input.copy();
			isEditor = true;
		} else {
			the_responder = new SpeakResponder();
		}
	}

	public void onCreate(Bundle b) {
		this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		setContentView(R.layout.responder_speak_dialog);

		EditText message = (EditText) findViewById(R.id.responder_speak_message);
		CheckBox interrupt = (CheckBox) findViewById(R.id.responder_speak_interrupt);
		message.setText(the_responder.getMessage());
		interrupt.setChecked(the_responder.getInterrupt());

		Button done = (Button) findViewById(R.id.responder_speak_done_button);
		done.setOnClickListener(new DoneListener());

		Button cancel = (Button) findViewById(R.id.responder_speak_cancel);
		cancel.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				SpeakResponderEditor.this.dismiss();
			}
		});
	}

	private class DoneListener implements View.OnClickListener {

		public void onClick(View arg0) {
			EditText message =
					(EditText) SpeakResponderEditor.this.findViewById(R.id.responder_speak_message);
			CheckBox interrupt =
					(CheckBox) SpeakResponderEditor.this.findViewById(R.id.responder_speak_interrupt);

			Validator checker = new Validator();
			checker.add(message, Validator.VALIDATE_NOT_BLANK, "Say field");

			String result = checker.validate();
			if (result != null) {
				checker.showMessage(SpeakResponderEditor.this.getContext(), result);
				return;
			}

			the_responder.setMessage(message.getText().toString());
			the_responder.setInterrupt(interrupt.isChecked());

			// Say it once, here, so the engine is known to work before the player
			// finds out during a fight that this phone has no voice installed.
			SpeechEngine.get(SpeakResponderEditor.this.getContext())
					.speak(the_responder.getMessage(), true);

			if (isEditor) {
				finish_with.editTriggerResponder(the_responder, original);
			} else {
				finish_with.newTriggerResponder(the_responder);
			}

			SpeakResponderEditor.this.dismiss();
		}
	};
}

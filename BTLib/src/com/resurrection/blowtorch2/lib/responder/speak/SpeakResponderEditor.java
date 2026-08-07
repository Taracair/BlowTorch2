package com.resurrection.blowtorch2.lib.responder.speak;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.responder.TriggerResponderEditorDoneListener;
import com.resurrection.blowtorch2.lib.util.SpeechEngine;
import com.resurrection.blowtorch2.lib.validator.Validator;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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

		// Start the engine now rather than on the first test, so that by the time
		// the player presses the button it has either come up or failed, and the
		// note underneath already says which.
		SpeechEngine.get(getContext());
		showEngineStatus();

		Button test = (Button) findViewById(R.id.responder_speak_test);
		test.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				EditText m = (EditText) findViewById(R.id.responder_speak_message);
				String say = m.getText() == null ? "" : m.getText().toString();
				if (say.trim().length() == 0) {
					Toast.makeText(getContext(), "Nothing to say yet.",
							Toast.LENGTH_SHORT).show();
					return;
				}
				SpeechEngine.get(getContext()).speak(say, true);
				showEngineStatus();
			}
		});

		Button help = (Button) findViewById(R.id.responder_speak_help);
		help.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				showHelp();
			}
		});

		Button done = (Button) findViewById(R.id.responder_speak_done_button);
		done.setOnClickListener(new DoneListener());

		Button cancel = (Button) findViewById(R.id.responder_speak_cancel);
		cancel.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				SpeakResponderEditor.this.dismiss();
			}
		});
	}

	/** Say what the engine is doing, under the button that uses it. */
	private void showEngineStatus() {
		TextView status = (TextView) findViewById(R.id.responder_speak_status);
		if (status == null) {
			return;
		}
		SpeechEngine engine = SpeechEngine.get(getContext());
		String problem = engine.getProblem();
		if (problem != null) {
			status.setText("Speech: " + problem + "  Press ? for help.");
			return;
		}
		if (engine.isStarting()) {
			status.setText("Speech: still starting. Press ▶ in a moment.");
			return;
		}
		status.setText("$1, $word and variables are filled in from the match.");
	}

	private void showHelp() {
		final SpeechEngine engine = SpeechEngine.get(getContext());
		String problem = engine.getProblem();
		StringBuilder text = new StringBuilder();
		text.append("This action says the message out loud using the phone's own"
				+ " speech engine. BlowTorch carries no voices of its own, which is"
				+ " why it costs the app nothing.\n\n");
		if (problem != null) {
			text.append("Right now: ").append(problem).append("\n\n");
		} else if (engine.isReady()) {
			text.append("Right now: the engine is up and ready.\n\n");
		}
		text.append("If nothing is said:\n\n"
				+ "1. Press ▶ Say it now. If that is silent, the trigger is not"
				+ " the problem.\n"
				+ "2. Check the phone is not on silent, and turn the media volume"
				+ " up — speech plays on the media stream.\n"
				+ "3. Open Android's text-to-speech settings (below) and press its"
				+ " own Play/Listen button. If Android is silent too, the engine or"
				+ " its voice data is missing — install a voice there.\n"
				+ "4. Some phones ship no engine at all. Google Speech Services is"
				+ " the usual one; any engine will do.\n\n"
				+ "Fire-when still applies: an action set to fire only while the"
				+ " game window is closed says nothing while you are looking at it.");

		AlertDialog.Builder b = new AlertDialog.Builder(getContext());
		b.setTitle("Speaking out loud");
		b.setMessage(text.toString());
		b.setPositiveButton("Android speech settings",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface d, int which) {
						openSystemTtsSettings();
					}
				});
		b.setNegativeButton("Close", null);
		b.create().show();
	}

	private void openSystemTtsSettings() {
		try {
			Intent i = new Intent("com.android.settings.TTS_SETTINGS");
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			getContext().startActivity(i);
		} catch (ActivityNotFoundException e) {
			// Not every build has that screen under that name. Say where to look
			// rather than failing silently.
			Toast.makeText(getContext(),
					"Open Android Settings → Accessibility (or Language) →"
					+ " Text-to-speech output.", Toast.LENGTH_LONG).show();
		}
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

			// Nothing is spoken here. Saving used to say the message, which meant
			// closing the editor read a line out loud in whatever room you were
			// standing in. Testing is a button you press on purpose.
			if (isEditor) {
				finish_with.editTriggerResponder(the_responder, original);
			} else {
				finish_with.newTriggerResponder(the_responder);
			}

			SpeakResponderEditor.this.dismiss();
		}
	};
}

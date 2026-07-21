package com.resurrection.blowtorch2.lib.responder.setvariable;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.responder.TriggerResponderEditorDoneListener;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

public class SetVariableResponderEditor extends Dialog {

	private SetVariableResponder theResponder;
	private SetVariableResponder original;
	private TriggerResponderEditorDoneListener finishWith;
	private boolean isEditor = false;

	public SetVariableResponderEditor(Context context, SetVariableResponder input,
			TriggerResponderEditorDoneListener listener) {
		super(context);
		finishWith = listener;
		if (input == null) {
			theResponder = new SetVariableResponder();
		} else {
			theResponder = input.copy();
			original = input.copy();
			isEditor = true;
		}
	}

	public void onCreate(Bundle b) {
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		setContentView(R.layout.responder_setvariable_dialog);

		EditText name = (EditText) findViewById(R.id.responder_setvariable_name);
		EditText value = (EditText) findViewById(R.id.responder_setvariable_value);
		name.setText(theResponder.getVariableName());
		value.setText(theResponder.getVariableValue());

		Button done = (Button) findViewById(R.id.responder_setvariable_done);
		done.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				EditText n = (EditText) findViewById(R.id.responder_setvariable_name);
				EditText v = (EditText) findViewById(R.id.responder_setvariable_value);
				theResponder.setVariableName(n.getText().toString());
				theResponder.setVariableValue(v.getText().toString());
				if (isEditor) {
					finishWith.editTriggerResponder(theResponder, original);
				} else {
					finishWith.newTriggerResponder(theResponder);
				}
				dismiss();
			}
		});

		Button cancel = (Button) findViewById(R.id.responder_setvariable_cancel);
		cancel.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				dismiss();
			}
		});
	}
}

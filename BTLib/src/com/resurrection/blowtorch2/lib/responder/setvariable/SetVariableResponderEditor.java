package com.resurrection.blowtorch2.lib.responder.setvariable;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.responder.TriggerResponderEditorDoneListener;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

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
		Spinner mode = (Spinner) findViewById(R.id.responder_setvariable_mode);
		CheckBox persist = (CheckBox) findViewById(R.id.responder_setvariable_persist);
		name.setText(theResponder.getVariableName());
		value.setText(theResponder.getVariableValue());
		persist.setChecked(theResponder.isPersist());

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),
				R.layout.spinner_item_dark, SetVariableApply.MODE_LABELS);
		adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
		mode.setAdapter(adapter);
		mode.setSelection(SetVariableApply.indexOfMode(theResponder.getMode()), false);
		mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position,
					long id) {
				syncValueRow();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
		syncValueRow();

		Button done = (Button) findViewById(R.id.responder_setvariable_done);
		done.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				EditText n = (EditText) findViewById(R.id.responder_setvariable_name);
				EditText v = (EditText) findViewById(R.id.responder_setvariable_value);
				Spinner m = (Spinner) findViewById(R.id.responder_setvariable_mode);
				CheckBox p = (CheckBox) findViewById(R.id.responder_setvariable_persist);
				theResponder.setVariableName(n.getText().toString());
				theResponder.setVariableValue(v.getText().toString());
				int idx = m.getSelectedItemPosition();
				if (idx < 0 || idx >= SetVariableApply.MODE_TOKENS.length) {
					idx = 0;
				}
				theResponder.setMode(SetVariableApply.MODE_TOKENS[idx]);
				theResponder.setPersist(p.isChecked());
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

	private void syncValueRow() {
		Spinner mode = (Spinner) findViewById(R.id.responder_setvariable_mode);
		View row = findViewById(R.id.responder_setvariable_value_row);
		TextView label = (TextView) findViewById(R.id.responder_setvariable_value_label);
		int idx = mode.getSelectedItemPosition();
		if (idx < 0 || idx >= SetVariableApply.MODE_TOKENS.length) {
			idx = 0;
		}
		String token = SetVariableApply.MODE_TOKENS[idx];
		boolean unset = SetVariableApply.MODE_UNSET.equals(token);
		row.setVisibility(unset ? View.GONE : View.VISIBLE);
		if (SetVariableApply.MODE_ADD.equals(token)
				|| SetVariableApply.MODE_SUBTRACT.equals(token)) {
			label.setText("Number (may use $1):");
		} else if (SetVariableApply.MODE_APPEND.equals(token)) {
			label.setText("Text to append (may use $1):");
		} else {
			label.setText("Value (may use $1):");
		}
	}
}

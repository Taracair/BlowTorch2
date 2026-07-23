package com.resurrection.blowtorch2.lib.responder.gag;

import java.util.ArrayList;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.TriggerResponderEditorDoneListener;
import com.resurrection.blowtorch2.lib.responder.ExtraTextRetargetHelper;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

public class GagActionEditorDialog extends Dialog {
	TriggerResponder original;
	private TriggerResponderEditorDoneListener finish_with;
	private IConnectionBinder service;

	CheckBox output;
	CheckBox log;

	Spinner retargetSpinner;
	EditText retarget;
	private ArrayList<String> spinnerLabels;
	private boolean suppressSpinnerSync;

	public GagActionEditorDialog(Context context, TriggerResponder original,
			TriggerResponderEditorDoneListener listener) {
		this(context, original, listener, null);
	}

	public GagActionEditorDialog(Context context, TriggerResponder original,
			TriggerResponderEditorDoneListener listener, IConnectionBinder service) {
		super(context);
		this.original = original;
		finish_with = listener;
		this.service = service;
	}

	@Override
	public void onCreate(Bundle b) {

		this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);

		this.setContentView(R.layout.responder_gag_dialog);

		output = (CheckBox) this.findViewById(R.id.gag_output);
		log = (CheckBox) this.findViewById(R.id.gag_log);

		retargetSpinner = (Spinner) this.findViewById(R.id.retarget_spinner);
		retarget = (EditText) this.findViewById(R.id.retarget_text);

		setupRetargetSpinner();

		if (original != null) {
			output.setChecked(((GagAction) original).isGagOutput());
			log.setChecked(((GagAction) original).isGagLog());
			String existing = ((GagAction) original).getRetarget();
			if (existing != null) {
				retarget.setText(existing);
			}
			selectSpinnerForValue(existing);
		} else {
			output.setChecked(true);
			log.setChecked(true);
			retargetSpinner.setSelection(0);
		}

		findViewById(R.id.done).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				String target = ExtraTextRetargetHelper.normalizeRetarget(
						retarget.getText() != null ? retarget.getText().toString() : null);
				if (original != null) {
					GagAction edited = new GagAction();
					edited.setGagLog(log.isChecked());
					edited.setGagOutput(output.isChecked());
					edited.setRetarget(target);
					finish_with.editTriggerResponder(edited, original);
				} else {
					GagAction tmp = new GagAction();
					tmp.setGagLog(log.isChecked());
					tmp.setGagOutput(output.isChecked());
					tmp.setRetarget(target);
					finish_with.newTriggerResponder(tmp);
				}

				GagActionEditorDialog.this.dismiss();
			}
		});

		findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				GagActionEditorDialog.this.dismiss();
			}
		});
	}

	private void setupRetargetSpinner() {
		spinnerLabels = ExtraTextRetargetHelper.buildSpinnerLabels(service);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),
				android.R.layout.simple_spinner_item, spinnerLabels);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		retargetSpinner.setAdapter(adapter);
		retargetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				if (suppressSpinnerSync) {
					return;
				}
				if (position <= 0) {
					retarget.setText("");
				} else if (position < spinnerLabels.size()) {
					retarget.setText(spinnerLabels.get(position));
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
	}

	private void selectSpinnerForValue(String value) {
		suppressSpinnerSync = true;
		int idx = ExtraTextRetargetHelper.indexOfSlot(spinnerLabels, value);
		retargetSpinner.setSelection(idx);
		suppressSpinnerSync = false;
	}

}

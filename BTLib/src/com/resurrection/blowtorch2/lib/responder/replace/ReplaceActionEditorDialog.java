package com.resurrection.blowtorch2.lib.responder.replace;

import java.util.ArrayList;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.responder.ExtraTextRetargetHelper;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.TriggerResponderEditorDoneListener;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

public class ReplaceActionEditorDialog extends Dialog {

	TriggerResponder original;
	private TriggerResponderEditorDoneListener finish_with;
	private IConnectionBinder service;

	EditText with;
	Spinner retargetSpinner;
	EditText retarget;
	private ArrayList<String> spinnerLabels;
	private boolean suppressSpinnerSync;

	public ReplaceActionEditorDialog(Context context, TriggerResponder original,
			TriggerResponderEditorDoneListener listener) {
		this(context, original, listener, null);
	}

	public ReplaceActionEditorDialog(Context context, TriggerResponder original,
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

		this.setContentView(R.layout.responder_replace_dialog);

		((TextView) findViewById(R.id.titlebar)).setText("REPLACE RESPONDER");
		((TextView) findViewById(R.id.action_label)).setText("Replace triggered text with:");

		with = (EditText) findViewById(R.id.function);
		retargetSpinner = (Spinner) findViewById(R.id.retarget_spinner);
		retarget = (EditText) findViewById(R.id.retarget_text);

		setupRetargetSpinner();

		if (original != null) {
			ReplaceResponder src = (ReplaceResponder) original;
			with.setText(src.getWith());
			String existing = src.getRetarget();
			if (existing != null) {
				retarget.setText(existing);
			}
			selectSpinnerForValue(existing);
		} else {
			retargetSpinner.setSelection(0);
		}

		findViewById(R.id.done).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				String target = ExtraTextRetargetHelper.normalizeRetarget(
						retarget.getText() != null ? retarget.getText().toString() : null);
				if (original != null) {
					ReplaceResponder tmp = new ReplaceResponder();
					tmp.setWith(with.getText().toString());
					tmp.setFireType(original.getFireType());
					tmp.setRetarget(target);
					finish_with.editTriggerResponder(tmp, original);
				} else {
					ReplaceResponder tmp = new ReplaceResponder();
					tmp.setWith(with.getText().toString());
					tmp.setRetarget(target);
					finish_with.newTriggerResponder(tmp);
				}

				ReplaceActionEditorDialog.this.dismiss();
			}
		});

		findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				ReplaceActionEditorDialog.this.dismiss();
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

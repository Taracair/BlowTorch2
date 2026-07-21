package com.resurrection.blowtorch2.lib.trigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.trigger.condition.ConditionLeaf;
import com.resurrection.blowtorch2.lib.trigger.condition.ConditionType;
import com.resurrection.blowtorch2.lib.window.EditorDialogChrome;
import com.resurrection.blowtorch2.lib.window.PluginFilterSelectionDialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Edit a single trigger condition leaf.
 */
public class ConditionLeafEditorDialog extends Dialog {

	public interface DoneListener {
		void onConditionDone(ConditionLeaf leaf, ConditionLeaf originalOrNull);
	}

	private final ConditionLeaf editing;
	private final ConditionLeaf original;
	private final boolean isEdit;
	private final DoneListener listener;
	private final IConnectionBinder service;
	private final String selectedPlugin;

	private Spinner typeSpinner;
	private Spinner triggerSpinner;
	private EditText nameField;
	private EditText valueField;
	private TextView triggerLabel;
	private TextView nameLabel;
	private TextView valueLabel;
	private LinearLayout triggerRow;
	private LinearLayout nameRow;
	private LinearLayout valueRow;

	private ArrayList<String> triggerChoices = new ArrayList<String>();

	public ConditionLeafEditorDialog(Context context, ConditionLeaf input,
			IConnectionBinder service, String selectedPlugin, DoneListener listener) {
		super(context, EditorDialogChrome.dialogTheme());
		this.service = service;
		this.selectedPlugin = selectedPlugin;
		this.listener = listener;
		if (input == null) {
			editing = new ConditionLeaf();
			original = null;
			isEdit = false;
		} else {
			editing = input.copy();
			original = input.copy();
			isEdit = true;
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);

		LinearLayout root = new LinearLayout(getContext());
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(16, 12, 16, 12);

		TextView title = new TextView(getContext());
		title.setText(isEdit ? "EDIT CONDITION" : "NEW CONDITION");
		title.setTextColor(0xFF333333);
		title.setBackgroundColor(0xFF999999);
		title.setTextSize(15);
		title.setGravity(android.view.Gravity.CENTER);
		root.addView(title);

		typeSpinner = new Spinner(getContext());
		ArrayList<String> typeLabels = new ArrayList<String>();
		for (ConditionType t : ConditionType.values()) {
			typeLabels.add(t.displayLabel());
		}
		ArrayAdapter<String> typeAdapter = new ArrayAdapter<String>(getContext(),
				R.layout.spinner_item_dark, typeLabels);
		typeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
		typeSpinner.setAdapter(typeAdapter);
		root.addView(labeled("Type", typeSpinner));

		triggerSpinner = new Spinner(getContext());
		loadTriggerChoices();
		ArrayAdapter<String> trigAdapter = new ArrayAdapter<String>(getContext(),
				R.layout.spinner_item_dark, triggerChoices);
		trigAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
		triggerSpinner.setAdapter(trigAdapter);
		triggerRow = labeled("Trigger", triggerSpinner);
		triggerLabel = (TextView) triggerRow.getChildAt(0);
		root.addView(triggerRow);

		nameField = new EditText(getContext());
		nameField.setSingleLine(true);
		nameField.setHint("variable name");
		nameRow = labeled("Name", nameField);
		nameLabel = (TextView) nameRow.getChildAt(0);
		root.addView(nameRow);

		valueField = new EditText(getContext());
		valueField.setSingleLine(true);
		valueField.setHint("expected value");
		valueRow = labeled("Value", valueField);
		valueLabel = (TextView) valueRow.getChildAt(0);
		root.addView(valueRow);

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
				applyAndFinish();
			}
		});
		buttons.addView(cancel, new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
		buttons.addView(done, new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
		root.addView(buttons);

		setContentView(root);

		int typeIndex = 0;
		ConditionType[] types = ConditionType.values();
		for (int i = 0; i < types.length; i++) {
			if (types[i] == editing.getType()) {
				typeIndex = i;
				break;
			}
		}
		typeSpinner.setSelection(typeIndex, false);
		nameField.setText(editing.getName());
		valueField.setText(editing.getValue());
		selectTriggerChoice(editing.qualifiedTriggerName());

		typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				updateFieldVisibility();
			}

			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
		updateFieldVisibility();
	}

	private LinearLayout labeled(String label, View child) {
		LinearLayout row = new LinearLayout(getContext());
		row.setOrientation(LinearLayout.VERTICAL);
		TextView tv = new TextView(getContext());
		tv.setText(label);
		tv.setTextColor(0xFFE8E8E8);
		row.addView(tv);
		row.addView(child);
		return row;
	}

	private void updateFieldVisibility() {
		ConditionType type = ConditionType.values()[typeSpinner.getSelectedItemPosition()];
		boolean triggerType = type == ConditionType.TRIGGER_ENABLED
				|| type == ConditionType.TRIGGER_DISABLED;
		boolean needsValue = type == ConditionType.VARIABLE_EQUALS;
		triggerRow.setVisibility(triggerType ? View.VISIBLE : View.GONE);
		nameRow.setVisibility(triggerType ? View.GONE : View.VISIBLE);
		valueRow.setVisibility(needsValue ? View.VISIBLE : View.GONE);
		if (nameLabel != null) {
			nameLabel.setText(triggerType ? "Name" : "Variable");
		}
	}

	@SuppressWarnings("unchecked")
	private void loadTriggerChoices() {
		triggerChoices.clear();
		triggerChoices.add("(pick trigger)");
		TreeSet<String> names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		try {
			HashMap<String, TriggerData> map;
			if (selectedPlugin == null
					|| PluginFilterSelectionDialog.MAIN_SETTINGS.equals(selectedPlugin)) {
				map = (HashMap<String, TriggerData>) service.getTriggerData();
				if (map != null) {
					for (String n : map.keySet()) {
						if (n != null && n.length() > 0) {
							names.add(n);
						}
					}
				}
			} else {
				map = (HashMap<String, TriggerData>) service.getPluginTriggerData(selectedPlugin);
				if (map != null) {
					for (String n : map.keySet()) {
						if (n != null && n.length() > 0) {
							names.add(selectedPlugin + ":" + n);
						}
					}
				}
			}
			// Also offer other plugins for cross-deps when editing main.
			if (selectedPlugin == null
					|| PluginFilterSelectionDialog.MAIN_SETTINGS.equals(selectedPlugin)) {
				try {
					java.util.List<?> plugins = service.getPluginsWithTriggers();
					if (plugins != null) {
						for (Object o : plugins) {
							if (!(o instanceof String)) {
								continue;
							}
							String pname = (String) o;
							HashMap<String, TriggerData> pmap =
									(HashMap<String, TriggerData>) service.getPluginTriggerData(pname);
							if (pmap == null) {
								continue;
							}
							for (String n : pmap.keySet()) {
								if (n != null && n.length() > 0) {
									names.add(pname + ":" + n);
								}
							}
						}
					}
				} catch (RemoteException ignored) {
				}
			}
		} catch (RemoteException e) {
			// picker optional
		}
		triggerChoices.addAll(names);
	}

	private void selectTriggerChoice(String qualified) {
		if (qualified == null || qualified.length() == 0) {
			triggerSpinner.setSelection(0, false);
			return;
		}
		for (int i = 0; i < triggerChoices.size(); i++) {
			if (qualified.equals(triggerChoices.get(i))) {
				triggerSpinner.setSelection(i, false);
				return;
			}
		}
		// Free-form: put into name field path by appending
		triggerChoices.add(qualified);
		ArrayAdapter<String> trigAdapter = new ArrayAdapter<String>(getContext(),
				R.layout.spinner_item_dark, triggerChoices);
		trigAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
		triggerSpinner.setAdapter(trigAdapter);
		triggerSpinner.setSelection(triggerChoices.size() - 1, false);
	}

	private void applyAndFinish() {
		ConditionType type = ConditionType.values()[typeSpinner.getSelectedItemPosition()];
		editing.setType(type);
		if (type == ConditionType.TRIGGER_ENABLED || type == ConditionType.TRIGGER_DISABLED) {
			int idx = triggerSpinner.getSelectedItemPosition();
			String choice = idx > 0 && idx < triggerChoices.size() ? triggerChoices.get(idx) : "";
			if (choice.contains(":")) {
				int colon = choice.indexOf(':');
				editing.setPlugin(choice.substring(0, colon));
				editing.setName(choice.substring(colon + 1));
			} else {
				editing.setPlugin("");
				editing.setName(choice);
			}
			editing.setValue("");
		} else {
			editing.setPlugin("");
			editing.setName(nameField.getText().toString().trim());
			editing.setValue(type == ConditionType.VARIABLE_EQUALS
					? valueField.getText().toString() : "");
		}
		if (listener != null) {
			listener.onConditionDone(editing, isEdit ? original : null);
		}
		dismiss();
	}
}

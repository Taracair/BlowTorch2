package com.resurrection.blowtorch2.lib.trigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.alias.AliasData;
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
import android.widget.Toast;

/**
 * Edit a single trigger/timer condition leaf.
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
	private Spinner phoneSpinner;
	private View phoneRow;
	private TextView phoneNeeds;
	private final ArrayList<String> phoneChoices = new ArrayList<String>();
	private final String selectedPlugin;

	private Spinner typeSpinner;
	private Spinner triggerSpinner;
	private Spinner aliasSpinner;
	private EditText nameField;
	private EditText valueField;
	private TextView nameLabel;
	private LinearLayout triggerRow;
	private LinearLayout aliasRow;
	private LinearLayout nameRow;
	private LinearLayout valueRow;
	private TextView variableHint;

	private ArrayList<String> triggerChoices = new ArrayList<String>();
	private ArrayList<String> aliasChoices = new ArrayList<String>();

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
		root.addView(triggerRow);

		aliasSpinner = new Spinner(getContext());
		loadAliasChoices();
		ArrayAdapter<String> aliasAdapter = new ArrayAdapter<String>(getContext(),
				R.layout.spinner_item_dark, aliasChoices);
		aliasAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
		aliasSpinner.setAdapter(aliasAdapter);
		aliasRow = labeled("Alias", aliasSpinner);
		root.addView(aliasRow);

		// The phone, in words. Everything below this still works — a variable is
		// a variable — but nobody should have to know that "face down" is spelled
		// device.facing = down before they can gate a trigger on it.
		phoneSpinner = new Spinner(getContext());
		phoneChoices.clear();
		phoneChoices.add("(type a variable below)");
		for (com.resurrection.blowtorch2.lib.service.sensor.DeviceConditions.Choice ch
				: com.resurrection.blowtorch2.lib.service.sensor.DeviceConditions.all()) {
			phoneChoices.add(ch.getLabel());
		}
		ArrayAdapter<String> phoneAdapter = new ArrayAdapter<String>(getContext(),
				R.layout.spinner_item_dark, phoneChoices);
		phoneAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
		phoneSpinner.setAdapter(phoneAdapter);
		phoneRow = labeled("The phone", phoneSpinner);
		root.addView(phoneRow);

		phoneNeeds = new TextView(getContext());
		phoneNeeds.setTextColor(0xFFCCCCCC);
		phoneNeeds.setTextSize(12);
		phoneNeeds.setPadding(0, 0, 0, 6);
		root.addView(phoneNeeds);

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
		root.addView(valueRow);

		variableHint = new TextView(getContext());
		variableHint.setTextColor(0xFFCCCCCC);
		variableHint.setTextSize(12);
		variableHint.setPadding(0, 4, 0, 8);
		variableHint.setText(
				"Session variables are sticky notes for this connection — not pattern syntax.\n"
						+ "Set them with the Set Variable action or Lua SetVariable.\n"
						+ "Read them here, or as ${name} in alias / responder text.");
		root.addView(variableHint);

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

		phoneSpinner.setOnItemSelectedListener(
				new android.widget.AdapterView.OnItemSelectedListener() {
					@Override
					public void onItemSelected(android.widget.AdapterView<?> parent,
							View view, int position, long id) {
						if (position == 0) {
							phoneNeeds.setText(
									com.resurrection.blowtorch2.lib.service.sensor
										.DeviceConditions.NEEDS_WATCHING);
							return;
						}
						com.resurrection.blowtorch2.lib.service.sensor.DeviceConditions.Choice ch =
								com.resurrection.blowtorch2.lib.service.sensor.DeviceConditions
									.all().get(position - 1);
						nameField.setText(ch.getVariable());
						valueField.setText(ch.getValue());
						// The type has to be "variable equals" for this to mean
						// anything, and picking from this list is a clear enough
						// statement of intent to set it rather than complain.
						selectType(ConditionType.VARIABLE_EQUALS);
						phoneNeeds.setText("Needs: " + ch.getNeeds() + "\n\n"
								+ com.resurrection.blowtorch2.lib.service.sensor
									.DeviceConditions.NEEDS_WATCHING);
					}

					@Override
					public void onNothingSelected(android.widget.AdapterView<?> parent) {
					}
				});

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
		// Reopening a condition that came from the list shows it selected there,
		// rather than as two fields the player has to recognise.
		com.resurrection.blowtorch2.lib.service.sensor.DeviceConditions.Choice existing =
				com.resurrection.blowtorch2.lib.service.sensor.DeviceConditions.match(
						editing.getName(), editing.getValue());
		if (existing != null) {
			phoneSpinner.setSelection(
					com.resurrection.blowtorch2.lib.service.sensor.DeviceConditions
						.all().indexOf(existing) + 1);
		} else {
			phoneNeeds.setText(com.resurrection.blowtorch2.lib.service.sensor
					.DeviceConditions.NEEDS_WATCHING);
		}
		ConditionType initial = editing.getType() != null
				? editing.getType() : ConditionType.TRIGGER_ENABLED;
		if (initial.isTriggerGate()) {
			selectChoice(triggerSpinner, triggerChoices, editing.qualifiedName());
		} else if (initial.isAliasGate()) {
			selectChoice(aliasSpinner, aliasChoices, editing.qualifiedName());
		}

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

	/** Move the type spinner, which moves the visible fields with it. */
	private void selectType(final ConditionType wanted) {
		ConditionType[] types = ConditionType.values();
		for (int i = 0; i < types.length; i++) {
			if (types[i] == wanted) {
				if (typeSpinner.getSelectedItemPosition() != i) {
					typeSpinner.setSelection(i);
				}
				return;
			}
		}
	}

	private void updateFieldVisibility() {
		ConditionType type = ConditionType.values()[typeSpinner.getSelectedItemPosition()];
		boolean triggerType = type.isTriggerGate();
		boolean aliasType = type.isAliasGate();
		boolean variableType = type.isVariableGate();
		boolean needsValue = type.needsExpectedValue();
		triggerRow.setVisibility(triggerType ? View.VISIBLE : View.GONE);
		aliasRow.setVisibility(aliasType ? View.VISIBLE : View.GONE);
		nameRow.setVisibility(variableType ? View.VISIBLE : View.GONE);
		if (phoneRow != null) {
			phoneRow.setVisibility(variableType ? View.VISIBLE : View.GONE);
			phoneNeeds.setVisibility(variableType ? View.VISIBLE : View.GONE);
		}
		valueRow.setVisibility(needsValue ? View.VISIBLE : View.GONE);
		variableHint.setVisibility(variableType ? View.VISIBLE : View.GONE);
		if (nameLabel != null) {
			nameLabel.setText("Variable");
		}
		if (needsValue && type == ConditionType.ALIAS_EQUALS) {
			valueField.setHint("alias With / replacement text");
		} else if (needsValue) {
			valueField.setHint("expected value");
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
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"ConditionLeafEditorDialog.load triggers", e);
		}
		triggerChoices.addAll(names);
	}

	@SuppressWarnings("unchecked")
	private void loadAliasChoices() {
		aliasChoices.clear();
		aliasChoices.add("(pick alias)");
		TreeSet<String> names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		try {
			HashMap<String, AliasData> map;
			if (selectedPlugin == null
					|| PluginFilterSelectionDialog.MAIN_SETTINGS.equals(selectedPlugin)) {
				map = (HashMap<String, AliasData>) service.getAliases();
				if (map != null) {
					for (String n : map.keySet()) {
						if (n != null && n.length() > 0) {
							names.add(n);
						}
					}
				}
			} else {
				map = (HashMap<String, AliasData>) service.getPluginAliases(selectedPlugin);
				if (map != null) {
					for (String n : map.keySet()) {
						if (n != null && n.length() > 0) {
							names.add(selectedPlugin + ":" + n);
						}
					}
				}
			}
			if (selectedPlugin == null
					|| PluginFilterSelectionDialog.MAIN_SETTINGS.equals(selectedPlugin)) {
				try {
					java.util.List<?> plugins = service.getPluginsWithAliases();
					if (plugins != null) {
						for (Object o : plugins) {
							if (!(o instanceof String)) {
								continue;
							}
							String pname = (String) o;
							HashMap<String, AliasData> pmap =
									(HashMap<String, AliasData>) service.getPluginAliases(pname);
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
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"ConditionLeafEditorDialog.load aliases", e);
		}
		aliasChoices.addAll(names);
	}

	private void selectChoice(Spinner spinner, ArrayList<String> choices, String qualified) {
		if (qualified == null || qualified.length() == 0) {
			spinner.setSelection(0, false);
			return;
		}
		for (int i = 0; i < choices.size(); i++) {
			if (qualified.equals(choices.get(i))) {
				spinner.setSelection(i, false);
				return;
			}
		}
		choices.add(qualified);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),
				R.layout.spinner_item_dark, choices);
		adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
		spinner.setAdapter(adapter);
		spinner.setSelection(choices.size() - 1, false);
	}

	private void applyAndFinish() {
		ConditionType type = ConditionType.values()[typeSpinner.getSelectedItemPosition()];
		editing.setType(type);
		if (type.isTriggerGate()) {
			String choice = selectedChoice(triggerSpinner, triggerChoices);
			if (choice.length() == 0) {
				toastPickRequired("Pick a trigger for this condition.");
				return;
			}
			applyQualifiedName(choice);
			editing.setValue("");
		} else if (type.isAliasGate()) {
			String choice = selectedChoice(aliasSpinner, aliasChoices);
			if (choice.length() == 0) {
				toastPickRequired("Pick an alias for this condition.");
				return;
			}
			applyQualifiedName(choice);
			editing.setValue(type == ConditionType.ALIAS_EQUALS
					? valueField.getText().toString() : "");
		} else {
			editing.setPlugin("");
			editing.setName(nameField.getText().toString().trim());
			if (editing.getName().length() == 0) {
				toastPickRequired("Enter a variable name.");
				return;
			}
			editing.setValue(type == ConditionType.VARIABLE_EQUALS
					? valueField.getText().toString() : "");
		}
		if (listener != null) {
			listener.onConditionDone(editing, isEdit ? original : null);
		}
		dismiss();
	}

	private String selectedChoice(Spinner spinner, ArrayList<String> choices) {
		int idx = spinner.getSelectedItemPosition();
		if (idx > 0 && idx < choices.size()) {
			return choices.get(idx);
		}
		return "";
	}

	private void applyQualifiedName(String choice) {
		if (choice.contains(":")) {
			int colon = choice.indexOf(':');
			editing.setPlugin(choice.substring(0, colon));
			editing.setName(choice.substring(colon + 1));
		} else {
			editing.setPlugin("");
			editing.setName(choice);
		}
	}

	private void toastPickRequired(String message) {
		Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
	}
}

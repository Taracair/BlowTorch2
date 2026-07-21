package com.resurrection.blowtorch2.lib.timer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.TriggerResponderEditorDoneListener;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder.FIRE_WHEN;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder.RESPONDER_TYPE;
import com.resurrection.blowtorch2.lib.responder.ack.AckResponder;
import com.resurrection.blowtorch2.lib.responder.ack.AckResponderEditor;
import com.resurrection.blowtorch2.lib.responder.notification.NotificationResponder;
import com.resurrection.blowtorch2.lib.responder.notification.NotificationResponderEditor;
import com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableResponder;
import com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableResponderEditor;
import com.resurrection.blowtorch2.lib.responder.toast.ToastResponder;
import com.resurrection.blowtorch2.lib.responder.toast.ToastResponderEditor;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.trigger.ConditionLeafEditorDialog;
import com.resurrection.blowtorch2.lib.trigger.condition.ConditionGroup;
import com.resurrection.blowtorch2.lib.trigger.condition.ConditionLeaf;
import com.resurrection.blowtorch2.lib.validator.Validator;
import com.resurrection.blowtorch2.lib.window.EditorDialogChrome;
import com.resurrection.blowtorch2.lib.window.PluginFilterSelectionDialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.RelativeLayout.LayoutParams;

public class TimerEditorDialog extends Dialog implements DialogInterface.OnClickListener,TriggerResponderEditorDoneListener {

	private TableRow legend;
	private TableLayout responderTable;
	private TableLayout conditionsTable;
	
	private TimerData the_timer;
	private TimerData orig_timer;
	
	private IConnectionBinder service;
	
	private Handler finish_with;
	
	HashMap<Integer,Integer> checkopens;
	HashMap<Integer,Integer> checkclosed;
	
	private CheckBox repeat;
	private EditText name;
	private EditText seconds;
	
	private boolean isEditor = false;
	
	String plugin = PluginFilterSelectionDialog.MAIN_SETTINGS;
	
	public TimerEditorDialog(Context c,String plugin,TimerData input,IConnectionBinder pService,Handler reportto) {
		super(c, EditorDialogChrome.dialogTheme());
		service = pService;
		finish_with = reportto;
		
		if(input == null) {
			the_timer = new TimerData();
		} else {
			the_timer = input.copy();
			orig_timer = input.copy();
			isEditor = true;
		}
		
		this.plugin = plugin;
		
		checkopens = new HashMap<Integer,Integer>();
		checkclosed = new HashMap<Integer,Integer>();
		
	}
	
	public void onCreate(Bundle b) {
		super.onCreate(b);
		
		this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		
		setContentView(R.layout.timer_editor_dialog);
		
		name = (EditText)findViewById(R.id.timer_editor_name);
		seconds = (EditText)findViewById(R.id.timer_editor_seconds);
		
		repeat = (CheckBox)findViewById(R.id.timer_repeat_checkbox);
		

		legend= (TableRow)findViewById(R.id.timer_notification_legend);
		responderTable = (TableLayout)findViewById(R.id.timer_notification_table);
		
		Button newresponder = (Button)findViewById(R.id.timer_new_notification);
		newresponder.setOnClickListener(new NewResponderListener());
		
		
		refreshResponderTable();
		setupConditionsSection();
		
		//hook up additional buttons.
		Button cancelbutton = (Button)findViewById(R.id.timer_editor_cancel);
		cancelbutton.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				
				//TODO: Check if destroyed here.
				TimerEditorDialog.this.dismiss();
			}
		});
		
		Button donebutton = (Button)findViewById(R.id.timer_editor_done_button);
		donebutton.setOnClickListener(new TimerEditerDoneListener());
		
		
		if(isEditor) {
			name.setText(orig_timer.getName());
			seconds.setText(orig_timer.getSeconds().toString());
			repeat.setChecked(orig_timer.isRepeat());
			donebutton.setText("Done");

		}
		setupGroupField();
		EditorDialogChrome.applyNearlyFullScreen(this);
	}

	@SuppressWarnings("unchecked")
	private void setupGroupField() {
		AutoCompleteTextView group =
				(AutoCompleteTextView) findViewById(R.id.timer_editor_group);
		if (group == null) {
			return;
		}
		String existing = the_timer.getGroup();
		group.setText(existing != null ? existing : "");

		TreeSet<String> names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		try {
			HashMap<String, TimerData> map;
			if (PluginFilterSelectionDialog.MAIN_SETTINGS.equals(plugin)) {
				map = (HashMap<String, TimerData>) service.getTimers();
			} else {
				map = (HashMap<String, TimerData>) service.getPluginTimers(plugin);
			}
			if (map != null) {
				for (TimerData t : map.values()) {
					if (t == null) {
						continue;
					}
					String g = t.getGroup();
					if (g != null && g.length() > 0
							&& !TimerData.DEFAULT_GROUP.equals(g)) {
						names.add(g);
					}
				}
			}
		} catch (RemoteException e) {
			// optional
		}
		ArrayList<String> nameList = new ArrayList<String>(names);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),
				R.layout.spinner_dropdown_item_dark, nameList);
		group.setAdapter(adapter);
		group.setThreshold(1);
		group.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus && group.getAdapter() != null
						&& group.getAdapter().getCount() > 0) {
					group.showDropDown();
				}
			}
		});

		Spinner picker = (Spinner) findViewById(R.id.timer_editor_group_spinner);
		if (picker == null) {
			return;
		}
		final ArrayList<String> spinnerLabels = new ArrayList<String>();
		spinnerLabels.add("(default)");
		spinnerLabels.addAll(nameList);
		ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(getContext(),
				R.layout.spinner_item_dark, spinnerLabels);
		spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
		picker.setAdapter(spinnerAdapter);
		String current = group.getText() != null ? group.getText().toString().trim() : "";
		int selected = 0;
		if (current.length() > 0) {
			for (int i = 0; i < nameList.size(); i++) {
				if (nameList.get(i).equals(current)) {
					selected = i + 1;
					break;
				}
			}
		}
		picker.setSelection(selected, false);
		picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			private boolean first = true;
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				if (first) {
					first = false;
					return;
				}
				if (position <= 0) {
					group.setText("");
				} else {
					group.setText(spinnerLabels.get(position));
					group.setSelection(group.getText().length());
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
	}

	private String readGroupField() {
		AutoCompleteTextView group =
				(AutoCompleteTextView) findViewById(R.id.timer_editor_group);
		if (group == null) {
			return TimerData.DEFAULT_GROUP;
		}
		String text = group.getText() != null ? group.getText().toString().trim() : "";
		return text.length() == 0 ? TimerData.DEFAULT_GROUP : text;
	}
	
	private class TimerEditerDoneListener implements View.OnClickListener {

		public void onClick(View v) {
			//here we validate and invoke the timer saving.
			
			
			Validator checker = new Validator();
			checker.add(name, Validator.VALIDATE_NOT_BLANK, "Timer Name");
			checker.add(seconds, Validator.VALIDATE_NOT_BLANK|Validator.VALIDATE_NUMBER|Validator.VALIDATE_NUMBER_NOT_ZERO, "Timer duration");
			
			String result = checker.validate();
			if(result != null) {
				checker.showMessage(TimerEditorDialog.this.getContext(), result);
				return;
			}
			
			String theName = name.getText().toString();
			String theSeconds = seconds.getText().toString();
			boolean theRepeat = repeat.isChecked();
				
			//now we are validated. proceed with save.
			if(isEditor) {
				the_timer.setName(theName);
				the_timer.setSeconds(Integer.parseInt(theSeconds));
				the_timer.setRepeat(theRepeat);
				the_timer.setGroup(readGroupField());

				//responders should be handled already.
				try {
					if(plugin.equals(PluginFilterSelectionDialog.MAIN_SETTINGS)) {
						service.updateTimer(orig_timer, the_timer);
					} else {
						service.updatePluginTimer(plugin, orig_timer, the_timer);
					}
					service.saveSettings();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				finish_with.sendMessageDelayed(finish_with.obtainMessage(100, the_timer),10);
			} else {
				the_timer.setName(theName);
				the_timer.setSeconds(Integer.parseInt(theSeconds));
				the_timer.setRepeat(theRepeat);
				the_timer.setGroup(readGroupField());

				try {
					if(plugin.equals(PluginFilterSelectionDialog.MAIN_SETTINGS)) {
						service.addTimer(the_timer);
					} else {
						service.addPluginTimer(plugin,the_timer);
					}
					service.saveSettings();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				finish_with.sendMessageDelayed(finish_with.obtainMessage(100,the_timer),10);
			}
			
			
			TimerEditorDialog.this.dismiss();
		}
		
	};
	
	private void refreshResponderTable() {
		
		legend.setVisibility(View.GONE);
		
		TableRow newbutton = (TableRow)findViewById(R.id.timer_new_responder_row);
		//responderTable.removeView(newbutton);
		responderTable.removeViews(1, responderTable.getChildCount()-1);
		
		//RelativeLayout p = (RelativeLayout)findViewById(R.id.newtriggerlayout);
		LayoutParams params = new LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,RelativeLayout.LayoutParams.WRAP_CONTENT);
		int margin =  (int) (0*this.getContext().getResources().getDisplayMetrics().density);
		params.rightMargin = margin;
		params.leftMargin =  margin;
		params.topMargin =  margin;
		params.bottomMargin = margin;
		
		int checkboxnumber = 123456; //generate semi unique id's each time we generate this table. we need to do this because the check changed listeners are freaking. out.
		
		checkopens.clear();
		checkclosed.clear();
		int count = 0;
		//boolean legendAdded = false;
		for(TriggerResponder responder : the_timer.getResponders()) {
			//if(!legendAdded) {
			//	responderTable.addView(legend);
			//	legendAdded = true;
			//}
			TableRow row = new TableRow(this.getContext());
			
			TextView label = new TextView(this.getContext());
			label.setOnClickListener(new EditResponderListener(the_timer.getResponders().indexOf(responder)));
			if(responder.getType() == RESPONDER_TYPE.NOTIFICATION) {
				label.setText("Notification: " + ((NotificationResponder)responder).getTitle());
			} else if(responder.getType() == RESPONDER_TYPE.TOAST) {
				label.setText("Toast Message: " + ((ToastResponder)responder).getMessage());
			} else if(responder.getType() == RESPONDER_TYPE.ACK){
				label.setText("Ack With: " + ((AckResponder)responder).getAckWith());
			} else if(responder.getType() == RESPONDER_TYPE.SET_VARIABLE) {
				SetVariableResponder sv = (SetVariableResponder) responder;
				label.setText("SetVariable: " + sv.getVariableName() + "=" + sv.getVariableValue());
			}
			label.setGravity(Gravity.CENTER);
			label.setSingleLine(true);
			
			TableRow.LayoutParams labelLp = new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f);
			label.setLayoutParams(labelLp);
			LinearLayout l1 = new LinearLayout(this.getContext());
			l1.setGravity(Gravity.CENTER);
			LinearLayout l2 = new LinearLayout(this.getContext());
			l2.setGravity(Gravity.CENTER);
			CheckBox windowOpen = new CheckBox(this.getContext()); windowOpen.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL); windowOpen.setId(checkboxnumber);
			checkopens.put(the_timer.getResponders().indexOf(responder), checkboxnumber);
			checkboxnumber++;
			
			CheckBox windowClose = new CheckBox(this.getContext()); windowClose.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL); windowClose.setId(checkboxnumber);
			checkclosed.put(the_timer.getResponders().indexOf(responder),checkboxnumber);
			checkboxnumber++;

			
			windowOpen.setOnCheckedChangeListener(new WindowOpenCheckChangeListener(the_timer.getResponders().indexOf(responder)));
			windowClose.setOnCheckedChangeListener(new WindowClosedCheckChangeListener(the_timer.getResponders().indexOf(responder)));
			
			int deleteSize = (int) (36 * this.getContext().getResources().getDisplayMetrics().density);
			LinearLayout deleteHolder = new LinearLayout(this.getContext());
			deleteHolder.setGravity(Gravity.CENTER);
			ImageButton delete = new ImageButton(this.getContext());
			delete.setBackgroundColor(0);
			delete.setImageResource(android.R.drawable.ic_menu_delete);
			delete.setPadding(0, 0, 0, 0);
			delete.setLayoutParams(new LinearLayout.LayoutParams(deleteSize, deleteSize));
			delete.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
			delete.setOnClickListener(new DeleteResponderListener(the_timer.getResponders().indexOf(responder)));
			deleteHolder.addView(delete);
			
			windowOpen.setGravity(Gravity.CENTER); windowOpen.setText("");
			windowClose.setGravity(Gravity.CENTER); windowClose.setText("");
			
			l1.addView(windowOpen);
			l2.addView(windowClose);
			
			TableRow.LayoutParams fixedLp = new TableRow.LayoutParams(
					TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
			l1.setLayoutParams(fixedLp);
			l2.setLayoutParams(fixedLp);
			deleteHolder.setLayoutParams(fixedLp);
			
			row.addView(label); row.addView(l1); row.addView(l2); row.addView(deleteHolder);
			responderTable.addView(row);
			
			
			if(responder.getFireType() == FIRE_WHEN.WINDOW_OPEN || responder.getFireType()==FIRE_WHEN.WINDOW_BOTH) {
				windowOpen.setChecked(true);
				//windowOpen.setText("OC");
			} else {
				windowOpen.setChecked(false);
				//windowOpen.setText("OU");
			}
			
			if(responder.getFireType() == FIRE_WHEN.WINDOW_CLOSED || responder.getFireType() == FIRE_WHEN.WINDOW_BOTH) {
				windowClose.setChecked(true);
				//windowClose.setText("CC");
			} else {
				windowClose.setChecked(false);
				//windowClose.setText("CU");
			}
			
			count++;
		}
		if(count > 0) {
			
			legend.setVisibility(View.VISIBLE);
		}
		
		responderTable.addView(newbutton);
	}
	
	private class DeleteResponderListener implements View.OnClickListener,DialogInterface.OnClickListener {

		int position;
		
		public DeleteResponderListener(int i) {
			position = i;
		}
		
		public void onClick(View arg0) {
			AlertDialog.Builder builder = new AlertDialog.Builder(TimerEditorDialog.this.getContext());
			builder.setPositiveButton("Delete", this);
			builder.setNegativeButton("Cancel", this);
			builder.setTitle("Are you sure?");
			AlertDialog deleter = builder.create();
			deleter.show();
		}

		public void onClick(DialogInterface arg0, int arg1) {
			if(arg1 == DialogInterface.BUTTON_POSITIVE) {
				//really delete the button
				the_timer.getResponders().remove(position);
				refreshResponderTable();
			}
		}
		
	};
	
	private class NewResponderListener implements View.OnClickListener {

		public void onClick(View v) {
			//give out a list of options
			CharSequence[] items = {"Notification","Toast Message","Ack With","Set Variable"};
			AlertDialog.Builder builder = new AlertDialog.Builder(TimerEditorDialog.this.getContext());
			builder.setTitle("Type:");
			
			builder.setItems(items, TimerEditorDialog.this);
			AlertDialog dialog = builder.create();
			dialog.show();
		}
		
	}
	
	public void onClick(DialogInterface arg0, int arg1) {
		arg0.dismiss();
		switch(arg1) {
		case 0: //notificaiton
			NotificationResponderEditor notifyEditor = new NotificationResponderEditor(this.getContext(),null,this);
			notifyEditor.show();
			break;
		case 1: //toast
			ToastResponderEditor tedit = new ToastResponderEditor(TimerEditorDialog.this.getContext(),null,TimerEditorDialog.this);
			tedit.show();
			break; 
		case 2:
			AckResponderEditor aedit = new AckResponderEditor(TimerEditorDialog.this.getContext(),null,TimerEditorDialog.this);
			aedit.show();
			break; //ack
		case 3:
			new SetVariableResponderEditor(TimerEditorDialog.this.getContext(), null, TimerEditorDialog.this).show();
			break;
		default:
			break;
		}
		
	}
	
	private class EditResponderListener implements View.OnClickListener {

		int position;
		
		public EditResponderListener(int pos) {
			position = pos;
		}
		
		public void onClick(View v) {
			TriggerResponder responder = the_timer.getResponders().get(position);
			switch(responder.getType()) {
			case NOTIFICATION:
				//show the notification editor
				NotificationResponderEditor redit = new NotificationResponderEditor(TimerEditorDialog.this.getContext(),(NotificationResponder)responder.copy(),TimerEditorDialog.this);
				redit.show();
				break;
			case TOAST:
				ToastResponderEditor tedit = new ToastResponderEditor(TimerEditorDialog.this.getContext(),(ToastResponder)responder.copy(),TimerEditorDialog.this);
				tedit.show();
				break;
			case ACK:
				AckResponderEditor aedit = new AckResponderEditor(TimerEditorDialog.this.getContext(),(AckResponder)responder.copy(),TimerEditorDialog.this);
				aedit.show();
				break;
			case SET_VARIABLE:
				new SetVariableResponderEditor(TimerEditorDialog.this.getContext(),
						(SetVariableResponder) responder.copy(), TimerEditorDialog.this).show();
				break;
			default:
				break;
			}
			
		}
		
	}

	public void editTriggerResponder(TriggerResponder edited,
			TriggerResponder original) {
		int pos = the_timer.getResponders().indexOf(original);
		the_timer.getResponders().remove(pos);
		the_timer.getResponders().add(pos,edited);
		refreshResponderTable();
	}

	public void newTriggerResponder(TriggerResponder newresponder) {
		the_timer.getResponders().add(newresponder);
		refreshResponderTable();
		
	}
	
	private class WindowOpenCheckChangeListener implements CompoundButton.OnCheckedChangeListener {

		private final int position;
		
		WindowOpenCheckChangeListener(int i) {
			position = i;
		}
		
		public void onCheckedChanged(CompoundButton arg0, boolean checked) {
			if(checked) {
				//check the closed check state.
				the_timer.getResponders().get(position).addFireType(FIRE_WHEN.WINDOW_OPEN);
				///Log.e("TEDITOR","TRIGGER TYPE " + the_trigger.getResponders().get(position).getType().getIntVal() + " AT "+ position + " ADDING windowOpen");
			} else {
				the_timer.getResponders().get(position).removeFireType(FIRE_WHEN.WINDOW_OPEN);
				//Log.e("TEDITOR","TRIGGER TYPE " + the_trigger.getResponders().get(position).getType().getIntVal() + " AT "+ position + " REMOVING windowOpen");
			}
			//Log.e("TEDITOR","TRIGGER TYPE " + the_trigger.getResponders().get(position).getType() + " AT "+ position + " NOW " + the_trigger.getResponders().get(position).getFireType().getString());
			
			
		}
		
	};
	
	private class WindowClosedCheckChangeListener implements CompoundButton.OnCheckedChangeListener {

		private final int position;
		
		WindowClosedCheckChangeListener(int i) {
			position = i;
		}
		
		public void onCheckedChanged(CompoundButton arg0, boolean checked) {
			if(checked) {
				//check the closed check state.
				the_timer.getResponders().get(position).addFireType(FIRE_WHEN.WINDOW_CLOSED);
				//Log.e("TEDITOR","TRIGGER TYPE " + the_trigger.getResponders().get(position).getType().getIntVal() + " AT "+ position + " ADDING windowClosed");
			} else {
				the_timer.getResponders().get(position).removeFireType(FIRE_WHEN.WINDOW_CLOSED);
				//Log.e("TEDITOR","TRIGGER TYPE " + the_trigger.getResponders().get(position).getType().getIntVal() + " AT "+ position + " REMOVING windowClosed");
				
			}
			//Log.e("TEDITOR","TRIGGER TYPE " + the_trigger.getResponders().get(position).getType().getIntVal() + " AT "+ position + " NOW " + the_trigger.getResponders().get(position).getFireType().getString());
			
		}
		
	};
	
	private void setupConditionsSection() {
		conditionsTable = (TableLayout) findViewById(R.id.timer_conditions_table);
		Spinner opSpinner = (Spinner) findViewById(R.id.timer_conditions_op);
		if (the_timer.getConditions() == null) {
			the_timer.setConditions(new ConditionGroup());
		}
		if (opSpinner != null) {
			ArrayAdapter<String> opAdapter = new ArrayAdapter<String>(getContext(),
					R.layout.spinner_item_dark,
					new String[] { "All (AND)", "Any (OR)" });
			opAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
			opSpinner.setAdapter(opAdapter);
			opSpinner.setSelection(
					the_timer.getConditions().getOp() == ConditionGroup.Op.OR ? 1 : 0, false);
			opSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					the_timer.getConditions().setOp(
							position == 1 ? ConditionGroup.Op.OR : ConditionGroup.Op.AND);
					updateConditionsHint();
				}
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});
		}
		Button add = (Button) findViewById(R.id.timer_new_condition);
		if (add != null) {
			add.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					new ConditionLeafEditorDialog(
							TimerEditorDialog.this.getContext(), null, service, plugin,
							new ConditionLeafEditorDialog.DoneListener() {
								public void onConditionDone(ConditionLeaf leaf, ConditionLeaf originalOrNull) {
									the_timer.getConditions().getChildren().add(leaf);
									refreshConditionsTable();
								}
							}).show();
				}
			});
		}
		refreshConditionsTable();
	}

	private void updateConditionsHint() {
		TextView hint = (TextView) findViewById(R.id.timer_conditions_hint);
		if (hint == null) {
			return;
		}
		if (the_timer.getConditions() == null || the_timer.getConditions().isEmpty()) {
			hint.setText("No conditions — always runs when the timer fires.");
		} else if (the_timer.getConditions().getOp() == ConditionGroup.Op.OR) {
			hint.setText("Any condition may be true (OR).");
		} else {
			hint.setText("All conditions must be true (AND).");
		}
	}

	private void refreshConditionsTable() {
		if (conditionsTable == null) {
			conditionsTable = (TableLayout) findViewById(R.id.timer_conditions_table);
		}
		if (conditionsTable == null) {
			return;
		}
		conditionsTable.removeAllViews();
		if (the_timer.getConditions() == null) {
			the_timer.setConditions(new ConditionGroup());
		}
		updateConditionsHint();
		int deleteSize = (int) (36 * getContext().getResources().getDisplayMetrics().density);
		java.util.List<ConditionLeaf> leaves = the_timer.getConditions().getChildren();
		for (int i = 0; i < leaves.size(); i++) {
			final int index = i;
			ConditionLeaf leaf = leaves.get(i);
			TableRow row = new TableRow(getContext());
			TextView label = new TextView(getContext());
			label.setText(leaf.summary());
			label.setTextColor(0xFFE8E8E8);
			label.setSingleLine(true);
			label.setGravity(Gravity.CENTER_VERTICAL);
			label.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f));
			label.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					ConditionLeaf existing = the_timer.getConditions().getChildren().get(index);
					new ConditionLeafEditorDialog(
							TimerEditorDialog.this.getContext(), existing, service, plugin,
							new ConditionLeafEditorDialog.DoneListener() {
								public void onConditionDone(ConditionLeaf leaf, ConditionLeaf originalOrNull) {
									the_timer.getConditions().getChildren().set(index, leaf);
									refreshConditionsTable();
								}
							}).show();
				}
			});
			LinearLayout deleteHolder = new LinearLayout(getContext());
			deleteHolder.setGravity(Gravity.CENTER);
			ImageButton delete = new ImageButton(getContext());
			delete.setBackgroundColor(0);
			delete.setImageResource(android.R.drawable.ic_menu_delete);
			delete.setPadding(0, 0, 0, 0);
			delete.setLayoutParams(new LinearLayout.LayoutParams(deleteSize, deleteSize));
			delete.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
			delete.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					the_timer.getConditions().getChildren().remove(index);
					refreshConditionsTable();
				}
			});
			deleteHolder.addView(delete);
			row.addView(label);
			row.addView(deleteHolder);
			conditionsTable.addView(row);
		}
	}
	
}

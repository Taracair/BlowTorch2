package com.resurrection.blowtorch2.lib.timer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.gauge.GaugeTimerWidgetBind;
import com.resurrection.blowtorch2.lib.gauge.GaugeWidget;
import com.resurrection.blowtorch2.lib.gauge.GaugeWidgetsStore;
import com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsGroup;
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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
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
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

public class TimerEditorDialog extends Dialog implements DialogInterface.OnClickListener,TriggerResponderEditorDoneListener {

	private LinearLayout actionList;
	private TableLayout conditionsTable;
	
	private TimerData the_timer;
	private TimerData orig_timer;
	
	private IConnectionBinder service;
	
	private Handler finish_with;
	
	private CheckBox repeat;
	private CheckBox showAsWidget;
	private EditText name;
	private EditText hours;
	private EditText minutes;
	private EditText seconds;
	private TextView durationSummary;
	
	private boolean isEditor = false;
	
	String plugin = PluginFilterSelectionDialog.MAIN_SETTINGS;
	
	public TimerEditorDialog(Context c,String plugin,TimerData input,IConnectionBinder pService,Handler reportto) {
		super(c, EditorDialogChrome.fullScreenTheme());
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
	}
	
	public void onCreate(Bundle b) {
		super.onCreate(b);
		
		this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		
		setContentView(R.layout.timer_editor_dialog);
		
		name = (EditText)findViewById(R.id.timer_editor_name);
		hours = (EditText)findViewById(R.id.timer_editor_hours);
		minutes = (EditText)findViewById(R.id.timer_editor_minutes);
		seconds = (EditText)findViewById(R.id.timer_editor_seconds);
		durationSummary = (TextView)findViewById(R.id.timer_editor_duration_summary);

		repeat = (CheckBox)findViewById(R.id.timer_repeat_checkbox);
		showAsWidget = (CheckBox)findViewById(R.id.timer_show_as_widget);
		

		actionList = (LinearLayout)findViewById(R.id.timer_action_list);
		
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

		Button morebutton = (Button)findViewById(R.id.timer_editor_help_button);
		if (morebutton != null) {
			morebutton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					showTimerHelp();
				}
			});
		}
		
		
		setupDurationFields();

		if(isEditor) {
			name.setText(orig_timer.getName());
			Integer stored = orig_timer.getSeconds();
			setDurationFields(stored != null ? stored.intValue() : 0);
			repeat.setChecked(orig_timer.isRepeat());
			if (showAsWidget != null) {
				showAsWidget.setChecked(GaugeTimerWidgetBind.isShowing(
						readGaugeWidgets(), orig_timer.getName()));
			}
			donebutton.setText("Done");

		}
		updateDurationSummary();
		setupGroupField();
		EditorDialogChrome.applyFullScreen(this);
	}

	/** The ? beside Done: what a timer is and what the fields do. */
	private void showTimerHelp() {
		com.resurrection.blowtorch2.lib.window.EditorHelp.show(
				getContext(), "Timer editor", TIMER_HELP_TEXT);
	}

	static final String TIMER_HELP_TEXT =
			"A timer waits, then runs its actions. Nothing has to happen in the game "
			+ "for it to fire -- that is what makes it different from a trigger.\n\n"
			+ "EVERY\n"
			+ "Hours, minutes and seconds are added up, not range-checked, so 90 in "
			+ "the seconds box is the same as 1m 30s. The line underneath shows the "
			+ "total that will actually be used. The presets fill the boxes for you.\n\n"
			+ "REPEAT\n"
			+ "Off: it fires once and stops. On: it starts again as soon as it fires. "
			+ "A repeating 1-second timer sends its command every second, so watch "
			+ "what the actions do.\n\n"
			+ "ACTIONS\n"
			+ "The same list as a trigger: Ack sends a command, Toast and Notification "
			+ "put something on the phone, Set Variable stores a session value. There "
			+ "is no matched line here, so there is no $1 to use.\n\n"
			+ "CONDITIONS\n"
			+ "Checked when the timer fires, not while it counts down — not a substitute "
			+ "for the interval. A timer whose condition is false at that moment does "
			+ "nothing and, if it repeats, goes round again. It is a gate, not a pause. "
			+ "Example: Variable equals fighting = 1, set from your combat triggers, "
			+ "and a healing timer that runs all the time but only acts in a fight. "
			+ "Variables are session sticky notes (Set Variable / Lua SetVariable); "
			+ "use ${name} in alias or action text.\n\n"
			+ "FROM THE INPUT BAR, BY NAME\n"
			+ "    .timer play <name>       start it\n"
			+ "    .timer pause <name>      hold it where it is\n"
			+ "    .timer reset <name>      back to full duration\n"
			+ "    .timer stop <name>       stop and reset\n"
			+ "    .timer info <name>       how long is left\n"
			+ "    .timer duration <name> <seconds>\n"
			+ "Add silent as a last word to suppress the toast: .timer play heal "
			+ "silent. Useful when a trigger drives the timer.\n\n"
			+ "Changing the duration does not stop the timer: one that was running "
			+ "keeps running on the new length, from now.\n\n"
			+ "GROUP\n"
			+ "A label like combat. The Timers list shows it, sorts by it, and filters "
			+ "on it. It is for finding timers, not for switching them: there is no "
			+ ".timer group command.\n\n"
			+ "WHILE THE PHONE SLEEPS\n"
			+ "Timers run in the connection\'s own process, so they keep counting while "
			+ "the game window is in the background. Android can still delay a long "
			+ "one on a sleeping phone; a timer is not an alarm clock.\n\n"
			+ "OVERLAY WIDGET\n"
			+ "Check Show as overlay widget to put a countdown on the game window, "
			+ "bound to this timer's name. Uncheck hides it (it is not deleted). "
			+ "Options → Window → Widgets → Manage widgets…, or "
			+ ".widget source <id> timer <name>.";

	/** Wire the h/m/s boxes and the preset row; the running total is echoed under them. */
	private void setupDurationFields() {
		TextWatcher echo = new TextWatcher() {
			public void beforeTextChanged(CharSequence s, int a, int b, int c) {
			}

			public void onTextChanged(CharSequence s, int a, int b, int c) {
			}

			public void afterTextChanged(Editable s) {
				updateDurationSummary();
			}
		};
		if (hours != null) {
			hours.addTextChangedListener(echo);
		}
		if (minutes != null) {
			minutes.addTextChangedListener(echo);
		}
		if (seconds != null) {
			seconds.addTextChangedListener(echo);
		}

		LinearLayout presets = (LinearLayout) findViewById(R.id.timer_editor_presets);
		if (presets == null) {
			return;
		}
		presets.removeAllViews();
		addPreset(presets, "30s", 30);
		addPreset(presets, "1m", 60);
		addPreset(presets, "5m", 5 * 60);
		addPreset(presets, "15m", 15 * 60);
		addPreset(presets, "1h", 60 * 60);
	}

	private void addPreset(LinearLayout row, String label, final int totalSeconds) {
		Button b = new Button(getContext());
		b.setText(label);
		b.setTextSize(12);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		lp.rightMargin = (int) (4 * getContext().getResources().getDisplayMetrics().density);
		b.setLayoutParams(lp);
		b.setPadding(0, 0, 0, 0);
		b.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				setDurationFields(totalSeconds);
			}
		});
		row.addView(b);
	}

	private void setDurationFields(int totalSeconds) {
		if (hours != null) {
			hours.setText(Integer.toString(TimerDuration.hoursOf(totalSeconds)));
		}
		if (minutes != null) {
			minutes.setText(Integer.toString(TimerDuration.minutesOf(totalSeconds)));
		}
		if (seconds != null) {
			seconds.setText(Integer.toString(TimerDuration.secondsOf(totalSeconds)));
		}
		updateDurationSummary();
	}

	/** Total the three boxes. Blank is zero, and 90 in any box just adds — see TimerDuration. */
	private int readDurationSeconds() {
		return TimerDuration.toSeconds(
				TimerDuration.parseField(hours != null ? hours.getText().toString() : null),
				TimerDuration.parseField(minutes != null ? minutes.getText().toString() : null),
				TimerDuration.parseField(seconds != null ? seconds.getText().toString() : null));
	}

	private void updateDurationSummary() {
		if (durationSummary == null) {
			return;
		}
		int total = readDurationSeconds();
		if (total <= 0) {
			durationSummary.setText("");
		} else {
			durationSummary.setText("= " + TimerDuration.format(total)
					+ " (" + total + "s)");
		}
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
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("TimerEditorDialog.save timer", e);
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

	private ArrayList<GaugeWidget> readGaugeWidgets() {
		try {
			SettingsGroup sg = service.getSettings();
			if (sg != null) {
				Object o = sg.findOptionByKey(GaugeWidgetsStore.SETTING_KEY);
				if (o instanceof StringOption) {
					Object val = ((StringOption) o).getValue();
					if (val != null) {
						return GaugeWidgetsStore.parse(val.toString());
					}
				}
			}
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"TimerEditorDialog.readGaugeWidgets", e);
		}
		return new ArrayList<GaugeWidget>();
	}

	private void applyShowAsWidget(final String previousName, final String timerName) {
		if (showAsWidget == null || timerName == null || timerName.trim().length() == 0) {
			return;
		}
		ArrayList<GaugeWidget> list = readGaugeWidgets();
		boolean want = showAsWidget.isChecked();
		if (want) {
			GaugeWidget g;
			if (previousName != null && previousName.trim().length() > 0
					&& !previousName.trim().equalsIgnoreCase(timerName.trim())) {
				g = GaugeTimerWidgetBind.rebind(list, previousName, timerName);
			} else {
				g = GaugeTimerWidgetBind.ensure(list, timerName);
			}
			if (g == null) {
				return;
			}
		} else {
			GaugeTimerWidgetBind.hide(list, previousName);
			GaugeTimerWidgetBind.hide(list, timerName);
		}
		try {
			service.updateStringSetting(GaugeWidgetsStore.SETTING_KEY,
					GaugeWidgetsStore.toJson(list));
			com.resurrection.blowtorch2.lib.util.SettingsSaver.saveInBackground(service);
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"TimerEditorDialog.applyShowAsWidget", e);
		}
	}

	private class TimerEditerDoneListener implements View.OnClickListener {

		public void onClick(View v) {
			//here we validate and invoke the timer saving.
			
			
			Validator checker = new Validator();
			checker.add(name, Validator.VALIDATE_NOT_BLANK, "Timer Name");

			String result = checker.validate();
			if(result != null) {
				checker.showMessage(TimerEditorDialog.this.getContext(), result);
				return;
			}

			// The duration is three boxes now, so it is validated as a total: a blank hours
			// box is not an error, and a timer of 1h 0m 0s must not be rejected for having
			// a zero in it. Only "adds up to nothing" is wrong.
			int theSeconds = readDurationSeconds();
			if(theSeconds <= 0) {
				checker.showMessage(TimerEditorDialog.this.getContext(),
						"Timer duration must be more than zero.");
				return;
			}

			String theName = name.getText().toString();
			boolean theRepeat = repeat.isChecked();
				
			//now we are validated. proceed with save.
			if(isEditor) {
				the_timer.setName(theName);
				the_timer.setSeconds(theSeconds);
				the_timer.setRepeat(theRepeat);
				the_timer.setGroup(readGroupField());
				// Saving must not persist a stale playing flag from when the dialog opened.
				the_timer.setPlaying(false);

				//responders should be handled already.
				try {
					if(plugin.equals(PluginFilterSelectionDialog.MAIN_SETTINGS)) {
						service.updateTimer(orig_timer, the_timer);
					} else {
						service.updatePluginTimer(plugin, orig_timer, the_timer);
					}
					// Off the UI thread: updateTimer above is synchronous, so the
					// service already holds what this write puts down.
					com.resurrection.blowtorch2.lib.util.SettingsSaver.saveInBackground(service);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				finish_with.sendMessageDelayed(finish_with.obtainMessage(100, the_timer),10);
			} else {
				the_timer.setName(theName);
				the_timer.setSeconds(theSeconds);
				the_timer.setRepeat(theRepeat);
				the_timer.setGroup(readGroupField());
				the_timer.setPlaying(false);

				try {
					if(plugin.equals(PluginFilterSelectionDialog.MAIN_SETTINGS)) {
						service.addTimer(the_timer);
					} else {
						service.addPluginTimer(plugin,the_timer);
					}
					// As above: the new timer is in the service already.
					com.resurrection.blowtorch2.lib.util.SettingsSaver.saveInBackground(service);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				finish_with.sendMessageDelayed(finish_with.obtainMessage(100,the_timer),10);
			}

			String previousName = (isEditor && orig_timer != null)
					? orig_timer.getName() : null;
			applyShowAsWidget(previousName, theName);
			
			TimerEditorDialog.this.dismiss();
		}
		
	};
	
	/**
	 * Sensors-style rows. Fire-when stays on the row (Open / Closed) because
	 * none of the responder editors expose Window Open / Window Closed.
	 */
	private void refreshResponderTable() {
		if (actionList == null) {
			actionList = (LinearLayout) findViewById(R.id.timer_action_list);
		}
		if (actionList == null) {
			return;
		}
		actionList.removeAllViews();
		LayoutInflater inflater = LayoutInflater.from(getContext());
		List<TriggerResponder> responders = the_timer.getResponders();
		for (int position = 0; position < responders.size(); position++) {
			TriggerResponder responder = responders.get(position);
			View row = inflater.inflate(R.layout.editor_action_row, actionList, false);
			TextView type = (TextView) row.findViewById(R.id.action_row_type);
			TextView summary = (TextView) row.findViewById(R.id.action_row_summary);
			CheckBox windowOpen = (CheckBox) row.findViewById(R.id.action_row_open);
			CheckBox windowClosed = (CheckBox) row.findViewById(R.id.action_row_closed);
			ImageButton delete = (ImageButton) row.findViewById(R.id.action_row_delete);
			View body = row.findViewById(R.id.action_row_body);

			type.setText(com.resurrection.blowtorch2.lib.trigger.TriggerEditorDialog.actionTypeLabel(responder));
			String line = com.resurrection.blowtorch2.lib.trigger.TriggerEditorDialog.actionSummary(responder);
			if (line.length() == 0) {
				summary.setVisibility(View.GONE);
			} else {
				summary.setText(line);
			}

			EditResponderListener edit = new EditResponderListener(position);
			body.setOnClickListener(edit);
			type.setOnClickListener(edit);
			summary.setOnClickListener(edit);
			delete.setOnClickListener(new DeleteResponderListener(position));

			windowOpen.setOnCheckedChangeListener(new WindowOpenCheckChangeListener(position));
			windowClosed.setOnCheckedChangeListener(new WindowClosedCheckChangeListener(position));
			FIRE_WHEN fire = responder.getFireType();
			windowOpen.setChecked(fire == FIRE_WHEN.WINDOW_OPEN || fire == FIRE_WHEN.WINDOW_BOTH);
			windowClosed.setChecked(fire == FIRE_WHEN.WINDOW_CLOSED || fire == FIRE_WHEN.WINDOW_BOTH);
			com.resurrection.blowtorch2.lib.trigger.TriggerEditorDialog.confineFireWhenCheckBoxes(
					windowOpen, windowClosed);

			actionList.addView(row);
		}
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
			// Appended: this dialog dispatches on the index, so inserting would
			// silently rebind everything after it.
			CharSequence[] items = {"Notification","Toast Message","Ack With","Set Variable","Speak Out Loud","Play a Sound"};
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
		case 4:
			new com.resurrection.blowtorch2.lib.responder.speak.SpeakResponderEditor(
					TimerEditorDialog.this.getContext(), null, TimerEditorDialog.this).show();
			break;
		case 5:
			new com.resurrection.blowtorch2.lib.responder.sound.SoundResponderEditor(
					TimerEditorDialog.this.getContext(), null, TimerEditorDialog.this).show();
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
			case SPEAK:
				new com.resurrection.blowtorch2.lib.responder.speak.SpeakResponderEditor(
						TimerEditorDialog.this.getContext(),
						(com.resurrection.blowtorch2.lib.responder.speak.SpeakResponder)responder.copy(),
						TimerEditorDialog.this).show();
				break;
			case SOUND:
				new com.resurrection.blowtorch2.lib.responder.sound.SoundResponderEditor(
						TimerEditorDialog.this.getContext(),
						(com.resurrection.blowtorch2.lib.responder.sound.SoundResponder)responder.copy(),
						TimerEditorDialog.this).show();
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
					new String[] { "AND", "OR" });
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

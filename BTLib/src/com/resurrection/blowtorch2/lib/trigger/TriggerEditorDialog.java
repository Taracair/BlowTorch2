package com.resurrection.blowtorch2.lib.trigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.resurrection.blowtorch2.lib.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.util.Linkify;
//import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.resurrection.blowtorch2.lib.responder.*;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder.FIRE_WHEN;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder.RESPONDER_TYPE;
import com.resurrection.blowtorch2.lib.responder.ack.*;
import com.resurrection.blowtorch2.lib.responder.color.ColorAction;
import com.resurrection.blowtorch2.lib.responder.color.ColorActionEditor;
import com.resurrection.blowtorch2.lib.responder.tap.TapAction;
import com.resurrection.blowtorch2.lib.responder.tap.TapActionEditor;
import com.resurrection.blowtorch2.lib.responder.gag.GagAction;
import com.resurrection.blowtorch2.lib.responder.gag.GagActionEditorDialog;
import com.resurrection.blowtorch2.lib.responder.notification.*;
import com.resurrection.blowtorch2.lib.responder.replace.ReplaceActionEditorDialog;
import com.resurrection.blowtorch2.lib.responder.replace.ReplaceResponder;
import com.resurrection.blowtorch2.lib.responder.script.ScriptResponder;
import com.resurrection.blowtorch2.lib.responder.script.ScriptResponderEditor;
import com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableApply;
import com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableResponder;
import com.resurrection.blowtorch2.lib.responder.setvariable.SetVariableResponderEditor;
import com.resurrection.blowtorch2.lib.responder.chat.ChatThreadResponder;
import com.resurrection.blowtorch2.lib.responder.chat.ChatThreadResponderEditor;
import com.resurrection.blowtorch2.lib.responder.toast.*;
import com.resurrection.blowtorch2.lib.trigger.condition.ConditionGroup;
import com.resurrection.blowtorch2.lib.trigger.condition.ConditionLeaf;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.validator.Validator;
import com.resurrection.blowtorch2.lib.window.EditorDialogChrome;
import com.resurrection.blowtorch2.lib.window.PluginFilterSelectionDialog;

public class TriggerEditorDialog extends Dialog implements DialogInterface.OnClickListener,TriggerResponderEditorDoneListener{

	private LinearLayout actionList;
	private TableLayout conditionsTable;
	/** Player opened a long preview; stay open while they type. */
	private boolean mPreviewExpandedByUser;

	/** Fold the match preview when it has more than this many lines. */
	static final int PREVIEW_COLLAPSE_AFTER_LINES = 3;
	/** A one-line regex wraps in the preview; count newlines alone misses it. */
	static final int PREVIEW_COLLAPSE_AFTER_CHARS =
			PREVIEW_COLLAPSE_AFTER_LINES * 40;
	
	private TriggerData the_trigger;
	private TriggerData original_trigger;
	private boolean isEditor = false;
	
	private IConnectionBinder service;
	
	private Handler finish_with;
	
	private boolean mEditorWarning = true;
	
	//private CheckBox literal;
	private CheckBox once;
	
	String selectedPlugin = null;
	
	public TriggerEditorDialog(Context context,TriggerData input,IConnectionBinder pService,Handler finisher,String selectedPlugin,boolean showWarning) {
		super(context, EditorDialogChrome.fullScreenTheme());
		mEditorWarning = showWarning;
		this.selectedPlugin = selectedPlugin;
		service = pService;
		finish_with = finisher;
		if(input == null) {
			the_trigger = new TriggerData();
			
		} else {
			the_trigger = input.copy();
			original_trigger = input.copy();
			isEditor=true;
		}
		
		//Log.e("TEDITOR","CONSTRUCTED, GOT A FUCKING STUPID OBJECT THAT HAS RESPONDERS.");
		//for(TriggerResponder responder : the_trigger.getResponders()) {
		///	Log.e("TEDITOR","responder " + responder.getType() + " fires " + responder.getFireType());
		//}
		
	}

	/**
	 * Open a brand new trigger already set to a device gesture.
	 *
	 * <p>For the Sensors screen, where the player picked "put the phone face
	 * down" and should not then have to find that same choice again in a
	 * dropdown. Call before {@code show()}; ignored when editing an existing
	 * trigger, which already knows what it fires on.
	 */
	public void presetGesture(final com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture gesture) {
		if (gesture == null || isEditor) {
			return;
		}
		the_trigger.setName(gesture.getId());
		the_trigger.setPattern(gesture.getPattern());
		the_trigger.setInterpretAsRegex(false);
		the_trigger.setEnabled(true);
	}

	public void onCreate(Bundle b) {
		this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setBackgroundDrawableResource(com.resurrection.blowtorch2.lib.R.drawable.dialog_window_crawler1);
		setContentView(com.resurrection.blowtorch2.lib.R.layout.trigger_editor_dialog);
		
		ScrollView sv = (ScrollView)findViewById(R.id.trigger_editor_scroll_container);
		sv.setScrollbarFadingEnabled(false);
		
		actionList = (LinearLayout)findViewById(R.id.trigger_action_list);
		refreshResponderTable();
		setupConditionsSection();
		
		Button newresponder = (Button)findViewById(R.id.trigger_new_notification);
		newresponder.setOnClickListener(new NewResponderListener());
		
		Button donelistener = (Button)findViewById(R.id.trigger_editor_done_button);
		donelistener.setOnClickListener(new TriggerEditorDoneListener());
		
		Button helpButton = (Button)findViewById(R.id.trigger_editor_help_button);
		if (helpButton != null) {
			helpButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					showEditorHelp();
				}
			});
		}

		Button cancel = (Button)findViewById(R.id.new_trigger_cancel);
		cancel.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View arg0) {
				boolean changed = hasTriggerChanged();
				if(changed) {
					//Log.e("TEDITR","DATA CHANGED");
					AlertDialog.Builder builder = new AlertDialog.Builder(TriggerEditorDialog.this.getContext());
					builder.setTitle("Destroy Changes?");
					builder.setMessage("You have changed the data of this trigger, are you sure you want to dismiss?");
					DialogInterface.OnClickListener listner = new DialogInterface.OnClickListener() {
						
						public void onClick(DialogInterface arg0, int arg1) {
							switch(arg1) {
							case DialogInterface.BUTTON_POSITIVE: //quit
								arg0.dismiss();
								TriggerEditorDialog.this.dismiss();
								break;
							case DialogInterface.BUTTON_NEGATIVE: //cancel
								arg0.dismiss();
								break;
							case DialogInterface.BUTTON_NEUTRAL: //save and quit
								arg0.dismiss();
								Button done = (Button)TriggerEditorDialog.this.findViewById(R.id.trigger_editor_done_button);
								done.performClick();
								break;
								
							}
						}
					};
					builder.setPositiveButton("Destroy", listner);
					builder.setNegativeButton("Cancel", listner);
					builder.setNeutralButton("Save", listner);
					AlertDialog dialog = builder.create();
					dialog.show();
				} else {
					//Log.e("TEDITR","DATA NOT CHANGED");
					TriggerEditorDialog.this.dismiss();
				}
				
			}
		});
		
		//literal = (CheckBox)findViewById(R.id.trigger_literal_checkbox);
		once = (CheckBox)findViewById(R.id.trigger_once_checkbox);
		
		//if(isEditor) {
		EditText title = (EditText)findViewById(R.id.trigger_editor_name);
		EditText pattern = (EditText)findViewById(R.id.trigger_editor_pattern);
		AutoCompleteTextView group = (AutoCompleteTextView)findViewById(R.id.trigger_editor_group);
		
		CheckBox literal = (CheckBox)findViewById(R.id.trigger_literal_checkbox);
		
		title.setText(the_trigger.getName());
		pattern.setText(the_trigger.getPattern());
		if (group != null) {
			String g = the_trigger.getGroup();
			group.setText(g != null ? g : "");
			populateGroupSuggestions(group);
		}
		
		literal.setChecked(!the_trigger.isInterpretAsRegex());
		once.setChecked(the_trigger.isFireOnce());
		
		if(isEditor) {
			Button editdone = (Button)findViewById(R.id.trigger_editor_done_button);
			editdone.setText("Done");
		}	
		//}
		
		literal.setOnCheckedChangeListener(new LiteralCheckChangedListener());
		once.setOnCheckedChangeListener(new FireOnceCheckChangedListener());
		setupTriggerPreview(title, pattern, literal);
		setupSourcePicker(pattern, literal, title);
		EditorDialogChrome.applyFullScreen(this);
	}

	/**
	 * What this trigger fires on: a line from the world, or something the phone
	 * itself felt.
	 *
	 * <p>A device gesture is stored as a pattern like {@code !wave}, but nobody
	 * should ever have to know that, and nobody should be able to break it by
	 * editing the field by hand — one stray keystroke and Done would turn the
	 * gesture into a trigger watching the game for the literal text "!wave",
	 * silently and for ever. So picking a gesture fills the pattern in and locks
	 * the field; picking "a line from the world" hands it back.
	 *
	 * <p>Gestures this phone has no sensor for are listed and marked rather than
	 * hidden: a profile is a thing people share, and one built for a phone with a
	 * proximity sensor should be readable on a phone without one.
	 */
	private void setupSourcePicker(final EditText pattern, final CheckBox literal,
			final EditText title) {
		final Spinner source = (Spinner) findViewById(R.id.trigger_editor_source_spinner);
		if (source == null) {
			return;
		}
		final java.util.List<com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture>
				gestures = com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.all();
		final java.util.List<String> labels = new java.util.ArrayList<String>();
		labels.add("A line from the world");
		for (com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture g : gestures) {
			com.resurrection.blowtorch2.lib.service.sensor.GestureAvailability.Resolution r =
					com.resurrection.blowtorch2.lib.service.sensor.GestureAvailability.resolve(
							getContext(), g);
			labels.add(g.getLabel() + (r.isAvailable() ? "" : "  (not on this phone)"));
		}
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),
				android.R.layout.simple_spinner_item, labels);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		source.setAdapter(adapter);

		com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture current =
				com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.fromPattern(
						the_trigger.getPattern(), !the_trigger.isInterpretAsRegex());
		int selected = 0;
		if (current != null) {
			for (int i = 0; i < gestures.size(); i++) {
				if (gestures.get(i).getId().equals(current.getId())) {
					selected = i + 1;
					break;
				}
			}
		}
		source.setSelection(selected);
		applySourceLock(pattern, literal, selected > 0);

		source.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position,
					long id) {
				if (position == 0) {
					// Leaving a gesture: the pattern field still holds "!wave",
					// which is not something the player typed and not something
					// they want to watch the game for.
					if (com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog
							.isGesturePattern(pattern.getText().toString(),
									literal.isChecked())) {
						pattern.setText("");
					}
					applySourceLock(pattern, literal, false);
					return;
				}
				com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture g =
						gestures.get(position - 1);
				pattern.setText(g.getPattern());
				// A gesture is matched by name, never as a regular expression.
				literal.setChecked(true);
				if (title.getText().toString().trim().length() == 0) {
					title.setText(g.getId());
				}
				applySourceLock(pattern, literal, true);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
	}

	/** Lock or release the two fields a gesture owns. */
	private void applySourceLock(final EditText pattern, final CheckBox literal,
			final boolean isGesture) {
		pattern.setEnabled(!isGesture);
		pattern.setFocusable(!isGesture);
		pattern.setFocusableInTouchMode(!isGesture);
		literal.setEnabled(!isGesture);
	}

	/** Suggest existing group names; autocomplete + dropdown spinner of known groups. */
	@SuppressWarnings("unchecked")
	private void populateGroupSuggestions(final AutoCompleteTextView group) {
		TreeSet<String> names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		try {
			HashMap<String, TriggerData> map;
			if (selectedPlugin == null
					|| PluginFilterSelectionDialog.MAIN_SETTINGS.equals(selectedPlugin)) {
				map = (HashMap<String, TriggerData>) service.getTriggerData();
			} else {
				map = (HashMap<String, TriggerData>) service.getPluginTriggerData(selectedPlugin);
			}
			if (map != null) {
				for (TriggerData t : map.values()) {
					if (t == null) {
						continue;
					}
					String g = t.getGroup();
					if (g != null && g.length() > 0
							&& !TriggerData.DEFAULT_GROUP.equals(g)) {
						names.add(g);
					}
				}
			}
		} catch (RemoteException e) {
			// Suggestions are optional.
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable("TriggerEditorDialog.save trigger", e);
		}
		ArrayList<String> nameList = new ArrayList<String>(names);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),
				R.layout.spinner_dropdown_item_dark,
				nameList);
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

		Spinner picker = (Spinner) findViewById(R.id.trigger_editor_group_spinner);
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
	
	private void setupTriggerPreview(final EditText title, final EditText pattern, final CheckBox literal) {
		final TextView preview = (TextView) findViewById(R.id.trigger_match_preview);
		if (preview == null) {
			return;
		}
		pattern.setTypeface(Typeface.MONOSPACE);
		loadAliasNames();
		TextWatcher watcher = new TextWatcher() {
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				updateTriggerPreview(title, pattern, literal, preview);
			}
			public void afterTextChanged(Editable s) {}
		};
		pattern.addTextChangedListener(watcher);
		title.addTextChangedListener(watcher);
		View.OnClickListener expandClick = new View.OnClickListener() {
			public void onClick(View v) {
				if (shouldCollapsePreview(preview.getText())) {
					mPreviewExpandedByUser = !mPreviewExpandedByUser;
					applyPreviewFold(preview);
				}
			}
		};
		final TextView expand = (TextView) findViewById(R.id.trigger_preview_expand);
		if (expand != null) {
			expand.setOnClickListener(expandClick);
		}
		updateTriggerPreview(title, pattern, literal, preview);
	}

	private void updateTriggerPreview(EditText title, EditText pattern, CheckBox literal, TextView preview) {
		String patternText = pattern.getText().toString();
		com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture gesture =
				com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.fromPattern(
						patternText, literal.isChecked());
		if (gesture != null) {
			com.resurrection.blowtorch2.lib.service.sensor.GestureAvailability.Resolution r =
					com.resurrection.blowtorch2.lib.service.sensor.GestureAvailability.resolve(
							getContext(), gesture);
			preview.setText(gesture.getHelp() + "\n\nOn this phone: " + r.describe()
					+ "\nTry it without moving the phone: .sensor fire " + gesture.getId());
		} else if (patternText.trim().length() == 0) {
			preview.setText("Enter a pattern to preview what the trigger watches for.");
		} else {
			String name = title.getText().toString().trim();
			String mode = literal.isChecked() ? "literal text" : "regular expression";
			String header = name.length() > 0 ? ("Trigger «" + name + "» watches server output for:\n") : "Watches server output for:\n";
			StringBuilder out = new StringBuilder();
			out.append(header).append("«").append(patternText).append("»\n(mode: ").append(mode).append(")");
			out.append(patternStatus(patternText, literal.isChecked()));
			preview.setText(out.toString());
		}
		applyPreviewFold(preview);
	}

	static int countPreviewLines(final CharSequence text) {
		if (text == null || text.length() == 0) {
			return 0;
		}
		int n = 1;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				n++;
			}
		}
		return n;
	}

	static boolean shouldCollapsePreview(final CharSequence text) {
		if (text == null || text.length() == 0) {
			return false;
		}
		if (countPreviewLines(text) > PREVIEW_COLLAPSE_AFTER_LINES) {
			return true;
		}
		return text.length() > PREVIEW_COLLAPSE_AFTER_CHARS;
	}

	private void applyPreviewFold(final TextView preview) {
		TextView expand = (TextView) findViewById(R.id.trigger_preview_expand);
		boolean fold = shouldCollapsePreview(preview.getText());
		preview.setVisibility(View.VISIBLE);
		if (!fold) {
			preview.setMaxLines(Integer.MAX_VALUE);
			preview.setEllipsize(null);
			if (expand != null) {
				expand.setVisibility(View.GONE);
			}
			return;
		}
		if (expand != null) {
			expand.setVisibility(View.VISIBLE);
			expand.setText(mPreviewExpandedByUser ? "Show less" : "Show all");
		}
		if (mPreviewExpandedByUser) {
			preview.setMaxLines(Integer.MAX_VALUE);
			preview.setEllipsize(null);
		} else {
			preview.setMaxLines(PREVIEW_COLLAPSE_AFTER_LINES);
			preview.setEllipsize(TextUtils.TruncateAt.END);
		}
	}

	/**
	 * What the pattern field is actually going to do, in the editor, before the
	 * trigger is saved.
	 *
	 * <p>Three things were invisible and each cost a session to find out the
	 * hard way. A regex that does not compile is not rejected -- {@code
	 * TriggerData.buildData} falls back to matching the text literally, on
	 * purpose -- so a mistyped bracket produced a trigger that simply never
	 * fired, with the reason recorded in {@code getPatternError()} and shown
	 * nowhere. An alias's text is pasted in before the pattern is compiled --
	 * whether the whole pattern is the alias's name or it was named inside a
	 * longer one -- and a pattern that quietly stopped meaning what it says is
	 * exactly the thing that has to be said out loud, here, while it can still
	 * be changed. And an alias whose text cannot stand in a pattern is refused
	 * rather than pasted, which is only fair to say too.
	 *
	 * @param patternText The raw contents of the pattern field.
	 * @param isLiteral Whether the literal-text checkbox is ticked.
	 * @return Lines to append to the preview, each starting with a newline.
	 */
	private String patternStatus(final String patternText, final boolean isLiteral) {
		StringBuilder out = new StringBuilder();
		String resolved = TriggerAliasReference.resolve(patternText, aliasNames);
		for (String line : TriggerAliasReference.explain(patternText, aliasNames)) {
			out.append("\n").append(line);
		}
		if (!resolved.equals(patternText)) {
			out.append("\nAfter the alias: «").append(resolved).append("»");
		}
		if (!isLiteral) {
			try {
				Pattern p = Pattern.compile(resolved);
				int groups = p.matcher("").groupCount();
				if (groups > 0) {
					out.append("\nCompiles. ").append(groups).append(" capture group(s): $1");
					if (groups > 1) {
						out.append("..$").append(groups);
					}
					out.append(" in the responses.");
				} else {
					out.append("\nCompiles. No capture groups, so there is no $1.");
				}
			} catch (PatternSyntaxException bad) {
				String why = bad.getDescription() != null
						? bad.getDescription() + " (at position " + bad.getIndex() + ")"
						: bad.getMessage();
				out.append("\n\u26a0 Not a valid regular expression: ").append(why)
					.append("\nIt will be matched as literal text instead, so it will")
					.append(" only fire on a line containing exactly that text.");
			}
		}
		return out.toString();
	}

	/** Alias names to bodies, read once when the editor opens; null until then. */
	private HashMap<String, String> aliasNames;

	/** The ? beside Done: pattern box plus the CONDITIONS essay. */
	private void showEditorHelp() {
		com.resurrection.blowtorch2.lib.window.EditorHelp.show(
				getContext(), "Trigger editor",
				PATTERN_HELP_TEXT + "\n\n"
						+ com.resurrection.blowtorch2.lib.window.EditorHelp.TRIGGER_EDITOR_CONDITIONS);
	}

	static final String PATTERN_HELP_TEXT =
			"The pattern is the text you are waiting for the GAME to print. It is not "
			+ "something you type.\n\n"
			+ "LITERAL? ON\n"
			+ "The pattern is plain text and matches exactly those characters.\n\n"
			+ "LITERAL? OFF\n"
			+ "The pattern is a regular expression. Brackets capture: (\\w+) hits you "
			+ "puts the name in $1, which you can use in the responses. If it does not "
			+ "compile it is matched as plain text instead, and the preview under the "
			+ "box says what was wrong.\n\n"
			+ "USING AN ALIAS\n"
			+ "Type an alias's name on its own and the trigger watches for that alias's "
			+ "text instead of the name. So with an alias item that types "
			+ "circuit, a pattern of item watches for the word circuit. Edit the "
			+ "alias later and every trigger using it follows.\n\n"
			+ "To use one inside a longer pattern, write $alias{name}:\n"
			+ "    You see a $alias{item} here\\.\n\n"
			+ "The preview under the box always names the alias it found and the text "
			+ "it will watch for.\n\n"
			+ "FOUR ALIASES CANNOT BE USED\n"
			+ "The pattern is then left exactly as you wrote it, so the trigger "
			+ "visibly does not fire rather than quietly watching for something else. "
			+ "The preview says which of these it was:\n"
			+ "1. There is no alias of that name.\n"
			+ "2. The alias is several commands, like sip health;stand. That is not one "
			+ "piece of text the game can print.\n"
			+ "3. The alias uses $1-style captures from what you type, like "
			+ "get $1 from bag. A trigger has nothing to fill those from.\n"
			+ "4. The alias names another alias. One level only, so a pair of aliases "
			+ "naming each other cannot loop.\n\n"
			+ "A disabled alias still gives its text: disabling stops it expanding what "
			+ "you type, and the trigger is only borrowing the words.\n\n"
			+ "IF YOU REALLY WANT THE NAME AS TEXT\n"
			+ "Only a pattern that is exactly the name is replaced. Turn Literal? off "
			+ "and write ^name$ and it is a pattern of its own again.";

	/**
	 * Snapshot the alias names so the preview can resolve $alias{...} without a
	 * binder round trip per keystroke.
	 *
	 * <p>Once, at open: the whole map crosses the binder, and the preview runs
	 * on every character typed into the pattern field. A dead binder leaves an
	 * empty map, so the preview says the alias is unknown -- which is what the
	 * service would do with it too.
	 */
	@SuppressWarnings("unchecked")
	private void loadAliasNames() {
		aliasNames = new HashMap<String, String>();
		if (service == null) {
			return;
		}
		try {
			aliasNames.putAll(TriggerAliasReference.bodies(
					(java.util.Map<String, com.resurrection.blowtorch2.lib.alias.AliasData>)
							service.getAliases()));
		} catch (RemoteException dead) {
			// No hint; the editor still edits.
		} catch (RuntimeException dead) {
			// A binder can throw anything the service threw, and a missing hint
			// is never worth taking the editor down for.
		}
	}
	
	
	private boolean hasTriggerChanged() {
		if(original_trigger == null) {
			return false;
		}
		TriggerData test = original_trigger.copy();
		
		
		EditText title = (EditText)findViewById(R.id.trigger_editor_name);
		EditText pattern = (EditText)findViewById(R.id.trigger_editor_pattern);
		
		CheckBox literal = (CheckBox)findViewById(R.id.trigger_literal_checkbox);
		CheckBox fireOnce = (CheckBox)findViewById(R.id.trigger_once_checkbox);
		boolean retval = false;
		if(!(title.getText().toString().equals(test.getName()))) retval = true;
		if(!(pattern.getText().toString().equals(test.getPattern()))) retval = true;
		String groupText = readGroupField();
		String existingGroup = test.getGroup() != null ? test.getGroup() : "";
		if(!groupText.equals(existingGroup)) retval = true;
		if(test.isInterpretAsRegex() != !literal.isChecked()) retval = true;
		if(test.isFireOnce() != fireOnce.isChecked()) retval = true; 
		
		ConditionGroup origCond = original_trigger.getConditions() != null
				? original_trigger.getConditions() : new ConditionGroup();
		ConditionGroup curCond = the_trigger.getConditions() != null
				? the_trigger.getConditions() : new ConditionGroup();
		if (!origCond.equals(curCond)) {
			retval = true;
		}

		boolean checkresponder = false;
		if(test.getResponders().size() == the_trigger.getResponders().size()) { checkresponder = true; } else { retval = true; }
		
		if(checkresponder) {
			Iterator<TriggerResponder> test_responders = original_trigger.getResponders().iterator();
			Iterator<TriggerResponder> current_responders = the_trigger.getResponders().iterator();
			
			while(test_responders.hasNext()) {
				TriggerResponder torig = test_responders.next();
				TriggerResponder tcurr = current_responders.next();
				if(!torig.equals(tcurr)) {
					retval = true;
				}
			}
			
		}
		return retval;
	}

	/** Optional group name; blank means default (ungrouped). */
	private String readGroupField() {
		AutoCompleteTextView group =
				(AutoCompleteTextView) findViewById(R.id.trigger_editor_group);
		if (group == null) {
			return TriggerData.DEFAULT_GROUP;
		}
		String text = group.getText() != null ? group.getText().toString().trim() : "";
		return text.length() == 0 ? TriggerData.DEFAULT_GROUP : text;
	}
	
	private class TriggerEditorDoneListener implements View.OnClickListener {

		public void onClick(View v) {
			//return the trigger whatever the modification state.
			

		
			
			//responders should already be set up.
			EditText title = (EditText)findViewById(R.id.trigger_editor_name);
			EditText pattern = (EditText)findViewById(R.id.trigger_editor_pattern);
			
			Validator checker = new Validator();
			checker.add(title, Validator.VALIDATE_NOT_BLANK, "Trigger name");
			checker.add(pattern,Validator.VALIDATE_NOT_BLANK,"Pattern");
			
			String result = checker.validate();
			if(result != null) {
				checker.showMessage(TriggerEditorDialog.this.getContext(), result);
				return;
			}
			
			CheckBox literal = (CheckBox)findViewById(R.id.trigger_literal_checkbox);
			
			if(pattern.getText().toString().equals("")) {
				//the pattern can not be blank.
				AlertDialog.Builder builder = new AlertDialog.Builder(TriggerEditorDialog.this.getContext());
				builder.setPositiveButton("Acknowledge.", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface arg0, int arg1) {
						arg0.dismiss();
					}
				});
				
				builder.setMessage("Pattern can not be blank.");
				builder.setTitle("Pattern error.");
				AlertDialog error = builder.create();
				error.show();
				
				return;
			} else {
				//check to make sure it is a valid pattern
				if(the_trigger.isInterpretAsRegex()) {
					try {
						// Against the resolved text: $alias{...} is pasted in
						// before the service compiles this, so refusing the
						// unresolved form would reject a pattern that is fine
						// and accept one that is not.
						Pattern p = Pattern.compile(
								TriggerAliasReference.resolve(pattern.getText().toString(), aliasNames));
						p.pattern();
					} catch (PatternSyntaxException e) {
						AlertDialog.Builder builder = new AlertDialog.Builder(TriggerEditorDialog.this.getContext());
						builder.setPositiveButton("Acknowledge.", new DialogInterface.OnClickListener() {
							
							public void onClick(DialogInterface arg0, int arg1) {
								arg0.dismiss();
							}
						});
						
						builder.setMessage(e.getMessage());
						builder.setTitle("Problem with pattern syntax.");
						
						AlertDialog error = builder.create();
						error.show();
						//AlertDialog error = builder.create();
						//error.show();
						TextView tvtmp = (TextView)error.findViewById(android.R.id.message);
						tvtmp.setTypeface(Typeface.MONOSPACE);
						
						return;
					}
				}
			}
			
			// A sensor reading arrives here with its pattern already filled in,
			// so Done is valid the moment the editor opens and one tap saves a
			// trigger with nothing in it. It then shows up on the Sensors list
			// as set up, which is the opposite of true. A text trigger cannot
			// reach this state — its pattern starts blank and the check above
			// stops it — so the guard is only for the readings.
			if (the_trigger.getResponders().isEmpty()
					&& com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog
							.isGesturePattern(pattern.getText().toString(),
									literal.isChecked())) {
				AlertDialog.Builder builder =
						new AlertDialog.Builder(TriggerEditorDialog.this.getContext());
				builder.setTitle("Nothing to do yet");
				builder.setMessage("This reading has no actions, so firing it would do"
						+ " nothing. Add one under Actions — Ack sends a command to the"
						+ " game — or Cancel to leave the reading unset.");
				builder.setPositiveButton("Back to the editor",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface d, int which) {
								d.dismiss();
							}
						});
				builder.create().show();
				return;
			}

			if(isEditor) {
				//do editor type action
				the_trigger.setName(title.getText().toString());
				the_trigger.setPattern(pattern.getText().toString());
				the_trigger.setGroup(readGroupField());
				the_trigger.setInterpretAsRegex(!literal.isChecked());
				
				//i don't care anymore about the checkchanged listeners. it was a neat idea, but here goes.
				try {
					if(selectedPlugin.equals(PluginFilterSelectionDialog.MAIN_SETTINGS)) {
						service.updateTrigger(original_trigger,the_trigger);
					} else {	
						service.updatePluginTrigger(selectedPlugin,original_trigger,the_trigger);
					}
					// Same barrier as TimerEditorDialog: the list's Done saves too,
					// but conditions must reach disk as soon as the editor commits
					// so a :stellar death cannot drop an in-memory-only
					// ConditionGroup. Still asked for here, just not waited for:
					// updateTrigger above is synchronous, so the service already
					// holds everything this write puts down. Measured at 329 ms on
					// the UI thread, 7 August.
					com.resurrection.blowtorch2.lib.util.SettingsSaver.saveInBackground(service);
				} catch (RemoteException e) {
					throw new RuntimeException(e);
				}
				finish_with.sendMessageDelayed(finish_with.obtainMessage(100,the_trigger),10);
			} else {	
				the_trigger.setName(title.getText().toString());
				the_trigger.setPattern(pattern.getText().toString());
				the_trigger.setGroup(readGroupField());
				the_trigger.setInterpretAsRegex(!literal.isChecked());
				try {
					if(selectedPlugin.equals(PluginFilterSelectionDialog.MAIN_SETTINGS)) {
						service.newTrigger(the_trigger);
					} else {
						service.newPluginTrigger(selectedPlugin,the_trigger);
					}
					// As above: the new trigger is in the service already.
					com.resurrection.blowtorch2.lib.util.SettingsSaver.saveInBackground(service);
				} catch (RemoteException e) {
					throw new RuntimeException(e);
				}
				finish_with.sendMessageDelayed(finish_with.obtainMessage(100,the_trigger),10);
			}
			
			TriggerEditorDialog.this.dismiss();
		}
		
	}
	
	/**
	 * Sensors-style rows. Fire-when stays on the row (Open / Closed) because
	 * none of the responder editors expose Window Open / Window Closed.
	 */
	private void refreshResponderTable() {
		if (actionList == null) {
			actionList = (LinearLayout) findViewById(R.id.trigger_action_list);
		}
		if (actionList == null) {
			return;
		}
		actionList.removeAllViews();
		LayoutInflater inflater = LayoutInflater.from(getContext());
		List<TriggerResponder> responders = the_trigger.getResponders();
		for (int position = 0; position < responders.size(); position++) {
			TriggerResponder responder = responders.get(position);
			View row = inflater.inflate(R.layout.editor_action_row, actionList, false);
			TextView type = (TextView) row.findViewById(R.id.action_row_type);
			TextView summary = (TextView) row.findViewById(R.id.action_row_summary);
			CheckBox windowOpen = (CheckBox) row.findViewById(R.id.action_row_open);
			CheckBox windowClosed = (CheckBox) row.findViewById(R.id.action_row_closed);
			ImageButton delete = (ImageButton) row.findViewById(R.id.action_row_delete);
			View body = row.findViewById(R.id.action_row_body);

			type.setText(actionTypeLabel(responder));
			String line = actionSummary(responder);
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
			confineFireWhenCheckBoxes(windowOpen, windowClosed);

			actionList.addView(row);
		}
	}

	/**
	 * Open/Closed have no other home, but AppCompat's Material CheckBox style
	 * keeps a ~48dp target (20dp-radius ripple, theme minHeight). XML already
	 * caps and wraps them; this strips whatever inflation put back so the last
	 * Closed box cannot cover New Action.
	 */
	public static void confineFireWhenCheckBoxes(CheckBox open, CheckBox closed) {
		confineFireWhenCheckBox(open);
		confineFireWhenCheckBox(closed);
	}

	private static void confineFireWhenCheckBox(CheckBox box) {
		if (box == null) {
			return;
		}
		box.setMinHeight(0);
		box.setMinWidth(0);
		box.setBackground(null);
		if (box.getParent() instanceof ViewGroup) {
			ViewGroup wrapper = (ViewGroup) box.getParent();
			wrapper.setClipChildren(true);
			wrapper.setClipToPadding(true);
		}
	}

	public static String actionTypeLabel(TriggerResponder responder) {
		if (responder == null || responder.getType() == null) {
			return "";
		}
		switch (responder.getType()) {
		case NOTIFICATION:
			return "Notification";
		case TOAST:
			return "Toast";
		case SOUND:
			return "Sound";
		case SPEAK:
			return "Speak";
		case ACK:
			return "Ack";
		case SCRIPT:
			return "Function";
		case REPLACE:
			return "Replace";
		case GAG:
			return "Gag";
		case TAP:
			return "Tappable";
		case COLOR:
			return "Color";
		case SET_VARIABLE:
			return "Set Variable";
		case CHAT_THREAD:
			return "Send to thread";
		default:
			return "";
		}
	}

	public static String actionSummary(TriggerResponder responder) {
		if (responder == null || responder.getType() == null) {
			return "";
		}
		switch (responder.getType()) {
		case NOTIFICATION:
			return nullToEmpty(((NotificationResponder) responder).getTitle());
		case TOAST:
			return nullToEmpty(((ToastResponder) responder).getMessage());
		case SOUND:
			return com.resurrection.blowtorch2.lib.util.NotificationSounds.displayLabel(
					((com.resurrection.blowtorch2.lib.responder.sound.SoundResponder) responder).getSoundPath());
		case SPEAK:
			return nullToEmpty(((com.resurrection.blowtorch2.lib.responder.speak.SpeakResponder) responder).getMessage());
		case ACK:
			return nullToEmpty(((AckResponder) responder).getAckWith());
		case SCRIPT:
			return nullToEmpty(((ScriptResponder) responder).getFunction());
		case REPLACE:
			return nullToEmpty(((ReplaceResponder) responder).getWith());
		case GAG:
			return "";
		case TAP: {
			TapAction tap = (TapAction) responder;
			int extra = tap.getCommands().size() - 1;
			return nullToEmpty(tap.getCommand())
					+ (extra > 0 ? " (+" + extra + " in a menu)" : "");
		}
		case COLOR:
			return Integer.toString(((ColorAction) responder).getColor());
		case SET_VARIABLE: {
			SetVariableResponder sv = (SetVariableResponder) responder;
			String name = nullToEmpty(sv.getVariableName());
			String value = nullToEmpty(sv.getVariableValue());
			String mode = sv.getMode();
			if (SetVariableApply.MODE_ADD.equals(mode)) {
				return name + " +" + value;
			}
			if (SetVariableApply.MODE_SUBTRACT.equals(mode)) {
				return name + " -" + value;
			}
			if (SetVariableApply.MODE_APPEND.equals(mode)) {
				return name + " …" + value;
			}
			if (SetVariableApply.MODE_UNSET.equals(mode)) {
				return "unset " + name;
			}
			return name + "=" + value;
		}
		case CHAT_THREAD: {
			ChatThreadResponder chat = (ChatThreadResponder) responder;
			String id = nullToEmpty(chat.getThreadId());
			String title = nullToEmpty(chat.getTitle());
			if (title.length() > 0 && !title.equals(id)) {
				return id + " · " + title + (chat.isMine() ? " · mine" : "");
			}
			return chat.isMine() ? id + " · mine" : id;
		}
		default:
			return "";
		}
	}

	private static String nullToEmpty(String s) {
		return s == null ? "" : s;
	}

	private class EditResponderListener implements View.OnClickListener {

		int position;
		
		public EditResponderListener(int pos) {
			position = pos;
		}
		
		public void onClick(View v) {
			TriggerResponder responder = the_trigger.getResponders().get(position);
			switch(responder.getType()) {
			case NOTIFICATION:
				//show the notification editor
				NotificationResponderEditor redit = new NotificationResponderEditor(TriggerEditorDialog.this.getContext(),(NotificationResponder)responder.copy(),TriggerEditorDialog.this);
				redit.show();
				break;
			case TOAST:
				ToastResponderEditor tedit = new ToastResponderEditor(TriggerEditorDialog.this.getContext(),(ToastResponder)responder.copy(),TriggerEditorDialog.this);
				tedit.show();
				break;
			case SPEAK:
				new com.resurrection.blowtorch2.lib.responder.speak.SpeakResponderEditor(
						TriggerEditorDialog.this.getContext(),
						(com.resurrection.blowtorch2.lib.responder.speak.SpeakResponder)responder.copy(),
						TriggerEditorDialog.this).show();
				break;
			case SOUND:
				new com.resurrection.blowtorch2.lib.responder.sound.SoundResponderEditor(
						TriggerEditorDialog.this.getContext(),
						(com.resurrection.blowtorch2.lib.responder.sound.SoundResponder)responder.copy(),
						TriggerEditorDialog.this).show();
				break;
			case ACK:
				AckResponderEditor aedit = new AckResponderEditor(TriggerEditorDialog.this.getContext(),(AckResponder)responder.copy(),TriggerEditorDialog.this);
				aedit.show();
				break;
			case SCRIPT:
				ScriptResponderEditor sedit = new ScriptResponderEditor(TriggerEditorDialog.this.getContext(),(ScriptResponder)responder.copy(),TriggerEditorDialog.this);
				sedit.show();
				break;
			case COLOR:
				ColorActionEditor color = new ColorActionEditor(TriggerEditorDialog.this.getContext(),(ColorAction)responder.copy(),TriggerEditorDialog.this);
				color.show();
				break;
			case GAG:
				GagActionEditorDialog gag = new GagActionEditorDialog(TriggerEditorDialog.this.getContext(),(GagAction)responder.copy(),TriggerEditorDialog.this, service);
				gag.show();
				break;
			case REPLACE:
				ReplaceActionEditorDialog rep = new ReplaceActionEditorDialog(TriggerEditorDialog.this.getContext(),(ReplaceResponder)responder.copy(),TriggerEditorDialog.this, service);
				rep.show();
				break;
			case SET_VARIABLE:
				new SetVariableResponderEditor(TriggerEditorDialog.this.getContext(),
						(SetVariableResponder) responder.copy(), TriggerEditorDialog.this).show();
				break;
			case TAP:
				new TapActionEditor(TriggerEditorDialog.this.getContext(),
						(TapAction) responder.copy(), TriggerEditorDialog.this).show();
				break;
			case CHAT_THREAD:
				new ChatThreadResponderEditor(TriggerEditorDialog.this.getContext(),
						(ChatThreadResponder) responder.copy(), TriggerEditorDialog.this).show();
				break;
			default:
				break;
			}
			
		}
		
	}
	
	private class WindowOpenCheckChangeListener implements CompoundButton.OnCheckedChangeListener {

		private final int position;
		
		WindowOpenCheckChangeListener(int i) {
			position = i;
		}
		
		public void onCheckedChanged(CompoundButton arg0, boolean checked) {
			if(checked) {
				//check the closed check state.
				the_trigger.getResponders().get(position).addFireType(FIRE_WHEN.WINDOW_OPEN);
				///Log.e("TEDITOR","TRIGGER TYPE " + the_trigger.getResponders().get(position).getType().getIntVal() + " AT "+ position + " ADDING windowOpen");
			} else {
				the_trigger.getResponders().get(position).removeFireType(FIRE_WHEN.WINDOW_OPEN);
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
				the_trigger.getResponders().get(position).addFireType(FIRE_WHEN.WINDOW_CLOSED);
				//Log.e("TEDITOR","TRIGGER TYPE " + the_trigger.getResponders().get(position).getType().getIntVal() + " AT "+ position + " ADDING windowClosed");
			} else {
				the_trigger.getResponders().get(position).removeFireType(FIRE_WHEN.WINDOW_CLOSED);
				//Log.e("TEDITOR","TRIGGER TYPE " + the_trigger.getResponders().get(position).getType().getIntVal() + " AT "+ position + " REMOVING windowClosed");
				
			}
			//Log.e("TEDITOR","TRIGGER TYPE " + the_trigger.getResponders().get(position).getType().getIntVal() + " AT "+ position + " NOW " + the_trigger.getResponders().get(position).getFireType().getString());
			
		}
		
	};
	
	private class DeleteResponderListener implements View.OnClickListener,DialogInterface.OnClickListener {

		int position;
		
		public DeleteResponderListener(int i) {
			position = i;
		}
		
		public void onClick(View arg0) {
			AlertDialog.Builder builder = new AlertDialog.Builder(TriggerEditorDialog.this.getContext());
			builder.setPositiveButton("Delete", this);
			builder.setNegativeButton("Cancel", this);
			builder.setTitle("Are you sure?");
			AlertDialog deleter = builder.create();
			deleter.show();
		}

		public void onClick(DialogInterface arg0, int arg1) {
			if(arg1 == DialogInterface.BUTTON_POSITIVE) {
				//really delete the button
				the_trigger.getResponders().remove(position);
				refreshResponderTable();
			}
		}
		
	};
	
	
	private class NewResponderListener implements View.OnClickListener {

		public void onClick(View v) {
			//give out a list of options
			// Appended. The dialog dispatches on the index, so inserting rather
			// than appending would silently rebind every entry after it.
			CharSequence[] items = {"Notification","Toast Message","Ack With","Script","Color","Gag","Replace","Set Variable","Tappable Word","Speak Out Loud","Play a Sound","Send to thread"};
			AlertDialog.Builder builder = new AlertDialog.Builder(TriggerEditorDialog.this.getContext());
			builder.setTitle("Type:");
			
			builder.setItems(items, TriggerEditorDialog.this);
			AlertDialog dialog = builder.create();
			dialog.show();
		}
		
	}
	
	private class WarningCheckChangedLitener implements CompoundButton.OnCheckedChangeListener {

		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {
			TriggerEditorDialog.this.mEditorWarning = isChecked;
			try {
				service.setShowRegexWarning(isChecked);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
	
	private class LiteralCheckChangedListener implements CompoundButton.OnCheckedChangeListener {

		public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
			TextView preview = (TextView) TriggerEditorDialog.this.findViewById(R.id.trigger_match_preview);
			if (preview != null) {
				EditText title = (EditText) TriggerEditorDialog.this.findViewById(R.id.trigger_editor_name);
				EditText pattern = (EditText) TriggerEditorDialog.this.findViewById(R.id.trigger_editor_pattern);
				updateTriggerPreview(title, pattern, (CheckBox) arg0, preview);
			}
			if(arg1) {
				the_trigger.setInterpretAsRegex(false); //NO NOT INTERPRET AS REGEX
			} else {
				the_trigger.setInterpretAsRegex(true);
				if(!mEditorWarning) { return; };
				AlertDialog.Builder builder = new AlertDialog.Builder(TriggerEditorDialog.this.getContext());
				builder.setTitle("Warning");
				//builder.setMessage("You have turned on regular expression parsing for this trigger. Poorly formed expressions can cause the following: break other triggers, drain your battery, dump thousands of bytes to the server, etc. Please read the guide to the Java Pattern Class if you need more information. Have a nice day.");
				//build the custom view with a checkbox.
				ScrollView scroller = new ScrollView(getContext());
				LinearLayout top = new LinearLayout(TriggerEditorDialog.this.getContext());
				LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
				scroller.setLayoutParams(tp);
				TextView v = new TextView(TriggerEditorDialog.this.getContext());
				v.setText("Regular expressions have been enabled. Unpredictable or poor performance can result from overly broad regular expressions. Please see the documentation for the Java Pattern Class for more information.");
				int pad = (int) (5.0f * getContext().getResources().getDisplayMetrics().density);
				
				v.setTextAppearance(getContext(), android.R.attr.textAppearanceMedium);
				v.setTextSize(3*pad);
				Pattern wikiWordMatcher = Pattern.compile("Java Pattern Class");
				String wikiViewURL =    "";
				Linkify.TransformFilter transform = new Linkify.TransformFilter() {
					
					@Override
					public String transformUrl(Matcher match, String url) {
						// TODO Auto-generated method stub
						return "http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html";
					}
				};
				Linkify.MatchFilter matcher = new Linkify.MatchFilter() {
					
					@Override
					public boolean acceptMatch(CharSequence s, int start, int end) {
						// TODO Auto-generated method stub
						return true;
					}
				};
				Linkify.addLinks(v, wikiWordMatcher, wikiViewURL,matcher,transform);
				v.setPadding(pad, pad, pad, pad);
				CheckBox b = new CheckBox(getContext());
				b.setChecked(mEditorWarning);
				b.setOnCheckedChangeListener(new WarningCheckChangedLitener());
				b.setText("Always display this message.");
				//b.setChecked(true);
				b.setPadding(pad, pad, pad, pad);
				b.setLayoutParams(tp);
				v.setLayoutParams(tp);
				top.setLayoutParams(tp);
				top.addView(v);
				top.addView(b);
				top.setOrientation(LinearLayout.VERTICAL);
				scroller.addView(top);
				builder.setView(scroller);
				builder.setPositiveButton("Acknowledge.", new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface arg0, int arg1) {
						arg0.dismiss();
					}
					
				});
				
				AlertDialog dialog = builder.create();
				dialog.show();
				//the_trigger.setInterpretAsRegex(true); //DO INTERPRET AS REGEX.
			}
		}
		
	}
	
	private class FireOnceCheckChangedListener implements CompoundButton.OnCheckedChangeListener {

		public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
			if(arg1) {
				the_trigger.setFireOnce(true); 
			} else {
				the_trigger.setFireOnce(false); 
			}
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
			ToastResponderEditor tedit = new ToastResponderEditor(TriggerEditorDialog.this.getContext(),null,TriggerEditorDialog.this);
			tedit.show();
			break; 
		case 2:
			AckResponderEditor aedit = new AckResponderEditor(TriggerEditorDialog.this.getContext(),null,TriggerEditorDialog.this);
			aedit.show();
			break; //ack
		case 3:
			ScriptResponderEditor edit = new ScriptResponderEditor(TriggerEditorDialog.this.getContext(),null,TriggerEditorDialog.this);
			edit.show();
			break;
		case 4:
			ColorActionEditor color = new ColorActionEditor(TriggerEditorDialog.this.getContext(),null,TriggerEditorDialog.this);
			color.show();
			break;
		case 5:
			GagActionEditorDialog gag = new GagActionEditorDialog(TriggerEditorDialog.this.getContext(),null,TriggerEditorDialog.this, service);
			gag.show();
			break;
		case 6:
			ReplaceActionEditorDialog rep = new ReplaceActionEditorDialog(TriggerEditorDialog.this.getContext(),null,TriggerEditorDialog.this, service);
			rep.show();
			break;
		case 7:
			new SetVariableResponderEditor(TriggerEditorDialog.this.getContext(), null, TriggerEditorDialog.this).show();
			break;
		case 8:
			new TapActionEditor(TriggerEditorDialog.this.getContext(), null, TriggerEditorDialog.this).show();
			break;
		case 9:
			new com.resurrection.blowtorch2.lib.responder.speak.SpeakResponderEditor(
					TriggerEditorDialog.this.getContext(), null, TriggerEditorDialog.this).show();
			break;
		case 10:
			new com.resurrection.blowtorch2.lib.responder.sound.SoundResponderEditor(
					TriggerEditorDialog.this.getContext(), null, TriggerEditorDialog.this).show();
			break;
		case 11:
			new ChatThreadResponderEditor(
					TriggerEditorDialog.this.getContext(), null, TriggerEditorDialog.this).show();
			break;
		default:
			break;
		}
		
	}
	

	public void editTriggerResponder(TriggerResponder edited,TriggerResponder original) {
		
		//Log.e("TEDITOR","ATTEMPTING TO MODIFY TRIGGER");
		int pos = the_trigger.getResponders().indexOf(original);
		//Log.e("TEDITOR","ORIGINAL RESPONDER LIVES AT:" + pos);
		the_trigger.getResponders().remove(pos);
		the_trigger.getResponders().add(pos,edited);
		refreshResponderTable();
		
		//Log.e("TEDITOR","ATTEMPTING TO MODIFY RESPONDERS");
		//for(TriggerResponder responder : the_trigger.getResponders()) {
			//Log.e("TEDITOR","RESPONDER TYPE " + responder.getType() + " RESPONDS " + responder.getFireType());
		//}
	}

	public void newTriggerResponder(TriggerResponder newresponder) {
		//so the new responder is in.
		the_trigger.getResponders().add(newresponder);
		refreshResponderTable();
	}


	private void setupConditionsSection() {
		conditionsTable = (TableLayout) findViewById(R.id.trigger_conditions_table);
		Spinner opSpinner = (Spinner) findViewById(R.id.trigger_conditions_op);
		if (the_trigger.getConditions() == null) {
			the_trigger.setConditions(new ConditionGroup());
		}
		if (opSpinner != null) {
			ArrayAdapter<String> opAdapter = new ArrayAdapter<String>(getContext(),
					R.layout.spinner_item_dark,
					new String[] { "AND", "OR" });
			opAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
			opSpinner.setAdapter(opAdapter);
			opSpinner.setSelection(
					the_trigger.getConditions().getOp() == ConditionGroup.Op.OR ? 1 : 0, false);
			opSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					the_trigger.getConditions().setOp(
							position == 1 ? ConditionGroup.Op.OR : ConditionGroup.Op.AND);
					updateConditionsHint();
				}
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});
		}
		Button add = (Button) findViewById(R.id.trigger_new_condition);
		if (add != null) {
			add.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					new ConditionLeafEditorDialog(
							TriggerEditorDialog.this.getContext(), null, service, selectedPlugin,
							new ConditionLeafEditorDialog.DoneListener() {
								public void onConditionDone(ConditionLeaf leaf, ConditionLeaf originalOrNull) {
									the_trigger.getConditions().getChildren().add(leaf);
									refreshConditionsTable();
								}
							}).show();
				}
			});
		}
		refreshConditionsTable();
	}

	private void updateConditionsHint() {
		TextView hint = (TextView) findViewById(R.id.trigger_conditions_hint);
		if (hint == null) {
			return;
		}
		if (the_trigger.getConditions() == null || the_trigger.getConditions().isEmpty()) {
			hint.setText("No conditions — always runs when the pattern matches.");
		} else if (the_trigger.getConditions().getOp() == ConditionGroup.Op.OR) {
			hint.setText("Any condition may be true (OR).");
		} else {
			hint.setText("All conditions must be true (AND).");
		}
	}

	private void refreshConditionsTable() {
		if (conditionsTable == null) {
			conditionsTable = (TableLayout) findViewById(R.id.trigger_conditions_table);
		}
		if (conditionsTable == null) {
			return;
		}
		conditionsTable.removeAllViews();
		if (the_trigger.getConditions() == null) {
			the_trigger.setConditions(new ConditionGroup());
		}
		updateConditionsHint();
		int deleteSize = (int) (36 * getContext().getResources().getDisplayMetrics().density);
		java.util.List<ConditionLeaf> leaves = the_trigger.getConditions().getChildren();
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
					ConditionLeaf existing = the_trigger.getConditions().getChildren().get(index);
					new ConditionLeafEditorDialog(
							TriggerEditorDialog.this.getContext(), existing, service, selectedPlugin,
							new ConditionLeafEditorDialog.DoneListener() {
								public void onConditionDone(ConditionLeaf leaf, ConditionLeaf originalOrNull) {
									the_trigger.getConditions().getChildren().set(index, leaf);
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
					the_trigger.getConditions().getChildren().remove(index);
					refreshConditionsTable();
				}
			});
			deleteHolder.addView(delete);
			row.addView(label);
			row.addView(deleteHolder);
			conditionsTable.addView(row);
		}
	}

	public void updateOrientation(int newOrientation) {
		this.setContentView(R.layout.trigger_editor_dialog);
	}

	




	

}

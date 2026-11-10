package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcutButtons.Button;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcuts.Row;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

/** One shortcut. Cancel, ?, Done, and Delete when the row already exists. */
public final class KeyboardShortcutEditorDialog extends Dialog {

	public interface Host {
		void save(KeyboardShortcuts next);

		String buttonLines();
	}

	private final KeyboardShortcuts stored;
	private final int previousKey;
	private final int previousMods;
	private final Host host;
	private final KeyboardShortcutCapture capture = new KeyboardShortcutCapture();
	private final TextView keyLabel;
	private final EditText nameField;
	private final EditText tapField;
	private final EditText holdField;
	private final CheckBox repeatBox;
	private final CheckBox quietBox;
	private final TextView quietNote;
	private final RadioGroup actions;
	private final View commandBlock;
	private final View followBlock;
	private final TextView followNote;
	private TextView legacyNote;
	private final int lineId;
	private final int clearId;
	/** ACTION_INPUT and ACTION_STEP stay until the player picks a radio. */
	private final String loadedAction;
	private String linkSet = "";
	private String linkId = "";
	private String liveCommand = "";
	private String liveHold = "";
	private final boolean rowEnabled;
	private final boolean rowPad;
	private boolean formLock;

	private KeyboardShortcutEditorDialog(final Activity activity, final KeyboardShortcuts stored,
			final int previousKey, final int previousMods, final Host host) {
		super(activity, EditorDialogChrome.fullScreenTheme());
		this.stored = stored == null ? KeyboardShortcuts.empty() : stored;
		this.previousKey = previousKey;
		this.previousMods = previousMods;
		this.host = host;
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		Window window = getWindow();
		if (window != null) {
			window.setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		}

		Row existing = previousKey >= 0 ? this.stored.row(previousKey, previousMods) : null;
		loadedAction = existing == null || existing.action == null
				? KeyboardShortcuts.ACTION_LINE : existing.action;
		if (existing != null) {
			capture.set(existing.keyCode, existing.modifiers);
		}

		float density = activity.getResources().getDisplayMetrics().density;
		int pad = (int) (16 * density);
		LinearLayout shell = new LinearLayout(activity);
		shell.setOrientation(LinearLayout.VERTICAL);
		shell.setBackgroundColor(ContextCompat.getColor(activity, R.color.chrome_body));
		shell.addView(title(activity, existing == null ? "New shortcut" : "Keyboard shortcut", density));

		LinearLayout form = new LinearLayout(activity);
		form.setOrientation(LinearLayout.VERTICAL);
		form.setPadding(pad, pad, pad, pad);

		nameField = field(activity, "Name");
		if (existing != null) {
			nameField.setText(existing.name);
		}
		form.addView(label(activity, "Name"));
		form.addView(nameField);

		rowEnabled = existing == null || existing.enabled;
		rowPad = existing != null && existing.pad;

		form.addView(label(activity, "Key (tap to record keystroke)"));
		keyLabel = new KeyRow(activity);
		keyLabel.setText(capture.label());
		keyLabel.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		keyLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
		keyLabel.setBackgroundColor(ContextCompat.getColor(activity, R.color.chrome_title_bar));
		int chip = (int) (12 * density);
		keyLabel.setPadding(chip, chip, chip, chip);
		keyLabel.setFocusable(false);
		keyLabel.setFocusableInTouchMode(false);
		keyLabel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				setKeyListening(!capture.armed());
			}
		});
		form.addView(keyLabel);

		form.addView(label(activity, "Action"));
		actions = new RadioGroup(activity);
		actions.setOrientation(LinearLayout.VERTICAL);
		lineId = View.generateViewId();
		clearId = View.generateViewId();
		actions.addView(radio(activity, lineId, "Send a line"));
		actions.addView(radio(activity, clearId, "Clear the bar"));
		if (KeyboardShortcuts.ACTION_ESCAPE.equals(loadedAction)) {
			actions.check(clearId);
		} else if (!KeyboardShortcuts.ACTION_INPUT.equals(loadedAction)
				&& !KeyboardShortcuts.ACTION_STEP.equals(loadedAction)) {
			actions.check(lineId);
		}
		form.addView(actions);
		if (KeyboardShortcuts.ACTION_INPUT.equals(loadedAction)) {
			legacyNote = caption(activity,
					"This key sends the current line. Pick Send a line to change that.");
			form.addView(legacyNote);
		} else if (KeyboardShortcuts.ACTION_STEP.equals(loadedAction)) {
			legacyNote = caption(activity,
					"This key steps back. Pick Send a line to change that. .stepback does the same.");
			form.addView(legacyNote);
		}

		commandBlock = new LinearLayout(activity);
		((LinearLayout) commandBlock).setOrientation(LinearLayout.VERTICAL);
		tapField = field(activity, "Tap command");
		holdField = field(activity, "Hold command");
		if (existing != null) {
			tapField.setText(existing.tap);
			holdField.setText(existing.hold);
		}
		((LinearLayout) commandBlock).addView(label(activity, "Tap command"));
		((LinearLayout) commandBlock).addView(tapField);
		((LinearLayout) commandBlock).addView(label(activity, "Hold command"));
		((LinearLayout) commandBlock).addView(holdField);
		repeatBox = new CheckBox(activity);
		repeatBox.setText("Repeat while held");
		repeatBox.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		repeatBox.setChecked(existing != null && existing.repeat);
		((LinearLayout) commandBlock).addView(repeatBox);
		TextView why = caption(activity, EditorHelp.HOLD_AND_REPEAT);
		int stepIndent = (int) (36 * density);
		why.setPadding(stepIndent, 0, 0, (int) (4 * density));
		((LinearLayout) commandBlock).addView(why);
		form.addView(commandBlock);

		followBlock = new LinearLayout(activity);
		((LinearLayout) followBlock).setOrientation(LinearLayout.VERTICAL);
		followNote = caption(activity, "");
		((LinearLayout) followBlock).addView(followNote);
		android.widget.Button stop = new android.widget.Button(activity);
		stop.setAllCaps(false);
		stop.setText("Stop following this button");
		stop.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				formLock = true;
				tapField.setText(liveCommand);
				holdField.setText(liveHold);
				linkSet = "";
				linkId = "";
				formLock = false;
				applyAction();
			}
		});
		((LinearLayout) followBlock).addView(stop);
		form.addView(followBlock);
		if (existing != null && existing.followsButton()) {
			linkSet = existing.buttonSet;
			linkId = existing.buttonId;
			refreshFollow();
		}

		quietBox = new CheckBox(activity);
		quietBox.setText("Local echo");
		quietBox.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		quietBox.setChecked(existing == null || !existing.quiet);
		form.addView(quietBox);
		quietNote = caption(activity, EditorHelp.QUIET_CAPTION);
		quietNote.setPadding(stepIndent, 0, 0, (int) (4 * density));
		form.addView(quietNote);

		android.widget.Button useButton = new android.widget.Button(activity);
		useButton.setAllCaps(false);
		useButton.setSingleLine(false);
		useButton.setText(EditorHelp.USE_BUTTON);
		useButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				pickButton();
			}
		});
		form.addView(useButton);

		ScrollView scroll = new ScrollView(activity);
		scroll.addView(form, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		shell.addView(scroll, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		android.widget.Button cancel = new android.widget.Button(activity);
		cancel.setText("Cancel");
		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				dismiss();
			}
		});
		android.widget.Button help = new android.widget.Button(activity);
		help.setText("?");
		help.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				EditorHelp.show(getContext(), "Keyboard shortcut", EditorHelp.SHORTCUTS);
			}
		});
		android.widget.Button done = new android.widget.Button(activity);
		done.setText("Done");
		done.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				save();
			}
		});
		if (existing != null) {
			android.widget.Button delete = new android.widget.Button(activity);
			delete.setText("Delete");
			delete.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(final View v) {
					confirmDelete();
				}
			});
			shell.addView(buttonBar(activity, density, cancel, help, delete, done));
		} else {
			shell.addView(buttonBar(activity, density, cancel, help, done));
		}

		setContentView(shell, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		EditorDialogChrome.applyFullScreen(this);

		actions.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final RadioGroup group, final int checkedId) {
				applyAction();
			}
		});
		repeatBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final CompoundButton button, final boolean checked) {
				if (formLock) {
					return;
				}
				if (checked && holdField.getText().length() > 0) {
					formLock = true;
					holdField.setText("");
					formLock = false;
				}
			}
		});
		holdField.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(final CharSequence s, final int start, final int count,
					final int after) {
			}

			@Override
			public void onTextChanged(final CharSequence s, final int start, final int before,
					final int count) {
			}

			@Override
			public void afterTextChanged(final Editable s) {
				if (formLock) {
					return;
				}
				if (s != null && s.toString().trim().length() > 0 && repeatBox.isChecked()) {
					formLock = true;
					repeatBox.setChecked(false);
					formLock = false;
				}
			}
		});
		applyAction();
	}

	public static void show(final Activity activity, final KeyboardShortcuts stored,
			final int previousKey, final int previousMods, final Host host) {
		if (activity == null || host == null) {
			return;
		}
		new KeyboardShortcutEditorDialog(activity, stored, previousKey, previousMods, host).show();
	}

	@Override
	protected void onStart() {
		super.onStart();
		applyKeyListening();
	}

	/** The key row stays on the latest press until it is tapped again. */
	private void setKeyListening(final boolean on) {
		if (on) {
			capture.arm();
		} else {
			capture.disarm();
		}
		applyKeyListening();
	}

	private void applyKeyListening() {
		boolean listen = capture.armed();
		setTypingAllowed(nameField, !listen);
		setTypingAllowed(tapField, !listen);
		setTypingAllowed(holdField, !listen);
		keyLabel.setFocusable(listen);
		keyLabel.setFocusableInTouchMode(listen);
		keyLabel.setText(capture.label());
		if (!listen) {
			nameField.requestFocus();
			return;
		}
		View focused = getCurrentFocus();
		if (focused instanceof EditText) {
			focused.clearFocus();
		}
		keyLabel.requestFocus();
	}

	private static void setTypingAllowed(final EditText field, final boolean allowed) {
		field.setFocusable(allowed);
		field.setFocusableInTouchMode(allowed);
	}

	/** Ctrl+Up/Down key-down reaches this row and not the dialog. Phone 2026-11-07: keys 19 and 20, meta 0x3000, scan 103 and 108. */
	private boolean takeCtrlVerticalArrow(final KeyEvent event) {
		if (event == null || event.getAction() != KeyEvent.ACTION_DOWN || !capture.armed()) {
			return false;
		}
		if (!KeyboardShortcutCapture.ctrlVerticalArrow(event.getKeyCode(), event.getMetaState())) {
			return false;
		}
		capture.onKey(event.getAction(), event.getKeyCode(), event.getMetaState(),
				event.getScanCode(), event.getDeviceId());
		keyLabel.setText(capture.label());
		return true;
	}

	private final class KeyRow extends TextView {
		KeyRow(final Activity activity) {
			super(activity);
		}

		@Override
		public boolean dispatchKeyEventPreIme(final KeyEvent event) {
			if (takeCtrlVerticalArrow(event)) {
				return true;
			}
			return super.dispatchKeyEventPreIme(event);
		}
	}

	@Override
	public boolean dispatchKeyEvent(final KeyEvent event) {
		if (event != null
				&& capture.onKey(event.getAction(), event.getKeyCode(), event.getMetaState(),
						event.getScanCode(), event.getDeviceId())) {
			keyLabel.setText(capture.label());
			return true;
		}
		return super.dispatchKeyEvent(event);
	}

	private void applyAction() {
		int checked = actions.getCheckedRadioButtonId();
		boolean line = checked == lineId;
		boolean linked = line && linkId.length() > 0;
		boolean hideQuiet = checked == clearId
				|| (checked != lineId && KeyboardShortcuts.ACTION_INPUT.equals(loadedAction));
		commandBlock.setVisibility(line && !linked ? View.VISIBLE : View.GONE);
		followBlock.setVisibility(linked ? View.VISIBLE : View.GONE);
		int quiet = hideQuiet ? View.GONE : View.VISIBLE;
		quietBox.setVisibility(quiet);
		quietNote.setVisibility(quiet);
		if (legacyNote != null) {
			boolean kept = checked != lineId && checked != clearId;
			legacyNote.setVisibility(kept ? View.VISIBLE : View.GONE);
		}
	}

	private void refreshFollow() {
		KeyboardShortcutButtons.Button found = KeyboardShortcutButtons.find(
				host.buttonLines(), linkSet, linkId);
		String where = linkSet.length() > 0 ? linkSet + " / " + linkId : linkId;
		if (found == null) {
			liveCommand = "";
			liveHold = "";
			followNote.setText("Follows " + where + ". That button is not in the list.");
			return;
		}
		liveCommand = found.command;
		liveHold = found.hold;
		StringBuilder text = new StringBuilder();
		text.append("Follows ").append(where).append(".\n");
		text.append("This key sends that button's current command");
		if (liveHold.length() > 0) {
			text.append(" and its hold line");
		}
		text.append(".\nNow: ");
		text.append(liveCommand.length() > 0 ? liveCommand : "(empty)");
		if (liveHold.length() > 0) {
			text.append("\nHold: ").append(liveHold);
		}
		followNote.setText(text.toString());
	}

	private void save() {
		if (capture.keyCode() < 0) {
			setKeyListening(true);
			return;
		}
		String action = KeyboardShortcuts.ACTION_LINE;
		int checked = actions.getCheckedRadioButtonId();
		if (checked == clearId) {
			action = KeyboardShortcuts.ACTION_ESCAPE;
		} else if (checked != lineId
				&& (KeyboardShortcuts.ACTION_INPUT.equals(loadedAction)
						|| KeyboardShortcuts.ACTION_STEP.equals(loadedAction))) {
			action = loadedAction;
		}
		boolean quiet = !quietBox.isChecked()
				&& !KeyboardShortcuts.ACTION_INPUT.equals(action)
				&& !KeyboardShortcuts.ACTION_ESCAPE.equals(action);
		int newKey = capture.keyCode();
		int newMods = capture.modifiers();
		Row taken = stored.clash(newKey, newMods, previousKey, previousMods);
		if (taken != null) {
			String who = taken.name != null && taken.name.trim().length() > 0
					? taken.name.trim()
					: (taken.pad ? "the number pad" : "another shortcut");
			new AlertDialog.Builder(getContext())
					.setTitle("Key already used")
					.setMessage(KeyboardShortcuts.chordLabel(newKey, newMods)
							+ " is already used by " + who + ".")
					.setPositiveButton("OK", null)
					.show();
			return;
		}
		Row row = Row.of(newKey, newMods, action, rowEnabled,
				repeatBox.isChecked(), tapField.getText().toString(), holdField.getText().toString(),
				nameField.getText().toString(), quiet)
				.asPad(rowPad);
		if (KeyboardShortcuts.ACTION_LINE.equals(action) && linkId.length() > 0) {
			row = row.withLink(linkSet, linkId);
		}
		host.save(stored.withRow(previousKey, previousMods, row));
		dismiss();
	}

	private void confirmDelete() {
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle("Delete shortcut");
		builder.setMessage("Delete this shortcut?");
		builder.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				dialog.dismiss();
				host.save(stored.withoutRow(previousKey, previousMods));
				dismiss();
			}
		});
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				dialog.dismiss();
			}
		});
		builder.create().show();
	}

	private void pickButton() {
		KeyboardShortcutButtonPicker.show(getContext(), host.buttonLines(),
				new KeyboardShortcutButtonPicker.Choice() {
					@Override
					public void picked(final Button picked) {
						formLock = true;
						actions.check(lineId);
						repeatBox.setChecked(false);
						if (picked.id.length() == 0) {
							linkSet = "";
							linkId = "";
							tapField.setText(picked.command);
							holdField.setText(picked.hold);
						} else {
							linkSet = picked.set;
							linkId = picked.id;
							refreshFollow();
						}
						formLock = false;
						applyAction();
					}
				});
	}

	private static TextView title(final Activity activity, final String text, final float density) {
		TextView titleView = new TextView(activity);
		titleView.setText(text);
		titleView.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		titleView.setBackgroundColor(ContextCompat.getColor(activity, R.color.chrome_title_bar));
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
		titleView.setGravity(Gravity.CENTER);
		titleView.setLayoutParams(new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, (int) (42 * density)));
		return titleView;
	}

	private static TextView caption(final Activity activity, final String text) {
		TextView view = new TextView(activity);
		view.setText(text);
		view.setTextColor(ContextCompat.getColor(activity, R.color.chrome_hint));
		view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
		return view;
	}

	private static TextView label(final Activity activity, final String text) {
		TextView view = new TextView(activity);
		view.setText(text);
		view.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		view.setPadding(0, (int) (8 * activity.getResources().getDisplayMetrics().density), 0, 0);
		return view;
	}

	private static EditText field(final Activity activity, final String hint) {
		EditText field = new EditText(activity);
		field.setHint(hint);
		field.setSingleLine(true);
		field.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		field.setHintTextColor(ContextCompat.getColor(activity, R.color.chrome_hint));
		return field;
	}

	private static RadioButton radio(final Activity activity, final int id, final String text) {
		RadioButton radio = new RadioButton(activity);
		radio.setId(id);
		radio.setText(text);
		radio.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		return radio;
	}

	private static LinearLayout buttonBar(final Activity activity, final float density,
			final android.widget.Button... buttons) {
		LinearLayout footer = new LinearLayout(activity);
		footer.setOrientation(LinearLayout.HORIZONTAL);
		footer.setGravity(Gravity.CENTER);
		int barPad = (int) (6 * density);
		footer.setPadding(barPad, barPad, barPad, barPad);
		footer.setBackgroundColor(ContextCompat.getColor(activity, R.color.chrome_title_bar));
		for (int i = 0; i < buttons.length; i++) {
			buttons[i].setMinHeight((int) (44 * density));
			footer.addView(buttons[i], new LinearLayout.LayoutParams(
					0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		}
		return footer;
	}
}

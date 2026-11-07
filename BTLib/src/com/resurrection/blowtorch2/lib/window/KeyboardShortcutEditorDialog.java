package com.resurrection.blowtorch2.lib.window;

import java.util.List;

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
	private final CheckBox enabledBox;
	private final CheckBox repeatBox;
	private final CheckBox quietBox;
	private final RadioGroup actions;
	private final View commandBlock;
	private final int lineId;
	private final int inputId;
	private final int stepId;
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
		if (existing != null) {
			capture.set(existing.keyCode, existing.modifiers);
		} else {
			capture.arm();
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

		enabledBox = new CheckBox(activity);
		enabledBox.setText("Enabled");
		enabledBox.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		enabledBox.setChecked(existing == null || existing.enabled);
		form.addView(enabledBox);

		form.addView(label(activity, "Key"));
		keyLabel = new TextView(activity);
		keyLabel.setText(capture.label());
		keyLabel.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		keyLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
		keyLabel.setBackgroundColor(ContextCompat.getColor(activity, R.color.chrome_title_bar));
		int chip = (int) (12 * density);
		keyLabel.setPadding(chip, chip, chip, chip);
		keyLabel.setFocusableInTouchMode(true);
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
		inputId = View.generateViewId();
		stepId = View.generateViewId();
		actions.addView(radio(activity, lineId, "Send a line"));
		actions.addView(radio(activity, inputId, "Send the current line"));
		actions.addView(radio(activity, stepId, "Step back"));
		TextView stepNote = caption(activity, "Uses the directions set in Speedwalk Directions.");
		int stepIndent = (int) (36 * density);
		stepNote.setPadding(stepIndent, 0, 0, (int) (4 * density));
		String action = existing == null ? KeyboardShortcuts.ACTION_LINE : existing.action;
		if (KeyboardShortcuts.ACTION_INPUT.equals(action)) {
			actions.check(inputId);
		} else if (KeyboardShortcuts.ACTION_STEP.equals(action)) {
			actions.check(stepId);
		} else {
			actions.check(lineId);
		}
		form.addView(actions);
		form.addView(stepNote);

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
		TextView why = label(activity,
				"Repeat and a hold line are not both on. Filling a hold line turns repeat off.");
		why.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
		((LinearLayout) commandBlock).addView(why);
		form.addView(commandBlock);

		quietBox = new CheckBox(activity);
		quietBox.setText("Don't show in the window");
		quietBox.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		quietBox.setChecked(existing != null && existing.quiet);
		form.addView(quietBox);

		android.widget.Button useButton = new android.widget.Button(activity);
		useButton.setText("Use a button");
		useButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				pickButton();
			}
		});
		form.addView(useButton);
		form.addView(caption(activity, "that already exists on your screen"));

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
		keyLabel.setText(capture.label());
		if (!listen) {
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
		boolean line = actions.getCheckedRadioButtonId() == lineId;
		boolean input = actions.getCheckedRadioButtonId() == inputId;
		commandBlock.setVisibility(line ? View.VISIBLE : View.GONE);
		quietBox.setVisibility(input ? View.GONE : View.VISIBLE);
	}

	private void save() {
		if (capture.keyCode() < 0) {
			setKeyListening(true);
			return;
		}
		String action = KeyboardShortcuts.ACTION_LINE;
		int checked = actions.getCheckedRadioButtonId();
		if (checked == inputId) {
			action = KeyboardShortcuts.ACTION_INPUT;
		} else if (checked == stepId) {
			action = KeyboardShortcuts.ACTION_STEP;
		}
		Row row = Row.of(capture.keyCode(), capture.modifiers(), action, enabledBox.isChecked(),
				repeatBox.isChecked(), tapField.getText().toString(), holdField.getText().toString(),
				nameField.getText().toString(), quietBox.isChecked() && checked != inputId);
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
		String raw = host.buttonLines();
		final List<Button> buttons = KeyboardShortcutButtons.parse(raw);
		if (buttons.isEmpty()) {
			new AlertDialog.Builder(getContext())
					.setMessage("This set has no buttons.")
					.setPositiveButton("Close", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(final DialogInterface dialog, final int which) {
							dialog.dismiss();
						}
					})
					.create()
					.show();
			return;
		}
		String[] labels = new String[buttons.size()];
		for (int i = 0; i < buttons.size(); i++) {
			labels[i] = buttons.get(i).listLabel();
		}
		new AlertDialog.Builder(getContext())
				.setTitle("Use a button")
				.setItems(labels, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog, final int which) {
						Button picked = buttons.get(which);
						formLock = true;
						actions.check(lineId);
						tapField.setText(picked.command);
						holdField.setText(picked.hold);
						repeatBox.setChecked(false);
						formLock = false;
						applyAction();
					}
				})
				.create()
				.show();
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

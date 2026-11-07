package com.resurrection.blowtorch2.lib.window;

import java.util.Map;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.speedwalk.DirectionData;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcutPad.Cell;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcutPad.Draft;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

/** Number pad. Cancel throws the draft away. Done replaces the pad rows. */
public final class KeyboardShortcutPadDialog extends Dialog {

	public interface Host {
		void save(KeyboardShortcuts next);

		Map<String, DirectionData> directions();
	}

	private final KeyboardShortcuts stored;
	private final Host host;
	private final Draft draft;
	private final Button[] cells = new Button[KeyboardShortcutPad.CELLS];
	private final KeyboardShortcutCapture latchCapture = new KeyboardShortcutCapture();
	private final TextView latchLabel;
	private final TextView lookNote;
	private final TextView latchNote;
	private final int noneId;
	private final int altId;
	private final int ctrlId;
	private final RadioGroup modifiers;
	private boolean latchArmed;

	private KeyboardShortcutPadDialog(final Activity activity, final KeyboardShortcuts stored,
			final Host host) {
		super(activity, EditorDialogChrome.fullScreenTheme());
		this.stored = stored == null ? KeyboardShortcuts.empty() : stored;
		this.host = host;
		this.draft = KeyboardShortcutPad.fromRows(this.stored);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		Window window = getWindow();
		if (window != null) {
			window.setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		}

		float density = activity.getResources().getDisplayMetrics().density;
		int pad = (int) (16 * density);
		LinearLayout shell = new LinearLayout(activity);
		shell.setOrientation(LinearLayout.VERTICAL);
		shell.setBackgroundColor(ContextCompat.getColor(activity, R.color.chrome_body));
		shell.addView(title(activity, "Number pad", density));

		LinearLayout form = new LinearLayout(activity);
		form.setOrientation(LinearLayout.VERTICAL);
		form.setPadding(pad, pad, pad, pad);
		form.addView(grid(activity, density));

		form.addView(label(activity, "Preset"));
		form.addView(preset(activity, "Compass words", KeyboardShortcutPad.compass()));
		form.addView(preset(activity, "Short steps", KeyboardShortcutPad.shortSteps()));
		Button speed = new Button(activity);
		speed.setText("Fill from Speedwalk Directions");
		speed.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				Map<String, DirectionData> directions = host.directions();
				KeyboardShortcutPad.copyCommands(draft, KeyboardShortcutPad.speedwalk(directions));
				showCells();
			}
		});
		form.addView(speed);

		form.addView(label(activity, "Modifier"));
		modifiers = new RadioGroup(activity);
		modifiers.setOrientation(LinearLayout.VERTICAL);
		noneId = View.generateViewId();
		altId = View.generateViewId();
		ctrlId = View.generateViewId();
		modifiers.addView(radio(activity, noneId, "None"));
		modifiers.addView(radio(activity, altId, "Alt"));
		modifiers.addView(radio(activity, ctrlId, "Ctrl"));
		if (draft.modifier == KeyboardShortcuts.MOD_CTRL) {
			modifiers.check(ctrlId);
		} else if (draft.modifier == KeyboardShortcuts.MOD_ALT) {
			modifiers.check(altId);
		} else {
			modifiers.check(noneId);
		}
		form.addView(modifiers);

		final CheckBox look = new CheckBox(activity);
		look.setText("Look");
		look.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		look.setChecked(draft.look);
		form.addView(look);
		lookNote = caption(activity, KeyboardShortcutPad.lookCaption(draft.modifier));
		form.addView(lookNote);

		form.addView(label(activity, "Walk lock"));
		latchLabel = new TextView(activity);
		latchLabel.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		latchLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
		latchLabel.setBackgroundColor(ContextCompat.getColor(activity, R.color.chrome_title_bar));
		int chip = (int) (12 * density);
		latchLabel.setPadding(chip, chip, chip, chip);
		latchLabel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				if (draft.modifier == 0) {
					return;
				}
				latchArmed = !latchArmed;
				if (latchArmed) {
					latchCapture.arm();
				} else {
					latchCapture.disarm();
				}
				showLatch();
			}
		});
		form.addView(latchLabel);
		latchNote = caption(activity, KeyboardShortcutPad.latchCaption(draft.modifier));
		form.addView(latchNote);
		showLatch();

		form.addView(caption(activity, "When the pad walks is Options → Typing."));

		look.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final android.widget.CompoundButton button, final boolean checked) {
				draft.look = checked;
			}
		});
		modifiers.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final RadioGroup group, final int checkedId) {
				if (checkedId == ctrlId) {
					draft.modifier = KeyboardShortcuts.MOD_CTRL;
				} else if (checkedId == altId) {
					draft.modifier = KeyboardShortcuts.MOD_ALT;
				} else {
					draft.modifier = 0;
					draft.latchKey = -1;
					latchArmed = false;
					latchCapture.disarm();
				}
				showLatch();
			}
		});

		ScrollView scroll = new ScrollView(activity);
		scroll.addView(form, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		shell.addView(scroll, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		Button cancel = new Button(activity);
		cancel.setText("Cancel");
		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				dismiss();
			}
		});
		Button help = new Button(activity);
		help.setText("?");
		help.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				EditorHelp.show(getContext(), "Number pad", EditorHelp.NUMBER_PAD);
			}
		});
		Button done = new Button(activity);
		done.setText("Done");
		done.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				host.save(KeyboardShortcutPad.apply(stored, draft));
				dismiss();
			}
		});
		shell.addView(buttonBar(activity, density, cancel, help, done));
		setContentView(shell, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		EditorDialogChrome.applyFullScreen(this);
	}

	public static void show(final Activity activity, final KeyboardShortcuts stored, final Host host) {
		if (activity == null || host == null) {
			return;
		}
		new KeyboardShortcutPadDialog(activity, stored, host).show();
	}

	@Override
	public boolean dispatchKeyEvent(final KeyEvent event) {
		if (latchArmed && draft.modifier != 0 && event != null
				&& latchCapture.onKey(event.getAction(), event.getKeyCode(), event.getMetaState(),
						event.getScanCode(), event.getDeviceId())) {
			if (latchCapture.keyCode() >= 0) {
				draft.latchKey = latchCapture.keyCode();
			}
			showLatch();
			return true;
		}
		return super.dispatchKeyEvent(event);
	}

	private LinearLayout grid(final Activity activity, final float density) {
		LinearLayout grid = new LinearLayout(activity);
		grid.setOrientation(LinearLayout.VERTICAL);
		int gap = (int) (4 * density);
		for (int row = 0; row < KeyboardShortcutPad.CELLS / 3; row++) {
			LinearLayout line = new LinearLayout(activity);
			line.setOrientation(LinearLayout.HORIZONTAL);
			for (int col = 0; col < 3; col++) {
				final int index = row * 3 + col;
				Button cell = new Button(activity);
				cell.setAllCaps(false);
				cell.setMinHeight((int) (64 * density));
				cell.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(final View v) {
						editCell(index);
					}
				});
				cells[index] = cell;
				LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
						0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
				params.setMargins(gap, gap, gap, gap);
				line.addView(cell, params);
			}
			grid.addView(line);
		}
		showCells();
		return grid;
	}

	private void showCells() {
		for (int i = 0; i < cells.length; i++) {
			cells[i].setText(KeyboardShortcutPad.cellLabel(draft.cells[i]));
		}
	}

	private void showLatch() {
		lookNote.setText(KeyboardShortcutPad.lookCaption(draft.modifier));
		latchNote.setText(KeyboardShortcutPad.latchCaption(draft.modifier));
		latchLabel.setText(KeyboardShortcutPad.latchChip(draft.modifier, draft.latchKey, latchArmed,
				latchCapture.label()));
	}

	private void editCell(final int index) {
		final Cell cell = draft.cells[index];
		final android.content.Context context = getContext();
		LinearLayout box = new LinearLayout(context);
		box.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
		box.setPadding(pad, pad, pad, pad);
		final EditText command = new EditText(context);
		command.setSingleLine(true);
		command.setInputType(InputType.TYPE_CLASS_TEXT);
		command.setText(KeyboardShortcutPad.editorText(cell));
		if (cell.keyCode == KeyboardShortcuts.KEYCODE_NUMPAD_ENTER) {
			command.setHint(KeyboardShortcutPad.SEND_CURRENT_LINE);
		}
		box.addView(command);
		final CheckBox repeat = new CheckBox(context);
		repeat.setText("Repeat");
		repeat.setChecked(cell.repeat);
		if (cell.keyCode != KeyboardShortcuts.KEYCODE_NUMPAD_ENTER) {
			box.addView(repeat);
		}
		new AlertDialog.Builder(context)
				.setTitle(cell.face)
				.setView(box)
				.setPositiveButton("Done", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog, final int which) {
						KeyboardShortcutPad.edit(draft, index,
								command.getText() == null ? "" : command.getText().toString(),
								repeat.isChecked());
						showCells();
						dialog.dismiss();
					}
				})
				.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog, final int which) {
						dialog.dismiss();
					}
				})
				.create()
				.show();
	}

	private Button preset(final Activity activity, final String title, final Draft source) {
		Button button = new Button(activity);
		button.setText(title);
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				KeyboardShortcutPad.copyCommands(draft, source);
				showCells();
			}
		});
		return button;
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

	private static RadioButton radio(final Activity activity, final int id, final String text) {
		RadioButton radio = new RadioButton(activity);
		radio.setId(id);
		radio.setText(text);
		radio.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		return radio;
	}

	private static LinearLayout buttonBar(final Activity activity, final float density,
			final Button... buttons) {
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

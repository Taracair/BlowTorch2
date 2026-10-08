package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.os.RemoteException;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

/** Direction commands, and the mode picker. Same chrome as the other editors. */
public final class GlobalGestureEditorDialog {

	public interface ModeChoice {
		void onMode(int mode);

		void onScroll(int scroll);
	}

	private static final String[][] ROWS = {
		{ "n", "↑" },
		{ "ne", "↗" },
		{ "e", "→" },
		{ "se", "↘" },
		{ "s", "↓" },
		{ "sw", "↙" },
		{ "w", "←" },
		{ "nw", "↖" },
	};

	private static final String[] MODES = {
		"Classic", "One finger", "Two fingers", "Both",
	};

	private static final String[] MODE_NOTES = {
		"One finger scrolls. Two fingers copy.",
		"A one-finger swipe sends a command.",
		"A two-finger swipe sends a command.",
		"One-finger and two-finger swipes send commands.",
	};

	private static final String[] SCROLL_NOTES = {
		"A one-finger swipe sends a command. Two fingers scroll the text.",
		"A move before the hold scrolls. The gesture starts after the hold.",
		"A one-finger swipe does not scroll.",
	};

	private GlobalGestureEditorDialog() {
	}

	public static void show(final Activity activity, final IConnectionBinder service,
			final Runnable onChanged) {
		if (activity == null || service == null) {
			return;
		}
		final Dialog dialog = chromeDialog(activity);
		float density = activity.getResources().getDisplayMetrics().density;
		GlobalGestures current = GlobalGestures.current();
		final EditText[][] fields = new EditText[2][ROWS.length];

		LinearLayout shell = new LinearLayout(activity);
		shell.setOrientation(LinearLayout.VERTICAL);
		shell.setBackgroundColor(ContextCompat.getColor(activity, R.color.chrome_body));
		shell.addView(titleBar(activity, "Edit global gestures", density));

		LinearLayout form = new LinearLayout(activity);
		form.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (16 * density);
		form.setPadding(pad, pad, pad, pad);
		TextView note = bodyText(activity, density);
		note.setText("A blank direction sends no command. Same list as Options → Gestures.");
		form.addView(note);
		addSection(activity, form, density, "One finger", 1, current, fields);
		addSection(activity, form, density, "Two fingers", 2, current, fields);

		ScrollView scroll = new ScrollView(activity);
		scroll.addView(form, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		shell.addView(scroll, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		Button cancel = new Button(activity);
		cancel.setText("Cancel");
		Button done = new Button(activity);
		done.setText("Done");
		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		done.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String stored = collect(fields);
				try {
					service.updateStringSetting(GlobalGestures.KEY_BINDINGS, stored);
				} catch (RemoteException e) {
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
							"GlobalGestureEditorDialog", e);
					return;
				}
				GlobalGestures.publish(GlobalGestures.current().withBindings(stored));
				if (onChanged != null) {
					onChanged.run();
				}
				dialog.dismiss();
			}
		});
		shell.addView(buttonBar(activity, density, cancel, done));

		dialog.setContentView(shell, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		EditorDialogChrome.applyFloatingWrapContentHeight(dialog);
		dialog.show();
	}

	public static void showMode(final Activity activity, final int currentMode,
			final int currentScroll, final ModeChoice choice) {
		if (activity == null || choice == null) {
			return;
		}
		final Dialog dialog = chromeDialog(activity);
		float density = activity.getResources().getDisplayMetrics().density;
		LinearLayout shell = new LinearLayout(activity);
		shell.setOrientation(LinearLayout.VERTICAL);
		shell.setBackgroundColor(ContextCompat.getColor(activity, R.color.chrome_body));
		shell.addView(titleBar(activity, "Gesture mode", density));

		LinearLayout form = new LinearLayout(activity);
		form.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (16 * density);
		form.setPadding(pad, pad, pad, pad);
		TextView note = bodyText(activity, density);
		note.setText("Same scrolling choice as Options → Gestures. Grey when this mode does not use it.");
		form.addView(note);

		form.addView(sectionLabel(activity, density, "Scrolling"));
		final RadioButton[] scrollRadios = new RadioButton[GlobalGestures.SCROLL_CHOICES.length];
		RadioGroup scrollGroup = new RadioGroup(activity);
		scrollGroup.setOrientation(LinearLayout.VERTICAL);
		final int[] scrollIds = new int[scrollRadios.length];
		int scrollChecked = GlobalGestures.clampScroll(currentScroll);
		for (int i = 0; i < scrollRadios.length; i++) {
			scrollRadios[i] = choiceRadio(activity, density,
					GlobalGestures.SCROLL_CHOICES[i], SCROLL_NOTES[i]);
			scrollIds[i] = scrollRadios[i].getId();
			scrollGroup.addView(scrollRadios[i]);
		}
		scrollGroup.check(scrollIds[scrollChecked]);
		scrollGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup g, int checkedId) {
				for (int i = 0; i < scrollIds.length; i++) {
					if (scrollIds[i] == checkedId) {
						choice.onScroll(i);
						return;
					}
				}
			}
		});
		form.addView(scrollGroup);

		form.addView(sectionLabel(activity, density, "Mode"));
		RadioGroup group = new RadioGroup(activity);
		group.setOrientation(LinearLayout.VERTICAL);
		final int[] ids = new int[MODES.length];
		final int[] modeHolder = new int[] { GlobalGestures.clampMode(currentMode) };
		for (int i = 0; i < MODES.length; i++) {
			RadioButton radio = choiceRadio(activity, density, MODES[i], MODE_NOTES[i]);
			ids[i] = radio.getId();
			group.addView(radio);
		}
		group.check(ids[modeHolder[0]]);
		paintScrollRadios(scrollRadios, modeHolder[0]);
		group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup g, int checkedId) {
				for (int i = 0; i < ids.length; i++) {
					if (ids[i] == checkedId) {
						modeHolder[0] = i;
						paintScrollRadios(scrollRadios, i);
						choice.onMode(i);
						return;
					}
				}
			}
		});
		form.addView(group);

		ScrollView scroll = new ScrollView(activity);
		scroll.addView(form, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		shell.addView(scroll, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		Button close = new Button(activity);
		close.setText("Close");
		close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		shell.addView(buttonBar(activity, density, close));

		dialog.setContentView(shell, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		EditorDialogChrome.applyFloatingWrapContentHeight(dialog);
		dialog.show();
	}

	private static Dialog chromeDialog(final Activity activity) {
		Dialog dialog = new Dialog(activity, EditorDialogChrome.dialogTheme());
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		Window window = dialog.getWindow();
		if (window != null) {
			window.setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		}
		dialog.setCanceledOnTouchOutside(true);
		return dialog;
	}

	private static TextView sectionLabel(final Activity activity, final float density,
			final String title) {
		TextView header = new TextView(activity);
		header.setText(title);
		header.setTextColor(0xFF88CCFF);
		header.setTypeface(Typeface.DEFAULT_BOLD);
		header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
		header.setPadding(0, (int) (8 * density), 0, (int) (4 * density));
		return header;
	}

	private static RadioButton choiceRadio(final Activity activity, final float density,
			final String title, final String note) {
		RadioButton radio = new RadioButton(activity);
		String line = title + "\n" + note;
		SpannableString span = new SpannableString(line);
		int split = title.length() + 1;
		span.setSpan(new RelativeSizeSpan(0.78f), split, line.length(),
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		span.setSpan(new ForegroundColorSpan(0xFFB0B6BE), split, line.length(),
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		radio.setText(span);
		radio.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		radio.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
		radio.setLineSpacing(2f * density, 1f);
		radio.setMinHeight((int) (56 * density));
		radio.setPadding(radio.getPaddingLeft(), (int) (6 * density),
				radio.getPaddingRight(), (int) (6 * density));
		radio.setId(View.generateViewId());
		return radio;
	}

	/** Classic and Two fingers do not use this choice. The stored value stays. */
	private static void paintScrollRadios(final RadioButton[] radios, final int mode) {
		boolean live = mode == GlobalGestures.MODE_ONE || mode == GlobalGestures.MODE_BOTH;
		for (int i = 0; i < radios.length; i++) {
			radios[i].setEnabled(live);
			radios[i].setAlpha(live ? 1f : 0.4f);
		}
	}

	private static TextView titleBar(final Activity activity, final String title,
			final float density) {
		TextView titleView = new TextView(activity);
		titleView.setText(title);
		titleView.setAllCaps(true);
		titleView.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		titleView.setBackgroundColor(ContextCompat.getColor(activity, R.color.chrome_title_bar));
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		titleView.setTypeface(Typeface.DEFAULT_BOLD);
		titleView.setGravity(Gravity.CENTER);
		titleView.setLayoutParams(new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, (int) (42 * density)));
		return titleView;
	}

	private static TextView bodyText(final Activity activity, final float density) {
		TextView note = new TextView(activity);
		note.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		note.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		note.setPadding(0, 0, 0, (int) (8 * density));
		return note;
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
		footer.setLayoutParams(new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return footer;
	}

	private static String collect(final EditText[][] fields) {
		StringBuilder sb = new StringBuilder();
		for (int fingers = 1; fingers <= 2; fingers++) {
			for (int i = 0; i < ROWS.length; i++) {
				String cmd = fields[fingers - 1][i].getText().toString().trim()
						.replace('\n', ' ');
				if (cmd.length() == 0) {
					continue;
				}
				sb.append(fingers).append('.').append(ROWS[i][0]).append('=')
						.append(cmd).append('\n');
			}
		}
		return sb.toString();
	}

	private static void addSection(final Activity activity, final LinearLayout root,
			final float density, final String title, final int fingers,
			final GlobalGestures current, final EditText[][] fields) {
		TextView header = new TextView(activity);
		header.setText(title);
		header.setTextColor(0xFF88CCFF);
		header.setTypeface(Typeface.DEFAULT_BOLD);
		header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
		header.setPadding(0, (int) (12 * density), 0, (int) (4 * density));
		root.addView(header, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		for (int i = 0; i < ROWS.length; i++) {
			LinearLayout row = new LinearLayout(activity);
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setGravity(Gravity.CENTER_VERTICAL);
			TextView label = new TextView(activity);
			label.setText(ROWS[i][1]);
			label.setGravity(Gravity.CENTER);
			label.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
			label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
			row.addView(label, new LinearLayout.LayoutParams(
					(int) (40 * density), ViewGroup.LayoutParams.WRAP_CONTENT));
			EditText edit = new EditText(activity);
			edit.setSingleLine(true);
			edit.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
			edit.setHint("command");
			edit.setHintTextColor(0x88F2F4F6);
			String cmd = current.binding(fingers, ROWS[i][0]);
			if (cmd != null) {
				edit.setText(cmd);
			}
			row.addView(edit, new LinearLayout.LayoutParams(
					0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
			root.addView(row, new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			fields[fingers - 1][i] = edit;
		}
	}
}

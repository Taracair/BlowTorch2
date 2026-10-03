package com.resurrection.blowtorch2.lib.trigger;

import java.util.List;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.Option;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsGroup;
import com.resurrection.blowtorch2.lib.service.sensor.ShakeTrace;
import com.resurrection.blowtorch2.lib.service.sensor.CustomShakeLibrary;
import com.resurrection.blowtorch2.lib.service.sensor.CustomShakeLibrary.Entry;
import com.resurrection.blowtorch2.lib.service.sensor.SensorWorldFlags;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Names, strokes and the switch that quiets the four built-in directions.
 * Shapes stay on this phone. Commands stay in this world's triggers.
 */
public class MyShakesDialog extends Dialog {

	public interface Closed {
		void onClosed();
	}

	private final IConnectionBinder service;
	private final boolean showRegexWarning;
	private final Closed closed;
	private LinearLayout rows;
	private boolean ignoreSwitch;

	public MyShakesDialog(final Context context, final IConnectionBinder service,
			final boolean showRegexWarning, final Closed closed) {
		super(context, R.style.BlowTorch_Dialog_FullScreen);
		this.service = service;
		this.showRegexWarning = showRegexWarning;
		this.closed = closed;
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (12 * density);

		LinearLayout root = new LinearLayout(getContext());
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(0xFF16181C);

		TextView title = new TextView(getContext());
		title.setText("MY SHAKES");
		title.setTextColor(0xFFF2F4F6);
		title.setBackgroundColor(0xFF1E2126);
		title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setGravity(Gravity.CENTER);
		root.addView(title, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, (int) (42 * density)));

		ScrollView scroll = new ScrollView(getContext());
		LinearLayout body = new LinearLayout(getContext());
		body.setOrientation(LinearLayout.VERTICAL);
		body.setPadding(pad, pad, pad, pad);

		LinearLayout switchRow = new LinearLayout(getContext());
		switchRow.setOrientation(LinearLayout.HORIZONTAL);
		switchRow.setGravity(Gravity.CENTER_VERTICAL);
		TextView switchLabel = new TextView(getContext());
		switchLabel.setText("Use my shakes");
		switchLabel.setTextColor(0xFFF2F4F6);
		switchLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
		switchLabel.setTypeface(Typeface.DEFAULT_BOLD);
		switchRow.addView(switchLabel, new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
		Switch use = new Switch(getContext());
		ignoreSwitch = true;
		use.setChecked(readFlag(SensorWorldFlags.MY_SHAKES, false));
		ignoreSwitch = false;
		use.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final CompoundButton button, final boolean on) {
				if (ignoreSwitch) {
					return;
				}
				writeFlag(SensorWorldFlags.MY_SHAKES, on);
			}
		});
		switchRow.addView(use);
		body.addView(switchRow);

		TextView explain = new TextView(getContext());
		explain.setTextColor(0xFF9AA3AD);
		explain.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
		explain.setPadding(0, pad / 3, 0, pad);
		explain.setText("Off until you turn this on. On: left, right, up, down and"
				+ " letter patterns stay quiet in this world. Shake the phone keeps"
				+ " its own switch. The lines stay on this phone. The command is a"
				+ " trigger in this world, so another phone records the shake again.");
		body.addView(explain);

		rows = new LinearLayout(getContext());
		rows.setOrientation(LinearLayout.VERTICAL);
		body.addView(rows);

		Button neu = new Button(getContext());
		neu.setText("New shake");
		neu.setAllCaps(false);
		neu.setMinHeight((int) (44 * density));
		neu.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				openEditor(null);
			}
		});
		body.addView(neu);

		scroll.addView(body);
		root.addView(scroll, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

		Button close = new Button(getContext());
		close.setText("Close");
		close.setMinHeight((int) (44 * density));
		close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				dismiss();
			}
		});
		LinearLayout footer = new LinearLayout(getContext());
		footer.setBackgroundColor(0xFF1E2126);
		footer.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
		footer.addView(close, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));
		root.addView(footer);

		setContentView(root);
		Window window = getWindow();
		if (window != null) {
			window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
					WindowManager.LayoutParams.MATCH_PARENT);
		}
		fill();
	}

	private void fill() {
		rows.removeAllViews();
		CustomShakeLibrary lib = load();
		if (lib.entries().isEmpty()) {
			TextView none = new TextView(getContext());
			none.setText("No shakes recorded on this phone yet.");
			none.setTextColor(0xFF9AA3AD);
			none.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
			none.setPadding(0, 0, 0, 12);
			rows.addView(none);
			return;
		}
		for (int i = 0; i < lib.entries().size(); i++) {
			rows.addView(row(lib.entries().get(i)));
		}
	}

	private View row(final Entry entry) {
		float density = getContext().getResources().getDisplayMetrics().density;
		LinearLayout line = new LinearLayout(getContext());
		line.setOrientation(LinearLayout.VERTICAL);
		line.setPadding(0, (int) (8 * density), 0, (int) (8 * density));

		TextView name = new TextView(getContext());
		name.setText(entry.getName());
		name.setTextColor(0xFFF2F4F6);
		name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
		name.setTypeface(Typeface.DEFAULT_BOLD);
		line.addView(name);

		TextView detail = new TextView(getContext());
		detail.setTextColor(0xFF8FC9A0);
		detail.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
		String ready = entry.canMatch() ? "" : " — needs " + CustomShakeLibrary.MIN_TRACES;
		detail.setText(entry.getTraces().size() + " lines, match " + entry.getTolerance()
				+ ready);
		line.addView(detail);

		LinearLayout buttons = new LinearLayout(getContext());
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		Button edit = small("Edit");
		edit.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				openEditor(entry);
			}
		});
		Button command = small("Command");
		command.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				MyShakeEditorDialog.openCommand(getContext(), service, showRegexWarning,
						entry.getName(), new Runnable() {
							@Override
							public void run() {
								fill();
							}
						});
			}
		});
		Button delete = small("Delete");
		delete.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				confirmDelete(entry.getName());
			}
		});
		buttons.addView(edit);
		buttons.addView(command);
		buttons.addView(delete);
		line.addView(buttons);
		return line;
	}

	private void openEditor(final Entry entry) {
		String name = entry == null ? null : entry.getName();
		List<ShakeTrace> traces = entry == null ? null : entry.getTraces();
		int tolerance = entry == null
				? CustomShakeLibrary.DEFAULT_TOLERANCE : entry.getTolerance();
		new MyShakeEditorDialog(getContext(), service, showRegexWarning, name, traces,
				tolerance, new MyShakeEditorDialog.Saved() {
					@Override
					public void onSaved() {
						fill();
					}
				}).show();
	}

	private void confirmDelete(final String name) {
		new AlertDialog.Builder(getContext())
				.setMessage("Remove the recorded shape \"" + name + "\" from this"
						+ " phone? The command in this world stays.")
				.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog, final int which) {
						try {
							CustomShakeLibrary lib = CustomShakeLibrary.decode(
									service.getCustomShakeLibrary()).without(name);
							service.setCustomShakeLibrary(lib.encode());
							fill();
						} catch (RemoteException e) {
							com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
									"MyShakesDialog.delete", e);
						}
					}
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private CustomShakeLibrary load() {
		try {
			return CustomShakeLibrary.decode(service.getCustomShakeLibrary());
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"MyShakesDialog.load", e);
			return CustomShakeLibrary.empty();
		}
	}

	private boolean readFlag(final String key, final boolean fallback) {
		try {
			SettingsGroup root = service.getSettings();
			if (root == null) {
				return fallback;
			}
			Option opt = root.findOptionByKey(key);
			if (opt instanceof BooleanOption) {
				Object value = ((BooleanOption) opt).getValue();
				if (value instanceof Boolean) {
					return ((Boolean) value).booleanValue();
				}
			}
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"MyShakesDialog.read", e);
		}
		return fallback;
	}

	private void writeFlag(final String key, final boolean on) {
		try {
			service.updateBooleanSetting(key, on);
			service.saveSettings();
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"MyShakesDialog.write", e);
		}
	}

	private Button small(final String text) {
		Button button = new Button(getContext());
		button.setText(text);
		button.setAllCaps(false);
		button.setMinHeight((int) (40 * getContext().getResources().getDisplayMetrics().density));
		button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
		return button;
	}

	@Override
	public void dismiss() {
		super.dismiss();
		if (closed != null) {
			closed.onClosed();
		}
	}
}

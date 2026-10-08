package com.resurrection.blowtorch2.lib.trigger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.service.sensor.CustomShakeLibrary;
import com.resurrection.blowtorch2.lib.service.sensor.CustomShakeLibrary.Entry;
import com.resurrection.blowtorch2.lib.service.sensor.CustomShakeNames;
import com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog;
import com.resurrection.blowtorch2.lib.service.sensor.ScreenAxes;
import com.resurrection.blowtorch2.lib.service.sensor.ShakeCapture;
import com.resurrection.blowtorch2.lib.service.sensor.ShakeTrace;
import com.resurrection.blowtorch2.lib.window.PluginFilterSelectionDialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Record one named shake. Each finished stroke is another line on the same
 * two plots. The shape is saved on this phone; the command is a normal trigger.
 */
public class MyShakeEditorDialog extends Dialog {

	public interface Saved {
		void onSaved();
	}

	private final IConnectionBinder service;
	private final boolean showRegexWarning;
	private final String existingName;
	private final Saved saved;
	private final List<ShakeTrace> traces = new ArrayList<ShakeTrace>();
	/** Names already stored on this phone. A new shake must not reuse one. */
	private final Set<String> usedNames = new HashSet<String>();
	private final ShakeCapture capture = new ShakeCapture();
	private final Handler handler = new Handler(Looper.getMainLooper());
	/** Dies with this process, so the service drops recording if the dialog never closes. */
	private final Binder recordingToken = new Binder();
	private final float[] screenAccel = new float[3];

	private int tolerance = CustomShakeLibrary.DEFAULT_TOLERANCE;
	private float shakeThreshold = 15f;
	/** When the previous stroke opened, or -1. Same clock as play. */
	private long lastStrokeStart = -1L;
	private SensorManager manager;
	private Sensor sensor;
	private SensorEventListener listener;

	private EditText nameField;
	private TextView count;
	private TextView reading;
	private TextView closeness;
	private ShakeTraceView plane;
	private ShakeTraceView depth;
	private Button save;

	public MyShakeEditorDialog(final Context context, final IConnectionBinder service,
			final boolean showRegexWarning, final String existingName,
			final List<ShakeTrace> existing, final int existingTolerance,
			final Saved saved) {
		super(context, R.style.BlowTorch_Dialog_FullScreen);
		this.service = service;
		this.showRegexWarning = showRegexWarning;
		this.existingName = existingName;
		this.saved = saved;
		if (existing != null) {
			traces.addAll(existing);
		}
		if (existingName != null) {
			tolerance = existingTolerance;
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (12 * density + 0.5f);

		LinearLayout root = new LinearLayout(getContext());
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(0xFF16181C);

		TextView title = new TextView(getContext());
		title.setText(existingName == null ? "NEW SHAKE" : existingName.toUpperCase(Locale.US));
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

		nameField = new EditText(getContext());
		nameField.setHint("Shake name");
		nameField.setSingleLine(true);
		nameField.setTextColor(Color.WHITE);
		if (existingName != null) {
			nameField.setText(existingName);
			nameField.setEnabled(false);
		}
		nameField.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(final CharSequence s, final int start,
					final int count, final int after) {
			}

			@Override
			public void onTextChanged(final CharSequence s, final int start,
					final int before, final int count) {
				showStatus();
			}

			@Override
			public void afterTextChanged(final Editable s) {
			}
		});
		body.addView(nameField);

		count = label();
		body.addView(count);
		reading = label();
		reading.setTextColor(0xFF66CCFF);
		body.addView(reading);

		body.addView(caption("Across the screen. Right is right, up is up."));
		plane = new ShakeTraceView(getContext());
		plane.setEmptyText("Shake, and the line appears here.");
		body.addView(plane, plotParams(density));

		body.addView(caption("Toward you is up on this plot, away is down."));
		depth = new ShakeTraceView(getContext());
		depth.setDepth(true);
		depth.setEmptyText("A poke toward you or away shows up here.");
		body.addView(depth, plotParams(density));

		closeness = label();
		body.addView(closeness);
		SeekBar slider = new SeekBar(getContext());
		slider.setMax(100);
		slider.setProgress(tolerance);
		slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(final SeekBar bar, final int progress,
					final boolean fromUser) {
				tolerance = progress;
				showCloseness();
			}

			@Override
			public void onStartTrackingTouch(final SeekBar bar) {
			}

			@Override
			public void onStopTrackingTouch(final SeekBar bar) {
			}
		});
		body.addView(slider);

		Button undo = footerButton("Undo");
		undo.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				if (!traces.isEmpty()) {
					traces.remove(traces.size() - 1);
					redraw();
				}
			}
		});
		Button command = footerButton("Command");
		command.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				if (chosenName() == null) {
					count.setText("Name it first. Letters and digits, two or more.");
					return;
				}
				openCommand();
			}
		});
		LinearLayout tools = new LinearLayout(getContext());
		tools.setOrientation(LinearLayout.HORIZONTAL);
		tools.setPadding(0, pad / 2, 0, 0);
		tools.addView(undo, weight());
		tools.addView(command, weight());
		body.addView(tools);

		scroll.addView(body);
		root.addView(scroll, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

		LinearLayout footer = new LinearLayout(getContext());
		footer.setOrientation(LinearLayout.HORIZONTAL);
		footer.setBackgroundColor(0xFF1E2126);
		footer.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);

		Button cancel = footerButton("Cancel");
		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				dismiss();
			}
		});
		save = footerButton("Done");
		save.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				saveShape();
			}
		});
		footer.addView(cancel, weight());
		footer.addView(save, weight());
		root.addView(footer);

		setContentView(root);
		Window window = getWindow();
		if (window != null) {
			window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
					WindowManager.LayoutParams.MATCH_PARENT);
			window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
		}
		try {
			shakeThreshold = service.getShakeThreshold();
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"MyShakeEditorDialog.threshold", e);
		}
		loadUsedNames();
		redraw();
		listen();
	}

	private void loadUsedNames() {
		try {
			CustomShakeLibrary lib = CustomShakeLibrary.decode(service.getCustomShakeLibrary());
			for (int i = 0; i < lib.entries().size(); i++) {
				usedNames.add(lib.entries().get(i).getName());
			}
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"MyShakeEditorDialog.names", e);
		}
	}

	@SuppressWarnings("deprecation")
	private int displayRotation() {
		Window window = getWindow();
		if (window == null) {
			return ScreenAxes.ROTATION_0;
		}
		WindowManager manager = window.getWindowManager();
		if (manager == null) {
			return ScreenAxes.ROTATION_0;
		}
		Display display = manager.getDefaultDisplay();
		if (display == null) {
			return ScreenAxes.ROTATION_0;
		}
		return display.getRotation();
	}

	private void listen() {
		Object raw = getContext().getSystemService(Context.SENSOR_SERVICE);
		manager = (raw instanceof SensorManager) ? (SensorManager) raw : null;
		if (manager == null) {
			reading.setText("No motion sensor.");
			return;
		}
		sensor = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
		if (sensor == null) {
			reading.setText("This phone has no linear acceleration sensor, so a"
					+ " recorded shake cannot be told from a tilt. Shake the phone"
					+ " still works.");
			return;
		}
		listener = new SensorEventListener() {
			@Override
			public void onSensorChanged(final SensorEvent event) {
				if (event == null || event.values == null || event.values.length < 3) {
					return;
				}
				float x = event.values[0];
				float y = event.values[1];
				float z = event.values[2];
				float magnitude = (float) Math.sqrt((x * x) + (y * y) + (z * z));
				reading.setText(String.format(Locale.US, "%.1f m/s²", magnitude));
				long now = android.os.SystemClock.elapsedRealtime();
				ScreenAxes.toScreen(displayRotation(), x, y, z, screenAccel);
				if (!capture.isOpen()) {
					if (!ShakeCapture.mayStart(now, lastStrokeStart)) {
						if (magnitude >= shakeThreshold) {
							count.setText("Too soon. The next shake can start half"
									+ " a second after the last one started.");
						}
						return;
					}
					if (traces.size() >= CustomShakeLibrary.MAX_TRACES) {
						if (magnitude >= shakeThreshold) {
							count.setText("12 lines is the limit. Undo one to"
									+ " record another.");
						}
						return;
					}
					if (magnitude < shakeThreshold) {
						return;
					}
					lastStrokeStart = now;
				}
				ShakeTrace done = capture.add(now, screenAccel[0], screenAccel[1],
						screenAccel[2], magnitude, shakeThreshold);
				if (done == null) {
					return;
				}
				traces.add(done.resampled(CustomShakeLibrary.POINTS));
				redraw();
			}

			@Override
			public void onAccuracyChanged(final Sensor s, final int accuracy) {
			}
		};
		manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME, handler);
		try {
			service.setShakeRecording(true, recordingToken);
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"MyShakeEditorDialog.recording", e);
		}
	}

	private void redraw() {
		plane.setTraces(traces);
		depth.setTraces(traces);
		showStatus();
	}

	private void showStatus() {
		String problem = nameProblem();
		if (problem != null) {
			count.setText(problem);
		} else {
			int n = traces.size();
			String need = n < CustomShakeLibrary.MIN_TRACES
					? " Shake the same way again. " + CustomShakeLibrary.MIN_TRACES
							+ " before Done."
					: " More lines cover more of how you actually shake.";
			count.setText(n + (n == 1 ? " line." : " lines.") + need);
		}
		showCloseness();
		refreshSave();
	}

	/** A finished new name that is already taken, or null while the field is still being typed. */
	private String nameProblem() {
		if (existingName != null || nameField == null || nameField.getText() == null) {
			return null;
		}
		String name = CustomShakeLibrary.normalizeName(nameField.getText().toString());
		if (name == null) {
			return null;
		}
		if (CustomShakeLibrary.isReserved(name) || usedNames.contains(name)) {
			return "That name is already used.";
		}
		return null;
	}

	private void showCloseness() {
		String word = tolerance < 34 ? "tight" : (tolerance < 67 ? "middling" : "loose");
		closeness.setText("How closely it has to match: " + word + " (" + tolerance + ")");
	}

	private void refreshSave() {
		String name = chosenName();
		boolean ok = nameProblem() == null && name != null
				&& traces.size() >= CustomShakeLibrary.MIN_TRACES;
		save.setEnabled(ok);
	}

	private String chosenName() {
		if (existingName != null) {
			return existingName;
		}
		return CustomShakeLibrary.normalizeName(nameField.getText().toString());
	}

	private void saveShape() {
		String name = chosenName();
		if (name == null) {
			count.setText("Use a short name: a letter, then letters or digits.");
			return;
		}
		if (nameProblem() != null) {
			count.setText(nameProblem());
			return;
		}
		if (traces.size() < CustomShakeLibrary.MIN_TRACES) {
			count.setText("Record at least " + CustomShakeLibrary.MIN_TRACES + " lines.");
			return;
		}
		try {
			CustomShakeLibrary lib = CustomShakeLibrary.decode(service.getCustomShakeLibrary());
			if (existingName == null && lib.byName(name) != null) {
				count.setText("That name is already used.");
				return;
			}
			if (CustomShakeLibrary.isReserved(name)) {
				count.setText("That name is already used.");
				return;
			}
			if (lib.byName(name) == null
					&& lib.entries().size() >= CustomShakeLibrary.MAX_NAMES) {
				count.setText("40 named shakes is the limit. Delete one in My shakes first.");
				return;
			}
			CustomShakeLibrary next = lib.with(new Entry(name, tolerance, traces));
			if (next == lib) {
				count.setText("That shake was not saved.");
				return;
			}
			service.setCustomShakeLibrary(next.encode());
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"MyShakeEditorDialog.save", e);
			return;
		}
		if (saved != null) {
			saved.onSaved();
		}
		dismiss();
	}

	/** Point the saved name at a command, script or sound. The shape is untouched. */
	void openCommand() {
		final Saved done = saved;
		openCommand(getContext(), service, showRegexWarning, chosenName(), new Runnable() {
			@Override
			public void run() {
				if (done != null) {
					done.onSaved();
				}
			}
		});
	}

	static void openCommand(final Context context, final IConnectionBinder service,
			final boolean showRegexWarning, final String name, final Runnable onDone) {
		GestureCatalog.Gesture gesture = CustomShakeNames.gesture(name);
		if (gesture == null || service == null || context == null) {
			return;
		}
		TriggerData target = null;
		try {
			java.util.Map<?, ?> all = service.getTriggerData();
			if (all != null) {
				for (Object value : all.values()) {
					if (value instanceof TriggerData
							&& gesture.getPattern().equals(((TriggerData) value).getPattern())
							&& !((TriggerData) value).isInterpretAsRegex()) {
						target = (TriggerData) value;
						break;
					}
				}
			}
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"MyShakeEditorDialog.command", e);
		}
		final boolean editing = target != null;
		TriggerEditorDialog editor = new TriggerEditorDialog(context,
				editing ? target : null, service, new Handler() {
					@Override
					public void handleMessage(final Message msg) {
						if (onDone != null) {
							onDone.run();
						}
					}
				}, PluginFilterSelectionDialog.MAIN_SETTINGS, showRegexWarning);
		if (!editing) {
			editor.presetGesture(gesture);
		}
		editor.show();
	}

	@Override
	public void dismiss() {
		try {
			service.setShakeRecording(false, recordingToken);
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"MyShakeEditorDialog.recording", e);
		}
		if (manager != null && listener != null) {
			try {
				manager.unregisterListener(listener);
			} catch (Exception ignored) {
			}
		}
		listener = null;
		handler.removeCallbacksAndMessages(null);
		super.dismiss();
	}

	private TextView label() {
		TextView view = new TextView(getContext());
		view.setTextColor(0xFF9AA3AD);
		view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
		return view;
	}

	private TextView caption(final String text) {
		TextView view = label();
		view.setText(text);
		view.setPadding(0, (int) (8 * getContext().getResources().getDisplayMetrics().density),
				0, 0);
		return view;
	}

	private LinearLayout.LayoutParams plotParams(final float density) {
		return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				(int) (150 * density));
	}

	private Button footerButton(final String text) {
		Button button = new Button(getContext());
		button.setText(text);
		button.setMinHeight((int) (44 * getContext().getResources().getDisplayMetrics().density));
		button.setAllCaps(false);
		return button;
	}

	private LinearLayout.LayoutParams weight() {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		lp.leftMargin = 4;
		lp.rightMargin = 4;
		return lp;
	}
}

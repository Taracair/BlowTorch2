package com.resurrection.blowtorch2.lib.trigger;

import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.service.sensor.MotionStats;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Teach the app how hard <em>you</em> shake this phone.
 *
 * <p><b>Why this is not a number in a settings box.</b> The shake threshold is
 * the one value here that cannot be right for everybody: it depends on the
 * sensor, on how the phone is held, and on how vigorously its owner shakes it.
 * Measured on one phone at a desk, a shake peaked at 27.7 m/s² and a quiet
 * baseline at 10.4 — but neither number means anything on someone else's phone,
 * and the maintainer's own runs were simulated. So the app ships a starting
 * value and this screen replaces it with a measurement.
 *
 * <p><b>Two halves, and the second is the one people skip.</b> Shaking sets a
 * floor; carrying the phone about sets a ceiling. A threshold that catches your
 * shake but also catches your walk to the shop will send commands to the game
 * from your pocket, so the walk is measured too and the result is only accepted
 * when the two do not overlap.
 *
 * <p>Runs in the UI process on purpose: the player is looking at the screen, the
 * reading has to move while they shake, and nothing here needs the service until
 * a number has been chosen. The chosen number is handed over as a command, which
 * is also how anybody can set it by hand.
 */
public class ShakeCalibrationDialog extends Dialog {

	/** Fraction of the weakest shake to sit the threshold at. */
	private static final double OF_SHAKE = 0.65;
	/** How far above the busiest moment of the walk it must still sit. */
	private static final double ABOVE_WALK = 1.25;
	private static final int SHAKE_SECONDS = 6;
	private static final int WALK_SECONDS = 10;

	private final IConnectionBinder service;
	private final Handler handler = new Handler(Looper.getMainLooper());

	private SensorManager manager;
	private Sensor sensor;
	private boolean usesGravity;
	private SensorEventListener listener;

	private TextView instruction;
	private TextView reading;
	private ProgressBar meter;
	private Button action;
	private TextView verdict;

	private MotionStats shaking;
	private MotionStats walking;
	private double liveMax;

	/** Where the player is in the two-step measurement. */
	private enum Step { READY, SHAKING, BETWEEN, WALKING, DONE }

	private Step step = Step.READY;

	public ShakeCalibrationDialog(final Context context, final IConnectionBinder service) {
		super(context);
		this.service = service;
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle("Calibrate shake");
		LinearLayout root = new LinearLayout(getContext());
		root.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (14 * getContext().getResources().getDisplayMetrics().density);
		root.setPadding(pad, pad, pad, pad);

		instruction = new TextView(getContext());
		instruction.setTextSize(15f);
		instruction.setTextColor(Color.WHITE);
		root.addView(instruction);

		reading = new TextView(getContext());
		reading.setTextSize(28f);
		reading.setTextColor(0xFF66CCFF);
		reading.setPadding(0, pad / 2, 0, 0);
		root.addView(reading);

		meter = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
		meter.setMax(400);
		root.addView(meter, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		verdict = new TextView(getContext());
		verdict.setTextSize(13f);
		verdict.setTextColor(Color.LTGRAY);
		verdict.setPadding(0, pad / 2, 0, pad / 2);
		root.addView(verdict);

		action = new Button(getContext());
		action.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				advance();
			}
		});
		root.addView(action);

		Button close = new Button(getContext());
		close.setText("Close");
		close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				dismiss();
			}
		});
		root.addView(close);

		setContentView(root);
		prepareSensor();
		showStep();
	}

	private void prepareSensor() {
		Object service = getContext().getSystemService(Context.SENSOR_SERVICE);
		manager = (service instanceof SensorManager) ? (SensorManager) service : null;
		if (manager == null) {
			return;
		}
		sensor = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
		usesGravity = false;
		if (sensor == null) {
			sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			usesGravity = true;
		}
	}

	private void showStep() {
		switch (step) {
		case READY:
			if (sensor == null) {
				instruction.setText("This phone has no motion sensor, so there is"
						+ " nothing to calibrate. The shake gesture cannot work here.");
				action.setEnabled(false);
				return;
			}
			instruction.setText("Two short measurements.\n\n1. Shake the phone the way"
					+ " you would to get out of a fight — really do it, for "
					+ SHAKE_SECONDS + " seconds.");
			action.setText("Start shaking");
			break;
		case SHAKING:
			instruction.setText("Shake now. Keep going until this stops.");
			action.setText("measuring…");
			action.setEnabled(false);
			break;
		case BETWEEN:
			instruction.setText("2. Now hold the phone as you would while walking with"
					+ " it, and walk about for " + WALK_SECONDS + " seconds. This is the"
					+ " half that stops the game getting commands from your pocket.");
			action.setText("Start walking");
			action.setEnabled(true);
			break;
		case WALKING:
			instruction.setText("Walk now. Keep going until this stops.");
			action.setText("measuring…");
			action.setEnabled(false);
			break;
		case DONE:
			action.setText("Save this");
			action.setEnabled(true);
			break;
		default:
			break;
		}
	}

	private void advance() {
		switch (step) {
		case READY:
			shaking = new MotionStats();
			step = Step.SHAKING;
			showStep();
			startRun(SHAKE_SECONDS, shaking);
			break;
		case BETWEEN:
			walking = new MotionStats();
			step = Step.WALKING;
			showStep();
			startRun(WALK_SECONDS, walking);
			break;
		case DONE:
			save();
			break;
		default:
			break;
		}
	}

	private void startRun(final int seconds, final MotionStats into) {
		if (manager == null || sensor == null) {
			return;
		}
		liveMax = 0.0;
		listener = new SensorEventListener() {
			@Override
			public void onSensorChanged(final SensorEvent event) {
				if (event == null || event.values == null || event.values.length < 3) {
					return;
				}
				double magnitude;
				if (usesGravity) {
					magnitude = MotionStats.gravityRemoved(event.values[0], event.values[1],
							event.values[2], SensorManager.GRAVITY_EARTH);
				} else {
					magnitude = Math.sqrt((event.values[0] * event.values[0])
							+ (event.values[1] * event.values[1])
							+ (event.values[2] * event.values[2]));
				}
				into.record(event.timestamp, magnitude);
				if (magnitude > liveMax) {
					liveMax = magnitude;
				}
				// The moving number is the point: without it the player cannot
				// tell a sensor that is not reporting from a shake that is too
				// gentle, which is the same confusion the probe was built for.
				reading.setText(String.format(java.util.Locale.US,
						"%.1f m/s²   (peak %.1f)", magnitude, liveMax));
				meter.setProgress((int) Math.min(400, magnitude * 10));
			}

			@Override
			public void onAccuracyChanged(final Sensor s, final int accuracy) {
			}
		};
		manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME, handler);
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				stopRun();
				step = (step == Step.SHAKING) ? Step.BETWEEN : Step.DONE;
				if (step == Step.DONE) {
					decide();
				}
				showStep();
			}
		}, seconds * 1000L);
	}

	private void stopRun() {
		if (manager != null && listener != null) {
			try {
				manager.unregisterListener(listener);
			} catch (Exception ignored) {
				// Nothing to do: the run is over either way.
			}
		}
		listener = null;
		meter.setProgress(0);
	}

	/** The number this measurement suggests, or a reason it cannot suggest one. */
	private double suggestion = 0.0;

	private void decide() {
		double shakePeak = shaking == null ? 0.0 : shaking.peak();
		double walkPeak = walking == null ? 0.0 : walking.peak();
		suggestion = 0.0;
		if (shakePeak < 4.0) {
			verdict.setText(String.format(java.util.Locale.US,
					"That barely moved — peak %.1f m/s². Either the sensor is not"
					+ " reporting or the shake was too gentle. Try again.", shakePeak));
			action.setEnabled(false);
			return;
		}
		double fromShake = shakePeak * OF_SHAKE;
		double fromWalk = walkPeak * ABOVE_WALK;
		if (fromWalk >= fromShake) {
			verdict.setText(String.format(java.util.Locale.US,
					"Your shake peaked at %.1f and carrying the phone peaked at %.1f."
					+ " They are too close to tell apart: a threshold that catches the"
					+ " shake would also fire while you walk. Shake harder, or use a"
					+ " different gesture — waving a hand over the screen has no such"
					+ " problem.", shakePeak, walkPeak));
			action.setEnabled(false);
			return;
		}
		suggestion = fromShake;
		verdict.setText(String.format(java.util.Locale.US,
				"Shake peaked at %.1f, carrying peaked at %.1f.\nSuggested threshold:"
				+ " %.1f m/s² — comfortably under your shake and well over anything"
				+ " that happens in a pocket.", shakePeak, walkPeak, suggestion));
	}

	private void save() {
		if (suggestion <= 0.0 || service == null) {
			return;
		}
		try {
			String command = String.format(java.util.Locale.US,
					".sensor threshold shake %.1f\r\n", suggestion);
			service.sendData(command.getBytes("UTF-8"));
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"ShakeCalibrationDialog.save", e);
		}
		dismiss();
	}

	@Override
	public void dismiss() {
		// Whatever step it stopped on, the sensor goes back.
		stopRun();
		handler.removeCallbacksAndMessages(null);
		super.dismiss();
	}
}

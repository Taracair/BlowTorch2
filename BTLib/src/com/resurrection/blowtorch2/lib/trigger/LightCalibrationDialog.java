package com.resurrection.blowtorch2.lib.trigger;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Teach the app what <em>dark</em> and <em>bright</em> mean where you play.
 *
 * <p><b>Why this cannot be a shipped number.</b> Lux readings are not comparable
 * between phones — the sensor sits under different glass, with different
 * coatings — and they are not comparable between rooms either. Measured on one
 * Pixel 9a: an unlit room read 0 and an ordinary lit room 150 to 350. A player
 * whose "dark" is a lit hallway at night needs a different line from one who
 * plays in a blackout, and neither of them should have to learn what a lux is.
 *
 * <p>So the player stands where it is dark and taps a button, then where it is
 * bright and taps again. The two thresholds are placed between the readings,
 * with a band in the middle that is neither — a room sitting exactly on a single
 * line would otherwise flip between dark and bright as a cloud went past, and
 * every trigger gated on it would fire each time.
 *
 * <p><b>Reading it live matters here more than for shake.</b> This sensor is
 * on-change: a still phone in steady light reports once and then says nothing at
 * all, which looks exactly like a broken sensor. Showing the last reading, and
 * saying that few readings are normal, is the difference between a screen people
 * trust and one they report as faulty.
 */
public class LightCalibrationDialog extends Dialog {

	private final IConnectionBinder service;
	private final Handler handler = new Handler(Looper.getMainLooper());

	private SensorManager manager;
	private Sensor sensor;
	private SensorEventListener listener;

	private TextView instruction;
	private TextView reading;
	private TextView verdict;
	private Button capture;
	private Button save;

	private float latest = -1f;
	private float darkSample = -1f;
	private float brightSample = -1f;

	public LightCalibrationDialog(final Context context, final IConnectionBinder service) {
		super(context, R.style.BlowTorch_Dialog);
		this.service = service;
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);

		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (14 * density + 0.5f);

		LinearLayout root = new LinearLayout(getContext());
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(color(R.color.chrome_body));

		TextView title = new TextView(getContext());
		title.setText("Calibrate light");
		title.setTextColor(color(R.color.chrome_title_text));
		title.setBackgroundColor(color(R.color.chrome_title_bar));
		title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setAllCaps(true);
		title.setGravity(Gravity.CENTER);
		root.addView(title, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, (int) (42 * density + 0.5f)));

		LinearLayout body = new LinearLayout(getContext());
		body.setOrientation(LinearLayout.VERTICAL);
		body.setPadding(pad, pad, pad, pad);

		instruction = new TextView(getContext());
		instruction.setTextSize(15f);
		instruction.setTextColor(Color.WHITE);
		body.addView(instruction);

		reading = new TextView(getContext());
		reading.setTextSize(26f);
		reading.setTextColor(0xFF66CCFF);
		reading.setPadding(0, pad / 2, 0, 0);
		body.addView(reading);

		verdict = new TextView(getContext());
		verdict.setTextSize(13f);
		verdict.setTextColor(color(R.color.chrome_description));
		verdict.setPadding(0, pad / 2, 0, pad / 2);
		body.addView(verdict);

		root.addView(body, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));

		LinearLayout footer = new LinearLayout(getContext());
		footer.setOrientation(LinearLayout.HORIZONTAL);
		footer.setBackgroundColor(color(R.color.chrome_title_bar));
		int footPad = (int) (6 * density + 0.5f);
		footer.setPadding(footPad, footPad, footPad, footPad);

		capture = new Button(getContext());
		capture.setMinHeight((int) (44 * density + 0.5f));
		capture.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				take();
			}
		});

		save = new Button(getContext());
		save.setText("Save");
		save.setEnabled(false);
		save.setMinHeight((int) (44 * density + 0.5f));
		save.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				store();
			}
		});

		Button close = new Button(getContext());
		close.setText("Close");
		close.setMinHeight((int) (44 * density + 0.5f));
		close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				dismiss();
			}
		});

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
				(int) (44 * density + 0.5f), 1f);
		footer.addView(capture, lp);
		LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0,
				(int) (44 * density + 0.5f), 1f);
		saveLp.leftMargin = footPad;
		footer.addView(save, saveLp);
		LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(0,
				(int) (44 * density + 0.5f), 1f);
		closeLp.leftMargin = footPad;
		footer.addView(close, closeLp);
		root.addView(footer);

		setContentView(root);
		Window window = getWindow();
		if (window != null) {
			int width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.92f);
			window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
			window.setGravity(Gravity.CENTER);
		}
		start();
		showStep();
	}

	private int color(int id) {
		return getContext().getResources().getColor(id);
	}

	private void start() {
		Object svc = getContext().getSystemService(Context.SENSOR_SERVICE);
		manager = (svc instanceof SensorManager) ? (SensorManager) svc : null;
		if (manager == null) {
			return;
		}
		sensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT);
		if (sensor == null) {
			return;
		}
		listener = new SensorEventListener() {
			@Override
			public void onSensorChanged(final SensorEvent event) {
				if (event == null || event.values == null || event.values.length < 1) {
					return;
				}
				latest = event.values[0];
				reading.setText(String.format(Locale.US, "%.0f lux", latest));
			}

			@Override
			public void onAccuracyChanged(final Sensor s, final int accuracy) {
			}
		};
		manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler);
		reading.setText("waiting for a reading…");
	}

	private void showStep() {
		if (sensor == null) {
			instruction.setText("This phone reports no light sensor, so there is"
					+ " nothing to calibrate and the dark and bright conditions"
					+ " will never be true here.");
			capture.setEnabled(false);
			return;
		}
		if (darkSample < 0f) {
			instruction.setText("1. Take the phone somewhere as dark as the dark you"
					+ " care about — the room where you play at night, or a pocket —"
					+ " and tap the button.");
			capture.setText("This is dark");
			verdict.setText("The number above is what the sensor reads right now. It"
					+ " only changes when the light does, so a still number is normal"
					+ " and not a fault.");
			return;
		}
		instruction.setText("2. Now somewhere bright — a lit room in the day, or"
				+ " outdoors — and tap again.");
		capture.setText("This is bright");
	}

	private void take() {
		if (latest < 0f) {
			verdict.setText("No reading yet. Move the phone, or cover and uncover the"
					+ " top of the screen: this sensor speaks only when the light"
					+ " changes.");
			return;
		}
		if (darkSample < 0f) {
			darkSample = latest;
			showStep();
			verdict.setText(String.format(Locale.US,
					"Dark here reads %.0f lux. Now find somewhere bright.", darkSample));
			return;
		}
		brightSample = latest;
		decide();
	}

	private void decide() {
		if (brightSample <= darkSample + 5f) {
			verdict.setText(String.format(Locale.US,
					"Dark read %.0f and bright read %.0f — too close to tell apart."
					+ " Try again with a real difference between the two places,"
					+ " otherwise every room would count as the same one.",
					darkSample, brightSample));
			brightSample = -1f;
			save.setEnabled(false);
			return;
		}
		verdict.setText(String.format(Locale.US,
				"Dark %.0f, bright %.0f.\nAnything at or under %.0f lux will count as"
				+ " dark, at or over %.0f as bright, and in between as neither —"
				+ " which is what stops a room on the line flipping back and forth.",
				darkSample, brightSample, darkThreshold(), brightThreshold()));
		save.setEnabled(true);
	}

	/** A quarter of the way up from the dark reading, and never below it. */
	private float darkThreshold() {
		return darkSample + ((brightSample - darkSample) * 0.25f);
	}

	/** Three quarters of the way up, so the middle band is real. */
	private float brightThreshold() {
		return darkSample + ((brightSample - darkSample) * 0.75f);
	}

	private void store() {
		if (service == null || brightSample < 0f) {
			return;
		}
		try {
			String command = String.format(Locale.US,
					".sensor threshold light %.0f %.0f\r\n",
					darkThreshold(), brightThreshold());
			service.sendData(command.getBytes("UTF-8"));
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"LightCalibrationDialog.store", e);
		}
		dismiss();
	}

	@Override
	public void dismiss() {
		if (manager != null && listener != null) {
			try {
				manager.unregisterListener(listener);
			} catch (Exception ignored) {
				// The screen is closing either way.
			}
		}
		listener = null;
		handler.removeCallbacksAndMessages(null);
		super.dismiss();
	}
}

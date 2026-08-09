package com.resurrection.blowtorch2.lib.service.sensor;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;
import com.resurrection.blowtorch2.lib.util.SessionLogger;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;

/**
 * What this particular phone's sensors are, and what they actually deliver.
 *
 * <p><b>Why this exists.</b> Every design for gesture input has to answer two
 * questions first, and neither can be answered by reading code: does this device
 * have the sensor at all, and does it deliver to <i>this process</i>? Sensor
 * hardware differs between models more than any other part of the platform —
 * plenty of recent phones report no discrete proximity sensor — and responders
 * run in {@code :stellar}, not in the UI process, so "the accelerometer works"
 * is not the same claim as "the accelerometer works where the responders are".
 *
 * <p>A player-run measurement, not instrumentation: nothing is registered until
 * the command is typed, everything is unregistered when it finishes, and the
 * answer is printed into the game window rather than into a log nobody will
 * fetch off a phone. That is why it stays in tracked code, the same argument
 * {@code ProbeCommand} makes for {@code .probe lines}.
 */
public final class SensorProbe {

	/** Longest a single sampling run may last. */
	public static final int MAX_SECONDS = 60;
	/** Shortest run worth the trouble. */
	public static final int MIN_SECONDS = 3;

	/** One run at a time. Two listeners fighting would measure each other. */
	private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

	private SensorProbe() {
	}

	/** Every sensor this device admits to, with the numbers that decide a design. */
	public static String inventory(final Context context) {
		SensorManager manager = managerFrom(context);
		if (manager == null) {
			return "\nNo SensorManager on this device. No sensor reading can be built.\n";
		}
		List<Sensor> all = manager.getSensorList(Sensor.TYPE_ALL);
		StringBuilder out = new StringBuilder();
		out.append("\n--- sensors on this device ---\n");
		if (all == null || all.isEmpty()) {
			out.append("The device reports no sensors at all.\n");
			return out.toString();
		}
		for (Sensor s : all) {
			out.append(String.format(Locale.US,
					"%-28s type %-3d  %5.2f mA  range %.1f  res %.4f%n",
					clip(s.getName(), 28), s.getType(), s.getPower(),
					s.getMaximumRange(), s.getResolution()));
			out.append(String.format(Locale.US,
					"    vendor %s | min delay %d us | fifo %d | %s%n",
					clip(s.getVendor(), 24), s.getMinDelay(), s.getFifoMaxEventCount(),
					s.isWakeUpSensor() ? "wake-up" : "not wake-up"));
		}
		out.append("\n--- what BlowTorch would look for ---\n");
		out.append(present(manager, Sensor.TYPE_LINEAR_ACCELERATION, "shake, first choice"));
		out.append(present(manager, Sensor.TYPE_ACCELEROMETER, "shake fallback, face up/down"));
		out.append(present(manager, Sensor.TYPE_GAME_ROTATION_VECTOR, "flip, first choice"));
		out.append(present(manager, Sensor.TYPE_PROXIMITY, "wave over the screen"));
		out.append(present(manager, Sensor.TYPE_LIGHT, "wave fallback, dark room"));
		out.append(present(manager, Sensor.TYPE_SIGNIFICANT_MOTION, "picked the phone up"));
		out.append(present(manager, Sensor.TYPE_STEP_COUNTER, "walking (needs a permission)"));
		out.append("\nA reading whose sensors are all missing cannot be offered on this\n");
		out.append("device, and the settings screen has to say so rather than go quiet.\n");
		out.append("Presence is not delivery: .probe sensors shake measures that.\n");
		return out.toString();
	}

	/**
	 * Watch the light sensor and report what this room actually reads.
	 *
	 * <p>"Dark" and "bright" are the two words a player wants, and the lux
	 * numbers behind them cannot be written from a desk: sensors differ, and so
	 * do rooms. Run this in the dark, under a lamp and outdoors, and the three
	 * readings are what a threshold should be built from.
	 *
	 * @return the message to print now.
	 */
	public static String startLightRun(final Connection connection, final int seconds) {
		if (connection == null) {
			return "";
		}
		int duration = Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, seconds));
		final Context context = contextOf(connection);
		if (context == null) {
			return "\nThe connection has no context to ask.\n";
		}
		final SensorManager manager = managerFrom(context);
		if (manager == null) {
			return "\nNo SensorManager on this device.\n";
		}
		final Sensor light = manager.getDefaultSensor(Sensor.TYPE_LIGHT);
		if (light == null) {
			return "\nThis phone reports no light sensor, so nothing can be built\n"
					+ "on how bright the room is.\n";
		}
		if (!RUNNING.compareAndSet(false, true)) {
			return "\nA sensor probe is already running. Wait for it to report.\n";
		}
		final MotionStats stats = new MotionStats();
		stats.describeSource(light.getName() + " (light, lux)", light.isWakeUpSensor());
		final WeakReference<Connection> weak = new WeakReference<Connection>(connection);
		final HandlerThread thread = new HandlerThread("bt-light-probe");
		thread.start();
		final Handler own = new Handler(thread.getLooper());
		final SensorEventListener listener = new SensorEventListener() {
			@Override
			public void onSensorChanged(final SensorEvent event) {
				if (event == null || event.values == null || event.values.length < 1) {
					return;
				}
				stats.record(event.timestamp, event.values[0]);
			}

			@Override
			public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
			}
		};
		final boolean registered = manager.registerListener(listener, light,
				SensorManager.SENSOR_DELAY_NORMAL, own);
		final int reported = duration;
		own.postDelayed(new Runnable() {
			@Override
			public void run() {
				try {
					manager.unregisterListener(listener);
				} catch (Exception e) {
					BlowTorchLogger.logMinor("SensorProbe.unregisterLight", e);
				}
				StringBuilder body = new StringBuilder();
				body.append(header(registered, reported));
				body.append(lightReport(stats));
				deliver(weak, body.toString());
				RUNNING.set(false);
				thread.quitSafely();
			}
		}, reported * 1000L);
		return "\nLight probe running for " + reported + " s on " + light.getName()
				+ ".\nLeave the phone where the light is what you want measured.\n";
	}

	/**
	 * The light reading, in its own words.
	 *
	 * <p>Not {@code MotionStats.report()}: the light sensor is on-change, so a
	 * still phone in steady light reports once and then says nothing for the rest
	 * of the run. Printed through the motion report that looks alarming — "0 Hz",
	 * "largest gap 9188 ms", rows about gestures per m/s² — when it is in fact the
	 * sensor working exactly as designed. Few readings is the normal case here, so
	 * the report says so.
	 */
	private static String lightReport(final MotionStats stats) {
		StringBuilder out = new StringBuilder();
		out.append("\n--- light probe ---\n");
		if (stats.getSampleCount() == 0) {
			out.append("Nothing arrived. The light sensor reports only when the light\n");
			out.append("changes, so try again while moving the phone between a lit and\n");
			out.append("a shaded place.\n");
			return out.toString();
		}
		out.append(String.format(Locale.US, "readings      : %d%n", stats.getSampleCount()));
		out.append(String.format(Locale.US, "darkest       : %.0f lux%n", stats.percentile(0.0)));
		out.append(String.format(Locale.US, "typical       : %.0f lux%n", stats.percentile(0.5)));
		out.append(String.format(Locale.US, "brightest     : %.0f lux%n", stats.peak()));
		out.append("\nA handful of readings is normal: this sensor speaks only when\n");
		out.append("the light changes, not on a schedule.\n");
		out.append("\nFor a sense of scale, measured on one phone: a dark room reads 0,\n");
		out.append("an ordinary lit room 150-350. Daylight outdoors is far higher.\n");
		out.append("Run this where you actually play to learn what your rooms read.\n");
		return out.toString();
	}

	/**
	 * Register a motion sensor for a few seconds and report what arrived.
	 *
	 * <p>Returns immediately; the report is printed when the run ends. Sampling
	 * happens on a thread of its own — measuring the sensor on the connection's
	 * looper would measure our own loop as much as the hardware — and the report
	 * is handed back to the connection thread, which is the thread that owns the
	 * text buffer.
	 *
	 * @return the message to print now, telling the player what is happening.
	 */
	public static String startMotionRun(final Connection connection, final int seconds) {
		if (connection == null) {
			return "";
		}
		int duration = seconds;
		if (duration < MIN_SECONDS) {
			duration = MIN_SECONDS;
		}
		if (duration > MAX_SECONDS) {
			duration = MAX_SECONDS;
		}
		final Context context = contextOf(connection);
		if (context == null) {
			return "\nThe connection has no context to ask. This is a client bug,\n"
					+ "not a fact about your phone.\n";
		}
		final SensorManager manager = managerFrom(context);
		if (manager == null) {
			return "\nNo SensorManager on this device.\n";
		}
		Sensor chosen = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
		boolean gravityIncluded = false;
		if (chosen == null) {
			chosen = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			gravityIncluded = true;
		}
		if (chosen == null) {
			return "\nThis device reports neither linear acceleration nor an\n"
					+ "accelerometer. The shake reading is not possible here.\n";
		}
		if (!RUNNING.compareAndSet(false, true)) {
			return "\nA motion probe is already running. Wait for it to report.\n";
		}

		final MotionStats stats = new MotionStats();
		stats.describeSource(chosen.getName() + (gravityIncluded
				? " (accelerometer, gravity removed)" : " (linear acceleration)"),
				chosen.isWakeUpSensor());
		final boolean removeGravity = gravityIncluded;
		final WeakReference<Connection> weak = new WeakReference<Connection>(connection);
		final HandlerThread thread = new HandlerThread("bt-motion-probe");
		thread.start();
		final Handler own = new Handler(thread.getLooper());

		final SensorEventListener listener = new SensorEventListener() {
			@Override
			public void onSensorChanged(final SensorEvent event) {
				if (event == null || event.values == null || event.values.length < 3) {
					return;
				}
				double magnitude;
				if (removeGravity) {
					magnitude = MotionStats.gravityRemoved(event.values[0], event.values[1],
							event.values[2], SensorManager.GRAVITY_EARTH);
				} else {
					magnitude = Math.sqrt((event.values[0] * event.values[0])
							+ (event.values[1] * event.values[1])
							+ (event.values[2] * event.values[2]));
				}
				stats.record(event.timestamp, magnitude);
			}

			@Override
			public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
			}
		};

		final boolean registered = manager.registerListener(listener, chosen,
				SensorManager.SENSOR_DELAY_GAME, own);
		final int reportedSeconds = duration;
		own.postDelayed(new Runnable() {
			@Override
			public void run() {
				// Unregister first and unconditionally. A listener that outlives
				// its probe is exactly the battery drain the player would blame
				// on the whole app.
				try {
					manager.unregisterListener(listener);
				} catch (Exception e) {
					BlowTorchLogger.logMinor("SensorProbe.unregisterListener", e);
				}
				String body = header(registered, reportedSeconds) + stats.report();
				deliver(weak, body);
				RUNNING.set(false);
				thread.quitSafely();
			}
		}, reportedSeconds * 1000L);

		if (!registered) {
			return "\nThe sensor refused registration in this process. The run will\n"
					+ "report in " + reportedSeconds + " s and that answer is the point.\n";
		}
		// Most linear-acceleration sensors are not wake-up sensors: let the
		// screen go off mid-run and delivery stops, the report says "samples:
		// NONE", and that reads as "the service process cannot receive sensor
		// events" — which would be the wrong answer to the biggest question
		// this probe exists to settle.
		return "\nMotion probe running for " + reportedSeconds + " s on "
				+ chosen.getName() + ".\nShake the phone the way you would in a fight — "
				+ "or walk with it, for the\nbaseline run. "
				+ (chosen.isWakeUpSensor() ? "" : "KEEP THE SCREEN ON: this sensor stops\n"
					+ "delivering when the display sleeps, and the run would read as empty.\n")
				+ "The reading prints here when it ends.\n";
	}

	/**
	 * The line the registration decision rests on: responders live in
	 * {@code :stellar}, so whether events arrive <i>here</i> is what decides
	 * whether a gesture source needs to cross the binder at all.
	 */
	private static String header(final boolean registered, final int seconds) {
		StringBuilder out = new StringBuilder();
		out.append("\nregistration  : ").append(registered ? "accepted" : "REFUSED")
			.append(" in the service process (:stellar)\n");
		out.append("requested     : ").append(seconds).append(" s at SENSOR_DELAY_GAME\n");
		return out.toString();
	}

	/**
	 * Hand the report to the connection thread. The text buffer belongs to that
	 * thread, and a probe that corrupted the buffer would be a worse bug than
	 * the one it was measuring for.
	 */
	private static void deliver(final WeakReference<Connection> weak, final String body) {
		final Connection connection = weak.get();
		if (connection == null) {
			return;
		}
		Handler handler = connection.getHandler();
		if (handler == null) {
			return;
		}
		handler.post(new Runnable() {
			@Override
			public void run() {
				connection.sendDataToWindow(body);
				// Also into the session log: a measurement that can only be read
				// off the screen cannot leave the phone, and this one exists to
				// be carried back to whoever is choosing a threshold.
				try {
					SessionLogger.appendIncoming(connection.getContext(),
							connection.getDisplayName(), body);
				} catch (Exception e) {
					BlowTorchLogger.logMinor("SensorProbe.sessionLog", e);
				}
			}
		});
	}

	private static String present(final SensorManager manager, final int type,
			final String what) {
		Sensor s = manager.getDefaultSensor(type);
		if (s == null) {
			return String.format(Locale.US, "  MISSING  %-32s (%s)%n",
					typeName(type), what);
		}
		return String.format(Locale.US, "  present  %-32s (%s)%n",
				clip(s.getName(), 32), what);
	}

	private static String typeName(final int type) {
		switch (type) {
		case Sensor.TYPE_LINEAR_ACCELERATION:
			return "linear acceleration";
		case Sensor.TYPE_ACCELEROMETER:
			return "accelerometer";
		case Sensor.TYPE_GAME_ROTATION_VECTOR:
			return "game rotation vector";
		case Sensor.TYPE_PROXIMITY:
			return "proximity";
		case Sensor.TYPE_LIGHT:
			return "light";
		case Sensor.TYPE_SIGNIFICANT_MOTION:
			return "significant motion";
		case Sensor.TYPE_STEP_COUNTER:
			return "step counter";
		default:
			return "type " + type;
		}
	}

	/**
	 * The service context, or null. Separate from {@link #managerFrom} so that
	 * "no context" and "no SensorManager" cannot print the same message: the
	 * first is our bug, the second is a fact about the device.
	 */
	private static Context contextOf(final Connection connection) {
		try {
			return connection.getContext();
		} catch (Exception e) {
			BlowTorchLogger.logMinor("SensorProbe.contextOf", e);
			return null;
		}
	}

	private static SensorManager managerFrom(final Context context) {
		if (context == null) {
			return null;
		}
		Object service = context.getSystemService(Context.SENSOR_SERVICE);
		return (service instanceof SensorManager) ? (SensorManager) service : null;
	}

	private static String clip(final String text, final int width) {
		if (text == null) {
			return "";
		}
		return text.length() <= width ? text : text.substring(0, width);
	}
}

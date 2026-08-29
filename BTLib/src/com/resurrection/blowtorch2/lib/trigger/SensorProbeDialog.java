package com.resurrection.blowtorch2.lib.trigger;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.service.sensor.DeviceState;
import com.resurrection.blowtorch2.lib.service.sensor.FacingDetector;
import com.resurrection.blowtorch2.lib.service.sensor.GestureAvailability;
import com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog;
import com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture;
import com.resurrection.blowtorch2.lib.service.sensor.GestureTiming;
import com.resurrection.blowtorch2.lib.service.sensor.MotionStats;
import com.resurrection.blowtorch2.lib.service.sensor.OneShotGestures;
import com.resurrection.blowtorch2.lib.service.sensor.OrientationGesture;
import com.resurrection.blowtorch2.lib.window.EditorDialogChrome;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

/**
 * Does this phone actually see it? Test, on a row of the Sensors list.
 *
 * <p><b>The question this answers, and the one it does not.</b> Firing a
 * reading with {@code .sensor fire} proves the <em>actions</em> — it runs them
 * without anybody moving the phone. It says nothing about whether the phone can
 * see you wave, which on Android is a real question: sensor hardware differs by
 * model, a proximity sensor may be under the screen or absent, and a reading
 * that is listed as available can still sit there reporting nothing. This
 * screen watches the sensor live and shows what it reports while you do the
 * gesture.
 *
 * <p><b>Where it runs, and why that is allowed.</b> In the UI process, like
 * {@link ShakeCalibrationDialog} and for the same reason: the player is looking
 * at the screen, the number has to move while they move, and nothing here needs
 * the service. Every listener is released in {@link #dismiss()}. The detector
 * that fires real triggers is untouched and still lives in the service.
 *
 * <p><b>What it will not claim.</b> Shake and the two light readings are
 * decided by thresholds a player calibrates, and those are written by the
 * service process into preferences this process may hold a stale copy of. So
 * this screen shows the raw reading for those two and points at Calibrate
 * rather than announcing a verdict it cannot stand behind. Everything else —
 * near and far, which way up, the one-shot sensors, the system events — is
 * decided by the same shared code the real detector uses.
 */
public class SensorProbeDialog extends Dialog {

	private static final int SEEN_COLOR = 0xFF8FC9A0;
	private static final int IDLE_COLOR = 0xFF9AA3AD;
	private static final int DEAD_COLOR = 0xFFC98F8F;

	private final Gesture gesture;
	private final IConnectionBinder service;
	private final boolean configured;
	private final Handler handler = new Handler(Looper.getMainLooper());

	private TextView reading;
	private TextView verdict;
	private TextView caveat;

	private SensorManager manager;
	private SensorEventListener listener;
	private BroadcastReceiver receiver;
	private ComponentCallbacks orientationCallbacks;
	private Context orientationHost;
	private int lastOrientation;
	private boolean orientationSeeded;
	private OneShotGestures oneShot;
	private Runnable pendingCover;

	private int seen;
	/** Proximity only: when the hand arrived, so the release can be timed. */
	private long coveredSince;
	private boolean proximitySeeded;
	/**
	 * The plug state the last broadcast reported, or null before the first one.
	 *
	 * <p>Both plug broadcasts are sticky: registering delivers the state the
	 * phone is already in, and {@code ACTION_BATTERY_CHANGED} then arrives again
	 * on every change of charge level. Counting either as a gesture would report
	 * "seen it" for a charger nobody touched, which is exactly the hole the real
	 * watcher had to close.
	 */
	private Boolean lastPlugState;
	/** True where "seen" is an event rather than a reading that keeps arriving. */
	private boolean readingIsMomentary;
	private double peak;
	/** The same rules the service detector runs on, so the two cannot disagree. */
	private final FacingDetector facingDetector = new FacingDetector();
	/** A cover that already fired, so lifting the hand does not talk over it. */
	private boolean coverFired;

	public SensorProbeDialog(final Context context, final Gesture gesture,
			final IConnectionBinder service, final boolean configured) {
		super(context, EditorDialogChrome.dialogTheme());
		this.gesture = gesture;
		this.service = service;
		this.configured = configured;
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		setContentView(R.layout.sensor_probe_dialog);
		EditorDialogChrome.applyFloatingWrapContentHeight(this);

		((TextView) findViewById(R.id.titlebar)).setText("TEST: "
				+ gesture.getLabel().toUpperCase(java.util.Locale.US));
		reading = (TextView) findViewById(R.id.reading);
		verdict = (TextView) findViewById(R.id.verdict);
		caveat = (TextView) findViewById(R.id.caveat);

		Button close = (Button) findViewById(R.id.close);
		close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				dismiss();
			}
		});

		Button fire = (Button) findViewById(R.id.fire);
		// Only where something answers the reading. The two halves are different
		// questions and this is the other one: not "can the phone see it" but
		// "is what I set up what I meant".
		fire.setVisibility(configured ? View.VISIBLE : View.GONE);
		fire.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				runTheActions();
			}
		});

		start();
	}

	private void start() {
		GestureAvailability.Resolution r =
				GestureAvailability.resolve(getContext(), gesture);
		TextView instruction = (TextView) findViewById(R.id.instruction);
		if (!r.isAvailable()) {
			instruction.setText(r.missingReason()
					+ " There is nothing to watch, so this reading cannot fire here."
					+ " It is still worth setting up if you share the profile.");
			reading.setText("\u2014");
			verdict.setText("Not available on this phone.");
			verdict.setTextColor(DEAD_COLOR);
			return;
		}
		instruction.setText("Do it now: " + lowerFirst(gesture.getLabel())
				+ ". This screen shows what the phone reports while you do.");

		String provider = r.getProvider();
		if (GestureCatalog.BY_SYSTEM.equals(provider)) {
			startSystem();
		} else if (GestureCatalog.BY_PICKUP_SENSOR.equals(provider)
				|| GestureCatalog.BY_SIGNIFICANT_MOTION.equals(provider)
				|| GestureCatalog.BY_STATIONARY.equals(provider)) {
			startOneShot();
		} else {
			startStreaming(r.getSensor(), provider);
		}
	}

	/** Headphones, charger, battery, screen, rotation: no sensor chip. */
	private void startSystem() {
		if (OrientationGesture.ID_LANDSCAPE.equals(gesture.getId())
				|| OrientationGesture.ID_PORTRAIT.equals(gesture.getId())) {
			startOrientation();
			return;
		}
		if ("batterylow".equals(gesture.getId()) || "batteryok".equals(gesture.getId())) {
			startBatteryPercent();
			return;
		}
		readingIsMomentary = true;
		reading.setText("Listening");
		verdict.setText("Waiting for it to happen.");
		final String wanted = systemActionFor(gesture.getId());
		IntentFilter filter = new IntentFilter();
		filter.addAction(wanted);
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(final Context context, final Intent intent) {
				if (intent == null || !wanted.equals(intent.getAction())) {
					return;
				}
				// The plug broadcasts carry which way round it went, and the
				// sticky one is delivered on registration for the state the
				// phone is already in. Both would otherwise report a gesture
				// nobody made, which is the same hole the real watcher has.
				if (!matchesDirection(intent)) {
					return;
				}
				count();
			}
		};
		try {
			getContext().registerReceiver(receiver, filter);
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"SensorProbeDialog.registerReceiver", e);
			receiver = null;
		}
		caveat.setText("A system event, so this works on every phone and costs"
				+ " nothing to watch.");
	}

	private void startOrientation() {
		readingIsMomentary = true;
		reading.setText("Listening");
		verdict.setText("Waiting for you to turn the phone.");
		orientationSeeded = false;
		orientationHost = getContext().getApplicationContext();
		if (orientationHost == null) {
			orientationHost = getContext();
		}
		lastOrientation = orientationHost.getResources().getConfiguration().orientation;
		orientationSeeded = true;
		orientationCallbacks = new ComponentCallbacks() {
			@Override
			public void onConfigurationChanged(final Configuration newConfig) {
				if (newConfig == null) {
					return;
				}
				int next = newConfig.orientation;
				String id = OrientationGesture.idForChange(lastOrientation, next);
				if (!orientationSeeded) {
					lastOrientation = next;
					orientationSeeded = true;
					return;
				}
				if (id == null) {
					return;
				}
				lastOrientation = next;
				if (gesture.getId().equals(id)) {
					count();
				} else {
					verdict.setText("The other way. This reading wants "
							+ ("landscape".equals(gesture.getId())
									? "landscape." : "portrait."));
					verdict.setTextColor(IDLE_COLOR);
				}
			}

			@Override
			public void onLowMemory() {
			}
		};
		try {
			orientationHost.registerComponentCallbacks(orientationCallbacks);
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"SensorProbeDialog.registerOrientation", e);
			orientationCallbacks = null;
		}
		caveat.setText("A system event, so this works on every phone and costs"
				+ " nothing to watch. The way the phone is now is not a fire.");
	}

	/**
	 * Charge as a percent. The crossing that fires lives in the service, so this
	 * screen shows the number rather than passing a verdict — same reason shake
	 * and light show a raw reading.
	 */
	private void startBatteryPercent() {
		readingIsMomentary = false;
		reading.setText("\u2014");
		verdict.setText("Watching charge. Crossing the threshold fires, not the"
				+ " number sitting still.");
		final IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(final Context context, final Intent intent) {
				if (intent == null) {
					return;
				}
				int level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
				int scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
				if (scale <= 0 || level < 0) {
					return;
				}
				int percent = (int) Math.round((level * 100.0) / scale);
				if (percent < 0 || percent > 100) {
					return;
				}
				reading.setText(percent + "%");
			}
		};
		try {
			getContext().registerReceiver(receiver, filter);
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"SensorProbeDialog.registerBattery", e);
			receiver = null;
		}
		caveat.setText("A system event, so this works on every phone. What counts as"
				+ " low is Options \u2192 Device \u2192 Battery low threshold."
				+ " .sensor fire " + gesture.getId()
				+ " tries the trigger without waiting for the charge to move.");
	}

	private static String systemActionFor(final String id) {
		if (id.startsWith("headphones")) {
			return Intent.ACTION_HEADSET_PLUG;
		}
		if (id.startsWith("power")) {
			return Intent.ACTION_BATTERY_CHANGED;
		}
		return "screenon".equals(id) ? Intent.ACTION_SCREEN_ON : Intent.ACTION_SCREEN_OFF;
	}

	/**
	 * Whether this broadcast is the half of the pair we are watching for, and
	 * whether it is a change rather than a restatement.
	 *
	 * <p>Screen on and screen off are separate actions, so arriving at all is
	 * the answer. The two plugs are one action with an extra, and both are
	 * sticky, so the state has to be remembered and compared.
	 */
	private boolean matchesDirection(final Intent intent) {
		String id = gesture.getId();
		boolean nowPlugged;
		boolean wantPlugged;
		if ("headphonesin".equals(id) || "headphonesout".equals(id)) {
			nowPlugged = intent.getIntExtra("state", 0) == 1;
			wantPlugged = "headphonesin".equals(id);
		} else if ("powerin".equals(id) || "powerout".equals(id)) {
			int status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
			nowPlugged = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
					|| status == android.os.BatteryManager.BATTERY_STATUS_FULL;
			wantPlugged = "powerin".equals(id);
		} else {
			return true;
		}
		Boolean before = lastPlugState;
		lastPlugState = Boolean.valueOf(nowPlugged);
		// The first one is where the phone already was. The battery broadcast
		// then repeats on every change of charge level with nothing plugged or
		// unplugged, which is why this asks for a change and not for a state.
		if (before == null || before.booleanValue() == nowPlugged) {
			return false;
		}
		return nowPlugged == wantPlugged;
	}

	/** Pick up, start moving, go still: armed once, re-armed after each firing. */
	private void startOneShot() {
		readingIsMomentary = true;
		reading.setText("Armed");
		verdict.setText("Waiting for the sensor to report.");
		oneShot = new OneShotGestures(getContext(), new OneShotGestures.Sink() {
			@Override
			public void onGesture(final String gestureId) {
				// The trigger sensor callback is not guaranteed to be the main
				// thread, and these are views.
				handler.post(new Runnable() {
					@Override
					public void run() {
						count();
					}
				});
			}
		});
		java.util.HashSet<String> wanted = new java.util.HashSet<String>();
		wanted.add(gesture.getId());
		oneShot.setWanted(wanted);
		caveat.setText("This sensor watches for the movement itself and wakes the"
				+ " phone only when it sees it, so it may take a few seconds and"
				+ " will not react to a small nudge.");
	}

	private void startStreaming(final Sensor sensor, final String provider) {
		Object system = getContext().getSystemService(Context.SENSOR_SERVICE);
		manager = (system instanceof SensorManager) ? (SensorManager) system : null;
		if (manager == null || sensor == null) {
			verdict.setText("This phone would not hand over the sensor.");
			verdict.setTextColor(DEAD_COLOR);
			return;
		}
		listener = new SensorEventListener() {
			@Override
			public void onSensorChanged(final SensorEvent event) {
				if (event == null || event.values == null || event.values.length < 1) {
					return;
				}
				onReading(sensor, provider, event);
			}

			@Override
			public void onAccuracyChanged(final Sensor s, final int accuracy) {
			}
		};
		// GAME for movement, which peaks and is gone in a fraction of a second;
		// NORMAL for everything else, which is a state and not an event.
		int rate = GestureCatalog.BY_LINEAR_ACCELERATION.equals(provider)
				|| GestureCatalog.BY_ACCELEROMETER.equals(provider)
				? SensorManager.SENSOR_DELAY_GAME : SensorManager.SENSOR_DELAY_NORMAL;
		manager.registerListener(listener, sensor, rate, handler);
		caveatFor(provider);
	}

	private void caveatFor(final String provider) {
		if ("shake".equals(gesture.getId())) {
			caveat.setText("How hard a shake has to be is a number you calibrate, and"
					+ " it is kept by the part of the app that does the detecting, so"
					+ " this screen shows the movement rather than passing a verdict."
					+ " Options \u2192 Device \u2192 Calibrate shake sets it from your"
					+ " own shake.");
			return;
		}
		if (GestureCatalog.BY_LIGHT.equals(provider)
				&& gesture.getId().startsWith("got")) {
			caveat.setText("What counts as dark and as bright is calibrated, and kept"
					+ " by the part of the app that does the detecting, so this screen"
					+ " shows the lux rather than passing a verdict. Options \u2192"
					+ " Device \u2192 Calibrate light sets it from where you play.");
			return;
		}
		if (GestureCatalog.BY_PROXIMITY.equals(provider)) {
			caveat.setText("A wave and a cover come from the same sensor and are told"
					+ " apart by how long your hand stays: under "
					+ (GestureTiming.COVER_MILLIS / 1000) + " second is a wave, longer"
					+ " is a cover.");
		}
	}

	private void onReading(final Sensor sensor, final String provider,
			final SensorEvent event) {
		if (GestureCatalog.BY_PROXIMITY.equals(provider)) {
			onProximity(sensor, event.values[0]);
			return;
		}
		if (GestureCatalog.BY_LIGHT.equals(provider)) {
			reading.setText(String.format(java.util.Locale.US, "%.0f lux",
					event.values[0]));
			// The light sensor is also what stands in for a wave on a phone with
			// no proximity sensor, and then the useful instruction is different.
			verdict.setText("wave".equals(gesture.getId())
					? "This phone has no proximity sensor, so the light sensor answers"
						+ " for a wave. Wave over the top and watch the lux drop."
					: "The light sensor is reporting. Cover it, or take the phone"
						+ " somewhere brighter, and watch the number move.");
			return;
		}
		if (event.values.length < 3) {
			return;
		}
		if (GestureCatalog.BY_GRAVITY.equals(provider)
				|| "facedown".equals(gesture.getId()) || "faceup".equals(gesture.getId())) {
			onFacing(event.values[2]);
			return;
		}
		onMotion(provider, event);
	}

	private void onProximity(final Sensor sensor, final float value) {
		boolean covered = DeviceState.isCovered(value, sensor.getMaximumRange());
		reading.setText(covered ? "Near" : "Far");
		if (!proximitySeeded) {
			// The first reading arrives on registration and is where your hand
			// already was, not something you just did.
			proximitySeeded = true;
			verdict.setText("The proximity sensor is reporting. Wave over the top of"
					+ " the screen.");
			return;
		}
		if (covered) {
			coveredSince = SystemClock.elapsedRealtime();
			coverFired = false;
			if ("cover".equals(gesture.getId())) {
				pendingCover = new Runnable() {
					@Override
					public void run() {
						pendingCover = null;
						coverFired = true;
						count();
					}
				};
				handler.postDelayed(pendingCover, GestureTiming.COVER_MILLIS);
			}
			return;
		}
		if (pendingCover != null) {
			handler.removeCallbacks(pendingCover);
			pendingCover = null;
		}
		long held = coveredSince == 0L ? -1L : SystemClock.elapsedRealtime() - coveredSince;
		coveredSince = 0L;
		String was = GestureTiming.classifyRelease(held);
		if ("wave".equals(gesture.getId()) && "wave".equals(was)) {
			count();
			return;
		}
		// A cover that has already been seen says so. Reporting how long the
		// hand stayed on the way out would paint over the answer.
		if (held >= 0L && !coverFired) {
			verdict.setText(String.format(java.util.Locale.US,
					"Your hand was there for %.1f s \u2014 %s.", held / 1000.0,
					was == null ? "long enough to be a cover, not a wave"
							: "short enough to be a wave, not a cover"));
			verdict.setTextColor(IDLE_COLOR);
		}
	}

	/**
	 * The live reading is the raw classification; "seen it" is the detector's.
	 *
	 * <p>Those are not the same moment and the gap is the point. A phone that
	 * is already face down when this opens reads "Face down" straight away and
	 * has fired nothing, because settling out of unknown is where the phone was
	 * lying rather than something anybody did. Saying "seen it" for it would be
	 * the probe disagreeing with what actually happens.
	 */
	private void onFacing(final float z) {
		String live = DeviceState.classifyFacing(z);
		reading.setText(DeviceState.UP.equals(live) ? "Face up"
				: DeviceState.DOWN.equals(live) ? "Face down" : "Neither");
		if (!facingDetector.accept(z, SystemClock.elapsedRealtime())) {
			return;
		}
		if (gesture.getId().equals(facingDetector.getGesture())) {
			count();
			return;
		}
		String settled = facingDetector.getFacing();
		verdict.setText(DeviceState.UNKNOWN.equals(settled)
				? "Not flat enough either way. Lay it on a table."
				: facingDetector.getGesture() == null
					? "That is where it was already lying. Turn it over and back."
					: "The other way up. This reading wants the other side.");
		verdict.setTextColor(IDLE_COLOR);
	}

	private void onMotion(final String provider, final SensorEvent event) {
		double magnitude;
		if (GestureCatalog.BY_ACCELEROMETER.equals(provider)) {
			magnitude = MotionStats.gravityRemoved(event.values[0], event.values[1],
					event.values[2], SensorManager.GRAVITY_EARTH);
		} else {
			magnitude = Math.sqrt((event.values[0] * event.values[0])
					+ (event.values[1] * event.values[1])
					+ (event.values[2] * event.values[2]));
		}
		if (magnitude > peak) {
			peak = magnitude;
		}
		reading.setText(String.format(java.util.Locale.US, "%.1f m/s\u00B2  (peak %.1f)",
				magnitude, peak));
		verdict.setText("The movement sensor is reporting. Shake the phone and watch"
				+ " the peak.");
	}

	/** One sighting: the whole point of the screen, so it is said loudly. */
	private void count() {
		seen++;
		verdict.setTextColor(SEEN_COLOR);
		verdict.setText(seen == 1
				? "Seen it. This phone can do this reading."
				: "Seen it \u2014 " + seen + " times.");
		if (readingIsMomentary) {
			reading.setText("Seen");
		}
	}

	private void runTheActions() {
		String said;
		try {
			service.sendData((".sensor fire " + gesture.getId() + "\r\n").getBytes("UTF-8"));
			said = "Ran what is set up for " + gesture.getId()
					+ ". It shows in the game window.";
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"SensorProbeDialog.runTheActions", e);
			said = "Could not reach the connection to run that.";
		}
		android.widget.Toast.makeText(getContext(), said,
				android.widget.Toast.LENGTH_SHORT).show();
	}

	private static String lowerFirst(final String text) {
		if (text == null || text.length() == 0) {
			return "";
		}
		return Character.toLowerCase(text.charAt(0)) + text.substring(1);
	}

	@Override
	public void dismiss() {
		// Everything registered here is released here. A sensor listener that
		// outlives the screen that wanted it is the battery drain a player
		// blames on the whole app.
		if (manager != null && listener != null) {
			try {
				manager.unregisterListener(listener);
			} catch (Exception ignored) {
				// The screen is going either way.
			}
		}
		listener = null;
		if (receiver != null) {
			try {
				getContext().unregisterReceiver(receiver);
			} catch (Exception ignored) {
				// Never registered, or already gone.
			}
			receiver = null;
		}
		if (orientationCallbacks != null && orientationHost != null) {
			try {
				orientationHost.unregisterComponentCallbacks(orientationCallbacks);
			} catch (Exception ignored) {
				// Never registered, or already gone.
			}
			orientationCallbacks = null;
			orientationHost = null;
		}
		if (oneShot != null) {
			oneShot.stopAll();
			oneShot = null;
		}
		handler.removeCallbacksAndMessages(null);
		pendingCover = null;
		super.dismiss();
	}
}

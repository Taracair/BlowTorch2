package com.resurrection.blowtorch2.lib.service.sensor;

import java.util.Map;

import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

/**
 * Keeps {@link DeviceState} up to date and pushes it into the worlds that asked
 * for it.
 *
 * <p><b>Where this runs.</b> In the service process, alongside the responders —
 * measured on 8 Aug 2026, not assumed: {@code .probe sensors shake} reported
 * {@code registration: accepted in the service process (:stellar)} and 605
 * readings in ten seconds. So there is no binder crossing here and no dependence
 * on a window being open.
 *
 * <p><b>What it costs.</b> The three broadcasts are free — the system sends them
 * whether we listen or not, and none of them holds a wake lock. Proximity is a
 * registered sensor and therefore is not free, so it is registered only while at
 * least one connection has the setting on, and released the moment none does. A
 * listener that outlives its reason is the battery drain a player blames on the
 * whole app.
 */
public final class DeviceStateWatcher {

	private final Context context;
	private final DeviceState state = new DeviceState();
	private final Handler handler = new Handler(Looper.getMainLooper());

	/**
	 * How long a hand may sit over the screen and still count as a wave rather
	 * than a deliberate cover. Told apart by time, not by how hard the gesture
	 * was done — the one distinction a proximity sensor can make reliably.
	 */
	private static final long WAVE_MAX_MILLIS = 900L;
	/** How long a hand must stay put before it is a cover. */
	private static final long COVER_MILLIS = 1000L;
	/**
	 * How hard a shake has to be, in m/s2.
	 *
	 * <p><b>Provisional.</b> Measured on one phone on 8 Aug 2026 — a shake
	 * peaked at 27.7 and the quiet baseline at 10.4 — but both runs were
	 * simulated at a desk, and a real walk with the phone in a pocket has never
	 * been measured. Calibration per device is the step that replaces this.
	 */
	private static final float SHAKE_THRESHOLD = 15.0f;
	/** Without a dead time one shake of the wrist fires four times. */
	private static final long SHAKE_DEAD_MILLIS = 500L;

	private boolean receiverRegistered;
	private boolean sensorRegistered;
	private boolean motionRegistered;
	private SensorManager sensorManager;
	private Sensor proximity;
	private Sensor motion;
	private boolean motionHasGravity;
	private long coveredSince;
	private long lastShakeAt;
	private Runnable pendingCover;

	/** The connections to serve; supplied by the service, never held. */
	public interface Audience {
		/** Live connections that asked for device.* variables, possibly empty. */
		Iterable<Connection> listeners();

		/** Live connections with at least one enabled gesture trigger. */
		Iterable<Connection> gestureListeners();
	}

	private final Audience audience;

	public DeviceStateWatcher(final Context context, final Audience audience) {
		this.context = context;
		this.audience = audience;
	}

	private final BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context ignored, final Intent intent) {
			if (intent == null || intent.getAction() == null) {
				return;
			}
			boolean changed = false;
			String action = intent.getAction();
			if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
				changed = state.setHeadphones(intent.getIntExtra("state", 0) == 1);
			} else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
				int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
				changed = state.setCharging(status == BatteryManager.BATTERY_STATUS_CHARGING
						|| status == BatteryManager.BATTERY_STATUS_FULL);
				changed |= state.setBatteryPercent(
						intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
						intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1));
			} else if (Intent.ACTION_SCREEN_ON.equals(action)) {
				changed = state.setScreenOn(true);
			} else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
				changed = state.setScreenOn(false);
			}
			if (changed) {
				push();
			}
		}
	};

	private final SensorEventListener proximityListener = new SensorEventListener() {
		@Override
		public void onSensorChanged(final SensorEvent event) {
			if (event == null || event.values == null || event.values.length < 1
					|| proximity == null) {
				return;
			}
			boolean covered = DeviceState.isCovered(event.values[0],
					proximity.getMaximumRange());
			if (state.setCovered(covered)) {
				push();
				onCoverChanged(covered);
			}
		}

		@Override
		public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
		}
	};

	/**
	 * Start or stop watching, to match what the worlds currently want.
	 *
	 * <p>Called whenever a connection's settings are read or change, so turning
	 * the option off releases the sensor without the player restarting anything.
	 */
	public synchronized void refresh() {
		boolean wantsState = false;
		for (Connection c : audience.listeners()) {
			if (c != null) {
				wantsState = true;
				break;
			}
		}
		// Which sensors any world is actually waiting on. A gesture nobody has a
		// trigger for costs nothing, which is the whole reason to ask.
		java.util.Set<String> providers = new java.util.LinkedHashSet<String>();
		for (Connection c : audience.gestureListeners()) {
			if (c == null) {
				continue;
			}
			for (String id : c.enabledGestureIds()) {
				GestureCatalog.Gesture g = GestureCatalog.byId(id);
				if (g != null) {
					providers.addAll(g.getProviders());
				}
			}
		}
		boolean wantsProximity = wantsState
				|| providers.contains(GestureCatalog.BY_PROXIMITY);
		boolean wantsMotion = providers.contains(GestureCatalog.BY_LINEAR_ACCELERATION)
				|| providers.contains(GestureCatalog.BY_ACCELEROMETER);

		if (wantsState) {
			startBroadcasts();
		} else {
			stopBroadcasts();
		}
		if (wantsProximity) {
			startProximity();
		} else {
			stopProximity();
		}
		if (wantsMotion) {
			startMotion();
		} else {
			stopMotion();
		}
	}

	private void startBroadcasts() {
		if (!receiverRegistered) {
			try {
				IntentFilter filter = new IntentFilter();
				filter.addAction(Intent.ACTION_HEADSET_PLUG);
				filter.addAction(Intent.ACTION_BATTERY_CHANGED);
				filter.addAction(Intent.ACTION_SCREEN_ON);
				filter.addAction(Intent.ACTION_SCREEN_OFF);
				// ACTION_BATTERY_CHANGED is sticky, so registering also delivers
				// the current charge immediately — the state is populated without
				// waiting for the phone to be plugged in.
				context.registerReceiver(receiver, filter);
				receiverRegistered = true;
				seedFromSystem();
			} catch (Exception e) {
				BlowTorchLogger.logMinor("DeviceStateWatcher.registerReceiver", e);
			}
		}
	}

	private SensorManager manager() {
		if (sensorManager == null) {
			Object service = context.getSystemService(Context.SENSOR_SERVICE);
			sensorManager = (service instanceof SensorManager)
					? (SensorManager) service : null;
		}
		return sensorManager;
	}

	private void startProximity() {
		if (sensorRegistered) {
			return;
		}
		SensorManager m = manager();
		if (m == null) {
			return;
		}
		if (proximity == null) {
			proximity = m.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		}
		// No proximity sensor is a normal answer, not a failure: device.covered
		// stays absent, a condition testing it reads false, and the wave gesture
		// is reported as unavailable rather than silently never firing.
		if (proximity == null) {
			return;
		}
		try {
			sensorRegistered = m.registerListener(proximityListener, proximity,
					SensorManager.SENSOR_DELAY_NORMAL, handler);
		} catch (Exception e) {
			BlowTorchLogger.logMinor("DeviceStateWatcher.registerProximity", e);
		}
	}

	private void startMotion() {
		if (motionRegistered) {
			return;
		}
		SensorManager m = manager();
		if (m == null) {
			return;
		}
		if (motion == null) {
			motion = m.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
			motionHasGravity = false;
			if (motion == null) {
				motion = m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
				motionHasGravity = true;
			}
		}
		if (motion == null) {
			return;
		}
		try {
			// GAME rather than NORMAL: a shake lasts a fraction of a second and
			// NORMAL can sample slowly enough to miss the peak entirely.
			motionRegistered = m.registerListener(motionListener, motion,
					SensorManager.SENSOR_DELAY_GAME, handler);
		} catch (Exception e) {
			BlowTorchLogger.logMinor("DeviceStateWatcher.registerMotion", e);
		}
	}

	private void stopBroadcasts() {
		if (!receiverRegistered) {
			return;
		}
		try {
			context.unregisterReceiver(receiver);
		} catch (Exception e) {
			BlowTorchLogger.logMinor("DeviceStateWatcher.unregisterReceiver", e);
		}
		receiverRegistered = false;
	}

	private void stopProximity() {
		if (!sensorRegistered || sensorManager == null) {
			return;
		}
		try {
			sensorManager.unregisterListener(proximityListener);
		} catch (Exception e) {
			BlowTorchLogger.logMinor("DeviceStateWatcher.unregisterProximity", e);
		}
		sensorRegistered = false;
		if (pendingCover != null) {
			handler.removeCallbacks(pendingCover);
			pendingCover = null;
		}
		coveredSince = 0L;
	}

	private void stopMotion() {
		if (!motionRegistered || sensorManager == null) {
			return;
		}
		try {
			sensorManager.unregisterListener(motionListener);
		} catch (Exception e) {
			BlowTorchLogger.logMinor("DeviceStateWatcher.unregisterMotion", e);
		}
		motionRegistered = false;
	}

	/**
	 * A hand arrived over the screen, or left it.
	 *
	 * <p>Two gestures come out of one sensor, and they are told apart by how
	 * long the hand stayed: gone again quickly is a wave, still there after a
	 * second is a cover. Doing it by time rather than by distance is what makes
	 * the pair safe — a proximity sensor reports "near" and "far" and little
	 * else, so there is no second reading to get wrong.
	 */
	private void onCoverChanged(final boolean covered) {
		if (covered) {
			coveredSince = android.os.SystemClock.elapsedRealtime();
			pendingCover = new Runnable() {
				@Override
				public void run() {
					// Still covered when this arrives: a deliberate hold.
					pendingCover = null;
					fire("cover");
				}
			};
			handler.postDelayed(pendingCover, COVER_MILLIS);
			return;
		}
		if (pendingCover != null) {
			handler.removeCallbacks(pendingCover);
			pendingCover = null;
		}
		long held = android.os.SystemClock.elapsedRealtime() - coveredSince;
		if (coveredSince > 0L && held <= WAVE_MAX_MILLIS) {
			fire("wave");
		}
		coveredSince = 0L;
	}

	private final SensorEventListener motionListener = new SensorEventListener() {
		@Override
		public void onSensorChanged(final SensorEvent event) {
			if (event == null || event.values == null || event.values.length < 3) {
				return;
			}
			double magnitude;
			if (motionHasGravity) {
				magnitude = MotionStats.gravityRemoved(event.values[0], event.values[1],
						event.values[2], SensorManager.GRAVITY_EARTH);
			} else {
				magnitude = Math.sqrt((event.values[0] * event.values[0])
						+ (event.values[1] * event.values[1])
						+ (event.values[2] * event.values[2]));
			}
			if (magnitude < SHAKE_THRESHOLD) {
				return;
			}
			long now = android.os.SystemClock.elapsedRealtime();
			if (now - lastShakeAt < SHAKE_DEAD_MILLIS) {
				return;
			}
			lastShakeAt = now;
			fire("shake");
		}

		@Override
		public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
		}
	};

	/**
	 * Hand the gesture to every world waiting for it.
	 *
	 * <p>{@code fireDeviceGesture} posts onto the connection's own handler, so
	 * nothing here touches the trigger system from a sensor callback.
	 */
	private void fire(final String gestureId) {
		for (Connection c : audience.gestureListeners()) {
			if (c != null) {
				c.fireDeviceGesture(gestureId);
			}
		}
	}

	/**
	 * Ask the system for what it will not tell us until it changes.
	 *
	 * <p>{@code ACTION_BATTERY_CHANGED} is sticky, so charge arrives the moment
	 * we register. Screen and headphones are not: without this, both would stay
	 * unset until the player next locked the phone or plugged something in — and
	 * "unset" means "this phone cannot tell", which would be a lie.
	 */
	private void seedFromSystem() {
		try {
			Object power = context.getSystemService(Context.POWER_SERVICE);
			if (power instanceof PowerManager) {
				state.setScreenOn(((PowerManager) power).isInteractive());
			}
		} catch (Exception e) {
			BlowTorchLogger.logMinor("DeviceStateWatcher.seedScreen", e);
		}
		try {
			Object audio = context.getSystemService(Context.AUDIO_SERVICE);
			if (audio instanceof AudioManager) {
				boolean plugged = false;
				AudioDeviceInfo[] outputs =
						((AudioManager) audio).getDevices(AudioManager.GET_DEVICES_OUTPUTS);
				if (outputs != null) {
					for (AudioDeviceInfo info : outputs) {
						int type = info.getType();
						if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET
								|| type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
								|| type == AudioDeviceInfo.TYPE_USB_HEADSET) {
							plugged = true;
							break;
						}
					}
				}
				state.setHeadphones(plugged);
			}
		} catch (Exception e) {
			BlowTorchLogger.logMinor("DeviceStateWatcher.seedHeadphones", e);
		}
		// Proximity has no equivalent: it is on-change and reports as soon as it
		// is registered, so device.covered fills itself in.
	}

	/** Release everything. Safe to call when nothing is registered. */
	public synchronized void stopWatching() {
		stopBroadcasts();
		stopProximity();
		stopMotion();
	}

	/** Write everything known into every connection that wants it. */
	public void push() {
		Map<String, String> snapshot = state.snapshot();
		for (Connection c : audience.listeners()) {
			if (c == null) {
				continue;
			}
			for (Map.Entry<String, String> e : snapshot.entrySet()) {
				// The store is synchronized, and conditions read it on the
				// connection thread. Writing from here is safe; writing a value
				// the store already has is not, it is just waste — hence the
				// change checks upstream.
				c.getSessionVariables().set(e.getKey(), e.getValue());
			}
		}
	}

	/** The reading behind {@code .probe sensors state}. */
	public String report() {
		return state.report();
	}

	/** Whether the proximity half is live, for the report. */
	public synchronized boolean isProximityWatched() {
		return sensorRegistered;
	}
}

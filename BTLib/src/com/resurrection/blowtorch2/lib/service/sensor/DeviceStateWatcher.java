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
	/** How long a hand must stay put before it is a cover. */
	private static final long COVER_MILLIS = 1000L;
	/**
	 * Anything shorter than a cover is a wave. Deliberately the same number:
	 * with a gap between the two, a hand held for exactly that long would be
	 * neither, and the gesture would fail intermittently for no visible reason.
	 */
	private static final long WAVE_MAX_MILLIS = COVER_MILLIS;
	/**
	 * How hard a shake has to be, in m/s2.
	 *
	 * <p><b>Provisional.</b> Measured on one phone on 8 Aug 2026 — a shake
	 * peaked at 27.7 and the quiet baseline at 10.4 — but both runs were
	 * simulated at a desk, and a real walk with the phone in a pocket has never
	 * been measured. Calibration per device is the step that replaces this.
	 */
	private float shakeThreshold = GestureTuning.DEFAULT_SHAKE;
	/** Without a dead time one shake of the wrist fires four times. */
	private static final long SHAKE_DEAD_MILLIS = 500L;
	/**
	 * How much of gravity has to be along the screen's axis before the phone
	 * counts as lying flat, in m/s2 out of about 9.8. Two thresholds rather than
	 * one: a phone standing on edge, or being carried, is neither face up nor
	 * face down, and should not flip between the two as it wobbles.
	 */
	private static final float FLAT_ENOUGH = 8.0f;
	/**
	 * How long it has to stay that way. Turning a phone over passes through
	 * every angle on the way, and without this the trip would fire on the way
	 * down as well as at the end of it.
	 */
	private static final long FACING_SETTLE_MILLIS = 400L;

	private boolean receiverRegistered;
	private boolean sensorRegistered;
	private boolean motionRegistered;
	private SensorManager sensorManager;
	private Sensor proximity;
	private Sensor motion;
	private boolean motionHasGravity;
	private boolean proximitySeeded;
	private boolean facingRegistered;
	private Sensor facingSensor;
	private String facing = DeviceState.UNKNOWN;
	private String facingCandidate = DeviceState.UNKNOWN;
	private long facingCandidateSince;
	/**
	 * Broadcasts already seen once. The interesting ones are sticky: registering
	 * delivers the current charge and jack state at once, and treating that as a
	 * change would fire "the charger was plugged in" every time the app starts.
	 * The same hole the first proximity reading had.
	 */
	private final java.util.HashSet<String> seededActions = new java.util.HashSet<String>();
	/** Pick-up, significant motion and stationary: armed one at a time, re-armed
	 * after each firing. Kept apart because their API is not the streaming one. */
	private OneShotGestures oneShot;
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
			boolean firstOfItsKind = seededActions.add(action);
			if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
				boolean plugged = intent.getIntExtra("state", 0) == 1;
				changed = state.setHeadphones(plugged);
				if (changed && !firstOfItsKind) {
					fire(plugged ? "headphonesin" : "headphonesout");
				}
			} else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
				int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
				boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
						|| status == BatteryManager.BATTERY_STATUS_FULL;
				changed = state.setCharging(charging);
				if (changed && !firstOfItsKind) {
					fire(charging ? "powerin" : "powerout");
				}
				changed |= state.setBatteryPercent(
						intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
						intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1));
			} else if (Intent.ACTION_SCREEN_ON.equals(action)) {
				// Only on a real change, like the two above. Registration seeds
				// the screen state from PowerManager, so a broadcast arriving
				// straight after for the state we already recorded would
				// otherwise announce a gesture nobody made.
				changed = state.setScreenOn(true);
				if (changed) {
					fire("screenon");
				}
			} else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
				changed = state.setScreenOn(false);
				if (changed) {
					fire("screenoff");
				}
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
			boolean changed = state.setCovered(covered);
			if (!proximitySeeded) {
				// Proximity is on-change: registering delivers the current value
				// straight away, and that first reading is not a gesture. A phone
				// lying face down or sitting in a pocket when the sensor is picked
				// up would otherwise "cover" a second later and send whatever the
				// player bound to it, with nobody having moved a hand.
				proximitySeeded = true;
				if (changed) {
					push();
				}
				return;
			}
			if (changed) {
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
	/** What the last refresh decided, so an unchanged one costs almost nothing. */
	private java.util.Set<String> lastWanted;
	private boolean lastWantedState;

	public synchronized void refresh() {
		boolean wantsState = false;
		for (Connection c : audience.listeners()) {
			if (c != null) {
				wantsState = true;
				break;
			}
		}
		// Which gestures any world is actually waiting on. Asked per gesture and
		// not per provider name: shake falls back from linear acceleration to the
		// raw accelerometer, and reading that fallback as "someone wants the
		// facing sensor" would listen to the wrong thing on a phone that has no
		// linear acceleration sensor.
		java.util.Set<String> wanted = new java.util.LinkedHashSet<String>();
		for (Connection c : audience.gestureListeners()) {
			if (c != null) {
				wanted.addAll(c.enabledGestureIds());
			}
		}
		boolean wantsProximity = wantsState
				|| wanted.contains("wave") || wanted.contains("cover");
		boolean wantsMotion = wanted.contains("shake");
		boolean wantsFacing = wanted.contains("facedown") || wanted.contains("faceup");
		// The system events ride on the broadcast receiver, which is otherwise
		// registered only for the device.* variables.
		for (String id : wanted) {
			GestureCatalog.Gesture g = GestureCatalog.byId(id);
			if (g != null && g.getProviders().contains(GestureCatalog.BY_SYSTEM)) {
				wantsState = true;
				break;
			}
		}

		// buildTriggerSystem calls this, and that can run several times a second
		// while a profile enables and disables triggers — see docs/HANDOFF.md on
		// what else rides that path. Nothing below changes unless the answer
		// changed, so leave immediately when it did not.
		if (lastWanted != null && lastWantedState == wantsState
				&& lastWanted.equals(wanted)) {
			return;
		}
		lastWanted = new java.util.LinkedHashSet<String>(wanted);
		lastWantedState = wantsState;

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
		if (wantsFacing) {
			startFacing();
		} else {
			stopFacing();
		}
		if (oneShot == null) {
			oneShot = new OneShotGestures(context, new OneShotGestures.Sink() {
				@Override
				public void onGesture(final String gestureId) {
					fire(gestureId);
				}
			});
		}
		oneShot.setWanted(wanted);
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
		proximitySeeded = false;
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
		// Read at registration, so a calibration takes effect the moment the
		// gesture is next picked up rather than at the next restart.
		shakeThreshold = GestureTuning.shakeThreshold(context);
		try {
			// GAME rather than NORMAL: a shake lasts a fraction of a second and
			// NORMAL can sample slowly enough to miss the peak entirely.
			motionRegistered = m.registerListener(motionListener, motion,
					SensorManager.SENSOR_DELAY_GAME, handler);
		} catch (Exception e) {
			BlowTorchLogger.logMinor("DeviceStateWatcher.registerMotion", e);
		}
	}

	private void startFacing() {
		if (facingRegistered) {
			return;
		}
		SensorManager m = manager();
		if (m == null) {
			return;
		}
		if (facingSensor == null) {
			facingSensor = m.getDefaultSensor(Sensor.TYPE_GRAVITY);
			if (facingSensor == null) {
				// The raw accelerometer reads gravity too when the phone is
				// still, which is exactly when this gesture is asked about.
				facingSensor = m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			}
		}
		if (facingSensor == null) {
			return;
		}
		facing = DeviceState.UNKNOWN;
		facingCandidate = DeviceState.UNKNOWN;
		try {
			// NORMAL, not GAME: putting a phone down is not a fast event, and
			// this one may stay registered for hours.
			facingRegistered = m.registerListener(facingListener, facingSensor,
					SensorManager.SENSOR_DELAY_NORMAL, handler);
		} catch (Exception e) {
			BlowTorchLogger.logMinor("DeviceStateWatcher.registerFacing", e);
		}
	}

	private void stopFacing() {
		if (!facingRegistered || sensorManager == null) {
			return;
		}
		try {
			sensorManager.unregisterListener(facingListener);
		} catch (Exception e) {
			BlowTorchLogger.logMinor("DeviceStateWatcher.unregisterFacing", e);
		}
		facingRegistered = false;
		facing = DeviceState.UNKNOWN;
		facingCandidate = DeviceState.UNKNOWN;
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
		seededActions.clear();
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
		proximitySeeded = false;
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
			if (magnitude < shakeThreshold) {
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
	 * Which way up the phone is now, from gravity along the screen's axis.
	 *
	 * <p>Face up is about +9.8 on Z, face down about -9.8, and everything in
	 * between — held in a hand, standing in a dock, carried in a pocket — is
	 * neither. Reporting "neither" rather than guessing is what keeps a phone
	 * being walked with from flipping between the two gestures all the way to
	 * the shop.
	 */
	private final SensorEventListener facingListener = new SensorEventListener() {
		@Override
		public void onSensorChanged(final SensorEvent event) {
			if (event == null || event.values == null || event.values.length < 3) {
				return;
			}
			float z = event.values[2];
			String now;
			if (z >= FLAT_ENOUGH) {
				now = DeviceState.UP;
			} else if (z <= -FLAT_ENOUGH) {
				now = DeviceState.DOWN;
			} else {
				now = DeviceState.UNKNOWN;
			}
			long elapsed = android.os.SystemClock.elapsedRealtime();
			if (!now.equals(facingCandidate)) {
				facingCandidate = now;
				facingCandidateSince = elapsed;
				return;
			}
			if (now.equals(facing)
					|| (elapsed - facingCandidateSince) < FACING_SETTLE_MILLIS) {
				return;
			}
			String previous = facing;
			facing = now;
			if (state.setFacing(facing)) {
				push();
			}
			// The first reading after registration settles into up or down and
			// is not a gesture: the phone was already lying there. Only a real
			// change of side counts, which is why this needs the previous value
			// and not just the new one.
			if (DeviceState.UNKNOWN.equals(previous)) {
				return;
			}
			if (DeviceState.DOWN.equals(facing)) {
				fire("facedown");
			} else if (DeviceState.UP.equals(facing)) {
				fire("faceup");
			}
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
			// Each world answers for itself: the gate is a per-world setting, and
			// two worlds may disagree about whether a shake in a pocket counts.
			if (c != null && c.allowsGestureNow(gestureId)) {
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
		stopFacing();
		if (oneShot != null) {
			oneShot.stopAll();
		}
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

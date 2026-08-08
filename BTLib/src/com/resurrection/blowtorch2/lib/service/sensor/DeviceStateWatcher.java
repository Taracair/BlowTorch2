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
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;

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

	private boolean receiverRegistered;
	private boolean sensorRegistered;
	private SensorManager sensorManager;
	private Sensor proximity;

	/** The connections to push into; supplied by the service, never held. */
	public interface Audience {
		/** Live connections that have asked for device state, possibly empty. */
		Iterable<Connection> listeners();
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
			if (state.setCovered(DeviceState.isCovered(event.values[0],
					proximity.getMaximumRange()))) {
				push();
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
		boolean wanted = false;
		for (Connection c : audience.listeners()) {
			if (c != null) {
				wanted = true;
				break;
			}
		}
		if (wanted) {
			startWatching();
		} else {
			stopWatching();
		}
	}

	private void startWatching() {
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
			} catch (Exception e) {
				BlowTorchLogger.logMinor("DeviceStateWatcher.registerReceiver", e);
			}
		}
		if (!sensorRegistered) {
			if (sensorManager == null) {
				Object service = context.getSystemService(Context.SENSOR_SERVICE);
				sensorManager = (service instanceof SensorManager)
						? (SensorManager) service : null;
			}
			if (sensorManager != null && proximity == null) {
				proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
			}
			// No proximity sensor is a normal answer, not a failure: device.covered
			// stays absent and a condition testing it reads false.
			if (sensorManager != null && proximity != null) {
				try {
					sensorRegistered = sensorManager.registerListener(proximityListener,
							proximity, SensorManager.SENSOR_DELAY_NORMAL, handler);
				} catch (Exception e) {
					BlowTorchLogger.logMinor("DeviceStateWatcher.registerListener", e);
				}
			}
		}
	}

	/** Release everything. Safe to call when nothing is registered. */
	public synchronized void stopWatching() {
		if (receiverRegistered) {
			try {
				context.unregisterReceiver(receiver);
			} catch (Exception e) {
				BlowTorchLogger.logMinor("DeviceStateWatcher.unregisterReceiver", e);
			}
			receiverRegistered = false;
		}
		if (sensorRegistered && sensorManager != null) {
			try {
				sensorManager.unregisterListener(proximityListener);
			} catch (Exception e) {
				BlowTorchLogger.logMinor("DeviceStateWatcher.unregisterListener", e);
			}
			sensorRegistered = false;
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

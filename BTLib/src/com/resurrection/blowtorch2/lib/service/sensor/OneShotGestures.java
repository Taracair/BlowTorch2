package com.resurrection.blowtorch2.lib.service.sensor;

import java.util.HashMap;
import java.util.Map;

import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;

/**
 * Significant-motion / pickup / still: {@code requestTriggerSensor}, then
 * re-arm after each fire. Treating them as a streaming sensor works once.
 */
public final class OneShotGestures {

	/** What to do when one of these fires. */
	public interface Sink {
		void onGesture(String gestureId);
	}

	private final Context context;
	private final Sink sink;
	private final Map<String, TriggerEventListener> armed =
			new HashMap<String, TriggerEventListener>();
	private final Map<String, Sensor> sensors = new HashMap<String, Sensor>();
	private SensorManager sensorManager;

	public OneShotGestures(final Context context, final Sink sink) {
		this.context = context;
		this.sink = sink;
	}

	/**
	 * Arm exactly the gestures in this set, and disarm anything else.
	 *
	 * @param wanted gesture ids from {@link GestureCatalog}; ones this class does
	 *        not handle are ignored.
	 */
	public synchronized void setWanted(final java.util.Set<String> wanted) {
		for (GestureCatalog.Gesture g : GestureCatalog.all()) {
			if (!isOneShot(g)) {
				continue;
			}
			boolean shouldBeArmed = wanted != null && wanted.contains(g.getId());
			if (shouldBeArmed) {
				arm(g);
			} else {
				disarm(g.getId());
			}
		}
	}

	/** Let everything go. */
	public synchronized void stopAll() {
		for (String id : new java.util.ArrayList<String>(armed.keySet())) {
			disarm(id);
		}
	}

	private static boolean isOneShot(final GestureCatalog.Gesture g) {
		return g.getProviders().contains(GestureCatalog.BY_PICKUP_SENSOR)
				|| g.getProviders().contains(GestureCatalog.BY_SIGNIFICANT_MOTION)
				|| g.getProviders().contains(GestureCatalog.BY_STATIONARY);
	}

	private void arm(final GestureCatalog.Gesture gesture) {
		if (armed.containsKey(gesture.getId())) {
			return;
		}
		SensorManager manager = manager();
		if (manager == null) {
			return;
		}
		GestureAvailability.Resolution resolution =
				GestureAvailability.resolve(context, gesture);
		final Sensor sensor = resolution.getSensor();
		// A phone without the sensor is a normal answer: the gesture is listed as
		// unavailable and simply never fires.
		if (sensor == null) {
			return;
		}
		final String id = gesture.getId();
		TriggerEventListener listener = new TriggerEventListener() {
			@Override
			public void onTrigger(final TriggerEvent event) {
				// Disarmed by the firing itself. Re-arm before handing the
				// gesture on, so a slow responder cannot swallow the next one.
				rearm(id);
				if (sink != null) {
					sink.onGesture(id);
				}
			}
		};
		try {
			if (manager.requestTriggerSensor(listener, sensor)) {
				armed.put(id, listener);
				sensors.put(id, sensor);
			}
		} catch (Exception e) {
			BlowTorchLogger.logMinor("OneShotGestures.arm " + id, e);
		}
	}

	private synchronized void rearm(final String id) {
		TriggerEventListener listener = armed.get(id);
		Sensor sensor = sensors.get(id);
		SensorManager manager = manager();
		if (listener == null || sensor == null || manager == null) {
			return;
		}
		try {
			manager.requestTriggerSensor(listener, sensor);
		} catch (Exception e) {
			BlowTorchLogger.logMinor("OneShotGestures.rearm " + id, e);
		}
	}

	private void disarm(final String id) {
		TriggerEventListener listener = armed.remove(id);
		Sensor sensor = sensors.remove(id);
		SensorManager manager = manager();
		if (listener == null || sensor == null || manager == null) {
			return;
		}
		try {
			manager.cancelTriggerSensor(listener, sensor);
		} catch (Exception e) {
			BlowTorchLogger.logMinor("OneShotGestures.disarm " + id, e);
		}
	}

	private SensorManager manager() {
		if (sensorManager == null && context != null) {
			Object service = context.getSystemService(Context.SENSOR_SERVICE);
			sensorManager = (service instanceof SensorManager)
					? (SensorManager) service : null;
		}
		return sensorManager;
	}
}

/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

import java.util.HashMap;

import android.os.Message;
import android.os.SystemClock;

import com.resurrection.blowtorch2.lib.service.function.SpecialCommand;
import com.resurrection.blowtorch2.lib.service.plugin.Plugin;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.timer.TimerDuration;

/** Timer CRUD, play/pause/stop, and .timer command action handling for a Connection. */
final class ConnectionTimers {

	/** 1000 mm. */
	private static final double ONE_THOUSAND_MILLIS = 1000.0;

	/** Enum used for the Timer command action ordinals. */
	enum TIMER_ACTION {
		/** Play action.*/
		PLAY,
		/** Pause action. */
		PAUSE,
		/** Reset action.*/
		RESET,
		/** Info action.*/
		INFO,
		/** Stop action. */
		STOP,
		/** Set duration in seconds (from .timer duration). */
		DURATION,
		/** No action. */
		NONE
	}

	private final Connection host;

	ConnectionTimers(final Connection host) {
		this.host = host;
	}

	/** Handle MESSAGE_TIMER* from the connection handler. */
	void handleTimerMessage(final Message msg) {
		switch (msg.what) {
		case Connection.MESSAGE_TIMERSTOP:
			doTimerAction((String) msg.obj, msg.arg2, TIMER_ACTION.STOP);
			break;
		case Connection.MESSAGE_TIMERSTART:
			doTimerAction((String) msg.obj, msg.arg2, TIMER_ACTION.PLAY);
			break;
		case Connection.MESSAGE_TIMERRESET:
			doTimerAction((String) msg.obj, msg.arg2, TIMER_ACTION.RESET);
			break;
		case Connection.MESSAGE_TIMERINFO:
			doTimerAction((String) msg.obj, msg.arg2, TIMER_ACTION.INFO);
			break;
		case Connection.MESSAGE_TIMERPAUSE:
			doTimerAction((String) msg.obj, msg.arg2, TIMER_ACTION.PAUSE);
			break;
		case Connection.MESSAGE_TIMERDURATION:
			doTimerDuration((String) msg.obj, msg.arg1, msg.arg2);
			break;
		default:
			break;
		}
	}

	/** Sets a timer's stored duration in seconds. A running timer keeps running on the
	 ** new length, starting from now; see {@link Plugin#setTimerDuration}. */
	void doTimerDuration(final String name, final int seconds, final int arg2) {
		Plugin timerHost = findTimerHost(name);
		boolean silent = arg2 == 0;
		if (timerHost == null) {
			host.dispatchNoProcess(SpecialCommand.getErrorMessage("Timer command error",
					"No timer with name " + name + " found.").getBytes());
			return;
		}
		if (seconds <= 0) {
			host.dispatchNoProcess(SpecialCommand.getErrorMessage("Timer duration error",
					"Duration must be more than zero seconds.").getBytes());
			return;
		}
		if (!timerHost.setTimerDuration(name, seconds)) {
			host.dispatchNoProcess(SpecialCommand.getErrorMessage("Timer command error",
					"No timer with name " + name + " found.").getBytes());
			return;
		}
		persistTimerSettings();
		if (!silent) {
			host.toast("Timer " + name + ": " + TimerDuration.format(seconds));
		}
	}

	private Plugin findTimerHost(final String name) {
		if (host.mSettings.getSettings().getTimers().containsKey(name)) {
			return host.mSettings;
		}
		for (Plugin p : host.mPlugins) {
			if (p.getSettings().getTimers().containsKey(name)) {
				return p;
			}
		}
		return null;
	}

	/** Work horse method for the timer command.
	 * 
	 * @param obj The name of the timer.
	 * @param arg2 The silent flag (0 = silent, anything else = not silent).
	 * @param action The action that was harvested from the entry point.
	 */
	void doTimerAction(final String obj, final int arg2, final TIMER_ACTION action) {
		//check for valid ordinals.
		boolean found = false;
		Plugin timerHost = findTimerHost(obj);
		if (timerHost != null) {
			found = true;
		}
		boolean silent = false;
		if (arg2 == 0) {
			silent = true;
		}
		
		if (!found) {
			//show error message.
			host.dispatchNoProcess(SpecialCommand.getErrorMessage("Timer command error", "No timer with name " + obj + " found.").getBytes());
		} else {
			switch (action) {
			case PLAY:
				timerHost.startTimer(obj);
				notifyGauges();
				if (!silent) {
					host.toast("Timer " + obj + " started.");
				}
				break;
			case PAUSE:
				timerHost.pauseTimer(obj);
				notifyGauges();
				if (!silent) {
					host.toast("Timer " + obj + " paused.");
				}
				break;
			case RESET:
				timerHost.resetTimer(obj);
				notifyGauges();
				if (!silent) {
					host.toast("Timer " + obj + " reset.");
				}
				break;
			case STOP:
				timerHost.pauseTimer(obj);
				timerHost.resetTimer(obj);
				notifyGauges();
				if (!silent) {
					host.toast("Timer " + obj + " stopped.");
				}
				break;
			case INFO:
				TimerData t = timerHost.getSettings().getTimers().get(obj);
				if (t.isPlaying()) {
					long now = SystemClock.elapsedRealtime();
					long dur = now - t.getStartTime();
					int sec = t.getSeconds() - (int) (dur / ONE_THOUSAND_MILLIS);
					host.toast(obj + ": " + sec + "s");
				} else {
					if (t.getRemainingTime() != t.getSeconds()) {
						int sec = t.getSeconds() - t.getRemainingTime();
						host.toast("Timer " + obj + " is paused, " + sec + " remain.");
					} else {
						host.toast("Timer " + obj + " is not running.");
					}
				}
				break;
			case NONE:
				break;
			default:
				break;
			}
		}
	}

	/** Removes a timer from the target plugin. */
	void deletePluginTimer(final String plugin, final String name) {
		Plugin p = host.mPluginMap.get(plugin);
		if (p != null) {
			p.getSettings().getTimers().remove(name);
			p.getSettings().setDirty(true);
			persistTimerSettings();
		}
	}

	/** Gets a timer from the main settings plugin. */
	TimerData getTimer(final String name) {
		TimerData timer = host.mSettings.getSettings().getTimers().get(name);
		return (timer == null) ? null : timer.copy();
	}

	/** Removes a timer from the main settings plugin. */
	void deleteTimer(final String name) {
		host.mSettings.getSettings().getTimers().remove(name);
		host.mSettings.getSettings().setDirty(true);
		persistTimerSettings();
	}

	/** Gets a timer from the target plugin. */
	TimerData getPluginTimer(final String plugin, final String name) {
		Plugin p = host.mPluginMap.get(plugin);
		if (p != null) {
			TimerData timer = p.getSettings().getTimers().get(name);
			return (timer == null) ? null : timer.copy();
		} else {
			return null;
		}
	}

	/** Adds a timer to the target plugin. */
	void addPluginTimer(final String plugin, final TimerData newtimer) {
		Plugin p = host.mPluginMap.get(plugin);
		if (p != null) {
			newtimer.setRemainingTime(newtimer.getSeconds());
			p.getSettings().getTimers().put(newtimer.getName(), newtimer.copy());
			p.getSettings().setDirty(true);
			persistTimerSettings();
		}
	}

	/** Updates a timer in the target plugin. */
	void updatePluginTimer(final String plugin, final TimerData old,
		final TimerData newtimer) {
		Plugin p = host.mPluginMap.get(plugin);
		if (p != null) {
			applyTimerEdit(p, old, newtimer);
		}

	}

	/** Updates a timer in the main settings plugin. */
	void updateTimer(final TimerData old, final TimerData newtimer) {
		applyTimerEdit(host.mSettings, old, newtimer);
	}

	/** Replaces a timer with its edited version, in whichever plugin owns it.
	 *
	 * Two rules, and they were both got wrong once each:
	 *
	 * The remaining time is reset. It is a position inside a run of the <em>old</em>
	 * length, so against a new length it means nothing — and startTimer reads a
	 * remaining time that differs from the duration as a run to resume. While it was
	 * carried over, a timer changed from 30 s to 10 s still fired after 30, which was
	 * half of the stuck-timer report of 1 Aug 2026.
	 *
	 * A timer that was running keeps running, on the new length. Changing how long a
	 * timer runs is not a request to stop it; stop is for that. Asked before the
	 * cancel, because cancelling is what makes the two cases indistinguishable
	 * afterwards — and asked of the scheduler map rather than the playing flag, which
	 * can be stale.
	 *
	 * @param owner The plugin holding the timer.
	 * @param old The timer as it was, whose name may differ from the new one.
	 * @param newtimer The edited timer.
	 */
	private void applyTimerEdit(final Plugin owner, final TimerData old,
		final TimerData newtimer) {
		boolean wasRunning = owner.isTimerRunning(old.getName());
		owner.cancelTimerTask(old.getName());
		owner.getSettings().getTimers().remove(old.getName());
		newtimer.setPlaying(false);
		newtimer.setRemainingTime(newtimer.getSeconds());
		owner.getSettings().getTimers().put(newtimer.getName(), newtimer.copy());
		owner.getSettings().setDirty(true);
		if (wasRunning) {
			owner.startTimer(newtimer.getName());
		}
		persistTimerSettings();
	}

	/** Gets the timer map for the main settings plugin. */
	HashMap<String, TimerData> getTimers() {
		host.mSettings.updateTimerProgress();
		HashMap<String, TimerData> timers = host.mSettings.getSettings().getTimers();
		HashMap<String, TimerData> copy = new HashMap<String, TimerData>(timers.size());
		for (java.util.Map.Entry<String, TimerData> entry : timers.entrySet()) {
			copy.put(entry.getKey(), entry.getValue().copy());
		}
		return copy;
	}

	/** Gets the timer map for a target plugin. */
	HashMap<String, TimerData> getPluginTimers(final String plugin) {
		Plugin p = host.mPluginMap.get(plugin);
		if (p != null) {
			p.updateTimerProgress();
			HashMap<String, TimerData> timers = p.getSettings().getTimers();
			HashMap<String, TimerData> copy = new HashMap<String, TimerData>(timers.size());
			for (java.util.Map.Entry<String, TimerData> entry : timers.entrySet()) {
				copy.put(entry.getKey(), entry.getValue().copy());
			}
			return copy;
		} else {
			return null;
		}
	}

	/** Adds a new timer into the main settings plugin. */
	void addTimer(final TimerData newtimer) {
		newtimer.setRemainingTime(newtimer.getSeconds());
		host.mSettings.getSettings().getTimers().put(newtimer.getName(), newtimer.copy());
		host.mSettings.getSettings().setDirty(true);
		persistTimerSettings();
	}

	/** Starts a timer in the main settings plugin with the target name. */
	void playTimer(final String key) {
		host.mSettings.startTimer(key);
		notifyGauges();
	}

	/** Starts a timer in the target plugin. */
	void playPluginTimer(final String plugin, final String timer) {
		Plugin p = host.mPluginMap.get(plugin);
		if (p != null) {
			p.startTimer(timer);
			notifyGauges();
		}
	}

	/** Pauses a timer in the main settings plugin. */
	void pauseTimer(final String key) {
		host.mSettings.pauseTimer(key);
		notifyGauges();
	}

	/** Pauses a timer in the target plugin. */
	void pausePluginTimer(final String plugin, final String timer) {
		Plugin p = host.mPluginMap.get(plugin);
		if (p != null) {
			p.pauseTimer(timer);
			notifyGauges();
		}
	}

	/** Stops a timer in the main settings plugin. */
	void stopTimer(final String key) {
		host.mSettings.stopTimer(key);
		notifyGauges();
	}

	/** Stops a timer in the target plugin. */
	void stopPluginTimer(final String plugin, final String key) {
		Plugin p = host.mPluginMap.get(plugin);
		if (p != null) {
			p.stopTimer(key);
			notifyGauges();
		}
	}

	/** Persists timer edits immediately so they survive session close and reconnect. */
	void persistTimerSettings() {
		host.saveMainSettings();
		notifyGauges();
	}

	private void notifyGauges() {
		if (host.mGauges != null) {
			host.mGauges.onTimerTick();
		}
	}
}

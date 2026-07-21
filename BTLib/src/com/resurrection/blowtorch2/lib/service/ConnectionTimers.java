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
		default:
			break;
		}
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
		Plugin timerHost = null;
		if (host.mSettings.getSettings().getTimers().containsKey(obj)) {
			timerHost = host.mSettings;
			found = true;
		} else {
			//check plugins
			for (Plugin p : host.mPlugins) {
				if (p.getSettings().getTimers().containsKey(obj)) {
					timerHost = p;
					found = true;
				}
			}
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
				if (!silent) {
					host.toast("Timer " + obj + " started.");
				}
				break;
			case PAUSE:
				timerHost.pauseTimer(obj);
				if (!silent) {
					host.toast("Timer " + obj + " paused.");
				}
				break;
			case RESET:
				timerHost.resetTimer(obj);
				if (!silent) {
					host.toast("Timer " + obj + " reset.");
				}
				break;
			case STOP:
				timerHost.pauseTimer(obj);
				timerHost.resetTimer(obj);
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
			p.getSettings().getTimers().remove(old.getName());
			p.getSettings().getTimers().put(newtimer.getName(), newtimer.copy());
			p.getSettings().setDirty(true);
			persistTimerSettings();
		}
		
	}

	/** Updates a timer in the main settings plugin. */
	void updateTimer(final TimerData old, final TimerData newtimer) {
		host.mSettings.getSettings().getTimers().remove(old.getName());
		host.mSettings.getSettings().getTimers().put(newtimer.getName(), newtimer.copy());
		host.mSettings.getSettings().setDirty(true);
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
	}

	/** Starts a timer in the target plugin. */
	void playPluginTimer(final String plugin, final String timer) {
		Plugin p = host.mPluginMap.get(plugin);
		if (p != null) {
			p.startTimer(timer);
		}
	}

	/** Pauses a timer in the main settings plugin. */
	void pauseTimer(final String key) {
		host.mSettings.pauseTimer(key);
	}

	/** Pauses a timer in the target plugin. */
	void pausePluginTimer(final String plugin, final String timer) {
		Plugin p = host.mPluginMap.get(plugin);
		if (p != null) {
			p.pauseTimer(timer);
		}
	}

	/** Stops a timer in the main settings plugin. */
	void stopTimer(final String key) {
		host.mSettings.stopTimer(key);
	}

	/** Stops a timer in the target plugin. */
	void stopPluginTimer(final String plugin, final String key) {
		Plugin p = host.mPluginMap.get(plugin);
		if (p != null) {
			p.stopTimer(key);
		}
	}

	/** Persists timer edits immediately so they survive session close and reconnect. */
	void persistTimerSettings() {
		host.saveMainSettings();
	}
}

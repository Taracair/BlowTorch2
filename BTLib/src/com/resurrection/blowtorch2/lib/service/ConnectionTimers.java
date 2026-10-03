/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import android.os.Message;

import com.resurrection.blowtorch2.lib.service.function.SpecialCommand;
import com.resurrection.blowtorch2.lib.service.function.TimerCommand;
import com.resurrection.blowtorch2.lib.service.plugin.Plugin;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.timer.TimerDuration;
import com.resurrection.blowtorch2.lib.timer.TimerInfoText;

/** Timer CRUD, play/pause/stop, and .timer command action handling for a Connection. */
final class ConnectionTimers {

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
			doTimerInfo((String) msg.obj, msg.arg1 == 1);
			break;
		case Connection.MESSAGE_TIMERPAUSE:
			doTimerAction((String) msg.obj, msg.arg2, TIMER_ACTION.PAUSE);
			break;
		case Connection.MESSAGE_TIMERDURATION:
			if (msg.obj instanceof TimerCommand.DurationChange) {
				doTimerDuration((TimerCommand.DurationChange) msg.obj);
			} else {
				doTimerDuration((String) msg.obj, msg.arg1, msg.arg2);
			}
			break;
		default:
			break;
		}
	}

	/** Absolute set (bare seconds) or relative remaining adjust ({@code 50s}/{@code -2m}). */
	void doTimerDuration(final TimerCommand.DurationChange change) {
		if (change == null || change.name == null) {
			return;
		}
		if (change.relative) {
			doTimerDurationRelative(change.name, change.seconds, change.silent);
		} else {
			doTimerDuration(change.name, change.seconds, change.silent ? 0 : 50);
		}
	}

	/**
	 * Adds {@code deltaSeconds} to remaining only; stored duration is unchanged.
	 * Keeps a running timer running when remaining stays above zero.
	 */
	void doTimerDurationRelative(final String name, final int deltaSeconds,
			final boolean silent) {
		Plugin timerHost = findTimerHost(name);
		if (timerHost == null) {
			host.dispatchNoProcess(SpecialCommand.getErrorMessage("Timer command error",
					"No timer with name " + name + " found.").getBytes());
			return;
		}
		timerHost.updateTimerProgress();
		TimerData t = timerHost.getSettings().getTimers().get(name);
		if (t == null) {
			host.dispatchNoProcess(SpecialCommand.getErrorMessage("Timer command error",
					"No timer with name " + name + " found.").getBytes());
			return;
		}
		boolean wasRunning = timerHost.isTimerRunning(name);
		int next = TimerDuration.adjustRemaining(t.getRemainingTime(), deltaSeconds);
		timerHost.cancelTimerTask(name);
		t.setRemainingTime(next);
		t.setPlaying(false);
		timerHost.getSettings().setDirty(true);
		if (wasRunning && next > 0) {
			timerHost.startTimer(name, true);
		}
		persistTimerSettings();
		if (!silent) {
			host.toast("Timer " + name + ": " + TimerDuration.format(next) + " left");
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

	/**
	 * Status for one timer, or every timer when {@code name} is empty.
	 *
	 * @param name Timer name, or empty / null for all.
	 * @param toWindow true writes into the game window; false is a long toast.
	 *                 An all-timer dump always goes to the window.
	 */
	void doTimerInfo(final String name, final boolean toWindow) {
		if (name == null || name.trim().length() == 0) {
			String all = describeAllTimers();
			if (all.length() == 0) {
				emitTimerInfo("No timers in this world.", true);
				return;
			}
			emitTimerInfo(all, true);
			return;
		}
		Plugin timerHost = findTimerHost(name);
		if (timerHost == null) {
			host.dispatchNoProcess(SpecialCommand.getErrorMessage("Timer command error",
					"No timer with name " + name + " found.").getBytes());
			return;
		}
		timerHost.updateTimerProgress();
		TimerData t = timerHost.getSettings().getTimers().get(name);
		if (t == null) {
			host.dispatchNoProcess(SpecialCommand.getErrorMessage("Timer command error",
					"No timer with name " + name + " found.").getBytes());
			return;
		}
		int seconds = t.getSeconds() == null ? 0 : t.getSeconds().intValue();
		String text = TimerInfoText.describe(name, seconds, t.getRemainingTime(),
				timerHost.isTimerRunning(name), t.isRepeat(),
				timerHost.timerDisplayFull(name));
		emitTimerInfo(text, toWindow);
	}

	private String describeAllTimers() {
		StringBuilder sb = new StringBuilder();
		appendTimers(sb, host.mSettings);
		if (host.mPlugins != null) {
			for (Plugin p : host.mPlugins) {
				appendTimers(sb, p);
			}
		}
		return sb.toString();
	}

	private void appendTimers(final StringBuilder sb, final Plugin owner) {
		if (owner == null || owner.getSettings() == null
				|| owner.getSettings().getTimers() == null
				|| owner.getSettings().getTimers().isEmpty()) {
			return;
		}
		owner.updateTimerProgress();
		ArrayList<String> names = new ArrayList<String>(owner.getSettings().getTimers().keySet());
		Collections.sort(names);
		for (String n : names) {
			TimerData t = owner.getSettings().getTimers().get(n);
			if (t == null) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append("\n\n");
			}
			int seconds = t.getSeconds() == null ? 0 : t.getSeconds().intValue();
			sb.append(TimerInfoText.describe(n, seconds, t.getRemainingTime(),
					owner.isTimerRunning(n), t.isRepeat(),
					owner.timerDisplayFull(n)));
		}
	}

	private void emitTimerInfo(final String text, final boolean toWindow) {
		if (toWindow) {
			host.dispatchNoProcess(("\n" + text + "\n").getBytes());
		} else {
			host.toast(text, true);
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

	boolean gateExists(final String plugin, final String name) {
		return gateTimerPlugin(plugin, name) != null;
	}

	boolean gateRunning(final String plugin, final String name) {
		Plugin owner = gateTimerPlugin(plugin, name);
		return owner != null && owner.isTimerRunning(name);
	}

	int gateRemainingSeconds(final String plugin, final String name) {
		Plugin owner = gateTimerPlugin(plugin, name);
		if (owner == null || name == null) {
			return -1;
		}
		owner.updateTimerProgress();
		com.resurrection.blowtorch2.lib.timer.TimerData t =
				owner.getSettings().getTimers().get(name);
		if (t == null) {
			return -1;
		}
		return t.getRemainingTime();
	}

	private Plugin gateTimerPlugin(final String plugin, final String name) {
		if (name == null || name.length() == 0) {
			return null;
		}
		if (plugin != null && plugin.length() > 0) {
			if (host.mSettings != null && plugin.equals(host.mSettings.getName())
					&& host.mSettings.getSettings().getTimers().containsKey(name)) {
				return host.mSettings;
			}
			if (host.mPlugins != null) {
				for (Plugin p : host.mPlugins) {
					if (p != null && plugin.equals(p.getName())
							&& p.getSettings().getTimers().containsKey(name)) {
						return p;
					}
				}
			}
			return null;
		}
		return findTimerHost(name);
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
				doTimerInfo(obj, false);
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
	 * If the stored seconds value changed, remaining starts from the new length
	 * (carrying the old remaining across a shorter duration used to fire late —
	 * stuck-timer report of 1 Aug 2026). If seconds are unchanged, keep the live
	 * remaining so Done / "show as overlay widget" does not restart a run from zero.
	 *
	 * A timer that was running keeps running when remaining stays above zero.
	 * Asked of the scheduler map before cancel; the playing flag can be stale.
	 *
	 * @param owner The plugin holding the timer.
	 * @param old The timer as it was, whose name may differ from the new one.
	 * @param newtimer The edited timer.
	 */
	private void applyTimerEdit(final Plugin owner, final TimerData old,
		final TimerData newtimer) {
		boolean wasRunning = owner.isTimerRunning(old.getName());
		if (wasRunning) {
			owner.updateTimerProgress();
		}
		TimerData live = owner.getSettings().getTimers().get(old.getName());
		int previousSeconds = 0;
		if (live != null && live.getSeconds() != null) {
			previousSeconds = live.getSeconds().intValue();
		} else if (old.getSeconds() != null) {
			previousSeconds = old.getSeconds().intValue();
		}
		int newSeconds = newtimer.getSeconds() == null ? 0 : newtimer.getSeconds().intValue();
		int liveRemaining = live != null ? live.getRemainingTime()
				: (old.getRemainingTime() > 0 ? old.getRemainingTime() : newSeconds);
		int keepRemaining = TimerDuration.remainingAfterEdit(
				previousSeconds, newSeconds, liveRemaining);
		owner.cancelTimerTask(old.getName());
		owner.getSettings().getTimers().remove(old.getName());
		newtimer.setPlaying(false);
		newtimer.setRemainingTime(keepRemaining);
		owner.getSettings().getTimers().put(newtimer.getName(), newtimer.copy());
		owner.getSettings().setDirty(true);
		if (wasRunning && keepRemaining > 0) {
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

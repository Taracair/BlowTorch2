package com.resurrection.blowtorch2.lib.util;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;

import android.content.Context;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

/**
 * One connection to the phone's speech engine, for triggers that speak.
 *
 * <p><b>Costs nothing to ship.</b> {@link TextToSpeech} is a platform API and
 * the voices belong to whatever engine the phone already has. There is no model
 * to bundle, so this adds no weight to the APK.
 *
 * <p><b>One instance, made on first use.</b> Binding to the engine is a service
 * connection, not a call, so it is opened once and kept — one per utterance
 * would spend most of a fight connecting. It is never opened at all until a
 * trigger actually speaks, so a player who does not use this pays nothing.
 *
 * <p><b>The queue is the hard part, not the speaking.</b> A MUD prints several
 * lines a second in a fight. Plain {@code QUEUE_ADD} leaves the speech a minute
 * behind what is on screen and useless; plain {@code QUEUE_FLUSH} means only
 * the last line of any burst is ever heard. So: a short queue, and when it
 * overflows the newest line wins and the backlog is dropped. What you hear is
 * always about now.
 *
 * <p>Owned by the process the triggers run in ({@code :stellar}), which is also
 * what lets an alert be heard while the game window is in the background.
 */
public final class SpeechEngine {

	/**
	 * How many utterances may be waiting before the backlog is dropped.
	 *
	 * <p>Three is about two seconds of speech. Past that the words being spoken
	 * are describing something that has already stopped being true.
	 */
	public static final int MAX_PENDING = 3;

	/**
	 * The same text within this many milliseconds is not repeated.
	 *
	 * <p>Worlds repeat lines — a status line, the same miss message four times
	 * in a round. Saying it four times is noise, and it pushes the line that
	 * mattered out of the queue.
	 */
	public static final long REPEAT_GUARD_MS = 1500;

	/**
	 * How long after the last sign of typing speech stays quiet.
	 *
	 * <p>A safety net, not the mechanism: the input bar says when it is empty
	 * again, and this only matters if that message never arrives — the UI
	 * process being killed mid-command, say. Without it a player could be left
	 * with an alert that has silently stopped working and no way to guess why.
	 */
	public static final long TYPING_QUIET_TIMEOUT_MS = 30000;

	/**
	 * Whether typing silences speech at all — the player's choice, from
	 * Options → Input.
	 *
	 * <p>Off, matching the option's own default. Speaking whenever a trigger
	 * fires is what this app has always done, so that is what a player who never
	 * opens the option gets. On, the quiet falls over the busiest moments — you
	 * type most in a fight — and an alert that goes silent exactly then is a
	 * worse failure than one that talks too much, so it is opted into.
	 */
	private static volatile boolean quietWhileTyping = false;

	/** @param quiet true to stay silent while a command is being composed. */
	public static void setQuietWhileTyping(final boolean quiet) {
		quietWhileTyping = quiet;
	}

	/** Set while there is half a command in the input bar. */
	private static volatile boolean playerTyping = false;
	private static volatile long playerTypingAt = 0;

	/**
	 * The input bar has something in it, or has just been emptied.
	 *
	 * <p>Static because the caller is a binder thread in {@code :stellar} and the
	 * engine may not have been made yet — the first thing a player does after
	 * connecting is often type, and building a speech engine to record that
	 * would be absurd.
	 *
	 * @param typing true while a command is being composed.
	 */
	public static void setPlayerTyping(final boolean typing) {
		playerTyping = typing;
		playerTypingAt = android.os.SystemClock.elapsedRealtime();
	}

	/** True while a command is being composed and the timeout has not lapsed. */
	private static boolean isPlayerTyping() {
		if (!playerTyping) {
			return false;
		}
		if (android.os.SystemClock.elapsedRealtime() - playerTypingAt
				> TYPING_QUIET_TIMEOUT_MS) {
			playerTyping = false;
			return false;
		}
		return true;
	}

	private static SpeechEngine instance = null;

	private TextToSpeech tts = null;
	private boolean ready = false;
	private boolean failed = false;
	/** Said before the engine finished starting; spoken once it has. */
	private final ArrayDeque<String> waitingForInit = new ArrayDeque<String>();
	/** Utterances handed to the engine and not yet finished. */
	private int outstanding = 0;
	private String lastText = null;
	private long lastTextAt = 0;
	private int utteranceSeq = 0;
	/** Why it is not speaking, in words a player can act on. */
	private String problem = null;

	/**
	 * Kept so the engine can say when nobody can hear it.
	 *
	 * <p>The application context, so holding it leaks nothing — this is a
	 * process-lifetime singleton already.
	 */
	private final Context appContext;

	private SpeechEngine(final Context context) {
		final Context app = context.getApplicationContext();
		this.appContext = app;
		try {
			tts = new TextToSpeech(app, new TextToSpeech.OnInitListener() {
				@Override
				public void onInit(int status) {
					onEngineReady(status);
				}
			});
		} catch (Throwable t) {
			// A phone with no speech engine at all is a supported phone. It just
			// cannot do this, and must not be a crash on the service thread.
			failed = true;
			problem = "This phone has no speech engine that BlowTorch can reach.";
			BlowTorchLogger.logMinor("SpeechEngine.init", t);
		}
	}

	public static synchronized SpeechEngine get(final Context context) {
		if (instance == null) {
			instance = new SpeechEngine(context);
		}
		return instance;
	}

	/** Whether anything has ever been asked of the engine. */
	public static synchronized boolean exists() {
		return instance != null;
	}

	private synchronized void onEngineReady(final int status) {
		if (status != TextToSpeech.SUCCESS) {
			failed = true;
			waitingForInit.clear();
			problem = "No speech engine answered. Install one (Google Speech"
					+ " Services, or your phone's own) and pick it in Android"
					+ " Settings under Accessibility or Language.";
			BlowTorchLogger.logMinor("SpeechEngine",
					new IllegalStateException("TextToSpeech init failed: " + status));
			return;
		}
		chooseVoice();
		attachProgressListener();
		ready = true;
		while (!waitingForInit.isEmpty()) {
			handToEngine(waitingForInit.poll(), false);
		}
	}

	/**
	 * Speak in the phone's language if the engine has it, English if not.
	 *
	 * <p>{@code setLanguage} answers with a status rather than throwing, and a
	 * missing voice is the ordinary case — a Polish phone with an engine that
	 * only shipped English. Ignoring that answer is a silent nothing: the engine
	 * is up, {@code speak} returns success, and no sound is ever made.
	 */
	private void chooseVoice() {
		try {
			int r = tts.setLanguage(Locale.getDefault());
			if (r != TextToSpeech.LANG_MISSING_DATA
					&& r != TextToSpeech.LANG_NOT_SUPPORTED) {
				return;
			}
			int fallback = tts.setLanguage(Locale.ENGLISH);
			if (fallback == TextToSpeech.LANG_MISSING_DATA
					|| fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
				problem = "The speech engine has no voice installed. Open its"
						+ " settings in Android (Accessibility or Language →"
						+ " Text-to-speech) and download a voice.";
				return;
			}
			problem = "No " + Locale.getDefault().getDisplayLanguage()
					+ " voice is installed, so English is being used. Download"
					+ " one in Android's text-to-speech settings to change that.";
		} catch (Throwable t) {
			// Keep the engine: the default voice may still speak, and refusing
			// to speak at all because a locale was not matched helps nobody.
			BlowTorchLogger.logMinor("SpeechEngine.setLanguage", t);
		}
	}

	/**
	 * What is wrong, or null when nothing is.
	 *
	 * <p>Read by the Speak action's editor so the answer is where the player is
	 * looking, rather than in a log file they have no reason to open.
	 */
	public synchronized String getProblem() {
		return problem;
	}

	/** True once the engine is up and has been given a voice. */
	public synchronized boolean isReady() {
		return ready && !failed;
	}

	/** Still connecting: neither ready nor known to have failed. */
	public synchronized boolean isStarting() {
		return !ready && !failed;
	}

	private void attachProgressListener() {
		try {
			tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
				@Override
				public void onStart(String id) {
				}

				@Override
				public void onDone(String id) {
					finished();
				}

				@Override
				@Deprecated
				public void onError(String id) {
					finished();
				}

				@Override
				public void onError(String id, int errorCode) {
					finished();
				}
			});
		} catch (Throwable t) {
			// Without the listener the pending count never falls, which would
			// make every utterance look like an overflow and flush the one
			// before it. Better to lose the cap than to lose the speech.
			BlowTorchLogger.logMinor("SpeechEngine.progressListener", t);
		}
	}

	private synchronized void finished() {
		if (outstanding > 0) {
			outstanding--;
		}
	}

	/**
	 * Say something.
	 *
	 * @param text what to say; null, empty or all whitespace is ignored.
	 * @param interrupt true to stop whatever is being said and say this instead.
	 *        For a warning that is only true right now.
	 */
	public synchronized void speak(final String text, final boolean interrupt) {
		speak(text, interrupt, true);
	}

	/**
	 * Say something.
	 *
	 * @param text what to say.
	 * @param interrupt cut off whatever is being said.
	 * @param warnWhenSilent whether this caller wants to be told when the media
	 *        volume is off. Per action, so one trigger can be told to keep quiet
	 *        about it without silencing the warning everywhere.
	 */
	public synchronized void speak(final String text, final boolean interrupt,
			final boolean warnWhenSilent) {
		if (failed || tts == null || text == null) {
			return;
		}
		if (quietWhileTyping && isPlayerTyping()) {
			// Dropped, not queued. Held back, it would all arrive at once the
			// moment the command is sent, which is worse than not hearing it:
			// the player would be read a fight that has already moved on.
			return;
		}
		String say = text.trim();
		if (say.length() == 0) {
			return;
		}
		long now = android.os.SystemClock.elapsedRealtime();
		if (say.equals(lastText) && now - lastTextAt < REPEAT_GUARD_MS) {
			return;
		}
		lastText = say;
		lastTextAt = now;
		if (!ready) {
			// Still starting. Keep only as many as would be worth saying by the
			// time it is up.
			while (waitingForInit.size() >= MAX_PENDING) {
				waitingForInit.poll();
			}
			waitingForInit.add(say);
			return;
		}
		handToEngine(say, interrupt);
		// Speech goes out on the media stream, so it has exactly the failure the
		// sound action has: the engine is up, speak() succeeds, the volume is at
		// zero and nothing is said. Same warning, same thirty-second limiter —
		// a player with a speaking trigger and a sounding trigger gets one
		// message, not two.
		if (warnWhenSilent) {
			TriggerSounds.warnIfStreamSilent(appContext,
					android.media.AudioManager.STREAM_MUSIC, "media",
					"Speech not heard", "");
		}
	}

	private void handToEngine(final String say, final boolean interrupt) {
		boolean flush = interrupt || outstanding >= MAX_PENDING;
		int mode = flush ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
		if (flush) {
			outstanding = 0;
		}
		String id = "bt" + (++utteranceSeq);
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				tts.speak(say, mode, null, id);
			} else {
				HashMap<String, String> params = new HashMap<String, String>();
				params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id);
				tts.speak(say, mode, params);
			}
			outstanding++;
		} catch (Throwable t) {
			BlowTorchLogger.logMinor("SpeechEngine.speak", t);
		}
	}

	/** Stop talking now, without closing the engine. */
	public synchronized void stop() {
		if (tts == null) {
			return;
		}
		try {
			tts.stop();
		} catch (Throwable t) {
			BlowTorchLogger.logMinor("SpeechEngine.stop", t);
		}
		outstanding = 0;
		waitingForInit.clear();
	}

	/**
	 * Give the engine back.
	 *
	 * <p>It is a bound service. Leaving it bound when the process that asked for
	 * it is going away is a leak the system notices.
	 */
	public static synchronized void release() {
		if (instance == null) {
			return;
		}
		SpeechEngine e = instance;
		instance = null;
		if (e.tts != null) {
			try {
				e.tts.stop();
				e.tts.shutdown();
			} catch (Throwable t) {
				BlowTorchLogger.logMinor("SpeechEngine.release", t);
			}
		}
	}
}

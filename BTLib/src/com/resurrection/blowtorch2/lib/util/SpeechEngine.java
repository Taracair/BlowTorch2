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

	private SpeechEngine(final Context context) {
		final Context app = context.getApplicationContext();
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
			BlowTorchLogger.logMinor("SpeechEngine",
					new IllegalStateException("TextToSpeech init failed: " + status));
			return;
		}
		try {
			tts.setLanguage(Locale.getDefault());
		} catch (Throwable t) {
			// Keep the engine: the default voice still speaks, and refusing to
			// speak at all because a locale was not matched helps nobody.
			BlowTorchLogger.logMinor("SpeechEngine.setLanguage", t);
		}
		attachProgressListener();
		ready = true;
		while (!waitingForInit.isEmpty()) {
			handToEngine(waitingForInit.poll(), false);
		}
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
		if (failed || tts == null || text == null) {
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

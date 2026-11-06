package com.resurrection.blowtorch2.lib.window;

import android.os.Handler;
import android.view.KeyEvent;

/**
 * Posts hold and repeat on the input bar's existing handler. The matcher
 * does not wait, and a stale callback is dropped when its token no longer matches.
 */
public final class KeyboardShortcutController {

	public interface Sink {
		void sendLine(String line, boolean quiet);

		void sendInput();

		void showMark(String mark);
	}

	private final Handler handler;
	private final Sink sink;
	private final KeyboardShortcutMatcher matcher = new KeyboardShortcutMatcher();
	private String loaded;
	private String shownMark;
	private boolean markPublished;

	public KeyboardShortcutController(final Handler handler, final Sink sink) {
		this.handler = handler;
		this.sink = sink;
	}

	public boolean onKey(final KeyEvent event, final int hook) {
		if (event == null) {
			return false;
		}
		KeyboardShortcutMatcher.Outcome outcome = matcher.onKey(fromEvent(event, hook));
		apply(outcome);
		return outcome.consumed;
	}

	public void setStored(final String stored) {
		KeyboardShortcuts next = KeyboardShortcuts.parse(stored);
		String canonical = next.format();
		if (canonical.equals(loaded)) {
			return;
		}
		loaded = canonical;
		matcher.setShortcuts(next);
		publishMark();
	}

	public void setHardwareKeyboard(final boolean present) {
		matcher.setHardwareKeyboard(present);
		publishMark();
	}

	public void setTypingForced(final boolean forced) {
		matcher.setTypingForced(forced);
		publishMark();
	}

	public void setDialogOpen(final boolean open) {
		matcher.setDialogOpen(open);
	}

	public void setWalkingSuspended(final boolean suspended) {
		matcher.setWalkingSuspended(suspended);
	}

	public void republishMark() {
		markPublished = false;
		publishMark();
	}

	private void publishMark() {
		String next = matcher.mark();
		if (markPublished && (next == null ? shownMark == null : next.equals(shownMark))) {
			return;
		}
		markPublished = true;
		shownMark = next;
		if (sink != null) {
			sink.showMark(next);
		}
	}

	private void apply(final KeyboardShortcutMatcher.Outcome outcome) {
		if (outcome == null || sink == null) {
			return;
		}
		if (outcome.sendInput) {
			sink.sendInput();
		} else if (outcome.send != null && outcome.send.length() > 0) {
			sink.sendLine(outcome.send, outcome.quiet);
		}
		if (handler == null) {
			return;
		}
		if (outcome.scheduleHoldMs > 0) {
			final int token = outcome.timerToken;
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					apply(matcher.onHoldFired(token));
				}
			}, outcome.scheduleHoldMs);
		}
		if (outcome.scheduleRepeatMs > 0) {
			final int token = outcome.timerToken;
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					apply(matcher.onRepeatFired(token));
				}
			}, outcome.scheduleRepeatMs);
		}
	}

	/**
	 * {@code KeyEvent.ACTION_DOWN} is 0 and {@code ACTION_UP} is 1.
	 * Plain unicode is {@code getUnicodeChar(0)}; meta unicode is
	 * {@code getUnicodeChar(metaState)}.
	 */
	static KeyboardShortcutMatcher.Key fromEvent(final KeyEvent event, final int hook) {
		int meta = event.getMetaState();
		return new KeyboardShortcutMatcher.Key(
				event.getAction(),
				event.getKeyCode(),
				meta,
				event.getUnicodeChar(0),
				event.getUnicodeChar(meta),
				event.getScanCode(),
				event.getFlags(),
				event.getRepeatCount(),
				event.getDeviceId(),
				hook);
	}
}

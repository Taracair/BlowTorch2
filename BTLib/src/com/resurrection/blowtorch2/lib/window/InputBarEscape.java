package com.resurrection.blowtorch2.lib.window;

/**
 * Escape on the input bar, and the draft a history step has to keep.
 * A recalled line restores that draft. Any other text is cleared.
 * An empty bar hides the keyboard.
 */
public final class InputBarEscape {

	public enum Kind {
		RESTORE,
		CLEAR,
		HIDE
	}

	public static final class Result {
		public final Kind kind;
		/** The draft, when {@link Kind#RESTORE}. */
		public final String text;

		private Result(final Kind kind, final String text) {
			this.kind = kind;
			this.text = text == null ? "" : text;
		}
	}

	public static final class Recall {
		public final boolean recalling;
		public final String draft;

		private Recall(final boolean recalling, final String draft) {
			this.recalling = recalling;
			this.draft = draft == null ? "" : draft;
		}
	}

	private InputBarEscape() {
	}

	/**
	 * @param recalling the bar is showing a command recalled from history
	 * @param draft what was in the bar before that recall
	 * @param bar the text on screen now
	 */
	public static Result decide(final boolean recalling, final String draft, final String bar) {
		if (recalling) {
			return new Result(Kind.RESTORE, draft);
		}
		if (bar != null && bar.length() > 0) {
			return new Result(Kind.CLEAR, "");
		}
		return new Result(Kind.HIDE, "");
	}

	/**
	 * The first step away from what was typed keeps that text as the draft.
	 * Later steps keep the same draft. An empty line is no longer a recall.
	 */
	public static Recall note(final boolean wasRecalling, final String draft, final String before,
			final String shown) {
		String kept = wasRecalling ? text(draft) : text(before);
		boolean recalling = shown != null && shown.length() > 0;
		return new Recall(recalling, kept);
	}

	private static String text(final String value) {
		return value == null ? "" : value;
	}
}

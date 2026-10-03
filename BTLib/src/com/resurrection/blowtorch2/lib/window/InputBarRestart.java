package com.resurrection.blowtorch2.lib.window;

/**
 * {@code setInputType} and {@code restartInput} move the caret to 0.
 * A settings reload that did not change the bar must not call them.
 */
public final class InputBarRestart {

	private InputBarRestart() {
	}

	public static boolean needed(final int haveType, final int wantType,
			final int haveMaxLines, final int wantMaxLines,
			final boolean havePasswordMask, final boolean wantPasswordMask,
			final boolean haveSingleLine, final boolean wantSingleLine,
			final boolean imeChanged, final boolean ignoreMultiLine) {
		int have = haveType;
		int want = wantType;
		if (ignoreMultiLine) {
			// setSingleLine(false) ORs this in. The type we pass to setInputType
			// must not, or Enter inserts a newline instead of Send.
			have &= ~android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;
			want &= ~android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;
		}
		if (imeChanged || havePasswordMask != wantPasswordMask
				|| haveSingleLine != wantSingleLine) {
			return true;
		}
		return have != want || haveMaxLines != wantMaxLines;
	}
}

package com.resurrection.blowtorch2.lib.window;

/**
 * When a button label is drawn wrapped. The Wrap label checkbox word-wraps a
 * long name; a real newline in the label (Enter in the editor) is a hard break
 * even when that checkbox is off. Typed {@code \\n} is not expanded, so a
 * label like {@code C:\notes} stays one line.
 */
public final class ButtonLabelWrap {

	private ButtonLabelWrap() {
	}

	public static boolean usesWrappedLayout(boolean wrapLabel, String label) {
		if (wrapLabel) {
			return true;
		}
		return label != null && label.indexOf('\n') >= 0;
	}
}

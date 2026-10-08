package com.resurrection.blowtorch2.lib.window;

/**
 * One line for the renderer chip. {@code typeset}, {@code tiles}, and
 * {@code bake} are always present. The number is lines that took that path
 * this frame, including zero. {@code hw} / {@code sw} is the window canvas.
 */
public final class RendererDebugLabel {

	private RendererDebugLabel() {
	}

	public static String format(final int tiles, final int bake, final int typeset,
			final boolean hardware) {
		final StringBuilder sb = new StringBuilder();
		append(sb, "typeset", typeset);
		sb.append(" · ");
		append(sb, "tiles", tiles);
		sb.append(" · ");
		append(sb, "bake", bake);
		sb.append(" · ");
		sb.append(hardware ? "hw" : "sw");
		return sb.toString();
	}

	/**
	 * Shrink {@code textSize} so {@code textWidth} fits in {@code maxWidth}.
	 * Does not go below {@code minSize}. A non-positive width is left alone.
	 */
	static float fitTextSize(final float textSize, final float textWidth,
			final float maxWidth, final float minSize) {
		if (textWidth <= maxWidth || textWidth <= 0f || maxWidth <= 0f) {
			return textSize;
		}
		final float fitted = textSize * (maxWidth / textWidth);
		if (fitted < minSize) {
			return minSize;
		}
		return fitted;
	}

	private static void append(final StringBuilder sb, final String name, final int n) {
		sb.append(name).append(' ').append(n > 0 ? n : 0);
	}
}

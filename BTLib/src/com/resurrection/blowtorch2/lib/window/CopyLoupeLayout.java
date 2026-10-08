package com.resurrection.blowtorch2.lib.window;

/**
 * Copy-widget disc and the four action buttons that sit outside it.
 * Size and zoom percents are the same as {@link PrefixPickLoupe}.
 */
public final class CopyLoupeLayout {

	public static final int HIT_NONE = 0;
	public static final int HIT_DISC = 1;
	public static final int HIT_COPY = 2;
	public static final int HIT_SWAP = 3;
	public static final int HIT_EXIT = 4;
	public static final int HIT_TRIGGER = 5;

	/** Hit radius in dp. Placement uses this plus {@link #BUTTON_GAP_DP}. */
	public static final float BUTTON_RADIUS_DP = 34f;
	/** Negative pulls the icons onto the disc rim. */
	public static final float BUTTON_GAP_DP = -8f;
	/** Drawn icon is this times the hit radius. */
	public static final float ICON_DRAW_SCALE = 1.4f;

	private static final int RIGHT = 0;
	private static final int LEFT = 1;
	private static final int ABOVE = 2;
	private static final int BELOW = 3;

	private CopyLoupeLayout() {
	}

	public static float buttonRadiusPx(final float density) {
		float d = density <= 0f ? 1f : density;
		return BUTTON_RADIUS_DP * d;
	}

	public static float gapPx(final float density) {
		float d = density <= 0f ? 1f : density;
		return BUTTON_GAP_DP * d;
	}

	public static float iconSidePx(final float buttonRadiusPx) {
		if (buttonRadiusPx <= 0f) {
			return 0f;
		}
		return buttonRadiusPx * ICON_DRAW_SCALE;
	}

	/**
	 * Copy to the right, swap to the left, close below, new-trigger above.
	 * A button that would clip flips to the next free compass point.
	 * {@code out} is copyX, copyY, swapX, swapY, exitX, exitY, triggerX,
	 * triggerY (length ≥ 8).
	 */
	public static void placeButtons(final float cx, final float cy,
			final float discR, final float btnR, final float gap,
			final float viewW, final float viewH, final float[] out) {
		float dist = discR + gap + btnR;
		int copy = pickSlot(cx, cy, dist, btnR, viewW, viewH,
				RIGHT, LEFT, ABOVE, BELOW, -1, -1, -1);
		int swap = pickSlot(cx, cy, dist, btnR, viewW, viewH,
				LEFT, RIGHT, ABOVE, BELOW, copy, -1, -1);
		int exit = pickSlot(cx, cy, dist, btnR, viewW, viewH,
				BELOW, ABOVE, LEFT, RIGHT, copy, swap, -1);
		int trigger = pickSlot(cx, cy, dist, btnR, viewW, viewH,
				ABOVE, BELOW, RIGHT, LEFT, copy, swap, exit);
		writeSlot(cx, cy, dist, copy, out, 0);
		writeSlot(cx, cy, dist, swap, out, 2);
		writeSlot(cx, cy, dist, exit, out, 4);
		writeSlot(cx, cy, dist, trigger, out, 6);
		clampButton(out, 0, btnR, viewW, viewH);
		clampButton(out, 2, btnR, viewW, viewH);
		clampButton(out, 4, btnR, viewW, viewH);
		clampButton(out, 6, btnR, viewW, viewH);
		spreadButtons(out, cx, cy, btnR, viewW, viewH);
	}

	/**
	 * Buttons first so a tap on the gap prefers the action, then the disc.
	 * {@code buttons} is the array filled by {@link #placeButtons}.
	 */
	public static int hit(final float x, final float y, final float cx,
			final float cy, final float discR, final float[] buttons,
			final float btnR) {
		if (buttons != null && buttons.length >= 6) {
			if (buttons.length >= 8
					&& inCircle(x, y, buttons[6], buttons[7], btnR)) {
				return HIT_TRIGGER;
			}
			if (inCircle(x, y, buttons[4], buttons[5], btnR)) {
				return HIT_EXIT;
			}
			if (inCircle(x, y, buttons[2], buttons[3], btnR)) {
				return HIT_SWAP;
			}
			if (inCircle(x, y, buttons[0], buttons[1], btnR)) {
				return HIT_COPY;
			}
		}
		if (inCircle(x, y, cx, cy, discR)) {
			return HIT_DISC;
		}
		return HIT_NONE;
	}

	/**
	 * Disc flush with the right of the view. Dragging further should pan a
	 * canvas wider than the screen, same idea as the caret hitting the top
	 * or bottom and scrolling the buffer.
	 */
	public static boolean discAgainstRight(final float cx, final float discR,
			final float viewW) {
		return cx + discR >= viewW - 1f;
	}

	/** Disc flush with the left of the view. */
	public static boolean discAgainstLeft(final float cx, final float discR) {
		return cx - discR <= 1f;
	}

	static boolean inCircle(final float x, final float y, final float cx,
			final float cy, final float r) {
		float dx = x - cx;
		float dy = y - cy;
		return dx * dx + dy * dy <= r * r;
	}

	private static int pickSlot(final float cx, final float cy, final float dist,
			final float btnR, final float viewW, final float viewH,
			final int a, final int b, final int c, final int d,
			final int used1, final int used2, final int used3) {
		int[] order = new int[] { a, b, c, d };
		int fallback = a;
		for (int i = 0; i < order.length; i++) {
			int slot = order[i];
			if (slot == used1 || slot == used2 || slot == used3) {
				continue;
			}
			if (slotFits(cx, cy, dist, btnR, viewW, viewH, slot)) {
				return slot;
			}
			if (fallback == used1 || fallback == used2 || fallback == used3) {
				fallback = slot;
			}
		}
		return fallback;
	}

	private static boolean slotFits(final float cx, final float cy,
			final float dist, final float btnR, final float viewW,
			final float viewH, final int slot) {
		float x = cx + slotDx(slot, dist);
		float y = cy + slotDy(slot, dist);
		return x - btnR >= 2f && x + btnR <= viewW - 2f
				&& y - btnR >= 2f && y + btnR <= viewH - 2f;
	}

	private static void writeSlot(final float cx, final float cy,
			final float dist, final int slot, final float[] out,
			final int at) {
		out[at] = cx + slotDx(slot, dist);
		out[at + 1] = cy + slotDy(slot, dist);
	}

	/**
	 * A corner has two free compass points and four buttons, so clamp stacks
	 * the leftovers. An edge leaves the fourth button on top of the disc.
	 * Park either one on the next ring that clears the disc centre and the
	 * buttons already placed.
	 */
	private static void spreadButtons(final float[] out, final float cx,
			final float cy, final float btnR, final float viewW,
			final float viewH) {
		float need = btnR * 2f + 1f;
		int[] at = new int[] { 0, 2, 4, 6 };
		for (int i = 0; i < at.length; i++) {
			if (overlapsEarlier(out, at, i, need)
					|| coversDiscCentre(out, at[i], cx, cy, btnR)) {
				relocate(out, at, i, need, cx, cy, btnR, viewW, viewH);
			}
		}
	}

	private static boolean coversDiscCentre(final float[] out, final int at,
			final float cx, final float cy, final float btnR) {
		float dx = out[at] - cx;
		float dy = out[at + 1] - cy;
		return dx * dx + dy * dy <= btnR * btnR;
	}

	private static boolean overlapsEarlier(final float[] out, final int[] at,
			final int index, final float need) {
		float x = out[at[index]];
		float y = out[at[index] + 1];
		for (int j = 0; j < index; j++) {
			float dx = x - out[at[j]];
			float dy = y - out[at[j] + 1];
			if (dx * dx + dy * dy < need * need) {
				return true;
			}
		}
		return false;
	}

	private static void relocate(final float[] out, final int[] at,
			final int index, final float need, final float cx, final float cy,
			final float btnR, final float viewW, final float viewH) {
		int b = at[index];
		float dx = out[b] - cx;
		float dy = out[b + 1] - cy;
		float baseR = (float) Math.hypot(dx, dy);
		if (baseR <= btnR) {
			baseR = btnR + 1f;
		}
		if (baseR < need) {
			baseR = need;
		}
		double baseAng = Math.atan2(dy, dx);
		float maxR = Math.max(viewW, viewH);
		float min = btnR + 2f;
		for (float radius = baseR; radius <= maxR; radius += btnR) {
			for (int step = 0; step < 24; step++) {
				int k = (step + 1) / 2;
				int sign = (step % 2 == 0) ? 1 : -1;
				double ang = baseAng + k * sign * (Math.PI / 12.0);
				float nx = cx + (float) (Math.cos(ang) * radius);
				float ny = cy + (float) (Math.sin(ang) * radius);
				if (nx < min || nx > viewW - min || ny < min || ny > viewH - min) {
					continue;
				}
				boolean clear = true;
				for (int j = 0; j < index; j++) {
					float ox = nx - out[at[j]];
					float oy = ny - out[at[j] + 1];
					if (ox * ox + oy * oy < need * need) {
						clear = false;
						break;
					}
				}
				if (clear) {
					out[b] = nx;
					out[b + 1] = ny;
					return;
				}
			}
		}
	}

	private static void clampButton(final float[] out, final int at,
			final float btnR, final float viewW, final float viewH) {
		float min = btnR + 2f;
		float maxX = viewW - btnR - 2f;
		float maxY = viewH - btnR - 2f;
		if (maxX < min) {
			out[at] = viewW * 0.5f;
		} else {
			if (out[at] < min) {
				out[at] = min;
			} else if (out[at] > maxX) {
				out[at] = maxX;
			}
		}
		if (maxY < min) {
			out[at + 1] = viewH * 0.5f;
		} else {
			if (out[at + 1] < min) {
				out[at + 1] = min;
			} else if (out[at + 1] > maxY) {
				out[at + 1] = maxY;
			}
		}
	}

	private static float slotDx(final int slot, final float dist) {
		if (slot == RIGHT) {
			return dist;
		}
		if (slot == LEFT) {
			return -dist;
		}
		return 0f;
	}

	private static float slotDy(final int slot, final float dist) {
		if (slot == BELOW) {
			return dist;
		}
		if (slot == ABOVE) {
			return -dist;
		}
		return 0f;
	}
}

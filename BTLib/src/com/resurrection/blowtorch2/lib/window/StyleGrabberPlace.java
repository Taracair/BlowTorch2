package com.resurrection.blowtorch2.lib.window;

/**
 * View-local placement for the grabber panel. Finger coordinates are in the
 * host window; clamping uses that window's size, not the display.
 */
public final class StyleGrabberPlace {

	public final float left;
	public final float top;
	public final float right;
	public final float bottom;

	public StyleGrabberPlace(final float left, final float top, final float right,
			final float bottom) {
		this.left = left;
		this.top = top;
		this.right = right;
		this.bottom = bottom;
	}

	/**
	 * Prefer to the right of the finger, then clamp so the panel stays inside
	 * {@code [margin, view - margin]}. Shrinks if the panel is larger than the
	 * window.
	 */
	public static StyleGrabberPlace of(final float fingerX, final float fingerY,
			final float panelW, final float panelH, final float viewW, final float viewH,
			final float gap, final float margin, final float topAnchor) {
		float vw = viewW > 0f ? viewW : 1f;
		float vh = viewH > 0f ? viewH : 1f;
		float m = margin < 0f ? 0f : margin;
		float maxW = Math.max(0f, vw - 2f * m);
		float maxH = Math.max(0f, vh - 2f * m);
		float w = panelW < maxW ? panelW : maxW;
		float h = panelH < maxH ? panelH : maxH;
		if (w < 0f) {
			w = 0f;
		}
		if (h < 0f) {
			h = 0f;
		}

		float left = fingerX + gap;
		if (left + w > vw - m) {
			left = fingerX - w - gap;
		}
		float minLeft = m;
		float maxLeft = vw - w - m;
		if (maxLeft < minLeft) {
			left = minLeft;
		} else if (left < minLeft) {
			left = minLeft;
		} else if (left > maxLeft) {
			left = maxLeft;
		}

		float top = fingerY - h * topAnchor;
		float minTop = m;
		float maxTop = vh - h - m;
		if (maxTop < minTop) {
			top = minTop;
		} else if (top < minTop) {
			top = minTop;
		} else if (top > maxTop) {
			top = maxTop;
		}
		return new StyleGrabberPlace(left, top, left + w, top + h);
	}

	/** Slot index for a tap in a panel laid out as {@code slots} equal rows. */
	public static int rowAt(final float y, final float top, final float height,
			final int slots) {
		if (slots <= 0 || height <= 0f) {
			return 0;
		}
		int row = (int) ((y - top) / (height / slots));
		if (row < 0) {
			return 0;
		}
		if (row >= slots) {
			return slots - 1;
		}
		return row;
	}

	public static boolean hitSquare(final float x, final float y, final float left,
			final float top, final float size) {
		return x >= left && y >= top && x <= left + size && y <= top + size;
	}

	/**
	 * Idle close is top-left of the Window. The overflow ⋮ sits in
	 * {@code gameplay_chrome_overlay} (bottom-end), above this view.
	 */
	public static float idleCloseLeft(final float viewW, final float size,
			final float margin) {
		return margin;
	}
}

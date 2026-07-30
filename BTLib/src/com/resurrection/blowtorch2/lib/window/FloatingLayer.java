package com.resurrection.blowtorch2.lib.window;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/**
 * Generic host for freely positioned children over the game area. Does not
 * steal touches between children — a miss returns {@code false} so text
 * selection / scroll underneath still work.
 *
 * <p>Clamping, chrome keep-out and IME policy live in
 * {@link FloatingButtonController} / {@link FloatingLayerGeometry}; this view
 * is only the pass-through container.
 */
public class FloatingLayer extends FrameLayout {

	public FloatingLayer(Context context) {
		super(context);
		init();
	}

	public FloatingLayer(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public FloatingLayer(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	private void init() {
		setClickable(false);
		setFocusable(false);
		setWillNotDraw(true);
		setClipChildren(false);
		setClipToPadding(false);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		return false;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// Empty space must reach the text underneath.
		return false;
	}
}

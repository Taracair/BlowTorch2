package com.resurrection.blowtorch2.lib.window;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.resurrection.blowtorch2.lib.window.SuperButtonGestures.BoundSwipes;

/**
 * One floating button: draw + gestures matching the grid
 * ({@link SuperButtonGestures}). Very-long-press ({@link SuperButtonGestures#MOVE_HOLD_MS})
 * enters move mode without firing hold; hold fires on release when deferred.
 */
public class FloatingButtonView extends View {

	interface Callbacks {
		void sendCommand(String text);

		void loadButtonSet(String name);

		void onFloatPositionChanged(int index, int x, int y);

		void onFloatDragFinished(int index, int x, int y);

		boolean showGestureHints();

		boolean hapticPressEnabled();

		boolean hapticFlipEnabled();

		int parentWidth();

		int maxBottomFor(FloatingButtonView view);

		void bringLayerUnderChrome();

		/**
		 * Put this view at {@code x},{@code y}. The controller decides what that
		 * means: margins inside the floating layer, or the position of this
		 * button's own overlay window. The gesture code must not care.
		 */
		void moveTo(FloatingButtonView view, int x, int y);

		/** Current {@code {x, y}} of this view in whichever space it lives in. */
		int[] positionOf(FloatingButtonView view);
	}

	private FloatingButtonModel model;
	private Callbacks callbacks;
	private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint calloutBg = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint calloutText = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF oval = new RectF();
	private final Handler handler = new Handler(Looper.getMainLooper());

	private boolean pressed;
	private boolean flippedVisual;
	private boolean fingerDown;
	private boolean multiTouchCancelled;
	private boolean enteredMoveMode;
	private boolean dragging;
	private boolean holdCancelledByMove;
	private long downUptime;
	private float downX;
	private float downY;
	private float lastX;
	private float lastY;
	private float swipeThresholdPx;
	private String previewDir;
	private String callout;
	private BoundSwipes bound;
	private float dragFingerOffsetX;
	private float dragFingerOffsetY;
	/** Maps screen top-left of the view into {@link Callbacks#positionOf} space. */
	private float positionSpaceDeltaX;
	private float positionSpaceDeltaY;
	private boolean dragOriginCaptured;

	private final Runnable enterMoveRunnable = new Runnable() {
		@Override
		public void run() {
			if (!fingerDown || multiTouchCancelled || dragging) {
				return;
			}
			enteredMoveMode = true;
			dragging = true;
			previewDir = null;
			callout = "move";
			invalidate();
			performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
		}
	};

	public FloatingButtonView(Context context) {
		super(context);
		textPaint.setTypeface(Typeface.DEFAULT_BOLD);
		textPaint.setTextAlign(Paint.Align.CENTER);
		framePaint.setStyle(Paint.Style.STROKE);
		arrowPaint.setStyle(Paint.Style.STROKE);
		arrowPaint.setStrokeWidth(3f);
		arrowPaint.setColor(0xFFFFFFFF);
		calloutBg.setColor(0xCC000000);
		calloutText.setColor(0xFFFFFFFF);
		calloutText.setTextAlign(Paint.Align.CENTER);
		calloutText.setTextSize(12f * getResources().getDisplayMetrics().scaledDensity);
		swipeThresholdPx = SuperButtonGestures.SWIPE_THRESHOLD_DP
				* getResources().getDisplayMetrics().density;
	}

	void bind(FloatingButtonModel model, Callbacks callbacks) {
		this.model = model;
		this.callbacks = callbacks;
		this.bound = model != null ? model.boundSwipes() : null;
		requestLayout();
		invalidate();
	}

	FloatingButtonModel getModel() {
		return model;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		float density = getResources().getDisplayMetrics().density;
		int w = (int) Math.ceil((model != null ? model.widthDp : 80f) * density);
		int h = (int) Math.ceil((model != null ? model.heightDp : 80f) * density);
		setMeasuredDimension(w, h);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (model == null) {
			return;
		}
		int bg = pressed ? model.selectedColor
				: (flippedVisual ? model.flipColor : model.primaryColor);
		int fg = flippedVisual ? model.flipLabelColor : model.labelColor;
		fillPaint.setColor(bg);
		oval.set(0, 0, getWidth(), getHeight());
		if (model.floatRound) {
			canvas.drawOval(oval, fillPaint);
		} else {
			canvas.drawRect(oval, fillPaint);
		}
		if (model.floatFrame) {
			// High-contrast outline so the floater stays readable over game text.
			int outline = contrastingOutline(fg, bg);
			framePaint.setColor(outline);
			framePaint.setStrokeWidth(Math.max(2f,
					getResources().getDisplayMetrics().density * 2f));
			float inset = framePaint.getStrokeWidth() / 2f;
			RectF frame = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
			if (model.floatRound) {
				canvas.drawOval(frame, framePaint);
			} else {
				canvas.drawRect(frame, framePaint);
			}
		}
		String text = flippedVisual && model.flipLabel != null && model.flipLabel.length() > 0
				? model.flipLabel : model.label;
		if (text == null) {
			text = "";
		}
		textPaint.setColor(fg);
		textPaint.setTextSize(model.labelSizeSp * getResources().getDisplayMetrics().scaledDensity);
		Paint.FontMetrics fm = textPaint.getFontMetrics();
		float textY = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
		canvas.drawText(text, getWidth() / 2f, textY, textPaint);

		boolean hintsOn = callbacks != null && callbacks.showGestureHints()
				&& model.showGestureLabel;
		if (hintsOn) {
			drawStaticGestureHints(canvas);
		}
		if (previewDir != null) {
			drawPreviewArrow(canvas, previewDir);
		}
		// Callout stays inside the view: overlay windows are sized to the button,
		// so drawing above y=0 is clipped (and was invisible on Mode A).
		if (callout != null && callout.length() > 0 && hintsOn) {
			float density = getResources().getDisplayMetrics().density;
			float pad = 4f * density;
			String label = callout.length() > 28 ? callout.substring(0, 27) + "…" : callout;
			calloutText.setColor(0xFFFFFFFF);
			calloutText.setTextAlign(Paint.Align.CENTER);
			float tw = Math.min(getWidth() - pad, calloutText.measureText(label) + pad * 2f);
			float th = calloutText.getTextSize() + pad;
			float left = Math.max(0, (getWidth() - tw) / 2f);
			float top = pad;
			canvas.drawRect(left, top, left + tw, top + th, calloutBg);
			canvas.drawText(label, left + tw / 2f,
					top + th - pad / 2f - calloutText.descent(), calloutText);
		}
	}

	/**
	 * Same compass as the grid tile ({@code BUTTON:drawGestureIndicators}): U/D/L/R
	 * on edge midpoints, small arrows in the corners, Hold in the bottom-right.
	 */
	private void drawStaticGestureHints(Canvas canvas) {
		float density = getResources().getDisplayMetrics().density;
		float w = getWidth();
		float h = getHeight();
		float inset = Math.max(4f * density, Math.min(w, h) * 0.08f);
		float arrow = Math.max(6f * density, Math.min(w, h) * 0.12f);
		int color = 0x96FFFFFF;
		Paint hint = calloutText;
		float prevSize = hint.getTextSize();
		Paint.Align prevAlign = hint.getTextAlign();
		int prevColor = hint.getColor();
		hint.setTextAlign(Paint.Align.LEFT);
		hint.setTextSize(Math.max(7f * density, arrow * 1.35f));
		hint.setColor(color);
		float midX = w * 0.5f;
		float midY = h * 0.5f;
		Paint.FontMetrics fm = hint.getFontMetrics();
		float baseline = -fm.ascent;

		if (hasSwipe(SuperButtonGestures.DIR_UP)) {
			canvas.drawText("U", midX - arrow * 0.4f, inset + arrow * 0.35f + baseline * 0.35f, hint);
		}
		if (hasSwipe(SuperButtonGestures.DIR_DOWN)) {
			canvas.drawText("D", midX - arrow * 0.4f, h - inset, hint);
		}
		if (hasSwipe(SuperButtonGestures.DIR_LEFT)) {
			canvas.drawText("L", inset, midY + arrow * 0.35f, hint);
		}
		if (hasSwipe(SuperButtonGestures.DIR_RIGHT)) {
			canvas.drawText("R", w - inset - arrow * 0.6f, midY + arrow * 0.35f, hint);
		}

		float corner = inset + arrow * 0.55f;
		drawCornerHint(canvas, SuperButtonGestures.DIR_UP_LEFT, corner, corner, arrow);
		drawCornerHint(canvas, SuperButtonGestures.DIR_UP_RIGHT, w - corner, corner, arrow);
		drawCornerHint(canvas, SuperButtonGestures.DIR_DOWN_LEFT, corner, h - corner, arrow);
		drawCornerHint(canvas, SuperButtonGestures.DIR_DOWN_RIGHT, w - corner, h - corner, arrow);

		if (model.holdCommand != null && model.holdCommand.length() > 0) {
			hint.setColor(0xAAFFFF66);
			hint.setTextSize(Math.max(7f * density, arrow * 1.2f));
			hint.setTextAlign(Paint.Align.RIGHT);
			canvas.drawText("Hold", w - 4f * density, h - 4f * density, hint);
		}

		hint.setTextSize(prevSize);
		hint.setTextAlign(prevAlign);
		hint.setColor(prevColor);
	}

	private boolean hasSwipe(String dir) {
		return model.commandForDirection(dir) != null;
	}

	private void drawCornerHint(Canvas canvas, String dir, float cx, float cy, float size) {
		if (!hasSwipe(dir)) {
			return;
		}
		float dx = 0f;
		float dy = 0f;
		if (SuperButtonGestures.DIR_UP_LEFT.equals(dir)
				|| SuperButtonGestures.DIR_UP_RIGHT.equals(dir)) {
			dy = -1f;
		} else {
			dy = 1f;
		}
		if (SuperButtonGestures.DIR_UP_LEFT.equals(dir)
				|| SuperButtonGestures.DIR_DOWN_LEFT.equals(dir)) {
			dx = -1f;
		} else {
			dx = 1f;
		}
		float mag = (float) Math.hypot(dx, dy);
		dx = dx / mag * size * 0.5f;
		dy = dy / mag * size * 0.5f;
		arrowPaint.setColor(0x96FFFFFF);
		arrowPaint.setStrokeWidth(Math.max(2f, size * 0.18f));
		canvas.drawLine(cx - dx * 0.4f, cy - dy * 0.4f, cx + dx, cy + dy, arrowPaint);
	}

	private void drawPreviewArrow(Canvas canvas, String dir) {
		float cx = getWidth() / 2f;
		float cy = getHeight() / 2f;
		float len = Math.min(getWidth(), getHeight()) * 0.35f;
		float dx = 0;
		float dy = 0;
		if (SuperButtonGestures.DIR_UP.equals(dir) || SuperButtonGestures.DIR_UP_LEFT.equals(dir)
				|| SuperButtonGestures.DIR_UP_RIGHT.equals(dir)) {
			dy = -1;
		}
		if (SuperButtonGestures.DIR_DOWN.equals(dir) || SuperButtonGestures.DIR_DOWN_LEFT.equals(dir)
				|| SuperButtonGestures.DIR_DOWN_RIGHT.equals(dir)) {
			dy = 1;
		}
		if (SuperButtonGestures.DIR_LEFT.equals(dir) || SuperButtonGestures.DIR_UP_LEFT.equals(dir)
				|| SuperButtonGestures.DIR_DOWN_LEFT.equals(dir)) {
			dx = -1;
		}
		if (SuperButtonGestures.DIR_RIGHT.equals(dir) || SuperButtonGestures.DIR_UP_RIGHT.equals(dir)
				|| SuperButtonGestures.DIR_DOWN_RIGHT.equals(dir)) {
			dx = 1;
		}
		float mag = (float) Math.hypot(dx, dy);
		if (mag < 0.001f) {
			return;
		}
		dx = dx / mag * len;
		dy = dy / mag * len;
		arrowPaint.setColor(0xFFFFFFFF);
		arrowPaint.setStrokeWidth(3f);
		canvas.drawLine(cx, cy, cx + dx, cy + dy, arrowPaint);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (model == null || callbacks == null) {
			return false;
		}
		int masked = event.getActionMasked();
		if (masked == MotionEvent.ACTION_POINTER_DOWN) {
			if (fingerDown) {
				multiTouchCancelled = true;
				cancelGesture();
				return true;
			}
		}
		switch (masked) {
			case MotionEvent.ACTION_DOWN:
				return onDown(event);
			case MotionEvent.ACTION_MOVE:
				return onMove(event);
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				return onUp(event, masked == MotionEvent.ACTION_CANCEL);
			default:
				return fingerDown;
		}
	}

	private boolean onDown(MotionEvent event) {
		multiTouchCancelled = false;
		enteredMoveMode = false;
		dragging = false;
		holdCancelledByMove = false;
		dragOriginCaptured = false;
		fingerDown = true;
		pressed = true;
		flippedVisual = false;
		previewDir = null;
		downUptime = SystemClock.uptimeMillis();
		downX = event.getRawX();
		downY = event.getRawY();
		lastX = downX;
		lastY = downY;
		callout = null;
		if (model.holdCommand != null && model.holdCommand.length() > 0) {
			callout = "hold  " + model.holdCommand;
		}
		handler.removeCallbacks(enterMoveRunnable);
		handler.postDelayed(enterMoveRunnable, SuperButtonGestures.MOVE_HOLD_MS);
		if (callbacks.hapticPressEnabled()) {
			performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
		}
		invalidate();
		return true;
	}

	private boolean onMove(MotionEvent event) {
		if (!fingerDown || multiTouchCancelled) {
			return false;
		}
		float rawX = event.getRawX();
		float rawY = event.getRawY();
		float dx = rawX - downX;
		float dy = rawY - downY;
		lastX = rawX;
		lastY = rawY;

		if (dragging) {
			moveByFinger(rawX, rawY);
			return true;
		}

		float density = getResources().getDisplayMetrics().density;
		float moveDist = (float) Math.hypot(dx, dy);
		if (SuperButtonGestures.shouldCancelHoldForMove(moveDist, density)) {
			holdCancelledByMove = true;
		}

		float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
		if (moveDist > Math.max(slop, swipeThresholdPx)) {
			handler.removeCallbacks(enterMoveRunnable);
		}

		String dir = SuperButtonGestures.resolveSwipeDirection(bound, dx, dy, swipeThresholdPx);
		boolean outside = !containsLocal(event.getX(), event.getY());
		flippedVisual = outside && dir == null;
		previewDir = dir;
		callout = buildCallout(dir, outside);
		invalidate();
		return true;
	}

	private void moveByFinger(float rawX, float rawY) {
		// Overlay windows are parented by ViewRootImpl, not a View — casting
		// getParent() crashed on the first drag move after long-press.
		if (!dragOriginCaptured) {
			int[] at = callbacks.positionOf(this);
			int[] loc = new int[2];
			getLocationOnScreen(loc);
			dragFingerOffsetX = rawX - loc[0];
			dragFingerOffsetY = rawY - loc[1];
			positionSpaceDeltaX = at[0] - loc[0];
			positionSpaceDeltaY = at[1] - loc[1];
			dragOriginCaptured = true;
		}
		int x = Math.round(rawX - dragFingerOffsetX + positionSpaceDeltaX);
		int y = Math.round(rawY - dragFingerOffsetY + positionSpaceDeltaY);
		int maxBottom = callbacks.maxBottomFor(this);
		x = FloatingLayerGeometry.clampX(x, getWidth(), callbacks.parentWidth());
		y = FloatingLayerGeometry.clampY(y, getHeight(), maxBottom);
		callbacks.moveTo(this, x, y);
		callbacks.onFloatPositionChanged(model.index, x, y);
		callbacks.bringLayerUnderChrome();
	}

	private boolean onUp(MotionEvent event, boolean cancelled) {
		handler.removeCallbacks(enterMoveRunnable);
		if (!fingerDown) {
			return false;
		}
		fingerDown = false;
		long duration = SystemClock.uptimeMillis() - downUptime;
		float dx = (cancelled ? lastX : event.getRawX()) - downX;
		float dy = (cancelled ? lastY : event.getRawY()) - downY;
		boolean outside = !containsLocal(
				cancelled ? getWidth() / 2f : event.getX(),
				cancelled ? getHeight() / 2f : event.getY());

		if (multiTouchCancelled || cancelled) {
			resetVisual();
			dragOriginCaptured = false;
			return true;
		}

		if (dragging || enteredMoveMode) {
			int[] at = callbacks.positionOf(this);
			callbacks.onFloatDragFinished(model.index, at[0], at[1]);
			resetVisual();
			dragOriginCaptured = false;
			return true;
		}

		String dir = SuperButtonGestures.resolveSwipeDirection(bound, dx, dy, swipeThresholdPx);
		if (dir != null) {
			String cmd = model.commandForDirection(dir);
			if (cmd != null) {
				callbacks.sendCommand(cmd);
			}
		} else if (SuperButtonGestures.shouldFireHoldOnRelease(duration, enteredMoveMode,
				holdCancelledByMove)
				&& model.holdCommand != null && model.holdCommand.length() > 0) {
			callbacks.sendCommand(model.holdCommand);
		} else if (outside && model.flipCommand != null && model.flipCommand.length() > 0) {
			callbacks.sendCommand(model.flipCommand);
			if (callbacks.hapticFlipEnabled()) {
				performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
			}
		} else if (!outside) {
			if (model.switchTo != null && model.switchTo.length() > 0) {
				callbacks.loadButtonSet(model.switchTo);
			} else if (model.command != null && model.command.length() > 0) {
				callbacks.sendCommand(model.command);
			}
		}
		resetVisual();
		dragOriginCaptured = false;
		return true;
	}

	private void cancelGesture() {
		handler.removeCallbacks(enterMoveRunnable);
		fingerDown = false;
		dragging = false;
		enteredMoveMode = false;
		dragOriginCaptured = false;
		resetVisual();
	}

	private void resetVisual() {
		pressed = false;
		flippedVisual = false;
		previewDir = null;
		callout = null;
		invalidate();
	}

	private boolean containsLocal(float x, float y) {
		return x >= 0 && y >= 0 && x < getWidth() && y < getHeight();
	}

	private String buildCallout(String dir, boolean outside) {
		if (dir != null) {
			String cmd = model.commandForDirection(dir);
			if (cmd != null) {
				return glyph(dir) + "  " + cmd;
			}
		}
		if (outside && model.flipCommand != null && model.flipCommand.length() > 0) {
			return model.flipCommand;
		}
		if (dir == null && !outside && model.holdCommand != null
				&& model.holdCommand.length() > 0) {
			return "hold  " + model.holdCommand;
		}
		return null;
	}

	private static String glyph(String dir) {
		if (SuperButtonGestures.DIR_UP.equals(dir)) {
			return "↑";
		}
		if (SuperButtonGestures.DIR_DOWN.equals(dir)) {
			return "↓";
		}
		if (SuperButtonGestures.DIR_LEFT.equals(dir)) {
			return "←";
		}
		if (SuperButtonGestures.DIR_RIGHT.equals(dir)) {
			return "→";
		}
		if (SuperButtonGestures.DIR_UP_LEFT.equals(dir)) {
			return "↖";
		}
		if (SuperButtonGestures.DIR_UP_RIGHT.equals(dir)) {
			return "↗";
		}
		if (SuperButtonGestures.DIR_DOWN_LEFT.equals(dir)) {
			return "↙";
		}
		if (SuperButtonGestures.DIR_DOWN_RIGHT.equals(dir)) {
			return "↘";
		}
		return "";
	}

	/** Light outline on dark fill (and the reverse) so the border is visible. */
	private static int contrastingOutline(int labelColor, int fillColor) {
		int a = (fillColor >> 24) & 0xFF;
		int r = (fillColor >> 16) & 0xFF;
		int g = (fillColor >> 8) & 0xFF;
		int b = fillColor & 0xFF;
		// Perceived luminance; translucent fills count as dark over game text.
		double lum = (0.299 * r + 0.587 * g + 0.114 * b) * (a / 255.0);
		if (lum < 140) {
			return 0xE0FFFFFF;
		}
		return 0xE0000000;
	}
}

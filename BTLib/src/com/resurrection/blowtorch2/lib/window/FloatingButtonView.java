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
		 * Put the visible button's top-left at {@code x},{@code y} — the same
		 * space as stored {@code floatX}/{@code floatY}. The view itself is
		 * larger (hint-padding margin, see {@link #hintPadLeftPx()} /
		 * {@link #hintPadTopPx()}), so the controller turns this into layer
		 * margins or the dual overlay pair (touch window at the button;
		 * visual window offset by that padding). The gesture code must not care.
		 */
		void moveTo(FloatingButtonView view, int x, int y);

		/**
		 * Current {@code {x, y}} of the visible button's top-left, in whichever
		 * space this view lives in — already in {@code floatX}/{@code floatY}
		 * space (layer: margin + pad; overlay: touch-window x/y).
		 */
		int[] positionOf(FloatingButtonView view);
	}

	/**
	 * Room reserved outside the visible button for gesture hints, mirroring
	 * {@code BUTTON:drawGestureIndicators} / {@code drawGestureLabel} on the
	 * grid — there they draw on a shared full-window canvas that nothing
	 * clips. A floating button instead gets its own overlay windows
	 * ({@code TYPE_APPLICATION_OVERLAY}), so anything drawn past a
	 * button-sized window used to be clipped. The view is therefore larger
	 * than the button and draws hints in the margin.
	 *
	 * <p>In overlay mode the controller hosts this padded view in a
	 * {@code FLAG_NOT_TOUCHABLE} window and a separate button-sized touchable
	 * proxy — a single padded touchable window would swallow keyboard taps in
	 * the hint band ({@code FLAG_NOT_TOUCH_MODAL} only passes touches outside
	 * the window rectangle). In the in-app {@link FloatingLayer} the padded
	 * view is the only child and returning {@code false} from
	 * {@link #onTouchEvent} lets the layer pass padding taps through.
	 *
	 * <p>Left/right are equal on purpose: it keeps every "horizontal centre of
	 * the button" calculation ({@code getWidth() / 2f}) correct without a
	 * separate button-centre offset. Top is much larger than the sides because
	 * it also has to fit the press callout box above the button, not just an
	 * edge letter.
	 */
	private static final float HINT_PAD_SIDE_DP = 14f;
	private static final float HINT_PAD_BOTTOM_DP = 18f;
	private static final float HINT_PAD_TOP_DP = 44f;

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
	/** Pixel size of the hint padding band, fixed per view (density only). */
	private final int padLeftPx;
	private final int padTopPx;
	private final int padRightPx;
	private final int padBottomPx;
	/** Pixel size of the visible button itself, recomputed on every measure. */
	private int buttonWidthPx;
	private int buttonHeightPx;

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
		float density = getResources().getDisplayMetrics().density;
		swipeThresholdPx = SuperButtonGestures.SWIPE_THRESHOLD_DP * density;
		padLeftPx = Math.round(HINT_PAD_SIDE_DP * density);
		padRightPx = Math.round(HINT_PAD_SIDE_DP * density);
		padTopPx = Math.round(HINT_PAD_TOP_DP * density);
		padBottomPx = Math.round(HINT_PAD_BOTTOM_DP * density);
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
		buttonWidthPx = (int) Math.ceil((model != null ? model.widthDp : 80f) * density);
		buttonHeightPx = (int) Math.ceil((model != null ? model.heightDp : 80f) * density);
		// The view is bigger than the button so hints have somewhere to draw
		// (see HINT_PAD_*); the button itself keeps its configured size.
		setMeasuredDimension(
				buttonWidthPx + padLeftPx + padRightPx,
				buttonHeightPx + padTopPx + padBottomPx);
	}

	/** Pixel width of the visible button, excluding the hint padding band. */
	int buttonWidthPx() {
		return buttonWidthPx;
	}

	/** Pixel height of the visible button, excluding the hint padding band. */
	int buttonHeightPx() {
		return buttonHeightPx;
	}

	/** Left edge of the visible button within this view, i.e. the hint padding width. */
	int hintPadLeftPx() {
		return padLeftPx;
	}

	/** Top edge of the visible button within this view, i.e. the hint padding height. */
	int hintPadTopPx() {
		return padTopPx;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (model == null) {
			return;
		}
		// The button occupies an inset rect, not the whole view — the margin
		// around it is the hint padding band (HINT_PAD_*).
		float left = padLeftPx;
		float top = padTopPx;
		float right = left + buttonWidthPx;
		float bottom = top + buttonHeightPx;
		int bg = pressed ? model.selectedColor
				: (flippedVisual ? model.flipColor : model.primaryColor);
		int fg = flippedVisual ? model.flipLabelColor : model.labelColor;
		fillPaint.setColor(bg);
		oval.set(left, top, right, bottom);
		if (model.floatRound) {
			canvas.drawOval(oval, fillPaint);
		} else {
			canvas.drawRect(oval, fillPaint);
		}
		// Border (player colour) wins over floatFrame (auto-contrast). Drawing
		// both stacked a second outline on MED/SUT-style floaters.
		boolean strokeChrome = model.border || model.floatFrame;
		if (strokeChrome) {
			int outline = model.border
					? model.borderColor
					: contrastingOutline(fg, bg);
			framePaint.setColor(outline);
			framePaint.setStrokeWidth(Math.max(2f,
					getResources().getDisplayMetrics().density * 2f));
			float inset = framePaint.getStrokeWidth() / 2f;
			RectF frame = new RectF(left + inset, top + inset, right - inset, bottom - inset);
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
		float midX = (left + right) / 2f;
		float midY = (top + bottom) / 2f;
		textPaint.setColor(fg);
		textPaint.setTextSize(model.labelSizeSp * getResources().getDisplayMetrics().scaledDensity);
		Paint.FontMetrics fm = textPaint.getFontMetrics();
		float textY = midY - (fm.ascent + fm.descent) / 2f;
		canvas.drawText(text, midX, textY, textPaint);

		boolean hintsOn = callbacks != null && callbacks.showGestureHints()
				&& model.showGestureLabel;
		if (hintsOn) {
			drawStaticGestureHints(canvas, left, top, right, bottom);
		}
		if (previewDir != null) {
			drawPreviewArrow(canvas, previewDir, midX, midY);
		}
		// Press callout sits above the button, in the padding band — same
		// placement as BUTTON:drawGestureLabel on the grid. Overlay hosting
		// sizes a NOT_TOUCHABLE window to include this band
		// (FloatingButtonController dual-window).
		if (callout != null && callout.length() > 0 && hintsOn) {
			drawCallout(canvas, left, top, right);
		}
	}

	private void drawCallout(Canvas canvas, float buttonLeft, float buttonTop, float buttonRight) {
		float density = getResources().getDisplayMetrics().density;
		float pad = 4f * density;
		float gap = 6f * density;
		String label = callout.length() > 28 ? callout.substring(0, 27) + "…" : callout;
		calloutText.setColor(0xFFFFFFFF);
		calloutText.setTextAlign(Paint.Align.CENTER);
		float tw = Math.min(getWidth() - pad, calloutText.measureText(label) + pad * 2f);
		float th = calloutText.getTextSize() + pad;
		float midX = (buttonLeft + buttonRight) / 2f;
		float left = Math.max(0, Math.min(getWidth() - tw, midX - tw / 2f));
		float bottomEdge = buttonTop - gap;
		float top = Math.max(0, bottomEdge - th);
		canvas.drawRect(left, top, left + tw, top + th, calloutBg);
		canvas.drawText(label, left + tw / 2f,
				top + th - pad / 2f - calloutText.descent(), calloutText);
	}

	/**
	 * Same compass as the grid tile ({@code BUTTON:drawGestureIndicators}): U/D/L/R
	 * on edge midpoints, small arrows in the corners, Hold in the bottom-right —
	 * but drawn just <em>outside</em> {@code [left,top,right,bottom]}, in the hint
	 * padding band, rather than inset inside it. The grid can inset them inside
	 * the tile because tiles are large; a floating button can be as small as a
	 * thumb, where an inset hint collides with the button's own label (the
	 * "only a D shows" symptom this fixes).
	 */
	private void drawStaticGestureHints(Canvas canvas, float left, float top,
			float right, float bottom) {
		float density = getResources().getDisplayMetrics().density;
		float w = right - left;
		float h = bottom - top;
		float gap = Math.max(2f * density, Math.min(w, h) * 0.04f);
		float arrow = Math.max(6f * density, Math.min(w, h) * 0.12f);
		int color = 0x96FFFFFF;
		Paint hint = calloutText;
		float prevSize = hint.getTextSize();
		Paint.Align prevAlign = hint.getTextAlign();
		int prevColor = hint.getColor();
		hint.setTextSize(Math.max(7f * density, arrow * 1.35f));
		hint.setColor(color);
		float midX = (left + right) * 0.5f;
		float midY = (top + bottom) * 0.5f;
		Paint.FontMetrics fm = hint.getFontMetrics();
		float vCenterOffset = -(fm.ascent + fm.descent) / 2f;

		if (hasSwipe(SuperButtonGestures.DIR_UP)) {
			hint.setTextAlign(Paint.Align.CENTER);
			canvas.drawText("U", midX, top - gap, hint);
		}
		if (hasSwipe(SuperButtonGestures.DIR_DOWN)) {
			hint.setTextAlign(Paint.Align.CENTER);
			canvas.drawText("D", midX, bottom + gap - fm.ascent, hint);
		}
		if (hasSwipe(SuperButtonGestures.DIR_LEFT)) {
			hint.setTextAlign(Paint.Align.RIGHT);
			canvas.drawText("L", left - gap, midY + vCenterOffset, hint);
		}
		if (hasSwipe(SuperButtonGestures.DIR_RIGHT)) {
			hint.setTextAlign(Paint.Align.LEFT);
			canvas.drawText("R", right + gap, midY + vCenterOffset, hint);
		}

		float cornerOffset = gap + arrow * 0.5f;
		drawCornerHint(canvas, SuperButtonGestures.DIR_UP_LEFT,
				left - cornerOffset, top - cornerOffset, arrow);
		drawCornerHint(canvas, SuperButtonGestures.DIR_UP_RIGHT,
				right + cornerOffset, top - cornerOffset, arrow);
		drawCornerHint(canvas, SuperButtonGestures.DIR_DOWN_LEFT,
				left - cornerOffset, bottom + cornerOffset, arrow);
		drawCornerHint(canvas, SuperButtonGestures.DIR_DOWN_RIGHT,
				right + cornerOffset, bottom + cornerOffset, arrow);

		if (model.holdCommand != null && model.holdCommand.length() > 0) {
			hint.setColor(0xAAFFFF66);
			hint.setTextSize(Math.max(7f * density, arrow * 1.2f));
			hint.setTextAlign(Paint.Align.RIGHT);
			canvas.drawText("Hold", right, bottom + gap - fm.ascent, hint);
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

	private void drawPreviewArrow(Canvas canvas, String dir, float cx, float cy) {
		// Sized from the button, not the padded view, so the arrow stays inside
		// the button like before — only the static hints moved outward.
		float len = Math.min(buttonWidthPx, buttonHeightPx) * 0.35f;
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
		if (!containsLocal(event.getX(), event.getY())) {
			// Hint-padding miss (in-app layer only — overlay delivers events
			// through a button-sized proxy, so this path is unused there).
			// Returning false lets the FloatingLayer pass the tap through.
			return false;
		}
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
		// Clamp the button itself, not the padded view, so the hint band can
		// run to (or past) the screen edge without the button stopping short.
		x = FloatingLayerGeometry.clampX(x, buttonWidthPx, callbacks.parentWidth());
		y = FloatingLayerGeometry.clampY(y, buttonHeightPx, maxBottom);
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
				cancelled ? padLeftPx + buttonWidthPx / 2f : event.getX(),
				cancelled ? padTopPx + buttonHeightPx / 2f : event.getY());

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

	/** True when {@code x,y} (view-local) falls inside the visible button, not the hint padding band. */
	private boolean containsLocal(float x, float y) {
		return x >= padLeftPx && y >= padTopPx
				&& x < padLeftPx + buttonWidthPx && y < padTopPx + buttonHeightPx;
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

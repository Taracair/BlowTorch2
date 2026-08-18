/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.gauge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.resurrection.blowtorch2.lib.window.SuperButtonGestures;

/**
 * One overlay gauge: draw via {@link GaugePainter}. Sticky edit mode
 * ({@link GaugeWidgetEditGestures}): not editing, the widget is fixed and
 * tap/swipe fire as usual; a {@link GaugeWidgetEditGestures#EDIT_HOLD_MS}
 * long-press enters edit (no hold command). Editing draws a border and the
 * resize grip; drag moves, grip resizes, a short tap exits without firing tap.
 *
 * <p>Opacity is {@link View#setAlpha} only, same as floating buttons — paints
 * stay fully opaque so they are not faded twice.
 *
 * <p>{@link GaugeWidget.ImeMode} is ignored here (stay / hide / overlay is
 * the controller's job). Command strings are not dispatched here.
 */
public class GaugeWidgetView extends View {

	public interface Callbacks {
		void onTap(String id);

		/** {@code dir} is {@code up}/{@code down}/{@code left}/{@code right}. */
		void onSwipe(String id, String dir);

		void onHold(String id);

		/** Proposed top-left in screen coordinates (raw / LAYOUT_IN_SCREEN). */
		void onMove(String id, int x, int y);

		void onResize(String id, int w, int h);

		void onMoveFinished(String id);

		void onResizeFinished(String id);

		void onEnterEdit(String id);

		void onExitEdit(String id);
	}

	private final GaugePainter painter = new GaugePainter();
	private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint gripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint editBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Rect gaugeBounds = new Rect();
	private final Path gripPath = new Path();
	private final Handler handler = new Handler(Looper.getMainLooper());

	private String gaugeId = "";
	private String shape = GaugePainter.SHAPE_HBAR;
	private String label = "";
	private boolean showLabel = true;
	private boolean showValue = true;
	private double value;
	private double max = GaugeWidget.DEFAULT_LIVE_MAX;
	private boolean low;
	private int fillColor = GaugeWidget.DEFAULT_COLOR_FILL;
	private int trackColor = GaugeWidget.DEFAULT_COLOR_TRACK;
	private int warnColor = GaugeWidget.DEFAULT_COLOR_WARN;
	private Callbacks callbacks;

	private boolean fingerDown;
	private boolean multiTouchCancelled;
	private boolean editing;
	private boolean dragging;
	private boolean resizing;
	private boolean holdCancelledByMove;
	private boolean suppressReleaseCommands;
	private long downUptime;
	private float downRawX;
	private float downRawY;
	private float lastRawX;
	private float lastRawY;
	private int startScreenX;
	private int startScreenY;
	private int startW;
	private int startH;
	private float swipeThresholdPx;

	private final Runnable editHoldRunnable = new Runnable() {
		@Override
		public void run() {
			if (!fingerDown || multiTouchCancelled || resizing) {
				return;
			}
			if (holdCancelledByMove) {
				return;
			}
			long duration = SystemClock.uptimeMillis() - downUptime;
			if (GaugeWidgetEditGestures.shouldExitEditOnHold(editing, false, false,
					duration)) {
				dragging = false;
				suppressReleaseCommands = true;
				fireExitEdit();
				return;
			}
			if (!GaugeWidgetEditGestures.shouldEnterEditOnHold(editing, false, false,
					duration)) {
				return;
			}
			fireEnterEdit();
			dragging = true;
			captureStartScreen();
			if (callbacks != null) {
				callbacks.onMove(gaugeId, startScreenX, startScreenY);
			}
		}
	};

	public GaugeWidgetView(final Context context) {
		this(context, null);
	}

	public GaugeWidgetView(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		labelPaint.setColor(0xFFFFFFFF);
		labelPaint.setTextAlign(Paint.Align.LEFT);
		valuePaint.setColor(0xFFFFFFFF);
		valuePaint.setTextAlign(Paint.Align.CENTER);
		valuePaint.setShadowLayer(3f, 0f, 0f, 0xCC000000);
		gripPaint.setColor(0xAAFFFFFF);
		gripPaint.setStyle(Paint.Style.FILL);
		editBorderPaint.setColor(0xFFFFCC44);
		editBorderPaint.setStyle(Paint.Style.STROKE);
		float density = safeDensity();
		swipeThresholdPx = SuperButtonGestures.SWIPE_THRESHOLD_DP * density;
	}

	public void setCallbacks(final Callbacks callbacks) {
		this.callbacks = callbacks;
	}

	public void setEditing(final boolean editing) {
		if (this.editing == editing) {
			return;
		}
		this.editing = editing;
		invalidate();
	}

	public boolean isEditing() {
		return editing;
	}

	public void bind(final String id, final GaugeWidget.Shape shape, final String label,
			final boolean showLabel, final boolean showValue, final int opacity) {
		bind(id, shape != null ? shape.toJsonValue() : GaugePainter.SHAPE_HBAR,
				label, showLabel, showValue, opacity);
	}

	public void bind(final String id, final String shape, final String label,
			final boolean showLabel, final boolean showValue, final int opacity) {
		this.gaugeId = id != null ? id : "";
		this.shape = GaugePainter.canonicalizeShape(shape);
		this.label = label != null ? label : "";
		this.showLabel = showLabel;
		this.showValue = showValue;
		int op = opacity;
		if (op < 0) {
			op = 0;
		} else if (op > 100) {
			op = 100;
		}
		setAlpha(op / 100f);
		invalidate();
	}

	/**
	 * Live numbers for painting. For {@link GaugeWidget.Shape#TIMER} pass
	 * {@code remainSec} as {@code value} and {@code durationSec} as {@code max}
	 * (same as {@link GaugeWidget#ratio()}). {@code low} selects warn fill.
	 */
	public void setAmounts(final double value, final double max, final boolean low,
			final int fill, final int track, final int warn) {
		this.value = value;
		this.max = max;
		this.low = low;
		this.fillColor = fill;
		this.trackColor = track;
		this.warnColor = warn;
		invalidate();
	}

	public String getGaugeId() {
		return gaugeId;
	}

	/** View-local coordinates. Bottom-right {@link GaugeResizeGrip#GRIP_DP} square. */
	public boolean hitResizeGrip(final float x, final float y) {
		return GaugeResizeGrip.contains(x, y, getWidth(), getHeight(),
				GaugeResizeGrip.gripPx(safeDensity()));
	}

	@Override
	protected void onDraw(final Canvas canvas) {
		super.onDraw(canvas);
		int w = getWidth();
		int h = getHeight();
		if (w <= 0 || h <= 0) {
			return;
		}
		float density = safeDensity();
		float sDensity = getResources().getDisplayMetrics().scaledDensity;
		if (sDensity <= 0f) {
			sDensity = density;
		}
		labelPaint.setTextSize(11f * sDensity);
		valuePaint.setTextSize(Math.max(10f * sDensity, Math.min(w, h) * 0.22f));
		layoutGaugeBounds(w, h, density);
		float ratio = GaugePainter.ratio(value, max);
		int fill = GaugePainter.resolveFillColor(low, fillColor, warnColor);
		painter.paint(canvas, shape, gaugeBounds, ratio, fill, trackColor, 0);
		drawCaptions(canvas, density);
		if (GaugeWidgetEditGestures.shouldDrawEditChrome(editing)) {
			drawEditChrome(canvas, density);
			drawResizeGrip(canvas);
		}
	}

	private void layoutGaugeBounds(final int w, final int h, final float density) {
		boolean ringish = GaugePainter.SHAPE_RING.equals(shape)
				|| GaugePainter.SHAPE_TIMER.equals(shape);
		if (ringish) {
			gaugeBounds.set(0, 0, w, h);
			return;
		}
		float pad = 4f * density;
		if (GaugePainter.SHAPE_VBAR.equals(shape)) {
			int top = 0;
			if (showLabel && label.length() > 0) {
				top = Math.round(labelPaint.getTextSize() + pad);
				if (top > h / 2) {
					top = h / 3;
				}
			}
			gaugeBounds.set(0, top, w, h);
			return;
		}
		int left = 0;
		if (showLabel && label.length() > 0) {
			left = Math.round(labelPaint.measureText(label) + pad * 2f);
			if (left > w / 2) {
				left = w / 3;
			}
		}
		gaugeBounds.set(left, 0, w, h);
	}

	private void drawCaptions(final Canvas canvas, final float density) {
		boolean ringish = GaugePainter.SHAPE_RING.equals(shape)
				|| GaugePainter.SHAPE_TIMER.equals(shape);
		String amount = showValue ? GaugePainter.formatAmount(value, max) : null;
		boolean hasLabel = showLabel && label.length() > 0;
		if (!hasLabel && amount == null) {
			return;
		}
		if (ringish) {
			drawRingCaptions(canvas, hasLabel, amount);
			return;
		}
		if (hasLabel) {
			Paint.FontMetrics fm = labelPaint.getFontMetrics();
			if (GaugePainter.SHAPE_VBAR.equals(shape)) {
				labelPaint.setTextAlign(Paint.Align.CENTER);
				float x = getWidth() / 2f;
				float y = -fm.ascent + 2f * density;
				canvas.drawText(label, x, y, labelPaint);
			} else {
				labelPaint.setTextAlign(Paint.Align.LEFT);
				float x = 2f * density;
				float midY = gaugeBounds.top + gaugeBounds.height() / 2f;
				float y = midY - (fm.ascent + fm.descent) / 2f;
				canvas.drawText(label, x, y, labelPaint);
			}
		}
		if (amount != null && gaugeBounds.width() > 0 && gaugeBounds.height() > 0) {
			valuePaint.setTextAlign(Paint.Align.CENTER);
			Paint.FontMetrics fm = valuePaint.getFontMetrics();
			float x = gaugeBounds.left + gaugeBounds.width() / 2f;
			float y = gaugeBounds.top + gaugeBounds.height() / 2f
					- (fm.ascent + fm.descent) / 2f;
			canvas.drawText(amount, x, y, valuePaint);
		}
	}

	private void drawRingCaptions(final Canvas canvas, final boolean hasLabel,
			final String amount) {
		float cx = getWidth() / 2f;
		float cy = getHeight() / 2f;
		labelPaint.setTextAlign(Paint.Align.CENTER);
		valuePaint.setTextAlign(Paint.Align.CENTER);
		if (hasLabel && amount != null) {
			Paint.FontMetrics lf = labelPaint.getFontMetrics();
			Paint.FontMetrics vf = valuePaint.getFontMetrics();
			float labelH = lf.descent - lf.ascent;
			float valueH = vf.descent - vf.ascent;
			float gap = 2f;
			float block = labelH + gap + valueH;
			float top = cy - block / 2f;
			canvas.drawText(label, cx, top - lf.ascent, labelPaint);
			canvas.drawText(amount, cx, top + labelH + gap - vf.ascent, valuePaint);
			return;
		}
		if (hasLabel) {
			Paint.FontMetrics fm = labelPaint.getFontMetrics();
			canvas.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, labelPaint);
			return;
		}
		Paint.FontMetrics fm = valuePaint.getFontMetrics();
		canvas.drawText(amount, cx, cy - (fm.ascent + fm.descent) / 2f, valuePaint);
	}

	private void drawEditChrome(final Canvas canvas, final float density) {
		editBorderPaint.setStrokeWidth(Math.max(2f, 2f * density));
		float inset = editBorderPaint.getStrokeWidth() / 2f;
		canvas.drawRect(inset, inset, getWidth() - inset, getHeight() - inset,
				editBorderPaint);
	}

	private void drawResizeGrip(final Canvas canvas) {
		int[] r = GaugeResizeGrip.rect(getWidth(), getHeight(),
				GaugeResizeGrip.gripPx(safeDensity()));
		if (r[2] <= r[0] || r[3] <= r[1]) {
			return;
		}
		gripPath.reset();
		gripPath.moveTo(r[2], r[1]);
		gripPath.lineTo(r[2], r[3]);
		gripPath.lineTo(r[0], r[3]);
		gripPath.close();
		canvas.drawPath(gripPath, gripPaint);
	}

	@Override
	public boolean onTouchEvent(final MotionEvent event) {
		if (gaugeId.length() == 0) {
			return false;
		}
		int masked = event.getActionMasked();
		if (event.getPointerCount() > 1) {
			// Let the window still open the two-finger copy widget.
			if (fingerDown) {
				multiTouchCancelled = true;
				cancelGesture(true);
			}
			return false;
		}
		if (multiTouchCancelled) {
			if (masked == MotionEvent.ACTION_UP || masked == MotionEvent.ACTION_CANCEL) {
				multiTouchCancelled = false;
				fingerDown = false;
			}
			return false;
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

	private boolean onDown(final MotionEvent event) {
		multiTouchCancelled = false;
		dragging = false;
		resizing = false;
		holdCancelledByMove = false;
		suppressReleaseCommands = false;
		fingerDown = true;
		downUptime = SystemClock.uptimeMillis();
		downRawX = event.getRawX();
		downRawY = event.getRawY();
		lastRawX = downRawX;
		lastRawY = downRawY;
		captureStartScreen();
		startW = getWidth();
		startH = getHeight();
		handler.removeCallbacks(editHoldRunnable);
		boolean hitGrip = hitResizeGrip(event.getX(), event.getY());
		if (GaugeWidgetEditGestures.shouldArmResize(editing, hitGrip)) {
			resizing = true;
			return true;
		}
		if (GaugeWidgetEditGestures.shouldArmEditHoldTimer(editing, hitGrip)) {
			handler.postDelayed(editHoldRunnable, GaugeWidgetEditGestures.EDIT_HOLD_MS);
		}
		return true;
	}

	private boolean onMove(final MotionEvent event) {
		if (!fingerDown || multiTouchCancelled) {
			return false;
		}
		float rawX = event.getRawX();
		float rawY = event.getRawY();
		float dx = rawX - downRawX;
		float dy = rawY - downRawY;
		lastRawX = rawX;
		lastRawY = rawY;
		if (resizing) {
			resizeByFinger(dx, dy);
			return true;
		}
		if (dragging) {
			moveByFinger(dx, dy);
			return true;
		}
		float density = safeDensity();
		float moveDist = (float) Math.hypot(dx, dy);
		if (SuperButtonGestures.shouldCancelHoldForMove(moveDist, density)) {
			holdCancelledByMove = true;
		}
		float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
		if (moveDist > Math.max(slop, swipeThresholdPx)) {
			handler.removeCallbacks(editHoldRunnable);
			if (editing) {
				dragging = true;
				moveByFinger(dx, dy);
			}
		}
		return true;
	}

	private void captureStartScreen() {
		int[] loc = new int[2];
		getLocationOnScreen(loc);
		startScreenX = loc[0];
		startScreenY = loc[1];
	}

	private void moveByFinger(final float dx, final float dy) {
		if (callbacks == null) {
			return;
		}
		callbacks.onMove(gaugeId, startScreenX + Math.round(dx),
				startScreenY + Math.round(dy));
	}

	private void resizeByFinger(final float dx, final float dy) {
		if (callbacks == null) {
			return;
		}
		int grip = GaugeResizeGrip.gripPx(safeDensity());
		int[] wh = GaugeGeometry.clampSize(startW + Math.round(dx),
				startH + Math.round(dy), grip, Integer.MAX_VALUE, Integer.MAX_VALUE);
		callbacks.onResize(gaugeId, wh[0], wh[1]);
	}

	private boolean onUp(final MotionEvent event, final boolean cancelled) {
		handler.removeCallbacks(editHoldRunnable);
		if (!fingerDown) {
			return false;
		}
		fingerDown = false;
		float dx = (cancelled ? lastRawX : event.getRawX()) - downRawX;
		float dy = (cancelled ? lastRawY : event.getRawY()) - downRawY;

		if (multiTouchCancelled || cancelled) {
			boolean wasDrag = dragging;
			boolean wasResize = resizing;
			resetGestureFlags();
			if (wasDrag) {
				fireMoveFinished();
			}
			if (wasResize) {
				fireResizeFinished();
			}
			return true;
		}

		if (resizing) {
			resetGestureFlags();
			fireResizeFinished();
			return true;
		}
		if (dragging) {
			resetGestureFlags();
			fireMoveFinished();
			return true;
		}
		if (suppressReleaseCommands) {
			resetGestureFlags();
			return true;
		}
		if (GaugeWidgetEditGestures.shouldExitEditOnTap(editing, false, false, false)) {
			resetGestureFlags();
			fireExitEdit();
			return true;
		}

		if (GaugeWidgetEditGestures.shouldDispatchCommands(editing, false)) {
			String dir = SuperButtonGestures.classifySwipe4(dx, dy, swipeThresholdPx);
			if (dir != null) {
				if (callbacks != null) {
					callbacks.onSwipe(gaugeId, dir);
				}
			} else if (callbacks != null) {
				callbacks.onTap(gaugeId);
			}
		}
		resetGestureFlags();
		return true;
	}

	private void cancelGesture(final boolean fireFinished) {
		handler.removeCallbacks(editHoldRunnable);
		boolean wasDrag = dragging;
		boolean wasResize = resizing;
		fingerDown = false;
		resetGestureFlags();
		if (fireFinished) {
			if (wasDrag) {
				fireMoveFinished();
			}
			if (wasResize) {
				fireResizeFinished();
			}
		}
	}

	private void resetGestureFlags() {
		dragging = false;
		resizing = false;
		holdCancelledByMove = false;
		suppressReleaseCommands = false;
	}

	private void fireEnterEdit() {
		if (callbacks != null) {
			callbacks.onEnterEdit(gaugeId);
		} else {
			setEditing(true);
		}
	}

	private void fireExitEdit() {
		if (callbacks != null) {
			callbacks.onExitEdit(gaugeId);
		} else {
			setEditing(false);
		}
	}

	private void fireMoveFinished() {
		if (callbacks != null) {
			callbacks.onMoveFinished(gaugeId);
		}
	}

	private void fireResizeFinished() {
		if (callbacks != null) {
			callbacks.onResizeFinished(gaugeId);
		}
	}

	private float safeDensity() {
		float d = getResources().getDisplayMetrics().density;
		return d > 0f ? d : 1f;
	}
}

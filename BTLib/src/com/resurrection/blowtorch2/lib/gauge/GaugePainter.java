/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.gauge;

import java.util.Locale;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * Draws overlay gauges onto a {@link Canvas}. Geometry lives in
 * {@link GaugeGeometry}; this class only paints.
 *
 * <p>Shape {@code timer} is the Zelda ring with a thicker stroke. The arc is
 * remaining time: the caller passes {@code ratio = remain / duration} (same
 * 0..1 channel as HP). {@link GaugeWidget.ImeMode} is not a shape and is
 * ignored here.
 */
public final class GaugePainter {

	public static final String SHAPE_HBAR = "hbar";
	public static final String SHAPE_VBAR = "vbar";
	public static final String SHAPE_RING = "ring";
	public static final String SHAPE_TIMER = "timer";

	/**
	 * Android {@code drawArc} 0° is 3 o'clock. Combined with
	 * {@link GaugeGeometry#ringSweepDegrees} this starts the fill at 12 o'clock
	 * and sweeps clockwise.
	 */
	public static final float RING_START_ANGLE_DEG = -90f;

	/** Stroke as a fraction of the inscribed square (HP ring). */
	public static final float RING_STROKE_FRACTION = 0.18f;
	/** Timer ring is a bit thicker so it does not read as another HP pip. */
	public static final float TIMER_STROKE_FRACTION = 0.26f;

	private final Paint fillPaint;
	private final Paint trackPaint;
	private final Paint bgPaint;
	private final RectF oval;

	public GaugePainter() {
		fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		oval = new RectF();
	}

	/**
	 * Dispatch by canonical shape. Unknown names paint as {@link #SHAPE_HBAR}.
	 */
	public void paint(final Canvas canvas, final String shape, final Rect bounds,
			final float ratio, final int fillColor, final int trackColor,
			final int bgAlpha) {
		String s = canonicalizeShape(shape);
		if (SHAPE_VBAR.equals(s)) {
			paintVBar(canvas, bounds, ratio, fillColor, trackColor, bgAlpha);
			return;
		}
		if (SHAPE_RING.equals(s)) {
			paintRing(canvas, bounds, ratio, fillColor, trackColor, bgAlpha, false);
			return;
		}
		if (SHAPE_TIMER.equals(s)) {
			paintRing(canvas, bounds, ratio, fillColor, trackColor, bgAlpha, true);
			return;
		}
		paintHBar(canvas, bounds, ratio, fillColor, trackColor, bgAlpha);
	}

	public void paintHBar(final Canvas canvas, final Rect bounds, final float ratio,
			final int fillColor, final int trackColor, final int bgAlpha) {
		if (canvas == null || bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
			return;
		}
		float r = (float) GaugeGeometry.clampRatio(ratio);
		drawPlate(canvas, bounds, bgAlpha, false);
		trackPaint.setStyle(Paint.Style.FILL);
		trackPaint.setColor(trackColor);
		canvas.drawRect(bounds, trackPaint);
		int[] fill = GaugeGeometry.hbarFill(bounds.left, bounds.top, bounds.width(),
				bounds.height(), r);
		if (fill[2] > fill[0] && fill[3] > fill[1]) {
			fillPaint.setStyle(Paint.Style.FILL);
			fillPaint.setStrokeCap(Paint.Cap.BUTT);
			fillPaint.setColor(fillColor);
			canvas.drawRect(fill[0], fill[1], fill[2], fill[3], fillPaint);
		}
	}

	/** Fill grows from the bottom of {@code bounds} upward. */
	public void paintVBar(final Canvas canvas, final Rect bounds, final float ratio,
			final int fillColor, final int trackColor, final int bgAlpha) {
		if (canvas == null || bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
			return;
		}
		float r = (float) GaugeGeometry.clampRatio(ratio);
		drawPlate(canvas, bounds, bgAlpha, false);
		trackPaint.setStyle(Paint.Style.FILL);
		trackPaint.setColor(trackColor);
		canvas.drawRect(bounds, trackPaint);
		int[] fill = GaugeGeometry.vbarFill(bounds.left, bounds.top, bounds.width(),
				bounds.height(), r);
		if (fill[2] > fill[0] && fill[3] > fill[1]) {
			fillPaint.setStyle(Paint.Style.FILL);
			fillPaint.setStrokeCap(Paint.Cap.BUTT);
			fillPaint.setColor(fillColor);
			canvas.drawRect(fill[0], fill[1], fill[2], fill[3], fillPaint);
		}
	}

	/**
	 * Zelda-style ring: circular track stroke plus a clockwise fill arc from
	 * 12 o'clock. Inner hole is the unstroked centre (not a pie).
	 */
	public void paintRing(final Canvas canvas, final Rect bounds, final float ratio,
			final int fillColor, final int trackColor, final int bgAlpha) {
		paintRing(canvas, bounds, ratio, fillColor, trackColor, bgAlpha, false);
	}

	/**
	 * @param thickStroke {@code true} for {@link #SHAPE_TIMER} (same geometry,
	 *        heavier stroke)
	 */
	public void paintRing(final Canvas canvas, final Rect bounds, final float ratio,
			final int fillColor, final int trackColor, final int bgAlpha,
			final boolean thickStroke) {
		if (canvas == null || bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
			return;
		}
		float stroke = ringStrokeWidth(bounds.width(), bounds.height(), thickStroke);
		if (stroke <= 0f) {
			return;
		}
		if (!setRingOval(bounds, stroke)) {
			return;
		}
		drawPlate(canvas, bounds, bgAlpha, true);
		trackPaint.setStyle(Paint.Style.STROKE);
		trackPaint.setStrokeCap(Paint.Cap.ROUND);
		trackPaint.setStrokeJoin(Paint.Join.ROUND);
		trackPaint.setStrokeWidth(stroke);
		trackPaint.setColor(trackColor);
		canvas.drawOval(oval, trackPaint);
		float sweep = GaugeGeometry.ringSweepDegrees(ratio);
		if (sweep > 0f) {
			fillPaint.setStyle(Paint.Style.STROKE);
			fillPaint.setStrokeCap(Paint.Cap.ROUND);
			fillPaint.setStrokeJoin(Paint.Join.ROUND);
			fillPaint.setStrokeWidth(stroke);
			fillPaint.setColor(fillColor);
			canvas.drawArc(oval, RING_START_ANGLE_DEG, sweep, false, fillPaint);
		}
	}

	private void drawPlate(final Canvas canvas, final Rect bounds, final int bgAlpha,
			final boolean circular) {
		int a = bgAlpha;
		if (a <= 0) {
			return;
		}
		if (a > 255) {
			a = 255;
		}
		bgPaint.setStyle(Paint.Style.FILL);
		bgPaint.setColor(a << 24);
		if (circular) {
			canvas.drawOval(oval, bgPaint);
		} else {
			canvas.drawRect(bounds, bgPaint);
		}
	}

	/**
	 * Inscribed oval inset by half the stroke so the ring stays inside
	 * {@code bounds}.
	 *
	 * @return false when the oval would invert
	 */
	private boolean setRingOval(final Rect bounds, final float stroke) {
		int size = Math.min(bounds.width(), bounds.height());
		if (size <= 0) {
			return false;
		}
		float cx = bounds.left + bounds.width() / 2f;
		float cy = bounds.top + bounds.height() / 2f;
		float radius = size / 2f;
		float inset = stroke / 2f;
		if (inset >= radius) {
			return false;
		}
		oval.set(cx - radius + inset, cy - radius + inset,
				cx + radius - inset, cy + radius - inset);
		return oval.width() > 0f && oval.height() > 0f;
	}

	public static float ringStrokeWidth(final int width, final int height,
			final boolean thick) {
		int size = Math.min(width, height);
		if (size <= 0) {
			return 0f;
		}
		float fraction = thick ? TIMER_STROKE_FRACTION : RING_STROKE_FRACTION;
		float stroke = size * fraction;
		float max = size * 0.45f;
		if (stroke < 4f) {
			stroke = 4f;
		}
		if (stroke > max) {
			stroke = max;
		}
		return stroke;
	}

	/** {@code liveValue / liveMax} in 0..1. {@code max <= 0} or NaN → 0. */
	public static float ratio(final double value, final double max) {
		if (max <= 0.0 || Double.isNaN(max) || Double.isNaN(value)) {
			return 0f;
		}
		return (float) GaugeGeometry.clampRatio(value / max);
	}

	/** Warn colour replaces fill when {@code isLow}. */
	public static int resolveFillColor(final boolean isLow, final int fillColor,
			final int warnColor) {
		return isLow ? warnColor : fillColor;
	}

	/**
	 * Canonical shape names this painter understands. {@code tim} → timer
	 * (survey name), {@code zelda} → ring. {@code ime} is an overlay mode, not
	 * a shape — treated as hbar like {@link GaugeWidget.Shape#fromJsonValue}.
	 */
	public static String canonicalizeShape(final String raw) {
		if (raw == null) {
			return SHAPE_HBAR;
		}
		String s = raw.trim().toLowerCase(Locale.US);
		if ("vbar".equals(s) || "vertical".equals(s) || "vert".equals(s)) {
			return SHAPE_VBAR;
		}
		if ("ring".equals(s) || "circle".equals(s) || "pie".equals(s)
				|| "zelda".equals(s)) {
			return SHAPE_RING;
		}
		if ("timer".equals(s) || "tim".equals(s) || "countdown".equals(s)) {
			return SHAPE_TIMER;
		}
		return SHAPE_HBAR;
	}

	/** {@code 80} and {@code 100} → {@code "80/100"}; one decimal when needed. */
	public static String formatAmount(final double value, final double max) {
		return formatNumber(value) + "/" + formatNumber(max);
	}

	public static String formatNumber(final double n) {
		if (Double.isNaN(n) || Double.isInfinite(n)) {
			return "0";
		}
		if (n == Math.rint(n) && Math.abs(n) < 1e12d) {
			return Long.toString((long) Math.rint(n));
		}
		return String.format(Locale.US, "%.1f", n);
	}
}

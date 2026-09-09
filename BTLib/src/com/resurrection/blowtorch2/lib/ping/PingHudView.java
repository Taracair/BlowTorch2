package com.resurrection.blowtorch2.lib.ping;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import com.resurrection.blowtorch2.lib.gauge.GaugeWidgetEditGestures;

/**
 * Small RTT chip. Long-press then drag to move; a short tap does nothing.
 */
public final class PingHudView extends View {

	public interface Callbacks {
		void onMoveFinished(int leftPx, int topPx);
	}

	private static final int BG = 0xCC101418;
	private static final int TEXT_UNKNOWN = 0xFFBBBBBB;
	private static final int TEXT_GOOD = 0xFF7CFF9A;
	private static final int TEXT_OK = 0xFFFFE082;
	private static final int TEXT_SLOW = 0xFFFF8A80;

	private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF box = new RectF();
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final int touchSlop;

	private Callbacks callbacks;
	private int rttMs = PingProbe.NO_SAMPLE;
	private int sizeSp = PingHudLayout.DEFAULT_SIZE;
	private float radius;
	private float padH;
	private float padV;

	private boolean fingerDown;
	private boolean dragging;
	private float downX;
	private float downY;
	private float downVisX;
	private float downVisY;

	private final Runnable startDrag = new Runnable() {
		@Override
		public void run() {
			if (!fingerDown || dragging) {
				return;
			}
			dragging = true;
			ViewParent p = getParent();
			if (p != null) {
				p.requestDisallowInterceptTouchEvent(true);
			}
		}
	};

	public PingHudView(final Context context) {
		super(context);
		bgPaint.setColor(BG);
		textPaint.setColor(TEXT_UNKNOWN);
		textPaint.setFakeBoldText(true);
		touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
		applySize(sizeSp);
		setRtt(PingProbe.NO_SAMPLE);
	}

	public void setCallbacks(final Callbacks callbacks) {
		this.callbacks = callbacks;
	}

	public void setSizeSp(final int sp) {
		int next = PingHudLayout.clampSize(sp);
		if (next == sizeSp) {
			return;
		}
		sizeSp = next;
		applySize(sizeSp);
		requestLayout();
		invalidate();
	}

	public void setRtt(final int rttMs) {
		boolean sizeChange = !PingHudColor.label(this.rttMs).equals(PingHudColor.label(rttMs));
		this.rttMs = rttMs;
		int band = PingHudColor.band(rttMs);
		if (band == PingHudColor.GOOD) {
			textPaint.setColor(TEXT_GOOD);
		} else if (band == PingHudColor.OK) {
			textPaint.setColor(TEXT_OK);
		} else if (band == PingHudColor.SLOW) {
			textPaint.setColor(TEXT_SLOW);
		} else {
			textPaint.setColor(TEXT_UNKNOWN);
		}
		if (sizeChange) {
			requestLayout();
		}
		invalidate();
	}

	public boolean isDragging() {
		return dragging;
	}

	private void applySize(final int sp) {
		float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
				getResources().getDisplayMetrics());
		textPaint.setTextSize(px);
		radius = px * 0.35f;
		padH = px * 0.55f;
		padV = px * 0.28f;
	}

	@Override
	protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
		String label = PingHudColor.label(rttMs);
		float w = textPaint.measureText(label) + padH * 2f;
		Paint.FontMetrics fm = textPaint.getFontMetrics();
		float h = (fm.descent - fm.ascent) + padV * 2f;
		setMeasuredDimension((int) Math.ceil(w), (int) Math.ceil(h));
	}

	@Override
	protected void onDraw(final Canvas canvas) {
		box.set(0, 0, getWidth(), getHeight());
		canvas.drawRoundRect(box, radius, radius, bgPaint);
		String label = PingHudColor.label(rttMs);
		Paint.FontMetrics fm = textPaint.getFontMetrics();
		float textX = padH;
		float textY = padV - fm.ascent;
		canvas.drawText(label, textX, textY, textPaint);
	}

	@Override
	public boolean onTouchEvent(final MotionEvent event) {
		switch (event.getActionMasked()) {
		case MotionEvent.ACTION_DOWN:
			fingerDown = true;
			dragging = false;
			downX = event.getRawX();
			downY = event.getRawY();
			downVisX = getLeft() + getTranslationX();
			downVisY = getTop() + getTranslationY();
			handler.postDelayed(startDrag, GaugeWidgetEditGestures.EDIT_HOLD_MS);
			return true;
		case MotionEvent.ACTION_MOVE:
			if (!fingerDown) {
				return true;
			}
			float dx = event.getRawX() - downX;
			float dy = event.getRawY() - downY;
			if (!dragging) {
				if (Math.hypot(dx, dy) > touchSlop) {
					handler.removeCallbacks(startDrag);
				}
				return true;
			}
			ViewParent parent = getParent();
			if (!(parent instanceof View)) {
				return true;
			}
			View host = (View) parent;
			int maxL = Math.max(0, host.getWidth() - getWidth());
			int maxT = Math.max(0, host.getHeight() - getHeight());
			int visL = clamp((int) (downVisX + dx), 0, maxL);
			int visT = clamp((int) (downVisY + dy), 0, maxT);
			setTranslationX(visL - getLeft());
			setTranslationY(visT - getTop());
			return true;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			handler.removeCallbacks(startDrag);
			boolean wasDrag = dragging;
			fingerDown = false;
			dragging = false;
			if (wasDrag && callbacks != null
					&& event.getActionMasked() == MotionEvent.ACTION_UP) {
				callbacks.onMoveFinished(
						(int) (getLeft() + getTranslationX()),
						(int) (getTop() + getTranslationY()));
			}
			return true;
		default:
			return super.onTouchEvent(event);
		}
	}

	private static int clamp(final int v, final int min, final int max) {
		if (v < min) {
			return min;
		}
		if (v > max) {
			return max;
		}
		return v;
	}
}

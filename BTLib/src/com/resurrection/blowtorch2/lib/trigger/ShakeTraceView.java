package com.resurrection.blowtorch2.lib.trigger;

import java.util.ArrayList;
import java.util.List;

import com.resurrection.blowtorch2.lib.service.sensor.ShakeTrace;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Recorded shakes on one plane. {@code depth} false is the screen (X right, Y
 * up). True is toward the player (X right, Z toward you, drawn upward).
 */
public class ShakeTraceView extends View {

	private static final int[] COLORS = {
		0xFF6CB6FF, 0xFFE06C75, 0xFF98C379, 0xFFE5C07B, 0xFFC678DD, 0xFF56B6C2
	};

	private final List<ShakeTrace> traces = new ArrayList<ShakeTrace>();
	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private boolean depth;
	private String empty = "";

	public ShakeTraceView(final Context context) {
		super(context);
	}

	public ShakeTraceView(final Context context, final AttributeSet attrs) {
		super(context, attrs);
	}

	public void setDepth(final boolean depth) {
		this.depth = depth;
		invalidate();
	}

	public void setEmptyText(final String empty) {
		this.empty = empty == null ? "" : empty;
		invalidate();
	}

	public void setTraces(final List<ShakeTrace> next) {
		traces.clear();
		if (next != null) {
			traces.addAll(next);
		}
		invalidate();
	}

	@Override
	protected void onDraw(final Canvas canvas) {
		float density = getResources().getDisplayMetrics().density;
		float pad = 10f * density;
		int w = getWidth();
		int h = getHeight();
		if (traces.isEmpty()) {
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(0xFF6E7680);
			paint.setTextSize(13f * density);
			canvas.drawText(empty, pad, h * 0.55f, paint);
			return;
		}
		float minA = Float.MAX_VALUE;
		float maxA = -Float.MAX_VALUE;
		float minB = Float.MAX_VALUE;
		float maxB = -Float.MAX_VALUE;
		for (int t = 0; t < traces.size(); t++) {
			ShakeTrace trace = traces.get(t);
			for (int i = 0; i < trace.size(); i++) {
				float a = trace.x(i);
				float b = depth ? trace.z(i) : trace.y(i);
				if (a < minA) {
					minA = a;
				}
				if (a > maxA) {
					maxA = a;
				}
				if (b < minB) {
					minB = b;
				}
				if (b > maxB) {
					maxB = b;
				}
			}
		}
		float spanA = Math.max(1f, maxA - minA);
		float spanB = Math.max(1f, maxB - minB);
		float extraA = spanA * 0.08f;
		float extraB = spanB * 0.08f;
		minA -= extraA;
		maxA += extraA;
		minB -= extraB;
		maxB += extraB;
		spanA = maxA - minA;
		spanB = maxB - minB;
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeCap(Paint.Cap.ROUND);
		paint.setStrokeJoin(Paint.Join.ROUND);
		for (int t = 0; t < traces.size(); t++) {
			ShakeTrace trace = traces.get(t);
			Path path = new Path();
			for (int i = 0; i < trace.size(); i++) {
				float a = trace.x(i);
				float b = depth ? trace.z(i) : trace.y(i);
				float px = pad + ((a - minA) / spanA) * (w - (2f * pad));
				float py = pad + ((maxB - b) / spanB) * (h - (2f * pad));
				if (i == 0) {
					path.moveTo(px, py);
				} else {
					path.lineTo(px, py);
				}
			}
			boolean last = t == traces.size() - 1;
			paint.setColor(COLORS[t % COLORS.length]);
			paint.setStrokeWidth((last ? 4.5f : 2.5f) * density);
			canvas.drawPath(path, paint);
		}
	}
}

package com.resurrection.blowtorch2.lib.mapper;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Pie / radial menu overlay for mapper actions.
 * Dark translucent circle, wedges with short labels; tap outside dismisses.
 * Configurable via constructor; use {@link #createLevelsMenu} / {@link #createToolsMenu}.
 */
public class MapperRadialMenu extends Dialog {

	public interface Listener {
		void onRadialAction(String action);
	}

	// --- Levels radial ---
	public static final String ACTION_LIST = "list";
	public static final String ACTION_UP = "up";
	public static final String ACTION_DOWN = "down";
	public static final String ACTION_ROOT = "home";
	public static final String ACTION_PARENT = "parent";
	public static final String ACTION_DELETE_LEVEL = "delete";

	/** @deprecated alias of {@link #ACTION_LIST} */
	@Deprecated
	public static final String ACTION_LEVELS = ACTION_LIST;
	/** @deprecated alias of {@link #ACTION_UP} */
	@Deprecated
	public static final String ACTION_FLOOR_UP = ACTION_UP;
	/** @deprecated alias of {@link #ACTION_DOWN} */
	@Deprecated
	public static final String ACTION_FLOOR_DOWN = ACTION_DOWN;

	// --- Tools radial ---
	public static final String ACTION_PATHS = "paths";
	public static final String ACTION_DRAW = "draw";
	public static final String ACTION_LINKS = "links";
	public static final String ACTION_HERE = "here";
	public static final String ACTION_EDIT = "edit";
	public static final String ACTION_SAVE = "save";
	public static final String ACTION_FIND = "find";
	public static final String ACTION_REC = "rec";

	private static final String[] LEVELS_ACTIONS = {
			ACTION_LIST,
			ACTION_UP,
			ACTION_DOWN,
			ACTION_ROOT,
			ACTION_PARENT,
			ACTION_DELETE_LEVEL
	};

	private static final String[] LEVELS_LABELS = {
			"List",
			"↑",
			"↓",
			"Root",
			"Door",
			"Delete"
	};

	private static final String[] TOOLS_ACTIONS = {
			ACTION_PATHS,
			ACTION_DRAW,
			ACTION_LINKS,
			ACTION_HERE,
			ACTION_EDIT,
			ACTION_SAVE,
			ACTION_FIND,
			ACTION_REC
	};

	private static final String[] TOOLS_LABELS = {
			"Paths",
			"Draw",
			"Links",
			"Here",
			"Edit",
			"Save",
			"Find",
			"Rec"
	};

	private static final int COLOR_BG = 0xE61A1A1A;
	private static final int COLOR_WEDGE_A = 0xFF2A3A4A;
	private static final int COLOR_WEDGE_B = 0xFF243240;
	private static final int COLOR_ACCENT = 0xFFE8C547;
	private static final int COLOR_LABEL = 0xFF9EC5FF;
	private static final int COLOR_RING = 0x88E8C547;

	private final Listener listener;
	private final String hubLabel;
	private final String[] actions;
	private final String[] labels;

	public MapperRadialMenu(Context ctx, String hubLabel, String[] actions,
			String[] labels, Listener listener) {
		super(ctx, android.R.style.Theme_Translucent_NoTitleBar);
		this.listener = listener;
		this.hubLabel = hubLabel != null ? hubLabel : "◎";
		if (actions == null || labels == null || actions.length == 0
				|| actions.length != labels.length) {
			throw new IllegalArgumentException(
					"actions/labels must be non-null, non-empty, and same length");
		}
		this.actions = actions;
		this.labels = labels;
		setCancelable(true);
		setCanceledOnTouchOutside(true);
	}

	/** Levels browse / floor / root / door / delete radial (hub ↕). */
	public static MapperRadialMenu createLevelsMenu(Context ctx, Listener listener) {
		return new MapperRadialMenu(ctx, "↕", LEVELS_ACTIONS, LEVELS_LABELS, listener);
	}

	/** Tools radial: paths, draw, links, here, edit, save, find, rec (hub ⚙). */
	public static MapperRadialMenu createToolsMenu(Context ctx, Listener listener) {
		return new MapperRadialMenu(ctx, "⚙", TOOLS_ACTIONS, TOOLS_LABELS, listener);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		Window window = getWindow();
		if (window != null) {
			window.setBackgroundDrawableResource(android.R.color.transparent);
			window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
			window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
					WindowManager.LayoutParams.MATCH_PARENT);
		}

		FrameLayout root = new FrameLayout(getContext());
		root.setBackgroundColor(0x66000000);
		root.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});

		RadialView pie = new RadialView(getContext());
		float density = getContext().getResources().getDisplayMetrics().density;
		int size = (int) (260 * density);
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
		lp.gravity = android.view.Gravity.CENTER;
		pie.setLayoutParams(lp);
		pie.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// consume so root outside-tap doesn't fire when hitting the pie
			}
		});
		root.addView(pie);
		setContentView(root);
	}

	private void fire(String action) {
		dismiss();
		if (listener != null && action != null) {
			listener.onRadialAction(action);
		}
	}

	private final class RadialView extends View {
		private final Paint wedgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint hubText = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final RectF oval = new RectF();
		private final Path wedgePath = new Path();
		private int highlight = -1;

		RadialView(Context context) {
			super(context);
			wedgePaint.setStyle(Paint.Style.FILL);
			strokePaint.setStyle(Paint.Style.STROKE);
			strokePaint.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
			strokePaint.setColor(COLOR_RING);
			textPaint.setColor(COLOR_LABEL);
			textPaint.setTextAlign(Paint.Align.CENTER);
			textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
			hubPaint.setColor(COLOR_BG);
			hubPaint.setStyle(Paint.Style.FILL);
			hubText.setColor(COLOR_ACCENT);
			hubText.setTextAlign(Paint.Align.CENTER);
			hubText.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
			setClickable(true);
		}

		@Override
		protected void onDraw(Canvas canvas) {
			float w = getWidth();
			float h = getHeight();
			float cx = w / 2f;
			float cy = h / 2f;
			float radius = Math.min(w, h) * 0.48f;
			float hubR = radius * 0.28f;
			float density = getResources().getDisplayMetrics().density;
			textPaint.setTextSize(12f * density);
			hubText.setTextSize(hubLabel.length() > 2 ? 11f * density : 14f * density);

			oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
			int n = actions.length;
			float sweep = 360f / n;
			// Start at top (-90°)
			float start = -90f - sweep / 2f;

			for (int i = 0; i < n; i++) {
				float a0 = start + i * sweep;
				wedgePaint.setColor(i == highlight
						? COLOR_ACCENT
						: (i % 2 == 0 ? COLOR_WEDGE_A : COLOR_WEDGE_B));
				if (i == highlight) {
					wedgePaint.setAlpha(220);
				}
				wedgePath.reset();
				wedgePath.moveTo(cx, cy);
				wedgePath.arcTo(oval, a0, sweep);
				wedgePath.close();
				canvas.drawPath(wedgePath, wedgePaint);

				// Label at mid-angle
				double midRad = Math.toRadians(a0 + sweep / 2f);
				float lr = radius * 0.68f;
				float lx = cx + (float) Math.cos(midRad) * lr;
				float ly = cy + (float) Math.sin(midRad) * lr
						- (textPaint.descent() + textPaint.ascent()) / 2f;
				int labelColor = i == highlight ? 0xFF1A1A1A : COLOR_LABEL;
				textPaint.setColor(labelColor);
				canvas.drawText(labels[i], lx, ly, textPaint);
			}

			canvas.drawCircle(cx, cy, radius, strokePaint);
			canvas.drawCircle(cx, cy, hubR, hubPaint);
			canvas.drawCircle(cx, cy, hubR, strokePaint);
			canvas.drawText(hubLabel, cx,
					cy - (hubText.descent() + hubText.ascent()) / 2f, hubText);
		}

		@Override
		public boolean onTouchEvent(MotionEvent event) {
			float cx = getWidth() / 2f;
			float cy = getHeight() / 2f;
			float dx = event.getX() - cx;
			float dy = event.getY() - cy;
			float dist = (float) Math.sqrt(dx * dx + dy * dy);
			float radius = Math.min(getWidth(), getHeight()) * 0.48f;
			float hubR = radius * 0.28f;

			int action = event.getActionMasked();
			if (action == MotionEvent.ACTION_DOWN
					|| action == MotionEvent.ACTION_MOVE) {
				if (dist > radius || dist < hubR) {
					highlight = -1;
				} else {
					highlight = wedgeAt(dx, dy);
				}
				invalidate();
				return true;
			}
			if (action == MotionEvent.ACTION_UP) {
				if (dist <= hubR) {
					highlight = -1;
					invalidate();
					dismiss();
					return true;
				}
				if (dist <= radius) {
					int idx = wedgeAt(dx, dy);
					highlight = -1;
					invalidate();
					if (idx >= 0 && idx < actions.length) {
						fire(actions[idx]);
					}
					return true;
				}
				highlight = -1;
				invalidate();
				dismiss();
				return true;
			}
			if (action == MotionEvent.ACTION_CANCEL) {
				highlight = -1;
				invalidate();
			}
			return true;
		}

		/** Map touch vector to wedge index (0 = top). */
		private int wedgeAt(float dx, float dy) {
			int n = actions.length;
			float sweep = 360f / n;
			float start = -90f - sweep / 2f;
			double deg = Math.toDegrees(Math.atan2(dy, dx));
			float angle = (float) deg;
			float rel = angle - start;
			while (rel < 0f) {
				rel += 360f;
			}
			while (rel >= 360f) {
				rel -= 360f;
			}
			int idx = (int) (rel / sweep);
			if (idx < 0) {
				idx = 0;
			}
			if (idx >= n) {
				idx = n - 1;
			}
			return idx;
		}
	}
}

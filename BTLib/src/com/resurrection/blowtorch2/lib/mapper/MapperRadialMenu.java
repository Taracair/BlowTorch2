package com.resurrection.blowtorch2.lib.mapper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * In-overlay pie menu (not a Dialog — avoids flashing the system status bar).
 * Attach to the mapper root; tap outside or hub dismisses.
 */
public final class MapperRadialMenu {

	public interface Listener {
		void onRadialAction(String action);
	}

	// Floors
	public static final String ACTION_LIST = "list";
	public static final String ACTION_UP = "up";
	public static final String ACTION_DOWN = "down";
	public static final String ACTION_ROOT = "home";
	public static final String ACTION_PARENT = "parent";
	public static final String ACTION_DELETE_LEVEL = "delete";

	@Deprecated
	public static final String ACTION_LEVELS = ACTION_LIST;
	@Deprecated
	public static final String ACTION_FLOOR_UP = ACTION_UP;
	@Deprecated
	public static final String ACTION_FLOOR_DOWN = ACTION_DOWN;

	// Build / map tools
	public static final String ACTION_PATHS = "paths";
	public static final String ACTION_DRAW = "draw";
	public static final String ACTION_LINKS = "links";
	public static final String ACTION_HERE = "here";
	public static final String ACTION_EDIT = "edit";

	// Nav / session
	public static final String ACTION_REC = "rec";
	public static final String ACTION_FOLLOW = "follow";
	public static final String ACTION_FIND = "find";
	public static final String ACTION_UNDO = "undo";
	public static final String ACTION_CENTER = "center";
	public static final String ACTION_CLOSE = "close";

	// File
	public static final String ACTION_SAVE = "save";
	public static final String ACTION_MAPS = "maps";
	public static final String ACTION_NEW = "new";
	public static final String ACTION_EXPORT = "export";

	private static final String[] NAV_ACTIONS = {
			ACTION_REC, ACTION_FOLLOW, ACTION_CENTER, ACTION_FIND,
			ACTION_UNDO, ACTION_CLOSE
	};
	private static final String[] NAV_LABELS = {
			"Record", "Follow", "Center", "Find", "Undo", "Close"
	};

	private static final String[] FLOORS_ACTIONS = {
			ACTION_LIST, ACTION_UP, ACTION_DOWN, ACTION_ROOT,
			ACTION_PARENT, ACTION_DELETE_LEVEL
	};
	private static final String[] FLOORS_LABELS = {
			"List", "↑", "↓", "Root", "Door", "Delete"
	};

	private static final String[] BUILD_ACTIONS = {
			ACTION_DRAW, ACTION_LINKS, ACTION_PATHS, ACTION_HERE, ACTION_EDIT
	};
	private static final String[] BUILD_LABELS = {
			"Draw", "Links", "Paths", "Here", "Edit"
	};

	private static final String[] FILE_ACTIONS = {
			ACTION_SAVE, ACTION_MAPS, ACTION_NEW, ACTION_EXPORT
	};
	private static final String[] FILE_LABELS = {
			"Save", "Maps", "New", "Export"
	};

	private static final int COLOR_BG = 0xE61A1A1A;
	private static final int COLOR_WEDGE_A = 0xFF2A3A4A;
	private static final int COLOR_WEDGE_B = 0xFF243240;
	private static final int COLOR_ACCENT = 0xFFE8C547;
	private static final int COLOR_LABEL = 0xFF9EC5FF;
	private static final int COLOR_RING = 0x88E8C547;

	private MapperRadialMenu() {
	}

	public static void showNav(ViewGroup parent, Listener listener) {
		show(parent, "Nav", NAV_ACTIONS, NAV_LABELS, listener);
	}

	public static void showFloors(ViewGroup parent, Listener listener) {
		show(parent, "Floors", FLOORS_ACTIONS, FLOORS_LABELS, listener);
	}

	public static void showBuild(ViewGroup parent, Listener listener) {
		show(parent, "Build", BUILD_ACTIONS, BUILD_LABELS, listener);
	}

	public static void showFile(ViewGroup parent, Listener listener) {
		show(parent, "File", FILE_ACTIONS, FILE_LABELS, listener);
	}

	/** @deprecated use {@link #showFloors} */
	@Deprecated
	public static void createLevelsMenu(Context ignored, Listener listener) {
		// no-op legacy; callers should use showFloors(parent, …)
	}

	public static void show(ViewGroup parent, String hubLabel, String[] actions,
			String[] labels, final Listener listener) {
		if (parent == null || actions == null || labels == null
				|| actions.length == 0 || actions.length != labels.length) {
			return;
		}
		dismissExisting(parent);
		Context ctx = parent.getContext();
		final FrameLayout dim = new FrameLayout(ctx);
		dim.setLayoutParams(new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		dim.setBackgroundColor(0x88000000);
		dim.setClickable(true);
		dim.setFocusable(true);
		dim.setTag("mapper_radial_overlay");

		RadialPie pie = new RadialPie(ctx, hubLabel, actions, labels,
				new Listener() {
					@Override
					public void onRadialAction(String action) {
						dismissExisting(parent);
						if (listener != null && action != null) {
							listener.onRadialAction(action);
						}
					}
				}, new Runnable() {
					@Override
					public void run() {
						dismissExisting(parent);
					}
				});
		float density = ctx.getResources().getDisplayMetrics().density;
		int size = (int) (280 * density);
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
		lp.gravity = Gravity.CENTER;
		pie.setLayoutParams(lp);
		dim.addView(pie);
		dim.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismissExisting(parent);
			}
		});
		parent.addView(dim);
		dim.bringToFront();
	}

	public static void dismissExisting(ViewGroup parent) {
		if (parent == null) {
			return;
		}
		for (int i = parent.getChildCount() - 1; i >= 0; i--) {
			View child = parent.getChildAt(i);
			if (child != null && "mapper_radial_overlay".equals(child.getTag())) {
				parent.removeViewAt(i);
			}
		}
	}

	private static final class RadialPie extends View {
		private final Paint wedgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint hubText = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final RectF oval = new RectF();
		private final Path wedgePath = new Path();
		private final String hubLabel;
		private final String[] actions;
		private final String[] labels;
		private final Listener listener;
		private final Runnable dismiss;
		private int highlight = -1;

		RadialPie(Context context, String hubLabel, String[] actions, String[] labels,
				Listener listener, Runnable dismiss) {
			super(context);
			this.hubLabel = hubLabel != null ? hubLabel : "◎";
			this.actions = actions;
			this.labels = labels;
			this.listener = listener;
			this.dismiss = dismiss;
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
			textPaint.setTextSize(11f * density);
			hubText.setTextSize(13f * density);

			oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
			int n = actions.length;
			float sweep = 360f / n;
			float start = -90f - sweep / 2f;

			for (int i = 0; i < n; i++) {
				float a0 = start + i * sweep;
				wedgePaint.setColor(i == highlight
						? COLOR_ACCENT
						: (i % 2 == 0 ? COLOR_WEDGE_A : COLOR_WEDGE_B));
				wedgePath.reset();
				wedgePath.moveTo(cx, cy);
				wedgePath.arcTo(oval, a0, sweep);
				wedgePath.close();
				canvas.drawPath(wedgePath, wedgePaint);

				double midRad = Math.toRadians(a0 + sweep / 2f);
				float lr = radius * 0.68f;
				float lx = cx + (float) Math.cos(midRad) * lr;
				float ly = cy + (float) Math.sin(midRad) * lr
						- (textPaint.descent() + textPaint.ascent()) / 2f;
				textPaint.setColor(i == highlight ? 0xFF1A1A1A : COLOR_LABEL);
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
			if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
				highlight = (dist > radius || dist < hubR) ? -1 : wedgeAt(dx, dy);
				invalidate();
				return true;
			}
			if (action == MotionEvent.ACTION_UP) {
				if (dist <= hubR) {
					highlight = -1;
					invalidate();
					if (dismiss != null) {
						dismiss.run();
					}
					return true;
				}
				if (dist <= radius) {
					int idx = wedgeAt(dx, dy);
					highlight = -1;
					invalidate();
					if (idx >= 0 && idx < actions.length && listener != null) {
						listener.onRadialAction(actions[idx]);
					}
					return true;
				}
				highlight = -1;
				invalidate();
				if (dismiss != null) {
					dismiss.run();
				}
				return true;
			}
			if (action == MotionEvent.ACTION_CANCEL) {
				highlight = -1;
				invalidate();
			}
			return true;
		}

		private int wedgeAt(float dx, float dy) {
			int n = actions.length;
			float sweep = 360f / n;
			float start = -90f - sweep / 2f;
			double deg = Math.toDegrees(Math.atan2(dy, dx));
			float rel = (float) deg - start;
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

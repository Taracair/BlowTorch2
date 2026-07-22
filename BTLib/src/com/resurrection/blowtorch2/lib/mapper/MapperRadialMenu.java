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
 * In-overlay pie menu. Supports optional secondary state lines under labels
 * (e.g. {@code Follow} + {@code on}) without crowding the hub.
 */
public final class MapperRadialMenu {

	public interface Listener {
		void onRadialAction(String action);
	}

	/** Optional per-wedge state shown under the main label (may be null/empty). */
	public static final class Item {
		public final String action;
		public final String label;
		public final String state;

		public Item(String action, String label) {
			this(action, label, null);
		}

		public Item(String action, String label, String state) {
			this.action = action;
			this.label = label != null ? label : "";
			this.state = state;
		}
	}

	// Nav
	public static final String ACTION_GO_THERE = "gothere";
	public static final String ACTION_PATH_TO = "pathto";
	public static final String ACTION_FIND = "find";
	public static final String ACTION_CENTER = "center";
	public static final String ACTION_FOLLOW = "follow";
	public static final String ACTION_REC = "rec";
	public static final String ACTION_UNDO = "undo";

	// Floors
	public static final String ACTION_LIST = "list";
	public static final String ACTION_UP = "up";
	public static final String ACTION_DOWN = "down";
	public static final String ACTION_ROOT = "home";
	public static final String ACTION_PARENT = "parent";
	public static final String ACTION_DELETE_LEVEL = "delete";
	public static final String ACTION_RENAME_LEVEL = "rename";
	@Deprecated
	public static final String ACTION_LEVELS = ACTION_LIST;
	@Deprecated
	public static final String ACTION_FLOOR_UP = ACTION_UP;
	@Deprecated
	public static final String ACTION_FLOOR_DOWN = ACTION_DOWN;

	// Edit
	public static final String ACTION_DRAW = "draw";
	public static final String ACTION_LINKS = "links";
	public static final String ACTION_HERE = "here";
	public static final String ACTION_EDIT = "edit";
	public static final String ACTION_ONE_WAY = "oneway";
	public static final String ACTION_PATHS = "paths";
	public static final String ACTION_MOVES = "moves";
	public static final String ACTION_ADD_LEVEL = "addlevel";
	public static final String ACTION_LINK_MAP = "linkmap";

	// More
	public static final String ACTION_SAVE = "save";
	public static final String ACTION_MAPS = "maps";
	public static final String ACTION_NEW = "new";
	public static final String ACTION_EXPORT = "export";
	public static final String ACTION_OPACITY = "opacity";
	public static final String ACTION_CAPTURE = "capture";
	public static final String ACTION_GMCP = "gmcp";
	public static final String ACTION_GMCP_GROW = "gmcpgrow";
	public static final String ACTION_ARROW_LABELS = "arrowlabels";
	public static final String ACTION_WINDOW_ECHO = "windowecho";

	/** @deprecated Close lives on the title-bar ✕ only. */
	@Deprecated
	public static final String ACTION_CLOSE = "close";

	private static final int COLOR_BG = 0xE61A1A1A;
	private static final int COLOR_WEDGE_A = 0xFF2A3A4A;
	private static final int COLOR_WEDGE_B = 0xFF243240;
	private static final int COLOR_ACCENT = 0xFFE8C547;
	private static final int COLOR_LABEL = 0xFF9EC5FF;
	private static final int COLOR_STATE = 0xFFB8D4A8;
	private static final int COLOR_RING = 0x88E8C547;

	private MapperRadialMenu() {
	}

	public static void showNav(ViewGroup parent, Listener listener,
			boolean recording, boolean follow) {
		Item[] items = {
				new Item(ACTION_PATH_TO, "Path to"),
				new Item(ACTION_GO_THERE, "Go there"),
				new Item(ACTION_FIND, "Find"),
				new Item(ACTION_CENTER, "Center"),
				new Item(ACTION_FOLLOW, "Follow", follow ? "on" : "off"),
				new Item(ACTION_REC, "Record", recording ? "on" : "off"),
				new Item(ACTION_UNDO, "Undo")
		};
		show(parent, "Nav", items, listener);
	}

	public static void showFloors(ViewGroup parent, Listener listener) {
		Item[] items = {
				new Item(ACTION_LIST, "Floor list"),
				new Item(ACTION_UP, "Floor ↑"),
				new Item(ACTION_DOWN, "Floor ↓"),
				new Item(ACTION_ROOT, "Root floor"),
				new Item(ACTION_PARENT, "To entrance"),
				new Item(ACTION_RENAME_LEVEL, "Rename floor"),
				new Item(ACTION_DELETE_LEVEL, "Delete floor")
		};
		show(parent, "Floors", items, listener);
	}

	public static void showEdit(ViewGroup parent, Listener listener,
			boolean drawOn, boolean linksOn, boolean pathsSpread,
			boolean acceptOneWay) {
		Item[] items = {
				new Item(ACTION_DRAW, "Draw", drawOn ? "on" : "off"),
				new Item(ACTION_LINKS, "Link mode", linksOn ? "on" : "off"),
				new Item(ACTION_HERE, "Set Here"),
				new Item(ACTION_EDIT, "Edit tile"),
				new Item(ACTION_PATHS, "Layout",
						pathsSpread ? "spread" : "packed"),
				new Item(ACTION_ONE_WAY, "1-way specials",
						acceptOneWay ? "on" : "off"),
				new Item(ACTION_MOVES, "Moves"),
				new Item(ACTION_LINK_MAP, "Link map")
		};
		show(parent, "Edit", items, listener);
	}

	/** @deprecated use {@link #showEdit} */
	@Deprecated
	public static void showBuild(ViewGroup parent, Listener listener) {
		showEdit(parent, listener, false, false, true, false);
	}

	/** @deprecated use {@link #showEdit} */
	@Deprecated
	public static void showBuild(ViewGroup parent, Listener listener,
			boolean acceptOneWay) {
		showEdit(parent, listener, false, false, true, acceptOneWay);
	}

	public static void showMore(ViewGroup parent, Listener listener, int opacity,
			boolean gmcpOn, boolean gmcpGrow, boolean arrowLabels, boolean windowEcho) {
		Item[] items = {
				new Item(ACTION_SAVE, "Save"),
				new Item(ACTION_MAPS, "Maps"),
				new Item(ACTION_NEW, "New map"),
				new Item(ACTION_EXPORT, "Export"),
				new Item(ACTION_OPACITY, "Opacity…", opacity + "%"),
				new Item(ACTION_ARROW_LABELS, "Arrow labels", arrowLabels ? "on" : "off"),
				new Item(ACTION_WINDOW_ECHO, "Window echo", windowEcho ? "on" : "off"),
				new Item(ACTION_CAPTURE, "Capture"),
				new Item(ACTION_GMCP, "GMCP sync", gmcpOn ? "on" : "off"),
				new Item(ACTION_GMCP_GROW, "GMCP grow", gmcpGrow ? "on" : "off")
		};
		show(parent, "More", items, listener);
	}

	/** @deprecated use {@link #showMore} with full flags */
	@Deprecated
	public static void showMore(ViewGroup parent, Listener listener, int opacity,
			boolean gmcpOn, boolean gmcpGrow, boolean arrowLabels) {
		showMore(parent, listener, opacity, gmcpOn, gmcpGrow, arrowLabels, true);
	}

	/** @deprecated use {@link #showMore} with full flags */
	@Deprecated
	public static void showMore(ViewGroup parent, Listener listener, int opacity,
			boolean gmcpOn, boolean gmcpGrow) {
		showMore(parent, listener, opacity, gmcpOn, gmcpGrow, true, true);
	}

	/** @deprecated use {@link #showMore} with GMCP flags */
	@Deprecated
	public static void showMore(ViewGroup parent, Listener listener, int opacity) {
		showMore(parent, listener, opacity, true, true, true, true);
	}

	/** @deprecated use {@link #showMore} */
	@Deprecated
	public static void showFile(ViewGroup parent, Listener listener) {
		showMore(parent, listener, 85, true, true, true, true);
	}

	public static void show(ViewGroup parent, String hubLabel, Item[] items,
			final Listener listener) {
		if (parent == null || items == null || items.length == 0) {
			return;
		}
		String[] actions = new String[items.length];
		String[] labels = new String[items.length];
		String[] states = new String[items.length];
		for (int i = 0; i < items.length; i++) {
			actions[i] = items[i].action;
			labels[i] = items[i].label;
			states[i] = items[i].state;
		}
		show(parent, hubLabel, actions, labels, states, listener);
	}

	public static void show(ViewGroup parent, String hubLabel, String[] actions,
			String[] labels, final Listener listener) {
		show(parent, hubLabel, actions, labels, null, listener);
	}

	public static void show(ViewGroup parent, String hubLabel, String[] actions,
			String[] labels, String[] states, final Listener listener) {
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

		RadialPie pie = new RadialPie(ctx, hubLabel, actions, labels, states,
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
		int size = (int) (300 * density);
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
		private final Paint statePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint hubText = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final RectF oval = new RectF();
		private final Path wedgePath = new Path();
		private final String hubLabel;
		private final String[] actions;
		private final String[] labels;
		private final String[] states;
		private final Listener listener;
		private final Runnable dismiss;
		private int highlight = -1;

		RadialPie(Context context, String hubLabel, String[] actions,
				String[] labels, String[] states, Listener listener,
				Runnable dismiss) {
			super(context);
			this.hubLabel = hubLabel != null ? hubLabel : "◎";
			this.actions = actions;
			this.labels = labels;
			this.states = states;
			this.listener = listener;
			this.dismiss = dismiss;
			wedgePaint.setStyle(Paint.Style.FILL);
			strokePaint.setStyle(Paint.Style.STROKE);
			strokePaint.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
			strokePaint.setColor(COLOR_RING);
			textPaint.setColor(COLOR_LABEL);
			textPaint.setTextAlign(Paint.Align.CENTER);
			textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
			statePaint.setColor(COLOR_STATE);
			statePaint.setTextAlign(Paint.Align.CENTER);
			statePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
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
			statePaint.setTextSize(9f * density);
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
				float ly = cy + (float) Math.sin(midRad) * lr;
				boolean hasState = states != null && i < states.length
						&& states[i] != null && states[i].length() > 0;
				textPaint.setColor(i == highlight ? 0xFF1A1A1A : COLOR_LABEL);
				statePaint.setColor(i == highlight ? 0xFF333333 : COLOR_STATE);
				if (hasState) {
					canvas.drawText(labels[i], lx,
							ly - 5f * density, textPaint);
					canvas.drawText(states[i], lx,
							ly + 8f * density, statePaint);
				} else {
					canvas.drawText(labels[i], lx,
							ly - (textPaint.descent() + textPaint.ascent()) / 2f,
							textPaint);
				}
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
			float radius = Math.min(getWidth(), getHeight()) * 0.48f;
			float hubR = radius * 0.28f;
			float x = event.getX() - cx;
			float y = event.getY() - cy;
			float dist = (float) Math.sqrt(x * x + y * y);
			int action = event.getActionMasked();
			if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
				if (dist <= hubR || dist > radius) {
					highlight = -1;
				} else {
					highlight = indexForAngle(x, y);
				}
				invalidate();
				return true;
			}
			if (action == MotionEvent.ACTION_UP) {
				if (dist <= hubR) {
					if (dismiss != null) {
						dismiss.run();
					}
				} else if (dist <= radius) {
					int idx = indexForAngle(x, y);
					if (idx >= 0 && idx < actions.length && listener != null) {
						listener.onRadialAction(actions[idx]);
					}
				} else if (dismiss != null) {
					dismiss.run();
				}
				highlight = -1;
				invalidate();
				return true;
			}
			if (action == MotionEvent.ACTION_CANCEL) {
				highlight = -1;
				invalidate();
			}
			return true;
		}

		private int indexForAngle(float x, float y) {
			int n = actions.length;
			float sweep = 360f / n;
			float start = -90f - sweep / 2f;
			double deg = Math.toDegrees(Math.atan2(y, x));
			if (deg < 0) {
				deg += 360.0;
			}
			float rel = (float) deg - start;
			while (rel < 0) {
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

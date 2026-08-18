/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.gauge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;
import com.resurrection.blowtorch2.lib.window.FloatingLayer;
import com.resurrection.blowtorch2.lib.window.FloatingLayerGeometry;
import com.resurrection.blowtorch2.lib.window.MainWindow;

import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

/**
 * Overlay gauges on {@code window_container}: one {@link FloatingLayer} tagged
 * {@link #LAYER_TAG}, sibling of extra text, not a child of {@link
 * com.resurrection.blowtorch2.lib.window.Window} and not shared with floating
 * buttons.
 *
 * <p>Z-order after attach: game Windows → extra text → this layer → floating
 * buttons → ⋮ ({@code bringViewUnderChrome} then {@code raiseFloatingButtons}).
 *
 * <p>IME: STAY rides with game text (Chrome keep-text rule on the tag). HIDE
 * children go {@code GONE} while liftPx &gt; 0. OVERLAY widgets live in one
 * {@code TYPE_APPLICATION_OVERLAY} window sized to that gauge, same idea as
 * floating buttons — a full-screen overlay window would eat game and IME
 * touches. Missing permission treats them as STAY.
 */
public class GaugeWidgetController {

	public static final String LAYER_TAG = "gauge_widget_layer";

	public interface Host {
		MainWindow getMainWindow();

		void sendCommand(String text);

		/** Persist geometry/style JSON (never live values). */
		void persistGaugeWidgets(List<GaugeWidget> widgets);

		void bringViewUnderChrome(View overlay);

		void raiseFloatingButtons();

		int getImeLiftPx();

		void applyCurrentImeLift();

		int floatingOverlayBottomLimit(int inputBarTop, int overlayLeft,
				int overlayRight);

		void whenFabStripMeasured(Runnable action);

		boolean fabStripHasSize();

		View findGameplayInputBar();
	}

	private final Host host;

	private final ArrayList<GaugeWidget> widgets = new ArrayList<GaugeWidget>();
	private final HashMap<String, GaugeWidgetView> stayViews =
			new HashMap<String, GaugeWidgetView>();
	private final HashMap<String, GaugeWidgetView> overlayViews =
			new HashMap<String, GaugeWidgetView>();
	private final HashMap<String, WindowManager.LayoutParams> overlayParamsById =
			new HashMap<String, WindowManager.LayoutParams>();
	private final HashMap<String, double[]> lastValues = new HashMap<String, double[]>();

	private FloatingLayer layer;
	private boolean skipNextSync;
	private boolean overlayPromptShown;
	private boolean overlayDeniedThisSession;
	private boolean chromeReclampScheduled;
	private boolean selectionHidden;
	private boolean resumed = true;
	private int lastLiftPx;
	private String editingId;

	private final GaugeWidgetView.Callbacks viewCallbacks = new GaugeWidgetView.Callbacks() {
		@Override
		public void onTap(final String id) {
			sendWidgetCommand(id, commandForTap(id));
		}

		@Override
		public void onSwipe(final String id, final String dir) {
			sendWidgetCommand(id, commandForSwipe(id, dir));
		}

		@Override
		public void onHold(final String id) {
			sendWidgetCommand(id, commandForHold(id));
		}

		@Override
		public void onMove(final String id, final int x, final int y) {
			applyMove(id, x, y);
		}

		@Override
		public void onResize(final String id, final int w, final int h) {
			applyResize(id, w, h);
		}

		@Override
		public void onMoveFinished(final String id) {
			flushPersist();
		}

		@Override
		public void onResizeFinished(final String id) {
			flushPersist();
		}

		@Override
		public void onEnterEdit(final String id) {
			setEditingId(id);
		}

		@Override
		public void onExitEdit(final String id) {
			if (id != null && id.equals(editingId)) {
				setEditingId(null);
			}
		}
	};

	public GaugeWidgetController(final Host host) {
		this.host = host;
	}

	/**
	 * Parse compact live-value JSON {@code [{"id","v","m"}]} into id →
	 * {@code [v, m]}. Invalid / blank → empty map. Unknown ids kept as given
	 * after {@link GaugeWidgetsStore#normalizeName}; unnormalizable skipped.
	 */
	public static HashMap<String, double[]> parseValuesJson(final String json) {
		HashMap<String, double[]> out = new HashMap<String, double[]>();
		if (json == null) {
			return out;
		}
		String trimmed = json.trim();
		if (trimmed.length() == 0 || "null".equalsIgnoreCase(trimmed)) {
			return out;
		}
		try {
			JSONArray arr = new JSONArray(trimmed);
			for (int i = 0; i < arr.length(); i++) {
				JSONObject o = arr.optJSONObject(i);
				if (o == null) {
					continue;
				}
				String id = GaugeWidgetsStore.normalizeName(o.optString("id", ""));
				if (id == null) {
					continue;
				}
				double v = o.optDouble("v", 0.0);
				double m = o.optDouble("m", GaugeWidget.DEFAULT_LIVE_MAX);
				if (Double.isNaN(v)) {
					v = 0.0;
				}
				if (Double.isNaN(m)) {
					m = GaugeWidget.DEFAULT_LIVE_MAX;
				}
				out.put(id, new double[] { v, m });
			}
		} catch (JSONException e) {
			return new HashMap<String, double[]>();
		} catch (RuntimeException e) {
			return new HashMap<String, double[]>();
		}
		return out;
	}

	/**
	 * Write geometry into portrait or landscape fields. Does not copy portrait
	 * into {@code land*} — all-zero land* still means “use portrait”.
	 */
	public static void writeGeometry(final GaugeWidget g, final boolean landscape,
			final int x, final int y, final int w, final int h) {
		if (g == null) {
			return;
		}
		if (landscape) {
			g.setLandX(x);
			g.setLandY(y);
			g.setLandW(w);
			g.setLandH(h);
		} else {
			g.setX(x);
			g.setY(y);
			g.setW(w);
			g.setH(h);
		}
	}

	/**
	 * Display geometry. Landscape with all {@code land*=0} substitutes portrait
	 * and does not mutate the widget. After a landscape write, {@code land_x}
	 * / {@code land_y} of 0 are real edges (size is also stored).
	 *
	 * @return {@code int[]{x, y, w, h}}
	 */
	public static int[] readGeometry(final GaugeWidget g, final boolean landscape) {
		if (g == null) {
			return new int[] { 0, 0, 1, 1 };
		}
		if (landscape) {
			return new int[] { g.resolveLandX(), g.resolveLandY(),
					g.resolveLandW(), g.resolveLandH() };
		}
		return new int[] { g.getX(), g.getY(), g.getW(), g.getH() };
	}

	/**
	 * Rebuild from persisted JSON. {@code enabled == false} flushes pending
	 * geometry then removes layers without writing {@code []}.
	 */
	public void sync(final String json, final boolean enabled) {
		if (skipNextSync) {
			skipNextSync = false;
			if (enabled) {
				return;
			}
		}
		if (!enabled) {
			rebuild(new ArrayList<GaugeWidget>());
			return;
		}
		rebuild(GaugeWidgetsStore.parse(json));
	}

	public void applyValues(final String json) {
		HashMap<String, double[]> parsed = parseValuesJson(json);
		for (Map.Entry<String, double[]> e : parsed.entrySet()) {
			lastValues.put(e.getKey(), e.getValue());
			GaugeWidget g = findWidget(e.getKey());
			if (g == null || e.getValue() == null) {
				continue;
			}
			g.setLiveValue(e.getValue()[0]);
			g.setLiveMax(e.getValue()[1]);
			if (g.getShape() == GaugeWidget.Shape.TIMER) {
				g.setRemainSec(e.getValue()[0]);
				g.setDurationSec(e.getValue()[1]);
			}
		}
		paintLiveValues();
	}

	public void onImeLiftChanged(final int liftPx) {
		lastLiftPx = Math.max(0, liftPx);
		applyImeHide();
	}

	public void setHiddenForSelection(final boolean hidden) {
		selectionHidden = hidden;
		if (hidden) {
			if (layer != null) {
				layer.setVisibility(View.INVISIBLE);
			}
			detachAllOverlayWindows();
			return;
		}
		if (layer != null) {
			layer.setVisibility(View.VISIBLE);
		}
		applyImeHide();
		if (hasOverlayWidgets() && canOverlay() && resumed) {
			reattachOverlayWindows();
		}
	}

	public void onPause() {
		resumed = false;
		detachAllOverlayWindows();
	}

	public void onResume() {
		resumed = true;
		if (selectionHidden) {
			return;
		}
		if (hasOverlayWidgets() && canOverlay()) {
			reattachOverlayWindows();
			applySelectionVisibility();
		} else if (hasOverlayWidgets()) {
			rebuild(copyWidgets());
		}
	}

	public void onOrientationChanged() {
		if (widgets.isEmpty()) {
			return;
		}
		rebuild(copyWidgets());
	}

	public void bringUnderChrome() {
		if (layer != null) {
			host.bringViewUnderChrome(layer);
		}
		host.raiseFloatingButtons();
	}

	public void detach() {
		rebuild(new ArrayList<GaugeWidget>());
		widgets.clear();
		lastValues.clear();
	}

	private void rebuild(final ArrayList<GaugeWidget> list) {
		widgets.clear();
		if (list != null) {
			for (int i = 0; i < list.size(); i++) {
				GaugeWidget g = list.get(i);
				if (g != null) {
					widgets.add(g.copy());
				}
			}
		}
		clearViews();
		if (widgets.isEmpty()) {
			detachStayLayer();
			detachAllOverlayWindows();
			host.raiseFloatingButtons();
			return;
		}
		maybeAskForOverlayPermission();
		boolean overlayOk = canOverlay();
		boolean needStay = false;
		boolean needOverlay = false;
		for (int i = 0; i < widgets.size(); i++) {
			GaugeWidget g = widgets.get(i);
			if (g == null || !g.isVisible()) {
				continue;
			}
			if (usesOverlayWindow(g, overlayOk)) {
				needOverlay = true;
			} else {
				needStay = true;
			}
		}
		if (needOverlay && resumed && !selectionHidden) {
			if (!canOverlay()) {
				overlayOk = false;
				needOverlay = false;
				needStay = true;
				detachAllOverlayWindows();
			}
		} else {
			detachAllOverlayWindows();
		}
		if (needStay) {
			ensureStayLayer();
		} else {
			detachStayLayer();
		}
		lastLiftPx = Math.max(0, host.getImeLiftPx());
		for (int i = 0; i < widgets.size(); i++) {
			GaugeWidget g = widgets.get(i);
			if (g == null || !g.isVisible()) {
				continue;
			}
			boolean overlay = usesOverlayWindow(g, overlayOk) && !selectionHidden;
			GaugeWidgetView view = null;
			if (overlay) {
				view = attachOverlayWidget(g);
				if (view == null) {
					overlayOk = false;
					overlay = false;
					if (layer == null) {
						ensureStayLayer();
					}
				}
			}
			if (!overlay) {
				if (layer == null) {
					continue;
				}
				view = new GaugeWidgetView(layer.getContext());
				view.bind(g.getId(), g.getShape(), g.getLabel(), g.isShowLabel(),
						g.isShowValue(), g.getOpacity());
				view.setBoundSwipes(g.boundSwipes());
				view.setCallbacks(viewCallbacks);
				FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1);
				lp.gravity = Gravity.TOP | Gravity.START;
				layer.addView(view, lp);
				stayViews.put(g.getId(), view);
			}
			if (view != null) {
				layoutView(view, g, overlay);
			}
		}
		applyEditChrome();
		applyImeHide();
		applySelectionVisibility();
		paintLiveValues();
		reclampWhenChromeIsMeasured();
		scheduleUnplacedCentre();
		if (layer != null) {
			host.bringViewUnderChrome(layer);
			host.applyCurrentImeLift();
		}
		host.raiseFloatingButtons();
	}

	private void ensureStayLayer() {
		if (layer != null) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		RelativeLayout container = (RelativeLayout) activity.findViewById(
				R.id.window_container);
		if (container == null) {
			return;
		}
		layer = new FloatingLayer(activity);
		layer.setTag(LAYER_TAG);
		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT);
		container.addView(layer, lp);
	}

	private GaugeWidgetView attachOverlayWidget(final GaugeWidget g) {
		MainWindow activity = host.getMainWindow();
		WindowManager wm = windowManager();
		if (activity == null || wm == null || g == null || !resumed) {
			return null;
		}
		float density = density();
		int[] geo = readGeometry(g, isLandscape());
		int wPx = Math.max(1, Math.round(geo[2] * density));
		int hPx = Math.max(1, Math.round(geo[3] * density));
		int[] screen = containerToScreen(Math.round(geo[0] * density),
				Math.round(geo[1] * density));
		GaugeWidgetView view = new GaugeWidgetView(activity);
		view.bind(g.getId(), g.getShape(), g.getLabel(), g.isShowLabel(),
				g.isShowValue(), g.getOpacity());
		view.setBoundSwipes(g.boundSwipes());
		view.setCallbacks(viewCallbacks);
		WindowManager.LayoutParams p = newOverlayParams(screen[0], screen[1], wPx, hPx);
		try {
			wm.addView(view, p);
		} catch (RuntimeException e) {
			BlowTorchLogger.logThrowable("GaugeWidgetController.attachOverlayWidget", e);
			overlayDeniedThisSession = true;
			return null;
		}
		overlayViews.put(g.getId(), view);
		overlayParamsById.put(g.getId(), p);
		if (selectionHidden) {
			view.setVisibility(View.INVISIBLE);
		}
		return view;
	}

	private RelativeLayout windowContainer() {
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return null;
		}
		return (RelativeLayout) activity.findViewById(R.id.window_container);
	}

	private int[] containerToScreen(final int xInContainer, final int yInContainer) {
		RelativeLayout container = windowContainer();
		int[] loc = new int[2];
		if (container != null) {
			container.getLocationOnScreen(loc);
		}
		return new int[] { loc[0] + xInContainer, loc[1] + yInContainer };
	}

	private int[] screenToContainer(final int screenX, final int screenY) {
		int[] origin = containerToScreen(0, 0);
		return new int[] { screenX - origin[0], screenY - origin[1] };
	}

	/**
	 * Screen → layout-local for a parent that may be translated (IME lift on
	 * the STAY layer). {@link View#getLocationOnScreen} includes translation,
	 * so the result matches left/top margins, not {@code window_container}.
	 */
	private int[] screenToParent(final View parent, final int screenX,
			final int screenY) {
		int[] loc = new int[2];
		if (parent != null) {
			parent.getLocationOnScreen(loc);
		}
		return new int[] { screenX - loc[0], screenY - loc[1] };
	}

	private WindowManager.LayoutParams newOverlayParams(final int x, final int y,
			final int w, final int h) {
		int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
				| WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
				| WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
		WindowManager.LayoutParams p = new WindowManager.LayoutParams(
				Math.max(1, w),
				Math.max(1, h),
				WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
				flags,
				PixelFormat.TRANSLUCENT);
		p.gravity = Gravity.TOP | Gravity.START;
		p.x = x;
		p.y = y;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
			p.setFitInsetsTypes(0);
		}
		return p;
	}

	private void updateOverlayLayout(final String id, final int screenX,
			final int screenY, final int w, final int h) {
		GaugeWidgetView view = overlayViews.get(id);
		WindowManager.LayoutParams p = overlayParamsById.get(id);
		WindowManager wm = windowManager();
		if (view == null || p == null || wm == null) {
			return;
		}
		p.x = screenX;
		p.y = screenY;
		p.width = Math.max(1, w);
		p.height = Math.max(1, h);
		try {
			wm.updateViewLayout(view, p);
		} catch (RuntimeException e) {
			BlowTorchLogger.logMinor("GaugeWidgetController.updateOverlayLayout", e);
		}
	}

	private void detachAllOverlayWindows() {
		WindowManager wm = windowManager();
		for (GaugeWidgetView v : overlayViews.values()) {
			if (wm != null && v != null) {
				try {
					wm.removeViewImmediate(v);
				} catch (RuntimeException e) {
					// Already detached.
				}
			}
		}
		overlayViews.clear();
		overlayParamsById.clear();
	}

	private void reattachOverlayWindows() {
		if (!resumed || !canOverlay()) {
			return;
		}
		rebuild(copyWidgets());
	}

	private void detachStayLayer() {
		clearStayViews();
		if (layer != null && layer.getParent() instanceof ViewGroup) {
			((ViewGroup) layer.getParent()).removeView(layer);
		}
		layer = null;
	}

	private void clearViews() {
		clearStayViews();
		detachAllOverlayWindows();
	}

	private void clearStayViews() {
		stayViews.clear();
		if (layer != null) {
			layer.removeAllViews();
		}
	}

	private void layoutView(final GaugeWidgetView view, final GaugeWidget g,
			final boolean overlay) {
		MainWindow activity = host.getMainWindow();
		if (activity == null || view == null || g == null) {
			return;
		}
		float density = density();
		boolean unplaced = g.isUnplaced();
		int[] geo = readGeometry(g, isLandscape());
		int xPx = unplaced ? 0 : Math.round(geo[0] * density);
		int yPx = unplaced ? 0 : Math.round(geo[1] * density);
		int wPx = Math.round(geo[2] * density);
		int hPx = Math.round(geo[3] * density);
		View parent = overlay ? windowContainer() : layer;
		int parentW = parent != null ? parent.getWidth() : 0;
		int parentH = parent != null ? parent.getHeight() : 0;
		if (parentW <= 0) {
			parentW = activity.getResources().getDisplayMetrics().widthPixels;
		}
		if (parentH <= 0) {
			parentH = activity.getResources().getDisplayMetrics().heightPixels;
		}
		int minPx = GaugeResizeGrip.gripPx(density);
		int[] wh = GaugeGeometry.clampSize(wPx, hPx, minPx, parentW, parentH);
		if (unplaced) {
			int[] c = GaugeSpawnPlacement.center(parentW, parentH, wh[0], wh[1]);
			xPx = c[0];
			yPx = c[1];
		}
		int[] xy = clampPosition(xPx, yPx, wh[0], wh[1], parent, overlay);
		placeView(g.getId(), view, xy[0], xy[1], wh[0], wh[1], overlay);
		boolean parentMeasured = parent != null && parent.getWidth() > 0
				&& parent.getHeight() > 0;
		if (unplaced && parentMeasured) {
			writeGeometryFromPx(g, xy[0], xy[1], wh[0], wh[1]);
			if (isLandscape() && g.isUnplaced()) {
				float d = density();
				if (d <= 0f) {
					d = 1f;
				}
				writeGeometry(g, false, Math.round(xy[0] / d), Math.round(xy[1] / d),
						Math.round(wh[0] / d), Math.round(wh[1] / d));
			}
			flushPersist();
		}
	}

	private void applyMove(final String id, final int x, final int y) {
		GaugeWidgetView view = findView(id);
		GaugeWidget g = findWidget(id);
		if (view == null || g == null) {
			return;
		}
		boolean overlay = overlayViews.containsKey(id);
		View parent = overlay ? windowContainer() : layer;
		int[] localXy = overlay ? screenToContainer(x, y)
				: screenToParent(parent, x, y);
		int w = view.getWidth() > 0 ? view.getWidth() : overlayWidth(id, view);
		int h = view.getHeight() > 0 ? view.getHeight() : overlayHeight(id, view);
		int[] xy = clampPosition(localXy[0], localXy[1], w, h, parent, overlay);
		placeView(id, view, xy[0], xy[1], w, h, overlay);
		writeGeometryFromPx(g, xy[0], xy[1], w, h);
	}

	private void applyResize(final String id, final int w, final int h) {
		GaugeWidgetView view = findView(id);
		GaugeWidget g = findWidget(id);
		if (view == null || g == null) {
			return;
		}
		boolean overlay = overlayViews.containsKey(id);
		View parent = overlay ? windowContainer() : layer;
		float density = density();
		int minPx = GaugeResizeGrip.gripPx(density);
		int parentW = parent != null && parent.getWidth() > 0
				? parent.getWidth() : Integer.MAX_VALUE;
		int parentH = parent != null && parent.getHeight() > 0
				? parent.getHeight() : Integer.MAX_VALUE;
		int[] wh = GaugeGeometry.clampSize(w, h, minPx, parentW, parentH);
		int x;
		int y;
		if (overlay) {
			WindowManager.LayoutParams p = overlayParamsById.get(id);
			int[] c = screenToContainer(p != null ? p.x : 0, p != null ? p.y : 0);
			x = c[0];
			y = c[1];
		} else {
			FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
			x = lp != null ? lp.leftMargin : 0;
			y = lp != null ? lp.topMargin : 0;
		}
		int[] xy = clampPosition(x, y, wh[0], wh[1], parent, overlay);
		placeView(id, view, xy[0], xy[1], wh[0], wh[1], overlay);
		writeGeometryFromPx(g, xy[0], xy[1], wh[0], wh[1]);
	}

	private void placeView(final String id, final GaugeWidgetView view,
			final int x, final int y, final int w, final int h,
			final boolean overlay) {
		if (overlay) {
			int[] screen = containerToScreen(x, y);
			updateOverlayLayout(id, screen[0], screen[1], w, h);
		} else {
			applyMargins(view, x, y, w, h);
		}
	}

	private int overlayWidth(final String id, final GaugeWidgetView view) {
		WindowManager.LayoutParams p = overlayParamsById.get(id);
		if (p != null && p.width > 0) {
			return p.width;
		}
		return layoutWidth(view);
	}

	private int overlayHeight(final String id, final GaugeWidgetView view) {
		WindowManager.LayoutParams p = overlayParamsById.get(id);
		if (p != null && p.height > 0) {
			return p.height;
		}
		return layoutHeight(view);
	}

	private int[] clampPosition(final int x, final int y, final int w, final int h,
			final View parent, final boolean overlay) {
		int parentW = parent != null && parent.getWidth() > 0
				? parent.getWidth()
				: host.getMainWindow() != null
						? host.getMainWindow().getResources().getDisplayMetrics().widthPixels
						: w;
		int parentH = parent != null && parent.getHeight() > 0
				? parent.getHeight()
				: host.getMainWindow() != null
						? host.getMainWindow().getResources().getDisplayMetrics().heightPixels
						: h;
		int maxBottom = parentH;
		int[] parentLoc = new int[2];
		if (parent != null) {
			parent.getLocationOnScreen(parentLoc);
		}
		if (!overlay) {
			View inputbar = host.findGameplayInputBar();
			if (inputbar != null && inputbar.getHeight() > 0) {
				int[] barLoc = new int[2];
				inputbar.getLocationOnScreen(barLoc);
				int barTopInParent = barLoc[1] - parentLoc[1];
				if (barTopInParent > 0 && barTopInParent < maxBottom) {
					maxBottom = barTopInParent;
				}
			}
		}
		int screenLeft = parentLoc[0] + x;
		int screenRight = screenLeft + w;
		int screenMaxBottom = parentLoc[1] + maxBottom;
		int limited = host.floatingOverlayBottomLimit(screenMaxBottom, screenLeft,
				screenRight);
		maxBottom = limited - parentLoc[1];
		int cx = FloatingLayerGeometry.clampX(x, w, parentW);
		int cy = FloatingLayerGeometry.clampY(y, h, maxBottom);
		return new int[] { cx, cy };
	}

	private void applyMargins(final View view, final int x, final int y,
			final int w, final int h) {
		FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
		if (lp == null) {
			lp = new FrameLayout.LayoutParams(w, h);
			lp.gravity = Gravity.TOP | Gravity.START;
		}
		lp.width = w;
		lp.height = h;
		lp.leftMargin = x;
		lp.topMargin = y;
		view.setLayoutParams(lp);
	}

	private void writeGeometryFromPx(final GaugeWidget g, final int xPx,
			final int yPx, final int wPx, final int hPx) {
		float d = density();
		if (d <= 0f) {
			d = 1f;
		}
		writeGeometry(g, isLandscape(), Math.round(xPx / d), Math.round(yPx / d),
				Math.round(wPx / d), Math.round(hPx / d));
	}

	/**
	 * Stay-layer is MATCH_PARENT and still 0×0 in the same {@code rebuild}
	 * that {@code addView}s it, so the first centre used display pixels and
	 * did not persist ({@code parentMeasured} false). Post once the layer has
	 * a real size so UNPLACED widgets land in the game window and stay there.
	 */
	private void scheduleUnplacedCentre() {
		boolean any = false;
		for (int i = 0; i < widgets.size(); i++) {
			GaugeWidget g = widgets.get(i);
			if (g != null && g.isUnplaced()) {
				any = true;
				break;
			}
		}
		if (!any) {
			return;
		}
		final View v = layer != null ? layer : windowContainer();
		if (v == null) {
			return;
		}
		v.post(new Runnable() {
			@Override
			public void run() {
				reclampAll();
			}
		});
	}

	private void reclampWhenChromeIsMeasured() {
		if (chromeReclampScheduled) {
			return;
		}
		if (host.fabStripHasSize()) {
			return;
		}
		chromeReclampScheduled = true;
		host.whenFabStripMeasured(new Runnable() {
			@Override
			public void run() {
				chromeReclampScheduled = false;
				reclampAll();
			}
		});
	}

	private void reclampAll() {
		boolean overlayOk = canOverlay();
		for (int i = 0; i < widgets.size(); i++) {
			GaugeWidget g = widgets.get(i);
			if (g == null) {
				continue;
			}
			GaugeWidgetView view = findView(g.getId());
			if (view != null) {
				layoutView(view, g, usesOverlayWindow(g, overlayOk));
			}
		}
	}

	private void applyImeHide() {
		boolean imeUp = lastLiftPx > 0;
		for (Map.Entry<String, GaugeWidgetView> e : stayViews.entrySet()) {
			GaugeWidget g = findWidget(e.getKey());
			if (g == null || e.getValue() == null) {
				continue;
			}
			if (g.getImeMode() == GaugeWidget.ImeMode.HIDE) {
				e.getValue().setVisibility(imeUp ? View.GONE : View.VISIBLE);
			}
		}
	}

	private void applySelectionVisibility() {
		int vis = selectionHidden ? View.INVISIBLE : View.VISIBLE;
		if (layer != null) {
			layer.setVisibility(vis);
		}
		for (GaugeWidgetView v : overlayViews.values()) {
			if (v != null) {
				v.setVisibility(vis);
			}
		}
	}

	private void paintLiveValues() {
		for (int i = 0; i < widgets.size(); i++) {
			GaugeWidget g = widgets.get(i);
			if (g == null) {
				continue;
			}
			GaugeWidgetView view = findView(g.getId());
			if (view == null) {
				continue;
			}
			double[] amt = lastValues.get(g.getId());
			double v;
			double m;
			if (amt != null) {
				v = amt[0];
				m = amt[1];
				if (g.getShape() == GaugeWidget.Shape.TIMER) {
					g.setRemainSec(v);
					g.setDurationSec(m);
				} else {
					g.setLiveValue(v);
					g.setLiveMax(m);
				}
			} else if (g.getShape() == GaugeWidget.Shape.TIMER) {
				v = g.getRemainSec();
				m = g.getDurationSec();
			} else {
				v = g.getLiveValue();
				m = g.getLiveMax();
			}
			boolean low = false;
			if (g.getWarnPct() > 0 && m > 0 && !Double.isNaN(v) && !Double.isNaN(m)) {
				low = (v / m) * 100.0 <= g.getWarnPct();
			}
			view.setAmounts(v, m, low, g.getColorFill(), g.getColorTrack(),
					g.getColorWarn());
		}
	}

	private void maybeAskForOverlayPermission() {
		if (overlayPromptShown || overlayDeniedThisSession || canOverlay()) {
			return;
		}
		if (!hasOverlayWidgets()) {
			return;
		}
		final MainWindow activity = host.getMainWindow();
		if (activity == null || activity.isFinishing()) {
			return;
		}
		overlayPromptShown = true;
		try {
			new android.app.AlertDialog.Builder(activity)
					.setTitle("Let gauges sit on top of the keyboard?")
					.setMessage("Android only lets an app draw over the keyboard from a "
							+ "system overlay, which needs the \"Display over other apps\" "
							+ "permission.\n\nWithout it the gauge still works, but it stays "
							+ "on the game window instead of sitting on top of the keys.")
					.setPositiveButton("Open settings",
							new android.content.DialogInterface.OnClickListener() {
								@Override
								public void onClick(android.content.DialogInterface d,
										int which) {
									openOverlaySettings(activity);
								}
							})
					.setNegativeButton("Not now", null)
					.show();
		} catch (RuntimeException e) {
			BlowTorchLogger.logMinor("GaugeWidgetController.maybeAskForOverlayPermission",
					e);
		}
	}

	private void openOverlaySettings(final MainWindow activity) {
		try {
			android.content.Intent i = new android.content.Intent(
					Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
					android.net.Uri.parse("package:" + activity.getPackageName()));
			activity.startActivity(i);
		} catch (RuntimeException e) {
			try {
				activity.startActivity(new android.content.Intent(
						Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
			} catch (RuntimeException e2) {
				BlowTorchLogger.logMinor("GaugeWidgetController.openOverlaySettings", e2);
			}
		}
	}

	private boolean canOverlay() {
		if (overlayDeniedThisSession) {
			return false;
		}
		MainWindow a = host.getMainWindow();
		return a != null && Settings.canDrawOverlays(a);
	}

	private boolean usesOverlayWindow(final GaugeWidget g, final boolean overlayOk) {
		return overlayOk && g != null && g.getImeMode() == GaugeWidget.ImeMode.OVERLAY;
	}

	private boolean hasOverlayWidgets() {
		for (int i = 0; i < widgets.size(); i++) {
			GaugeWidget g = widgets.get(i);
			if (g != null && g.isVisible()
					&& g.getImeMode() == GaugeWidget.ImeMode.OVERLAY) {
				return true;
			}
		}
		return false;
	}

	private WindowManager windowManager() {
		MainWindow a = host.getMainWindow();
		return a == null ? null
				: (WindowManager) a.getSystemService(android.content.Context.WINDOW_SERVICE);
	}

	private void flushPersist() {
		if (widgets.isEmpty()) {
			return;
		}
		ArrayList<GaugeWidget> out = new ArrayList<GaugeWidget>();
		for (int i = 0; i < widgets.size(); i++) {
			GaugeWidget g = widgets.get(i);
			if (g != null) {
				out.add(g.copy());
			}
		}
		skipNextSync = true;
		host.persistGaugeWidgets(out);
	}

	private void sendWidgetCommand(final String id, final String command) {
		if (command == null || command.length() == 0) {
			return;
		}
		host.sendCommand(command);
	}

	private void setEditingId(final String id) {
		editingId = id != null && id.length() > 0 ? id : null;
		applyEditChrome();
	}

	private void applyEditChrome() {
		applyEditChromeMap(stayViews);
		applyEditChromeMap(overlayViews);
	}

	private void applyEditChromeMap(final HashMap<String, GaugeWidgetView> map) {
		for (Map.Entry<String, GaugeWidgetView> e : map.entrySet()) {
			GaugeWidgetView v = e.getValue();
			if (v != null) {
				v.setEditing(editingId != null && editingId.equals(e.getKey()));
			}
		}
	}

	private String commandForTap(final String id) {
		GaugeWidget g = findWidget(id);
		return g != null ? g.getTapCommand() : "";
	}

	private String commandForHold(final String id) {
		GaugeWidget g = findWidget(id);
		return g != null ? g.getHoldCommand() : "";
	}

	private String commandForSwipe(final String id, final String dir) {
		GaugeWidget g = findWidget(id);
		return g != null ? g.commandForSwipe(dir) : "";
	}

	private GaugeWidget findWidget(final String id) {
		return GaugeWidgetsStore.find(widgets, id);
	}

	private GaugeWidgetView findView(final String id) {
		GaugeWidgetView v = stayViews.get(id);
		if (v != null) {
			return v;
		}
		return overlayViews.get(id);
	}

	private ArrayList<GaugeWidget> copyWidgets() {
		ArrayList<GaugeWidget> out = new ArrayList<GaugeWidget>();
		for (int i = 0; i < widgets.size(); i++) {
			GaugeWidget g = widgets.get(i);
			if (g != null) {
				out.add(g.copy());
			}
		}
		return out;
	}

	private boolean isLandscape() {
		MainWindow a = host.getMainWindow();
		return a != null && a.getResources().getConfiguration().orientation
				== Configuration.ORIENTATION_LANDSCAPE;
	}

	private float density() {
		MainWindow a = host.getMainWindow();
		if (a == null) {
			return 1f;
		}
		float d = a.getResources().getDisplayMetrics().density;
		return d > 0f ? d : 1f;
	}

	private static int layoutWidth(final View view) {
		ViewGroup.LayoutParams lp = view.getLayoutParams();
		return lp != null ? lp.width : 0;
	}

	private static int layoutHeight(final View view) {
		ViewGroup.LayoutParams lp = view.getLayoutParams();
		return lp != null ? lp.height : 0;
	}
}

package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;

/**
 * Floating button copies over the game: reads button snapshots from UI Lua,
 * hosts {@link FloatingButtonView}s in a {@link FloatingLayer}, writes
 * {@code floatX}/{@code floatY} back on drag drop. Shape mirrors
 * {@link ExtraTextOverlayController}.
 *
 * <p><b>Two hosting modes.</b> With {@code SYSTEM_ALERT_WINDOW} granted each
 * button gets its own {@code TYPE_APPLICATION_OVERLAY} window and therefore
 * draws <em>over</em> the keyboard, staying wherever it was put. Without the
 * grant the buttons live in a {@link FloatingLayer} inside
 * {@code window_container}, which the window manager stacks below the IME — so
 * there they can only be kept clear of the keys, never on top of them.
 *
 * <p>One window per button, not one full-screen window: an overlay window
 * consumes every touch inside its own bounds, and returning {@code false} from
 * a view does not pass the event to the window underneath. A full-screen
 * overlay would eat the keyboard.
 */
public class FloatingButtonController {

	private static final String TAG = "FloatingButtons";
	static final String LAYER_TAG = "floating_button_layer";

	public interface Host {
		MainWindow getMainWindow();

		Handler getUiHandler();

		void sendCommand(String text);

		void loadButtonSet(String name);

		/** Apply floatX/Y in UI Lua then persist via saveButtons. */
		void persistFloatPosition(int buttonIndex, int floatX, int floatY);

		boolean isFloatingButtonsEnabled();

		boolean showGestureHints();

		boolean hapticPressEnabled();

		boolean hapticFlipEnabled();

		/** Current IME lift in px (0 when keyboard down). */
		int getImeLiftPx();

		/**
		 * Re-measure IME coverage from the visible frame when insets under-report.
		 * Returns the authoritative lift in px.
		 */
		int refreshImeLiftPx();
	}

	private final Host host;
	private final Handler uiHandler;
	private FloatingLayer layer;
	private final List<FloatingButtonView> views = new ArrayList<FloatingButtonView>();
	private boolean editingHidden;
	private boolean chromeReclampScheduled;
	private int lastImeLiftPx;
	/** True while buttons live in their own overlay windows. */
	private boolean overlayMode;
	/** Overlay params per view, so add and remove stay symmetric. */
	private final java.util.HashMap<FloatingButtonView, WindowManager.LayoutParams>
			overlayParams = new java.util.HashMap<FloatingButtonView, WindowManager.LayoutParams>();
	/** False while the activity is paused: no windows may be added then. */
	private boolean resumed;
	/** Last payload from Lua, so an IME change can rebuild without asking again. */
	private final List<FloatingButtonModel> lastModels = new ArrayList<FloatingButtonModel>();
	/** Asked for the overlay grant once already this activity. */
	private boolean overlayPromptShown;

	private final FloatingButtonView.Callbacks viewCallbacks = new FloatingButtonView.Callbacks() {
		@Override
		public void sendCommand(String text) {
			host.sendCommand(text);
		}

		@Override
		public void loadButtonSet(String name) {
			host.loadButtonSet(name);
		}

		@Override
		public void onFloatPositionChanged(int index, int x, int y) {
			// Live drag only — never persist per move (sync binder ~280ms).
		}

		@Override
		public void onFloatDragFinished(int index, int x, int y) {
			host.persistFloatPosition(index, x, y);
		}

		@Override
		public boolean showGestureHints() {
			return host.showGestureHints();
		}

		@Override
		public boolean hapticPressEnabled() {
			return host.hapticPressEnabled();
		}

		@Override
		public boolean hapticFlipEnabled() {
			return host.hapticFlipEnabled();
		}

		@Override
		public int parentWidth() {
			if (overlayMode) {
				return displayWidth();
			}
			return layer != null ? layer.getWidth() : 0;
		}

		@Override
		public void moveTo(FloatingButtonView view, int x, int y) {
			WindowManager.LayoutParams p = overlayParams.get(view);
			if (p != null) {
				p.x = x;
				p.y = y;
				WindowManager wm = windowManager();
				if (wm != null) {
					try {
						wm.updateViewLayout(view, p);
					} catch (IllegalArgumentException e) {
						// View already detached; the drop will be discarded.
					}
				}
				return;
			}
			FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
			if (lp == null) {
				lp = new FrameLayout.LayoutParams(view.getWidth(), view.getHeight());
			}
			lp.leftMargin = x;
			lp.topMargin = y;
			lp.width = view.getWidth();
			lp.height = view.getHeight();
			view.setLayoutParams(lp);
		}

		@Override
		public int[] positionOf(FloatingButtonView view) {
			WindowManager.LayoutParams p = overlayParams.get(view);
			if (p != null) {
				return new int[] { p.x, p.y };
			}
			FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
			if (lp != null) {
				return new int[] { lp.leftMargin, lp.topMargin };
			}
			return new int[] { view.getLeft(), view.getTop() };
		}

		@Override
		public int maxBottomFor(FloatingButtonView view) {
			if (overlayMode) {
				// Drawing over the keyboard, so there is nothing to keep clear
				// of. The display edge is the only limit — this is what stops
				// the button being shoved upward when the IME opens.
				return displayHeight();
			}
			int left = view.getLeft();
			int right = left + Math.max(view.getWidth(), 1);
			FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
			if (lp != null && lp.leftMargin > 0) {
				left = lp.leftMargin;
				right = left + Math.max(view.getWidth(), lp.width > 0 ? lp.width : 1);
			}
			FloatingButtonModel m = view.getModel();
			// Mode A: ceiling is the visible-frame bottom (top of soft keyboard).
			if (m != null && m.isKeyboardMode() && isSoftKeyboardCoveringLayer() && layer != null) {
				float density = view.getResources().getDisplayMetrics().density;
				return modeACeiling(left, right, density);
			}
			// Mode B: resting keep-out only (D11 — IME must not move the button).
			return computeMaxBottom(left, right);
		}

		@Override
		public void bringLayerUnderChrome() {
			FloatingButtonController.this.bringUnderChrome();
		}
	};

	public FloatingButtonController(Host host) {
		this.host = host;
		Handler h = host.getUiHandler();
		this.uiHandler = h != null ? h : new Handler(Looper.getMainLooper());
	}

	/**
	 * JSON payload from Lua: {@code {editing:bool, buttons:[...]}}.
	 * Called on the UI thread from {@link MainWindow#onFloatingButtonsChanged}.
	 */
	public void onButtonsChanged(String json) {
		if (!host.isFloatingButtonsEnabled()) {
			clearViews();
			setLayerVisible(false);
			return;
		}
		ensureLayer();
		try {
			JSONObject root = new JSONObject(json);
			editingHidden = root.optBoolean("editing", false);
			if (editingHidden) {
				clearViews();
				setLayerVisible(false);
				return;
			}
			JSONArray arr = root.optJSONArray("buttons");
			List<FloatingButtonModel> models = new ArrayList<FloatingButtonModel>();
			if (arr != null) {
				for (int i = 0; i < arr.length(); i++) {
					JSONObject o = arr.optJSONObject(i);
					if (o == null) {
						continue;
					}
					models.add(new FloatingButtonModel(o));
				}
			}
			rebuild(models);
		} catch (JSONException e) {
			BlowTorchLogger.logMinor(TAG + ".onButtonsChanged", e);
		}
	}

	public void onMasterSwitchChanged(boolean enabled) {
		if (!enabled) {
			clearViews();
			setLayerVisible(false);
		} else {
			// Lua will re-push on next load/notify; ask for a refresh if possible.
			MainWindow mw = host.getMainWindow();
			if (mw != null) {
				mw.windowCall("button_window", "notifyFloatingButtonsChanged", "");
			}
		}
	}

	/** IME lift changed — Mode A visibility / Y. Mode B untouched. */
	public void onImeLiftChanged(int liftPx) {
		// Use what was passed. This used to throw the argument away and ask a
		// frame-based estimator instead, which under adjustNothing always
		// answered 0 — so the correct height the insets listener had just
		// measured was discarded on the way in.
		lastImeLiftPx = Math.max(0, liftPx);
		if (editingHidden || !host.isFloatingButtonsEnabled()) {
			return;
		}
		if (overlayMode) {
			// Keyboard-mode windows exist only while the keys are up, so the set
			// of windows changes. Position never does — see rebuildOverlay.
			rebuild(new ArrayList<FloatingButtonModel>(lastModels));
			return;
		}
		if (layer == null) {
			return;
		}
		boolean imeUp = isSoftKeyboardCoveringLayer();
		for (FloatingButtonView v : views) {
			FloatingButtonModel m = v.getModel();
			if (m == null) {
				continue;
			}
			if (m.isKeyboardMode()) {
				v.setVisibility(imeUp ? View.VISIBLE : View.GONE);
				if (imeUp) {
					layoutChild(v, m, true);
				}
			}
		}
		bringUnderChrome();
	}

	public void bringUnderChrome() {
		if (layer == null) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		ChromeController chrome = activity != null ? activity.getChromeController() : null;
		if (chrome != null) {
			chrome.bringViewUnderChrome(layer);
		} else {
			layer.bringToFront();
		}
	}

	public void detach() {
		clearViews();
		if (layer != null) {
			ViewGroup parent = (ViewGroup) layer.getParent();
			if (parent != null) {
				parent.removeView(layer);
			}
			layer = null;
		}
	}

	/**
	 * Ask for the overlay grant the first time a floating button actually exists.
	 *
	 * <p>Deliberately not at startup: nobody should be asked for "display over
	 * other apps" before they have shown any interest in the feature. This fires
	 * when a payload first contains a floating button and the grant is missing —
	 * which is the moment the player ticked the box in the editor.
	 *
	 * <p>Never blocks anything. The button is already saved, and refusing only
	 * costs the over-the-keyboard placement: the in-window layer still draws it,
	 * kept clear of the keys instead of on top of them.
	 */
	private void maybeAskForOverlayPermission(List<FloatingButtonModel> models) {
		if (overlayPromptShown || models == null || models.isEmpty() || canOverlay()) {
			return;
		}
		final MainWindow activity = host.getMainWindow();
		if (activity == null || activity.isFinishing()) {
			return;
		}
		overlayPromptShown = true;
		try {
			new android.app.AlertDialog.Builder(activity)
					.setTitle("Let the button sit on top of the keyboard?")
					.setMessage("Android only lets an app draw over the keyboard from a "
							+ "system overlay, which needs the \"Display over other apps\" "
							+ "permission.\n\nWithout it the floating button still works, "
							+ "but it has to stay clear of the keys instead of sitting on "
							+ "top of them.")
					.setPositiveButton("Open settings", new android.content.DialogInterface.OnClickListener() {
						@Override
						public void onClick(android.content.DialogInterface d, int which) {
							openOverlaySettings(activity);
						}
					})
					.setNegativeButton("Not now", null)
					.show();
		} catch (RuntimeException e) {
			BlowTorchLogger.logMinor(TAG + ".maybeAskForOverlayPermission", e);
		}
	}

	private void openOverlaySettings(MainWindow activity) {
		try {
			android.content.Intent i = new android.content.Intent(
					Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
					android.net.Uri.parse("package:" + activity.getPackageName()));
			activity.startActivity(i);
		} catch (RuntimeException e) {
			// Some ROMs hide the per-app screen; the global list is better than
			// nothing, and a failure here must not take the editor down.
			try {
				activity.startActivity(new android.content.Intent(
						Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
			} catch (RuntimeException e2) {
				BlowTorchLogger.logMinor(TAG + ".openOverlaySettings", e2);
			}
		}
	}

	/** True when the player has granted "display over other apps". */
	private boolean canOverlay() {
		MainWindow a = host.getMainWindow();
		return a != null && Settings.canDrawOverlays(a);
	}

	private WindowManager windowManager() {
		MainWindow a = host.getMainWindow();
		return a == null ? null
				: (WindowManager) a.getSystemService(android.content.Context.WINDOW_SERVICE);
	}

	private int displayWidth() {
		MainWindow a = host.getMainWindow();
		return a == null ? 0 : a.getResources().getDisplayMetrics().widthPixels;
	}

	private int displayHeight() {
		MainWindow a = host.getMainWindow();
		return a == null ? 0 : a.getResources().getDisplayMetrics().heightPixels;
	}

	/**
	 * One window per button.
	 *
	 * <p>{@code FLAG_NOT_FOCUSABLE} so the keyboard keeps input focus — without
	 * it the overlay steals it and typing stops. {@code FLAG_NOT_TOUCH_MODAL} so
	 * everything outside this button's own rectangle still reaches the game and
	 * the keys.
	 */
	private WindowManager.LayoutParams newOverlayParams(int x, int y, int w, int h) {
		WindowManager.LayoutParams p = new WindowManager.LayoutParams(
				w > 0 ? w : WindowManager.LayoutParams.WRAP_CONTENT,
				h > 0 ? h : WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
						| WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
						| WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
				PixelFormat.TRANSLUCENT);
		p.gravity = Gravity.TOP | Gravity.START;
		p.x = x;
		p.y = y;
		return p;
	}

	private void attachOverlayView(FloatingButtonView v, int x, int y, int w, int h) {
		WindowManager wm = windowManager();
		if (wm == null || overlayParams.containsKey(v)) {
			return;
		}
		WindowManager.LayoutParams p = newOverlayParams(x, y, w, h);
		try {
			wm.addView(v, p);
			overlayParams.put(v, p);
		} catch (RuntimeException e) {
			// Permission revoked between the check and here, or the window
			// manager refused. Fall back rather than take the process down.
			BlowTorchLogger.logThrowable(TAG + ".attachOverlayView", e);
		}
	}

	private void removeOverlayView(FloatingButtonView v) {
		WindowManager.LayoutParams p = overlayParams.remove(v);
		WindowManager wm = windowManager();
		if (wm == null || p == null) {
			return;
		}
		try {
			wm.removeViewImmediate(v);
		} catch (IllegalArgumentException e) {
			// Already detached. Removing twice is not an error worth a log.
		}
	}

	/** Activity resumed: overlay windows may exist again. */
	public void onResume() {
		resumed = true;
		if (!host.isFloatingButtonsEnabled()) {
			return;
		}
		MainWindow mw = host.getMainWindow();
		if (mw != null) {
			// Lua re-pushes the current set; rebuild puts the windows back.
			mw.windowCall("button_window", "notifyFloatingButtonsChanged", "");
		}
	}

	/**
	 * Activity paused: every overlay window comes down.
	 *
	 * <p>An overlay floats over other apps and the home screen, so leaving it up
	 * would follow the player out of the game.
	 */
	public void onPause() {
		resumed = false;
		clearViews();
	}

	private void ensureLayer() {
		overlayMode = canOverlay();
		if (overlayMode) {
			// Buttons live in their own windows; no in-app layer is needed.
			detachLayerView();
			return;
		}
		if (layer != null) {
			bringUnderChrome();
			return;
		}
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		RelativeLayout container = (RelativeLayout) activity.findViewById(R.id.window_container);
		if (container == null) {
			return;
		}
		layer = new FloatingLayer(activity);
		layer.setTag(LAYER_TAG);
		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT);
		container.addView(layer, lp);
		bringUnderChrome();
	}

	private void rebuild(List<FloatingButtonModel> models) {
		if (models != lastModels) {
			lastModels.clear();
			lastModels.addAll(models);
		}
		maybeAskForOverlayPermission(models);
		ensureLayer();
		lastImeLiftPx = Math.max(0, host.refreshImeLiftPx());
		clearViews();
		boolean imeUp = isSoftKeyboardCoveringLayer();
		if (overlayMode) {
			rebuildOverlay(models, imeUp);
			return;
		}
		if (layer == null) {
			return;
		}
		setLayerVisible(true);
		for (FloatingButtonModel m : models) {
			FloatingButtonView v = new FloatingButtonView(layer.getContext());
			v.bind(m, viewCallbacks);
			boolean keyboard = m.isKeyboardMode();
			if (keyboard && !imeUp) {
				v.setVisibility(View.GONE);
			}
			FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT,
					ViewGroup.LayoutParams.WRAP_CONTENT);
			layer.addView(v, lp);
			views.add(v);
			layoutChild(v, m, keyboard && imeUp);
		}
		reclampWhenChromeIsMeasured();
		bringUnderChrome();
	}

	/**
	 * One overlay window per button, placed where the player left it.
	 *
	 * <p>Nothing here consults the keyboard height for position: the window is
	 * above the IME, so a button stays put whether the keys are up or down.
	 * Keyboard mode only decides whether the window exists at all.
	 */
	private void rebuildOverlay(List<FloatingButtonModel> models, boolean imeUp) {
		MainWindow activity = host.getMainWindow();
		if (activity == null || !resumed) {
			return;
		}
		float density = activity.getResources().getDisplayMetrics().density;
		int margin = Math.round(FloatingLayerGeometry.DEFAULT_MARGIN_DP * density);
		for (FloatingButtonModel m : models) {
			if (m.isKeyboardMode() && !imeUp) {
				// "Show only with keyboard": no window at all right now.
				continue;
			}
			FloatingButtonView v = new FloatingButtonView(activity);
			v.bind(m, viewCallbacks);
			v.measure(
					View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
					View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
			int w = Math.max(1, v.getMeasuredWidth());
			int h = Math.max(1, v.getMeasuredHeight());
			int x = m.floatX == FloatingLayerGeometry.UNPLACED
					? (m.hasGridOrigin
							? FloatingLayerGeometry.gridCenterToLeft(m.gridX, m.widthDp, density)
							: margin)
					: m.floatX;
			int y = m.floatY == FloatingLayerGeometry.UNPLACED
					? (m.hasGridOrigin
							? FloatingLayerGeometry.gridCenterToTop(
									m.gridY, m.heightDp, density, m.statusOffsetPx)
							: Math.max(0, displayHeight() - h - margin))
					: m.floatY;
			x = FloatingLayerGeometry.clampX(x, w, displayWidth());
			y = FloatingLayerGeometry.clampY(y, h, displayHeight());
			attachOverlayView(v, x, y, w, h);
			views.add(v);
		}
	}

	private void layoutChild(final FloatingButtonView v, final FloatingButtonModel m,
			final boolean keyboardAboveIme) {
		v.post(new Runnable() {
			@Override
			public void run() {
				if (layer == null || v.getParent() != layer) {
					return;
				}
				int w = v.getMeasuredWidth();
				int h = v.getMeasuredHeight();
				if (w <= 0 || h <= 0) {
					v.measure(
							View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
							View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
					w = v.getMeasuredWidth();
					h = v.getMeasuredHeight();
				}
				float density = v.getResources().getDisplayMetrics().density;
				int marginPx = Math.round(FloatingLayerGeometry.DEFAULT_MARGIN_DP * density);
				int resolvedX;
				if (m.floatX == FloatingLayerGeometry.UNPLACED && m.hasGridOrigin) {
					resolvedX = FloatingLayerGeometry.gridCenterToLeft(
							m.gridX, m.widthDp, density);
				} else if (m.floatX == FloatingLayerGeometry.UNPLACED) {
					resolvedX = Math.round(FloatingLayerGeometry.DEFAULT_MARGIN_DP * density);
				} else {
					resolvedX = m.floatX;
				}
				int maxBottom;
				if (keyboardAboveIme) {
					// Park against WindowVisibleDisplayFrame.bottom — the real
					// top of the soft keyboard on screen — not layerHeight−lift
					// (that mismatch parked Mode A under the keys).
					maxBottom = modeACeiling(resolvedX, resolvedX + w, density);
				} else {
					maxBottom = computeMaxBottom(resolvedX, resolvedX + w);
				}
				if (keyboardAboveIme) {
					// Mode A: X from drag/grid; Y just above the keys.
					int x = FloatingLayerGeometry.clampX(resolvedX, w, layer.getWidth());
					int y = FloatingLayerGeometry.clampY(
							Math.max(0, maxBottom - h),
							h, maxBottom);
					applyLayoutParams(v, x, y, w, h);
					return;
				}
				int resolvedY;
				if (m.floatY == FloatingLayerGeometry.UNPLACED && m.hasGridOrigin) {
					resolvedY = FloatingLayerGeometry.gridCenterToTop(
							m.gridY, m.heightDp, density, m.statusOffsetPx);
				} else if (m.floatY == FloatingLayerGeometry.UNPLACED) {
					resolvedY = Math.max(0, maxBottom - h - marginPx);
				} else {
					resolvedY = m.floatY;
				}
				int x = FloatingLayerGeometry.clampX(resolvedX, w, layer.getWidth());
				int y = FloatingLayerGeometry.clampY(resolvedY, h, maxBottom);
				applyLayoutParams(v, x, y, w, h);
			}
		});
	}

	private void applyLayoutParams(FloatingButtonView v, int x, int y, int w, int h) {
		FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
		if (lp == null) {
			lp = new FrameLayout.LayoutParams(w, h);
		}
		lp.width = w;
		lp.height = h;
		lp.leftMargin = x;
		lp.topMargin = y;
		v.setLayoutParams(lp);
	}

	/**
	 * Exclusive bottom for Mode A: the top of the soft keyboard.
	 *
	 * <p>The layer is pinned — {@code applyImeChromeLift} explicitly leaves it at
	 * {@code translationY 0} — so its height is the full window and subtracting
	 * the IME height gives the keyboard's top directly, with nothing to
	 * double-count. This stays inside our window; it is not a system overlay, so
	 * it cannot draw *on* the keyboard, only above it.
	 */
	private int modeACeiling(int overlayLeft, int overlayRight, float density) {
		int gap = Math.round(4 * density);
		int keyboardTop = Math.max(0, layer.getHeight() - lastImeLiftPx);
		int ceiling = chromeKeepOut(keyboardTop, overlayLeft, overlayRight, true);
		return Math.max(0, ceiling - gap);
	}

	/**
	 * True when the soft keyboard is up, so Mode A buttons should show.
	 *
	 * <p>One authority: the IME inset the chrome listener measured. The previous
	 * version preferred {@code getWindowVisibleDisplayFrame} whenever the layer
	 * had a real size — which is always — and that frame cannot answer here: the
	 * manifest says {@code adjustNothing}, the window is never resized for the
	 * keyboard, and the frame therefore never shrinks.
	 *
	 * <p>A floor of 120dp so a stray small inset is not mistaken for a keyboard.
	 */
	private boolean isSoftKeyboardCoveringLayer() {
		if (layer == null) {
			return lastImeLiftPx > 0;
		}
		float density = layer.getResources().getDisplayMetrics().density;
		return lastImeLiftPx >= Math.round(120 * density);
	}

	/**
	 * Resting keep-out for Mode B: input bar in layout coordinates, ignoring IME
	 * {@code translationY}. Using the lifted bar's screen Y would clamp floaters
	 * upward — the keyboard "pushing" them (D11 forbids that).
	 */
	private int computeMaxBottom(int overlayLeft, int overlayRight) {
		MainWindow activity = host.getMainWindow();
		if (activity == null || layer == null) {
			return 0;
		}
		int maxBottom = layer.getHeight();
		int inputTop = computeInputBarTopInLayer(false);
		if (inputTop > 0) {
			maxBottom = inputTop;
		}
		return chromeKeepOut(maxBottom, overlayLeft, overlayRight, false);
	}

	/**
	 * Top of the input bar in floating-layer coordinates, or 0 if unknown.
	 *
	 * @param includeImeTranslation if false, use layout {@link View#getTop()} so
	 *        IME {@code translationY} cannot push Mode B (D11) while height
	 *        changes (search chrome) still move the resting ceiling.
	 */
	private int computeInputBarTopInLayer(boolean includeImeTranslation) {
		MainWindow activity = host.getMainWindow();
		if (activity == null || layer == null) {
			return 0;
		}
		View inputbar = findInputBar(activity);
		if (inputbar == null || inputbar.getHeight() <= 0) {
			return 0;
		}
		if (!includeImeTranslation) {
			// Same parent (window_container): layout top ignores translationY.
			return Math.max(0, inputbar.getTop() - layer.getTop());
		}
		int[] layerLoc = new int[2];
		int[] barLoc = new int[2];
		layer.getLocationOnScreen(layerLoc);
		inputbar.getLocationOnScreen(barLoc);
		return Math.max(0, barLoc[1] - layerLoc[1]);
	}

	/**
	 * @param includeImeTranslation Mode A uses live screen keep-out (⋮ rises with
	 *        the bar). Mode B uses resting coordinates so a lifted ⋮ cannot pull
	 *        the floater up (D11).
	 */
	private int chromeKeepOut(int maxBottom, int overlayLeft, int overlayRight,
			boolean includeImeTranslation) {
		MainWindow activity = host.getMainWindow();
		if (activity == null || layer == null) {
			return maxBottom;
		}
		if (!includeImeTranslation) {
			return restingFabKeepOut(activity, maxBottom, overlayLeft, overlayRight);
		}
		ChromeController chrome = activity.getChromeController();
		if (chrome == null) {
			return maxBottom;
		}
		int[] layerLoc = new int[2];
		layer.getLocationOnScreen(layerLoc);
		int screenMax = layerLoc[1] + maxBottom;
		int limited = chrome.floatingOverlayBottomLimit(
				screenMax, layerLoc[0] + overlayLeft, layerLoc[0] + overlayRight);
		return Math.max(0, limited - layerLoc[1]);
	}

	/**
	 * ⋮ keep-out in resting (keyboard-down) space: undo the FAB strip's IME
	 * translation so Mode B is not clamped upward while the keyboard is up.
	 */
	private int restingFabKeepOut(MainWindow activity, int maxBottom,
			int overlayLeft, int overlayRight) {
		View fabStrip = activity.findViewById(R.id.gameplay_fab_strip);
		if (fabStrip == null || fabStrip.getVisibility() != View.VISIBLE
				|| fabStrip.getWidth() <= 0 || fabStrip.getHeight() <= 0
				|| layer == null) {
			return maxBottom;
		}
		int[] layerLoc = new int[2];
		int[] fabLoc = new int[2];
		layer.getLocationOnScreen(layerLoc);
		fabStrip.getLocationOnScreen(fabLoc);
		int stripLeft = fabLoc[0];
		int stripRight = fabLoc[0] + fabStrip.getWidth();
		int overlayLeftScreen = layerLoc[0] + overlayLeft;
		int overlayRightScreen = layerLoc[0] + overlayRight;
		if (overlayRightScreen <= stripLeft || overlayLeftScreen >= stripRight) {
			return maxBottom;
		}
		int stripTopResting = fabLoc[1] - (int) fabStrip.getTranslationY();
		int stripTopInLayer = stripTopResting - layerLoc[1]
				+ (int) layer.getTranslationY();
		if (stripTopInLayer < maxBottom) {
			return Math.max(0, stripTopInLayer);
		}
		return maxBottom;
	}

	private void reclampWhenChromeIsMeasured() {
		if (chromeReclampScheduled) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		ChromeController chrome = activity != null ? activity.getChromeController() : null;
		if (chrome == null || chrome.fabStripHasSize()) {
			return;
		}
		chromeReclampScheduled = true;
		chrome.whenFabStripMeasured(new Runnable() {
			@Override
			public void run() {
				chromeReclampScheduled = false;
				for (FloatingButtonView v : views) {
					FloatingButtonModel m = v.getModel();
					if (m != null) {
						boolean kb = m.isKeyboardMode() && isSoftKeyboardCoveringLayer();
						layoutChild(v, m, kb);
					}
				}
			}
		});
	}

	private void clearViews() {
		for (FloatingButtonView v : views) {
			if (overlayParams.containsKey(v)) {
				removeOverlayView(v);
			} else if (layer != null) {
				layer.removeView(v);
			}
		}
		views.clear();
		overlayParams.clear();
	}

	/** Take the in-app layer down when the buttons move to overlay windows. */
	private void detachLayerView() {
		if (layer == null) {
			return;
		}
		if (layer.getParent() instanceof ViewGroup) {
			((ViewGroup) layer.getParent()).removeView(layer);
		}
		layer = null;
	}

	private void setLayerVisible(boolean visible) {
		if (layer != null) {
			layer.setVisibility(visible ? View.VISIBLE : View.GONE);
		}
	}

	private static View findInputBar(MainWindow activity) {
		ViewGroup container = (ViewGroup) activity.findViewById(R.id.window_container);
		if (container == null) {
			return null;
		}
		View inputbar = container.findViewById(ChromeController.LEGACY_INPUT_BAR_ID);
		if (inputbar == null) {
			inputbar = container.findViewById(R.id.inputbar);
		}
		return inputbar;
	}
}

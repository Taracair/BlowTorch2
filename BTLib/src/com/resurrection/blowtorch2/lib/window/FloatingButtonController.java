package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;

/**
 * Floating copies of grid buttons. With {@code SYSTEM_ALERT_WINDOW}, each
 * button is two overlay windows: a button-sized touch proxy and a larger
 * {@code FLAG_NOT_TOUCHABLE} hint surface — one padded touchable window would
 * swallow IME taps in the hint band. Without the grant, a {@link FloatingLayer}
 * inside {@code window_container} sits below the IME.
 */
public class FloatingButtonController {

	private static final String TAG = "FloatingButtons";
	static final String LAYER_TAG = "floating_button_layer";

	/**
	 * Overlay hosting for one button: visual surface (padded, not touchable)
	 * plus a transparent touch proxy sized to the visible button.
	 */
	private static final class OverlayWindows {
		final FloatingButtonView visual;
		final OverlayTouchProxy touch;
		WindowManager.LayoutParams visualParams;
		WindowManager.LayoutParams touchParams;

		OverlayWindows(FloatingButtonView visual, OverlayTouchProxy touch) {
			this.visual = visual;
			this.touch = touch;
		}
	}

	/**
	 * Button-sized transparent window that owns the gesture. Forwards events
	 * into the padded {@link FloatingButtonView} with a local-coordinate offset
	 * so {@code containsLocal} still matches the visible tile.
	 */
	private static final class OverlayTouchProxy extends View {
		private FloatingButtonView target;
		private int offsetX;
		private int offsetY;

		OverlayTouchProxy(Context context) {
			super(context);
			// Pure hit target — the padded FloatingButtonView draws underneath.
			setWillNotDraw(true);
		}

		void bind(FloatingButtonView target, int offsetX, int offsetY) {
			this.target = target;
			this.offsetX = offsetX;
			this.offsetY = offsetY;
		}

		@Override
		public boolean onTouchEvent(MotionEvent event) {
			if (target == null) {
				return false;
			}
			MotionEvent shifted = MotionEvent.obtain(event);
			try {
				shifted.offsetLocation(offsetX, offsetY);
				return target.dispatchTouchEvent(shifted);
			} finally {
				shifted.recycle();
			}
		}
	}

	public interface Host {
		MainWindow getMainWindow();

		Handler getUiHandler();

		void sendCommand(String text);

		void loadButtonSet(String name);

		/**
		 * Apply floatX/Y in UI Lua then persist via saveButtons.
		 *
		 * @param gridX centre of the button where the drag left it, in the
		 *        coordinates the grid uses — measured to be the same screen
		 *        coordinates, so no status-bar term belongs here.
		 *        {@link Integer#MIN_VALUE} leaves the grid alone (landscape).
		 */
		void persistFloatPosition(int buttonIndex, int floatX, int floatY, int gridX, int gridY);

		boolean isFloatingButtonsEnabled();

		boolean showGestureHints();

		boolean hapticPressEnabled();

		boolean hapticFlipEnabled();

		/** Current IME lift in px (0 when keyboard down). */
		int getImeLiftPx();

		/** Last {@code isVisible(ime)} from the chrome insets listener. */
		boolean isImeVisible();

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
	/** Last {@code isVisible(ime)} from chrome. Starts false (keyboard down). */
	private boolean lastImeVisible;
	/**
	 * Hide issued (or IME visibility dropped) while lift is still high.
	 * Hysteresis would keep Mode A up until the hide animation ends.
	 */
	private final SoftKeyboardCoverage.HideRequest imeHideRequest =
			new SoftKeyboardCoverage.HideRequest();
	/** True while buttons live in their own overlay windows. */
	private boolean overlayMode;
	/** Keyboard covering state last committed to overlay / in-layer windows. */
	private boolean lastImeCovering;
	private Runnable pendingImeCoveringApply;
	/** Overlay pair per visual view, so add and remove stay symmetric. */
	private final java.util.HashMap<FloatingButtonView, OverlayWindows> overlays =
			new java.util.HashMap<FloatingButtonView, OverlayWindows>();
	/**
	 * False only while the activity is paused.
	 *
	 * <p>Starts true on purpose. It used to start false and wait for
	 * {@code onResume}, but the controller is built from
	 * {@code onServiceConnected}, which runs *after* the first resume — so
	 * {@code MainWindow.onResume}'s null check skipped it and nothing ever set
	 * this, leaving rebuildOverlay to return before adding a single window.
	 */
	private boolean resumed = true;
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
			// Lua is updated below; keep lastModels in step too. Otherwise the next
			// IME/chrome rebuild (e.g. .sendbutton toggling the input bar) recreates
			// overlay windows from the pre-drag snapshot and the button snaps back.
			// Where the finger left it, as the grid stores it: the centre of the
			// button. Portrait only -- the grid keeps one position and it is the
			// portrait one. Without this the drag would be undone by the next
			// rebuild, which places the button from the grid.
			int gridX = Integer.MIN_VALUE;
			int gridY = Integer.MIN_VALUE;
			FloatingButtonView dragged = findViewFor(index);
			FloatingButtonModel model = dragged != null ? dragged.getModel() : null;
			// In landscape only once the grid has a landscape layout of its own,
			// otherwise a drag here would move the button in portrait as well.
			if (dragged != null
					&& (!isLandscape() || (model != null && model.hasOwnLandscapeGrid()))) {
				gridX = x + Math.max(dragged.buttonWidthPx(), 1) / 2;
				gridY = y + Math.max(dragged.buttonHeightPx(), 1) / 2;
			}
			rememberFloatPosition(index, x, y, gridX, gridY);
			host.persistFloatPosition(index, x, y, gridX, gridY);
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
			// x,y are the visible button's top-left — the same space as floatX/Y.
			OverlayWindows pair = overlays.get(view);
			if (pair != null) {
				updateOverlayPairLayout(pair, x, y);
				return;
			}
			int padLeft = view.hintPadLeftPx();
			int padTop = view.hintPadTopPx();
			FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
			if (lp == null) {
				lp = new FrameLayout.LayoutParams(view.getWidth(), view.getHeight());
			}
			lp.leftMargin = x - padLeft;
			lp.topMargin = y - padTop;
			lp.width = view.getWidth();
			lp.height = view.getHeight();
			view.setLayoutParams(lp);
		}

		@Override
		public int[] positionOf(FloatingButtonView view) {
			OverlayWindows pair = overlays.get(view);
			if (pair != null && pair.touchParams != null) {
				// Touch window is exactly the visible button — floatX/Y space.
				return new int[] { pair.touchParams.x, pair.touchParams.y };
			}
			int padLeft = view.hintPadLeftPx();
			int padTop = view.hintPadTopPx();
			FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
			if (lp != null) {
				return new int[] { lp.leftMargin + padLeft, lp.topMargin + padTop };
			}
			return new int[] { view.getLeft() + padLeft, view.getTop() + padTop };
		}

		@Override
		public int maxBottomFor(FloatingButtonView view) {
			if (overlayMode) {
				// Drawing over the keyboard, so there is nothing to keep clear
				// of. The display edge is the only limit — this is what stops
				// the button being shoved upward when the IME opens.
				return displayHeight();
			}
			int padLeft = view.hintPadLeftPx();
			int buttonWidth = Math.max(view.buttonWidthPx(), 1);
			int left = view.getLeft() + padLeft;
			int right = left + buttonWidth;
			FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
			if (lp != null && lp.leftMargin > 0) {
				left = lp.leftMargin + padLeft;
				right = left + buttonWidth;
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

	/** Which of the two stored position pairs is live right now. */
	private boolean isLandscape() {
		MainWindow mw = host.getMainWindow();
		if (mw == null) {
			return false;
		}
		return mw.getResources().getConfiguration().orientation
				== android.content.res.Configuration.ORIENTATION_LANDSCAPE;
	}

	/**
	 * The device turned. The activity survives a turn
	 * ({@code configChanges="orientation"}), so nothing else re-places the
	 * buttons — read the other stored pair and rebuild from it.
	 *
	 * <p>Both pairs travel on every snapshot, so this needs no round trip to
	 * Lua: a push would be a synchronous binder call (~280ms) on the UI thread
	 * during a turn, which is exactly what the drag path already avoids.
	 */
	public void onOrientationChanged() {
		if (editingHidden || !host.isFloatingButtonsEnabled()) {
			return;
		}
		boolean land = isLandscape();
		if (lastModels == null || lastModels.isEmpty()) {
			return;
		}
		if (lastModels.get(0).landscape == land) {
			return;
		}
		List<FloatingButtonModel> turned = new ArrayList<FloatingButtonModel>(lastModels.size());
		for (FloatingButtonModel m : lastModels) {
			turned.add(m.forOrientation(land));
		}
		rebuild(turned);
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
					models.add(new FloatingButtonModel(o, isLandscape()));
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

	/** IME lift changed — Mode A visibility. Mode B untouched. */
	public void onImeLiftChanged(int liftPx, boolean imeVisible) {
		// Use what was passed. This used to throw the argument away and ask a
		// frame-based estimator instead, which under adjustNothing always
		// answered 0 — so the correct height the insets listener had just
		// measured was discarded on the way in.
		boolean previousVisible = lastImeVisible;
		lastImeLiftPx = Math.max(0, liftPx);
		lastImeVisible = imeVisible;
		// Visibility dropping while we had already seen the IME is hide starting
		// (swipe, back, or hideSoftInput). Always-false isVisible never trips this,
		// so Mode A still follows lift on a device where that flag is broken.
		if (previousVisible && !imeVisible) {
			imeHideRequest.request();
		}
		imeHideRequest.tick(lastImeLiftPx, density());
		if (editingHidden || !host.isFloatingButtonsEnabled()) {
			return;
		}
		scheduleImeCoveringApply();
	}

	/**
	 * hideSoftInput was asked. Mode A windows come down now, not when the IME
	 * inset finally falls under {@link SoftKeyboardCoverage#HIDE_DP}.
	 */
	public void onKeyboardDismissRequested() {
		imeHideRequest.request();
		imeHideRequest.tick(lastImeLiftPx, density());
		if (editingHidden || !host.isFloatingButtonsEnabled()) {
			return;
		}
		cancelPendingImeCovering();
		applyImeCovering();
	}

	/**
	 * Keyboard animation sends several IME insets. Adding overlay windows on the
	 * first one that crosses 120dp, then again if a later inset dips under that
	 * floor, is the double blink (teardown + attach, twice). Wait until covering
	 * has been quietly true for {@link SoftKeyboardCoverage#SETTLE_MS}.
	 * Removing them is immediate: the same wait left Mode A hanging in the air
	 * for the hide animation.
	 */
	private void scheduleImeCoveringApply() {
		boolean covering = computeImeCovering();
		if (covering == lastImeCovering) {
			cancelPendingImeCovering();
			if (!overlayMode && covering) {
				relayoutKeyboardModeChildren();
			}
			return;
		}
		cancelPendingImeCovering();
		if (!covering) {
			applyImeCovering();
			return;
		}
		pendingImeCoveringApply = new Runnable() {
			@Override
			public void run() {
				pendingImeCoveringApply = null;
				applyImeCovering();
			}
		};
		uiHandler.postDelayed(pendingImeCoveringApply,
				SoftKeyboardCoverage.settleMs(true));
	}

	/** Follow IME height while already covering. Overlay Y does not use lift. */
	private void relayoutKeyboardModeChildren() {
		if (layer == null) {
			return;
		}
		for (FloatingButtonView v : views) {
			FloatingButtonModel m = v.getModel();
			if (m != null && m.isKeyboardMode()) {
				layoutChild(v, m, true);
			}
		}
	}

	private void applyImeCovering() {
		boolean covering = computeImeCovering();
		if (covering == lastImeCovering) {
			return;
		}
		lastImeCovering = covering;
		if (overlayMode) {
			syncLastModelsFromLiveOverlayPositions();
			updateKeyboardModeOverlays(covering);
			return;
		}
		if (layer == null) {
			return;
		}
		for (FloatingButtonView v : views) {
			FloatingButtonModel m = v.getModel();
			if (m == null) {
				continue;
			}
			if (m.isKeyboardMode()) {
				v.setVisibility(covering ? View.VISIBLE : View.GONE);
				if (covering) {
					layoutChild(v, m, true);
				}
			}
		}
		bringUnderChrome();
	}

	private void cancelPendingImeCovering() {
		if (pendingImeCoveringApply != null) {
			uiHandler.removeCallbacks(pendingImeCoveringApply);
			pendingImeCoveringApply = null;
		}
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
	 * Overlay window flags for one surface of a button pair.
	 *
	 * <p>{@code FLAG_NOT_FOCUSABLE} so the keyboard keeps input focus — without
	 * it the overlay steals it and typing stops. Touchable windows also get
	 * {@code FLAG_NOT_TOUCH_MODAL} so everything outside that window's rectangle
	 * still reaches the game and the keys. Hint windows use
	 * {@code FLAG_NOT_TOUCHABLE} so the padding band never swallows taps.
	 */
	private WindowManager.LayoutParams newOverlayParams(int x, int y, int w, int h,
			boolean touchable) {
		int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
				| WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
		if (touchable) {
			flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
		} else {
			// The hint window is the button plus a band around it for the gesture
			// hints, so near an edge it does not fit on the screen -- and the
			// window manager slides a window that does not fit back inside.
			// Measured: asked for y=2206 with height 263 on a 2424 screen, got
			// 2161, so the tile was drawn 45px above its own touch window while
			// the button beside it, further from the edge, was drawn correctly.
			// NO_LIMITS lets the band hang off the screen instead of dragging the
			// button with it. It draws nothing there and takes no touches.
			flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
					| WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
		}
		WindowManager.LayoutParams p = new WindowManager.LayoutParams(
				w > 0 ? w : WindowManager.LayoutParams.WRAP_CONTENT,
				h > 0 ? h : WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
				flags,
				PixelFormat.TRANSLUCENT);
		p.gravity = Gravity.TOP | Gravity.START;
		p.x = x;
		p.y = y;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
			// Fit nothing: p.y is then the position on the screen, full stop.
			//
			// By default the window manager insets an overlay window by the
			// status and navigation bars, so p.y is measured inside that safe
			// area while getLocationOnScreen -- which is what the drag works in
			// -- is measured from the top of the screen. The two agree only
			// while the bars are exactly as they were when the button was
			// dropped, and coming back from the background is precisely when
			// that changes: the button reappears a status bar lower.
			// FLAG_LAYOUT_IN_SCREEN used to say this; since API 30 this does.
			p.setFitInsetsTypes(0);
		}
		return p;
	}

	/**
	 * Attach the dual overlay pair for one button.
	 *
	 * @param buttonX visible-button top-left X ({@code floatX} space)
	 * @param buttonY visible-button top-left Y ({@code floatY} space)
	 * @param buttonW visible button width
	 * @param buttonH visible button height
	 * @param visualW padded visual width (button + hint band)
	 * @param visualH padded visual height
	 */
	private void attachOverlayPair(FloatingButtonView v, int buttonX, int buttonY,
			int buttonW, int buttonH, int visualW, int visualH) {
		WindowManager wm = windowManager();
		if (wm == null || overlays.containsKey(v)) {
			return;
		}
		int padLeft = v.hintPadLeftPx();
		int padTop = v.hintPadTopPx();
		OverlayTouchProxy touch = new OverlayTouchProxy(v.getContext());
		touch.bind(v, padLeft, padTop);
		WindowManager.LayoutParams visualParams = newOverlayParams(
				buttonX - padLeft, buttonY - padTop, visualW, visualH, false);
		WindowManager.LayoutParams touchParams = newOverlayParams(
				buttonX, buttonY, buttonW, buttonH, true);
		OverlayWindows pair = new OverlayWindows(v, touch);
		pair.visualParams = visualParams;
		pair.touchParams = touchParams;
		try {
			// Visual first (under); touch proxy on top so it owns the gesture.
			wm.addView(v, visualParams);
			wm.addView(touch, touchParams);
			overlays.put(v, pair);
		} catch (RuntimeException e) {
			// Permission revoked between the check and here, or the window
			// manager refused. Tear down any half-attached pair.
			BlowTorchLogger.logThrowable(TAG + ".attachOverlayPair", e);
			try {
				wm.removeViewImmediate(touch);
			} catch (RuntimeException ignored) {
				// not attached
			}
			try {
				wm.removeViewImmediate(v);
			} catch (RuntimeException ignored) {
				// not attached
			}
		}
	}

	private void updateOverlayPairLayout(OverlayWindows pair, int buttonX, int buttonY) {
		if (pair.visualParams == null || pair.touchParams == null) {
			return;
		}
		int padLeft = pair.visual.hintPadLeftPx();
		int padTop = pair.visual.hintPadTopPx();
		pair.visualParams.x = buttonX - padLeft;
		pair.visualParams.y = buttonY - padTop;
		pair.touchParams.x = buttonX;
		pair.touchParams.y = buttonY;
		WindowManager wm = windowManager();
		if (wm == null) {
			return;
		}
		try {
			wm.updateViewLayout(pair.visual, pair.visualParams);
			wm.updateViewLayout(pair.touch, pair.touchParams);
		} catch (IllegalArgumentException e) {
			// View already detached; the drop will be discarded.
		}
	}

	private void removeOverlayPair(FloatingButtonView v) {
		OverlayWindows pair = overlays.remove(v);
		WindowManager wm = windowManager();
		if (wm == null || pair == null) {
			return;
		}
		try {
			wm.removeViewImmediate(pair.touch);
		} catch (IllegalArgumentException e) {
			// Already detached.
		}
		try {
			wm.removeViewImmediate(pair.visual);
		} catch (IllegalArgumentException e) {
			// Already detached.
		}
	}

	/**
	 * Activity resumed: overlay windows may exist again.
	 *
	 * <p>Marks the layer resumed and asks for nothing. It used to request a push
	 * here, and that was the blink: at this point in {@code MainWindow.onResume}
	 * the button set has not been reverted yet, so Lua's {@code buttons} is still
	 * the cleared one — a single BACK button with no {@code floating} field — and
	 * the push it answered with was an empty list. Every floating button came
	 * down, and {@code restoreButtons} put them all back a moment later.
	 *
	 * <p>{@code buttonwindow.restoreButtons} now notifies on both its paths, so
	 * there is exactly one push per resume and it carries the real set. Setting
	 * {@code resumed} before that call still matters — {@code rebuild} and
	 * {@code rebuildOverlay} both refuse to place windows while it is false — so
	 * this must keep running before {@code restoreButtonsOnResume}.
	 */
	public void onResume() {
		resumed = true;
	}

	/**
	 * Activity paused: every overlay window comes down.
	 *
	 * <p>An overlay floats over other apps and the home screen, so leaving it up
	 * would follow the player out of the game.
	 */
	public void onPause() {
		resumed = false;
		imeHideRequest.clear();
		cancelPendingImeCovering();
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
		// Every rebuild resolves the orientation itself. onOrientationChanged is
		// not the only way a turn can reach here (a Lua push, an IME rebuild and
		// a resume all land in rebuild too), and a snapshot that still says
		// "portrait" after the device turned would place the button from the
		// wrong stored pair.
		boolean land = isLandscape();
		for (int i = 0; i < models.size(); i++) {
			FloatingButtonModel m = models.get(i);
			if (m.landscape != land) {
				models.set(i, m.forOrientation(land));
			}
		}
		if (models != lastModels) {
			lastModels.clear();
			lastModels.addAll(models);
		}
		maybeAskForOverlayPermission(models);
		ensureLayer();
		lastImeLiftPx = Math.max(0, host.refreshImeLiftPx());
		lastImeVisible = host.isImeVisible();
		cancelPendingImeCovering();
		if (overlayMode && (host.getMainWindow() == null || !resumed)) {
			// rebuildOverlay would refuse to put the windows back, and taking
			// them down anyway leaves no buttons at all until Lua happens to
			// push again -- the "they blink and then they are gone" case.
			return;
		}
		clearViews();
		boolean imeUp = isSoftKeyboardCoveringLayer();
		lastImeCovering = imeUp;
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
	 * Dual overlay windows per button, placed where the player left it.
	 *
	 * <p>Nothing here consults the keyboard height for position: the windows are
	 * above the IME, so a button stays put whether the keys are up or down.
	 * Keyboard mode only decides whether the windows exist at all.
	 */
	private void rebuildOverlay(List<FloatingButtonModel> models, boolean imeUp) {
		if (host.getMainWindow() == null || !resumed) {
			return;
		}
		lastImeCovering = imeUp;
		for (FloatingButtonModel m : models) {
			if (m.isKeyboardMode() && !imeUp) {
				// "Show only with keyboard": no window at all right now.
				continue;
			}
			attachOverlayFor(m);
		}
	}

	/**
	 * Add or remove only the windows the keyboard decides about, leaving every
	 * other button's window untouched — an attached window that is not moving is
	 * the one thing that cannot blink.
	 */
	private void updateKeyboardModeOverlays(boolean imeUp) {
		if (host.getMainWindow() == null || !resumed) {
			return;
		}
		for (int i = views.size() - 1; i >= 0; i--) {
			FloatingButtonView v = views.get(i);
			FloatingButtonModel m = v.getModel();
			if (m != null && m.isKeyboardMode() && !imeUp) {
				removeOverlayPair(v);
				views.remove(i);
			}
		}
		if (!imeUp) {
			return;
		}
		for (FloatingButtonModel m : lastModels) {
			if (m.isKeyboardMode() && findViewFor(m.index) == null) {
				attachOverlayFor(m);
			}
		}
	}

	private FloatingButtonView findViewFor(int index) {
		for (FloatingButtonView v : views) {
			FloatingButtonModel m = v.getModel();
			if (m != null && m.index == index) {
				return v;
			}
		}
		return null;
	}

	/** One button's pair of overlay windows, placed where the player left it. */
	private void attachOverlayFor(FloatingButtonModel m) {
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		float density = activity.getResources().getDisplayMetrics().density;
		int margin = Math.round(FloatingLayerGeometry.DEFAULT_MARGIN_DP * density);
		FloatingButtonView v = new FloatingButtonView(activity);
		v.bind(m, viewCallbacks);
		v.measure(
				View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
				View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
		// visualW/H (padded) size the hint window; buttonW/H are what
		// floatX/Y and clamping use, and also size the touchable window.
		int visualW = Math.max(1, v.getMeasuredWidth());
		int visualH = Math.max(1, v.getMeasuredHeight());
		int buttonW = Math.max(1, v.buttonWidthPx());
		int buttonH = Math.max(1, v.buttonHeightPx());
		// The grid is the position, in portrait. Measured 4 Aug on the phone: a
		// button at grid x=645.75, y=2044.875 has its centre at screen (644,
		// 2045) -- the grid's numbers are already screen coordinates here, which
		// is why no status-bar offset belongs in this conversion. So the copy is
		// placed from the grid centre and the two cannot drift apart; a drag
		// writes the grid back (see onFloatDragFinished), which is the only
		// other thing that may move it.
		//
		// Landscape uses the grid too once the player has laid buttons out
		// there; until then the grid holds one position, the portrait one, and
		// the landscape floating pair is what the player set on this side.
		boolean useGrid = m.hasGridOrigin
				&& (!isLandscape() || m.hasOwnLandscapeGrid());
		int x = useGrid
				? Math.round(m.gridX) - buttonW / 2
				: (m.floatX == FloatingLayerGeometry.UNPLACED
						? (m.hasGridOrigin
								? FloatingLayerGeometry.gridCenterToLeft(m.gridX, m.widthDp, density)
								: margin)
						: m.floatX);
		// No status-bar offset in the seed. Measured on the phone (dumpsys window,
		// 3 Aug): the dragged button sits at its stored y=2171 and looks right,
		// the never-dragged one is seeded at 2322 -- 151px lower, and the status
		// bar on this device is 152px. The offset belongs to the in-app layer,
		// whose coordinates start under the status bar; an overlay window's y is
		// the position on the screen, so adding it again drops the button by a
		// status bar on every rebuild, which is why only that button keeps
		// wandering off and only it comes back wrong.
		// A button nobody has placed yet goes to the bottom edge, not to where
		// its grid twin sits. Measured 4 Aug: the grid origin for these is
		// y=967, which drops an unplaced button into the middle of the game
		// text -- the "one of them ran off to the top" report.
		int y = useGrid
				? Math.round(m.gridY) - buttonH / 2
				: (m.floatY == FloatingLayerGeometry.UNPLACED
						? Math.max(0, displayHeight() - buttonH - margin)
						: m.floatY);
		x = FloatingLayerGeometry.clampX(x, buttonW, displayWidth());
		y = FloatingLayerGeometry.clampY(y, buttonH, displayHeight());
		attachOverlayPair(v, x, y, buttonW, buttonH, visualW, visualH);
		views.add(v);
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
				// w,h (padded) are the FrameLayout child's own size; buttonW/H
				// (visible button only) are what floatX/Y, clamping and the
				// FAB/IME keep-out rects are expressed in — same split as the
				// overlay path in rebuildOverlay.
				int buttonW = Math.max(v.buttonWidthPx(), 1);
				int buttonH = Math.max(v.buttonHeightPx(), 1);
				int padLeft = v.hintPadLeftPx();
				int padTop = v.hintPadTopPx();
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
					maxBottom = modeACeiling(resolvedX, resolvedX + buttonW, density);
				} else {
					maxBottom = computeMaxBottom(resolvedX, resolvedX + buttonW);
				}
				if (keyboardAboveIme) {
					// Mode A: X from drag/grid; Y just above the keys.
					int x = FloatingLayerGeometry.clampX(resolvedX, buttonW, layer.getWidth());
					int y = FloatingLayerGeometry.clampY(
							Math.max(0, maxBottom - buttonH),
							buttonH, maxBottom);
					applyLayoutParams(v, x - padLeft, y - padTop, w, h);
					return;
				}
				int resolvedY;
				if (m.floatY == FloatingLayerGeometry.UNPLACED && m.hasGridOrigin) {
					resolvedY = FloatingLayerGeometry.gridCenterToTop(
							m.gridY, m.heightDp, density, m.statusOffsetPx);
				} else if (m.floatY == FloatingLayerGeometry.UNPLACED) {
					resolvedY = Math.max(0, maxBottom - buttonH - marginPx);
				} else {
					resolvedY = m.floatY;
				}
				int x = FloatingLayerGeometry.clampX(resolvedX, buttonW, layer.getWidth());
				int y = FloatingLayerGeometry.clampY(resolvedY, buttonH, maxBottom);
				applyLayoutParams(v, x - padLeft, y - padTop, w, h);
			}
		});
	}

	/**
	 * {@code x,y} is the top-left of the padded view — already offset from the
	 * button's own top-left by the caller — and {@code w,h} its full padded size.
	 */
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
	 * Write a drag-drop position into {@link #lastModels} so the next overlay
	 * rebuild does not resurrect the pre-drag coordinates.
	 */
	private void rememberFloatPosition(int index, int x, int y) {
		rememberFloatPosition(index, x, y, Integer.MIN_VALUE, Integer.MIN_VALUE);
	}

	private void rememberFloatPosition(int index, int x, int y, int gridX, int gridY) {
		rememberFloatPosition(index, x, y, gridX, gridY, false);
	}

	private void rememberFloatPosition(int index, int x, int y, boolean onlyIfPlaced) {
		rememberFloatPosition(index, x, y, Integer.MIN_VALUE, Integer.MIN_VALUE, onlyIfPlaced);
	}

	/** The cached snapshot for a Lua button index, or null when it is gone. */
	private FloatingButtonModel findModel(int index) {
		for (int i = 0; i < lastModels.size(); i++) {
			if (lastModels.get(i).index == index) {
				return lastModels.get(i);
			}
		}
		return null;
	}

	/**
	 * @param onlyIfPlaced true when the position was read off a live window
	 *        rather than dropped by a finger. A window whose pair is still
	 *        {@link FloatingLayerGeometry#UNPLACED} is sitting on a seed, not on
	 *        anything the player chose, and writing that seed in would be a lie
	 *        about where they put it.
	 *
	 *        <p>Measured 4 Aug: this is how the two orientations kept sharing a
	 *        position. Turning the phone left the landscape pair unplaced, the
	 *        overlay window kept showing the portrait coordinates, and the next
	 *        IME event copied those coordinates into the landscape pair — so
	 *        landscape "followed" portrait one keyboard tap later, and a stored
	 *        y of 2171 then put the button below a 1080-tall landscape screen,
	 *        which is why it could not be found there at all.
	 */
	private void rememberFloatPosition(int index, int x, int y, int gridX, int gridY,
			boolean onlyIfPlaced) {
		for (int i = 0; i < lastModels.size(); i++) {
			FloatingButtonModel m = lastModels.get(i);
			if (m.index == index) {
				if (onlyIfPlaced
						&& m.forOrientation(isLandscape()).floatX == FloatingLayerGeometry.UNPLACED) {
					return;
				}
				// Resolve the orientation now instead of trusting the flag the
				// snapshot was built with. A cached model can predate the turn
				// (nothing forces a fresh Lua push on a rotation), and a stale
				// flag meant a drag in landscape wrote the portrait pair — the
				// exact symptom of the two layouts still being shared.
				FloatingButtonModel next = m.forOrientation(isLandscape())
						.withFloatPosition(x, y);
				if (gridX != Integer.MIN_VALUE && gridY != Integer.MIN_VALUE) {
					next = next.withGridPosition(gridX, gridY);
				}
				lastModels.set(i, next);
				return;
			}
		}
	}

	/**
	 * Overlay windows can move (drag) without a fresh Lua push. Before an IME
	 * rebuild, copy their live touch-window position into the cache that rebuild
	 * reads. The touch window is already in floatX/Y (visible-button) space.
	 */
	private void syncLastModelsFromLiveOverlayPositions() {
		for (FloatingButtonView v : views) {
			OverlayWindows pair = overlays.get(v);
			FloatingButtonModel m = v.getModel();
			if (pair == null || pair.touchParams == null || m == null) {
				continue;
			}
			rememberFloatPosition(m.index, pair.touchParams.x, pair.touchParams.y, true);
		}
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
	 * <p>One authority: the IME inset the chrome listener measured. Hysteresis
	 * ({@link SoftKeyboardCoverage}) so a dip during the slide does not toggle.
	 * A hide request beats hysteresis so Mode A does not wait out the animation.
	 */
	private boolean isSoftKeyboardCoveringLayer() {
		return computeImeCovering();
	}

	private boolean computeImeCovering() {
		if (imeHideRequest.isRequested()) {
			return false;
		}
		return SoftKeyboardCoverage.isCovering(lastImeLiftPx, density(), lastImeCovering);
	}

	private float density() {
		if (layer != null) {
			return layer.getResources().getDisplayMetrics().density;
		}
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return 1f;
		}
		return activity.getResources().getDisplayMetrics().density;
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
		cancelPendingImeCovering();
		for (FloatingButtonView v : views) {
			if (overlays.containsKey(v)) {
				removeOverlayPair(v);
			} else if (layer != null) {
				layer.removeView(v);
			}
		}
		views.clear();
		overlays.clear();
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

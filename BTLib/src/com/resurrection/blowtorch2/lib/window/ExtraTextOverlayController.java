package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.WindowToken;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * One overlay per {@link ExtraTextSlot} on {@code window_container}, under
 * {@code gameplay_chrome_overlay} (same z-order idea as mapper).
 * <p>
 * Host bridges MainWindow ↔ Connection.getExtraTextSlots() /
 * ensureExtraTextSlots when those APIs are available.
 */
public class ExtraTextOverlayController {

	private static final String TAG = "ExtraTextOverlay";
	private static final int LEGACY_INPUT_BAR_ID = ChromeController.LEGACY_INPUT_BAR_ID;
	/** Min drawer height (dp): keep grab strip usable; cannot shrink away. */
	private static final int MIN_DRAWER_DP = 50;
	private static final int MIN_FLOAT_DP = 160;
	private static final float MAX_DRAWER_SCREEN_FRACTION = 0.70f;
	private static final long PERSIST_DEBOUNCE_MS = 450L;

	/**
	 * Bridge to MainWindow / Connection.
	 * TODO: thin-wrap Connection.getExtraTextSlots() + ensureExtraTextSlots when
	 * exposed on {@code IConnectionBinder}.
	 */
	public interface Host {
		MainWindow getMainWindow();

		/** Current slots (never null; empty → controller no-ops). */
		List<ExtraTextSlot> getExtraTextSlots();

		WindowToken findWindowToken(String name);

		void registerWindowCallback(WindowToken token, Window window);

		void unregisterWindowCallback(WindowToken token, Window window);

		String getDataDir();

		Handler getUiHandler();

		/** Persist slot geometry/visibility (debounced by controller). */
		void persistExtraTextSlots(List<ExtraTextSlot> slots);
	}

	private static final class OverlayEntry {
		ExtraTextSlot slot;
		View overlayRoot;
		LinearLayout titleBar;
		TextView titleView;
		TextView dragHandle;
		TextView collapseBtn;
		android.widget.ImageButton closeBtn;
		FrameLayout contentHost;
		View edgeTop;
		View edgeBottom;
		View accentLine;
		View resizeHandle;
		Window window;
		/** A re-clamp is already waiting on the ⋮ strip being measured. */
		boolean chromeReclampScheduled;
	}

	private final Host host;
	private final Map<String, OverlayEntry> entries = new HashMap<String, OverlayEntry>();
	private final Handler persistHandler = new Handler(Looper.getMainLooper());
	private final Runnable persistRunnable = new Runnable() {
		@Override
		public void run() {
			flushPersist();
		}
	};

	public ExtraTextOverlayController(Host host) {
		this.host = host;
	}

	/** Create / update / remove overlays to match Host slots. */
	public void sync() {
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		RelativeLayout container = (RelativeLayout) activity.findViewById(R.id.window_container);
		if (container == null) {
			return;
		}

		List<ExtraTextSlot> slots = host.getExtraTextSlots();
		if (slots == null) {
			slots = new ArrayList<ExtraTextSlot>();
		}

		Set<String> keep = new HashSet<String>();
		for (int i = 0; i < slots.size(); i++) {
			ExtraTextSlot slot = slots.get(i);
			if (slot == null || slot.getName() == null || slot.getName().length() == 0) {
				continue;
			}
			keep.add(slot.getName());
			OverlayEntry entry = entries.get(slot.getName());
			// New overlay XML (FrameLayout + weighted body; edge overlaid, not in body).
			if (entry != null && entry.overlayRoot != null) {
				View body = entry.overlayRoot.findViewById(R.id.extra_text_body);
				View edge = entry.overlayRoot.findViewById(R.id.extra_text_edge_bottom);
				boolean legacy = body == null
						|| (edge != null && body != null && edge.getParent() == body);
				if (legacy) {
					destroyEntry(slot.getName());
					entry = null;
				}
			}
			if (entry == null) {
				entry = inflateOverlay(activity, container, slot);
				if (entry == null) {
					continue;
				}
				entries.put(slot.getName(), entry);
			} else {
				entry.slot = slot.copy();
			}
			bindContent(entry);
			applyChromeForMode(entry);
			applyLayout(entry);
			applyVisibility(entry);
			applyScrollSpeed(entry);
			bringUnderChrome(entry);
		}

		ArrayList<String> remove = new ArrayList<String>();
		for (String name : entries.keySet()) {
			if (!keep.contains(name)) {
				remove.add(name);
			}
		}
		for (int i = 0; i < remove.size(); i++) {
			destroyEntry(remove.get(i));
		}

		ChromeController chrome = activity.getChromeController();
		if (chrome != null) {
			chrome.bringViewUnderChrome(null);
		} else {
			View chromeView = activity.findViewById(R.id.gameplay_chrome_overlay);
			if (chromeView != null) {
				chromeView.bringToFront();
			}
		}
	}

	private static void ensureViewId(View v) {
		if (v != null && v.getId() == View.NO_ID) {
			v.setId(View.generateViewId());
		}
	}

	/** True if {@code windowName} is an extra-text slot (skip {@code initWindow}). */
	public boolean managesWindowName(String windowName) {
		if (windowName == null) {
			return false;
		}
		if (entries.containsKey(windowName)) {
			return true;
		}
		List<ExtraTextSlot> slots = host.getExtraTextSlots();
		if (slots == null) {
			return false;
		}
		for (int i = 0; i < slots.size(); i++) {
			ExtraTextSlot s = slots.get(i);
			if (s != null && windowName.equals(s.getName())) {
				return true;
			}
		}
		return false;
	}

	public void detach() {
		persistHandler.removeCallbacks(persistRunnable);
		ArrayList<String> names = new ArrayList<String>(entries.keySet());
		for (int i = 0; i < names.size(); i++) {
			destroyEntry(names.get(i));
		}
		entries.clear();
	}

	private OverlayEntry inflateOverlay(MainWindow activity, RelativeLayout container,
			ExtraTextSlot slot) {
		LayoutInflater inflater = LayoutInflater.from(activity);
		View root = inflater.inflate(R.layout.extra_text_overlay, container, false);
		OverlayEntry e = new OverlayEntry();
		e.slot = slot.copy();
		e.overlayRoot = root;
		e.titleBar = (LinearLayout) root.findViewById(R.id.extra_text_title_bar);
		e.titleView = (TextView) root.findViewById(R.id.extra_text_title);
		e.dragHandle = (TextView) root.findViewById(R.id.extra_text_drag_handle);
		e.collapseBtn = (TextView) root.findViewById(R.id.extra_text_collapse);
		e.closeBtn = (android.widget.ImageButton) root.findViewById(R.id.extra_text_close);
		e.contentHost = (FrameLayout) root.findViewById(R.id.extra_text_content);
		e.edgeTop = root.findViewById(R.id.extra_text_edge_top);
		e.edgeBottom = root.findViewById(R.id.extra_text_edge_bottom);
		e.accentLine = root.findViewById(R.id.extra_text_accent);
		e.resizeHandle = root.findViewById(R.id.extra_text_resize_handle);
		e.overlayRoot.setTag("extra_text_overlay:" + slot.getName());
		ensureViewId(e.overlayRoot);

		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
				RelativeLayout.LayoutParams.MATCH_PARENT,
				RelativeLayout.LayoutParams.WRAP_CONTENT);
		container.addView(root, lp);
		wireInteractions(e);
		return e;
	}

	private void showOpacityPicker(final OverlayEntry e) {
		if (e == null || e.slot == null) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		final int[] choices = new int[] { 40, 50, 60, 70, 80, 85, 90, 100 };
		CharSequence[] labels = new CharSequence[choices.length];
		int cur = e.slot.getOpacity();
		int selected = 5;
		for (int i = 0; i < choices.length; i++) {
			labels[i] = choices[i] + "%";
			if (choices[i] == cur) {
				selected = i;
			}
		}
		new android.app.AlertDialog.Builder(activity)
				.setTitle("Opacity (now " + cur + "%)")
				.setSingleChoiceItems(labels, selected,
						new android.content.DialogInterface.OnClickListener() {
							@Override
							public void onClick(android.content.DialogInterface dialog, int which) {
								e.slot.setOpacity(choices[which]);
								applyOpacity(e);
								schedulePersist();
								dialog.dismiss();
							}
						})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void bindContent(OverlayEntry e) {
		if (e == null || e.contentHost == null || e.slot == null || e.window != null) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}

		WindowToken token = host.findWindowToken(e.slot.getName());
		if (token == null) {
			Log.w(TAG, "No WindowToken for slot '" + e.slot.getName()
					+ "' — waiting for Connection.ensureExtraTextSlots()");
			return;
		}

		RelativeLayout container = (RelativeLayout) activity.findViewById(R.id.window_container);
		if (container != null) {
			View stray = container.findViewWithTag(token.getName());
			if (stray instanceof Window && stray.getParent() == container) {
				container.removeView(stray);
			}
		}

		Window win = new Window(host.getDataDir(), activity, token.getName(),
				token.getPluginName(), host.getUiHandler(), token.getSettings(), activity);
		win.setTag(token.getName());
		win.setId(token.getId());
		win.setLayoutParams(new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT));
		// Overlay windows must paint immediately (bufferText=true only queues to hold buffer).
		token.setBufferText(false);
		win.setBufferText(false);
		win.setWordWrap(true);
		if (token.getSettings() != null) {
			token.getSettings().setOption("word_wrap", "true");
		}
		if (token.getBuffer() != null) {
			win.setBuffer(token.getBuffer());
		}
		// Re-apply after buffer swap so linkify from Window settings sticks.
		boolean linksOn = true;
		boolean bare = true;
		String extras = "";
		if (token.getSettings() != null) {
			try {
				Object o = token.getSettings().findOptionByKey("hyperlinks_enabled");
				if (o instanceof BooleanOption && ((BooleanOption) o).getValue() instanceof Boolean) {
					linksOn = ((Boolean) ((BooleanOption) o).getValue()).booleanValue();
				}
				Object bareO = token.getSettings().findOptionByKey("hyperlink_bare_domains");
				if (bareO instanceof BooleanOption && ((BooleanOption) bareO).getValue() instanceof Boolean) {
					bare = ((Boolean) ((BooleanOption) bareO).getValue()).booleanValue();
				}
				Object extrasO = token.getSettings().findOptionByKey("hyperlink_extra_tlds");
				if (extrasO instanceof StringOption && ((StringOption) extrasO).getValue() instanceof String) {
					extras = (String) ((StringOption) extrasO).getValue();
				}
			} catch (Exception ignored) {
			}
		}
		win.setLinksEnabled(linksOn);
		if (win.getBuffer() != null) {
			win.getBuffer().setUrlLinkSettings(bare, extras);
		}
		// Same two-finger copy widget as the main game window (defaults true;
		// keep it on explicitly — overlay parents used to clip the disc away).
		win.setTextSelectionEnabled(true);
		if (e.contentHost != null) {
			e.contentHost.setClipChildren(false);
			e.contentHost.setClipToPadding(false);
		}
		if (e.overlayRoot instanceof android.view.ViewGroup) {
			((android.view.ViewGroup) e.overlayRoot).setClipChildren(false);
			((android.view.ViewGroup) e.overlayRoot).setClipToPadding(false);
		}
		try {
			host.registerWindowCallback(token, win);
		} catch (Exception ex) {
			Log.e(TAG, "registerWindowCallback failed for " + token.getName(), ex);
		}
		e.contentHost.removeAllViews();
		e.contentHost.addView(win);
		e.window = win;
		if (activity.windowMap != null) {
			activity.windowMap.put(token.getName(), win);
		}
		win.setVisibility(View.VISIBLE);
		win.flushBuffer();
		win.invalidate();
		win.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View v, int left, int top, int right, int bottom,
					int oldLeft, int oldTop, int oldRight, int oldBottom) {
				int nw = right - left;
				int nh = bottom - top;
				int ow = oldRight - oldLeft;
				int oh = oldBottom - oldTop;
				if (nw <= 1 || nh <= 1) {
					return;
				}
				if (nw == ow && nh == oh) {
					return;
				}
				v.invalidate();
			}
		});
		// First layout often runs before the overlay has a real size — repaint after.
		e.contentHost.post(new Runnable() {
			@Override
			public void run() {
				if (e.window == null) {
					return;
				}
				e.window.requestLayout();
				e.window.flushBuffer();
				e.window.invalidate();
			}
		});
	}

	private void applyChromeForMode(OverlayEntry e) {
		if (e == null || e.slot == null) {
			return;
		}
		ExtraTextSlot.Mode mode = e.slot.getMode();
		if (mode != ExtraTextSlot.Mode.FLOAT && mode != ExtraTextSlot.Mode.DRAWER_TOP) {
			mode = ExtraTextSlot.Mode.DRAWER_TOP;
			e.slot.setMode(mode);
		}
		boolean floatMode = mode == ExtraTextSlot.Mode.FLOAT;
		boolean drawer = !floatMode;
		// Both are per-slot options (Manage windows… → Edit).
		//
		// "Title bar" is a paint switch, not a layout one: the strip stays where
		// it is and keeps carrying the drag listeners, it just stops being drawn.
		// A floating pane you cannot move is worse than an ugly one, and the
		// maintainer asked for the pre-fix feel — an invisible bar you can still
		// grab — rather than a pane with no handle at all.
		boolean bar = floatMode && e.slot.isShowTitleBar();
		boolean close = floatMode && e.slot.isShowClose();
		boolean grip = floatMode && e.slot.isShowResizeHandle();
		final android.content.res.Resources res = e.overlayRoot != null
				? e.overlayRoot.getResources() : null;

		// Drawer: no title / no collapse — show/hide via .window / Options only.
		// Float: title + drag + muted accent under title.
		if (e.titleBar != null) {
			e.titleBar.setVisibility(floatMode ? View.VISIBLE : View.GONE);
			if (res != null) {
				e.titleBar.setBackgroundColor(bar
						? res.getColor(R.color.extra_text_title_bar)
						: android.graphics.Color.TRANSPARENT);
			}
		}
		if (e.titleView != null) {
			String t = e.slot.getTitle();
			if (t == null || t.length() == 0) {
				t = e.slot.getName();
			}
			e.titleView.setText(t);
			// VISIBLE even when unpainted: an INVISIBLE view takes no touches, and
			// this one is half the drag strip.
			e.titleView.setVisibility(floatMode ? View.VISIBLE : View.GONE);
			if (res != null) {
				e.titleView.setTextColor(bar
						? res.getColor(R.color.extra_text_title_text)
						: android.graphics.Color.TRANSPARENT);
			}
		}
		if (e.dragHandle != null) {
			e.dragHandle.setVisibility(floatMode ? View.VISIBLE : View.GONE);
			if (res != null) {
				e.dragHandle.setTextColor(bar
						? res.getColor(R.color.extra_text_grip)
						: android.graphics.Color.TRANSPARENT);
			}
		}
		if (e.collapseBtn != null) {
			e.collapseBtn.setVisibility(View.GONE);
		}
		// A floating window had no way to close itself: you had to know .window hide,
		// or find the slot in Options. Drawers are shown and hidden by their own
		// chrome, so the control only appears on the floating ones.
		if (e.closeBtn != null) {
			e.closeBtn.setVisibility(close ? View.VISIBLE : View.GONE);
			final OverlayEntry entry = e;
			e.closeBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (entry.slot == null) {
						return;
					}
					entry.slot.setVisible(false);
					applyVisibility(entry);
					schedulePersist();
				}
			});
		}
		if (e.accentLine != null) {
			e.accentLine.setVisibility(bar ? View.VISIBLE : View.GONE);
		}
		if (e.resizeHandle != null) {
			// Paint switch, like the title bar: the 40dp corner keeps resizing
			// whatever it looks like. GONE would take the only way to resize a
			// floating pane with it.
			e.resizeHandle.setVisibility(floatMode ? View.VISIBLE : View.GONE);
			if (res != null && e.resizeHandle instanceof TextView) {
				((TextView) e.resizeHandle).setTextColor(grip
						? res.getColor(R.color.extra_text_resize_grip)
						: android.graphics.Color.TRANSPARENT);
			}
			if (res != null) {
				e.resizeHandle.setBackgroundColor(grip
						? res.getColor(R.color.extra_text_resize_plate)
						: android.graphics.Color.TRANSPARENT);
			}
			if (floatMode) {
				e.resizeHandle.bringToFront();
			}
		}
		if (e.edgeTop != null) {
			e.edgeTop.setVisibility(View.GONE);
		}
		if (e.edgeBottom != null) {
			// Drawer always shows the bottom grab strip (min height keeps it usable).
			e.edgeBottom.setVisibility(drawer ? View.VISIBLE : View.GONE);
			if (drawer) {
				e.edgeBottom.bringToFront();
			}
		}
		if (e.contentHost != null) {
			e.contentHost.setVisibility(View.VISIBLE);
		}
		// Collapse flag is obsolete for drawers — clear so geometry uses full height.
		if (drawer && e.slot.isCollapsed()) {
			e.slot.setCollapsed(false);
		}
	}

	/**
	 * Push the slot's scroll speed onto the live overlay.
	 *
	 * <p>Deliberately not routed through the overlay's SettingsGroup: extra-text
	 * WindowTokens are rebuilt by {@code Connection.ensureExtraTextSlots()} and
	 * never land in {@code settings.getWindows()}, so nothing there is saved.
	 * The slot JSON is the durable copy; this only mirrors it onto the view, and
	 * it runs on every {@link #sync()} so a change applies without reopening.
	 */
	private void applyScrollSpeed(OverlayEntry e) {
		if (e == null || e.window == null || e.slot == null) {
			return;
		}
		e.window.applyScrollSensitivityChoice(
				Integer.valueOf(e.slot.resolveScrollChoice(mainWindowScrollChoice())));
	}

	/** Re-apply every overlay's speed; used when the main window's choice moves. */
	public void refreshScrollSpeeds() {
		for (OverlayEntry e : entries.values()) {
			applyScrollSpeed(e);
		}
	}

	/**
	 * @return The main window's current {@code scroll_sensitivity} choice, or the
	 *         default when the main Window view is not up yet.
	 */
	private int mainWindowScrollChoice() {
		MainWindow activity = host.getMainWindow();
		if (activity == null || activity.windowMap == null) {
			return WindowToken.DEFAULT_SCROLL_SENSITIVITY;
		}
		Window main = activity.windowMap.get("mainDisplay");
		if (main == null) {
			return WindowToken.DEFAULT_SCROLL_SENSITIVITY;
		}
		return main.getScrollSensitivityChoice();
	}

	private void applyVisibility(OverlayEntry e) {
		if (e == null || e.overlayRoot == null || e.slot == null) {
			return;
		}
		e.overlayRoot.setVisibility(e.slot.isVisible() ? View.VISIBLE : View.GONE);
		applyOpacity(e);
	}

	private void applyOpacity(OverlayEntry e) {
		if (e == null || e.overlayRoot == null || e.slot == null) {
			return;
		}
		int pct = e.slot.getOpacity();
		if (pct < 40) {
			pct = 40;
		} else if (pct > 100) {
			pct = 100;
		}
		e.overlayRoot.setAlpha(pct / 100f);
	}

	private void applyLayout(OverlayEntry e) {
		if (e == null || e.overlayRoot == null || e.slot == null) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		float density = activity.getResources().getDisplayMetrics().density;
		int screenH = activity.getResources().getDisplayMetrics().heightPixels;
		View inputbar = findGameplayInputBar();
		ExtraTextSlot.Mode mode = e.slot.getMode();

		RelativeLayout.LayoutParams lp;
		if (mode == ExtraTextSlot.Mode.FLOAT) {
			int w = Math.max((int) (MIN_FLOAT_DP * density),
					(int) (e.slot.getFloatW() * density));
			int h = Math.max((int) (MIN_FLOAT_DP * density),
					(int) (e.slot.getFloatH() * density));
			int x = Math.max(0, (int) (e.slot.getFloatX() * density));
			int y = Math.max(0, (int) (e.slot.getFloatY() * density));
			y = clampFloatTop(y, h, inputbar, screenH, x, x + w);
			lp = new RelativeLayout.LayoutParams(w, h);
			lp.leftMargin = x;
			lp.topMargin = y;
			lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
			e.overlayRoot.setLayoutParams(lp);
			e.overlayRoot.requestLayout();
			if (e.resizeHandle != null) {
				e.resizeHandle.bringToFront();
			}
			reclampWhenChromeIsMeasured(e);
		} else {
			applyDrawerHeight(e);
		}
	}

	/**
	 * Drawer height only: reuse existing {@link RelativeLayout.LayoutParams}
	 * when present so a finger drag does not allocate a new params object per
	 * MOVE. Rules are set once if this is the first drawer layout.
	 */
	private void applyDrawerHeight(OverlayEntry e) {
		if (e == null || e.overlayRoot == null || e.slot == null) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		float density = activity.getResources().getDisplayMetrics().density;
		int screenH = activity.getResources().getDisplayMetrics().heightPixels;
		int maxH = (int) (screenH * MAX_DRAWER_SCREEN_FRACTION);
		int minH = (int) (MIN_DRAWER_DP * density);
		int heightPx = Math.max(minH,
				Math.min(maxH, (int) (e.slot.getHeightDp() * density)));
		ViewGroup.LayoutParams glp = e.overlayRoot.getLayoutParams();
		RelativeLayout.LayoutParams lp;
		if (glp instanceof RelativeLayout.LayoutParams) {
			lp = (RelativeLayout.LayoutParams) glp;
			if (lp.height == heightPx
					&& lp.width == RelativeLayout.LayoutParams.MATCH_PARENT
					&& lp.leftMargin == 0
					&& lp.topMargin == 0) {
				return;
			}
			lp.width = RelativeLayout.LayoutParams.MATCH_PARENT;
			lp.height = heightPx;
			// Float → drawer reuses this object; clear float X/Y or the strip
			// stays offset instead of spanning the top edge.
			lp.leftMargin = 0;
			lp.topMargin = 0;
			lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		} else {
			lp = new RelativeLayout.LayoutParams(
					RelativeLayout.LayoutParams.MATCH_PARENT, heightPx);
			lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		}
		e.overlayRoot.setLayoutParams(lp);
		e.overlayRoot.requestLayout();
	}

	/**
	 * A window restored before the ⋮ strip was measured got no keep-out; lay it
	 * out again once the strip exists. Once per entry — the second pass finds the
	 * strip measured and has nothing left to schedule.
	 */
	private void reclampWhenChromeIsMeasured(final OverlayEntry e) {
		if (e.chromeReclampScheduled) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		ChromeController chrome = activity != null ? activity.getChromeController() : null;
		if (chrome == null || chrome.fabStripHasSize()) {
			return;
		}
		// Set before scheduling: the applyLayout below re-enters this method.
		e.chromeReclampScheduled = true;
		chrome.whenFabStripMeasured(new Runnable() {
			@Override
			public void run() {
				applyLayout(e);
			}
		});
	}

	/**
	 * Above the input bar so the player can still type, and above the ⋮ strip
	 * when the window reaches that corner — otherwise this window's resize
	 * handle and ⋮ sit in the same 48dp box and chrome wins every touch.
	 * See {@link ChromeController#floatingOverlayBottomLimit}.
	 */
	private int clampFloatTop(int top, int height, View inputbar, int screenH,
			int left, int right) {
		int maxBottom = screenH;
		if (inputbar != null && inputbar.getHeight() > 0) {
			int[] loc = new int[2];
			inputbar.getLocationOnScreen(loc);
			maxBottom = loc[1];
		}
		MainWindow activity = host.getMainWindow();
		ChromeController chrome = activity != null ? activity.getChromeController() : null;
		if (chrome != null) {
			maxBottom = chrome.floatingOverlayBottomLimit(maxBottom, left, right);
		}
		int maxTop = Math.max(0, maxBottom - height);
		if (top > maxTop) {
			return maxTop;
		}
		return Math.max(0, top);
	}

	private void wireInteractions(final OverlayEntry e) {
		if (e.titleView != null) {
			e.titleView.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					showOpacityPicker(e);
					return true;
				}
			});
		}
		if (e.titleBar != null) {
			e.titleBar.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					showOpacityPicker(e);
					return true;
				}
			});
		}

		// Every gesture below measures from where the finger went down rather than
		// from the previous move event. Per-event deltas were converted to dp with
		// Math.round and the fraction thrown away each time, so at this screen's
		// density a slow drag delivering a pixel per event rounded to zero every
		// time and nothing moved at all until the finger was yanked hard enough to
		// carry two pixels in one event.
		View.OnTouchListener floatDrag = new View.OnTouchListener() {
			float downX;
			float downY;
			int startXDp;
			int startYDp;

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (e.slot == null || e.slot.getMode() != ExtraTextSlot.Mode.FLOAT) {
					return false;
				}
				MainWindow activity = host.getMainWindow();
				if (activity == null) {
					return false;
				}
				float density = activity.getResources().getDisplayMetrics().density;
				switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					downX = event.getRawX();
					downY = event.getRawY();
					startXDp = e.slot.getFloatX();
					startYDp = e.slot.getFloatY();
					return true;
				case MotionEvent.ACTION_MOVE: {
					int dxDp = Math.round((event.getRawX() - downX) / density);
					int dyDp = Math.round((event.getRawY() - downY) / density);
					e.slot.setFloatX(Math.max(0, startXDp + dxDp));
					e.slot.setFloatY(Math.max(0, startYDp + dyDp));
					applyLayout(e);
					bringUnderChrome(e);
					return true;
				}
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					schedulePersist();
					return true;
				default:
					return false;
				}
			}
		};
		if (e.dragHandle != null) {
			e.dragHandle.setOnTouchListener(floatDrag);
		}
		if (e.titleView != null) {
			e.titleView.setOnTouchListener(floatDrag);
		}

		if (e.resizeHandle != null) {
			e.resizeHandle.setOnTouchListener(new View.OnTouchListener() {
				float downX;
				float downY;
				int startW;
				int startH;

				@Override
				public boolean onTouch(View v, MotionEvent event) {
					if (e.slot == null || e.slot.getMode() != ExtraTextSlot.Mode.FLOAT) {
						return false;
					}
					MainWindow activity = host.getMainWindow();
					if (activity == null) {
						return false;
					}
					float density = activity.getResources().getDisplayMetrics().density;
					switch (event.getActionMasked()) {
					case MotionEvent.ACTION_DOWN:
						downX = event.getRawX();
						downY = event.getRawY();
						startW = e.slot.getFloatW();
						startH = e.slot.getFloatH();
						return true;
					case MotionEvent.ACTION_MOVE: {
						int dwDp = Math.round((event.getRawX() - downX) / density);
						int dhDp = Math.round((event.getRawY() - downY) / density);
						e.slot.setFloatW(Math.max(MIN_FLOAT_DP, startW + dwDp));
						e.slot.setFloatH(Math.max(MIN_FLOAT_DP, startH + dhDp));
						applyLayout(e);
						bringUnderChrome(e);
						return true;
					}
					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_CANCEL:
						schedulePersist();
						return true;
					default:
						return false;
					}
				}
			});
		}

		View.OnTouchListener drawerResize = new View.OnTouchListener() {
			float downY;
			int startHeightDp;

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (e.slot == null || e.slot.getMode() == ExtraTextSlot.Mode.FLOAT) {
					return false;
				}
				MainWindow activity = host.getMainWindow();
				if (activity == null) {
					return false;
				}
				float density = activity.getResources().getDisplayMetrics().density;
				int screenH = activity.getResources().getDisplayMetrics().heightPixels;
				int maxDp = Math.max(MIN_DRAWER_DP,
						(int) ((screenH * MAX_DRAWER_SCREEN_FRACTION) / density));
				switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					downY = event.getRawY();
					startHeightDp = e.slot.getHeightDp();
					return true;
				case MotionEvent.ACTION_MOVE: {
					// Drag down grows height; floor at MIN_DRAWER_DP. Measured from
					// the down point, so dragging back out of a clamp returns the
					// height the finger actually describes rather than crawling.
					int next = startHeightDp
							+ Math.round((event.getRawY() - downY) / density);
					if (next < MIN_DRAWER_DP) {
						next = MIN_DRAWER_DP;
					}
					if (next > maxDp) {
						next = maxDp;
					}
					if (next == e.slot.getHeightDp()) {
						return true;
					}
					e.slot.setHeightDp(next);
					// Height-only: skip bringUnderChrome — z-order is unchanged and
					// bringToFront on every MOVE was redrawing the whole container.
					applyDrawerHeight(e);
					return true;
				}
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					applyLayout(e);
					bringUnderChrome(e);
					schedulePersist();
					return true;
				default:
					return false;
				}
			}
		};
		if (e.edgeTop != null) {
			e.edgeTop.setOnTouchListener(drawerResize);
		}
		if (e.edgeBottom != null) {
			e.edgeBottom.setOnTouchListener(drawerResize);
		}
	}

	private void bringUnderChrome(OverlayEntry e) {
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		ChromeController chrome = activity.getChromeController();
		if (chrome != null) {
			chrome.bringViewUnderChrome(e != null ? e.overlayRoot : null);
		} else if (e != null && e.overlayRoot != null) {
			e.overlayRoot.bringToFront();
			View chromeView = activity.findViewById(R.id.gameplay_chrome_overlay);
			if (chromeView != null) {
				chromeView.bringToFront();
			}
		}
	}

	private View findGameplayInputBar() {
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return null;
		}
		ViewGroup container = (ViewGroup) activity.findViewById(R.id.window_container);
		if (container == null) {
			return null;
		}
		View inputbar = container.findViewById(LEGACY_INPUT_BAR_ID);
		if (inputbar == null) {
			inputbar = container.findViewById(R.id.inputbar);
		}
		return inputbar;
	}

	private void schedulePersist() {
		persistHandler.removeCallbacks(persistRunnable);
		persistHandler.postDelayed(persistRunnable, PERSIST_DEBOUNCE_MS);
	}

	private void flushPersist() {
		List<ExtraTextSlot> out = new ArrayList<ExtraTextSlot>();
		List<ExtraTextSlot> hostSlots = host.getExtraTextSlots();
		if (hostSlots != null) {
			for (int i = 0; i < hostSlots.size(); i++) {
				ExtraTextSlot base = hostSlots.get(i);
				if (base == null) {
					continue;
				}
				OverlayEntry e = entries.get(base.getName());
				if (e != null && e.slot != null) {
					out.add(e.slot.copy());
				} else {
					out.add(base.copy());
				}
			}
		}
		host.persistExtraTextSlots(out);
	}

	private void destroyEntry(String name) {
		OverlayEntry e = entries.remove(name);
		if (e == null) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		if (e.window != null) {
			WindowToken token = host.findWindowToken(name);
			try {
				// Always unregister — token may already be removed from Connection.
				host.unregisterWindowCallback(token, e.window);
			} catch (Exception ex) {
				Log.e(TAG, "unregisterWindowCallback failed for " + name, ex);
			}
			try {
				e.window.shutdown();
			} catch (Exception ignored) {
			}
			try {
				e.window.closeLua();
			} catch (Exception ignored) {
			}
			if (activity != null && activity.windowMap != null) {
				activity.windowMap.remove(name);
			}
			if (e.window.getParent() instanceof ViewGroup) {
				((ViewGroup) e.window.getParent()).removeView(e.window);
			}
			e.window = null;
		}
		if (e.overlayRoot != null && e.overlayRoot.getParent() instanceof ViewGroup) {
			((ViewGroup) e.overlayRoot.getParent()).removeView(e.overlayRoot);
		}
		e.overlayRoot = null;
	}
}

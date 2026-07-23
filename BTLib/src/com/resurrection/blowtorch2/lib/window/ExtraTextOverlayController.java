package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.WindowToken;

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
	private static final int MIN_DRAWER_DP = 80;
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

		/** Options → Drawers push game text? */
		boolean isPushMainTextEnabled();

		/**
		 * Shrink {@code mainDisplay} by drawer insets (px). Does not move
		 * {@code button_window}. Pass zeros to clear.
		 */
		void setMainTextDrawerInsets(int topPx, int bottomPx);
	}

	private static final class OverlayEntry {
		ExtraTextSlot slot;
		View overlayRoot;
		LinearLayout titleBar;
		TextView titleView;
		TextView dragHandle;
		TextView collapseBtn;
		FrameLayout contentHost;
		View edgeTop;
		View edgeBottom;
		View resizeHandle;
		Window window;
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

		updateMainTextInsets();

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

	/**
	 * When push-main is on, top/bottom drawers reserve space in mainDisplay
	 * (margins). Floating windows never contribute. Buttons stay full-bleed.
	 */
	private void updateMainTextInsets() {
		int topPx = 0;
		int bottomPx = 0;
		if (host.isPushMainTextEnabled()) {
			for (OverlayEntry e : entries.values()) {
				if (e == null || e.slot == null || e.overlayRoot == null) {
					continue;
				}
				if (!e.slot.isVisible()) {
					continue;
				}
				ExtraTextSlot.Mode mode = e.slot.getMode();
				if (mode == ExtraTextSlot.Mode.FLOAT) {
					continue;
				}
				int h = 0;
				ViewGroup.LayoutParams lp = e.overlayRoot.getLayoutParams();
				if (lp != null && lp.height > 0) {
					h = lp.height;
				}
				if (h <= 0) {
					h = e.overlayRoot.getHeight();
				}
				if (h <= 0) {
					continue;
				}
				if (mode == ExtraTextSlot.Mode.DRAWER_TOP) {
					if (h > topPx) {
						topPx = h;
					}
				} else if (mode == ExtraTextSlot.Mode.DRAWER_BOTTOM) {
					if (h > bottomPx) {
						bottomPx = h;
					}
				}
			}
		}
		host.setMainTextDrawerInsets(topPx, bottomPx);
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
		host.setMainTextDrawerInsets(0, 0);
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
		e.contentHost = (FrameLayout) root.findViewById(R.id.extra_text_content);
		e.edgeTop = root.findViewById(R.id.extra_text_edge_top);
		e.edgeBottom = root.findViewById(R.id.extra_text_edge_bottom);
		e.resizeHandle = root.findViewById(R.id.extra_text_resize_handle);
		e.overlayRoot.setTag("extra_text_overlay:" + slot.getName());

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
		if (token.getBuffer() != null) {
			win.setBuffer(token.getBuffer());
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
	}

	private void applyChromeForMode(OverlayEntry e) {
		if (e == null || e.slot == null) {
			return;
		}
		ExtraTextSlot.Mode mode = e.slot.getMode();
		boolean floatMode = mode == ExtraTextSlot.Mode.FLOAT;
		boolean drawer = !floatMode;

		if (e.titleView != null) {
			String t = e.slot.getTitle();
			if (t == null || t.length() == 0) {
				t = e.slot.getName();
			}
			e.titleView.setText(t);
		}
		if (e.dragHandle != null) {
			e.dragHandle.setVisibility(floatMode ? View.VISIBLE : View.GONE);
		}
		if (e.collapseBtn != null) {
			e.collapseBtn.setVisibility(drawer ? View.VISIBLE : View.GONE);
			updateCollapseChevron(e);
		}
		if (e.resizeHandle != null) {
			e.resizeHandle.setVisibility(
					floatMode && !e.slot.isCollapsed() ? View.VISIBLE : View.GONE);
		}
		if (e.edgeTop != null) {
			e.edgeTop.setVisibility(
					mode == ExtraTextSlot.Mode.DRAWER_BOTTOM && !e.slot.isCollapsed()
							? View.VISIBLE : View.GONE);
		}
		if (e.edgeBottom != null) {
			e.edgeBottom.setVisibility(
					mode == ExtraTextSlot.Mode.DRAWER_TOP && !e.slot.isCollapsed()
							? View.VISIBLE : View.GONE);
		}
		if (e.contentHost != null) {
			e.contentHost.setVisibility(e.slot.isCollapsed() ? View.GONE : View.VISIBLE);
		}
	}

	private void updateCollapseChevron(OverlayEntry e) {
		if (e.collapseBtn == null || e.slot == null) {
			return;
		}
		boolean top = e.slot.getMode() == ExtraTextSlot.Mode.DRAWER_TOP;
		if (e.slot.isCollapsed()) {
			e.collapseBtn.setText(top ? "▾" : "▴");
		} else {
			e.collapseBtn.setText(top ? "▴" : "▾");
		}
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
			y = clampFloatTop(y, h, inputbar, screenH);
			lp = new RelativeLayout.LayoutParams(w, h);
			lp.leftMargin = x;
			lp.topMargin = y;
			lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		} else {
			int heightPx;
			if (e.slot.isCollapsed()) {
				heightPx = measureTitleBarHeight(e, density);
			} else {
				int maxH = (int) (screenH * MAX_DRAWER_SCREEN_FRACTION);
				int minH = (int) (MIN_DRAWER_DP * density);
				heightPx = Math.max(minH,
						Math.min(maxH, (int) (e.slot.getHeightDp() * density)));
			}
			lp = new RelativeLayout.LayoutParams(
					RelativeLayout.LayoutParams.MATCH_PARENT, heightPx);
			lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			if (mode == ExtraTextSlot.Mode.DRAWER_TOP) {
				lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
			} else if (inputbar != null && inputbar.getId() != View.NO_ID) {
				lp.addRule(RelativeLayout.ABOVE, inputbar.getId());
			} else {
				lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
			}
		}
		e.overlayRoot.setLayoutParams(lp);
		e.overlayRoot.requestLayout();
		if (e.resizeHandle != null && mode == ExtraTextSlot.Mode.FLOAT) {
			e.resizeHandle.bringToFront();
		}
		updateMainTextInsets();
	}

	private int measureTitleBarHeight(OverlayEntry e, float density) {
		if (e.titleBar != null) {
			int h = e.titleBar.getHeight();
			if (h <= 0) {
				h = e.titleBar.getMeasuredHeight();
			}
			if (h > 0) {
				return h + (int) (4 * density);
			}
		}
		return (int) (40 * density);
	}

	private int clampFloatTop(int top, int height, View inputbar, int screenH) {
		int maxBottom = screenH;
		if (inputbar != null && inputbar.getHeight() > 0) {
			int[] loc = new int[2];
			inputbar.getLocationOnScreen(loc);
			maxBottom = loc[1];
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
		if (e.collapseBtn != null) {
			e.collapseBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (e.slot == null || e.slot.getMode() == ExtraTextSlot.Mode.FLOAT) {
						return;
					}
					e.slot.setCollapsed(!e.slot.isCollapsed());
					applyChromeForMode(e);
					applyLayout(e);
					schedulePersist();
				}
			});
		}

		View.OnTouchListener floatDrag = new View.OnTouchListener() {
			float lastX;
			float lastY;

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
					lastX = event.getRawX();
					lastY = event.getRawY();
					return true;
				case MotionEvent.ACTION_MOVE: {
					int dx = (int) (event.getRawX() - lastX);
					int dy = (int) (event.getRawY() - lastY);
					lastX = event.getRawX();
					lastY = event.getRawY();
					e.slot.setFloatX(Math.max(0,
							e.slot.getFloatX() + Math.round(dx / density)));
					e.slot.setFloatY(Math.max(0,
							e.slot.getFloatY() + Math.round(dy / density)));
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
				float lastX;
				float lastY;

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
						lastX = event.getRawX();
						lastY = event.getRawY();
						return true;
					case MotionEvent.ACTION_MOVE: {
						int dx = (int) (event.getRawX() - lastX);
						int dy = (int) (event.getRawY() - lastY);
						lastX = event.getRawX();
						lastY = event.getRawY();
						e.slot.setFloatW(Math.max(MIN_FLOAT_DP,
								e.slot.getFloatW() + Math.round(dx / density)));
						e.slot.setFloatH(Math.max(MIN_FLOAT_DP,
								e.slot.getFloatH() + Math.round(dy / density)));
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
			float lastY;

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (e.slot == null || e.slot.isCollapsed()
						|| e.slot.getMode() == ExtraTextSlot.Mode.FLOAT) {
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
				boolean topDrawer = e.slot.getMode() == ExtraTextSlot.Mode.DRAWER_TOP;
				switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					lastY = event.getRawY();
					return true;
				case MotionEvent.ACTION_MOVE: {
					float dy = event.getRawY() - lastY;
					lastY = event.getRawY();
					int deltaDp = Math.round(dy / density);
					int next = e.slot.getHeightDp() + (topDrawer ? deltaDp : -deltaDp);
					if (next < MIN_DRAWER_DP) {
						next = MIN_DRAWER_DP;
					}
					if (next > maxDp) {
						next = maxDp;
					}
					e.slot.setHeightDp(next);
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
				if (token != null) {
					host.unregisterWindowCallback(token, e.window);
				}
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

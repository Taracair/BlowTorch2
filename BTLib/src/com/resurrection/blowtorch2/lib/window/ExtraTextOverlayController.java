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
		FrameLayout contentHost;
		View edgeTop;
		View edgeBottom;
		View accentLine;
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
		if (token.getSettings() != null) {
			try {
				Object o = token.getSettings().findOptionByKey("hyperlinks_enabled");
				if (o instanceof BooleanOption && ((BooleanOption) o).getValue() instanceof Boolean) {
					linksOn = ((Boolean) ((BooleanOption) o).getValue()).booleanValue();
				}
			} catch (Exception ignored) {
			}
		}
		win.setLinksEnabled(linksOn);
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

		// Drawer: no title / no collapse — show/hide via .window / Options only.
		// Float: title + drag + muted accent under title.
		if (e.titleBar != null) {
			e.titleBar.setVisibility(floatMode ? View.VISIBLE : View.GONE);
		}
		if (e.titleView != null) {
			String t = e.slot.getTitle();
			if (t == null || t.length() == 0) {
				t = e.slot.getName();
			}
			e.titleView.setText(t);
			e.titleView.setVisibility(floatMode ? View.VISIBLE : View.GONE);
		}
		if (e.dragHandle != null) {
			e.dragHandle.setVisibility(floatMode ? View.VISIBLE : View.GONE);
		}
		if (e.collapseBtn != null) {
			e.collapseBtn.setVisibility(View.GONE);
		}
		if (e.accentLine != null) {
			e.accentLine.setVisibility(floatMode ? View.VISIBLE : View.GONE);
		}
		if (e.resizeHandle != null) {
			e.resizeHandle.setVisibility(floatMode ? View.VISIBLE : View.GONE);
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
			int maxH = (int) (screenH * MAX_DRAWER_SCREEN_FRACTION);
			int minH = (int) (MIN_DRAWER_DP * density);
			int heightPx = Math.max(minH,
					Math.min(maxH, (int) (e.slot.getHeightDp() * density)));
			lp = new RelativeLayout.LayoutParams(
					RelativeLayout.LayoutParams.MATCH_PARENT, heightPx);
			lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		}
		e.overlayRoot.setLayoutParams(lp);
		e.overlayRoot.requestLayout();
		if (e.resizeHandle != null && mode == ExtraTextSlot.Mode.FLOAT) {
			e.resizeHandle.bringToFront();
		}
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
					lastY = event.getRawY();
					return true;
				case MotionEvent.ACTION_MOVE: {
					float dy = event.getRawY() - lastY;
					lastY = event.getRawY();
					// Drag down grows height; floor at MIN_DRAWER_DP.
					int next = e.slot.getHeightDp() + Math.round(dy / density);
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

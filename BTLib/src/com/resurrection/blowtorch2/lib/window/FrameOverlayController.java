package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.FrameEvent;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * {@code mudstd.frame} image windows. Server {@code type} is a starting point;
 * the player's shape sticks. Geometry is one default, not per frame id (ids
 * collide across worlds). Resize is debounced at {@link #RESIZE_DEBOUNCE_MS}
 * so a drag is not dozens of {@code frame.resized} events.
 */
public class FrameOverlayController implements FrameImageStore.Listener {

	private static final int MIN_FLOAT_DP = 120;
	private static final int MIN_DRAWER_DP = 80;
	private static final float MAX_DRAWER_SCREEN_FRACTION = 0.70f;
	private static final int LEGACY_INPUT_BAR_ID = ChromeController.LEGACY_INPUT_BAR_ID;

	/** Where the shape a player settled on is kept between sessions. */
	private static final String PREFS = "blowtorch_frames";
	private static final String KEY_MODE_FLOAT = "frame_mode_float";
	private static final String KEY_X = "frame_x_dp";
	private static final String KEY_Y = "frame_y_dp";
	private static final String KEY_W = "frame_w_dp";
	private static final String KEY_H = "frame_h_dp";
	private static final String KEY_DRAWER_H = "frame_drawer_h_dp";
	private static final String KEY_OPACITY = "frame_opacity";

	private static final int DEFAULT_X_DP = 16;
	private static final int DEFAULT_Y_DP = 48;
	private static final int DEFAULT_W_DP = 260;
	private static final int DEFAULT_H_DP = 220;
	private static final int DEFAULT_DRAWER_H_DP = 200;
	private static final int DEFAULT_OPACITY = 100;

	/** Long enough that a drag settles first; short enough not to look stuck. */
	private static final long PERSIST_DEBOUNCE_MS = 450L;

	/**
	 * How long a size has to hold still before the server hears about it.
	 *
	 * <p>A resize drag lays the frame out on every touch move, and each pass was
	 * a {@code frame.resized} on the wire — dozens of them for one gesture, all
	 * describing sizes the player passed through on the way to the one they
	 * wanted. The server only cares where the drag ended.
	 */
	private static final long RESIZE_DEBOUNCE_MS = 400L;

	/** What the controller needs from MainWindow and the service. */
	public interface Host {
		MainWindow getMainWindow();

		/** Tell the server the player closed this frame (frame.closed reason user). */
		void closeFrameOnServer(String id);

		/** Tell the server how big the frame turned out (frame.resized). */
		void reportFrameSize(String id, int widthPx, int heightPx);
	}

	/** One frame on screen. */
	private static final class Entry {
		String id;
		String label = "";
		View root;
		LinearLayout titleBar;
		TextView titleView;
		TextView dragHandle;
		android.widget.ImageButton closeBtn;
		android.widget.ImageButton drawerCloseBtn;
		FrameLayout contentHost;
		ImageView imageView;
		TextView statusView;
		View accentLine;
		View edgeBottom;
		View resizeHandle;
		/** A re-clamp is already waiting on the ⋮ strip being measured. */
		boolean chromeReclampScheduled;
		/** Last size reported to the server, so an unchanged layout says nothing. */
		int reportedW;
		int reportedH;
		/** The size waiting to be reported once the frame stops moving. */
		int pendingW;
		int pendingH;
		/** Sends {@link #pendingW}×{@link #pendingH}; see {@link #reportSize}. */
		Runnable resizeSend;
	}

	/** The shape every frame currently takes. One set of numbers, not one per id. */
	private static final class Shape {
		boolean floating = true;
		int x = DEFAULT_X_DP;
		int y = DEFAULT_Y_DP;
		int w = DEFAULT_W_DP;
		int h = DEFAULT_H_DP;
		int drawerH = DEFAULT_DRAWER_H_DP;
		int opacity = DEFAULT_OPACITY;
	}

	private final Host host;
	private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();
	/** The spec each open frame was last told to show, for a rebuild. */
	private final Map<String, String> specs = new HashMap<String, String>();
	private final Shape shape = new Shape();
	private final Handler persistHandler = new Handler(Looper.getMainLooper());
	private final Handler resizeHandler = new Handler(Looper.getMainLooper());
	private final Runnable persistRunnable = new Runnable() {
		@Override
		public void run() {
			saveShape();
		}
	};
	private boolean shapeLoaded;

	public FrameOverlayController(final Host host) {
		this.host = host;
		FrameImageStore.get().addListener(this);
	}

	/** Apply a batch of events from the service. */
	public void apply(final List<FrameEvent> events) {
		if (events == null || events.isEmpty()) {
			return;
		}
		loadShapeOnce();
		for (int i = 0; i < events.size(); i++) {
			FrameEvent e = events.get(i);
			if (e == null) {
				continue;
			}
			String op = e.getOp();
			if (FrameEvent.OP_OPEN.equals(op)) {
				openFrame(e);
			} else if (FrameEvent.OP_IMAGE.equals(op)) {
				imageFor(e);
			} else if (FrameEvent.OP_INLINE.equals(op)) {
				// No window for this one — the picture belongs to a marker sitting
				// in the game text. All that is wanted here is the load; the
				// Window that draws it repaints itself when the store answers.
				FrameImageStore.get().request(e.getId(), e.getImage());
			} else if (FrameEvent.OP_CLOSE.equals(op)) {
				removeFrame(e.getId());
			} else if (FrameEvent.OP_CLEAR.equals(op)) {
				closeAll();
			}
		}
	}

	/** True while at least one frame is on screen. */
	public boolean hasFrames() {
		return !entries.isEmpty();
	}

	/** Tear every frame down. Used when the connection ends or the activity does. */
	public void closeAll() {
		ArrayList<String> ids = new ArrayList<String>(entries.keySet());
		for (int i = 0; i < ids.size(); i++) {
			removeFrame(ids.get(i));
		}
		entries.clear();
		specs.clear();
	}

	public void detach() {
		persistHandler.removeCallbacks(persistRunnable);
		// closeAll() below cancels each entry's pending size send; this catches a
		// callback for a frame that has already left the map.
		resizeHandler.removeCallbacksAndMessages(null);
		FrameImageStore.get().removeListener(this);
		closeAll();
	}

	@Override
	public void onFrameImageChanged(final String key) {
		if (key == null) {
			for (Entry e : entries.values()) {
				applyImage(e);
			}
			return;
		}
		Entry e = entries.get(key);
		if (e != null) {
			applyImage(e);
		}
	}

	// ---- frames -----------------------------------------------------------

	private void openFrame(final FrameEvent e) {
		MainWindow activity = host.getMainWindow();
		if (activity == null || e.getId().length() == 0) {
			return;
		}
		RelativeLayout container = (RelativeLayout) activity.findViewById(R.id.window_container);
		if (container == null) {
			return;
		}
		Entry entry = entries.get(e.getId());
		if (entry == null) {
			entry = inflate(activity, container, e.getId());
			if (entry == null) {
				return;
			}
			entries.put(e.getId(), entry);
			// The server's type is a starting suggestion for a frame nobody has
			// arranged yet. Once the player has moved a frame, their shape wins:
			// changing it under them because the next frame said "docked" would
			// undo a choice they made on purpose.
			if (!shapeLoaded || !hasSavedShape()) {
				shape.floating = !"docked".equalsIgnoreCase(e.getType());
			}
		}
		entry.label = e.getLabel();
		applyChrome(entry);
		applyLayout(entry);
		applyImage(entry);
		bringUnderChrome(entry);
	}

	private void imageFor(final FrameEvent e) {
		if (e.getId().length() == 0) {
			return;
		}
		if (!entries.containsKey(e.getId())) {
			// An image for a frame we never opened. The service filters these
			// out already; if one arrives anyway, dropping it silently would
			// leave a picture nowhere.
			return;
		}
		specs.put(e.getId(), e.getImage());
		// requestFresh, not request: a server sending frame.image is saying there is
		// a new picture, and eden's map lives at one unchanging URL whose contents
		// change as the character walks. Trusting the string here is what left the
		// frame showing the room the player had already left.
		FrameImageStore.get().requestFresh(e.getId(), e.getImage());
		Entry entry = entries.get(e.getId());
		applyImage(entry);
	}

	private void removeFrame(final String id) {
		Entry e = entries.remove(id);
		specs.remove(id);
		FrameImageStore.get().forget(id);
		if (e == null) {
			return;
		}
		if (e.resizeSend != null) {
			resizeHandler.removeCallbacks(e.resizeSend);
			e.resizeSend = null;
		}
		if (e.imageView != null) {
			e.imageView.setImageDrawable(null);
		}
		if (e.root != null && e.root.getParent() instanceof ViewGroup) {
			((ViewGroup) e.root.getParent()).removeView(e.root);
		}
		e.root = null;
	}

	private Entry inflate(final MainWindow activity, final RelativeLayout container,
			final String id) {
		LayoutInflater inflater = LayoutInflater.from(activity);
		View root = inflater.inflate(R.layout.frame_overlay, container, false);
		if (root == null) {
			return null;
		}
		final Entry e = new Entry();
		e.id = id;
		e.root = root;
		e.titleBar = (LinearLayout) root.findViewById(R.id.frame_title_bar);
		e.titleView = (TextView) root.findViewById(R.id.frame_title);
		e.dragHandle = (TextView) root.findViewById(R.id.frame_drag_handle);
		e.closeBtn = (android.widget.ImageButton) root.findViewById(R.id.frame_close);
		e.drawerCloseBtn = (android.widget.ImageButton)
				root.findViewById(R.id.frame_drawer_close);
		e.contentHost = (FrameLayout) root.findViewById(R.id.frame_content);
		e.imageView = (ImageView) root.findViewById(R.id.frame_image);
		e.statusView = (TextView) root.findViewById(R.id.frame_status);
		e.accentLine = root.findViewById(R.id.frame_accent);
		e.edgeBottom = root.findViewById(R.id.frame_edge_bottom);
		e.resizeHandle = root.findViewById(R.id.frame_resize_handle);
		root.setTag("frame_overlay:" + id);
		if (root.getId() == View.NO_ID) {
			root.setId(View.generateViewId());
		}
		container.addView(root, new RelativeLayout.LayoutParams(
				RelativeLayout.LayoutParams.MATCH_PARENT,
				RelativeLayout.LayoutParams.WRAP_CONTENT));
		wire(e);
		e.resizeSend = new Runnable() {
			@Override
			public void run() {
				if (e.pendingW == e.reportedW && e.pendingH == e.reportedH) {
					return;
				}
				// Marked as told only now, when it is actually said. Doing it when
				// the send was scheduled would let a cancelled send convince us the
				// server had heard a size it never did.
				e.reportedW = e.pendingW;
				e.reportedH = e.pendingH;
				host.reportFrameSize(e.id, e.pendingW, e.pendingH);
			}
		};
		if (e.contentHost != null) {
			e.contentHost.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
				@Override
				public void onLayoutChange(View v, int left, int top, int right, int bottom,
						int oldLeft, int oldTop, int oldRight, int oldBottom) {
					reportSize(e, right - left, bottom - top);
				}
			});
		}
		return e;
	}

	/**
	 * Tell the server the frame's real pixel size, once it has settled.
	 *
	 * <p>{@code frame.opened} goes out before any of this exists and carries
	 * zeroes for pixels, so the first {@code frame.resized} is a correction the
	 * server needs. Everything after it is only worth sending when the size the
	 * player left the frame at differs from the one the server was last told —
	 * a layout pass runs for reasons that have nothing to do with the frame, and
	 * a drag runs one per touch move.
	 */
	private void reportSize(final Entry e, final int w, final int h) {
		if (e == null || w <= 1 || h <= 1) {
			return;
		}
		e.pendingW = w;
		e.pendingH = h;
		if (e.resizeSend == null) {
			return;
		}
		// Cancel first in both branches. A drag that wanders and comes back to the
		// size the server already knows must also call off the send that was armed
		// for a size the frame no longer has.
		resizeHandler.removeCallbacks(e.resizeSend);
		if (w == e.reportedW && h == e.reportedH) {
			return;
		}
		resizeHandler.postDelayed(e.resizeSend, RESIZE_DEBOUNCE_MS);
	}

	// ---- looks ------------------------------------------------------------

	private void applyChrome(final Entry e) {
		if (e == null || e.root == null) {
			return;
		}
		boolean floating = shape.floating;
		if (e.titleBar != null) {
			e.titleBar.setVisibility(floating ? View.VISIBLE : View.GONE);
		}
		if (e.titleView != null) {
			String t = e.label != null && e.label.length() > 0 ? e.label : e.id;
			e.titleView.setText(t);
		}
		if (e.accentLine != null) {
			e.accentLine.setVisibility(floating ? View.VISIBLE : View.GONE);
		}
		if (e.resizeHandle != null) {
			e.resizeHandle.setVisibility(floating ? View.VISIBLE : View.GONE);
			if (floating) {
				e.resizeHandle.bringToFront();
			}
		}
		if (e.edgeBottom != null) {
			e.edgeBottom.setVisibility(floating ? View.GONE : View.VISIBLE);
			if (!floating) {
				e.edgeBottom.bringToFront();
			}
		}
		// A drawer has no title bar to hang a close button on, and a frame the
		// player cannot shut is a frame that owns the top of their screen.
		if (e.drawerCloseBtn != null) {
			e.drawerCloseBtn.setVisibility(floating ? View.GONE : View.VISIBLE);
			if (!floating) {
				e.drawerCloseBtn.bringToFront();
			}
		}
		if (e.root != null) {
			int pct = shape.opacity < 40 ? 40 : (shape.opacity > 100 ? 100 : shape.opacity);
			e.root.setAlpha(pct / 100f);
		}
	}

	private void applyLayout(final Entry e) {
		MainWindow activity = host.getMainWindow();
		if (e == null || e.root == null || activity == null) {
			return;
		}
		float density = activity.getResources().getDisplayMetrics().density;
		int screenH = activity.getResources().getDisplayMetrics().heightPixels;
		RelativeLayout.LayoutParams lp;
		if (shape.floating) {
			int w = Math.max((int) (MIN_FLOAT_DP * density), (int) (shape.w * density));
			int h = Math.max((int) (MIN_FLOAT_DP * density), (int) (shape.h * density));
			int x = Math.max(0, (int) (shape.x * density));
			int y = clampFloatTop(Math.max(0, (int) (shape.y * density)), h, screenH, x, x + w);
			lp = new RelativeLayout.LayoutParams(w, h);
			lp.leftMargin = x;
			lp.topMargin = y;
			lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
			e.root.setLayoutParams(lp);
			e.root.requestLayout();
			reclampWhenChromeIsMeasured(e);
		} else {
			int maxH = (int) (screenH * MAX_DRAWER_SCREEN_FRACTION);
			int minH = (int) (MIN_DRAWER_DP * density);
			int h = Math.max(minH, Math.min(maxH, (int) (shape.drawerH * density)));
			ViewGroup.LayoutParams glp = e.root.getLayoutParams();
			if (glp instanceof RelativeLayout.LayoutParams) {
				lp = (RelativeLayout.LayoutParams) glp;
				if (lp.height == h
						&& lp.width == RelativeLayout.LayoutParams.MATCH_PARENT
						&& lp.leftMargin == 0
						&& lp.topMargin == 0) {
					return;
				}
				lp.width = RelativeLayout.LayoutParams.MATCH_PARENT;
				lp.height = h;
				// Float → drawer reuses this object; clear float X/Y.
				lp.leftMargin = 0;
				lp.topMargin = 0;
				lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
				lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
			} else {
				lp = new RelativeLayout.LayoutParams(
						RelativeLayout.LayoutParams.MATCH_PARENT, h);
				lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
				lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
			}
			e.root.setLayoutParams(lp);
			e.root.requestLayout();
		}
	}

	/**
	 * A frame restored before the ⋮ strip was measured got no keep-out; lay it
	 * out again once the strip exists. Once per entry — the second pass finds the
	 * strip measured and has nothing left to schedule.
	 */
	private void reclampWhenChromeIsMeasured(final Entry e) {
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
	 * Keep a floating frame above the input bar, so the player can still type,
	 * and above the ⋮ strip when it reaches that corner, so neither the frame's
	 * handles nor ⋮ ends up buried under the other.
	 *
	 * <p>{@code left}/{@code right} are the frame's own edges in the container,
	 * which is match_parent, so they are screen x as well.
	 */
	private int clampFloatTop(final int top, final int height, final int screenH,
			final int left, final int right) {
		int maxBottom = screenH;
		View inputbar = findInputBar();
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
		return top > maxTop ? maxTop : Math.max(0, top);
	}

	private View findInputBar() {
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

	/**
	 * Show the picture, or say in words why there is not one.
	 *
	 * <p>A blank rectangle is the one thing this must never be: a player who
	 * cannot tell "still fetching" from "the server's URL is wrong" has no way
	 * to know whether waiting will help.
	 */
	private void applyImage(final Entry e) {
		if (e == null || e.imageView == null || e.statusView == null) {
			return;
		}
		FrameImageStore store = FrameImageStore.get();
		Bitmap bmp = store.getBitmap(e.id);
		if (bmp != null && !bmp.isRecycled()) {
			e.imageView.setImageBitmap(bmp);
			e.imageView.setVisibility(View.VISIBLE);
			e.statusView.setVisibility(View.GONE);
			return;
		}
		e.imageView.setImageDrawable(null);
		e.imageView.setVisibility(View.INVISIBLE);
		e.statusView.setVisibility(View.VISIBLE);
		String failure = store.getFailure(e.id);
		if (failure != null) {
			e.statusView.setText("Could not show this picture: " + failure);
		} else if (store.isLoading(e.id)) {
			e.statusView.setText("Loading…");
		} else if (specs.containsKey(e.id)) {
			e.statusView.setText("Loading…");
		} else {
			e.statusView.setText("The server opened this frame but has not sent a picture yet.");
		}
	}

	private void bringUnderChrome(final Entry e) {
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		ChromeController chrome = activity.getChromeController();
		if (chrome != null) {
			chrome.bringViewUnderChrome(e != null ? e.root : null);
		} else if (e != null && e.root != null) {
			e.root.bringToFront();
			View chromeView = activity.findViewById(R.id.gameplay_chrome_overlay);
			if (chromeView != null) {
				chromeView.bringToFront();
			}
		}
	}

	// ---- gestures ---------------------------------------------------------

	private void wire(final Entry e) {
		View.OnClickListener close = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// The server is told first, then the window goes. The service
				// answers with a close event of its own, which is what actually
				// removes the frame — one path, whether the player used the
				// button or typed .frame close.
				host.closeFrameOnServer(e.id);
			}
		};
		if (e.closeBtn != null) {
			e.closeBtn.setOnClickListener(close);
		}
		if (e.drawerCloseBtn != null) {
			e.drawerCloseBtn.setOnClickListener(close);
		}

		View.OnLongClickListener menu = new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				showFrameMenu(e);
				return true;
			}
		};
		if (e.titleBar != null) {
			e.titleBar.setOnLongClickListener(menu);
		}
		if (e.titleView != null) {
			e.titleView.setOnLongClickListener(menu);
		}
		if (e.drawerCloseBtn != null) {
			e.drawerCloseBtn.setOnLongClickListener(menu);
		}
		if (e.edgeBottom != null) {
			e.edgeBottom.setOnLongClickListener(menu);
		}

		// Both gestures measure from where the finger went down rather than from
		// the previous event. Per-event deltas rounded to dp throw the fraction
		// away every time, and a slow drag then delivers nothing at all — the
		// same mistake was found and fixed in the extra text overlays.
		View.OnTouchListener drag = new View.OnTouchListener() {
			float downX;
			float downY;
			int startX;
			int startY;

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (!shape.floating) {
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
					startX = shape.x;
					startY = shape.y;
					return true;
				case MotionEvent.ACTION_MOVE:
					shape.x = Math.max(0, startX + Math.round((event.getRawX() - downX) / density));
					shape.y = Math.max(0, startY + Math.round((event.getRawY() - downY) / density));
					applyAllLayouts();
					return true;
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
			e.dragHandle.setOnTouchListener(drag);
		}
		if (e.titleView != null) {
			e.titleView.setOnTouchListener(drag);
		}

		if (e.resizeHandle != null) {
			e.resizeHandle.setOnTouchListener(new View.OnTouchListener() {
				float downX;
				float downY;
				int startW;
				int startH;

				@Override
				public boolean onTouch(View v, MotionEvent event) {
					if (!shape.floating) {
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
						startW = shape.w;
						startH = shape.h;
						return true;
					case MotionEvent.ACTION_MOVE:
						shape.w = Math.max(MIN_FLOAT_DP,
								startW + Math.round((event.getRawX() - downX) / density));
						shape.h = Math.max(MIN_FLOAT_DP,
								startH + Math.round((event.getRawY() - downY) / density));
						applyAllLayouts();
						return true;
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

		if (e.edgeBottom != null) {
			e.edgeBottom.setOnTouchListener(new View.OnTouchListener() {
				float downY;
				int startH;

				@Override
				public boolean onTouch(View v, MotionEvent event) {
					if (shape.floating) {
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
						startH = shape.drawerH;
						return true;
					case MotionEvent.ACTION_MOVE: {
						int next = startH + Math.round((event.getRawY() - downY) / density);
						if (next < MIN_DRAWER_DP) {
							next = MIN_DRAWER_DP;
						}
						if (next > maxDp) {
							next = maxDp;
						}
						if (next == shape.drawerH) {
							return true;
						}
						shape.drawerH = next;
						// Shared shape.drawerH — every open frame must track it.
						// Skip bringUnderChrome (z-order unchanged on height-only).
						for (Entry other : entries.values()) {
							applyLayout(other);
						}
						return true;
					}
					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_CANCEL:
						applyAllLayouts();
						schedulePersist();
						return true;
					default:
						return false;
					}
				}
			});
		}
	}

	private void showFrameMenu(final Entry e) {
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		final CharSequence[] items = new CharSequence[] {
				shape.floating ? "Show as a drawer at the top" : "Show as a floating window",
				"Opacity (now " + shape.opacity + "%)",
				"Close this frame",
		};
		new android.app.AlertDialog.Builder(activity)
				.setTitle(e.label != null && e.label.length() > 0 ? e.label : e.id)
				.setItems(items, new android.content.DialogInterface.OnClickListener() {
					@Override
					public void onClick(android.content.DialogInterface dialog, int which) {
						switch (which) {
						case 0:
							shape.floating = !shape.floating;
							applyAllChrome();
							applyAllLayouts();
							schedulePersist();
							break;
						case 1:
							showOpacityPicker();
							break;
						default:
							host.closeFrameOnServer(e.id);
							break;
						}
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void showOpacityPicker() {
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		final int[] choices = new int[] { 40, 50, 60, 70, 80, 85, 90, 100 };
		CharSequence[] labels = new CharSequence[choices.length];
		int selected = choices.length - 1;
		for (int i = 0; i < choices.length; i++) {
			labels[i] = choices[i] + "%";
			if (choices[i] == shape.opacity) {
				selected = i;
			}
		}
		new android.app.AlertDialog.Builder(activity)
				.setTitle("Opacity (now " + shape.opacity + "%)")
				.setSingleChoiceItems(labels, selected,
						new android.content.DialogInterface.OnClickListener() {
							@Override
							public void onClick(android.content.DialogInterface dialog, int which) {
								shape.opacity = choices[which];
								applyAllChrome();
								schedulePersist();
								dialog.dismiss();
							}
						})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void applyAllChrome() {
		for (Entry e : entries.values()) {
			applyChrome(e);
		}
	}

	private void applyAllLayouts() {
		for (Entry e : entries.values()) {
			applyLayout(e);
			bringUnderChrome(e);
		}
	}

	// ---- the remembered shape --------------------------------------------

	private SharedPreferences prefs() {
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return null;
		}
		return activity.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
	}

	private boolean hasSavedShape() {
		SharedPreferences p = prefs();
		return p != null && p.contains(KEY_MODE_FLOAT);
	}

	private void loadShapeOnce() {
		if (shapeLoaded) {
			return;
		}
		SharedPreferences p = prefs();
		if (p == null) {
			return;
		}
		shapeLoaded = true;
		shape.floating = p.getBoolean(KEY_MODE_FLOAT, true);
		shape.x = p.getInt(KEY_X, DEFAULT_X_DP);
		shape.y = p.getInt(KEY_Y, DEFAULT_Y_DP);
		shape.w = p.getInt(KEY_W, DEFAULT_W_DP);
		shape.h = p.getInt(KEY_H, DEFAULT_H_DP);
		shape.drawerH = p.getInt(KEY_DRAWER_H, DEFAULT_DRAWER_H_DP);
		shape.opacity = p.getInt(KEY_OPACITY, DEFAULT_OPACITY);
	}

	private void schedulePersist() {
		persistHandler.removeCallbacks(persistRunnable);
		persistHandler.postDelayed(persistRunnable, PERSIST_DEBOUNCE_MS);
	}

	/**
	 * Save the shape.
	 *
	 * <p>{@code apply()} rather than {@code commit()}: this runs on the main
	 * thread at the end of a drag, and committing would put a disk write there.
	 * Losing the last nudge of a window to a kill is not worth a stutter.
	 */
	private void saveShape() {
		SharedPreferences p = prefs();
		if (p == null) {
			return;
		}
		p.edit()
				.putBoolean(KEY_MODE_FLOAT, shape.floating)
				.putInt(KEY_X, shape.x)
				.putInt(KEY_Y, shape.y)
				.putInt(KEY_W, shape.w)
				.putInt(KEY_H, shape.h)
				.putInt(KEY_DRAWER_H, shape.drawerH)
				.putInt(KEY_OPACITY, shape.opacity)
				.apply();
	}
}

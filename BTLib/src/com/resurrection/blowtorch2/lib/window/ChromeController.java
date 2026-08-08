package com.resurrection.blowtorch2.lib.window;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.RelativeLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.resurrection.blowtorch2.lib.R;

/**
 * Gameplay chrome: input bar / divider anchors, IME lift, FAB strip, toolbar
 * appearance, and inset-derived status/title bar heights for the Lua contract.
 * Lifecycle, menus, and windowCall stay on {@link MainWindow}.
 */
public final class ChromeController {

	static final int LEGACY_INPUT_BAR_ID = 10;
	static final int LEGACY_DIVIDER_ID = 40;
	static final int LEGACY_TEXT_INPUT_ID = 30;

	private final MainWindow activity;
	private View.OnLayoutChangeListener mInputBarChromeLayoutListener = null;

	private int statusBarHeight = 1;
	private int titleBarHeight;
	private boolean isFullScreen = false;
	/** Last IME lift applied via translationY (px). 0 when keyboard is down. */
	private int imeLiftPx = 0;

	/**
	 * ⋮ appearance, from Options → Miscellaneous. Defaults match the drawable
	 * this replaces, so a profile that never touches the options looks the same.
	 */
	private int overflowOpacityPct =
			com.resurrection.blowtorch2.lib.service.plugin.ConnectionSettingsPlugin
					.OVERFLOW_OPACITY_DEFAULT;
	private boolean overflowShowBackground = true;
	private boolean overflowShowBorder = true;
	/**
	 * Built plate, kept until the appearance options change.
	 *
	 * <p>{@link #applyOverflowAppearance()} runs from {@link #updateMenuChrome()},
	 * which runs from {@link #refresh()}, which runs from
	 * {@link #onApplyWindowInsets}. IME insets are dispatched repeatedly through
	 * the keyboard animation, so building the drawable there would allocate three
	 * objects per frame for a picture that has not changed.
	 */
	private android.graphics.drawable.Drawable overflowPlateCache = null;

	ChromeController(MainWindow activity) {
		this.activity = activity;
	}

	/** Current IME lift in px; floating Mode A uses this. */
	int getImeLiftPx() {
		return imeLiftPx;
	}

	void loadHeightsFromPrefs() {
		SharedPreferences sprefs = activity.getSharedPreferences("STATUS_BAR_HEIGHT", 0);
		statusBarHeight = sprefs.getInt("STATUS_BAR_HEIGHT",
				(int) (25 * activity.getResources().getDisplayMetrics().density));
		titleBarHeight = sprefs.getInt("TITLE_BAR_HEIGHT", 0);
	}

	/**
	 * Insets body for the Activity-registered listener. Nav-bar padding only —
	 * do not pad for IME (that resizes Lua button_window). Lift via translation.
	 */
	WindowInsetsCompat onApplyWindowInsets(View view, WindowInsetsCompat windowInsets) {
		Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
		Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
		Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
		view.setPadding(cutout.left, 0, cutout.right, bars.bottom);
		// The one authority for how tall the keyboard is.
		//
		// This used to be second-guessed by an estimator built on
		// getWindowVisibleDisplayFrame(). That could never work here: the manifest
		// says adjustNothing, so the window is never resized when the IME shows
		// and the visible frame never shrinks — the estimator returned 0 by
		// construction, and having two authorities for one number meant whichever
		// spoke last won. IME insets are dispatched regardless of soft input mode
		// from API 30 on, which is where Mode A works; below that this reads 0 and
		// keyboard-mode floaters stay hidden, and there is no fallback that works
		// under adjustNothing.
		int lift = Math.max(0, ime.bottom - bars.bottom);
		android.util.Log.i("BTPROF", "insets lift=" + lift
				+ " imeBottom=" + ime.bottom + " barsBottom=" + bars.bottom
				+ " barsTop=" + bars.top + " wasLift=" + imeLiftPx
				+ " wasBarsTop=" + statusBarHeight
				+ " fullScreen=" + isFullScreen);
		applyImeChromeLift((RelativeLayout) view, lift);
		imeLiftPx = lift;
		activity.onFloatingButtonsImeLift(lift);
		statusBarHeight = bars.top;
		titleBarHeight = bars.top;
		SharedPreferences.Editor insetEditor =
				activity.getSharedPreferences("STATUS_BAR_HEIGHT", 0).edit();
		insetEditor.putInt("TITLE_BAR_HEIGHT", titleBarHeight);
		insetEditor.putInt("STATUS_BAR_HEIGHT", bars.top);
		insetEditor.apply();
		refresh();
		return windowInsets;
	}

	double getStatusBarHeight() {
		return statusBarHeight;
	}

	boolean isStatusBarHidden() {
		return isFullScreen;
	}

	double getTitleBarHeight() {
		return titleBarHeight;
	}

	boolean isFullScreen() {
		return isFullScreen;
	}

	void setFullScreen(boolean fullScreen) {
		isFullScreen = fullScreen;
	}

	View findGameplayInputBar(RelativeLayout rl) {
		View inputbar = rl.findViewById(LEGACY_INPUT_BAR_ID);
		if (inputbar == null) {
			inputbar = rl.findViewById(R.id.inputbar);
		}
		return inputbar;
	}

	View findGameplayDivider(RelativeLayout rl) {
		View divider = rl.findViewById(LEGACY_DIVIDER_ID);
		if (divider == null) {
			divider = rl.findViewById(R.id.divider);
		}
		return divider;
	}

	/**
	 * Translate gameplay chrome above the IME while keeping adjustNothing / no IME padding.
	 * By default lifts input chrome and game text so output stays readable. Leaves
	 * {@code button_window} untranslated so Lua button coordinates stay stable.
	 * When Options → Window → Keep text still with keyboard? is on, game text windows
	 * also stay put (only the input bar / FAB rise).
	 */
	void applyImeChromeLift(RelativeLayout rl, int liftPx) {
		if (rl == null) {
			return;
		}
		final boolean keepText = activity.keepTextStillWithIme();
		float ty = -liftPx;
		for (int i = 0; i < rl.getChildCount(); i++) {
			View child = rl.getChildAt(i);
			if (child instanceof com.resurrection.blowtorch2.lib.window.Window) {
				String tag = String.valueOf(child.getTag());
				// Options → Window → Bottom padding with keyboard needs to know the
				// keyboard is up. This is the one authority for that (see
				// onApplyWindowInsets); text windows are told, button_window is not
				// — its coordinates are Lua's and must not move.
				if (!"button_window".equals(tag)) {
					((com.resurrection.blowtorch2.lib.window.Window) child)
							.setImeLiftPx(liftPx);
				}
				if ("button_window".equals(tag) || keepText) {
					// Buttons always fixed; game text fixed when Keep text still is on.
					child.setTranslationY(0f);
					continue;
				}
			}
			// Mapper / extra-text / floating-button overlays stay pinned; only
			// game Windows + input lift. Mode B floaters must not move with the
			// keyboard (SUPER_BUTTON_PLAN D11); Mode A is positioned by
			// FloatingButtonController against the IME height instead.
			if (child.getId() == R.id.mapper_overlay_root) {
				child.setTranslationY(0f);
				continue;
			}
			Object tagObj = child.getTag();
			if (tagObj != null && tagObj.toString().startsWith("extra_text_overlay:")) {
				child.setTranslationY(0f);
				continue;
			}
			if (tagObj != null && FloatingButtonController.LAYER_TAG.equals(tagObj.toString())) {
				child.setTranslationY(0f);
				continue;
			}
			child.setTranslationY(ty);
		}
		// FAB strip is in a sibling overlay. Keep it locked to the input bar's IME lift
		// (same translationY). Positioning uses layout bottomMargin only — do not also
		// recompute from window locations while translated (that double-counts IME height).
		View inputbar = findGameplayInputBar(rl);
		View fabStrip = activity.findViewById(R.id.gameplay_fab_strip);
		if (fabStrip != null) {
			fabStrip.setTranslationY(inputbar != null ? inputbar.getTranslationY() : ty);
		}
		// The floating completion chips are the FAB strip's neighbour in that
		// same overlay, and they rest on the input bar the same way — so they
		// need the same lift. Without it they stayed at the bottom of an
		// unresized window and the keyboard covered them, which is what the
		// chips "falling under the keyboard" was.
		// Unless the player has dragged it somewhere: then the margin it was
		// given is the position they chose, and adding the lift on top would
		// move it out from under them every time the keyboard opened. See
		// MainWindow.positionWordSuggestionOverlay.
		View floatingChips = activity.findViewById(R.id.input_word_suggestions_float);
		if (floatingChips != null) {
			floatingChips.setTranslationY(activity.isSuggestionPanelPlaced()
					? 0f : (inputbar != null ? inputbar.getTranslationY() : ty));
		}
	}

	/**
	 * Profiles still say {@code above="40"} (legacy divider). The divider now lives
	 * inside the input bar, so RelativeLayout ignores that rule and text windows
	 * draw under the chrome. Remap to the input bar id (10) for non-overlay windows.
	 */
	void anchorWindowAboveInputChrome(RelativeLayout.LayoutParams params,
			String windowName) {
		if (params == null) {
			return;
		}
		// button_window stays full-bleed so Lua button coordinates stay stable.
		if ("button_window".equals(windowName)) {
			return;
		}
		int above = params.getRule(RelativeLayout.ABOVE);
		if (above == LEGACY_DIVIDER_ID || above == R.id.divider) {
			params.addRule(RelativeLayout.ABOVE, LEGACY_INPUT_BAR_ID);
		}
	}

	/** Re-apply chrome anchors when input bar height changes (grow / search / Edit). */
	void rematerializeGameWindowChromeAnchors(RelativeLayout rl) {
		if (rl == null) {
			return;
		}
		boolean changed = false;
		for (int i = 0; i < rl.getChildCount(); i++) {
			View child = rl.getChildAt(i);
			if (!(child instanceof com.resurrection.blowtorch2.lib.window.Window)) {
				continue;
			}
			ViewGroup.LayoutParams glp = child.getLayoutParams();
			if (!(glp instanceof RelativeLayout.LayoutParams)) {
				continue;
			}
			RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) glp;
			int before = lp.getRule(RelativeLayout.ABOVE);
			anchorWindowAboveInputChrome(lp, String.valueOf(child.getTag()));
			if (lp.getRule(RelativeLayout.ABOVE) != before) {
				child.setLayoutParams(lp);
				changed = true;
			}
		}
		if (changed) {
			rl.requestLayout();
		}
	}

	void bringGameplayChromeToFront(RelativeLayout rl) {
		if (rl == null) {
			return;
		}
		View inputbar = findGameplayInputBar(rl);
		if (inputbar != null) {
			inputbar.bringToFront();
		}
		// Floating copies sit above the input bar (Mode A editing keys share
		// that band when the keyboard is up) and below ⋮.
		View floating = findFloatingButtonLayer(rl);
		if (floating != null) {
			floating.bringToFront();
		}
		View overlay = activity.findViewById(R.id.gameplay_chrome_overlay);
		if (overlay != null) {
			overlay.bringToFront();
		}
	}

	private static View findFloatingButtonLayer(RelativeLayout rl) {
		if (rl == null) {
			return null;
		}
		for (int i = 0; i < rl.getChildCount(); i++) {
			View child = rl.getChildAt(i);
			if (child != null
					&& FloatingButtonController.LAYER_TAG.equals(String.valueOf(child.getTag()))) {
				return child;
			}
		}
		return null;
	}

	/**
	 * Raise an overlay (mapper / extra text / floating buttons), then put ⋮
	 * above it. Floating stays above the input bar so Mode A keys remain
	 * visible over the keyboard band.
	 */
	void bringViewUnderChrome(View overlay) {
		RelativeLayout rl = (RelativeLayout) activity.findViewById(R.id.window_container);
		View inputbar = findGameplayInputBar(rl);
		if (inputbar != null) {
			inputbar.bringToFront();
		}
		if (overlay != null) {
			overlay.bringToFront();
		}
		View floating = findFloatingButtonLayer(rl);
		if (floating != null && floating != overlay) {
			floating.bringToFront();
		}
		View chromeOverlay = activity.findViewById(R.id.gameplay_chrome_overlay);
		if (chromeOverlay != null) {
			chromeOverlay.bringToFront();
		}
	}

	void layoutGameplayChrome(RelativeLayout rl) {
		if (rl == null) {
			return;
		}
		final View inputbar = findGameplayInputBar(rl);
		final View divider = findGameplayDivider(rl);
		final View toolbar = rl.findViewById(R.id.my_toolbar);
		final View fabStrip = activity.findViewById(R.id.gameplay_fab_strip);
		if (inputbar == null) {
			return;
		}
		final float density = activity.getResources().getDisplayMetrics().density;
		final int margin = (int) (4 * density);
		final int dividerHeight = (int) (3 * density);

		RelativeLayout.LayoutParams inputLp = new RelativeLayout.LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		inputLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		inputbar.setLayoutParams(inputLp);

		// Legacy layouts kept divider as a RelativeLayout sibling above the input bar.
		if (divider != null && divider.getParent() == rl
				&& divider.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
			RelativeLayout.LayoutParams dividerLp = new RelativeLayout.LayoutParams(
					LayoutParams.MATCH_PARENT, dividerHeight);
			dividerLp.addRule(RelativeLayout.ABOVE, inputbar.getId());
			divider.setLayoutParams(dividerLp);
		}

		if (toolbar != null) {
			RelativeLayout.LayoutParams toolbarLp = new RelativeLayout.LayoutParams(
					LayoutParams.MATCH_PARENT, 0);
			toolbarLp.addRule(RelativeLayout.ABOVE, inputbar.getId());
			toolbar.setLayoutParams(toolbarLp);
		}

		if (fabStrip != null) {
			final View inputbarFinal = inputbar;
			final int marginFinal = margin;
			Runnable placeFab = new Runnable() {
				@Override
				public void run() {
					placeGameplayFabStrip(fabStrip, inputbarFinal, marginFinal);
				}
			};
			inputbar.removeCallbacks(placeFab);
			inputbar.post(placeFab);
			if (mInputBarChromeLayoutListener == null) {
				mInputBarChromeLayoutListener = new View.OnLayoutChangeListener() {
					@Override
					public void onLayoutChange(View v, int left, int top, int right, int bottom,
							int oldLeft, int oldTop, int oldRight, int oldBottom) {
						int oldH = oldBottom - oldTop;
						int newH = bottom - top;
						if (oldH != newH) {
							placeGameplayFabStrip(fabStrip, inputbarFinal, marginFinal);
						}
					}
				};
			}
			inputbar.removeOnLayoutChangeListener(mInputBarChromeLayoutListener);
			inputbar.addOnLayoutChangeListener(mInputBarChromeLayoutListener);
		}
		bindGameplayFabControls();
		rematerializeGameWindowChromeAnchors(rl);
		bringGameplayChromeToFront(rl);
	}

	/**
	 * Anchor ⋮ above the input chrome (never over Edit/Send).
	 *
	 * <p>End and bottom gaps use the same {@code margin} so the corner looks
	 * even. The strip lives in {@code gameplay_chrome_overlay}, a sibling of
	 * {@code window_container} that does not share its nav-bar padding — add
	 * that padding into the bottom inset or ⋮ sits too low by exactly
	 * {@code bars.bottom}. IME lift stays on translationY via
	 * {@link #applyImeChromeLift}; do not use window locations here.
	 */
	void placeGameplayFabStrip(View fabStrip, View inputbar, int margin) {
		if (fabStrip == null || inputbar == null) {
			return;
		}
		if (!(fabStrip.getParent() instanceof View)) {
			return;
		}
		float density = activity.getResources().getDisplayMetrics().density;
		int inputH = Math.max(inputbar.getHeight(), inputbar.getMeasuredHeight());
		if (inputH <= 0) {
			inputbar.post(new Runnable() {
				@Override
				public void run() {
					placeGameplayFabStrip(fabStrip, inputbar, margin);
				}
			});
			return;
		}
		int navPad = 0;
		View container = activity.findViewById(R.id.window_container);
		if (container != null) {
			navPad = container.getPaddingBottom();
		}
		int bottomInset = inputH + navPad + margin;
		android.widget.FrameLayout.LayoutParams stripLp =
				new android.widget.FrameLayout.LayoutParams(
						LayoutParams.WRAP_CONTENT, (int) (48 * density + 0.5f));
		stripLp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
		stripLp.setMargins(0, 0, margin, bottomInset);
		fabStrip.setLayoutParams(stripLp);
		fabStrip.setTranslationY(inputbar.getTranslationY());
	}

	/**
	 * Lowest screen y a floating overlay may reach without burying ⋮.
	 *
	 * <p>Floating overlays (frame, extra text) clamp their bottom to the top of
	 * the input bar so the player can still type. The ⋮ strip sits in that gap,
	 * bottom-end, so an overlay taken all the way down at the right-hand edge
	 * lands its own drag/resize handle in exactly the ⋮'s 48dp box. Chrome is
	 * above the overlay, so ⋮ wins the touch and the overlay's handle becomes the
	 * thing that cannot be grabbed — the mirror image of the reported bug, and
	 * just as annoying.
	 *
	 * <p>So an overlay that reaches across the strip stops above it instead. An
	 * overlay that does not reach that far is unaffected: most of the screen
	 * width still goes right down to the input bar.
	 *
	 * @param inputBarTop Limit the caller would otherwise use (screen y).
	 * @param overlayLeft Overlay's left edge, screen pixels.
	 * @param overlayRight Overlay's right edge, screen pixels.
	 * @return inputBarTop, or the strip's top when the two would overlap.
	 */
	int floatingOverlayBottomLimit(int inputBarTop, int overlayLeft, int overlayRight) {
		View fabStrip = activity.findViewById(R.id.gameplay_fab_strip);
		if (fabStrip == null || fabStrip.getVisibility() != View.VISIBLE
				|| fabStrip.getWidth() <= 0 || fabStrip.getHeight() <= 0) {
			return inputBarTop;
		}
		int[] loc = new int[2];
		fabStrip.getLocationOnScreen(loc);
		int stripLeft = loc[0];
		int stripRight = loc[0] + fabStrip.getWidth();
		if (overlayRight <= stripLeft || overlayLeft >= stripRight) {
			return inputBarTop;
		}
		// getLocationOnScreen already includes the IME translationY, so no
		// separate IME term here — see placeGameplayFabStrip.
		int stripTop = loc[1];
		return stripTop < inputBarTop ? stripTop : inputBarTop;
	}

	/** True when the ⋮ strip has been measured, so the keep-out can be computed. */
	boolean fabStripHasSize() {
		View fabStrip = activity.findViewById(R.id.gameplay_fab_strip);
		return fabStrip != null && fabStrip.getWidth() > 0 && fabStrip.getHeight() > 0;
	}

	/**
	 * Run {@code action} once the ⋮ strip has a size.
	 *
	 * <p>{@link #floatingOverlayBottomLimit} can only answer once the strip has
	 * been measured, and returns the input bar unchanged before that. Overlays
	 * restored at startup lay themselves out first, so the keep-out silently did
	 * nothing on exactly the path that needs it: a floating frame restored into
	 * the bottom-right corner puts its drag handle inside the ⋮'s 48dp box, and
	 * since chrome draws on top, every touch there goes to ⋮ — the handle cannot
	 * be grabbed, so the frame cannot be moved out from under it either. Dragging
	 * and resizing re-clamp as they go; restoring had nothing to re-clamp on.
	 *
	 * <p>Callers should check {@link #fabStripHasSize()} first and only use this
	 * when it is false, so the common path costs nothing.
	 */
	void whenFabStripMeasured(final Runnable action) {
		if (action == null) {
			return;
		}
		final View fabStrip = activity.findViewById(R.id.gameplay_fab_strip);
		if (fabStrip == null) {
			return;
		}
		if (fabStrip.getWidth() > 0 && fabStrip.getHeight() > 0) {
			action.run();
			return;
		}
		fabStrip.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View v, int l, int t, int r, int b,
					int ol, int ot, int or, int ob) {
				if (v.getWidth() <= 0 || v.getHeight() <= 0) {
					return;
				}
				v.removeOnLayoutChangeListener(this);
				action.run();
			}
		});
	}

	/**
	 * Set how the gameplay ⋮ is drawn.
	 *
	 * <p>Only the button's own background and alpha change. The strip is left
	 * alone: {@code ChromeSmokeTest} asserts ⋮'s parent is still the strip
	 * itself, and {@link #placeGameplayFabStrip} rebuilds the strip's params on
	 * every input-bar height change.
	 *
	 * @param opacityPct   Percent, clamped to the option's floor–100.
	 * @param showBackground Draw the translucent disc behind the glyph.
	 * @param showBorder     Draw the thin ring around the disc.
	 */
	void setOverflowAppearance(int opacityPct, boolean showBackground, boolean showBorder) {
		int pct = opacityPct;
		if (pct < com.resurrection.blowtorch2.lib.service.plugin.ConnectionSettingsPlugin
				.OVERFLOW_OPACITY_MIN) {
			pct = com.resurrection.blowtorch2.lib.service.plugin.ConnectionSettingsPlugin
					.OVERFLOW_OPACITY_MIN;
		} else if (pct > 100) {
			pct = 100;
		}
		overflowOpacityPct = pct;
		overflowShowBackground = showBackground;
		overflowShowBorder = showBorder;
		overflowPlateCache = null;
		applyOverflowAppearance();
	}

	/**
	 * Paint the ⋮ from the stored appearance.
	 *
	 * <p>Called again from {@link #updateMenuChrome()} because that is what runs
	 * on every chrome refresh — the plate has to survive one.
	 */
	void applyOverflowAppearance() {
		final View overflowMenu = activity.findViewById(R.id.overflow_menu);
		if (overflowMenu == null) {
			return;
		}
		overflowMenu.setAlpha(overflowOpacityPct / 100f);
		if (!overflowShowBackground && !overflowShowBorder) {
			// Bare glyph: no plate at all, which is the "stop covering my text"
			// setting. Pressed feedback goes with it — there is nothing to tint.
			overflowMenu.setBackground(null);
			return;
		}
		if (overflowPlateCache == null) {
			android.graphics.drawable.StateListDrawable sel =
					new android.graphics.drawable.StateListDrawable();
			sel.addState(new int[] { android.R.attr.state_pressed },
					overflowPlate(0xF0343A42, 0xAA8888CC));
			sel.addState(new int[0],
					overflowPlate(0xD216161C,
							activity.getResources().getColor(R.color.game_chrome_edge)));
			overflowPlateCache = sel;
		}
		if (overflowMenu.getBackground() != overflowPlateCache) {
			overflowMenu.setBackground(overflowPlateCache);
		}
	}

	/** One state of the ⋮ backing plate; either half may be switched off. */
	private android.graphics.drawable.GradientDrawable overflowPlate(int solid, int stroke) {
		android.graphics.drawable.GradientDrawable d =
				new android.graphics.drawable.GradientDrawable();
		d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
		d.setColor(overflowShowBackground ? solid : Color.TRANSPARENT);
		if (overflowShowBorder) {
			int w = (int) (activity.getResources().getDisplayMetrics().density + 0.5f);
			d.setStroke(Math.max(1, w), stroke);
		}
		return d;
	}

	/** Wrench + (during edit) settings/done/cancel sit in one bottom-end strip. */
	void bindGameplayFabControls() {
		final View overflowMenu = activity.findViewById(R.id.overflow_menu);
		if (overflowMenu != null) {
			overflowMenu.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					activity.showGameplayOptionsMenu(v);
				}
			});
			overflowMenu.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					// Long-press overflow enters button edit mode.
					activity.windowCall("button_window", "doEdit", "");
					return true;
				}
			});
		}
		View undo = activity.findViewById(R.id.editor_undo);
		if (undo != null) {
			undo.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					activity.windowCall("button_window", "editorMenuUndo", "");
				}
			});
		}
		View redo = activity.findViewById(R.id.editor_redo);
		if (redo != null) {
			redo.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					activity.windowCall("button_window", "editorMenuRedo", "");
				}
			});
		}
		View settings = activity.findViewById(R.id.editor_settings);
		if (settings != null) {
			settings.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					activity.windowCall("button_window", "editorMenuSettings", "");
				}
			});
		}
		View done = activity.findViewById(R.id.editor_done);
		if (done != null) {
			done.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					activity.windowCall("button_window", "editorMenuDone", "");
				}
			});
		}
		View cancel = activity.findViewById(R.id.editor_cancel);
		if (cancel != null) {
			cancel.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					activity.windowCall("button_window", "editorMenuCancel", "");
				}
			});
		}
	}

	/**
	 * Jedno źródło prawdy dla chrome gry: odśwież pozycje menu oraz przelicz
	 * offsety w oknach Lua (przyciski).
	 */
	void refresh() {
		layoutGameplayChrome((RelativeLayout) activity.findViewById(R.id.window_container));
		updateMenuChrome();
		activity.windowCall("button_window", "delayedStatusRefresh", "");
		activity.scheduleRenawsAfterChromeRefresh();
	}

	/**
	 * Button-layout editing uses overlay icons (settings/done/cancel) to the left
	 * of the overflow control. Hide ⋮ while editing — those actions already live
	 * on the FAB strip (overflow popup cannot invoke Lua menu click listeners).
	 * The ActionBar toolbar stays hidden so chrome never jumps to the top.
	 */
	void updateMenuChrome() {
		final androidx.appcompat.widget.Toolbar toolbar =
				(androidx.appcompat.widget.Toolbar) activity.findViewById(R.id.my_toolbar);
		final View overflowMenu = activity.findViewById(R.id.overflow_menu);
		final View editorActions = activity.findViewById(R.id.editor_actions);
		final boolean showEditorChrome = activity.getEditorMenuStackSize() > 0;

		if (toolbar != null) {
			ViewGroup.LayoutParams lp = toolbar.getLayoutParams();
			if (lp != null) {
				lp.height = 0;
				if (lp instanceof ViewGroup.MarginLayoutParams) {
					((ViewGroup.MarginLayoutParams) lp).topMargin = 0;
				}
				toolbar.setLayoutParams(lp);
			}
			toolbar.setVisibility(View.GONE);
		}
		if (activity.getSupportActionBar() != null) {
			activity.getSupportActionBar().hide();
		}
		if (overflowMenu != null) {
			overflowMenu.setVisibility(showEditorChrome ? View.GONE : View.VISIBLE);
			applyOverflowAppearance();
		}
		if (editorActions != null) {
			editorActions.setVisibility(showEditorChrome ? View.VISIBLE : View.GONE);
		}
		RelativeLayout rl = (RelativeLayout) activity.findViewById(R.id.window_container);
		bringGameplayChromeToFront(rl);
	}

	void configureGameplayToolbar(androidx.appcompat.widget.Toolbar toolbar) {
		if (toolbar == null) {
			return;
		}
		ColorDrawable transparent = new ColorDrawable(Color.TRANSPARENT);
		toolbar.setBackground(transparent);
		toolbar.setBackgroundDrawable(transparent);
		toolbar.setElevation(0f);
		toolbar.setClickable(false);
		toolbar.setFocusable(false);
		toolbar.setContentInsetsAbsolute(0, 0);
		toolbar.setContentInsetsRelative(0, 0);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			toolbar.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
			toolbar.setStateListAnimator(null);
		}
		if (activity.getSupportActionBar() != null) {
			activity.getSupportActionBar().setBackgroundDrawable(transparent);
			activity.getSupportActionBar().setElevation(0f);
		}
		if (toolbar.getParent() instanceof View) {
			View parent = (View) toolbar.getParent();
			parent.setBackgroundColor(Color.TRANSPARENT);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				parent.setElevation(0f);
			}
		}
		View decor = activity.getWindow().getDecorView();
		if (decor != null) {
			decor.setBackgroundColor(Color.TRANSPARENT);
		}
		toolbar.post(new Runnable() {
			@Override
			public void run() {
				toolbar.setBackground(transparent);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
					toolbar.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
				}
			}
		});
	}
}

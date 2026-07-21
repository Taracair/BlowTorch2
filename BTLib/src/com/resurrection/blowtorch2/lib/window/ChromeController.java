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
	/** Extra gap above the input chrome so ⋮ clears Edit/Send. */
	private static final float OVERFLOW_LIFT_PHONE_DIP = 20f;
	private static final float OVERFLOW_LIFT_TABLET_DIP = 24f;

	private final MainWindow activity;
	private View.OnLayoutChangeListener mInputBarChromeLayoutListener = null;

	private int statusBarHeight = 1;
	private int titleBarHeight;
	private boolean isFullScreen = false;

	ChromeController(MainWindow activity) {
		this.activity = activity;
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
		view.setPadding(0, 0, 0, bars.bottom);
		int lift = Math.max(0, ime.bottom - bars.bottom);
		applyImeChromeLift((RelativeLayout) view, lift);
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
	 * Translate gameplay content above the IME while keeping adjustNothing / no IME padding.
	 * Lifts input chrome and game text windows so output stays readable. Leaves
	 * {@code button_window} untranslated so Lua button coordinates do not jump from a
	 * layout resize (buttons under the IME stay covered until the keyboard closes).
	 */
	void applyImeChromeLift(RelativeLayout rl, int liftPx) {
		if (rl == null) {
			return;
		}
		float ty = -liftPx;
		for (int i = 0; i < rl.getChildCount(); i++) {
			View child = rl.getChildAt(i);
			if (child instanceof com.resurrection.blowtorch2.lib.window.Window
					&& "button_window".equals(String.valueOf(child.getTag()))) {
				// Keep Lua buttons fixed; prioritize text readability over button usability under IME.
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
		View overlay = activity.findViewById(R.id.gameplay_chrome_overlay);
		if (overlay != null) {
			overlay.bringToFront();
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
			final float liftDip = activity.getResources().getConfiguration().smallestScreenWidthDp >= 600
					? OVERFLOW_LIFT_TABLET_DIP
					: OVERFLOW_LIFT_PHONE_DIP;
			Runnable placeFab = new Runnable() {
				@Override
				public void run() {
					placeGameplayFabStrip(fabStrip, inputbarFinal, marginFinal, liftDip);
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
							placeGameplayFabStrip(fabStrip, inputbarFinal, marginFinal, liftDip);
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

	/** Anchor ⋮ above the input chrome (never over Edit/Send). */
	void placeGameplayFabStrip(View fabStrip, View inputbar, int margin, float liftDip) {
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
					placeGameplayFabStrip(fabStrip, inputbar, margin, liftDip);
				}
			});
			return;
		}
		// Layout-only inset above the input bar. IME lift is applied via translationY
		// synced to the input bar in applyImeChromeLift — do not use window locations here.
		int bottomInset = inputH + margin + (int) (liftDip * density + 0.5f);
		android.widget.FrameLayout.LayoutParams stripLp =
				new android.widget.FrameLayout.LayoutParams(
						LayoutParams.WRAP_CONTENT, (int) (48 * density + 0.5f));
		stripLp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
		stripLp.setMargins(0, 0, margin, bottomInset);
		fabStrip.setLayoutParams(stripLp);
		fabStrip.setTranslationY(inputbar.getTranslationY());
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

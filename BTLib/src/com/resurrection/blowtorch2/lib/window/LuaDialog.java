package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.R;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.util.Log;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class LuaDialog extends Dialog {

	private static final String TAG = "LuaDialog";

	/** Edge-to-edge content; opaque window background (existing behaviour). */
	public static final int LAYOUT_FULLSCREEN = 0;
	/** Transparent window with a dimmed game view behind; content is a bottom panel. */
	public static final int LAYOUT_BOTTOM_SHEET = 1;
	/**
	 * Only as large as its content, centred. For the dialogs that ask one
	 * question — a name, a confirmation — which otherwise inherit the fullscreen
	 * sizing below and take the whole screen to hold one text field.
	 */
	public static final int LAYOUT_COMPACT = 2;

	private View mView = null;
	private Context mContext = null;
	private boolean mTitle;
	private Drawable mBorder;
	private int mLayoutMode = LAYOUT_FULLSCREEN;
	/** Bottom-sheet only: true = see grid above panel; false = opaque fullscreen frame. */
	private boolean mPresentationOverGrid = false;
	/**
	 * Opt-in: react to the soft keyboard so focused fields in scrollable dialog
	 * content stay visible. Off by default — every Lua dialog hosts through this
	 * class, and bottom-sheet / compact modes must not inherit a resize path.
	 *
	 * <p>What this actually does (LAYOUT_FULLSCREEN — the button editor):
	 * <ul>
	 *   <li>{@code SOFT_INPUT_ADJUST_RESIZE} on the dialog window. This is the
	 *       real path on minSdk 28 through targetSdk 36. It is deprecated at 30
	 *       but only <em>ignored</em> when the window has
	 *       {@code setDecorFitsSystemWindows(false)}; this class calls that only
	 *       for {@link #LAYOUT_BOTTOM_SHEET}, never for fullscreen, so resize
	 *       still applies here.
	 *   <li>Skip {@code FLAG_FULLSCREEN} when the status bar is hidden. That flag
	 *       suppresses soft-input resize. Side effect: for a player who hides the
	 *       status bar, the editor opens with the status bar showing. A covered
	 *       field is worse than a visible status bar; stated so it is not found
	 *       by the maintainer on the phone.
	 *   <li>Also {@code Math.max} the content bottom padding with
	 *       {@link WindowInsetsCompat.Type#ime()}. With decor-fits true and
	 *       ADJUST_RESIZE active the window shrinks out from under the keyboard,
	 *       so {@code ime().bottom} is expected to read 0 and this branch is a
	 *       no-op — belt-and-braces whose inset was never measured on a device.
	 *       Do not treat the comment as evidence that the inset does work.
	 * </ul>
	 */
	private boolean mAdjustForIme = false;
	
	public LuaDialog(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}
	
	public LuaDialog(Context context,View v,boolean title,Drawable border) {
		this(context, v, title, border, LAYOUT_FULLSCREEN);
	}

	public LuaDialog(Context context, View v, boolean title, Drawable border, int layoutMode) {
		super(context, R.style.BlowTorch_Dialog_FullScreen);
		mContext = context;
		mView = v;
		mTitle = title;
		mBorder = border;
		mLayoutMode = layoutMode;
	}

	private boolean canShow() {
		if (!(mContext instanceof Activity)) {
			return true;
		}
		Activity activity = (Activity) mContext;
		if (activity.isFinishing()) {
			return false;
		}
		return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed();
	}
	
	@Override
	public void show() {
		if (!canShow()) {
			return;
		}
		try {
			super.show();
		} catch (WindowManager.BadTokenException e) {
			Log.w(TAG, "Unable to show dialog; activity token is stale", e);
		}
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		if(!mTitle) {
			this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		}
		final boolean bottomSheet = mLayoutMode == LAYOUT_BOTTOM_SHEET;
		mPresentationOverGrid = bottomSheet;
		if (bottomSheet) {
			this.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		} else if(mBorder != null) {
			this.getWindow().setBackgroundDrawable(mBorder);
		} else {
			this.getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		}
		
		MainWindow w = (MainWindow)mContext;
		// FLAG_FULLSCREEN suppresses soft-input resize. When the caller opted into
		// IME adjustment, skip it so ADJUST_RESIZE can shrink the window. Trade:
		// a player who hides the status bar in the game will see the status bar
		// while this dialog is open. System-bar clearance still comes from the
		// insets listener below.
		if (w.isStatusBarHidden() && !mAdjustForIme) {
			this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		
		Window window = this.getWindow();
		if (window != null) {
			// Bottom sheet must draw edge-to-edge so the grid shows through the
			// transparent region. Opaque fullscreen presentation later opts back
			// into fitting system windows via setPresentationOverGrid(false).
			// LAYOUT_FULLSCREEN keeps its existing edge-to-edge + padding path.
			if (bottomSheet) {
				WindowCompat.setDecorFitsSystemWindows(window, false);
			}
			final boolean compact = mLayoutMode == LAYOUT_COMPACT;
			int wantWidth = compact
					? WindowManager.LayoutParams.WRAP_CONTENT
					: WindowManager.LayoutParams.MATCH_PARENT;
			int wantHeight = compact
					? WindowManager.LayoutParams.WRAP_CONTENT
					: WindowManager.LayoutParams.MATCH_PARENT;
			window.setLayout(wantWidth, wantHeight);
			WindowManager.LayoutParams attrs = window.getAttributes();
			attrs.width = wantWidth;
			attrs.height = wantHeight;
			attrs.gravity = compact ? Gravity.CENTER : Gravity.FILL;
			if (bottomSheet) {
				attrs.dimAmount = 0.10f;
				window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
			}
			if (mAdjustForIme) {
				// Primary path on 28–36 for LAYOUT_FULLSCREEN (see mAdjustForIme).
				// Compact/bottom-sheet callers should leave mAdjustForIme false.
				window.setSoftInputMode(
						WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
								| WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
			}
			window.setAttributes(attrs);
		}

		// Force full-bleed content even when inflate kept tablet fixed sizes.
		// A compact dialog is the exception and keeps whatever size its content
		// asked for: overriding it here is what made every one-question dialog
		// fill the screen no matter what its Lua layout params said.
		ViewGroup.LayoutParams contentLp = mView.getLayoutParams();
		if (mLayoutMode == LAYOUT_COMPACT) {
			if (contentLp == null) {
				contentLp = new ViewGroup.LayoutParams(
						ViewGroup.LayoutParams.WRAP_CONTENT,
						ViewGroup.LayoutParams.WRAP_CONTENT);
			}
		} else if (contentLp == null) {
			contentLp = new ViewGroup.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.MATCH_PARENT);
		} else {
			contentLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
			contentLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
		}
		this.setContentView(mView, contentLp);

		ViewCompat.setOnApplyWindowInsetsListener(mView, (view, insets) -> {
			Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			// Edge-to-edge bottom sheet: pad content clear of system bars.
			// Opaque fullscreen sheet: window already fits system bars — do not
			// also pad, or large empty bands appear inside the dashed frame.
			// A compact dialog is centred and only as tall as its content, so it
			// is nowhere near a system bar. Padding it by the bar insets would
			// just grow it — the very thing this mode exists to stop.
			boolean padForSystemBars = mLayoutMode != LAYOUT_COMPACT
					&& (mLayoutMode != LAYOUT_BOTTOM_SHEET || mPresentationOverGrid);
			int top = padForSystemBars ? sys.top : 0;
			int bottom = padForSystemBars ? sys.bottom : 0;
			// Belt-and-braces only: with decor-fits + ADJUST_RESIZE the window
			// already shrinks, so ime().bottom is expected to be 0 here. Never
			// measured on a device — do not cite this as a working IME height.
			if (mAdjustForIme) {
				Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
				bottom = Math.max(bottom, ime.bottom);
			}
			view.setPadding(view.getPaddingLeft(), top,
					view.getPaddingRight(), bottom);
			return insets;
		});
		ViewCompat.requestApplyInsets(mView);
	}

	/**
	 * Opt into soft-keyboard avoidance for this dialog only. Must be called
	 * before {@link #show()} so {@link #onCreate} can set soft-input mode and
	 * skip {@code FLAG_FULLSCREEN} (which also means the status bar may show
	 * even when the game hides it — see {@link #mAdjustForIme}). Leave off for
	 * bottom-sheet and compact dialogs — those modes were sized without an IME
	 * path.
	 */
	public void setAdjustForIme(boolean adjustForIme) {
		mAdjustForIme = adjustForIme;
	}

	/**
	 * Bottom sheet: transparent window, grid visible above.
	 * Fullscreen: opaque dashed frame fitted to the system-bar safe area.
	 */
	public void setPresentationOverGrid(boolean overGrid) {
		Window window = getWindow();
		if (window == null) {
			return;
		}
		mPresentationOverGrid = overGrid;
		if (overGrid) {
			WindowCompat.setDecorFitsSystemWindows(window, false);
			window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			WindowManager.LayoutParams attrs = window.getAttributes();
			attrs.dimAmount = 0.10f;
			window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
			window.setAttributes(attrs);
		} else {
			// Fit the window (and crawler drawable) to the safe area so the
			// dashed stroke hugs content. Root padding is cleared by the insets
			// listener — the drawable's own 3sp shape padding is enough inset.
			WindowCompat.setDecorFitsSystemWindows(window, true);
			window.setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
			window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
		}
		if (mView != null) {
			ViewCompat.requestApplyInsets(mView);
		}
	}
}

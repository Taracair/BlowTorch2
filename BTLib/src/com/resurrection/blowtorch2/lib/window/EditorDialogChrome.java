package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.R;

import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * Shared chrome for Alias / Trigger / Timer editors and the Sensor Test card.
 *
 * <p>Family A editors (alias / trigger / timer) are opaque full-screen, same
 * shell as the TRIGGERS list: {@link #fullScreenTheme()} plus
 * {@link #applyFullScreen(Dialog)} after {@link Dialog#setContentView}.
 *
 * <p>Short floating cards (Sensor Test, {@link EditorHelp}) keep
 * {@link #dialogTheme()} and {@link #applyFloatingWrapContentHeight(Dialog)}.
 */
public final class EditorDialogChrome {

	private static final float WIDTH_FRACTION = 0.96f;
	private static final float HEIGHT_FRACTION = 0.94f;

	private EditorDialogChrome() {
	}

	/** Theme for editors that should float over a dimmed game window. */
	public static int dialogTheme() {
		return R.style.BlowTorch_Dialog;
	}

	/**
	 * Theme for Alias / Trigger / Timer editors: opaque full-screen Family A,
	 * {@code windowIsFloating=false}, same shell as the TRIGGERS list.
	 */
	public static int fullScreenTheme() {
		return R.style.BlowTorch_Dialog_FullScreen;
	}

	/**
	 * Stretch the dialog to MATCH_PARENT on both axes so the list underneath
	 * is fully covered. Call after {@link Dialog#setContentView}.
	 */
	public static void applyFullScreen(Dialog dialog) {
		if (dialog == null) {
			return;
		}
		Window window = dialog.getWindow();
		if (window == null) {
			return;
		}
		Context host = unwrap(dialog.getContext());
		if (host instanceof MainWindow && ((MainWindow) host).isStatusBarHidden()) {
			window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT);
		WindowManager.LayoutParams attrs = window.getAttributes();
		attrs.width = ViewGroup.LayoutParams.MATCH_PARENT;
		attrs.height = ViewGroup.LayoutParams.MATCH_PARENT;
		attrs.gravity = Gravity.FILL;
		window.setAttributes(attrs);
		stretchContentToFill(dialog, window);
		applySystemBarInsets(dialog);
	}

	/**
	 * Size the dialog nearly full-screen and stretch the content root to fill it.
	 * Call after {@link Dialog#setContentView}.
	 */
	public static void applyNearlyFullScreen(Dialog dialog) {
		if (dialog == null) {
			return;
		}
		Window window = dialog.getWindow();
		if (window == null) {
			return;
		}
		Context context = dialog.getContext();
		DisplayMetrics metrics = context.getResources().getDisplayMetrics();
		int width = Math.max(1, (int) (metrics.widthPixels * WIDTH_FRACTION));
		int height = Math.max(1, (int) (metrics.heightPixels * HEIGHT_FRACTION));

		applyFloatingFrame(window, width, height);
		stretchContentToFill(dialog, window);
	}

	/**
	 * Same floating width / dim as {@link #applyNearlyFullScreen}, but height
	 * wraps the form (capped at {@link #HEIGHT_FRACTION}). Short editors (Sensor
	 * Test, {@link EditorHelp}) keep Cancel/Done under the fields; if content
	 * exceeds the cap the middle {@link ScrollView} scrolls and the button bar
	 * stays visible.
	 * Call after {@link Dialog#setContentView}.
	 * <p>
	 * Expects a vertical {@link LinearLayout} shell with a direct
	 * {@link ScrollView} child (weight 1) between title and button bar.
	 */
	public static void applyFloatingWrapContentHeight(Dialog dialog) {
		if (dialog == null) {
			return;
		}
		Window window = dialog.getWindow();
		if (window == null) {
			return;
		}
		Context context = dialog.getContext();
		DisplayMetrics metrics = context.getResources().getDisplayMetrics();
		int width = Math.max(1, (int) (metrics.widthPixels * WIDTH_FRACTION));
		int maxHeight = Math.max(1, (int) (metrics.heightPixels * HEIGHT_FRACTION));

		View shell = contentShell(dialog);
		ScrollView scroll = findDirectScrollView(shell);
		int desired = measureNaturalHeight(shell, scroll, width);
		int height = Math.min(Math.max(desired, 1), maxHeight);

		applyFloatingFrame(window, width, height);
		stretchContentToFill(dialog, window);
		ensureScrollWeight(scroll);
	}

	private static Context unwrap(Context context) {
		Context c = context;
		while (c instanceof ContextWrapper) {
			if (c instanceof MainWindow) {
				return c;
			}
			Context next = ((ContextWrapper) c).getBaseContext();
			if (next == c) {
				break;
			}
			c = next;
		}
		return context;
	}

	private static void applySystemBarInsets(Dialog dialog) {
		View shell = contentShell(dialog);
		if (shell == null) {
			return;
		}
		androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(shell, (view, insets) -> {
			androidx.core.graphics.Insets sys =
					insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
							| androidx.core.view.WindowInsetsCompat.Type.ime());
			view.setPadding(view.getPaddingLeft(), sys.top,
					view.getPaddingRight(), sys.bottom);
			return insets;
		});
		androidx.core.view.ViewCompat.requestApplyInsets(shell);
	}

	private static View contentShell(Dialog dialog) {
		View contentRoot = dialog.findViewById(android.R.id.content);
		if (contentRoot instanceof ViewGroup && ((ViewGroup) contentRoot).getChildCount() > 0) {
			return ((ViewGroup) contentRoot).getChildAt(0);
		}
		return null;
	}

	private static ScrollView findDirectScrollView(View shell) {
		if (!(shell instanceof ViewGroup)) {
			return null;
		}
		ViewGroup group = (ViewGroup) shell;
		for (int i = 0; i < group.getChildCount(); i++) {
			View child = group.getChildAt(i);
			if (child instanceof ScrollView) {
				return (ScrollView) child;
			}
		}
		return null;
	}

	/**
	 * Measure shell height as if the ScrollView wrapped its children (no weight
	 * stretch), so a short form yields a short dialog.
	 */
	private static int measureNaturalHeight(View shell, ScrollView scroll, int width) {
		if (shell == null) {
			return 1;
		}
		LinearLayout.LayoutParams scrollLp = null;
		int savedHeight = 0;
		float savedWeight = 0f;
		if (scroll != null && scroll.getLayoutParams() instanceof LinearLayout.LayoutParams) {
			scrollLp = (LinearLayout.LayoutParams) scroll.getLayoutParams();
			savedHeight = scrollLp.height;
			savedWeight = scrollLp.weight;
			scrollLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
			scrollLp.weight = 0f;
			scroll.setLayoutParams(scrollLp);
		}

		shell.measure(
				View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
		int desired = shell.getMeasuredHeight();

		if (scrollLp != null) {
			scrollLp.height = savedHeight;
			scrollLp.weight = savedWeight;
			scroll.setLayoutParams(scrollLp);
		}
		return desired;
	}

	private static void ensureScrollWeight(ScrollView scroll) {
		if (scroll == null || !(scroll.getLayoutParams() instanceof LinearLayout.LayoutParams)) {
			return;
		}
		LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) scroll.getLayoutParams();
		lp.height = 0;
		lp.weight = 1f;
		scroll.setLayoutParams(lp);
	}

	private static void applyFloatingFrame(Window window, int width, int height) {
		window.setLayout(width, height);
		WindowManager.LayoutParams attrs = window.getAttributes();
		attrs.width = width;
		attrs.height = height;
		attrs.gravity = Gravity.CENTER;
		attrs.dimAmount = 0.55f;
		window.setAttributes(attrs);
		window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
	}

	private static void stretchContentToFill(Dialog dialog, Window window) {
		View root = window.getDecorView();
		if (root instanceof ViewGroup) {
			View content = ((ViewGroup) root).getChildAt(0);
			if (content != null) {
				ViewGroup.LayoutParams lp = content.getLayoutParams();
				if (lp != null) {
					lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
					lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
					content.setLayoutParams(lp);
				}
			}
		}

		View contentRoot = dialog.findViewById(android.R.id.content);
		if (contentRoot instanceof ViewGroup && ((ViewGroup) contentRoot).getChildCount() > 0) {
			View child = ((ViewGroup) contentRoot).getChildAt(0);
			ViewGroup.LayoutParams lp = child.getLayoutParams();
			if (lp != null) {
				lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
				lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
				child.setLayoutParams(lp);
			} else {
				child.setLayoutParams(new ViewGroup.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT,
						ViewGroup.LayoutParams.MATCH_PARENT));
			}
		}
	}
}

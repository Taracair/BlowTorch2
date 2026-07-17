package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.R;

import android.app.Dialog;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

/**
 * Shared chrome for Alias / Trigger / Timer editors: nearly full-screen while
 * keeping a floating dialog theme (dimmed backdrop visible around the edges).
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

		window.setLayout(width, height);
		WindowManager.LayoutParams attrs = window.getAttributes();
		attrs.width = width;
		attrs.height = height;
		attrs.gravity = Gravity.CENTER;
		attrs.dimAmount = 0.55f;
		window.setAttributes(attrs);
		window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

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

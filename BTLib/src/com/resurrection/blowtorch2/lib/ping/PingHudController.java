package com.resurrection.blowtorch2.lib.ping;

import com.resurrection.blowtorch2.lib.service.function.PingCommand;
import com.resurrection.blowtorch2.lib.window.MainWindow;

import android.view.View;
import android.widget.RelativeLayout;

/**
 * One ping chip on {@code window_container}, under ⋮. Tag
 * {@link #LAYER_TAG} so IME lift pins it (top of the game, not under the
 * keyboard).
 */
public final class PingHudController {

	public static final String LAYER_TAG = "ping_hud";

	public interface Host {
		MainWindow getMainWindow();

		void bringViewUnderChrome(View overlay);

		boolean updateMainWindowInteger(String key, int value);
	}

	private final Host host;
	private PingHudView view;
	private int lastRtt = PingProbe.NO_SAMPLE;

	public PingHudController(final Host host) {
		this.host = host;
	}

	public void attach() {
		MainWindow activity = host.getMainWindow();
		if (activity == null || activity.isFinishing()) {
			return;
		}
		RelativeLayout rl = (RelativeLayout) activity.findViewById(
				com.resurrection.blowtorch2.lib.R.id.window_container);
		if (rl == null) {
			return;
		}
		if (view == null) {
			view = new PingHudView(activity);
			view.setTag(LAYER_TAG);
			view.setCallbacks(new PingHudView.Callbacks() {
				@Override
				public void onMoveFinished(final int leftPx, final int topPx) {
					persistPos(leftPx, topPx);
				}
			});
			RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
					RelativeLayout.LayoutParams.WRAP_CONTENT,
					RelativeLayout.LayoutParams.WRAP_CONTENT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
			rl.addView(view, lp);
			host.bringViewUnderChrome(view);
			applyFromWindow();
		}
		view.setRtt(lastRtt);
	}

	public void detach() {
		if (view == null) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		if (activity != null) {
			RelativeLayout rl = (RelativeLayout) activity.findViewById(
					com.resurrection.blowtorch2.lib.R.id.window_container);
			if (rl != null) {
				rl.removeView(view);
			}
		}
		view = null;
	}

	public void applyFromWindow() {
		if (view == null) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		boolean on = activity.pingHudEnabled();
		view.setVisibility(on ? View.VISIBLE : View.GONE);
		if (!on) {
			return;
		}
		view.setSizeSp(activity.pingHudSizeSp());
		view.setAlpha(PingHudLayout.alphaFromOpacity(activity.pingHudOpacity()));
		view.post(new Runnable() {
			@Override
			public void run() {
				placeFromPercents();
			}
		});
	}

	public void setRtt(final int rttMs) {
		lastRtt = rttMs;
		if (view != null) {
			view.setRtt(rttMs);
			if (view.getVisibility() == View.VISIBLE && !view.isDragging()) {
				view.post(new Runnable() {
					@Override
					public void run() {
						if (view != null && !view.isDragging()) {
							placeFromPercents();
						}
					}
				});
			}
		}
	}

	private void placeFromPercents() {
		if (view == null || view.getParent() == null) {
			return;
		}
		View parent = (View) view.getParent();
		int vw = view.getWidth();
		int vh = view.getHeight();
		if (vw <= 0 || vh <= 0) {
			view.measure(0, 0);
			vw = view.getMeasuredWidth();
			vh = view.getMeasuredHeight();
		}
		MainWindow activity = host.getMainWindow();
		int x = PingHudLayout.pxFromPercent(activity.pingHudX(), parent.getWidth(), vw);
		int y = PingHudLayout.pxFromPercent(activity.pingHudY(), parent.getHeight(), vh);
		RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) view.getLayoutParams();
		lp.leftMargin = x;
		lp.topMargin = y;
		view.setLayoutParams(lp);
		view.setTranslationX(0f);
		view.setTranslationY(0f);
	}

	private void persistPos(final int leftPx, final int topPx) {
		if (view == null || view.getParent() == null) {
			return;
		}
		View parent = (View) view.getParent();
		int x = PingHudLayout.percentFromPx(leftPx, parent.getWidth(), view.getWidth());
		int y = PingHudLayout.percentFromPx(topPx, parent.getHeight(), view.getHeight());
		host.updateMainWindowInteger(PingCommand.OPTION_X, x);
		host.updateMainWindowInteger(PingCommand.OPTION_Y, y);
		RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) view.getLayoutParams();
		lp.leftMargin = leftPx;
		lp.topMargin = topPx;
		view.setLayoutParams(lp);
		view.setTranslationX(0f);
		view.setTranslationY(0f);
	}
}

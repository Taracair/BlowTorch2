package com.resurrection.blowtorch2.lib.window;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Grid-button holes in button_window pixels. Mapped to the game window in
 * {@link FloatingButtonController#collectWindowLocalRects}.
 */
public final class GridObstacleRects {

	private GridObstacleRects() {
	}

	public static void addFromJson(final JSONArray arr,
			final List<ButtonTextFlow.RectPx> dest) {
		addFromJson(arr, dest, null);
	}

	/**
	 * @param forced rects whose button asked to be a hole on its own
	 *               ({@code force:true}). May be null.
	 */
	public static void addFromJson(final JSONArray arr,
			final List<ButtonTextFlow.RectPx> dest,
			final List<ButtonTextFlow.RectPx> forced) {
		if (arr == null || dest == null) {
			return;
		}
		for (int i = 0; i < arr.length(); i++) {
			JSONObject o = arr.optJSONObject(i);
			if (o == null) {
				continue;
			}
			int l = o.optInt("l", 0);
			int t = o.optInt("t", 0);
			int r = o.optInt("r", 0);
			int b = o.optInt("b", 0);
			if (r <= l || b <= t) {
				continue;
			}
			ButtonTextFlow.RectPx rect = new ButtonTextFlow.RectPx(l, t, r, b);
			dest.add(rect);
			if (forced != null && o.optBoolean("force", false)) {
				forced.add(rect);
			}
		}
	}
}

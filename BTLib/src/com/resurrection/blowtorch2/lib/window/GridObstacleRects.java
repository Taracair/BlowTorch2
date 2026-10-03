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
			dest.add(new ButtonTextFlow.RectPx(l, t, r, b));
		}
	}
}

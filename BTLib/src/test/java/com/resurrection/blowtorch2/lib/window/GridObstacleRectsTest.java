package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class GridObstacleRectsTest {

	@Test
	public void addsFiniteRectsAndSkipsEmpty() throws Exception {
		JSONArray arr = new JSONArray();
		JSONObject ok = new JSONObject();
		ok.put("l", 10);
		ok.put("t", 20);
		ok.put("r", 110);
		ok.put("b", 80);
		arr.put(ok);
		JSONObject empty = new JSONObject();
		empty.put("l", 0);
		empty.put("t", 0);
		empty.put("r", 0);
		empty.put("b", 10);
		arr.put(empty);
		List<ButtonTextFlow.RectPx> dest = new ArrayList<ButtonTextFlow.RectPx>();
		GridObstacleRects.addFromJson(arr, dest);
		assertEquals(1, dest.size());
		assertEquals(10, dest.get(0).left);
		assertEquals(20, dest.get(0).top);
		assertEquals(110, dest.get(0).right);
		assertEquals(80, dest.get(0).bottom);
	}

	@Test
	public void nullArrayIsNoOp() {
		List<ButtonTextFlow.RectPx> dest = new ArrayList<ButtonTextFlow.RectPx>();
		GridObstacleRects.addFromJson(null, dest);
		assertEquals(0, dest.size());
	}
}

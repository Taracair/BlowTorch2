package com.resurrection.blowtorch2.lib.trigger.style;

import java.util.IdentityHashMap;
import java.util.List;

import com.resurrection.blowtorch2.lib.window.TextTree;

/**
 * Style models for the lines in the current dispatch call. Cleared before
 * that call returns. Not a cache the other process has to see.
 */
public final class DispatchStyles {

	private static final ThreadLocal<IdentityHashMap<TextTree.Line, StyleLineModel>> SLOT =
			new ThreadLocal<IdentityHashMap<TextTree.Line, StyleLineModel>>();

	private DispatchStyles() {
	}

	public static void bind(List<TextTree.Line> lines, StyleLineModel[] models) {
		if (lines == null || models == null) {
			SLOT.remove();
			return;
		}
		IdentityHashMap<TextTree.Line, StyleLineModel> map =
				new IdentityHashMap<TextTree.Line, StyleLineModel>();
		int n = lines.size();
		if (n > models.length) {
			n = models.length;
		}
		for (int i = 0; i < n; i++) {
			TextTree.Line line = lines.get(i);
			if (line != null && models[i] != null) {
				map.put(line, models[i]);
			}
		}
		SLOT.set(map);
	}

	public static StyleLineModel forLine(TextTree.Line line) {
		if (line == null) {
			return null;
		}
		IdentityHashMap<TextTree.Line, StyleLineModel> map = SLOT.get();
		if (map == null) {
			return null;
		}
		return map.get(line);
	}

	public static void clear() {
		SLOT.remove();
	}
}

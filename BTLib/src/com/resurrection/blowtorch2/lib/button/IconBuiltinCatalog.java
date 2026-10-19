package com.resurrection.blowtorch2.lib.button;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.content.res.AssetManager;

/** Built-in button pictures under {@code assets/buttonfaces/<set>/<id>.png}. */
public final class IconBuiltinCatalog {

	private IconBuiltinCatalog() {
	}

	public static Map<String, List<String>> load(final AssetManager assets) {
		Map<String, List<String>> out = new LinkedHashMap<String, List<String>>();
		if (assets == null) {
			return out;
		}
		String[] sets;
		try {
			sets = assets.list("buttonfaces");
		} catch (IOException e) {
			return out;
		}
		if (sets == null) {
			return out;
		}
		Arrays.sort(sets);
		for (int i = 0; i < sets.length; i++) {
			String set = sets[i] == null ? "" : sets[i].toLowerCase(Locale.US);
			if (!token(set)) {
				continue;
			}
			String[] files;
			try {
				files = assets.list("buttonfaces/" + set);
			} catch (IOException e) {
				continue;
			}
			if (files == null || files.length == 0) {
				continue;
			}
			List<String> ids = new ArrayList<String>();
			for (int f = 0; f < files.length; f++) {
				String id = idOf(files[f]);
				if (id != null && !ids.contains(id)) {
					ids.add(id);
				}
			}
			if (ids.isEmpty()) {
				continue;
			}
			Collections.sort(ids);
			out.put(set, ids);
		}
		return out;
	}

	private static String idOf(final String file) {
		if (file == null) {
			return null;
		}
		String lower = file.toLowerCase(Locale.US);
		String stem;
		if (lower.endsWith(".png") || lower.endsWith(".svg")) {
			stem = lower.substring(0, lower.length() - 4);
		} else {
			return null;
		}
		if (!token(stem)) {
			return null;
		}
		return stem;
	}

	private static boolean token(final String s) {
		if (s == null || s.length() == 0) {
			return false;
		}
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
			if (!ok) {
				return false;
			}
		}
		return true;
	}
}

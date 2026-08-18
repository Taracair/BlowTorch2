/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.gauge;

import java.util.List;
import java.util.Locale;

/**
 * Ensure / hide an overlay gauge bound to a client {@code .timer}. No Android.
 * Ids go through {@link GaugeWidgetsStore#normalizeName}; invalid timer names
 * are derived into {@code [a-z0-9_]{1,24}}. Unchecking prefers hide, not
 * delete, so a widget the player also edited in Manage widgets is not lost.
 */
public final class GaugeTimerWidgetBind {

	private GaugeTimerWidgetBind() {
	}

	/**
	 * Widget id derived from a timer name. Null only when nothing safe can be
	 * minted (null/blank after stripping).
	 */
	public static String widgetIdFromTimerName(final String timerName) {
		if (timerName == null) {
			return null;
		}
		String normalized = GaugeWidgetsStore.normalizeName(timerName);
		if (normalized != null) {
			return normalized;
		}
		StringBuilder sb = new StringBuilder();
		String s = timerName.trim().toLowerCase(Locale.US);
		for (int i = 0; i < s.length() && sb.length() < 24; i++) {
			char c = s.charAt(i);
			boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
			if (ok) {
				sb.append(c);
			} else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') {
				sb.append('_');
			}
		}
		while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '_') {
			sb.setLength(sb.length() - 1);
		}
		if (sb.length() == 0) {
			sb.append("timer");
		}
		normalized = GaugeWidgetsStore.normalizeName(sb.toString());
		if (normalized != null) {
			return normalized;
		}
		String prefixed = "t_" + sb.toString();
		if (prefixed.length() > 24) {
			prefixed = prefixed.substring(0, 24);
		}
		return GaugeWidgetsStore.normalizeName(prefixed);
	}

	/** Visible widget bound to this timer name, if any. */
	public static boolean isShowing(final List<GaugeWidget> list, final String timerName) {
		GaugeWidget g = findBound(list, timerName);
		return g != null && g.isVisible();
	}

	/**
	 * Make a timer-shaped, timer-sourced widget visible (create if needed).
	 *
	 * @return the widget, or null if the list is full / no id can be minted
	 */
	public static GaugeWidget ensure(final List<GaugeWidget> list, final String timerName) {
		if (list == null || timerName == null || timerName.trim().length() == 0) {
			return null;
		}
		String name = timerName.trim();
		GaugeWidget existing = findBound(list, name);
		if (existing != null) {
			existing.setVisible(true);
			existing.setSource(GaugeWidget.Source.TIMER);
			existing.setPath(name);
			existing.setTimerName(name);
			return existing;
		}
		if (list.size() >= GaugeWidgetsStore.MAX) {
			return null;
		}
		String id = uniquify(list, widgetIdFromTimerName(name));
		if (id == null) {
			return null;
		}
		GaugeWidget g = new GaugeWidget(id);
		g.setLabel(name);
		g.setShape(GaugeWidget.Shape.TIMER);
		g.setSource(GaugeWidget.Source.TIMER);
		g.setPath(name);
		g.setTimerName(name);
		g.setX(GaugeSpawnPlacement.UNPLACED);
		g.setY(GaugeSpawnPlacement.UNPLACED);
		list.add(g);
		return g;
	}

	/**
	 * Move an existing timer bind to a new {@code .timer} name, or mint one.
	 * If a widget is already bound to {@code newName}, that one is shown and
	 * the old bind is hidden — do not leave two gauges on the same timer.
	 */
	public static GaugeWidget rebind(final List<GaugeWidget> list,
			final String oldName, final String newName) {
		if (list == null || newName == null || newName.trim().length() == 0) {
			return null;
		}
		String neu = newName.trim();
		GaugeWidget already = findBound(list, neu);
		GaugeWidget old = oldName != null && oldName.trim().length() > 0
				? findBound(list, oldName.trim()) : null;
		if (already != null && already != old) {
			if (old != null) {
				old.setVisible(false);
			}
			already.setVisible(true);
			already.setSource(GaugeWidget.Source.TIMER);
			already.setPath(neu);
			already.setTimerName(neu);
			return already;
		}
		if (old != null) {
			old.setVisible(true);
			old.setSource(GaugeWidget.Source.TIMER);
			old.setPath(neu);
			old.setTimerName(neu);
			return old;
		}
		return ensure(list, neu);
	}

	/**
	 * Hide the widget bound to this timer. Does not delete it.
	 *
	 * @return true if a widget was hidden
	 */
	public static boolean hide(final List<GaugeWidget> list, final String timerName) {
		GaugeWidget g = findBound(list, timerName);
		if (g == null || !g.isVisible()) {
			return false;
		}
		g.setVisible(false);
		return true;
	}

	static GaugeWidget findBound(final List<GaugeWidget> list, final String timerName) {
		if (list == null || timerName == null) {
			return null;
		}
		String name = timerName.trim();
		if (name.length() == 0) {
			return null;
		}
		String id = widgetIdFromTimerName(name);
		for (int i = 0; i < list.size(); i++) {
			GaugeWidget g = list.get(i);
			if (g == null) {
				continue;
			}
			if (g.getSource() == GaugeWidget.Source.TIMER
					&& name.equalsIgnoreCase(g.resolveTimerName())) {
				return g;
			}
		}
		if (id != null) {
			GaugeWidget byId = GaugeWidgetsStore.find(list, id);
			if (byId != null && byId.getSource() == GaugeWidget.Source.TIMER) {
				String bound = byId.resolveTimerName();
				if (bound.length() == 0 || name.equalsIgnoreCase(bound)) {
					return byId;
				}
			}
		}
		return null;
	}

	private static String uniquify(final List<GaugeWidget> list, final String base) {
		if (base == null) {
			return null;
		}
		if (GaugeWidgetsStore.find(list, base) == null) {
			return base;
		}
		for (int n = 2; n <= 20; n++) {
			String suffix = "_" + n;
			String id;
			if (base.length() + suffix.length() <= 24) {
				id = base + suffix;
			} else {
				id = base.substring(0, 24 - suffix.length()) + suffix;
			}
			if (GaugeWidgetsStore.normalizeName(id) != null
					&& GaugeWidgetsStore.find(list, id) == null) {
				return id;
			}
		}
		return null;
	}
}

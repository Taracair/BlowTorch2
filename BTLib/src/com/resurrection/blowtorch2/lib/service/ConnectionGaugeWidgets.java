/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.resurrection.blowtorch2.lib.gauge.GaugeBinding;
import com.resurrection.blowtorch2.lib.gauge.GaugeSpawnPlacement;
import com.resurrection.blowtorch2.lib.gauge.GaugeWidget;
import com.resurrection.blowtorch2.lib.gauge.GaugeWidgetsStore;
import com.resurrection.blowtorch2.lib.gauge.WidgetCommandParser;
import com.resurrection.blowtorch2.lib.service.plugin.Plugin;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption;
import com.resurrection.blowtorch2.lib.timer.TimerData;

/**
 * Service-process store for overlay gauges. Canonical list lives here; the UI
 * is pushed config via {@code gaugeWidgetUi} and live values via
 * {@code gaugeWidgetValues}. Live amounts are session memory and are never
 * written by {@link #persist()}.
 */
final class ConnectionGaugeWidgets {

	private static final long TICK_MS = 250L;

	private final Connection host;
	private final Object lock = new Object();
	private final ArrayList<GaugeWidget> list = new ArrayList<GaugeWidget>();
	/** elapsedRealtime deadline for MANUAL {@link GaugeWidget.Shape#TIMER} widgets. */
	private final HashMap<String, Long> deadlines = new HashMap<String, Long>();

	private boolean ticking;

	private final Runnable pushRunnable = new Runnable() {
		@Override
		public void run() {
			if (host != null) {
				host.requestGaugeWidgetValues();
			}
		}
	};

	private final Runnable tickRunnable = new Runnable() {
		@Override
		public void run() {
			onTimerTick();
			Handler h = host != null ? host.mHandler : null;
			if (ticking && h != null) {
				h.postDelayed(this, TICK_MS);
			}
		}
	};

	ConnectionGaugeWidgets(final Connection host) {
		this.host = host;
	}

	boolean isEnabled() {
		if (host == null || host.mSettings == null
				|| host.mSettings.getSettings() == null
				|| host.mSettings.getSettings().getOptions() == null) {
			return true;
		}
		try {
			Object o = host.mSettings.getSettings().getOptions()
					.findOptionByKey(GaugeWidgetsStore.ENABLED_KEY);
			if (o instanceof BooleanOption) {
				Object val = ((BooleanOption) o).getValue();
				if (val instanceof Boolean) {
					return ((Boolean) val).booleanValue();
				}
			}
		} catch (Exception ignored) {
		}
		return true;
	}

	/**
	 * Config JSON for the UI. Empty when overlays are disabled so the UI has
	 * nothing to draw (there is no separate enabled getter on the binder).
	 */
	String toPersistedJson() {
		synchronized (lock) {
			if (!isEnabled()) {
				return "[]";
			}
			return GaugeWidgetsStore.toJson(new ArrayList<GaugeWidget>(list));
		}
	}

	/** Compact live amounts {@code [{"id","v","m"}]}. Works while disabled. */
	String toValuesJson() {
		JSONArray arr = new JSONArray();
		synchronized (lock) {
			for (int i = 0; i < list.size(); i++) {
				GaugeWidget g = list.get(i);
				if (g == null) {
					continue;
				}
				try {
					JSONObject o = new JSONObject();
					o.put("id", g.getId());
					if (g.getShape() == GaugeWidget.Shape.TIMER) {
						o.put("v", g.getRemainSec());
						o.put("m", g.getDurationSec());
					} else {
						o.put("v", g.getLiveValue());
						o.put("m", g.getLiveMax());
					}
					arr.put(o);
				} catch (Exception e) {
					Log.w("BlowTorch", "gauge values json", e);
				}
			}
		}
		return arr.toString();
	}

	void reloadFromSettings() {
		synchronized (lock) {
			reloadFromSettingsLocked();
		}
	}

	private void reloadFromSettingsLocked() {
		HashMap<String, LiveSnap> saved = new HashMap<String, LiveSnap>();
		for (int i = 0; i < list.size(); i++) {
			GaugeWidget g = list.get(i);
			if (g == null || g.getId() == null || g.getId().length() == 0) {
				continue;
			}
			LiveSnap snap = new LiveSnap();
			snap.v = g.getLiveValue();
			snap.m = g.getLiveMax();
			snap.remain = g.getRemainSec();
			snap.duration = g.getDurationSec();
			Long dl = deadlines.get(g.getId());
			snap.deadline = dl != null ? dl.longValue() : 0L;
			saved.put(g.getId(), snap);
		}
		String json = "[]";
		if (host != null && host.mSettings != null
				&& host.mSettings.getSettings() != null
				&& host.mSettings.getSettings().getOptions() != null) {
			try {
				Object o = host.mSettings.getSettings().getOptions()
						.findOptionByKey(GaugeWidgetsStore.SETTING_KEY);
				if (o instanceof StringOption) {
					Object val = ((StringOption) o).getValue();
					if (val != null) {
						json = val.toString();
					}
				}
			} catch (Exception e) {
				Log.w("BlowTorch", "reloadGaugeWidgetsFromSettings failed", e);
			}
		}
		ArrayList<GaugeWidget> next = GaugeWidgetsStore.parse(json);
		list.clear();
		deadlines.clear();
		for (int i = 0; i < next.size(); i++) {
			GaugeWidget g = next.get(i);
			if (g == null) {
				continue;
			}
			LiveSnap snap = saved.get(g.getId());
			if (snap != null) {
				g.setLiveValue(snap.v);
				g.setLiveMax(snap.m);
				g.setRemainSec(snap.remain);
				if (g.getDurationSec() <= 0.0 && snap.duration > 0.0) {
					g.setDurationSec(snap.duration);
				}
				if (snap.deadline > 0L) {
					deadlines.put(g.getId(), Long.valueOf(snap.deadline));
				}
			}
			list.add(g);
		}
		syncTimerLoop();
	}

	void persist() {
		synchronized (lock) {
			persistLocked();
		}
	}

	private void persistLocked() {
		if (host == null || host.mSettings == null
				|| host.mSettings.getSettings() == null
				|| host.mSettings.getSettings().getOptions() == null) {
			return;
		}
		GaugeWidgetsStore.validate(list);
		String json = GaugeWidgetsStore.toJson(list);
		host.mSettings.getSettings().getOptions().setOption(
				GaugeWidgetsStore.SETTING_KEY, json);
		host.requestSettingsSave();
	}

	/**
	 * Apply a parsed {@code .widget} mutation. List/help are handled by
	 * {@link com.resurrection.blowtorch2.lib.service.function.WidgetCommand}.
	 * On failure sets {@link WidgetCommandParser.Result#error} and returns null.
	 *
	 * @return a status line for the game window, or null when {@code r.error} is set
	 */
	String apply(final WidgetCommandParser.Result r) {
		synchronized (lock) {
			return applyLocked(r);
		}
	}

	private String applyLocked(final WidgetCommandParser.Result r) {
		if (r == null || r.action == null) {
			return null;
		}
		if (WidgetCommandParser.ACTION_SET.equals(r.action)) {
			return applySet(r);
		}
		if (WidgetCommandParser.ACTION_ADD.equals(r.action)) {
			return applyAdd(r);
		}
		if (WidgetCommandParser.ACTION_REMOVE.equals(r.action)) {
			return applyRemove(r);
		}
		GaugeWidget g = requireWidget(r);
		if (g == null) {
			return null;
		}
		if (WidgetCommandParser.ACTION_SHOW.equals(r.action)
				|| WidgetCommandParser.ACTION_HIDE.equals(r.action)) {
			boolean vis = r.flag != null && r.flag.booleanValue();
			g.setVisible(vis);
			afterConfig();
			return vis ? "Widget " + g.getId() + " shown."
					: "Widget " + g.getId() + " hidden.";
		}
		if (WidgetCommandParser.ACTION_SHAPE.equals(r.action)) {
			g.setShape(GaugeWidget.Shape.fromJsonValue(r.shape));
			afterConfig();
			return "Widget " + g.getId() + " shape " + g.getShape().toJsonValue() + ".";
		}
		if (WidgetCommandParser.ACTION_COLOR.equals(r.action)) {
			g.setColorFill(GaugeWidget.parseColor(r.color, g.getColorFill()));
			afterConfig();
			return "Widget " + g.getId() + " colour "
					+ GaugeWidget.formatColor(g.getColorFill()) + ".";
		}
		if (WidgetCommandParser.ACTION_TRACK.equals(r.action)) {
			g.setColorTrack(GaugeWidget.parseColor(r.color, g.getColorTrack()));
			afterConfig();
			return "Widget " + g.getId() + " track "
					+ GaugeWidget.formatColor(g.getColorTrack()) + ".";
		}
		if (WidgetCommandParser.ACTION_OPACITY.equals(r.action)) {
			if (r.opacity != null) {
				g.setOpacity(r.opacity.intValue());
			}
			afterConfig();
			return "Widget " + g.getId() + " opacity " + g.getOpacity() + "%.";
		}
		if (WidgetCommandParser.ACTION_SIZE.equals(r.action)) {
			if (r.w != null) {
				g.setW(r.w.intValue());
			}
			if (r.h != null) {
				g.setH(r.h.intValue());
			}
			afterConfig();
			return "Widget " + g.getId() + " size " + g.getW() + "x" + g.getH() + ".";
		}
		if (WidgetCommandParser.ACTION_MOVE.equals(r.action)) {
			if (r.x != null) {
				g.setX(r.x.intValue());
			}
			if (r.y != null) {
				g.setY(r.y.intValue());
			}
			afterConfig();
			return "Widget " + g.getId() + " moved to " + g.getX() + "," + g.getY() + ".";
		}
		if (WidgetCommandParser.ACTION_LABEL.equals(r.action)) {
			g.setLabel(r.text != null ? r.text : "");
			afterConfig();
			if (g.getLabel().length() == 0) {
				return "Widget " + g.getId() + " label cleared.";
			}
			return "Widget " + g.getId() + " label " + g.getLabel() + ".";
		}
		if (WidgetCommandParser.ACTION_VALUE.equals(r.action)) {
			boolean on = r.flag != null && r.flag.booleanValue();
			g.setShowValue(on);
			afterConfig();
			return "Widget " + g.getId() + " value "
					+ (on ? "on" : "off") + ".";
		}
		if (WidgetCommandParser.ACTION_CAPTION.equals(r.action)) {
			boolean on = r.flag != null && r.flag.booleanValue();
			g.setShowLabel(on);
			afterConfig();
			return "Widget " + g.getId() + " caption "
					+ (on ? "on" : "off") + ".";
		}
		if (WidgetCommandParser.ACTION_SOURCE.equals(r.action)) {
			return applySource(r, g);
		}
		if (WidgetCommandParser.ACTION_TAP.equals(r.action)) {
			g.setTapCommand(r.text != null ? r.text : "");
			afterConfig();
			return commandSetLine(g.getId(), "tap", g.getTapCommand());
		}
		if (WidgetCommandParser.ACTION_HOLD.equals(r.action)) {
			g.setHoldCommand(r.text != null ? r.text : "");
			afterConfig();
			return commandSetLine(g.getId(), "hold", g.getHoldCommand());
		}
		if (WidgetCommandParser.ACTION_SWIPE.equals(r.action)) {
			return applySwipe(r, g);
		}
		if (WidgetCommandParser.ACTION_WARN.equals(r.action)) {
			return applyWarn(r, g);
		}
		if (WidgetCommandParser.ACTION_IME.equals(r.action)) {
			g.setImeMode(GaugeWidget.ImeMode.fromJsonValue(r.ime));
			afterConfig();
			return "Widget " + g.getId() + " ime " + g.getImeMode().toJsonValue() + ".";
		}
		r.error = "Unknown subcommand.";
		return null;
	}

	String listDump() {
		synchronized (lock) {
			return listDumpLocked();
		}
	}

	private String listDumpLocked() {
		StringBuilder sb = new StringBuilder();
		if (list.isEmpty()) {
			sb.append("  (none — use .widget add <id> [shape])\n");
			return sb.toString();
		}
		for (int i = 0; i < list.size(); i++) {
			GaugeWidget g = list.get(i);
			if (g == null) {
				continue;
			}
			String path = g.getPath();
			if (path == null || path.length() == 0) {
				path = "-";
			}
			double v;
			double m;
			if (g.getShape() == GaugeWidget.Shape.TIMER) {
				v = g.getRemainSec();
				m = g.getDurationSec();
			} else {
				v = g.getLiveValue();
				m = g.getLiveMax();
			}
			sb.append("  ").append(g.getId()).append(' ')
					.append(g.getShape().toJsonValue()).append(' ')
					.append(g.getSource().toJsonValue()).append(' ')
					.append(path).append(' ')
					.append(formatAmount(v)).append('/').append(formatAmount(m))
					.append(' ')
					.append(g.isVisible() ? "visible" : "hidden")
					.append('\n');
		}
		return sb.toString();
	}

	void onGmcp(final String module, final String bodyJson) {
		synchronized (lock) {
			onGmcpLocked(module, bodyJson);
		}
	}

	private void onGmcpLocked(final String module, final String bodyJson) {
		boolean dirty = false;
		for (int i = 0; i < list.size(); i++) {
			GaugeWidget g = list.get(i);
			if (g == null || g.getSource() != GaugeWidget.Source.GMCP) {
				continue;
			}
			Double v = GaugeBinding.numberFromGmcpJson(module, bodyJson, g.getPath());
			if (v != null && v.doubleValue() != g.getLiveValue()) {
				g.setLiveValue(v.doubleValue());
				dirty = true;
			}
			if (g.getMaxPath() != null && g.getMaxPath().length() > 0) {
				Double max = GaugeBinding.numberFromGmcpJson(module, bodyJson,
						g.getMaxPath());
				if (max != null && max.doubleValue() != g.getLiveMax()) {
					g.setLiveMax(max.doubleValue());
					dirty = true;
				}
			}
		}
		if (dirty) {
			schedulePush();
		}
	}

	void onSessionVar(final String name, final String value) {
		synchronized (lock) {
			onSessionVarLocked(name, value);
		}
	}

	private void onSessionVarLocked(final String name, final String value) {
		if (name == null || name.length() == 0) {
			return;
		}
		boolean dirty = false;
		for (int i = 0; i < list.size(); i++) {
			GaugeWidget g = list.get(i);
			if (g == null || g.getSource() != GaugeWidget.Source.VAR) {
				continue;
			}
			if (pathEquals(g.getPath(), name)) {
				dirty |= applyVarNumber(g, value, false);
			}
			if (pathEquals(g.getMaxPath(), name)) {
				dirty |= applyVarNumber(g, value, true);
			}
		}
		if (dirty) {
			schedulePush();
		}
	}

	void onMcpStatus() {
		synchronized (lock) {
			onMcpStatusLocked();
		}
	}

	private void onMcpStatusLocked() {
		if (host == null || !hasSource(GaugeWidget.Source.MCP)) {
			return;
		}
		Map<String, String> cache;
		try {
			cache = host.getMcpEngine().getStatusCache();
		} catch (Exception e) {
			return;
		}
		if (cache == null) {
			return;
		}
		boolean dirty = false;
		for (int i = 0; i < list.size(); i++) {
			GaugeWidget g = list.get(i);
			if (g == null || g.getSource() != GaugeWidget.Source.MCP) {
				continue;
			}
			Double v = numberFromCache(cache, g.getPath());
			if (v != null && v.doubleValue() != g.getLiveValue()) {
				g.setLiveValue(v.doubleValue());
				dirty = true;
			}
			if (g.getMaxPath() != null && g.getMaxPath().length() > 0) {
				Double max = numberFromCache(cache, g.getMaxPath());
				if (max != null && max.doubleValue() != g.getLiveMax()) {
					g.setLiveMax(max.doubleValue());
					dirty = true;
				}
			}
		}
		if (dirty) {
			schedulePush();
		}
	}

	void onTimerTick() {
		synchronized (lock) {
			onTimerTickLocked();
		}
	}

	private void onTimerTickLocked() {
		boolean dirty = false;
		if (hasSource(GaugeWidget.Source.TIMER)) {
			refreshHostTimers();
			for (int i = 0; i < list.size(); i++) {
				GaugeWidget g = list.get(i);
				if (g == null || g.getSource() != GaugeWidget.Source.TIMER) {
					continue;
				}
				TimerData t = findTimer(g.resolveTimerName());
				if (t == null) {
					continue;
				}
				Integer sec = t.getSeconds();
				double duration = sec != null ? sec.doubleValue() : 0.0;
				double remain = t.getRemainingTime();
				if (remain != g.getRemainSec() || duration != g.getDurationSec()
						|| remain != g.getLiveValue() || duration != g.getLiveMax()) {
					g.setRemainSec(remain);
					g.setDurationSec(duration);
					g.setLiveValue(remain);
					g.setLiveMax(duration);
					dirty = true;
				}
			}
		}
		if (hasTimerShape()) {
			long now = SystemClock.elapsedRealtime();
			for (int i = 0; i < list.size(); i++) {
				GaugeWidget g = list.get(i);
				if (g == null || g.getShape() != GaugeWidget.Shape.TIMER) {
					continue;
				}
				if (g.getSource() == GaugeWidget.Source.TIMER) {
					continue;
				}
				Long dl = deadlines.get(g.getId());
				if (dl == null || dl.longValue() <= 0L) {
					continue;
				}
				double remain = (dl.longValue() - now) / 1000.0;
				if (remain < 0.0) {
					remain = 0.0;
				}
				if (remain != g.getRemainSec() || remain != g.getLiveValue()) {
					g.setRemainSec(remain);
					g.setLiveValue(remain);
					dirty = true;
				}
			}
		}
		if (hasSource(GaugeWidget.Source.MCP)) {
			onMcpStatus();
		}
		if (dirty) {
			schedulePush();
		}
	}

	/**
	 * Finished, decolorized output (the same string triggers see). Splits into
	 * lines. Connection thread; each regex uses a local Matcher.
	 */
	void onOutputText(final String stripped) {
		if (stripped == null || stripped.length() == 0) {
			return;
		}
		synchronized (lock) {
			if (!hasSource(GaugeWidget.Source.REGEX)) {
				return;
			}
			boolean dirty = false;
			int start = 0;
			int n = stripped.length();
			for (int i = 0; i <= n; i++) {
				if (i == n || stripped.charAt(i) == '\n' || stripped.charAt(i) == '\r') {
					if (i > start) {
						dirty |= applyOutputLineLocked(stripped.substring(start, i));
					}
					if (i < n && stripped.charAt(i) == '\r' && i + 1 < n
							&& stripped.charAt(i + 1) == '\n') {
						i++;
					}
					start = i + 1;
				}
			}
			if (dirty) {
				schedulePush();
			}
		}
	}

	void onOutputLine(final String line) {
		synchronized (lock) {
			if (applyOutputLineLocked(line)) {
				schedulePush();
			}
		}
	}

	private boolean applyOutputLineLocked(final String line) {
		if (line == null || line.length() == 0) {
			return false;
		}
		boolean dirty = false;
		for (int i = 0; i < list.size(); i++) {
			GaugeWidget g = list.get(i);
			if (g == null || g.getSource() != GaugeWidget.Source.REGEX) {
				continue;
			}
			double[] nums = GaugeBinding.numbersFromRegexLine(line, g.getPath(),
					g.getMaxPath());
			if (nums == null || nums.length < 1) {
				continue;
			}
			if (nums[0] != g.getLiveValue()) {
				g.setLiveValue(nums[0]);
				dirty = true;
			}
			if (nums.length >= 2 && nums[1] != g.getLiveMax()) {
				g.setLiveMax(nums[1]);
				dirty = true;
			}
		}
		return dirty;
	}

	private String applyAdd(final WidgetCommandParser.Result r) {
		if (GaugeWidgetsStore.find(list, r.id) != null) {
			r.error = "Widget '" + r.id + "' already exists.";
			return null;
		}
		if (list.size() >= GaugeWidgetsStore.MAX) {
			r.error = "At most " + GaugeWidgetsStore.MAX + " widgets.";
			return null;
		}
		GaugeWidget g = new GaugeWidget(r.id);
		g.setLabel(r.id);
		g.setShape(GaugeWidget.Shape.fromJsonValue(
				r.shape != null ? r.shape : WidgetCommandParser.SHAPE_HBAR));
		g.setX(GaugeSpawnPlacement.UNPLACED);
		g.setY(GaugeSpawnPlacement.UNPLACED);
		list.add(g);
		afterConfig();
		return "Added widget " + g.getId() + " (" + g.getShape().toJsonValue() + ").";
	}

	private String applyRemove(final WidgetCommandParser.Result r) {
		GaugeWidget g = requireWidget(r);
		if (g == null) {
			return null;
		}
		list.remove(g);
		deadlines.remove(g.getId());
		afterConfig();
		return "Removed widget " + g.getId() + ".";
	}

	private String applySet(final WidgetCommandParser.Result r) {
		GaugeWidget g = requireWidget(r);
		if (g == null) {
			return null;
		}
		if (r.value != null) {
			g.setLiveValue(r.value.doubleValue());
			if (g.getShape() == GaugeWidget.Shape.TIMER) {
				g.setRemainSec(r.value.doubleValue());
				long now = SystemClock.elapsedRealtime();
				deadlines.put(g.getId(), Long.valueOf(
						now + (long) (r.value.doubleValue() * 1000.0)));
			}
		}
		if (r.max != null) {
			g.setLiveMax(r.max.doubleValue());
			if (g.getShape() == GaugeWidget.Shape.TIMER) {
				g.setDurationSec(r.max.doubleValue());
			}
		}
		syncTimerLoop();
		if (host != null) {
			host.requestGaugeWidgetValues();
		}
		if (g.getShape() == GaugeWidget.Shape.TIMER) {
			return "Widget " + g.getId() + " " + formatAmount(g.getRemainSec())
					+ "/" + formatAmount(g.getDurationSec()) + ".";
		}
		return "Widget " + g.getId() + " " + formatAmount(g.getLiveValue())
				+ "/" + formatAmount(g.getLiveMax()) + ".";
	}

	private String applySource(final WidgetCommandParser.Result r,
			final GaugeWidget g) {
		GaugeWidget.Source src = GaugeWidget.Source.fromJsonValue(r.source);
		g.setSource(src);
		if (src == GaugeWidget.Source.MANUAL) {
			g.setPath("");
			g.setMaxPath("");
			g.setTimerName("");
		} else if (src == GaugeWidget.Source.TIMER) {
			String name = r.path != null ? r.path : "";
			g.setPath(name);
			g.setTimerName(name);
			g.setMaxPath("");
		} else {
			g.setPath(r.path != null ? r.path : "");
			g.setMaxPath(r.maxPath != null ? r.maxPath : "");
			g.setTimerName("");
		}
		afterConfig();
		StringBuilder sb = new StringBuilder();
		sb.append("Widget ").append(g.getId()).append(" source ")
				.append(src.toJsonValue());
		if (g.getPath().length() > 0) {
			sb.append(' ').append(g.getPath());
		}
		if (g.getMaxPath().length() > 0) {
			sb.append(' ').append(g.getMaxPath());
		}
		sb.append('.');
		return sb.toString();
	}

	private String applySwipe(final WidgetCommandParser.Result r,
			final GaugeWidget g) {
		String cmd = r.text != null ? r.text : "";
		String dir = r.swipeDir != null ? r.swipeDir : "";
		if (WidgetCommandParser.SWIPE_UP.equals(dir)) {
			g.setSwipeUp(cmd);
		} else if (WidgetCommandParser.SWIPE_DOWN.equals(dir)) {
			g.setSwipeDown(cmd);
		} else if (WidgetCommandParser.SWIPE_LEFT.equals(dir)) {
			g.setSwipeLeft(cmd);
		} else if (WidgetCommandParser.SWIPE_RIGHT.equals(dir)) {
			g.setSwipeRight(cmd);
		}
		afterConfig();
		return commandSetLine(g.getId(), "swipe " + dir, cmd);
	}

	private String applyWarn(final WidgetCommandParser.Result r,
			final GaugeWidget g) {
		if (r.flag != null && !r.flag.booleanValue()) {
			g.setWarnPct(0);
			afterConfig();
			return "Widget " + g.getId() + " warn off.";
		}
		if (r.warnPct != null) {
			g.setWarnPct(r.warnPct.intValue());
		}
		if (r.color != null) {
			g.setColorWarn(GaugeWidget.parseColor(r.color, g.getColorWarn()));
		}
		afterConfig();
		return "Widget " + g.getId() + " warn " + g.getWarnPct() + "%.";
	}

	private GaugeWidget requireWidget(final WidgetCommandParser.Result r) {
		GaugeWidget g = GaugeWidgetsStore.find(list, r.id);
		if (g == null) {
			r.error = "No widget named '" + r.id + "'.";
			return null;
		}
		return g;
	}

	private void afterConfig() {
		persistLocked();
		if (host != null) {
			host.requestGaugeWidgetUi();
			host.requestGaugeWidgetValues();
		}
		syncTimerLoop();
	}

	private void schedulePush() {
		if (host == null || host.mHandler == null) {
			return;
		}
		host.mHandler.removeCallbacks(pushRunnable);
		host.mHandler.post(pushRunnable);
	}

	private void syncTimerLoop() {
		boolean want = wantsTimerLoop();
		Handler h = host != null ? host.mHandler : null;
		if (h == null) {
			ticking = false;
			return;
		}
		if (want && !ticking) {
			ticking = true;
			h.removeCallbacks(tickRunnable);
			h.postDelayed(tickRunnable, TICK_MS);
		} else if (!want && ticking) {
			ticking = false;
			h.removeCallbacks(tickRunnable);
		}
	}

	private boolean wantsTimerLoop() {
		return hasSource(GaugeWidget.Source.TIMER) || hasTimerShape();
	}

	private boolean hasTimerShape() {
		for (int i = 0; i < list.size(); i++) {
			GaugeWidget g = list.get(i);
			if (g != null && g.getShape() == GaugeWidget.Shape.TIMER) {
				return true;
			}
		}
		return false;
	}

	private boolean hasSource(final GaugeWidget.Source src) {
		for (int i = 0; i < list.size(); i++) {
			GaugeWidget g = list.get(i);
			if (g != null && g.getSource() == src) {
				return true;
			}
		}
		return false;
	}

	private void refreshHostTimers() {
		if (host == null) {
			return;
		}
		if (host.mSettings != null) {
			host.mSettings.updateTimerProgress();
		}
		if (host.mPlugins != null) {
			for (int i = 0; i < host.mPlugins.size(); i++) {
				Plugin p = host.mPlugins.get(i);
				if (p != null && p != host.mSettings) {
					p.updateTimerProgress();
				}
			}
		}
	}

	private TimerData findTimer(final String name) {
		if (name == null || name.length() == 0 || host == null) {
			return null;
		}
		TimerData t = findTimerInPlugin(host.mSettings, name);
		if (t != null) {
			return t;
		}
		if (host.mPlugins != null) {
			for (int i = 0; i < host.mPlugins.size(); i++) {
				t = findTimerInPlugin(host.mPlugins.get(i), name);
				if (t != null) {
					return t;
				}
			}
		}
		return null;
	}

	private static TimerData findTimerInPlugin(final Plugin p, final String name) {
		if (p == null || p.getSettings() == null
				|| p.getSettings().getTimers() == null) {
			return null;
		}
		HashMap<String, TimerData> timers = p.getSettings().getTimers();
		TimerData exact = timers.get(name);
		if (exact != null) {
			return exact;
		}
		for (Map.Entry<String, TimerData> e : timers.entrySet()) {
			if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
				return e.getValue();
			}
		}
		return null;
	}

	private static boolean applyVarNumber(final GaugeWidget g, final String value,
			final boolean max) {
		if (value == null) {
			if (max) {
				if (g.getLiveMax() != 0.0) {
					g.setLiveMax(0.0);
					return true;
				}
			} else if (g.getLiveValue() != 0.0) {
				g.setLiveValue(0.0);
				return true;
			}
			return false;
		}
		Double n = GaugeBinding.parseNumber(value);
		if (n == null) {
			return false;
		}
		if (max) {
			if (n.doubleValue() != g.getLiveMax()) {
				g.setLiveMax(n.doubleValue());
				return true;
			}
		} else if (n.doubleValue() != g.getLiveValue()) {
			g.setLiveValue(n.doubleValue());
			return true;
		}
		return false;
	}

	private static Double numberFromCache(final Map<String, String> cache,
			final String path) {
		if (path == null || path.length() == 0 || cache == null) {
			return null;
		}
		String v = cache.get(path);
		if (v == null) {
			for (Map.Entry<String, String> e : cache.entrySet()) {
				if (e.getKey() != null && e.getKey().equalsIgnoreCase(path)) {
					v = e.getValue();
					break;
				}
			}
		}
		return GaugeBinding.parseNumber(v);
	}

	private static boolean pathEquals(final String path, final String name) {
		if (path == null || path.length() == 0 || name == null) {
			return false;
		}
		return path.equalsIgnoreCase(name);
	}

	private static String commandSetLine(final String id, final String kind,
			final String cmd) {
		if (cmd == null || cmd.length() == 0) {
			return "Widget " + id + " " + kind + " cleared.";
		}
		return "Widget " + id + " " + kind + " " + cmd + ".";
	}

	private static String formatAmount(final double n) {
		if (n == Math.rint(n) && !Double.isInfinite(n)) {
			return Long.toString((long) n);
		}
		return String.format(Locale.US, "%.2f", Double.valueOf(n));
	}

	private static final class LiveSnap {
		double v;
		double m;
		double remain;
		double duration;
		long deadline;
	}
}

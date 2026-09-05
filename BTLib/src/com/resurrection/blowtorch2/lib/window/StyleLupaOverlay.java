package com.resurrection.blowtorch2.lib.window;

import java.util.List;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.resurrection.blowtorch2.lib.service.function.LupaCommand;
import com.resurrection.blowtorch2.lib.trigger.style.StyleClipboard;
import com.resurrection.blowtorch2.lib.trigger.style.StyleClipboard.LayerRow;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec;
import com.resurrection.blowtorch2.lib.trigger.style.StyleSnapshot;

/**
 * Finger inspector: drag shows a live layer list, release makes it tappable,
 * Copy / New trigger apply the checked layers.
 */
public final class StyleLupaOverlay {

	public interface Host {
		Context context();
		Inspect inspectStyleAt(float x, float y);
		void invalidateHost();
		void openNewStyleTrigger(StyleMatchSpec spec, String pattern);
	}

	public static final class Inspect {
		public final StyleSnapshot snap;
		public final String glyph;

		public Inspect(final StyleSnapshot snap, final String glyph) {
			this.snap = snap;
			this.glyph = glyph == null ? "" : glyph;
		}
	}

	private final Host host;
	private final float density;
	private int mode = LupaCommand.MODE_OFF;
	private boolean fingerDown;
	private boolean listArmed;
	private float fingerX;
	private float fingerY;
	private StyleSnapshot snap;
	private String glyph = "";
	private boolean looksMode;
	private boolean extrasStrict;
	private boolean combineAny;
	private boolean[] checked = new boolean[0];
	private List<LayerRow> rows;
	private final Paint panel = new Paint();
	private final Paint text = new Paint();
	private final Paint accent = new Paint();
	private final Paint boxStroke = new Paint();
	private final RectF box = new RectF();
	private float rowH;
	private float panelW;

	public StyleLupaOverlay(final Host host) {
		this.host = host;
		density = host.context().getResources().getDisplayMetrics().density;
		rowH = 28f * density;
		panelW = 280f * density;
		panel.setColor(0xEE101018);
		panel.setAntiAlias(true);
		text.setColor(0xFFEEEEEE);
		text.setAntiAlias(true);
		text.setTextSize(13f * density);
		accent.setColor(0xFF66CCFF);
		accent.setAntiAlias(true);
		accent.setTextSize(13f * density);
		boxStroke.setColor(0xFFEEEEEE);
		boxStroke.setAntiAlias(true);
		boxStroke.setStyle(Paint.Style.STROKE);
		boxStroke.setStrokeWidth(Math.max(1f, density));
	}

	public boolean isOn() {
		return mode != LupaCommand.MODE_OFF;
	}

	public void setMode(final int mode) {
		this.mode = mode;
		if (mode == LupaCommand.MODE_OFF) {
			fingerDown = false;
			listArmed = false;
			snap = null;
			rows = null;
		}
		host.invalidateHost();
	}

	public boolean onTouch(final MotionEvent event) {
		if (!isOn()) {
			return false;
		}
		float x = event.getX();
		float y = event.getY();
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			if (listArmed && hitPanel(x, y)) {
				return tapPanel(x, y);
			}
			if (listArmed && !hitPanel(x, y)) {
				if (mode == LupaCommand.MODE_TAP) {
					setMode(LupaCommand.MODE_OFF);
				} else {
					listArmed = false;
					snap = null;
					rows = null;
					host.invalidateHost();
				}
				return true;
			}
			fingerDown = true;
			listArmed = false;
			fingerX = x;
			fingerY = y;
			refreshInspect(x, y);
			return true;
		case MotionEvent.ACTION_MOVE:
			if (fingerDown && !listArmed) {
				fingerX = x;
				fingerY = y;
				refreshInspect(x, y);
			}
			return true;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			if (fingerDown && !listArmed) {
				fingerDown = false;
				listArmed = snap != null;
				host.invalidateHost();
			}
			return true;
		default:
			return true;
		}
	}

	public void draw(final Canvas c) {
		if (!isOn()) {
			return;
		}
		c.drawCircle(fingerX, fingerY, 8f * density, accent);
		if (snap == null || rows == null) {
			c.drawText("Lupa: drag a glyph", 16f * density, 48f * density, accent);
			return;
		}
		layoutBox();
		c.drawRoundRect(box, 8f * density, 8f * density, panel);
		float y = box.top + rowH * 0.75f;
		c.drawText(looksMode ? "Looks the same" : "Exact recipe", box.left + 12f * density, y, accent);
		y += rowH;
		c.drawText(combineAny ? "ANY layer" : "ALL layers", box.left + 12f * density, y, text);
		y += rowH;
		c.drawText(extrasStrict ? "No extra attributes" : "Extra attributes OK",
				box.left + 12f * density, y, text);
		y += rowH;
		for (int i = 0; i < rows.size(); i++) {
			boolean on = i < checked.length && checked[i];
			float left = box.left + 12f * density;
			float top = y - 14f * density;
			RectF cb = new RectF(left, top, left + 14f * density, top + 14f * density);
			if (on) {
				c.drawRect(cb, accent);
			} else {
				c.drawRect(cb, boxStroke);
			}
			c.drawText(rows.get(i).label, left + 22f * density, y, text);
			y += rowH;
		}
		float by = box.bottom - rowH * 0.45f;
		c.drawText("Copy", box.left + 16f * density, by, accent);
		c.drawText("New trigger", box.left + 100f * density, by, accent);
	}

	private void layoutBox() {
		int n = rows == null ? 0 : rows.size();
		float h = rowH * (n + 5);
		float left = fingerX + 24f * density;
		float top = fingerY - h * 0.3f;
		int screenW = host.context().getResources().getDisplayMetrics().widthPixels;
		int screenH = host.context().getResources().getDisplayMetrics().heightPixels;
		if (left + panelW > screenW) {
			left = fingerX - panelW - 24f * density;
		}
		if (left < 8f * density) {
			left = 8f * density;
		}
		if (top < 8f * density) {
			top = 8f * density;
		}
		if (top + h > screenH - 8f * density) {
			top = Math.max(8f * density, screenH - h - 8f * density);
		}
		box.set(left, top, left + panelW, top + h);
	}

	private boolean hitPanel(final float x, final float y) {
		if (rows == null) {
			return false;
		}
		layoutBox();
		return box.contains(x, y);
	}

	private boolean tapPanel(final float x, final float y) {
		layoutBox();
		int row = (int) ((y - box.top) / rowH);
		int n = rows.size();
		if (row == 0) {
			looksMode = !looksMode;
			host.invalidateHost();
			return true;
		}
		if (row == 1) {
			combineAny = !combineAny;
			host.invalidateHost();
			return true;
		}
		if (row == 2) {
			extrasStrict = !extrasStrict;
			host.invalidateHost();
			return true;
		}
		int idx = row - 3;
		if (idx >= 0 && idx < n) {
			if (idx < checked.length) {
				checked[idx] = !checked[idx];
			}
			host.invalidateHost();
			return true;
		}
		if (x < box.left + 90f * density) {
			copyClipboard();
		} else {
			openTrigger();
		}
		return true;
	}

	private void copyClipboard() {
		String body = StyleClipboard.describeChecked(snap, glyph, checked, looksMode);
		ClipboardManager cm = (ClipboardManager) host.context()
				.getSystemService(Context.CLIPBOARD_SERVICE);
		if (cm != null) {
			cm.setPrimaryClip(ClipData.newPlainText("BlowTorch style", body));
		}
		afterCopy();
	}

	private void openTrigger() {
		StyleMatchSpec spec = StyleClipboard.specFromChecks(snap, glyph, checked,
				looksMode, extrasStrict, combineAny);
		String pattern = "";
		if (rows != null) {
			for (int i = 0; i < rows.size() && i < checked.length; i++) {
				if (checked[i] && "text".equals(rows.get(i).id)) {
					pattern = glyph;
				}
			}
		}
		host.openNewStyleTrigger(spec, pattern);
		afterCopy();
	}

	private void afterCopy() {
		if (mode == LupaCommand.MODE_ONCE || mode == LupaCommand.MODE_TAP) {
			setMode(LupaCommand.MODE_OFF);
		} else {
			listArmed = false;
			snap = null;
			rows = null;
			host.invalidateHost();
		}
	}

	private void refreshInspect(final float x, final float y) {
		Inspect hit = host.inspectStyleAt(x, y);
		if (hit == null || hit.snap == null) {
			return;
		}
		snap = hit.snap;
		glyph = hit.glyph;
		rows = StyleClipboard.layers(snap, glyph);
		checked = new boolean[rows.size()];
		for (int i = 0; i < rows.size(); i++) {
			checked[i] = rows.get(i).defaultOn;
		}
		host.invalidateHost();
	}
}

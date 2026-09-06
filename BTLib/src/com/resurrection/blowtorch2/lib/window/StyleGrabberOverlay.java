package com.resurrection.blowtorch2.lib.window;

import java.util.List;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.function.GrabberCommand;
import com.resurrection.blowtorch2.lib.trigger.style.StyleClipboard;
import com.resurrection.blowtorch2.lib.trigger.style.StyleClipboard.LayerRow;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec;
import com.resurrection.blowtorch2.lib.trigger.style.StyleSnapshot;

/**
 * Finger inspector: drag shows a live layer list, release makes it tappable,
 * Copy / New trigger apply the checked layers.
 */
public final class StyleGrabberOverlay {

	public interface Host {
		Context context();
		Inspect inspectStyleAt(float x, float y);
		void invalidateHost();
		void openNewStyleTrigger(StyleMatchSpec spec, String pattern);
		int overlayWidth();
		int overlayHeight();
		void dismissGrabber();
	}

	public static final class Inspect {
		public final StyleSnapshot snap;
		public final String glyph;
		public final float cellLeft;
		public final float cellTop;
		public final float cellRight;
		public final float cellBottom;

		public Inspect(final StyleSnapshot snap, final String glyph) {
			this(snap, glyph, 0f, 0f, 0f, 0f);
		}

		public Inspect(final StyleSnapshot snap, final String glyph,
				final float cellLeft, final float cellTop, final float cellRight,
				final float cellBottom) {
			this.snap = snap;
			this.glyph = glyph == null ? "" : glyph;
			this.cellLeft = cellLeft;
			this.cellTop = cellTop;
			this.cellRight = cellRight;
			this.cellBottom = cellBottom;
		}

		public boolean hasCell() {
			return cellRight > cellLeft + 0.5f && cellBottom > cellTop + 0.5f;
		}
	}

	private final Host host;
	private final float density;
	private int mode = GrabberCommand.MODE_OFF;
	private boolean fingerDown;
	private boolean consumeUntilUp;
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
	private boolean haveCell;
	private float cellLeft;
	private float cellTop;
	private float cellRight;
	private float cellBottom;
	private final Paint panel = new Paint();
	private final Paint text = new Paint();
	private final Paint accent = new Paint();
	private final Paint boxStroke = new Paint();
	private final Paint idleCloseFill = new Paint();
	private final Paint reticle = new Paint();
	private final Paint pip = new Paint();
	private final Paint cellFill = new Paint();
	private final Paint loupeText = new Paint();
	private final RectF loupeBox = new RectF();
	private final Drawable closeIcon;
	private final RectF box = new RectF();
	private float rowH;
	private float layoutRowH;
	private float panelW;
	private float closeSize;
	private float closePad;
	private float copySplitFromLeft;
	private final RectF close = new RectF();

	/** Title, match mode, combine, extras, then layers, then Copy / New trigger. */
	private static final int CHROME = 6;

	public StyleGrabberOverlay(final Host host) {
		this.host = host;
		density = host.context().getResources().getDisplayMetrics().density;
		rowH = 28f * density;
		layoutRowH = rowH;
		panelW = 200f * density;
		closeSize = 32f * density;
		closePad = 6f * density;
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
		idleCloseFill.setColor(host.context().getResources()
				.getColor(R.color.extra_text_title_bar, null));
		idleCloseFill.setAntiAlias(true);
		reticle.setColor(0xFF66CCFF);
		reticle.setAntiAlias(true);
		reticle.setStyle(Paint.Style.STROKE);
		reticle.setStrokeWidth(Math.max(2f, 2f * density));
		pip.setColor(0xFF66CCFF);
		pip.setAntiAlias(true);
		pip.setStyle(Paint.Style.FILL);
		cellFill.setColor(0x4466CCFF);
		cellFill.setStyle(Paint.Style.FILL);
		loupeText.setColor(0xFFEEEEEE);
		loupeText.setAntiAlias(true);
		loupeText.setTextSize(18f * density);
		closeIcon = host.context().getResources()
				.getDrawable(R.drawable.ic_window_close, host.context().getTheme())
				.mutate();
		closeIcon.setColorFilter(new PorterDuffColorFilter(0xFFD2D8DF,
				PorterDuff.Mode.SRC_IN));
	}

	public boolean isOn() {
		return mode != GrabberCommand.MODE_OFF;
	}

	/** True until UP/CANCEL after a consumed DOWN, including after dismiss. */
	public boolean consumingGesture() {
		return consumeUntilUp;
	}

	public void setMode(final int mode) {
		this.mode = mode;
		if (mode == GrabberCommand.MODE_OFF) {
			fingerDown = false;
			listArmed = false;
			snap = null;
			rows = null;
			haveCell = false;
		}
		host.invalidateHost();
	}

	public boolean onTouch(final MotionEvent event) {
		if (!isOn()) {
			if (!consumeUntilUp) {
				return false;
			}
			int leftover = event.getActionMasked();
			if (leftover == MotionEvent.ACTION_UP
					|| leftover == MotionEvent.ACTION_CANCEL) {
				consumeUntilUp = false;
			}
			return true;
		}
		float x = event.getX();
		float y = event.getY();
		int action = event.getActionMasked();
		switch (action) {
		case MotionEvent.ACTION_DOWN:
			if (snap == null || rows == null) {
				if (hitIdleClose(x, y)) {
					host.dismissGrabber();
					consumeUntilUp = true;
					return true;
				}
			} else if (hitPanelClose(x, y)) {
				host.dismissGrabber();
				consumeUntilUp = true;
				return true;
			}
			if (listArmed && hitPanel(x, y)) {
				consumeUntilUp = true;
				return tapPanel(x, y);
			}
			if (listArmed && !hitPanel(x, y)) {
				if (mode == GrabberCommand.MODE_TAP) {
					host.dismissGrabber();
				} else {
					listArmed = false;
					snap = null;
					rows = null;
					haveCell = false;
					host.invalidateHost();
				}
				consumeUntilUp = true;
				return true;
			}
			Inspect hit = host.inspectStyleAt(x, y);
			if (hit == null || hit.snap == null) {
				consumeUntilUp = false;
				return false;
			}
			fingerDown = true;
			listArmed = false;
			fingerX = x;
			fingerY = y;
			applyHit(hit);
			consumeUntilUp = true;
			return true;
		case MotionEvent.ACTION_MOVE:
			if (!consumeUntilUp) {
				return false;
			}
			if (!fingerDown || listArmed) {
				return true;
			}
			fingerX = x;
			fingerY = y;
			refreshInspect(x, y);
			return true;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			if (!consumeUntilUp) {
				return false;
			}
			consumeUntilUp = false;
			if (!fingerDown && !listArmed) {
				return true;
			}
			if (fingerDown && !listArmed) {
				fingerDown = false;
				listArmed = snap != null;
				host.invalidateHost();
			}
			return true;
		default:
			return consumeUntilUp || fingerDown || listArmed;
		}
	}

	public void draw(final Canvas c) {
		if (!isOn()) {
			return;
		}
		if (snap == null || rows == null) {
			layoutIdleClose();
			drawClose(c, true);
			return;
		}
		if (fingerDown || listArmed) {
			drawReticle(c);
		}
		layoutBox();
		c.save();
		c.clipRect(box);
		c.drawRoundRect(box, 8f * density, 8f * density, panel);
		float step = layoutRowH > 0f ? layoutRowH : rowH;
		float y = box.top + step * 0.75f;
		c.drawText("Grabber", box.left + 12f * density, y, accent);
		y += step;
		c.drawText(looksMode ? "Looks the same" : "Exact recipe", box.left + 12f * density, y, text);
		y += step;
		c.drawText(combineAny ? "ANY layer" : "ALL layers", box.left + 12f * density, y, text);
		y += step;
		c.drawText(extrasStrict ? "No extra attributes" : "Extra attributes OK",
				box.left + 12f * density, y, text);
		y += step;
		float labelMax = box.width() - 42f * density;
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
			c.drawText(fit(text, rows.get(i).label, labelMax), left + 22f * density, y, text);
			y += step;
		}
		float by = box.bottom - step * 0.45f;
		float copyX = box.left + 16f * density;
		c.drawText("Copy", copyX, by, accent);
		c.drawText("New trigger", box.left + copySplitFromLeft, by, accent);
		c.restore();
		if (fingerDown) {
			drawLoupe(c);
		}
		drawClose(c, false);
	}

	private void layoutBox() {
		int n = rows == null ? 0 : rows.size();
		float h = rowH * (n + CHROME);
		float margin = 8f * density;
		float gap = 24f * density;
		panelW = measurePanelW();
		StyleGrabberPlace p = StyleGrabberPlace.of(fingerX, fingerY, panelW, h,
				host.overlayWidth(), host.overlayHeight(), gap, margin, 0.3f);
		box.set(p.left, p.top, p.right, p.bottom);
		int slots = n + CHROME;
		if (slots < 1) {
			slots = 1;
		}
		float bh = box.height();
		layoutRowH = bh > 0f ? bh / slots : rowH;
		close.set(box.right - closeSize, box.top, box.right, box.top + closeSize);
	}

	private float measurePanelW() {
		float padL = 12f * density;
		float padR = closeSize;
		float max = 0f;
		max = Math.max(max, accent.measureText("Grabber"));
		max = Math.max(max, text.measureText(looksMode ? "Looks the same" : "Exact recipe"));
		max = Math.max(max, text.measureText(combineAny ? "ANY layer" : "ALL layers"));
		max = Math.max(max, text.measureText(
				extrasStrict ? "No extra attributes" : "Extra attributes OK"));
		if (rows != null) {
			float check = 22f * density;
			for (int i = 0; i < rows.size(); i++) {
				String id = rows.get(i).id;
				if ("text".equals(id) || "href".equals(id)) {
					continue;
				}
				max = Math.max(max, check + text.measureText(rows.get(i).label));
			}
		}
		float copy = accent.measureText("Copy");
		float neu = accent.measureText("New trigger");
		copySplitFromLeft = 16f * density + copy + 12f * density;
		max = Math.max(max, 16f * density + copy + 12f * density + neu);
		float w = max + padL + padR;
		float min = 148f * density;
		if (w < min) {
			w = min;
		}
		float cap = host.overlayWidth() - 16f * density;
		if (cap > min && w > cap) {
			w = cap;
		}
		return w;
	}

	private void layoutIdleClose() {
		float margin = 8f * density;
		float left = StyleGrabberPlace.idleCloseLeft(host.overlayWidth(), closeSize, margin);
		close.set(left, margin, left + closeSize, margin + closeSize);
	}

	private void drawClose(final Canvas c, final boolean idle) {
		if (idle) {
			// Idle sits on game text, not a title bar; plate is extra_text_title_bar.
			c.drawRoundRect(close, 4f * density, 4f * density, idleCloseFill);
		}
		int pad = Math.round(closePad);
		closeIcon.setBounds(Math.round(close.left) + pad, Math.round(close.top) + pad,
				Math.round(close.right) - pad, Math.round(close.bottom) - pad);
		closeIcon.draw(c);
	}

	private boolean hitIdleClose(final float x, final float y) {
		layoutIdleClose();
		return StyleGrabberPlace.hitSquare(x, y, close.left, close.top, closeSize);
	}

	private boolean hitPanelClose(final float x, final float y) {
		if (rows == null) {
			return false;
		}
		layoutBox();
		return StyleGrabberPlace.hitSquare(x, y, close.left, close.top, closeSize);
	}

	private boolean hitPanel(final float x, final float y) {
		if (rows == null) {
			return false;
		}
		layoutBox();
		return box.contains(x, y);
	}

	private boolean tapPanel(final float x, final float y) {
		if (hitPanelClose(x, y)) {
			host.dismissGrabber();
			return true;
		}
		layoutBox();
		int n = rows.size();
		int row = StyleGrabberPlace.rowAt(y, box.top, box.height(), n + CHROME);
		if (row == 0) {
			return true;
		}
		if (row == 1) {
			looksMode = !looksMode;
			host.invalidateHost();
			return true;
		}
		if (row == 2) {
			combineAny = !combineAny;
			host.invalidateHost();
			return true;
		}
		if (row == 3) {
			extrasStrict = !extrasStrict;
			host.invalidateHost();
			return true;
		}
		int idx = row - 4;
		if (idx >= 0 && idx < n) {
			if (idx < checked.length) {
				checked[idx] = !checked[idx];
			}
			host.invalidateHost();
			return true;
		}
		if (x < box.left + copySplitFromLeft) {
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
		if (mode == GrabberCommand.MODE_ONCE || mode == GrabberCommand.MODE_TAP) {
			host.dismissGrabber();
		} else {
			listArmed = false;
			snap = null;
			rows = null;
			haveCell = false;
			host.invalidateHost();
		}
	}

	private void refreshInspect(final float x, final float y) {
		Inspect hit = host.inspectStyleAt(x, y);
		if (hit == null || hit.snap == null) {
			return;
		}
		applyHit(hit);
	}

	private void applyHit(final Inspect hit) {
		snap = hit.snap;
		glyph = hit.glyph;
		haveCell = hit.hasCell();
		if (haveCell) {
			cellLeft = hit.cellLeft;
			cellTop = hit.cellTop;
			cellRight = hit.cellRight;
			cellBottom = hit.cellBottom;
		}
		rows = StyleClipboard.layers(snap, glyph);
		checked = new boolean[rows.size()];
		for (int i = 0; i < rows.size(); i++) {
			checked[i] = rows.get(i).defaultOn;
		}
		host.invalidateHost();
	}

	private void drawReticle(final Canvas c) {
		boolean onCell = haveCell && cellBottom > 0f && cellTop < host.overlayHeight()
				&& cellRight > 0f && cellLeft < host.overlayWidth();
		if (onCell) {
			c.drawRect(cellLeft, cellTop, cellRight, cellBottom, cellFill);
		}
		float ax = onCell ? (cellLeft + cellRight) * 0.5f : fingerX;
		float ay = onCell ? (cellTop + cellBottom) * 0.5f : fingerY;
		c.drawCircle(ax, ay, 10f * density, reticle);
		c.drawCircle(ax, ay, 2.5f * density, pip);
	}

	private void drawLoupe(final Canvas c) {
		if (glyph == null || glyph.length() == 0) {
			return;
		}
		float pad = 8f * density;
		loupeText.setTextSize(Math.max(text.getTextSize() * 1.8f, 18f * density));
		float maxW = box.width() - pad * 2f;
		if (maxW < 24f * density) {
			maxW = 24f * density;
		}
		String shown = fit(loupeText, glyph, maxW);
		float tw = loupeText.measureText(shown);
		float loupeW = tw + pad * 2f;
		Paint.FontMetrics fm = loupeText.getFontMetrics();
		float loupeH = (fm.descent - fm.ascent) + pad * 2f;
		float gap = 8f * density;
		float margin = 8f * density;
		StyleGrabberPlace lp = StyleGrabberPlace.loupeOf(box.left, box.top,
				box.right, box.bottom, loupeW, loupeH,
				host.overlayWidth(), host.overlayHeight(), gap, margin);
		loupeBox.set(lp.left, lp.top, lp.right, lp.bottom);
		c.drawRoundRect(loupeBox, 8f * density, 8f * density, panel);
		c.drawText(shown, lp.left + pad, lp.top + pad - fm.ascent, loupeText);
	}

	private String fit(final Paint p, final String s, final float maxW) {
		return StyleGrabberText.ellipsize(s, maxW, new StyleGrabberText.Widths() {
			@Override
			public float measure(final String t) {
				return p.measureText(t);
			}
		});
	}
}

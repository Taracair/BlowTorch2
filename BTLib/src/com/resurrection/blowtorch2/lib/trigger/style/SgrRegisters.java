package com.resurrection.blowtorch2.lib.trigger.style;

import java.util.List;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.SgrStyle;

/**
 * ANSI SGR register machine used for style matching and the grabber. Same
 * classification as {@code Window.updateColorRegisters}: SGR 1 is bright, not
 * weight; private 66/67 are weight; {@code 38;5;n} is xterm, not italic.
 *
 * <p>Not the draw path. Light-paper remap stays in {@code Window}.
 */
public final class SgrRegisters {

	private Integer selectedColor = Integer.valueOf(37);
	private Integer selectedBackground = Integer.valueOf(40);
	private Integer selectedBright = Integer.valueOf(0);
	private boolean xterm256FG;
	private boolean xterm256BG;
	private boolean trueColorFG;
	private boolean trueColorBG;
	private boolean xterm256Color;
	private boolean xterm256FGStart;
	private boolean xterm256BGStart;
	private boolean trueColorCollect;
	private boolean trueColorIsFG;
	private int trueColorCount;
	private final int[] trueColorRGB = new int[3];
	private final SgrStyle sgr = new SgrStyle();
	private String href;

	public static SgrRegisters defaults() {
		return new SgrRegisters();
	}

	public SgrRegisters copy() {
		SgrRegisters o = new SgrRegisters();
		o.selectedColor = selectedColor;
		o.selectedBackground = selectedBackground;
		o.selectedBright = selectedBright;
		o.xterm256FG = xterm256FG;
		o.xterm256BG = xterm256BG;
		o.trueColorFG = trueColorFG;
		o.trueColorBG = trueColorBG;
		o.sgr.setBits(sgr.bits());
		o.href = href;
		return o;
	}

	/**
	 * Mid-sequence flags reset at the top of every colour unit, as
	 * {@code Window.applyColorUnit} does.
	 */
	public void beginColorUnit() {
		xterm256Color = false;
		xterm256FGStart = false;
		xterm256BGStart = false;
		trueColorCollect = false;
		trueColorCount = 0;
	}

	public void applyOps(final List<Integer> ops) {
		if (ops == null) {
			return;
		}
		for (int i = 0; i < ops.size(); i++) {
			apply(ops.get(i));
		}
	}

	public Colorizer.COLOR_TYPE apply(final Integer i) {
		if (i == null) {
			return Colorizer.COLOR_TYPE.NOT_A_COLOR;
		}

		if (trueColorCollect) {
			int component = i.intValue();
			if (component < 0) {
				component = 0;
			} else if (component > 255) {
				component = 255;
			}
			trueColorRGB[trueColorCount++] = component;
			if (trueColorCount >= 3) {
				int packed = (trueColorRGB[0] << 16) | (trueColorRGB[1] << 8)
						| trueColorRGB[2];
				if (trueColorIsFG) {
					selectedColor = Integer.valueOf(packed);
					trueColorFG = true;
					xterm256FG = false;
				} else {
					selectedBackground = Integer.valueOf(packed);
					trueColorBG = true;
					xterm256BG = false;
				}
				trueColorCollect = false;
				trueColorCount = 0;
			}
			return null;
		}

		if (xterm256Color) {
			if (xterm256FGStart) {
				selectedColor = i;
				xterm256FGStart = false;
				xterm256Color = false;
				xterm256FG = true;
				trueColorFG = false;
			} else if (xterm256BGStart) {
				selectedBackground = i;
				xterm256BGStart = false;
				xterm256Color = false;
				xterm256BG = true;
				trueColorBG = false;
			}
			return null;
		}

		Colorizer.COLOR_TYPE type = Colorizer.getColorType(i);
		switch (type) {
		case FOREGROUND:
			selectedColor = i;
			xterm256FGStart = false;
			xterm256BGStart = false;
			xterm256Color = false;
			xterm256FG = false;
			trueColorFG = false;
			trueColorCollect = false;
			break;
		case BACKGROUND:
			selectedBackground = i;
			xterm256FGStart = false;
			xterm256BGStart = false;
			xterm256Color = false;
			xterm256BG = false;
			trueColorBG = false;
			trueColorCollect = false;
			break;
		case ZERO_CODE:
			selectedBright = Integer.valueOf(0);
			selectedColor = Integer.valueOf(37);
			selectedBackground = Integer.valueOf(40);
			xterm256FGStart = false;
			xterm256BGStart = false;
			xterm256Color = false;
			xterm256FG = false;
			xterm256BG = false;
			trueColorFG = false;
			trueColorBG = false;
			trueColorCollect = false;
			trueColorCount = 0;
			sgr.clear();
			break;
		case BRIGHT_CODE:
			selectedBright = Integer.valueOf(1);
			xterm256FGStart = false;
			xterm256BGStart = false;
			xterm256Color = false;
			break;
		case NORMAL_INTENSITY:
			selectedBright = Integer.valueOf(0);
			sgr.clearFaint();
			sgr.clearWeight();
			xterm256FGStart = false;
			xterm256BGStart = false;
			xterm256Color = false;
			break;
		case DIM_CODE:
			if (xterm256FGStart || xterm256BGStart) {
				trueColorCollect = true;
				trueColorIsFG = xterm256FGStart;
				trueColorCount = 0;
				xterm256FGStart = false;
				xterm256BGStart = false;
				xterm256Color = false;
			} else {
				sgr.setFaint(true);
			}
			break;
		case SGR_STYLE:
			sgr.apply(i.intValue());
			break;
		case DEFAULT_FOREGROUND:
			selectedColor = Integer.valueOf(37);
			xterm256FGStart = false;
			xterm256BGStart = false;
			xterm256Color = false;
			xterm256FG = false;
			trueColorFG = false;
			break;
		case DEFAULT_BACKGROUND:
			selectedBackground = Integer.valueOf(40);
			xterm256FGStart = false;
			xterm256BGStart = false;
			xterm256Color = false;
			xterm256BG = false;
			trueColorBG = false;
			break;
		case XTERM_256_FG_START:
			xterm256FGStart = true;
			trueColorCollect = false;
			break;
		case XTERM_256_BG_START:
			xterm256BGStart = true;
			trueColorCollect = false;
			break;
		case XTERM_256_FIVE:
			if (xterm256BGStart || xterm256FGStart) {
				xterm256Color = true;
			} else {
				sgr.setBlink(true);
			}
			break;
		default:
			return Colorizer.COLOR_TYPE.NOT_A_COLOR;
		}
		return type;
	}

	public void setHref(final String href) {
		this.href = href;
	}

	public String href() {
		return href;
	}

	public StyleSnapshot snapshot() {
		return StyleSnapshot.fromRegisters(this);
	}

	Integer selectedColor() {
		return selectedColor;
	}

	Integer selectedBackground() {
		return selectedBackground;
	}

	Integer selectedBright() {
		return selectedBright;
	}

	boolean xterm256FG() {
		return xterm256FG;
	}

	boolean xterm256BG() {
		return xterm256BG;
	}

	boolean trueColorFG() {
		return trueColorFG;
	}

	boolean trueColorBG() {
		return trueColorBG;
	}

	int sgrBits() {
		return sgr.bits();
	}
}

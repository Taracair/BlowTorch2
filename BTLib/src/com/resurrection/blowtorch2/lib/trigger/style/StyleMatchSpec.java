package com.resurrection.blowtorch2.lib.trigger.style;

import android.os.Parcel;
import android.os.Parcelable;

import com.resurrection.blowtorch2.lib.service.SgrStyle;
import com.resurrection.blowtorch2.lib.trigger.style.StyleSnapshot.ColorSpace;

/**
 * What a style trigger requires. Each layer is ignore / require / forbid.
 * Combine ALL vs ANY applies to the non-ignore layers. Extras FORBID means
 * any SGR flag that was ignored must be off on the glyph.
 *
 * <p>Empty spec (every layer ignore, no text) is inactive — old triggers.
 */
public final class StyleMatchSpec implements Parcelable {

	public enum Gate {
		IGNORE,
		REQUIRE,
		FORBID;

		public static Gate fromXml(final String raw) {
			if (raw == null) {
				return IGNORE;
			}
			String s = raw.trim().toLowerCase();
			if ("require".equals(s) || "on".equals(s) || "yes".equals(s)
					|| "true".equals(s) || "1".equals(s)) {
				return REQUIRE;
			}
			if ("forbid".equals(s) || "off".equals(s) || "not".equals(s)
					|| "false".equals(s) || "0".equals(s)) {
				return FORBID;
			}
			return IGNORE;
		}

		public String xmlValue() {
			switch (this) {
			case REQUIRE:
				return "require";
			case FORBID:
				return "forbid";
			default:
				return "ignore";
			}
		}
	}

	public enum Combine {
		ALL,
		ANY;

		public static Combine fromXml(final String raw) {
			if (raw != null && "any".equalsIgnoreCase(raw.trim())) {
				return ANY;
			}
			return ALL;
		}

		public String xmlValue() {
			return this == ANY ? "any" : "all";
		}
	}

	public enum Extras {
		ALLOW,
		FORBID;

		public static Extras fromXml(final String raw) {
			if (raw != null && ("forbid".equalsIgnoreCase(raw.trim())
					|| "strict".equalsIgnoreCase(raw.trim()))) {
				return FORBID;
			}
			return ALLOW;
		}

		public String xmlValue() {
			return this == FORBID ? "forbid" : "allow";
		}
	}

	public enum ColorMode {
		EXACT,
		LOOKS;

		public static ColorMode fromXml(final String raw) {
			if (raw != null && ("looks".equalsIgnoreCase(raw.trim())
					|| "look".equalsIgnoreCase(raw.trim()))) {
				return LOOKS;
			}
			return EXACT;
		}

		public String xmlValue() {
			return this == LOOKS ? "looks" : "exact";
		}
	}

	private Combine combine = Combine.ALL;
	private Extras extras = Extras.ALLOW;
	private ColorMode colorMode = ColorMode.EXACT;

	private Gate fgGate = Gate.IGNORE;
	private ColorSpace fgSpace = ColorSpace.ANSI16;
	private int fgCode = 37;

	private Gate bgGate = Gate.IGNORE;
	private ColorSpace bgSpace = ColorSpace.ANSI16;
	private int bgCode = 40;

	private Gate weight = Gate.IGNORE;
	private Gate bright = Gate.IGNORE;
	private Gate italic = Gate.IGNORE;
	private Gate underline = Gate.IGNORE;
	private Gate doubleUnderline = Gate.IGNORE;
	private Gate strike = Gate.IGNORE;
	private Gate reverse = Gate.IGNORE;
	private Gate faint = Gate.IGNORE;
	private Gate blink = Gate.IGNORE;
	private Gate href = Gate.IGNORE;
	private String hrefValue = "";

	private String text = "";
	private Gate textGate = Gate.IGNORE;
	private boolean textRegex;

	public static StyleMatchSpec inactive() {
		return new StyleMatchSpec();
	}

	public StyleMatchSpec copy() {
		StyleMatchSpec o = new StyleMatchSpec();
		o.combine = combine;
		o.extras = extras;
		o.colorMode = colorMode;
		o.fgGate = fgGate;
		o.fgSpace = fgSpace;
		o.fgCode = fgCode;
		o.bgGate = bgGate;
		o.bgSpace = bgSpace;
		o.bgCode = bgCode;
		o.weight = weight;
		o.bright = bright;
		o.italic = italic;
		o.underline = underline;
		o.doubleUnderline = doubleUnderline;
		o.strike = strike;
		o.reverse = reverse;
		o.faint = faint;
		o.blink = blink;
		o.href = href;
		o.hrefValue = hrefValue;
		o.text = text;
		o.textGate = textGate;
		o.textRegex = textRegex;
		return o;
	}

	public boolean isActive() {
		if (fgGate != Gate.IGNORE || bgGate != Gate.IGNORE || weight != Gate.IGNORE
				|| bright != Gate.IGNORE || italic != Gate.IGNORE
				|| underline != Gate.IGNORE || doubleUnderline != Gate.IGNORE
				|| strike != Gate.IGNORE || reverse != Gate.IGNORE
				|| faint != Gate.IGNORE || blink != Gate.IGNORE
				|| href != Gate.IGNORE) {
			return true;
		}
		return text != null && text.length() > 0;
	}

	public Combine getCombine() {
		return combine;
	}

	public void setCombine(final Combine combine) {
		this.combine = combine == null ? Combine.ALL : combine;
	}

	public Extras getExtras() {
		return extras;
	}

	public void setExtras(final Extras extras) {
		this.extras = extras == null ? Extras.ALLOW : extras;
	}

	public ColorMode getColorMode() {
		return colorMode;
	}

	public void setColorMode(final ColorMode colorMode) {
		this.colorMode = colorMode == null ? ColorMode.EXACT : colorMode;
	}

	public Gate getFgGate() {
		return fgGate;
	}

	public void setFg(final Gate gate, final ColorSpace space, final int code) {
		fgGate = gate == null ? Gate.IGNORE : gate;
		fgSpace = space == null ? ColorSpace.ANSI16 : space;
		fgCode = code;
	}

	public ColorSpace getFgSpace() {
		return fgSpace;
	}

	public int getFgCode() {
		return fgCode;
	}

	public Gate getBgGate() {
		return bgGate;
	}

	public void setBg(final Gate gate, final ColorSpace space, final int code) {
		bgGate = gate == null ? Gate.IGNORE : gate;
		bgSpace = space == null ? ColorSpace.ANSI16 : space;
		bgCode = code;
	}

	public ColorSpace getBgSpace() {
		return bgSpace;
	}

	public int getBgCode() {
		return bgCode;
	}

	public Gate getWeight() {
		return weight;
	}

	public void setWeight(final Gate weight) {
		this.weight = nz(weight);
	}

	public Gate getBright() {
		return bright;
	}

	public void setBright(final Gate bright) {
		this.bright = nz(bright);
	}

	public Gate getItalic() {
		return italic;
	}

	public void setItalic(final Gate italic) {
		this.italic = nz(italic);
	}

	public Gate getUnderline() {
		return underline;
	}

	public void setUnderline(final Gate underline) {
		this.underline = nz(underline);
	}

	public Gate getDoubleUnderline() {
		return doubleUnderline;
	}

	public void setDoubleUnderline(final Gate doubleUnderline) {
		this.doubleUnderline = nz(doubleUnderline);
	}

	public Gate getStrike() {
		return strike;
	}

	public void setStrike(final Gate strike) {
		this.strike = nz(strike);
	}

	public Gate getReverse() {
		return reverse;
	}

	public void setReverse(final Gate reverse) {
		this.reverse = nz(reverse);
	}

	public Gate getFaint() {
		return faint;
	}

	public void setFaint(final Gate faint) {
		this.faint = nz(faint);
	}

	public Gate getBlink() {
		return blink;
	}

	public void setBlink(final Gate blink) {
		this.blink = nz(blink);
	}

	public Gate getHref() {
		return href;
	}

	public void setHref(final Gate href, final String value) {
		this.href = nz(href);
		this.hrefValue = value == null ? "" : value;
	}

	public String getHrefValue() {
		return hrefValue;
	}

	public String getText() {
		return text;
	}

	public void setText(final String text) {
		this.text = text == null ? "" : text;
		if (this.text.length() > 0 && textGate == Gate.IGNORE) {
			textGate = Gate.REQUIRE;
		}
		if (this.text.length() == 0) {
			textGate = Gate.IGNORE;
		}
	}

	public Gate getTextGate() {
		return textGate;
	}

	public void setTextGate(final Gate textGate) {
		if (text.length() == 0) {
			this.textGate = Gate.IGNORE;
			return;
		}
		this.textGate = nz(textGate);
	}

	public boolean isTextRegex() {
		return textRegex;
	}

	public void setTextRegex(final boolean textRegex) {
		this.textRegex = textRegex;
	}

	private static Gate nz(final Gate g) {
		return g == null ? Gate.IGNORE : g;
	}

	int ignoredOnBits() {
		int bits = 0;
		if (weight == Gate.IGNORE) {
			bits |= SgrStyle.WEIGHT;
		}
		if (italic == Gate.IGNORE) {
			bits |= SgrStyle.ITALIC;
		}
		if (underline == Gate.IGNORE) {
			bits |= SgrStyle.UNDERLINE;
		}
		if (doubleUnderline == Gate.IGNORE) {
			bits |= SgrStyle.DOUBLE_UNDERLINE;
		}
		if (strike == Gate.IGNORE) {
			bits |= SgrStyle.STRIKE;
		}
		if (reverse == Gate.IGNORE) {
			bits |= SgrStyle.REVERSE;
		}
		if (faint == Gate.IGNORE) {
			bits |= SgrStyle.FAINT;
		}
		if (blink == Gate.IGNORE) {
			bits |= SgrStyle.BLINK | SgrStyle.FAST_BLINK;
		}
		return bits;
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof StyleMatchSpec)) {
			return false;
		}
		StyleMatchSpec s = (StyleMatchSpec) o;
		return combine == s.combine && extras == s.extras && colorMode == s.colorMode
				&& fgGate == s.fgGate && fgSpace == s.fgSpace && fgCode == s.fgCode
				&& bgGate == s.bgGate && bgSpace == s.bgSpace && bgCode == s.bgCode
				&& weight == s.weight && bright == s.bright && italic == s.italic
				&& underline == s.underline && doubleUnderline == s.doubleUnderline
				&& strike == s.strike && reverse == s.reverse && faint == s.faint
				&& blink == s.blink && href == s.href
				&& hrefValue.equals(s.hrefValue) && text.equals(s.text)
				&& textGate == s.textGate
				&& textRegex == s.textRegex;
	}

	@Override
	public int hashCode() {
		return combine.hashCode() + 31 * extras.hashCode() + fgCode + bgCode;
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(final Parcel out, final int flags) {
		out.writeInt(combine.ordinal());
		out.writeInt(extras.ordinal());
		out.writeInt(colorMode.ordinal());
		out.writeInt(fgGate.ordinal());
		out.writeInt(fgSpace.ordinal());
		out.writeInt(fgCode);
		out.writeInt(bgGate.ordinal());
		out.writeInt(bgSpace.ordinal());
		out.writeInt(bgCode);
		out.writeInt(weight.ordinal());
		out.writeInt(bright.ordinal());
		out.writeInt(italic.ordinal());
		out.writeInt(underline.ordinal());
		out.writeInt(doubleUnderline.ordinal());
		out.writeInt(strike.ordinal());
		out.writeInt(reverse.ordinal());
		out.writeInt(faint.ordinal());
		out.writeInt(blink.ordinal());
		out.writeInt(href.ordinal());
		out.writeString(hrefValue);
		out.writeString(text);
		out.writeInt(textRegex ? 1 : 0);
		out.writeInt(textGate.ordinal());
	}

	public static final Parcelable.Creator<StyleMatchSpec> CREATOR =
			new Parcelable.Creator<StyleMatchSpec>() {
				public StyleMatchSpec createFromParcel(final Parcel in) {
					StyleMatchSpec s = new StyleMatchSpec();
					s.readFromParcel(in);
					return s;
				}

				public StyleMatchSpec[] newArray(final int size) {
					return new StyleMatchSpec[size];
				}
			};

	void readFromParcel(final Parcel in) {
		combine = Combine.values()[clamp(in.readInt(), Combine.values().length)];
		extras = Extras.values()[clamp(in.readInt(), Extras.values().length)];
		colorMode = ColorMode.values()[clamp(in.readInt(), ColorMode.values().length)];
		fgGate = Gate.values()[clamp(in.readInt(), Gate.values().length)];
		fgSpace = ColorSpace.values()[clamp(in.readInt(), ColorSpace.values().length)];
		fgCode = in.readInt();
		bgGate = Gate.values()[clamp(in.readInt(), Gate.values().length)];
		bgSpace = ColorSpace.values()[clamp(in.readInt(), ColorSpace.values().length)];
		bgCode = in.readInt();
		weight = Gate.values()[clamp(in.readInt(), Gate.values().length)];
		bright = Gate.values()[clamp(in.readInt(), Gate.values().length)];
		italic = Gate.values()[clamp(in.readInt(), Gate.values().length)];
		underline = Gate.values()[clamp(in.readInt(), Gate.values().length)];
		doubleUnderline = Gate.values()[clamp(in.readInt(), Gate.values().length)];
		strike = Gate.values()[clamp(in.readInt(), Gate.values().length)];
		reverse = Gate.values()[clamp(in.readInt(), Gate.values().length)];
		faint = Gate.values()[clamp(in.readInt(), Gate.values().length)];
		blink = Gate.values()[clamp(in.readInt(), Gate.values().length)];
		href = Gate.values()[clamp(in.readInt(), Gate.values().length)];
		hrefValue = in.readString();
		if (hrefValue == null) {
			hrefValue = "";
		}
		text = in.readString();
		if (text == null) {
			text = "";
		}
		textRegex = in.readInt() != 0;
		textGate = Gate.values()[clamp(in.readInt(), Gate.values().length)];
	}

	private static int clamp(final int v, final int n) {
		if (v < 0 || v >= n) {
			return 0;
		}
		return v;
	}
}

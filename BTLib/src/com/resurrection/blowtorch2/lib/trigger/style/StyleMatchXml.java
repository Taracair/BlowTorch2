package com.resurrection.blowtorch2.lib.trigger.style;

import java.io.IOException;

import org.xml.sax.Attributes;
import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec.Gate;
import com.resurrection.blowtorch2.lib.trigger.style.StyleSnapshot.ColorSpace;

import android.sax.Element;
import android.sax.StartElementListener;

public final class StyleMatchXml {

	private StyleMatchXml() {
	}

	public static void registerListeners(final Element trigger,
			final TriggerData current) {
		Element root = trigger.getChild(BasePluginParser.TAG_STYLE_MATCH);
		root.setStartElementListener(new StartElementListener() {
			public void start(final Attributes a) {
				StyleMatchSpec spec = new StyleMatchSpec();
				spec.setCombine(StyleMatchSpec.Combine.fromXml(
						a.getValue("", BasePluginParser.ATTR_STYLE_COMBINE)));
				spec.setExtras(StyleMatchSpec.Extras.fromXml(
						a.getValue("", BasePluginParser.ATTR_STYLE_EXTRAS)));
				spec.setColorMode(StyleMatchSpec.ColorMode.fromXml(
						a.getValue("", BasePluginParser.ATTR_STYLE_COLOR_MODE)));
				String text = a.getValue("", BasePluginParser.ATTR_STYLE_TEXT);
				if (text != null) {
					spec.setText(text);
				}
				String textGate = a.getValue("", BasePluginParser.ATTR_STYLE_TEXT_GATE);
				if (textGate != null) {
					spec.setTextGate(Gate.fromXml(textGate));
				}
				spec.setTextRegex("true".equalsIgnoreCase(
						a.getValue("", BasePluginParser.ATTR_STYLE_TEXT_REGEX)));
				current.setStyleMatch(spec);
			}
		});
		Element layer = root.getChild(BasePluginParser.TAG_STYLE_LAYER);
		layer.setStartElementListener(new StartElementListener() {
			public void start(final Attributes a) {
				StyleMatchSpec spec = current.getStyleMatch();
				if (spec == null) {
					spec = new StyleMatchSpec();
					current.setStyleMatch(spec);
				}
				String name = a.getValue("", BasePluginParser.ATTR_NAME);
				Gate gate = Gate.fromXml(a.getValue("", BasePluginParser.ATTR_STYLE_GATE));
				applyLayer(spec, name, gate, a);
			}
		});
	}

	static void applyLayer(final StyleMatchSpec spec, final String name,
			final Gate gate, final Attributes a) {
		if (name == null) {
			return;
		}
		if ("fg".equals(name)) {
			spec.setFg(gate, space(a), code(a, 37));
		} else if ("bg".equals(name)) {
			spec.setBg(gate, space(a), code(a, 40));
		} else if ("weight".equals(name)) {
			spec.setWeight(gate);
		} else if ("bright".equals(name)) {
			spec.setBright(gate);
		} else if ("italic".equals(name)) {
			spec.setItalic(gate);
		} else if ("underline".equals(name)) {
			spec.setUnderline(gate);
		} else if ("doubleUnderline".equals(name)) {
			spec.setDoubleUnderline(gate);
		} else if ("strike".equals(name)) {
			spec.setStrike(gate);
		} else if ("reverse".equals(name)) {
			spec.setReverse(gate);
		} else if ("faint".equals(name)) {
			spec.setFaint(gate);
		} else if ("blink".equals(name)) {
			spec.setBlink(gate);
		} else if ("href".equals(name)) {
			String href = a.getValue("", BasePluginParser.ATTR_STYLE_HREF);
			spec.setHref(gate, href);
		}
	}

	private static ColorSpace space(final Attributes a) {
		String raw = a.getValue("", BasePluginParser.ATTR_STYLE_SPACE);
		if (raw == null) {
			return ColorSpace.ANSI16;
		}
		if ("xterm".equalsIgnoreCase(raw) || "xterm256".equalsIgnoreCase(raw)) {
			return ColorSpace.XTERM256;
		}
		if ("rgb".equalsIgnoreCase(raw)) {
			return ColorSpace.RGB;
		}
		return ColorSpace.ANSI16;
	}

	private static int code(final Attributes a, final int fallback) {
		String raw = a.getValue("", BasePluginParser.ATTR_STYLE_CODE);
		if (raw == null || raw.length() == 0) {
			return fallback;
		}
		try {
			if (raw.startsWith("#") && raw.length() == 7) {
				return Integer.parseInt(raw.substring(1), 16);
			}
			return Integer.parseInt(raw);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public static void saveToXML(final XmlSerializer out, final StyleMatchSpec spec)
			throws IOException {
		if (spec == null || !spec.isActive()) {
			return;
		}
		out.startTag("", BasePluginParser.TAG_STYLE_MATCH);
		out.attribute("", BasePluginParser.ATTR_STYLE_COMBINE, spec.getCombine().xmlValue());
		out.attribute("", BasePluginParser.ATTR_STYLE_EXTRAS, spec.getExtras().xmlValue());
		out.attribute("", BasePluginParser.ATTR_STYLE_COLOR_MODE,
				spec.getColorMode().xmlValue());
		if (spec.getText() != null && spec.getText().length() > 0) {
			out.attribute("", BasePluginParser.ATTR_STYLE_TEXT, spec.getText());
			if (spec.getTextGate() == Gate.FORBID) {
				out.attribute("", BasePluginParser.ATTR_STYLE_TEXT_GATE, "forbid");
			}
			if (spec.isTextRegex()) {
				out.attribute("", BasePluginParser.ATTR_STYLE_TEXT_REGEX, "true");
			}
		}
		writeFlag(out, "fg", spec.getFgGate(), spec.getFgSpace(), spec.getFgCode(), null);
		writeFlag(out, "bg", spec.getBgGate(), spec.getBgSpace(), spec.getBgCode(), null);
		writeFlag(out, "weight", spec.getWeight(), null, 0, null);
		writeFlag(out, "bright", spec.getBright(), null, 0, null);
		writeFlag(out, "italic", spec.getItalic(), null, 0, null);
		writeFlag(out, "underline", spec.getUnderline(), null, 0, null);
		writeFlag(out, "doubleUnderline", spec.getDoubleUnderline(), null, 0, null);
		writeFlag(out, "strike", spec.getStrike(), null, 0, null);
		writeFlag(out, "reverse", spec.getReverse(), null, 0, null);
		writeFlag(out, "faint", spec.getFaint(), null, 0, null);
		writeFlag(out, "blink", spec.getBlink(), null, 0, null);
		writeFlag(out, "href", spec.getHref(), null, 0, spec.getHrefValue());
		out.endTag("", BasePluginParser.TAG_STYLE_MATCH);
	}

	private static void writeFlag(final XmlSerializer out, final String name,
			final Gate gate, final ColorSpace space, final int code, final String href)
			throws IOException {
		if (gate == Gate.IGNORE) {
			return;
		}
		out.startTag("", BasePluginParser.TAG_STYLE_LAYER);
		out.attribute("", BasePluginParser.ATTR_NAME, name);
		out.attribute("", BasePluginParser.ATTR_STYLE_GATE, gate.xmlValue());
		if (space != null && ("fg".equals(name) || "bg".equals(name))) {
			out.attribute("", BasePluginParser.ATTR_STYLE_SPACE, spaceXml(space));
			out.attribute("", BasePluginParser.ATTR_STYLE_CODE, Integer.toString(code));
		}
		if (href != null && href.length() > 0) {
			out.attribute("", BasePluginParser.ATTR_STYLE_HREF, href);
		}
		out.endTag("", BasePluginParser.TAG_STYLE_LAYER);
	}

	private static String spaceXml(final ColorSpace space) {
		if (space == ColorSpace.XTERM256) {
			return "xterm";
		}
		if (space == ColorSpace.RGB) {
			return "rgb";
		}
		return "ansi";
	}
}

package com.resurrection.blowtorch2.lib.responder.color;

import java.io.IOException;

import org.xml.sax.Attributes;
import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginSettings;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.Element;
import android.sax.TextElementListener;

public final class ColorActionParser {
	public static void registerListeners(Element root,TriggerData current_trigger) {
		Element color = root.getChild(BasePluginParser.TAG_COLORACTION);
		color.setTextElementListener(new ColorElementListener(current_trigger));
	}
	
	public static void saveColorActionToXML(XmlSerializer out,ColorAction r) throws IllegalArgumentException, IllegalStateException, IOException {
		out.startTag("", BasePluginParser.TAG_COLORACTION);
		TriggerColorPaint p = r.getPaint();
		out.attribute("", "text", p.formatTextAttr());
		out.attribute("", "background", p.formatBackgroundAttr());
		String bgMode = p.formatBackgroundModeAttr();
		if (bgMode != null) {
			out.attribute("", "backgroundMode", bgMode);
		}
		writeStyle(out, "bold", p.hasStyle(TriggerColorPaint.STYLE_BOLD));
		writeStyle(out, "faint", p.hasStyle(TriggerColorPaint.STYLE_FAINT));
		writeStyle(out, "italic", p.hasStyle(TriggerColorPaint.STYLE_ITALIC));
		writeStyle(out, "underline", p.hasStyle(TriggerColorPaint.STYLE_UNDERLINE));
		writeStyle(out, "reverse", p.hasStyle(TriggerColorPaint.STYLE_REVERSE));
		writeStyle(out, "strike", p.hasStyle(TriggerColorPaint.STYLE_STRIKE));
		out.endTag("", BasePluginParser.TAG_COLORACTION);
	}

	private static void writeStyle(XmlSerializer out, String name, boolean on)
			throws IOException {
		if (on) {
			out.attribute("", name, "true");
		}
	}
}

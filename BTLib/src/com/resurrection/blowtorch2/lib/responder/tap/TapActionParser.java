package com.resurrection.blowtorch2.lib.responder.tap;

import java.io.IOException;

import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.Element;

public final class TapActionParser {

	public static void registerListeners(Element root, TriggerData current_trigger) {
		Element tap = root.getChild(BasePluginParser.TAG_TAPACTION);
		tap.setTextElementListener(new TapElementListener(current_trigger));
	}

	public static void saveTapActionToXML(XmlSerializer out, TapAction r)
			throws IllegalArgumentException, IllegalStateException, IOException {
		out.startTag("", BasePluginParser.TAG_TAPACTION);
		out.attribute("", "command", r.getCommand());
		out.attribute("", "underline", Boolean.toString(r.isUnderline()));
		out.attribute("", "bold", Boolean.toString(r.isBold()));
		out.attribute("", "frame", Boolean.toString(r.isFrame()));
		out.attribute("", "recolor", Boolean.toString(r.isRecolor()));
		out.attribute("", "color", Integer.toString(r.getColor()));
		out.endTag("", BasePluginParser.TAG_TAPACTION);
	}
}

package com.resurrection.blowtorch2.lib.responder.tap;

import java.io.IOException;

import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.Element;

public final class TapActionParser {

	public static void registerListeners(Element root, TriggerData current_trigger) {
		Element tap = root.getChild(BasePluginParser.TAG_TAPACTION);
		// A start listener, not a text one: an element with an end-text listener
		// cannot be given children, and the extra commands are children.
		tap.setStartElementListener(new TapElementListener(current_trigger));
		// Second and further commands arrive as children, after the action itself
		// has been added by the listener above; TapCommandElementListener appends
		// to it. A file written before the menu existed simply has no children.
		tap.getChild(BasePluginParser.TAG_TAPCOMMAND)
				.setEndTextElementListener(new TapCommandElementListener(current_trigger));
	}

	public static void saveTapActionToXML(XmlSerializer out, TapAction r)
			throws IllegalArgumentException, IllegalStateException, IOException {
		out.startTag("", BasePluginParser.TAG_TAPACTION);
		// The first command stays where it always was, so a trigger with one
		// command reads back the same in any version of the app.
		out.attribute("", "command", r.getCommand());
		out.attribute("", "underline", Boolean.toString(r.isUnderline()));
		out.attribute("", "bold", Boolean.toString(r.isBold()));
		out.attribute("", "frame", Boolean.toString(r.isFrame()));
		java.util.List<String> all = r.getCommands();
		for (int i = 1; i < all.size(); i++) {
			out.startTag("", BasePluginParser.TAG_TAPCOMMAND);
			out.text(all.get(i));
			out.endTag("", BasePluginParser.TAG_TAPCOMMAND);
		}
		out.endTag("", BasePluginParser.TAG_TAPACTION);
	}
}

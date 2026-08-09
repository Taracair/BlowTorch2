package com.resurrection.blowtorch2.lib.responder.sound;

import java.io.IOException;

import org.xmlpull.v1.XmlSerializer;

import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.Element;

public class SoundResponderParser {

	public static void registerListeners(Element root, Object obj,
			TriggerData current_trigger, TimerData current_timer) {
		Element sound = root.getChild(BasePluginParser.TAG_SOUNDRESPONDER);
		sound.setStartElementListener(
				new SoundElementListener(obj, current_trigger, current_timer));
	}

	public static void saveSoundResponderToXML(XmlSerializer out, SoundResponder r)
			throws IllegalArgumentException, IllegalStateException, IOException {
		out.startTag("", BasePluginParser.TAG_SOUNDRESPONDER);
		out.attribute("", BasePluginParser.ATTR_SOUNDPATH, r.getSoundPath());
		out.attribute("", BasePluginParser.ATTR_SOUNDGAP,
				Integer.toString(r.getMinGapMs()));
		out.attribute("", BasePluginParser.ATTR_SOUNDVOLUME,
				Integer.toString(r.getVolumePercent()));
		out.attribute("", BasePluginParser.ATTR_SOUNDWARN,
				Boolean.toString(r.getWarnWhenSilent()));
		out.attribute("", BasePluginParser.ATTR_FIRETYPE, r.getFireType().getString());
		out.endTag("", BasePluginParser.TAG_SOUNDRESPONDER);
	}
}

package com.resurrection.blowtorch2.lib.responder.tap;

import org.xml.sax.Attributes;

import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.TextElementListener;

public class TapElementListener implements TextElementListener {

	TriggerData current_trigger = null;

	public TapElementListener(TriggerData current_trigger) {
		this.current_trigger = current_trigger;
	}

	public void start(Attributes a) {
		TapAction tmp = new TapAction();
		if (a.getValue("", "command") != null) {
			tmp.setCommand(a.getValue("", "command"));
		}
		if (a.getValue("", "underline") != null) {
			tmp.setUnderline(Boolean.parseBoolean(a.getValue("", "underline")));
		}
		if (a.getValue("", "bold") != null) {
			tmp.setBold(Boolean.parseBoolean(a.getValue("", "bold")));
		}
		if (a.getValue("", "frame") != null) {
			tmp.setFrame(Boolean.parseBoolean(a.getValue("", "frame")));
		}
		if (a.getValue("", "recolor") != null) {
			tmp.setRecolor(Boolean.parseBoolean(a.getValue("", "recolor")));
		}
		if (a.getValue("", "color") != null) {
			try {
				tmp.setColor(Integer.parseInt(a.getValue("", "color")));
			} catch (NumberFormatException e) {
				// Leave the default rather than dropping the whole trigger.
			}
		}
		current_trigger.getResponders().add(tmp.copy());
	}

	public void end(String body) {
	}
}

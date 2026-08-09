package com.resurrection.blowtorch2.lib.responder.tap;

import org.xml.sax.Attributes;

import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.StartElementListener;

/**
 * Reads the tap action's own attributes. Deliberately a start listener and not
 * a text listener: {@code android.sax.Element.getChild} refuses to add a child
 * to an element that has an end-text listener ("This element already has an end
 * text element listener. It cannot have children."), and {@code <tap>} needs
 * children for its second and further commands. The tag never had a body.
 */
public class TapElementListener implements StartElementListener {

	TriggerData current_trigger = null;

	public TapElementListener(TriggerData current_trigger) {
		this.current_trigger = current_trigger;
	}

	public void start(Attributes a) {
		TapAction tmp = new TapAction();
		if (a.getValue("", "command") != null) {
			tmp.setCommand(a.getValue("", "command"));
		}
		if (a.getValue("", "tapsends") != null) {
			tmp.setTapSendsFirst(Boolean.parseBoolean(a.getValue("", "tapsends")));
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
		if (a.getValue("", "group") != null) {
			try {
				tmp.setGroup(Integer.parseInt(a.getValue("", "group")));
			} catch (NumberFormatException e) {
				// Whole match, the default, rather than dropping the trigger.
			}
		}
		// "recolor" and "color" were written by an earlier build. Colouring a
		// word is a colour trigger's job, so they are read by nobody now —
		// unknown attributes are simply ignored here.
		current_trigger.getResponders().add(tmp.copy());
	}
}

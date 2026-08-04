package com.resurrection.blowtorch2.lib.responder.tap;

import java.util.List;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

import android.sax.EndTextElementListener;

/**
 * A second, third, … command on a tap action. The first one is an attribute on
 * the tap tag itself and is read by {@link TapElementListener}; SAX hands us
 * the children afterwards, so the action is already on the trigger's responder
 * list and we append to it.
 */
public class TapCommandElementListener implements EndTextElementListener {

	private final TriggerData current_trigger;

	public TapCommandElementListener(TriggerData current_trigger) {
		this.current_trigger = current_trigger;
	}

	public void end(String body) {
		if (body == null || body.trim().length() == 0 || current_trigger == null) {
			return;
		}
		List<TriggerResponder> responders = current_trigger.getResponders();
		if (responders == null || responders.isEmpty()) {
			return;
		}
		TriggerResponder last = responders.get(responders.size() - 1);
		if (last instanceof TapAction) {
			((TapAction) last).addCommand(body);
		}
	}
}

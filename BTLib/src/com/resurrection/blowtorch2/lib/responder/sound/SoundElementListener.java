package com.resurrection.blowtorch2.lib.responder.sound;

import org.xml.sax.Attributes;

import android.sax.StartElementListener;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BasePluginParser;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

public class SoundElementListener implements StartElementListener {

	TriggerData current_trigger = null;
	TimerData current_timer = null;
	Object selector = null;

	public SoundElementListener(Object selector, TriggerData current_trigger,
			TimerData current_timer) {
		this.current_trigger = current_trigger;
		this.selector = selector;
		this.current_timer = current_timer;
	}

	public void start(Attributes attributes) {
		SoundResponder r = new SoundResponder();
		r.setSoundPath(attributes.getValue("", BasePluginParser.ATTR_SOUNDPATH));
		r.setMinGapMs(intOr(attributes.getValue("", BasePluginParser.ATTR_SOUNDGAP),
				SoundResponder.DEFAULT_MIN_GAP_MS));
		r.setVolumePercent(intOr(attributes.getValue("", BasePluginParser.ATTR_SOUNDVOLUME),
				SoundResponder.DEFAULT_VOLUME_PERCENT));
		String fireType = attributes.getValue("", BasePluginParser.ATTR_FIRETYPE);
		if (fireType == null) {
			fireType = "";
		}
		if (fireType.equals(TriggerResponder.FIRE_WINDOW_OPEN)) {
			r.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_OPEN);
		} else if (fireType.equals(TriggerResponder.FIRE_WINDOW_CLOSED)) {
			r.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_CLOSED);
		} else if (fireType.equals(TriggerResponder.FIRE_NEVER)) {
			r.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_NEVER);
		} else {
			r.setFireType(TriggerResponder.FIRE_WHEN.WINDOW_BOTH);
		}

		if (selector instanceof TriggerData) {
			current_trigger.getResponders().add(r.copy());
		} else if (selector instanceof TimerData) {
			current_timer.getResponders().add(r.copy());
		}
	}

	/**
	 * A number from the file, or the default when it is missing or nonsense.
	 *
	 * <p>An older profile has neither attribute, and a hand-edited one can have
	 * anything. Falling back keeps a trigger that plays a sound rather than one
	 * that refuses to load.
	 */
	private static int intOr(final String raw, final int fallback) {
		if (raw == null || raw.length() == 0) {
			return fallback;
		}
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}

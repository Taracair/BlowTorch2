package com.resurrection.blowtorch2.lib.service.plugin.settings;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class SettingsGroupBooleanUpdateTest {

	@Test
	public void updateBooleanReachesANestedOption() {
		SettingsGroup root = new SettingsGroup();
		SettingsGroup input = new SettingsGroup();
		SettingsGroup gestures = new SettingsGroup();
		BooleanOption show = new BooleanOption();
		show.setKey("global_gesture_show_mode");
		show.setValue(Boolean.TRUE);
		gestures.addOption(show);
		input.addOption(gestures);
		root.addOption(input);

		root.updateBoolean("global_gesture_show_mode", false);

		assertEquals(Boolean.FALSE, show.getValue());
	}

	@Test
	public void updateBooleanStillWritesTheTreeWhenTheLookupMapMisses() throws Exception {
		SettingsGroup root = new SettingsGroup();
		SettingsGroup gestures = new SettingsGroup();
		BooleanOption show = new BooleanOption();
		show.setKey("global_gesture_show_mode");
		show.setValue(Boolean.TRUE);
		gestures.addOption(show);
		root.addOption(gestures);

		Field map = SettingsGroup.class.getDeclaredField("optionsMap");
		map.setAccessible(true);
		@SuppressWarnings("unchecked")
		Map<String, Option> optionsMap = (Map<String, Option>) map.get(root);
		optionsMap.remove("global_gesture_show_mode");

		root.updateBoolean("global_gesture_show_mode", false);

		assertEquals(Boolean.FALSE, show.getValue());
	}
}

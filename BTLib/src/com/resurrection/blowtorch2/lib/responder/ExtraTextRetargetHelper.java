package com.resurrection.blowtorch2.lib.responder;

import java.util.ArrayList;

import android.os.RemoteException;

import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsGroup;
import com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption;
import com.resurrection.blowtorch2.lib.window.ExtraTextSlot;
import com.resurrection.blowtorch2.lib.window.ExtraTextSlotsStore;

/**
 * Shared helpers for gag/replace retarget UI (extra text slot names + None).
 */
public final class ExtraTextRetargetHelper {

	private ExtraTextRetargetHelper() {
	}

	/** Empty / whitespace → null (no MESSAGE retarget). */
	public static String normalizeRetarget(final String raw) {
		if (raw == null) {
			return null;
		}
		String t = raw.trim();
		return t.length() == 0 ? null : t;
	}

	/** Labels for spinner: index 0 is "None", then known slot names. */
	public static ArrayList<String> buildSpinnerLabels(final IConnectionBinder service) {
		ArrayList<String> labels = new ArrayList<String>();
		labels.add("None");
		ArrayList<String> slots = loadSlotNames(service);
		for (int i = 0; i < slots.size(); i++) {
			labels.add(slots.get(i));
		}
		return labels;
	}

	public static ArrayList<String> loadSlotNames(final IConnectionBinder service) {
		ArrayList<String> names = new ArrayList<String>();
		if (service == null) {
			return names;
		}
		try {
			SettingsGroup sg = service.getSettings();
			if (sg == null) {
				return names;
			}
			Object o = sg.findOptionByKey(ExtraTextSlotsStore.SETTING_KEY);
			if (o instanceof StringOption) {
				Object val = ((StringOption) o).getValue();
				String json = val != null ? val.toString() : "[]";
				ArrayList<ExtraTextSlot> slots = ExtraTextSlotsStore.parse(json);
				for (int i = 0; i < slots.size(); i++) {
					ExtraTextSlot s = slots.get(i);
					if (s != null && s.getName() != null && s.getName().length() > 0) {
						names.add(s.getName());
					}
				}
			}
		} catch (RemoteException e) {
			// ignore — editor still allows custom typing
		} catch (Exception e) {
			// ignore
		}
		return names;
	}

	/**
	 * Spinner index for an existing retarget value. Unknown custom names stay on None (0)
	 * so the EditText keeps the custom text.
	 */
	public static int indexOfSlot(final ArrayList<String> labels, final String value) {
		if (labels == null || value == null || value.trim().length() == 0) {
			return 0;
		}
		String want = value.trim();
		for (int i = 1; i < labels.size(); i++) {
			if (want.equalsIgnoreCase(labels.get(i))) {
				return i;
			}
		}
		return 0;
	}
}

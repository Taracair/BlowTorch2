package com.resurrection.blowtorch2.lib.trigger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Cascade order: smaller {@link TriggerData#getSequence()} runs first, then
 * name A→Z (ignore case) so two triggers at the default 10 do not shuffle
 * with HashMap iteration.
 *
 * <p>{@code Plugin.sortTriggers} is the live sorter. Keep going? does not
 * choose this order; it only stops later triggers after a fire.
 */
public final class TriggerOrder {

	private TriggerOrder() {
	}

	public static final Comparator<TriggerData> COMPARATOR = new Comparator<TriggerData>() {
		@Override
		public int compare(final TriggerData a, final TriggerData b) {
			return TriggerOrder.compare(a, b);
		}
	};

	public static int compare(final TriggerData a, final TriggerData b) {
		if (a == b) {
			return 0;
		}
		if (a == null) {
			return 1;
		}
		if (b == null) {
			return -1;
		}
		int sa = a.getSequence();
		int sb = b.getSequence();
		if (sa != sb) {
			return sa < sb ? -1 : 1;
		}
		return nameOf(a).compareToIgnoreCase(nameOf(b));
	}

	/**
	 * Empty or unreadable editor text is {@link TriggerData#DEFAULT_SEQUENCE},
	 * never 0. Missing XML already loads as 10.
	 */
	public static int parseSequence(final String raw) {
		if (raw == null) {
			return TriggerData.DEFAULT_SEQUENCE;
		}
		String s = raw.trim();
		if (s.length() == 0) {
			return TriggerData.DEFAULT_SEQUENCE;
		}
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return TriggerData.DEFAULT_SEQUENCE;
		}
	}

	/**
	 * Where {@code typedName} at {@code sequence} would sit among {@code inSet}
	 * (one plugin — cascade does not merge worlds).
	 *
	 * @param editingName Name already in the set, excluded so a rename does not
	 *     list the old row as a neighbour. Null when this is a new trigger.
	 */
	public static String describeNeighbors(final List<TriggerData> inSet,
			final String editingName, final String typedName, final int sequence) {
		if (typedName == null || typedName.trim().length() == 0) {
			return "Give this trigger a name to see where it sits.";
		}
		String selfName = typedName.trim();
		ArrayList<TriggerData> all = new ArrayList<TriggerData>();
		if (inSet != null) {
			for (TriggerData t : inSet) {
				if (t == null || t.getName() == null) {
					continue;
				}
				if (t.getName().equals(selfName)) {
					continue;
				}
				if (editingName != null && t.getName().equals(editingName)) {
					continue;
				}
				all.add(t);
			}
		}
		TriggerData self = new TriggerData();
		self.setName(selfName);
		self.setSequence(sequence);
		all.add(self);
		Collections.sort(all, COMPARATOR);
		int idx = -1;
		for (int i = 0; i < all.size(); i++) {
			if (selfName.equals(all.get(i).getName())) {
				idx = i;
				break;
			}
		}
		if (idx < 0) {
			return "Give this trigger a name to see where it sits.";
		}
		if (all.size() == 1) {
			return "Only trigger in this set.";
		}
		String prev = idx > 0 ? all.get(idx - 1).getName() : null;
		String next = idx + 1 < all.size() ? all.get(idx + 1).getName() : null;
		String place;
		if (prev == null) {
			place = "Fires first in this set, before " + next + ".";
		} else if (next == null) {
			place = "Fires last in this set, after " + prev + ".";
		} else {
			place = "Fires after " + prev + ", before " + next + ".";
		}
		ArrayList<String> ties = new ArrayList<String>();
		for (TriggerData t : all) {
			if (t.getName().equals(selfName)) {
				continue;
			}
			if (t.getSequence() == sequence) {
				ties.add(t.getName());
			}
		}
		if (ties.isEmpty()) {
			return place;
		}
		return place + " Same sequence as " + joinNames(ties, 4)
				+ " — split the numbers if that order is wrong.";
	}

	static String joinNames(final List<String> names, final int max) {
		if (names == null || names.isEmpty()) {
			return "";
		}
		int shown = Math.min(max, names.size());
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < shown; i++) {
			if (i > 0) {
				out.append(", ");
			}
			out.append(names.get(i));
		}
		int more = names.size() - shown;
		if (more > 0) {
			out.append(" and ").append(more).append(" more");
		}
		return out.toString();
	}

	private static String nameOf(final TriggerData t) {
		return t.getName() == null ? "" : t.getName();
	}
}

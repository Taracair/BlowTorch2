package com.resurrection.blowtorch2.lib.launcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.resurrection.blowtorch2.lib.settings.SettingsFileNames;

/**
 * Names for servers brought in from an imported list.
 * A free name is kept. A taken name becomes "Name (2)", then "(3)".
 */
public final class ImportedServerNames {

	private static final Pattern NUMBERED = Pattern.compile("^(.*) \\((\\d+)\\)$");

	private ImportedServerNames() {
	}

	public static String allocate(String desired, Set<String> taken) {
		ArrayList<String> existing = new ArrayList<String>();
		if (taken != null) {
			existing.addAll(taken);
		}
		return assign(existing, Collections.singletonList(desired), false).get(0);
	}

	/**
	 * One new display name per incoming row, in the same order.
	 * Exact names that can stay are claimed before any "(2)" is issued, so
	 * "Foo (2)" is not given a file that "Foo2" in the same batch still needs.
	 * On replace, files already on the phone stay reserved unless the import
	 * keeps that same display name. A generated "(2)" never takes a file that
	 * already has a name. Starter Tutorial keeps its file.
	 */
	public static List<String> assign(Collection<String> existing, List<String> incoming, boolean replace) {
		ArrayList<String> incomingNames = new ArrayList<String>();
		if (incoming != null) {
			for (String name : incoming) {
				incomingNames.add(name == null || name.length() == 0 ? "Mud" : name);
			}
		}
		HashMap<String, HashSet<String>> diskOwner = new HashMap<String, HashSet<String>>();
		if (existing != null) {
			for (String name : existing) {
				if (name != null) {
					putOwner(diskOwner, name);
				}
			}
		}
		HashSet<String> claimed = new HashSet<String>();
		if (!replace && existing != null) {
			for (String name : existing) {
				if (name != null) {
					claimed.add(name);
				}
			}
		}
		if (replace) {
			claimed.add(BuiltinTutorial.DISPLAY_NAME);
			HashSet<String> tutorial = new HashSet<String>();
			tutorial.add(BuiltinTutorial.DISPLAY_NAME);
			diskOwner.put(SettingsFileNames.xmlFileName(BuiltinTutorial.DISPLAY_NAME), tutorial);
		}

		String[] assigned = new String[incomingNames.size()];
		boolean[] later = new boolean[incomingNames.size()];
		for (int i = 0; i < incomingNames.size(); i++) {
			String name = incomingNames.get(i);
			if (canKeepExact(name, claimed, diskOwner)) {
				assigned[i] = name;
				claim(name, claimed, diskOwner);
			} else {
				later[i] = true;
			}
		}
		for (int i = 0; i < incomingNames.size(); i++) {
			if (!later[i]) {
				continue;
			}
			String base = baseOf(incomingNames.get(i));
			int n = 2;
			String candidate = base + " (" + n + ")";
			while (!canNumber(candidate, claimed, diskOwner)) {
				n++;
				candidate = base + " (" + n + ")";
			}
			assigned[i] = candidate;
			claim(candidate, claimed, diskOwner);
		}
		ArrayList<String> result = new ArrayList<String>();
		for (String name : assigned) {
			result.add(name);
		}
		return result;
	}

	private static void putOwner(HashMap<String, HashSet<String>> diskOwner, String name) {
		String file = SettingsFileNames.xmlFileName(name);
		HashSet<String> owners = diskOwner.get(file);
		if (owners == null) {
			owners = new HashSet<String>();
			diskOwner.put(file, owners);
		}
		owners.add(name);
	}

	private static void claim(String name, HashSet<String> claimed, HashMap<String, HashSet<String>> diskOwner) {
		claimed.add(name);
		putOwner(diskOwner, name);
	}

	/**
	 * The incoming row may keep its own name when that name is free, or when
	 * this exact display already uses the settings file.
	 */
	private static boolean canKeepExact(String name, Set<String> claimed,
			HashMap<String, HashSet<String>> diskOwner) {
		if (!displayAllowed(name, claimed)) {
			return false;
		}
		HashSet<String> owners = diskOwner.get(SettingsFileNames.xmlFileName(name));
		return owners == null || owners.contains(name);
	}

	/**
	 * A generated "Name (2)" may only take a file nobody owns. An existing
	 * "Foo (2)" on the phone is not a free slot for a row that was named Foo.
	 */
	private static boolean canNumber(String name, Set<String> claimed,
			HashMap<String, HashSet<String>> diskOwner) {
		if (!displayAllowed(name, claimed)) {
			return false;
		}
		HashSet<String> owners = diskOwner.get(SettingsFileNames.xmlFileName(name));
		return owners == null || owners.isEmpty();
	}

	private static boolean displayAllowed(String name, Set<String> claimed) {
		if (claimed.contains(name)) {
			return false;
		}
		String file = SettingsFileNames.xmlFileName(name);
		return !".xml".equals(file) && !SettingsFileNames.isLauncherList(file);
	}

	/** "Foo (2)" shares a base with "Foo". A name without that suffix is its own base. */
	private static String baseOf(String name) {
		Matcher matcher = NUMBERED.matcher(name);
		if (matcher.matches() && matcher.group(1).length() > 0) {
			return matcher.group(1);
		}
		return name;
	}

	/** Display name, then host, then port. Null rows are dropped. */
	public static List<MudConnection> byName(Collection<MudConnection> rows) {
		ArrayList<MudConnection> ordered = new ArrayList<MudConnection>();
		if (rows == null) {
			return ordered;
		}
		for (MudConnection row : rows) {
			if (row != null) {
				ordered.add(row);
			}
		}
		Collections.sort(ordered, new Comparator<MudConnection>() {
			public int compare(MudConnection a, MudConnection b) {
				int name = text(a.getDisplayName()).compareToIgnoreCase(text(b.getDisplayName()));
				if (name != 0) {
					return name;
				}
				int host = text(a.getHostName()).compareToIgnoreCase(text(b.getHostName()));
				if (host != 0) {
					return host;
				}
				return text(a.getPortString()).compareTo(text(b.getPortString()));
			}
		});
		return ordered;
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}
}

package com.resurrection.blowtorch2.lib.button;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Picks {@code builtin:set/id} or {@code named:file.png} from a catalog and
 * the names in the icon folder. A slash with an image extension is a file in a
 * subfolder; any other slash is built-in. A bare name prefers one matching file.
 * No Android.
 */
public final class IconFaceResolver {

	private static final String[] EXTS = new String[] { ".png", ".svg", ".jpg", ".jpeg" };

	private IconFaceResolver() {
	}

	/** What the folder lookup was able to see. */
	public static final class Folder {
		public final boolean configured;
		public final boolean readable;
		public final List<String> names;

		private Folder(final boolean configured, final boolean readable, final List<String> names) {
			this.configured = configured;
			this.readable = readable;
			this.names = names;
		}

		public static Folder none() {
			return new Folder(false, true, Collections.<String>emptyList());
		}

		public static Folder unreadable() {
			return new Folder(true, false, Collections.<String>emptyList());
		}

		public static Folder of(final List<String> names) {
			List<String> copy = new ArrayList<String>();
			if (names != null) {
				copy.addAll(names);
			}
			return new Folder(true, true, copy);
		}
	}

	public static final class Outcome {
		public final String error;
		public final String spec;

		private Outcome(final String error, final String spec) {
			this.error = error;
			this.spec = spec;
		}

		public static Outcome error(final String error) {
			return new Outcome(error, null);
		}

		public static Outcome spec(final String spec) {
			return new Outcome(null, spec);
		}
	}

	public static Outcome resolve(final String token, final Map<String, List<String>> builtins,
			final Folder folder) {
		if (token == null || token.length() == 0) {
			return Outcome.error("Name the icon.");
		}
		Map<String, List<String>> catalog = builtins == null
				? Collections.<String, List<String>>emptyMap() : builtins;
		Folder files = folder == null ? Folder.none() : folder;
		int slash = token.indexOf('/');
		if (slash >= 0 && explicitFile(token)) {
			return pathFile(token, files);
		}
		if (slash >= 0) {
			Outcome builtin = builtinPath(token, catalog);
			if (builtin.error != null && files.configured
					&& findKey(catalog, token.substring(0, slash)) == null) {
				return Outcome.error(builtin.error
						+ " A file in a subfolder needs its extension: " + token + ".png.");
			}
			return builtin;
		}
		if (explicitFile(token)) {
			return exactFile(token, files);
		}
		if (files.configured && !files.readable) {
			return Outcome.error("Cannot read the icon folder.");
		}
		if (files.configured && files.readable) {
			List<String> matches = stemMatches(token, files.names);
			if (matches.size() > 1) {
				return Outcome.error(join(matches) + " are in the icon folder. Name the file.");
			}
			if (matches.size() == 1) {
				String named = ButtonFaceRef.namedFile(matches.get(0));
				if (named == null) {
					return Outcome.error("No file " + matches.get(0) + " in the icon folder.");
				}
				return Outcome.spec("named:" + named);
			}
		}
		return builtinBare(token, catalog);
	}

	private static Outcome builtinPath(final String token, final Map<String, List<String>> catalog) {
		int slash = token.indexOf('/');
		if (slash <= 0 || slash != token.lastIndexOf('/') || slash == token.length() - 1) {
			return Outcome.error("No built-in icon " + token + ".");
		}
		String setWant = token.substring(0, slash);
		String idWant = token.substring(slash + 1);
		String set = findKey(catalog, setWant);
		if (set == null) {
			return Outcome.error("No built-in icon " + token + ".");
		}
		String id = findId(catalog.get(set), idWant);
		if (id == null) {
			return Outcome.error("No built-in icon " + token + ".");
		}
		return Outcome.spec("builtin:" + set + "/" + id);
	}

	/** The folder list holds top-level names only; whether the file exists is checked when it is opened. */
	private static Outcome pathFile(final String token, final Folder files) {
		if (!files.configured) {
			return Outcome.error("No icon folder. .icon folder chooses one.");
		}
		if (!files.readable) {
			return Outcome.error("Cannot read the icon folder.");
		}
		String named = ButtonFaceRef.namedPath(token);
		if (named == null) {
			return Outcome.error("No file " + token + " in the icon folder.");
		}
		return Outcome.spec("named:" + named);
	}

	private static Outcome exactFile(final String token, final Folder files) {
		if (!files.configured) {
			return Outcome.error("No icon folder. .icon folder chooses one.");
		}
		if (!files.readable) {
			return Outcome.error("Cannot read the icon folder.");
		}
		String named = ButtonFaceRef.namedFile(token);
		if (named == null) {
			return Outcome.error("No file " + token + " in the icon folder.");
		}
		int count = 0;
		String found = null;
		for (int i = 0; i < files.names.size(); i++) {
			String name = files.names.get(i);
			if (token.equals(name)) {
				count++;
				found = name;
			}
		}
		if (count > 1) {
			return Outcome.error(token + " is in the icon folder more than once.");
		}
		if (count == 1) {
			return Outcome.spec("named:" + found);
		}
		return Outcome.error("No file " + token + " in the icon folder.");
	}

	private static Outcome builtinBare(final String token, final Map<String, List<String>> catalog) {
		List<String> sets = new ArrayList<String>();
		String id = null;
		List<String> setNames = new ArrayList<String>(catalog.keySet());
		Collections.sort(setNames);
		for (int i = 0; i < setNames.size(); i++) {
			String set = setNames.get(i);
			String found = findId(catalog.get(set), token);
			if (found != null) {
				sets.add(set);
				id = found;
			}
		}
		if (sets.size() == 1) {
			return Outcome.spec("builtin:" + sets.get(0) + "/" + id);
		}
		if (sets.isEmpty()) {
			return Outcome.error("No icon " + token + ".");
		}
		StringBuilder choices = new StringBuilder();
		for (int i = 0; i < sets.size(); i++) {
			if (i > 0) {
				choices.append(i == sets.size() - 1 ? " or " : ", ");
			}
			choices.append(sets.get(i)).append("/").append(id);
		}
		return Outcome.error(token + " is in " + join(sets) + ". Use " + choices + ".");
	}

	private static List<String> stemMatches(final String token, final List<String> names) {
		List<String> found = new ArrayList<String>();
		for (int e = 0; e < EXTS.length; e++) {
			for (int i = 0; i < names.size(); i++) {
				String name = names.get(i);
				if (name == null) {
					continue;
				}
				int dot = name.lastIndexOf('.');
				if (dot <= 0) {
					continue;
				}
				String stem = name.substring(0, dot);
				String ext = name.substring(dot).toLowerCase(Locale.US);
				if (stem.equalsIgnoreCase(token) && EXTS[e].equals(ext)
						&& ButtonFaceRef.namedFile(name) != null && !found.contains(name)) {
					found.add(name);
				}
			}
		}
		return found;
	}

	private static boolean explicitFile(final String token) {
		String lower = token.toLowerCase(Locale.US);
		for (int i = 0; i < EXTS.length; i++) {
			if (lower.endsWith(EXTS[i]) && lower.length() > EXTS[i].length()) {
				return true;
			}
		}
		return false;
	}

	private static String findKey(final Map<String, List<String>> catalog, final String want) {
		for (String key : catalog.keySet()) {
			if (key.equalsIgnoreCase(want)) {
				return key;
			}
		}
		return null;
	}

	private static String findId(final List<String> ids, final String want) {
		if (ids == null) {
			return null;
		}
		for (int i = 0; i < ids.size(); i++) {
			String id = ids.get(i);
			if (id != null && id.equalsIgnoreCase(want)) {
				return id;
			}
		}
		return null;
	}

	private static String join(final List<String> parts) {
		if (parts.size() == 1) {
			return parts.get(0);
		}
		if (parts.size() == 2) {
			return parts.get(0) + " and " + parts.get(1);
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				sb.append(i == parts.size() - 1 ? ", and " : ", ");
			}
			sb.append(parts.get(i));
		}
		return sb.toString();
	}
}

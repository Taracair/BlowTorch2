package com.resurrection.blowtorch2.lib.alias;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The alias the editor's New button stores: enabled, local echo inherited,
 * no set-variable rows. The map key drops one leading {@code ^} and one
 * trailing {@code $}; {@code pre} keeps them.
 */
public final class AliasCreate {

	private AliasCreate() {
	}

	public static String mapKey(String pre) {
		if (pre == null) {
			return "";
		}
		String key = pre;
		if (key.startsWith("^")) {
			key = key.substring(1);
		}
		if (key.endsWith("$")) {
			key = key.substring(0, key.length() - 1);
		}
		return key;
	}

	public static AliasData create(String pre, String post) {
		AliasData alias = new AliasData();
		alias.setPre(pre);
		alias.setPost(post);
		alias.setEnabled(true);
		alias.setLocalEcho(AliasLocalEcho.INHERIT);
		return alias;
	}

	/**
	 * Why this pair cannot be stored, or null when it can.
	 * {@code existing} is this plugin's alias map. {@code reserved} is the
	 * dot-command names ({@code map}, not {@code .map}).
	 */
	public static String rejection(String pre, String post,
			Map<String, AliasData> existing, Collection<String> reserved) {
		if (pre == null) {
			return "pattern is missing";
		}
		if (post == null) {
			return "replacement is missing";
		}
		String key = mapKey(pre);
		if (pre.length() == 0 || key.length() == 0) {
			return "pattern is empty";
		}
		if (post.length() == 0) {
			return "replacement is empty";
		}
		if (existing != null && existing.containsKey(key)) {
			return "\"" + key + "\" exists already";
		}
		if (reserved != null) {
			for (String name : reserved) {
				if (key.equals(name)) {
					return "\"" + key + "\" is reserved for a system command";
				}
			}
		}
		try {
			Pattern.compile(AliasPattern.wrapped(pre));
		} catch (PatternSyntaxException bad) {
			String desc = bad.getDescription() != null
					? bad.getDescription() + " (at position " + bad.getIndex() + ")"
					: bad.getMessage();
			return "\"" + pre + "\" is not a valid pattern: " + desc;
		}
		Map<String, AliasData> with = new HashMap<String, AliasData>();
		if (existing != null) {
			with.putAll(existing);
		}
		with.put(key, create(pre, post));
		String[] cycle = AliasEditorCycle.circularNames(with, key);
		if (cycle != null && cycle.length > 0) {
			StringBuilder names = new StringBuilder();
			for (int i = 0; i < cycle.length; i++) {
				if (i > 0) {
					names.append(", ");
				}
				names.append(cycle[i]);
			}
			return "circular reference with aliases: " + names;
		}
		return null;
	}
}

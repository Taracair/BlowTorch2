package com.resurrection.blowtorch2.lib.trigger.condition;

import java.util.HashMap;
import java.util.List;

import com.resurrection.blowtorch2.lib.alias.AliasData;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.Plugin;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;
import com.resurrection.blowtorch2.lib.trigger.condition.ConditionGroup.Op;

/**
 * Evaluates trigger/timer conditions after a pattern match (or timer fire) and
 * before responders run. Empty conditions = true (backward compatible).
 * Trigger/alias enabled gates only read {@code isEnabled()} (no recursive
 * condition evaluation).
 */
public final class ConditionEvaluator {

	private ConditionEvaluator() {
	}

	public static boolean evaluate(TriggerData trigger, Connection connection) {
		if (trigger == null) {
			return true;
		}
		return evaluate(trigger.getConditions(), connection);
	}

	public static boolean evaluate(ConditionGroup group, Connection connection) {
		if (group == null || group.isEmpty()) {
			return true;
		}
		if (connection == null) {
			return true;
		}
		List<ConditionLeaf> children = group.getChildren();
		Op op = group.getOp() != null ? group.getOp() : Op.AND;
		if (op == Op.OR) {
			for (ConditionLeaf leaf : children) {
				if (evaluateLeaf(leaf, connection)) {
					return true;
				}
			}
			return false;
		}
		for (ConditionLeaf leaf : children) {
			if (!evaluateLeaf(leaf, connection)) {
				return false;
			}
		}
		return true;
	}

	static boolean evaluateLeaf(ConditionLeaf leaf, Connection connection) {
		if (leaf == null || leaf.getType() == null) {
			return true;
		}
		switch (leaf.getType()) {
		case TRIGGER_ENABLED:
			return isTriggerEnabled(connection, leaf.getName(), leaf.getPlugin());
		case TRIGGER_DISABLED:
			return !isTriggerEnabled(connection, leaf.getName(), leaf.getPlugin());
		case ALIAS_ENABLED:
			return isAliasEnabled(connection, leaf.getName(), leaf.getPlugin());
		case ALIAS_DISABLED:
			return !isAliasEnabled(connection, leaf.getName(), leaf.getPlugin());
		case ALIAS_EQUALS: {
			AliasData alias = resolveAlias(connection, leaf.getName(), leaf.getPlugin());
			if (alias == null) {
				return false;
			}
			String actual = alias.getPost() != null ? alias.getPost() : "";
			String expected = leaf.getValue() != null ? leaf.getValue() : "";
			return actual.equals(expected);
		}
		case VARIABLE_EXISTS:
		case VARIABLE_EQUALS:
		case VARIABLE_BELOW:
		case VARIABLE_ABOVE:
			return evaluateVariable(leaf, connection.getSessionVariables());
		default:
			return true;
		}
	}

	/**
	 * Session-variable leaves without a {@link Connection}. Tests of variables
	 * must not call {@link #evaluate(ConditionGroup, Connection)} with a null
	 * connection: that path is true for the whole group.
	 */
	static boolean evaluateVariable(ConditionLeaf leaf, SessionVariableStore store) {
		if (leaf == null || leaf.getType() == null) {
			return true;
		}
		switch (leaf.getType()) {
		case VARIABLE_EXISTS:
			return store.exists(leaf.getName());
		case VARIABLE_EQUALS: {
			String actual = store.get(leaf.getName());
			if (actual == null) {
				return false;
			}
			String expected = leaf.getValue() != null ? leaf.getValue() : "";
			return actual.equals(expected);
		}
		case VARIABLE_BELOW:
		case VARIABLE_ABOVE:
			return compareVariableNumber(leaf, store);
		default:
			return true;
		}
	}

	/**
	 * Strict {@code <} / {@code >} after trim. Missing, non-numeric, NaN or
	 * infinity is false — not a pass, and not string equals.
	 */
	private static boolean compareVariableNumber(ConditionLeaf leaf,
			SessionVariableStore store) {
		if (store == null) {
			return false;
		}
		String actualRaw = store.get(leaf.getName());
		Double actual = parseFinite(actualRaw);
		Double expected = parseFinite(leaf.getValue());
		if (actual == null || expected == null) {
			return false;
		}
		if (leaf.getType() == ConditionType.VARIABLE_BELOW) {
			return actual.doubleValue() < expected.doubleValue();
		}
		return actual.doubleValue() > expected.doubleValue();
	}

	static Double parseFinite(String raw) {
		if (raw == null) {
			return null;
		}
		String t = raw.trim();
		if (t.length() == 0) {
			return null;
		}
		try {
			double d = Double.parseDouble(t);
			if (Double.isNaN(d) || Double.isInfinite(d)) {
				return null;
			}
			return Double.valueOf(d);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Public wrapper for {@code .trigger status} live gate diagnostics. */
	public static boolean evaluateLeafForDebug(ConditionLeaf leaf, Connection connection) {
		return evaluateLeaf(leaf, connection);
	}

	/**
	 * Resolve trigger by optional plugin + name, or {@code plugin:name} in name,
	 * matching {@code .trigger} conventions. Missing trigger → treated as disabled.
	 */
	static boolean isTriggerEnabled(Connection c, String name, String plugin) {
		TriggerData data = resolveTrigger(c, name, plugin);
		return data != null && data.isEnabled();
	}

	static boolean isAliasEnabled(Connection c, String name, String plugin) {
		AliasData data = resolveAlias(c, name, plugin);
		return data != null && data.isEnabled();
	}

	static TriggerData resolveTrigger(Connection c, String name, String plugin) {
		if (c == null || name == null) {
			return null;
		}
		String n = name.trim();
		String p = plugin != null ? plugin.trim() : "";
		if (n.length() == 0) {
			return null;
		}
		if (p.length() == 0) {
			int colon = n.indexOf(':');
			if (colon > 0) {
				p = n.substring(0, colon).trim();
				n = n.substring(colon + 1).trim();
				if (p.length() == 0 || n.length() == 0) {
					return null;
				}
			}
		}
		if (p.length() > 0) {
			HashMap<String, TriggerData> map = c.getPluginTriggers(p);
			if (map == null) {
				return null;
			}
			return map.get(n);
		}
		TriggerData main = c.getTriggers().get(n);
		if (main != null) {
			return main;
		}
		TriggerData found = null;
		for (Plugin pl : c.getPlugins()) {
			if (pl == null || pl.getSettings() == null) {
				continue;
			}
			HashMap<String, TriggerData> map = pl.getSettings().getTriggers();
			if (map == null) {
				continue;
			}
			TriggerData t = map.get(n);
			if (t != null) {
				if (found != null) {
					// Ambiguous across plugins — treat as not found / disabled.
					return null;
				}
				found = t;
			}
		}
		return found;
	}

	/**
	 * Same resolution rules as {@code .alias} / {@link #resolveTrigger}: main
	 * first, then a unique plugin match; {@code plugin:name} when set.
	 * Missing alias → treated as disabled.
	 */
	static AliasData resolveAlias(Connection c, String name, String plugin) {
		if (c == null || name == null) {
			return null;
		}
		String n = name.trim();
		String p = plugin != null ? plugin.trim() : "";
		if (n.length() == 0) {
			return null;
		}
		if (p.length() == 0) {
			int colon = n.indexOf(':');
			if (colon > 0) {
				p = n.substring(0, colon).trim();
				n = n.substring(colon + 1).trim();
				if (p.length() == 0 || n.length() == 0) {
					return null;
				}
			}
		}
		if (p.length() > 0) {
			HashMap<String, AliasData> map = c.getPluginAliases(p);
			if (map == null) {
				return null;
			}
			return map.get(n);
		}
		AliasData main = c.getAliases().get(n);
		if (main != null) {
			return main;
		}
		AliasData found = null;
		for (Plugin pl : c.getPlugins()) {
			if (pl == null || pl.getSettings() == null) {
				continue;
			}
			HashMap<String, AliasData> map = pl.getSettings().getAliases();
			if (map == null) {
				continue;
			}
			AliasData a = map.get(n);
			if (a != null) {
				if (found != null) {
					return null;
				}
				found = a;
			}
		}
		return found;
	}
}

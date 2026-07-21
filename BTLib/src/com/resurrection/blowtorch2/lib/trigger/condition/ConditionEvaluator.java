package com.resurrection.blowtorch2.lib.trigger.condition;

import java.util.HashMap;
import java.util.List;

import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.Plugin;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;
import com.resurrection.blowtorch2.lib.trigger.condition.ConditionGroup.Op;

/**
 * Evaluates trigger conditions after a pattern match and before responders fire.
 * Empty conditions = true (backward compatible). TriggerEnabled only reads
 * {@link TriggerData#isEnabled()} (no recursive condition evaluation).
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
		case VARIABLE_EXISTS:
			return connection.getSessionVariables().exists(leaf.getName());
		case VARIABLE_EQUALS: {
			String actual = connection.getSessionVariables().get(leaf.getName());
			if (actual == null) {
				return false;
			}
			String expected = leaf.getValue() != null ? leaf.getValue() : "";
			return actual.equals(expected);
		}
		default:
			return true;
		}
	}

	/**
	 * Resolve trigger by optional plugin + name, or {@code plugin:name} in name,
	 * matching {@code .trigger} conventions. Missing trigger → treated as disabled.
	 */
	static boolean isTriggerEnabled(Connection c, String name, String plugin) {
		TriggerData data = resolveTrigger(c, name, plugin);
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
}

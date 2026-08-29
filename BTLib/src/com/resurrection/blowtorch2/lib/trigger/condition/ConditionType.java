package com.resurrection.blowtorch2.lib.trigger.condition;

/**
 * Leaf condition kinds for trigger/timer gates.
 */
public enum ConditionType {
	TRIGGER_ENABLED("triggerEnabled"),
	TRIGGER_DISABLED("triggerDisabled"),
	ALIAS_ENABLED("aliasEnabled"),
	ALIAS_DISABLED("aliasDisabled"),
	ALIAS_EQUALS("aliasEquals"),
	VARIABLE_EQUALS("variableEquals"),
	VARIABLE_EXISTS("variableExists"),
	VARIABLE_BELOW("variableBelow"),
	VARIABLE_ABOVE("variableAbove");

	private final String xmlValue;

	ConditionType(String xmlValue) {
		this.xmlValue = xmlValue;
	}

	public String getXmlValue() {
		return xmlValue;
	}

	/** Parse XML {@code type} attribute; accepts camelCase and snake_case. */
	public static ConditionType fromXml(String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim();
		if (s.length() == 0) {
			return null;
		}
		if ("triggerEnabled".equalsIgnoreCase(s) || "trigger_enabled".equalsIgnoreCase(s)) {
			return TRIGGER_ENABLED;
		}
		if ("triggerDisabled".equalsIgnoreCase(s) || "trigger_disabled".equalsIgnoreCase(s)) {
			return TRIGGER_DISABLED;
		}
		if ("aliasEnabled".equalsIgnoreCase(s) || "alias_enabled".equalsIgnoreCase(s)) {
			return ALIAS_ENABLED;
		}
		if ("aliasDisabled".equalsIgnoreCase(s) || "alias_disabled".equalsIgnoreCase(s)) {
			return ALIAS_DISABLED;
		}
		if ("aliasEquals".equalsIgnoreCase(s) || "alias_equals".equalsIgnoreCase(s)) {
			return ALIAS_EQUALS;
		}
		if ("variableEquals".equalsIgnoreCase(s) || "variable_equals".equalsIgnoreCase(s)) {
			return VARIABLE_EQUALS;
		}
		if ("variableExists".equalsIgnoreCase(s) || "variable_exists".equalsIgnoreCase(s)) {
			return VARIABLE_EXISTS;
		}
		if ("variableBelow".equalsIgnoreCase(s) || "variable_below".equalsIgnoreCase(s)) {
			return VARIABLE_BELOW;
		}
		if ("variableAbove".equalsIgnoreCase(s) || "variable_above".equalsIgnoreCase(s)) {
			return VARIABLE_ABOVE;
		}
		return null;
	}

	public String displayLabel() {
		switch (this) {
		case TRIGGER_ENABLED:
			return "Only if trigger is ON";
		case TRIGGER_DISABLED:
			return "Only if trigger is OFF";
		case ALIAS_ENABLED:
			return "Only if alias is ON";
		case ALIAS_DISABLED:
			return "Only if alias is OFF";
		case ALIAS_EQUALS:
			return "Alias replacement equals";
		case VARIABLE_EQUALS:
			return "Variable equals";
		case VARIABLE_EXISTS:
			return "Variable exists";
		case VARIABLE_BELOW:
			return "Variable is below";
		case VARIABLE_ABOVE:
			return "Variable is above";
		default:
			return name();
		}
	}

	/** True when this leaf picks a trigger by name (and optional plugin). */
	public boolean isTriggerGate() {
		return this == TRIGGER_ENABLED || this == TRIGGER_DISABLED;
	}

	/** True when this leaf picks an alias by name (and optional plugin). */
	public boolean isAliasGate() {
		return this == ALIAS_ENABLED || this == ALIAS_DISABLED || this == ALIAS_EQUALS;
	}

	/** True when this leaf reads a session variable. */
	public boolean isVariableGate() {
		return this == VARIABLE_EQUALS || this == VARIABLE_EXISTS
				|| this == VARIABLE_BELOW || this == VARIABLE_ABOVE;
	}

	/** True when the leaf needs a free-text expected value. */
	public boolean needsExpectedValue() {
		return this == VARIABLE_EQUALS || this == ALIAS_EQUALS
				|| this == VARIABLE_BELOW || this == VARIABLE_ABOVE;
	}
}

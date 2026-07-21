package com.resurrection.blowtorch2.lib.trigger.condition;

/**
 * Leaf condition kinds for trigger gates (v1).
 */
public enum ConditionType {
	TRIGGER_ENABLED("triggerEnabled"),
	TRIGGER_DISABLED("triggerDisabled"),
	VARIABLE_EQUALS("variableEquals"),
	VARIABLE_EXISTS("variableExists");

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
		if ("variableEquals".equalsIgnoreCase(s) || "variable_equals".equalsIgnoreCase(s)) {
			return VARIABLE_EQUALS;
		}
		if ("variableExists".equalsIgnoreCase(s) || "variable_exists".equalsIgnoreCase(s)) {
			return VARIABLE_EXISTS;
		}
		return null;
	}

	public String displayLabel() {
		switch (this) {
		case TRIGGER_ENABLED:
			return "Trigger enabled";
		case TRIGGER_DISABLED:
			return "Trigger disabled";
		case VARIABLE_EQUALS:
			return "Variable equals";
		case VARIABLE_EXISTS:
			return "Variable exists";
		default:
			return name();
		}
	}
}

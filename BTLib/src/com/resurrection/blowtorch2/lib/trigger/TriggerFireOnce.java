package com.resurrection.blowtorch2.lib.trigger;

/**
 * How often a trigger may fire. Parcel codes 0/1 match the old boolean; 2 is
 * until the player next sends from the input bar.
 */
public enum TriggerFireOnce {
	OFF,
	UNTIL_ENABLE,
	UNTIL_SEND;

	public static TriggerFireOnce fromXml(final String raw) {
		if (raw == null) {
			return OFF;
		}
		if ("true".equals(raw) || "once".equals(raw)) {
			return UNTIL_ENABLE;
		}
		if ("send".equals(raw)) {
			return UNTIL_SEND;
		}
		return OFF;
	}

	/**
	 * Profile attribute, or null when the old writers omitted a false flag.
	 */
	public String xmlValue() {
		switch (this) {
		case UNTIL_ENABLE:
			return "true";
		case UNTIL_SEND:
			return "send";
		default:
			return null;
		}
	}

	public static TriggerFireOnce fromParcel(final int code) {
		if (code == 2) {
			return UNTIL_SEND;
		}
		if (code == 1) {
			return UNTIL_ENABLE;
		}
		return OFF;
	}

	public int toParcel() {
		switch (this) {
		case UNTIL_SEND:
			return 2;
		case UNTIL_ENABLE:
			return 1;
		default:
			return 0;
		}
	}

	public boolean quietsAfterFire() {
		return this != OFF;
	}
}

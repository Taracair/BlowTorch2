package com.resurrection.blowtorch2.lib.responder.setvariable;

/**
 * Payload for {@code Connection.MESSAGE_SET_VARIABLE}: name after {@code $n}
 * substitution, plus how to apply it.
 */
public final class SetVariableOp {

	public final String key;
	public final String value;
	public final String mode;
	public final boolean persist;

	public SetVariableOp(String key, String value, String mode, boolean persist) {
		this.key = key != null ? key : "";
		this.value = value != null ? value : "";
		this.mode = SetVariableApply.normalizeMode(mode);
		this.persist = persist;
	}
}

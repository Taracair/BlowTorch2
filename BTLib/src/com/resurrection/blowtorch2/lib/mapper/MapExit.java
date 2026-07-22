package com.resurrection.blowtorch2.lib.mapper;

/**
 * Directed edge between two map tiles: from {@code fromId} to {@code toId}
 * via a player command string.
 */
public class MapExit {

	private String fromId;
	private String toId;
	private String command;
	private boolean special;
	/** Optional reverse command hint (e.g. south for a north exit). May be null. */
	private String reverseCommand;
	/**
	 * Optional portal to another saved map (basename). When set, walking this
	 * exit loads that map instead of (or after) moving to {@code toId}.
	 */
	private String targetMap;

	public MapExit() {
	}

	public MapExit(String fromId, String toId, String command) {
		this(fromId, toId, command, false, null);
	}

	public MapExit(String fromId, String toId, String command, boolean special,
			String reverseCommand) {
		this.fromId = fromId;
		this.toId = toId;
		this.command = command;
		this.special = special;
		this.reverseCommand = reverseCommand;
	}

	public String getFromId() {
		return fromId;
	}

	public void setFromId(String fromId) {
		this.fromId = fromId;
	}

	public String getToId() {
		return toId;
	}

	public void setToId(String toId) {
		this.toId = toId;
	}

	public String getCommand() {
		return command;
	}

	public void setCommand(String command) {
		this.command = command;
	}

	public boolean isSpecial() {
		return special;
	}

	public void setSpecial(boolean special) {
		this.special = special;
	}

	public String getReverseCommand() {
		return reverseCommand;
	}

	public void setReverseCommand(String reverseCommand) {
		this.reverseCommand = reverseCommand;
	}

	public String getTargetMap() {
		return targetMap;
	}

	public void setTargetMap(String targetMap) {
		this.targetMap = targetMap;
	}
}

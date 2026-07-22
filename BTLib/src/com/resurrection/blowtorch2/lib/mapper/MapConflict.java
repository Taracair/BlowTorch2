package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A detected inconsistency on a map (asymmetry, grid collision, etc.).
 */
public class MapConflict {

	public enum Type {
		ASYMMETRIC,
		GRID_COLLISION,
		DUPLICATE_EXIT,
		GMCP_MISMATCH,
		/** GMCP wants a title or position change that may fight the user layout. */
		GMCP_LAYOUT
	}

	private String id;
	private Type type;
	private String message;
	private final List<String> tileIds = new ArrayList<String>();
	private boolean resolved;

	public MapConflict() {
		this.id = UUID.randomUUID().toString();
	}

	public MapConflict(Type type, String message, List<String> tileIds) {
		this.id = UUID.randomUUID().toString();
		this.type = type;
		this.message = message;
		if (tileIds != null) {
			this.tileIds.addAll(tileIds);
		}
		this.resolved = false;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public List<String> getTileIds() {
		return tileIds;
	}

	public void setTileIds(List<String> tileIds) {
		this.tileIds.clear();
		if (tileIds != null) {
			this.tileIds.addAll(tileIds);
		}
	}

	public boolean isResolved() {
		return resolved;
	}

	public void setResolved(boolean resolved) {
		this.resolved = resolved;
	}
}

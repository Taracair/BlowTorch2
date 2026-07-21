package com.resurrection.blowtorch2.lib.mapper;

import java.util.UUID;

/**
 * A drawing / navigation layer within a {@link MudMap}.
 * <p>
 * Root floors keep {@code anchorTileId == null}. Nested floors are opened from
 * a door/stairs tile on another level ({@code anchorTileId} + {@code anchorDir}).
 */
public class MapLevel {

	private String id;
	private String name;
	/** Sort order among levels (ascending). */
	private int index;
	/** Tile on another level that opens this floor (nullable for root). */
	private String anchorTileId;
	/** How you enter from the anchor: typically up/down/in/out (nullable). */
	private String anchorDir;

	public MapLevel() {
		this.id = UUID.randomUUID().toString();
	}

	public MapLevel(String id, String name, int index) {
		this(id, name, index, null, null);
	}

	public MapLevel(String id, String name, int index, String anchorTileId,
			String anchorDir) {
		this.id = id != null ? id : UUID.randomUUID().toString();
		this.name = name;
		this.index = index;
		this.anchorTileId = anchorTileId;
		this.anchorDir = anchorDir;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public String getAnchorTileId() {
		return anchorTileId;
	}

	public void setAnchorTileId(String anchorTileId) {
		this.anchorTileId = anchorTileId;
	}

	public String getAnchorDir() {
		return anchorDir;
	}

	public void setAnchorDir(String anchorDir) {
		this.anchorDir = anchorDir;
	}
}

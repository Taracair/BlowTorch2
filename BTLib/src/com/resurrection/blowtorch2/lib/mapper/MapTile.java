package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single room/tile on a map level, with outgoing exits.
 */
public class MapTile {

	private String id;
	private String levelId;
	private int gridX;
	private int gridY;
	private String title;
	private String notes;
	private final List<MapExit> exits = new ArrayList<MapExit>();

	public MapTile() {
		this.id = UUID.randomUUID().toString();
	}

	public MapTile(String id, String levelId, int gridX, int gridY) {
		this.id = id != null ? id : UUID.randomUUID().toString();
		this.levelId = levelId;
		this.gridX = gridX;
		this.gridY = gridY;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getLevelId() {
		return levelId;
	}

	public void setLevelId(String levelId) {
		this.levelId = levelId;
	}

	public int getGridX() {
		return gridX;
	}

	public void setGridX(int gridX) {
		this.gridX = gridX;
	}

	public int getGridY() {
		return gridY;
	}

	public void setGridY(int gridY) {
		this.gridY = gridY;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	/** Outgoing exits from this tile. Mutable list owned by the tile. */
	public List<MapExit> getExits() {
		return exits;
	}

	public void setExits(List<MapExit> exits) {
		this.exits.clear();
		if (exits != null) {
			this.exits.addAll(exits);
		}
	}

	public void addExit(MapExit exit) {
		if (exit != null) {
			exits.add(exit);
		}
	}
}

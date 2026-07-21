package com.resurrection.blowtorch2.lib.mapper;

import java.util.UUID;

/**
 * A drawing / navigation layer within a {@link MudMap}.
 */
public class MapLevel {

	private String id;
	private String name;
	/** Sort order among levels (ascending). */
	private int index;

	public MapLevel() {
		this.id = UUID.randomUUID().toString();
	}

	public MapLevel(String id, String name, int index) {
		this.id = id != null ? id : UUID.randomUUID().toString();
		this.name = name;
		this.index = index;
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
}

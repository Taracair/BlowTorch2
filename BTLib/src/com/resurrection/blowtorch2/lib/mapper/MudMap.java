package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Named map container: levels, tiles, conflicts, and current position ids.
 * Session-only state (e.g. recording) lives outside this model.
 */
public class MudMap {

	private String id;
	private String name;
	private String hostHint;
	private String currentTileId;
	private String currentLevelId;
	private final List<MapLevel> levels = new ArrayList<MapLevel>();
	private final List<MapTile> tiles = new ArrayList<MapTile>();
	private final List<MapConflict> conflicts = new ArrayList<MapConflict>();

	public MudMap() {
		this.id = UUID.randomUUID().toString();
	}

	public MudMap(String id, String name) {
		this.id = id != null ? id : UUID.randomUUID().toString();
		this.name = name;
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

	public String getHostHint() {
		return hostHint;
	}

	public void setHostHint(String hostHint) {
		this.hostHint = hostHint;
	}

	public String getCurrentTileId() {
		return currentTileId;
	}

	public void setCurrentTileId(String currentTileId) {
		this.currentTileId = currentTileId;
	}

	public String getCurrentLevelId() {
		return currentLevelId;
	}

	public void setCurrentLevelId(String currentLevelId) {
		this.currentLevelId = currentLevelId;
	}

	public List<MapLevel> getLevels() {
		return levels;
	}

	public void setLevels(List<MapLevel> levels) {
		this.levels.clear();
		if (levels != null) {
			this.levels.addAll(levels);
		}
	}

	public List<MapTile> getTiles() {
		return tiles;
	}

	public void setTiles(List<MapTile> tiles) {
		this.tiles.clear();
		if (tiles != null) {
			this.tiles.addAll(tiles);
		}
	}

	public List<MapConflict> getConflicts() {
		return conflicts;
	}

	public void setConflicts(List<MapConflict> conflicts) {
		this.conflicts.clear();
		if (conflicts != null) {
			this.conflicts.addAll(conflicts);
		}
	}

	/** Find a tile by id, or null. */
	public MapTile findTile(String tileId) {
		if (tileId == null) {
			return null;
		}
		for (MapTile tile : tiles) {
			if (tileId.equals(tile.getId())) {
				return tile;
			}
		}
		return null;
	}

	/** Find a level by id, or null. */
	public MapLevel findLevel(String levelId) {
		if (levelId == null) {
			return null;
		}
		for (MapLevel level : levels) {
			if (levelId.equals(level.getId())) {
				return level;
			}
		}
		return null;
	}

	/** Levels whose door/stairs tile is {@code tileId} (any entry direction). */
	public List<MapLevel> findLevelsAnchoredOn(String tileId) {
		List<MapLevel> out = new ArrayList<MapLevel>();
		if (tileId == null) {
			return out;
		}
		for (MapLevel level : levels) {
			if (level != null && tileId.equals(level.getAnchorTileId())) {
				out.add(level);
			}
		}
		return out;
	}

	/**
	 * First level anchored on {@code tileId} with matching entry direction
	 * ({@code dir} compared via lexicon normalize / long form; null/empty dir
	 * matches any).
	 */
	public MapLevel findLevelAnchoredOn(String tileId, String dir) {
		if (tileId == null) {
			return null;
		}
		String want = dir != null ? MapDirections.normalize(dir, null) : "";
		if (want.length() == 0 && dir != null) {
			want = dir.trim().toLowerCase(Locale.US);
		}
		String wantLong = want.length() > 0 ? MapDirections.toLongForm(want) : "";
		for (MapLevel level : levels) {
			if (level == null || !tileId.equals(level.getAnchorTileId())) {
				continue;
			}
			if (want.length() == 0) {
				return level;
			}
			String ad = level.getAnchorDir();
			if (ad == null) {
				continue;
			}
			String got = MapDirections.normalize(ad, null);
			if (got.length() == 0) {
				got = ad.trim().toLowerCase(Locale.US);
			}
			if (want.equalsIgnoreCase(got)) {
				return level;
			}
			String gotLong = MapDirections.toLongForm(got);
			if (wantLong != null && wantLong.equalsIgnoreCase(gotLong)) {
				return level;
			}
		}
		return null;
	}
}

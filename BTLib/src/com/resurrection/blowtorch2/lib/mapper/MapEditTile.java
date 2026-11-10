package com.resurrection.blowtorch2.lib.mapper;

import java.util.List;

/**
 * Which tile {@code .map note for <id>} edits. A blank id is the current
 * tile. A mapper tile id wins over a game room id that happens to be the
 * same string.
 */
public final class MapEditTile {

	private MapEditTile() {
	}

	public static MapTile resolve(List<MapTile> tiles, String tileId, MapTile current) {
		if (tileId == null || tileId.trim().length() == 0) {
			return current;
		}
		String id = tileId.trim();
		MapTile byGameRoom = null;
		if (tiles != null) {
			for (MapTile tile : tiles) {
				if (tile == null) {
					continue;
				}
				if (id.equals(tile.getId())) {
					return tile;
				}
				if (byGameRoom == null && id.equals(tile.getExternalId())) {
					byGameRoom = tile;
				}
			}
		}
		return byGameRoom;
	}
}

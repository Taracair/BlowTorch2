package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

/**
 * Walking back into a room the mapper has already drawn must not draw it twice.
 *
 * The mudstandards mapping guide lists "missing or intentionally omitted unique
 * identifiers" as a case every mapper has to handle, and it is not only a
 * property of the server: "GMCP: Match by room number?" is a switch a player can
 * turn off, which reaches the same code path on a MUD that does send numbers.
 *
 * Tile identity is resolved by external id, or by absolute coordinates when
 * those are enabled. With neither, nothing matches an arrival to an existing
 * tile, so grow mode has no way to tell a revisit from a discovery.
 */
public class MapperNoRoomNumberTest {

	private MapperController mapper;

	@Before
	public void setUp() {
		mapper = new MapperController(new Object());
		mapper.setGmcpGrow(true);
		// The case under test: no stable identity available from the server.
		mapper.setGmcpUseNum(false);
		mapper.setGmcpUseCoords(false);
	}

	private static Map<String, String> exits(String... pairs) {
		Map<String, String> out = new HashMap<String, String>();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	private void arrive(String name, Map<String, String> exitDests) {
		mapper.onGmcpRoom(name, null, null, null, null,
				new ArrayList<String>(exitDests.keySet()), exitDests);
	}

	private int tilesTitled(String title) {
		int n = 0;
		for (MapTile t : mapper.getMap().getTiles()) {
			if (t != null && title.equals(t.getTitle())) {
				n++;
			}
		}
		return n;
	}

	private int tileCount() {
		List<MapTile> tiles = mapper.getMap().getTiles();
		return tiles == null ? 0 : tiles.size();
	}

	/**
	 * Tiles with no title are stubs the exit list created for directions nobody
	 * has walked yet. Those are wanted -- they show where you can still go -- so
	 * the thing to count is rooms actually visited.
	 */
	private int visitedTiles() {
		int n = 0;
		for (MapTile t : mapper.getMap().getTiles()) {
			if (t != null && t.getTitle() != null && t.getTitle().trim().length() > 0) {
				n++;
			}
		}
		return n;
	}

	@Test
	public void walkingOutAndBackDoesNotDuplicateTheStartingRoom() {
		arrive("A small Path", exits("e", "", "w", ""));

		mapper.onPlayerCommand("e");
		arrive("Near the Cottage", exits("w", ""));

		mapper.onPlayerCommand("w");
		arrive("A small Path", exits("e", "", "w", ""));

		assertEquals("walking back should return to the same tile, not add one",
				1, tilesTitled("A small Path"));
		assertEquals("two rooms were visited, so two titled tiles",
				2, visitedTiles());
	}

	@Test
	public void pacingBetweenTwoRoomsDoesNotGrowTheMap() {
		arrive("A small Path", exits("e", ""));
		mapper.onPlayerCommand("e");
		arrive("Near the Cottage", exits("w", ""));
		mapper.onPlayerCommand("w");
		arrive("A small Path", exits("e", ""));

		// Whatever the map looks like after one round trip, walking the same two
		// rooms again must not add to it.
		int settled = tileCount();
		for (int i = 0; i < 5; i++) {
			mapper.onPlayerCommand("e");
			arrive("Near the Cottage", exits("w", ""));
			mapper.onPlayerCommand("w");
			arrive("A small Path", exits("e", ""));
		}
		assertEquals("pacing between two rooms must not grow the map",
				settled, tileCount());
		assertEquals("still only two rooms visited", 2, visitedTiles());
	}
}

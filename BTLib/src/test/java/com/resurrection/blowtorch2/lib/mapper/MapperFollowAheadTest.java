package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The BatMUD player's quick walk through an area already mapped: Follow moves
 * the marker on each typed "e", and the rooms the game sends afterwards must not
 * pull it back to the first one.
 */
public class MapperFollowAheadTest {

	private static MapperController batmudLine() {
		MapperController mapper = new MapperController(new Object());
		mapper.setCaptureExitsRegex("^@dir (?<move>[^,]*), (?<exits>.*)$");
		mapper.setCaptureIdRegex("^@roomid (.*)$");
		mapper.onOutputText("@roomid A\n@dir look, east\n");
		mapper.onOutputText("@roomid B\n@dir east, east,west\n");
		mapper.onOutputText("@roomid C\n@dir east, east,west\n");
		mapper.onOutputText("@roomid D\n@dir east, west\n");
		return mapper;
	}

	private static MapTile byId(final MapperController mapper, final String id) {
		for (MapTile t : mapper.getMap().getTiles()) {
			if (id.equals(t.getExternalId())) {
				return t;
			}
		}
		throw new AssertionError("no room " + id);
	}

	private static String here(final MapperController mapper) {
		return mapper.currentTile().getExternalId();
	}

	@Test
	public void roomsArrivingAfterTheBurstDoNotPullTheMarkerBack() {
		MapperController mapper = batmudLine();
		mapper.setCurrentTileId(byId(mapper, "A").getId());
		mapper.onPlayerCommand("e");
		mapper.onPlayerCommand("e");
		mapper.onPlayerCommand("e");
		assertEquals("D", here(mapper));
		mapper.onOutputText("@roomid B\n@dir east, east,west\n");
		assertEquals("D", here(mapper));
		mapper.onOutputText("@roomid C\n@dir east, east,west\n");
		assertEquals("D", here(mapper));
		mapper.onOutputText("@roomid D\n@dir east, west\n");
		assertEquals("D", here(mapper));
		assertEquals(4, mapper.getMap().getTiles().size());
	}

	@Test
	public void aRoomOffTheFollowedPathStillMovesTheMarker() {
		MapperController mapper = batmudLine();
		mapper.setCurrentTileId(byId(mapper, "A").getId());
		mapper.onPlayerCommand("e");
		mapper.onPlayerCommand("e");
		// The second move failed and something carried us back to A.
		mapper.onOutputText("@roomid B\n@dir east, east,west\n");
		assertEquals("C", here(mapper));
		mapper.onOutputText("@roomid A\n@dir west, east\n");
		assertEquals("A", here(mapper));
		// The queue is gone: the next room behaves as before.
		mapper.onOutputText("@roomid B\n@dir east, east,west\n");
		assertEquals("B", here(mapper));
	}

	@Test
	public void theFirstRoomIsNotRelinkedFromWhereFollowGotTo() {
		MapperController mapper = batmudLine();
		MapTile c = byId(mapper, "C");
		MapExit cEast = null;
		for (MapExit e : c.getExits()) {
			if ("e".equals(e.getCommand())) {
				cEast = e;
			}
		}
		// Unwalked, so a walk named by the game may re-aim it.
		cEast.setVerified(false);
		mapper.setCurrentTileId(byId(mapper, "A").getId());
		mapper.onPlayerCommand("e");
		mapper.onPlayerCommand("e");
		mapper.onPlayerCommand("e");
		// Without the queue this arrival was taken as "e from C" and re-aimed C's exit to B.
		mapper.onOutputText("@roomid B\n@dir east, east,west\n");
		assertEquals(byId(mapper, "D").getId(), cEast.getToId());
	}

	@Test
	public void followWithoutRoomIdsIsNotUndone() {
		// GMCP without room numbers: nothing will ever confirm a Follow step.
		MapperController mapper = new MapperController(new Object());
		mapper.setGmcpUseNum(false);
		mapper.setGmcpUseCoords(false);
		java.util.Map<String, String> none = new java.util.HashMap<String, String>();
		mapper.onGmcpRoom("West", null, null, null, null, new java.util.ArrayList<String>(), none);
		mapper.onPlayerCommand("e");
		mapper.onGmcpRoom("East", null, null, null, null, new java.util.ArrayList<String>(), none);
		MapTile east = mapper.currentTile();
		mapper.onPlayerCommand("w");
		mapper.onPlayerCommand("e");
		assertEquals(east.getId(), mapper.currentTile().getId());
		mapper.expireFollowAhead(Long.MAX_VALUE);
		assertEquals(east.getId(), mapper.currentTile().getId());
	}

	@Test
	public void quietGameAfterAFailedMoveTakesTheMarkerBack() {
		MapperController mapper = batmudLine();
		mapper.setCurrentTileId(byId(mapper, "A").getId());
		mapper.onPlayerCommand("e");
		mapper.onPlayerCommand("e");
		mapper.onPlayerCommand("e");
		mapper.onOutputText("@roomid B\n@dir east, east,west\n");
		assertEquals("D", here(mapper));
		// The second "e" was blocked: no more rooms come.
		mapper.expireFollowAhead(Long.MAX_VALUE);
		assertEquals("B", here(mapper));
	}

	@Test
	public void aRoomWithNoExitsLineIsCommittedBeforeTheMarkerGoesBack() {
		MapperController mapper = batmudLine();
		mapper.setCurrentTileId(byId(mapper, "A").getId());
		mapper.onPlayerCommand("e");
		mapper.onPlayerCommand("e");
		mapper.onOutputText("@roomid B\n@dir east, east,west\n@roomid C\n");
		mapper.expireFollowAhead(Long.MAX_VALUE);
		assertEquals("C", here(mapper));
	}
}

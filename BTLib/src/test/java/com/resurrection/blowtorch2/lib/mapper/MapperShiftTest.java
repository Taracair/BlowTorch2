package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** {@code .map shift}: opening a row or column beside Here on maps that do not fit the grid. */
public class MapperShiftTest {

	private static MapperController batmud() {
		MapperController mapper = new MapperController(new Object());
		mapper.setCaptureExitsRegex("^@dir (?<move>[^,]*), (?<exits>.*)$");
		mapper.setCaptureIdRegex("^@roomid (.*)$");
		mapper.setGmcpCreateExits(false);
		return mapper;
	}

	@Test
	public void shiftEastMovesOnlyRoomsEastOfHere() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid here\n@dir , east\n");
		mapper.setEditMode(true);
		MapTile here = mapper.currentTile();
		MapTile east = place(mapper, here.getGridX() + 1, here.getGridY());
		MapTile west = place(mapper, here.getGridX() - 1, here.getGridY());
		MapTile ne = place(mapper, here.getGridX() + 2, here.getGridY() - 3);
		ne.setLockPosition(true);
		String msg = mapper.shiftRooms("east", 1);
		assertTrue(msg, msg.startsWith("Mapper: shifted 2 rooms (1 with a locked position) by 1."));
		assertEquals(here.getGridX() + 2, east.getGridX());
		assertEquals(here.getGridX() + 3, ne.getGridX());
		assertEquals(here.getGridY() - 3, ne.getGridY());
		assertEquals(here.getGridX() - 1, west.getGridX());
		assertEquals(0, here.getGridX());
	}

	@Test
	public void shiftSoutheastMovesEachAxisOnItsOwnAndUndoPutsThemBack() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid here\n@dir , east\n");
		mapper.setEditMode(true);
		MapTile east = place(mapper, 1, 0);
		MapTile south = place(mapper, 0, 1);
		MapTile se = place(mapper, 2, 2);
		mapper.shiftRooms("southeast", 2);
		assertEquals("3,0", east.getGridX() + "," + east.getGridY());
		assertEquals("0,3", south.getGridX() + "," + south.getGridY());
		assertEquals("4,4", se.getGridX() + "," + se.getGridY());
		mapper.undo();
		MapTile back = null;
		for (MapTile t : mapper.getMap().getTiles()) {
			if (t.getGridX() == 2 && t.getGridY() == 2) {
				back = t;
			}
		}
		assertTrue(back != null);
	}

	@Test
	public void shiftRefusesAWordThatIsNotACompassDirection() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid here\n@dir , east\n");
		String msg = mapper.shiftRooms("up", 1);
		assertTrue(msg, msg.contains("compass direction"));
	}

	private static MapTile place(final MapperController mapper, final int x, final int y) {
		mapper.placeTile(x, y, null);
		for (MapTile t : mapper.getMap().getTiles()) {
			if (t.getGridX() == x && t.getGridY() == y) {
				return t;
			}
		}
		throw new AssertionError("no tile at " + x + "," + y);
	}

	@Test
	public void shiftStaysOnHeresFloorWhenItsFloorIdIsMissing() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid here\n@dir , east\n");
		mapper.setEditMode(true);
		MapTile other = place(mapper, 5, 0);
		other.setLevelId("someLevel");
		mapper.currentTile().setLevelId(null);
		mapper.shiftRooms("east", 1);
		assertEquals(5, other.getGridX());
	}
}

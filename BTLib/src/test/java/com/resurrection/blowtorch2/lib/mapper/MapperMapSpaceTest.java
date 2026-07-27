package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;

import org.junit.Before;
import org.junit.Test;

/**
 * Coordinates only mean something inside the map file they were measured in.
 *
 * On older eden the surface is one file and each cave gets its own, numbered
 * from scratch. Going down from the cellar reaches a cave room reporting
 * z=0 -- read against the surface that says the cellar's cellar is at ground
 * level, and the map raised a conflict every time the player passed through.
 */
public class MapperMapSpaceTest {

	private static final String SURFACE =
			"https://eden-test.rpgframework.de:4079/world/world01/01/mmp.xml";
	private static final String CAVE =
			"https://eden-test.rpgframework.de:4079/dynamic/mmp/1_2_4c46977d.xml";

	private MapperController mapper;

	@Before
	public void setUp() {
		mapper = new MapperController(new Object());
	}

	private void arrive(String name, int num, int x, int y, int z, String space,
			String exitsJson) {
		mapper.onGmcpRoomRaw("Room.Info", "{\"num\":" + num + ",\"name\":\"" + name
				+ "\",\"coords\":{\"id\":0,\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z
				+ "},\"map\":\"" + space + "\",\"exits\":" + exitsJson + "}");
	}

	private MapTile tileNamed(String title) {
		for (MapTile t : mapper.getMap().getTiles()) {
			if (t != null && title.equals(t.getTitle())) {
				return t;
			}
		}
		return null;
	}

	/** The room that starts the map sets the space its coordinates are read in. */
	@Test
	public void theFirstRoomSetsTheHomeSpace() {
		arrive("A small Path", 1001010, 8, 6, 0, SURFACE, "{\"D\":1001011}");
		assertNotNull(tileNamed("A small Path"));
	}

	/**
	 * A room from another map file must not be placed on the level its own z
	 * names, because that z counts from its own floor, not ours.
	 */
	@Test
	public void aRoomFromAnotherFileDoesNotClaimOurLevel() {
		arrive("A small Path", 1001010, 8, 6, 0, SURFACE, "{\"D\":1001011}");
		String surfaceLevel = tileNamed("A small Path").getLevelId();

		mapper.onPlayerCommand("d");
		arrive("Underground Storage", 1001011, 8, 6, -1, SURFACE,
				"{\"D\":1002001,\"U\":1001010}");
		MapTile cellar = tileNamed("Underground Storage");
		assertNotNull(cellar);
		assertNotEquals("the cellar is below the surface",
				surfaceLevel, cellar.getLevelId());

		mapper.onPlayerCommand("d");
		arrive("At the exit", 1002001, 2, 2, 0, CAVE, "{\"U\":1001011}");
		MapTile cave = tileNamed("At the exit");
		assertNotNull("the cave room should become a tile of its own", cave);
		assertNotEquals("a cave room reporting z=0 in its own file must not land "
				+ "on the surface", surfaceLevel, cave.getLevelId());
		assertNotEquals("nor may it share the cellar it was reached from",
				cellar.getLevelId(), cave.getLevelId());
	}

	/** Rooms inside the home space keep using their coordinates for levels. */
	@Test
	public void theHomeSpaceStillDrivesLevels() {
		arrive("A small Path", 1001010, 8, 6, 0, SURFACE, "{\"D\":1001011}");
		String surface = tileNamed("A small Path").getLevelId();

		mapper.onPlayerCommand("d");
		arrive("Underground Storage", 1001011, 8, 6, -1, SURFACE,
				"{\"D\":1002001,\"U\":1001010}");

		mapper.onPlayerCommand("u");
		arrive("A small Path", 1001010, 8, 6, 0, SURFACE, "{\"D\":1001011}");
		assertEquals("coming back up returns to the surface level",
				surface, tileNamed("A small Path").getLevelId());
	}
}

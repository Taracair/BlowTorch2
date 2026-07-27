package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/**
 * Which cell a newly seen room lands on when the one it should have had is
 * taken. The order used to have nothing to do with the way the player walked.
 */
public class MapperPlacementOrderTest {

	/** Grid convention: y grows downward, so north is -1. */
	private static final int[] EAST = { 1, 0 };
	private static final int[] WEST = { -1, 0 };
	private static final int[] NORTH = { 0, -1 };
	private static final int[] SOUTH = { 0, 1 };

	@Test
	public void withoutTravelKeepsThePlainScanOrder() {
		List<int[]> cells = MapperController.ringOffsetsByTravel(1, null);
		assertEquals(8, cells.size());
		assertArrayEquals(new int[] { -1, -1 }, cells.get(0));
		assertArrayEquals(new int[] { -1, 0 }, cells.get(1));
		assertArrayEquals(new int[] { -1, 1 }, cells.get(2));
		assertArrayEquals(new int[] { 1, 1 }, cells.get(7));
	}

	@Test
	public void walkingEastPrefersTheCellToTheEast() {
		assertArrayEquals(new int[] { 1, 0 },
				MapperController.ringOffsetsByTravel(1, EAST).get(0));
	}

	@Test
	public void eachDirectionLeadsWithItsOwnCell() {
		assertArrayEquals(new int[] { -1, 0 },
				MapperController.ringOffsetsByTravel(1, WEST).get(0));
		assertArrayEquals(new int[] { 0, -1 },
				MapperController.ringOffsetsByTravel(1, NORTH).get(0));
		assertArrayEquals(new int[] { 0, 1 },
				MapperController.ringOffsetsByTravel(1, SOUTH).get(0));
	}

	/** The reported shape: walking west must not put the room below. */
	@Test
	public void walkingWestNeverPrefersStraightDown() {
		List<int[]> cells = MapperController.ringOffsetsByTravel(1, WEST);
		int west = indexOf(cells, -1, 0);
		int down = indexOf(cells, 0, 1);
		assertTrue("west cell should be considered before the cell below",
				west < down);
	}

	/** Directly behind is the last resort at every radius. */
	@Test
	public void theCellBehindComesLast() {
		for (int r = 1; r <= 3; r++) {
			List<int[]> cells = MapperController.ringOffsetsByTravel(r, EAST);
			int[] last = cells.get(cells.size() - 1);
			assertEquals("radius " + r + " should end behind the player", -r, last[0]);
			assertEquals("radius " + r + " should end level with the player", 0, last[1]);
		}
	}

	@Test
	public void diagonalTravelLeadsWithThatDiagonal() {
		assertArrayEquals(new int[] { 1, 1 },
				MapperController.ringOffsetsByTravel(1, new int[] { 1, 1 }).get(0));
	}

	@Test
	public void everyRingCellIsOfferedExactlyOnce() {
		for (int r = 1; r <= 3; r++) {
			List<int[]> plain = MapperController.ringOffsetsByTravel(r, null);
			List<int[]> sorted = MapperController.ringOffsetsByTravel(r, EAST);
			assertEquals(plain.size(), sorted.size());
			for (int[] cell : plain) {
				assertTrue("ring " + r + " lost (" + cell[0] + "," + cell[1] + ")",
						indexOf(sorted, cell[0], cell[1]) >= 0);
			}
		}
	}

	private static int indexOf(List<int[]> cells, int dx, int dy) {
		for (int i = 0; i < cells.size(); i++) {
			if (cells.get(i)[0] == dx && cells.get(i)[1] == dy) {
				return i;
			}
		}
		return -1;
	}
}

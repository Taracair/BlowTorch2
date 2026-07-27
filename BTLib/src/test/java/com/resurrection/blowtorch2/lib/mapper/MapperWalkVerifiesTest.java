package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

/**
 * Walking a room must mark the exit that got you there as walked.
 *
 * This is driven the way the client drives it -- the command first, then the
 * GMCP room that answers it -- because the bug this covers was entirely about
 * the order of those two. Follow mode steps the current tile along the exit the
 * moment the command is sent, so reading "where we came from" during the GMCP
 * room gave the room we had already arrived at, and nothing was ever confirmed.
 *
 * The rooms and numbers are the real ones from an older eden session log.
 */
public class MapperWalkVerifiesTest {

	private MapperController mapper;

	@Before
	public void setUp() {
		mapper = new MapperController(new Object());
	}

	/** GMCP exits arrive as direction → destination room number. */
	private static Map<String, String> exits(String... pairs) {
		Map<String, String> out = new HashMap<String, String>();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			out.put(pairs[i], pairs[i + 1]);
		}
		return out;
	}

	private static List<String> dirs(Map<String, String> exitDestNums) {
		return new ArrayList<String>(exitDestNums.keySet());
	}

	private void arrive(String name, String num, Map<String, String> exitDestNums) {
		mapper.onGmcpRoom(name, num, null, null, null, dirs(exitDestNums), exitDestNums);
	}

	private MapTile tileNamed(String title) {
		for (MapTile t : mapper.getMap().getTiles()) {
			if (t != null && title.equals(t.getTitle())) {
				return t;
			}
		}
		return null;
	}

	private MapExit exitOf(String fromTitle, String command) {
		MapTile from = tileNamed(fromTitle);
		assertNotNull("no tile titled " + fromTitle, from);
		for (MapExit e : from.getExits()) {
			if (e != null && command.equalsIgnoreCase(e.getCommand())) {
				return e;
			}
		}
		return null;
	}

	@Test
	public void walkingAnExitMarksItWalked() {
		Map<String, String> smallPath = exits("D", "1001011", "E", "1001012", "W", "1001009");
		arrive("A small Path", "1001010", smallPath);

		mapper.onPlayerCommand("w");
		arrive("Near the Cottage", "1001009",
				exits("S", "1001028", "E", "1001010", "W", "1001029", "N", "1001005"));

		MapExit walked = exitOf("A small Path", "w");
		assertNotNull("the exit that was walked should exist", walked);
		assertTrue("walking w should mark it walked", walked.isVerified());
	}

	/**
	 * Down has no grid delta, so an earlier version threw it away before it ever
	 * counted as a move. Going down into the cellar is exactly the case that
	 * exposed it.
	 */
	@Test
	public void goingDownCountsAsAMove() {
		arrive("A small Path", "1001010",
				exits("D", "1001011", "E", "1001012", "W", "1001009"));

		mapper.onPlayerCommand("d");
		arrive("Underground Storage", "1001011", exits("D", "1002001", "U", "1001010"));

		MapExit down = exitOf("A small Path", "d");
		assertNotNull("the descent should exist as an exit", down);
		assertTrue("going down should mark the descent walked", down.isVerified());
	}

	/** The way back is a separate claim and stays unproved until it is walked. */
	@Test
	public void theReturnTripIsNotProvedByTheOutwardOne() {
		arrive("Beehives", "1001008", exits("S", "1001029", "E", "1001007"));

		mapper.onPlayerCommand("e");
		arrive("Herb Garden", "1001007",
				exits("E", "1001005", "W", "1001008", "N", "1001025"));

		MapExit out = exitOf("Beehives", "e");
		assertNotNull(out);
		assertTrue("e out of Beehives was walked", out.isVerified());

		MapExit back = exitOf("Herb Garden", "w");
		if (back != null) {
			assertEquals("w back to Beehives has not been walked",
					false, back.isVerified());
		}
	}

	/** Arriving without having typed a move proves nothing. */
	@Test
	public void aRoomThatArrivesOnItsOwnProvesNothing() {
		arrive("A small Path", "1001010",
				exits("D", "1001011", "E", "1001012", "W", "1001009"));
		arrive("Near the Cottage", "1001009", exits("E", "1001010"));

		MapExit any = exitOf("A small Path", "w");
		if (any != null) {
			assertEquals("no command was typed, so nothing is proved",
					false, any.isVerified());
		}
	}
}

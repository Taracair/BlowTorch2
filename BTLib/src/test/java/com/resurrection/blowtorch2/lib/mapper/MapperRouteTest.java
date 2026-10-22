package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/** {@code .map route}: a move the player declares, linked when the room id changes. */
public class MapperRouteTest {

	private static MapperController batmud() {
		MapperController mapper = new MapperController(new Object());
		mapper.setCaptureExitsRegex("^@dir (?<move>[^,]*), (?<exits>.*)$");
		mapper.setCaptureIdRegex("^@roomid (.*)$");
		mapper.setGmcpCreateExits(false);
		return mapper;
	}

	private static List<String> commands(final MapTile tile) {
		List<String> out = new ArrayList<String>();
		for (MapExit e : tile.getExits()) {
			out.add(e.getCommand());
		}
		return out;
	}

	/** What Connection does for ".map route X": declare, then X goes out as a sent line. */
	private static void route(final MapperController mapper, final String cmd) {
		mapper.declareMove(cmd);
		mapper.onPlayerCommand(cmd);
	}

	@Test
	public void aRoutedSpecialLinksWhenTheServerNamesNoMove() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid a\n@dir , north\n");
		route(mapper, "drawbridge");
		mapper.onOutputText("@roomid keep\n@dir , out\n");
		route(mapper, "out");
		mapper.onOutputText("@roomid a\n@dir , north\n");
		assertEquals(2, mapper.getMap().getTiles().size());
		MapTile a = mapper.currentTile();
		assertEquals("[drawbridge]", commands(a).toString());
		MapTile keep = mapper.getMap().findTile(a.getExits().get(0).getToId());
		assertEquals("[out]", commands(keep).toString());
		assertTrue(a.getExits().get(0).isVerified());
	}

	@Test
	public void withoutRouteAnUnnamedSpecialGetsNoLink() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid a\n@dir , north\n");
		mapper.onPlayerCommand("drawbridge");
		mapper.onOutputText("@roomid keep\n@dir , out\n");
		assertEquals(0, mapper.getMap().findTile(firstTileId(mapper, "a")).getExits().size());
	}

	@Test
	public void aCommandSentAfterTheRouteCancelsIt() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid a\n@dir , north\n");
		route(mapper, "drawbridge");
		mapper.onPlayerCommand("say hi");
		mapper.onOutputText("@roomid elsewhere\n@dir , north\n");
		assertEquals(0, mapper.getMap().findTile(firstTileId(mapper, "a")).getExits().size());
	}

	@Test
	public void aBlockedRouteDrawsNothing() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid a\n@dir , north\n");
		route(mapper, "drawbridge");
		mapper.onOutputText("The drawbridge is up.\n");
		assertEquals(1, mapper.getMap().getTiles().size());
		assertEquals(0, mapper.currentTile().getExits().size());
	}

	@Test
	public void theServerMoveStillBeatsTheRoute() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid a\n@dir , north\n");
		route(mapper, "enter");
		mapper.onOutputText("@roomid b\n@dir north, south\n");
		assertEquals("[n]", commands(mapper.getMap().findTile(firstTileId(mapper, "a"))).toString());
	}

	@Test
	public void aRouteAfterADirectionOnTheSameLineIsNotLinked() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid a\n@dir , north\n");
		// "n;.map route drawbridge": the route runs while n is still queued.
		mapper.declareMove("drawbridge");
		mapper.onPlayerCommand("n\r\ndrawbridge");
		mapper.onOutputText("@roomid b\n@dir , south\n");
		mapper.onOutputText("@roomid keep\n@dir , out\n");
		assertEquals("[n]", commands(mapper.getMap().findTile(firstTileId(mapper, "a"))).toString());
		assertEquals("[]", commands(mapper.getMap().findTile(firstTileId(mapper, "b"))).toString());
	}

	@Test
	public void aRouteTypedBeforeThePreviousRoomArrivedIsRefused() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid a\n@dir , north\n");
		mapper.onPlayerCommand("n");
		String refused = mapper.declareMove("drawbridge");
		assertTrue(refused, refused.contains("no room has arrived yet for \"n\""));
		mapper.onPlayerCommand("drawbridge");
		mapper.onOutputText("@roomid b\n@dir , south\n");
		mapper.onOutputText("@roomid keep\n@dir , out\n");
		assertEquals("[n]", commands(mapper.getMap().findTile(firstTileId(mapper, "a"))).toString());
		assertEquals("[]", commands(mapper.getMap().findTile(firstTileId(mapper, "b"))).toString());
	}

	@Test
	public void theRoutedTextMayDifferFromWhatWasTyped() {
		// Unaccent or lowercase-start can change the sent text; the route's own line still counts.
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid a\n@dir , north\n");
		mapper.declareMove("Wejd\u017a");
		mapper.onPlayerCommand("wejdz");
		mapper.onOutputText("@roomid keep\n@dir , out\n");
		assertEquals(1, commands(mapper.getMap().findTile(firstTileId(mapper, "a"))).size());
	}

	@Test
	public void aRouteFollowedByACommandOnTheSameLineIsNotLinked() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid a\n@dir , north\n");
		mapper.declareMove("drawbridge");
		mapper.onPlayerCommand("drawbridge\r\nlook");
		mapper.onOutputText("@roomid keep\n@dir , out\n");
		assertEquals("[]", commands(mapper.getMap().findTile(firstTileId(mapper, "a"))).toString());
	}

	@Test
	public void aSecondRouteBeforeTheFirstRoomArrivesIsRefused() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid a\n@dir , north\n");
		route(mapper, "drawbridge");
		String refused = mapper.declareMove("enter");
		assertTrue(refused, refused.contains("no room has arrived yet for \"drawbridge\""));
		mapper.onPlayerCommand("enter");
		mapper.onOutputText("@roomid keep\n@dir , out\n");
		assertEquals("[]", commands(mapper.getMap().findTile(firstTileId(mapper, "a"))).toString());
	}

	@Test
	public void aCancelledRouteDoesNotCaptureTheNextMove() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid a\n@dir , north\n");
		mapper.declareMove("drawbridge");
		mapper.cancelDeclaredMove();
		mapper.onPlayerCommand("n");
		mapper.onOutputText("@roomid b\n@dir , south\n");
		assertEquals("[n]", commands(mapper.getMap().findTile(firstTileId(mapper, "a"))).toString());
	}

	private static String firstTileId(final MapperController mapper, final String externalId) {
		for (MapTile t : mapper.getMap().getTiles()) {
			if (externalId.equals(t.getExternalId())) {
				return t.getId();
			}
		}
		return null;
	}
}

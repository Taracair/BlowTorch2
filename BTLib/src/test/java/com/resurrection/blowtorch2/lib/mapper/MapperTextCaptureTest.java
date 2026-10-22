package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.mapper.MapperController.CapturePreview;

/** Mapping from game text for worlds without GMCP (a BatMUD player's @roomid / @dir lines). */
public class MapperTextCaptureTest {

	private static final String EXITS = "^@dir (?<move>[^,]*), (?<exits>.*)$";

	private static MapperController batmud() {
		MapperController mapper = new MapperController(new Object());
		mapper.setCaptureExitsRegex(EXITS);
		mapper.setCaptureIdRegex("^@roomid (.*)$");
		return mapper;
	}

	private static List<String> commands(final MapTile tile) {
		List<String> out = new ArrayList<String>();
		for (MapExit e : tile.getExits()) {
			out.add(e.getCommand());
		}
		return out;
	}

	@Test
	public void emptyIdRegexMapsNothing() {
		MapperController mapper = new MapperController(new Object());
		mapper.onOutputText("@roomid a1\n@dir north, south,east\n");
		assertNull(mapper.currentTile());
	}

	@Test
	public void idAndExitsInSeparatePacketsMakeOneRoom() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid oefjo#3eijfk\n");
		assertNull(mapper.currentTile());
		mapper.onOutputText("@dir north, south,east,west,north\n");
		MapTile here = mapper.currentTile();
		assertNotNull(here);
		assertEquals("oefjo#3eijfk", here.getExternalId());
		assertEquals(4, here.getExits().size());
	}

	@Test
	public void serverMoveLinksTheNewRoomAndARevisitIsFoundById() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid A\n@dir look, north\n");
		MapTile a = mapper.currentTile();
		mapper.onOutputText("@roomid B\n@dir north, south\n");
		MapTile b = mapper.currentTile();
		assertTrue(!a.getId().equals(b.getId()));
		assertEquals(a.getGridX(), b.getGridX());
		assertTrue(commands(a).toString(), commands(a).contains("n"));
		assertEquals(a.getGridY() - 1, b.getGridY());
		mapper.onOutputText("@roomid A\n@dir south, north\n");
		assertEquals(a.getId(), mapper.currentTile().getId());
		StringBuilder dump = new StringBuilder();
		for (MapTile t : mapper.getMap().getTiles()) {
			dump.append(t.getExternalId()).append('@').append(t.getGridX()).append(',')
					.append(t.getGridY()).append(commands(t)).append(' ');
		}
		assertEquals(dump.toString(), 2, mapper.getMap().getTiles().size());
	}

	@Test
	public void nextCommandCommitsARoomWithNoExitsLine() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid lonely\n");
		mapper.onPlayerCommand("look");
		assertEquals("lonely", mapper.currentTile().getExternalId());
	}

	@Test
	public void previewReadsTheNamedExitsGroup() {
		CapturePreview preview = new MapperController(new Object()).previewCapture(
				"^@roomid (.*)$", EXITS, "@roomid x\n@dir north, south,east", 5);
		assertEquals("south,east", preview.exits);
	}

	@Test
	public void previewNamesALiveIdThatIsNotOnScreen() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid oefjo#3eijfk\n");
		String line = mapper.roomIdPreview("@dir north, south,east\n");
		assertTrue(line, line.startsWith("oefjo#3eijfk (seen in the game text"));
	}

	@Test
	public void previewQuotesTheLinesBeforeAnExitsLineWithNoId() {
		MapperController mapper = batmud();
		mapper.onOutputText("HP:100 > @roomid oefjo#3eijfk\n@dir north, south,east\n");
		String line = mapper.roomIdPreview("HP:100 > @roomid oefjo#3eijfk\n@dir north, south,east");
		assertTrue(line, line.startsWith("(no match) The last exits line came with no room id open."));
		assertTrue(line, line.contains("\u00abHP:100 > @roomid oefjo#3eijfk\u00bb"));
	}

	@Test
	public void previewExplainsTheLatestEventNotAnOldId() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid a1\n@dir north, south\n");
		mapper.onOutputText("HP:100 > @roomid b2\n@dir east, west\n");
		String line = mapper.roomIdPreview("");
		assertTrue(line, line.startsWith("(no match) The last exits line came with no room id open."));
		assertTrue(line, line.contains("\u00abHP:100 > @roomid b2\u00bb"));
		mapper.onOutputText("@roomid c3\n");
		assertTrue(mapper.roomIdPreview("").startsWith("c3 (seen in the game text"));
	}

	@Test
	public void exitListsSplitOnCommasSpacesAndAnd() {
		assertEquals("[south, east, west]",
				MapperController.splitExits("south,east, west").toString());
		assertEquals("[north, up]", MapperController.splitExits("north and up.").toString());
	}

	@Test
	public void recordModeTakesTheTileTheTypedMoveMade() {
		MapperController mapper = batmud();
		mapper.setEditMode(true);
		mapper.setRecording(true);
		mapper.onOutputText("@roomid A\n@dir look, north\n");
		mapper.onPlayerCommand("north");
		mapper.onOutputText("@roomid B\n@dir north, south\n");
		StringBuilder dump = new StringBuilder();
		for (MapTile t : mapper.getMap().getTiles()) {
			dump.append(t.getExternalId()).append('@').append(t.getGridX()).append(',')
					.append(t.getGridY()).append(commands(t)).append(' ');
		}
		assertEquals(dump.toString(), 2, mapper.getMap().getTiles().size());
		assertEquals("B", mapper.currentTile().getExternalId());
	}

	/** The player's scrollback after walking e then s: both rooms are still in the 20-line window. */
	private static final String CHURCH_THEN_TOWER = "e\n"
			+ "[ ]-( )-[ ]  Church of all Gods\n"
			+ "@myloc laenor 8555 8664 0\n"
			+ "@roomid acoOalAfoxAOQewBzEi2v1\n"
			+ "@dir east, north,east,south,west\n"
			+ "s\n"
			+ "    ( )      Entrance to Church Tower\n"
			+ "@myloc laenor 8557 8665 0\n"
			+ "@roomid NkvW90b1CScEwEaq7bkWL1\n"
			+ "@dir south, north,south\n"
			+ "Entered area church of all gods with direction south";

	@Test
	public void previewShowsTheNewestRoomInTheWindowNotTheOldest() {
		MapperController mapper = batmud();
		mapper.onOutputText(CHURCH_THEN_TOWER);
		assertTrue(mapper.roomIdPreview(CHURCH_THEN_TOWER).startsWith("NkvW90b1CScEwEaq7bkWL1"));
		CapturePreview preview = mapper.previewCapture("^@roomname (.*)$", EXITS,
				CHURCH_THEN_TOWER, 20);
		assertEquals("north,south", preview.exits);
	}

	@Test
	public void previewNamesTheLastRoomTheMapperDrew() {
		MapperController mapper = batmud();
		mapper.setCaptureTitleRegex("^@roomname (.*)$");
		mapper.onOutputText("@roomid a1\n@roomname Temple entrance\n@dir west, east,west\n");
		mapper.onOutputText("@roomid b2\n@roomname Church of all Gods\n@dir east, north,south\n");
		String drawn = mapper.lastDrawnRoom();
		assertEquals("b2, title \"Church of all Gods\", exits [north, south], came by east", drawn);
	}

	/** The player's loop a→b→c→d→e→a with Record off: a revisit is found by id and every walk is a link. */
	@Test
	public void recordOffClosesALoopByIdWithOnlyWalkedLinks() {
		MapperController mapper = batmud();
		mapper.setGmcpCreateExits(false);
		mapper.onOutputText("@roomid a\n@dir , south\n");
		String[][] steps = { { "s", "b", "south" }, { "e", "c", "east" }, { "n", "d", "north" },
				{ "nw", "e", "northwest" }, { "w", "a", "west" }, { "s", "b", "south" } };
		for (String[] step : steps) {
			mapper.onPlayerCommand(step[0]);
			mapper.onOutputText("@roomid " + step[1] + "\n@dir " + step[2] + ", north,south\n");
		}
		assertEquals(5, mapper.getMap().getTiles().size());
		MapTile e = null;
		for (MapTile t : mapper.getMap().getTiles()) {
			if ("e".equals(t.getExternalId())) {
				e = t;
			}
		}
		assertEquals("[w]", commands(e).toString());
		assertEquals("a", mapper.getMap().findTile(e.getExits().get(0).getToId()).getExternalId());
		assertEquals("b", mapper.currentTile().getExternalId());
	}

	@Test
	public void aSpecialMoveNamedByTheServerLinksBothWays() {
		MapperController mapper = batmud();
		mapper.setGmcpCreateExits(false);
		mapper.onOutputText("@roomid a\n@dir , north\n");
		mapper.onPlayerCommand("drawbridge");
		mapper.onOutputText("@roomid keep\n@dir drawbridge, out\n");
		mapper.onPlayerCommand("out");
		mapper.onOutputText("@roomid a\n@dir out, north\n");
		assertEquals(2, mapper.getMap().getTiles().size());
		assertEquals("a", mapper.currentTile().getExternalId());
		assertEquals("[drawbridge]", commands(mapper.currentTile()).toString());
		MapTile keep = mapper.getMap().findTile(mapper.currentTile().getExits().get(0).getToId());
		assertEquals("[out]", commands(keep).toString());
		assertEquals("a", mapper.getMap().findTile(keep.getExits().get(0).getToId()).getExternalId());
	}

	/** What the manual warns about: Record on draws ahead of the game and the walk back to a loses its link. */
	@Test
	public void recordOnLosesTheLinkThatClosesTheLoop() {
		MapperController mapper = batmud();
		mapper.setGmcpCreateExits(false);
		mapper.setEditMode(true);
		mapper.setRecording(true);
		mapper.onOutputText("@roomid a\n@dir , south\n");
		String[][] steps = { { "s", "b", "south" }, { "e", "c", "east" }, { "n", "d", "north" },
				{ "nw", "e", "northwest" }, { "w", "a", "west" } };
		for (String[] step : steps) {
			mapper.onPlayerCommand(step[0]);
			mapper.onOutputText("@roomid " + step[1] + "\n@dir " + step[2] + ", north,south\n");
		}
		assertTrue(mapper.getMap().getTiles().size() > 5);
		for (MapTile t : mapper.getMap().getTiles()) {
			if ("e".equals(t.getExternalId())) {
				for (MapExit x : t.getExits()) {
					MapTile to = mapper.getMap().findTile(x.getToId());
					assertTrue(to == null || !"a".equals(to.getExternalId()));
				}
			}
		}
	}
}

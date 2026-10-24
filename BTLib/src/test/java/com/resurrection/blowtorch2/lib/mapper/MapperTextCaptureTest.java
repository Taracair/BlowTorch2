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
		mapper.setEditMode(true);
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
		mapper.setEditMode(true);
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
	public void browseMovesAmongDrawnRoomsButDrawsNoNewOnes() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid A\n@dir look, north\n");
		mapper.onOutputText("@roomid B\n@dir north, south\n");
		mapper.setEditMode(false);
		mapper.onOutputText("@roomid A\n@dir south, north\n");
		assertEquals("A", mapper.currentTile().getExternalId());
		mapper.onOutputText("@roomid C\n@dir east, west\n");
		assertEquals("A", mapper.currentTile().getExternalId());
		assertEquals(2, mapper.getMap().getTiles().size());
		assertTrue(mapper.lastDrawnRoom(), mapper.lastDrawnRoom().contains("Browse mode"));
	}

	@Test
	public void browseFollowsIntoAPlaceholderRoom() {
		MapperController mapper = batmud();
		mapper.setGmcpCreateExits(true);
		mapper.onOutputText("@roomid A\n@dir look, north\n");
		int before = mapper.getMap().getTiles().size();
		mapper.setEditMode(false);
		mapper.onOutputText("@roomid B\n@dir north, south\n");
		// The placeholder north of A is drawn, so Here goes there, but gets no id.
		assertEquals(before, mapper.getMap().getTiles().size());
		assertTrue(mapper.currentTile().getExternalId() == null
				|| mapper.currentTile().getExternalId().length() == 0);
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

	/** Record on draws ahead of the game (an extra room); the walk back to a now replaces Record's guess. */
	@Test
	public void recordOnDrawsAnExtraRoomButTheWalkClosesTheLoop() {
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
		boolean closed = false;
		for (MapTile t : mapper.getMap().getTiles()) {
			if ("e".equals(t.getExternalId())) {
				for (MapExit x : t.getExits()) {
					MapTile to = mapper.getMap().findTile(x.getToId());
					closed |= to != null && "a".equals(to.getExternalId()) && x.isVerified();
				}
			}
		}
		assertTrue(closed);
	}

	@Test
	public void lastRoomLineSaysWhenFollowPolicyLeftTheRoomOffTheMap() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid a\n@dir , south\n");
		mapper.setGmcpPolicy(MapperController.GMCP_POLICY_FOLLOW);
		mapper.onPlayerCommand("s");
		mapper.onOutputText("@roomid b\n@dir south, north\n");
		assertTrue(mapper.lastDrawnRoom(), mapper.lastDrawnRoom().startsWith("no room with id b on the map (Room Sync policy is Follow"));
	}

	@Test
	public void lastRoomLineShowsTheTitleTheTileKept() {
		MapperController mapper = batmud();
		mapper.setCaptureTitleRegex("^@roomname (.*)$");
		mapper.onOutputText("@roomid a\n@roomname Mine\n@dir , south\n");
		mapper.currentTile().setLockTitle(true);
		mapper.onOutputText("@roomid a\n@roomname Server name\n@dir , south\n");
		assertTrue(mapper.lastDrawnRoom(), mapper.lastDrawnRoom().startsWith("a, title \"Mine\""));
	}

	@Test
	public void lastRoomLineIsForgottenWithANewMap() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid a\n@dir , south\n");
		mapper.newMap("other");
		assertNull(mapper.lastDrawnRoom());
	}

	@Test
	public void applyTakesTheTitleOfTheNewestRoomNotTheOneBefore() {
		MapperController mapper = batmud();
		mapper.setCaptureTitleRegex("^@roomname (.*)$");
		String window = "@roomid church\n@roomname Church of all Gods\n@dir east, north,east,south,west\n"
				+ "s\n@roomid tower\n@roomname Entrance to Church Tower\n@dir south, north,south";
		CapturePreview preview = mapper.currentRoomPreview(window, 20);
		assertEquals("Entrance to Church Tower", preview.title);
		assertEquals("north,south", preview.exits);
	}

	@Test
	public void applyKeepsALockedTitle() {
		MapperController mapper = batmud();
		mapper.onOutputText("@roomid a\n@dir , south\n");
		mapper.currentTile().setTitle("Mine");
		mapper.currentTile().setLockTitle(true);
		CapturePreview preview = new CapturePreview();
		preview.title = "Other";
		mapper.applyCapture(preview);
		assertEquals("Mine", mapper.currentTile().getTitle());
	}

	@Test
	public void previewTitleNeverComesFromTheIdLineItself() {
		MapperController mapper = new MapperController(new Object());
		mapper.setEditMode(true);
		mapper.setCaptureIdRegex("^Room #(?<id>\\d+)");
		CapturePreview preview = mapper.currentRoomPreview("Room #12\nyou see dust\nexits: north", 20);
		assertNull(preview.title);
	}

	/** The BatMUD player's tower: "@dir up, ..." went onto the same floor as a special exit. */
	@Test
	public void upAndDownFromTheServerChangeFloor() {
		MapperController mapper = batmud();
		mapper.setGmcpCreateExits(false);
		mapper.onOutputText("@roomid L303\n@roomshort Main level of southwest tower\n@dir south, north,east,up,down\n");
		MapTile main = mapper.currentTile();
		mapper.onOutputText("@roomid top\n@dir up, down\n");
		MapTile top = mapper.currentTile();
		assertTrue(!main.getLevelId().equals(top.getLevelId()));
		assertEquals(top.getLevelId(), mapper.getMap().getCurrentLevelId());
		assertEquals(main.getGridX(), top.getGridX());
		assertEquals(main.getGridY(), top.getGridY());
		mapper.onOutputText("@roomid L303\n@dir down, north,east,up,down\n");
		assertEquals(main.getId(), mapper.currentTile().getId());
		mapper.onOutputText("@roomid cellar\n@dir down, up\n");
		MapTile cellar = mapper.currentTile();
		assertTrue(!cellar.getLevelId().equals(main.getLevelId()));
		assertTrue(!cellar.getLevelId().equals(top.getLevelId()));
		assertEquals(3, mapper.getMap().getTiles().size());
		assertEquals("[u, d]", commands(main).toString());
	}

	@Test
	public void upNeverTakesOverADifferentRoomOnTheFloorAbove() {
		MapperController mapper = batmud();
		mapper.setGmcpCreateExits(false);
		mapper.onOutputText("@roomid a\n@dir , up,east\n");
		mapper.onOutputText("@roomid a2\n@dir up, down,east\n");
		mapper.onOutputText("@roomid a3\n@dir east, west\n");
		MapTile a3 = mapper.currentTile();
		mapper.onOutputText("@roomid a2\n@dir west, down,east\n");
		mapper.onOutputText("@roomid a\n@dir down, up,east\n");
		mapper.onOutputText("@roomid b\n@dir east, west,up\n");
		MapTile b = mapper.currentTile();
		assertEquals(a3.getGridX(), b.getGridX());
		mapper.onOutputText("@roomid b2\n@dir up, down\n");
		MapTile b2 = mapper.currentTile();
		assertEquals("b2", b2.getExternalId());
		assertEquals("a3", a3.getExternalId());
		assertEquals(a3.getLevelId(), b2.getLevelId());
		assertEquals(5, mapper.getMap().getTiles().size());
	}

	@Test
	public void aTypedUpChangesFloorWhenTheServerNamesNoMove() {
		MapperController mapper = batmud();
		mapper.setGmcpCreateExits(false);
		mapper.onOutputText("@roomid a\n@dir , up\n");
		MapTile a = mapper.currentTile();
		mapper.onPlayerCommand("u");
		mapper.onOutputText("@roomid b\n@dir , down\n");
		assertTrue(!a.getLevelId().equals(mapper.currentTile().getLevelId()));
		assertEquals("[u]", commands(a).toString());
	}

	/** a b on one row, c above a and d above b; then c east is a new room x, but d is there. */
	private static MapperController clashEastOfC() {
		MapperController mapper = batmud();
		mapper.setGmcpCreateExits(false);
		mapper.setEditMode(true);
		mapper.setCaptureTitleRegex("^@roomshort (.*)$");
		mapper.onOutputText("@roomid a\n@dir , east,north\n");
		mapper.onOutputText("@roomid b\n@dir east, west,north\n");
		mapper.onOutputText("@roomid d\n@dir north, south\n");
		mapper.onOutputText("@roomid b\n@dir south, west,north\n");
		mapper.onOutputText("@roomid a\n@dir west, east,north\n");
		mapper.onOutputText("@roomid c\n@roomshort West wall\n@dir north, south,east\n");
		assertNull(mapper.lastMisfitHint());
		mapper.onOutputText("@roomid x\n@dir east, west\n");
		return mapper;
	}

	private static MapTile byId(final MapperController mapper, final String id) {
		for (MapTile t : mapper.getMap().getTiles()) {
			if (id.equals(t.getExternalId())) {
				return t;
			}
		}
		return null;
	}

	@Test
	public void aRoomThatDoesNotFitSaysWhyAndHowToFixIt() {
		MapperController mapper = clashEastOfC();
		String hint = mapper.lastMisfitHint();
		assertNotNull(hint);
		assertTrue(hint, hint.startsWith("Mapper: you went east from \u00abWest wall\u00bb, but the map already has the room with id d east of it"));
		assertTrue(hint, hint.contains(".map shift on its own"));
		MapTile c = byId(mapper, "c");
		MapTile x = byId(mapper, "x");
		assertTrue(x.getGridX() != c.getGridX() + 1 || x.getGridY() != c.getGridY());
	}

	@Test
	public void shiftAlonePutsTheMisfitWhereItWasWalked() {
		MapperController mapper = clashEastOfC();
		MapTile c = byId(mapper, "c");
		MapTile d = byId(mapper, "d");
		int dx = d.getGridX();
		String status = mapper.shiftLastMisfit();
		assertTrue(status, status.startsWith("Mapper: moved 2 rooms east"));
		MapTile x = byId(mapper, "x");
		assertEquals(c.getGridX() + 1, x.getGridX());
		assertEquals(c.getGridY(), x.getGridY());
		assertEquals(dx + 1, d.getGridX());
		assertEquals(c.getGridX(), byId(mapper, "a").getGridX());
		assertTrue(mapper.shiftLastMisfit().startsWith("Mapper: no room is waiting"));
		mapper.undo();
		assertEquals(dx, byId(mapper, "d").getGridX());
	}

	@Test
	public void shiftAloneRefusesWhenTheMapChangedSince() {
		MapperController mapper = clashEastOfC();
		MapTile d = byId(mapper, "d");
		d.setGridY(d.getGridY() - 5);
		String status = mapper.shiftLastMisfit();
		assertTrue(status, status.startsWith("Mapper: the map has changed"));
	}

	@Test
	public void anotherNewRoomForgetsTheMisfit() {
		MapperController mapper = clashEastOfC();
		mapper.onOutputText("@roomid y\n@dir west, east\n");
		assertTrue(mapper.shiftLastMisfit().startsWith("Mapper: no room is waiting"));
	}

	/** The BatMUD player: "The 'Record on' really mislead me". */
	@Test
	public void recordOnWithAnIdRegexSaysItIsNotNeeded() {
		MapperController mapper = batmud();
		mapper.setEditMode(true);
		String status = mapper.setRecordingStatus(true);
		assertTrue(status, status.startsWith("Mapper recording: on. Record is not needed here"));
		assertTrue(status, status.endsWith(".map record off"));
		assertEquals("Mapper recording: off", mapper.setRecordingStatus(false));
	}

	@Test
	public void recordOnWithoutAnIdRegexSaysNothingMore() {
		MapperController mapper = new MapperController(new Object());
		mapper.setEditMode(true);
		assertEquals("Mapper recording: on", mapper.setRecordingStatus(true));
	}

	@Test
	public void nameNoLongerAsksForRecordWhenMappingFromText() {
		String status = batmud().nameFromBuffer();
		assertTrue(status, !status.contains("Record"));
		assertTrue(new MapperController(new Object()).nameFromBuffer().contains("Record is on"));
	}

	/** With Create missing exit neighbors on, c's east was guessed to d; walking it into x proves otherwise. */
	@Test
	public void aWalkReplacesAGuessedLinkToTheRoomInTheWay() {
		MapperController mapper = batmud();
		mapper.setEditMode(true);
		mapper.onOutputText("@roomid a\n@dir , east,north\n");
		mapper.onOutputText("@roomid b\n@dir east, west,north\n");
		mapper.onOutputText("@roomid d\n@dir north, south\n");
		mapper.onOutputText("@roomid b\n@dir south, west,north\n");
		mapper.onOutputText("@roomid a\n@dir west, east,north\n");
		mapper.onOutputText("@roomid c\n@dir north, south,east\n");
		MapTile c = mapper.currentTile();
		MapExit east = null;
		for (MapExit e : c.getExits()) {
			if ("e".equals(e.getCommand())) {
				east = e;
			}
		}
		assertEquals("d", mapper.getMap().findTile(east.getToId()).getExternalId());
		assertTrue(!east.isVerified());
		mapper.onOutputText("@roomid x\n@dir east, west\n");
		assertEquals("x", mapper.getMap().findTile(east.getToId()).getExternalId());
		assertTrue(east.isVerified());
		assertEquals(2, c.getExits().size());
	}

	@Test
	public void aWalkNeverReplacesAnotherWalk() {
		MapperController mapper = batmud();
		mapper.setGmcpCreateExits(false);
		mapper.onOutputText("@roomid a\n@dir , east\n");
		mapper.onOutputText("@roomid b\n@dir east, west\n");
		MapTile a = mapper.getMap().getTiles().get(0);
		mapper.onOutputText("@roomid a\n@dir west, east\n");
		mapper.onOutputText("@roomid z\n@dir east, west\n");
		assertEquals("b", mapper.getMap().findTile(a.getExits().get(0).getToId()).getExternalId());
	}

	/** A typed e that failed, then a flee: the arrival is not proof, so the link to d stays. */
	@Test
	public void aTypedMoveNeverReplacesAGuessedLink() {
		MapperController mapper = batmud();
		mapper.setEditMode(true);
		mapper.onOutputText("@roomid a\n@dir , east,north\n");
		mapper.onOutputText("@roomid b\n@dir east, west,north\n");
		mapper.onOutputText("@roomid d\n@dir north, south\n");
		mapper.onOutputText("@roomid b\n@dir south, west,north\n");
		mapper.onOutputText("@roomid a\n@dir west, east,north\n");
		mapper.onOutputText("@roomid c\n@dir north, south,east\n");
		MapTile c = mapper.currentTile();
		mapper.onPlayerCommand("e");
		mapper.onOutputText("@roomid r\n@dir , west\n");
		for (MapExit e : c.getExits()) {
			if ("e".equals(e.getCommand())) {
				assertEquals("d", mapper.getMap().findTile(e.getToId()).getExternalId());
				assertTrue(!e.isVerified());
			}
		}
	}
}

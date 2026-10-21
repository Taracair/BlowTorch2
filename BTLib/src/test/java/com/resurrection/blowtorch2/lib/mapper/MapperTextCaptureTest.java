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
}

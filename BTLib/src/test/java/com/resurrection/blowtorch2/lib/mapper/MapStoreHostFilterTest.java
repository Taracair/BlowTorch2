package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.resurrection.blowtorch2.lib.speedwalk.DirectionData;

/** Unit tests for per-world map ownership ({@link MapStore#mapBelongsToHost}). */
public class MapStoreHostFilterTest {

	@Test
	public void hostHintMatchIsCaseInsensitive() {
		assertTrue(MapStore.mapBelongsToHost("eden",
				"eden-test.rpgframework.de", "eden-test.rpgframework.de"));
		assertTrue(MapStore.mapBelongsToHost("eden",
				"EDEN-TEST.RPGFRAMEWORK.DE", "eden-test.rpgframework.de"));
	}

	@Test
	public void hostHintMismatchExcludesMap() {
		assertFalse(MapStore.mapBelongsToHost("samsara",
				"samsaramoo.com", "eden-test.rpgframework.de"));
	}

	@Test
	public void legacyFileNamedAfterHostMatches() {
		assertTrue(MapStore.mapBelongsToHost(
				"eden-test.rpgframework.de", null,
				"eden-test.rpgframework.de"));
	}

	@Test
	public void unclaimedDefaultVisibleToAnyWorld() {
		assertTrue(MapStore.mapBelongsToHost("default", null,
				"eden-test.rpgframework.de"));
		assertTrue(MapStore.mapBelongsToHost("default", "",
				"samsaramoo.com"));
	}

	@Test
	public void customLegacyNameWithoutHintDoesNotMatch() {
		assertFalse(MapStore.mapBelongsToHost("my-old-map", null,
				"eden-test.rpgframework.de"));
	}

	@Test
	public void lookIsNotRecordableEvenWhenDirectionMapMapsIt() {
		Map<String, DirectionData> dirs = new HashMap<String, DirectionData>();
		DirectionData d = new DirectionData();
		d.setDirection("l");
		d.setCommand("look");
		dirs.put("l", d);
		assertFalse(MapDirections.isRecordableMovement("look", null));
		// Follow still resolves through the direction map — recording must not.
		assertNotNull(MapDirections.normalize("look", dirs));
	}

	@Test
	public void southIsRecordable() {
		assertTrue(MapDirections.isRecordableMovement("south", null));
		assertTrue(MapDirections.isRecordableMovement("go south", null));
	}

	// ---- reading hostHint without parsing the whole map -------------------
	//
	// listMapsForHost asks every saved map who it belongs to, on the UI thread
	// when the mapper opens and on the connection handler when a world connects.
	// It reads the head of each file instead of loading it. These pin the two
	// things that makes safe: where toJson puts the field, and what the reader
	// does when the head does not answer the question.

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** Small on purpose: these cases are about the field, not about the budget,
	 *  and the org.json on the test classpath does not order keys the way the
	 *  device one does. Keeping the file inside the budget makes them decide the
	 *  same way either way. */
	private static MudMap mapWithHint(String hint) {
		MudMap map = new MudMap("id-1", "eden");
		map.setHostHint(hint);
		List<MapTile> tiles = new ArrayList<MapTile>();
		for (int i = 0; i < 3; i++) {
			MapTile t = new MapTile();
			t.setId("tile-" + i);
			t.setTitle("room " + i);
			tiles.add(t);
		}
		map.setTiles(tiles);
		return map;
	}

	private File writeFile(String name, String content) throws IOException {
		File f = tmp.newFile(name);
		FileOutputStream out = new FileOutputStream(f);
		try {
			out.write(content.getBytes("UTF-8"));
		} finally {
			out.close();
		}
		return f;
	}

	@Test
	public void hintIsFoundWhereverInTheFileItSitsAsLongAsTheFileIsRead() throws Exception {
		// Deliberately last. JSONObject's field order is an implementation
		// detail of whichever org.json is present (the device one keeps
		// insertion order; the one on the unit-test classpath does not), so the
		// reader must not depend on it for a correct answer.
		File f = writeFile("hintlast.json",
				"{\n  \"version\": 1,\n  \"tiles\": [],\n"
						+ "  \"hostHint\": \"eden-test.rpgframework.de\"\n}");
		assertEquals("eden-test.rpgframework.de", MapStore.readHostHintFromPrefix(f));
	}

	@Test
	public void prefixReadFindsTheHintInARealMapFile() throws Exception {
		File f = writeFile("eden.json",
				MapStore.toJson(mapWithHint("eden-test.rpgframework.de")).toString(2));
		assertEquals("eden-test.rpgframework.de", MapStore.readHostHintFromPrefix(f));
	}

	@Test
	public void emptyHintInARealMapFileReadsAsEmptyNotUnknown() throws Exception {
		File f = writeFile("nohint.json", MapStore.toJson(mapWithHint("")).toString(2));
		assertEquals("", MapStore.readHostHintFromPrefix(f));
	}

	@Test
	public void shortFileWithoutTheFieldIsAnswered() throws Exception {
		File f = writeFile("bare.json", "{\n  \"version\": 1,\n  \"name\": \"x\"\n}");
		// The whole file fit in the budget, so "no hint" is a fact, not a maybe.
		assertEquals("", MapStore.readHostHintFromPrefix(f));
	}

	@Test
	public void hintPastTheBudgetIsReportedAsUnknownSoTheCallerParses() throws Exception {
		StringBuilder sb = new StringBuilder("{\n  \"padding\": \"");
		for (int i = 0; i < 9000; i++) {
			sb.append('x');
		}
		sb.append("\",\n  \"hostHint\": \"samsaramoo.com\"\n}");
		File f = writeFile("late.json", sb.toString());
		assertNull("beyond the prefix the reader must defer, not claim there is none",
				MapStore.readHostHintFromPrefix(f));
	}

	@Test
	public void escapesInTheHintAreUndone() throws Exception {
		// On disk the value is an escaped slash followed by a unicode escape for
		// the letter A. Built by concatenation because javac's unicode pass runs
		// over the whole source file, comments included, and rejects a stray
		// backslash-u wherever it appears.
		String uEscape = "\\" + "u0041";
		File f = writeFile("escaped.json",
				"{\n  \"version\": 1,\n  \"hostHint\": \"a\\/b" + uEscape + "\"\n}");
		assertEquals("a/bA", MapStore.readHostHintFromPrefix(f));
	}
}

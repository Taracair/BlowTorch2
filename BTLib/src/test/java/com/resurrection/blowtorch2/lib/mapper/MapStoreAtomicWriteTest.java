package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The map file must survive the process dying mid-save.
 * <p>
 * {@code MapStore.writeFile} used to open the map itself with
 * {@code new FileOutputStream(file)}, which empties the target before the first
 * byte of the new content is written. This process is killed routinely — the
 * cached-app freezer, a swipe from recents, low memory — and a kill inside that
 * window left a truncated map that no longer parsed. There is no way to kill a
 * JVM at the right instant from a unit test, so these pin the property that
 * makes the window impossible instead: the bytes go to a staging file and the
 * map is replaced by a rename.
 */
public class MapStoreAtomicWriteTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static MudMap smallMap(String id, String title) {
		MudMap map = new MudMap(id, "eden");
		map.setHostHint("eden-test.rpgframework.de");
		List<MapTile> tiles = new ArrayList<MapTile>();
		MapTile t = new MapTile();
		t.setId("tile-0");
		t.setTitle(title);
		tiles.add(t);
		map.setTiles(tiles);
		return map;
	}

	/** Linux inode number. Same file rewritten in place keeps it; a rename does not. */
	private static Object fileKey(File f) throws IOException {
		BasicFileAttributes a = Files.readAttributes(f.toPath(), BasicFileAttributes.class);
		return a.fileKey();
	}

	private static int countTempFiles(File dir) {
		String[] names = dir.list();
		if (names == null) {
			return 0;
		}
		int n = 0;
		for (String name : names) {
			if (name.endsWith(".tmp")) {
				n++;
			}
		}
		return n;
	}

	@Test
	public void savedMapReadsBack() throws Exception {
		File f = new File(tmp.getRoot(), "eden.json");
		MapStore.saveToFile(f, smallMap("id-1", "The Commons"));

		MudMap back = MapStore.loadFromFile(f);
		assertNotNull(back);
		assertEquals("id-1", back.getId());
		assertEquals(1, back.getTiles().size());
		assertEquals("The Commons", back.getTiles().get(0).getTitle());
	}

	/**
	 * The discriminator. Under the old in-place write the second save reuses the
	 * same inode, because the same file is truncated and refilled; that is
	 * exactly the state a kill could catch half-done.
	 */
	@Test
	public void rewriteReplacesTheFileInsteadOfTruncatingIt() throws Exception {
		File f = new File(tmp.getRoot(), "eden.json");
		MapStore.saveToFile(f, smallMap("id-1", "The Commons"));
		Object first = fileKey(f);
		assertNotNull("no inode reported — this test cannot run on this filesystem", first);

		MapStore.saveToFile(f, smallMap("id-1", "The Commons, rebuilt"));
		Object second = fileKey(f);

		assertFalse("the map was rewritten in place; a kill mid-write would truncate it",
				first.equals(second));
		assertEquals("The Commons, rebuilt",
				MapStore.loadFromFile(f).getTiles().get(0).getTitle());
	}

	@Test
	public void successfulSaveLeavesNoStagingFileBehind() throws Exception {
		File f = new File(tmp.getRoot(), "eden.json");
		MapStore.saveToFile(f, smallMap("id-1", "The Commons"));
		MapStore.saveToFile(f, smallMap("id-1", "again"));

		assertEquals("staging files must not accumulate in the maps directory",
				0, countTempFiles(tmp.getRoot()));
	}

	/**
	 * A save that cannot complete must leave the previous map alone. The failure
	 * is forced by making the target un-renameable-onto (a non-empty directory);
	 * what is being pinned is that the old bytes are still there afterwards and
	 * that the staging file is cleaned up.
	 */
	@Test
	public void failedSaveKeepsTheOldMapAndCleansUp() throws Exception {
		File blocked = new File(tmp.getRoot(), "blocked.json");
		assertTrue(blocked.mkdir());
		File inside = new File(blocked, "keep-me");
		FileOutputStream out = new FileOutputStream(inside);
		try {
			out.write("intact".getBytes("UTF-8"));
		} finally {
			out.close();
		}

		try {
			MapStore.saveToFile(blocked, smallMap("id-1", "The Commons"));
			fail("a save that cannot replace the target must report it");
		} catch (IOException expected) {
			// the caller still has the previous map
		}

		assertTrue("the existing map was destroyed by a failed save", inside.isFile());
		assertEquals(6, inside.length());
		assertEquals("staging file left behind after a failed save",
				0, countTempFiles(tmp.getRoot()));
	}
}

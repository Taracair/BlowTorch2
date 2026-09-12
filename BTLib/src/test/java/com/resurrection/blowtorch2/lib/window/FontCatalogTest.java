package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class FontCatalogTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void pickerDoesNotListVeraNextToDejaVu() {
		List<FontCatalog.Face> faces = FontCatalog.bundledPickerFaces();
		HashSet<String> paths = new HashSet<String>();
		for (FontCatalog.Face f : faces) {
			paths.add(f.path);
		}
		assertTrue(paths.contains("fonts/DejaVuSansMono.ttf"));
		assertTrue(paths.contains("fonts/FairfaxHD.ttf"));
		assertTrue(paths.contains("fonts/JetBrainsMonoNL-Regular.ttf"));
		assertTrue(paths.contains("fonts/UbuntuSansMono-Regular.ttf"));
		assertTrue(paths.contains("fonts/AtkinsonHyperlegibleMono-Regular.ttf"));
		assertTrue(paths.contains("fonts/Inconsolata-Regular.ttf"));
		assertFalse("Vera is DejaVu's parent design; keep the file, drop the row",
				paths.contains("fonts/VeraMono.ttf"));
		assertFalse(paths.contains("/system/fonts/"));
	}

	@Test
	public void pickerDoesNotDumpTheSystemFontsDirectory() {
		for (FontCatalog.Face f : FontCatalog.optionalSystemFaces()) {
			assertTrue(f.path.startsWith("/system/fonts/"));
			assertTrue(FontCatalog.isFontFileName(f.path));
		}
		assertEquals(4, FontCatalog.optionalSystemFaces().size());
	}

	@Test
	public void assembleAddsOnlySystemFilesThatExistThenLoadRow() {
		List<FontCatalog.Face> rows = FontCatalog.assemblePicker(
				Arrays.asList("/system/fonts/DroidSansMono.ttf"),
				Arrays.asList(new FontCatalog.Face("/tmp/mine.ttf", "Mine")),
				true);
		HashSet<String> paths = new HashSet<String>();
		for (FontCatalog.Face f : rows) {
			paths.add(f.path);
		}
		assertTrue(paths.contains("/system/fonts/DroidSansMono.ttf"));
		assertFalse(paths.contains("/system/fonts/CutiveMono.ttf"));
		assertTrue(paths.contains("/tmp/mine.ttf"));
		assertEquals(FontCatalog.LOAD_FROM_STORAGE,
				rows.get(rows.size() - 1).path);
		assertEquals("Load from storage…", rows.get(rows.size() - 1).label);
	}

	@Test
	public void assembleDoesNotDuplicateAUserFileThatIsAlreadyBundled() {
		List<FontCatalog.Face> rows = FontCatalog.assemblePicker(
				null,
				Arrays.asList(new FontCatalog.Face("fonts/DejaVuSansMono.ttf",
						"copy")),
				false);
		int n = 0;
		for (FontCatalog.Face f : rows) {
			if ("fonts/DejaVuSansMono.ttf".equals(f.path)) {
				n++;
			}
		}
		assertEquals(1, n);
	}

	@Test
	public void displayNameKeepsBundledLabels() {
		assertEquals("DejaVu Sans Mono",
				FontCatalog.displayName("fonts/DejaVuSansMono.ttf"));
		assertEquals("Fairfax HD", FontCatalog.displayName("fonts/FairfaxHD.ttf"));
		assertEquals("Bitstream Vera Sans Mono",
				FontCatalog.displayName("fonts/VeraMono.ttf"));
		assertEquals("Sans serif", FontCatalog.displayName("sans serif"));
		assertEquals("Sans serif", FontCatalog.displayName("sans serrif"));
		assertEquals("My Font",
				FontCatalog.displayName("/storage/emulated/0/BlowTorch/My_Font.ttf"));
	}

	@Test
	public void otfCountsAsAFontFile() {
		assertTrue(FontCatalog.isFontFileName("FairfaxHD.otf"));
		assertTrue(FontCatalog.isBundledAssetPath("fonts/Foo.otf"));
		assertFalse(FontCatalog.isFontFileName("notes.txt"));
		assertFalse(FontCatalog.isBundledAssetPath("/system/fonts/RobotoMono-Regular.ttf"));
	}

	@Test
	public void sanitizeImportNameStripsPathAndJunk() {
		assertEquals("MyFont.ttf",
				FontCatalog.sanitizeImportFileName("../../My Font!.ttf"));
		assertEquals("imported.ttf", FontCatalog.sanitizeImportFileName(null));
		assertEquals("imported.ttf", FontCatalog.sanitizeImportFileName("***"));
		assertEquals("plain.ttf", FontCatalog.sanitizeImportFileName("plain"));
		assertEquals("mine.otf", FontCatalog.sanitizeImportFileName("mine.otf"));
	}

	@Test
	public void uniqueImportTargetAddsASuffixWhenTheFileExists() throws Exception {
		File dir = tmp.newFolder("fonts");
		File first = FontCatalog.uniqueImportTarget(dir, "Mine.ttf");
		assertEquals("Mine.ttf", first.getName());
		assertTrue(first.createNewFile());
		File second = FontCatalog.uniqueImportTarget(dir, "Mine.ttf");
		assertEquals("Mine-2.ttf", second.getName());
	}
}

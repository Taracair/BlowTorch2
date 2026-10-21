package com.resurrection.blowtorch2.lib.button;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class IconCommandTest {

	private static Map<String, List<String>> catalog() {
		Map<String, List<String>> catalog = new LinkedHashMap<String, List<String>>();
		catalog.put("marks", Arrays.asList("sword", "shield"));
		catalog.put("relics", Arrays.asList("bell"));
		catalog.put("signals", Arrays.asList("bell"));
		return catalog;
	}

	@Test
	public void blankIsStatusAndHelpIsTheWall() {
		IconCommandParser.Result blank = IconCommandParser.parse(null);
		assertEquals(IconCommandParser.ACTION_STATUS, blank.action);
		assertNull(blank.error);

		IconCommandParser.Result help = IconCommandParser.parse("help");
		assertEquals(IconCommandParser.ACTION_HELP, help.action);
		assertTrue(IconCommandParser.parse(".icon help extra").error.contains(".icon set"));
	}

	@Test
	public void setParsesLabelSlotAndClear() {
		IconCommandParser.Result main = IconCommandParser.parse("set LOOK marks/sword");
		assertEquals("LOOK", main.label);
		assertEquals(IconCommandParser.SLOT_MAIN, main.slot);
		assertEquals("marks/sword", main.token);
		assertFalse(main.clear);

		IconCommandParser.Result quoted = IconCommandParser.parse("set \"look north\" sword.png");
		assertEquals("look north", quoted.label);
		assertEquals("sword.png", quoted.token);

		IconCommandParser.Result single = IconCommandParser.parse("set 'LOOK' off");
		assertEquals("LOOK", single.label);
		assertTrue(single.clear);
		assertEquals(IconCommandParser.SLOT_MAIN, single.slot);

		IconCommandParser.Result held = IconCommandParser.parse("set LOOK held wards/lock");
		assertEquals(IconCommandParser.SLOT_HELD, held.slot);
		assertEquals("wards/lock", held.token);

		IconCommandParser.Result darken = IconCommandParser.parse("set LOOK HELD DARKEN");
		assertEquals(IconCommandParser.SLOT_HELD, darken.slot);
		assertTrue(darken.clear);

		IconCommandParser.Result same = IconCommandParser.parse("set LOOK flip same");
		assertEquals(IconCommandParser.SLOT_FLIP, same.slot);
		assertTrue(same.clear);

		IconCommandParser.Result namedHeld = IconCommandParser.parse("set LOOK held");
		assertEquals(IconCommandParser.SLOT_MAIN, namedHeld.slot);
		assertEquals("held", namedHeld.token);
		assertTrue(IconCommandParser.parse("set LOOK held darken extra").error.contains(".icon set"));
		assertEquals("Name the button.", IconCommandParser.parse("set \"\" sword").error);
		assertEquals("Close the quote around the button label.",
				IconCommandParser.parse("set \"look sword").error);
		assertEquals("look", IconCommandParser.parse("set look marks/sword").label);
	}

	@Test
	public void listAndFolderRejectExtraWords() {
		assertEquals(IconCommandParser.ACTION_LIST, IconCommandParser.parse("list").action);
		assertEquals(IconCommandParser.ACTION_LIST_FILES, IconCommandParser.parse("list FILES").action);
		IconCommandParser.Result set = IconCommandParser.parse("list Marks");
		assertEquals(IconCommandParser.ACTION_LIST_SET, set.action);
		assertEquals("Marks", set.listName);
		assertEquals(IconCommandParser.ACTION_FOLDER, IconCommandParser.parse("folder").action);
		assertTrue(IconCommandParser.parse("folder please").error.contains(".icon folder"));
		assertTrue(IconCommandParser.parse("nope").error.contains(".icon help"));
	}

	@Test
	public void slashWithAnExtensionIsAFileInASubfolder() {
		IconFaceResolver.Folder folder = IconFaceResolver.Folder.of(Arrays.asList("sword.png"));
		IconFaceResolver.Outcome path = IconFaceResolver.resolve("portrait/knight.png", catalog(), folder);
		assertEquals("named:portrait/knight.png", path.spec);
		assertNull(path.error);

		IconFaceResolver.Outcome none = IconFaceResolver.resolve("portrait/knight.png", catalog(),
				IconFaceResolver.Folder.none());
		assertEquals("No icon folder. .icon folder chooses one.", none.error);

		IconFaceResolver.Outcome bare = IconFaceResolver.resolve("portrait/knight", catalog(), folder);
		assertTrue(bare.error, bare.error.endsWith("needs its extension: portrait/knight.png."));
	}

	@Test
	public void namedPathRefusesTraversalAndEmptySegments() {
		assertEquals("portrait/knight.png", ButtonFaceRef.namedPath("portrait/knight.png"));
		assertEquals("a b/c/knight.png", ButtonFaceRef.namedPath("a b/c/knight.png"));
		assertEquals("knight.png", ButtonFaceRef.namedPath("knight.png"));
		assertNull(ButtonFaceRef.namedPath("/knight.png"));
		assertNull(ButtonFaceRef.namedPath("../knight.png"));
		assertNull(ButtonFaceRef.namedPath("portrait/../knight.png"));
		assertNull(ButtonFaceRef.namedPath("./knight.png"));
		assertNull(ButtonFaceRef.namedPath("portrait//knight.png"));
		assertNull(ButtonFaceRef.namedPath("portrait\\knight.png"));
		assertNull(ButtonFaceRef.namedPath("portrait/"));
		assertNull(ButtonFaceRef.namedPath("portrait/knight.txt"));
		assertEquals("portrait/knight.png", ButtonFaceRef.parse("named:portrait/knight.png").named);
	}

	@Test
	public void fileListShowsSubfolders() {
		assertEquals("mud:\n  portrait/\n  A file in a subfolder: .icon set <label> portrait/<file>.png\n"
				+ "  a.png\n", IconReports.files("mud", Arrays.asList("a.png"), Arrays.asList("portrait")));
	}

	@Test
	public void slashIsBuiltinEvenWhenTheFileExists() {
		IconFaceResolver.Folder folder = IconFaceResolver.Folder.of(Arrays.asList("sword.png", "sword.svg"));
		IconFaceResolver.Outcome slash = IconFaceResolver.resolve("marks/sword", catalog(), folder);
		assertEquals("builtin:marks/sword", slash.spec);
		assertNull(slash.error);

		IconFaceResolver.Outcome folded = IconFaceResolver.resolve("Marks/Sword", catalog(), folder);
		assertEquals("builtin:marks/sword", folded.spec);

		IconFaceResolver.Outcome missing = IconFaceResolver.resolve("marks/nope", catalog(), folder);
		assertEquals("No built-in icon marks/nope.", missing.error);
		assertNull(missing.spec);
	}

	@Test
	public void bareNamePrefersOneFile() {
		IconFaceResolver.Folder folder = IconFaceResolver.Folder.of(
				Arrays.asList("readme.txt", "sword.png"));
		IconFaceResolver.Outcome file = IconFaceResolver.resolve("sword", catalog(), folder);
		assertEquals("named:sword.png", file.spec);

		IconFaceResolver.Outcome capital = IconFaceResolver.resolve("SWORD", catalog(), folder);
		assertEquals("named:sword.png", capital.spec);

		IconFaceResolver.Outcome both = IconFaceResolver.resolve("sword", catalog(),
				IconFaceResolver.Folder.of(Arrays.asList("sword.png", "sword.svg")));
		assertTrue(both.error.contains("sword.png and sword.svg"));
		assertNull(both.spec);
	}

	@Test
	public void bareNameFallsThroughToAUniqueBuiltin() {
		IconFaceResolver.Outcome none = IconFaceResolver.resolve("sword", catalog(),
				IconFaceResolver.Folder.none());
		assertEquals("builtin:marks/sword", none.spec);

		IconFaceResolver.Outcome empty = IconFaceResolver.resolve("shield", catalog(),
				IconFaceResolver.Folder.of(Arrays.asList("other.png")));
		assertEquals("builtin:marks/shield", empty.spec);

		IconFaceResolver.Outcome bell = IconFaceResolver.resolve("bell", catalog(),
				IconFaceResolver.Folder.none());
		assertTrue(bell.error.contains("relics/bell"));
		assertTrue(bell.error.contains("signals/bell"));
		assertNull(bell.spec);

		IconFaceResolver.Outcome absent = IconFaceResolver.resolve("nope", catalog(),
				IconFaceResolver.Folder.none());
		assertEquals("No icon nope.", absent.error);
	}

	@Test
	public void explicitFileDoesNotFallThrough() {
		IconFaceResolver.Outcome missing = IconFaceResolver.resolve("sword.png", catalog(),
				IconFaceResolver.Folder.of(Arrays.asList("shield.png")));
		assertEquals("No file sword.png in the icon folder.", missing.error);

		IconFaceResolver.Outcome noFolder = IconFaceResolver.resolve("sword.png", catalog(),
				IconFaceResolver.Folder.none());
		assertEquals("No icon folder. .icon folder chooses one.", noFolder.error);

		IconFaceResolver.Outcome unread = IconFaceResolver.resolve("sword", catalog(),
				IconFaceResolver.Folder.unreadable());
		assertEquals("Cannot read the icon folder.", unread.error);

		IconFaceResolver.Outcome exact = IconFaceResolver.resolve("Sword.PNG", catalog(),
				IconFaceResolver.Folder.of(Arrays.asList("Sword.PNG")));
		assertEquals("named:Sword.PNG", exact.spec);

		IconFaceResolver.Outcome wrongCase = IconFaceResolver.resolve("sword.PNG", catalog(),
				IconFaceResolver.Folder.of(Arrays.asList("sword.png")));
		assertEquals("No file sword.PNG in the icon folder.", wrongCase.error);
	}

	@Test
	public void namedSpecRejectsTraversal() {
		assertEquals("sword.png", ButtonFaceRef.parse("named:sword.png").named);
		assertEquals("My Icon.png", ButtonFaceRef.parse("named:My Icon.png").named);
		assertEquals("épée.png", ButtonFaceRef.parse("named:épée.png").named);
		assertTrue(ButtonFaceRef.parse("named:../sword.png").isNone());
		assertEquals("a/b.png", ButtonFaceRef.parse("named:a/b.png").named);
		assertTrue(ButtonFaceRef.parse("named:/a/b.png").isNone());
		assertTrue(ButtonFaceRef.parse("named:sword.txt").isNone());
		assertTrue(ButtonFaceRef.parse("builtin:marks/sword").assetPng.endsWith("sword.png"));
		assertEquals("/tmp/sword.png", ButtonFaceRef.parse("file:/tmp/sword.png").file);
	}

	@Test
	public void reportsListSetsAndFiles() {
		String sets = IconReports.sets(catalog(), "Icon folder: mud");
		assertTrue(sets.contains("marks (2)"));
		assertTrue(sets.contains("Icon folder: mud"));
		assertTrue(IconReports.oneSet("marks", catalog().get("marks")).contains("  shield\n"));
		assertTrue(IconReports.files("mud", Arrays.asList("b.png", "a.png"), null).startsWith("mud:\n  a.png\n"));
		assertTrue(IconReports.status("").contains(".icon folder"));
		assertTrue(IconReports.status("Icon folder: mud").contains("sword.png"));
	}
}

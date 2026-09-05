package com.resurrection.blowtorch2.lib.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsOptionXmlTest;
import com.resurrection.blowtorch2.lib.settings.BaseParser;

/**
 * Favorite XML round-trip (writeTo + applyItemAttributes), comparator order,
 * and shortcut lookup. Avoids android.util.Xml / android.sax on the JVM.
 */
public class LauncherFavoriteXmlTest {

	@Test
	public void favoriteAttributeNameIsFavorite() {
		assertEquals("favorite", BaseParser.ATTR_FAVORITE);
	}

	@Test
	public void shortcutExtrasKeepExistingMainWindowNames() {
		assertEquals("DISPLAY", Launcher.EXTRA_DISPLAY);
		assertEquals("HOST", Launcher.EXTRA_HOST);
		assertEquals("PORT", Launcher.EXTRA_PORT);
		assertEquals("TLS", Launcher.EXTRA_TLS);
		assertEquals("LAUNCH_FROM_SHORTCUT", Launcher.EXTRA_LAUNCH_FROM_SHORTCUT);
	}

	@Test
	public void pinUsesLaunchWorldActionAndWorldUriNotMainLauncher() {
		assertEquals("com.resurrection.blowtorch2.LAUNCH_WORLD",
				LauncherShortcutExtras.ACTION_LAUNCH_WORLD);
		assertEquals("blowtorch", LauncherShortcutExtras.URI_SCHEME);
		assertEquals("world", LauncherShortcutExtras.URI_HOST);
		assertFalse("android.intent.action.MAIN".equals(
				LauncherShortcutExtras.ACTION_LAUNCH_WORLD));
		assertEquals("com.resurrection.blowtorch2.lib.launcher.WorldLaunchActivity",
				LauncherShortcutExtras.WORLD_LAUNCH_ACTIVITY);
		assertFalse(LauncherShortcutExtras.WORLD_LAUNCH_ACTIVITY.contains("FreeLauncher"));
	}

	@Test
	public void worldLaunchFlagsDoNotResetExistingTaskToServerList() {
		int flags = WorldLaunch.MAIN_WINDOW_LAUNCH_FLAGS;
		assertEquals("RESET_TASK_IF_NEEDED resumes the server-list task as-is",
				0, flags & android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		assertTrue((flags & android.content.Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
		assertTrue((flags & android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
		assertTrue((flags & android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
		assertEquals("pins must stay in Recents with the game task",
				0, flags & android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
	}

	@Test
	public void returnToServerListClearsOnlyWhenGameIsTaskRoot() {
		int fromPin = WorldLaunch.returnToServerListFlags(true);
		assertTrue((fromPin & android.content.Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
		assertTrue((fromPin & android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0);
		assertEquals("CLEAR_TOP on a pin-rooted task would finish the list with the game",
				0, fromPin & android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);

		int fromList = WorldLaunch.returnToServerListFlags(false);
		assertTrue((fromList & android.content.Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
		assertTrue((fromList & android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
		assertTrue((fromList & android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
		assertEquals("CLEAR_TASK would drop the live server list under the game",
				0, fromList & android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
	}

	@Test
	public void copyPreservesFavorite() {
		MudConnection src = world("Acheron", "host.example", "4000");
		src.setFavorite(true);
		MudConnection copy = src.copy();
		assertTrue(copy.isFavorite());
		src.setFavorite(false);
		assertTrue("copy is independent", copy.isFavorite());
		assertFalse(src.copy().isFavorite());
	}

	@Test
	public void writeOmitsFavoriteWhenFalseAndWritesTrueWhenStarred() throws Exception {
		LauncherSettings settings = new LauncherSettings();
		MudConnection plain = world("plain", "a.example", "4000");
		MudConnection starred = world("starred", "b.example", "4001");
		starred.setFavorite(true);
		settings.getList().put(plain.getDisplayName(), plain);
		settings.getList().put(starred.getDisplayName(), starred);

		String xml = writeList(settings);
		String starredTag = itemTag(xml, "starred");
		String plainTag = itemTag(xml, "plain");
		assertNotNull(starredTag);
		assertNotNull(plainTag);
		assertTrue(starredTag.contains("favorite=\"true\""));
		assertFalse(plainTag.contains("favorite="));
	}

	@Test
	public void roundTripFavoriteTrueAndFalse() throws Exception {
		LauncherSettings settings = new LauncherSettings();
		MudConnection starred = world("starred", "b.example", "4001");
		starred.setFavorite(true);
		starred.setUseTls(true);
		MudConnection plain = world("plain", "a.example", "4000");
		settings.getList().put(starred.getDisplayName(), starred);
		settings.getList().put(plain.getDisplayName(), plain);

		String xml = writeList(settings);
		MudConnection backStarred = parseItem(xml, "starred");
		MudConnection backPlain = parseItem(xml, "plain");
		assertNotNull(backStarred);
		assertNotNull(backPlain);
		assertTrue(backStarred.isFavorite());
		assertFalse(backPlain.isFavorite());
		assertTrue(backStarred.isUseTls());
		assertFalse(backPlain.isUseTls());
	}

	@Test
	public void absentFavoriteParsesFalseAndClearsPriorTrue() {
		MudConnection dest = new MudConnection();
		LauncherSAXParser.applyItemAttributes(dest, "once", "h", "1", "never",
				"", null, null, "true");
		assertTrue(dest.isFavorite());
		LauncherSAXParser.applyItemAttributes(dest, "twice", "h", "1", "never",
				"", null, null, null);
		assertFalse("reused parser object must not leak favorite", dest.isFavorite());
	}

	@Test
	public void comparatorTutorialThenFavoritesThenLastPlayed() {
		MudConnection tutorial = BuiltinTutorial.buildEntry();
		MudConnection favNever = world("fav-never", "f.example", "1");
		favNever.setFavorite(true);
		favNever.setLastPlayed("never");
		MudConnection favPlayed = world("fav-played", "g.example", "2");
		favPlayed.setFavorite(true);
		favPlayed.setLastPlayed("20260101T120000");
		MudConnection plainPlayed = world("plain-played", "p.example", "3");
		plainPlayed.setLastPlayed("20260801T120000");
		MudConnection plainNever = world("plain-never", "q.example", "4");
		plainNever.setLastPlayed("never");

		ArrayList<MudConnection> list = new ArrayList<MudConnection>();
		list.add(plainNever);
		list.add(plainPlayed);
		list.add(favNever);
		list.add(tutorial);
		list.add(favPlayed);
		Collections.sort(list, new Launcher.ConnectionComparator());

		assertTrue(BuiltinTutorial.isTutorialEntry(list.get(0)));
		assertTrue(list.get(1).isFavorite());
		assertTrue(list.get(2).isFavorite());
		assertFalse(list.get(3).isFavorite());
		assertFalse(list.get(4).isFavorite());
		assertEquals("plain-played", list.get(3).getDisplayName());
		assertEquals("plain-never", list.get(4).getDisplayName());
		assertEquals("fav-played", list.get(1).getDisplayName());
		assertEquals("fav-never", list.get(2).getDisplayName());
	}

	@Test
	public void findLaunchTargetByDisplayThenHostPort() {
		LauncherSettings settings = new LauncherSettings();
		MudConnection world = world("Discworld", "discworld.starturtle.net", "4242");
		settings.getList().put(world.getDisplayName(), world);

		assertEquals(world, Launcher.findLaunchTarget(settings, "Discworld", null, null));
		assertEquals(world, Launcher.findLaunchTarget(settings, "gone",
				"discworld.starturtle.net", "4242"));
		assertNull(Launcher.findLaunchTarget(settings, "gone", "no.example", "1"));
		assertNull(Launcher.findLaunchTarget(settings, null, null, null));
	}

	@Test
	public void shortcutIdSanitizesDisplayName() {
		assertTrue(Launcher.shortcutIdForDisplayName("Discworld").startsWith("Discworld_"));
		assertEquals("Acheron_MUD_" + Integer.toHexString("Acheron MUD".hashCode()),
				Launcher.shortcutIdForDisplayName("Acheron MUD"));
		assertFalse(Launcher.shortcutIdForDisplayName("Acheron")
				.equals(Launcher.shortcutIdForDisplayName("Acheron!")));
		assertTrue(Launcher.shortcutIdForDisplayName("***").startsWith("world_"));
		assertEquals("world", Launcher.shortcutIdForDisplayName(null));
	}

	private static MudConnection world(String name, String host, String port) {
		MudConnection m = new MudConnection();
		m.setDisplayName(name);
		m.setHostName(host);
		m.setPortString(port);
		return m;
	}

	private static String writeList(LauncherSettings settings) throws Exception {
		SettingsOptionXmlTest.RecordingXmlSerializer out =
				new SettingsOptionXmlTest.RecordingXmlSerializer();
		LauncherSettings.writeTo(settings, out);
		return out.toString();
	}

	private static String itemTag(String xml, String name) {
		Pattern item = Pattern.compile("<item\\s[^>]*>");
		Matcher matcher = item.matcher(xml);
		while (matcher.find()) {
			String tag = matcher.group();
			if (tag.contains("name=\"" + name + "\"")) {
				return tag;
			}
		}
		return null;
	}

	private static MudConnection parseItem(String xml, String name) {
		String tag = itemTag(xml, name);
		if (tag == null) {
			return null;
		}
		HashMap<String, String> attrs = new HashMap<String, String>();
		Matcher m = Pattern.compile("(\\w+)=\"([^\"]*)\"").matcher(tag);
		while (m.find()) {
			attrs.put(m.group(1), m.group(2));
		}
		MudConnection dest = new MudConnection();
		LauncherSAXParser.applyItemAttributes(dest,
				attrs.get(BaseParser.ATTR_NAME),
				attrs.get(BaseParser.ATTR_HOST),
				attrs.get(BaseParser.ATTR_PORT),
				attrs.get(BaseParser.ATTR_DATEPLAYED),
				attrs.get(BaseParser.ATTR_DESCRIPTION),
				attrs.get(BaseParser.ATTR_OFFLINE),
				attrs.get(BaseParser.ATTR_TLS),
				attrs.get(BaseParser.ATTR_FAVORITE));
		return dest;
	}
}

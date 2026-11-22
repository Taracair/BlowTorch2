package com.resurrection.blowtorch2.lib.launcher;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

public class ImportedServerNamesTest {

	@Test
	public void freeNameStays() {
		assertEquals("Foo", ImportedServerNames.allocate("Foo", new HashSet<String>()));
	}

	@Test
	public void takenNameGetsTheNextNumber() {
		HashSet<String> taken = new HashSet<String>();
		taken.add("Foo");
		assertEquals("Foo (2)", ImportedServerNames.allocate("Foo", taken));
		taken.add("Foo (2)");
		assertEquals("Foo (3)", ImportedServerNames.allocate("Foo", taken));
	}

	@Test
	public void numberedNameThatIsFreeStays() {
		HashSet<String> taken = new HashSet<String>();
		taken.add("Foo");
		assertEquals("Foo (2)", ImportedServerNames.allocate("Foo (2)", taken));
	}

	@Test
	public void numberedCollisionContinuesFromTheBase() {
		HashSet<String> taken = new HashSet<String>();
		taken.add("Foo");
		taken.add("Foo (2)");
		assertEquals("Foo (3)", ImportedServerNames.allocate("Foo (2)", taken));
	}

	@Test
	public void blankBecomesMud() {
		assertEquals("Mud", ImportedServerNames.allocate(null, new HashSet<String>()));
		assertEquals("Mud", ImportedServerNames.allocate("", new HashSet<String>()));
	}

	@Test
	public void numberedNameMustNotShareAnotherWorldsSettingsFile() {
		HashSet<String> taken = new HashSet<String>();
		taken.add("Foo");
		taken.add("Foo2");
		assertEquals("Foo (3)", ImportedServerNames.allocate("Foo", taken));
	}

	@Test
	public void launcherListNameIsNotReused() {
		assertEquals("blowtorch_launcher_list (2)",
				ImportedServerNames.allocate("blowtorch_launcher_list", new HashSet<String>()));
	}

	@Test
	public void duplicateMapKeyKeepsBoth() {
		HashSet<String> used = new HashSet<String>();
		assertEquals("Foo", LauncherSAXParser.mapKey("Foo", used, true));
		used.add("Foo");
		assertEquals("Foo", LauncherSAXParser.mapKey("Foo", used, false));
		String second = LauncherSAXParser.mapKey("Foo", used, true);
		assertEquals(false, "Foo".equals(second));
	}

	@Test
	public void batchClaimsExactNamesBeforeNumbers() {
		List<String> names = ImportedServerNames.assign(
				java.util.Collections.singleton("Foo"),
				java.util.Arrays.asList("Foo", "Foo2"),
				false);
		assertEquals("Foo (3)", names.get(0));
		assertEquals("Foo2", names.get(1));
	}

	@Test
	public void replaceDoesNotReuseAFileLeftOnThePhone() {
		List<String> names = ImportedServerNames.assign(
				java.util.Collections.singleton("Foo2"),
				java.util.Arrays.asList("Foo", "Foo"),
				true);
		assertEquals("Foo", names.get(0));
		assertEquals("Foo (3)", names.get(1));
	}

	@Test
	public void replaceDoesNotGiveANewRowAnExistingNumberedName() {
		List<String> names = ImportedServerNames.assign(
				java.util.Collections.singletonList("Foo (2)"),
				java.util.Arrays.asList("Foo", "Foo"),
				true);
		assertEquals("Foo", names.get(0));
		assertEquals("Foo (3)", names.get(1));
	}

	@Test
	public void replaceDoesNotGiveANewRowASharedNumberedName() {
		List<String> names = ImportedServerNames.assign(
				java.util.Arrays.asList("Foo (2)", "Foo2"),
				java.util.Arrays.asList("Foo", "Foo"),
				true);
		assertEquals("Foo", names.get(0));
		assertEquals("Foo (3)", names.get(1));
	}

	@Test
	public void replaceKeepsFoo2WhenAnotherNameSharesItsFile() {
		List<String> names = ImportedServerNames.assign(
				java.util.Arrays.asList("Foo (2)", "Foo2"),
				java.util.Collections.singletonList("Foo2"),
				true);
		assertEquals("Foo2", names.get(0));
	}

	@Test
	public void replaceKeepsBothNamesThatShareAFile() {
		List<String> names = ImportedServerNames.assign(
				java.util.Arrays.asList("Foo (2)", "Foo2"),
				java.util.Arrays.asList("Foo (2)", "Foo2"),
				true);
		assertEquals("Foo (2)", names.get(0));
		assertEquals("Foo2", names.get(1));
	}

	@Test
	public void replaceKeepsTheSameDisplayName() {
		List<String> names = ImportedServerNames.assign(
				java.util.Collections.singleton("Foo2"),
				java.util.Collections.singletonList("Foo2"),
				true);
		assertEquals("Foo2", names.get(0));
	}

	@Test
	public void replaceDoesNotGiveTheTutorialFileToALookalike() {
		List<String> names = ImportedServerNames.assign(
				java.util.Collections.singleton(BuiltinTutorial.DISPLAY_NAME),
				java.util.Collections.singletonList("Starter-Tutorial"),
				true);
		assertEquals("Starter-Tutorial (2)", names.get(0));
	}

	@Test
	public void twoImportsOfTheSameName() {
		HashSet<String> taken = new HashSet<String>();
		taken.add("Foo");
		String first = ImportedServerNames.allocate("Foo", taken);
		taken.add(first);
		String second = ImportedServerNames.allocate("Foo", taken);
		assertEquals("Foo (2)", first);
		assertEquals("Foo (3)", second);
	}

	@Test
	public void byNameSortsDisplayThenHost() {
		MudConnection later = row("B", "b.example", "2");
		MudConnection earlier = row("a", "a.example", "1");
		List<MudConnection> ordered = ImportedServerNames.byName(Arrays.asList(later, earlier, null));
		assertEquals(2, ordered.size());
		assertEquals("a", ordered.get(0).getDisplayName());
		assertEquals("B", ordered.get(1).getDisplayName());
	}

	private static MudConnection row(String name, String host, String port) {
		MudConnection connection = new MudConnection();
		connection.setDisplayName(name);
		connection.setHostName(host);
		connection.setPortString(port);
		return connection;
	}
}

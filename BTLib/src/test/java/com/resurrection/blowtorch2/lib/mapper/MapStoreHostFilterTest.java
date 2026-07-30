package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

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
}

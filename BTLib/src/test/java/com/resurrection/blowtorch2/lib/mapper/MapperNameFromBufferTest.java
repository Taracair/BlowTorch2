package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.mapper.MapperController.CapturePreview;

public class MapperNameFromBufferTest {

	@Test
	public void titleRegexTakesTheFirstCapitalLine() {
		MapperController mapper = new MapperController(new Object());
		CapturePreview preview = mapper.previewCapture("^([A-Z].*)$", "$^",
				"you see dust\nTemple Square\nExits: north", 10);
		assertEquals("Temple Square", preview.title);
		assertEquals(null, preview.exits);
	}

	@Test
	public void nameFromBufferWaitsUntilRecording() {
		MapperController mapper = new MapperController(new Object());
		String idle = mapper.nameFromBuffer();
		assertTrue(idle, idle.contains("Record"));
	}

	@Test
	public void nameCreatesTheFirstTileWhenNoneExistsYet() {
		MapperController mapper = new MapperController(new Object());
		mapper.setEditMode(true);
		mapper.setRecording(true);
		assertEquals(null, mapper.currentTile());
		String named = mapper.applyRoomName("Temple Square");
		assertTrue(named, named.contains("Temple Square"));
		assertEquals("Temple Square", mapper.currentTile().getTitle());
	}

	@Test
	public void nameFromBufferWithNoLinesSaysSo() {
		MapperController mapper = new MapperController(new Object());
		mapper.setEditMode(true);
		mapper.setRecording(true);
		String empty = mapper.nameFromBuffer();
		assertTrue(empty, empty.contains("no recent lines"));
	}
}

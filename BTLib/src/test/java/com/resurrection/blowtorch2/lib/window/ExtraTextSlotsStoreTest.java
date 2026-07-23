package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;

public class ExtraTextSlotsStoreTest {

	@Test
	public void normalizeName_acceptsValid() {
		assertEquals("chat", ExtraTextSlotsStore.normalizeName("Chat"));
		assertEquals("tells_1", ExtraTextSlotsStore.normalizeName(" tells_1 "));
	}

	@Test
	public void normalizeName_rejectsReservedAndInvalid() {
		assertNull(ExtraTextSlotsStore.normalizeName("main"));
		assertNull(ExtraTextSlotsStore.normalizeName("mainDisplay"));
		assertNull(ExtraTextSlotsStore.normalizeName("button_window"));
		assertNull(ExtraTextSlotsStore.normalizeName("Bad-Name"));
		assertNull(ExtraTextSlotsStore.normalizeName(""));
		assertNull(ExtraTextSlotsStore.normalizeName(null));
	}

	@Test
	public void parse_invalidJson_returnsEmpty() {
		assertTrue(ExtraTextSlotsStore.parse(null).isEmpty());
		assertTrue(ExtraTextSlotsStore.parse("").isEmpty());
		assertTrue(ExtraTextSlotsStore.parse("{not-array}").isEmpty());
		assertTrue(ExtraTextSlotsStore.parse("null").isEmpty());
	}

	@Test
	public void parseAndToJson_roundTrip() {
		String json = "["
				+ "{\"name\":\"chat\",\"title\":\"Chat\",\"mode\":\"drawer_top\","
				+ "\"height_dp\":160,\"float_x\":24,\"float_y\":120,\"float_w\":320,\"float_h\":220,"
				+ "\"opacity\":70,\"visible\":true,\"collapsed\":false,"
				+ "\"gmcp\":[\"Char.Vitals\",\"Comm.*\"]}"
				+ "]";
		ArrayList<ExtraTextSlot> slots = ExtraTextSlotsStore.parse(json);
		assertEquals(1, slots.size());
		assertEquals("chat", slots.get(0).getName());
		assertEquals(ExtraTextSlot.Mode.DRAWER_TOP, slots.get(0).getMode());
		assertEquals(70, slots.get(0).getOpacity());
		assertEquals(2, slots.get(0).getGmcpModules().size());
		assertTrue(slots.get(0).matchesGmcpModule("Char.Vitals"));
		assertTrue(slots.get(0).matchesGmcpModule("Comm.Channel"));
		assertFalse(slots.get(0).matchesGmcpModule("Room.Info"));
		String out = ExtraTextSlotsStore.toJson(slots);
		ArrayList<ExtraTextSlot> again = ExtraTextSlotsStore.parse(out);
		assertEquals(1, again.size());
		assertEquals("chat", again.get(0).getName());
		assertEquals("Chat", again.get(0).getTitle());
		assertEquals(ExtraTextSlot.Mode.DRAWER_TOP, again.get(0).getMode());
		assertEquals(70, again.get(0).getOpacity());
		assertTrue(again.get(0).matchesGmcpModule("comm.channel"));
	}

	@Test
	public void parse_legacyDrawerBottom_mapsToTop() {
		String json = "[{\"name\":\"old\",\"mode\":\"drawer_bottom\"}]";
		ArrayList<ExtraTextSlot> slots = ExtraTextSlotsStore.parse(json);
		assertEquals(1, slots.size());
		assertEquals(ExtraTextSlot.Mode.DRAWER_TOP, slots.get(0).getMode());
		assertEquals("drawer_top", slots.get(0).getMode().toJsonValue());
	}

	@Test
	public void gmcpPattern_exactAndPrefix() {
		ExtraTextSlot s = new ExtraTextSlot("vitals");
		s.setGmcpModulesCsv("Char.Vitals, Char., Room.*");
		assertTrue(s.matchesGmcpModule("Char.Vitals"));
		assertTrue(s.matchesGmcpModule("char.status"));
		assertTrue(s.matchesGmcpModule("Room.Info"));
		assertFalse(s.matchesGmcpModule("Comm.Channel"));
		assertEquals("Char.Vitals, Char., Room.*", s.getGmcpModulesCsv());
	}
	@Test
	public void opacity_clampedToReadableRange() {
		ExtraTextSlot s = new ExtraTextSlot("chat");
		s.setOpacity(10);
		assertEquals(40, s.getOpacity());
		s.setOpacity(200);
		assertEquals(100, s.getOpacity());
	}

	@Test
	public void parse_skipsReservedAndCapsAtMax() {
		StringBuilder sb = new StringBuilder("[");
		sb.append("{\"name\":\"main\"},");
		for (int i = 0; i < 12; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append("{\"name\":\"w").append(i).append("\"}");
		}
		sb.append(']');
		ArrayList<ExtraTextSlot> slots = ExtraTextSlotsStore.parse(sb.toString());
		assertEquals(ExtraTextSlotsStore.MAX_SLOTS, slots.size());
		assertFalse(slots.isEmpty());
		assertNotNull(ExtraTextSlotsStore.normalizeName(slots.get(0).getName()));
	}
}

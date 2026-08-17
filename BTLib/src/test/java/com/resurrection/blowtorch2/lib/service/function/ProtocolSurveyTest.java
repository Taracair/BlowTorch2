package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class ProtocolSurveyTest {

	@Test
	public void notConnectedSaysSoAndDoesNotEnable() {
		ProtocolSurvey.Snapshot s = new ProtocolSurvey.Snapshot();
		String report = ProtocolSurvey.format(s);
		assertTrue(report.contains("Not connected"));
		assertTrue(ProtocolSurvey.keysToEnable(s).isEmpty());
	}

	@Test
	public void gmcpOfferedButOffIsRefusedAndEnableable() {
		ProtocolSurvey.Snapshot s = new ProtocolSurvey.Snapshot();
		s.connected = true;
		s.offeredGmcp = true;
		s.useGmcp = false;
		s.sawOsc8 = true;
		s.osc8On = true;
		String report = ProtocolSurvey.format(s);
		assertTrue(report.contains("GMCP"));
		assertTrue(report.contains("server offered"));
		assertTrue(report.contains("we refused"));
		assertTrue(report.contains("StickMUD item hashes need GMCP"));
		List<String> keys = ProtocolSurvey.keysToEnable(s);
		assertEquals(1, keys.size());
		assertEquals(ProtocolSurvey.KEY_GMCP, keys.get(0));
	}

	@Test
	public void osc8SeenButOffIsEnableableWithoutReconnect() {
		ProtocolSurvey.Snapshot s = new ProtocolSurvey.Snapshot();
		s.connected = true;
		s.sawOsc8 = true;
		s.osc8On = false;
		List<String> keys = ProtocolSurvey.keysToEnable(s);
		assertEquals(1, keys.size());
		assertEquals(ProtocolSurvey.KEY_OSC8, keys.get(0));
		assertFalse(ProtocolSurvey.needsReconnect(ProtocolSurvey.KEY_OSC8));
		assertTrue(ProtocolSurvey.needsReconnect(ProtocolSurvey.KEY_GMCP));
		assertTrue(ProtocolSurvey.needsReconnect(ProtocolSurvey.KEY_MCP));
	}

	@Test
	public void quietWhenNothingOfferedOrOn() {
		ProtocolSurvey.Snapshot s = new ProtocolSurvey.Snapshot();
		s.connected = true;
		String report = ProtocolSurvey.format(s);
		assertTrue(report.contains("nothing offered"));
		assertTrue(ProtocolSurvey.keysToEnable(s).isEmpty());
	}

	@Test
	public void mcpHelloAndMccpLiveShowOnTheReport() {
		ProtocolSurvey.Snapshot s = new ProtocolSurvey.Snapshot();
		s.connected = true;
		s.offeredMcp = true;
		s.useMcp = true;
		s.mcpHandshaken = true;
		s.offeredMccp = true;
		s.useMccp = true;
		s.compressed = true;
		String report = ProtocolSurvey.format(s);
		assertTrue(report.contains("MCP"));
		assertTrue(report.contains("handshaken"));
		assertTrue(report.contains("compression on"));
		assertTrue(ProtocolSurvey.keysToEnable(s).isEmpty());
	}

	@Test
	public void enableDoesNotFlipMapperOrMtts() {
		ProtocolSurvey.Snapshot s = new ProtocolSurvey.Snapshot();
		s.connected = true;
		s.offeredGmcp = true;
		s.useGmcp = false;
		List<String> keys = ProtocolSurvey.keysToEnable(s);
		assertFalse(keys.contains("mapper_use_gmcp"));
		assertFalse(keys.contains("use_mtts"));
	}
}

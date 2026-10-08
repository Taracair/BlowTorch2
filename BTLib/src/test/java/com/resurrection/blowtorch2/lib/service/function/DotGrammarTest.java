package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DotGrammarTest {

	@Test
	public void echoBlahIsUsageAndBareIsStatus() {
		assertEquals(EchoCommand.ECHO_STATUS, EchoCommand.classify(""));
		assertEquals(EchoCommand.ECHO_ON, EchoCommand.classify("on"));
		assertEquals(EchoCommand.ECHO_OFF, EchoCommand.classify("off"));
		assertEquals(EchoCommand.ECHO_HELP, EchoCommand.classify("help"));
		assertEquals(EchoCommand.ECHO_BAD, EchoCommand.classify("blah"));
		assertEquals(EchoCommand.ECHO_BAD, EchoCommand.classify("on please"));
	}

	@Test
	public void protocolsBlahIsUsage() {
		assertEquals(ProtocolSurveyCommand.SURVEY, ProtocolSurveyCommand.classify(""));
		assertEquals(ProtocolSurveyCommand.HELP, ProtocolSurveyCommand.classify("help"));
		assertEquals(ProtocolSurveyCommand.ENABLE, ProtocolSurveyCommand.classify("enable"));
		assertEquals(ProtocolSurveyCommand.ENABLE, ProtocolSurveyCommand.classify("on"));
		assertEquals(ProtocolSurveyCommand.BAD, ProtocolSurveyCommand.classify("blah"));
		assertEquals(ProtocolSurveyCommand.BAD, ProtocolSurveyCommand.classify("enable please"));
	}

	@Test
	public void msspAndMsdpUnknownIsNotADump() {
		assertEquals(ProtocolsCommand.DUMP, ProtocolsCommand.classify("", false));
		assertEquals(ProtocolsCommand.HELP, ProtocolsCommand.classify("help", false));
		assertEquals(ProtocolsCommand.BAD, ProtocolsCommand.classify("blah", false));
		assertEquals(ProtocolsCommand.DUMP, ProtocolsCommand.classify("", true));
		assertEquals(ProtocolsCommand.VERB, ProtocolsCommand.classify("list", true));
		assertEquals(ProtocolsCommand.VERB, ProtocolsCommand.classify("send HP", true));
		assertEquals(ProtocolsCommand.BAD, ProtocolsCommand.classify("blah", true));
		assertEquals(ProtocolsCommand.BAD, ProtocolsCommand.classify("list extra extra", false));
	}

	@Test
	public void probeUnknownAndBadLinesAreNotTheCatalogue() {
		assertEquals(ProbeCommand.PROBE_CATALOGUE, ProbeCommand.classify(""));
		assertEquals(ProbeCommand.PROBE_RUN, ProbeCommand.classify("connection"));
		assertEquals(ProbeCommand.PROBE_RUN, ProbeCommand.classify("lines"));
		assertEquals(ProbeCommand.PROBE_RUN, ProbeCommand.classify("lines on"));
		assertEquals(ProbeCommand.PROBE_ERROR, ProbeCommand.classify("blah"));
		assertEquals(ProbeCommand.PROBE_ERROR, ProbeCommand.classify("on"));
		assertEquals(ProbeCommand.PROBE_ERROR, ProbeCommand.classify("lines foo"));
		assertEquals(ProbeCommand.PROBE_ERROR, ProbeCommand.classify("lines on please"));
	}

	@Test
	public void disconnectAndReconnectIgnoreNothingButBare() {
		assertTrue(DisconnectCommand.isBare(null));
		assertTrue(DisconnectCommand.isBare(""));
		assertTrue(DisconnectCommand.isBare("   "));
		assertFalse(DisconnectCommand.isBare("blah"));
		assertFalse(DisconnectCommand.isBare("off"));
		assertTrue(ReconnectCommand.isBare(""));
		assertFalse(ReconnectCommand.isBare("please"));
	}

	@Test
	public void heatmapFooDoesNotShow() {
		assertEquals(ButtonHeatCommand.SHOW, ButtonHeatCommand.classify(""));
		assertEquals(ButtonHeatCommand.SHOW, ButtonHeatCommand.classify("   "));
		assertEquals(ButtonHeatCommand.OFF, ButtonHeatCommand.classify("OFF"));
		assertEquals(ButtonHeatCommand.RESET, ButtonHeatCommand.classify("reset"));
		assertEquals(ButtonHeatCommand.BAD, ButtonHeatCommand.classify("foo"));
		assertEquals(ButtonHeatCommand.BAD, ButtonHeatCommand.classify("off please"));
	}

	@Test
	public void wrapKeepsSynonymsAndRejectsASecondWord() {
		assertEquals(Boolean.TRUE, WrapCommand.parseArgument("yes"));
		assertEquals(Boolean.TRUE, WrapCommand.parseArgument("1"));
		assertEquals(Boolean.FALSE, WrapCommand.parseArgument("no"));
		assertNull(WrapCommand.parseArgument("on please"));
		assertNull(WrapCommand.parseArgument("yes please"));
	}

	@Test
	public void trailingWordAfterOnDoesNotWrite() {
		assertNull(GmcpCommand.parseFeedArgument("on please"));
		assertEquals(Boolean.TRUE, GmcpCommand.parseFeedArgument("on"));
		assertEquals(Boolean.TRUE, GmcpCommand.parseFeedArgument("yes"));
		assertEquals(Boolean.FALSE, GmcpCommand.parseFeedArgument("off"));
		assertNull(GmcpCommand.parseSniffArgument("on please"));
		assertEquals(Boolean.TRUE, GmcpCommand.parseSniffArgument("1"));

		assertNull(McpCommand.parseFeedArgument("on please"));
		assertEquals(Boolean.TRUE, McpCommand.parseFeedArgument("true"));
		assertEquals(Boolean.FALSE, McpCommand.parseFeedArgument("no"));
		assertNull(McpCommand.parseSniffArgument("on please"));
		assertEquals(Boolean.FALSE, McpCommand.parseSniffArgument("off"));

		assertNull(MxpCommand.parseArgument("on please"));
		assertEquals(Boolean.TRUE, MxpCommand.parseArgument("on"));
		assertEquals(Boolean.TRUE, MxpCommand.parseArgument("yes"));
		assertEquals(Boolean.FALSE, MxpCommand.parseArgument("0"));

		assertNull(UnaccentCommand.parseArgument("on please"));
		assertEquals(Boolean.TRUE, UnaccentCommand.parseArgument("on"));
		assertEquals(Boolean.TRUE, UnaccentCommand.parseArgument("1"));
		assertEquals(Boolean.FALSE, UnaccentCommand.parseArgument("false"));
	}

	@Test
	public void bareTriggerAliasGmcpMcpIsStatus() {
		assertEquals(TriggerCommand.ROUTE_STATUS, TriggerCommand.route(""));
		assertEquals(TriggerCommand.ROUTE_HELP, TriggerCommand.route("help"));
		assertEquals(TriggerCommand.ROUTE_VERB, TriggerCommand.route("status"));
		assertEquals(TriggerCommand.ROUTE_VERB, TriggerCommand.route("list"));
		assertEquals(AliasCommand.ROUTE_STATUS, AliasCommand.route(""));
		assertEquals(AliasCommand.ROUTE_HELP, AliasCommand.route("?"));
		assertEquals(AliasCommand.ROUTE_VERB, AliasCommand.route("state"));
		assertEquals(AliasCommand.ROUTE_VERB, AliasCommand.route("list"));
		assertEquals(GmcpCommand.ROUTE_STATUS, GmcpCommand.route(""));
		assertEquals(GmcpCommand.ROUTE_HELP, GmcpCommand.route("help"));
		assertEquals(GmcpCommand.ROUTE_VERB, GmcpCommand.route("enable Char"));
		assertEquals(McpCommand.ROUTE_STATUS, McpCommand.route("   "));
		assertEquals(McpCommand.ROUTE_HELP, McpCommand.route("help"));
		assertEquals(McpCommand.ROUTE_VERB, McpCommand.route("status"));
	}
}

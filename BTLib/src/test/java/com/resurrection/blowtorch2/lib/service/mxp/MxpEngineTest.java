package com.resurrection.blowtorch2.lib.service.mxp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class MxpEngineTest {

	private static final String ESC = "\u001B";

	private static MxpEngine on() {
		MxpEngine e = new MxpEngine();
		e.setEnabled(true);
		e.setActive(true);
		e.applyMode(6); // lock secure, what Circlemud sends after handshake
		return e;
	}

	private static String utf(final MxpEngine e, final String in) {
		byte[] out = e.process(in.getBytes(StandardCharsets.UTF_8));
		return new String(out, StandardCharsets.UTF_8);
	}

	@Test
	public void disabledCopiesUnchanged() {
		MxpEngine e = new MxpEngine();
		e.setEnabled(false);
		assertEquals("<B>x</B>", utf(e, "<B>x</B>"));
	}

	@Test
	public void inactiveLeavesTagsUntilModeCsi() {
		MxpEngine e = new MxpEngine();
		e.setEnabled(true);
		e.setActive(false);
		assertEquals("<B>x</B>", utf(e, "<B>x</B>"));
		String out = utf(e, ESC + "[6z<B>x</B>");
		assertTrue(out.contains("\u001B[1m"));
		assertTrue(out.contains("x"));
		assertFalse(out.contains("<B>"));
	}

	@Test
	public void boldBecomesSgr() {
		MxpEngine e = on();
		String out = utf(e, "<B>x</B>");
		assertEquals("\u001B[1mx\u001B[22m", out);
	}

	@Test
	public void colorNamedRed() {
		MxpEngine e = on();
		String out = utf(e, "<COLOR red>x</COLOR>");
		assertTrue(out.startsWith("\u001B[31m"));
		assertTrue(out.contains("x"));
		assertTrue(out.contains("\u001B[39;49m"));
	}

	@Test
	public void entityLt() {
		MxpEngine e = on();
		assertEquals("<", utf(e, "&lt;"));
	}

	@Test
	public void malformedEntityStays() {
		MxpEngine e = on();
		assertEquals("John & Judy", utf(e, "John & Judy"));
	}

	@Test
	public void numericEntityBelow32Dropped() {
		MxpEngine e = on();
		assertEquals("ab", utf(e, "a&#7;b"));
	}

	@Test
	public void sendHrefBecomesOsc() {
		MxpEngine e = on();
		String out = utf(e, "<SEND href=\"look north\">N</SEND>");
		assertTrue(out.contains("mxp-send:"));
		assertTrue(out.contains("look north") || out.contains("look%20north"));
		assertTrue(out.contains("N"));
		assertTrue(out.contains("\u001B]8;;") || out.contains("\u001B]8;"));
	}

	@Test
	public void sendTextIsTheCommand() {
		MxpEngine e = on();
		String out = utf(e, "<SEND>buy bread</SEND>");
		assertTrue(out.contains("mxp-send:"));
		assertTrue(MxpLinks.sendCommand(extractHref(out)).equals("buy bread"));
		assertTrue(out.contains("buy bread"));
	}

	@Test
	public void sendMenuUsesPipe() {
		MxpEngine e = on();
		String out = utf(e,
				"<SEND href=\"look|get\" hint=\"click|Look|Get\">thing</SEND>");
		assertTrue(out.contains(MxpLinks.MENU));
		String href = extractHref(out);
		String[] parts = MxpLinks.menuHintsAndCommands(href);
		assertEquals("click|Look|Get", parts[0]);
		assertEquals("look|get", parts[1]);
	}

	@Test
	public void expireEmitsCommandAndNotifies() {
		MxpEngine e = on();
		MxpEngine.CollectingListener L = new MxpEngine.CollectingListener();
		e.setListener(L);
		String out = utf(e, "<EXPIRE Exits>");
		assertTrue(out.contains("mxp-expire:Exits"));
		assertEquals(1, L.expires.size());
		assertEquals("Exits", L.expires.get(0));
	}

	@Test
	public void openModeRejectsSend() {
		MxpEngine e = new MxpEngine();
		e.setEnabled(true);
		e.setActive(true);
		e.applyMode(5); // lock open
		String out = utf(e, "<SEND>north</SEND>");
		assertFalse(out.contains("mxp-send:"));
		assertTrue(out.contains("north"));
	}

	@Test
	public void tempSecureAllowsOneSend() {
		MxpEngine e = new MxpEngine();
		e.setEnabled(true);
		e.setActive(true);
		e.applyMode(5);
		String out = utf(e, ESC + "[4z<SEND href=\"n\">N</SEND>");
		assertTrue(out.contains("mxp-send:"));
		assertTrue(out.contains("N"));
	}

	@Test
	public void lockSecureSurvivesNewline() {
		MxpEngine e = on();
		String out = utf(e, "<B>a</B>\n<SEND href=\"x\">y</SEND>");
		assertTrue(out.contains("mxp-send:"));
	}

	@Test
	public void nobrEatsOneNewline() {
		MxpEngine e = on();
		assertEquals("ab", utf(e, "a<NOBR>\nb"));
	}

	@Test
	public void brIsNotAModeNewline() {
		MxpEngine e = on();
		String out = utf(e, "<SEND href=\"x\">a<BR>b</SEND>");
		assertTrue(out.contains("a\nb"));
		assertTrue(out.contains("mxp-send:"));
	}

	@Test
	public void commentIsStripped() {
		MxpEngine e = on();
		assertEquals("ac", utf(e, "a<!-- hide -->c"));
	}

	@Test
	public void versionRepliesToMud() {
		MxpEngine e = on();
		MxpEngine.CollectingListener L = new MxpEngine.CollectingListener();
		e.setListener(L);
		e.setClient("BlowTorch", "2.3.0-test");
		utf(e, "<VERSION>");
		assertEquals(1, L.mudReplies.size());
		assertTrue(L.mudReplies.get(0).contains("CLIENT=BlowTorch"));
		assertTrue(L.mudReplies.get(0).contains("VERSION=2.3.0-test"));
		assertTrue(L.mudReplies.get(0).contains("MXP=1.0"));
		assertTrue(L.mudReplies.get(0).contains("\u001B[1z"));
	}

	@Test
	public void supportRepliesPlusSend() {
		MxpEngine e = on();
		MxpEngine.CollectingListener L = new MxpEngine.CollectingListener();
		e.setListener(L);
		utf(e, "<SUPPORT send image>");
		assertEquals(1, L.mudReplies.size());
		assertTrue(L.mudReplies.get(0).contains("+send"));
		assertTrue(L.mudReplies.get(0).contains("-image"));
	}

	@Test
	public void customElementExpandsSend() {
		MxpEngine e = on();
		utf(e, "<!ELEMENT Item '<SEND href=\"buy &text;\">'><Item>bread</Item>");
		// Second process is the same stream:
		MxpEngine e2 = on();
		String out = utf(e2, "<!EL Item '<SEND href=\"buy &text;\">'><Item>bread</Item>");
		assertTrue(out.contains("mxp-send:"));
		assertEquals("buy bread", MxpLinks.sendCommand(extractHref(out)));
		assertTrue(out.contains("bread"));
		assertFalse(out.contains("<Item>"));
	}

	@Test
	public void varSetsEntityAndShowsValue() {
		MxpEngine e = on();
		MxpEngine.CollectingListener L = new MxpEngine.CollectingListener();
		e.setListener(L);
		String out = utf(e, "Hp: <VAR Hp>100</VAR>");
		assertEquals("Hp: 100", out);
		assertEquals("100", L.variables.get("Hp"));
	}

	@Test
	public void entityDefineThenExpand() {
		MxpEngine e = on();
		String out = utf(e, "<!ENTITY Version \"6.15\">ver &Version;");
		assertEquals("ver 6.15", out);
	}

	@Test
	public void unquotedNumericEntityDoesNotLeak() {
		MxpEngine e = on();
		MxpEngine.CollectingListener L = new MxpEngine.CollectingListener();
		e.setListener(L);
		String out = utf(e,
				"<!EN hp 501 publish><!EN xp 556 publish><!EN gp 50 publish>"
				+ "<!EN maxhp 501 publish><!EN maxgp 50 publish>> You open");
		assertFalse("unquoted ENTITY value must not dump the tag",
				out.contains("<!EN"));
		assertTrue(out.contains("> You open"));
		assertEquals("501", L.variables.get("hp"));
		assertEquals("556", L.variables.get("xp"));
		assertEquals("50", L.variables.get("gp"));
		assertEquals("501", L.variables.get("maxhp"));
		assertEquals("50", L.variables.get("maxgp"));
	}

	@Test
	public void holdoverAcrossPackets() {
		MxpEngine e = on();
		byte[] a = e.process("<SE".getBytes(StandardCharsets.UTF_8));
		assertEquals(0, a.length);
		String out = utf(e, "ND href=\"n\">N</SEND>");
		assertTrue(out.contains("mxp-send:"));
		assertTrue(out.contains("N"));
	}

	@Test
	public void destDoesNotPolluteMain() {
		MxpEngine e = on();
		MxpEngine.CollectingListener L = new MxpEngine.CollectingListener();
		e.setListener(L);
		String out = utf(e, "main<DEST Status>vitals</DEST>after");
		assertEquals("mainafter", out);
		assertTrue(L.dests.containsKey("Status"));
		assertEquals("vitals", new String(L.dests.get("Status").toByteArray(),
				StandardCharsets.UTF_8));
	}

	@Test
	public void flagSetStoresVariable() {
		MxpEngine e = on();
		MxpEngine.CollectingListener L = new MxpEngine.CollectingListener();
		e.setListener(L);
		utf(e, "<!ELEMENT Hp FLAG=\"Set hp\"><Hp>42</Hp>");
		assertEquals("42", L.variables.get("hp"));
	}

	@Test
	public void templeExampleStripsMarkup() {
		MxpEngine e = on();
		String src = "<!EL Ex '<SEND>'><RName>The Main Temple</RName>"
				+ "<Ex>N</Ex>";
		// RName is unknown until defined; unknown secure tags drop markup keep text
		String out = utf(e, src);
		assertTrue(out.contains("The Main Temple"));
		assertTrue(out.contains("N"));
		assertTrue(out.contains("mxp-send:"));
	}

	@Test
	public void greaterThanInsideQuotedHref() {
		MxpEngine e = on();
		String out = utf(e, "<SEND href=\"look > north\">x</SEND>");
		assertEquals("look > north", MxpLinks.sendCommand(extractHref(out)));
		assertTrue(out.contains("x"));
	}

	@Test
	public void lockedLeavesMarkupAlone() {
		MxpEngine e = new MxpEngine();
		e.setEnabled(true);
		e.setActive(true);
		e.applyMode(7);
		assertEquals("<B>x</B>", utf(e, "<B>x</B>"));
	}

	@Test
	public void scriptBodyIsDropped() {
		MxpEngine e = on();
		String out = utf(e, "a<SCRIPT>evil()</SCRIPT>b");
		assertEquals("ab", out);
		assertFalse(out.contains("evil"));
	}

	@Test
	public void promptSendUsesPromptScheme() {
		MxpEngine e = on();
		String out = utf(e, "<SEND href=\"say hi\" prompt>hi</SEND>");
		assertTrue(out.contains(MxpLinks.PROMPT));
		assertEquals("say hi", MxpLinks.promptCommand(extractHref(out)));
	}

	@Test
	public void emptyExpireNotifiesBlankGroup() {
		MxpEngine e = on();
		MxpEngine.CollectingListener L = new MxpEngine.CollectingListener();
		e.setListener(L);
		utf(e, "<EXPIRE>");
		assertEquals(1, L.expires.size());
		assertEquals("", L.expires.get(0));
	}

	@Test
	public void headingIsBold() {
		MxpEngine e = on();
		String out = utf(e, "<H1>Title</H1>");
		assertTrue(out.contains("\u001B[1m"));
		assertTrue(out.contains("Title"));
		assertFalse(out.contains("<H1>"));
	}

	@Test
	public void unknownTagStaysAsText() {
		MxpEngine e = on();
		assertEquals("<100hp>", utf(e, "<100hp>"));
		assertEquals("HP < 50\n", utf(e, "HP < 50\n"));
	}

	@Test
	public void kavirSecureThenLockedKeepsColorOpen() {
		MxpEngine e = on();
		String out = utf(e, ESC + "[1z<COLOR red>" + ESC + "[7zX"
				+ ESC + "[1z</COLOR>" + ESC + "[7z");
		assertTrue(out.contains("\u001B[31m"));
		int color = out.indexOf("\u001B[31m");
		int text = out.indexOf('X');
		int reset = out.indexOf("\u001B[39;49m");
		assertTrue(color >= 0 && text > color);
		assertTrue("reset must not land before the text", reset < 0 || reset > text);
	}

	@Test
	public void elementHrefExpandsNumericEntities() {
		MxpEngine e = on();
		String out = utf(e,
				"<!EL Get '<SEND href=\"get &#39;&text;&#39;\">'><Get>arm</Get>");
		assertEquals("get 'arm'", MxpLinks.sendCommand(extractHref(out)));
	}

	@Test
	public void soundInOpenModeIsPlayedNotPrinted() {
		MxpEngine e = new MxpEngine();
		e.setEnabled(true);
		e.setActive(true);
		e.applyMode(0);
		MxpEngine.CollectingListener L = new MxpEngine.CollectingListener();
		e.setListener(L);
		String out = utf(e, "<SOUND hit.wav V=80 U=\"https://ex.com/s/\">");
		assertFalse(out.contains("<SOUND"));
		assertEquals(1, L.sounds.size());
		assertEquals("hit.wav", L.sounds.get(0).fname);
		assertEquals(80, L.sounds.get(0).volume);
		assertEquals("sound", L.sounds.get(0).mediaType);
		assertEquals("https://ex.com/s/", L.sounds.get(0).url);
		assertFalse(L.sounds.get(0).continueMusic);
	}

	@Test
	public void sendInOpenModeIsStillIgnored() {
		MxpEngine e = new MxpEngine();
		e.setEnabled(true);
		e.setActive(true);
		e.applyMode(0);
		String out = utf(e, "<SEND href=\"look\">north</SEND>");
		assertTrue(out.contains("<SEND") || out.contains("north"));
		assertFalse(out.contains("mxp-send:"));
	}

	@Test
	public void musicContinueIsPositionalCNotPriority() {
		MxpEngine e = on();
		MxpEngine.CollectingListener L = new MxpEngine.CollectingListener();
		e.setListener(L);
		utf(e, "<MUSIC town.ogg 40 -1 1>");
		assertEquals(1, L.sounds.size());
		assertEquals("town.ogg", L.sounds.get(0).fname);
		assertEquals(40, L.sounds.get(0).volume);
		assertEquals(-1, L.sounds.get(0).loops);
		assertEquals(50, L.sounds.get(0).priority);
		assertTrue(L.sounds.get(0).continueMusic);
		assertEquals("music", L.sounds.get(0).mediaType);
	}

	@Test
	public void soundOffIsAStopRequest() {
		MxpEngine e = on();
		MxpEngine.CollectingListener L = new MxpEngine.CollectingListener();
		e.setListener(L);
		utf(e, "<SOUND Off>");
		assertEquals(1, L.sounds.size());
		assertTrue(MxpSound.isStop(L.sounds.get(0).fname));
	}

	@Test
	public void supportListsSoundAndMusicNotImage() {
		MxpEngine e = on();
		MxpEngine.CollectingListener L = new MxpEngine.CollectingListener();
		e.setListener(L);
		utf(e, "<SUPPORT>");
		assertTrue(L.mudReplies.get(0).contains("+sound"));
		assertTrue(L.mudReplies.get(0).contains("+music"));
		assertFalse(L.mudReplies.get(0).contains("+image"));
		assertFalse(L.mudReplies.get(0).contains("+gauge"));
	}

	@Test
	public void soundTypeGroupIsNotTheUrl() {
		MxpEngine e = on();
		MxpEngine.CollectingListener L = new MxpEngine.CollectingListener();
		e.setListener(L);
		utf(e, "<SOUND thunder.wav T=weather U=\"https://ex.com/s/\">");
		assertEquals(1, L.sounds.size());
		assertEquals("weather", L.sounds.get(0).group);
		assertEquals("https://ex.com/s/", L.sounds.get(0).url);
		assertEquals("thunder.wav", L.sounds.get(0).fname);
	}

	@Test
	public void musicCZeroMeansDoNotContinue() {
		MxpEngine e = on();
		MxpEngine.CollectingListener L = new MxpEngine.CollectingListener();
		e.setListener(L);
		utf(e, "<MUSIC town.ogg C=0>");
		assertEquals(1, L.sounds.size());
		assertFalse(L.sounds.get(0).continueMusic);
		assertEquals("music", L.sounds.get(0).mediaType);
	}

	@Test
	public void supportDottedQueryIsAnswered() {
		MxpEngine e = on();
		MxpEngine.CollectingListener L = new MxpEngine.CollectingListener();
		e.setListener(L);
		utf(e, "<SUPPORT send.expire image>");
		assertEquals(1, L.mudReplies.size());
		assertTrue(L.mudReplies.get(0).contains("+send.expire"));
		assertTrue(L.mudReplies.get(0).contains("-image"));
	}

	private static String extractHref(final String oscStream) {
		int start = oscStream.indexOf("mxp-");
		assertTrue("no mxp href in " + oscStream, start >= 0);
		int bel = oscStream.indexOf('\u0007', start);
		assertTrue(bel > start);
		return oscStream.substring(start, bel);
	}
}

package com.resurrection.blowtorch2.lib.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChatAnnounceTest {

	@Test
	public void coerceModeAcceptsIntegerAndStringElseOff() {
		assertEquals(ChatAnnounce.MODE_OFF, ChatAnnounce.coerceMode(null));
		assertEquals(ChatAnnounce.MODE_OFF, ChatAnnounce.coerceMode(0));
		assertEquals(ChatAnnounce.MODE_EVERY, ChatAnnounce.coerceMode(1));
		assertEquals(ChatAnnounce.MODE_DIGEST, ChatAnnounce.coerceMode(2));
		assertEquals(ChatAnnounce.MODE_OFF, ChatAnnounce.coerceMode(3));
		assertEquals(ChatAnnounce.MODE_EVERY, ChatAnnounce.coerceMode("1"));
		assertEquals(ChatAnnounce.MODE_DIGEST, ChatAnnounce.coerceMode("2"));
		assertEquals(ChatAnnounce.MODE_OFF, ChatAnnounce.coerceMode("nope"));
	}

	@Test
	public void coerceSecondsClampsAndDefaults() {
		assertEquals(60, ChatAnnounce.coerceSeconds(null));
		assertEquals(60, ChatAnnounce.coerceSeconds("60"));
		assertEquals(5, ChatAnnounce.coerceSeconds(1));
		assertEquals(3600, ChatAnnounce.coerceSeconds(99999));
		assertEquals(60, ChatAnnounce.coerceSeconds("x"));
	}

	@Test
	public void lineTextUsesChatWhenTitleBlank() {
		assertEquals("Thread VERMIN has new messages: 5",
				ChatAnnounce.lineText("VERMIN", 5));
		assertEquals("Thread Chat has new messages: 1",
				ChatAnnounce.lineText("", 1));
		assertEquals("Thread Chat has new messages: 2",
				ChatAnnounce.lineText("   ", 2));
		assertEquals("Thread Chat has new messages: 3",
				ChatAnnounce.lineText(null, 3));
		String framed = ChatAnnounce.windowLine("VERMIN", 1);
		assertTrue(framed.startsWith("\n"));
		assertTrue(framed.endsWith("\n"));
		assertFalse(framed.contains("1CORP"));
		assertEquals("\nThread VERMIN has new messages: 1\n", framed);
	}

	@Test
	public void lineOffNeverAnnouncesOnAppend() {
		assertFalse(ChatAnnounce.shouldAnnounceLine(
				ChatAnnounce.MODE_OFF, true));
	}

	@Test
	public void lineEveryAnnouncesEachCounted() {
		assertTrue(ChatAnnounce.shouldAnnounceLine(
				ChatAnnounce.MODE_EVERY, true));
	}

	@Test
	public void digestAppendDoesNotLine() {
		assertFalse(ChatAnnounce.shouldAnnounceLine(
				ChatAnnounce.MODE_DIGEST, true));
	}

	@Test
	public void digestTimerPublishesUnreadBatch() {
		assertTrue(ChatAnnounce.shouldPublishDigestLine(
				ChatAnnounce.MODE_DIGEST, 5));
		assertFalse(ChatAnnounce.shouldPublishDigestLine(
				ChatAnnounce.MODE_DIGEST, 0));
		assertFalse(ChatAnnounce.shouldPublishDigestLine(
				ChatAnnounce.MODE_EVERY, 5));
		assertFalse(ChatAnnounce.shouldPublishDigestLine(
				ChatAnnounce.MODE_OFF, 5));
	}

	@Test
	public void notifyRefreshesEveryCounted() {
		assertTrue(ChatAnnounce.shouldRefreshNotify(true, true));
		assertFalse(ChatAnnounce.shouldRefreshNotify(false, true));
		assertFalse(ChatAnnounce.shouldRefreshNotify(true, false));
	}

	@Test
	public void notifyAlertIsEveryOrFirstOfWindow() {
		assertTrue(ChatAnnounce.shouldAlertNotify(
				true, ChatAnnounce.MODE_EVERY, true, false));
		assertTrue(ChatAnnounce.shouldAlertNotify(
				true, ChatAnnounce.MODE_DIGEST, true, true));
		assertFalse(ChatAnnounce.shouldAlertNotify(
				true, ChatAnnounce.MODE_DIGEST, true, false));
		assertTrue(ChatAnnounce.shouldAlertNotify(
				true, ChatAnnounce.MODE_OFF, true, true));
		assertFalse(ChatAnnounce.shouldAlertNotify(
				true, ChatAnnounce.MODE_OFF, true, false));
		assertFalse(ChatAnnounce.shouldAlertNotify(
				false, ChatAnnounce.MODE_EVERY, true, true));
	}

	@Test
	public void digestTimerStartsOnFirstAppend() {
		assertTrue(ChatAnnounce.shouldStartDigestTimer(
				ChatAnnounce.MODE_DIGEST, false, true, true));
		assertFalse(ChatAnnounce.shouldStartDigestTimer(
				ChatAnnounce.MODE_DIGEST, false, true, false));
		assertTrue(ChatAnnounce.shouldStartDigestTimer(
				ChatAnnounce.MODE_OFF, true, true, true));
		assertFalse(ChatAnnounce.shouldStartDigestTimer(
				ChatAnnounce.MODE_OFF, false, true, true));
		assertFalse(ChatAnnounce.shouldStartDigestTimer(
				ChatAnnounce.MODE_EVERY, true, true, true));
	}

	@Test
	public void mineOrNotCountedNeverAnnouncesOrNotifies() {
		assertFalse(ChatAnnounce.shouldAnnounceLine(
				ChatAnnounce.MODE_EVERY, false));
		assertFalse(ChatAnnounce.shouldAnnounceLine(
				ChatAnnounce.MODE_DIGEST, false));
		assertFalse(ChatAnnounce.shouldRefreshNotify(true, false));
		assertFalse(ChatAnnounce.shouldAlertNotify(
				true, ChatAnnounce.MODE_EVERY, false, true));
		assertFalse(ChatAnnounce.shouldStartDigestTimer(
				ChatAnnounce.MODE_DIGEST, true, false, true));
		assertFalse(ChatAnnounce.shouldPublishDigestLine(
				ChatAnnounce.MODE_DIGEST, 0));
	}
}

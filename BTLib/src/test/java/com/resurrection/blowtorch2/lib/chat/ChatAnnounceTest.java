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
	}

	@Test
	public void lineOffNeverAnnounces() {
		assertFalse(ChatAnnounce.shouldAnnounceLine(
				ChatAnnounce.MODE_OFF, true, 10_000L, 0L, 60));
	}

	@Test
	public void lineEveryAnnouncesEachCounted() {
		assertTrue(ChatAnnounce.shouldAnnounceLine(
				ChatAnnounce.MODE_EVERY, true, 10_000L, 9_000L, 60));
	}

	@Test
	public void digestFirstTimeAnnounces() {
		assertTrue(ChatAnnounce.shouldAnnounceLine(
				ChatAnnounce.MODE_DIGEST, true, 1_000L, 0L, 60));
	}

	@Test
	public void digestTooSoonDoesNot() {
		assertFalse(ChatAnnounce.shouldAnnounceLine(
				ChatAnnounce.MODE_DIGEST, true, 30_000L, 1_000L, 60));
	}

	@Test
	public void digestAfterIntervalDoes() {
		assertTrue(ChatAnnounce.shouldAnnounceLine(
				ChatAnnounce.MODE_DIGEST, true, 61_000L, 1_000L, 60));
	}

	@Test
	public void notifyWithLineOffStillUsesDigest() {
		assertFalse(ChatAnnounce.shouldAnnounceLine(
				ChatAnnounce.MODE_OFF, true, 1_000L, 0L, 60));
		assertTrue(ChatAnnounce.shouldNotify(
				true, ChatAnnounce.MODE_OFF, true, 1_000L, 0L, 60));
		assertFalse(ChatAnnounce.shouldNotify(
				true, ChatAnnounce.MODE_OFF, true, 30_000L, 1_000L, 60));
		assertTrue(ChatAnnounce.shouldNotify(
				true, ChatAnnounce.MODE_OFF, true, 61_000L, 1_000L, 60));
		assertFalse(ChatAnnounce.shouldNotify(
				false, ChatAnnounce.MODE_OFF, true, 1_000L, 0L, 60));
	}

	@Test
	public void mineOrNotCountedNeverAnnouncesOrNotifies() {
		assertFalse(ChatAnnounce.shouldAnnounceLine(
				ChatAnnounce.MODE_EVERY, false, 1_000L, 0L, 60));
		assertFalse(ChatAnnounce.shouldAnnounceLine(
				ChatAnnounce.MODE_DIGEST, false, 1_000L, 0L, 60));
		assertFalse(ChatAnnounce.shouldNotify(
				true, ChatAnnounce.MODE_EVERY, false, 1_000L, 0L, 60));
		assertFalse(ChatAnnounce.shouldNotify(
				true, ChatAnnounce.MODE_OFF, false, 1_000L, 0L, 60));
	}
}

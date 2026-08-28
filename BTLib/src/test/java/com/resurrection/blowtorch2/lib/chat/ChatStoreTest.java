package com.resurrection.blowtorch2.lib.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/**
 * Inbox append/list/search/cap without Android {@code Context}.
 *
 * <p>{@link ChatInbox} is the JSON + cap helper {@link ChatStore} persists
 * through {@code AtomicFiles}. These tests call that helper (and the
 * package-visible in-memory {@link ChatStore}) so they run on the JVM.
 */
public class ChatStoreTest {

	@Test
	public void appendListsAThreadWithLastBodyAndUnread() {
		ChatStore store = new ChatStore(new ChatInbox());
		store.append("vermin", "VERMIN", "[ VERMIN ] : Taracair says, \"hi\"");
		List<ChatThreadSummary> threads = store.listThreads();
		assertEquals(1, threads.size());
		ChatThreadSummary t = threads.get(0);
		assertEquals("vermin", t.getThreadId());
		assertEquals("VERMIN", t.getTitle());
		assertEquals("[ VERMIN ] : Taracair says, \"hi\"", t.getLastBody());
		assertEquals(1, t.getUnreadCount());
		assertTrue(t.getLastWhenMs() > 0L);
	}

	@Test
	public void secondAppendSameThreadUpdatesLastAndUnread() {
		ChatStore store = new ChatStore(new ChatInbox());
		store.appendAt("vermin", "VERMIN", "first", 1000L);
		store.appendAt("vermin", "VERMIN", "second", 2000L);
		List<ChatThreadSummary> threads = store.listThreads();
		assertEquals(1, threads.size());
		assertEquals("second", threads.get(0).getLastBody());
		assertEquals(2000L, threads.get(0).getLastWhenMs());
		assertEquals(2, threads.get(0).getUnreadCount());
		assertEquals(2, store.unreadCount("vermin"));
		assertEquals("VERMIN", store.threadTitle("vermin"));
		List<ChatMessage> msgs = store.messages("vermin", 10);
		assertEquals(2, msgs.size());
		assertEquals("first", msgs.get(0).getBody());
		assertEquals("second", msgs.get(1).getBody());
	}

	@Test
	public void listThreadsOrdersByMostRecent() {
		ChatStore store = new ChatStore(new ChatInbox());
		store.appendAt("old", "Old", "a", 1000L);
		store.appendAt("new", "New", "b", 3000L);
		store.appendAt("old", "Old", "c", 2000L);
		List<ChatThreadSummary> threads = store.listThreads();
		assertEquals(2, threads.size());
		assertEquals("new", threads.get(0).getThreadId());
		assertEquals("old", threads.get(1).getThreadId());
		assertEquals("c", threads.get(1).getLastBody());
	}

	@Test
	public void messagesLimitReturnsTheNewestInChronologicalOrder() {
		ChatStore store = new ChatStore(new ChatInbox());
		store.appendAt("t", "T", "one", 1L);
		store.appendAt("t", "T", "two", 2L);
		store.appendAt("t", "T", "three", 3L);
		List<ChatMessage> lastTwo = store.messages("t", 2);
		assertEquals(2, lastTwo.size());
		assertEquals("two", lastTwo.get(0).getBody());
		assertEquals("three", lastTwo.get(1).getBody());
	}

	@Test
	public void searchFindsBodyAndRespectsTimeBounds() {
		ChatStore store = new ChatStore(new ChatInbox());
		store.appendAt("vermin", "VERMIN", "rat gossip", 1000L);
		store.appendAt("tells", "Tells", "Bob tells you 'hi'", 2000L);
		store.appendAt("vermin", "VERMIN", "more rats", 3000L);

		List<ChatMessage> rats = store.search("rat", null, null);
		assertEquals(2, rats.size());
		assertEquals("rat gossip", rats.get(0).getBody());
		assertEquals("more rats", rats.get(1).getBody());

		List<ChatMessage> bob = store.search("bob", null, null);
		assertEquals(1, bob.size());
		assertEquals("tells", bob.get(0).getThreadId());

		List<ChatMessage> window = store.search(null, Long.valueOf(1500L), Long.valueOf(2500L));
		assertEquals(1, window.size());
		assertEquals("Bob tells you 'hi'", window.get(0).getBody());

		List<ChatMessage> untilExclusive = store.search("rat", null, Long.valueOf(3000L));
		assertEquals(1, untilExclusive.size());
		assertEquals("rat gossip", untilExclusive.get(0).getBody());
	}

	@Test
	public void capDropsOldestMessagesAcrossTheWorld() {
		ChatInbox inbox = new ChatInbox();
		for (int i = 0; i < ChatInbox.MAX_MESSAGES + 5; i++) {
			inbox.append("t", "T", "msg-" + i, i);
		}
		assertEquals(ChatInbox.MAX_MESSAGES, inbox.messageCount());
		List<ChatMessage> kept = inbox.messages("t", ChatInbox.MAX_MESSAGES);
		assertEquals("msg-5", kept.get(0).getBody());
		assertEquals("msg-" + (ChatInbox.MAX_MESSAGES + 4),
				kept.get(kept.size() - 1).getBody());
	}

	@Test
	public void chatStoreAppendCapsAtMax() {
		ChatStore store = new ChatStore(new ChatInbox());
		for (int i = 0; i < ChatInbox.MAX_MESSAGES + 1; i++) {
			store.appendAt("t", "T", "m" + i, i);
		}
		assertEquals(ChatInbox.MAX_MESSAGES, store.messages("t", Integer.MAX_VALUE).size());
		assertEquals("m1", store.messages("t", Integer.MAX_VALUE).get(0).getBody());
	}

	@Test
	public void setMaxMessagesPrunesAndZeroMeansHardCeiling() {
		ChatStore store = new ChatStore(new ChatInbox());
		for (int i = 0; i < 20; i++) {
			store.appendAt("t", "T", "m" + i, i);
		}
		store.setMaxMessages(5);
		assertEquals(5, store.messages("t", Integer.MAX_VALUE).size());
		assertEquals("m15", store.messages("t", Integer.MAX_VALUE).get(0).getBody());
		assertEquals(ChatInbox.HARD_MAX_MESSAGES,
				ChatStore.coerceMaxMessages(Integer.valueOf(0)));
		assertEquals(ChatInbox.HARD_MAX_MESSAGES,
				ChatStore.coerceMaxMessages("0"));
		assertEquals(4000, ChatStore.coerceMaxMessages(null));
		assertEquals(100, ChatStore.coerceMaxMessages("100"));
	}

	@Test
	public void jsonLoadDoesNotTruncateAtDefaultFourThousand() {
		ChatInbox inbox = new ChatInbox();
		inbox.setMaxMessages(0);
		for (int i = 0; i < 4005; i++) {
			inbox.append("t", "T", "m" + i, i);
		}
		assertEquals(4005, inbox.messageCount());
		ChatInbox loaded = ChatInbox.fromJsonBytes(inbox.toJsonBytes());
		assertEquals(4005, loaded.messageCount());
		assertEquals(ChatInbox.HARD_MAX_MESSAGES, loaded.maxMessages());
	}

	@Test
	public void jsonRoundTripPreservesMaxMessages() {
		ChatInbox inbox = new ChatInbox();
		inbox.setMaxMessages(8000);
		inbox.append("t", "T", "hello", 1L);
		ChatInbox loaded = ChatInbox.fromJsonBytes(inbox.toJsonBytes());
		assertEquals(8000, loaded.maxMessages());
		assertEquals(1, loaded.messageCount());
	}

	@Test
	public void legacyJsonWithoutMaxKeyDoesNotTruncateAtFourThousand() {
		String json = "{\"threads\":[],\"messages\":[";
		StringBuilder sb = new StringBuilder(json);
		for (int i = 0; i < 5; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append("{\"threadId\":\"t\",\"title\":\"T\",\"body\":\"m")
					.append(i).append("\",\"whenMs\":").append(i)
					.append(",\"mine\":false}");
		}
		sb.append("]}");
		ChatInbox loaded = ChatInbox.fromJsonBytes(sb.toString().getBytes());
		assertEquals(5, loaded.messageCount());
		assertEquals(ChatInbox.HARD_MAX_MESSAGES, loaded.maxMessages());
	}

	@Test
	public void uiProcessSendDoesNotReapplyDefaultFourThousandCap() {
		ChatInbox inbox = new ChatInbox();
		inbox.setMaxMessages(0);
		for (int i = 0; i < 4005; i++) {
			inbox.append("t", "T", "m" + i, i);
		}
		ChatStore store = new ChatStore(inbox);
		store.appendOutgoing("t", "You", "reply");
		assertEquals(4006, store.messages("t", Integer.MAX_VALUE).size());
	}

	@Test
	public void setMaxMessagesWritesCapEvenWhenNothingIsPruned() {
		ChatInbox inbox = new ChatInbox();
		inbox.append("t", "T", "only-one", 1L);
		assertEquals(ChatInbox.DEFAULT_MAX_MESSAGES, inbox.maxMessages());
		ChatStore store = new ChatStore(inbox);
		store.setMaxMessages(0);
		assertEquals(ChatInbox.HARD_MAX_MESSAGES, inbox.maxMessages());
		ChatInbox loaded = ChatInbox.fromJsonBytes(inbox.toJsonBytes());
		assertEquals(ChatInbox.HARD_MAX_MESSAGES, loaded.maxMessages());
		assertEquals(1, loaded.messageCount());
	}

	@Test
	public void jsonRoundTripPreservesMessagesAndReplyTemplate() {
		ChatInbox inbox = new ChatInbox();
		inbox.append("vermin", "VERMIN", "line with \"quotes\" and \nnewline", 42L);
		inbox.setReplyTemplate("vermin", "c $text");
		byte[] json = inbox.toJsonBytes();
		ChatInbox loaded = ChatInbox.fromJsonBytes(json);
		List<ChatMessage> msgs = loaded.messages("vermin", 10);
		assertEquals(1, msgs.size());
		assertEquals("line with \"quotes\" and \nnewline", msgs.get(0).getBody());
		assertEquals(42L, msgs.get(0).getWhenMs());
		assertEquals("c $text", loaded.replyTemplate("vermin"));
		assertEquals("VERMIN", loaded.listThreads().get(0).getTitle());
	}

	@Test
	public void corruptJsonLoadsEmpty() {
		ChatInbox loaded = ChatInbox.fromJsonBytes("not json".getBytes());
		assertEquals(0, loaded.messageCount());
		assertTrue(loaded.listThreads().isEmpty());
	}

	@Test
	public void replyTemplateDefaultsEmptyAndSurvivesFirstSet() {
		ChatStore store = new ChatStore(new ChatInbox());
		assertEquals("", store.replyTemplate("vermin"));
		store.setReplyTemplate("vermin", "c $text");
		assertEquals("c $text", store.replyTemplate("vermin"));
		store.append("vermin", "VERMIN", "hello");
		assertEquals("c $text", store.replyTemplate("vermin"));
	}

	@Test
	public void fileNameSanitizesLikeWorldLaunch() {
		assertEquals("MyWorld.chat.json", ChatStore.fileNameForDisplay("My World!"));
		assertEquals("Aardwolf.chat.json", ChatStore.fileNameForDisplay("Aardwolf"));
		assertEquals(".chat.json", ChatStore.fileNameForDisplay(""));
		assertEquals(".chat.json", ChatStore.fileNameForDisplay(null));
	}

	@Test
	public void emptyTitleOnLaterAppendKeepsExistingTitle() {
		ChatStore store = new ChatStore(new ChatInbox());
		store.appendAt("vermin", "VERMIN", "one", 1L);
		store.appendAt("vermin", "", "two", 2L);
		assertEquals("VERMIN", store.listThreads().get(0).getTitle());
	}

	@Test
	public void markSeenZerosUnreadThenNextAppendCountsOne() {
		ChatStore store = new ChatStore(new ChatInbox());
		store.append("vermin", "VERMIN", "hi");
		assertEquals(1, store.listThreads().get(0).getUnreadCount());
		store.markSeen("vermin");
		assertEquals(0, store.listThreads().get(0).getUnreadCount());
		store.append("vermin", "VERMIN", "again");
		assertEquals(1, store.listThreads().get(0).getUnreadCount());
	}

	@Test
	public void seedTemplateOnlyIfEmptyAndSameLockAsAppend() {
		ChatStore store = new ChatStore(new ChatInbox());
		store.append("vermin", "VERMIN", "one", "c $text");
		assertEquals("c $text", store.replyTemplate("vermin"));
		store.append("vermin", "VERMIN", "two", "c other");
		assertEquals("c $text", store.replyTemplate("vermin"));
	}

	@Test
	public void capDropsGhostThreadsWithNoRemainingMessages() {
		ChatInbox inbox = new ChatInbox();
		for (int i = 0; i < ChatInbox.MAX_MESSAGES + 5; i++) {
			inbox.append("t" + i, "T" + i, "msg-" + i, i);
		}
		assertEquals(ChatInbox.MAX_MESSAGES, inbox.messageCount());
		List<ChatThreadSummary> threads = inbox.listThreads();
		assertEquals(ChatInbox.MAX_MESSAGES, threads.size());
		for (int i = 0; i < threads.size(); i++) {
			assertFalse("ghost t0 should be gone", "t0".equals(threads.get(i).getThreadId()));
		}
		assertEquals("t" + (ChatInbox.MAX_MESSAGES + 4), threads.get(0).getThreadId());
	}

	@Test
	public void mineNeedleMarksIncomingAndDoesNotBadge() {
		ChatStore store = new ChatStore(new ChatInbox());
		store.setMineNeedle("vermin", "Taracair");
		store.appendAt("vermin", "VERMIN", "[ VERMIN ]: Elyak waves.", 1L);
		store.appendAt("vermin", "VERMIN",
				"[ VERMIN ]: Taracair says, \"Test\"", 2L);
		List<ChatMessage> msgs = store.messages("vermin", 10);
		assertFalse(msgs.get(0).isMine());
		assertTrue(msgs.get(1).isMine());
		assertEquals(1, store.listThreads().get(0).getUnreadCount());
	}

	@Test
	public void mineTriggerMatchesSpeakerNotMention() {
		ChatInbox inbox = new ChatInbox();
		inbox.setMineNeedle("vermin", "Taracair");
		assertTrue(inbox.bodyLooksMine("vermin",
				"[ VERMIN ]: Taracair says, \"Test\""));
		assertTrue(inbox.bodyLooksMine("vermin",
				"[ VERMIN ] : Taracair says, \"Test\""));
		assertTrue(inbox.bodyLooksMine("vermin",
				"[ VERMIN ]: Taracair, the bard, says, \"Test\""));
		assertFalse(inbox.bodyLooksMine("vermin",
				"[ VERMIN ]: Elyak says, \"hi Taracair\""));
		assertFalse(inbox.bodyLooksMine("vermin", "[ VERMIN ]: Elyak waves."));
		assertTrue(inbox.bodyLooksMine("vermin", "Taracair tells you 'yo'"));
		assertFalse(inbox.bodyLooksMine("vermin", "Bob tells you 'hi Taracair'"));
		assertFalse(inbox.bodyLooksMine("vermin", "hi Taracair"));
		inbox.setMineNeedle("vermin", "]: Taracair");
		assertTrue(inbox.bodyLooksMine("vermin",
				"[ VERMIN ]: Taracair says, \"Test\""));
		assertFalse(inbox.bodyLooksMine("vermin",
				"[ VERMIN ]: Elyak says, \"hi Taracair\""));
		inbox.setMineNeedle("vermin", "You say");
		assertTrue(inbox.bodyLooksMine("vermin", "You say, \"hello\""));
		assertFalse(inbox.bodyLooksMine("vermin",
				"[ VERMIN ]: Taracair says, \"hello\""));
		inbox.setMineNeedle("vermin", "]: Taracair|You say");
		assertTrue(inbox.bodyLooksMine("vermin",
				"[ VERMIN ]: Taracair says, \"Test\""));
		assertTrue(inbox.bodyLooksMine("vermin", "You say, \"hello\""));
		assertFalse(inbox.bodyLooksMine("vermin",
				"[ VERMIN ]: Elyak says, \"hi Taracair\""));
	}

	@Test
	public void displayMineUsesPatternOrStoredFlag() {
		ChatInbox inbox = new ChatInbox();
		inbox.setMineNeedle("vermin", "Taracair");
		inbox.append("vermin", "VERMIN",
				"[ VERMIN ]: Elyak says, \"hi Taracair\"", 1L, false, false);
		assertFalse(inbox.displayMine(inbox.messages("vermin", 1).get(0)));
		inbox.append("vermin", "You", "hi Taracair", 2L, true, false);
		assertTrue(inbox.displayMine(inbox.messages("vermin", 2).get(1)));
		inbox.append("vermin", "VERMIN", "You say, \"hello\"", 3L, true, false);
		assertTrue(inbox.displayMine(inbox.messages("vermin", 3).get(2)));
	}

	@Test
	public void absorbedSendStaysMineWhenPatternIsYouSay() {
		ChatInbox inbox = new ChatInbox();
		inbox.setMineNeedle("vermin", "You say");
		inbox.append("vermin", "VERMIN",
				"[ VERMIN ]: Taracair says, \"Test\"", 1L, true, false);
		assertTrue(inbox.displayMine(inbox.messages("vermin", 1).get(0)));
	}

	@Test
	public void threadMessagesFilterSearchAndDates() {
		ChatInbox inbox = new ChatInbox();
		inbox.append("vermin", "VERMIN", "alpha", 1000L);
		inbox.append("vermin", "VERMIN", "bravo goblin", 2000L);
		inbox.append("tells", "Tells", "goblin", 2000L);
		inbox.append("vermin", "VERMIN", "charlie", 3000L);
		List<ChatMessage> hits = inbox.messages("vermin", 10, "goblin", null, null);
		assertEquals(1, hits.size());
		assertEquals("bravo goblin", hits.get(0).getBody());
		List<ChatMessage> window = inbox.messages("vermin", 10, "",
				Long.valueOf(1500L), Long.valueOf(2500L));
		assertEquals(1, window.size());
		assertEquals("bravo goblin", window.get(0).getBody());
	}

	@Test
	public void outgoingIsMineWithoutUnreadThenEchoIsAbsorbed() {
		ChatStore store = new ChatStore(new ChatInbox());
		store.appendOutgoing("vermin", "You", "Test");
		assertEquals(0, store.listThreads().get(0).getUnreadCount());
		assertTrue(store.messages("vermin", 10).get(0).isMine());
		store.appendAt("vermin", "VERMIN",
				"[ VERMIN ]: Taracair says, \"Test\"",
				System.currentTimeMillis(), null, true, false);
		List<ChatMessage> msgs = store.messages("vermin", 10);
		assertEquals(1, msgs.size());
		assertTrue(msgs.get(0).isMine());
		assertTrue(msgs.get(0).getBody().contains("Taracair"));
	}

	@Test
	public void absorbSkipsOtherPeoplesLinesInBetween() {
		ChatStore store = new ChatStore(new ChatInbox());
		store.appendOutgoing("vermin", "You", "Test");
		store.appendAt("vermin", "VERMIN", "[ VERMIN ]: Elyak waves.",
				System.currentTimeMillis());
		store.appendAt("vermin", "VERMIN",
				"[ VERMIN ]: Taracair says, \"Test\"",
				System.currentTimeMillis(), null, true, false);
		List<ChatMessage> msgs = store.messages("vermin", 10);
		assertEquals(2, msgs.size());
		assertTrue(msgs.get(0).isMine());
		assertTrue(msgs.get(0).getBody().contains("Taracair"));
		assertFalse(msgs.get(1).isMine());
	}

	@Test
	public void triggerMineFlagPaintsOwnBubble() {
		ChatStore store = new ChatStore(new ChatInbox());
		store.append("vermin", "VERMIN", "I said this", null, true);
		assertTrue(store.messages("vermin", 1).get(0).isMine());
		assertEquals(0, store.listThreads().get(0).getUnreadCount());
	}

	@Test
	public void jsonRoundTripPreservesMineAndNeedle() {
		ChatInbox inbox = new ChatInbox();
		inbox.setMineNeedle("vermin", "Taracair");
		inbox.setMineNeedle("tells", "Bob");
		inbox.setMineColor(0xFF123456);
		inbox.append("vermin", "VERMIN", "hello", 1L, true, false);
		inbox.append("tells", "Tells", "yo", 2L, false, true);
		ChatInbox loaded = ChatInbox.fromJsonBytes(inbox.toJsonBytes());
		assertEquals("", loaded.mineNeedle());
		assertEquals("Taracair", loaded.mineNeedle("vermin"));
		assertEquals("Bob", loaded.mineNeedle("tells"));
		assertEquals(0xFF123456, loaded.mineColor());
		assertTrue(loaded.messages("vermin", 1).get(0).isMine());
	}

	@Test
	public void mineNeedleIsPerThread() {
		ChatStore store = new ChatStore(new ChatInbox());
		store.setMineNeedle("vermin", "Taracair");
		store.setMineNeedle("tells", "Bob");
		store.appendAt("vermin", "VERMIN",
				"[ VERMIN ]: Elyak says, \"hi Taracair\"", 1L);
		store.appendAt("vermin", "VERMIN",
				"[ VERMIN ]: Taracair says, \"Test\"", 2L);
		store.appendAt("tells", "Tells", "Taracair tells you 'yo'", 3L);
		store.appendAt("tells", "Tells", "Bob tells you 'yo'", 4L);
		List<ChatMessage> vermin = store.messages("vermin", 10);
		assertFalse(vermin.get(0).isMine());
		assertFalse(store.displayMine(vermin.get(0)));
		assertTrue(vermin.get(1).isMine());
		assertTrue(store.displayMine(vermin.get(1)));
		List<ChatMessage> tells = store.messages("tells", 10);
		assertFalse(tells.get(0).isMine());
		assertFalse(store.displayMine(tells.get(0)));
		assertTrue(tells.get(1).isMine());
		assertTrue(store.displayMine(tells.get(1)));
	}

	@Test
	public void totalUnreadSumsTwoThreads() {
		ChatStore store = new ChatStore(new ChatInbox());
		store.appendAt("vermin", "VERMIN", "a", 1L);
		store.appendAt("vermin", "VERMIN", "b", 2L);
		store.appendAt("tells", "Tells", "c", 3L);
		assertEquals(3, store.totalUnread());
		store.markSeen("vermin");
		assertEquals(1, store.totalUnread());
	}

	@Test
	public void oldRootMineNeedleSeedsExistingThreadsOnly() {
		String json = "{\"mineNeedle\":\"Taracair\",\"threads\":[{"
				+ "\"threadId\":\"vermin\",\"title\":\"VERMIN\","
				+ "\"replyTemplate\":\"\",\"unreadCount\":0}],"
				+ "\"messages\":[{\"threadId\":\"vermin\",\"title\":\"VERMIN\","
				+ "\"body\":\"hi\",\"whenMs\":1,\"mine\":false}]}";
		ChatInbox loaded = ChatInbox.fromJsonBytes(json.getBytes());
		assertEquals("Taracair", loaded.mineNeedle("vermin"));
		assertEquals("", loaded.mineNeedle());
		loaded.append("tells", "Tells", "yo", 2L);
		assertEquals("", loaded.mineNeedle("tells"));
	}

	@Test
	public void deleteThreadRemovesMessagesAndUnread() {
		ChatStore store = new ChatStore(new ChatInbox());
		store.appendAt("vermin", "VERMIN", "a", 1L);
		store.appendAt("vermin", "VERMIN", "b", 2L);
		store.appendAt("tells", "Tells", "c", 3L);
		assertEquals(3, store.totalUnread());
		assertTrue(store.deleteThread("vermin"));
		assertEquals(0, store.messages("vermin", 10).size());
		assertEquals(1, store.listThreads().size());
		assertEquals("tells", store.listThreads().get(0).getThreadId());
		assertEquals(1, store.totalUnread());
		assertFalse(store.deleteThread("vermin"));
	}

	@Test
	public void resolveThreadIdMatchesIdAndTitleCaseInsensitively() {
		ChatStore store = new ChatStore(new ChatInbox());
		store.appendAt("chan1", "VERMIN", "a", 1L);
		store.appendAt("chan2", "Tells", "b", 2L);
		assertEquals("chan1", store.resolveThreadId("chan1"));
		assertEquals("chan1", store.resolveThreadId("CHAN1"));
		assertEquals("chan1", store.resolveThreadId("VERMIN"));
		assertEquals("chan1", store.resolveThreadId("vermin"));
		assertNull(store.resolveThreadId(null));
		assertNull(store.resolveThreadId(""));
		assertNull(store.resolveThreadId("   "));
		assertNull(store.resolveThreadId("nope"));
	}

	@Test
	public void resolveThreadIdUniqueContainsAndAmbiguousIsNull() {
		ChatStore store = new ChatStore(new ChatInbox());
		store.appendAt("g", "Guild Chat", "a", 1L);
		store.appendAt("p", "Party Chat", "b", 2L);
		assertEquals("g", store.resolveThreadId("guild"));
		assertEquals("p", store.resolveThreadId("party"));
		assertNull(store.resolveThreadId("chat"));
		assertNull(store.resolveThreadId("a"));
	}
}

package com.resurrection.blowtorch2.lib.chat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ChatNotifyBucketTest {

	@Test
	public void coerceKeepsKnownBucketsAndMapsTheRestToOther() {
		assertEquals(ChatNotifyBucket.TELLS, ChatNotifyBucket.coerce("tells"));
		assertEquals(ChatNotifyBucket.CHANNELS, ChatNotifyBucket.coerce("channels"));
		assertEquals(ChatNotifyBucket.AUCTION, ChatNotifyBucket.coerce("auction"));
		assertEquals(ChatNotifyBucket.OTHER, ChatNotifyBucket.coerce("other"));
		assertEquals(ChatNotifyBucket.OTHER, ChatNotifyBucket.coerce(null));
		assertEquals(ChatNotifyBucket.OTHER, ChatNotifyBucket.coerce(""));
		assertEquals(ChatNotifyBucket.OTHER, ChatNotifyBucket.coerce("TELLS"));
		assertEquals(ChatNotifyBucket.OTHER, ChatNotifyBucket.coerce("nick"));
	}

	@Test
	public void labelMatchesTheFourChips() {
		assertEquals("Tells", ChatNotifyBucket.label("tells"));
		assertEquals("Channels", ChatNotifyBucket.label("channels"));
		assertEquals("Auction", ChatNotifyBucket.label("auction"));
		assertEquals("Other", ChatNotifyBucket.label("other"));
		assertEquals("Other", ChatNotifyBucket.label("nope"));
	}
}

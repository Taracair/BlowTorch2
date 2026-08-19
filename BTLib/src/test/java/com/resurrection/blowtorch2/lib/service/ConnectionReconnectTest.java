package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Auto Reconnect off must mean off. Persistent connection used to OR into the
 * retry switch and raise the try count to 20, which is the 19-tries-remaining
 * line on a world that had Auto Reconnect unchecked and Tries set to 2.
 */
public class ConnectionReconnectTest {

	@Test
	public void autoOffAndPersistentOnDoesNotRetry() {
		ConnectionReconnect r = new ConnectionReconnect(null);
		r.setAutoReconnect(Boolean.FALSE);
		r.setPersistent(Boolean.TRUE);
		r.setLimit(Integer.valueOf(2));
		assertFalse(r.wantsReconnect());
		assertFalse(r.reconnectOnPeerClose());
		assertFalse(r.canAttempt());
		assertEquals(-1, r.consumeAttempt(3000L));
	}

	@Test
	public void autoOnUsesTheConfiguredTryCountEvenWhenPersistent() {
		ConnectionReconnect r = new ConnectionReconnect(null);
		r.setAutoReconnect(Boolean.TRUE);
		r.setPersistent(Boolean.TRUE);
		r.setLimit(Integer.valueOf(2));
		assertTrue(r.wantsReconnect());
		assertTrue(r.reconnectOnPeerClose());
		assertEquals(1, r.consumeAttempt(3000L));
		assertEquals(0, r.consumeAttempt(3000L));
		assertEquals(-1, r.consumeAttempt(3000L));
	}

	@Test
	public void autoOnWithoutPersistentDoesNotRetryAPeerClose() {
		ConnectionReconnect r = new ConnectionReconnect(null);
		r.setAutoReconnect(Boolean.TRUE);
		r.setPersistent(Boolean.FALSE);
		assertTrue(r.wantsReconnect());
		assertFalse(r.reconnectOnPeerClose());
	}
}

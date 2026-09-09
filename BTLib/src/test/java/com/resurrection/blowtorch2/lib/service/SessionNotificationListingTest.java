package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SessionNotificationListingTest {

	@Test
	public void includeStartingOrConnected() {
		assertTrue(SessionNotificationListing.include(true, false));
		assertTrue(SessionNotificationListing.include(false, true));
		assertTrue(SessionNotificationListing.include(true, true));
		assertFalse(SessionNotificationListing.include(false, false));
	}
}

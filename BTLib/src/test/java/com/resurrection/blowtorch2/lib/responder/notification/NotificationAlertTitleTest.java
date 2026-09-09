package com.resurrection.blowtorch2.lib.responder.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class NotificationAlertTitleTest {

	@Test
	public void prefixesWorldWhenBothPresent() {
		assertEquals("world-a · goblin",
				NotificationAlertTitle.withWorld("world-a", "goblin"));
	}

	@Test
	public void worldOnlyWhenTitleEmpty() {
		assertEquals("world-a", NotificationAlertTitle.withWorld("world-a", ""));
		assertEquals("world-a", NotificationAlertTitle.withWorld("world-a", null));
	}

	@Test
	public void titleOnlyWhenWorldMissing() {
		assertEquals("goblin", NotificationAlertTitle.withWorld("", "goblin"));
		assertEquals("goblin", NotificationAlertTitle.withWorld(null, "goblin"));
	}

	@Test
	public void throttleKeySeparatesWorldsWithSameTitle() {
		String a = NotificationAlertTitle.throttleKey("world-a", "goblin", "hi");
		String b = NotificationAlertTitle.throttleKey("world-b", "goblin", "hi");
		assertNotEquals(a, b);
	}
}

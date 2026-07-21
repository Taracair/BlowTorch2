package com.resurrection.blowtorch2.lib.trigger.condition;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConditionEvaluatorTest {
	@Test public void emptyGroupIsTrue() {
		assertTrue(new ConditionGroup().isEmpty());
	}
	@Test public void variableStoreBasics() {
		SessionVariableStore store = new SessionVariableStore();
		assertFalse(store.exists("combat"));
		store.set("combat", "1");
		assertTrue(store.exists("combat"));
		assertEquals("1", store.get("combat"));
		store.unset("combat");
		assertFalse(store.exists("combat"));
	}
	@Test public void conditionTypeXmlRoundTrip() {
		for (ConditionType t : ConditionType.values()) {
			assertEquals(t, ConditionType.fromXml(t.getXmlValue()));
		}
		assertEquals(ConditionType.TRIGGER_ENABLED, ConditionType.fromXml("trigger_enabled"));
	}
	@Test public void leafSummaryAndQualifiedName() {
		ConditionLeaf leaf = new ConditionLeaf(ConditionType.TRIGGER_ENABLED, "loot", "hunt", "");
		assertEquals("hunt:loot", leaf.qualifiedTriggerName());
		assertTrue(leaf.summary().contains("enabled"));
	}
}

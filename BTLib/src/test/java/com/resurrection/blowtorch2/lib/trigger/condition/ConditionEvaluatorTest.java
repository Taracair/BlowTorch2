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
		assertEquals(ConditionType.ALIAS_ENABLED, ConditionType.fromXml("alias_enabled"));
		assertEquals(ConditionType.ALIAS_DISABLED, ConditionType.fromXml("aliasDisabled"));
		assertEquals(ConditionType.ALIAS_EQUALS, ConditionType.fromXml("alias_equals"));
	}
	@Test public void leafSummaryAndQualifiedName() {
		ConditionLeaf leaf = new ConditionLeaf(ConditionType.TRIGGER_ENABLED, "loot", "hunt", "");
		assertEquals("hunt:loot", leaf.qualifiedName());
		assertTrue(leaf.summary().contains("ON"));
		ConditionLeaf alias = new ConditionLeaf(ConditionType.ALIAS_DISABLED, "kk", "", "");
		assertEquals("kk", alias.qualifiedName());
		assertTrue(alias.summary().contains("Alias"));
		assertTrue(alias.summary().contains("OFF"));
		ConditionLeaf eq = new ConditionLeaf(ConditionType.ALIAS_EQUALS, "kk", "", "kill $1");
		assertTrue(eq.summary().contains("kill $1"));
		assertTrue(ConditionType.ALIAS_EQUALS.needsExpectedValue());
	}
	@Test public void gateHelpers() {
		assertTrue(ConditionType.TRIGGER_ENABLED.isTriggerGate());
		assertTrue(ConditionType.ALIAS_ENABLED.isAliasGate());
		assertTrue(ConditionType.ALIAS_EQUALS.isAliasGate());
		assertTrue(ConditionType.ALIAS_EQUALS.needsExpectedValue());
		assertTrue(ConditionType.VARIABLE_EQUALS.isVariableGate());
		assertFalse(ConditionType.ALIAS_ENABLED.isTriggerGate());
	}
}

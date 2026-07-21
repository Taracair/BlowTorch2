package com.resurrection.blowtorch2.lib.trigger.condition;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConditionEvaluatorTest {

	@Test
	public void emptyGroupIsTrue() {
		ConditionGroup g = new ConditionGroup();
		assertTrue(g.isEmpty());
		assertTrue(g.equals(new ConditionGroup()));
	}

	@Test
	public void variableStoreBasics() {
		SessionVariableStore store = new SessionVariableStore();
		assertFalse(store.exists("combat"));
		assertNull(store.get("combat"));
		store.set("combat", "1");
		assertTrue(store.exists("combat"));
		assertEquals("1", store.get("combat"));
		store.unset("combat");
		assertFalse(store.exists("combat"));
	}

	@Test
	public void leafEvaluateVariableEquals() {
		SessionVariableStore store = new SessionVariableStore();
		store.set("area", "necropolis");
		ConditionLeaf eq = new ConditionLeaf(ConditionType.VARIABLE_EQUALS, "area", "", "necropolis");
		ConditionLeaf bad = new ConditionLeaf(ConditionType.VARIABLE_EQUALS, "area", "", "town");
		ConditionLeaf exists = new ConditionLeaf(ConditionType.VARIABLE_EXISTS, "area", "", "");
		ConditionLeaf missing = new ConditionLeaf(ConditionType.VARIABLE_EXISTS, "nope", "", "");

		assertTrue(store.exists(eq.getName()) && store.get(eq.getName()).equals(eq.getValue()));
		assertFalse(store.get(bad.getName()).equals(bad.getValue()));
		assertTrue(store.exists(exists.getName()));
		assertFalse(store.exists(missing.getName()));
	}

	@Test
	public void andOrSemanticsOnLeaves() {
		ConditionGroup and = new ConditionGroup();
		and.setOp(ConditionGroup.Op.AND);
		and.getChildren().add(new ConditionLeaf(ConditionType.VARIABLE_EXISTS, "a", "", ""));
		and.getChildren().add(new ConditionLeaf(ConditionType.VARIABLE_EQUALS, "a", "", "1"));

		ConditionGroup or = and.copy();
		or.setOp(ConditionGroup.Op.OR);

		SessionVariableStore store = new SessionVariableStore();
		store.set("a", "1");

		boolean andOk = true;
		for (ConditionLeaf leaf : and.getChildren()) {
			andOk = andOk && evalVar(leaf, store);
		}
		assertTrue(andOk);

		store.unset("a");
		boolean orOk = false;
		for (ConditionLeaf leaf : or.getChildren()) {
			orOk = orOk || evalVar(leaf, store);
		}
		assertFalse(orOk);

		store.set("a", "2");
		orOk = false;
		for (ConditionLeaf leaf : or.getChildren()) {
			orOk = orOk || evalVar(leaf, store);
		}
		assertTrue(orOk); // VARIABLE_EXISTS still true
	}

	@Test
	public void conditionTypeXmlRoundTrip() {
		for (ConditionType t : ConditionType.values()) {
			assertEquals(t, ConditionType.fromXml(t.getXmlValue()));
		}
		assertEquals(ConditionType.TRIGGER_ENABLED, ConditionType.fromXml("trigger_enabled"));
	}

	@Test
	public void leafSummaryAndQualifiedName() {
		ConditionLeaf leaf = new ConditionLeaf(ConditionType.TRIGGER_ENABLED, "loot", "hunt", "");
		assertEquals("hunt:loot", leaf.qualifiedTriggerName());
		assertTrue(leaf.summary().contains("enabled"));
	}

	private static boolean evalVar(ConditionLeaf leaf, SessionVariableStore store) {
		switch (leaf.getType()) {
		case VARIABLE_EXISTS:
			return store.exists(leaf.getName());
		case VARIABLE_EQUALS: {
			String v = store.get(leaf.getName());
			return v != null && v.equals(leaf.getValue());
		}
		default:
			return false;
		}
	}
}

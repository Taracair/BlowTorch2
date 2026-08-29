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
		assertTrue(ConditionType.VARIABLE_BELOW.isVariableGate());
		assertTrue(ConditionType.VARIABLE_ABOVE.isVariableGate());
		assertTrue(ConditionType.VARIABLE_BELOW.needsExpectedValue());
		assertTrue(ConditionType.VARIABLE_ABOVE.needsExpectedValue());
		assertFalse(ConditionType.VARIABLE_EXISTS.needsExpectedValue());
		assertFalse(ConditionType.ALIAS_ENABLED.isTriggerGate());
	}

	@Test public void fromXmlAcceptsBelowAboveCamelAndSnake() {
		assertEquals(ConditionType.VARIABLE_BELOW, ConditionType.fromXml("variableBelow"));
		assertEquals(ConditionType.VARIABLE_BELOW, ConditionType.fromXml("variable_below"));
		assertEquals(ConditionType.VARIABLE_ABOVE, ConditionType.fromXml("variableAbove"));
		assertEquals(ConditionType.VARIABLE_ABOVE, ConditionType.fromXml("variable_above"));
		assertNull(ConditionType.fromXml("nope"));
		assertNull(ConditionType.fromXml(""));
	}

	@Test public void missingVariableEqualsAndExistsAreFalse() {
		SessionVariableStore store = new SessionVariableStore();
		assertFalse(eval(ConditionType.VARIABLE_EQUALS, "kills", "10", store));
		assertFalse(eval(ConditionType.VARIABLE_EXISTS, "kills", "", store));
	}

	@Test public void variableEqualsIsExactString() {
		SessionVariableStore store = new SessionVariableStore();
		store.set("kills", "1");
		assertTrue(eval(ConditionType.VARIABLE_EQUALS, "kills", "1", store));
		assertFalse(eval(ConditionType.VARIABLE_EQUALS, "kills", "01", store));
		store.set("kills", "01");
		assertFalse(eval(ConditionType.VARIABLE_EQUALS, "kills", "1", store));
		assertTrue(eval(ConditionType.VARIABLE_EXISTS, "kills", "", store));
	}

	@Test public void deviceBatteryEqualsStaysExactText() {
		SessionVariableStore store = new SessionVariableStore();
		store.set("device.battery", "74");
		assertTrue(eval(ConditionType.VARIABLE_EQUALS, "device.battery", "74", store));
		assertFalse(eval(ConditionType.VARIABLE_EQUALS, "device.battery", "74.0", store));
	}

	@Test public void variableAboveIsStrictGreater() {
		SessionVariableStore store = new SessionVariableStore();
		store.set("kills", "10");
		assertTrue(eval(ConditionType.VARIABLE_ABOVE, "kills", "9", store));
		assertFalse(eval(ConditionType.VARIABLE_ABOVE, "kills", "10", store));
		assertFalse(eval(ConditionType.VARIABLE_ABOVE, "kills", "11", store));
	}

	@Test public void variableBelowIsStrictLess() {
		SessionVariableStore store = new SessionVariableStore();
		store.set("hp", "15");
		assertTrue(eval(ConditionType.VARIABLE_BELOW, "hp", "30", store));
		store.set("hp", "30");
		assertFalse(eval(ConditionType.VARIABLE_BELOW, "hp", "30", store));
		store.set("hp", "31");
		assertFalse(eval(ConditionType.VARIABLE_BELOW, "hp", "30", store));
	}

	@Test public void deviceBatteryBelowIsNumericNotStringEquals() {
		SessionVariableStore store = new SessionVariableStore();
		store.set("device.battery", "74");
		assertTrue(eval(ConditionType.VARIABLE_BELOW, "device.battery", "80", store));
		assertFalse(eval(ConditionType.VARIABLE_EQUALS, "device.battery", "74.0", store));
	}

	@Test public void nonNumericOrMissingNumericCompareIsFalse() {
		SessionVariableStore store = new SessionVariableStore();
		assertFalse(eval(ConditionType.VARIABLE_BELOW, "hp", "30", store));
		store.set("hp", "yes");
		assertFalse(eval(ConditionType.VARIABLE_BELOW, "hp", "1", store));
		store.set("hp", "15");
		assertFalse(eval(ConditionType.VARIABLE_BELOW, "hp", "nope", store));
		store.set("hp", "NaN");
		assertFalse(eval(ConditionType.VARIABLE_ABOVE, "hp", "1", store));
		store.set("hp", " 15 ");
		assertTrue(eval(ConditionType.VARIABLE_BELOW, "hp", " 30 ", store));
	}

	@Test public void belowAboveSummariesReadAsNumbers() {
		assertTrue(new ConditionLeaf(ConditionType.VARIABLE_BELOW, "hp", "", "30")
				.summary().contains("below 30"));
		assertTrue(new ConditionLeaf(ConditionType.VARIABLE_ABOVE, "kills", "", "9")
				.summary().contains("above 9"));
	}

	private static boolean eval(ConditionType type, String name, String value,
			SessionVariableStore store) {
		return ConditionEvaluator.evaluateVariable(
				new ConditionLeaf(type, name, "", value), store);
	}
}

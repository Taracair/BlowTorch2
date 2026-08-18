package com.resurrection.blowtorch2.lib.gauge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;

public class GaugeTimerWidgetBindTest {

	@Test
	public void idFromValidName() {
		assertEquals("stunwait", GaugeTimerWidgetBind.widgetIdFromTimerName("StunWait"));
		assertEquals("heal_1", GaugeTimerWidgetBind.widgetIdFromTimerName("heal_1"));
	}

	@Test
	public void idFromInvalidName() {
		assertEquals("heal_me", GaugeTimerWidgetBind.widgetIdFromTimerName("Heal me!"));
		assertEquals("t_main", GaugeTimerWidgetBind.widgetIdFromTimerName("main"));
		assertEquals("timer", GaugeTimerWidgetBind.widgetIdFromTimerName("!!!"));
		assertNull(GaugeTimerWidgetBind.widgetIdFromTimerName(null));
	}

	@Test
	public void ensureCreatesUnplacedTimerWidget() {
		ArrayList<GaugeWidget> list = new ArrayList<GaugeWidget>();
		GaugeWidget g = GaugeTimerWidgetBind.ensure(list, "stunwait");
		assertNotNull(g);
		assertEquals(1, list.size());
		assertEquals("stunwait", g.getId());
		assertEquals(GaugeWidget.Shape.TIMER, g.getShape());
		assertEquals(GaugeWidget.Source.TIMER, g.getSource());
		assertEquals("stunwait", g.getPath());
		assertTrue(g.isUnplaced());
		assertTrue(g.isVisible());
		assertTrue(GaugeTimerWidgetBind.isShowing(list, "stunwait"));
	}

	@Test
	public void ensureReusesAndHideDoesNotDelete() {
		ArrayList<GaugeWidget> list = new ArrayList<GaugeWidget>();
		GaugeWidget first = GaugeTimerWidgetBind.ensure(list, "regen");
		first.setX(40);
		first.setY(12);
		GaugeWidget again = GaugeTimerWidgetBind.ensure(list, "regen");
		assertEquals(1, list.size());
		assertEquals(first, again);
		assertEquals(40, again.getX());
		assertTrue(GaugeTimerWidgetBind.hide(list, "regen"));
		assertEquals(1, list.size());
		assertFalse(list.get(0).isVisible());
		assertFalse(GaugeTimerWidgetBind.isShowing(list, "regen"));
		GaugeTimerWidgetBind.ensure(list, "regen");
		assertTrue(list.get(0).isVisible());
	}

	@Test
	public void hideUnknownIsFalse() {
		assertFalse(GaugeTimerWidgetBind.hide(new ArrayList<GaugeWidget>(), "nope"));
	}

	@Test
	public void rebindMovesTheSameWidgetToTheNewTimerName() {
		ArrayList<GaugeWidget> list = new ArrayList<GaugeWidget>();
		GaugeWidget g = GaugeTimerWidgetBind.ensure(list, "heal");
		g.setX(40);
		g.setY(12);
		GaugeWidget moved = GaugeTimerWidgetBind.rebind(list, "heal", "heals");
		assertEquals(1, list.size());
		assertEquals(g, moved);
		assertEquals("heals", moved.getPath());
		assertEquals("heals", moved.getTimerName());
		assertEquals(40, moved.getX());
		assertTrue(moved.isVisible());
		assertFalse(GaugeTimerWidgetBind.isShowing(list, "heal"));
		assertTrue(GaugeTimerWidgetBind.isShowing(list, "heals"));
	}

	@Test
	public void rebindHidesTheOldWhenNewNameAlreadyHasAWidget() {
		ArrayList<GaugeWidget> list = new ArrayList<GaugeWidget>();
		GaugeWidget old = GaugeTimerWidgetBind.ensure(list, "heal");
		GaugeWidget neu = GaugeTimerWidgetBind.ensure(list, "heals");
		assertEquals(2, list.size());
		GaugeWidget kept = GaugeTimerWidgetBind.rebind(list, "heal", "heals");
		assertEquals(2, list.size());
		assertEquals(neu, kept);
		assertFalse(old.isVisible());
		assertTrue(neu.isVisible());
		assertEquals("heals", neu.getPath());
	}
}

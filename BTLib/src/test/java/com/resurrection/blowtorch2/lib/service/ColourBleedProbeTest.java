package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.window.TextTree;

/**
 * {@code .probe bleed} must cost nothing when unbound, and must name magenta
 * xterm indices without treating every 38;5;n as purple.
 */
public class ColourBleedProbeTest {

	@Test
	public void unboundRecordsNothing() {
		assertTrue(ColourBleedProbe.bound() == null);
		ColourBleedProbe p = new ColourBleedProbe();
		p.recordColor("vermin", "[ VERMIN ]", 2, 16, null, null, null);
		assertTrue(p.report().contains("color: 1"));
	}

	@Test
	public void bindMakesColorActionSeeTheProbe() {
		ColourBleedProbe p = new ColourBleedProbe();
		ColourBleedProbe.bind(p);
		try {
			assertTrue(ColourBleedProbe.bound() == p);
		} finally {
			ColourBleedProbe.unbind();
		}
		assertTrue(ColourBleedProbe.bound() == null);
	}

	@Test
	public void magentaXtermIsThePurpleFamilyOnly() {
		assertTrue(ColourBleedProbe.magentaXterm(5));
		assertTrue(ColourBleedProbe.magentaXterm(201));
		assertFalse(ColourBleedProbe.magentaXterm(2));
		assertFalse(ColourBleedProbe.magentaXterm(46));
		assertFalse(ColourBleedProbe.magentaXterm(37));
	}

	@Test
	public void looksMagentaOpsCatchesAnsi35AndXterm5() {
		assertTrue(ColourBleedProbe.looksMagentaOps(
				Collections.singletonList(Integer.valueOf(35))));
		assertTrue(ColourBleedProbe.looksMagentaOps(
				Arrays.asList(Integer.valueOf(38), Integer.valueOf(5),
						Integer.valueOf(5))));
		assertFalse(ColourBleedProbe.looksMagentaOps(
				Arrays.asList(Integer.valueOf(38), Integer.valueOf(5),
						Integer.valueOf(2))));
	}

	@Test
	public void reportMarksSuspectWhenRestoreIsMagenta() {
		ColourBleedProbe p = new ColourBleedProbe();
		TextTree tree = new TextTree();
		TextTree.Color restore = tree.makeColor(Collections.singletonList(
				Integer.valueOf(35)));
		p.recordColor("_vermin", "[ VERMIN ]", 2, 16, null, restore, null);
		String report = p.report();
		assertTrue(report, report.contains("SUSPECT_MAGENTA"));
		assertTrue(report, report.contains("paintFg=2"));
		assertTrue(report, report.contains("restore=[35]"));
	}

	@Test
	public void dumpRecordsPlainTextWhenTheTreeStillHasLines() throws Exception {
		ColourBleedProbe p = new ColourBleedProbe();
		TextTree tree = new TextTree();
		tree.addBytesImpl("A lamp stands here.\n".getBytes("UTF-8"));
		p.recordColor("sample", "lamp", 11, 0, null, null, tree.getLines().get(0));
		p.recordDispatchDump("window-a", tree);
		String report = p.report();
		assertTrue(report, report.contains("A lamp stands here."));
		assertTrue(report, report.contains("lines=1 "));
	}

	@Test
	public void dumpIsEmptyAfterDumpToBytesDiscardsTheTree() throws Exception {
		ColourBleedProbe p = new ColourBleedProbe();
		TextTree tree = new TextTree();
		tree.addBytesImpl("A lamp stands here.\n".getBytes("UTF-8"));
		p.recordColor("sample", "lamp", 11, 0, null, null, tree.getLines().get(0));
		tree.dumpToBytes(false);
		p.recordDispatchDump("window-a", tree);
		assertTrue(p.report().contains("lines=0 "));
	}

	@Test
	public void opsListRendersNullAndEmpty() {
		assertTrue("null".equals(ColourBleedProbe.opsList(null)));
		assertTrue("empty".equals(ColourBleedProbe.opsList(
				Collections.<Integer>emptyList())));
		assertTrue("39;49".equals(ColourBleedProbe.opsList(
				Arrays.asList(Integer.valueOf(39), Integer.valueOf(49)))));
	}
}

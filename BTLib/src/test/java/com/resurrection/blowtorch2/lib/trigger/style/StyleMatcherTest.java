package com.resurrection.blowtorch2.lib.trigger.style;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.SgrStyle;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec.Gate;
import com.resurrection.blowtorch2.lib.trigger.style.StyleSnapshot.ColorSpace;
import com.resurrection.blowtorch2.lib.window.TextTree;

public class StyleMatcherTest {

	private static final String ESC = "\u001B";

	@Test
	public void sgr1IsBrightNotWeight() {
		SgrRegisters r = SgrRegisters.defaults();
		r.beginColorUnit();
		r.applyOps(Arrays.asList(Integer.valueOf(1), Integer.valueOf(32)));
		StyleSnapshot s = r.snapshot();
		assertTrue(s.bright);
		assertFalse(s.weight());
		assertEquals(ColorSpace.ANSI16, s.fgSpace);
		assertEquals(32, s.fgCode);
	}

	@Test
	public void private66IsWeight() {
		SgrRegisters r = SgrRegisters.defaults();
		r.beginColorUnit();
		r.apply(Integer.valueOf(SgrStyle.WEIGHT_ON_CODE));
		StyleSnapshot s = r.snapshot();
		assertTrue(s.weight());
		assertFalse(s.bright);
	}

	@Test
	public void xterm208IsNotAnsi() throws Exception {
		TextTree tree = new TextTree();
		tree.addBytesImpl((ESC + "[38;5;208morange\n").getBytes("UTF-8"));
		StyleLineModel[] models = StyleLineModel.buildTree(tree);
		assertTrue(models.length > 0);
		StyleSnapshot s = models[0].atColumn(0);
		assertEquals(ColorSpace.XTERM256, s.fgSpace);
		assertEquals(208, s.fgCode);
	}

	@Test
	public void requireBoldAllowsExtraUnderline() {
		StyleMatchSpec spec = new StyleMatchSpec();
		spec.setWeight(Gate.REQUIRE);
		StyleSnapshot boldUl = new StyleSnapshot(ColorSpace.ANSI16, 37,
				ColorSpace.ANSI16, 40, false,
				SgrStyle.WEIGHT | SgrStyle.UNDERLINE, null);
		assertTrue(StyleMatcher.matches(boldUl, spec));
	}

	@Test
	public void extrasForbidRejectsUnexpectedUnderline() {
		StyleMatchSpec spec = new StyleMatchSpec();
		spec.setWeight(Gate.REQUIRE);
		spec.setExtras(StyleMatchSpec.Extras.FORBID);
		StyleSnapshot boldUl = new StyleSnapshot(ColorSpace.ANSI16, 37,
				ColorSpace.ANSI16, 40, false,
				SgrStyle.WEIGHT | SgrStyle.UNDERLINE, null);
		StyleSnapshot boldOnly = new StyleSnapshot(ColorSpace.ANSI16, 37,
				ColorSpace.ANSI16, 40, false, SgrStyle.WEIGHT, null);
		assertFalse(StyleMatcher.matches(boldUl, spec));
		assertTrue(StyleMatcher.matches(boldOnly, spec));
	}

	@Test
	public void anyCombineFiresOnOneLayer() {
		StyleMatchSpec spec = new StyleMatchSpec();
		spec.setCombine(StyleMatchSpec.Combine.ANY);
		spec.setWeight(Gate.REQUIRE);
		spec.setItalic(Gate.REQUIRE);
		StyleSnapshot italicOnly = new StyleSnapshot(ColorSpace.ANSI16, 37,
				ColorSpace.ANSI16, 40, false, SgrStyle.ITALIC, null);
		assertTrue(StyleMatcher.matches(italicOnly, spec));
		StyleSnapshot neither = new StyleSnapshot(ColorSpace.ANSI16, 37,
				ColorSpace.ANSI16, 40, false, 0, null);
		assertFalse(StyleMatcher.matches(neither, spec));
	}

	@Test
	public void exactFgDoesNotMatchLooksAlikeXterm() {
		StyleMatchSpec spec = new StyleMatchSpec();
		spec.setFg(Gate.REQUIRE, ColorSpace.ANSI16, 32);
		spec.setColorMode(StyleMatchSpec.ColorMode.EXACT);
		StyleSnapshot xterm = new StyleSnapshot(ColorSpace.XTERM256, 2,
				ColorSpace.ANSI16, 40, false, 0, null);
		assertFalse(StyleMatcher.matches(xterm, spec));
		spec.setColorMode(StyleMatchSpec.ColorMode.LOOKS);
		assertTrue(StyleMatcher.matches(xterm, spec));
	}

	@Test
	public void inactiveSpecAlwaysMatches() {
		StyleSnapshot s = new StyleSnapshot(ColorSpace.ANSI16, 31, ColorSpace.ANSI16,
				40, false, 0, null);
		assertTrue(StyleMatcher.matches(s, StyleMatchSpec.inactive()));
	}

	@Test
	public void textLayerUsesRun() {
		StyleMatchSpec spec = new StyleMatchSpec();
		spec.setText("goblin");
		StyleSnapshot s = new StyleSnapshot(ColorSpace.ANSI16, 37, ColorSpace.ANSI16,
				40, false, 0, null);
		assertTrue(StyleMatcher.matches(s, spec, "a goblin here"));
		assertFalse(StyleMatcher.matches(s, spec, "orc"));
	}

	@Test
	public void triggerPaintIsNotApplied() throws Exception {
		TextTree tree = new TextTree();
		tree.addBytesImpl((ESC + "[32mgreen\n").getBytes("UTF-8"));
		TextTree.Line line = tree.getLines().get(0);
		for (TextTree.Unit u : line.getData()) {
			if (u instanceof TextTree.Color) {
				((TextTree.Color) u).setTriggerPaint(true);
			}
		}
		SgrRegisters r = SgrRegisters.defaults();
		StyleLineModel model = StyleLineModel.build(line, r);
		assertEquals(ColorSpace.ANSI16, model.atColumn(0).fgSpace);
		assertEquals(37, model.atColumn(0).fgCode);
	}

	@Test
	public void forbidFgRejectsThatColour() {
		StyleMatchSpec spec = new StyleMatchSpec();
		spec.setFg(Gate.FORBID, ColorSpace.ANSI16, 32);
		StyleSnapshot green = new StyleSnapshot(ColorSpace.ANSI16, 32,
				ColorSpace.ANSI16, 40, false, 0, null);
		StyleSnapshot white = new StyleSnapshot(ColorSpace.ANSI16, 37,
				ColorSpace.ANSI16, 40, false, 0, null);
		assertFalse(StyleMatcher.matches(green, spec));
		assertTrue(StyleMatcher.matches(white, spec));
	}

	@Test
	public void lupaChecksBecomeRequire() {
		StyleSnapshot s = new StyleSnapshot(ColorSpace.XTERM256, 208,
				ColorSpace.ANSI16, 40, true, SgrStyle.WEIGHT, null);
		boolean[] on = new boolean[] { true, false, true, true, false, false, false,
				false, false, false, false, false };
		StyleMatchSpec spec = StyleClipboard.specFromChecks(s, "loot", on, false,
				false, false);
		assertTrue(spec.isActive());
		assertEquals(Gate.REQUIRE, spec.getFgGate());
		assertEquals(208, spec.getFgCode());
		assertEquals(Gate.IGNORE, spec.getBgGate());
		assertEquals(Gate.REQUIRE, spec.getBright());
		assertEquals(Gate.REQUIRE, spec.getWeight());
		assertTrue(StyleMatcher.matches(s, spec, "loot"));
	}

	@Test
	public void gateTrueIsRequireFalseIsForbid() {
		assertEquals(Gate.REQUIRE, Gate.fromXml("true"));
		assertEquals(Gate.FORBID, Gate.fromXml("false"));
	}

	@Test
	public void seedCarriesBrightIntoLineWithNoColorUnit() throws Exception {
		TextTree first = new TextTree();
		first.addBytesImpl((ESC + "[1;31mHello\n").getBytes("UTF-8"));
		SgrRegisters seed = SgrRegisters.defaults();
		StyleLineModel.buildTree(first, seed);
		assertTrue(seed.snapshot().bright);
		assertEquals(31, seed.snapshot().fgCode);

		TextTree second = new TextTree();
		second.addBytesImpl("world\n".getBytes("UTF-8"));
		StyleLineModel[] noSeed = StyleLineModel.buildTree(second);
		assertFalse(noSeed[0].atColumn(0).bright);
		assertEquals(37, noSeed[0].atColumn(0).fgCode);

		StyleLineModel[] withSeed = StyleLineModel.buildTree(second, seed);
		assertTrue(withSeed[0].atColumn(0).bright);
		assertEquals(31, withSeed[0].atColumn(0).fgCode);
	}

	@Test
	public void osc8CloseClearsHrefOnFollowingText() throws Exception {
		TextTree tree = new TextTree();
		String chunk = ESC + "]8;;https://example.com/real\u0007"
				+ "click" + ESC + "]8;;\u0007" + " after\n";
		tree.addBytesImpl(chunk.getBytes("UTF-8"));
		StyleLineModel[] models = StyleLineModel.buildTree(tree);
		StyleLineModel model = models[0];
		int click = model.plain.indexOf("click");
		int after = model.plain.indexOf(" after");
		assertTrue(click >= 0 && after >= 0);
		assertEquals("https://example.com/real", model.atColumn(click).href);
		assertTrue(model.atColumn(after).href == null
				|| model.atColumn(after).href.length() == 0);
	}

	@Test
	public void forbidRunTextRejectsThatString() {
		StyleMatchSpec spec = new StyleMatchSpec();
		spec.setText("loot");
		spec.setTextGate(Gate.FORBID);
		StyleSnapshot s = new StyleSnapshot(ColorSpace.ANSI16, 37, ColorSpace.ANSI16,
				40, false, 0, null);
		assertFalse(StyleMatcher.matches(s, spec, "a loot pile"));
		assertTrue(StyleMatcher.matches(s, spec, "a chest"));
	}

	@Test
	public void emptyForbidRunTextIsNotActive() {
		StyleMatchSpec spec = new StyleMatchSpec();
		spec.setTextGate(Gate.FORBID);
		assertFalse(spec.isActive());
		StyleSnapshot s = new StyleSnapshot(ColorSpace.ANSI16, 37, ColorSpace.ANSI16,
				40, false, 0, null);
		assertTrue(StyleMatcher.matches(s, spec, "anything"));
	}

	@Test
	public void secondLineInSameChunkInheritsSgr() throws Exception {
		TextTree tree = new TextTree();
		tree.addBytesImpl((ESC + "[1;31mHello\nworld\n").getBytes("UTF-8"));
		StyleLineModel[] models = StyleLineModel.buildTree(tree);
		assertEquals(2, models.length);
		assertTrue(models[1].atColumn(0).bright);
		assertEquals(31, models[1].atColumn(0).fgCode);
		assertTrue(models[0].atColumn(0).bright);
		assertEquals(31, models[0].atColumn(0).fgCode);
		assertEquals("Hello".length(), models[1].matchLength());
	}
}

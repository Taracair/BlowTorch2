package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.Colorizer;

/**
 * Characterization of how {@link TextTree} splits SGR into operation lists.
 * Window's draw path feeds those integers to {@link Colorizer}; if the split is
 * wrong, 256-colour text draws as the default colour.
 *
 * <p>xterm ctlseqs / ITU T.416: {@code CSI 38;5;n m} and {@code CSI 38:5:n m}
 * are both indexed 256-colour. Truecolor is {@code CSI 38;2;r;g;b m} or
 * {@code CSI 38:2::r:g:b m}.
 */
public class TextTreeXtermColorTest {

	private static final String ESC = "\u001B";

	private static List<Integer> firstColorOps(final String chunk)
			throws UnsupportedEncodingException {
		TextTree tree = new TextTree();
		tree.addBytesImpl((chunk + "x\n").getBytes("UTF-8"));
		List<List<Integer>> all = colorOps(tree);
		assertTrue("expected a Color unit in: " + visible(chunk), !all.isEmpty());
		return all.get(0);
	}

	private static List<List<Integer>> colorOps(final TextTree tree) {
		List<List<Integer>> out = new ArrayList<List<Integer>>();
		List<TextTree.Line> lines = tree.getLines();
		for (int i = lines.size() - 1; i >= 0; i--) {
			for (TextTree.Unit u : lines.get(i).getData()) {
				if (u instanceof TextTree.Color) {
					out.add(new ArrayList<Integer>(((TextTree.Color) u).getOperations()));
				}
			}
		}
		return out;
	}

	private static String visible(final String s) {
		return s.replace(ESC, "<ESC>");
	}

	/** Evennia / Tintin / most MUDs: semicolon form. */
	@Test
	public void semicolon256ForegroundSplitsTo38_5_n() throws Exception {
		assertEquals(Arrays.asList(38, 5, 196),
				firstColorOps(ESC + "[38;5;196m"));
	}

	@Test
	public void semicolon256BackgroundSplitsTo48_5_n() throws Exception {
		assertEquals(Arrays.asList(48, 5, 16),
				firstColorOps(ESC + "[48;5;16m"));
	}

	@Test
	public void semicolonTruecolorForegroundSplitsTo38_2_rgb() throws Exception {
		assertEquals(Arrays.asList(38, 2, 255, 128, 0),
				firstColorOps(ESC + "[38;2;255;128;0m"));
	}

	@Test
	public void aixtermBrightForegroundIsASingleOp() throws Exception {
		assertEquals(Arrays.asList(91),
				firstColorOps(ESC + "[91m"));
	}

	@Test
	public void ansi16StillSplits() throws Exception {
		assertEquals(Arrays.asList(1, 31),
				firstColorOps(ESC + "[1;31m"));
	}

	@Test
	public void sgr22SurvivesAsItsOwnColorUnit() throws Exception {
		assertEquals(Arrays.asList(22),
				firstColorOps(ESC + "[22m"));
		TextTree tree = new TextTree();
		tree.addBytesImpl((ESC + "[1;37mbright" + ESC + "[22mnormal\n")
				.getBytes("UTF-8"));
		List<List<Integer>> ops = colorOps(tree);
		assertTrue("missing [1, 37]: " + ops, ops.contains(Arrays.asList(1, 37)));
		assertTrue("missing [22]: " + ops, ops.contains(Arrays.asList(22)));
	}

	@Test
	public void xtermIndex22IsPaletteNotSgrIntensity() throws Exception {
		assertEquals(Arrays.asList(38, 5, 22),
				firstColorOps(ESC + "[38;5;22m"));
	}

	/**
	 * {@code 38;5;3} is xterm index 3 (olive in the cube), not italic.
	 * Window's xterm branch consumes the 3 before {@code getColorType}.
	 * Standalone 3 is italic — that is a different unit.
	 */
	@Test
	public void xtermIndex3IsPaletteNotItalic() throws Exception {
		assertEquals(Arrays.asList(38, 5, 3),
				firstColorOps(ESC + "[38;5;3m"));
		assertEquals(Arrays.asList(3),
				firstColorOps(ESC + "[3m"));
	}

	/** Tempest Season login banner (measured 16 Aug 2026, no TTYPE). */
	@Test
	public void tempestSeasonBannerCyanSplits() throws Exception {
		assertEquals(Arrays.asList(0, 36),
				firstColorOps(ESC + "[0;36m"));
	}

	/**
	 * ITU T.416 / ISO-8613-6 colon form. Before this change the colon was
	 * skipped and {@code 38:5:196} became the single integer 385196, so
	 * Window never entered 256-colour mode.
	 */
	@Test
	public void colon256ForegroundSplitsTo38_5_n() throws Exception {
		assertEquals(Arrays.asList(38, 5, 196),
				firstColorOps(ESC + "[38:5:196m"));
	}

	@Test
	public void colon256BackgroundSplitsTo48_5_n() throws Exception {
		assertEquals(Arrays.asList(48, 5, 16),
				firstColorOps(ESC + "[48:5:16m"));
	}

	/** T.416 truecolor with empty colorspace slot → same ops as xterm {@code 38;2;r;g;b}. */
	@Test
	public void colonTruecolorDropsEmptyColorspace() throws Exception {
		assertEquals(Arrays.asList(38, 2, 255, 128, 0),
				firstColorOps(ESC + "[38:2::255:128:0m"));
	}

	@Test
	public void colonTruecolorWithColorspaceIdZero() throws Exception {
		assertEquals(Arrays.asList(38, 2, 255, 128, 0),
				firstColorOps(ESC + "[38:2:0:255:128:0m"));
	}

	/** xterm mixed form: semicolon after 38, colons for the rest. */
	@Test
	public void mixedSemicolonThenColon256() throws Exception {
		assertEquals(Arrays.asList(38, 5, 196),
				firstColorOps(ESC + "[38;5:196m"));
	}

	/** Combined FG+BG, the shape Aardwolf / many snippets emit. */
	@Test
	public void combinedSemicolonFgAndBg() throws Exception {
		assertEquals(Arrays.asList(38, 5, 196, 48, 5, 16),
				firstColorOps(ESC + "[38;5;196;48;5;16m"));
	}

	@Test
	public void paletteIndex196IsCubeRed() {
		assertEquals(0xFFFF0000, Colorizer.get256ColorValue(196));
		assertEquals(0xFFFF0000, Colorizer.getColorValue(0, 196, true));
	}
}

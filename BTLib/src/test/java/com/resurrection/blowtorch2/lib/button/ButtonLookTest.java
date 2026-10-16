package com.resurrection.blowtorch2.lib.button;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ButtonLookTest {

	@Test
	public void magentaBecomesClearAndRealRedStays() {
		int[] pixels = new int[] { 0xFFFF00FF, 0xFFF40AF4, 0xFFFF0000, 0x88FFFFFF };
		ButtonChromaKey.keyInto(pixels);
		assertEquals(0, pixels[0]);
		assertEquals(0, pixels[1]);
		assertEquals(0xFFFF0000, pixels[2]);
		assertEquals(0x88FFFFFF, pixels[3]);
	}

	@Test
	public void fontTagOnlyAtTheStart() {
		ButtonLabelPrefix.Result bold = ButtonLabelPrefix.parse("{font:bold}North");
		assertEquals("North", bold.display);
		assertNull(bold.path);
		assertEquals("bold", bold.style);

		ButtonLabelPrefix.Result mid = ButtonLabelPrefix.parse("go {font:bold}North");
		assertEquals("go {font:bold}North", mid.display);
		assertNull(mid.style);

		ButtonLabelPrefix.Result path = ButtonLabelPrefix.parse(
				"{font:bold:/storage/emulated/0/BlowTorch/fonts/My.ttf}Look");
		assertEquals("Look", path.display);
		assertEquals("bold", path.style);
		assertEquals("/storage/emulated/0/BlowTorch/fonts/My.ttf", path.path);

		ButtonLabelPrefix.Result serif = ButtonLabelPrefix.parse("{font:serif}Say");
		assertEquals("Say", serif.display);
		assertEquals("serif", serif.path);
		assertNull(serif.style);

		ButtonLabelPrefix.Result junk = ButtonLabelPrefix.parse("{font:nope}Stay");
		assertEquals("{font:nope}Stay", junk.display);
	}

	@Test
	public void centerBaselineMatchesTheFloatingCopy() {
		float top = 0f;
		float bottom = 40f;
		float ascent = -12f;
		float descent = 3f;
		float mid = 20f;
		float expected = mid - (ascent + descent) / 2f;
		assertEquals(expected, ButtonLabelAnchor.baseline(top, bottom, ascent, descent, "center", 4f),
				0.01f);
		assertEquals(10f, ButtonLabelAnchor.left(0f, 40f, 20f, "center", 4f), 0.01f);
		assertEquals(4f, ButtonLabelAnchor.left(0f, 40f, 20f, "left", 4f), 0.01f);
		assertEquals(16f, ButtonLabelAnchor.left(0f, 40f, 20f, "right", 4f), 0.01f);
		assertEquals(4f - ascent, ButtonLabelAnchor.baseline(top, bottom, ascent, descent, "top", 4f),
				0.01f);
	}

	@Test
	public void builtinIdRejectsTraversal() {
		assertEquals("buttonfaces/marks/sword.png", ButtonFaceRef.builtinPng("marks/sword"));
		assertNull(ButtonFaceRef.builtinPng("../marks/sword"));
		assertNull(ButtonFaceRef.builtinPng("marks/../sword"));
		assertNull(ButtonFaceRef.builtinPng("marks/sword/extra"));
		assertTrue(ButtonFaceRef.parse("builtin:marks/sword").assetPng.endsWith("sword.png"));
		assertTrue(ButtonFaceRef.parse("").isNone());
		assertTrue(ButtonFaceRef.isSvg("/storage/emulated/0/BlowTorch/x.SVG"));
		assertTrue(ButtonFaceRef.isRaster("/a/b.JPEG"));
		assertFalse(ButtonFaceRef.isRaster("/a/b.txt"));
	}

	@Test
	public void styleBitsDefaultToBold() {
		assertEquals(android.graphics.Typeface.BOLD, ButtonTypefaces.styleBits(null));
		assertEquals(android.graphics.Typeface.BOLD, ButtonTypefaces.styleBits(""));
		assertEquals(android.graphics.Typeface.NORMAL, ButtonTypefaces.styleBits("regular"));
		assertEquals(android.graphics.Typeface.ITALIC, ButtonTypefaces.styleBits("italic"));
		assertEquals(android.graphics.Typeface.BOLD_ITALIC, ButtonTypefaces.styleBits("bolditalic"));
		assertTrue(ButtonTypefaces.fileFace("fonts/FairfaxHD.ttf"));
		assertFalse(ButtonTypefaces.fileFace("serif"));
		assertFalse(ButtonTypefaces.fileFace("default"));
		assertTrue(ButtonTypefaces.fakeBold("fonts/FairfaxHD.ttf", "bold"));
		assertTrue(ButtonTypefaces.fakeBold("fonts/FairfaxHD.ttf", "bolditalic"));
		assertFalse(ButtonTypefaces.fakeBold("fonts/FairfaxHD.ttf", "regular"));
		assertFalse(ButtonTypefaces.fakeBold("serif", "bold"));
		assertEquals(-0.25f, ButtonTypefaces.textSkew("fonts/FairfaxHD.ttf", "italic"), 0f);
		assertEquals(-0.25f, ButtonTypefaces.textSkew("fonts/FairfaxHD.ttf", "bolditalic"), 0f);
		assertEquals(0f, ButtonTypefaces.textSkew("fonts/FairfaxHD.ttf", "regular"), 0f);
		assertEquals(0f, ButtonTypefaces.textSkew("monospace", "italic"), 0f);
		ButtonTypefaces.resolve(null, "fonts/missing.ttf", "bold");
		assertFalse(ButtonTypefaces.fakeBold("fonts/missing.ttf", "bold"));
		assertEquals(0f, ButtonTypefaces.textSkew("fonts/missing.ttf", "bold"), 0f);
		assertTrue(ButtonTypefaces.fakeBold("fonts/FairfaxHD.ttf", "bold"));
	}

	@Test
	public void swipePreviewKeepsTheIdlePicture() {
		assertEquals(ButtonFaces.STATE_PRESSED,
				ButtonFaces.pictureState(ButtonFaces.STATE_PRESSED, false));
		assertEquals(ButtonFaces.STATE_FLIP,
				ButtonFaces.pictureState(ButtonFaces.STATE_FLIP, false));
		assertEquals(ButtonFaces.STATE_IDLE,
				ButtonFaces.pictureState(ButtonFaces.STATE_PRESSED, true));
		assertEquals(ButtonFaces.STATE_IDLE,
				ButtonFaces.pictureState(ButtonFaces.STATE_FLIP, true));
		assertEquals(ButtonFaces.STATE_IDLE,
				ButtonFaces.pictureState(ButtonFaces.STATE_IDLE, false));
	}

	@Test
	public void hugeImageIsSampledBelowTheCap() {
		assertEquals(1, ButtonFaces.sampleSize(192, 192, 1024));
		assertEquals(8, ButtonFaces.sampleSize(5000, 5000, 1024));
		assertEquals(8, ButtonFaces.sampleSize(5000, 80, 1024));
		assertTrue(5000 / ButtonFaces.sampleSize(5000, 5000, 1024) <= 1024);
		assertEquals(8192, ButtonFaces.MAX_SOURCE_EDGE);
		assertEquals(2 * 1024 * 1024, ButtonFaces.MAX_SOURCE_BYTES);
	}

	@Test
	public void imagePickKeepsRasterNamesOnly() {
		assertEquals("sword.png", ButtonAssetPick.sanitizeImageName("sword.png"));
		assertEquals("a-b.jpg", ButtonAssetPick.sanitizeImageName("/storage/a-b.jpg"));
		assertNull(ButtonAssetPick.sanitizeImageName("notes.txt"));
		assertNull(ButtonAssetPick.sanitizeImageName(null));
	}
}

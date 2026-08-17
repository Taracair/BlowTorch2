package com.resurrection.blowtorch2.lib.service.mxp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MxpSoundTest {

	@Test
	public void offAndStopAreStops_fileNameIsNot() {
		assertTrue(MxpSound.isStop("Off"));
		assertTrue(MxpSound.isStop("stop"));
		assertFalse(MxpSound.isStop("Off.wav"));
		assertFalse(MxpSound.isStop("beep"));
	}

	@Test
	public void onlyHttpAndHttpsDownload() {
		assertTrue(MxpSound.isAllowedDownloadUrl("https://ex.com/s/"));
		assertTrue(MxpSound.isAllowedDownloadUrl("http://ex.com/s/"));
		assertFalse(MxpSound.isAllowedDownloadUrl("file:///sdcard/x.wav"));
		assertFalse(MxpSound.isAllowedDownloadUrl("javascript:alert(1)"));
		assertFalse(MxpSound.isAllowedDownloadUrl("ftp://ex.com/s.wav"));
		assertFalse(MxpSound.isAllowedDownloadUrl(""));
	}

	@Test
	public void directoryUrlAppendsFname() {
		assertEquals("https://ex.com/sounds/hit.wav",
				MxpSound.resolveDownloadUrl("https://ex.com/sounds/", "hit.wav"));
		assertEquals("https://ex.com/sounds/hit.wav",
				MxpSound.resolveDownloadUrl("https://ex.com/sounds", "hit.wav"));
	}

	@Test
	public void fileUrlIsUsedAsIs() {
		assertEquals("https://ex.com/sounds/hit.wav",
				MxpSound.resolveDownloadUrl("https://ex.com/sounds/hit.wav", "hit.wav"));
		assertEquals("https://ex.com/other.ogg",
				MxpSound.resolveDownloadUrl("https://ex.com/other.ogg", "hit.wav"));
	}

	@Test
	public void emptyFnameOnDirectoryIsNotADownload() {
		assertEquals("", MxpSound.resolveDownloadUrl("https://ex.com/sounds/", ""));
		assertEquals("", MxpSound.resolveDownloadUrl("file:///etc/passwd", "x.wav"));
	}

	@Test
	public void relativeNameDropsDotDot() {
		assertEquals("hit.wav", MxpSound.safeRelativeName("../hit.wav"));
		assertEquals("combat/hit.wav", MxpSound.safeRelativeName("combat/hit.wav"));
		assertEquals("etc/passwd", MxpSound.safeRelativeName("/etc/passwd"));
		assertEquals("", MxpSound.safeRelativeName(".."));
	}
}

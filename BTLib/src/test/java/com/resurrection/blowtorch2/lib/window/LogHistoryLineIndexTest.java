package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Paging the viewer: a page is a byte range, not the whole file as one String.
 */
public class LogHistoryLineIndexTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void pageDoesNotLoadTheWholeFileAsOneString() throws Exception {
		StringBuilder all = new StringBuilder();
		for (int i = 0; i < 50; i++) {
			all.append("line ").append(i).append('\n');
		}
		File f = folder.newFile("world_2026-01-01_00-00-00.txt");
		FileOutputStream out = new FileOutputStream(f);
		try {
			out.write(all.toString().getBytes(StandardCharsets.UTF_8));
		} finally {
			out.close();
		}
		LogHistoryDialog.LineIndex idx = LogHistoryDialog.LineIndex.build(f);
		assertEquals(50, idx.lineCount());
		String page = idx.readPage(f, 10, 5);
		assertEquals("line 10\nline 11\nline 12\nline 13\nline 14", page);
		assertTrue(page.indexOf("line 0") < 0);
		assertTrue(page.indexOf("line 49") < 0);
	}

	@Test
	public void offsetOfPageLineSkipsThatManyNewlines() {
		String page = "aaaa\nbbbb\ncccc\n";
		assertEquals(0, LogHistoryDialog.offsetOfPageLine(page, 0));
		assertEquals(5, LogHistoryDialog.offsetOfPageLine(page, 1));
		assertEquals(10, LogHistoryDialog.offsetOfPageLine(page, 2));
		assertEquals(page.length(), LogHistoryDialog.offsetOfPageLine(page, 99));
		assertEquals(0, LogHistoryDialog.offsetOfPageLine(null, 1));
	}

	@Test
	public void emptyFileHasNoLines() throws Exception {
		File f = folder.newFile("empty.txt");
		LogHistoryDialog.LineIndex idx = LogHistoryDialog.LineIndex.build(f);
		assertEquals(0, idx.lineCount());
		assertEquals("", idx.readPage(f, 0, 10));
	}
}

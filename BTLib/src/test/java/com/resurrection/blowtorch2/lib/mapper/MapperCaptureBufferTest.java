package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.mapper.MapperController.CapturePreview;
import com.resurrection.blowtorch2.lib.window.TextTree;

/** Capture preview reads the newest lines, not the start of the scrollback. */
public class MapperCaptureBufferTest {

	@Test
	public void newestLinesAreTheLastReceivedInOrder() throws Exception {
		TextTree tree = new TextTree();
		for (int i = 0; i < 50; i++) {
			tree.addBytesImpl(("filler " + i + "\n").getBytes("UTF-8"));
		}
		tree.addBytesImpl("@roomid oefjo#3eijfk\n@dir north, south,east,west,north\n"
				.getBytes("UTF-8"));
		List<String> lines = MapperController.newestLines(tree.getLines(), 20);
		assertEquals(20, lines.size());
		assertEquals("@dir north, south,east,west,north", lines.get(19).trim());
		assertEquals("@roomid oefjo#3eijfk", lines.get(18).trim());

		StringBuilder text = new StringBuilder();
		for (String l : lines) {
			text.append(l).append('\n');
		}
		CapturePreview preview = new MapperController(new Object()).previewCapture(
				"^@roomid (.*)$", "^@dir (.*), (.*)$", text.toString(), lines.size());
		assertEquals("oefjo#3eijfk", preview.title);
		assertEquals("north", preview.exits);
	}
}

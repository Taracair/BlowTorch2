package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;

/**
 * The draw-path colour memo on {@link TextTree.Color} must die when the ops
 * change — otherwise a replace responder that rewrites a colour unit would keep
 * painting the previous resolution.
 */
public class ColorDrawCacheInvalidationTest {

	@Test
	public void setOperationsClearsDrawCache() {
		TextTree tree = new TextTree();
		TextTree.Color color = tree.new Color();
		color.drawCacheValid = true;
		color.drawCacheBeforeFp = 42;
		color.drawCacheFg = 0xFFFF0000;

		ArrayList<Integer> ops = new ArrayList<Integer>();
		ops.add(Integer.valueOf(31));
		color.setOperations(ops);

		assertFalse(color.drawCacheValid);
	}

	@Test
	public void freshColorStartsWithoutCache() {
		TextTree tree = new TextTree();
		TextTree.Color color = tree.new Color();
		assertFalse(color.drawCacheValid);
	}

	@Test
	public void byteConstructorStartsWithoutCache() throws Exception {
		TextTree tree = new TextTree();
		byte[] esc = new byte[] { 0x1B, '[', '3', '1', 'm' };
		TextTree.Color color = tree.new Color(esc);
		assertFalse(color.drawCacheValid);
		assertTrue(color.getOperations().size() >= 1);
	}
}

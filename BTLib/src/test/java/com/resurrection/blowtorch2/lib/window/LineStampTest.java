package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;

import org.junit.Test;

public class LineStampTest {

	@Test
	public void parseRoundTripsTheMarkerPayload() {
		byte[] bytes = LineStamp.marker(1_700_000_000_000L);
		String all = new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
		assertTrue(all.startsWith("\u001b]"));
		assertTrue(all.endsWith("\u0007"));
		String payload = all.substring(2, all.length() - 1);
		assertEquals(Long.valueOf(1_700_000_000_000L), LineStamp.parse(payload));
	}

	@Test
	public void parseIgnoresOtherOsc() {
		assertEquals(null, LineStamp.parse("8;;https://example.com"));
		assertEquals(null, LineStamp.parse("BTIMG;k;3"));
		assertEquals(null, LineStamp.parse(null));
	}

	@Test
	public void overlayIsTimeOnlyOnTheSameDay() {
		long now = System.currentTimeMillis();
		String label = LineStamp.overlayLabel(now, now);
		assertTrue("same-day label should be HH:mm, was: " + label,
				label.matches("\\d{2}:\\d{2}"));
	}

	@Test
	public void overlayNamesTheDayWhenItIsNotToday() {
		Calendar c = Calendar.getInstance();
		c.set(2024, Calendar.AUGUST, 18, 23, 10, 0);
		long then = c.getTimeInMillis();
		Calendar now = Calendar.getInstance();
		now.set(2026, Calendar.AUGUST, 19, 12, 0, 0);
		String label = LineStamp.overlayLabel(then, now.getTimeInMillis());
		assertTrue("expected a year in " + label, label.contains("2024"));
		assertTrue(label.contains("Aug"));
	}

	@Test
	public void searchRecognisesAClockAndNotABareNumber() {
		assertTrue(LineStamp.looksLikeWhenQuery("14:32"));
		assertTrue(LineStamp.looksLikeWhenQuery("14:32:07"));
		assertTrue(LineStamp.looksLikeWhenQuery("18 Aug"));
		assertTrue(LineStamp.looksLikeWhenQuery("2026-08-18"));
		assertFalse(LineStamp.looksLikeWhenQuery("opens"));
		assertFalse(LineStamp.looksLikeWhenQuery("14"));
		assertTrue(LineStamp.looksLikeWhenQuery("Aug 18"));
	}

	@Test
	public void searchEighteenAugustDoesNotHitTheEighth() {
		Calendar c = Calendar.getInstance();
		c.set(2026, Calendar.AUGUST, 8, 14, 32, 0);
		c.set(Calendar.MILLISECOND, 0);
		long eighth = c.getTimeInMillis();
		c.set(2026, Calendar.AUGUST, 18, 14, 32, 0);
		long eighteenth = c.getTimeInMillis();
		assertFalse(LineStamp.matchesQuery(eighth, "18 Aug"));
		assertTrue(LineStamp.matchesQuery(eighteenth, "18 Aug"));
		assertTrue(LineStamp.matchesQuery(eighteenth, "Aug 18"));
		assertTrue(LineStamp.matchesQuery(eighteenth, "14:32"));
		assertFalse(LineStamp.matchesQuery(eighth, "18 Aug, 14:32"));
		assertTrue(LineStamp.matchesQuery(eighteenth, "2026-08-18"));
	}

	@Test
	public void dumpToBytesRoundTripsTheStampToAFreshTree() throws Exception {
		TextTree a = new TextTree();
		a.addBytesImpl("hello\n".getBytes("UTF-8"));
		long stamped = a.getLines().get(0).getReceivedAt();
		assertTrue(stamped > 0L);
		byte[] dump = a.dumpToBytes(true);
		TextTree b = new TextTree();
		b.addBytesImpl(dump);
		assertEquals(stamped, b.getLines().get(0).getReceivedAt());
		assertEquals("hello", TextTree.deColorLine(b.getLines().get(0)).toString());
	}
}

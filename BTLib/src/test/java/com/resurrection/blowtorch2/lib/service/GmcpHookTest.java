package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class GmcpHookTest {

	private static byte[] utf8(final String s) {
		try {
			return s.getBytes("UTF-8");
		} catch (java.io.UnsupportedEncodingException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	public void noStepsLeavesTheBody() throws Exception {
		GmcpHook.Result result = GmcpHook.apply("Room.Info", "{\"num\":1}", null);
		assertFalse(result.dropped);
		assertEquals("{\"num\":1}", result.body);
	}

	@Test
	public void nilLeavesTheBody() throws Exception {
		GmcpHook.Result result = GmcpHook.apply("Room.Info", "{\"num\":1}",
				Collections.singletonList(new GmcpHook.Step() {
					@Override
					public byte[] apply(final String module, final byte[] body) {
						assertEquals("Room.Info", module);
						return null;
					}
				}));
		assertFalse(result.dropped);
		assertEquals("{\"num\":1}", result.body);
	}

	@Test
	public void theSameBytesKeepTheOriginalText() throws Exception {
		final String original = "{ \"num\" : 1 }";
		GmcpHook.Result result = GmcpHook.apply("Room.Info", original,
				Collections.singletonList(new GmcpHook.Step() {
					@Override
					public byte[] apply(final String module, final byte[] body) {
						return Arrays.copyOf(body, body.length);
					}
				}));
		assertFalse(result.dropped);
		assertTrue(result.body == original);
	}

	@Test
	public void aStringReplacesTheBody() throws Exception {
		GmcpHook.Result result = GmcpHook.apply("Room.Info", "{\"name\":\"Hall\"}",
				Collections.singletonList(new GmcpHook.Step() {
					@Override
					public byte[] apply(final String module, final byte[] body) {
						return utf8("{\"name\":\"Temple\"}");
					}
				}));
		assertFalse(result.dropped);
		assertEquals("{\"name\":\"Temple\"}", result.body);
	}

	@Test
	public void anEmptyArrayDropsTheMessage() throws Exception {
		GmcpHook.Result result = GmcpHook.apply("Char.Vitals", "{\"hp\":1}",
				Collections.singletonList(new GmcpHook.Step() {
					@Override
					public byte[] apply(final String module, final byte[] body) {
						return new byte[0];
					}
				}));
		assertTrue(result.dropped);
		assertNull(result.body);
	}

	@Test
	public void aModuleOnlyMessageIsShownAndAnEmptyReturnDropsIt() throws Exception {
		GmcpHook.Result left = GmcpHook.apply("Core.Ping", null,
				Collections.singletonList(new GmcpHook.Step() {
					@Override
					public byte[] apply(final String module, final byte[] body) {
						assertEquals(0, body.length);
						return null;
					}
				}));
		assertFalse(left.dropped);
		assertNull(left.body);

		GmcpHook.Result dropped = GmcpHook.apply("Core.Ping", null,
				Collections.singletonList(new GmcpHook.Step() {
					@Override
					public byte[] apply(final String module, final byte[] body) {
						return new byte[0];
					}
				}));
		assertTrue(dropped.dropped);
	}

	@Test
	public void aThrowKeepsThatStepInputAndTheNextStepRuns() throws Exception {
		List<GmcpHook.Step> steps = new ArrayList<GmcpHook.Step>();
		steps.add(new GmcpHook.Step() {
			@Override
			public byte[] apply(final String module, final byte[] body) {
				throw new IllegalStateException("boom");
			}
		});
		steps.add(new GmcpHook.Step() {
			@Override
			public byte[] apply(final String module, final byte[] body) {
				return utf8("{\"num\":2}");
			}
		});
		GmcpHook.Result result = GmcpHook.apply("Room.Info", "{\"num\":1}", steps);
		assertEquals("{\"num\":2}", result.body);
	}

	@Test
	public void stepsRunInOrderOnTheReplacedBody() throws Exception {
		List<GmcpHook.Step> steps = new ArrayList<GmcpHook.Step>();
		steps.add(new GmcpHook.Step() {
			@Override
			public byte[] apply(final String module, final byte[] body) {
				return utf8("{\"name\":\"Hall\"}");
			}
		});
		steps.add(new GmcpHook.Step() {
			@Override
			public byte[] apply(final String module, final byte[] body) {
				String text = new String(body);
				assertEquals("{\"name\":\"Hall\"}", text);
				return utf8(text.replace("Hall", "Temple"));
			}
		});
		assertEquals("{\"name\":\"Temple\"}",
				GmcpHook.apply("Room.Info", "{\"name\":\"Old\"}", steps).body);
	}

	@Test
	public void aDisabledPluginIsNotIncluded() {
		assertFalse(GmcpHook.include(false, true));
		assertFalse(GmcpHook.include(true, false));
		assertTrue(GmcpHook.include(true, true));
	}

	@Test
	public void aDroppedBodyDoesNotReachTheMapperOrTriggerStandIn() throws Exception {
		GmcpHook.Result result = GmcpHook.apply("Room.Info", "{\"num\":1}",
				Collections.singletonList(new GmcpHook.Step() {
					@Override
					public byte[] apply(final String module, final byte[] body) {
						return new byte[0];
					}
				}));
		List<String> mapper = new ArrayList<String>();
		List<String> trigger = new ArrayList<String>();
		if (!result.dropped) {
			mapper.add(result.body);
			trigger.add(result.body);
			fail("a drop must not be delivered");
		}
		assertTrue(mapper.isEmpty());
		assertTrue(trigger.isEmpty());
	}
}

package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/** {@code OnGmcp} sits in front of absorb. A drop never becomes the stored table. */
public class ProcessorGmcpHookTest {

	private final List<Integer> messages = new ArrayList<Integer>();

	private Processor processor() {
		return new Processor("UTF-8", messages);
	}

	private static byte[] gmcp(final String text) {
		byte[] payload = text.getBytes(StandardCharsets.UTF_8);
		byte[] out = new byte[payload.length + 5];
		out[0] = TC.IAC;
		out[1] = TC.SB;
		out[2] = TC.GMCP;
		System.arraycopy(payload, 0, out, 3, payload.length);
		out[out.length - 2] = TC.IAC;
		out[out.length - 1] = TC.SE;
		return out;
	}

	@Test
	public void aDroppedBodyIsNotAbsorbed() {
		Processor processor = processor();
		processor.setGmcpRewrite(new GmcpHook.Rewriter() {
			@Override
			public GmcpHook.Result rewrite(final String module, final String body) {
				return GmcpHook.drop();
			}
		});
		processor.rawProcess(gmcp("Room.Info {\"num\":1}"));
		assertNull(processor.getGMCPValue("Room.Info.num"));
	}

	@Test
	public void aReplacedBodyIsWhatGetsAbsorbed() {
		Processor processor = processor();
		processor.setGmcpRewrite(new GmcpHook.Rewriter() {
			@Override
			public GmcpHook.Result rewrite(final String module, final String body) {
				assertEquals("Room.Info", module);
				assertEquals("{\"num\":1}", body);
				try {
					return GmcpHook.apply(module, body, java.util.Collections.singletonList(
							new GmcpHook.Step() {
								@Override
								public byte[] apply(final String name, final byte[] text) {
									return "{\"num\":9}".getBytes(StandardCharsets.UTF_8);
								}
							}));
				} catch (java.io.UnsupportedEncodingException e) {
					throw new AssertionError(e);
				}
			}
		});
		processor.rawProcess(gmcp("Room.Info {\"num\":1}"));
		assertEquals(Integer.valueOf(9), processor.getGMCPValue("Room.Info.num"));
	}

	@Test
	public void aModuleOnlyMessageArrivesWithNoBody() {
		Processor processor = processor();
		final String[] seen = new String[1];
		final boolean[] called = new boolean[1];
		processor.setGmcpRewrite(new GmcpHook.Rewriter() {
			@Override
			public GmcpHook.Result rewrite(final String module, final String body) {
				called[0] = true;
				seen[0] = body;
				assertEquals("Core.Ping", module);
				return GmcpHook.leave(body);
			}
		});
		processor.rawProcess(gmcp("Core.Ping"));
		assertEquals(true, called[0]);
		assertNull(seen[0]);
	}
}

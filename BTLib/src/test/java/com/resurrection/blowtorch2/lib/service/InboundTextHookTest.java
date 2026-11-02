package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class InboundTextHookTest {

	@Test
	public void noFiltersReturnsTheSameArray() {
		byte[] text = new byte[] { 'a', '\n' };
		assertSame(text, InboundTextHook.apply(text, null));
		assertSame(text, InboundTextHook.apply(text, Collections.<InboundTextHook.Filter>emptyList()));
	}

	@Test
	public void nullTextStaysNull() {
		assertSame(null, InboundTextHook.apply(null, Collections.singletonList(replace("x"))));
	}

	@Test
	public void nullFromAFilterKeepsTheCurrentBytes() {
		byte[] text = new byte[] { 'a' };
		byte[] out = InboundTextHook.apply(text, Collections.singletonList(new InboundTextHook.Filter() {
			@Override
			public byte[] apply(final byte[] in) {
				return null;
			}
		}));
		assertSame(text, out);
	}

	@Test
	public void aStringReplacesAndAnEmptyArraySwallows() {
		byte[] out = InboundTextHook.apply(new byte[] { 'a', '\n' }, Arrays.asList(
				replace("kept"),
				new InboundTextHook.Filter() {
					@Override
					public byte[] apply(final byte[] in) {
						return new byte[0];
					}
				}));
		assertArrayEquals(new byte[0], out);
	}

	@Test
	public void filtersRunInOrderAndSeeThePreviousReplacement() {
		byte[] out = InboundTextHook.apply(new byte[] { 'a' }, Arrays.asList(
				replace("ab"),
				new InboundTextHook.Filter() {
					@Override
					public byte[] apply(final byte[] in) {
						byte[] next = new byte[in.length + 1];
						System.arraycopy(in, 0, next, 0, in.length);
						next[in.length] = 'c';
						return next;
					}
				}));
		assertArrayEquals(new byte[] { 'a', 'b', 'c' }, out);
	}

	@Test
	public void aThrowKeepsThatStepAndTheNextFilterStillRuns() {
		byte[] out = InboundTextHook.apply(new byte[] { 'a' }, Arrays.asList(
				new InboundTextHook.Filter() {
					@Override
					public byte[] apply(final byte[] in) {
						throw new IllegalStateException("boom");
					}
				},
				new InboundTextHook.Filter() {
					@Override
					public byte[] apply(final byte[] in) {
						byte[] next = new byte[in.length + 1];
						System.arraycopy(in, 0, next, 0, in.length);
						next[in.length] = 'b';
						return next;
					}
				}));
		assertArrayEquals(new byte[] { 'a', 'b' }, out);
	}

	@Test
	public void aNullFilterIsSkipped() {
		byte[] text = new byte[] { 'a' };
		byte[] out = InboundTextHook.apply(text, Arrays.asList(
				(InboundTextHook.Filter) null,
				replace("b")));
		assertArrayEquals(new byte[] { 'b' }, out);
	}

	private static InboundTextHook.Filter replace(final String s) {
		return new InboundTextHook.Filter() {
			@Override
			public byte[] apply(final byte[] in) {
				return s.getBytes();
			}
		};
	}
}

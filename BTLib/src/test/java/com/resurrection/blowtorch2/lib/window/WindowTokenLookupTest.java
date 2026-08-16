package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.WindowToken;

/**
 * Extra-text overlay bind must not call {@code getWindowTokens()} when
 * {@code mWindows} already holds that token. That refetch unparceled every
 * TextTree on the UI thread (ANR, 16 Aug 2026).
 *
 * <p>{@link WindowTokenLookup#findIn} is the old scan. The policy change is:
 * a local hit does not go to the binder; a miss still does, unless the caller
 * passes a null remote (activity leaving).
 */
public class WindowTokenLookupTest {

	private static WindowToken named(String name) {
		return new WindowToken(name, null, null, "world");
	}

	@Test
	public void findIn_matchesByName() {
		WindowToken chat = named("chat");
		WindowToken[] windows = new WindowToken[] { named("mainDisplay"), chat };
		assertSame(chat, WindowTokenLookup.findIn(windows, "chat"));
		assertNull(WindowTokenLookup.findIn(windows, "missing"));
		assertNull(WindowTokenLookup.findIn(windows, null));
		assertNull(WindowTokenLookup.findIn(null, "chat"));
	}

	@Test
	public void find_whenLocalHasName_doesNotCallRemote() throws Exception {
		WindowToken chat = named("chat");
		final boolean[] called = { false };
		WindowToken hit = WindowTokenLookup.find(
				new WindowToken[] { chat },
				"chat",
				new WindowTokenLookup.RemoteTokens() {
					@Override
					public WindowToken[] fetch() {
						called[0] = true;
						fail("binder fetch must not run when mWindows already has the token");
						return new WindowToken[0];
					}
				});
		assertSame(chat, hit);
		assertFalse(called[0]);
	}

	@Test
	public void find_whenLocalMiss_callsRemote() throws Exception {
		final WindowToken chat = named("chat");
		final boolean[] called = { false };
		WindowToken hit = WindowTokenLookup.find(
				new WindowToken[] { named("mainDisplay") },
				"chat",
				new WindowTokenLookup.RemoteTokens() {
					@Override
					public WindowToken[] fetch() {
						called[0] = true;
						return new WindowToken[] { chat };
					}
				});
		assertTrue(called[0]);
		assertSame(chat, hit);
	}

	@Test
	public void find_whenRemoteNull_doesNotFetchOnMiss() throws Exception {
		WindowToken hit = WindowTokenLookup.find(
				new WindowToken[] { named("mainDisplay") },
				"chat",
				null);
		assertNull(hit);
	}

	@Test
	public void find_whenLocalEmpty_callsRemote() throws Exception {
		final WindowToken chat = named("chat");
		final boolean[] called = { false };
		WindowToken hit = WindowTokenLookup.find(
				new WindowToken[0],
				"chat",
				new WindowTokenLookup.RemoteTokens() {
					@Override
					public WindowToken[] fetch() {
						called[0] = true;
						return new WindowToken[] { chat };
					}
				});
		assertTrue(called[0]);
		assertSame(chat, hit);
	}

	@Test
	public void find_whenLocalNull_callsRemote() throws Exception {
		final WindowToken chat = named("chat");
		WindowToken hit = WindowTokenLookup.find(
				null,
				"chat",
				new WindowTokenLookup.RemoteTokens() {
					@Override
					public WindowToken[] fetch() {
						return new WindowToken[] { chat };
					}
				});
		assertSame(chat, hit);
	}

	@Test
	public void hasTokens_requiresNonEmptyArray() {
		assertFalse(WindowTokenLookup.hasTokens(null));
		assertFalse(WindowTokenLookup.hasTokens(new WindowToken[0]));
		assertTrue(WindowTokenLookup.hasTokens(new WindowToken[] { named("chat") }));
	}
}

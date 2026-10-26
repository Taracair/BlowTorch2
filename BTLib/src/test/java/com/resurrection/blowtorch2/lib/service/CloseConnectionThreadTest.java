package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/**
 * closeConnection arrives on a binder thread. Shutting down there nulled
 * Connection.mSettings while the main looper was mid-send.
 */
public class CloseConnectionThreadTest {

	@Test
	public void shutdownIsPostedToTheServiceLooper() throws Exception {
		String src = read("BTLib/src/com/resurrection/blowtorch2/lib/service/ConnectionBinderFacade.java");
		int start = src.indexOf("public void closeConnection");
		int post = src.indexOf("service.mHandler.post(", start);
		int shutdown = src.indexOf("c.shutdown();", start);
		assertTrue(start >= 0 && post > start);
		assertTrue("shutdown must run inside the posted Runnable", shutdown > post);
	}

	private static String read(String relative) throws Exception {
		java.io.File[] candidates = new java.io.File[] {
				new java.io.File(relative),
				new java.io.File("../" + relative),
		};
		for (java.io.File f : candidates) {
			if (f.isFile()) {
				return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
			}
		}
		throw new AssertionError("missing " + relative);
	}
}

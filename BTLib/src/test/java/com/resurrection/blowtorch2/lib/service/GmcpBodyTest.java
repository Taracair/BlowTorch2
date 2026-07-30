package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;

/**
 * What shape a GMCP body is, which is what the decode path used to get wrong.
 *
 * <p>No test here asserts an exception <em>message</em>. The device links
 * Android's org.json and these tests link the reference implementation, and the
 * two word their parse failures differently. The classification is the contract;
 * the wording of {@link GmcpBody#error()} is only for a log line.
 */
public class GmcpBodyTest {

	/** The ordinary case, and the one that must not change. */
	@Test
	public void objectBodyParses() {
		GmcpBody b = GmcpBody.of("{\"hp\": 100, \"name\": \"bob\"}");
		assertEquals(GmcpBody.Shape.OBJECT, b.shape());
		assertNotNull(b.object());
		assertEquals(100, b.object().optInt("hp"));
		assertNull(b.array());
		assertNull(b.error());
	}

	/**
	 * The bug. This is the exact shape BlowTorch sends in
	 * <code>core.supports.set</code>, and eden-test sends it back at us.
	 */
	@Test
	public void arrayBodyParsesAndIsNotAnError() {
		GmcpBody b = GmcpBody.of("[\"Core 1\", \"Char 1\", \"mudstd.room 1\"]");
		assertEquals(GmcpBody.Shape.ARRAY, b.shape());
		assertNotNull(b.array());
		assertEquals(3, b.array().length());
		assertNull(b.error());
	}

	/** A supports list, read as the tokens a server meant to send. */
	@Test
	public void arrayBodyReadsAsStringList() {
		GmcpBody b = GmcpBody.of("[\"Core 1\",\"mudstd.combat 1\",\"\",\"WebView 1\"]");
		ArrayList<String> tokens = b.asStringList();
		assertEquals(3, tokens.size());
		assertEquals("Core 1", tokens.get(0));
		assertEquals("mudstd.combat 1", tokens.get(1));
		assertEquals("WebView 1", tokens.get(2));
	}

	/** Not an array: nothing to list, and no exception either. */
	@Test
	public void stringListIsEmptyForOtherShapes() {
		assertTrue(GmcpBody.of("{\"a\":1}").asStringList().isEmpty());
		assertTrue(GmcpBody.of(null).asStringList().isEmpty());
		assertTrue(GmcpBody.of("nonsense").asStringList().isEmpty());
	}

	/** A packet that carried nothing after its module name. */
	@Test
	public void absentBodyIsAbsent() {
		for (String empty : new String[] {null, "", "   ", "\t"}) {
			GmcpBody b = GmcpBody.of(empty);
			assertEquals("expected ABSENT for [" + empty + "]",
					GmcpBody.Shape.ABSENT, b.shape());
			assertEquals("{}", b.json());
			assertNull(b.error());
		}
	}

	/** Legal JSON, nothing to absorb, not worth a red line. */
	@Test
	public void scalarBodiesAreLegalJson() {
		String[] scalars = {
			"\"\"", "\"hello\"", "\"say \\\"hi\\\"\"",
			"0", "5", "-2", "3.25", "-2.5e3", "1E+10", "1e-10",
			"true", "false", "null",
		};
		for (String scalar : scalars) {
			GmcpBody b = GmcpBody.of(scalar);
			assertEquals("expected SCALAR for [" + scalar + "]",
					GmcpBody.Shape.SCALAR, b.shape());
			assertNull(b.error());
			assertEquals(scalar, b.json());
		}
	}

	/**
	 * The error path still exists. Narrowing what counts as an error is the fix;
	 * removing the error would only move the symptom away from the cause.
	 */
	@Test
	public void garbageIsStillMalformed() {
		String[] garbage = {
			"()", "hello", "{\"a\":", "[1,2", "\"unterminated",
			"\"a\" \"b\"", "007", "1.", ".5", "+1", "1e", "NULL", "True",
		};
		// Not listed: "{} junk". Both org.json implementations stop reading at the
		// closing brace and ignore the rest, so that has always been accepted as
		// an object and this change does not tighten it.
		for (String bad : garbage) {
			GmcpBody b = GmcpBody.of(bad);
			assertEquals("expected MALFORMED for [" + bad + "]",
					GmcpBody.Shape.MALFORMED, b.shape());
			assertNotNull("a malformed body needs something to log", b.error());
			assertEquals("a malformed body is passed on exactly as it arrived",
					bad, b.json());
		}
	}

	/**
	 * What each consumer gets. These four answers are what the old inline code
	 * produced, so extra text slots see no change on the shapes they already saw.
	 */
	@Test
	public void jsonTextMatchesWhatConsumersUsedToGet() {
		assertEquals("{\"a\":1}", GmcpBody.of("{\"a\":1}").json());
		assertEquals("[1,2]", GmcpBody.of("[1, 2]").json());
		assertEquals("{}", GmcpBody.of(null).json());
		assertEquals("()", GmcpBody.of("()").json());
	}

	/** Whitespace around a body is not part of it. */
	@Test
	public void surroundingWhitespaceIsIgnored() {
		GmcpBody b = GmcpBody.of("  {\"a\":1}\n");
		assertEquals(GmcpBody.Shape.OBJECT, b.shape());
		assertEquals("{\"a\":1}", b.raw());
	}
}

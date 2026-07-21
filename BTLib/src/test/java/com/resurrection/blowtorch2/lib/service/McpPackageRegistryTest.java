package com.resurrection.blowtorch2.lib.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Characterization tests for MCP package string parse / enable leaf logic.
 */
public class McpPackageRegistryTest {

	@Test
	public void defaultEnablesNegotiateAndNativePackages() {
		McpPackageRegistry reg = new McpPackageRegistry();
		assertTrue(reg.isEnabled("mcp-negotiate"));
		assertTrue(reg.isEnabled("dns-org-hellmoo-status"));
		assertTrue(reg.isEnabled("dns-com-awns-ping"));
	}

	@Test
	public void parseQuotedPackagesWithVersionRange() {
		McpPackageRegistry reg = McpPackageRegistry.fromPackagesOption(
				"\"mcp-negotiate 1.0 2.0\", \"dns-com-awns-ping 1.0\"");
		assertTrue(reg.isEnabled("mcp-negotiate"));
		assertTrue(reg.isEnabled("dns-com-awns-ping"));
		assertFalse(reg.isEnabled("dns-org-hellmoo-status"));
		McpPackageRegistry.PackageInfo neg = reg.get("mcp-negotiate");
		assertNotNull(neg);
		assertEquals("1.0", neg.minVersion);
		assertEquals("2.0", neg.maxVersion);
	}

	@Test
	public void toPackagesStringRoundTrip() {
		McpPackageRegistry reg = McpPackageRegistry.fromPackagesOption(
				"\"mcp-negotiate 1.0 2.0\", \"dns-com-awns-ping 1.0\"");
		String packed = reg.toPackagesString();
		assertTrue(packed.contains("mcp-negotiate"));
		assertTrue(packed.contains("dns-com-awns-ping"));
		McpPackageRegistry again = McpPackageRegistry.fromPackagesOption(packed);
		assertTrue(again.isEnabled("dns-com-awns-ping"));
		assertTrue(again.isEnabled("mcp-negotiate"));
	}

	@Test
	public void disableCannotRemoveNegotiate() {
		McpPackageRegistry reg = new McpPackageRegistry();
		reg.setEnabled("dns-com-awns-ping", false);
		assertFalse(reg.isEnabled("dns-com-awns-ping"));
		reg.setEnabled("mcp-negotiate", false);
		assertTrue(reg.isEnabled("mcp-negotiate"));
	}

	@Test
	public void noteSeenAndStatus() {
		McpPackageRegistry reg = new McpPackageRegistry();
		reg.noteSeen("dns-com-example-custom");
		assertTrue(reg.seenPackages().contains("dns-com-example-custom"));
		assertTrue(reg.statusLine().contains("seen="));
	}

	@Test
	public void emptyStringFallsBackToDefaults() {
		McpPackageRegistry reg = McpPackageRegistry.fromPackagesOption("");
		assertTrue(reg.isEnabled("mcp-negotiate"));
		assertFalse(reg.nativePackages().isEmpty());
	}
}

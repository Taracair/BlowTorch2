package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AlertGroupSummaryPolicyTest {

	private static final int SUMMARY_ID = StellarService.ALERT_GROUP_SUMMARY_ID;
	private static final String GROUP = StellarService.NOTIFICATION_GROUP;

	@Test
	public void emptyShadeCancelsSummary() {
		assertFalse(AlertGroupSummaryPolicy.shouldPostSummary(0));
	}

	@Test
	public void oneChildKeepsSummary() {
		assertTrue(AlertGroupSummaryPolicy.shouldPostSummary(1));
	}

	@Test
	public void countSkipsSummaryAndUngrouped() {
		int[] ids = new int[] { SUMMARY_ID, 101, 1, 202 };
		String[] groups = new String[] { GROUP, GROUP, null, "other.group" };
		assertEquals(1, AlertGroupSummaryPolicy.countAlertChildren(
				ids, groups, SUMMARY_ID, GROUP));
	}

	@Test
	public void countNullInputsIsZero() {
		assertEquals(0, AlertGroupSummaryPolicy.countAlertChildren(
				null, new String[] { GROUP }, SUMMARY_ID, GROUP));
		assertEquals(0, AlertGroupSummaryPolicy.countAlertChildren(
				new int[] { 101 }, null, SUMMARY_ID, GROUP));
		assertEquals(0, AlertGroupSummaryPolicy.countAlertChildren(
				new int[] { 101 }, new String[] { GROUP }, SUMMARY_ID, null));
	}

	@Test
	public void sessionTapWhenConnected() {
		assertTrue(AlertGroupSummaryPolicy.useSessionTap(true));
		assertFalse(AlertGroupSummaryPolicy.useSessionTap(false));
	}
}

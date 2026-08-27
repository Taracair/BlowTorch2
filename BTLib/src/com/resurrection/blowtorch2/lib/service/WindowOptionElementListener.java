/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

import org.xml.sax.Attributes;

import android.sax.TextElementListener;

import com.resurrection.blowtorch2.lib.window.TextTree;

/** Custom TextElementListener object used by the settings inflating routine to inflate window option settings from the SAX parser. */
public class WindowOptionElementListener implements TextElementListener {

	/** The current working window to put inflated settings into. */
	private WindowToken mCurrentWindow = null;
	/** The current option key found by the SAX parser. */
	private String mCurrentKey = "";
	
	/** Generic constructor.
	 * 
	 * @param w The window token object to put settings into.
	 */
	public WindowOptionElementListener(final WindowToken w) {
		mCurrentWindow = w;
	}
	
	/** Implementation of the TextElementListener.Start routine.
	 * 
	 * @param a The attributes associated with this tag.
	 */
	public final void start(final Attributes a) {
		if (a.getValue("", "key") != null) {
			mCurrentKey = a.getValue("", "key");
		}
	}

	/** Impelmentation of the TextElementListener.end(...) routine.
	 * 
	 * @param body The text between the tag start and tag end.
	 */
	public final void end(final String body) {
		String value = body;
		if ("buffer_size".equals(mCurrentKey) && body != null) {
			try {
				value = Integer.toString(TextTree.clampMaxLines(
						Integer.parseInt(body.trim())));
			} catch (NumberFormatException ignored) {
				value = body;
			}
		}
		mCurrentWindow.getSettings().setOption(mCurrentKey, value);
	}

}

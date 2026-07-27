package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.resurrection.blowtorch2.lib.service.plugin.settings.ConnectionSetttingsParser;

import org.junit.Test;

/**
 * Both settings writers walk the option tree recursively, and since the window
 * group was nested under root and the extra text group under the window group,
 * each one reaches keys the other owns. Every foreign key used to cost an
 * IllegalArgumentException, a stack trace and a write to the error log file —
 * about 45 per save, which queued ahead of the button payload on the same
 * handler and was the visible delay when switching button sets.
 */
public class SettingsOptionKeyOwnershipTest {

	@Test
	public void windowKeysAreNotClaimedByTheConnectionWriter() {
		// Seen in the log storm, written inside <window> by WindowTokenParser.
		String[] windowKeys = { "word_wrap", "scroll_sensitivity", "font_size", "buffer_size",
				"top_padding", "newest_at_top", "ime_keep_text", "hyperlinks_enabled",
				"hyperlink_mode", "hyperlink_color", "color_option", "line_extra", "font_path",
				"tap_dismiss_keyboard" };
		for (String key : windowKeys) {
			assertTrue(key + " should be owned by WindowTokenParser",
					WindowTokenParser.isWindowOptionKey(key));
			assertFalse(key + " must be skipped by the connection writer, not thrown on",
					ConnectionSetttingsParser.isConnectionOptionKey(key));
		}
	}

	@Test
	public void extraTextKeysAreNotClaimedByTheWindowWriter() {
		String[] rootKeys = { "extra_text_windows", "extra_text_windows_enabled",
				"manage_extra_text_windows" };
		for (String key : rootKeys) {
			assertFalse(key + " must be skipped by the window writer, not thrown on",
					WindowTokenParser.isWindowOptionKey(key));
		}
	}

	@Test
	public void noKeyIsClaimedByBothWriters() {
		// A key owned by both would be written twice into different sections.
		for (WindowToken.OPTION_KEY k : WindowToken.OPTION_KEY.values()) {
			assertFalse("key claimed by both writers: " + k.name(),
					ConnectionSetttingsParser.isConnectionOptionKey(k.name()));
		}
	}

	@Test
	public void unknownKeysAreSkippedRatherThanThrown() {
		assertFalse(WindowTokenParser.isWindowOptionKey("not_a_real_key"));
		assertFalse(ConnectionSetttingsParser.isConnectionOptionKey("not_a_real_key"));
		assertFalse(WindowTokenParser.isWindowOptionKey(null));
		assertFalse(ConnectionSetttingsParser.isConnectionOptionKey(null));
	}
}

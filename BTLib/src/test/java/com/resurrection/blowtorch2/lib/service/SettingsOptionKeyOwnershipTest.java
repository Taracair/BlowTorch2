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
				"top_padding", "bottom_padding", "bottom_padding_keyboard",
				"newest_at_top", "dim_repeated_lines", "dim_repeated_window", "dim_repeated_strength",
				"scroll_dates", "scroll_dates_opacity",
				"ime_keep_text", "input_bar_show_edit", "input_bar_show_send",
				"hyperlinks_enabled", "osc8_links",
				"hyperlink_mode", "hyperlink_color", "hyperlink_bare_domains",
				"hyperlink_extra_tlds", "color_option", "line_extra", "font_path",
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
	public void gaugeWidgetKeysAreNotClaimedByTheWindowWriter() {
		String[] rootKeys = { "gauge_widgets", "gauge_widgets_enabled",
				"manage_gauge_widgets" };
		for (String key : rootKeys) {
			assertFalse(key + " must be skipped by the window writer, not thrown on",
					WindowTokenParser.isWindowOptionKey(key));
		}
		assertTrue("gauge_widgets must be persisted by the connection writer",
				ConnectionSetttingsParser.isConnectionOptionKey("gauge_widgets"));
		assertTrue("gauge_widgets_enabled must be persisted by the connection writer",
				ConnectionSetttingsParser.isConnectionOptionKey("gauge_widgets_enabled"));
	}

	@Test
	public void noKeyIsClaimedByBothWriters() {
		// A key owned by both would be written twice into different sections.
		for (WindowToken.OPTION_KEY k : WindowToken.OPTION_KEY.values()) {
			assertFalse("key claimed by both writers: " + k.name(),
					ConnectionSetttingsParser.isConnectionOptionKey(k.name()));
		}
	}

	/**
	 * The suggestion bar's place is written by the connection writer, and the
	 * boolean it replaced is not written at all any more. A key missing from
	 * OPTION_KEY is skipped as foreign and silently never saved — which is what
	 * input_history_size did for years.
	 */
	@Test
	public void theSuggestionBarPlaceIsWrittenAndTheOldSwitchIsNot() {
		assertTrue(ConnectionSetttingsParser.isConnectionOptionKey("word_complete_where"));
		assertFalse("the old boolean is read from old profiles, never written back",
				ConnectionSetttingsParser.isConnectionOptionKey(
						ConnectionSetttingsParser.LEGACY_OVERLAY_KEY));
	}

	@Test
	public void unknownKeysAreSkippedRatherThanThrown() {
		assertFalse(WindowTokenParser.isWindowOptionKey("not_a_real_key"));
		assertFalse(ConnectionSetttingsParser.isConnectionOptionKey("not_a_real_key"));
		assertFalse(WindowTokenParser.isWindowOptionKey(null));
		assertFalse(ConnectionSetttingsParser.isConnectionOptionKey(null));
	}

	@Test
	public void mxpKeysAreConnectionOwnedNotWindow() {
		String[] mxpKeys = { "use_mxp", "log_mxp", "mxp_feed" };
		for (String key : mxpKeys) {
			assertTrue(key + " must be persisted by the connection writer",
					ConnectionSetttingsParser.isConnectionOptionKey(key));
			assertFalse(key + " must not be claimed by the window writer",
					WindowTokenParser.isWindowOptionKey(key));
		}
	}

	@Test
	public void chatKeysAreConnectionOwnedNotWindow() {
		String[] chatKeys = { "chat_unread_dot", "chat_announce",
				"chat_announce_seconds", "chat_android_notify",
				"chat_max_messages" };
		for (String key : chatKeys) {
			assertTrue(key + " must be persisted by the connection writer",
					ConnectionSetttingsParser.isConnectionOptionKey(key));
			assertFalse(key + " must not be claimed by the window writer",
					WindowTokenParser.isWindowOptionKey(key));
		}
	}
}

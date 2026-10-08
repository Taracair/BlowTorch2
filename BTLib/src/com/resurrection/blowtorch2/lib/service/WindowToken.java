/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Set;

import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.ColorOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.FileOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.IntegerOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.ListOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsGroup;
import com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption;
import com.resurrection.blowtorch2.lib.service.LayoutGroup.LAYOUT_TYPE;
import com.resurrection.blowtorch2.lib.settings.HyperSettings;
import com.resurrection.blowtorch2.lib.window.TextTree;
import com.resurrection.blowtorch2.lib.window.LightPaper;
import com.resurrection.blowtorch2.lib.window.ScrollSensitivity;
import com.resurrection.blowtorch2.lib.window.RepeatedLineDimmer;
import com.resurrection.blowtorch2.lib.window.FontCatalog;
import com.resurrection.blowtorch2.lib.window.TimestampFormat;

import android.content.res.Configuration;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.ViewGroup.LayoutParams;
import android.widget.RelativeLayout;

/** The serializable data that constitutes a foreground miniwindow. */
public class WindowToken implements Parcelable {
	/** Default hyperlink color. */
	public static final int DEFAULT_HYPERLINK_COLOR = 0xFF66CCFF;
	/** Default hyperlink mode. */
	public static final int DEFAULT_HYPERLINK_MODE = 3;
	/** Default colorizing mode. */
	public static final int DEFAULT_COLOR_MODE = 0;
	/** .avoidbuttons letters — one character at a time past the button hole. */
	public static final int AVOID_BUTTONS_BREAK_LETTERS = 0;
	/** .avoidbuttons words — do not split a word across the button hole. */
	public static final int AVOID_BUTTONS_BREAK_WORDS = 1;
	/** Default break mode while text_avoid_buttons is on (letters). */
	public static final int DEFAULT_AVOID_BUTTONS_BREAK = AVOID_BUTTONS_BREAK_LETTERS;
	/** Default font size. */
	public static final int DEFAULT_FONT_SIZE = 20;
	/** Default .pick magnifier size (percent of the original circle). */
	public static final int DEFAULT_PICK_LOUPE_SIZE =
			com.resurrection.blowtorch2.lib.window.PrefixPickLoupe.DEFAULT_SIZE;
	/** Default .pick magnifier zoom (percent; 200 = 2×). */
	public static final int DEFAULT_PICK_LOUPE_ZOOM =
			com.resurrection.blowtorch2.lib.window.PrefixPickLoupe.DEFAULT_ZOOM;
	/** Default copy-widget magnifier size (percent of the original circle). */
	public static final int DEFAULT_COPY_LOUPE_SIZE =
			com.resurrection.blowtorch2.lib.window.PrefixPickLoupe.DEFAULT_SIZE;
	/** Default copy-widget magnifier zoom (percent; 200 = 2×). */
	public static final int DEFAULT_COPY_LOUPE_ZOOM =
			com.resurrection.blowtorch2.lib.window.PrefixPickLoupe.DEFAULT_ZOOM;
	/** Default opacity of the scroll-dates overlay (percent). Matches the first paint. */
	public static final int DEFAULT_SCROLL_DATES_OPACITY = 75;
	/** Floor so the date stays readable. Same idea as overflow-button opacity. */
	public static final int SCROLL_DATES_OPACITY_MIN = 15;

	/** Clamp a typed opacity to {@link #SCROLL_DATES_OPACITY_MIN}–100. */
	public static int clampScrollDatesOpacity(final int percent) {
		if (percent < SCROLL_DATES_OPACITY_MIN) {
			return SCROLL_DATES_OPACITY_MIN;
		}
		if (percent > 100) {
			return 100;
		}
		return percent;
	}
	/** Default line spacing extra size. */
	public static final int DEFAULT_LINE_EXTRA = 3;
	/** Default buffer size. */
	/** Default scrollback lines. Higher = more history in RAM; session .txt log covers longer runs. */
	public static final int DEFAULT_BUFFER_SIZE = 2000;
	/**
	 * How much raw text one window keeps, whatever its line count says.
	 *
	 * <p>Two jobs. It bounds the heap — measured (TextTreeFootprintTest), a raw
	 * byte of MUD text costs 30-45 bytes of ART heap once it is words, colour
	 * runs and list nodes, so 512 KB is roughly 20 MB per tree, held twice
	 * because the UI keeps its own copy of what the service has. And it keeps
	 * the token inside a binder transaction: {@link #writeToParcel} carries the
	 * whole buffer, and the ~1 MB transaction limit is a hard crash, not a
	 * truncation.
	 *
	 * <p>512 KB is about 6500 lines of ordinary 80-column text, so an ordinary
	 * world reaches the line cap first and this never bites. A world that sends
	 * 2000-character map dumps hits it instead — which is the point.
	 *
	 * <p><b>The 30-45x figure is a model, not a heap reading.</b> Confirm with
	 * {@code dumpsys meminfo} on a filled buffer before trusting it.
	 */
	public static final int BUFFER_BYTE_BUDGET = 512 * 1024;
	/** Default top text inset (pixels). */
	public static final int DEFAULT_TOP_PADDING = 0;
	/** Default bottom text inset (pixels). */
	public static final int DEFAULT_BOTTOM_PADDING = 0;
	/** Default extra bottom text inset while the soft keyboard is up (pixels). */
	public static final int DEFAULT_BOTTOM_PADDING_KEYBOARD = 0;
	/** Default scroll sensitivity: 100, where text follows the finger 1:1. */
	public static final int DEFAULT_SCROLL_SENSITIVITY = ScrollSensitivity.DEFAULT_PERCENT;
	/** Default font path (bundled asset). */
	public static final String DEFAULT_FONT_PATH = "fonts/DejaVuSansMono.ttf";
	/** Required field for the parcelable interface. */
	public static final Parcelable.Creator<WindowToken> CREATOR = new Parcelable.Creator<WindowToken>() {

		public WindowToken createFromParcel(final Parcel arg0) {
			return new WindowToken(arg0);
		}

		public WindowToken[] newArray(final int arg0) {
			return new WindowToken[arg0];
		}
	};
	/** Enumeration containing the possible settings keys for a window. */
	public enum OPTION_KEY {
		/** Hyperlinking on or off. */
		hyperlinks_enabled,
		/** OSC 8 hyperlinks (ESC ]8;params;URI). Independent of regex linkify. */
		osc8_links,
		/** Hyperlink style (colorize, no colorize, only colorize if bland). */
		hyperlink_mode,
		/** Color to make hyperlinks. */
		hyperlink_color,
		/** Match bare domains (example.com) in addition to http(s)/www. */
		hyperlink_bare_domains,
		/** Extra bare-domain TLDs (CSV), e.g. ai,to — short ones are off by default. */
		hyperlink_extra_tlds,
		/** Word wrapping on or off. */
		word_wrap,
		/** Dim long lines that match a recent one (same room look). Default off. */
		dim_repeated_lines,
		/** How many recent long lines the dimmer remembers. Default 12. */
		dim_repeated_window,
		/** How hard to dim a repeat (percent). Higher is darker. Default 50. */
		dim_repeated_strength,
		/** Light grey paper and darkened ink. Off by default. .light on|off */
		light_paper,
		/** 1 grey … 5 near-white. Default 2. .light 1–5 */
		light_paper_shade,
		/** Date overlay + position mark while scrolled into history. .when on|off */
		scroll_dates,
		/** Opacity of that date overlay, percent. Default 75. .when opacity N */
		scroll_dates_opacity,
		/** Per-line arrival time on the right of the game. .timestamp on|off */
		line_stamps,
		/** Prefix that stamp on the left of each session-log line. .timestamp log */
		line_stamps_log,
		/** Hour/minute/second/month/year bits. Default hour+minute. */
		line_stamps_fields,
		/** Text canvas width as a percent of the screen; over 100 scrolls sideways. */
		text_canvas_width,
		/** Wrap game text around floating buttons instead of drawing under them. */
		text_avoid_buttons,
		/** While avoiding buttons: break letters (default) or whole words. */
		text_avoid_buttons_break,
		/** After a send, jump the main window to the live edge. Default on. */
		jump_on_send,
		/** Newest game lines at the top of the window (older below). */
		newest_at_top,
		/** Top inset for game text (pixels); extra to the camera-cutout options. */
		top_padding,
		/** Bottom inset for game text (pixels); always applied. */
		bottom_padding,
		/** Further bottom inset while the soft keyboard is up (pixels). */
		bottom_padding_keyboard,
		/** Soft keyboard lifts input only; game text stays put. */
		ime_keep_text,
		/** Keep game chrome out of the camera hole in portrait. Default on. */
		cutout_portrait,
		/** Keep game chrome out of the camera hole in landscape. Default on. */
		cutout_landscape,
		/** Show the Edit button on the input bar (main window chrome). */
		input_bar_show_edit,
		/** Show the Send button on the input bar (main window chrome). */
		input_bar_show_send,
		/** Landscape Edit strip uses two rows. Off: one full-width row. */
		input_edit_tools_two_rows,
		/** Coast after lift using swipe speed (OverScroller), like a web page. */
		android_fling,
		/** How far the text travels per unit of finger travel when scrolling. */
		scroll_sensitivity,
		/** Color mode (see color debug option). */
		color_option,
		/** Font size option. */
		font_size,
		/** Line extra option. */
		line_extra,
		/** Buffer size. */
		buffer_size,
		/** Path to the font to use. */
		font_path,
		/** Tap empty game area to dismiss soft keyboard. */
		tap_dismiss_keyboard,
		/** .pick magnifier diameter, percent of the original circle. Default 118. */
		pick_loupe_size,
		/** .pick magnifier zoom, percent (200 = 2×). */
		pick_loupe_zoom,
		/** Copy-widget magnifier diameter, percent of the original circle. Default 118. */
		copy_loupe_size,
		/** Copy-widget magnifier zoom, percent (200 = 2×). */
		copy_loupe_zoom
	}
	/** Hyperlink decoration off. */
	private static final int HYPERLINK_OFF = 0;
	/** Hyperlink highlight. */
	private static final int HYPERLINK_HIGHLIGHT = 1;
	/** Hyperlink colorize. */
	private static final int HYPERLINK_HIGHLIGHT_COLOR = 2;
	/** Hyperlink colorize if bland. */
	private static final int HYPERLINK_HIGHLIGHT_IF_BLAND = 3;
	/** Hyperlink background colorize. */
	private static final int HYPERLINK_BACKGROUND = 4;
	/** Small layout int for parcel indicator. */
	private static final int LAYOUT_SMALL = 0;
	/** Normal layout int for parcel indicator. */
	private static final int LAYOUT_NORMAL = 1;
	/** Large layout int for parcel indicator. */
	private static final int LAYOUT_LARGE = 2;
	/** XLarge layout int for parcel indicator. */
	private static final int LAYOUT_XLARGE = 3;
	
	/** The layout map for this window, maps layout type to the layoutgroup object. */
	private HashMap<LayoutGroup.LAYOUT_TYPE, LayoutGroup> mLayouts;
	/** The name of this window. */
	private String mName;
	/** Weather or not to buffer incoming text. */
	private boolean mBufferText = false;
	/** The id of this window. */
	private int mId;
	/** The text buffer for this window. */
	private TextTree mBuffer;
	/** The connection display name that owns this window. */
	private String mDisplayHost;
	/** The settings for this window. */
	private SettingsGroup mSettings = null;
	/** The name of the script to execute when this window is initialized. */
	private String mScriptName;
	/** The name of the plugin that owns this window. */
	private String mPluginName;
	
	/** Generic constructor. */
	public WindowToken() {
		mName = "";
		mDisplayHost = "";
		mLayouts = new HashMap<LayoutGroup.LAYOUT_TYPE, LayoutGroup>(0);
		initSettings();
	}
	
	/** A more functional constructor.
	 * 
	 * @param name The name of this window.
	 * @param scriptName The script body to execute (can be null).
	 * @param pluginName The plugin name that owns this window.
	 * @param displayHost The display name of the connection that owns this window.
	 */
	public WindowToken(final String name, final String scriptName, final String pluginName, final String displayHost) {
		//type = TYPE.SCRIPT;
		this.mName = name;
		this.mDisplayHost = displayHost;

		this.mScriptName = scriptName;
		this.mPluginName = pluginName;
		mBuffer = new TextTree();
		mBuffer.setMaxBytes(BUFFER_BYTE_BUDGET);
		mLayouts = new HashMap<LayoutGroup.LAYOUT_TYPE, LayoutGroup>(0);
		
		initSettings();
	}
	
	/** Parcellable constructor. Constructs from a parcel.
	 * 
	 * @param p The incoming parcel object.
	 */
	public WindowToken(final Parcel p) {
		//owner = p.readString();
		mName = p.readString();
		//if(name.equals("chats")) {
		//	long ssfd = System.currentTimeMillis();
		//	ssfd = ssfd +10;
		//}
		mDisplayHost = p.readString();
		mId = p.readInt();
		//Log.e("TOKEN","PARCEL: READING WINDOW WITH ID:" + id);
		mLayouts = new HashMap<LayoutGroup.LAYOUT_TYPE, LayoutGroup>();
		//layout paramters.
		int numLayoutGroups = p.readInt();
		for (int i = 0; i < numLayoutGroups; i++) {
			LayoutGroup g = new LayoutGroup();
			int layoutType = p.readInt();
			switch(layoutType) {
			case LAYOUT_SMALL:
				g.setType(LAYOUT_TYPE.small);
				break;
			case LAYOUT_NORMAL:
				g.setType(LAYOUT_TYPE.normal);
				break;
			case LAYOUT_LARGE:
				g.setType(LAYOUT_TYPE.large);
				break;
			case LAYOUT_XLARGE:
				g.setType(LAYOUT_TYPE.xlarge);
				break;
			default:
				break;
			}
			
			int pWidth = p.readInt();
			int pHeight = p.readInt();
			int pMarginTop = p.readInt();
			int pMarginLeft = p.readInt();
			int pMarginRight = p.readInt();
			int pMarginBottom = p.readInt();
	
			RelativeLayout.LayoutParams portraitParams = g.getPortraitParams();
			portraitParams.height = pHeight;
			portraitParams.width = pWidth;
			portraitParams.setMargins(pMarginLeft, pMarginTop, pMarginRight, pMarginBottom);
			//int numrules = p.readInt();
			boolean done = false;
			while (!done) {
				int rule = p.readInt();
				if (rule > -1) {
					int option = p.readInt();
					portraitParams.addRule(rule, option);
				} else {
					done = true;
				}
			}
			
			
			int lWidth = p.readInt();
			int lHeight = p.readInt();
			int lMarginTop = p.readInt();
			int lMarginLeft = p.readInt();
			int lMarginRight = p.readInt();
			int lMarginBottom = p.readInt();
			RelativeLayout.LayoutParams landscapeParams = g.getLandscapeParams();
			landscapeParams.height = lHeight;
			landscapeParams.width = lWidth;
			landscapeParams.setMargins(lMarginLeft, lMarginTop, lMarginRight, lMarginBottom);
			done = false;
			while (!done) {
				int rule = p.readInt();
				if (rule > -1) {
					int option = p.readInt();
					landscapeParams.addRule(rule, option);
				} else {
					done = true;
				}
			}
			
			mLayouts.put(g.getType(), g);
		}
		setBufferText((p.readInt() == 0) ? false : true);
		int script = p.readInt();
		if (script != 1) {
			mScriptName = null;
			mPluginName = null;
		} else {
			mScriptName = p.readString();
			mPluginName = p.readString();
		}
		final int bufferLines = p.readInt();
		final int bufferBytes = p.readInt();
		byte[] buf = p.createByteArray();
		mBuffer = new TextTree();
		//type = TYPE.SCRIPT;

		// The caps go on before the text does. This tree is brand new, so without
		// them it would prune at the 2000-line default while reading the bytes
		// back, and a player who asked for more would lose the difference every
		// time a token crossed the binder — before any setting reached it.
		if (bufferLines > 0) {
			mBuffer.setMaxLines(bufferLines);
		}
		mBuffer.setMaxBytes(bufferBytes);

		try {
			mBuffer.addBytesImpl(buf);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		mSettings = p.readParcelable(com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsGroup.class.getClassLoader());
		
	}
	
	/** The real initialization routine. */
	private void initSettings() {
		SettingsGroup window = new SettingsGroup();
		window.setTitle("Window");
		window.setDescription("Font, colours, wrap, padding, links, and the menu button.");
		window.setKey("window_group");

		SettingsGroup text = new SettingsGroup();
		text.setTitle("Text");
		text.setDescription("Font, colours, wrap, and how lines are drawn.");
		text.setKey("window_text_group");

		SettingsGroup layout = new SettingsGroup();
		layout.setTitle("Layout");
		layout.setDescription("Padding, scrolling, the keyboard, and the menu button.");
		layout.setKey("window_layout_group");

		SettingsGroup hyperlinks = new SettingsGroup();
		hyperlinks.setTitle("Links");
		hyperlinks.setDescription("Web addresses in the text, and words the game marks tappable.");
		hyperlinks.setKey("hyperlinks_options");

		SettingsGroup inputBar = new SettingsGroup();
		inputBar.setTitle("Input bar");
		inputBar.setDescription("The Edit and Send buttons on the input bar.");
		inputBar.setKey("window_input_bar_group");
		
		BooleanOption hyperlinksEnabled = new BooleanOption();
		hyperlinksEnabled.setTitle("Enable Hyperlinks?");
		hyperlinksEnabled.setDescription("Make http(s)://, www., and (when enabled below) bare domain URLs clickable.");
		hyperlinksEnabled.setKey("hyperlinks_enabled");
		hyperlinksEnabled.setValue(true);

		ListOption hyperlinkMode = new ListOption();
		hyperlinkMode.setTitle("Hyperlink Mode");
		hyperlinkMode.setDescription("Underline a web address, colour it, or both.");
		hyperlinkMode.setKey("hyperlink_mode");
		hyperlinkMode.addItem("None");
		hyperlinkMode.addItem("Underline");
		hyperlinkMode.addItem("Underline with specified Color");
		hyperlinkMode.addItem("Underline and Colorize, only if no ANSI color is specified");
		hyperlinkMode.addItem("Background highlight with specified color");
		hyperlinkMode.setValue(Integer.valueOf(DEFAULT_HYPERLINK_MODE));
		
		ColorOption hyperlinkColor = new ColorOption();
		hyperlinkColor.setTitle("Hyperlink Color");
		hyperlinkColor.setDescription("The color the hyperlink will be colorized with.");
		hyperlinkColor.setKey("hyperlink_color");
		hyperlinkColor.setValue(Integer.valueOf(DEFAULT_HYPERLINK_COLOR));

		BooleanOption hyperlinkBare = new BooleanOption();
		hyperlinkBare.setTitle("Link bare domains?");
		hyperlinkBare.setDescription("Also match hostnames like example.com without http; short endings such as to, ch, and ai stay off so ordinary text is not full of false links.");
		hyperlinkBare.setKey("hyperlink_bare_domains");
		hyperlinkBare.setValue(true);

		StringOption hyperlinkExtraTlds = new StringOption();
		hyperlinkExtraTlds.setTitle("Extra TLDs (CSV)");
		hyperlinkExtraTlds.setDescription("Extra bare-domain endings, comma-separated and without dots, added to the built-in list.");
		hyperlinkExtraTlds.setKey("hyperlink_extra_tlds");
		hyperlinkExtraTlds.setValue("");

		BooleanOption osc8Links = new BooleanOption();
		osc8Links.setTitle("Use OSC 8?");
		osc8Links.setDescription("Make words the game marks tappable, so a tap can send a command, fill the input bar, or open a web page.");
		osc8Links.setKey("osc8_links");
		osc8Links.setValue(true);
		
		
		BooleanOption wordWrap = new BooleanOption();
		wordWrap.setTitle("Word Wrap?");
		wordWrap.setDescription("Break a long line at a space; off, the line runs off the side of the screen.");
		wordWrap.setKey("word_wrap");
		wordWrap.setValue(true);

		BooleanOption dimRepeatedLines = new BooleanOption();
		dimRepeatedLines.setTitle("Dim repeated lines?");
		dimRepeatedLines.setDescription("When a long line comes back identical, paint it dimmer so what changed stands out.");
		dimRepeatedLines.setKey("dim_repeated_lines");
		dimRepeatedLines.setValue(false);

		IntegerOption dimRepeatedWindow = new IntegerOption();
		dimRepeatedWindow.setTitle("Remember how many lines?");
		dimRepeatedWindow.setDescription("How many recent long lines stay in memory before an old repeated line is bright again.");
		dimRepeatedWindow.setKey("dim_repeated_window");
		dimRepeatedWindow.setValue(RepeatedLineDimmer.DEFAULT_WINDOW);

		IntegerOption dimRepeatedStrength = new IntegerOption();
		dimRepeatedStrength.setTitle("Dim strength (%)");
		dimRepeatedStrength.setDescription("How hard to dim a repeated line, from 10 to 90, where 50 is half as bright.");
		dimRepeatedStrength.setKey("dim_repeated_strength");
		dimRepeatedStrength.setValue(RepeatedLineDimmer.DEFAULT_STRENGTH);

		BooleanOption lightPaper = new BooleanOption();
		lightPaper.setTitle("Light theme?");
		lightPaper.setDescription("Light paper and dark ink, while colours the game sends stay readable.");
		lightPaper.setKey("light_paper");
		lightPaper.setValue(false);

		IntegerOption lightPaperShade = new IntegerOption();
		lightPaperShade.setTitle("Light paper shade (1–5)");
		lightPaperShade.setDescription("How light the paper is while Light theme is on, from 1 grey to 5 near-white.");
		lightPaperShade.setKey("light_paper_shade");
		lightPaperShade.setValue(Integer.valueOf(LightPaper.SHADE_DEFAULT));

		BooleanOption scrollDates = new BooleanOption();
		scrollDates.setTitle("Scroll dates?");
		scrollDates.setDescription("While scrolled into history, show when the text on screen arrived, and a mark for where you are in the buffer.");
		scrollDates.setKey("scroll_dates");
		scrollDates.setValue(false);

		IntegerOption scrollDatesOpacity = new IntegerOption();
		scrollDatesOpacity.setTitle("Scroll date opacity (%)");
		scrollDatesOpacity.setDescription("How solid the day, time, and position mark are while scrolled into history ("
				+ SCROLL_DATES_OPACITY_MIN + "–100).");
		scrollDatesOpacity.setKey("scroll_dates_opacity");
		scrollDatesOpacity.setValue(DEFAULT_SCROLL_DATES_OPACITY);

		BooleanOption lineStamps = new BooleanOption();
		lineStamps.setTitle("Line timestamps?");
		lineStamps.setDescription("Show when each line arrived, on the right, without changing wrap, triggers, or copy.");
		lineStamps.setKey("line_stamps");
		lineStamps.setValue(false);

		BooleanOption lineStampsLog = new BooleanOption();
		lineStampsLog.setTitle("Timestamps in session log?");
		lineStampsLog.setDescription("Prefix the same stamp on the left of each logged incoming line.");
		lineStampsLog.setKey("line_stamps_log");
		lineStampsLog.setValue(false);

		IntegerOption lineStampsFields = new IntegerOption();
		lineStampsFields.setTitle("Timestamp parts");
		lineStampsFields.setDescription("Which parts of the date and time each stamp shows.");
		lineStampsFields.setKey("line_stamps_fields");
		lineStampsFields.setValue(Integer.valueOf(TimestampFormat.DEFAULT));

		IntegerOption canvasWidth = new IntegerOption();
		canvasWidth.setTitle("Text width (% of screen)");
		canvasWidth.setDescription("Give the text more room than the screen, then drag sideways to read the rest; 100 fits the screen.");
		canvasWidth.setKey("text_canvas_width");
		canvasWidth.setValue(100);

		BooleanOption avoidButtons = new BooleanOption();
		avoidButtons.setTitle("Text avoids on-screen buttons?");
		avoidButtons.setDescription("Game text wraps around on-screen buttons instead of drawing under them.");
		avoidButtons.setKey("text_avoid_buttons");
		avoidButtons.setValue(false);

		ListOption avoidButtonsBreak = new ListOption();
		avoidButtonsBreak.setTitle("Avoid-buttons break");
		avoidButtonsBreak.setDescription("While text avoids buttons, move one character at a time past the hole, or keep whole words on one side.");
		avoidButtonsBreak.setKey("text_avoid_buttons_break");
		avoidButtonsBreak.addItem("Letters");
		avoidButtonsBreak.addItem("Words");
		avoidButtonsBreak.setValue(Integer.valueOf(DEFAULT_AVOID_BUTTONS_BREAK));

		BooleanOption jumpOnSend = new BooleanOption();
		jumpOnSend.setTitle("Jump to the live edge when you send?");
		jumpOnSend.setDescription("After you send a line, scroll to the newest text.");
		jumpOnSend.setKey("jump_on_send");
		jumpOnSend.setValue(true);

		// Tappable words are a trigger action; old tappable_* keys in XML are dropped.
		BooleanOption newestAtTop = new BooleanOption();
		newestAtTop.setTitle("Newest text at top?");
		newestAtTop.setDescription("Put fresh output at the top, which reverses line order so maps and ASCII art appear upside down.");
		newestAtTop.setKey("newest_at_top");
		newestAtTop.setValue(false);

		IntegerOption topPadding = new IntegerOption();
		topPadding.setTitle("Top padding (px)");
		topPadding.setDescription("Extra empty space, in pixels, above the game text, on top of the camera-cutout inset.");
		topPadding.setKey("top_padding");
		topPadding.setValue(DEFAULT_TOP_PADDING);

		IntegerOption bottomPadding = new IntegerOption();
		bottomPadding.setTitle("Bottom padding (px)");
		bottomPadding.setDescription("Extra empty space, in pixels, below the game text, so the last line stays clear of the input bar.");
		bottomPadding.setKey("bottom_padding");
		bottomPadding.setValue(DEFAULT_BOTTOM_PADDING);

		IntegerOption bottomPaddingKeyboard = new IntegerOption();
		bottomPaddingKeyboard.setTitle("Bottom padding with keyboard (px)");
		bottomPaddingKeyboard.setDescription("Further empty space, in pixels, below the game text while the soft keyboard is open.");
		bottomPaddingKeyboard.setKey("bottom_padding_keyboard");
		bottomPaddingKeyboard.setValue(DEFAULT_BOTTOM_PADDING_KEYBOARD);

		BooleanOption imeKeepText = new BooleanOption();
		imeKeepText.setTitle("Keep text still with keyboard?");
		imeKeepText.setDescription("Opening the soft keyboard lifts only the input bar, and the game text stays put.");
		imeKeepText.setKey("ime_keep_text");
		imeKeepText.setValue(false);

		BooleanOption cutoutPortrait = new BooleanOption();
		cutoutPortrait.setTitle("Avoid camera cutout (portrait)?");
		cutoutPortrait.setDescription("Keep game text out of the camera hole while the phone is upright.");
		cutoutPortrait.setKey("cutout_portrait");
		cutoutPortrait.setValue(true);

		BooleanOption cutoutLandscape = new BooleanOption();
		cutoutLandscape.setTitle("Avoid camera cutout (landscape)?");
		cutoutLandscape.setDescription("Keep game text out of the camera hole while the phone is landscape.");
		cutoutLandscape.setKey("cutout_landscape");
		cutoutLandscape.setValue(true);

		BooleanOption showInputEdit = new BooleanOption();
		showInputEdit.setTitle("Show Edit button?");
		showInputEdit.setDescription("Puts an Edit button on the input bar.");
		showInputEdit.setKey("input_bar_show_edit");
		showInputEdit.setValue(true);

		BooleanOption showInputSend = new BooleanOption();
		showInputSend.setTitle("Show Send button?");
		showInputSend.setDescription("Puts a Send button on the input bar.");
		showInputSend.setKey("input_bar_show_send");
		showInputSend.setValue(true);

		BooleanOption editToolsTwoRows = new BooleanOption();
		editToolsTwoRows.setTitle("Edit strip: two rows in landscape?");
		editToolsTwoRows.setDescription("The Edit tools sit on two rows while the phone is landscape.");
		editToolsTwoRows.setKey("input_edit_tools_two_rows");
		editToolsTwoRows.setValue(false);

		BooleanOption androidFling = new BooleanOption();
		androidFling.setTitle("Android fling?");
		androidFling.setDescription("After you lift your finger, the text coasts with the speed of the swipe, and scroll sensitivity is off while this is on.");
		androidFling.setKey("android_fling");
		androidFling.setValue(false);

		ListOption scrollSensitivity = new ListOption();
		scrollSensitivity.setTitle("Scroll sensitivity");
		scrollSensitivity.setDescription("How far the text moves for a given swipe; 100% follows your finger, and this is off while Android fling is on.");
		scrollSensitivity.setKey("scroll_sensitivity");
		scrollSensitivity.setValue(Integer.valueOf(DEFAULT_SCROLL_SENSITIVITY));
		String[] scrollLabels = ScrollSensitivity.labels();
		for (int i = 0; i < scrollLabels.length; i++) {
			scrollSensitivity.addItem(scrollLabels[i]);
		}

		BooleanOption tapDismiss = new BooleanOption();
		tapDismiss.setTitle("Tap window hides keyboard?");
		tapDismiss.setDescription("A loose tap on the game text (not a button) dismisses the soft keyboard.");
		tapDismiss.setKey("tap_dismiss_keyboard");
		tapDismiss.setValue(true);
		
		ListOption colorOption = new ListOption();
		colorOption.setTitle("ANSI Color");
		colorOption.setDescription("Use the colours the game sends, turn them off, or show the raw codes in the text.");
		colorOption.setKey("color_option");
		colorOption.setValue(0);
		colorOption.addItem("Enabled");
		colorOption.addItem("Disabled");
		colorOption.addItem("Show and colorize codes");
		colorOption.addItem("Show codes, do not colorize");
		
		IntegerOption fontSize = new IntegerOption();
		fontSize.setTitle("Font Size");
		fontSize.setDescription("The height of a drawn character, in pixels (6–96).");
		fontSize.setKey("font_size");
		fontSize.setValue(DEFAULT_FONT_SIZE);
		
		IntegerOption lineExtra = new IntegerOption();
		lineExtra.setTitle("Line Spacing");
		lineExtra.setDescription("The extra space in between lines (in pixels)");
		lineExtra.setKey("line_extra");
		lineExtra.setValue(2);

		IntegerOption pickLoupeSize = new IntegerOption();
		pickLoupeSize.setTitle("Pick loupe size (%)");
		pickLoupeSize.setDescription("How big the pick magnifier is, from 50 to 200 percent.");
		pickLoupeSize.setKey("pick_loupe_size");
		pickLoupeSize.setValue(DEFAULT_PICK_LOUPE_SIZE);

		IntegerOption pickLoupeZoom = new IntegerOption();
		pickLoupeZoom.setTitle("Pick loupe zoom (%)");
		pickLoupeZoom.setDescription("How much the pick magnifier enlarges the game text, from 150 to 350 percent.");
		pickLoupeZoom.setKey("pick_loupe_zoom");
		pickLoupeZoom.setValue(DEFAULT_PICK_LOUPE_ZOOM);

		IntegerOption copyLoupeSize = new IntegerOption();
		copyLoupeSize.setTitle("Copy loupe size (%)");
		copyLoupeSize.setDescription("How big the two-finger copy magnifier is, from 50 to 200 percent.");
		copyLoupeSize.setKey("copy_loupe_size");
		copyLoupeSize.setValue(DEFAULT_COPY_LOUPE_SIZE);

		IntegerOption copyLoupeZoom = new IntegerOption();
		copyLoupeZoom.setTitle("Copy loupe zoom (%)");
		copyLoupeZoom.setDescription("How much the two-finger copy magnifier enlarges the game text, from 150 to 350 percent.");
		copyLoupeZoom.setKey("copy_loupe_zoom");
		copyLoupeZoom.setValue(DEFAULT_COPY_LOUPE_ZOOM);
		
		IntegerOption bufferSize = new IntegerOption();
		bufferSize.setTitle("Text Buffer Size");
		bufferSize.setDescription("Lines kept on screen for scrollback (100–20000), usually capped first by about 512 KB of text, so use the session log for weeks of history.");
		bufferSize.setKey("buffer_size");
		bufferSize.setValue(DEFAULT_BUFFER_SIZE);
		
		FileOption fontPath = new FileOption();
		fontPath.setTitle("Font");
		fontPath.setDescription("The typeface for the game window, from the bundled faces, a few system monospace files, or a font file you load.");
		fontPath.setKey("font_path");
		fontPath.setValue(DEFAULT_FONT_PATH);
		java.util.List<FontCatalog.Face> bundled = FontCatalog.bundledPickerFaces();
		for (int i = 0; i < bundled.size(); i++) {
			fontPath.addItem(bundled.get(i).path);
		}
		fontPath.addPath("BlowTorch/");
		fontPath.addPath("BlowTorch/fonts/");
		fontPath.addExtension(".ttf");
		fontPath.addExtension(".otf");
		
		hyperlinks.addOption(osc8Links);
		hyperlinks.addOption(hyperlinksEnabled);
		hyperlinks.addOption(hyperlinkMode);
		hyperlinks.addOption(hyperlinkColor);
		hyperlinks.addOption(hyperlinkBare);
		hyperlinks.addOption(hyperlinkExtraTlds);

		text.addOption(fontPath);
		text.addOption(fontSize);
		text.addOption(lineExtra);
		text.addOption(colorOption);
		text.addOption(wordWrap);
		text.addOption(bufferSize);
		text.addOption(lightPaper);
		text.addOption(lightPaperShade);
		text.addOption(dimRepeatedLines);
		text.addOption(dimRepeatedWindow);
		text.addOption(dimRepeatedStrength);
		text.addOption(lineStamps);
		text.addOption(lineStampsLog);
		text.addOption(lineStampsFields);
		text.addOption(canvasWidth);
		text.addOption(newestAtTop);
		text.addOption(avoidButtons);
		text.addOption(avoidButtonsBreak);
		text.addOption(pickLoupeSize);
		text.addOption(pickLoupeZoom);
		text.addOption(copyLoupeSize);
		text.addOption(copyLoupeZoom);

		layout.addOption(topPadding);
		layout.addOption(bottomPadding);
		layout.addOption(bottomPaddingKeyboard);
		layout.addOption(imeKeepText);
		layout.addOption(cutoutPortrait);
		layout.addOption(cutoutLandscape);
		layout.addOption(androidFling);
		layout.addOption(scrollSensitivity);
		layout.addOption(jumpOnSend);
		layout.addOption(tapDismiss);
		layout.addOption(scrollDates);
		layout.addOption(scrollDatesOpacity);

		inputBar.addOption(showInputEdit);
		inputBar.addOption(showInputSend);
		inputBar.addOption(editToolsTwoRows);

		window.addOption(text);
		window.addOption(layout);
		window.addOption(hyperlinks);
		window.addOption(inputBar);

		setSettings(window);
	}
	
	/** Shallow copy function.
	 * 
	 * @return A new WindowToken with the exact settings as this one.
	 */
	public final WindowToken copy() {
		WindowToken w = null;
		//if(this.scriptName == null) {
		//	w = new WindowToken(this.name,this.x,this.y,this.width,this.height);
		//} else {
		w = new WindowToken(this.mName, this.mScriptName, this.mPluginName, this.mDisplayHost);
		//}
		w.setSettings(this.getSettings());
		this.setSettings(null);
		this.initSettings();
		w.mId = this.mId;
		
		for (LayoutGroup g : this.mLayouts.values()) {
			//LayoutGroup g = layouts.get(i);
			LayoutGroup newg = new LayoutGroup();
			newg.setType(g.getType());
			RelativeLayout.LayoutParams pparams = g.getPortraitParams();
			RelativeLayout.LayoutParams newportraitparams = new RelativeLayout.LayoutParams(pparams.width, pparams.height);
			newportraitparams.setMargins(pparams.leftMargin, pparams.topMargin, pparams.rightMargin, pparams.bottomMargin);
			int[] rules = pparams.getRules();
			for (int j = 0; j < rules.length; j++) {
				if (rules[j] != 0) {
					newportraitparams.addRule(j, rules[j]);
				}
			}
			
			RelativeLayout.LayoutParams lparams = g.getLandscapeParams();
			RelativeLayout.LayoutParams newlandscapeparams = new RelativeLayout.LayoutParams(lparams.width, lparams.height);
			newportraitparams.setMargins(lparams.leftMargin, lparams.topMargin, lparams.rightMargin, lparams.bottomMargin);
			rules = null;
			rules = lparams.getRules();
			for (int j = 0; j < rules.length; j++) {
				if (rules[j] != 0) {
					newlandscapeparams.addRule(j, rules[j]);
				}
			}
			
			newg.setLandscapeParams(newlandscapeparams);
			newg.setPortraitParams(newportraitparams);
			
			w.mLayouts.put(newg.getType(), newg);
		}

		return w;
	}
	
	/** Setter for mName.
	 * 
	 * @param name The name to use.
	 */
	public final void setName(final String name) {
		this.mName = name;
	}

	/** Getter for mName.
	 * 
	 * @return mName
	 */
	public final String getName() {
		return mName;
	}
	
	/** Getter for mDisplayHost.
	 * 
	 * @return mDisplayHost
	 */
	public final String getDisplayHost() {
		return mDisplayHost;
	}
	
	/** Setter for mDisplayHost.
	 * 
	 * @param str The display host name to use.
	 */
	public final void setDisplayHost(final String str) {
		mDisplayHost = str;
	}

	/** Required method for the Parcellable interface. I think this needs to return a unique random int.
	 * 
	 * @return The integer description for the contents of this parcel.
	 */
	public final int describeContents() {
		return 0;
	}

	/** Implementation of the Parcelable writeToParcel method.
	 * 
	 * @param p The parcel to write to.
	 * @param arg1 The flags for the operation.
	 */
	public final void writeToParcel(final Parcel p, final int arg1) {
		p.writeString(mName);
		p.writeString(mDisplayHost);
		p.writeInt(mId);
		Set<LayoutGroup.LAYOUT_TYPE> keySet = mLayouts.keySet();
		p.writeInt(keySet.size());
		for (LayoutGroup g : mLayouts.values()) {
			switch(g.getType()) {
			case small:
				p.writeInt(LAYOUT_SMALL);
				break;
			case normal:
				p.writeInt(LAYOUT_NORMAL);
				break;
			case large:
				p.writeInt(LAYOUT_LARGE);
				break;
			case xlarge:
				p.writeInt(LAYOUT_XLARGE);
				break;
			default:
				break;
			}
			
			RelativeLayout.LayoutParams portraitParams = g.getPortraitParams();
			RelativeLayout.LayoutParams landscapeParams = g.getLandscapeParams();
			p.writeInt(portraitParams.width);
			p.writeInt(portraitParams.height);
			p.writeInt(portraitParams.topMargin);
			p.writeInt(portraitParams.leftMargin);
			p.writeInt(portraitParams.rightMargin);
			p.writeInt(portraitParams.bottomMargin);
			
			int[] rules = portraitParams.getRules();
			for (int i = 0; i < rules.length; i++) {
				if (rules[i] != 0) {
					p.writeInt(i);
					p.writeInt(rules[i]);
				}
			}
			p.writeInt(-1);
			
			p.writeInt(landscapeParams.width);
			p.writeInt(landscapeParams.height);
			p.writeInt(landscapeParams.topMargin);
			p.writeInt(landscapeParams.leftMargin);
			p.writeInt(landscapeParams.rightMargin);
			p.writeInt(landscapeParams.bottomMargin);
			
			rules = null;
			rules = landscapeParams.getRules();
			for (int i = 0; i < rules.length; i++) {
				if (rules[i] != 0) {
					p.writeInt(i);
					p.writeInt(rules[i]);
				}
			}
			p.writeInt(-1);
		}
		if (mBufferText) {
			p.writeInt(1);
		} else {
			p.writeInt(0);
		}
		if (mScriptName != null) {
			p.writeInt(1);
			p.writeString(mScriptName);
			p.writeString(mPluginName);
		} else {
			p.writeInt(0);
			//Log.e("PARCEL","WINDOWTOKEN("+name+") DUMPING: " + buffer.getLines().size() + " lines.");
		}
		// The caps travel with the text: see the reading constructor.
		p.writeInt(mBuffer == null ? 0 : mBuffer.getMaxLines());
		p.writeInt(mBuffer == null ? BUFFER_BYTE_BUDGET : mBuffer.getMaxBytes());
		p.writeByteArray(bufferForParcel());
		
		p.writeParcelable(mSettings, arg1);
	}

	/**
	 * The buffer as bytes, cut to something a binder transaction can carry.
	 *
	 * <p>A token goes to the UI process through AIDL, and the transaction budget
	 * there is about a megabyte for everything in flight — going over it is
	 * {@code TransactionTooLargeException}, which takes the window registration
	 * down rather than shortening it. {@link #BUFFER_BYTE_BUDGET} keeps the
	 * buffer near that on its own, but not exactly: the tree counts the bytes of
	 * its units, while the dump also writes a newline per line, so a tree sitting
	 * just under budget dumps just over it. This is the guard for that gap, and
	 * it keeps the newest text, which is the part the player is looking at.
	 *
	 * <p>Measured with a probe: the cut always lands just after a newline, so the
	 * bytes handed on start at a line boundary and no half-finished escape
	 * sequence survives into the parse. The one exception is a single line longer
	 * than the whole budget, where there is no newline to cut at — that line
	 * arrives shortened, which is the intended trade.
	 */
	private byte[] bufferForParcel() {
		byte[] dump = mBuffer == null ? new byte[0] : mBuffer.dumpToBytes(true);
		return Connection.trimToNewestLines(dump, BUFFER_BYTE_BUDGET);
	}

	/** Setter for mBuffer.
	 * 
	 * @param buffer The new buffer to use.
	 */
	public final void setBuffer(final TextTree buffer) {
		this.mBuffer = buffer;
		if (buffer != null) {
			// A buffer handed in from elsewhere (a settings reload keeps the old
			// one) is scrollback from this point on, so it lives under the same
			// budget as one this token made itself.
			buffer.setMaxBytes(BUFFER_BYTE_BUDGET);
		}
	}

	/** Getter for mBuffer.
	 * 
	 * @return mBuffer
	 */
	public final TextTree getBuffer() {
		return mBuffer;
	}
	
	/** Setter for mBufferText.
	 * 
	 * @param bufferText The new bufferText value.
	 */
	public final void setBufferText(final boolean bufferText) {
		this.mBufferText = bufferText;
	}

	/** Getter for mBufferText.
	 * 
	 * @return mBufferText
	 */
	public final boolean isBufferText() {
		return mBufferText;
	}
	
	/** Setter for mScriptName.
	 * 
	 * @param scriptName The new script name to execute on window initialization.
	 */
	public final void setScriptName(final String scriptName) {
		this.mScriptName = scriptName;
	}

	/** Getter for mScriptName.
	 * 
	 * @return mScriptName
	 */
	public final String getScriptName() {
		return mScriptName;
	}
	/** Setter for mPluginName.
	 * 
	 * @param pluginName The new plugin owner name to use.
	 */
	public final void setPluginName(final String pluginName) {
		this.mPluginName = pluginName;
	}

	/** Getter for mPluginName.
	 * 
	 * @return mPluginName
	 */
	public final String getPluginName() {
		return mPluginName;
	}

	/** Setter for mId.
	 * 
	 * @param id The new id value to use.
	 */
	public final void setId(final int id) {
		this.mId = id;
	}

	/** Getter for mId.
	 * 
	 * @return mId
	 */
	public final int getId() {
		return mId;
	}
	
	/** Getter for mLayouts.
	 * 
	 * @return mLayouts
	 */
	public final HashMap<LayoutGroup.LAYOUT_TYPE, LayoutGroup> getLayouts() {
		return mLayouts;
	}
	
	/** Utility method to reset this window token to the default values. */
	public final void resetToDefaults() {
		mName = "";
		mId = 0;
		mBufferText = false;
		mLayouts.clear();
	}

	/** Layout getter routine.
	 * 
	 * @param size The size value for the display.
	 * @param landscape True for landscape, false for portrait.
	 * @return The layout paramter object to be used for the current layout.
	 */
	public final LayoutParams getLayout(final int size, final boolean landscape) {
		
		switch(size) {
		case Configuration.SCREENLAYOUT_SIZE_SMALL:
			//tmp.setLayoutParams(w.getLayout(Configuration.SCREENLAYOUT_SIZE_SMALL,landscape));
			if (mLayouts.get(LayoutGroup.LAYOUT_TYPE.small) != null) {
				if (landscape) {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.small).getLandscapeParams();
				} else {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.small).getPortraitParams();
				}
			} else if (mLayouts.get(LayoutGroup.LAYOUT_TYPE.normal) != null) {
				if (landscape) {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.normal).getLandscapeParams();
				} else {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.normal).getPortraitParams();
				}
			} else if (mLayouts.get(LayoutGroup.LAYOUT_TYPE.large) != null) {
				if (landscape) {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.large).getLandscapeParams();
				} else {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.large).getPortraitParams();
				}
			} else if (mLayouts.get(LayoutGroup.LAYOUT_TYPE.xlarge) != null) {
				if (landscape) {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.xlarge).getLandscapeParams();
				} else {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.xlarge).getPortraitParams();
				}
			} else {
				RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
				return p;
			}
			//break;
		case Configuration.SCREENLAYOUT_SIZE_NORMAL:
			if (mLayouts.get(LayoutGroup.LAYOUT_TYPE.normal) != null) {
				if (landscape) {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.normal).getLandscapeParams();
				} else {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.normal).getPortraitParams();
				}
			} else if (mLayouts.get(LayoutGroup.LAYOUT_TYPE.large) != null) {
				if (landscape) {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.large).getLandscapeParams();
				} else {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.large).getPortraitParams();
				}
			} else if (mLayouts.get(LayoutGroup.LAYOUT_TYPE.small) != null) {
				if (landscape) {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.small).getLandscapeParams();
				} else {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.small).getPortraitParams();
				}
			} else if (mLayouts.get(LayoutGroup.LAYOUT_TYPE.xlarge) != null) {
				if (landscape) {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.xlarge).getLandscapeParams();
				} else {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.xlarge).getPortraitParams();
				}
			} else {
				RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
				return p;
			}
			//break;
		case Configuration.SCREENLAYOUT_SIZE_LARGE:
			if (mLayouts.get(LayoutGroup.LAYOUT_TYPE.large) != null) {
				if (landscape) {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.large).getLandscapeParams();
				} else {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.large).getPortraitParams();
				}
			} else if (mLayouts.get(LayoutGroup.LAYOUT_TYPE.normal) != null) {
				if (landscape) {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.normal).getLandscapeParams();
				} else {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.normal).getPortraitParams();
				}
			} else if (mLayouts.get(LayoutGroup.LAYOUT_TYPE.small) != null) {
				if (landscape) {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.small).getLandscapeParams();
				} else {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.small).getPortraitParams();
				}
			} else if (mLayouts.get(LayoutGroup.LAYOUT_TYPE.xlarge) != null) {
				if (landscape) {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.xlarge).getLandscapeParams();
				} else {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.xlarge).getPortraitParams();
				}
			} else {
				RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
				return p;
			}
			
		case Configuration.SCREENLAYOUT_SIZE_XLARGE:
			if (mLayouts.get(LayoutGroup.LAYOUT_TYPE.xlarge) != null) {
				if (landscape) {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.xlarge).getLandscapeParams();
				} else {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.xlarge).getPortraitParams();
				}
			} else if (mLayouts.get(LayoutGroup.LAYOUT_TYPE.large) != null) {
				if (landscape) {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.large).getLandscapeParams();
				} else {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.large).getPortraitParams();
				}
			} else if (mLayouts.get(LayoutGroup.LAYOUT_TYPE.normal) != null) {
				if (landscape) {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.normal).getLandscapeParams();
				} else {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.normal).getPortraitParams();
				}
			} else if (mLayouts.get(LayoutGroup.LAYOUT_TYPE.normal) != null) {
				if (landscape) {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.normal).getLandscapeParams();
				} else {
					return mLayouts.get(LayoutGroup.LAYOUT_TYPE.normal).getPortraitParams();
				}
			} else {
				RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
				return p;
			}
		default:
			break;
		}
		RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
		return p;
		//return null;
	}

	/** Getter for mSettings.
	 * 
	 * @return mSettings
	 */
	public final SettingsGroup getSettings() {
		return mSettings;
	}

	/** Setter for mSettings.
	 * 
	 * @param settings The new settings group to use for this window's settings.
	 */
	public final void setSettings(final SettingsGroup settings) {
		this.mSettings = settings;
	}

	/** Utility method to set a new buffer size, prunes the tree after setting the value.
	 * 
	 * @param amount The new buffer size to use.
	 */
	public final void setBufferSize(final int amount) {
		this.mBuffer.setMaxLines(amount);
		this.mBuffer.prune();
	}

	/** Utility method to absorb v1 window settings into this window.
	 * 
	 * @param s The old deprecated HyperSettings object to sniff for settings.
	 */
	public final void importV1Settings(final HyperSettings s) {
		this.getSettings().setOption("hyperlinks_enabled", Boolean.toString(s.isHyperLinkEnabled()));
		//HyperSettings.LINK_MODE.
		switch(s.getHyperLinkMode()) {
		case NONE:
			this.getSettings().setOption("hyperlink_mode", Integer.toString(HYPERLINK_OFF));
			break;
		case HIGHLIGHT:
			this.getSettings().setOption("hyperlink_mode", Integer.toString(HYPERLINK_HIGHLIGHT));
			break;
		case HIGHLIGHT_COLOR:
			this.getSettings().setOption("hyperlink_mode", Integer.toString(HYPERLINK_HIGHLIGHT_COLOR));
			break;
		case HIGHLIGHT_COLOR_ONLY_BLAND:
			this.getSettings().setOption("hyperlink_mode", Integer.toString(HYPERLINK_HIGHLIGHT_IF_BLAND));
			break;
		case BACKGROUND:
			this.getSettings().setOption("hyperlink_mode", Integer.toString(HYPERLINK_BACKGROUND));
			break;
		default:
			break;
		}
		
		this.getSettings().setOption("hyperlink_color", Integer.toString(s.getHyperLinkColor()));
		this.getSettings().setOption("word_wrap", Boolean.toString(s.isWordWrap()));
		this.getSettings().setOption("color_option", Integer.toString(s.isDisableColor() ? 1 : 0));
		
		this.getSettings().setOption("font_size", Integer.toString(s.getLineSize()));
		this.getSettings().setOption("line_extra", Integer.toString(s.getLineSpaceExtra()));
		this.getSettings().setOption("buffer_size", Integer.toString(s.getMaxLines()));
		if (s.getFontName().equals("")) {
			this.getSettings().setOption("font_path", s.getFontPath());
		} else {
			this.getSettings().setOption("font_path", s.getFontName());
		}
	}
	

	//public void addSettingsToParent() {
		
	//}

	
	//public void setBuffer(TextTree buffer) {
	//	this.buffer = buffer;
	//}
	
}

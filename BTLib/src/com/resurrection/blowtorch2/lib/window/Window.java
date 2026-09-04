/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.window;


import java.io.File;
import java.io.UnsupportedEncodingException;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.ListIterator;
import org.keplerproject.luajava.JavaFunction;
import org.keplerproject.luajava.LuaException;
import org.keplerproject.luajava.LuaObject;
import org.keplerproject.luajava.LuaState;
import org.keplerproject.luajava.LuaStateFactory;

import com.resurrection.blowtorch2.lib.service.IWindowCallback;
import com.resurrection.blowtorch2.lib.service.LuaLibraryHelper;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.ColorOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.FileOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.IntegerOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.ListOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsGroup;
import com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption;


import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.ClipboardManager;
import android.util.AttributeSet;
import android.util.Log;

import android.view.Gravity;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.SettingsChangedListener;
import com.resurrection.blowtorch2.lib.service.SgrStyle;
import com.resurrection.blowtorch2.lib.service.WindowToken;
import com.resurrection.blowtorch2.lib.settings.HyperSettings;
import com.resurrection.blowtorch2.lib.settings.HyperSettings.LINK_MODE;
import com.resurrection.blowtorch2.lib.window.TextTree.Line;
import com.resurrection.blowtorch2.lib.window.TextTree.Selection;
import com.resurrection.blowtorch2.lib.window.TextTree.SelectionCursor;
import com.resurrection.blowtorch2.lib.window.TextTree.Unit;


/**
 * Programmable mini-window and ANSI drawing.
 */

@SuppressWarnings("deprecation")
public class Window extends View implements AnimatedRelativeLayout.OnAnimationEndListener, SettingsChangedListener {
	/** Add text to the main output window. */
	private static final int MESSAGE_ADDTEXT = 0;
	/** Redraw the screen. */
	private static final int MESSAGE_DRAW = 117;
	/** Immediately send the contents of the input bar to the server. */
	private static final int MESSAGE_FLUSHBUFFER = 118;
	/** Shutdown all windows and exit (?). */
	private static final int MESSAGE_SHUTDOWN = 119;
	/** Cross thread bridge message for the WindowXCallS function. */
	private static final int MESSAGE_PROCESSXCALLS = 4;
	/** Clear all the text in the input bar. */
	private static final int MESSAGE_CLEARTEXT = 5;
	/** Indicate that the settings have changed and should be reloaded. */
	private static final int MESSAGE_SETTINGSCHANGED = 6;
	/** Indicates that the system encoding has changed. */
	private static final int MESSAGE_ENCODINGCHANGED = 7;
	/** Sent from the onTouchEvent handler to indicate that selection should begin. */
	private static final int MESSAGE_STARTSELECTION = 8;
	/** Sent from the selection widget, scroll down. */
	private static final int MESSAGE_SCROLLDOWN = 9;
	/** Sent from the selection widget, scroll up. */
	private static final int MESSAGE_SCROLLUP = 10;
	/** Sent from the selection widget, scroll right. */
	private static final int MESSAGE_SCROLLRIGHT = 11;
	/** Sent from the selection widget, scroll left. */
	private static final int MESSAGE_SCROLLLEFT = 12;
	/** Cross thread bridge message for the WindowXCallB lua function. */
	private static final int MESSAGE_XCALLB = 13;
	/** Message used from lua I think to reset the window, and add text to it. */
	private static final int MESSAGE_RESETWITHDATA = 14;
	/** Server took over echoing (telnet ECHO): hide what is typed. arg1 1 = local echo on. */
	private static final int MESSAGE_LOCALECHO = 15;
	/** Scroll repeat rate inital value. */
	private static final int SCROLL_REPEAT_RATE = 300;
	/** Lua relative stack location -2. */
	private static final int TOP_MINUS_TWO = -2;
	/** Lua relative stack location -2. */
	private static final int TOP_MINUS_THREE = -3;
	/** Lua relative stack location -2. */
	private static final int TOP_MINUS_FOUR = -4;
	/** Lua relative stack location -2. */
	private static final int TOP_MINUS_FIVE = -5;
	/** Maximum fling velocity. */
	private static final float MAX_VELOCITY = 900f;
	/** Stop fling coast below this (px/s); keep in sync with calculateScrollBack. */
	private static final float FLING_STOP_VELOCITY = 15f;

	/** How far above the screen to look for the colour still in effect. */
	private static final int BLEED_SEARCH_MAX_LINES = 1000;

	/** The activity that owns this window. */
	private MainWindowCallback mParent = null;
	/** The bitmap that holds the "return to the bottom of the buffer" button graphic. */
	/** The bitmap that holds the selection widget cancel button. */
	private Bitmap mTextSelectionCancelBitmap = null;
	/** The bitmap that holds the selection widget copy button. */
	private Bitmap mTextSelectionCopyBitmap = null;
	/** The bitmap that holds the selection widget cursor swap button. */
	private Bitmap mTextSelectionSwapBitmap = null;
	/** Rectangle that represents the hot-zone (clickable region) for the home button. */
	private Rect mHomeWidgetRect = new Rect();
	/** Active scrollback-search highlight (line reference + query). */
	private TextTree.Line mSearchHighlightLine = null;
	private String mSearchHighlightQuery = null;
	/** Character offsets of matches within the highlighted line's plain text. */
	private final java.util.ArrayList<Integer> mSearchMatchStarts = new java.util.ArrayList<Integer>();
	private int mSearchMatchLen = 0;
	private final Paint mSearchMatchPaint = new Paint();
	private final Paint mSearchMatchTextPaint = new Paint();
	private boolean mTapDismissKeyboard = true;
	/** When true, newest buffer lines draw at the top (older below). */
	private boolean mNewestAtTop = false;
	/** When true, finished lines that match a recent long line paint dimmer. */
	private boolean mDimRepeatedLines = false;
	/** When true, OSC 8 hrefs are tappable. Independent of regex linkify. */
	private boolean mOsc8Links = true;
	/** How many recent long lines the dimmer remembers. */
	private int mDimRepeatedWindow = RepeatedLineDimmer.DEFAULT_WINDOW;
	/** How hard to dim (10–90); 50 keeps half the colour. */
	private int mDimRepeatedStrength = RepeatedLineDimmer.DEFAULT_STRENGTH;
	/** True while {@link #onDraw} is painting a line marked {@code dimRepeated}. */
	private boolean mPaintingDimLine = false;
	/** Last undimmed FG applied to {@code p}; dim is a multiply after this. */
	private int mResolvedFg = 0xFFFFFFFF;
	/** Extra empty pixels above game text (notch / camera). Buttons unaffected. */
	private int mTopPadding = 0;
	/** Extra empty pixels below game text, always. Buttons unaffected. */
	private int mBottomPadding = 0;
	/** Further empty pixels below game text while the soft keyboard is up. */
	private int mBottomPaddingIme = 0;
	/** Keyboard lift in px, pushed by {@link ChromeController#applyImeChromeLift}. */
	private int mImeLiftPx = 0;
	/** Gain applied to finger travel when scrolling. 1.0 means the text tracks the finger. */
	private float mScrollSensitivity = 1.0f;
	/**
	 * The {@code scroll_sensitivity} list choice behind {@link #mScrollSensitivity}.
	 * Kept alongside the gain so extra-text overlays can inherit this window's
	 * choice without reading the settings tree from another process.
	 */
	private int mScrollSensitivityChoice = WindowToken.DEFAULT_SCROLL_SENSITIVITY;
	/**
	 * Extra insets when an extra-text drawer covers this window (push-main).
	 * Shrinks the painted text region only — layout stays full-bleed so
	 * {@code button_window} coordinates are unchanged.
	 */
	private int mDrawerInsetTop = 0;
	private int mDrawerInsetBottom = 0;
	/** When true, IME lift skips game text windows (input bar still rises). */
	private boolean mImeKeepText = false;
	/** The buffer object that this window uses to store and draw ansi text.
	 *
	 * <p><b>Only the UI thread may change this buffer.</b> onDraw walks its line list
	 * three times per frame -- getScreenIterator, the bleed search and the draw loop --
	 * and only the first of those catches ConcurrentModificationException. A change
	 * from another thread would therefore not slow drawing down, it would throw out of
	 * onDraw and take the activity with it.
	 *
	 * <p>That is safe today because everything arrives through mHandler, which is
	 * created on the UI thread: the IWindowCallback binder stub only ever posts
	 * messages, it never touches the buffer itself. Keep it that way. If you need to
	 * feed text in from another thread, post a MESSAGE_ADDTEXT rather than calling
	 * addBytes directly -- {@link #warnIfNotUiThread} will complain in the log if you
	 * forget. */
	private TextTree mBuffer = null;
	/** Set once the buffer-thread rule has been reported, so the log is not flooded. */
	private boolean mBufferThreadWarned = false;
	/** The buffer that is used to buffer text when BufferText() is set. */
	private TextTree mHoldBuffer = null;
	/** The maximum height for this window. I don't think this is used. */
	private int mMaxHeight;
	/** The preference for fontsize. */
	private int mPrefFontSize = WindowToken.DEFAULT_FONT_SIZE;
	/** The height of this window. */
	private int mHeight = 1;
	/** The width of this window. */
	private int mWidth = 1;
	/** The measured width of one character using the preference font size. */
	private int mOneCharWidth = 1;
	/** The display density of the device's display panel. */
	private float mDensity;
	/** The LuaState associated with this window. */
	private LuaState mL = null;
	/** The string name of the plugin that launched this window. */
	private String mOwner;
	/** Variable to store the calculated number of lines that can be drawn at the current font size. */
	private int mCalculatedLinesInWindow;
	/** The preference value for the extra line space to add to each line. */
	private int mPrefLineExtra = 2;
	/** The preference value for the total linesize, the sum of the font + extra. */
	private int mPrefLineSize = (int) mPrefFontSize + mPrefLineExtra;
	/** The preferred font to use to draw text. */
	private Typeface mPrefFont = Typeface.MONOSPACE;
	/** The calclualated number of rows in the window for the preferred line size. */
	private int mCalculatedRowsInWindow;
	/** Tracker value for weather or not text selection should be available for this window. */
	private boolean mTextSelectionEnabled = true;
	/** The current fling velocity. */
	private double mFlingVelocity;
	/** The number of chars to fit to the width of the window, -1 to disable. */
	private int mFitChars = -1;
	/** Tracker value for weather or not to buffer incoming text (used while text selecting). */
	private boolean mBufferText = false;
	/** Tracker value for weather or not to center justify text being drawn. */
	private boolean mCenterJustify = false;
	/** Tracker value for weather or not the window has a script OnMeasure function implemented. */
	private boolean mHasScriptOnMeasure = false;
	/** Tracker value for what the current color debug mode is. */
	private int mColorDebugMode = 0;
	/** Tracker value for the current link mode. */
	private LINK_MODE mLinkMode = LINK_MODE.HIGHLIGHT_COLOR_ONLY_BLAND;
	/** Tracker value for the current link decoration color. */
	private int mLinkHighlightColor = HyperSettings.DEFAULT_HYPERLINK_COLOR;
	/** Light paper + darkened ink. Off by default. Extra-text copies the main window. */
	private boolean mLightPaper = false;
	/** 1 grey … 5 near-white. Default 2 = original warm paper. Extra-text copies main. */
	private int mLightPaperShade = LightPaper.SHADE_DEFAULT;
	/** ANSI Drawing routine current color register. */
	private Integer mSelectedColor = Integer.valueOf(37);
	/** ANSI Drawing routine current brightness register. */
	private Integer mSelectedBright = Integer.valueOf(0);
	/** Italic / underline / strike / reverse / faint / blink. Not a typeface. */
	private final SgrStyle mSgr = new SgrStyle();
	/** When true, MUD SGR 1 also sets {@link SgrStyle#weight()}. */
	private boolean mSgr1Weight;
	/** Slow/fast blink hide-phase for this frame (clock, not cached RGB). */
	private boolean mBlinkHiddenSlow;
	private boolean mBlinkHiddenFast;
	private boolean mBlinkSawThisFrame;
	private boolean mBlinkFastSawThisFrame;
	private boolean mBlinkPosted;
	private final Runnable mBlinkInvalidate = new Runnable() {
		@Override
		public void run() {
			mBlinkPosted = false;
			if (windowShowing) {
				invalidate();
			}
		}
	};
	/** ANSI Drawing routine current background color. */
	private Integer mSelectedBackground = Integer.valueOf(60);
	/** Utility variable that is used by the ANSI drawing routine to properly handle xterm 256 colors. */
	private boolean mXterm256FGStart = false;
	/** Utility variable that is used by the ANSI drawing routine to properly handle xterm 256 colors. */
	private boolean mXterm256BGStart = false;
	/** Utility variable that is used by the ANSI drawing routine to properly handle xterm 256 colors. */
	private boolean mXterm256Color = false;
	/** True while the current foreground register holds an xterm-256 palette index. */
	private boolean mXterm256FG = false;
	/** True while the current background register holds an xterm-256 palette index. */
	private boolean mXterm256BG = false;
	/** Collecting {@code 38;2;R;G;B} / {@code 48;2;R;G;B} components. */
	private boolean mTrueColorCollect = false;
	private boolean mTrueColorIsFG = true;
	private int mTrueColorCount = 0;
	private final int[] mTrueColorRGB = new int[3];
	/** Foreground is a packed 0xRRGGBB truecolor value in {@link #mSelectedColor}. */
	private boolean mTrueColorFG = false;
	/** Background is a packed 0xRRGGBB truecolor value in {@link #mSelectedBackground}. */
	private boolean mTrueColorBG = false;
	/** The handler message queue for this window. */
	private Handler mHandler = null;
	/** The handler message queue for the main window that holds this window. */
	private Handler mMainWindowHandler = null;
	/** Paint object associated with drawing text inside of the magnifier widget. */
	private Paint mTextSelectionIndicatorPaint = new Paint();
	/** Paint object associated with drawing the background highlight color of the magnifier widget. */
	private Paint mTextSelectionIndicatorBackgroundPaint = new Paint();
	/** The paint object associated with drawing the circle around the magnifier widget. */
	private Paint mTextSelectionIndicatorCirclePaint = new Paint();
	/** Synchronization target for touch handling and text adding (i think). */
	private Object mToken = new Object(); //token for synchronization.
	/** The user configurable settings for this window. */
	private SettingsGroup mSettings = null;
	/** Application context. */
	//private Context mContext = null;
	/** Bitmap that holds the selection indicator widget. */
	private Bitmap mSelectionIndicatorBitmap = null;
	/** Canvas that allows drawing to the selection indicator bitmap. */
	private Canvas mSelectionIndicatorCanvas = null;
	/** True while {@link #mSelectionIndicatorCanvas} has an unmatched save() for the circular clip. */
	private boolean mSelectionCanvasSaved = false;
	/** The font size for the selection widget. */
	private int mSelectionIndicatorFontSize = 30;
	/** Another patint object associatied with drawing the selection indicator. */
	private Paint mSelectionIndicatorPaint = new Paint();
	/** The measure of one character in the selection widget. */
	private int mSelectionCharacterWidth = 1;
	/** The measure of half of the selection widget. */
	private int mSelectionIndicatorHalfDimension = 60;
	/** Clip object to cut away the outside of the circle by masking. */
	private Path mSelectionIndicatorClipPath = new Path();
	/** Left button hot zone for the selection widget. */
	private Rect mSelectionIndicatorLeftButtonRect = new Rect();
	/** right button hot zone for the selection widget. */
	private Rect mSelectionIndicatorRightButtonRect = new Rect();
	/** top button hot zone for the selection widget. */
	private Rect mSelectionIndicatorUpButtonRect = new Rect();
	/** bottom button hot zone for the selection widget. */
	private Rect mSelectionIndicatorDownButtonRect = new Rect();
	/** center button hot zone for the selection widget. */
	private Rect mSelectionIndicatorCenterButtonRect = new Rect();
	/** hot zone for the selection widget. */
	private Rect mSelectionIndicatorRect = new Rect();
	/** Scroll repeat acceleration. */
	private int mScrollRepeatRateStep = 1;
	/** Scroll repeat rate variable. */
	private int mScrollRepeatRate = SCROLL_REPEAT_RATE;
	/** The minimum scroll repeat rate. */
	private int mScrollRepeatRateMin = 60;
	/** Indicates that a finger is currently down on the touchpad. */
	boolean mFingerDown = false;
	/** The difference between the last touch event and this event. */
	int diff_amount = 0;
	/** The x value of the start of the touch event. */
	Float start_x = null;
	/** The y value of the start of the touch event. */
	Float mStartY = null;
	/** Line/column at ACTION_DOWN (for selection / double-tap). */
	private int mTouchDownLine = 0;
	private int mTouchDownColumn = 0;
	/** Last completed tap (for double-tap to start text selection). */
	private long mLastTapUpTime = 0L;
	private float mLastTapUpX = 0f;
	private float mLastTapUpY = 0f;
	/** Previous MOVE sample (avoids allocating MotionEvent every frame). */
	private float mMoveLastY = 0f;
	private long mMoveLastTime = 0L;
	/** Indicates that the finger has left the touchpad. */
	boolean finger_down_to_up = false;
	/** The system time in millis that the last frame was drawn at. */
	long mLastFrameTime = 0;
	/** If a touch event happened inside of a link hitbox it will be noted here. */
	public int mTouchInLink = -1;
	/** Indicates weather a touch event happened inside of the jump to home button. */
	boolean homeWidgetFingerDown = false;
	/** The first finger pointer id. */
	int pointer = -1;
	/** Fling acceleration for scrolling. */
	float fling_accel = 200.0f; //(units per sec);
	/** List of link boxes found on the current drawing routine. */
	private ArrayList<LinkBox> linkBoxes = new ArrayList<LinkBox>(); /** TODO: make this into a preallocated list of a fixed size so you don't reallocate during the draw phase. */
	/** Paint object for the scroll bar. */
	private Paint mScrollerPaint = new Paint();
	/** Indicates if the home widget is being drawn. */
	private boolean homeWidgetShowing = false;
	/** Date overlay + position mark while in history. Options → Window → Scroll dates? */
	private boolean mScrollDates = false;
	private int mScrollDatesOpacity = WindowToken.DEFAULT_SCROLL_DATES_OPACITY;
	private final Paint mJumpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Path mJumpPath = new Path();
	private final Paint mWhenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	/** View bounds for the the scroller rectangle. */
	Rect scrollerRect = new Rect();
	/** This is kind of a promiscuous variable, color is set on the fly. */
	Paint featurePaint = new Paint();
	/** The internal memory location of the internal lua default libraries for BlowTorch. */
	private String mDataDir = null;
	/** Foreground text paint object, short name for code readability. */
	Paint p = new Paint();
	/** Backgrond highlighting paint object, short name for code readabliity. */
	Paint b = new Paint();
	/** What b's colour was last set to. The draw loop tests it once per unit, and
	 * Paint.getColor() crosses into native code every time; b is only ever recoloured
	 * from the four places that call setBgPaintColor. Paint defaults to black. */
	private int mBgPaintColor = 0xFF000000;

	/** Recolour the background paint, keeping the cached copy in step. */
	private void setBgPaintColor(final int color) {
		b.setColor(color);
		mBgPaintColor = color;
	}
	/** The configured hyperlink background or highlight color. */
	Paint linkColor = null;
	/** The minimum amount of scroll, i think this gets set before use. */
	private Double SCROLL_MIN = 24d;
	/** The current scrollback amount. */
	private Double mScrollback = SCROLL_MIN;
	/** Uhg. The screen iterator. I'm not really sure this whole thing is worth it. There has to be a better way. */
	ListIterator<TextTree.Line> screenIt = null;
	/** Helper object for the draw routine, iterates a line object unit to unit. */
	ListIterator<Unit> unitIterator = null;
	/** The minimum hitbox size for a link. */
	private int mLinkBoxHeightMinimum = 20;
	/** Inidcates the presence of a global OnDraw(Canvas) function in the backing script. */
	boolean mHasDrawRoutine = true;
	/** The name of this window. */
	private String mName = null;
	/** The clipping rectangle for the draw routine. */
	private Rect mClipRect = new Rect();
	/** The current link click that is being clicked. */
	private StringBuffer mCurrentLink = new StringBuffer();
	
	/** Constructor to be used, the others aren't really suppposed to be there. 
	 * 
	 * @param pDataDir The path to the internal lua libraries.
	 * @param context The application context.
	 * @param name The name of the window. 
	 * @param owner The plugin owner of the window.
	 * @param mainWindowHandler The callback handler to the MainWindow Activity.
	 * @param settings The serializable settings for the window.
	 * @param activity The MainWindowCallback to use to report changes from the scripts
	 */
	public Window(final String pDataDir,
			final Context context,
			final String name,
			final String owner,
			final Handler mainWindowHandler,
			final SettingsGroup settings,
			final MainWindowCallback activity) {
		super(context);
		this.mParent = activity;
		init(pDataDir, name, owner, mainWindowHandler, settings);
	}
	
	/** Generic window constructor, here to pass the lint test. Will not work. 
	 * 
	 * @param c Application Context
	 */
	public Window(final Context c) {
		super(c);
	}
	
	/** XML Inflation constructor, here to pass the lint test. Will not work. 
	 * 
	 * @param c Application context
	 * @param a I'm not really sure but you can't use it from lua.
	 */
	public Window(final Context c, final AttributeSet a) {
		super(c, a);
	}
	
	/** Kicks off a lua error message to the main output window. This has to be sent round trip through the Service in order to be picked up by the internal buffer.
	 * 
	 * @param message The lua error to display.
	 */
	public final void displayLuaError(final String message) {
		// Recorded here as well as on the service side: this is the process the error
		// happened in, and if the hop across to the service fails the only trace left
		// would be red text in the game window.
		try {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logError(
					getContext().getApplicationContext(), mName == null ? "window" : mName, message);
		} catch (Exception ignored) {
		}
		mMainWindowHandler.sendMessage(mMainWindowHandler.obtainMessage(MainWindow.MESSAGE_DISPLAYLUAERROR, "\n" + Colorizer.getRedColor() + message + Colorizer.getWhiteColor() + "\n"));
	}
	
	@Override
	protected final void onAttachedToWindow() {
		windowShowing = true;
		// Registered here rather than the first time a picture is seen in
		// onDraw. Doing it from the draw pass meant mutating the store's
		// listener list from inside a draw, and the store iterates that same
		// list to announce a load — the exact shape of bug the wait(5) retry
		// loop in onDraw was built to paper over.
		if (!mInlineImageListening) {
			FrameImageStore.get().addListener(mInlineImageRepaint);
			mInlineImageListening = true;
		}
	}
	
	@Override
	protected final void onDetachedFromWindow() {
		windowShowing = false;
		// The image store outlives any one Window — it is process-wide — so a
		// listener left on it would hold this view and its Activity alive.
		if (mInlineImageListening) {
			FrameImageStore.get().removeListener(mInlineImageRepaint);
			mInlineImageListening = false;
		}
		removeCallbacks(mBlinkInvalidate);
		mBlinkPosted = false;
	}
	


	/** Initialization routine. It all starts here.
	 * 
	 * @param dataDir The path to the internal lua libraries.
	 * @param name The name of the window. 
	 * @param owner The plugin owner of the window.
	 * @param mainWindowHandler The callback handler to the MainWindow Activity.
	 * @param settings The serializable settings for the window.
	 */
	private void init(final String dataDir, final String name, final String owner, final Handler mainWindowHandler, final SettingsGroup settings) {
		this.mDataDir = dataDir;
		this.mDensity = this.getContext().getResources().getDisplayMetrics().density;
		if ((Window.this.getContext().getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_XLARGE) {
			mSelectionIndicatorHalfDimension = (int) (90 * mDensity);
		} else {
			mSelectionIndicatorHalfDimension = (int) (60 * mDensity);
		}
		
		mSelectionIndicatorClipPath.addCircle(mSelectionIndicatorHalfDimension, mSelectionIndicatorHalfDimension, mSelectionIndicatorHalfDimension - 10, Path.Direction.CCW);
		mTextSelectionCancelBitmap = BitmapFactory.decodeResource(this.getContext().getResources(), com.resurrection.blowtorch2.lib.R.drawable.cancel_tiny);
		mTextSelectionCopyBitmap = BitmapFactory.decodeResource(this.getContext().getResources(), com.resurrection.blowtorch2.lib.R.drawable.copy_tiny);
		mTextSelectionSwapBitmap = BitmapFactory.decodeResource(this.getContext().getResources(), com.resurrection.blowtorch2.lib.R.drawable.swap);
		
		mTextSelectionIndicatorPaint.setStyle(Paint.Style.STROKE);
		mTextSelectionIndicatorPaint.setStrokeWidth(1 * mDensity);
		mTextSelectionIndicatorPaint.setColor(0xFFFF0000);
		mTextSelectionIndicatorPaint.setAntiAlias(true);
		
		mTextSelectionIndicatorBackgroundPaint.setStyle(Paint.Style.FILL);
		mTextSelectionIndicatorBackgroundPaint.setColor(0x770000FF);
		
		mTextSelectionIndicatorCirclePaint.setStyle(Paint.Style.STROKE);
		mTextSelectionIndicatorCirclePaint.setStrokeWidth(2);
		mTextSelectionIndicatorCirclePaint.setColor(0xFFFFFFFF);
		DashPathEffect dpe = new DashPathEffect(new float[]{3, 3}, 0);
		
		mTextSelectionIndicatorCirclePaint.setPathEffect(dpe);
		mTextSelectionIndicatorCirclePaint.setAntiAlias(true);
		// Modest yellow fill; matched glyphs redrawn in black for contrast on dark mud text.
		mSearchMatchPaint.setStyle(Paint.Style.FILL);
		mSearchMatchPaint.setColor(0xCCF6E27A);
		mSearchMatchTextPaint.setStyle(Paint.Style.FILL);
		mSearchMatchTextPaint.setColor(0xFF000000);
		mSearchMatchTextPaint.setAntiAlias(true);
		mLoupeHighlightPaint.setStyle(Paint.Style.FILL);
		mLoupeHighlightPaint.setColor(0x66FFEB3B);
		mLoupePanelPaint.setStyle(Paint.Style.FILL);
		mLoupePanelPaint.setColor(0xEE212121);
		mLoupePanelPaint.setAntiAlias(true);
		mLoupeTextPaint.setStyle(Paint.Style.FILL);
		mLoupeTextPaint.setColor(0xFFFFFFFF);
		mLoupeTextPaint.setAntiAlias(true);
		this.mSettings = settings;
		this.mSettings.setListener(this);
		mBuffer = new TextTree();
		mHoldBuffer = new TextTree();
		// The UI keeps its own copy of the service's scrollback, so it needs the
		// same byte budget — otherwise the cap that bounds one process leaves the
		// other free to grow.
		mBuffer.setMaxBytes(WindowToken.BUFFER_BYTE_BUDGET);
		mHoldBuffer.setMaxBytes(WindowToken.BUFFER_BYTE_BUDGET);
		mHandler = new Handler() {
			public void handleMessage(final Message msg) {
				switch(msg.what) {
				case MESSAGE_RESETWITHDATA:
					Window.this.resetAndAddText((byte[]) msg.obj);
					break;
				case MESSAGE_SCROLLLEFT:
					mScrollRepeatRate -= (mScrollRepeatRateStep++) * 5; if (mScrollRepeatRate < mScrollRepeatRateMin) { mScrollRepeatRate = mScrollRepeatRateMin; }
					Window.this.doScrollLeft(true);
					break;
				case MESSAGE_SCROLLRIGHT:
					mScrollRepeatRate -= (mScrollRepeatRateStep++) *5 ; if (mScrollRepeatRate < mScrollRepeatRateMin) { mScrollRepeatRate = mScrollRepeatRateMin; }
					Window.this.doScrollRight(true);
					break;
				case MESSAGE_SCROLLDOWN:
					mScrollRepeatRate -= (mScrollRepeatRateStep++) * 5; if (mScrollRepeatRate < mScrollRepeatRateMin) { mScrollRepeatRate = mScrollRepeatRateMin; }
					Window.this.doScrollDown(true);
					break;
				case MESSAGE_SCROLLUP:
					mScrollRepeatRate -= (mScrollRepeatRateStep++) * 5; if (mScrollRepeatRate < mScrollRepeatRateMin) { mScrollRepeatRate = mScrollRepeatRateMin; }
					Window.this.doScrollUp(true);
					break;
				case MESSAGE_STARTSELECTION:
					Window.this.startSelection(msg.arg1, msg.arg2);
					break;
				case MESSAGE_ENCODINGCHANGED:
					Window.this.updateEncoding((String) msg.obj);
					break;
				case MESSAGE_LOCALECHO:
					Window.this.onLocalEchoChanged(msg.arg1 == 1);
					break;
				case MESSAGE_SETTINGSCHANGED:
					Window.this.doUpdateSetting(msg.getData().getString("KEY"), msg.getData().getString("VALUE"));
					break;
				case MESSAGE_CLEARTEXT:
					mBuffer.empty();
					mHoldBuffer.empty();
					break;
				case MESSAGE_SHUTDOWN:
					Window.this.shutdown();
					break;
				case MESSAGE_FLUSHBUFFER:
					Window.this.flushBuffer();
					break;
				case MESSAGE_DRAW:
					Window.this.invalidate();
					break;
					
				case MESSAGE_ADDTEXT:
					Window.this.addBytes((byte[]) msg.obj, false);
					break;
				case MESSAGE_PROCESSXCALLS:
					Window.this.xcallS(msg.getData().getString("FUNCTION"), (String) msg.obj);
					
					break;
				case MESSAGE_XCALLB:
					//try {
					try {
						Window.this.xcallB(msg.getData().getString("FUNCTION"), (byte[]) msg.obj);
					} catch (LuaException e) {
						e.printStackTrace();
					}
					break;
				default:
					break;
				}
			}
		};
		
		
		//lua startup.
		mOwner = owner;
		
		this.mMainWindowHandler = mainWindowHandler;
		
		mName = name;
		
		mSelectionIndicatorBitmap = Bitmap.createBitmap(2 * mSelectionIndicatorHalfDimension, 2 * mSelectionIndicatorHalfDimension, Bitmap.Config.ARGB_8888);
		mSelectionIndicatorCanvas = new Canvas(mSelectionIndicatorBitmap);
		
		int full = mSelectionIndicatorHalfDimension * 2;
		int third = full / 3;
		
		mSelectionIndicatorLeftButtonRect.set(third, 0, 2 * third, 40);
		mSelectionIndicatorUpButtonRect.set(0, third, 40, 2 * third);
		mSelectionIndicatorRightButtonRect.set(full - 40, third, full, 2 * third);
		mSelectionIndicatorDownButtonRect.set(third, 2 * third, 2 * third, full);
		mSelectionIndicatorCenterButtonRect.set(third, third, 2 * third, 2 * third);
		
		mSelectionIndicatorRect.set(0, 0, full, full);
		
		//start extracting and setting settings.
		IntegerOption fontsize = (IntegerOption) settings.findOptionByKey("font_size");
		IntegerOption lineextra = (IntegerOption) settings.findOptionByKey("line_extra");
		IntegerOption buffersize = (IntegerOption) settings.findOptionByKey("buffer_size");
		FileOption fontpath = (FileOption) settings.findOptionByKey("font_path");
		ListOption colorOption = (ListOption) settings.findOptionByKey("color_option");
		ColorOption hyperlinkcolor = (ColorOption) settings.findOptionByKey("hyperlink_color");
		
		BooleanOption wordwrap = (BooleanOption) settings.findOptionByKey("word_wrap");
		IntegerOption canvasWidth = (IntegerOption) settings.findOptionByKey("text_canvas_width");
		if (canvasWidth != null) {
			mCanvasWidthFactor = Math.max(1.0f,
					Math.min(2.0f, ((Integer) canvasWidth.getValue()).intValue() / 100f));
		}
		BooleanOption hlenabled = (BooleanOption) settings.findOptionByKey("hyperlinks_enabled");
		BooleanOption tapDismiss = (BooleanOption) settings.findOptionByKey("tap_dismiss_keyboard");
		if (tapDismiss != null) {
			mTapDismissKeyboard = (Boolean) tapDismiss.getValue();
		}
		BooleanOption newestAtTop = (BooleanOption) settings.findOptionByKey("newest_at_top");
		if (newestAtTop != null) {
			mNewestAtTop = (Boolean) newestAtTop.getValue();
		}
		IntegerOption dimWindow = (IntegerOption) settings.findOptionByKey("dim_repeated_window");
		if (dimWindow != null) {
			applyDimRepeatedWindow((Integer) dimWindow.getValue());
		}
		IntegerOption dimStrength = (IntegerOption) settings.findOptionByKey("dim_repeated_strength");
		if (dimStrength != null) {
			applyDimRepeatedStrength((Integer) dimStrength.getValue());
		}
		BooleanOption dimRepeated = (BooleanOption) settings.findOptionByKey("dim_repeated_lines");
		if (dimRepeated != null) {
			applyDimRepeatedLines((Boolean) dimRepeated.getValue());
		}
		BooleanOption lightPaper = (BooleanOption) settings.findOptionByKey("light_paper");
		if (lightPaper != null) {
			mLightPaper = (Boolean) lightPaper.getValue();
		}
		IntegerOption lightPaperShade =
				(IntegerOption) settings.findOptionByKey("light_paper_shade");
		if (lightPaperShade != null) {
			mLightPaperShade = LightPaper.clampShade(
					((Integer) lightPaperShade.getValue()).intValue());
		}
		BooleanOption scrollDates = (BooleanOption) settings.findOptionByKey("scroll_dates");
		if (scrollDates != null) {
			mScrollDates = (Boolean) scrollDates.getValue();
		}
		IntegerOption scrollDatesOpacity =
				(IntegerOption) settings.findOptionByKey("scroll_dates_opacity");
		if (scrollDatesOpacity != null) {
			mScrollDatesOpacity = WindowToken.clampScrollDatesOpacity(
					((Integer) scrollDatesOpacity.getValue()).intValue());
		}
		BooleanOption osc8Links = (BooleanOption) settings.findOptionByKey("osc8_links");
		if (osc8Links != null) {
			applyOsc8Links((Boolean) osc8Links.getValue());
		}
		IntegerOption topPadding = (IntegerOption) settings.findOptionByKey("top_padding");
		if (topPadding != null) {
			mTopPadding = Math.max(0, (Integer) topPadding.getValue());
		}
		IntegerOption bottomPadding = (IntegerOption) settings.findOptionByKey("bottom_padding");
		if (bottomPadding != null) {
			mBottomPadding = Math.max(0, (Integer) bottomPadding.getValue());
		}
		IntegerOption bottomPaddingIme =
				(IntegerOption) settings.findOptionByKey("bottom_padding_keyboard");
		if (bottomPaddingIme != null) {
			mBottomPaddingIme = Math.max(0, (Integer) bottomPaddingIme.getValue());
		}
		BooleanOption imeKeepText = (BooleanOption) settings.findOptionByKey("ime_keep_text");
		if (imeKeepText != null) {
			mImeKeepText = (Boolean) imeKeepText.getValue();
		}
		ListOption scrollSensitivity = (ListOption) settings.findOptionByKey("scroll_sensitivity");
		if (scrollSensitivity != null) {
			mScrollSensitivityChoice = (Integer) scrollSensitivity.getValue();
			mScrollSensitivity = scrollSensitivityFromChoice(Integer.valueOf(mScrollSensitivityChoice));
		}
		
		ListOption hlmode = (ListOption) settings.findOptionByKey("hyperlink_mode");
		
		mPrefFont = loadFontFromName((String) fontpath.getValue());
		setGridTypeface(p);
		
		int bufferLines = (Integer) buffersize.getValue();
		// Legacy default was 300; raise once so existing profiles get usable scrollback.
		if (bufferLines <= 300) {
			bufferLines = WindowToken.DEFAULT_BUFFER_SIZE;
			buffersize.setValue(bufferLines);
		}
		mBuffer.setMaxLines(bufferLines);
		mHoldBuffer.setMaxLines(mBuffer.getMaxLines());
		if (mBuffer.getMaxLines() != ((Integer) buffersize.getValue()).intValue()) {
			buffersize.setValue(Integer.valueOf(mBuffer.getMaxLines()));
		}
		
		mPrefLineExtra = (Integer) lineextra.getValue();
		mPrefFontSize = (Integer) fontsize.getValue();
		setCharacterSizes(mPrefFontSize, mPrefLineExtra);
		
		switch((Integer) colorOption.getValue()) {
		case 0:
			this.setColorDebugMode(0);
			break;
		case 1:
			this.setColorDebugMode(3);
			break;
		case 2:
			this.setColorDebugMode(1);
			break;
		case 3:
			this.setColorDebugMode(2);
		default:
			break;
		}
		
		this.setWordWrap((Boolean) wordwrap.getValue());
		this.setLinkColor((Integer) hyperlinkcolor.getValue());
		this.setLinkMode((Integer) hlmode.getValue());
		this.setLinksEnabled((Boolean) hlenabled.getValue());
		applyUrlLinkSettingsFrom(settings);
	}

	/** Rebuild the buffer URL finder from hyperlink bare/extras options. */
	private void applyUrlLinkSettingsFrom(final SettingsGroup settings) {
		boolean bare = true;
		String extras = "";
		if (settings != null) {
			BooleanOption bareOpt = (BooleanOption) settings.findOptionByKey("hyperlink_bare_domains");
			if (bareOpt != null && bareOpt.getValue() instanceof Boolean) {
				bare = (Boolean) bareOpt.getValue();
			}
			StringOption extrasOpt = (StringOption) settings.findOptionByKey("hyperlink_extra_tlds");
			if (extrasOpt != null && extrasOpt.getValue() instanceof String) {
				extras = (String) extrasOpt.getValue();
			}
		}
		if (mBuffer != null) {
			mBuffer.setUrlLinkSettings(bare, extras);
		}
		if (mHoldBuffer != null) {
			mHoldBuffer.setUrlLinkSettings(bare, extras);
		}
	}

	private void applyDimRepeatedLines(final boolean enabled) {
		mDimRepeatedLines = enabled;
		if (mBuffer != null) {
			mBuffer.setDimRepeatedWindow(mDimRepeatedWindow);
			mBuffer.setDimRepeatedLines(enabled);
		}
	}

	private void applyOsc8Links(final boolean enabled) {
		mOsc8Links = enabled;
		if (mBuffer != null) {
			mBuffer.setOsc8Links(enabled);
		}
	}

	private void applyDimRepeatedWindow(final int n) {
		mDimRepeatedWindow = RepeatedLineDimmer.clampWindow(n);
		if (mBuffer != null) {
			mBuffer.setDimRepeatedWindow(mDimRepeatedWindow);
		}
	}

	private void applyDimRepeatedStrength(final int n) {
		mDimRepeatedStrength = RepeatedLineDimmer.clampStrength(n);
	}
	
	/** Resets the buffer with the given argument. 
	 * 
	 * @param obj The text to add in bytes.
	 */
	protected final void resetAndAddText(final byte[] obj) {
		mBuffer.empty();
		mHoldBuffer.empty();
		addBytes(obj, true);
	}

	/** The end of the WindowXCallB Lua function.
	 * 
	 * @param string The name of the global callback function to call.
	 * @param bytes The bytes to provide to it (gets converted to a lua string without going through java.
	 * @throws LuaException Thown when there is a problem with lua.
	 */
	protected final void xcallB(final String string, final byte[] bytes) throws LuaException {
		if (mL == null) { return; }
		mL.getGlobal("debug");
		mL.getField(-1, "traceback");
		mL.remove(TOP_MINUS_TWO);
		
		mL.getGlobal(string);
		if (mL.getLuaObject(-1).isFunction()) {
			mL.pushObjectValue(bytes);
			int ret = mL.pcall(1, 1, TOP_MINUS_THREE);
			if (ret != 0) {
				displayLuaError("WindowXCallB calling: " + string + " error:" + mL.getLuaObject(-1).getString());
			}
			// Both branches leave the traceback function plus one result (the
			// return value, or the error object). The error branch used to pop
			// neither, so every Lua error leaked two slots.
			mL.pop(2);
		} else {
			mL.pop(2);
		}
	}

	/**
	 * Open the widget with everything between the two fingers already selected.
	 *
	 * <p>Both ends are resolved the same way a single touch is, so each snaps to
	 * real text rather than to empty padding, and then the outer edges of the two
	 * become the span. The player still adjusts it with the widget afterwards —
	 * this only saves dragging across the whole passage first.
	 *
	 * <p>Line 0 is the newest line, so the earlier text is the one with the
	 * larger line index; that is what decides which end is the start.
	 *
	 * @param lineA Buffer line under the first finger.
	 * @param colA Column under the first finger.
	 * @param lineB Buffer line under the second finger.
	 * @param colB Column under the second finger.
	 */
	private void startSelectionBetween(final int lineA, final int colA,
			final int lineB, final int colB) {
		TextTree.Selection a = resolveSelectionForPoint(lineA, colA);
		TextTree.Selection b = resolveSelectionForPoint(lineB, colB);
		if (a == null || b == null) {
			// One finger was somewhere with no text under it; fall back to the
			// old behaviour rather than guess at a span.
			startSelection(lineA, colA);
			return;
		}
		TextTree.Selection earlier = a;
		TextTree.Selection later = b;
		if (a.start.line < b.start.line
				|| (a.start.line == b.start.line && a.start.column > b.start.column)) {
			earlier = b;
			later = a;
		}
		TextTree.Selection span = mBuffer.new Selection(earlier.start, later.end);
		beginSelectionWith(span);
	}

	/** Starts the selection mode, sets up structures and flags that cause the widget to be drawn in onDraw(...).
	 * 
	 * @param line Starting line.
	 * @param column Starting column.
	 */
	private void startSelection(final int line, final int column) {
		beginSelectionWith(resolveSelectionForPoint(line, column));
	}

	/** The shared half of opening the widget, whatever decided the span. */
	private void beginSelectionWith(final TextTree.Selection selection) {
		theSelection = selection;
		if (theSelection == null) {
			firstPress = true;
		} else {
			// Prefer floating the widget above the caret so it stays on-screen at live bottom.
			// Newest-at-top: float below so it stays clear of the live edge at the top.
			selectionIndicatorVectorX = mOneCharWidth + mSelectionIndicatorHalfDimension;
			if (mNewestAtTop) {
				selectionIndicatorVectorY = mSelectionIndicatorHalfDimension
						+ Math.max(mPrefLineSize, (int) (8 * mDensity));
			} else {
				selectionIndicatorVectorY = -mSelectionIndicatorHalfDimension
						- Math.max(mPrefLineSize, (int) (8 * mDensity));
			}
			this.setOnTouchListener(textSelectionTouchHandler);
			selectedSelector = theSelection.end;
			moveWidgetToSelector(selectedSelector);
			
			//start the window buffering so it does not interfere with our biz-nas.
			this.setBufferText(true);
			// Hide buttons only — widget stays drawn on this window.
			if (mMainWindowHandler != null) {
				mMainWindowHandler.sendMessage(
						mMainWindowHandler.obtainMessage(MainWindow.MESSAGE_TEXTSELECTION_FOCUS, this.getTag()));
			}
			this.invalidate();
		}
		
	}

	/**
	 * Map a touch to a selection. Short buffers sit at the bottom with empty padding above;
	 * taps there (or past the end of a short line) used to return null and never open the widget.
	 */
	private TextTree.Selection resolveSelectionForPoint(final int line, final int column) {
		final int broken = mBuffer.getBrokenLineCount();
		if (broken <= 0) {
			return null;
		}
		int useLine = line;
		int useColumn = column;
		if (useLine < 0) {
			useLine = 0;
		} else if (useLine >= broken) {
			useLine = broken - 1;
		}
		if (useColumn < 0) {
			useColumn = 0;
		}

		TextTree.Selection sel = mBuffer.getSelectionForPoint(useLine, useColumn);
		if (sel != null) {
			return sel;
		}
		if (useColumn != 0) {
			sel = mBuffer.getSelectionForPoint(useLine, 0);
			if (sel != null) {
				return sel;
			}
		}
		for (int d = 1; d < broken; d++) {
			final int newer = useLine - d;
			if (newer >= 0) {
				sel = mBuffer.getSelectionForPoint(newer, 0);
				if (sel != null) {
					return sel;
				}
			}
			final int older = useLine + d;
			if (older < broken) {
				sel = mBuffer.getSelectionForPoint(older, 0);
				if (sel != null) {
					return sel;
				}
			}
		}
		return null;
	}

	/** Convert raw view touch Y to a buffer broken-line index (0 = live / newest). */
	private int touchYToBufferLine(final float touchY) {
		// Short content always draws at the live edge; keep scroll delta at 0 so empty
		// padding does not invent huge line numbers before clamp.
		double scrollDelta = mScrollback - SCROLL_MIN;
		if (mBuffer.getBrokenLineCount() <= mCalculatedLinesInWindow) {
			scrollDelta = 0;
		}
		final int pad = textPadTop();
		final float localY = Math.max(0f, touchY - pad);
		final float y;
		if (mNewestAtTop) {
			// Newest at top: line 0 near top of content area.
			y = (float) (localY + scrollDelta);
		} else {
			// Classic: newest at bottom of content area.
			y = (float) (contentHeight() - localY + scrollDelta);
		}
		if (mPrefLineSize <= 0) {
			return 0;
		}
		return (int) Math.floor(y / (float) mPrefLineSize);
	}

	/**
	 * Map iterator-space baseline Y (content-height space) to on-screen baseline.
	 * Applies top padding and optional newest-at-top flip.
	 */
	private float screenBaselineY(final float logicalY) {
		final int pad = textPadTop();
		final int ch = contentHeight();
		if (!mNewestAtTop) {
			return pad + logicalY;
		}
		return pad + (ch - logicalY + mPrefLineSize);
	}

	/** Screen Y for a buffer line index (0 = newest), used by selection chrome. */
	private int bufferLineToScreenY(final int line, final float withinLineOffset) {
		final double scrollDelta = mScrollback - SCROLL_MIN;
		final float logicalFromBottom = line * mPrefLineSize + withinLineOffset - (float) scrollDelta;
		final int pad = textPadTop();
		final int ch = contentHeight();
		if (mNewestAtTop) {
			return pad + (int) logicalFromBottom;
		}
		return pad + (int) (ch - logicalFromBottom);
	}

	/**
	 * Unclamped bottom inset: the player's own padding, the keyboard-only extra
	 * while the keyboard is up, and any drawer covering this window.
	 *
	 * <p>The two player settings add up when the keyboard is out, which is what
	 * "both at once" in the options means.
	 */
	private int rawPadBottom() {
		int raw = mBottomPadding + Math.max(0, mDrawerInsetBottom);
		if (mImeLiftPx > 0) {
			raw += mBottomPaddingIme;
		}
		return raw;
	}

	/**
	 * Clamped bottom inset for game text (pixels).
	 *
	 * <p>Top wins ties: the top pad is the one that clears a camera cutout, so a
	 * bottom pad big enough to starve the text gives way instead.
	 */
	private int textPadBottom() {
		final int raw = rawPadBottom();
		if (raw <= 0 || mHeight <= 0) {
			return 0;
		}
		final int minText = Math.max(1, mPrefLineSize);
		final int maxPad = Math.max(0, mHeight - minText - clampedPadTopRaw());
		return Math.min(raw, maxPad);
	}

	/** Top pad clamped against the view alone, before the bottom pad is known. */
	private int clampedPadTopRaw() {
		final int raw = mTopPadding + mDrawerInsetTop;
		if (raw <= 0 || mHeight <= 0) {
			return 0;
		}
		final int minText = Math.max(1, mPrefLineSize);
		return Math.min(raw, Math.max(0, mHeight - minText));
	}

	/** Clamped top inset for game text (pixels). */
	private int textPadTop() {
		final int raw = clampedPadTopRaw();
		if (raw <= 0) {
			return 0;
		}
		final int minText = Math.max(1, mPrefLineSize);
		final int maxPad = Math.max(0, mHeight - minText - textPadBottom());
		return Math.min(raw, maxPad);
	}

	/** Drawable text area height after top/bottom padding. */
	private int contentHeight() {
		return Math.max(Math.max(1, mPrefLineSize),
				mHeight - textPadTop() - textPadBottom());
	}

	/**
	 * Re-measure the text area after an inset changed, keeping the player's
	 * place in the scrollback.
	 *
	 * <p>{@link #SCROLL_MIN} is derived from {@link #contentHeight()}, so every
	 * caller that moves an inset has to shift {@link #mScrollback} by the same
	 * amount or the view jumps. One copy, called from all of them.
	 */
	private void reflowForInsetChange() {
		if (mWidth <= 0 || mHeight <= 0) {
			return;
		}
		calculateCharacterFeatures(mWidth, mHeight);
		final double slack = 5 * Window.this.getResources().getDisplayMetrics().density;
		if (mScrollback == SCROLL_MIN) {
			SCROLL_MIN = contentHeight() - slack;
			mScrollback = SCROLL_MIN;
		} else {
			double oldmin = SCROLL_MIN;
			SCROLL_MIN = contentHeight() - slack;
			mScrollback -= oldmin - SCROLL_MIN;
		}
	}

	/**
	 * Tell this window how far the soft keyboard has lifted it, so the
	 * keyboard-only bottom padding can come and go with the keyboard.
	 *
	 * <p>Pushed from {@link ChromeController#applyImeChromeLift}, which already
	 * owns the one authority for keyboard height — this must not grow a second
	 * estimator of its own.
	 */
	public final void setImeLiftPx(final int liftPx) {
		final int lift = liftPx < 0 ? 0 : liftPx;
		if (lift == mImeLiftPx) {
			return;
		}
		final boolean wasUp = mImeLiftPx > 0;
		mImeLiftPx = lift;
		if (mBottomPaddingIme <= 0 || wasUp == (lift > 0)) {
			return;
		}
		reflowForInsetChange();
		invalidate();
	}

	/**
	 * Cover game text with drawer overlays without changing view layout
	 * (keeps button_window full-bleed). Pass zeros to clear.
	 */
	public final void setDrawerTextInsets(final int topPx, final int bottomPx) {
		int t = topPx < 0 ? 0 : topPx;
		int b = bottomPx < 0 ? 0 : bottomPx;
		if (t == mDrawerInsetTop && b == mDrawerInsetBottom) {
			return;
		}
		mDrawerInsetTop = t;
		mDrawerInsetBottom = b;
		reflowForInsetChange();
		invalidate();
		android.content.Context ctx = getContext();
		if (ctx instanceof MainWindow) {
			((MainWindow) ctx).scheduleRenawsAfterChromeRefresh();
		}
	}

	private void endTextSelectionMode(final View v) {
		v.setOnTouchListener(null);
		if (theSelection != null) {
			theSelection.start = null;
			theSelection.end = null;
		}
		theSelection = null;
		selectedSelector = null;
		Window.this.flushBuffer();
		Window.this.setBufferText(false);
		if (mMainWindowHandler != null) {
			mMainWindowHandler.sendEmptyMessage(MainWindow.MESSAGE_TEXTSELECTION_RELEASE);
		}
		v.invalidate();
	}
	
	/** Called from the MainWindow when the system encoding changes. 
	 * 
	 * @param value The new encoding value.
	 */
	protected final void updateEncoding(final String value) {
		mBuffer.setEncoding(value);
	}

	/** Telnet ECHO changed hands. Only the main window has an input bar to hide,
	 * so the base class does nothing.
	 *
	 * @param enabled true when the client echoes locally (normal typing), false
	 *        while the server has taken echoing over — a password prompt.
	 */
	protected void onLocalEchoChanged(final boolean enabled) {
		// The input bar belongs to the activity, not to this view.
		if (mParent != null) {
			mParent.setLocalEchoOff(!enabled);
		}
	}

	/** Implementation of the settings handler routine to handle when settings change. 
	 * 
	 * @param key The key of the setting that changed.
	 * @param value The new value of the setting.
	 */
	protected final void doUpdateSetting(final String key, final String value) {
		mSettings.setOption(key, value);
	}
	
	/** Updates relevant draw routine structures and temporary values. Measures one character and handles the fit routine. 
	 * 
	 * @param width Window width.
	 * @param height Window height.
	 */
	public final void calculateCharacterFeatures(final int width, final int height) {
		
		if (height == 0 && width == 0) {
			return;
		}
		mCalculatedLinesInWindow = (int) (contentHeight() / mPrefLineSize);
		
		featurePaint.setTypeface(mPrefFont);
		applyTerminalFontFeatures(featurePaint);
		featurePaint.setTextSize(mPrefFontSize);
		// Use the wider of common mono glyphs so proportional fallbacks still grid.
		float w = featurePaint.measureText("W");
		float m = featurePaint.measureText("M");
		float sp = featurePaint.measureText(" ");
		float zero = featurePaint.measureText("0");
		mOneCharWidth = (int) Math.ceil(Math.max(Math.max(w, m), Math.max(sp, zero)));
		if (mOneCharWidth < 1) {
			mOneCharWidth = 1;
		}
		mCalculatedRowsInWindow = width / mOneCharWidth;
		if (mCalculatedRowsInWindow < 1) {
			mCalculatedRowsInWindow = 1;
		}
		// Columns the buffer wraps at. Deliberately NOT mCalculatedRowsInWindow:
		// getCalculatedColumns() hands that field to MainWindow.reportLiveNawsToService,
		// so widening it would tell the MUD "my terminal is 180 columns" while the
		// screen still shows 90 — the server would then wrap for a width the player
		// cannot see. The physical value stays the truth we send; only wrapping and
		// drawing use the wider canvas.
		mWrapColumns = (int) (mCalculatedRowsInWindow * mCanvasWidthFactor);
		if (mWrapColumns < mCalculatedRowsInWindow) {
			mWrapColumns = mCalculatedRowsInWindow;
		}
		if (mCalculatedLinesInWindow < 1) {
			mCalculatedLinesInWindow = 1;
		}
		
		mSelectionIndicatorPaint.setTextSize(mSelectionIndicatorFontSize);
		mSelectionIndicatorPaint.setTypeface(mPrefFont);
		applyTerminalFontFeatures(mSelectionIndicatorPaint);
		mSelectionIndicatorPaint.setAntiAlias(true);
		mSelectionCharacterWidth = (int) Math.ceil(mSelectionIndicatorPaint.measureText("W"));
		selectionIndicatorVectorX = mOneCharWidth + mSelectionIndicatorHalfDimension;
		if (automaticBreaks) {
			this.setLineBreaks(0);
		}
		clampScrollX();
		
		if (mBuffer.getBrokenLineCount() == 0) {
			jumpToZero();
		}
		
	}

	/** Pixel width of {@code charCount} terminal cells (ANSI maps need a fixed grid). */
	private float cellWidth(final int charCount) {
		if (charCount <= 0 || mOneCharWidth <= 0) {
			return 0f;
		}
		return mOneCharWidth * (float) charCount;
	}

	/** Pixel width of a text unit on the fixed cell grid.
	 * Display cells come from {@link CellWidth} so a wide glyph's background
	 * and link box cover both columns. {@code text.charcount} is still the
	 * code-point count used for wrap/NAWS/selection columns. */
	private float cellWidth(final TextTree.Text text) {
		if (text == null) {
			return 0f;
		}
		final String s = text.getString();
		if (s == null || s.length() == 0) {
			return 0f;
		}
		return cellWidth(CellWidth.cells(s));
	}

	/** Pixel width of {@code s[0, utf16End)} on the display grid. */
	private float cellWidthPrefix(final String s, final int utf16End) {
		return cellWidth(CellWidth.cells(s, 0, utf16End));
	}

	/** Pixel width of {@code s[utf16Start, utf16End)} on the display grid. */
	private float cellWidthSpan(final String s, final int utf16Start, final int utf16End) {
		return cellWidth(CellWidth.cells(s, utf16Start, utf16End));
	}

	/**
	 * Draw {@code s} on a fixed terminal grid starting at {@code x}.
	 * ASCII and Block Elements occupy one cell; East-Asian-wide and emoji
	 * occupy two ({@link CellWidth}). Combining marks draw on the previous
	 * glyph and do not advance. Wrap/NAWS still count one column per code
	 * point, so a full row of emoji can overflow the canvas.
	 * Block elements (U+2580–U+259F) are painted geometrically so maps stay
	 * aligned even when the active font lacks those glyphs.
	 * Returns the pixel advance for the whole run.
	 */
	/** Reused so drawing a run of text does not allocate; getFontMetrics()
	 * returns a fresh object on every call. */
	private final Paint.FontMetrics mGridFontMetrics = new Paint.FontMetrics();

	/** Widths for a run of text, reused for the same reason. */
	private float[] mGridWidths = new float[256];

	/** Printable ASCII, probed once per font to learn whether it all sits on the grid. */
	private static final String ASCII_PROBE = buildAsciiProbe();

	private static String buildAsciiProbe() {
		final StringBuilder sb = new StringBuilder(0x7F - 0x20);
		for (int cp = 0x20; cp < 0x7F; cp++) {
			sb.append((char) cp);
		}
		return sb.toString();
	}

	// What the cached grid facts below were measured against. Advances depend on the
	// typeface and the text size, so those are the key -- not the Paint instance, since
	// drawing alternates between the body paint and the link paint.
	private Typeface mGridCacheTypeface = null;
	private float mGridCacheTextSize = -1f;
	private int mGridCacheCell = -1;
	private boolean mGridAsciiUniform = false;

	/**
	 * Refresh the cached font metrics and the "all printable ASCII is exactly one cell
	 * wide" answer, but only when the font actually changed. Both used to be recomputed
	 * for every drawn word: getTextWidths shapes the string through the text engine, so
	 * an ordinary line of output was being shaped twice, once to measure and once to draw.
	 */
	private void ensureGridCache(final Paint paint) {
		final Typeface tf = paint.getTypeface();
		final float ts = paint.getTextSize();
		if (tf == mGridCacheTypeface && ts == mGridCacheTextSize && mOneCharWidth == mGridCacheCell) {
			return;
		}
		mGridCacheTypeface = tf;
		mGridCacheTextSize = ts;
		mGridCacheCell = mOneCharWidth;
		paint.getFontMetrics(mGridFontMetrics);
		final int probeLen = ASCII_PROBE.length();
		if (mGridWidths.length < probeLen) {
			mGridWidths = new float[probeLen];
		}
		paint.getTextWidths(ASCII_PROBE, mGridWidths);
		boolean uniform = mOneCharWidth > 0;
		for (int i = 0; i < probeLen; i++) {
			if (Math.abs(mGridWidths[i] - mOneCharWidth) >= 0.01f) {
				uniform = false;
				break;
			}
		}
		mGridAsciiUniform = uniform;
	}

	/** True when the unit is nothing but spaces. */
	private static boolean isAllSpaces(final String s, final int len) {
		for (int i = 0; i < len; i++) {
			if (s.charAt(i) != ' ') {
				return false;
			}
		}
		return true;
	}

	/** True when every character is printable ASCII, so the probe above covers it. */
	private static boolean isPlainAscii(final String s, final int len) {
		for (int i = 0; i < len; i++) {
			final char ch = s.charAt(i);
			if (ch < 0x20 || ch > 0x7E) {
				return false;
			}
		}
		return true;
	}

	private float drawTextOnGrid(final Canvas c, final String s, final float x, final float y,
			final Paint paint) {
		if (s == null || s.length() == 0) {
			return 0f;
		}
		final float baseline = screenBaselineY(y);
		final float cell = mOneCharWidth;

		if (blinkGlyphsHiddenThisUnit()) {
			return gridAdvance(s);
		}

		// A space paints nothing, so the only reason to hand one to the canvas is a
		// decoration that spans it. Backgrounds, selection and search matches are all
		// drawn as their own rectangles by the caller, which leaves underline. About
		// half of all units are whitespace -- every gap between two words is its own
		// unit -- so skipping them halves the number of draw calls a line costs.
		final int spaceLen = s.length();
		if (isAllSpaces(s, spaceLen)
				&& !paint.isUnderlineText() && !paint.isStrikeThruText()
				&& !mSgr.doubleUnderline()) {
			return cell * spaceLen;
		}

		ensureGridCache(paint);
		final boolean overlayPass = paint == mWeightPaint;

		// Place each glyph on the cell origin. Clip the run so a wide fallback
		// cannot spill into the next unit.
		final float drawnWidth;
		if (mGridAsciiUniform && isPlainAscii(s, s.length())) {
			final Paint.FontMetrics fm = mGridFontMetrics;
			final float textTop = baseline + fm.ascent;
			final float textBot = baseline + fm.descent + 1f;
			c.save();
			c.clipRect(x, textTop, x + s.length() * cell, textBot);
			for (int i = 0; i < s.length(); i++) {
				c.drawText(s, i, i + 1, x + i * cell, baseline, paint);
			}
			c.restore();
			drawnWidth = cell * s.length();
		} else {

			final Paint.FontMetrics fm = mGridFontMetrics;
			// Block fills use the full line box; text must keep room below the baseline
			// for descenders (y, g, j, p) — clipping to baseline cut them off.
			final float lineTop = baseline - mPrefLineSize + Math.max(0, mPrefLineExtra);
			final float lineBot = baseline + Math.max(fm.descent + 1f, (float) mPrefLineExtra);
			final float textTop = baseline + fm.ascent;
			final float textBot = baseline + fm.descent + 1f;

			final int len = s.length();
			if (mGridWidths.length < len) {
				mGridWidths = new float[len];
			}
			paint.getTextWidths(s, mGridWidths);

			float cursor = x;
			float lastGlyphX = x;
			int lastGlyphCols = 1;
			int i = 0;
			while (i < len) {
				final int cp = s.codePointAt(i);
				final int charCount = Character.charCount(cp);
				final int cols = CellWidth.cells(cp);
				final boolean isBlock = cp >= 0x2580 && cp <= 0x259F;
				final float w = mGridWidths[i];

				if (cols == 0) {
					c.save();
					c.clipRect(lastGlyphX, textTop,
							lastGlyphX + lastGlyphCols * cell, textBot);
					c.drawText(s, i, i + charCount, lastGlyphX, baseline, paint);
					c.restore();
					i += charCount;
					continue;
				}

				final boolean exactCell = cols == 1 && !isBlock && Math.abs(w - cell) < 0.01f;
				final boolean fitsCell = cols == 1 && !isBlock && w > 0f && w <= cell + 0.5f;

				if (exactCell || fitsCell) {
					c.drawText(s, i, i + charCount, cursor, baseline, paint);
				} else if (cols == 2) {
					c.save();
					c.clipRect(cursor, lineTop, cursor + 2f * cell, lineBot);
					c.drawText(s, i, i + charCount, cursor, baseline, paint);
					c.restore();
				} else if (isBlock) {
					if (!overlayPass) {
						c.save();
						c.clipRect(cursor, lineTop, cursor + cell, lineBot);
						drawBlockElement(c, cp, cursor, lineTop, lineBot, cell, paint);
						c.restore();
					}
				} else {
					c.save();
					c.clipRect(cursor, textTop, cursor + cell, textBot);
					c.drawText(s, i, i + charCount, cursor, baseline, paint);
					c.restore();
				}
				lastGlyphX = cursor;
				lastGlyphCols = cols;
				cursor += cols * cell;
				i += charCount;
			}
			drawnWidth = cursor - x;
		}
		if (!overlayPass) {
			drawDoubleUnderlineHairline(c, x, y, drawnWidth, paint);
		}
		if (paintWeight() && !overlayPass) {
			mWeightPaint.setTextSize(paint.getTextSize());
			mWeightPaint.setAntiAlias(true);
			mWeightPaint.setColor(paint.getColor());
			mWeightPaint.setTypeface(Typeface.create(mPrefFont, Typeface.BOLD));
			applyTerminalFontFeatures(mWeightPaint);
			mWeightPaint.setFakeBoldText(true);
			mWeightPaint.setTextSkewX(paint.getTextSkewX());
			drawTextOnGrid(c, s, x, y, mWeightPaint);
		}
		return drawnWidth;
	}

	private float gridAdvance(final String s) {
		if (s == null || s.length() == 0) {
			return 0f;
		}
		if (mGridAsciiUniform && isPlainAscii(s, s.length())) {
			return mOneCharWidth * s.length();
		}
		return mOneCharWidth * CellWidth.cells(s);
	}

	private boolean blinkGlyphsHiddenThisUnit() {
		if (mSgr.fastBlink()) {
			mBlinkSawThisFrame = true;
			mBlinkFastSawThisFrame = true;
			return mBlinkHiddenFast;
		}
		if (mSgr.blink()) {
			mBlinkSawThisFrame = true;
			return mBlinkHiddenSlow;
		}
		return false;
	}

	private void drawDoubleUnderlineHairline(final Canvas c, final float x, final float y,
			final float width, final Paint paint) {
		if (!mSgr.doubleUnderline() || width <= 0f || c == null || paint == null) {
			return;
		}
		// Two equal hairlines. A density-tall rect under Paint's native underline
		// was one thin stroke plus a bar (measured on the baudtest Doubleul row).
		final float stroke = Math.max(1f, mDensity * 0.35f);
		final float gap = Math.max(2f, mDensity);
		final float y1 = screenBaselineY(y) + Math.max(1f, mDensity);
		final float y2 = y1 + stroke + gap;
		c.drawRect(x, y1, x + width, y1 + stroke, paint);
		c.drawRect(x, y2, x + width, y2 + stroke, paint);
	}

	private void scheduleBlinkIfNeeded() {
		final boolean scrollingNow = mFingerDown
				|| Math.abs(mFlingVelocity) > FLING_STOP_VELOCITY;
		if (!mBlinkSawThisFrame || !windowShowing || scrollingNow) {
			removeCallbacks(mBlinkInvalidate);
			mBlinkPosted = false;
			return;
		}
		if (mBlinkPosted) {
			return;
		}
		mBlinkPosted = true;
		final long delay = mBlinkFastSawThisFrame ? SgrStyle.BLINK_FAST_MS
				: SgrStyle.BLINK_SLOW_MS;
		postDelayed(mBlinkInvalidate, delay);
	}

	/** Reused by {@link #drawInlineImage}; onDraw must not allocate per frame. */
	private final android.graphics.Rect mInlineImageSrc = new android.graphics.Rect();
	private final android.graphics.RectF mInlineImageDst = new android.graphics.RectF();
	private android.graphics.Paint mInlineImagePaint;
	/** True once this Window has asked the image store to tell it about loads. */
	private boolean mInlineImageListening;

	/**
	 * Draw a picture over the block of lines a marker reserved for it.
	 *
	 * <p>The block is the marker's own line and the blank lines after it, so its
	 * height is a whole number of line heights and no line is ever a different
	 * size from its neighbours. That is the whole trick: scrolling, selection,
	 * link hit boxes and the scrollbar all assume a uniform line height, and
	 * this leaves every one of those assumptions true.
	 *
	 * <p>Both directions are handled. With "newest at top" on, the lines after
	 * the marker are drawn <i>above</i> it, so the top of the box is whichever
	 * end came out smaller on screen rather than the marker's own line.
	 *
	 * <p>Nothing is drawn while the picture is still loading, or if it never
	 * arrived. The reserved lines are simply blank, which is what they already
	 * were.
	 */
	private void drawInlineImage(final Canvas c, final Line line, final float baselineY) {
		String key = line.getInlineImageKey();
		if (key == null) {
			return;
		}
		android.graphics.Bitmap bmp = FrameImageStore.get().getBitmap(key);
		if (bmp == null || bmp.isRecycled()) {
			return;
		}
		int lines = line.getInlineImageLines();
		if (lines < 1) {
			lines = 1;
		}
		float thisEnd = cellTop(baselineY);
		float otherEnd = cellTop(baselineY + ((lines - 1) * mPrefLineSize));
		float top = Math.min(thisEnd, otherEnd);
		float height = lines * mPrefLineSize;
		// A hair of margin so a light picture does not run into the screen edge.
		float left = 2 * mDensity;
		float width = mWidth - (left * 2);
		if (width <= 0 || height <= 0) {
			return;
		}

		// Fit inside the box and keep the proportions. A map stretched to the
		// shape of a text block is a map you cannot read distances off.
		float scale = Math.min(width / bmp.getWidth(), height / bmp.getHeight());
		float drawW = bmp.getWidth() * scale;
		float drawH = bmp.getHeight() * scale;
		float drawX = left + ((width - drawW) / 2f);
		float drawY = top + ((height - drawH) / 2f);

		if (mInlineImagePaint == null) {
			mInlineImagePaint = new android.graphics.Paint();
			mInlineImagePaint.setFilterBitmap(true);
			mInlineImagePaint.setAntiAlias(true);
		}
		mInlineImageSrc.set(0, 0, bmp.getWidth(), bmp.getHeight());
		mInlineImageDst.set(drawX, drawY, drawX + drawW, drawY + drawH);
		c.drawBitmap(bmp, mInlineImageSrc, mInlineImageDst, mInlineImagePaint);
	}

	/** Repaint when a picture finishes loading after the line was already drawn. */
	private final FrameImageStore.Listener mInlineImageRepaint = new FrameImageStore.Listener() {
		@Override
		public void onFrameImageChanged(String key) {
			postInvalidate();
		}
	};

	/** Top of the ANSI background / block cell for baseline {@code y}. */
	private float cellTop(final float baselineY) {
		final float y = screenBaselineY(baselineY);
		return y - mPrefLineSize + Math.max(0, mPrefLineExtra);
	}

	/** Bottom of the ANSI background cell — includes descender room. */
	private float cellBottom(final float baselineY) {
		return screenBaselineY(baselineY) + Math.max(2, mPrefLineExtra + 2);
	}

	/**
	 * Paint Unicode Block Elements into a single cell. Returns true if handled.
	 * Shades are opaque blends (not alpha) so they keep a hard cell edge on colored backgrounds.
	 * @see <a href="https://www.unicode.org/charts/PDF/U2580.pdf">U+2580 Block Elements</a>
	 */
	private boolean drawBlockElement(final Canvas c, final int cp, final float left,
			final float top, final float bot, final float cell, final Paint paint) {
		if (cp < 0x2580 || cp > 0x259F) {
			return false;
		}
		final int oldStyle = paint.getStyle() == Paint.Style.FILL ? 0
				: (paint.getStyle() == Paint.Style.STROKE ? 1 : 2);
		paint.setStyle(Paint.Style.FILL);
		final float h = bot - top;
		final float right = left + cell;
		switch (cp) {
		case 0x2588: // FULL BLOCK
			c.drawRect(left, top, right, bot, paint);
			break;
		case 0x2580: // UPPER HALF
			c.drawRect(left, top, right, top + h * 0.5f, paint);
			break;
		case 0x2584: // LOWER HALF
			c.drawRect(left, top + h * 0.5f, right, bot, paint);
			break;
		case 0x258C: // LEFT HALF
			c.drawRect(left, top, left + cell * 0.5f, bot, paint);
			break;
		case 0x2590: // RIGHT HALF
			c.drawRect(left + cell * 0.5f, top, right, bot, paint);
			break;
		case 0x2591: // LIGHT SHADE
		case 0x2592: // MEDIUM SHADE
		case 0x2593: // DARK SHADE
			{
				final int amount = (cp == 0x2591) ? 64 : (cp == 0x2592) ? 128 : 192;
				final int color = paint.getColor();
				paint.setColor(LightPaper.shadeTowardPaper(color, amount, mLightPaper,
						mLightPaperShade));
				c.drawRect(left, top, right, bot, paint);
				paint.setColor(color);
			}
			break;
		default:
			{
				// Remaining partial blocks (⅛…⅞): approximate by filled fraction.
				if (cp >= 0x2581 && cp <= 0x2587) {
					final float frac = (cp - 0x2580) / 8f; // lower N/8
					c.drawRect(left, bot - h * frac, right, bot, paint);
				} else if (cp >= 0x2589 && cp <= 0x258F) {
					c.drawRect(left, top, left + cell * ((cp - 0x2588) / 8f), bot, paint);
				} else {
					c.drawRect(left, top, right, bot, paint);
				}
			}
			break;
		}
		if (oldStyle == 1) {
			paint.setStyle(Paint.Style.STROKE);
		} else if (oldStyle == 2) {
			paint.setStyle(Paint.Style.FILL_AND_STROKE);
		}
		return true;
	}
	

	/** Setter for mName.
	 * 
	 * @param name New name value.
	 */
	public final void setName(final String name) {
		mName = name;
	}
	
	/** Getter for mName. 
	 * 
	 * @return The name of this window.
	 */
	public final String getName() {
		return mName;
	}
	

	@Override
	public final boolean onTouchEvent(final MotionEvent t) {
		final int action = t.getActionMasked();
		// Two fingers on the game text → open copy widget (one-finger long-press does not).
		if (mTextSelectionEnabled && theSelection == null
				&& action == MotionEvent.ACTION_POINTER_DOWN
				&& t.getPointerCount() >= 2
				&& mBuffer.getBrokenLineCount() != 0) {
			mHandler.removeMessages(MESSAGE_STARTSELECTION);
			mFlingVelocity = 0.0f;
			// Prefer the first finger's current position (not a stale ACTION_DOWN that may
			// have been above short text / empty padding and produced a null hit).
			int selLine = mTouchDownLine;
			int selCol = mTouchDownColumn;
			try {
				int idx0 = t.findPointerIndex(t.getPointerId(0));
				if (idx0 >= 0) {
					float x0 = t.getX(idx0);
					float y0 = t.getY(idx0);
					selLine = touchYToBufferLine(y0);
					selCol = mOneCharWidth > 0
							? (int) Math.floor((x0 + mScrollX) / (float) mOneCharWidth) : 0;
				}
			} catch (Exception ignored) {
			}
			// Where the second finger landed. Selecting everything between the
			// two is what people expect from a two-finger grab, and it saves
			// dragging the widget across the whole span afterwards; the widget
			// then adjusts the edges as before.
			int selLine2 = selLine;
			int selCol2 = selCol;
			boolean haveSecond = false;
			try {
				int idx1 = t.findPointerIndex(t.getPointerId(1));
				if (idx1 >= 0) {
					selLine2 = touchYToBufferLine(t.getY(idx1));
					selCol2 = mOneCharWidth > 0
							? (int) Math.floor((t.getX(idx1) + mScrollX) / (float) mOneCharWidth) : 0;
					haveSecond = true;
				}
			} catch (Exception ignored) {
			}
			if (haveSecond && (selLine2 != selLine || selCol2 != selCol)) {
				startSelectionBetween(selLine, selCol, selLine2, selCol2);
			} else {
				startSelection(selLine, selCol);
			}
			return true;
		}

		int pointerIndex = (t.getAction() & MotionEvent.ACTION_POINTER_ID_MASK) >> MotionEvent.ACTION_POINTER_ID_SHIFT;
		int pointerId = t.getPointerId(pointerIndex);
		
		if (pointer > 0 && pointerId != pointer) {
			//but invalidate this anyway
			this.invalidate();
			return false;
		}
			//normal
		if (!scrollingEnabled) {
			return false;
		}
		int index = t.findPointerIndex(pointerId);
		start_x = Float.valueOf(t.getX(index));
		start_x = start_x + 1;
		
		if (mBuffer.getBrokenLineCount() != 0) {
			Rect rect = new Rect();
			if (!mFingerDown) {
				
				rect.top = 0;
				rect.left = 0;
				rect.right = mWidth;
				rect.bottom = mHeight;
				
				
				Point point = new Point();
				point.x = (int) t.getX();
				point.y = (int) t.getY();
				if (!rect.contains((int) t.getX(), (int) t.getY())) {
					return false;
				}
			}
			
			synchronized (mToken) {
			if (action == MotionEvent.ACTION_CANCEL
					|| action == MotionEvent.ACTION_POINTER_DOWN) {
				// Nothing here handles either of these, so ACTION_UP may never
				// arrive: a parent taking the gesture over, or a second finger
				// starting a pinch. A hold left pending would then open a menu
				// over a gesture the player had already turned into something
				// else.
				cancelTapLongPress();
				mTapLongPressFired = false;
				dismissLoupe();
			}
			if (action == MotionEvent.ACTION_DOWN) {
				pointer = pointerId;
				start_x = Float.valueOf(t.getX(index));
				mStartY = Float.valueOf(t.getY(index));
				mMoveLastY = mStartY.floatValue();
				mMoveLastTime = t.getEventTime();
				//calculate row/col
				float x = t.getX(index);
				float y = t.getY(index);
				mFlingVelocity = 0.0f;
				mFingerDown = true;
				finger_down_to_up = false;
				mLastFrameTime = 0;
				
				// Only hit-test links when idle — boxes are stale during drag/fling.
				mTouchInLink = -1;
				mTouchInTapWord = -1;
				if (Math.abs(mFlingVelocity) <= FLING_STOP_VELOCITY) {
					for (int tmpCount = 0; tmpCount < linkBoxes.size(); tmpCount++) {
						if (linkBoxes.get(tmpCount).getBox().contains(start_x.intValue(), mStartY.intValue())) {
							mTouchInLink = tmpCount;
						}
					}
					for (int tmpCount = 0; tmpCount < tapBoxes.size(); tmpCount++) {
						if (tapBoxes.get(tmpCount).getBox().contains(start_x.intValue(), mStartY.intValue())) {
							mTouchInTapWord = tmpCount;
						}
					}
				}
				
				scheduleTapLongPress();

				mDragAxis = DRAG_UNDECIDED;
				mMoveLastX = Float.valueOf(x);
				mDownX = Float.valueOf(x);
				mTouchDownLine = touchYToBufferLine(y);
				mTouchDownColumn = mOneCharWidth > 0
						? (int) Math.floor(x / (float) mOneCharWidth) : 0;
				// One-finger long-press no longer opens the copy widget.
				
				if (homeWidgetShowing) {
					if (mHomeWidgetRect.contains((int) x, (int) t.getY())) {
						homeWidgetFingerDown = true;
					}
				}
				
			}
			
			if (action == MotionEvent.ACTION_MOVE && mTapLongPress != null
					&& mStartY != null && mDownX != null) {
				// Past the same 8 dp the axis lock uses: this is a scroll, not a
				// hold. Cancelling here and not only in the axis block, because
				// that block is skipped entirely when the canvas is no wider than
				// the window — the ordinary 100% case, where the only gesture is
				// the vertical scroll and it must cancel a hold just the same.
				float heldDx = t.getX(index) - mDownX.floatValue();
				float heldDy = t.getY(index) - mStartY.floatValue();
				float holdSlop = 8f * mDensity;
				if (Math.abs(heldDx) > holdSlop || Math.abs(heldDy) > holdSlop) {
					cancelTapLongPress();
				}
			}

			if (action == MotionEvent.ACTION_MOVE && (mTapLongPressFired || mLoupeActive)) {
				// The hold has already opened the menu or the loupe. Letting the
				// rest of this method run would scroll the text out from under
				// it. The gesture belongs to that overlay now.
				if (mLoupeActive) {
					mLoupeFingerX = t.getX(index);
					mLoupeFingerY = t.getY(index);
					if (mLoupeMerged != null) {
						TapLoupe.Target now = TapLoupe.underFinger(mLoupeMerged,
								(int) mLoupeFingerX, (int) mLoupeFingerY,
								TapLoupe.radiusPx(mPrefLineSize, mDensity),
								Math.max(1, mPrefLineSize));
						if (now != null) {
							mLoupeSelected = now;
						}
					}
					invalidate();
				}
				return true;
			}

			if (action == MotionEvent.ACTION_MOVE && maxScrollX() > 0f
					&& mDragAxis == DRAG_UNDECIDED
					&& mStartY != null && mDownX != null) {
				// Decide the axis once per gesture and keep it. Re-deciding per
				// event let a sideways drag also feed the vertical block below,
				// which moves mScrollback — the buffer would creep while the
				// player scrolls across.
				float dx = t.getX(index) - mDownX.floatValue();
				float dy = t.getY(index) - mStartY.floatValue();
				float slop = 8f * mDensity;
				if (Math.abs(dx) >= slop || Math.abs(dy) >= slop) {
					mDragAxis = Math.abs(dx) > Math.abs(dy) ? DRAG_HORIZONTAL : DRAG_VERTICAL;
				}
			}

			if (action == MotionEvent.ACTION_MOVE && mDragAxis == DRAG_HORIZONTAL) {
				// Drag the canvas with the finger: moving left shows what is
				// further right, so the offset grows as x falls.
				float nowX = t.getX(index);
				if (mMoveLastX != null) {
					scrollHorizontallyBy(mMoveLastX.floatValue() - nowX);
				}
				mMoveLastX = Float.valueOf(nowX);
				mFlingVelocity = 0.0f;
				mTouchInLink = -1;
				return true;
			}

			if (action == MotionEvent.ACTION_MOVE) {
				float nowY = t.getY(index);
				long nowtime = t.getEventTime();
				float time = (nowtime - mMoveLastTime) / 1000.0f;
				if (time < 0.001f) {
					time = 0.001f;
				}
				float dist = nowY - mMoveLastY;
				float velocity = dist / time;
				// Cap what a finger can plausibly do first, then apply the sensitivity
				// gain — clamping afterwards would make every setting above Normal
				// saturate at the same speed and feel identical.
				if (velocity > MAX_VELOCITY) {
					velocity = MAX_VELOCITY;
				} else if (velocity < -MAX_VELOCITY) {
					velocity = -MAX_VELOCITY;
				}
				mFlingVelocity = velocity * mScrollSensitivity;

				if (mStartY != null
						&& Math.abs(nowY - mStartY) >= Math.max(mPrefLineSize * 2f, 32f * mDensity)) {
					mTouchInLink = -1;
				}

				if (dist != 0f) {
					final double delta = (mNewestAtTop ? -dist : dist) * mScrollSensitivity;
					mScrollback = mScrollback + delta;
					if (mScrollback < SCROLL_MIN) {
						mScrollback = SCROLL_MIN;
					} else if (mScrollback >= ((mBuffer.getBrokenLineCount() * mPrefLineSize))) {
						mScrollback = (double) ((mBuffer.getBrokenLineCount() * mPrefLineSize));
					}
					diff_amount = 0;
					mMoveLastY = nowY;
					mMoveLastTime = nowtime;
				} else {
					mMoveLastTime = nowtime;
				}
			}						
			
			if (action == MotionEvent.ACTION_UP) {
		        pointer = -1;
		        mFingerDown = false;
		        finger_down_to_up = true;
		        boolean wasHorizontal = mDragAxis == DRAG_HORIZONTAL;
		        mDragAxis = DRAG_UNDECIDED;
		        mMoveLastX = null;
		        mDownX = null;
		        if (wasHorizontal) {
		        	// Sideways drags end here: no fling, no tap, no keyboard dismiss.
		        	this.invalidate();
		        	return true;
		        }

				float upX = t.getX(index);
				float upY = t.getY(index);
				float tapSlop = Math.max(mPrefLineSize * 2f, 32f * mDensity);
				boolean smallMove = mStartY != null && start_x != null
						&& Math.abs(upY - mStartY) < tapSlop
						&& Math.abs(upX - start_x) < tapSlop;
		         
				cancelTapLongPress();
				if (mLoupeActive) {
					fireLoupeSelection();
					dismissLoupe();
					mTapLongPressFired = false;
					mTouchInTapWord = -1;
					mTouchInLink = -1;
					return true;
				}
				if (mTapLongPressFired) {
					// The hold already opened the menu. Sending on the way up as
					// well would both ask and act on the same gesture.
					mTapLongPressFired = false;
					mTouchInTapWord = -1;
					mTouchInLink = -1;
					return true;
				}

				if (mTouchInTapWord > -1 && smallMove
						&& mTouchInTapWord < tapBoxes.size()) {
					// A listed word was tapped. An http(s)/mailto link on the
					// same spot still wins — opening a browser is the surprise
					// to get wrong. MXP SEND is a command, same as the trigger,
					// so the player's tappable word takes that tap.
					String href = null;
					if (mTouchInLink >= 0 && mTouchInLink < linkBoxes.size()) {
						href = linkBoxes.get(mTouchInLink).getData();
					}
					if (mTouchInLink < 0
							|| com.resurrection.blowtorch2.lib.service.mxp.MxpLinks
									.tapWordOverrides(href)
							|| OscEight.tapWordOverrides(href)) {
						// The box already carries the finished commands: each rule
						// has its own, so they cannot be rebuilt from one setting.
						boolean sendsFirst = false;
						String[] cmds;
						if (mTouchInTapWord < tapCommands.size()) {
							TapTarget target = tapCommands.get(mTouchInTapWord);
							cmds = target.commands;
							sendsFirst = target.tapSendsFirst;
						} else {
							cmds = new String[] { tapBoxes.get(mTouchInTapWord).getData() };
						}
						if (cmds.length > 1 && !sendsFirst) {
							// More than one and the action has not been told to
							// pick: ask, anchored on the word that was hit so the
							// menu points at what it is about.
							openTapWordMenu(cmds, tapBoxes.get(mTouchInTapWord).getBox());
						} else {
							// One command, or the action says a tap sends the first
							// one and leaves the rest to a hold.
							mMainWindowHandler.sendMessage(mMainWindowHandler.obtainMessage(
									MainWindow.MESSAGE_TAPWORDCOMMAND, cmds[0]));
						}
						mTouchInTapWord = -1;
						mTouchInLink = -1;
						return true;
					}
				}
				mTouchInTapWord = -1;
				if (mTouchInLink > -1 && smallMove) {
					android.graphics.Rect hit = linkBoxes.get(mTouchInLink).getBox();
					int cx = (hit.left + hit.right) / 2;
					mMainWindowHandler.sendMessage(mMainWindowHandler.obtainMessage(
							MainWindow.MESSAGE_LAUNCHURL, cx, hit.top,
							linkBoxes.get(mTouchInLink).getData()));
			        mTouchInLink = -1;
				} else if (smallMove) {
					if (homeWidgetShowing && homeWidgetFingerDown) {
						if (mHomeWidgetRect.contains((int) upX, (int) upY)) {
							mScrollback = SCROLL_MIN;
							homeWidgetFingerDown = false;
							this.invalidate();
						}
					} else if (mTapDismissKeyboard) {
						// Loose tap on the game window (not a button): dismiss soft keyboard.
						mMainWindowHandler.sendEmptyMessage(MainWindow.MESSAGE_HIDEKEYBOARD);
					}
				} else {
					if (homeWidgetShowing && homeWidgetFingerDown) {
						if (mHomeWidgetRect.contains((int) upX, (int) upY)) {
							mScrollback = SCROLL_MIN;
							homeWidgetFingerDown = false;
							this.invalidate();
						}
					}
				}
		        
			}
			
			}
			// Sync to vsync during drag/fling — full invalidate() every MOVE was janky on main.
			if (action == MotionEvent.ACTION_MOVE
					|| (action == MotionEvent.ACTION_UP && Math.abs(mFlingVelocity) > FLING_STOP_VELOCITY)) {
				postInvalidateOnAnimation();
			} else {
				this.invalidate();
			}
			
			return true; //consumes
		}
		
		return false;
	}
	
	/** Maps the Options -> Window "Scroll sensitivity" choice onto a gain on finger travel.
	 *
	 * @param choice Index into the option's item list.
	 * @return Multiplier for scroll distance and fling speed; 1.0 tracks the finger.
	 */
	/** Reports, once, if this window's buffer is being changed off the UI thread.
	 *
	 * <p>Deliberately a loud complaint rather than a lock or a swallowed exception. The
	 * rule described on {@link #mBuffer} holds today, so anything this catches is a new
	 * mistake, and the useful thing is a stack trace pointing at the caller that broke
	 * it -- not a quietly dropped frame that hides the cause, and not a lock on every
	 * frame paying for a race that does not exist.
	 *
	 * @param what Name of the operation, for the log line.
	 */
	private void warnIfNotUiThread(final String what) {
		if (mBufferThreadWarned) {
			return;
		}
		if (Looper.myLooper() == Looper.getMainLooper()) {
			return;
		}
		mBufferThreadWarned = true;
		// logThrowable, not plain logcat: this is a latent crash, and a rule broken
		// during a play session is no use to anyone if it has rolled out of the ring
		// buffer by the time the crash gets reported.
		com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
				"Window[" + mName + "]." + what,
				new IllegalStateException("buffer changed on " + Thread.currentThread().getName()
						+ ", not the UI thread; onDraw walks it unguarded — post a message instead"));
	}

	/** @return This window's {@code scroll_sensitivity} list choice. */
	public final int getScrollSensitivityChoice() {
		return mScrollSensitivityChoice;
	}

	/** @return Whether this window paints light paper. */
	public final boolean isLightPaper() {
		return mLightPaper;
	}

	public final int getLightPaperShade() {
		return mLightPaperShade;
	}

	/**
	 * Extra-text overlays inherit the main window's light paper rather than
	 * their own (unpersisted) SettingsGroup.
	 */
	public final void applyLightPaper(final boolean light) {
		if (mLightPaper == light) {
			return;
		}
		mLightPaper = light;
		invalidate();
	}

	/**
	 * Extra-text copies this from main. Shade is ignored while light paper is
	 * off; changing it still invalidates so a later on uses the new sheet.
	 */
	public final void applyLightPaperShade(final int shade) {
		final int next = LightPaper.clampShade(shade);
		if (mLightPaperShade == next) {
			return;
		}
		mLightPaperShade = next;
		invalidate();
	}

	public void setSgr1Weight(boolean on) {
		if (mSgr1Weight == on) {
			return;
		}
		mSgr1Weight = on;
		invalidate();
	}

	/** Trigger 66/67, or MUD SGR 1 when the Service box is on. 67 must not pop the latter. */
	private boolean paintWeight() {
		return mSgr.weight()
				|| (mSgr1Weight && mSelectedBright != null && mSelectedBright.intValue() == 1);
	}

	/**
	 * Set the scroll gain from a {@code scroll_sensitivity} list choice.
	 * Extra-text overlays are driven through here rather than through their
	 * SettingsGroup, which is never persisted for them.
	 *
	 * @param choice A {@code scroll_sensitivity} index; out of range means Normal.
	 */
	public final void applyScrollSensitivityChoice(final Integer choice) {
		mScrollSensitivityChoice = choice == null
				? WindowToken.DEFAULT_SCROLL_SENSITIVITY : choice.intValue();
		mScrollSensitivity = scrollSensitivityFromChoice(choice);
		// A fling in flight was scaled by the old gain; let it stop rather than
		// change speed under the finger that already left the screen.
		mFlingVelocity = 0;
	}

	static float scrollSensitivityFromChoice(final Integer choice) {
		if (choice == null) {
			return 1.0f;
		}
		switch (choice.intValue()) {
		case 0: return 0.75f;
		case 1: return 1.0f;
		case 2: return 1.5f;
		case 3: return 2.0f;
		case 4: return 3.0f;
		default: return 1.0f;
		}
	}

	/** Called from onDraw, calculates a new scrollback value for this frame. */
	private void calculateScrollBack() {
		
		if (mLastFrameTime == 0) { //never drawn before
			if (mBuffer.getBrokenLineCount() <= mCalculatedLinesInWindow) { mScrollback = SCROLL_MIN; return;}
			if (mFingerDown) {
				// Newest-at-top: drag up to dig into history (older lines below).
				// Prefer live MOVE updates; this path is a fallback if MOVE skipped a frame.
				if (diff_amount != 0) {
					final double delta = mNewestAtTop ? -diff_amount : diff_amount;
					mScrollback = mScrollback + delta;
					if (mScrollback < SCROLL_MIN) {
						mScrollback = SCROLL_MIN;
					} else {
						if (mScrollback >= ((mBuffer.getBrokenLineCount() * mPrefLineSize))) {
							mScrollback = (double) ((mBuffer.getBrokenLineCount() * mPrefLineSize));
						}
					}
					diff_amount = 0;
				}
			} else {
				if (finger_down_to_up) {
					mLastFrameTime = System.currentTimeMillis(); 
					finger_down_to_up = false;
				}
			}
		} else {
			
			if (!mFingerDown) {				
				long nowdrawtime = System.currentTimeMillis(); 
				
				float durationSinceLastFrame = ((float) (nowdrawtime - mLastFrameTime)) / 1000.0f; //convert to seconds
				mLastFrameTime = System.currentTimeMillis();
				final double flingSign = mNewestAtTop ? -1.0 : 1.0;
				// Scale the deceleration by the same gain as the speed. A fling covers
				// v^2 / 2a, so leaving `a` alone would make double sensitivity travel
				// four times as far — this keeps the distance linear in the setting and
				// the fling lasting about as long at every setting.
				final float decel = fling_accel * mScrollSensitivity;
				if (mFlingVelocity < 0) {
					mFlingVelocity = mFlingVelocity + decel * durationSinceLastFrame;
					mScrollback =  mScrollback + flingSign * mFlingVelocity * durationSinceLastFrame;
				} else if (mFlingVelocity > 0) {
					mFlingVelocity = mFlingVelocity - decel * durationSinceLastFrame;
					mScrollback =  mScrollback + flingSign * mFlingVelocity * durationSinceLastFrame;
				}
				
				if (Math.abs(mFlingVelocity) < FLING_STOP_VELOCITY) {
					mFlingVelocity = 0;
					mLastFrameTime = 0;
					Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
				}
					
				if (mScrollback <= SCROLL_MIN) {
					mScrollback = SCROLL_MIN;
					mFlingVelocity = 0;
					mLastFrameTime = 0;
					Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
				}
				
				if (mScrollback >= ((mBuffer.getBrokenLineCount() * mPrefLineSize))) {
					mScrollback = (double) ((mBuffer.getBrokenLineCount() * mPrefLineSize));
					mFlingVelocity = 0;
					mLastFrameTime = 0;
					Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
					
				}
			}

			
		}
			
	}
	
	public void runScriptOnCreate() {
		if(mL == null) return;
		mL.getGlobal("debug");
		mL.getField(-1, "traceback");
		mL.remove(-2);
		
/*! \page entry_points
 * \section window Window Lua State Entry Points
 * \subsection OnCreate OnCreate
 * Called during window creation. After the main script has been loaded and the actual backing android View is created and shown.
 * 
 * \param none
 * 
 * \note General initialization of code can be done when the script is loaded. But certain graphical subsystems will be unavailable until this callback is called.
 */
		
		mL.getGlobal("OnCreate");
		if(mL.getLuaObject(-1).isFunction()) {
			int tmp = mL.pcall(0, 1, -2);
			if(tmp != 0) {
				displayLuaError("Calling OnCreate: "+mL.getLuaObject(-1).getString());
			}
			//Log.e("LUAWINDOW","OnCreate Success for window ("+this.getName()+")!");
			// Error branch used to pop neither the error object nor the
			// traceback function.
			mL.pop(2);
		} else {
			mL.pop(2);
		}
		
		mL.getGlobal("OnMeasure");
		if(mL.isFunction(-1)) {
			mHasScriptOnMeasure = true;
		} else {
			mHasScriptOnMeasure = false;
		}
		mL.pop(1);
	}
	

	@Override
	public final void onDraw(final Canvas c) {
		mBlinkSawThisFrame = false;
		mBlinkFastSawThisFrame = false;
		final long blinkNow = SystemClock.uptimeMillis();
		mBlinkHiddenSlow = ((blinkNow / SgrStyle.BLINK_SLOW_MS) & 1L) == 1L;
		mBlinkHiddenFast = ((blinkNow / SgrStyle.BLINK_FAST_MS) & 1L) == 1L;
		mSelectionCanvasSaved = false;
		if (selectedSelector != null && mSelectionIndicatorCanvas != null) {
			mSelectionIndicatorBitmap.eraseColor(0x00000000);
			int color = mScrollerPaint.getColor();
			int newcolor = 0xFF000000 | color;
			mScrollerPaint.setColor(newcolor);
			mSelectionIndicatorCanvas.drawRect(mSelectionIndicatorLeftButtonRect, mScrollerPaint);
			mSelectionIndicatorCanvas.drawRect(mSelectionIndicatorUpButtonRect, mScrollerPaint);
			mSelectionIndicatorCanvas.drawRect(mSelectionIndicatorRightButtonRect, mScrollerPaint);
			mSelectionIndicatorCanvas.drawRect(mSelectionIndicatorDownButtonRect, mScrollerPaint);
			mScrollerPaint.setColor(color);
			
			mSelectionIndicatorCanvas.save();
			mSelectionCanvasSaved = true;
			mSelectionIndicatorCanvas.clipPath(mSelectionIndicatorClipPath);
			mSelectionIndicatorCanvas.drawColor(0xFF444444);
			
		}
		int startline2 = 0, startcol = 0, endline = 0, endcol = 0;
		if (theSelection  != null) {

			if (theSelection.start.line == theSelection.end.line) {
				startline2 = theSelection.start.line;
				endline = theSelection.start.line;
				if (theSelection.end.column < theSelection.start.column) {
					startcol = theSelection.end.column;
					endcol = theSelection.start.column;
				} else{
					startcol = theSelection.start.column;
					endcol = theSelection.end.column;
				}
			} else if (theSelection.end.line >  theSelection.start.line) {
				startline2 = theSelection.end.line;
				startcol = theSelection.end.column;
				endline = theSelection.start.line;
				endcol = theSelection.start.column;
			} else {
				startline2 = theSelection.start.line;
				startcol = theSelection.start.column;
				endline = theSelection.end.line;
				endcol = theSelection.end.column;
			}
		}
		
		
		if (mBuffer.getBrokenLineCount() != 0) {
			if (linkColor == null) {
				
				linkColor = new Paint();
				linkColor.setAntiAlias(true);
				linkColor.setColor(themedLinkColor());
			}
			
			linkColor.setColor(themedLinkColor());
			calculateScrollBack();
			c.save();
			
			setBgPaintColor(LightPaper.paper(mLightPaper, mLightPaperShade));
			// Own bounds only, never drawColor. drawColor fills the whole clip,
			// and an extra text overlay turns clipChildren off on every parent up
			// to its root so the copy widget's disc is not cropped — which made
			// this one call paint over the float title bar (⋮ handle, title, ✕)
			// sitting above the text. For a window whose parent does clip this is
			// the same pixels drawColor covered.
			//
			// getWidth/getHeight rather than mWidth/mHeight: those fields are also
			// assigned by setDimensions/setWidth, which write the LayoutParams and
			// return, so between such a call and the next layout pass they hold a
			// requested size the canvas does not have yet. An undersized rect here
			// would leave an unpainted band on the main game window.
			c.drawRect(0, 0, getWidth(), getHeight(), b); // full window including pad = paper
			
			mClipRect.top = textPadTop();
			mClipRect.left = 0;
			mClipRect.right = mWidth;
			mClipRect.bottom = Math.max(mClipRect.top + 1, mHeight - textPadBottom());
			
			c.clipRect(mClipRect);
			
			c.drawRect(0, 0, mClipRect.right - mClipRect.left, mClipRect.top - mClipRect.bottom, b);
			p.setTypeface(mPrefFont);
			applyTerminalFontFeatures(p);
			p.setAntiAlias(true);
			p.setTextSize(mPrefFontSize);
			p.setColor(0xFFFFFFFF);
			p.setUnderlineText(false);
			p.setStrikeThruText(false);
			p.setTextSkewX(0f);
			mSgr.clear();
			
			// Sideways scroll is applied at the row origin rather than with
			// canvas.translate on purpose: link boxes are built from this same x
			// while drawing, so they stay in the space raw touch coordinates are
			// in and hit testing needs no compensation.
			float x = -mScrollX;
			float y = 0;
			final int ch = contentHeight();
			if (mPrefLineSize * mCalculatedLinesInWindow < ch) {
				
				y = ((mPrefLineSize * mCalculatedLinesInWindow) - ch) - mPrefLineSize;
				//Log.e("STARTY","STARTY IS:"+y);
			}
			
			
			//Iterator<TextTree.Unit> u = null;
			boolean stop = false;
			
			//TODO: STEP 0
			//calculate the y position of the first line.
			//float max_y = PREF_LINESIZE*the_tree.getLines().size();
			
			
			//instead of being able to draw from the buttom up like i would have liked.
			//we are going to do the first in hopefully few, really expensive operations.
			
			//TODO: STEP 1
			//noting the current scrollback & window size, calculate the position of the first line of text that we need to draw.
			//float y_position = WINDOW_HEIGHT+PREF_LINESIZE;
			//float line_number = y_position/PREF_LINESIZE;
			
			//TODO: STEP 2
			//new step 2, get an iterator to the start of the scrollback
			
			//get the iterator of the list at the given position.
			//i = the_tree.getLines().listIterator(line_number);
			//use our super cool iterator function.
			//Float offset = 0f;
			//synchronized(synch) {
			
			IteratorBundle bundle = null;
			boolean gotIt = false;
			// Retry without sleeping. This used to wait(5) between tries, up to twenty
			// times, which is a tenth of a second of frozen UI inside one frame — and
			// sleeping the only thread that drains the message queue cannot help,
			// because on this Handler the buffer is filled by that same thread.
			// Retries are also not free: a late throw means getScreenIterator walked
			// most of the scrollback first, so a few tries is the sensible ceiling and
			// the frame after this one is a better place to be than this one.
			int maxTries = 3;
			int tries = 0;

			while (!gotIt && tries < maxTries) {
				try {
					tries = tries + 1;
					bundle = getScreenIterator(mScrollback, mPrefLineSize);
					gotIt = true;
				} catch (ConcurrentModificationException e) {
					// Buffer mutated mid-draw. Give up on this frame and ask for another.
					Log.e("BlowTorch", "buffer changed under onDraw, try " + tries, e);
				}
			}
			if (!gotIt) {
				releaseSelectionCanvas();
				this.invalidate();
				return;
			}
			screenIt = bundle.getI();
			y = bundle.getOffset();

			int extraLines = bundle.getExtraLines();
			if (screenIt == null) { releaseSelectionCanvas(); return;}
			
			int startline = bundle.getStartLine();
			int workingline = startline;
			int workingcol = 0;
			// Column within the whole logical line, for tappable matching only.
			// workingcol cannot serve: it resets on every soft wrap (case BREAK),
			// while a match is found against the line as one string, so on every
			// wrapped row the two would disagree and the marks would land on the
			// wrong characters or nowhere. Counted in the same units as that
			// string (UTF-16 chars), reset only where the line's hits are found.
			int tapCol = 0;
			
			//TODO: STEP 3
			//find bleed.
			boolean bleeding = false;
			int back = 0;
			// Bounded on purpose. This searches back for the colour still in effect at
			// the top of the screen, and stops at the first one it finds. With no
			// colour to find it used to walk to the end of the scrollback and back
			// again on every frame — the original comment below calls that out. That
			// is why plain output scrolled worse than coloured output.
			//
			// Giving up after BLEED_SEARCH_MAX_LINES means: no colour code within that
			// many lines above the screen, so the default colour is what should be
			// drawn anyway. A buffer that plain has nothing to bleed.
			// Measured on a real MUD 28.07.2026: worst case over 300 frames of hard
			// scrolling was 9 lines and 2ms, because coloured output stops the scan
			// at the first code found. The 1000 line limit only bites on a buffer
			// with no colour at all above the screen.
			while (screenIt.hasNext() && !bleeding && back < BLEED_SEARCH_MAX_LINES) {

				Line l = screenIt.next();
				back++;

				for (Unit u : l.getData()) {
					if (u instanceof TextTree.Color) {
						mXterm256Color = false;
						mXterm256FGStart = false;
						mXterm256BGStart = false;
						mTrueColorCollect = false;
						mTrueColorCount = 0;
						for (int i = 0; i < ((TextTree.Color) u).getOperations().size(); i++) {
							updateColorRegisters(((TextTree.Color) u).getOperations().get(i));
							Colorizer.COLOR_TYPE type = Colorizer.getColorType(((TextTree.Color) u).getOperations().get(i));
							if (Colorizer.stopsFgBleedSearch(type)) {
								bleeding = true;
							}
						}
						if (mTrueColorFG) {
							p.setColor(0xFF000000 | (mSelectedColor.intValue() & 0xFFFFFF));
						} else if (mXterm256FG) {
							p.setColor(0xFF000000 | Colorizer.getColorValue(mSelectedBright, mSelectedColor, true));
						} else {
							p.setColor(0xFF000000 | Colorizer.getColorValue(mSelectedBright, mSelectedColor, false));
						}
						themeBleedForeground();
						// Do not bleed backgrounds: reset to paper (skipped as a cell).
						setBgPaintColor(LightPaper.paper(mLightPaper, mLightPaperShade));
	
					}
				}
			}
			
			if (!bleeding) {
				p.setColor(0xFF000000 | Colorizer.getColorValue(0, 37, false));
				p.setColor(LightPaper.remapForeground(p.getColor(), mLightPaper, true,
						mLightPaperShade));
				mSgr.clear();
			}
			// Reverse needs the register background; otherwise keep paper (do not
			// bleed a leftover 40 as a cell fill). Faint dims the bleed FG.
			if (mSgr.reverse()) {
				applyAnsiPaints(p, b);
			} else {
				applySgrDecorations(p);
				if (mSgr.faint()) {
					p.setColor(LightPaper.dimTowardPaper(p.getColor(),
							SgrStyle.FAINT_DIM_PERCENT, mLightPaper, mLightPaperShade));
				}
			}
			mResolvedFg = p.getColor();
			mPaintingDimLine = false;
			//TODO: STEP 4
			//advance the iterator back the number of units it took to find a bleed.
			//second real expensive move. In the case of a no color text buffer, it would walk from scroll to end and back every time. USE COLOR 
			while (back > 0) {
				screenIt.previous();
				back--;
			}

			if (screenIt.hasNext()) {
				screenIt.next(); // the bleed/back stuff seems to be messing with my calculation
			}
			//TODO: STEP 5
			//draw the text, from top to bottom.	
			
			boolean scrollingGesture = mFingerDown
					|| Math.abs(mFlingVelocity) > FLING_STOP_VELOCITY;

			int drawnlines = 0;
			boolean doingLink = false;
			
			mCurrentLink.setLength(0);
			if (!scrollingGesture) {
				linkBoxes.clear();
				tapBoxes.clear();
				tapCommands.clear();
			}
			
			while (!stop && screenIt.hasPrevious()) {
				Line l = screenIt.previous();
				int searchPlainPos = 0;
				mPaintingDimLine = mDimRepeatedLines && l.isDimRepeated();
				applyRepeatedLineForeground(p);

				// A picture the server sent, drawn over this line and the blank
				// ones under it. Before the text, so a line that somehow has both
				// still shows its text on top rather than under.
				if (l.getInlineImageKey() != null) {
					drawInlineImage(c, l, y);
				}


				if (mCenterJustify) {
					//center justify.

					int amount = mOneCharWidth * l.charcount;
					x = (float) ((mWidth / 2.0) - (amount / 2.0));
				}
				unitIterator = l.getIterator();
				// Whole-line matching, before the coloured runs are drawn.
				findTapHitsForLine(l);
				tapCol = 0;

				int linemode = 0;
				if (startline2 == endline && startline2 == workingline) {
					linemode = 1;
				} else if (startline2 == workingline) {
					linemode = 2;
				} else if (startline2 > workingline && endline < workingline) {
					
					linemode = 3;
				} else if (endline == workingline) {
					linemode = 4;
					
				}
				
				boolean finishedWithNewLine = false;
				
				while (unitIterator.hasNext()) {
					Unit u = unitIterator.next();
					final boolean useBackground =
							!LightPaper.skipCellBackground(mBgPaintColor, mLightPaper,
									mLightPaperShade);
					
					switch(u.type) {
					case WHITESPACE:
					case TEXT:
						TextTree.Text text = (TextTree.Text) u;
						boolean doIndicator = false;
						int indicatorlineoffset = 0;
						if (selectedSelector != null && selectedSelector.line == workingline) {
							doIndicator = true;
						} else if (selectedSelector != null && Math.abs(selectedSelector.line - workingline) < 3) {
							doIndicator = true;
							indicatorlineoffset = selectedSelector.line - workingline;
						}
						
						if (theSelection  != null) {
							switch(linemode) {
							case 1:
								int finishCol = workingcol + text.charcount;
								if (finishCol > startcol && finishCol - 1 <= endcol){
									if ((finishCol - startcol) < text.charcount) {
										int overshoot = startcol - workingcol;
										int overshootPixels = overshoot * mOneCharWidth;
										c.drawRect(x + overshootPixels, cellTop(y), x + cellWidth(text), cellBottom(y), mTextSelectionIndicatorBackgroundPaint);
									} else {
										c.drawRect(x, cellTop(y), x + cellWidth(text), cellBottom(y), mTextSelectionIndicatorBackgroundPaint);
									}
								} else if (finishCol > endcol) {
									if ((finishCol - endcol) < text.charcount) {
										int overshoot = endcol - workingcol + 1;
										int overshootPixels = overshoot * mOneCharWidth;
										c.drawRect(x, cellTop(y), x + overshootPixels, cellBottom(y), mTextSelectionIndicatorBackgroundPaint);
									} 
								} 
								break;
							case 2:
								finishCol = workingcol + text.charcount;
								if (finishCol > startcol) {
									if ((finishCol - startcol) < text.charcount) {
										int overshoot = startcol - workingcol;
										int overshootPixels = overshoot * mOneCharWidth;
										c.drawRect(x + overshootPixels, cellTop(y), x + cellWidth(text), cellBottom(y), mTextSelectionIndicatorBackgroundPaint);
									} else {
										c.drawRect(x, cellTop(y), x + cellWidth(text), cellBottom(y), mTextSelectionIndicatorBackgroundPaint);
									}
								} 
								break;
							case 3:
								
								c.drawRect(x, cellTop(y), x + cellWidth(text), cellBottom(y), mTextSelectionIndicatorBackgroundPaint);
								break;
							case 4:
								finishCol = workingcol + text.charcount;
								if (finishCol >= endcol) {
									if ((finishCol - endcol) < text.charcount) {
										int overshoot = endcol - workingcol + 1;
										int overshootPixels = overshoot * mOneCharWidth;
										c.drawRect(x, cellTop(y), x + overshootPixels, cellBottom(y), mScrollerPaint);
									}
								} else {
									c.drawRect(x, cellTop(y), x + cellWidth(text), cellBottom(y), mTextSelectionIndicatorBackgroundPaint);
								}
								break;
							default:
								break;
							}
						}
						
						if (useBackground) {
							c.drawRect(x, cellTop(y), x + cellWidth(text), cellBottom(y), b);
						}

						if (l == mSearchHighlightLine && mSearchMatchLen > 0
								&& !mSearchMatchStarts.isEmpty()) {
							String unitStr = text.getString();
							if (unitStr != null && unitStr.length() > 0) {
								int unitLen = unitStr.length();
								int unitPlainStart = searchPlainPos;
								for (int mi = 0; mi < mSearchMatchStarts.size(); mi++) {
									int matchStart = mSearchMatchStarts.get(mi).intValue();
									int matchEnd = matchStart + mSearchMatchLen;
									int overlapStart = Math.max(matchStart, unitPlainStart);
									int overlapEnd = Math.min(matchEnd, unitPlainStart + unitLen);
									if (overlapStart >= overlapEnd) {
										continue;
									}
									int localStart = overlapStart - unitPlainStart;
									int localEnd = overlapEnd - unitPlainStart;
									float left = x + cellWidthPrefix(unitStr, localStart);
									float right = left + cellWidthSpan(unitStr, localStart, localEnd);
									c.drawRect(left, cellTop(y), right, cellBottom(y), mSearchMatchPaint);
								}
							}
						}
						
						boolean mxpLink = text.getHref() != null
								&& com.resurrection.blowtorch2.lib.service.mxp.MxpLinks.isMxpHref(text.getHref())
								&& !com.resurrection.blowtorch2.lib.service.mxp.MxpLinks.isExpireCommand(text.getHref());
						boolean osc8 = (mOsc8Links || mxpLink) && text.getHref() != null
								&& !com.resurrection.blowtorch2.lib.service.mxp.MxpLinks.isExpireCommand(text.getHref());
						if (osc8 || text.isLink() || doingLink) {
							if (u instanceof TextTree.WhiteSpace && !osc8) {
								// Regex: whitespace ends the link. OSC 8 keeps
								// spaces inside "click here" as part of the span.
								for (int z = 0; z < linkBoxes.size(); z++) {
									if (linkBoxes.get(z).getData() == null) {
										linkBoxes.get(z).setData(mCurrentLink.toString());
									}
								}
								mCurrentLink.setLength(0);
								doingLink = false;
							} else {
								if (!osc8) {
									doingLink = true;
									mCurrentLink.append(text.getString());
								}
								
								
								Rect r = new Rect();
								final float linkY = screenBaselineY(y);
								r.left = (int) x;
								r.top = (int) (linkY - p.getTextSize());
								r.right = (int) (x + cellWidth(text));
								r.bottom = (int) (linkY + 5);
								if (mLinkMode == LINK_MODE.BACKGROUND) {
									linkColor.setColor(themedLinkColor());
									c.drawRect(r.left, r.top, r.right, r.bottom, linkColor);
								}
								
								int linkBoxHeightDips = (int) ((r.bottom - r.top) / this.getResources().getDisplayMetrics().density);
								if (linkBoxHeightDips < mLinkBoxHeightMinimum) {
									int additionalAmount = (mLinkBoxHeightMinimum - linkBoxHeightDips) / 2;
									if (additionalAmount > 0) {
										r.top -= additionalAmount * this.getResources().getDisplayMetrics().density;
										r.bottom += additionalAmount * this.getResources().getDisplayMetrics().density;
									}
								}
								
								LinkBox linkbox = new LinkBox(null, r);
								if (osc8) {
									linkbox.setData(text.getHref());
								}
								linkbox.setLabel(text.getString());
								if (!scrollingGesture) {
									linkBoxes.add(linkbox);
								}
								
							}
						}
						if (doingLink || osc8) {
							switch(mLinkMode) {
							case NONE:
								linkColor.setTextSize(p.getTextSize());
								linkColor.setTypeface(p.getTypeface());
								linkColor.setUnderlineText(false);
								linkColor.setColor(p.getColor());
								break;
							case HIGHLIGHT:
								linkColor.setTextSize(p.getTextSize());
								linkColor.setTypeface(p.getTypeface());
								linkColor.setColor(p.getColor());
								linkColor.setUnderlineText(true);
								break;
							case HIGHLIGHT_COLOR:
								linkColor.setTextSize(p.getTextSize());
								linkColor.setTypeface(p.getTypeface());
								linkColor.setColor(themedLinkColor());
								linkColor.setUnderlineText(true);
								break;
							case HIGHLIGHT_COLOR_ONLY_BLAND:
								
								linkColor.setTextSize(p.getTextSize());
								linkColor.setTypeface(p.getTypeface());
								if (mSelectedColor == 37) {
									linkColor.setColor(themedLinkColor());
								} else {
									linkColor.setColor(p.getColor());
								}
								linkColor.setUnderlineText(true);
								break;
							case BACKGROUND:
								linkColor.setTextSize(p.getTextSize());
								linkColor.setTypeface(p.getTypeface());
								linkColor.setUnderlineText(false);
								//calculate the "reverse-most-constrasty-color"
								int counterpart = 0xFF000000 | (mLinkHighlightColor ^ 0xFFFFFFFF);
								linkColor.setColor(counterpart);
								break;
							default:
								linkColor.setTextSize(p.getTextSize());
								linkColor.setTypeface(p.getTypeface());
								linkColor.setUnderlineText(false);
								linkColor.setColor(themedLinkColor());
							}
							linkColor.setStrikeThruText(p.isStrikeThruText());
							linkColor.setTextSkewX(p.getTextSkewX());
							applyTerminalFontFeatures(linkColor);
							if (p.isUnderlineText()) {
								linkColor.setUnderlineText(true);
							}
							
							if (doIndicator) {
								int unitEndCol = workingcol + (text.charcount - 1);
								if (unitEndCol > selectedSelector.column - 10 && workingcol < selectedSelector.column + 10) {
									float size = p.getTextSize();
									p.setTextSize(30);
									int overshoot = workingcol - selectedSelector.column;
									int ix = 0, iy = mSelectionIndicatorFontSize;
									ix = (int) (mSelectionIndicatorHalfDimension + (overshoot * mSelectionCharacterWidth) - 0.5 * mSelectionCharacterWidth);
									iy = (int) (mSelectionIndicatorHalfDimension + (0.5 * mSelectionIndicatorFontSize)) + (indicatorlineoffset * mSelectionIndicatorFontSize);
									mSelectionIndicatorCanvas.drawText(text.getString(), ix, iy, p);
									p.setTextSize(size);
								}
								
							}
							markTappableWords(c, text, x, y, linkColor, scrollingGesture, tapCol);
							tapCol += text.getString() != null ? text.getString().length() : 0;
							workingcol += text.charcount;
							x += drawTextOnGrid(c, text.getString(), x, y, linkColor);
							if (l == mSearchHighlightLine) {
								drawSearchMatchText(c, text.getString(), x - cellWidth(text), y, searchPlainPos);
							}
							searchPlainPos += text.getString() != null ? text.getString().length() : 0;
							
						} else {
							
							if (doIndicator) {
								int unitEndCol = workingcol + (text.charcount - 1);
								if (unitEndCol > selectedSelector.column - 10 && workingcol < selectedSelector.column + 10) {
									float size = p.getTextSize();
									p.setTextSize(30);
									int overshoot = workingcol - selectedSelector.column;
									int ix = 0 , iy = mSelectionIndicatorFontSize;
									ix = (int) (mSelectionIndicatorHalfDimension + (overshoot * mSelectionCharacterWidth) - 0.5 * mSelectionCharacterWidth);
									iy = (int) (mSelectionIndicatorHalfDimension + (0.5 * mSelectionIndicatorFontSize)) + (indicatorlineoffset * mSelectionIndicatorFontSize);
									mSelectionIndicatorCanvas.drawText(text.getString(), ix, iy, p);
									p.setTextSize(size);
								}
								
							}
							markTappableWords(c, text, x, y, p, scrollingGesture, tapCol);
							tapCol += text.getString() != null ? text.getString().length() : 0;
							workingcol += text.charcount;
							x += drawTextOnGrid(c, text.getString(), x, y, p);
							if (l == mSearchHighlightLine) {
								drawSearchMatchText(c, text.getString(), x - cellWidth(text), y, searchPlainPos);
							}
							searchPlainPos += text.getString() != null ? text.getString().length() : 0;
						}

						break;
					case COLOR:
						applyColorUnit((TextTree.Color) u, p, b);
						mResolvedFg = p.getColor();
						applyRepeatedLineForeground(p);
						if (mColorDebugMode == 1 || mColorDebugMode == 2) {
							String str = "";
							try {
								str = new String(((TextTree.Color) u).bin,"ISO-8859-1");
							} catch (UnsupportedEncodingException e) {
								e.printStackTrace();
							}
							c.drawText(str, x, screenBaselineY(y), p);
							x += p.measureText(str);
						}
						break;
					case NEWLINE:
					case BREAK:
						if (u instanceof TextTree.NewLine) {
							if (doingLink) {
								for (int z = 0; z < linkBoxes.size(); z++) {
									if (linkBoxes.get(z).getData() == null) {
										linkBoxes.get(z).setData(mCurrentLink.toString());
									}
								}
								mCurrentLink.setLength(0);
								doingLink = false;
								//REGISTER LINK BOX
							}
						} else if (u instanceof TextTree.Break) {
							workingline = workingline -1;
							if (startline2 == endline && startline2 == workingline) {
								linemode = 1;
							} else if (startline2 == workingline) {
								linemode = 2;
							} else if (startline2 > workingline && endline < workingline) {
								
								linemode = 3;
							} else if (endline == workingline) {
								linemode = 4;
								
							} else {
								linemode = -1;
							}
						}
						
						finishedWithNewLine = true;
						
						//TODO: make sure that where this is moved to works
						y = y + mPrefLineSize;
						
						
						x = -mScrollX;
						drawnlines++;
						workingcol = 0;
						if (drawnlines > mCalculatedLinesInWindow + extraLines) {
							stop = true;
						}
						break;
					default:
						break;
					}
				}
				if (!finishedWithNewLine) {
					y = y + mPrefLineSize;
					x = -mScrollX;
					drawnlines++;
					workingcol = 0;
				}
				workingline = workingline - 1;
				workingcol = 0;
				l.resetIterator();
			}
			if (!scrollingGesture || theSelection != null) {
				showScroller(c);
			}
			drawSelectionWidget(c);
			c.restore();
			if (!mFingerDown && Math.abs(mFlingVelocity) > FLING_STOP_VELOCITY) {
				postInvalidateOnAnimation();
			} else if (!mFingerDown) {
				mFlingVelocity = 0;
			}
		
		}
		
		//phew, do the lua stuff, and lets be done with this.
		c.save();
		boolean scrollingNow = mFingerDown
				|| Math.abs(mFlingVelocity) > FLING_STOP_VELOCITY;
		if (mHasDrawRoutine && !scrollingNow) {
			if (mL != null) {
				
/*! \page entry_points
 * \subsection OnDraw OnDraw
 * This function is called whenever the window is dirty and needs to redraw custom content.
 * 
 * \param canvas
 * 
 * \note It is difficult to know exactly what needs to be freed for garbage collection, how to do it, and weather or not it worked. 
 * A good example is the button window, it has many custom resources and I had run into memory issues with it when closing/opening the window a few times. 
 * It may never happen, it may happen after 100 open/close cycles, or 5, but the general trend of running the foreground process out of memory is an immediate termination of the window. 
 * So if you are in a case where you are coming back into the appliation after a phone call or web browser and it immediatly exits, this may be the culprit.
 */
				
				mL.getGlobal("debug");
				mL.getField(mL.getTop(), "traceback");
				mL.remove(TOP_MINUS_TWO);
				
				
				mL.getGlobal("OnDraw");
				if (mL.isFunction(mL.getTop())) {
					mL.pushJavaObject(c);
					int ret = mL.pcall(1, 1, TOP_MINUS_THREE);
					if (ret != 0) {
						displayLuaError("Error calling OnDraw: " + mL.getLuaObject(-1).toString());
					}
					// This leaked two slots per erroring frame: a broken OnDraw
					// climbed the stack at frame rate.
					mL.pop(2);
				} else {
					mHasDrawRoutine = false;
					mL.pop(2);
				}
			}
		}

		c.restore();
		drawLoupe(c);
		scheduleBlinkIfNeeded();
	}

	/** Utility class to keep track of a drawn link's hitbox and link info. */
	private class LinkBox {
		/** The link data (url). */
		private String mData;
		/** Display text for the loupe, when this box is a candidate. */
		private String mLabel;
		/** The hitbox in view coordinates. */
		private Rect mBox;
		/** Public constructor.
		 * 
		 * @param link Link data.
		 * @param rect Hitbox. 
		 */
		public LinkBox(final String link, final Rect rect) {
			//this.mData = link;
			this.mBox = rect;
		}
		/** Setter for data. 
		 * 
		 * @param data The data.
		 */
		public void setData(final String data) {
			this.mData = data;
		}
		/** Getter for data.
		 * 
		 * @return The data.
		 */
		public String getData() {
			return mData;
		}
		public void setLabel(final String label) {
			this.mLabel = label;
		}
		public String getLabel() {
			return mLabel;
		}
		/** Getter for the hitbox. 
		 * 
		 * @return The hitbox.
		 */
		public Rect getBox() {
			return mBox;
		}
	}
	
	/** Draws the scroller rectangle (and I think the selection box. 
	 * 
	 * @param c The canvas to draw on.
	 */
	public final void showScroller(final Canvas c) {
		mScrollerPaint.setColor(0xFFFF0000);
		
		if (mBuffer.getBrokenLineCount() < 1) {
			return; //no scroller to show.
		}

		final boolean atLiveEdge = mScrollback <= SCROLL_MIN + 3 * mDensity;
		
		if (!atLiveEdge
				&& mBuffer.getBrokenLineCount() > mCalculatedLinesInWindow) {
			homeWidgetShowing = true;
			layoutHomeWidgetRect();
			drawJumpChevron(c);
			if (mScrollDates) {
				drawWhenCluster(c);
			}
		} else {
			homeWidgetShowing = false;
		}
		
		double scrollerSize = 0.0f;
		double scrollerPos = 0.0f;
		double posPercent = 0.0f;
		
		float workingHeight = contentHeight();
		float workingWidth = mWidth;
		final float density = this.getResources().getDisplayMetrics().density;
		final int pad = textPadTop();
		
		Float windowPercent = workingHeight / (mBuffer.getBrokenLineCount()*mPrefLineSize);
		if (windowPercent > 1) {
			//then we have but 1 page to show
			return;
		}

		// Live output: do not paint the always-on right-edge thumb (reads as a stray
		// blue line). Show it only while scrolled into history. Scroll dates
		// replaces it with the mark next to the jump chevron.
		if (!atLiveEdge && !mScrollDates) {
			scrollerSize = windowPercent * workingHeight;
			posPercent = (mScrollback - (workingHeight / 2)) / (mBuffer.getBrokenLineCount() * mPrefLineSize);
			scrollerPos = workingHeight * posPercent;
			if (!mNewestAtTop) {
				scrollerPos = workingHeight - scrollerPos;
			}
			scrollerPos += pad;

			int blueValue = Math.max(0, Math.min(255, (int) (-1 * 255 * posPercent + 255)));
			int redValue = Math.max(0, Math.min(255, (int) (255 * posPercent)));
			int alphaValue = Math.max(0, Math.min(255, (int) ((255 - 70) * posPercent + 70)));
			int finalColor = android.graphics.Color.argb(alphaValue, redValue, 100, blueValue);
			mScrollerPaint.setColor(finalColor);
			scrollerRect.set((int) workingWidth - (int) (2 * density), (int) (scrollerPos - scrollerSize / 2), (int) workingWidth, (int) (scrollerPos + scrollerSize / 2));

			c.drawRect(scrollerRect, mScrollerPaint);
		}
		
		if (theSelection != null) {
			//compute rects for the guys.
			int startEdge = bufferLineToScreenY(theSelection.start.line, 0);
			int startTop;
			int startBottom;
			if (mNewestAtTop) {
				startTop = startEdge;
				startBottom = startEdge + mPrefLineSize;
			} else {
				startBottom = startEdge;
				startTop = startEdge - mPrefLineSize;
			}
			int startLeft = theSelection.start.column * mOneCharWidth;
			int startRight = startLeft + mOneCharWidth;
			
			int endEdge = bufferLineToScreenY(theSelection.end.line, 0);
			int endTop;
			int endBottom;
			if (mNewestAtTop) {
				endTop = endEdge;
				endBottom = endEdge + mPrefLineSize;
			} else {
				endBottom = endEdge;
				endTop = endEdge - mPrefLineSize;
			}
			int endLeft = theSelection.end.column * mOneCharWidth;
			int endRight = endLeft + mOneCharWidth;
			
			//int scroll_from_bottom = (int) (scrollback-SCROLL_MIN);
			
			c.drawRect(startLeft, startTop - 2, startRight, startBottom - 2, mTextSelectionIndicatorPaint);
			c.drawRect(endLeft, endTop - 2, endRight, endBottom - 2, mTextSelectionIndicatorPaint);
			
			int x = 0, y = 0;
			if (selectedSelector == theSelection.end) {
				x = endLeft + (endRight - endLeft) / 2;
				y = endTop + (endBottom - endTop) / 2;
			} else {
				x = startLeft + (startRight - startLeft) / 2;
				y = startTop + (startBottom - startTop) / 2;
			}
			
			if((Window.this.getContext().getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_XLARGE) {
				c.drawCircle(x, y-2, 50*density, mTextSelectionIndicatorCirclePaint);
			} else {
				c.drawCircle(x, y-2, 33*density, mTextSelectionIndicatorCirclePaint);
			}
			
		}
		
	}

	/**
	 * Undo the clip save taken at the top of onDraw for the selection widget.
	 *
	 * onDraw saves the widget canvas before drawing the disc, and showScroller is
	 * what pairs it with a restore. Two early returns sit between the two, so a
	 * frame that bailed out left an unmatched save behind and the next frame
	 * stacked another one on top of it.
	 */
	private void releaseSelectionCanvas() {
		if (!mSelectionCanvasSaved) {
			return;
		}
		try {
			mSelectionIndicatorCanvas.restore();
		} catch (IllegalArgumentException ignored) {
		}
		mSelectionCanvasSaved = false;
	}

	/**
	 * Paint the text selection widget.
	 *
	 * This used to live at the tail of showScroller, behind that method's own exit
	 * conditions -- and showScroller returns early when the buffer fits on one page,
	 * since there is no scrollbar worth drawing. Selection would then be fully live:
	 * touch handler installed, buttons hidden, taps landing on the widget's icons --
	 * with nothing painted. Pressing where the close icon happens to be put it all
	 * back, which is what made it look like the widget had broken rather than never
	 * been drawn. It is the selection's business, not the scrollbar's.
	 */
	private void drawSelectionWidget(final Canvas c) {
		if(selectedSelector != null) {
			if (mSelectionCanvasSaved) {
				try {
					mSelectionIndicatorCanvas.restore();
				} catch (IllegalArgumentException ignored) {
				}
				mSelectionCanvasSaved = false;
			}
			Paint edgePaint = new Paint();
			edgePaint.setStyle(Paint.Style.STROKE);
			edgePaint.setStrokeWidth(6);
			edgePaint.setAntiAlias(true);
			edgePaint.setColor(0xFFAA22AA);
			
			mSelectionIndicatorCanvas.drawPath(mSelectionIndicatorClipPath, edgePaint);
			
			// Icons after restore so the circle clip does not crop them; inset into the disc.
			int full = mSelectionIndicatorHalfDimension * 2;
			int inset = Math.max(8, (int) (10 * mDensity));
			drawSelectionIcon(mTextSelectionCopyBitmap, inset, inset);
			drawSelectionIcon(mTextSelectionCancelBitmap, inset, full - inset - iconHeight(mTextSelectionCancelBitmap));
			drawSelectionIcon(mTextSelectionSwapBitmap, full - inset - iconWidth(mTextSelectionSwapBitmap), inset);
			
			float left = (float) (mSelectionIndicatorHalfDimension - (0.5 * mSelectionCharacterWidth));
			float top = (float) (mSelectionIndicatorHalfDimension - (0.5 * mSelectionIndicatorFontSize));
			float right = (float) (mSelectionIndicatorHalfDimension + (0.5 * mSelectionCharacterWidth));
			float bottom = (float) (mSelectionIndicatorHalfDimension + (0.5 * mSelectionIndicatorFontSize));
			
			c.drawBitmap(mSelectionIndicatorBitmap, mWidgetX - mSelectionIndicatorHalfDimension, mWidgetY - mSelectionIndicatorHalfDimension, null);
			c.drawRect(left + (mWidgetX - mSelectionIndicatorHalfDimension), 
					top + (mWidgetY - mSelectionIndicatorHalfDimension), 
					right + (mWidgetX - mSelectionIndicatorHalfDimension),
					bottom + (mWidgetY - mSelectionIndicatorHalfDimension), 
					mScrollerPaint);		
		} else if (mSelectionCanvasSaved) {
			try {
				mSelectionIndicatorCanvas.restore();
			} catch (IllegalArgumentException ignored) {
			}
			mSelectionCanvasSaved = false;
		}
		
	}

	private int iconWidth(final Bitmap bmp) {
		return bmp != null ? bmp.getWidth() : 0;
	}

	private int iconHeight(final Bitmap bmp) {
		return bmp != null ? bmp.getHeight() : 0;
	}

	private void drawSelectionIcon(final Bitmap bmp, final int x, final int y) {
		if (bmp == null || mSelectionIndicatorCanvas == null) {
			return;
		}
		mSelectionIndicatorCanvas.drawBitmap(bmp, x, y, null);
	}

	/** Clears all text from the buffer. */
	public final void clearText() {
		warnIfNotUiThread("clearText");
		mBuffer.dumpToBytes(false);
		mBuffer.prune();
	}
	
	/** If the window was in buffering mode, this function will dump the buffered text into the real buffer. */
	public final void flushBuffer() {
		warnIfNotUiThread("flushBuffer");
		try {
			mBuffer.addBytesImpl(mHoldBuffer.dumpToBytes(false));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		mBuffer.prune();
		drawingIterator = null;
		this.invalidate();
	}

	/** Scroll so that {@code brokenLinesFromBottom} broken lines sit above the live edge. */
	public void scrollToBrokenLineFromBottom(int brokenLinesFromBottom) {
		synchronized (mToken) {
			if (brokenLinesFromBottom < 0) {
				brokenLinesFromBottom = 0;
			}
			int maxScrollLines = Math.max(0, mBuffer.getBrokenLineCount() - mCalculatedLinesInWindow);
			if (brokenLinesFromBottom > maxScrollLines) {
				brokenLinesFromBottom = maxScrollLines;
			}
			mScrollback = SCROLL_MIN + (brokenLinesFromBottom * (double) mPrefLineSize);
			mFlingVelocity = 0;
		}
		invalidate();
	}

	public void setSearchHighlight(String query, int brokenFromBottom, boolean caseSensitive) {
		mSearchHighlightQuery = query;
		mSearchHighlightLine = null;
		mSearchMatchStarts.clear();
		mSearchMatchLen = 0;
		if (query != null && query.length() > 0 && mBuffer != null) {
			int walked = 0;
			for (TextTree.Line line : mBuffer.getLines()) {
				int breaks = 1 + line.breaks;
				if (walked + breaks > brokenFromBottom) {
					mSearchHighlightLine = line;
					break;
				}
				walked += breaks;
			}
			if (mSearchHighlightLine != null) {
				String plain = TextTree.deColorLine(mSearchHighlightLine).toString();
				String hay = caseSensitive ? plain
						: plain.toLowerCase(java.util.Locale.getDefault());
				String needle = caseSensitive ? query
						: query.toLowerCase(java.util.Locale.getDefault());
				mSearchMatchLen = needle.length();
				int from = 0;
				while (from < hay.length() && mSearchMatchLen > 0) {
					int at = hay.indexOf(needle, from);
					if (at < 0) {
						break;
					}
					mSearchMatchStarts.add(Integer.valueOf(at));
					from = at + Math.max(1, mSearchMatchLen);
				}
			}
		}
		invalidate();
	}

	public void clearSearchHighlight() {
		mSearchHighlightLine = null;
		mSearchHighlightQuery = null;
		mSearchMatchStarts.clear();
		mSearchMatchLen = 0;
		invalidate();
	}

	/**
	 * Find {@code query} in scrollback. Returns broken-line offsets from bottom
	 * for each match (newest first), capped at {@code maxResults}. Offset points
	 * at the wrapped visual row that contains the first occurrence on that line.
	 */
	public java.util.ArrayList<Integer> findInScrollback(String query, int maxResults) {
		return findInScrollback(query, maxResults, false);
	}

	public java.util.ArrayList<Integer> findInScrollback(String query, int maxResults, boolean caseSensitive) {
		java.util.ArrayList<Integer> hits = new java.util.ArrayList<Integer>();
		if (query == null || query.trim().isEmpty() || mBuffer == null) {
			return hits;
		}
		String needle = caseSensitive ? query : query.toLowerCase(java.util.Locale.getDefault());
		boolean whenQuery = mScrollDates && LineStamp.looksLikeWhenQuery(query.trim());
		int brokenFromBottom = 0;
		for (TextTree.Line line : mBuffer.getLines()) {
			String plain = TextTree.deColorLine(line).toString();
			String hay = caseSensitive ? plain
					: plain.toLowerCase(java.util.Locale.getDefault());
			int matchAt = hay.indexOf(needle);
			boolean whenHit = whenQuery && LineStamp.matchesQuery(line.getReceivedAt(), query.trim());
			int breaks = 1 + line.breaks;
			if (matchAt >= 0 || whenHit) {
				int breaksBefore = 0;
				int chars = 0;
				for (TextTree.Unit u : line.getData()) {
					if (u instanceof TextTree.Break) {
						if (chars <= matchAt) {
							breaksBefore++;
						}
					} else if (u instanceof TextTree.Text) {
						String s = ((TextTree.Text) u).getString();
						if (s != null) {
							chars += s.length();
						}
					}
				}
				int rowFromBottom = line.breaks - breaksBefore;
				if (rowFromBottom < 0) {
					rowFromBottom = 0;
				}
				hits.add(Integer.valueOf(brokenFromBottom + rowFromBottom));
				if (hits.size() >= maxResults) {
					break;
				}
			}
			brokenFromBottom += breaks;
		}
		return hits;
	}

	/** Redraw matched glyphs in black over the yellow highlight fill. */
	private void drawSearchMatchText(Canvas c, String unitStr, float x, float y, int unitPlainStart) {
		if (mSearchMatchLen <= 0 || mSearchMatchStarts.isEmpty()
				|| unitStr == null || unitStr.length() == 0) {
			return;
		}
		int unitLen = unitStr.length();
		mSearchMatchTextPaint.setTextSize(p.getTextSize());
		mSearchMatchTextPaint.setTypeface(p.getTypeface());
		applyTerminalFontFeatures(mSearchMatchTextPaint);
		for (int mi = 0; mi < mSearchMatchStarts.size(); mi++) {
			int matchStart = mSearchMatchStarts.get(mi).intValue();
			int matchEnd = matchStart + mSearchMatchLen;
			int overlapStart = Math.max(matchStart, unitPlainStart);
			int overlapEnd = Math.min(matchEnd, unitPlainStart + unitLen);
			if (overlapStart >= overlapEnd) {
				continue;
			}
			int localStart = overlapStart - unitPlainStart;
			int localEnd = overlapEnd - unitPlainStart;
			float left = x + cellWidthPrefix(unitStr, localStart);
			drawTextOnGrid(c, unitStr.substring(localStart, localEnd), left, y,
					mSearchMatchTextPaint);
		}
	}

	public String getScrollbackLinePreview(int brokenLinesFromBottom) {
		return getScrollbackLinePreview(brokenLinesFromBottom, null, false);
	}

	/** Preview of the line at {@code brokenLinesFromBottom}, centered on {@code query} when found. */
	public String getScrollbackLinePreview(int brokenLinesFromBottom, String query, boolean caseSensitive) {
		int walked = 0;
		for (TextTree.Line line : mBuffer.getLines()) {
			int breaks = 1 + line.breaks;
			if (walked + breaks > brokenLinesFromBottom) {
				String plain = TextTree.deColorLine(line).toString();
				if (query != null && query.length() > 0 && plain.length() > 0) {
					String hay = caseSensitive ? plain
							: plain.toLowerCase(java.util.Locale.getDefault());
					String needle = caseSensitive ? query
							: query.toLowerCase(java.util.Locale.getDefault());
					int at = hay.indexOf(needle);
					if (at >= 0) {
						int start = Math.max(0, at - 24);
						int end = Math.min(plain.length(), at + query.length() + 24);
						return (start > 0 ? "…" : "") + plain.substring(start, end)
								+ (end < plain.length() ? "…" : "");
					}
				}
				if (plain.length() > 120) {
					return plain.substring(0, 117) + "…";
				}
				return plain;
			}
			walked += breaks;
		}
		return "";
	}

	/** If the window has been scrolled back, this function will return it to home. */
	public final void jumpToZero() {
		synchronized (mToken) {
			SCROLL_MIN = contentHeight() - (double) (5 * Window.this.getResources().getDisplayMetrics().density);
			mScrollback = SCROLL_MIN;
			mFlingVelocity = 0;
		}
	}

	/** This is kind of a hack function, schedule a redraw for i milliseconds.
	 * 
	 * @param i The number of milliseconds to wait before drawing.
	 */
	public final void doDelayedDraw(final int i) {
		if (!mHandler.hasMessages(MESSAGE_DRAW)) {
			mHandler.sendEmptyMessageDelayed(MESSAGE_DRAW, i);
		}
	}

	/** Sets the color debug mode for the window. */
	public void setColorDebugMode(int i) {
		mColorDebugMode = i;
		doDelayedDraw(1);
	}

	/** Sets the active encoding for the window. */
	public void setEncoding(String pEncoding) {	
		mBuffer.setEncoding(pEncoding);
		mHoldBuffer.setEncoding(pEncoding);
		
	}

	public void setCharacterSizes(int fontSize, int fontSpaceExtra) {
		mPrefFontSize = fontSize;
		mPrefLineExtra = fontSpaceExtra;
		mPrefLineSize = (int) (mPrefFontSize + mPrefLineExtra);
		calculateCharacterFeatures(mWidth,mHeight);
	}

	public void setMaxLines(int maxLines) {
		mBuffer.setMaxLines(maxLines);
		mHoldBuffer.setMaxLines(maxLines);
	}

	public void setFont(Typeface font) {
		mPrefFont = font;
	}
	
	public void setBold(boolean bold) {
		if(bold) {
			mPrefFont = Typeface.create(mPrefFont, Typeface.BOLD);
			setGridTypeface(p);
		} else {
			mPrefFont = Typeface.create(mPrefFont, Typeface.NORMAL);
			setGridTypeface(p);
		}
	}
	
	public Typeface getFont() {
		return mPrefFont;
	}

	/** Visible columns in this window (for NAWS / word wrap). */
	public int getCalculatedColumns() {
		return mCalculatedRowsInWindow;
	}

	/** Visible rows in this window (for NAWS). */
	public int getCalculatedRows() {
		return mCalculatedLinesInWindow;
	}
	
	
	/**
	 * How much wider than the screen the text canvas is (1.0 = as now). The
	 * player scrolls the overflow into view sideways; see {@link #mScrollX}.
	 */
	private float mCanvasWidthFactor = 1.0f;
	/** Columns the buffer wraps at — the canvas, not the screen (and not NAWS). */
	private int mWrapColumns = 0;
	/** Pixels of canvas hidden off the left edge. 0 = ordinary behaviour. */
	private float mScrollX = 0f;
	/** Axis this drag was locked to; nothing until the finger has committed. */
	private int mDragAxis = DRAG_UNDECIDED;
	/** Last X seen during a horizontal drag. */
	private Float mMoveLastX = null;
	/**
	 * X where the finger went down. Not start_x: that one is overwritten near
	 * the top of onTouchEvent on every event, including MOVE, so the sideways
	 * distance measured against it was always about one pixel and the axis
	 * lock could only ever come out vertical.
	 */
	private Float mDownX = null;
	private static final int DRAG_UNDECIDED = 0;
	private static final int DRAG_VERTICAL = 1;
	private static final int DRAG_HORIZONTAL = 2;

	/** Most matches one rule may mark on one line — see findTapHitsForLine. */
	private static final int MAX_TAP_HITS_PER_LINE = 16;
	/** Same shape as {@link #linkBoxes}, and in the same (raw touch) space. */
	private final ArrayList<LinkBox> tapBoxes = new ArrayList<LinkBox>();
	/** What a tap on one box does: what it may send, and whether it asks. */
	private static final class TapTarget {
		final String[] commands;
		final boolean tapSendsFirst;

		TapTarget(String[] commands, boolean tapSendsFirst) {
			this.commands = commands;
			this.tapSendsFirst = tapSendsFirst;
		}
	}

	/**
	 * What each box in {@link #tapBoxes} would send, {@code $word} already
	 * filled in, one entry per box and in the same order. Kept beside the boxes
	 * rather than as an index into {@link #mTapRules}: the boxes are rebuilt on
	 * every draw while a trigger edit can replace the rule list in between, and
	 * an index would then point at a different rule or past the end.
	 */
	private final ArrayList<TapTarget> tapCommands = new ArrayList<TapTarget>();
	private int mTouchInTapWord = -1;
	/**
	 * Pending "the finger has been on this word long enough" for the box under
	 * it, or null when nothing is waiting.
	 *
	 * <p>This class handles touch by hand — there is no GestureDetector here and
	 * adding one would have to take over the vertical scroll, the fling and the
	 * sideways drag as well. A posted runnable is the small version: it only
	 * knows about tap words and the loupe, and everything else on the touch path is untouched.
	 */
	private Runnable mTapLongPress = null;
	/**
	 * Set once the menu has been opened by holding, so the finger coming back up
	 * does not then also send a command.
	 */
	private boolean mTapLongPressFired = false;
	private final Paint mTapUnderlinePaint = new Paint();
	private final Paint mTapTextPaint = new Paint();
	private final Paint mWeightPaint = new Paint();
	private final Paint mLoupeHighlightPaint = new Paint();
	private final Paint mLoupePanelPaint = new Paint();
	private final Paint mLoupeTextPaint = new Paint();
	private final RectF mLoupePanelRect = new RectF();
	private ArrayList<TapLoupe.Target> mLoupeMerged = null;
	private TapLoupe.Target mLoupeSelected = null;
	private boolean mLoupeActive = false;
	private float mLoupeFingerX;
	private float mLoupeFingerY;

	/**
	 * One trigger carrying a TapAction: what to look for, what to send, and how
	 * to mark it. The pattern is matched here, while drawing, because the mark
	 * cannot cross the binder — see {@code TapAction}.
	 */
	public static final class TapRule {
		public final java.util.regex.Pattern pattern;
		/** One entry sends straight away; more than one opens a menu. */
		public final String[] commands;
		/** With several commands, whether a tap sends the first one. */
		public final boolean tapSendsFirst;
		public final boolean underline;
		public final boolean bold;
		public final boolean frame;
		/** 0 = the whole match is tappable, 1-9 = that capture group. */
		public final int group;

		public TapRule(java.util.regex.Pattern pattern, String[] commands,
				boolean tapSendsFirst, boolean underline,
				boolean bold, boolean frame, int group) {
			this.pattern = pattern;
			this.commands = commands != null && commands.length > 0
					? commands : new String[] { "look $word" };
			this.tapSendsFirst = tapSendsFirst;
			this.underline = underline;
			this.bold = bold;
			this.frame = frame;
			this.group = group;
		}
	}

	/**
	 * One match on the line being drawn: the columns it covers and the commands
	 * a tap on it sends, {@code $word} and the groups already filled in.
	 *
	 * <p>Matching happens once per line, not once per coloured run, so a match
	 * that crosses a colour change still counts — the trigger that made the rule
	 * sees the whole line too, and the two disagreeing was visible as "the
	 * trigger fired but nothing lit up". The marks are still drawn run by run,
	 * each in its own colour, so a two-colour phrase stays two colours.
	 */
	private static final class TapHit {
		final int startCol;
		final int endCol;
		final String[] commands;
		final boolean tapSendsFirst;
		final boolean underline;
		final boolean bold;
		final boolean frame;

		TapHit(int startCol, int endCol, String[] commands, boolean tapSendsFirst,
				boolean underline, boolean bold, boolean frame) {
			this.startCol = startCol;
			this.endCol = endCol;
			this.commands = commands;
			this.tapSendsFirst = tapSendsFirst;
			this.underline = underline;
			this.bold = bold;
			this.frame = frame;
		}
	}

	/** Shared empty result, so a line with no hits costs no allocation. */
	private static final ArrayList<TapHit> NO_TAP_HITS = new ArrayList<TapHit>();

	/** Hits on the line currently being drawn, in line columns. */
	private ArrayList<TapHit> mLineTapHits = NO_TAP_HITS;

	/**
	 * What was found on one line and what it was found from.
	 *
	 * <p>Generation, character count and unit count are the whole invalidation
	 * test. Text arriving mid-line does not mutate a line: a chunk that carries
	 * on an unfinished line builds a <em>new</em> Line object from the old one's
	 * data, so it is a different key and misses the cache on its own. What is
	 * left to catch is a line rewritten in place — a replace responder — and
	 * that changes the character count, or the unit count when the replacement
	 * happens to be the same length.
	 *
	 * <p>Rewrapping calls updateData and only adds or removes Break units, which
	 * this does not match against and which do not count as characters, so the
	 * hits stay right across a rotation.
	 */
	private static final class CachedTapHits {
		final int generation;
		final int charcount;
		final int units;
		final ArrayList<TapHit> hits;

		CachedTapHits(int generation, int charcount, int units, ArrayList<TapHit> hits) {
			this.generation = generation;
			this.charcount = charcount;
			this.units = units;
			this.hits = hits;
		}
	}

	/**
	 * Tap hits already worked out, per line.
	 *
	 * <p>Weak on purpose: the key is a line of the buffer, and lines are dropped
	 * from the buffer as it scrolls past its limit. A strong map would hold
	 * every line ever drawn alive for the life of the activity.
	 *
	 * <p>{@code TextTree.Line} does not override {@code equals}/{@code hashCode},
	 * so this is identity-keyed, which is what is wanted: two different lines
	 * with the same text are still two entries.
	 */
	private final java.util.WeakHashMap<TextTree.Line, CachedTapHits> mTapHitCache =
			new java.util.WeakHashMap<TextTree.Line, CachedTapHits>();

	/** Bumped by {@link #setTapRules}; every cached line is stale at once. */
	private int mTapRulesGeneration;

	private java.util.List<TapRule> mTapRules = new ArrayList<TapRule>();

	/** Replaces the rule set; called when triggers are loaded or edited. */
	public void setTapRules(final java.util.List<TapRule> rules) {
		mTapRules = rules != null ? rules : new ArrayList<TapRule>();
		mTapRulesGeneration++;
		mTapHitCache.clear();
		mLineTapHits = NO_TAP_HITS;
		this.invalidate();
	}

	/**
	 * Mark every trigger-matched run in this text unit and remember where it was
	 * drawn, so a tap can find it. Runs inside onDraw, so it does nothing at all
	 * — no matcher, no allocation — for a world with no tap triggers.
	 */
	private void markTappableWords(final Canvas c, final TextTree.Text text,
			final float x, final float y, final Paint p, final boolean scrollingGesture,
			final int unitStartCol) {
		if (text == null || mLineTapHits.isEmpty()) {
			return;
		}
		String s = text.getString();
		if (s == null || s.length() == 0) {
			return;
		}
		final int unitEndCol = unitStartCol + s.length();
		for (int i = 0; i < mLineTapHits.size(); i++) {
			TapHit hit = mLineTapHits.get(i);
			// The part of this match that falls inside this coloured run. A
			// match spanning two runs is drawn as two pieces and leaves two hit
			// boxes, both carrying the same commands, so a tap on either half
			// does the same thing.
			int from = Math.max(hit.startCol, unitStartCol);
			int to = Math.min(hit.endCol, unitEndCol);
            if (from >= to) {
				continue;
			}
			drawTapHit(c, x, y, p, s, from - unitStartCol, to - unitStartCol,
					scrollingGesture, hit.commands, hit.tapSendsFirst,
					hit.underline, hit.bold, hit.frame);
		}
	}

	/**
	 * Find every tappable match on one line, before its runs are drawn.
	 *
	 * <p>Costs nothing at all when no trigger carries a tap action: the rule
	 * list is empty and this returns before touching the line.
	 */
	private void findTapHitsForLine(final TextTree.Line line) {
		mLineTapHits = NO_TAP_HITS;
		if (mTapRules.isEmpty() || line == null) {
			return;
		}
		// The player's own regexes, matched against every line on screen. Doing
		// that once per frame is what a fling costs: sixty passes a second over
		// the same forty unchanged lines. The line is the unit of work, so the
		// answer is remembered per line and the fling reads it back.
		java.util.LinkedList<TextTree.Unit> data = line.getData();
		final int units = data != null ? data.size() : 0;
		CachedTapHits cached = mTapHitCache.get(line);
		if (cached != null && cached.generation == mTapRulesGeneration
				&& cached.charcount == line.charcount && cached.units == units) {
			mLineTapHits = cached.hits;
			return;
		}
		ArrayList<TapHit> found = computeTapHitsForLine(line);
		mTapHitCache.put(line,
				new CachedTapHits(mTapRulesGeneration, line.charcount, units, found));
		mLineTapHits = found;
	}

	/**
	 * The matching itself. Returns the shared empty list when nothing matched,
	 * which is the usual answer, so most lines cost no allocation at all.
	 */
	private ArrayList<TapHit> computeTapHitsForLine(final TextTree.Line line) {
		ArrayList<TapHit> out = null;
		StringBuilder plain = mLineTextScratch;
		plain.setLength(0);
		// Line.getIterator() hands out the line's ONE shared iterator, not a new
		// one: walking that here left it at the end and the drawing loop, which
		// asks for the same object straight afterwards, drew no units at all —
		// a connected session with an empty screen. Walk the list itself.
		java.util.LinkedList<TextTree.Unit> units = line.getData();
		if (units == null) {
			return NO_TAP_HITS;
		}
		// A fresh iterator, not get(i): this is a LinkedList and indexed access
		// would walk it again for every unit.
		for (TextTree.Unit unit : units) {
			if (unit instanceof TextTree.Text) {
				String piece = ((TextTree.Text) unit).getString();
				if (piece != null) {
					plain.append(piece);
				}
			}
		}
		if (plain.length() == 0) {
			return NO_TAP_HITS;
		}
		// Matcher takes a CharSequence, so the builder is matched as it stands:
		// this runs per line per frame and a String copy of every line on screen
		// is not something onDraw should be doing.
		CharSequence s = plain;
		for (int r = 0; r < mTapRules.size(); r++) {
			TapRule rule = mTapRules.get(r);
			if (rule.pattern == null) {
				continue;
			}
			java.util.regex.Matcher m = rule.pattern.matcher(s);
			int hits = 0;
			while (m.find()) {
				if (m.end() == m.start()) {
					break;
				}
				// The pattern is whatever the player typed, and this runs for
				// every line on screen. One that matches almost anything (".",
				// "\\s*") would otherwise put a box, a Rect and a String[] on
				// every character of every line. Marking the first few is still
				// a usable answer.
				if (++hits > MAX_TAP_HITS_PER_LINE) {
					break;
				}
				int start = m.start();
				int end = m.end();
				if (rule.group > 0 && rule.group <= m.groupCount()
						&& m.start(rule.group) >= 0) {
					// Only the chosen group lights up; the rest of the match was
					// there to recognise the line.
					start = m.start(rule.group);
					end = m.end(rule.group);
					if (start >= end) {
						continue;
					}
				}
				String tapped = s.subSequence(start, end).toString();
				String[] filled = new String[rule.commands.length];
				for (int i = 0; i < filled.length; i++) {
					filled[i] = fillTapCommand(rule.commands[i], tapped, m);
				}
				if (out == null) {
					out = new ArrayList<TapHit>(4);
				}
				out.add(new TapHit(start, end, filled, rule.tapSendsFirst,
						rule.underline, rule.bold, rule.frame));
			}
		}
		return out != null ? out : NO_TAP_HITS;
	}

	/** Scratch for {@link #findTapHitsForLine}; onDraw allocates nothing new. */
	private final StringBuilder mLineTextScratch = new StringBuilder();

	/**
	 * Fill a command in: {@code $word} is the text that will be tappable,
	 * {@code $0} the whole match, {@code $1}…{@code $9} its capture groups. A
	 * group the pattern does not have becomes empty rather than staying as the
	 * literal {@code $7} — the game should not be sent a dollar sign because a
	 * bracket was removed from the pattern.
	 */
	static String fillTapCommand(final String command, final String tapped,
			final java.util.regex.Matcher m) {
		if (command == null || command.indexOf('$') < 0) {
			return command != null ? command : "";
		}
		StringBuilder out = new StringBuilder(command.length() + 16);
		for (int i = 0; i < command.length(); i++) {
			char ch = command.charAt(i);
			if (ch != '$' || i + 1 >= command.length()) {
				out.append(ch);
				continue;
			}
			if (command.startsWith("$word", i)) {
				out.append(tapped);
				i += "$word".length() - 1;
				continue;
			}
			char next = command.charAt(i + 1);
			if (next >= '0' && next <= '9') {
				int g = next - '0';
				String value = null;
				if (g == 0) {
					value = m.group();
				} else if (g <= m.groupCount()) {
					value = m.group(g);
				}
				out.append(value != null ? value : "");
				i++;
				continue;
			}
			out.append(ch);
		}
		return out.toString();
	}

	/** Mark one run of characters as tappable and remember where it was drawn. */
	private void drawTapHit(final Canvas c, final float x, final float y, final Paint p,
			final String source, final int start, final int end, final boolean scrollingGesture,
			final String[] commands, final boolean tapSendsFirst,
			final boolean underline, final boolean bold,
			final boolean frame) {
		float left = x + cellWidthPrefix(source, start);
		float right = left + cellWidthSpan(source, start, end);
		float bottom = cellBottom(y);
		float top = cellTop(y);

		if (frame) {
			// Subtle box so the word reads as something you can press.
			mTapUnderlinePaint.setStyle(Paint.Style.STROKE);
			mTapUnderlinePaint.setStrokeWidth(Math.max(1f, mDensity));
			mTapUnderlinePaint.setColor(p.getColor());
			mTapUnderlinePaint.setAlpha(110);
			float inset = mDensity;
			c.drawRect(left - inset, top + inset, right + inset, bottom - inset,
					mTapUnderlinePaint);
			mTapUnderlinePaint.setStyle(Paint.Style.FILL);
		}
		if (underline) {
			mTapUnderlinePaint.setColor(p.getColor());
			mTapUnderlinePaint.setAlpha(150);
			c.drawRect(left, bottom - Math.max(2f, mDensity * 1.5f), right, bottom - 1f,
					mTapUnderlinePaint);
		}
		if (bold) {
			// Redraw over the glyphs already on the canvas. Same grid routine as
			// the original draw, so a bold face cannot widen the word and push
			// the rest of the line out of its cells. The colour is the one the
			// text already has — colouring a word is a colour trigger's job.
			mTapTextPaint.setTextSize(p.getTextSize());
			mTapTextPaint.setAntiAlias(true);
			mTapTextPaint.setColor(p.getColor());
			mTapTextPaint.setTypeface(bold ? Typeface.create(mPrefFont, Typeface.BOLD) : mPrefFont);
			applyTerminalFontFeatures(mTapTextPaint);
			mTapTextPaint.setFakeBoldText(bold);
			drawTextOnGrid(c, source.substring(start, end), left, y, mTapTextPaint);
		}

		Rect r = new Rect();
		r.left = (int) left;
		r.right = (int) right;
		r.top = (int) top;
		r.bottom = (int) bottom;
		int heightDips = (int) ((r.bottom - r.top) / mDensity);
		if (heightDips < mLinkBoxHeightMinimum) {
			int extra = (int) (((mLinkBoxHeightMinimum - heightDips) / 2) * mDensity);
			if (extra > 0) {
				r.top -= extra;
				r.bottom += extra;
			}
		}
		if (!scrollingGesture) {
			// LinkBox's constructor drops its first argument (the assignment is
			// commented out) and links fill the data in later via setData. That
			// is why $word came out empty before: getData() was null.
			LinkBox box = new LinkBox(commands[0], r);
			box.setData(commands[0]);
			box.setLabel(source.substring(start, end));
			tapBoxes.add(box);
			tapCommands.add(new TapTarget(commands, tapSendsFirst));
		}
	}

	/**
	 * Ask which command the player meant, anchored on the word that was hit.
	 *
	 * @param cmds the finished commands, {@code $word} already filled in.
	 * @param box where the word was, in the same space the boxes are built in.
	 */
	private void openTapWordMenu(final String[] cmds, final Rect box) {
		if (cmds == null || cmds.length == 0 || box == null) {
			return;
		}
		mMainWindowHandler.sendMessage(mMainWindowHandler.obtainMessage(
				MainWindow.MESSAGE_TAPWORDMENU, box.centerX(), box.top, cmds));
	}

	/**
	 * Start the clock on a hold, when there is something for a hold to do.
	 *
	 * <p>A word with several commands still opens the command menu. Several
	 * different words near the finger — on the same line, or stacked in the
	 * same column — open a loupe instead. A lone one-command word does not
	 * arm a timer.
	 *
	 * <p>The commands and the anchor are taken <b>now</b>, not looked up when the
	 * hold expires. The boxes are rebuilt on every draw and the game keeps
	 * writing while a finger rests on the screen, so by then the same index can
	 * be a different word — the menu would offer commands for something the
	 * player never touched.
	 */
	private void scheduleTapLongPress() {
		cancelTapLongPress();
		mTapLongPressFired = false;
		if (start_x == null || mStartY == null) {
			return;
		}
		final ArrayList<TapLoupe.Target> merged = buildLoupeTargets();
		final int fx = start_x.intValue();
		final int fy = mStartY.intValue();
		final int radius = TapLoupe.radiusPx(mPrefLineSize, mDensity);
		final TapLoupe.Query q = TapLoupe.query(merged, fx, fy, radius,
				Math.max(1, mPrefLineSize));
		if (q.kind == TapLoupe.Kind.NONE || q.selected == null) {
			return;
		}
		mTapLongPress = new Runnable() {
			public void run() {
				mTapLongPress = null;
				mTapLongPressFired = true;
				if (q.kind == TapLoupe.Kind.MENU) {
					Rect box = new Rect(q.selected.left, q.selected.top,
							q.selected.right, q.selected.bottom);
					openTapWordMenu(q.selected.commands, box);
				} else {
					mLoupeMerged = merged;
					mLoupeSelected = q.selected;
					mLoupeFingerX = fx;
					mLoupeFingerY = fy;
					mLoupeActive = true;
					invalidate();
				}
			}
		};
		postDelayed(mTapLongPress, android.view.ViewConfiguration.getLongPressTimeout());
	}

	/** The gesture turned into something else, or ended. Drop the pending hold. */
	private void cancelTapLongPress() {
		if (mTapLongPress != null) {
			removeCallbacks(mTapLongPress);
			mTapLongPress = null;
		}
	}

	private ArrayList<TapLoupe.Target> buildLoupeTargets() {
		ArrayList<TapLoupe.Target> raw = new ArrayList<TapLoupe.Target>();
		int n = Math.min(tapBoxes.size(), tapCommands.size());
		for (int i = 0; i < n; i++) {
			LinkBox box = tapBoxes.get(i);
			TapTarget t = tapCommands.get(i);
			if (box == null || box.getBox() == null || t == null
					|| t.commands == null || t.commands.length == 0) {
				continue;
			}
			Rect r = box.getBox();
			String label = box.getLabel();
			if (label == null || label.length() == 0) {
				label = t.commands[0];
			}
			raw.add(new TapLoupe.Target(r.left, r.top, r.right, r.bottom, label,
					t.commands, t.tapSendsFirst, false));
		}
		for (int i = 0; i < linkBoxes.size(); i++) {
			LinkBox box = linkBoxes.get(i);
			if (box == null || box.getBox() == null || box.getData() == null
					|| box.getData().length() == 0) {
				continue;
			}
			Rect r = box.getBox();
			String label = box.getLabel();
			if (label == null || label.length() == 0) {
				label = box.getData();
			}
			raw.add(new TapLoupe.Target(r.left, r.top, r.right, r.bottom, label,
					new String[] { box.getData() }, true, true));
		}
		return new ArrayList<TapLoupe.Target>(
				TapLoupe.merge(raw, Math.max(1, mPrefLineSize)));
	}

	private void fireLoupeSelection() {
		TapLoupe.Target t = mLoupeSelected;
		if (t == null || t.commands == null || t.commands.length == 0) {
			return;
		}
		if (t.launchHref) {
			Rect box = new Rect(t.left, t.top, t.right, t.bottom);
			int cx = (box.left + box.right) / 2;
			mMainWindowHandler.sendMessage(mMainWindowHandler.obtainMessage(
					MainWindow.MESSAGE_LAUNCHURL, cx, box.top, t.commands[0]));
			return;
		}
		if (TapLoupe.pickOpensMenu(t)) {
			openTapWordMenu(t.commands, new Rect(t.left, t.top, t.right, t.bottom));
			return;
		}
		mMainWindowHandler.sendMessage(mMainWindowHandler.obtainMessage(
				MainWindow.MESSAGE_TAPWORDCOMMAND, t.commands[0]));
	}

	private void dismissLoupe() {
		if (!mLoupeActive && mLoupeMerged == null) {
			return;
		}
		mLoupeActive = false;
		mLoupeMerged = null;
		mLoupeSelected = null;
		invalidate();
	}

	private void drawLoupe(final Canvas c) {
		if (!mLoupeActive || mLoupeSelected == null) {
			return;
		}
		TapLoupe.Target t = mLoupeSelected;
		c.drawRect(t.left, t.top, t.right, t.bottom, mLoupeHighlightPaint);
		String label = t.label;
		if (label == null || label.length() == 0) {
			label = t.commands.length > 0 ? t.commands[0] : "";
		}
		float textSize = Math.max(mPrefLineSize * 1.6f, 18f * mDensity);
		mLoupeTextPaint.setTextSize(textSize);
		mLoupeTextPaint.setTypeface(mPrefFont);
		applyTerminalFontFeatures(mLoupeTextPaint);
		float pad = 8f * mDensity;
		float tw = mLoupeTextPaint.measureText(label);
		float panelW = tw + pad * 2f;
		float panelH = textSize + pad * 2f;
		float px = mLoupeFingerX + 36f * mDensity;
		float py = mLoupeFingerY - panelH - 16f * mDensity;
		if (px + panelW > mWidth) {
			px = mLoupeFingerX - panelW - 36f * mDensity;
		}
		if (px < 0) {
			px = pad;
		}
		if (py < 0) {
			py = mLoupeFingerY + 16f * mDensity;
		}
		mLoupePanelRect.set(px, py, px + panelW, py + panelH);
		c.drawRoundRect(mLoupePanelRect, 8f * mDensity, 8f * mDensity, mLoupePanelPaint);
		c.drawText(label, px + pad, py + pad - mLoupeTextPaint.ascent(), mLoupeTextPaint);
	}

	/** Widest the canvas gets, in pixels. */
	private float canvasWidthPx() {
		return mWrapColumns > 0 ? mWrapColumns * (float) mOneCharWidth : 0f;
	}

	/**
	 * Furthest left the canvas can be pushed. Zero when the canvas is no wider
	 * than the window — which is the factor-1.0 default, so the sideways gesture
	 * is inert for anyone who leaves the option alone.
	 */
	private float maxScrollX() {
		float over = canvasWidthPx() - mWidth;
		return over > 0f ? over : 0f;
	}

	private void clampScrollX() {
		float max = maxScrollX();
		if (mScrollX > max) {
			mScrollX = max;
		}
		if (mScrollX < 0f) {
			mScrollX = 0f;
		}
	}

	/** Move the canvas sideways by {@code dx} pixels. True if it actually moved. */
	public boolean scrollHorizontallyBy(final float dx) {
		float before = mScrollX;
		mScrollX = mScrollX + dx;
		clampScrollX();
		if (mScrollX != before) {
			// The copy widget is anchored to a column, so it has to travel with
			// the canvas rather than stay where it was opened.
			if (theSelection != null && selectedSelector != null) {
				moveWidgetToSelector(selectedSelector);
			}
			this.invalidate();
			return true;
		}
		return false;
	}

	public void setCanvasWidthFactor(final float factor) {
		float f = factor;
		if (f < 1.0f) {
			f = 1.0f;
		}
		if (f > 2.0f) {
			f = 2.0f;
		}
		if (f == mCanvasWidthFactor) {
			return;
		}
		mCanvasWidthFactor = f;
		mScrollX = 0f;
		calculateCharacterFeatures(mWidth, mHeight);
		this.invalidate();
	}

	public float getCanvasWidthFactor() {
		return mCanvasWidthFactor;
	}

	boolean automaticBreaks = true;
	public void setLineBreaks(Integer i) {

			if(i == 0) {
				if(mWrapColumns != 0) {
					mBuffer.setLineBreakAt(mWrapColumns);
				} else {
					mBuffer.setLineBreakAt(80);
				}
				automaticBreaks = true;
			} else {
				mBuffer.setLineBreakAt(i);
				automaticBreaks = false;
			}
		
		
			
		this.invalidate();
	}
	
	public void setWordWrap(boolean pIn ) {
		
			mBuffer.setWordWrap(pIn);
		
			jumpToZero();
		
			this.invalidate();
	}
	
	public void setLinkMode(LINK_MODE mode) {
		this.mLinkMode = mode;
	}
	
	public void setLinkColor(int linkColor) {
		// Old default navy (0xFF3333AA) is unreadable on black MUD backgrounds.
		if ((linkColor & 0x00FFFFFF) == 0x003333AA) {
			linkColor = HyperSettings.DEFAULT_HYPERLINK_COLOR;
		}
		this.mLinkHighlightColor = linkColor;
	}

	public void setBuffer(TextTree buffer) {
		boolean linkify = mBuffer != null && mBuffer.isLinkify();
		this.mBuffer = buffer;
		if (this.mBuffer != null) {
			this.mBuffer.setLinkify(linkify);
			// Service-side trees arrive with the default finder; re-apply this
			// window's bare/extras so Options stick after MainWindow.initWindow
			// (and Extra text) adopt the shared buffer.
			applyUrlLinkSettingsFrom(mSettings);
			this.mBuffer.setDimRepeatedWindow(mDimRepeatedWindow);
			this.mBuffer.setDimRepeatedLines(mDimRepeatedLines);
			this.mBuffer.setOsc8Links(mOsc8Links);
		}
		// Pointer swap only — without a draw kick, a window that already laid
		// out against the empty constructor tree can stay blank after adopting
		// a full service-side buffer (UI process restart onto a live connection).
		drawingIterator = null;
		invalidate();
	}
	
	public void clearAllText() {
			warnIfNotUiThread("clearAllText");
			mBuffer.empty();
	}
	
	public void addBytes(byte[] obj,boolean jumpToEnd) {
			addBytesImpl(obj,jumpToEnd);
	}
	
	public void addText(String str,boolean jumpToEnd) {
		try {
			addBytesImpl(str.getBytes(mBuffer.getEncoding()),jumpToEnd);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
	}
	
	private void addBytesImpl(byte[] obj,boolean jumpToEnd) {
		warnIfNotUiThread("addBytes");
		if(obj.length == 0) return;
		
			if(mBufferText) {
				//synchronized(synch) {
					mHoldBuffer.addBytesImplSimple(obj);
				//}
				return;
			}
			
			int oldbrokencount = mBuffer.getBrokenLineCount();
			double old_max = mBuffer.getBrokenLineCount() * mPrefLineSize;
			//synchronized(synch) {
			int linesadded = 0;
			try {
				linesadded = mBuffer.addBytesImpl(obj);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			int tmpcount = mBuffer.getBrokenLineCount();
			drawingIterator = null;
			
			if(jumpToEnd) {
				mScrollback = SCROLL_MIN;
				//mHandler.sendEmptyMessage(MSG_CLEAR_NEW_TEXT_INDICATOR);
			} else {
				if(mBuffer.getBrokenLineCount() <= mCalculatedLinesInWindow) {
					mScrollback = SCROLL_MIN;
				} else {
					if(mScrollback > SCROLL_MIN + mPrefLineSize ) {
						//scrollback = oldposition * (the_tree.getBrokenLineCount()*PREF_LINESIZE);
						double new_max = mBuffer.getBrokenLineCount()*mPrefLineSize;
						int lines = (int) ((new_max - old_max)/mPrefLineSize);
						
						mScrollback += linesadded*mPrefLineSize;
						//Log.e("BYTE",mName+"REPORT: old_max="+old_max+" new_max="+new_max+" delta="+(new_max-old_max)+" scrollback="+scrollback + " lines="+lines + " oldbroken="+oldbrokencount+ "newbroken="+the_tree.getBrokenLineCount());
						
					} else {
						mScrollback = SCROLL_MIN;
					}
				
				}
			}
			mBuffer.prune();
			tmpcount = mBuffer.getBrokenLineCount();
			drawingIterator = null;
			if (mFingerDown || Math.abs(mFlingVelocity) > FLING_STOP_VELOCITY) {
				postInvalidateOnAnimation();
			} else {
				this.invalidate();
			}
	}
	
	
	
	private Colorizer.COLOR_TYPE updateColorRegisters(Integer i) {
		if(i == null) return Colorizer.COLOR_TYPE.NOT_A_COLOR;

		// Truecolor: ESC[38;2;R;G;Bm / ESC[48;2;R;G;Bm (chafa portraits, modern terminals)
		if (mTrueColorCollect) {
			int component = i.intValue();
			if (component < 0) {
				component = 0;
			} else if (component > 255) {
				component = 255;
			}
			mTrueColorRGB[mTrueColorCount++] = component;
			if (mTrueColorCount >= 3) {
				int packed = (mTrueColorRGB[0] << 16) | (mTrueColorRGB[1] << 8) | mTrueColorRGB[2];
				if (mTrueColorIsFG) {
					mSelectedColor = Integer.valueOf(packed);
					mTrueColorFG = true;
					mXterm256FG = false;
				} else {
					mSelectedBackground = Integer.valueOf(packed);
					mTrueColorBG = true;
					mXterm256BG = false;
				}
				mTrueColorCollect = false;
				mTrueColorCount = 0;
			}
			return null;
		}
		
		if(mXterm256Color) {
			if(mXterm256FGStart) {
				mSelectedColor = i;
				mXterm256FGStart = false;
				mXterm256Color = false;
				mXterm256FG = true;
				mTrueColorFG = false;
			} else if(mXterm256BGStart) {
				mSelectedBackground = i;
				mXterm256BGStart = false;
				mXterm256Color = false;
				mXterm256BG = true;
				mTrueColorBG = false;
			}
			
			return null;
		}
		
		Colorizer.COLOR_TYPE type = Colorizer.getColorType(i);
		switch(type) {
		case FOREGROUND:
			mSelectedColor = i;
			mXterm256FGStart = false;
			mXterm256BGStart = false;
			mXterm256Color = false;
			mXterm256FG = false;
			mTrueColorFG = false;
			mTrueColorCollect = false;
			break;
		case BACKGROUND:
			mSelectedBackground = i;
			mXterm256FGStart = false;
			mXterm256BGStart = false;
			mXterm256Color = false;
			mXterm256BG = false;
			mTrueColorBG = false;
			mTrueColorCollect = false;
			break;
		case ZERO_CODE:
			mSelectedBright = 0;
			mSelectedColor = 37;
			mSelectedBackground = 40;
			mXterm256FGStart = false;
			mXterm256BGStart = false;
			mXterm256Color = false;
			mXterm256FG = false;
			mXterm256BG = false;
			mTrueColorFG = false;
			mTrueColorBG = false;
			mTrueColorCollect = false;
			mTrueColorCount = 0;
			mSgr.clear();
			break;
		case BRIGHT_CODE:
			mSelectedBright = 1;
			mXterm256FGStart = false;
			mXterm256BGStart = false;
			mXterm256Color = false;
			break;
		case NORMAL_INTENSITY:
			// SGR 22: neither bold nor faint. Must clear leftover SGR 1 or
			// default grey (#BBBBBB) paints as bright white (#FFFFFF).
			mSelectedBright = 0;
			mSgr.clearFaint();
			mSgr.clearWeight();
			mXterm256FGStart = false;
			mXterm256BGStart = false;
			mXterm256Color = false;
			break;
		case DIM_CODE:
			// After 38/48, "2" starts truecolor (not classic DIM).
			if (mXterm256FGStart || mXterm256BGStart) {
				mTrueColorCollect = true;
				mTrueColorIsFG = mXterm256FGStart;
				mTrueColorCount = 0;
				mXterm256FGStart = false;
				mXterm256BGStart = false;
				mXterm256Color = false;
			} else {
				mSgr.setFaint(true);
			}
			break;
		case SGR_STYLE:
			mSgr.apply(i.intValue());
			break;
		case DEFAULT_FOREGROUND:
			mSelectedColor = 37;
			mXterm256FGStart = false;
			mXterm256BGStart = false;
			mXterm256Color = false;
			mXterm256FG = false;
			mTrueColorFG = false;
			break;
		case DEFAULT_BACKGROUND:
			mSelectedBackground = 40;
			mXterm256FGStart = false;
			mXterm256BGStart = false;
			mXterm256Color = false;
			mXterm256BG = false;
			mTrueColorBG = false;
			break;
		case XTERM_256_FG_START:
			mXterm256FGStart = true;
			mTrueColorCollect = false;
			break;
		case XTERM_256_BG_START:
			mXterm256BGStart = true;
			mTrueColorCollect = false;
			break;
		case XTERM_256_FIVE:
			if(mXterm256BGStart || mXterm256FGStart) {
				mXterm256Color = true;
			} else {
				mSgr.setBlink(true);
			}
			break;
		default:
			return Colorizer.COLOR_TYPE.NOT_A_COLOR;
		}
		
		return type;
	}

	/**
	 * Fingerprint of the ANSI registers that feed {@link #updateColorRegisters}.
	 * Mid-sequence flags (FG/BG start, truecolor collect) are reset at the top of
	 * every colour unit before its ops run, so they are not part of "before".
	 */
	private int ansiRegisterFingerprint() {
		int h = mSelectedColor != null ? mSelectedColor.intValue() : 0;
		h = 31 * h + (mSelectedBackground != null ? mSelectedBackground.intValue() : 0);
		h = 31 * h + (mSelectedBright != null ? mSelectedBright.intValue() : 0);
		h = 31 * h + (mXterm256FG ? 1 : 0) + (mXterm256BG ? 2 : 0)
				+ (mTrueColorFG ? 4 : 0) + (mTrueColorBG ? 8 : 0);
		h = 31 * h + (mLightPaper ? 16 : 0) + mLightPaperShade;
		h = 31 * h + mSgr.bits();
		h = 31 * h + (mSgr1Weight ? 32 : 0);
		return h;
	}

	/**
	 * Run one colour unit: replay its SGR ops into the registers and paint, or
	 * reuse the memo on the unit when the before-state matches. Measured 12 Aug
	 * 2026: dense combat spent ~10 ms/frame here re-parsing the same ~2500 units
	 * every fling frame; the memo makes steady scrolling revisit cached results.
	 */
	private void applyColorUnit(final TextTree.Color cu, final Paint textPaint,
			final Paint bgPaint) {
		mXterm256Color = false;
		mXterm256FGStart = false;
		mXterm256BGStart = false;
		mTrueColorCollect = false;
		mTrueColorCount = 0;

		final int beforeFp = ansiRegisterFingerprint();
		if (mColorDebugMode == 0 && cu.drawCacheValid && cu.drawCacheBeforeFp == beforeFp) {
			if (textPaint.getColor() != cu.drawCacheFg) {
				textPaint.setColor(cu.drawCacheFg);
			}
			if (bgPaint.getColor() != cu.drawCacheBg) {
				bgPaint.setColor(cu.drawCacheBg);
			}
			if (bgPaint == b) {
				mBgPaintColor = cu.drawCacheBg;
			}
			mSelectedColor = cu.drawCacheSelectedColor;
			mSelectedBackground = cu.drawCacheSelectedBackground;
			mSelectedBright = cu.drawCacheSelectedBright;
			mXterm256FG = cu.drawCacheXterm256FG;
			mXterm256BG = cu.drawCacheXterm256BG;
			mTrueColorFG = cu.drawCacheTrueColorFG;
			mTrueColorBG = cu.drawCacheTrueColorBG;
			mSgr.setBits(cu.drawCacheSgr);
			applySgrDecorations(textPaint);
			return;
		}

		final java.util.ArrayList<Integer> ops = cu.getOperations();
		if (ops != null) {
			for (int i = 0, n = ops.size(); i < n; i++) {
				updateColorRegisters(ops.get(i));
			}
		}

		if (mColorDebugMode == 2 || mColorDebugMode == 3) {
			textPaint.setColor(LightPaper.remapForeground(
					0xFF000000 | Colorizer.getColorValue(0, 37, false),
					mLightPaper, true, mLightPaperShade));
			setBgPaintColor(LightPaper.remapBackground(
					0xFF000000 | Colorizer.getColorValue(0, 40, false),
					mLightPaper, true, mLightPaperShade));
			return;
		}

		applyAnsiPaints(textPaint, bgPaint);

		cu.drawCacheBeforeFp = beforeFp;
		cu.drawCacheFg = textPaint.getColor();
		cu.drawCacheBg = bgPaint == b ? mBgPaintColor : bgPaint.getColor();
		cu.drawCacheSelectedColor = mSelectedColor;
		cu.drawCacheSelectedBackground = mSelectedBackground;
		cu.drawCacheSelectedBright = mSelectedBright;
		cu.drawCacheXterm256FG = mXterm256FG;
		cu.drawCacheXterm256BG = mXterm256BG;
		cu.drawCacheTrueColorFG = mTrueColorFG;
		cu.drawCacheTrueColorBG = mTrueColorBG;
		cu.drawCacheSgr = mSgr.bits();
		cu.drawCacheValid = true;
	}

	/** Apply current FG/BG registers to text and background paints. */
	private void applyAnsiPaints(final Paint textPaint, final Paint bgPaint) {
		final boolean defaultFg = LightPaper.isDefaultAnsiForeground(
				mSelectedColor, mXterm256FG, mTrueColorFG);
		final boolean defaultBg = LightPaper.isDefaultAnsiBackground(
				mSelectedBackground, mXterm256BG, mTrueColorBG);
		final int fgRaw;
		if (mTrueColorFG) {
			fgRaw = 0xFF000000 | (mSelectedColor.intValue() & 0xFFFFFF);
		} else {
			fgRaw = 0xFF000000 | Colorizer.getColorValue(
					mSelectedBright, mSelectedColor, mXterm256FG);
		}
		int fg = LightPaper.remapForeground(fgRaw, mLightPaper, defaultFg,
				mLightPaperShade);
		final int bgRaw = mTrueColorBG
				? (0xFF000000 | (mSelectedBackground.intValue() & 0xFFFFFF))
				: (0xFF000000 | Colorizer.getColorValue(0, mSelectedBackground, mXterm256BG));
		int bg = LightPaper.remapBackground(bgRaw, mLightPaper, defaultBg,
				mLightPaperShade);
		if (mSgr.reverse()) {
			final int swapped = fg;
			fg = bg;
			bg = swapped;
		}
		if (mSgr.faint()) {
			fg = LightPaper.dimTowardPaper(fg, SgrStyle.FAINT_DIM_PERCENT,
					mLightPaper, mLightPaperShade);
		}
		if (textPaint.getColor() != fg) {
			textPaint.setColor(fg);
		}
		if (bgPaint.getColor() != bg) {
			bgPaint.setColor(bg);
		}
		if (bgPaint == b) {
			mBgPaintColor = bg;
		}
		applySgrDecorations(textPaint);
	}

	/**
	 * Underline / strike / italic skew from {@link #mSgr}. Italic is skew, not
	 * {@link Typeface#ITALIC} — {@code ensureGridCache} keys typeface+size.
	 */
	private void applySgrDecorations(final Paint textPaint) {
		textPaint.setUnderlineText(mSgr.underline() && !mSgr.doubleUnderline());
		textPaint.setStrikeThruText(mSgr.strike());
		textPaint.setTextSkewX(mSgr.italic() ? SgrStyle.ITALIC_SKEW : 0f);
	}

	/** Bleed search found an open FG; remap it the same way {@link #applyAnsiPaints} does. */
	private void themeBleedForeground() {
		final boolean defaultFg = LightPaper.isDefaultAnsiForeground(
				mSelectedColor, mXterm256FG, mTrueColorFG);
		p.setColor(LightPaper.remapForeground(p.getColor(), mLightPaper, defaultFg,
				mLightPaperShade));
	}

	private int themedLinkColor() {
		return LightPaper.remapForeground(mLinkHighlightColor, mLightPaper, false,
				mLightPaperShade);
	}

	/** Scale resolved FG toward the paper by the player's dim strength. */
	private int dimRepeatedForeground(final int color) {
		return LightPaper.dimTowardPaper(color, mDimRepeatedStrength, mLightPaper,
				mLightPaperShade);
	}

	/**
	 * Apply {@link #mResolvedFg} to {@code paint}, dimmed when this line is a
	 * repeat. Called after colour-cache lookup so a dimmed line cannot write
	 * the scaled value into {@code Color.drawCacheFg}.
	 */
	private void applyRepeatedLineForeground(final Paint paint) {
		if (mPaintingDimLine) {
			paint.setColor(dimRepeatedForeground(mResolvedFg));
		} else if (paint.getColor() != mResolvedFg) {
			paint.setColor(mResolvedFg);
		}
	}
	
	public void setCullExtraneous(boolean pIn) {
		
		//synchronized(synch) {
			mBuffer.setCullExtraneous(pIn);
		//}
			
	}
	
	private class IteratorBundle {
		private ListIterator<TextTree.Line> i;
		private Float offset;
		private int extraLines;
		private int startLine;
		public IteratorBundle(ListIterator<TextTree.Line> pI,double pOffset,int lines,int startline) {
			setI(pI);
			setOffset((float)pOffset);
			setExtraLines(lines);
			setStartLine(startline);
		}
		public void setOffset(Float offset) {
			this.offset = offset;
		}
		public Float getOffset() {
			return offset;
		}
		public void setI(ListIterator<TextTree.Line> i) {
			this.i = i;
		}
		public ListIterator<TextTree.Line> getI() {
			return i;
		}
		public void setExtraLines(int extraLines) {
			this.extraLines = extraLines;
		}
		public int getExtraLines() {
			return extraLines;
		}
		public int getStartLine() {
			return startLine;
		}
		public void setStartLine(int startLine) {
			this.startLine = startLine;
		}
		
	}
	ListIterator<Line> drawingIterator = null;
	private IteratorBundle getScreenIterator(double pIn, float pLineSize) {
		float working_h = 0;
		double pY = pIn;
		double max = mBuffer.getBrokenLineCount() * pLineSize;
		if (pY >= max) {
			pY = max;
		}

		int startline = 0;
		int current = 0;
		// A fresh listIterator() is already positioned at the head and costs O(1) on
		// a LinkedList. Rewinding the cached one instead walked every node back to
		// the start on each frame, so scrolling got slower the longer the scrollback
		// grew — visibly janky well before the buffer looked full on screen.
		drawingIterator = mBuffer.getLines().listIterator();

		if (mBuffer.getBrokenLineCount() <= mCalculatedLinesInWindow) {
			int offset = 0;
			final int ch = contentHeight();
			if (mPrefLineSize * mCalculatedLinesInWindow < ch) {
				offset = ((mPrefLineSize) * mCalculatedLinesInWindow) - ch;
			}
			int under = mCalculatedLinesInWindow - (mBuffer.getBrokenLineCount() - 1);
			// Count broken lines (wraps), not Line objects — matches touch/selection indices.
			while (drawingIterator.hasNext()) {
				Line l = drawingIterator.next();
				startline += 1 + l.getBreaks();
			}
			// First drawn row is oldest; workingline starts at this value then decrements to 0 (newest).
			if (startline > 0) {
				startline = startline - 1;
			}
			float tmpy = (under * pLineSize - (offset + (mPrefLineSize / 3)));

			return new IteratorBundle(drawingIterator, tmpy, 0, startline);
		}
		int lines = 1;

		while (drawingIterator.hasNext()) {
			Line l = drawingIterator.next();
			working_h += pLineSize * (1 + l.getBreaks());
			current += 1 + l.getBreaks();
			lines = lines + 1;

			if (working_h >= pY) {
				int y = 0;
				final int ch = contentHeight();
				if (mPrefLineSize * mCalculatedLinesInWindow < ch) {
					y = ((mPrefLineSize) * mCalculatedLinesInWindow) - ch;
				}
				double delta = working_h - pY;
				double offset = delta - pLineSize;
				int extra = (int) Math.ceil(delta / pLineSize);
				if (drawingIterator.hasPrevious()) {
					drawingIterator.previous();
				}
				if (l.breaks > 0) {
					startline += l.breaks;
				}
				return new IteratorBundle(drawingIterator, -1 * offset, extra, startline);
			}
			startline += 1 + l.getBreaks();
		}

		return new IteratorBundle(drawingIterator, pLineSize, 0, startline);
	}

	public void setLinksEnabled(boolean hyperLinkEnabled) {
		mBuffer.setLinkify(hyperLinkEnabled);
	}

	public boolean windowShowing = false;
	public boolean loaded() {
		
		return windowShowing;
	}

	private IWindowCallback.Stub mCallback = new IWindowCallback.Stub() {

		public void rawDataIncoming(byte[] raw) throws RemoteException {
			mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_ADDTEXT, raw));
		}

		public void redraw() throws RemoteException {
			mHandler.sendEmptyMessage(Window.MESSAGE_FLUSHBUFFER);
		}

		public void shutdown() throws RemoteException {
			mHandler.sendEmptyMessage(MESSAGE_SHUTDOWN);
		}

		public void xcallS(String function, String str) throws RemoteException {
			Message msg = mHandler.obtainMessage(MESSAGE_PROCESSXCALLS,str);
			msg.getData().putString("FUNCTION", function);
			mHandler.sendMessage(msg);
		}

		public void clearText() throws RemoteException {
			mHandler.sendEmptyMessage(MESSAGE_CLEARTEXT);
		}

		@Override
		public void updateSetting(String key, String value)
				throws RemoteException {
			//mHandler.sendMessage(mHandler.ob)
			Message m = mHandler.obtainMessage(MESSAGE_SETTINGSCHANGED);
			m.getData().putString("KEY", key);
			m.getData().putString("VALUE", value);
			mHandler.sendMessage(m);
		}
		
		public void setEncoding(String value) {
			mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_ENCODINGCHANGED,value));
		}

		public void setLocalEcho(boolean enabled) {
			mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_LOCALECHO, enabled ? 1 : 0, 0));
		}

		public void tapRulesChanged() {
			// Straight to the activity: it owns the rules and coalesces the
			// refreshes, and this window has nothing to do with them.
			Handler h = mMainWindowHandler;
			if (h != null) {
				h.removeMessages(MainWindow.MESSAGE_REFRESHTAPRULES);
				h.sendEmptyMessageDelayed(MainWindow.MESSAGE_REFRESHTAPRULES, 250);
			}
		}

		@Override
		public void xcallB(String function, byte[] raw) throws RemoteException {
			Message m = mHandler.obtainMessage(MESSAGE_XCALLB,raw);
			m.getData().putString("FUNCTION", function);
			mHandler.sendMessage(m);
		}

		@Override
		public void resetWithRawDataIncoming(byte[] raw) throws RemoteException {
			mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_RESETWITHDATA,raw));
			
		}
		
	};
	
	public IWindowCallback.Stub getCallback() {
		return mCallback;
	}

	public void setBufferText(boolean bufferText) {
		this.mBufferText = bufferText;
	}

	public boolean isBufferText() {
		return mBufferText;
	}


	
	protected void xcallS(String string, String str) {
		if(mL == null) return;
		mL.getGlobal("debug");
		mL.getField(mL.getTop(), "traceback");
		mL.remove(-2);
		
		mL.getGlobal(string);
		if(mL.getLuaObject(-1).isFunction()) {
			
			//need to start iterating the given map, re-creating the table on the other side.
			//pushTable("",obj);
			mL.pushString(str);
			
			int ret = mL.pcall(1, 1, -3);
			if(ret !=0) {
				displayLuaError("WindowXCallT Error:" + mL.getLuaObject(-1).getString());
			}
			// Error branch used to pop neither the error object nor the
			// traceback function.
			mL.pop(2);
			
		} else {
			mL.pop(2);
		}
	}
	

	private void initLua() {
		mL.openLibs();
		String launchPath = mParent.getPathForPlugin(mOwner);
		if(mDataDir == null) {
			//this is bad.
		} else {
			
			//set up the path/cpath.
			//TODO: add the plugin load path.
			String packagePath = mDataDir + "/lua/share/5.1/?.lua";
			if(launchPath != null && !launchPath.equals("")) {
				File file = new File(launchPath);
				String dir = file.getParent();
				//file.getPar
				//L.pushString(dir);
				packagePath += ";" + dir + "/?.lua";
			}
			mL.getGlobal("package");
			mL.pushString(packagePath);
			mL.setField(-2, "path");
			
			mL.pushString(LuaLibraryHelper.buildCPath(getContext(), mDataDir));
			mL.setField(-2, "cpath");
			mL.pop(1);
			
		}
		
		
		NoteFunction df = new NoteFunction(mL);
		OptionsMenuFunction omf = new OptionsMenuFunction(mL);
		PluginXCallSFunction pxcf = new PluginXCallSFunction(mL);
		SheduleCallbackFunction scf = new SheduleCallbackFunction(mL);
		CancelSheduleCallbackFunction cscf = new CancelSheduleCallbackFunction(mL);
		GetDisplayDensityFunction gddf = new GetDisplayDensityFunction(mL); 
		SendToServerFunction stsf = new SendToServerFunction(mL);
		GetExternalStorageDirectoryFunction gesdf = new GetExternalStorageDirectoryFunction(mL);
		PushMenuStackFunction pmsf = new PushMenuStackFunction(mL);
		PopMenuStackFunction popmsf = new PopMenuStackFunction(mL);
		GetStatusBarHeight gsbshf = new GetStatusBarHeight(mL);
		StatusBarHiddenMethod sghm = new StatusBarHiddenMethod(mL);
		GetActionBarHeightFunction gabhf = new GetActionBarHeightFunction(mL);
		GetPluginInstallDirectoryFunction gpisdf = new GetPluginInstallDirectoryFunction(mL);
        CloseOptionsDialogFunction codf = new CloseOptionsDialogFunction(mL);
        GetActivityFunction gaf = new GetActivityFunction(mL);
        PluginInstalledFunction pif = new PluginInstalledFunction(mL);
        WindowSupportsFunction wsf = new WindowSupportsFunction(mL);
        WindowCallFunction wcf = new WindowCallFunction(mL);
        WindowBroadcastFunction wbcf = new WindowBroadcastFunction(mL);
        GetOptionValueFunction ogvf = new GetOptionValueFunction(mL);
		try {
			
			gsbshf.register("GetStatusBarHeight");
			//iv.register("Invalidate");
			df.register("Note");
			//bf.register("GetBounds");
			omf.register("AddOptionCallback");
			pxcf.register("PluginXCallS");
			scf.register("ScheduleCallback");
			cscf.register("CancelCallback");
			gddf.register("GetDisplayDensity");
			stsf.register("SendToServer");
			gesdf.register("GetExternalStorageDirectory");
			pmsf.register("PushMenuStack");
			popmsf.register("PopMenuStack");
			sghm.register("IsStatusBarHidden");
			gabhf.register("GetActionBarHeight");
			gpisdf.register("GetPluginInstallDirectory");
			codf.register("CloseOptionsDialog");
			gaf.register("GetActivity");
			pif.register("PluginInstalled");
			wcf.register("WindowCall");
			wsf.register("WindowSupports");
			wbcf.register("WindowBroadcast");
			ogvf.register("GetOptionValue");
		} catch (LuaException e) {
			e.printStackTrace();
		}
		
	}
	
	
	boolean noScript = true;
	public void loadScript(String body) {
		
		if(body == null || body.equals("")) {
			noScript = true;
			if(mL != null) {
				mL.close();
				mL = null;
			}
			return;
		} else {
			noScript = false;
		}
		if(mL != null) {
			mL.close();
			mL = null;
		}
		this.mL = LuaStateFactory.newLuaState();
		initLua();
		mL.pushJavaObject(this);
		mL.setGlobal("view");
		
		
		
		mL.getGlobal("debug");
		mL.getField(mL.getTop(), "traceback");
		mL.remove(-2);
		mL.LloadString(body);
		int ret = mL.pcall(0, 1, -2);
		if(ret != 0) {
			displayLuaError("Error Loading Script: "+mL.getLuaObject(mL.getTop()).getString());
		}
		// Error branch used to pop neither the error object nor the traceback
		// function.
		mL.pop(2);

	}
	

	
	/*! \page page1
\section window Window Functions
\subsection AddOptionCallback AddOptionCallback
Add a top level menu item that will call a global function when pressed.

\par Full Signature
\luacode
AddOptionCallback(functionName,menuText,iconDrawable)
\endluacode
\param functionName \b string value of the function name that will be called when the menu item is pressed.
\param menuText \b string value that will appear on the menu item.
\param iconDrawable \b android.graphics.drawable.Drawable the drawable resource that will be used for the icon.
\returns nothing
\par Example with no icon
\luacode
AddOptionCallback("functionName","Click Me!",nil)
\endluacode
\par Example with icon
\luacode
drawable = luajava.newInstance("android.drawable.BitmapDrawable",context:getResources(),"/path/to/image.png")
function menuClicked()
	Note("Menu Item Clicked!")
end

AddOptionCallback("menuClicked","Click Me!",drawable)
\endluacode

	*/
	private class OptionsMenuFunction extends JavaFunction {

		public OptionsMenuFunction(LuaState L) {
			super(L);
		}

		@Override
		public int execute() throws LuaException {
			String funcName = this.getParam(2).getString();
			String title = this.getParam(3).getString();
			
			
			
			
			Object o = null;
			LuaObject tmp = this.getParam(4);
			if(tmp != null && tmp.isJavaObject()) {
				o = tmp.getObject();
			}
			
			//Handler h = 
			Message msg = mMainWindowHandler.obtainMessage(MainWindow.MESSAGE_ADDOPTIONCALLBACK);
			if(o != null) msg.obj = o;
			Bundle b = msg.getData();
			b.putString("funcName", funcName);
			b.putString("title", title);
			b.putString("window", mName);
			msg.setData(b);
			mMainWindowHandler.sendMessage(msg);
			return 0;
		}
		
	}
	
  /*! \page page1
\subsection sec4 CancelCallback
Cancel a scheduled call made with ScheduleCallback.
\note This will cancel all pending callbacks with the given identifier.

\par Full Signature
\luacode
CancelCallback(id)
\endluacode
\param id \b number the callback id to cancel
\returns nothing
\par Example 
\luacode
CancelCallback(100)
\endluacode
	*/
	private class CancelSheduleCallbackFunction extends JavaFunction {

		public CancelSheduleCallbackFunction(LuaState L) {
			super(L);

		}

		@Override
		public int execute() throws LuaException {
			int id = Integer.parseInt(this.getParam(2).getString());
			//String callback = this.getParam(3).getString();
			//callScheduleCallback(id,callback);
			callbackHandler.removeMessages(id);
			return 0;
		}
		
	}
	
  /*! \page page1
\subsection sec16 CloseOptionsDialog
Closes the Options dialog if it is currently open.

\par Full Signature
\luacode
CloseOptionsDialog()
\endluacode
\param none
\returns nothing
\par Example 
\luacode
CloseOptionsDialog()
\endluacode
	*/
	private class CloseOptionsDialogFunction extends JavaFunction {
		public CloseOptionsDialogFunction(LuaState L) {
			super(L);
		}
		
		@Override
		public int execute() throws LuaException {
			// TODO Auto-generated method stub
			mMainWindowHandler.sendMessage(mMainWindowHandler.obtainMessage(MainWindow.MESSAGE_CLOSEOPTIONSDIALOG));
			return 0;
		}
	}
	
	private class GetActionBarHeightFunction extends JavaFunction {

		public GetActionBarHeightFunction(LuaState L) {
			super(L);
			// TODO Auto-generated constructor stub
		}

		@Override
		public int execute() throws LuaException {
			// TODO Auto-generated method stub
			L.pushString(Integer.toString(((int)Window.this.mParent.getTitleBarHeight())));
			return 1;
		}
		
	}
	
 /*! \page page1
\subsection sec0 GetActivity
Get a handle to the current Activity that is hosting the foreground window process.

\par Full Signature
\luacode
GetActivity()
\endluacode
\param none
\returns \b android.app.Activity the current Activity that is hosting the foreground processes.
\par Example 
\luacode
activity = GetActivity()
\endluacode
	*/
	private class GetActivityFunction extends JavaFunction {

		public GetActivityFunction(LuaState L) {
			super(L);
			// TODO Auto-generated constructor stub
		}

		@Override
		public int execute() throws LuaException {
			//Log.e("PLUGIN","Get External storage state:"+Environment)
			L.pushJavaObject((Activity)mParent.getActivity());
			return 1;
		}
		
	}
	
	 /*! \page page1
	\subsection GetOptionValue GetOptionValue
	Gets an option value by key from the parent plugin's option table.

	\par Full Signature
	\luacode
	GetOptionValue(key)
	\endluacode
	\param key \b unique key for the option given.
	\returns The value of the option, this could be a string, number or boolean, lists will return the ordinal of the selected value.
	\par Example 
	\luacode
	value = GetOptionValue("input_mode")
	\endluacode
		*/
		private class GetOptionValueFunction extends JavaFunction {
			//HashMap<String,String> 
			public GetOptionValueFunction(LuaState L) {
				super(L);
				// TODO Auto-generated constructor stub
			}

			@Override
			public int execute() throws LuaException {
				//String token = this.getParam(2).getString();
				String key = this.getParam(2).getString();
				//LuaObject foo = this.getParam(3);
				
				
				//--if(foo.isTable()) {
				//	Log.e("DEBUG","ARGUMENT IS TABLE");
				//}
				//HashMap<String,Object> dump = dumpTable("t",3);
				//
				/*L.pushNil();
				while(L.next(2) != 0) {
					
					String id = L.toString(-2);
					LuaObject l = L.getLuaObject(-1);
					if(l.isTable()) {
						//need to dump more tables
					} else {
						
					}
				}*/
				//mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_X, obj))
				//Message msg = mMainWindowHandler.obtainMessage(MainWindow.MESSAGE_PLUGINXCALLS,foo.getString());
				
				String ret;
				try {
					ret = mParent.getPluginOption(mOwner, key);
				} catch (RemoteException e) {
					L.pushNil();
					return 1;
				}
				
				//msg.getData().putString("PLUGIN",mOwner);
				//msg.getData().putString("FUNCTION", function);
				L.pushString(ret);
				//mMainWindowHandler.sendMessage(msg);
				// TODO Auto-generated method stub
				return 1;
			}		
			
		}
	
	private class GetDisplayDensityFunction extends JavaFunction {

		public GetDisplayDensityFunction(LuaState L) {
			super(L);
			
		}

		@Override
		public int execute() throws LuaException {
			float density = Window.this.getContext().getResources().getDisplayMetrics().density;
			//if((Window.this.getContext().getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_XLARGE) {
			//	density = density * 1.5f;
			//}
			//Log.e("WINODW","PUSHING DENSITY:"+Float.toString(density));
			L.pushNumber(density);
			return 1;
		}
		
	}
	

		private class GetExternalStorageDirectoryFunction extends JavaFunction {

			public GetExternalStorageDirectoryFunction(LuaState L) {
				super(L);
				// TODO Auto-generated constructor stub
			}

			@Override
			public int execute() throws LuaException {
				//Log.e("PLUGIN","Get External storage state:"+Environment)
				if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
					L.pushString(Environment.getExternalStorageDirectory().getAbsolutePath());
				} else {
					L.pushNil();
				}
				return 1;
			}
			
		}
		
	 
		

	private class GetPluginInstallDirectoryFunction extends JavaFunction {

		public GetPluginInstallDirectoryFunction(LuaState L) {
			super(L);
			// TODO Auto-generated constructor stub
		}

		@Override
		public int execute() throws LuaException {
			//Log.e("PLUGIN","Get External storage state:"+Environment)
			/*if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
				L.pushString(Environment.getExternalStorageDirectory().getAbsolutePath());
			} else {
				L.pushNil();
			}*/
			String path = mParent.getPathForPlugin(mOwner);
			//Log.e("LUA","FETCHED PATH ("+path+") for plugin, "+mOwner);
			File file = new File(path);
			String dir = file.getParent();
			//file.getPar
			L.pushString(dir);
			return 1;
		}
		
	}
	

	private class GetStatusBarHeight extends JavaFunction {

		public GetStatusBarHeight(LuaState L) {
			super(L);
			// TODO Auto-generated constructor stub
		}

		@Override
		public int execute() throws LuaException {
			// TODO Auto-generated method stub
			L.pushString(Integer.toString((int)Window.this.mParent.getStatusBarHeight()));
			return 1;
		}
		
	}
	
	/*! \page page1
\subsection sec14 IsStatusBarHidden
Gets the state of the status bar.

\par Full Signature
\luacode
IsStatusBarHidden()
\endluacode
\param none
\returns \b bool true if the status bar is hidden (full screen), false if the status bar is being shown (non full screen)
\par Example 
\luacode
if(IsStatusBarHidden()) then
 Note("status bar hidden")
else
 Note("status bar not hidden")
end
\endluacode
	*/
	private class StatusBarHiddenMethod extends JavaFunction {

		public StatusBarHiddenMethod(LuaState L) {
			super(L);
			// TODO Auto-generated constructor stub
		}

		@Override
		public int execute() throws LuaException {
			// TODO Auto-generated method stub
			L.pushBoolean(Window.this.mParent.isStatusBarHidden());
			return 1;
		}
		
	}
	
	

	protected class NoteFunction extends JavaFunction {

		public NoteFunction(LuaState L) {
			super(L);
			// TODO Auto-generated constructor stub
		}

		@Override
		public int execute() throws LuaException {
			String foo = this.getParam(2).getString();
			//Log.e("LUAWINDOW","DEBUG:"+foo);
			Window.this.mParent.dispatchLuaText(foo);
			return 0;
		}
		
	}
	
  /*! \page page1
\subsection PluginXCallS PluginXCallS
Calls a function in the parent plugin's Lua state. Provides one way signaling across the AIDL bridge to the plugin host running in the background.

\par Full Signature
\luacode
PluginXCallS(functionName,data)
\endluacode
\param functionName \b string the global function in the plugin's host Lua state.
\param data \b string the data to pass as a argument to the given function
\returns nothing
\par Example 
\luacode
PluginXCallS("saveData","300")
\endluacode
\note Tables can be serialized to a string and reconstituted in the plugin using loadstring(...) but the performance may suffer if the tables are large. See PluginXCallB for a slightly faster method of communication that doesn't involve the heavy Java string manipulation.
	*/
	private class PluginXCallSFunction extends JavaFunction {
		//HashMap<String,String> 
		public PluginXCallSFunction(LuaState L) {
			super(L);
			// TODO Auto-generated constructor stub
		}

		@Override
		public int execute() throws LuaException {
			//String token = this.getParam(2).getString();
			String function = this.getParam(2).getString();
			LuaObject foo = this.getParam(3);
			
			
			//--if(foo.isTable()) {
			//	Log.e("DEBUG","ARGUMENT IS TABLE");
			//}
			//HashMap<String,Object> dump = dumpTable("t",3);
			//
			/*L.pushNil();
			while(L.next(2) != 0) {
				
				String id = L.toString(-2);
				LuaObject l = L.getLuaObject(-1);
				if(l.isTable()) {
					//need to dump more tables
				} else {
					
				}
			}*/
			//mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_X, obj))
			Message msg = mMainWindowHandler.obtainMessage(MainWindow.MESSAGE_PLUGINXCALLS,foo.getString());
			
			msg.getData().putString("PLUGIN",mOwner);
			msg.getData().putString("FUNCTION", function);
			
			mMainWindowHandler.sendMessage(msg);
			// TODO Auto-generated method stub
			return 0;
		}		
		
	}
	

	
  /*! \page page1
\subsection sec12 PopMenuStack
Removes the current menu item and returns the menu stack to its previous state.

\par Full Signature
\luacode
PopMenuStack()
\endluacode
\param none
\returns nothing
\par Example 
\luacode
PopMenuStack()
\endluacode
\see PushMenuStack
	*/
	private class PopMenuStackFunction extends JavaFunction {

		public PopMenuStackFunction(LuaState L) {
			super(L);
			// TODO Auto-generated constructor stub
		}

		@Override
		public int execute() throws LuaException {
			mMainWindowHandler.sendMessage(mMainWindowHandler.obtainMessage(MainWindow.MESSAGE_POPMENUSTACK));
			return 0;
		}
		
	}
		
 /*! \page page1
\subsection sec11 PushMenuStack
Starts the creating of a new menu object, providing a global function name to call that will handle weather or not the back button is pressed while the menu stack is active.
After this function is called, the [PopulateMenu](entry_points.html#PopulateMenu) entry point will be called when the operating system has created the new menu to show.


\par Full Signature
\luacode
PushMenuStack(callbackName)
\endluacode
\param \b string the name of a global function to call if the back button is pressed to cancel the menu.
\returns nothing
\par Example 
\luacode
function PopulateMenu(menu)
 menu:addItem(0,1,1,foo)
end
PushMenuStack("menuBackPressed")

function menuBackPressed()
	PopMenuStack()
end
\endluacode
\see this relies largely on the Android Menu and MenuItem classes, please refer to the documentation and other menu related sample code.
	*/
	private class PushMenuStackFunction extends JavaFunction {

		public PushMenuStackFunction(LuaState L) {
			super(L);
			// TODO Auto-generated constructor stub
		}

		@Override
		public int execute() throws LuaException {
			//LuaObject func = this.getParam(2);
			//L.isFunction(-1);
			/*if(!L.isFunction(-1)) {
				this.L.pushString("Argument must be a function call back to be called on back button press.");
				this.L.error();
				return 0;
			}*/
			//this.L.LcheckString(2);
			
			String function = this.getParam(2).getString();
			
			//Log.e("PUSHMENUSTACK","FUNCTION NAME:"+function);
			
			Message m = mMainWindowHandler.obtainMessage(MainWindow.MESSAGE_PUSHMENUSTACK,Window.this.mName);
			m.getData().putString("CALLBACK", function);
			
			mMainWindowHandler.sendMessage(m);
			return 0;
		}
		
	}
	

	private class SendToServerFunction extends JavaFunction {

		public SendToServerFunction(LuaState L) {
			super(L);
			// TODO Auto-generated constructor stub
		}

		@Override
		public int execute() throws LuaException {
			if(this.getParam(2).isNil()) { return 0; }
			//Log.e("LUAWINDOW","script is sending:"+this.getParam(2).getString()+" to server.");
			mMainWindowHandler.sendMessage(mMainWindowHandler.obtainMessage(MainWindow.MESSAGE_SENDBUTTONDATA,this.getParam(2).getString()));
			return 0;
		}
		
	}
		
  /*! \page page1
\subsection sec3 ScheduleCallback
Add a top level menu item that will call a global function when pressed.

\par Full Signature
\luacode
ScheduleCallback(id,callbackName,delayMillis)
\endluacode
\param id \b number unique identifier associated with this event, will be passed to the callback.
\param callbackName \b string name of the global function to call after the desired elapsed time.
\param delayMillis \b dumber how long in milliseconds to delay the execution of the callback
\returns nothing
\par Example
\luacode
function delayCallback(id)
 Note(string.format("event %d fired.",id))
end

ScheduleCallback(100,"delayCallback",3000)
ScheduleCallback(104,"delayCallback",5000)
\endluacode
\tableofcontents
	*/
	private class SheduleCallbackFunction extends JavaFunction {

		public SheduleCallbackFunction(LuaState L) {
			super(L);

		}

		@Override
		public int execute() throws LuaException {
			int id = (int)this.getParam(2).getNumber();
			String callback = this.getParam(3).getString();
			long delay = Long.parseLong(this.getParam(4).getString());
			//callScheduleCallback(id,callback);
			Message msg = callbackHandler.obtainMessage(id,callback);
			callbackHandler.sendMessageDelayed(msg, delay);
			return 0;
		}
		
	}
	

	
	private Handler callbackHandler = new Handler() {
		public void handleMessage(Message msg) {
			//
			//just call the string.
			Window.this.callScheduleCallback(msg.arg1,(String)msg.obj);
			
			
		}
	};
	
	
	
	private void callScheduleCallback(int id,String callback) {
		if(mL == null) return;
		mL.getGlobal("debug");
		mL.getField(-1, "traceback");
		mL.remove(-2);
		
		mL.getGlobal(callback);
		if(mL.getLuaObject(-1).isFunction()) {
			//prepare to call.
			mL.pushString(Integer.toString(id));
			int ret = mL.pcall(1, 1, -3);
			if(ret != 0) {
				displayLuaError("Scheduled callback("+callback+") error:"+mL.getLuaObject(-1).toString());
			}
			// Same as xcallB: the error branch popped neither the error object
			// nor the traceback function.
			mL.pop(2);
		} else {
			//error no function.
			mL.pop(2);
		}
	}
	
	public void callFunction(String callback, String data) {
		mL.getGlobal("debug");
		mL.getField(mL.getTop(), "traceback");
		mL.remove(-2);
		
		mL.getGlobal(callback);
		if(mL.isFunction(mL.getTop())) {
			if(data != null) {
				mL.pushString(data);
			} else {
				mL.pushNil();
			}
			int tmp = mL.pcall(1, 1, -3);
			if(tmp != 0) {
				displayLuaError("Error calling window script function "+callback+": "+mL.getLuaObject(-1).getString());
			}
			// remove(-2) above left the traceback function on the stack, and
			// pcall leaves one result whichever way it went — so two, not one.
			// This popped 1 on success and 0 on error, which made it the worst
			// leak of the set: it grew on the NORMAL path. Everything
			// MainWindow.windowCall does comes through here — clearButtons on
			// every pause, restoreButtons on every resume, cancelTouchGesture
			// on every cancelled touch.
			mL.pop(2);
		} else {
			mL.pop(2);
		}
	}
	
  /*! \page page1
\subsection PluginInstalled PluginInstalled
Checks whether a plugin is installed.

\par Full Signature
\luacode
PluginInstalled(name)
\endluacode
\param name \b the plugin name to test.
\returns \b boolean whether or not the plugin is installed.
\par Example 
\luacode
if(PluginInstalled("button_window")) then
	WindowCall("button_window","clearButtons")
end
\endluacode
	*/
	private class PluginInstalledFunction extends JavaFunction {

		public PluginInstalledFunction(LuaState L) {
			super(L);
			
		}

		@Override
		public int execute() throws LuaException, RemoteException {
			String desired = this.getParam(2).getString();
			boolean result = mParent.isPluginInstalled(desired);
			//parent.isPluginInstalled();
			L.pushBoolean(result);
			return 1;
		}
		
	}

  /*! \page page1
\subsection WindowBroadcast WindowBroadcast
Calls a named global function in every window (if the window has defined it).

\par Full Signature
\luacode
WindowBroadcast(function,arg)
\endluacode
\param function \b the function to call.
\param arg \b string a string or number to provide to the function as an argument.
\returns nothing
\par Example 
\luacode
WindowBroadcast("adjustZOrder","now")
\endluacode
	*/
	private class WindowBroadcastFunction extends JavaFunction {

		public WindowBroadcastFunction(LuaState L) {
			super(L);
			// TODO Auto-generated constructor stub
		}

		@Override
		public int execute() throws LuaException, RemoteException {
			String function = this.getParam(2).getString();
			String data = this.getParam(3).getString();
			mParent.windowBroadcast(function, data);
			
			return 0;
		}
		
	}
  /*! \page page1
\subsection WindowCall WindowCall
Calls a named global function on the target window.

\par Full Signature
\luacode
WindowCall(name,function,arg)
\endluacode
\param name \b string the name of the window to target.
\param function \b the function to call.
\param arg \b string a string or number to provide to the function as an argument.
\returns nothing
\par Example 
\luacode
WindowCall("button_window","loadButtonSet","default")
\endluacode
\see WindowSupports to test whether or not it has a global function of a desired name.
	*/
	private class WindowCallFunction extends JavaFunction {

		public WindowCallFunction(LuaState L) {
			super(L);
			// TODO Auto-generated constructor stub
		}

		@Override
		public int execute() throws LuaException, RemoteException {
			String window = this.getParam(2).getString();
			String function = this.getParam(3).getString();
			String data = this.getParam(4).getString();
			
			mParent.windowCall(window,function,data);
			return 0;
		}
		
	}
	  /*! \page page1
\subsection WindowSupports WindowSupports
Tests whether a named global function exists in the target window.

\par Full Signature
\luacode
WindowSupports(name,function)
\endluacode
\param name \b string the name of the window to target.
\param function \b the function to test.
\returns \b boolean true if the window has a global function named \b function, false if not.
\par Example 
\luacode
if(WindowSupports("button_window","clearButtons")) then
	WindowCall("button_window","clearButtons")
end
\endluacode
\tableofcontents
		*/
		private class WindowSupportsFunction extends JavaFunction {

			public WindowSupportsFunction(LuaState L) {
				super(L);
				// TODO Auto-generated constructor stub
			}

			@Override
			public int execute() throws LuaException, RemoteException {
				String desired = this.getParam(2).getString();
				String function = this.getParam(2).getString();
				boolean ret = mParent.checkWindowSupports(desired,function);
				L.pushBoolean(ret);
				return 1;
			}
			
		}
	
	/*public void callFunction(String callback,Object o) {
		L.getGlobal("debug");
		L.getField(L.getTop(), "traceback");
		L.remove(-2);
		
		L.getGlobal(callback);
		
		if(L.isFunction(L.getTop())) {
			L.pushJavaObject(o);
			int tmp = L.pcall(1, 1, -3);
			if(tmp != 0) {
				displayLuaError("Error calling script callback: "+L.getLuaObject(-1).getString());
			} else {
				L.pop(2);
			}
		} else {
			L.pop(2);
		}
	}*/



	public void setDimensions(int width, int height) {
		LayoutParams p = (LayoutParams) this.getLayoutParams();
		p.width = width;
		p.height = height;
		mWidth = width;
		mHeight = height;
		calculateCharacterFeatures(mWidth,mHeight);
		//View v = ((View)this.getParent());
		//RelativeLayout.LayoutParams p = (LayoutParams) v.getLayoutParams();
		//p.height = mHeight;
		//p.width = mWidth;
		
		//Log.e("WINDOW","WINDOW HEIGHT NOW:" + mHeight);
		//v.setLayoutParams(p);
		//v.requestLayout();
		//this.requestLayout();
	}
	
	public void setWidth(int width) {
		LayoutParams p = (LayoutParams) this.getLayoutParams();
		p.width = width;
		//p.height = height;
		this.mWidth = width;
		calculateCharacterFeatures(mWidth,mHeight);
	}
	
	public void setHeight(int height) {
		LayoutParams p = (LayoutParams) this.getLayoutParams();
		//p.width = width;
		p.height = height;
		calculateCharacterFeatures(mWidth,mHeight);
	}
	

	/*public void updateAnchor(int x, int y) {
		View v = ((View)this.getParent());
		//v.setPadding(x, y, 0, 0);
		LayoutParams p = (LayoutParams) v.getLayoutParams();
		p.setMargins(x, y, 0, 0);
		
		//v.requestLayout();
	}*/
	
	public RelativeLayout getParentView() {
		return (RelativeLayout)this.getParent();
	}

	public void onCustomAnimationEnd() {
		//call into lua to notify that the parent animation has completed.
		callFunction("onParentAnimationEnd",null);
	}
	
	@Override
	public void onAnimationEnd() {
		//call into lua to notify that the parent animation has completed.
		callFunction("onAnimationEnd",null);
	}
	
	/*public void addView(View v) {
		RelativeLayout tmp = this.getParentView();
		tmp.addView(v);
		//tmp.getLayoutParams().
		//RelativeLayout.LayoutParams p = (LayoutParams) tmp.getLayoutParams();
		//p.setma
	}*/
	
	public int getMaxHeight() {
		return mMaxHeight;
	}
	
	public int getMaxWidth() {
		return mMaxHeight;
	}



	@Override
	public void updateSetting(String key, String value) {
		//convert to enum value, then switch, handle accordingly.
		BaseOption o = (BaseOption) mSettings.findOptionByKey(key);
		if (o == null) {
			return;
		}
		o.setValue(value);
		try {
			KEYS tmp = KEYS.valueOf(key);
			switch(tmp) {
			case hyperlinks_enabled:
				this.setLinksEnabled((Boolean)o.getValue());
				break;
			case hyperlink_mode:
				this.setLinkMode((Integer)o.getValue());
				break;
				
			case hyperlink_color:
				this.setLinkColor((Integer)o.getValue());
				break;
			case hyperlink_bare_domains:
			case hyperlink_extra_tlds:
				applyUrlLinkSettingsFrom(mSettings);
				break;
				
			case word_wrap:
				this.setWordWrap((Boolean)o.getValue());
				break;
			case text_canvas_width:
				// Stored as percent so it can be an IntegerOption like the rest.
				setCanvasWidthFactor(((Integer) o.getValue()).intValue() / 100f);
				break;
			case newest_at_top:
				mNewestAtTop = (Boolean) o.getValue();
				jumpToZero();
				this.invalidate();
				break;
			case dim_repeated_lines:
				applyDimRepeatedLines((Boolean) o.getValue());
				this.invalidate();
				break;
			case dim_repeated_window:
				applyDimRepeatedWindow((Integer) o.getValue());
				this.invalidate();
				break;
			case dim_repeated_strength:
				applyDimRepeatedStrength((Integer) o.getValue());
				this.invalidate();
				break;
			case light_paper:
				applyLightPaper((Boolean) o.getValue());
				if ("mainDisplay".equals(mName) && mMainWindowHandler != null) {
					mMainWindowHandler.sendEmptyMessage(MainWindow.MESSAGE_REFRESH_LIGHT_PAPER);
				}
				break;
			case light_paper_shade:
				{
					int n = LightPaper.clampShade(((Integer) o.getValue()).intValue());
					o.setValue(Integer.valueOf(n));
					applyLightPaperShade(n);
				}
				if ("mainDisplay".equals(mName) && mMainWindowHandler != null) {
					mMainWindowHandler.sendEmptyMessage(MainWindow.MESSAGE_REFRESH_LIGHT_PAPER);
				}
				break;
			case scroll_dates:
				mScrollDates = (Boolean) o.getValue();
				this.invalidate();
				break;
			case scroll_dates_opacity:
				{
					int n = WindowToken.clampScrollDatesOpacity(
							((Integer) o.getValue()).intValue());
					o.setValue(Integer.valueOf(n));
					mScrollDatesOpacity = n;
				}
				this.invalidate();
				break;
			case osc8_links:
				applyOsc8Links((Boolean) o.getValue());
				this.invalidate();
				break;
			case top_padding:
				mTopPadding = Math.max(0, (Integer) o.getValue());
				calculateCharacterFeatures(mWidth, mHeight);
				jumpToZero();
				this.invalidate();
				break;
			case bottom_padding:
				mBottomPadding = Math.max(0, (Integer) o.getValue());
				calculateCharacterFeatures(mWidth, mHeight);
				jumpToZero();
				this.invalidate();
				break;
			case bottom_padding_keyboard:
				mBottomPaddingIme = Math.max(0, (Integer) o.getValue());
				calculateCharacterFeatures(mWidth, mHeight);
				jumpToZero();
				this.invalidate();
				break;
			case ime_keep_text:
				mImeKeepText = (Boolean) o.getValue();
				if (mMainWindowHandler != null) {
					mMainWindowHandler.sendEmptyMessage(MainWindow.MESSAGE_REFRESH_IME_LIFT);
				}
				break;
			case input_bar_show_edit:
			case input_bar_show_send:
				// Main-window chrome only; extra-text tokens ignore the layout refresh.
				if ("mainDisplay".equals(mName) && mMainWindowHandler != null) {
					mMainWindowHandler.sendEmptyMessage(MainWindow.MESSAGE_REFRESH_INPUT_ACTIONS);
				}
				break;
			case scroll_sensitivity:
				applyScrollSensitivityChoice((Integer) o.getValue());
				// Overlays set to "same as main window" track this value, and they
				// live in MainWindow, not in the settings tree.
				if ("mainDisplay".equals(mName) && mMainWindowHandler != null) {
					mMainWindowHandler.sendEmptyMessage(MainWindow.MESSAGE_REFRESH_EXTRA_TEXT_SCROLL);
				}
				break;
			
			case color_option:
				switch((Integer)o.getValue()) {
				case 0:
					this.setColorDebugMode(0);
					break;
				case 1:
					this.setColorDebugMode(3);
					break;
				case 2:
					this.setColorDebugMode(1);
					break;
				case 3:
					this.setColorDebugMode(2);
				}
				break;				
			case font_size:
				setCharacterSizes((Integer)o.getValue(),mPrefLineExtra);
				break;
			case line_extra:
				setCharacterSizes(mPrefFontSize,(Integer)o.getValue());
				break;
			case buffer_size:
				mBuffer.setMaxLines((Integer)o.getValue());
				mHoldBuffer.setMaxLines(mBuffer.getMaxLines());
				// setMaxLines clamps. Put the number it settled on back into the
				// option, so the field shows what the window really keeps rather
				// than what was typed at it.
				if (mBuffer.getMaxLines() != ((Integer)o.getValue()).intValue()) {
					((IntegerOption)o).setValue(Integer.valueOf(mBuffer.getMaxLines()));
				}
				Message msg = mMainWindowHandler.obtainMessage(MainWindow.MESSAGE_WINDOWBUFFERMAXCHANGED);
				msg.arg1 = mBuffer.getMaxLines();
				msg.getData().putString("PLUGIN", this.mOwner);
				msg.getData().putString("WINDOW", mName);
				mMainWindowHandler.sendMessage(msg);
				break;
			case font_path:
				mPrefFont = loadFontFromName((String)o.getValue());
				setGridTypeface(p);
				this.invalidate();
				break;
			case tap_dismiss_keyboard:
				mTapDismissKeyboard = (Boolean) o.getValue();
				break;
				
			}
		} catch(IllegalArgumentException ignored) {
			// Key belongs to some other settings group; not ours to apply.
		} catch (NullPointerException ignored) {
			// Missing option object — ignore rather than crash the options UI.
		}
	}
	
	private void setLinkMode(Integer value) {
		switch(value) {
		case 0:
			setLinkMode(HyperSettings.LINK_MODE.NONE);
			break;
		case 1:
			setLinkMode(HyperSettings.LINK_MODE.HIGHLIGHT);
			break;
		case 2:
			setLinkMode(HyperSettings.LINK_MODE.HIGHLIGHT_COLOR);
			break;
		case 3:
			setLinkMode(HyperSettings.LINK_MODE.HIGHLIGHT_COLOR_ONLY_BLAND);
			break;
		case 4:
			setLinkMode(HyperSettings.LINK_MODE.BACKGROUND);
			break;
		}
	}

	private enum KEYS {
		hyperlinks_enabled,
		hyperlink_mode,
		hyperlink_color,
		hyperlink_bare_domains,
		hyperlink_extra_tlds,
		word_wrap,
		text_canvas_width,
		newest_at_top,
		dim_repeated_lines,
		dim_repeated_window,
		dim_repeated_strength,
		light_paper,
		light_paper_shade,
		scroll_dates,
		scroll_dates_opacity,
		osc8_links,
		top_padding,
		bottom_padding,
		bottom_padding_keyboard,
		ime_keep_text,
		input_bar_show_edit,
		input_bar_show_send,
		scroll_sensitivity,
		color_option,
		screen_on,
		font_size,
		line_extra,
		buffer_size,
		font_path,
		tap_dismiss_keyboard
	}

	/**
	 * Turn off liga/kern/calt on grid paints. The typeface's own advances are
	 * not the cell grid (batched drawText drifted after emoji fallback; the
	 * ASCII probe still reported uniform widths).
	 */
	private static final String TERMINAL_FONT_FEATURES =
			"'liga' 0,'dlig' 0,'clig' 0,'calt' 0,'kern' 0";

	private void setGridTypeface(final Paint paint) {
		paint.setTypeface(mPrefFont);
		applyTerminalFontFeatures(paint);
	}

	private static void applyTerminalFontFeatures(final Paint paint) {
		if (paint == null) {
			return;
		}
		paint.setFontFeatureSettings(TERMINAL_FONT_FEATURES);
	}

	private Typeface loadFontFromName(String name) {
		Typeface font = loadBundledOrSystemMonospace();
		if (name == null || name.length() == 0 || name.equals("none") || name.equals("monospace")) {
			return font;
		}
		try {
			// Bundled assets: "fonts/DejaVuSansMono.ttf"
			if (name.startsWith("fonts/") && name.endsWith(".ttf")) {
				// Null means the asset is missing. Keep the mono fallback loaded
				// above rather than handing null to Paint, which would silently
				// switch the grid to the default proportional face.
				final Typeface asset = typefaceFromAssetWithEmojiFallback(name);
				return asset != null ? asset : font;
			}
			if(name.contains("/")) {
				if(name.contains(Environment.getExternalStorageDirectory().getPath())) {
					String sdstate = Environment.getExternalStorageState();
					if(Environment.MEDIA_MOUNTED.equals(sdstate) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(sdstate)) {
						font = Typeface.createFromFile(name);
					}
				} else {
					font = Typeface.createFromFile(name);
				}
			} else {
				if(name.equals("sans serif") || name.equals("sans serrif")) {
					font = Typeface.SANS_SERIF;
				} else if (name.equals("default")) {
					font = Typeface.DEFAULT;
				} else {
					font = loadBundledOrSystemMonospace();
				}
			}
		} catch (RuntimeException e) {
			font = loadBundledOrSystemMonospace();
		}
		return font;
	}

	/**
	 * Prefer the bundled DejaVu Sans Mono (includes U+2580–U+259F at mono width),
	 * then a real system mono TTF, then {@link Typeface#MONOSPACE}.
	 */
	private Typeface loadBundledOrSystemMonospace() {
		Typeface bundled = typefaceFromAssetWithEmojiFallback("fonts/DejaVuSansMono.ttf");
		if (bundled != null) {
			return bundled;
		}
		String[] candidates = new String[] {
				"/system/fonts/DroidSansMono.ttf",
				"/system/fonts/RobotoMono-Regular.ttf",
				"/system/fonts/CutiveMono.ttf",
				"/system/fonts/NotoSansMono-Regular.ttf",
				"/system/fonts/SourceCodePro-Regular.ttf"
		};
		for (String path : candidates) {
			java.io.File f = new java.io.File(path);
			if (f.isFile()) {
				try {
					return Typeface.createFromFile(f);
				} catch (RuntimeException ignored) {
				}
			}
		}
		return Typeface.MONOSPACE;
	}

	/**
	 * DejaVu from assets does not pick up the system emoji chain by itself, so
	 * missing glyphs become tofu (measured on baudtest U+1F400 and the
	 * LociTerm line). API 29+ chains Noto Color Emoji as a fallback for holes
	 * only; Block Elements stay DejaVu.
	 */
	private Typeface typefaceFromAssetWithEmojiFallback(final String assetPath) {
		if (android.os.Build.VERSION.SDK_INT >= 29) {
			Typeface chained = typefaceApi29WithEmoji(assetPath);
			if (chained != null) {
				return chained;
			}
		}
		try {
			return Typeface.createFromAsset(getContext().getAssets(), assetPath);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	@android.annotation.TargetApi(29)
	private Typeface typefaceApi29WithEmoji(final String assetPath) {
		try {
			android.graphics.fonts.Font monoFont = new android.graphics.fonts.Font.Builder(
					getContext().getAssets(), assetPath).build();
			android.graphics.fonts.FontFamily monoFamily =
					new android.graphics.fonts.FontFamily.Builder(monoFont).build();
			Typeface.CustomFallbackBuilder builder =
					new Typeface.CustomFallbackBuilder(monoFamily);
			addSystemEmojiFallback(builder);
			return builder.build();
		} catch (java.io.IOException e) {
			return null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	@android.annotation.TargetApi(29)
	private void addSystemEmojiFallback(final Typeface.CustomFallbackBuilder builder) {
		String[] paths = {
				"/system/fonts/NotoColorEmoji.ttf",
				"/system/fonts/NotoColorEmojiFlags.ttf",
		};
		for (String path : paths) {
			java.io.File f = new java.io.File(path);
			if (!f.isFile()) {
				continue;
			}
			try {
				android.graphics.fonts.Font font =
						new android.graphics.fonts.Font.Builder(f).build();
				android.graphics.fonts.FontFamily family =
						new android.graphics.fonts.FontFamily.Builder(font).build();
				builder.addCustomFallback(family);
			} catch (java.io.IOException ignored) {
			} catch (IllegalArgumentException ignored) {
				return;
			}
		}
	}
	
	private View.OnTouchListener textSelectionTouchHandler = new View.OnTouchListener() {
		
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			//calculate out the position
			
			float x = event.getX();
			float y = event.getY();
			final int pad = textPadTop();
			final float localY = Math.max(0f, y - pad);
			
			if (mNewestAtTop) {
				y = localY + (float) (mScrollback - SCROLL_MIN);
			} else {
				// convert y to be at the bottom of the content area.
				y = (float) contentHeight() - localY;
				y += (mScrollback - SCROLL_MIN);
			}
			
			float xform_to_line = y / (float)mPrefLineSize;
			int line = (int)Math.floor(xform_to_line);
			
			float xform_to_column = x / (float)mOneCharWidth;
			int column = (int)Math.floor(xform_to_column);
			
			switch(event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				//if(firstPress) {

					
				//} else {
					if(Math.abs(theSelection.start.line - line) < 2 && Math.abs(theSelection.start.column - column) < 2) {
						selectedSelector = theSelection.start;
						selectionFingerDown = true;
						moveWidgetToSelector(selectedSelector);
						//Log.e("window","moving start selector");
						v.invalidate();
					} else if(Math.abs(theSelection.end.line - line) < 2 && Math.abs(theSelection.end.column - column) < 2) {
						selectedSelector = theSelection.end;
						moveWidgetToSelector(selectedSelector);
						selectionFingerDown = true;
						//Log.e("window","moving end selector");
						v.invalidate();
					} else {
						int modx = (int) x - (mWidgetX - mSelectionIndicatorHalfDimension);
						int mody = (int) event.getY() - (mWidgetY - mSelectionIndicatorHalfDimension);
						if(mSelectionIndicatorRect.contains(modx,mody)) {
							
							//int newx = (int) (x - selectionIndicatorRect.left);
							//int newy = (int) (mody - selectionIndicatorRect.top);
							
							int full = mSelectionIndicatorHalfDimension * 2;
							int third = full / 3;
							
							int col = modx / third;
							
							int row = mody / third;
							
							switch(row) {
							case 0:
								switch(col) {
								case 0:
									//upper left
									selectionButtonDown = SelectionWidgetButtons.NEXT;
									break;
								case 1:
									//upper middle
									selectionButtonDown = SelectionWidgetButtons.UP;
									int remainder = ((int)(mScrollback-SCROLL_MIN) % mPrefLineSize)-mPrefLineSize;
									//selectorCenterY -= PREF_LINESIZE;
									//if(selectorCenterY - (2*PREF_LINESIZE) < remainder) {
										//selectorCenterY = selectorCenterY + PREF_LINESIZE;
										//scrollback += PREF_LINESIZE;
										mHandler.sendEmptyMessageDelayed(MESSAGE_SCROLLUP,700);
									//}
									break;
								case 2:
									//upper right
									selectionButtonDown = SelectionWidgetButtons.COPY;
									break;
								}
								break;
							case 1:
								switch(col) {
								case 0:
									//middle left
									selectionButtonDown = SelectionWidgetButtons.LEFT;
									mHandler.sendEmptyMessageDelayed(MESSAGE_SCROLLLEFT,700);
									break;
								case 1:
									//center
									selectionButtonDown = SelectionWidgetButtons.CENTER;
									widgetCenterMovedX = 0;
									widgetCenterMovedY = 0;
									widgetCenterMoveXLast = (int) x;
									widgetCenterMoveYLast = (int) event.getY();
									break;
								case 2:
									selectionButtonDown = SelectionWidgetButtons.RIGHT;
									mHandler.sendEmptyMessageDelayed(MESSAGE_SCROLLRIGHT, 700);
									//middle right
									break;
								}
								break;
							case 2:
								switch(col) {
								case 0:
									//bottom left
									selectionButtonDown = SelectionWidgetButtons.EXIT;
									break;
								case 1:
									//bottom middle
									selectionButtonDown = SelectionWidgetButtons.DOWN;
									//int remainder = ((int)(scrollback-SCROLL_MIN) % PREF_LINESIZE) + PREF_LINESIZE;
									//selectorCenterY += PREF_LINESIZE;
									//if(selectorCenterY + PREF_LINESIZE > v.getHeight() - remainder) {
										//send the message to start scrolling.
										mHandler.sendEmptyMessageDelayed(MESSAGE_SCROLLDOWN,700);
									//}
									//calculateWidgetPosition(selectorCenterX,selectorCenterY);
									break;
								case 2:
									//bottom right
									break;
								}
								break;
							}
						}
						
						
					//}
				}
				break;
				
			case MotionEvent.ACTION_MOVE:
				if(selectionButtonDown != null && selectionButtonDown == SelectionWidgetButtons.CENTER) {
					widgetCenterMovedX += (x - widgetCenterMoveXLast);
					widgetCenterMovedY -= (event.getY() - widgetCenterMoveYLast);
					widgetCenterMoveXLast = (int) x;
					widgetCenterMoveYLast = (int) event.getY();
					if(Math.abs(widgetCenterMovedX) > mSelectionCharacterWidth) {
						int sign = (int)Math.signum(widgetCenterMovedX);
						if(sign > 0) {
							mScrollRepeatRate = SCROLL_REPEAT_RATE;
							mScrollRepeatRateStep = 1;
							doScrollRight(false);
						} else if(sign < 0) {
							mScrollRepeatRate = SCROLL_REPEAT_RATE;
							mScrollRepeatRateStep = 1;
							doScrollLeft(false);
						}
//						selectedSelector.column += 1 * Math.signum(widgetCenterMovedX);
//						selectorCenterX += one_char_is_this_wide * Math.signum(widgetCenterMovedX);
//						calculateWidgetPosition(selectorCenterX,selectorCenterY);
						widgetCenterMovedX = 0;
						v.invalidate();
					}
					if(Math.abs(widgetCenterMovedY) > mSelectionIndicatorFontSize) {
						int sign = (int)Math.signum(widgetCenterMovedY);
						if(sign > 0) {
							mScrollRepeatRate = SCROLL_REPEAT_RATE;
							mScrollRepeatRateStep = 1;
							doScrollUp(false);
						} else if(sign < 0) {
							mScrollRepeatRate = SCROLL_REPEAT_RATE;
							mScrollRepeatRateStep = 1;
							doScrollDown(false);
						}
//						selectedSelector.line += 1 * Math.signum(widgetCenterMovedY);
//						selectorCenterY += PREF_LINESIZE * -Math.signum(widgetCenterMovedY);
//						calculateWidgetPosition(selectorCenterX,selectorCenterY);
						widgetCenterMovedY = 0;
						v.invalidate();
					}
					return true;
				}
				if(selectedSelector != null && selectionFingerDown == true) {
					selectedSelector.column = column;
					selectedSelector.line = line;
					//Log.e("window","moving selector-> line:"+line+" col:"+column);
					
					widgetCenterMovedX += (x - widgetCenterMoveXLast);
					widgetCenterMovedY -= (event.getY() - widgetCenterMoveYLast);
					widgetCenterMoveXLast = (int) x;
					widgetCenterMoveYLast = (int) event.getY();
					if(Math.abs(widgetCenterMovedX) > mOneCharWidth) {
						int sign = (int)Math.signum(widgetCenterMovedX);
						if(sign > 0) {
							mScrollRepeatRate = SCROLL_REPEAT_RATE;
							mScrollRepeatRateStep = 1;
							doScrollRight(false);
						} else if(sign < 0) {
							mScrollRepeatRate = SCROLL_REPEAT_RATE;
							mScrollRepeatRateStep = 1;
							doScrollLeft(false);
						}
						widgetCenterMovedX = 0;
						v.invalidate();
					} 
					
					if(Math.abs(widgetCenterMovedY) > mSelectionIndicatorFontSize) {
						int sign = (int) Math.signum(widgetCenterMovedY);
						if(sign > 0) {
							mScrollRepeatRate = SCROLL_REPEAT_RATE;
							mScrollRepeatRateStep = 1;
							doScrollUp(false);
						} else if(sign < 0) {
							mScrollRepeatRate = SCROLL_REPEAT_RATE;
							mScrollRepeatRateStep = 1;
							doScrollDown(false);
						}

						widgetCenterMovedY = 0;
						v.invalidate();
					}

					
					v.invalidate();
				}
				break;
			case MotionEvent.ACTION_UP:
				selectionFingerDown = false;
				if(theSelection != null) {
					mHandler.removeMessages(MESSAGE_SCROLLDOWN);
					mHandler.removeMessages(MESSAGE_SCROLLUP);
					mHandler.removeMessages(MESSAGE_SCROLLLEFT);
					mHandler.removeMessages(MESSAGE_SCROLLRIGHT);
					mScrollRepeatRate = SCROLL_REPEAT_RATE;
					mScrollRepeatRateStep = 1;
					
					int mod2x = (int) x - (mWidgetX - mSelectionIndicatorHalfDimension);
					int mod2y = (int) event.getY() - (mWidgetY - mSelectionIndicatorHalfDimension);
					if(mSelectionIndicatorRect.contains(mod2x,mod2y)) {
						
						//int newx = (int) (x - (widgetX - selectionIndic);
						//int newy = (int) (event.getY() - selectionIndicatorRect.top);
						
						int full = mSelectionIndicatorHalfDimension * 2;
						int third = full / 3;
						
						int col = mod2x / third;
						
						int row = mod2y / third;
						
						SelectionWidgetButtons tmp = null;
						
						switch(row) {
						case 0:
							switch(col) {
							case 0:
								//upper left
								tmp = SelectionWidgetButtons.NEXT;
								break;
							case 1:
								//upper middle
								tmp = SelectionWidgetButtons.UP;
								break;
							case 2:
								//upper right
								tmp = SelectionWidgetButtons.COPY;
								break;
							}
							break;
						case 1:
							switch(col) {
							case 0:
								//middle left
								tmp = SelectionWidgetButtons.LEFT;
								break;
							case 1:
								//center
								tmp = SelectionWidgetButtons.CENTER;
								break;
							case 2:
								tmp = SelectionWidgetButtons.RIGHT;
								//middle right
								break;
							}
							break;
						case 2:
							switch(col) {
							case 0:
								//bottom left
								tmp = SelectionWidgetButtons.EXIT;
								break;
							case 1:
								//bottom middle
								tmp = SelectionWidgetButtons.DOWN;
								break;
							case 2:
								//bottom right
								break;
							}
							break;
						}
						
						if(selectionButtonDown != null && tmp == selectionButtonDown) {
							switch(tmp) {
							case UP:
								doScrollUp(false);

								break;
							case DOWN:
								doScrollDown(false);
								break;
							case NEXT:
								String copy = mBuffer.getTextSection(theSelection);
								ClipboardManager cpMan = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
								cpMan.setText(copy);
								endTextSelectionMode(v);
								return true;
							case LEFT:
								doScrollLeft(false);
								break;
							case RIGHT:
								doScrollRight(false);
								break;
							case CENTER:
								break;
							case COPY:
								//actually switch.
								if(selectedSelector == theSelection.end) {
									selectedSelector = theSelection.start;
								} else {
									selectedSelector = theSelection.end;
								}
								moveWidgetToSelector(selectedSelector);
								break;
							case EXIT:
								//get out and don't copy.
								endTextSelectionMode(v);
								return true;
								//break;
							}

							v.invalidate();
						}
					}
					
				}
				selectionButtonDown = null;
				break;
			}
			return true;
		}
	};
	
	private int widgetCenterMovedX = 0;
	private int widgetCenterMovedY = 0;
	private int widgetCenterMoveXLast = 0;
	private int widgetCenterMoveYLast = 0;
	
	private int mWidgetX = 0;
	private int mWidgetY = 0;
	
	private SelectionCursor selectedSelector;
	private Selection theSelection;
	boolean firstPress = true;
	
	private enum SelectionWidgetButtons {
		UP,
		DOWN,
		LEFT,
		RIGHT,
		CENTER,
		EXIT,
		COPY,
		NEXT,
	}
	
	private SelectionWidgetButtons selectionButtonDown = null;
	
	private void calculateWidgetPosition(int startX,int startY) {
		//test to see if we can place it here.
		//add the vector to the components of the widget's center
		int newWidgetX = (int) (startX + selectionIndicatorVectorX);
		int newWidgetY = (int) ((int) (startY + selectionIndicatorVectorY));
		final int half = mSelectionIndicatorHalfDimension;
		final int viewW = this.getWidth();
		final int viewH = this.getHeight();
		
		if((newWidgetX + half) > viewW) {
			selectionIndicatorVectorX -= mOneCharWidth;
			if(selectionIndicatorVectorX < (mOneCharWidth + half)) {
				selectionIndicatorVectorX = -(selectionIndicatorVectorX+mOneCharWidth);
			}
			newWidgetX = (int) (startX + selectionIndicatorVectorX);
			
		} else if((newWidgetX - half) < 0) {
			//flip the vector
			selectionIndicatorVectorX += mOneCharWidth;
			if(selectionIndicatorVectorX > -(mOneCharWidth+half)) {
				selectionIndicatorVectorX = -(selectionIndicatorVectorX-mOneCharWidth);
			}
			newWidgetX = (int) (startX + selectionIndicatorVectorX);
		}
		
		if((newWidgetY + half) > viewH) {
			selectionIndicatorVectorY -= mPrefLineSize;
			newWidgetY = (int) (startY + selectionIndicatorVectorY);
			if(newWidgetY + half > viewH) {
				// Pull fully above the caret / bottom edge (live output case).
				selectionIndicatorVectorY = -half - Math.max(mPrefLineSize, (int) (8 * mDensity));
				newWidgetY = (int) (startY + selectionIndicatorVectorY);
			}
			
		} else if ((newWidgetY - half) < 0) {
			selectionIndicatorVectorY += mPrefLineSize;
			
			newWidgetY = (int) (startY + selectionIndicatorVectorY);
			if(newWidgetY - half < 0) {
				selectionIndicatorVectorY = +half;
				newWidgetY = (int) (startY + selectionIndicatorVectorY);
			}
		}

		// Hard clamp so the full circle stays inside the window (input bar / bottom
		// caret used to push the widget off-screen so copy appeared "broken").
		if (newWidgetX - half < 0) {
			newWidgetX = half;
		} else if (newWidgetX + half > viewW) {
			newWidgetX = Math.max(half, viewW - half);
		}
		if (newWidgetY - half < 0) {
			newWidgetY = half;
		} else if (newWidgetY + half > viewH) {
			newWidgetY = Math.max(half, viewH - half);
		}
		
		mWidgetX = newWidgetX;
		mWidgetY = newWidgetY;
		
	}
	
	private void moveWidgetToSelector(TextTree.SelectionCursor cursor) {
		
		int part1 = (int) (selectedSelector.line * mPrefLineSize + (0.5*mSelectionIndicatorFontSize));
		//int part2 = (int) (scrollback);
		int part2 = (int) (selectedSelector.line * mPrefLineSize - (0.5*mSelectionIndicatorFontSize));
		
		
		if(part1 > mScrollback) {
			//calculate how much scroll to go to get to be true.
			//int tmp = (int) (scrollback-SCROLL_MIN);
			//int howmuch = part2 - part1;
			mScrollback = (double) part1;
		} else if(part2 < (mScrollback-SCROLL_MIN)) {
			mScrollback -= ((mScrollback-SCROLL_MIN) - part2);
		}
		
		// The column is a position on the canvas; the widget is drawn on screen.
		// Without the offset it stayed where it was created while the text moved
		// out from under it.
		int endx = (int) ((selectedSelector.column * mOneCharWidth) + (0.5*mOneCharWidth) - mScrollX);
		int endy = bufferLineToScreenY(selectedSelector.line, (float) (0.5 * mSelectionIndicatorFontSize));
		//widgetX = endx;
		//widgetY = endy;
		selectorCenterX = endx;
		selectorCenterY = endy;
		calculateWidgetPosition(selectorCenterX,selectorCenterY);
	}
	
	private int selectorCenterX = 0;
	private int selectorCenterY = 0;
	
	private float selectionIndicatorVectorX = 160;
	private float selectionIndicatorVectorY = 0;
	
	private boolean selectionFingerDown = false;
	private boolean scrollingEnabled = true;

/*! \page entry_points
 * \subsection OnDestroy OnDestroy
 * When the foreground process is being terimnated normally; used for memory management (freeing custom bitmaps, data, stuff that needs to be garbage collected).
 * 
 * \param none
 * 
 * \note It is difficult to know exactly what needs to be freed for garbage collection, how to do it, and wheather or not it worked. See the button window script for an example demonstrating use. The button window has many custom resources and I had run into memory issues with it when closing/opening the window a few times. It may never happen, it may happen after 100 open/close cycles, or 5, but the general trend of running the foreground process out of memory is an immediate termination of the window. So if you are in a case where you are coming back into the BlowTorch after a phone call or web browser session and it immediatly exits, this may be the culprit.
 */
		
	public void shutdown() {
		//Log.e("LUAWINDOW","SHUTTING DOWN: "+mName);
		if(mL == null) return;
		//call into lua to notify shutdown imminent.
		
		mL.getGlobal("debug");
		mL.getField(-1, "traceback");
		mL.remove(-2);
		
		Log.e("WINDOW","   Calling OnDestroy for state: " + mL.getStateId());
		
		mL.getGlobal("OnDestroy");
		Log.e("WINDOW"," getting global");
		if(mL.getLuaObject(mL.getTop()).isFunction()) {
			Log.e("WINDOW"," is function");
			int ret = mL.pcall(0, 1, -2);
			if(ret != 0) {
				Log.e("WINDOW","LUA ERROR:" + mL.getLuaObject(-1).getString());
				displayLuaError("Error in OnDestroy: "+mL.getLuaObject(-1).getString());
			}
			// Error branch used to pop neither the error object nor the
			// traceback function.
			mL.pop(2);
		} else {
			//no method.
			mL.pop(2);
		}
		
		//callbackHandler.removeCallbacksAndMessages(token)
		
		//mL = null;
		
	}
	
	public void closeLua() {
		if(mL != null) {
			mL.close();
			mL = null;
		}
	}
	
	@Override
	protected final void onMeasure(final int widthSpec, final int heightSpec) {
		int height = MeasureSpec.getSize(heightSpec);
		int width = MeasureSpec.getSize(widthSpec);
		
/*! \page entry_points
 * \subsection OnMeasure OnMeasure
 * Whenever the layout hierarchy initiates re-measuring (window hierarchy changed) this function is called, many times. There is much to know about this function. More documentation will come, but the information passed in the variables is called a measure spec. It contains the target dimension and the measurement mode. More information can be found here. <insert link>
 * 
 * \param widthspec
 * \param heightspec
 * 
 * \return width and height, see note
\par Example
\luacode
function OnMeasure(wspec,hspec)	
	if(wspec == measurespec_width and hspec == measurespec_height) then return measured_width,measured_height end
	--Note(string.format("measurespecs: %d, %d\n",wspec,hspec))
	measurespec_width = wspec
	measurespec_height = hspec
	measured_width = MeasureSpec:getSize(wspec)
	--local wmode = MeasureSpec:getMode(wspec)
	
	measured_height = MeasureSpec:getSize(hspec)
	--local hmode = MeasureSpec:getMode(hspec)
	
	function test()
		local orientation = view:getParent():getOrientation()
	end
	local ret,err = pcall(test,debug.traceback)
	if(not ret) then
		--there was a problem, but do we care
		--Note("stat widget is relative, width:"..measured_width.." height:"..measured_height.."\n")
		return measured_width,measured_height
	end
	
	
	local orientation = view:getParent():getOrientation()
	if(orientation == LinearLayout.VERTICAL) then
		view:fitFontSize(36)
		view:doFitFontSize(measured_width)
		measured_height = view:getLineSize()*view:getBuffer():getBrokenLineCount()
		
		--Note("stat widget is vertical, width:"..measured_width.." height:"..measured_height.."\n")
		return measured_width,measured_height
	else
		view:setCharacterSizes((measured_height-6)/3,2)
		measured_width = 37*view:measure("a")
		view:fitFontSize(-1)
		measured_height = view:getLineSize()*view:getBuffer():getBrokenLineCount()
		--Note("stat widget is horizontal, width:"..measured_width.." height:"..measured_height.."\n")
		return measured_width,measured_height
	end
	--end
	
	

end
\endluacode
 * 
 * \note This function expects a measured width and height value returned, e.g. return width,height is expected. If it is not supplied the window will not appear. 
 */
		if (mHasScriptOnMeasure && mL != null) {
			mL.getGlobal("debug");
			mL.getField(-1, "traceback");
			mL.remove(TOP_MINUS_TWO);
			
			mL.getGlobal("OnMeasure");
			if (mL.isFunction(-1)) {
				mL.pushNumber(widthSpec);
				mL.pushNumber(heightSpec);
				int ret = mL.pcall(2, 2, TOP_MINUS_FOUR);
				if (ret != 0) {
					displayLuaError("Error in OnMeasure:" + mL.getLuaObject(-1).getString());
					setMeasuredDimension(1, 1);
					// The error object AND the traceback function. This popped
					// only the error object, so a scripted window whose
					// OnMeasure is broken leaked a slot per layout pass.
					mL.pop(2);
					return;
				} else {
					int retHeight = (int) mL.getLuaObject(-1).getNumber();
					int retWidth = (int) mL.getLuaObject(TOP_MINUS_TWO).getNumber();
					// Two results plus the traceback function. Popping 2 left
					// the traceback behind on every measure pass — the success
					// path, so this grew during ordinary layout.
					mL.pop(3);
					setMeasuredDimension(retWidth, retHeight);
					return;
				}
			} else {
				mL.pop(2);
			}
		}
		int hspec = MeasureSpec.getMode(heightSpec);
		if (width != mWidth) {
			doFitFontSize(width);
		}
		switch(hspec) {
		case MeasureSpec.AT_MOST:
			break;
		case MeasureSpec.EXACTLY:
			break;
		case MeasureSpec.UNSPECIFIED:
			height = (mBuffer.getBrokenLineCount() * mPrefLineSize) + mPrefLineExtra;
			break;
		default:
			break;
		}
		
		setMeasuredDimension(width, height);
		
	}
	
	protected void onSizeChanged(int w,int h,int oldw,int oldh) {
		boolean dofit = false;
		if(mWidth != w) {
			dofit = true;
		}
		mWidth = w;
		mHeight = h;
		if(dofit) {
			doFitFontSize(mWidth);
		}
		calculateCharacterFeatures(mWidth,mHeight);
		

		//int diff = oldh - h;
		//scrollback -= diff;
		if(mScrollback == SCROLL_MIN) {
			SCROLL_MIN = contentHeight()-(double)(5*Window.this.getResources().getDisplayMetrics().density);
			mScrollback = SCROLL_MIN;
		} else {
			//we have to calculate the new scrollback position.
			double oldmin = SCROLL_MIN;
			SCROLL_MIN = contentHeight()-(double)(5*Window.this.getResources().getDisplayMetrics().density);
			mScrollback -= oldmin - SCROLL_MIN;
		}
		
		//if(the_tree.getBrokenLineCount() <= CALCULATED_LINESINWINDOW) {
		//	scrollback = 0.0;
		//}

		
		layoutHomeWidgetRect();
		
		Float foo = new Float(0);
		//foo.
		
/*! \page entry_points
 * \subsection OnSizeChanged OnSizeChanged
 * If the window's size changes this function is called.
 * 
 * \param new width
 * \param new height
 * \param old width
 * \param old height
 * 
 * \return none
\par Example
\luacode
function OnSizeChanged(w,h,oldw,oldh)
	Note("Window starting OnSizeChanged()")
	if(w == 0 and h == 0) then
		draw = false
	end
end
\endluacode
 * 
 */
		
		if(mL == null || !hasOnSizeChanged) return;
		mL.getGlobal("debug");
		mL.getField(mL.getTop(), "traceback");
		mL.remove(-2);
		mL.getGlobal("OnSizeChanged");
		if(mL.getLuaObject(mL.getTop()).isFunction()) {
			mL.pushString(Integer.toString(w));
			mL.pushString(Integer.toString(h));
			mL.pushString(Integer.toString(oldw));
			mL.pushString(Integer.toString(oldh));
			int ret = mL.pcall(4, 1, -6);
			if(ret != 0) {
				displayLuaError("Window("+mName+") OnSizeChangedError: " + mL.getLuaObject(-1).getString());
			}
			// Error branch used to pop neither the error object nor the
			// traceback function.
			mL.pop(2);
		} else {
			//Log.e("LUAWINDOW","Window("+mName+"): No OnSizeChanged Function Defined.");
			hasOnSizeChanged = false;
			mL.pop(2);
		}
		this.invalidate();
	}
	boolean hasOnSizeChanged = true;
	
	/**
	 * Place the jump-to-live chevron at bottom-right, above the overflow FAB.
	 * Newest-at-top only flips the arrow direction when drawing.
	 */
	private void layoutHomeWidgetRect() {
		int size = (int) (45 * mDensity);
		int margin = (int) (4 * mDensity);
		int clear = (int) (56 * mDensity);
		mHomeWidgetRect.set(mWidth - size - margin,
				mHeight - size - clear,
				mWidth - margin,
				mHeight - clear);
	}

	/** Dark bluish chevron: jump to live. Points down, or up when newest-at-top. */
	private void drawJumpChevron(final Canvas c) {
		Rect r = mHomeWidgetRect;
		float cx = r.exactCenterX();
		float cy = r.exactCenterY();
		float w = r.width() * 0.28f;
		float h = r.height() * 0.18f;
		mJumpPath.reset();
		if (mNewestAtTop) {
			mJumpPath.moveTo(cx, cy - h);
			mJumpPath.lineTo(cx + w, cy + h * 0.55f);
			mJumpPath.lineTo(cx - w, cy + h * 0.55f);
		} else {
			mJumpPath.moveTo(cx, cy + h);
			mJumpPath.lineTo(cx + w, cy - h * 0.55f);
			mJumpPath.lineTo(cx - w, cy - h * 0.55f);
		}
		mJumpPath.close();
		mJumpPaint.setStyle(Paint.Style.FILL);
		mJumpPaint.setColor(0xBB2A4A6E);
		c.drawPath(mJumpPath, mJumpPaint);
	}

	/**
	 * Date of the text on screen, plus a short position mark, to the left of
	 * the overflow ⋮. Off the live edge only — the caller already checked.
	 */
	private void drawWhenCluster(final Canvas c) {
		long stamp = viewportStampMillis();
		String label = stamp > 0L
				? LineStamp.overlayLabel(stamp, System.currentTimeMillis()) : "";
		float f = mScrollDatesOpacity / 100f;
		int dateA = Math.max(0, Math.min(255, Math.round(255f * f)));
		int trackA = Math.max(0, Math.min(255, Math.round(0x44 / 0.75f * f)));
		int thumbA = Math.max(0, Math.min(255, Math.round(0xCC / 0.75f * f)));
		int margin = (int) (4 * mDensity);
		int fab = (int) (48 * mDensity);
		int gap = (int) (6 * mDensity);
		int trackW = Math.max(2, (int) (3 * mDensity));
		int trackH = (int) (28 * mDensity);
		float clusterCy = mHeight - margin - fab / 2f;
		int sliderRight = mWidth - margin - fab - gap;
		int sliderLeft = sliderRight - trackW;
		int sliderTop = Math.round(clusterCy - trackH / 2f);
		int sliderBottom = sliderTop + trackH;
		mWhenPaint.setTextSize(11f * mDensity);
		mWhenPaint.setTypeface(Typeface.SANS_SERIF);
		mWhenPaint.setTextAlign(Paint.Align.RIGHT);
		float textX = sliderLeft - gap;
		float textY = clusterCy + mWhenPaint.getTextSize() * 0.35f;
		if (label.length() > 0) {
			mWhenPaint.setColor(android.graphics.Color.argb(dateA, 0x8A, 0xA4, 0xC0));
			c.drawText(label, textX, textY, mWhenPaint);
		}
		mWhenPaint.setColor(android.graphics.Color.argb(trackA, 0x2A, 0x4A, 0x6E));
		c.drawRect(sliderLeft, sliderTop, sliderRight, sliderBottom, mWhenPaint);
		float total = Math.max(1f, (float) (mBuffer.getBrokenLineCount() * mPrefLineSize));
		float window = Math.max(1f, (float) contentHeight());
		float maxScroll = Math.max(1f, total - window);
		float fromLive = (float) Math.max(0, mScrollback - SCROLL_MIN);
		float pos = Math.min(1f, fromLive / maxScroll);
		// pos 0 = live edge. Live text sits at the bottom of the window, so
		// the thumb belongs at the bottom of the track (flipped when newest-at-top).
		if (!mNewestAtTop) {
			pos = 1f - pos;
		}
		int thumbH = Math.max(trackW * 2, (int) (trackH * (window / total)));
		if (thumbH > trackH) {
			thumbH = trackH;
		}
		int travel = trackH - thumbH;
		int thumbTop = sliderTop + (int) (pos * travel);
		mWhenPaint.setColor(android.graphics.Color.argb(thumbA, 0x3A, 0x5F, 0x8A));
		c.drawRect(sliderLeft, thumbTop, sliderRight, thumbTop + thumbH, mWhenPaint);
	}

	/** Stamp of the line at the centre of the viewport, or 0. */
	private long viewportStampMillis() {
		if (mBuffer == null || mPrefLineSize <= 0) {
			return 0L;
		}
		int skip = (int) Math.max(0, (mScrollback - SCROLL_MIN) / mPrefLineSize)
				+ Math.max(0, mCalculatedLinesInWindow / 2);
		for (TextTree.Line line : mBuffer.getLines()) {
			int rows = 1 + line.getBreaks();
			if (skip < rows) {
				return line.getReceivedAt();
			}
			skip -= rows;
		}
		return 0L;
	}

	private void doScrollDown(boolean repeat) {
		//Log.e("FOO","do scroll down");
		selectedSelector.line -= 1;
		if(selectedSelector.line < 0) {
			selectedSelector.line = 0;
			repeat = false;
		} else {
			if (mNewestAtTop) {
				// Newer lines sit toward the top.
				int remainder = ((int) (mScrollback - SCROLL_MIN) % mPrefLineSize) - mPrefLineSize;
				selectorCenterY -= mPrefLineSize;
				if (selectorCenterY - mPrefLineSize < remainder) {
					selectorCenterY += mPrefLineSize;
					mScrollback -= mPrefLineSize;
				}
			} else {
				int remainder = ((int)(mScrollback-SCROLL_MIN) % mPrefLineSize) + mPrefLineSize;
				selectorCenterY += mPrefLineSize;
				if(selectorCenterY > this.getHeight() - remainder) {
					selectorCenterY -= mPrefLineSize;
					mScrollback -= mPrefLineSize;
				}
			}
			calculateWidgetPosition(selectorCenterX,selectorCenterY);
		}
	
		this.invalidate();
		if(repeat) {
			mHandler.sendEmptyMessageDelayed(MESSAGE_SCROLLDOWN,mScrollRepeatRate);
		} else {
			mHandler.removeMessages(MESSAGE_SCROLLDOWN);
		}
	}
	
	private void doScrollUp(boolean repeat) {
		//Log.e("FOO","do scroll up");
		selectedSelector.line += 1;
		if(selectedSelector.line == mBuffer.getBrokenLineCount()) {
			selectedSelector.line -= 1;
			repeat = false;
		} else {
			if (mNewestAtTop) {
				// Older lines sit toward the bottom.
				int remainder = ((int) (mScrollback - SCROLL_MIN) % mPrefLineSize) + mPrefLineSize;
				selectorCenterY += mPrefLineSize;
				if (selectorCenterY > this.getHeight() - remainder) {
					selectorCenterY -= mPrefLineSize;
					mScrollback += mPrefLineSize;
				}
			} else {
				int remainder = ((int)(mScrollback-SCROLL_MIN) % mPrefLineSize)-mPrefLineSize;
				selectorCenterY -= mPrefLineSize;
				if(selectorCenterY - (mPrefLineSize) < remainder) {
					selectorCenterY = selectorCenterY + mPrefLineSize;
					mScrollback += mPrefLineSize;
				}
			}
			calculateWidgetPosition(selectorCenterX,selectorCenterY);
		}
		this.invalidate();
		if(repeat) {
			mHandler.sendEmptyMessageDelayed(MESSAGE_SCROLLUP,mScrollRepeatRate);
		} else {
			mHandler.removeMessages(MESSAGE_SCROLLUP);
		}
	}
	
	private void doScrollLeft(boolean repeat) {
		selectedSelector.column -= 1;
		if(selectedSelector.column < 0) {
			selectedSelector.column = 0;
		} else {
			selectorCenterX -= mOneCharWidth;
			mWidgetY -= mOneCharWidth;
			calculateWidgetPosition(selectorCenterX,selectorCenterY);
		}
		this.invalidate();
		if(repeat) {
			mHandler.sendEmptyMessageDelayed(MESSAGE_SCROLLLEFT,mScrollRepeatRate);
		}else {
			mHandler.removeMessages(MESSAGE_SCROLLLEFT);
		}
	}
	
	private void doScrollRight(boolean repeat) {
		selectedSelector.column += 1;
		selectorCenterX += mOneCharWidth;
		calculateWidgetPosition(selectorCenterX,selectorCenterY);
		this.invalidate();
		if(repeat) {
			mHandler.sendEmptyMessageDelayed(MESSAGE_SCROLLRIGHT, mScrollRepeatRate);
		}else {
			mHandler.removeMessages(MESSAGE_SCROLLRIGHT);
		}
	}
	
	public boolean isTextSelectionEnabled() {
		return mTextSelectionEnabled;
	}



	public void setTextSelectionEnabled(boolean textSelectionEnabled) {
		this.mTextSelectionEnabled = textSelectionEnabled;
		//Log.e("sfdsf","setting text selection enabled="+textSelectionEnabled);
	}
	
	public void setScrollingEnabled(boolean scrollingEnabled) {
		this.scrollingEnabled  = scrollingEnabled;
	}
	
	public boolean isScrollingEnabled() {
		return this.scrollingEnabled;
	}

	/** Options → Window → Keep text still with keyboard? */
	public boolean isImeKeepText() {
		return mImeKeepText;
	}

	public void jumpToStart() {
		mScrollback = SCROLL_MIN;
		mFlingVelocity=0;
		this.invalidate();
	}



	
	public void populateMenu(Menu menu) {
		if(mL == null) return;
		mL.getGlobal("debug");
		mL.getField(-1, "traceback");
		mL.remove(-2);
/*! \page entry_points
 * \subsection PopulateMenu PopulateMenu
 * Called during the activity creation process. [I think] Before OnCreate is called, but after the plugin windows have been loaded and the script bodies run.
 * 
 * \param menu android.menu.Menu that is the menu for the foreground window activity.
\par Example
\luacode
menucallback = {}

function menucallback.onMenuItemClick(item)
	Note("menu item clicked")
	
	--this function must return true if it consumes the click event.
	return true
end
menucallback_cb = luajava.createProxy("android.view.MenuItem$OnMenuItemClickListener",menucallback)

 
function PopulateMenu(menu)
 	--see android Menu documentation, menu:add() returns an android.menu.MenuItem
 	--that can be manipulated to have a drawable (more sample code coming soon)
 	--and can be configured for Android 4.0 (ICS)+ features.
 	item = menu:add(0,401,401,"Ex Button Sets")
	item:setOnMenuItemClickListener(buttonsetMenuClicked_cb)
end
 
\endluacode
 * \note Need some example code. It is necessary to create and attach menu items to the top level menu. This is how the button window script attaches its menu item into the top level list.
 *  \tableofcontents
 */
		mL.getGlobal("PopulateMenu");
		if(mL.getLuaObject(-1).isFunction()) {
			mL.pushJavaObject(menu);
			int ret = mL.pcall(1, 1, -3);
			if(ret != 0) {
				displayLuaError("Error in PopulateMenu:"+mL.getLuaObject(-1).getString());
			} else {
				mL.pop(2);
			}
		} else {
			mL.pop(2);
		}
	}

	public boolean checkSupports(String function) {
		if(mL != null) {
			mL.getGlobal(function);
			
			boolean ret = mL.isFunction(-1);
			mL.pop(1);
			return ret;
		}
		return false;
	}

	public boolean isCenterJustify() {
		return mCenterJustify;
	}

	public void setCenterJustify(boolean centerJustify) {
		this.mCenterJustify = centerJustify;
	}
	
	public int getLineSize() {
		return mPrefLineSize;
	}
	
	public void fitFontSize(int chars) {
		//Log.e("LUA","SETTING FITCHARS:"+chars);
		mFitChars = chars;
	}
		
	public void doFitFontSize(int width) {
		if(mFitChars < 0) return;
		//Log.e("LUA","DOING THE FIT ROUTINE: "+mWidth+" chars:"+fitChars + " for window: "+this.getName());
		int windowWidth = width;
		//int windowWidth = service.getResources().getDisplayMetrics().widthPixels;
		//if(service.getResources().getDisplayMetrics().heightPixels > windowWidth) {
		//	windowWidth = service.getResources().getDisplayMetrics().heightPixels;
		//}
		float fontSize = 8.0f;
		float delta = 1.0f;
		Paint p = new Paint();
		p.setTextSize(8.0f);
		//p.setTypeface(Typeface.createFromFile(service.getFontName()));
		p.setTypeface(Typeface.MONOSPACE);
		boolean done = false;
		
		float charWidth = p.measureText("A");
		float charsPerLine = windowWidth / charWidth;
		
		if(charsPerLine < mFitChars) {
			//for QVGA screens, this test will always fail on the first step.
			done = true;
		} else {
			fontSize += delta;
			p.setTextSize(fontSize);
		}
		
		while(!done) {
			charWidth = p.measureText("A");
			charsPerLine = windowWidth / charWidth;
			if(Math.floor(charsPerLine) <= mFitChars) {
				done = true;
				fontSize -= delta; //return to the previous font size that produced > 80 characters.
			} else {
				fontSize += delta;
				p.setTextSize(fontSize);
			}
		}
		
		mPrefFontSize = (int) fontSize;
		mPrefLineSize = mPrefFontSize + mPrefLineExtra;
		calculateCharacterFeatures(mWidth,mHeight);
		//return (int)fontSize;
	}

	public TextTree getBuffer() {
		return mBuffer;
	}
	
	public double measure(String str) {
		return featurePaint.measureText(str);
	}
}


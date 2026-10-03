package com.resurrection.blowtorch2.lib.window;

import javax.security.auth.PrivateCredentialPermission;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.NoCopySpan;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.KeyListener;
import android.text.style.SuggestionSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;
import android.widget.TextView;

public class BetterEditText extends EditText {

	private Boolean useFullScreen = false;
	private Boolean BackSpaceBugFix = false;
	private boolean allowSuggestions = false;
	/** True only while telnet ECHO masks the bar — SwiftKey Incognito / Gboard private. */
	private boolean noPersonalizedLearning = false;
	/** Drawn hyphen inside a long word. The mark is stripped before send. */
	private boolean hyphenBreaksOn = false;
	private int hyphenMinLead = 5;
	private int hyphenMinTail = 3;
	private boolean hyphenPolish = false;
	private boolean hyphenWatcherInstalled = false;
	private boolean hyphenSyncing = false;
	private int hyphenSyncedWidth = -1;
	/** Letter to delete with a backspace or delete that only hit the mark. */
	private int hyphenSwallowAt = -1;
	private boolean hyphenSyncPosted = false;
	private final SpanWatcher hyphenSpanWatcher = new HyphenSpanWatcher();
	private final InputHyphenBreaks.Measurer hyphenMeasurer = new InputHyphenBreaks.Measurer() {
		@Override
		public float width(final String text, final int start, final int end) {
			return getPaint().measureText(text, start, end);
		}
	};
	
	public BetterEditText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public BetterEditText(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public BetterEditText(Context context) {
		super(context);
	}

	/**
	 * Always {@link #AUTOFILL_TYPE_NONE}: this widget is only the MUD command line.
	 * {@code importantForAutofill="no"} alone is not enough on Android 14+ (API 34) —
	 * the platform still includes such views in FillRequests when they look like
	 * credentials, and MainWindow briefly uses a password-style mask for telnet ECHO.
	 */
	@Override
	public int getAutofillType() {
		return AUTOFILL_TYPE_NONE;
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		ensureHyphenWatcher();
	}

	@Override
	protected void onDetachedFromWindow() {
		ghostPaddingHeld = false;
		ghostTouchDown = false;
		ghostRoomPosted = false;
		super.onDetachedFromWindow();
	}
	
	public InputConnection onCreateInputConnection(EditorInfo attrs) {
		final InputConnection connection;
		if (useFullScreen) {
			connection = super.onCreateInputConnection(attrs);
		} else if (BackSpaceBugFix) {
			connection = new InputConnectionWrapper(super.onCreateInputConnection(attrs), true);
		} else {
			attrs.imeOptions = this.getImeOptions();
			attrs.inputType = this.getInputType();
			attrs.actionId = EditorInfo.IME_ACTION_SEND;
			attrs.privateImeOptions = this.getPrivateImeOptions();
			attrs.extras = this.getInputExtras(true);
			attrs.actionLabel = "Send";
			connection = new EditableInputConnection(this);
		}
		// After super (fullscreen / backspace-fix), which rebuilds imeOptions from
		// getImeOptions() and would drop flags we only wrote into attrs first.
		applySuggestionPolicy(attrs);
		// Grow Input Bar sets MULTI_LINE so the field can show pasted blocks. Soft
		// IMEs then treat Enter as "insert newline" and never fire IME_ACTION_SEND —
		// measured on Darkwind: newbiehist opens `[ Paging … <enter> … ]`, Enter
		// grows the bar, the server waits forever while GMCP keeps ticking.
		return wrapEnterSends(connection);
	}

	/**
	 * Soft-keyboard Enter under a multi-line-looking field may arrive as
	 * {@code commitText("\n")}, {@code setComposingText("\n")}, or
	 * {@code sendKeyEvent(ENTER)} — never {@code IME_ACTION_SEND}. All of those
	 * become Send. Duplicate paths (IC + OnKeyListener) are collapsed by
	 * {@link MainWindow}'s process-input debounce.
	 */
	private InputConnection wrapEnterSends(final InputConnection base) {
		if (base == null) {
			return null;
		}
		return new InputConnectionWrapper(base, true) {
			@Override
			public boolean commitText(CharSequence text, int newCursorPosition) {
				if (isSoftEnterNewline(text)) {
					BetterEditText.this.onEditorAction(EditorInfo.IME_ACTION_SEND);
					return true;
				}
				return super.commitText(text, newCursorPosition);
			}

			@Override
			public boolean setComposingText(CharSequence text, int newCursorPosition) {
				if (isSoftEnterNewline(text)) {
					BetterEditText.this.onEditorAction(EditorInfo.IME_ACTION_SEND);
					return true;
				}
				return super.setComposingText(text, newCursorPosition);
			}

			@Override
			public boolean sendKeyEvent(KeyEvent event) {
				if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
					if (event.isShiftPressed() && isMultiLineVisually()) {
						return super.sendKeyEvent(event);
					}
					if (event.getAction() == KeyEvent.ACTION_DOWN) {
						BetterEditText.this.onEditorAction(EditorInfo.IME_ACTION_SEND);
					}
					return true;
				}
				return super.sendKeyEvent(event);
			}
		};
	}

	private boolean isMultiLineVisually() {
		return getMaxLines() > 1 || !isSingleLine();
	}

	/** Soft IMEs insert a lone newline for Enter instead of an editor action. */
	static boolean isSoftEnterNewline(final CharSequence text) {
		if (text == null || text.length() == 0) {
			return false;
		}
		for (int i = 0; i < text.length(); i++) {
			final char c = text.charAt(i);
			if (c != '\n' && c != '\r') {
				return false;
			}
		}
		return true;
	}

	/**
	 * Suggestions and password-sensitivity are separate. {@code NO_SUGGESTIONS} is
	 * the Options toggle; {@code IME_FLAG_NO_PERSONALIZED_LEARNING} is only for
	 * telnet ECHO (password). Tying the learning flag to suggestions-off made
	 * SwiftKey stay in Incognito for the whole session — same chrome as a
	 * password field, even with letters visible and {@code .echo} normal.
	 */
	private void applySuggestionPolicy(EditorInfo attrs) {
		if (allowSuggestions) {
			attrs.inputType &= ~InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
		} else {
			attrs.inputType |= InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
		}
		if (noPersonalizedLearning) {
			attrs.imeOptions |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
		} else {
			attrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
		}
	}
	
	public class EditableInputConnection extends BaseInputConnection {
	    private static final boolean DEBUG = false;
	    private static final String TAG = "EditableInputConnection";

	    private final TextView mTextView;

	    // Keeps track of nested begin/end batch edit to ensure this connection always has a
	    // balanced impact on its associated TextView.
	    // A negative value means that this connection has been finished by the InputMethodManager.
	    private int mBatchEditNesting;

	    public EditableInputConnection(TextView textview) {
	        super(textview, true);
	        mTextView = textview;
	    }

	    @Override
	    public Editable getEditable() {
	        TextView tv = mTextView;
	        if (tv != null) {
	            return tv.getEditableText();
	        }
	        return null;
	    }

	    @Override
	    public boolean beginBatchEdit() {
	        synchronized(this) {
	            if (mBatchEditNesting >= 0) {
	                mTextView.beginBatchEdit();
	                mBatchEditNesting++;
	                return true;
	            }
	        }
	        return false;
	    }

	    @Override
	    public boolean endBatchEdit() {
	        synchronized(this) {
	            if (mBatchEditNesting > 0) {
	                // When the connection is reset by the InputMethodManager and reportFinish
	                // is called, some endBatchEdit calls may still be asynchronously received from the
	                // IME. Do not take these into account, thus ensuring that this IC's final
	                // contribution to mTextView's nested batch edit count is zero.
	                mTextView.endBatchEdit();
	                mBatchEditNesting--;
	                return true;
	            }
	        }
	        return false;
	    }

	    /*//@Override
	    protected void reportFinish() {
	        //super.reportFinish();

	        synchronized(this) {
	            while (mBatchEditNesting > 0) {
	                endBatchEdit();
	            }
	            // Will prevent any further calls to begin or endBatchEdit
	            mBatchEditNesting = -1;
	        }
	    }*/

	    @Override
	    public boolean clearMetaKeyStates(int states) {
	        final Editable content = getEditable();
	        if (content == null) return false;
	        KeyListener kl = mTextView.getKeyListener();
	        if (kl != null) {
	            try {
	                kl.clearMetaKeyState(mTextView, content, states);
	            } catch (AbstractMethodError ignored) {
	            	// Some IMEs ship a KeyListener without this method; nothing to do about it.
	                // This is an old listener that doesn't implement the
	                // new method.
	            }
	        }
	        return true;
	    }

	    @Override
	    public boolean commitCompletion(CompletionInfo text) {
	        if (DEBUG) Log.v(TAG, "commitCompletion " + text);
	        mTextView.beginBatchEdit();
	        mTextView.onCommitCompletion(text);
	        mTextView.endBatchEdit();
	        return true;
	    }

	    /**
	     * Calls the {@link TextView#onCommitCorrection} method of the associated TextView.
	     */
	    @SuppressLint("NewApi")
		@Override
	    public boolean commitCorrection(CorrectionInfo correctionInfo) {
	        if (DEBUG) Log.v(TAG, "commitCorrection" + correctionInfo);
	        mTextView.beginBatchEdit();
	        mTextView.onCommitCorrection(correctionInfo);
	        mTextView.endBatchEdit();
	        return true;
	    }

	    @Override
	    public boolean performEditorAction(int actionCode) {
	        if (DEBUG) Log.v(TAG, "performEditorAction " + actionCode);
	        mTextView.onEditorAction(actionCode);
	        return true;
	    }
	    
	    @Override
	    public boolean performContextMenuAction(int id) {
	        if (DEBUG) Log.v(TAG, "performContextMenuAction " + id);
	        mTextView.beginBatchEdit();
	        mTextView.onTextContextMenuItem(id);
	        mTextView.endBatchEdit();
	        return true;
	    }
	    
	    @Override
	    public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
	        if (mTextView != null) {
	            ExtractedText et = new ExtractedText();
	            if (mTextView.extractText(request, et)) {
	                if ((flags&GET_EXTRACTED_TEXT_MONITOR) != 0) {
	                    //mTextView.setExtracting(request);
	                	
	                	//this method is not available to us however if we are using this we don't care about extracted text.
	                }
	                return et;
	            }
	        }
	        return null;
	    }

	    @Override
	    public boolean performPrivateCommand(String action, Bundle data) {
	        mTextView.onPrivateIMECommand(action, data);
	        return true;
	    }

	    @Override
	    public boolean commitText(CharSequence text, int newCursorPosition) {
	        if (mTextView == null) {
	            return super.commitText(text, newCursorPosition);
	        }
	        // wrapEnterSends usually catches this first; keep the rule here too
	        // if a path reaches EditableInputConnection without that wrapper.
	        if (isSoftEnterNewline(text)) {
	        	mTextView.onEditorAction(EditorInfo.IME_ACTION_SEND);
	        	return true;
	        }
	        if (text instanceof Spanned) {
	            //Spanned spanned = ((Spanned) text);
	            //SuggestionSpan[] spans = spanned.getSpans(0, text.length(), SuggestionSpan.class);
	            //InputManager mIMM = (InputManager)mTextView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
	            //mIMM.registerSuggestionSpansForNotification(spans);
	        }

	        //mTextView.resetErrorChangedFlag();
	        boolean success = super.commitText(text, newCursorPosition);
	        //mTextView.hideErrorIfUnchanged();

	        return success;
	    }
	}
	
	public boolean onCheckIsTextEditor() {
		//Log.e("BETTEREDIT","CHECKING IF TEXT EDITOR: super returns: " + super.onCheckIsTextEditor());
		return true;
	}
	
	public void setExtractedText(ExtractedText text) {
		//Log.e("BETTEREDIT","SETTING EXTRACTED TEXT");
		super.setExtractedText(text);
	}

	public void setUseFullScreen(Boolean useFullScreen) {
		this.useFullScreen = useFullScreen;
	}

	public Boolean getUseFullScreen() {
		return useFullScreen;
	}

	public void setBackSpaceBugFix(Boolean backSpaceBugFix) {
		BackSpaceBugFix = backSpaceBugFix;
		//BackSpaceBugFix = true;
	}

	public Boolean getBackSpaceBugFix() {
		return BackSpaceBugFix;
	}

	public void setAllowSuggestions(boolean allowSuggestions) {
		this.allowSuggestions = allowSuggestions;
	}

	public boolean getAllowSuggestions() {
		return allowSuggestions;
	}

	/**
	 * Ask the IME not to learn what is typed. Only for the password mask
	 * ({@code MainWindow} while telnet ECHO is held). Persisted on
	 * {@link #getImeOptions()} so Extract UI / Compatibility
	 * {@code super.onCreateInputConnection} keeps the flag.
	 */
	public void setNoPersonalizedLearning(boolean noPersonalizedLearning) {
		this.noPersonalizedLearning = noPersonalizedLearning;
		int ime = getImeOptions();
		if (noPersonalizedLearning) {
			ime |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
		} else {
			ime &= ~EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
		}
		setImeOptions(ime);
	}

	public boolean getNoPersonalizedLearning() {
		return noPersonalizedLearning;
	}
	
	/** What is drawn after the caret. Never in the text. */
	private String ghostText = null;
	/** The whole word a tap on the ghost would put in the bar. */
	private String ghostWord = null;
	/** Which suggestion the ghost is, so {@code .complete N} matches what you see. */
	private int ghostNumber = 0;
	private android.text.TextPaint ghostPaint = null;

	/** Told when the player taps the ghost itself. */
	public interface GhostTapListener {
		/** @param word the completion the ghost was standing for. */
		void onGhostTapped(String word);
	}

	private GhostTapListener ghostTapListener = null;

	/** Told when the caret moves, so suggestions can follow it. */
	public interface CaretListener {
		void onCaretMoved();
	}

	private CaretListener caretListener = null;

	/**
	 * Wrap-down ghost clearance changed. Chrome must lift by {@code liftPx}
	 * (0 to clear) so that row stays above the IME under adjustNothing.
	 */
	public interface GhostBelowFieldLiftListener {
		void onGhostBelowFieldLiftPx(int liftPx);
	}

	private GhostBelowFieldLiftListener ghostBelowFieldLiftListener = null;

	/**
	 * Draw extras (and refresh chips) with the caret off the end of the line.
	 * Off, the ghost is end-of-text only — drawing it over what follows reads
	 * as corruption.
	 */
	private boolean ghostAtCaret = false;
	/** A short mark between suggestions on the same line. Off sits them with a space. */
	private boolean ghostSplit = false;

	/**
	 * Where the ghost was last drawn, in this view's coordinates, so a tap can be
	 * matched against what the player can actually see. Two, because a ghost that
	 * does not fit the line is continued on the next one.
	 */
	private final android.graphics.RectF[] ghostRects = {
		new android.graphics.RectF(), new android.graphics.RectF()
	};
	private int ghostRectCount = 0;
	/** A touch that went down on the ghost, waiting to see if it is a tap. */
	private boolean ghostTouchDown = false;
	/** Hold setPadding until the gesture ends: it nulls Layout and Editor NPEs (Pixel, 15 Sep 2026). */
	private boolean ghostPaddingHeld = false;

	/** One room pass per frame, after the ghost and the text agree. */
	private boolean ghostRoomDirty = false;

	private boolean ghostRoomPosted = false;

	/** Most rows the bar will grow by to carry suggestions under the typed line. */
	public static final int MAX_GHOST_ROWS = 5;

	/**
	 * Most suggestions those rows can hold between them.
	 *
	 * <p>More than the rows, because they are packed side by side: a row of
	 * short words holds several. Matched to how many the completer offers, so
	 * the field is never the thing that drops one.
	 */
	public static final int MAX_GHOST_EXTRAS = WordSuggestions.MAX_ON_STRIP;

	/** Extra suggestions drawn on their own lines below, newest option first. */
	private String[] ghostExtras = null;

	/** What each of those lines inserts when tapped. */
	private String[] ghostExtraWords = null;

	/** Which suggestion each extra is, counting from 1; same styling as ghostNumber. */
	private int[] ghostExtraNumbers = null;

	private final android.graphics.RectF[] ghostExtraRects =
			new android.graphics.RectF[MAX_GHOST_EXTRAS];

	/** The bottom padding this field had before any room was reserved. */
	private int ghostBasePaddingBottom = -1;

	/** The top padding this field had before wrapped extras reserved room. */
	private int ghostBasePaddingTop = -1;

	/** The right padding this field had before the Hide/Send inset. */
	private int ghostBasePaddingRight = -1;

	/** Hide/Send column width, in px. Zero when those buttons are hidden. */
	private int actionStripWidthPx = 0;

	/** Hide/Send height, in px. */
	private int actionStripHeightPx = 0;

	/**
	 * Most rows the bar may <em>grow</em> by for the listing.
	 *
	 * <p>Zero does not turn the listing off; it means the listing may not take
	 * any height. The rest of the typed line is free space either way, so at
	 * zero the suggestions fill what is left of it and the count says how many
	 * did not fit. That is what {@code .suggest ghostlines 1} now gets: one
	 * line, as full as it goes.
	 */
	private int ghostMaxRows = 0;

	/** Top-padding rows currently reserved for wrapped extras above the typed line. */
	private int ghostTopRowsShown = -1;

	/** Bottom-padding rows currently reserved (wrap-down ghost only). */
	private int ghostBottomRowsShown = -1;

	/** Suggestions there was no room to show, counted for the +N mark. */
	private int ghostHiddenCount = 0;

	/** Highest row index from the last pack, or -1 when nothing was placed. */
	private int ghostPackedMaxRow = -1;

	/** Right edge and baseline of the last suggestion drawn; -1 when none was. */
	private float ghostLastDrawnX = -1;
	private float ghostLastDrawnBaseline = 0;

	/** Posted on the way down, run if the finger stays put long enough. */
	private Runnable ghostHoldRunnable = null;

	/** Widened so a thumb can hit a line of monospace type. */
	private static final float GHOST_TOUCH_SLOP_DIP = 8f;

	public void setGhostTapListener(final GhostTapListener listener) {
		this.ghostTapListener = listener;
	}

	public void setCaretListener(final CaretListener listener) {
		this.caretListener = listener;
	}

	public void setGhostBelowFieldLiftListener(
			final GhostBelowFieldLiftListener listener) {
		this.ghostBelowFieldLiftListener = listener;
	}

	/**
	 * Width and height of the Hide/Send column. Zero when those buttons are
	 * hidden. The field spans the row; the column only covers the bottom end.
	 */
	public void setActionStripInset(final int widthPx, final int heightPx) {
		int w = Math.max(0, widthPx);
		int h = Math.max(0, heightPx);
		if (w == actionStripWidthPx && h == actionStripHeightPx) {
			return;
		}
		actionStripWidthPx = w;
		actionStripHeightPx = h;
		scheduleGhostRoom();
		invalidate();
	}

	public void setGhostAtCaret(final boolean on) {
		if (ghostAtCaret == on) {
			return;
		}
		ghostAtCaret = on;
		scheduleGhostRoom();
		invalidate();
	}

	public void setGhostSplit(final boolean on) {
		if (ghostSplit == on) {
			return;
		}
		ghostSplit = on;
		scheduleGhostRoom();
		invalidate();
	}

	/**
	 * Ghost after the caret, drawn not inserted — a span would be seen by Keep
	 * Last and {@code wordBefore}, and a missed strip would send untyped text.
	 * Beside the caret when the last line has room; wrap-down only when it does
	 * not ({@link GhostExtraLayout#fitBesideOrWrapDown}). {@code word} is what a
	 * tap inserts (may differ from {@code drawn} on a forgiven typo).
	 */
	public void setGhostCompletion(String drawn, String word, int number) {
		String next = drawn == null || drawn.length() == 0 ? null : drawn;
		boolean same = (next == null ? ghostText == null : next.equals(ghostText))
				&& (word == null ? ghostWord == null : word.equals(ghostWord))
				&& number == ghostNumber;
		if (same) {
			return;
		}
		ghostText = next;
		ghostWord = word;
		ghostNumber = number;
		if (next == null) {
			ghostRectCount = 0;
			ghostTouchDown = false;
		}
		// Multi-line draws the ghost below the field and needs that row reserved.
		scheduleGhostRoom();
		invalidate();
	}

	public String getGhostCompletion() {
		return ghostText;
	}

	@Override
	protected void onLayout(final boolean changed, final int left, final int top,
			final int right, final int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		if (hyphenLineWidth() != hyphenSyncedWidth) {
			syncHyphenBreaks();
		}
		// setText/setPadding drop Layout; recomputing in that window cleared
		// the row and onDraw painted it on the keyboard edge (phone, 30 Sep 2026).
		if (ghostRoomDirty && !ghostPaddingHeld) {
			scheduleGhostRoom();
		}
	}

	@Override
	protected void onSelectionChanged(final int start, final int end) {
		super.onSelectionChanged(start, end);
		// Moving the caret off the end takes the ghost away, and the rows held
		// for its suggestions have to go with it. Nothing else asks again.
		if (ghostMaxRows > 0 || ghostAtCaret) {
			scheduleGhostRoom();
		}
		if (ghostAtCaret) {
			invalidate();
		}
		if (caretListener != null) {
			caretListener.onCaretMoved();
		}
		// The underline can drop on a caret move without a text change.
		postHyphenSync();
	}

	/**
	 * Suggestions to show with the line being typed, growing the bar for them.
	 *
	 * <p>The inline ghost is one word by construction. This is the other answer
	 * to that, for a player who works without a bar of chips: wrapped extras
	 * stack above the typed line (top padding) so they stay in the visible band
	 * above the keyboard under {@code adjustNothing}. It costs screen — that is
	 * the trade, and it is why this is off unless asked for.
	 *
	 * <p>Room is made with padding rather than by putting the words into the
	 * text. Text is what gets sent; a suggestion must never be able to become
	 * part of the command by an oversight somewhere else.
	 *
	 * @param lines what to draw, or null for none.
	 * @param words what each line inserts; same length as {@code lines}.
	 * @param numbers which suggestion each line is, counting from 1; may be null.
	 */
	public void setGhostExtras(final String[] lines, final String[] words,
			final int[] numbers) {
		int now = lines == null ? 0 : Math.min(lines.length, MAX_GHOST_EXTRAS);
		ghostExtras = now == 0 ? null : java.util.Arrays.copyOf(lines, now);
		ghostExtraWords = now == 0 ? null : java.util.Arrays.copyOf(words, now);
		ghostExtraNumbers = now == 0 || numbers == null
				? null : java.util.Arrays.copyOf(numbers, now);
		// The bar takes exactly the rows this many suggestions need at this
		// width, and gives them back when they go — which is what makes it
		// shrink again the moment a command is sent. The count is worked out
		// here rather than while drawing, because making room is a layout and a
		// layout must not happen inside onDraw.
		scheduleGhostRoom();
		invalidate();
	}

	/**
	 * Most rows of suggestions the bar may grow by, on top of the typed line.
	 *
	 * @param rows the ceiling; 0 keeps the listing to the rest of the typed
	 *        line. What turns it off is having no extras to show.
	 */
	public void setGhostMaxRows(final int rows) {
		int want = rows < 0 ? 0 : Math.min(rows, MAX_GHOST_ROWS);
		if (want == ghostMaxRows) {
			return;
		}
		ghostMaxRows = want;
		scheduleGhostRoom();
		invalidate();
	}

	/** Width one row of suggestions has to lay out in. */
	private float ghostRowWidth() {
		return getWidth() - getTotalPaddingLeft() - getTotalPaddingRight();
	}

	/**
	 * Width of a caret-middle row: the full field, minus the Hide/Send column
	 * the bottom row sits beside.
	 */
	private float caretListWidth() {
		int baseRight = ghostBasePaddingRight >= 0
				? ghostBasePaddingRight : getPaddingRight();
		// getWidth() shrinks when the one-line margin is set. Add it back so
		// the list width stays "full row, minus Hide/Send" either way.
		float full = getWidth() + actionStripMargin();
		float w = full - getTotalPaddingLeft() - baseRight - actionStripWidthPx;
		return w > 0f ? w : 0f;
	}

	/** Space between two suggestions. The first one sits against the text. */
	private float ghostGap() {
		android.text.TextPaint p = ghostPaint != null ? ghostPaint : getPaint();
		if (ghostSplit) {
			return p.getTextSize() * 0.45f;
		}
		return p.measureText(" ");
	}

	/**
	 * Reserve top padding for wrapped extras (visible band above the typed
	 * line). A wrap-down ghost keeps a one-line bottom padding draw slot, but
	 * clearing the IME is chrome {@code translationY}, not more bottom
	 * padding — under adjustNothing the bar bottom already sits on the IME.
	 *
	 * @return false when Layout was just dropped, so the caller keeps the
	 *         dirty flag and {@link #onLayout} runs this again.
	 */
	private boolean applyGhostRoom() {
		// Width is known but Layout was just dropped. Do not zero the reserved
		// row — the following onLayout pass fills it in.
		if (getWidth() > 0 && getLayout() == null) {
			return false;
		}
		int marginEnd = GhostExtraLayout.inputEndMarginPx(actionStripWidthPx);
		// Fit against the width the text will have after this pass. The
		// current Layout is still the old margin, and clearing the dirty flag
		// here would leave the reserved row on that line.
		android.text.Layout target = layoutAt(textInnerWidthForMargin(marginEnd));
		GhostExtraLayout.Fit inlineFit = inlineGhostFitOn(target);
		int belowField = inlineFit != null && inlineFit.wrapDown
				? inlineFit.chromeLiftRows : 0;
		int topRows = 0;
		if (ghostExtras != null && ghostExtras.length > 0 && ghostWouldDraw()) {
			// Suggestions stop before Hide/Send. The typed text does too: the
			// margin is the button column on every line.
			float avail = caretListWidth();
			if (avail <= 0) {
				// Not laid out yet. One wrapped row is the honest guess when the
				// ceiling allows growth; at a ceiling of zero the listing must
				// not grow the bar.
				int cap = ghostRowCap();
				if (listAtTextEnd()) {
					topRows = 0;
					belowField = Math.max(belowField, cap > 0 ? 1 : 0);
				} else {
					topRows = cap > 0 ? 1 : 0;
				}
			} else {
				android.text.Layout layout = target != null ? target : getLayout();
				float lineHeight = getPaint().getFontSpacing();
				float descent = getPaint().descent();
				float contentTop = 0f;
				float contentBottom = 0f;
				float ghostBaseline = 0f;
				if (layout != null) {
					contentTop = layout.getLineTop(0);
					int last = layout.getLineCount() - 1;
					contentBottom = layout.getLineBottom(last);
					ghostBaseline = layout.getLineBaseline(last);
				}
				if (belowField > 0) {
					ghostBaseline = contentBottom + lineHeight - descent;
				} else if (layout != null && ghostText != null && caretAtEnd()
						&& layoutHasOffset(layout, getSelectionStart())) {
					float ghostWidth = getPaint().measureText(ghostText)
							+ inlineGhostNumberWidth();
					float x = layout.getPrimaryHorizontal(getSelectionStart());
					float room = avail - x;
					if (ghostWidth > room) {
						float contentH = getHeight() - getTotalPaddingTop()
								- getTotalPaddingBottom();
						if (contentBottom + lineHeight <= layout.getHeight()
								|| contentBottom + lineHeight <= contentH) {
							ghostBaseline = ghostBaseline + lineHeight;
						}
					}
				}
				float startX = listAtTextEnd()
						? lastLineEndAt(textInnerWidthForMargin(marginEnd))
						: ghostEndAfter(inlineFit);
				if (inlineFit != null) {
					startX += ghostGap();
				}
				topRows = packGhostExtras(null, avail, startX,
						ghostBaseline, contentTop, contentBottom, lineHeight, 0f, 0f,
						false);
				if (listAtTextEnd()) {
					belowField = GhostExtraLayout.caretListRowsBelow(ghostPackedMaxRow);
					topRows = 0;
				}
			}
		}
		int marginBefore = actionStripMargin();
		applyGhostRowPadding(topRows, belowField, marginEnd);
		// Margin changed: the next layout reflows the line. Keep the dirty
		// flag so that pass measures the ghost on the new line.
		return actionStripMargin() == marginBefore;
	}

	/**
	 * The field's own layout just changed hyphenation. Suggestions measure
	 * that same layout, including the Edit/Send margin already on it.
	 */
	public void refreshGhostAfterHyphenation() {
		scheduleGhostRoom();
		invalidate();
	}

	/**
	 * One pass after the text and the Layout agree. Several setters in one
	 * tap (clear the ghost, then set the new list) share that pass.
	 */
	private void scheduleGhostRoom() {
		ghostRoomDirty = true;
		if (ghostRoomPosted) {
			return;
		}
		ghostRoomPosted = true;
		post(new Runnable() {
			@Override
			public void run() {
				ghostRoomPosted = false;
				if (!ghostRoomDirty || !ghostLayoutReady()) {
					return;
				}
				if (applyGhostRoom()) {
					ghostRoomDirty = false;
				}
			}
		});
	}

	/** Layout describes the current text. A shorter one must not size the row. */
	private boolean ghostLayoutReady() {
		if (getWidth() <= 0) {
			return false;
		}
		android.text.Layout layout = getLayout();
		if (layout == null) {
			return false;
		}
		CharSequence text = getText();
		CharSequence laid = layout.getText();
		if (text == null || laid == null) {
			return false;
		}
		return laid.length() == text.length();
	}

	/**
	 * Caret-middle list: after the typed text on the last line, not at the
	 * caret. At the caret it covers the words that follow (phone, 30 Sep 2026).
	 */
	private boolean listAtTextEnd() {
		return ghostAtCaret && !caretAtEnd();
	}

	/**
	 * Inline ghost placement for the current caret and suggestion width, or
	 * null when there is no end-of-text inline ghost to place.
	 */
	private GhostExtraLayout.Fit inlineGhostFit() {
		return inlineGhostFitOn(getLayout());
	}

	/**
	 * Inline ghost on {@code layout}. A tap lengthens the text before Layout
	 * catches up; {@code getPrimaryHorizontal} then throws (phone, 30 Sep 2026).
	 */
	private GhostExtraLayout.Fit inlineGhostFitOn(final android.text.Layout layout) {
		if (ghostText == null || !caretAtEnd() || !ghostWouldDraw() || layout == null) {
			return null;
		}
		float avail = caretListWidth();
		if (avail <= 0f) {
			return null;
		}
		int at = getSelectionStart();
		if (!layoutHasOffset(layout, at)) {
			return null;
		}
		float caretX = layout.getPrimaryHorizontal(at);
		float ghostWidth = getPaint().measureText(ghostText) + inlineGhostNumberWidth();
		return GhostExtraLayout.fitBesideOrWrapDown(caretX, ghostWidth, avail);
	}

	/**
	 * True when the inline ghost must start on the line below the typed block
	 * because the last line has no room left. Multi-line alone is not enough —
	 * beside the caret when that line still fits the suggestion.
	 */
	private boolean ghostDrawsBelowField() {
		GhostExtraLayout.Fit fit = inlineGhostFit();
		return fit != null && fit.wrapDown;
	}

	/**
	 * Would the ghost be drawn at all right now?
	 *
	 * <p>Room must not be held for something that is not going to appear. The
	 * inline ghost is drawn only with the caret at the very end of the text —
	 * put it back into the middle of the line and it would sit on top of what
	 * follows. With {@link #setGhostAtCaret} the extras still draw, starting
	 * at the end of the typed text, so those rows have to stay.
	 */
	private boolean ghostWouldDraw() {
		if (getText() == null) {
			return false;
		}
		int at = getSelectionStart();
		if (at < 0 || at != getSelectionEnd()) {
			return false;
		}
		boolean hasInline = ghostText != null;
		boolean hasExtras = ghostExtras != null && ghostExtras.length > 0;
		if (!hasInline && !hasExtras) {
			return false;
		}
		if (at == getText().length()) {
			return hasInline || hasExtras;
		}
		return ghostAtCaret && hasExtras;
	}

	private boolean caretAtEnd() {
		CharSequence text = getText();
		if (text == null) {
			return true;
		}
		int at = getSelectionStart();
		return at >= 0 && at == text.length() && at == getSelectionEnd();
	}

	/**
	 * Extra rows the listing may take. A full last line has no room at the
	 * end, so a ceiling of zero would hide the caret-middle list — allow one
	 * row below the field then.
	 */
	private int ghostRowCap() {
		if (ghostAtCaret && !caretAtEnd() && ghostMaxRows < 1) {
			return 1;
		}
		return ghostMaxRows;
	}

	/** X where extras follow a fit already measured on the destination width. */
	private float ghostEndAfter(final GhostExtraLayout.Fit fit) {
		if (fit == null) {
			return estimateGhostEndX();
		}
		float drawn = ghostText == null ? 0f : getPaint().measureText(ghostText);
		drawn += inlineGhostNumberWidth();
		if (fit.wrapDown) {
			return drawn;
		}
		return fit.x + drawn;
	}

	/**
	 * Where the inline ghost is likely to end, without waiting for a draw.
	 * The suggestions carry on from there, which decides how tall the bar must
	 * be. Measured from the text, not from the previous frame.
	 */
	private float estimateGhostEndX() {
		if (listAtTextEnd()) {
			return textEndX();
		}
		GhostExtraLayout.Fit fit = inlineGhostFit();
		if (fit != null) {
			return ghostEndAfter(fit);
		}
		CharSequence text = getText();
		String line = text == null ? "" : text.toString();
		int nl = line.lastIndexOf('\n');
		if (nl >= 0) {
			line = line.substring(nl + 1);
		}
		return getPaint().measureText(line)
				+ (ghostText == null ? 0 : getPaint().measureText(ghostText))
				+ inlineGhostNumberWidth();
	}

	/**
	 * X where the typed text ends on the last line. A tap inserts the word
	 * before this runs, while the Layout still describes the shorter text —
	 * {@code getPrimaryHorizontal} then throws (phone, 30 Sep 2026). Measure
	 * the last line until the lengths match.
	 */
	private float textEndX() {
		android.text.Layout layout = getLayout();
		CharSequence text = getText();
		float x;
		if (layout != null && text != null && text.length() > 0
				&& layoutHasOffset(layout, text.length())) {
			x = layout.getPrimaryHorizontal(text.length());
		} else {
			x = measuredLastLineEnd(text);
		}
		if (x < 0f) {
			x = 0f;
		}
		float avail = ghostRowWidth();
		if (avail > 0f && x > avail) {
			x = avail;
		}
		return x;
	}

	/**
	 * True when {@code offset} is inside the layout's text, including the
	 * exclusive end of its line. A longer {@link #getText()}, or a line end
	 * short of that offset, means the layout is stale and must not be queried.
	 */
	private static boolean layoutHasOffset(final android.text.Layout layout,
			final int offset) {
		CharSequence laid = layout.getText();
		if (laid == null || offset < 0 || offset > laid.length()) {
			return false;
		}
		int line = layout.getLineForOffset(offset);
		int start = layout.getLineStart(line);
		int limit = layout.getLineEnd(line) - start;
		int within = offset - start;
		return within >= 0 && within <= limit;
	}

	private float measuredLastLineEnd(final CharSequence text) {
		String line = text == null ? "" : text.toString();
		int nl = line.lastIndexOf('\n');
		if (nl >= 0) {
			line = line.substring(nl + 1);
		}
		return getPaint().measureText(line);
	}

	/**
	 * Inner text width if the Hide/Send margin were {@code marginEnd}.
	 * The view's current line count is the wrong input: clearing the margin
	 * can make a two-line field one line, and the margin comes back.
	 */
	private int textInnerWidthForMargin(final int marginEnd) {
		int inner = getWidth() + actionStripMargin() - marginEnd
				- getTotalPaddingLeft() - getTotalPaddingRight();
		return inner > 0 ? inner : 0;
	}

	/** End of the last line at {@code innerWidth}, not at the view's current width. */
	private float lastLineEndAt(final int innerWidth) {
		CharSequence text = getText();
		if (text == null || text.length() == 0 || innerWidth <= 0) {
			return 0f;
		}
		android.text.Layout layout = layoutAt(innerWidth);
		if (layout != null && layoutHasOffset(layout, text.length())) {
			return layout.getPrimaryHorizontal(text.length());
		}
		return measuredLastLineEnd(text);
	}

	private android.text.Layout layoutAt(final int innerWidth) {
		CharSequence text = getText();
		android.text.Layout layout = getLayout();
		if (layout != null && text != null && layout.getText() != null
				&& layout.getText().length() == text.length()
				&& layout.getWidth() == innerWidth) {
			return layout;
		}
		if (text == null || text.length() == 0 || innerWidth <= 0) {
			return layout;
		}
		return android.text.StaticLayout.Builder
				.obtain(text, 0, text.length(), getPaint(), innerWidth)
				.setIncludePad(getIncludeFontPadding())
				.setLineSpacing(getLineSpacingExtra(), getLineSpacingMultiplier())
				.setBreakStrategy(getBreakStrategy())
				.setHyphenationFrequency(getHyphenationFrequency())
				.build();
	}

	/**
	 * Grow the EditText for suggestion rows. Top padding is the upward stack.
	 * Bottom padding is a wrap-down slot; IME clearance for that slot is chrome
	 * {@code translationY}. The end margin is the Hide/Send column on every
	 * line. Not during a gesture: {@code setLayoutParams} drops {@code Layout}
	 * (Editor NPE, 15 Sep 2026).
	 */
	private void applyGhostRowPadding(final int topRows, final int bottomRows,
			final int marginEnd) {
		// Holding blocks shrink (setPadding nulls Layout mid-gesture). Growth
		// must still happen or extras draw under the keyboard (phone, 30 Sep).
		if (ghostPaddingHeld && topRows <= ghostTopRowsShown
				&& bottomRows <= ghostBottomRowsShown) {
			return;
		}
		if (ghostBasePaddingBottom < 0) {
			ghostBasePaddingBottom = getPaddingBottom();
		}
		if (ghostBasePaddingTop < 0) {
			ghostBasePaddingTop = getPaddingTop();
		}
		if (ghostBasePaddingRight < 0) {
			ghostBasePaddingRight = getPaddingRight();
		}
		float lineSpacing = getPaint().getFontSpacing();
		int lineHeight = Math.round(lineSpacing);
		// List starts at the bottom of the typed text. Hide/Send is taller than
		// one line; that surplus sits under the list, and the lift is the whole
		// pad so the last line is not the strip the keyboard covers
		// (phone, 30 Sep 2026).
		boolean caretBelow = listAtTextEnd() && bottomRows > 0;
		int extraBottom = GhostExtraLayout.belowFieldPadPx(bottomRows, lineSpacing,
				caretBelow ? actionStripHeightPx : 0);
		int belowLift = extraBottom;
		int top = ghostBasePaddingTop + topRows * lineHeight;
		int right = ghostBasePaddingRight;
		int bottom = ghostBasePaddingBottom + extraBottom;
		int marginNow = actionStripMargin();
		boolean marginOk = ghostPaddingHeld || marginNow == marginEnd;
		if (topRows == ghostTopRowsShown && bottomRows == ghostBottomRowsShown
				&& getPaddingTop() == top && getPaddingRight() == right
				&& getPaddingBottom() == bottom && marginOk) {
			notifyGhostBelowFieldLift(belowLift);
			return;
		}
		ghostTopRowsShown = topRows;
		ghostBottomRowsShown = bottomRows;
		if (!ghostPaddingHeld) {
			setActionStripMargin(marginEnd);
		}
		setPadding(getPaddingLeft(), top, right, bottom);
		notifyGhostBelowFieldLift(belowLift);
	}

	private int actionStripMargin() {
		android.view.ViewGroup.LayoutParams lp = getLayoutParams();
		if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
			return ((android.view.ViewGroup.MarginLayoutParams) lp).rightMargin;
		}
		return 0;
	}

	private void setActionStripMargin(final int marginEnd) {
		android.view.ViewGroup.LayoutParams lp = getLayoutParams();
		if (!(lp instanceof android.view.ViewGroup.MarginLayoutParams)) {
			return;
		}
		android.view.ViewGroup.MarginLayoutParams mlp =
				(android.view.ViewGroup.MarginLayoutParams) lp;
		if (mlp.rightMargin == marginEnd) {
			return;
		}
		mlp.rightMargin = marginEnd;
		setLayoutParams(mlp);
	}

	private void notifyGhostBelowFieldLift(final int liftPx) {
		if (ghostBelowFieldLiftListener != null) {
			ghostBelowFieldLiftListener.onGhostBelowFieldLiftPx(liftPx);
		}
	}

	/** The word a tap on the ghost would insert; null when there is no ghost. */
	public String getGhostWord() {
		return ghostWord;
	}

	@Override
	protected void onTextChanged(CharSequence text, int start, int lengthBefore,
			int lengthAfter) {
		super.onTextChanged(text, start, lengthBefore, lengthAfter);
		// A wrap-down ghost needs its row after layout, once the Layout matches
		// the text (phone, 29 Sep 2026: the ghost drew on the keyboard edge).
		scheduleGhostRoom();
	}

	/**
	 * Break a word that fits on the next line but not in the space left
	 * here. The mark is a break only; send strips it.
	 */
	public void setHyphenBreaks(final boolean on, final int minLead, final int minTail,
			final boolean polish) {
		hyphenBreaksOn = on;
		hyphenMinLead = minLead;
		hyphenMinTail = minTail;
		hyphenPolish = polish;
		ensureHyphenWatcher();
		syncHyphenBreaks();
	}

	private void ensureHyphenWatcher() {
		if (hyphenWatcherInstalled) {
			return;
		}
		hyphenWatcherInstalled = true;
		watchHyphenSpans(getText());
		addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(final CharSequence s, final int start,
					final int count, final int after) {
				if (hyphenSyncing) {
					return;
				}
				hyphenSwallowAt = letterBesideDeletedMark(s, start, count, after);
			}

			@Override
			public void onTextChanged(final CharSequence s, final int start,
					final int before, final int count) {
			}

			@Override
			public void afterTextChanged(final Editable s) {
				if (hyphenSyncing) {
					return;
				}
				int swallow = hyphenSwallowAt;
				hyphenSwallowAt = -1;
				if (s != null && swallow >= 0 && swallow < s.length()) {
					int[] cluster = InputHyphenBreaks.clusterRange(s.toString(), swallow);
					if (cluster[0] < cluster[1]
							&& s.charAt(cluster[0]) != InputHyphenBreaks.MARK
							&& !Character.isWhitespace(s.charAt(cluster[0]))) {
						String deleted = s.subSequence(cluster[0], cluster[1]).toString();
						InputFilter[] filters = s.getFilters();
						s.setFilters(withoutUndoFilter(filters));
						hyphenSyncing = true;
						try {
							s.delete(cluster[0], cluster[1]);
						} finally {
							hyphenSyncing = false;
							s.setFilters(filters);
						}
						retargetMarkDelete(deleted, cluster[0]);
					}
				}
				watchHyphenSpans(s);
				syncHyphenBreaks();
			}
		});
	}

	private void watchHyphenSpans(final Spannable text) {
		if (text == null || text.getSpanStart(hyphenSpanWatcher) >= 0) {
			return;
		}
		text.setSpan(hyphenSpanWatcher, 0, 0, Spanned.SPAN_POINT_POINT);
	}

	private void postHyphenSync() {
		if (hyphenSyncPosted) {
			return;
		}
		hyphenSyncPosted = true;
		post(new Runnable() {
			@Override
			public void run() {
				hyphenSyncPosted = false;
				syncHyphenBreaks();
			}
		});
	}

	private final class HyphenSpanWatcher implements SpanWatcher, NoCopySpan {
		@Override
		public void onSpanAdded(final Spannable text, final Object what, final int start,
				final int end) {
		}

		@Override
		public void onSpanRemoved(final Spannable text, final Object what, final int start,
				final int end) {
			if (BaseInputConnection.getComposingSpanStart(text) < 0) {
				postHyphenSync();
			}
		}

		@Override
		public void onSpanChanged(final Spannable text, final Object what, final int ostart,
				final int oend, final int nstart, final int nend) {
		}
	}

	private void syncHyphenBreaks() {
		if (hyphenSyncing) {
			return;
		}
		Editable text = getText();
		if (text == null) {
			return;
		}
		int width = hyphenLineWidth();
		if (hyphenBreaksOn && width <= 0) {
			return;
		}
		String raw = text.toString();
		String logical = InputHyphenBreaks.strip(raw);
		int[] cuts = hyphenBreaksOn
				? InputHyphenBreaks.cuts(logical, width,
						getPaint().measureText("-"), hyphenMinLead, hyphenMinTail,
						hyphenPolish, hyphenMeasurer)
				: InputHyphenBreaks.NO_CUTS;
		int cs = BaseInputConnection.getComposingSpanStart(text);
		int ce = BaseInputConnection.getComposingSpanEnd(text);
		InputHyphenBreaks.Op[] ops = InputHyphenBreaks.reconcile(raw, cuts, cs, ce);
		if (ops.length == 0) {
			hyphenSyncedWidth = width;
			return;
		}
		hyphenSyncing = true;
		try {
			applyHyphenOps(text, ops);
		} finally {
			hyphenSyncing = false;
			hyphenSyncedWidth = width;
		}
	}

	/**
	 * Where {@code logical} sits once marks are in the buffer.
	 */
	public void setLogicalSelection(final int logical) {
		Editable text = getText();
		String raw = text == null ? "" : text.toString();
		int at = InputHyphenBreaks.bufferIndex(raw, logical);
		int len = text == null ? 0 : text.length();
		setSelection(Math.max(0, Math.min(len, at)));
	}

	private void applyHyphenOps(final Editable text, final InputHyphenBreaks.Op[] ops) {
		// Recording the mark turns that undo step into a replace of the whole
		// line, and the next character joins it. The mark stays out of undo.
		InputFilter[] filters = text.getFilters();
		text.setFilters(withoutUndoFilter(filters));
		try {
			for (int i = 0; i < ops.length; i++) {
				InputHyphenBreaks.Op op = ops[i];
				if (op.insert) {
					text.insert(op.index, String.valueOf(InputHyphenBreaks.MARK));
				} else if (op.index < text.length()
						&& text.charAt(op.index) == InputHyphenBreaks.MARK) {
					text.delete(op.index, op.index + 1);
				}
			}
		} finally {
			text.setFilters(filters);
		}
		noteMarksInLastUndo(ops);
	}

	/**
	 * The step was stored before the mark existed. Shift it so undo still
	 * points at the letter, not at the mark that landed in front of it.
	 */
	private void noteMarksInLastUndo(final InputHyphenBreaks.Op[] ops) {
		if (ops == null || ops.length == 0) {
			return;
		}
		try {
			java.lang.reflect.Field editorField = TextView.class.getDeclaredField("mEditor");
			editorField.setAccessible(true);
			Object editor = editorField.get(this);
			if (editor == null) {
				return;
			}
			java.lang.reflect.Field managerField = editor.getClass().getDeclaredField("mUndoManager");
			managerField.setAccessible(true);
			Object manager = managerField.get(editor);
			if (manager == null) {
				return;
			}
			java.lang.reflect.Method begin = manager.getClass().getMethod("beginUpdate",
					CharSequence.class);
			java.lang.reflect.Method end = manager.getClass().getMethod("endUpdate");
			begin.invoke(manager, "Edit text");
			try {
				Object edit = manager.getClass().getMethod("getLastOperation", int.class)
						.invoke(manager, 1);
				if (edit == null || !edit.getClass().getName().endsWith("EditOperation")) {
					return;
				}
				shiftUndoForMarks(edit, ops);
			} finally {
				end.invoke(manager);
			}
		} catch (ReflectiveOperationException ignored) {
			// Hidden editor fields are blocked on some releases. Undo of the
			// keystroke that first inserted the mark can then miss by one.
		}
	}

	private static void shiftUndoForMarks(final Object edit, final InputHyphenBreaks.Op[] ops)
			throws ReflectiveOperationException {
		java.lang.reflect.Field startField = edit.getClass().getDeclaredField("mStart");
		java.lang.reflect.Field textField = edit.getClass().getDeclaredField("mNewText");
		java.lang.reflect.Field cursorField = edit.getClass().getDeclaredField("mNewCursorPos");
		java.lang.reflect.Field oldCursorField = edit.getClass().getDeclaredField("mOldCursorPos");
		startField.setAccessible(true);
		textField.setAccessible(true);
		cursorField.setAccessible(true);
		oldCursorField.setAccessible(true);
		int start = startField.getInt(edit);
		String recorded = (String) textField.get(edit);
		int cursor = cursorField.getInt(edit);
		int oldCursor = oldCursorField.getInt(edit);
		if (recorded == null) {
			return;
		}
		for (int i = 0; i < ops.length; i++) {
			InputHyphenBreaks.Op op = ops[i];
			if (op.insert) {
				if (op.index <= start) {
					start++;
				} else if (op.index < start + recorded.length()) {
					int rel = op.index - start;
					recorded = recorded.substring(0, rel) + InputHyphenBreaks.MARK
							+ recorded.substring(rel);
				}
				if (cursor >= 0 && op.index < cursor) {
					cursor++;
				}
				if (oldCursor >= 0 && op.index <= oldCursor) {
					oldCursor++;
				}
			} else if (op.index < start) {
				start--;
				if (cursor > op.index) {
					cursor--;
				}
				if (oldCursor > op.index) {
					oldCursor--;
				}
			} else if (op.index < start + recorded.length()
					&& recorded.charAt(op.index - start) == InputHyphenBreaks.MARK) {
				int rel = op.index - start;
				recorded = recorded.substring(0, rel) + recorded.substring(rel + 1);
				if (cursor > op.index) {
					cursor--;
				}
				if (oldCursor > op.index) {
					oldCursor--;
				}
			}
		}
		startField.setInt(edit, start);
		textField.set(edit, recorded);
		cursorField.setInt(edit, cursor);
		oldCursorField.setInt(edit, oldCursor);
	}

	/**
	 * The keystroke deleted the mark. The letter beside it is what should
	 * come back, and that delete must not become a replace of the whole line.
	 */
	private void retargetMarkDelete(final String deleted, final int at) {
		if (deleted == null || deleted.length() == 0) {
			return;
		}
		try {
			java.lang.reflect.Field editorField = TextView.class.getDeclaredField("mEditor");
			editorField.setAccessible(true);
			Object editor = editorField.get(this);
			if (editor == null) {
				return;
			}
			java.lang.reflect.Field managerField = editor.getClass().getDeclaredField("mUndoManager");
			managerField.setAccessible(true);
			Object manager = managerField.get(editor);
			if (manager == null) {
				return;
			}
			java.lang.reflect.Method begin = manager.getClass().getMethod("beginUpdate",
					CharSequence.class);
			java.lang.reflect.Method end = manager.getClass().getMethod("endUpdate");
			begin.invoke(manager, "Edit text");
			try {
				Object edit = manager.getClass().getMethod("getLastOperation", int.class)
						.invoke(manager, 1);
				if (edit == null || !edit.getClass().getName().endsWith("EditOperation")) {
					return;
				}
				java.lang.reflect.Field oldField = edit.getClass().getDeclaredField("mOldText");
				java.lang.reflect.Field startField = edit.getClass().getDeclaredField("mStart");
				java.lang.reflect.Field oldCursorField = edit.getClass().getDeclaredField(
						"mOldCursorPos");
				oldField.setAccessible(true);
				startField.setAccessible(true);
				oldCursorField.setAccessible(true);
				String old = (String) oldField.get(edit);
				if (old == null || old.indexOf(InputHyphenBreaks.MARK) < 0) {
					return;
				}
				oldField.set(edit, deleted);
				startField.setInt(edit, at);
				oldCursorField.setInt(edit, at + deleted.length());
			} finally {
				end.invoke(manager);
			}
		} catch (ReflectiveOperationException ignored) {
			// Same hidden fields as the mark shift. Undo restores the mark.
		}
	}

	private void stopSelectionHandles() {
		try {
			java.lang.reflect.Method stop = TextView.class.getDeclaredMethod("stopTextActionMode");
			stop.setAccessible(true);
			stop.invoke(this);
		} catch (ReflectiveOperationException ignored) {
			int at = Math.max(getSelectionStart(), getSelectionEnd());
			if (at >= 0) {
				setSelection(at);
			}
		}
	}

	private static InputFilter[] withoutUndoFilter(final InputFilter[] filters) {
		if (filters == null || filters.length == 0) {
			return filters == null ? new InputFilter[0] : filters;
		}
		int keep = 0;
		for (int i = 0; i < filters.length; i++) {
			if (!isUndoFilter(filters[i])) {
				keep++;
			}
		}
		if (keep == filters.length) {
			return filters;
		}
		InputFilter[] out = new InputFilter[keep];
		int j = 0;
		for (int i = 0; i < filters.length; i++) {
			if (!isUndoFilter(filters[i])) {
				out[j++] = filters[i];
			}
		}
		return out;
	}

	/**
	 * Backspace just after the mark, or delete on it, removes only the mark.
	 * The letter beside it is what the player meant to erase.
	 */
	private int letterBesideDeletedMark(final CharSequence s, final int start,
			final int count, final int after) {
		if (!hyphenBreaksOn || after != 0 || count != 1 || s == null) {
			return -1;
		}
		if (start < 0 || start >= s.length() || s.charAt(start) != InputHyphenBreaks.MARK) {
			return -1;
		}
		int sel = getSelectionStart();
		if (sel < 0 || sel != getSelectionEnd()) {
			return -1;
		}
		if (sel == start + 1 && start > 0) {
			return start - 1;
		}
		if (sel == start && start + 1 < s.length()) {
			return start;
		}
		return -1;
	}

	private static boolean isUndoFilter(final InputFilter filter) {
		return filter != null
				&& "android.widget.Editor$UndoInputFilter".equals(filter.getClass().getName());
	}

	private int hyphenLineWidth() {
		android.text.Layout layout = getLayout();
		if (layout != null && layout.getWidth() > 0) {
			return layout.getWidth();
		}
		int w = getWidth() - getTotalPaddingLeft() - getTotalPaddingRight();
		return w > 0 ? w : 0;
	}

	private void drawHyphenMarks(final android.graphics.Canvas canvas) {
		if (!hyphenBreaksOn) {
			return;
		}
		android.text.Layout layout = getLayout();
		if (layout == null) {
			return;
		}
		CharSequence laid = layout.getText();
		if (laid == null || !InputHyphenBreaks.contains(laid)) {
			return;
		}
		android.text.TextPaint paint = getPaint();
		int old = paint.getColor();
		paint.setColor(getCurrentTextColor());
		canvas.save();
		canvas.translate(getTotalPaddingLeft() - getScrollX(),
				getTotalPaddingTop() - getScrollY());
		try {
			int lines = layout.getLineCount();
			float hyphenWidth = paint.measureText("-");
			for (int line = 0; line < lines; line++) {
				if (layout.getParagraphDirection(line) < 0) {
					continue;
				}
				int start = layout.getLineStart(line);
				int end = layout.getLineEnd(line);
				int markAt = -1;
				for (int i = end; i > start; i--) {
					char c = laid.charAt(i - 1);
					if (c == InputHyphenBreaks.MARK) {
						markAt = i - 1;
						break;
					}
					if (c != ' ' && c != '\n' && c != '\t') {
						break;
					}
				}
				if (markAt < 0) {
					continue;
				}
				boolean tail = false;
				for (int k = markAt + 1; k < end; k++) {
					char c = laid.charAt(k);
					if (c != ' ' && c != '\n' && c != '\t'
							&& c != InputHyphenBreaks.MARK) {
						tail = true;
						break;
					}
				}
				if (tail) {
					continue;
				}
				if (markAt > 0 && isTypedHyphen(laid.charAt(markAt - 1))) {
					continue;
				}
				if (!layoutHasOffset(layout, markAt)) {
					continue;
				}
				float x = layout.getPrimaryHorizontal(markAt);
				if (x + hyphenWidth > layout.getWidth() + 1f) {
					continue;
				}
				canvas.drawText("-", x, layout.getLineBaseline(line), paint);
			}
		} finally {
			canvas.restore();
			paint.setColor(old);
		}
	}

	private static boolean isTypedHyphen(final char c) {
		return c == '-' || c == '\u2010';
	}

	@Override
	public boolean onTextContextMenuItem(final int id) {
		if ((id == android.R.id.copy || id == android.R.id.cut || id == android.R.id.shareText)
				&& getText() != null && InputHyphenBreaks.contains(getText())) {
			int start = Math.max(0, Math.min(getSelectionStart(), getSelectionEnd()));
			int end = Math.max(getSelectionStart(), getSelectionEnd());
			if (start < 0) {
				start = 0;
			}
			end = Math.min(end, getText().length());
			if (start < end) {
				String slice = InputHyphenBreaks.strip(
						getText().subSequence(start, end).toString());
				if (id == android.R.id.shareText) {
					android.content.Intent sharing = new android.content.Intent(
							android.content.Intent.ACTION_SEND);
					sharing.setType("text/plain");
					sharing.putExtra(android.content.Intent.EXTRA_TEXT, slice);
					getContext().startActivity(android.content.Intent.createChooser(sharing, null));
					stopSelectionHandles();
					return true;
				}
				ClipboardManager clips = (ClipboardManager) getContext()
						.getSystemService(Context.CLIPBOARD_SERVICE);
				if (clips == null) {
					return super.onTextContextMenuItem(id);
				}
				clips.setPrimaryClip(ClipData.newPlainText("text", slice));
				if (id == android.R.id.cut) {
					getText().delete(start, end);
				}
				stopSelectionHandles();
				return true;
			}
		}
		return super.onTextContextMenuItem(id);
	}

	@Override
	protected void onDraw(android.graphics.Canvas canvas) {
		// TextView may leave a clip on the scrolled text. Ghost extras sit in
		// top/bottom padding, which that clip cuts off.
		int clip = canvas.save();
		super.onDraw(canvas);
		canvas.restoreToCount(clip);
		drawHyphenMarks(canvas);
		ghostRectCount = 0;
		if (!ghostWouldDraw()) {
			return;
		}
		android.text.Layout layout = getLayout();
		if (layout == null || getText() == null) {
			return;
		}
		int at = getSelectionStart();
		if (at < 0) {
			return;
		}
		boolean atEnd = caretAtEnd();
		if (ghostPaint == null) {
			ghostPaint = new android.text.TextPaint();
		}
		ghostPaint.set(getPaint());
		ghostPaint.setColor((getCurrentTextColor() & 0x00FFFFFF) | 0x70000000);
		// Suggestions stop before Hide/Send. The field margin keeps the typed
		// text beside those buttons on every line.
		float lineWidth = caretListWidth();
		if (lineWidth <= 0f) {
			lineWidth = getWidth() - getTotalPaddingLeft() - getTotalPaddingRight();
		}
		final float originX = getTotalPaddingLeft() - getScrollX();
		final float originY = getTotalPaddingTop() - getScrollY();
		canvas.save();
		canvas.translate(originX, originY);

		float endX = lineWidth;
		float endBaseline = layout.getLineBaseline(layout.getLineCount() - 1);
		// Stale Layout: getPrimaryHorizontal throws. Skip this frame.
		boolean drawInline = atEnd && ghostText != null && layoutHasOffset(layout, at);
		GhostExtraLayout.Fit inlineFit = drawInline ? inlineGhostFit() : null;
		// Wrap down only when the last line has no room — not because lineCount > 1.
		boolean belowField = inlineFit != null && inlineFit.wrapDown;
		if (drawInline) {
			float x;
			float baseline;
			float top;
			float bottom;
			float lineHeight;
			if (belowField) {
				lineHeight = ghostPaint.getFontSpacing();
				// Tight under the typed block. Pinning to the view bottom left
				// a gap when the button band was taller than the line, and
				// drew on the keyboard edge when the row was not padded.
				x = 0f;
				top = layout.getLineBottom(layout.getLineCount() - 1);
				bottom = top + lineHeight;
				baseline = bottom - ghostPaint.descent();
			} else {
				int line = layout.getLineForOffset(at);
				x = layout.getPrimaryHorizontal(at);
				baseline = layout.getLineBaseline(line);
				top = layout.getLineTop(line);
				bottom = layout.getLineBottom(line);
				lineHeight = bottom - top;
			}
			float room = lineWidth - x;
			float ghostWidth = ghostPaint.measureText(ghostText);

			if (ghostWidth <= room) {
				canvas.drawText(ghostText, x, baseline, ghostPaint);
				addGhostRect(originX + x, originY + top, originX + x + ghostWidth,
						originY + bottom);
				endX = x + ghostWidth;
				endBaseline = baseline;
			} else {
				int fits = ghostPaint.breakText(ghostText, true, room, null);
				// A next line to continue on only exists if the view is already tall
				// enough for one. Wrap-down keeps to one reserved row (ellipsis).
				// On the typed line the ghost never adds height.
				boolean hasNextLine = !belowField
						&& (bottom + lineHeight <= layout.getHeight()
						|| bottom + lineHeight <= getHeight() - getTotalPaddingTop()
								- getTotalPaddingBottom());
				if (fits > 0 && hasNextLine) {
					String head = ghostText.substring(0, fits);
					String tail = ghostText.substring(fits);
					canvas.drawText(head, x, baseline, ghostPaint);
					addGhostRect(originX + x, originY + top,
							originX + x + ghostPaint.measureText(head), originY + bottom);
					int tailFits = ghostPaint.breakText(tail, true, lineWidth, null);
					if (tailFits < tail.length()) {
						tail = tailFits > 0 ? tail.substring(0, tailFits - 1) + "…" : "…";
					}
					float tailWidth = ghostPaint.measureText(tail);
					canvas.drawText(tail, 0, baseline + lineHeight, ghostPaint);
					addGhostRect(originX, originY + bottom, originX + tailWidth,
							originY + bottom + lineHeight);
					endX = tailWidth;
					endBaseline = baseline + lineHeight;
				} else {
					String cut = fits > 1 ? ghostText.substring(0, fits - 1) + "…" : "…";
					float cutWidth = ghostPaint.measureText(cut);
					canvas.drawText(cut, x, baseline, ghostPaint);
					addGhostRect(originX + x, originY + top, originX + x + cutWidth,
							originY + bottom);
					endX = x + cutWidth;
					endBaseline = baseline;
				}
			}
		}

		float inlineNumberX = -1f;
		if (drawInline && ghostNumber > 0) {
			inlineNumberX = endX;
			endX += ghostIndexWidth(ghostPaint, ghostNumber);
			if (ghostRectCount > 0) {
				float right = originX + endX;
				if (right > ghostRects[ghostRectCount - 1].right) {
					ghostRects[ghostRectCount - 1].right = right;
				}
			}
		}
		float extrasEndX = endX;
		float extrasBaseline = endBaseline;
		if (ghostExtras != null && ghostExtras.length > 0) {
			for (int i = 0; i < ghostExtraRects.length; i++) {
				ghostExtraRects[i] = null;
			}
			// After the typed text, on the last line. x=0 on that line, or the
			// caret's x, paints the list in the background of the sentence.
			float extrasStartX = drawInline ? endX : textEndX();
			if (drawInline) {
				extrasStartX += ghostGap();
			}
			float rowAvail = lineWidth;
			float contentTop = layout.getLineTop(0);
			float contentBottom = layout.getLineBottom(layout.getLineCount() - 1);
			packGhostExtras(canvas, rowAvail, extrasStartX, endBaseline,
					contentTop, contentBottom, ghostPaint.getFontSpacing(),
					originX, originY, ghostSplit && drawInline);
			if (ghostLastDrawnX >= 0) {
				extrasEndX = ghostLastDrawnX;
				extrasBaseline = ghostLastDrawnBaseline;
			}
		} else {
			ghostHiddenCount = 0;
		}

		// The count of what is not on screen, drawn where the last of them ended.
		// Only ever the ones you cannot see: writing "+2" beside two visible
		// words is telling the player something they can already count.
		if (ghostHiddenCount > 0) {
			String mark = " +" + ghostHiddenCount;
			android.text.TextPaint dim = new android.text.TextPaint(ghostPaint);
			dim.setTextSize(ghostPaint.getTextSize() * 0.8f);
			// After the last one actually drawn, on that one's line. Pinned to
			// the ghost's line instead, it landed on top of the typed text
			// whenever anything had wrapped.
			canvas.drawText(mark, extrasEndX, extrasBaseline, dim);
		}

		if (inlineNumberX >= 0f) {
			drawGhostIndex(canvas, ghostPaint, String.valueOf(ghostNumber),
					inlineNumberX, endBaseline);
		}
		canvas.restore();
	}

	/** A short stroke between two suggestions on the same line. */
	private void drawGhostDivider(final android.graphics.Canvas canvas,
			final android.text.TextPaint base, final float x, final float baseline) {
		float h = base.getTextSize() * 0.55f;
		android.graphics.Paint line = new android.graphics.Paint(base);
		line.setStyle(android.graphics.Paint.Style.STROKE);
		line.setStrokeWidth(Math.max(1f, base.getTextSize() * 0.06f));
		float mid = baseline - base.getTextSize() * 0.28f;
		canvas.drawLine(x, mid - h * 0.5f, x, mid + h * 0.5f, line);
	}

	/** A micro digit above the baseline, matching the inline ghost marker. */
	private void drawGhostIndex(final android.graphics.Canvas canvas,
			final android.text.TextPaint base, final String digit, final float x,
			final float baseline) {
		android.text.TextPaint mark = new android.text.TextPaint(base);
		mark.setTextSize(base.getTextSize() * 0.55f);
		canvas.drawText(digit, x, baseline - base.getTextSize() * 0.45f, mark);
	}

	/** Width reserved after the inline ghost for its number, or 0. */
	private float inlineGhostNumberWidth() {
		if (ghostNumber <= 0 || ghostText == null) {
			return 0f;
		}
		return ghostIndexWidth(getPaint(), ghostNumber);
	}

	/** Width of one index digit at the size {@link #drawGhostIndex} uses. */
	private float ghostIndexWidth(final android.text.TextPaint base, final int number) {
		android.text.TextPaint mark = new android.text.TextPaint(base);
		mark.setTextSize(base.getTextSize() * 0.55f);
		return mark.measureText(String.valueOf(number)) + 2f;
	}

	private void addGhostRect(final float left, final float top, final float right,
			final float bottom) {
		if (ghostRectCount >= ghostRects.length || right <= left) {
			return;
		}
		float pad = GHOST_TOUCH_SLOP_DIP * getResources().getDisplayMetrics().density;
		// Vertical slop only. Widening sideways would swallow taps meant for the
		// text that ends where the ghost begins.
		ghostRects[ghostRectCount].set(left, top - pad / 2f, right, bottom + pad / 2f);
		ghostRectCount++;
	}

	/**
	 * A tap on the ghost takes it.
	 *
	 * <p>Handled here rather than by a listener on the field, because only this
	 * view knows where the ghost ended up — it is drawn, so there is no span to
	 * hit-test. The down event is consumed when it lands on the ghost, which also
	 * keeps the caret from moving out from under the suggestion before the tap
	 * finishes.
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		final int action = event.getActionMasked();
		if (action == MotionEvent.ACTION_DOWN) {
			ghostPaddingHeld = true;
		}
		try {
			if (ghostTapListener != null
					&& (ghostWord != null
						|| (ghostExtraWords != null && ghostExtraWords.length > 0))) {
				switch (action) {
				case MotionEvent.ACTION_DOWN:
					if (hitsGhost(event.getX(), event.getY())) {
						ghostTouchDown = true;
						return true;
					}
					break;
				case MotionEvent.ACTION_UP:
					if (ghostTouchDown) {
						ghostTouchDown = false;
						String word = ghostWordAt(event.getX(), event.getY());
						if (word != null) {
							ghostTapListener.onGhostTapped(word);
						}
						return true;
					}
					break;
				case MotionEvent.ACTION_CANCEL:
					if (ghostTouchDown) {
						ghostTouchDown = false;
						return true;
					}
					break;
				default:
					if (ghostTouchDown) {
						return true;
					}
					break;
				}
			}
			if (getLayout() == null) {
				return true;
			}
			return super.onTouchEvent(event);
		} finally {
			if (action == MotionEvent.ACTION_UP
					|| action == MotionEvent.ACTION_CANCEL) {
				ghostPaddingHeld = false;
				scheduleGhostRoom();
			}
		}
	}

	/**
	 * Lay the other suggestions out, and draw them when there is a canvas.
	 *
	 * <p>One routine for both jobs on purpose: measuring in one place and
	 * drawing in another is how a bar ends up a row short of what it shows.
	 *
	 * <p>They carry on from where the inline ghost ended. Row 0 stays on that
	 * line; wrapped rows stack upward into top padding ({@link GhostExtraLayout})
	 * so they stay above the keyboard under adjustNothing. A caret-middle list
	 * has no inline ghost: row 0 starts after the typed text on the last line,
	 * and only a row that does not fit goes under the text. The bar lifts by
	 * those rows so the last one is not under the keyboard.
	 *
	 * @param canvas null to measure only.
	 * @param avail width of a full row.
	 * @param startX where the first one begins, past the ghost.
	 * @param ghostBaseline baseline of the line the ghost is on.
	 * @param contentTop top of the typed text block (layout line top of line 0).
	 * @param contentBottom bottom of the typed block. Caret-middle rows start
	 *        here, not at the view bottom.
	 * @param lineHeight one line of ghost type.
	 * @param originX left of the content, for hit rectangles.
	 * @param originY top of the content, for hit rectangles.
	 * @param splitAfterGhost a mark belongs in the gap reserved before the first
	 *        extra, and only if that extra stayed on the ghost's line.
	 * @return top-padding rows the bar must reserve above the typed block.
	 */
	private int packGhostExtras(final android.graphics.Canvas canvas, final float avail,
			final float startX, final float ghostBaseline, final float contentTop,
			final float contentBottom, final float lineHeight, final float originX,
			final float originY, final boolean splitAfterGhost) {
		ghostHiddenCount = 0;
		if (canvas != null) {
			ghostLastDrawnX = -1;
		}
		android.text.TextPaint p = canvas != null && ghostPaint != null
				? ghostPaint : getPaint();
		float gap = ghostGap();
		float[] widths = new float[ghostExtras.length];
		for (int i = 0; i < ghostExtras.length; i++) {
			String item = ghostExtras[i];
			if (item == null) {
				widths[i] = -1f;
				continue;
			}
			int number = ghostExtraNumbers != null && i < ghostExtraNumbers.length
					? ghostExtraNumbers[i] : 0;
			float indexW = number > 0 ? ghostIndexWidth(p, number) : 0f;
			widths[i] = indexW + p.measureText(item);
		}
		GhostExtraLayout.Pack pack = GhostExtraLayout.pack(widths, avail, startX, gap,
				ghostRowCap());
		ghostHiddenCount = pack.hiddenCount;
		ghostPackedMaxRow = pack.maxRow;
		float descent = p.descent();
		boolean belowField = listAtTextEnd();
		if (canvas != null) {
			// Text bottom, not the view bottom: a taller Hide/Send band stays
			// under the list instead of opening a gap above it.
			float firstBelow = contentBottom;
			int prev = -1;
			for (int i = 0; i < ghostExtras.length; i++) {
				int row = pack.rows[i];
				if (row < 0) {
					continue;
				}
				String item = ghostExtras[i];
				int number = ghostExtraNumbers != null && i < ghostExtraNumbers.length
						? ghostExtraNumbers[i] : 0;
				float indexW = number > 0 ? ghostIndexWidth(p, number) : 0f;
				float w = widths[i];
				if (w > avail) {
					int fits = p.breakText(item, true, avail - indexW, null);
					item = fits > 1 ? item.substring(0, fits - 1) + "…" : "…";
					w = indexW + p.measureText(item);
				}
				float itemX = pack.xs[i];
				float top;
				float baseline;
				if (belowField && row > 0) {
					top = GhostExtraLayout.belowFieldRowTop(row, firstBelow,
							lineHeight);
					baseline = top + lineHeight - descent;
				} else {
					top = GhostExtraLayout.rowTop(row, ghostBaseline, contentTop,
							lineHeight, descent);
					baseline = GhostExtraLayout.rowBaseline(row, ghostBaseline,
							contentTop, lineHeight, descent);
				}
				if (ghostSplit && prev >= 0 && pack.rows[prev] == row) {
					drawGhostDivider(canvas, p, pack.xs[i] - gap * 0.5f, baseline);
				} else if (splitAfterGhost && prev < 0 && row == 0) {
					drawGhostDivider(canvas, p, pack.xs[i] - gap * 0.5f, baseline);
				}
				prev = i;
				if (number > 0) {
					drawGhostIndex(canvas, p, String.valueOf(number), itemX, baseline);
					itemX += indexW;
				}
				canvas.drawText(item, itemX, baseline, p);
				ghostLastDrawnX = itemX + p.measureText(item);
				ghostLastDrawnBaseline = baseline;
				if (i < ghostExtraRects.length) {
					ghostExtraRects[i] = new android.graphics.RectF(
							originX + pack.xs[i], originY + top,
							originX + pack.xs[i] + w, originY + top + lineHeight);
				}
			}
		}
		if (belowField) {
			return 0;
		}
		return GhostExtraLayout.paddingRows(pack.maxRow, ghostBaseline, contentTop,
				lineHeight, descent);
	}

	/**
	 * What a touch landed on: -1 nothing, 0 the inline ghost, 1+i an extra line.
	 */
	private int ghostHitIndex(final float x, final float y) {
		for (int i = 0; i < ghostRectCount; i++) {
			if (ghostRects[i].contains(x, y)) {
				return 0;
			}
		}
		for (int i = 0; i < ghostExtraRects.length; i++) {
			if (ghostExtraRects[i] != null && ghostExtraRects[i].contains(x, y)) {
				return 1 + i;
			}
		}
		return -1;
	}

	private boolean hitsGhost(final float x, final float y) {
		return ghostHitIndex(x, y) >= 0;
	}

	/** The word the touch at this point would insert, or null. */
	private String ghostWordAt(final float x, final float y) {
		int hit = ghostHitIndex(x, y);
		if (hit < 0) {
			return null;
		}
		if (hit == 0) {
			return ghostWord;
		}
		int i = hit - 1;
		return ghostExtraWords != null && i < ghostExtraWords.length
				? ghostExtraWords[i] : null;
	}

	@Override
	protected void onAnimationEnd() {
		Log.e("BET","IN THE ANIMATION END LISTENER");
		super.onAnimationEnd();
		if(listener != null) {
			listener.onAnimationEnd();
		}
	}
	
	
	public void setListener(AnimationEndListener listener) {
		this.listener = listener;
	}

	public AnimationEndListener getListener() {
		return listener;
	}

	private AnimationEndListener listener = null;
	
	public interface AnimationEndListener {
		public void onAnimationEnd();
	}
	
	
	@Override
	public boolean onKeyPreIme(int keyCode, KeyEvent event)
    {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK)
        {
            if(mListener != null) {
            	mListener.onBackPressed();
            }
        }
        return super.onKeyPreIme(keyCode, event);
    }
	
	BackPressedListener mListener;
	
	public void setOnBackPressedListener(BackPressedListener l) {
		mListener = l;
	}
	
	public interface BackPressedListener {
		public void onBackPressed();
	}
	//protected boolean getDefaultEditable() {
	//	return true;
	//}
	
	//public Editable getText() {
	//	return (Editable)super.getText();
	//}
}

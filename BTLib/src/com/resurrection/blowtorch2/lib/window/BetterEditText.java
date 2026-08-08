package com.resurrection.blowtorch2.lib.window;

import javax.security.auth.PrivateCredentialPermission;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.Spanned;
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
		return connection;
	}

	/** Keep Autofill away from VARIATION_PASSWORD, but still stop the IME learning
	 * typed text when suggestions are off (telnet password mask, or the option). */
	private void applySuggestionPolicy(EditorInfo attrs) {
		if (allowSuggestions) {
			attrs.inputType &= ~InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
		} else {
			attrs.inputType |= InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			attrs.imeOptions |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
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
		// Persist on the view so super.onCreateInputConnection (Extract UI /
		// Compatibility) copies IME_FLAG_NO_PERSONALIZED_LEARNING from getImeOptions().
		int ime = getImeOptions();
		if (allowSuggestions) {
			ime &= ~EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
		} else {
			ime |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
		}
		setImeOptions(ime);
	}

	public boolean getAllowSuggestions() {
		return allowSuggestions;
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

		/**
		 * Hold the ghost to see the next suggestion instead.
		 *
		 * <p>The ghost can only ever show one, and without a bar of chips the
		 * others are unreachable: {@code .suggest 2} cannot be typed into the
		 * input bar, it can only be put on a button. A player who works from the
		 * ghost alone had one suggestion and no way to the rest.
		 */
		void onGhostHeld();
	}

	private GhostTapListener ghostTapListener = null;

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

	/** Set once a hold has fired, so the release is not also read as a tap. */
	private boolean ghostHeld = false;

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

	private final android.graphics.RectF[] ghostExtraRects =
			new android.graphics.RectF[MAX_GHOST_EXTRAS];

	/** The bottom padding this field had before any room was reserved. */
	private int ghostBasePaddingBottom = -1;

	/** Most rows the bar may grow to; 0 turns the listing off. */
	private int ghostMaxRows = 0;

	/** Rows the bar is currently tall enough for. */
	private int ghostRowsShown = -1;

	/** Suggestions there was no room to show, counted for the +N mark. */
	private int ghostHiddenCount = 0;

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

	/**
	 * Show a suggestion after the caret, in dimmed type.
	 *
	 * <p><b>Drawn, never inserted.</b> Putting the ghost in the Editable with a
	 * span would mean every TextWatcher on this field sees it — including the
	 * Keep Last one — {@code wordBefore} would start completing the ghost itself,
	 * and one missed strip on send would put a word the player never typed on the
	 * wire. Drawing it keeps the text exactly what was typed, so there is nothing
	 * to strip and nothing to get wrong.
	 *
	 * <p>The cost is still that it takes part in no measurement: the ghost never
	 * makes the bar taller or wider. What it does now, rather than give up, is
	 * use the room that is there — the rest of the line, then the next line if
	 * the view already has one, and failing both an ellipsis. Something dimmed is
	 * always visible, which is what makes it tappable.
	 *
	 * @param drawn what to show after the caret; null or empty clears the ghost.
	 * @param word the whole completion a tap should insert. When the suggestion
	 *        continues what was typed this is {@code typed + drawn}; for a
	 *        forgiven typo the letters differ, which is exactly why the tap
	 *        carries the word instead of re-deriving it from what is on screen.
	 * @param number which suggestion this is, counting from 1; 0 draws no marker.
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
		invalidate();
	}

	public String getGhostCompletion() {
		return ghostText;
	}

	@Override
	protected void onSelectionChanged(final int start, final int end) {
		super.onSelectionChanged(start, end);
		// Moving the caret off the end takes the ghost away, and the rows held
		// for its suggestions have to go with it. Nothing else asks again.
		if (ghostMaxRows > 0) {
			applyGhostRowPadding(rowsNeeded());
		}
	}

	/**
	 * Suggestions to show under the line being typed, growing the bar for them.
	 *
	 * <p>The inline ghost is one word by construction. This is the other answer
	 * to that, for a player who works without a bar of chips: the field gets
	 * taller and the rest are listed under what they are typing, each one
	 * tappable. It costs screen — that is the trade, and it is why this is off
	 * unless asked for.
	 *
	 * <p>Room is made with bottom padding rather than by putting the words into
	 * the text. Text is what gets sent; a suggestion must never be able to
	 * become part of the command by an oversight somewhere else.
	 *
	 * @param lines what to draw, or null for none.
	 * @param words what each line inserts; same length as {@code lines}.
	 */
	public void setGhostExtras(final String[] lines, final String[] words) {
		int now = lines == null ? 0 : Math.min(lines.length, MAX_GHOST_EXTRAS);
		ghostExtras = now == 0 ? null : java.util.Arrays.copyOf(lines, now);
		ghostExtraWords = now == 0 ? null : java.util.Arrays.copyOf(words, now);
		// The bar takes exactly the rows this many suggestions need at this
		// width, and gives them back when they go — which is what makes it
		// shrink again the moment a command is sent. The count is worked out
		// here rather than while drawing, because making room is a layout and a
		// layout must not happen inside onDraw.
		int need = rowsNeeded();
		android.util.Log.e("BTPROF", "[ghost] setExtras n=" + now
				+ " maxRows=" + ghostMaxRows + " rowsNeeded=" + need
				+ " wouldDraw=" + ghostWouldDraw() + " width=" + ghostRowWidth()
				+ " estEnd=" + estimateGhostEndX());
		applyGhostRowPadding(need);
		invalidate();
	}

	/**
	 * Most rows of suggestions the bar may grow to, on top of the typed line.
	 *
	 * @param rows the ceiling; 0 turns the listing off.
	 */
	public void setGhostMaxRows(final int rows) {
		int want = rows < 0 ? 0 : Math.min(rows, MAX_GHOST_ROWS);
		if (want == ghostMaxRows) {
			return;
		}
		ghostMaxRows = want;
		applyGhostRowPadding(rowsNeeded());
		invalidate();
	}

	/** Width one row of suggestions has to lay out in. */
	private float ghostRowWidth() {
		return getWidth() - getTotalPaddingLeft() - getTotalPaddingRight();
	}

	/** The gap between two suggestions sitting side by side. */
	private float ghostGap() {
		return getPaint().measureText("  ");
	}

	/**
	 * How many rows below the typed line the suggestions need.
	 *
	 * <p>Worked out by the same routine that draws them, so the height the bar
	 * takes and the words it shows can never disagree. Not done while drawing:
	 * making room is a layout.
	 */
	private int rowsNeeded() {
		if (ghostExtras == null || ghostExtras.length == 0 || ghostMaxRows <= 0
				|| !ghostWouldDraw()) {
			return 0;
		}
		float avail = ghostRowWidth();
		if (avail <= 0) {
			// Not laid out yet. One row is the honest guess, corrected the next
			// time anything changes.
			return 1;
		}
		return packGhostExtras(null, avail, estimateGhostEndX(), 0f, 0f, 0f, 0f, 0f);
	}

	/**
	 * Would the ghost be drawn at all right now?
	 *
	 * <p>Room must not be held for something that is not going to appear. The
	 * ghost is drawn only with the caret at the very end of the text — put it
	 * back into the middle of the line and the ghost goes, so the rows have to
	 * go with it or the bar stays tall around nothing.
	 */
	private boolean ghostWouldDraw() {
		if (ghostText == null || getText() == null) {
			return false;
		}
		int at = getSelectionStart();
		return at >= 0 && at == getText().length() && at == getSelectionEnd();
	}

	/**
	 * Where the inline ghost is likely to end, without waiting for a draw.
	 *
	 * <p>The suggestions carry on from there rather than starting on a fresh
	 * line, so how much room the first of them has depends on it — and that
	 * decides how tall the bar must be, which is a layout and cannot wait for
	 * drawing. Measured from the text rather than remembered from the last
	 * frame, which would size the bar for the line before this one.
	 */
	private float estimateGhostEndX() {
		CharSequence text = getText();
		String line = text == null ? "" : text.toString();
		int nl = line.lastIndexOf('\n');
		if (nl >= 0) {
			line = line.substring(nl + 1);
		}
		return getPaint().measureText(line)
				+ (ghostText == null ? 0 : getPaint().measureText(ghostText));
	}

	/** Make the bar exactly tall enough for {@code rows} of suggestions. */
	private void applyGhostRowPadding(final int rows) {
		if (ghostBasePaddingBottom < 0) {
			ghostBasePaddingBottom = getPaddingBottom();
		}
		if (rows == ghostRowsShown) {
			return;
		}
		ghostRowsShown = rows;
		int lineHeight = Math.round(getPaint().getFontSpacing());
		setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(),
				ghostBasePaddingBottom + rows * lineHeight);
	}

	/** The word a tap on the ghost would insert; null when there is no ghost. */
	public String getGhostWord() {
		return ghostWord;
	}

	@Override
	protected void onDraw(android.graphics.Canvas canvas) {
		super.onDraw(canvas);
		ghostRectCount = 0;
		if (ghostText == null) {
			return;
		}
		android.text.Layout layout = getLayout();
		if (layout == null || getText() == null) {
			return;
		}
		int at = getSelectionStart();
		// Only at the very end. Mid-line the ghost would sit on top of the text
		// that follows it, which reads as corruption rather than a suggestion.
		if (at < 0 || at != getText().length() || at != getSelectionEnd()) {
			return;
		}
		if (ghostPaint == null) {
			ghostPaint = new android.text.TextPaint();
		}
		ghostPaint.set(getPaint());
		ghostPaint.setColor((getCurrentTextColor() & 0x00FFFFFF) | 0x70000000);
		int line = layout.getLineForOffset(at);
		float x = layout.getPrimaryHorizontal(at);
		float lineWidth = getWidth() - getTotalPaddingLeft() - getTotalPaddingRight();
		float room = lineWidth - x;
		float ghostWidth = ghostPaint.measureText(ghostText);

		// The content origin. The scroll offsets matter once the bar has more
		// lines than it is tall: TextView scrolls its own layout, and a ghost
		// drawn without them lands under the visible text or off the view.
		final float originX = getTotalPaddingLeft() - getScrollX();
		final float originY = getTotalPaddingTop() - getScrollY();
		canvas.save();
		canvas.translate(originX, originY);
		float baseline = layout.getLineBaseline(line);
		float top = layout.getLineTop(line);
		float bottom = layout.getLineBottom(line);
		float lineHeight = bottom - top;
		float endX;
		float endBaseline;

		if (ghostWidth <= room) {
			canvas.drawText(ghostText, x, baseline, ghostPaint);
			addGhostRect(originX + x, originY + top, originX + x + ghostWidth,
					originY + bottom);
			endX = x + ghostWidth;
			endBaseline = baseline;
		} else {
			int fits = ghostPaint.breakText(ghostText, true, room, null);
			// A next line to continue on only exists if the view is already tall
			// enough for one. The ghost never adds height — that would mean
			// putting it in the text, which is the path this deliberately avoids.
			boolean hasNextLine =
					bottom + lineHeight <= layout.getHeight()
					|| bottom + lineHeight <= getHeight() - getTotalPaddingTop()
							- getTotalPaddingBottom();
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
				// No second line to use: show as much as the line holds and mark
				// it cut. Silently drawing nothing was the old behaviour, and it
				// read as "the ghost is broken".
				String cut = fits > 1 ? ghostText.substring(0, fits - 1) + "…" : "…";
				float cutWidth = ghostPaint.measureText(cut);
				canvas.drawText(cut, x, baseline, ghostPaint);
				addGhostRect(originX + x, originY + top, originX + x + cutWidth,
						originY + bottom);
				endX = x + cutWidth;
				endBaseline = baseline;
			}
		}

		android.util.Log.e("BTPROF", "[ghost] onDraw extras="
				+ (ghostExtras == null ? -1 : ghostExtras.length)
				+ " maxRows=" + ghostMaxRows);
		float extrasEndX = endX;
		float extrasBaseline = endBaseline;
		if (ghostExtras != null && ghostExtras.length > 0 && ghostMaxRows > 0) {
			for (int i = 0; i < ghostExtraRects.length; i++) {
				ghostExtraRects[i] = null;
			}
			float below = layout.getLineBottom(layout.getLineCount() - 1);
			// The ghost's end, with no gap added here: the packer puts the gap in
			// itself. Measuring with one value and drawing with another is how
			// the bar ends up a row short of what it shows, which is the whole
			// reason one routine does both.
			int rows = packGhostExtras(canvas, lineWidth, endX, below, endBaseline,
					ghostPaint.getFontSpacing(), originX, originY);
			android.util.Log.e("BTPROF", "[ghost] drew rows=" + rows
					+ " hidden=" + ghostHiddenCount + " endX=" + endX
					+ " lineWidth=" + lineWidth + " lastX=" + ghostLastDrawnX
					+ " padBottom=" + getPaddingBottom() + " h=" + getHeight());
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

		if (ghostNumber > 0) {
			// A micro digit above the ghost, so you can see which suggestion it is
			// and reach it with .complete N without looking down at the strip.
			android.text.TextPaint mark = new android.text.TextPaint(ghostPaint);
			mark.setTextSize(ghostPaint.getTextSize() * 0.55f);
			canvas.drawText(String.valueOf(ghostNumber), endX + 2,
					endBaseline - ghostPaint.getTextSize() * 0.45f, mark);
		}
		canvas.restore();
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
		if (ghostText != null && ghostWord != null && ghostTapListener != null) {
			switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				if (hitsGhost(event.getX(), event.getY())) {
					ghostTouchDown = true;
					startGhostHold();
					return true;
				}
				break;
			case MotionEvent.ACTION_UP:
				if (ghostTouchDown) {
					ghostTouchDown = false;
					cancelGhostHold();
					String word = ghostWordAt(event.getX(), event.getY());
					// A release after a hold is the end of the hold, not a tap.
					// Without this, holding to see the next suggestion would then
					// insert it as well, which is the opposite of looking.
					if (!ghostHeld && word != null) {
						ghostTapListener.onGhostTapped(word);
					}
					ghostHeld = false;
					return true;
				}
				break;
			case MotionEvent.ACTION_MOVE:
				if (ghostTouchDown && !hitsGhost(event.getX(), event.getY())) {
					// Wandered off it. Still ours until the finger lifts, but no
					// longer a hold on the thing it went down on.
					cancelGhostHold();
					return true;
				}
				break;
			case MotionEvent.ACTION_CANCEL:
				if (ghostTouchDown) {
					ghostTouchDown = false;
					cancelGhostHold();
					ghostHeld = false;
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
		return super.onTouchEvent(event);
	}

	/** Start counting towards a hold on the ghost. */
	private void startGhostHold() {
		cancelGhostHold();
		ghostHeld = false;
		ghostHoldRunnable = new Runnable() {
			@Override
			public void run() {
				ghostHoldRunnable = null;
				if (!ghostTouchDown || ghostTapListener == null) {
					return;
				}
				ghostHeld = true;
				performHapticFeedback(
						android.view.HapticFeedbackConstants.LONG_PRESS);
				ghostTapListener.onGhostHeld();
			}
		};
		postDelayed(ghostHoldRunnable,
				android.view.ViewConfiguration.getLongPressTimeout());
	}

	private void cancelGhostHold() {
		if (ghostHoldRunnable != null) {
			removeCallbacks(ghostHoldRunnable);
			ghostHoldRunnable = null;
		}
	}

	/**
	 * Lay the other suggestions out, and draw them when there is a canvas.
	 *
	 * <p>One routine for both jobs on purpose: measuring in one place and
	 * drawing in another is how a bar ends up a row short of what it shows.
	 *
	 * <p>They carry on from where the inline ghost ended rather than starting
	 * underneath it. The rest of that line is the cheapest space on the screen,
	 * and beginning below it spent a whole row on white space.
	 *
	 * @param canvas null to measure only.
	 * @param avail width of a full row.
	 * @param startX where the first one begins, past the ghost.
	 * @param below top of the first row under the text block.
	 * @param ghostBaseline baseline of the line the ghost is on.
	 * @param lineHeight one line of ghost type.
	 * @param originX left of the content, for hit rectangles.
	 * @param originY top of the content, for hit rectangles.
	 * @return rows used <em>below</em> the typed line.
	 */
	private int packGhostExtras(final android.graphics.Canvas canvas, final float avail,
			final float startX, final float below, final float ghostBaseline,
			final float lineHeight, final float originX, final float originY) {
		final float originYUsed = originY;
		ghostHiddenCount = 0;
		if (canvas != null) {
			ghostLastDrawnX = -1;
		}
		android.text.TextPaint p = canvas != null && ghostPaint != null
				? ghostPaint : getPaint();
		float gap = p.measureText("  ");
		int row = 0;
		float x = startX;
		for (int i = 0; i < ghostExtras.length; i++) {
			String item = ghostExtras[i];
			if (item == null) {
				continue;
			}
			float w = p.measureText(item);
			float lead = x > 0 ? gap : 0;
			if (x + lead + w > avail) {
				if (row + 1 > ghostMaxRows) {
					// Out of rows. What is left is counted, not dropped in
					// silence — that count is the only thing telling the player
					// there is more.
					ghostHiddenCount = countRemaining(i);
					break;
				}
				row++;
				x = 0;
				lead = 0;
				if (w > avail) {
					// Wider than the whole bar. Cut it rather than run off.
					int fits = p.breakText(item, true, avail, null);
					item = fits > 1 ? item.substring(0, fits - 1) + "…" : "…";
					w = p.measureText(item);
				}
			}
			if (canvas != null) {
				float itemX = x + lead;
				float top = row == 0
						? ghostBaseline - lineHeight + p.descent()
						: below + (row - 1) * lineHeight;
				float baseline = row == 0
						? ghostBaseline
						: top + lineHeight - p.descent();
				canvas.drawText(item, itemX, baseline, p);
				ghostLastDrawnX = itemX + w;
				ghostLastDrawnBaseline = baseline;
				if (i < ghostExtraRects.length) {
					ghostExtraRects[i] = new android.graphics.RectF(
							originX + itemX, originYUsed + top,
							originX + itemX + w, originYUsed + top + lineHeight);
				}
			}
			x += lead + w;
		}
		return row;
	}

	/** How many suggestions from this one onwards were not shown. */
	private int countRemaining(final int from) {
		int n = 0;
		for (int i = from; i < ghostExtras.length; i++) {
			if (ghostExtras[i] != null) {
				n++;
			}
		}
		return n;
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

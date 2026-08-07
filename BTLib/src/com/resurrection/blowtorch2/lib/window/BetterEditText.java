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
					return true;
				}
				break;
			case MotionEvent.ACTION_UP:
				if (ghostTouchDown) {
					ghostTouchDown = false;
					String word = ghostWord;
					if (hitsGhost(event.getX(), event.getY())) {
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
		return super.onTouchEvent(event);
	}

	private boolean hitsGhost(final float x, final float y) {
		for (int i = 0; i < ghostRectCount; i++) {
			if (ghostRects[i].contains(x, y)) {
				return true;
			}
		}
		return false;
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

package com.resurrection.blowtorch2.lib.window;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.util.SessionLogSearch;
import com.resurrection.blowtorch2.lib.util.SessionLogger;

/**
 * In-app browser for this world's {@code session_logs} files. List first, then
 * a paged TextView — never the whole 8 MB file as one String on the UI thread.
 */
public class LogHistoryDialog extends Dialog {

	private static final int PAGE_LINES = 300;
	private static final int HIGHLIGHT_LINE = 0x66FFCC00;
	private static final int HIGHLIGHT_QUERY = 0x4433AADD;
	private static final String HINT_LIST = "Filter names";
	private static final String HINT_HITS = "Find in logs";
	private static final String HINT_FILE = "Find in file";

	private final String mDisplay;
	private final Handler mMain = new Handler(Looper.getMainLooper());

	private TextView mTitle;
	private TextView mSubtitle;
	private ListView mList;
	private TextView mEmpty;
	private ScrollView mFileScroll;
	private TextView mFileText;
	private LinearLayout mFileFooter;
	private Button mOlder;
	private Button mNewer;
	private Button mBack;

	private final ArrayList<File> mFiles = new ArrayList<File>();
	private FileAdapter mAdapter;

	private volatile boolean mAlive = true;
	private int mListGen;
	private volatile int mSearchGen;
	private int mPageGen;

	private File mOpenFile;
	private LineIndex mIndex;
	private int mPageStart;
	private int mHighlightLine = -1;
	private String mQuery = "";
	private boolean mCaseSensitive;
	private File mPendingFile;
	private int mPendingLine = -1;
	private LinearLayout mFilterBar;
	private LinearLayout mListFilters;
	private TextView mFolderView;
	private TextView mFromBtn;
	private TextView mToBtn;
	private Button mLoadBtn;
	private EditText mSearch;
	private Button mClearBtn;
	private Button mSearchBtn;
	private LinearLayout mFindRow;
	private Button mFindPrev;
	private Button mFindNext;
	private TextView mFindCount;
	private boolean mSearchBusy;
	private String mSearchStatus = "";
	private boolean mHitsMode;
	private final ArrayList<SessionLogSearch.FileMatch> mHitFiles =
			new ArrayList<SessionLogSearch.FileMatch>();
	private SessionLogSearch.LineHits mFileHits;
	private int mFileHitAt = -1;
	private boolean mFreezeNameFilter;
	private boolean mFindOnOpen;
	private File mLookDir;
	private Long mFromMs;
	private Long mUntilExclusiveMs;
	private final ArrayList<File> mLoaded = new ArrayList<File>();
	private String mNameFilter = "";

	public LogHistoryDialog(Context context, String display) {
		this(context, display, null, -1, null, false);
	}

	public LogHistoryDialog(Context context, String display, File openAt,
			int lineIndex, String query, boolean caseSensitive) {
		super(context, R.style.BlowTorch_Dialog);
		mDisplay = display == null ? "" : display;
		mPendingFile = openAt;
		mPendingLine = lineIndex;
		mQuery = query == null ? "" : query;
		mCaseSensitive = caseSensitive;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);

		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (10 * density + 0.5f);

		LinearLayout root = new LinearLayout(getContext());
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(color(R.color.chrome_body));

		mTitle = chromeTitle("Session logs", density);
		root.addView(mTitle);

		mSubtitle = new TextView(getContext());
		mSubtitle.setTextColor(color(R.color.chrome_description));
		mSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
		mSubtitle.setPadding(pad, pad / 2, pad, pad / 2);
		mSubtitle.setText(idleHint());
		root.addView(mSubtitle);

		mFilterBar = buildFilterBar(density, pad);
		root.addView(mFilterBar);

		mEmpty = new TextView(getContext());
		mEmpty.setTextColor(color(R.color.chrome_description));
		mEmpty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
		mEmpty.setPadding(pad, pad, pad, pad);
		mEmpty.setText(idleHint());
		mEmpty.setVisibility(View.VISIBLE);

		mList = new ListView(getContext());
		mAdapter = new FileAdapter();
		mList.setAdapter(mAdapter);
		mList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (position < 0 || position >= mFiles.size()) {
					return;
				}
				File f = mFiles.get(position);
				int jump = -1;
				mFindOnOpen = false;
				if (mHitsMode) {
					SessionLogSearch.FileMatch hit = hitFor(f);
					if (hit != null) {
						jump = hit.firstLine;
						mFindOnOpen = true;
					}
				}
				openFile(f, jump);
			}
		});

		mFileText = new TextView(getContext());
		mFileText.setTypeface(Typeface.MONOSPACE);
		mFileText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
		mFileText.setTextColor(color(R.color.chrome_title_text));
		mFileText.setTextIsSelectable(true);
		mFileText.setPadding(pad, pad / 2, pad, pad / 2 + (int) (56 * density + 0.5f));
		mFileScroll = new ScrollView(getContext());
		mFileScroll.addView(mFileText, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		mFileScroll.setVisibility(View.GONE);

		FrameLayout body = new FrameLayout(getContext());
		FrameLayout.LayoutParams fill = new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT);
		body.addView(mEmpty, fill);
		body.addView(mList, fill);
		body.addView(mFileScroll, fill);
		root.addView(body, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

		mFileFooter = chromeFooter(density);
		mBack = chromeFooterButton("Back");
		mBack.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				leaveOpenFileToList();
			}
		});
		mOlder = chromeFooterButton("Older");
		mOlder.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				turnPage(-1);
			}
		});
		mNewer = chromeFooterButton("Newer");
		mNewer.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				turnPage(1);
			}
		});
		Button close = chromeFooterButton("Close");
		close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
		LinearLayout.LayoutParams lp = footerButtonParams(density);
		mFileFooter.addView(mBack, lp);
		LinearLayout.LayoutParams gap = footerButtonParams(density);
		gap.leftMargin = (int) (4 * density + 0.5f);
		mFileFooter.addView(mOlder, gap);
		mFileFooter.addView(mNewer, gap);
		mFileFooter.addView(close, gap);
		LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		root.addView(mFileFooter, footerLp);

		root.setLayoutParams(new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		setContentView(root);

		Window window = getWindow();
		if (window != null) {
			int width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.94f);
			int height = (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.88f);
			window.setLayout(width, height);
			window.setGravity(Gravity.CENTER);
			window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
					| WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		}

		showListWidgets();
		applySevenDays();
		paintDateChips();
		Context app = getContext().getApplicationContext();
		mLookDir = SessionLogger.getLogDirectory(app);
		paintFolder();
		if (mPendingFile != null) {
			if (mSearch != null && mQuery.length() > 0) {
				mSearch.setText(mQuery);
				mFreezeNameFilter = true;
			}
			mFindOnOpen = mQuery.length() > 0;
			openFile(mPendingFile, mPendingLine);
			mPendingFile = null;
			mPendingLine = -1;
		}
	}

	@Override
	public void dismiss() {
		mAlive = false;
		mListGen++;
		mSearchGen++;
		mPageGen++;
		super.dismiss();
	}

	@Override
	public void onBackPressed() {
		if (mOpenFile != null) {
			leaveOpenFileToList();
			return;
		}
		if (mHitsMode || boxHasText()) {
			clearSearchOnList();
			return;
		}
		super.onBackPressed();
	}

	/** Return to the file list (from a search jump, or Back). */
	public void showListScreen() {
		hideIme();
		abandonInFlightSearch();
		if (mOpenFile != null) {
			mFreezeNameFilter = boxHasText();
		}
		mPageGen++;
		mOpenFile = null;
		mIndex = null;
		mHighlightLine = -1;
		mFileHits = null;
		mFileHitAt = -1;
		mSearchStatus = "";
		mQuery = "";
		mFindOnOpen = false;
		showListWidgets();
		mTitle.setText("Session logs");
		if (mHitsMode) {
			paintHitsList();
		} else if (mLoaded.isEmpty()) {
			mSubtitle.setText(idleHint());
			mEmpty.setText(idleHint());
		} else {
			refreshListFromLoaded();
			mSubtitle.setText(listSummary());
		}
		paintSearchChrome();
	}

	/**
	 * File → list. Keeps the box; freezes the name filter so {@code goblin}
	 * does not collapse the Load list to filenames. ✕, footer Back, and
	 * hardware Back share this.
	 */
	private void leaveOpenFileToList() {
		showListScreen();
	}

	/**
	 * Jump to a match. Safe to call while the dialog is already showing.
	 */
	public void openFileAtLine(File file, int lineIndex, String query, boolean caseSensitive) {
		mQuery = query == null ? "" : query;
		mCaseSensitive = caseSensitive;
		if (mSearch != null && mQuery.length() > 0) {
			mSearch.setText(mQuery);
			mFreezeNameFilter = true;
		}
		mFindOnOpen = mQuery.length() > 0;
		if (file == null) {
			showListScreen();
			return;
		}
		openFile(file, lineIndex);
	}

	private void loadFileList() {
		final int gen = ++mListGen;
		mHitsMode = false;
		mHitFiles.clear();
		mFreezeNameFilter = false;
		abandonInFlightSearch();
		final File dir = mLookDir;
		final String display = mDisplay;
		final Long from = mFromMs;
		final Long until = mUntilExclusiveMs;
		final String folderLabel = dir == null ? "" : dir.getAbsolutePath();
		mSubtitle.setText("There may be thousands of session logs in this folder. "
				+ "Loading can take a while…\n" + folderLabel);
		mEmpty.setText("Loading…");
		mEmpty.setVisibility(View.VISIBLE);
		mList.setVisibility(View.GONE);
		if (mLoadBtn != null) {
			mLoadBtn.setEnabled(false);
		}
		SessionLogSearch.runIo(new Runnable() {
			@Override
			public void run() {
				final List<File> files = SessionLogger.listLogFiles(dir, display,
						from, until);
				mMain.post(new Runnable() {
					@Override
					public void run() {
						if (!mAlive || gen != mListGen) {
							return;
						}
						if (mLoadBtn != null) {
							mLoadBtn.setEnabled(true);
						}
						mLoaded.clear();
						mLoaded.addAll(files);
						applyNameFilter();
						if (mOpenFile == null) {
							if (mFiles.isEmpty()) {
								mEmpty.setText(emptyHint());
								mEmpty.setVisibility(View.VISIBLE);
								mList.setVisibility(View.GONE);
								mSubtitle.setText(emptyHint());
							} else {
								mEmpty.setVisibility(View.GONE);
								mList.setVisibility(View.VISIBLE);
								mSubtitle.setText(listSummary());
							}
						}
					}
				});
			}
		});
	}

	private String idleHint() {
		return "Choose dates, tap Load. A big folder can take a while.";
	}

	private String emptyHint() {
		String folder = mLookDir == null ? "/BlowTorch/session_logs/"
				: mLookDir.getAbsolutePath();
		return "No logs for " + mDisplay + " in this range.\n" + folder;
	}

	private String listSummary() {
		String folder = mLookDir == null ? "" : mLookDir.getAbsolutePath();
		String range = dateRangeLabel();
		int shown = mFiles.size();
		int loaded = mLoaded.size();
		String count = shown + " file" + (shown == 1 ? "" : "s");
		if (!mHitsMode && !mFreezeNameFilter && mNameFilter.length() > 0
				&& shown != loaded) {
			count = shown + " of " + loaded + " files matching \"" + mNameFilter + "\"";
		}
		return count + " for " + mDisplay + " (" + range + ").\n" + folder;
	}

	private String dateRangeLabel() {
		if (mFromMs == null && mUntilExclusiveMs == null) {
			return "all dates";
		}
		String from = mFromMs == null ? "…" : dayLabel(mFromMs.longValue());
		String to = mUntilExclusiveMs == null ? "…"
				: dayLabel(mUntilExclusiveMs.longValue() - 1L);
		return from + " → " + to;
	}

	private void applyNameFilter() {
		mFiles.clear();
		String q = mNameFilter == null ? "" : mNameFilter.trim().toLowerCase(Locale.US);
		for (int i = 0; i < mLoaded.size(); i++) {
			File f = mLoaded.get(i);
			if (q.length() == 0
					|| f.getName().toLowerCase(Locale.US).contains(q)) {
				mFiles.add(f);
			}
		}
		if (mAdapter != null) {
			mAdapter.notifyDataSetChanged();
		}
	}

	private void refreshListFromLoaded() {
		if (mFreezeNameFilter) {
			mFiles.clear();
			mFiles.addAll(mLoaded);
			if (mAdapter != null) {
				mAdapter.notifyDataSetChanged();
			}
		} else {
			applyNameFilter();
		}
	}

	private void paintHitsList() {
		mFiles.clear();
		for (int i = 0; i < mHitFiles.size(); i++) {
			SessionLogSearch.FileMatch m = mHitFiles.get(i);
			if (m != null && m.file != null) {
				mFiles.add(m.file);
			}
		}
		if (mAdapter != null) {
			mAdapter.notifyDataSetChanged();
		}
		String q = boxText();
		String sub = mHitFiles.size() + " file"
				+ (mHitFiles.size() == 1 ? "" : "s") + " with \"" + q + "\"";
		if (mHitFiles.size() > 0) {
			sub = sub + " · tap one";
		}
		if (mSearchStatus != null && mSearchStatus.length() > 0) {
			sub = sub + "\n" + mSearchStatus;
		}
		if (mFiles.isEmpty()) {
			mEmpty.setText(sub);
			mEmpty.setVisibility(View.VISIBLE);
			mList.setVisibility(View.GONE);
		} else {
			mEmpty.setVisibility(View.GONE);
			mList.setVisibility(View.VISIBLE);
		}
		mSubtitle.setText(sub);
	}

	private SessionLogSearch.FileMatch hitFor(File file) {
		if (file == null) {
			return null;
		}
		String path = file.getAbsolutePath();
		for (int i = 0; i < mHitFiles.size(); i++) {
			SessionLogSearch.FileMatch m = mHitFiles.get(i);
			if (m != null && m.file != null && path.equals(m.file.getAbsolutePath())) {
				return m;
			}
		}
		return null;
	}

	private String boxText() {
		if (mSearch == null || mSearch.getText() == null) {
			return "";
		}
		return mSearch.getText().toString().trim();
	}

	private boolean boxHasText() {
		return boxText().length() > 0;
	}

	private void setSearchBusy(boolean busy) {
		mSearchBusy = busy;
		if (mSearchBtn != null) {
			mSearchBtn.setEnabled(!busy);
			mSearchBtn.setText(busy ? "…" : "Search");
		}
	}

	private void abandonInFlightSearch() {
		mSearchGen++;
		setSearchBusy(false);
	}

	private void hideIme() {
		if (mSearch == null) {
			return;
		}
		InputMethodManager imm = (InputMethodManager) getContext()
				.getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null) {
			imm.hideSoftInputFromWindow(mSearch.getWindowToken(), 0);
		}
	}

	private void showListWidgets() {
		if (mFilterBar != null) {
			mFilterBar.setVisibility(View.VISIBLE);
		}
		if (mListFilters != null) {
			mListFilters.setVisibility(View.VISIBLE);
		}
		paintSearchChrome();
		mList.setVisibility(mFiles.isEmpty() ? View.GONE : View.VISIBLE);
		mEmpty.setVisibility(mFiles.isEmpty() ? View.VISIBLE : View.GONE);
		mFileScroll.setVisibility(View.GONE);
		if (mBack != null) {
			mBack.setVisibility(View.GONE);
		}
		mOlder.setEnabled(false);
		mNewer.setEnabled(false);
	}

	private void showFileWidgets() {
		if (mFilterBar != null) {
			mFilterBar.setVisibility(View.VISIBLE);
		}
		if (mListFilters != null) {
			mListFilters.setVisibility(View.GONE);
		}
		paintSearchChrome();
		mList.setVisibility(View.GONE);
		mEmpty.setVisibility(View.GONE);
		mFileScroll.setVisibility(View.VISIBLE);
		if (mBack != null) {
			mBack.setVisibility(View.VISIBLE);
			mBack.setEnabled(true);
		}
	}

	private void paintSearchChrome() {
		boolean file = mOpenFile != null;
		boolean hits = mHitsMode && !file;
		if (mSearch != null) {
			String hint = HINT_LIST;
			if (file) {
				hint = HINT_FILE;
			} else if (hits || mFreezeNameFilter) {
				hint = HINT_HITS;
			}
			mSearch.setHint(hint);
		}
		boolean showClear = false;
		if (file) {
			showClear = true;
		} else {
			showClear = boxHasText() || mHitsMode;
		}
		if (mClearBtn != null) {
			mClearBtn.setVisibility(showClear ? View.VISIBLE : View.GONE);
		}
		if (mSearchBtn != null) {
			mSearchBtn.setVisibility(View.VISIBLE);
		}
		int nav = (file && mFileHits != null && mFileHits.size > 0)
				? View.VISIBLE : View.GONE;
		if (mFindRow != null) {
			mFindRow.setVisibility(nav);
		}
		if (mFindPrev != null) {
			mFindPrev.setVisibility(nav);
		}
		if (mFindNext != null) {
			mFindNext.setVisibility(nav);
		}
		if (mFindCount != null) {
			mFindCount.setVisibility(nav);
			if (nav == View.VISIBLE) {
				int n = mFileHits.size;
				int at = mFileHitAt < 0 ? 0 : mFileHitAt + 1;
				mFindCount.setText(at + "/" + n);
			}
		}
		if (mSearchBtn != null) {
			mSearchBtn.setContentDescription(file ? "Find in file" : "Find in logs");
		}
	}

	private void openFile(final File file, final int lineIndex) {
		if (file == null) {
			return;
		}
		mOpenFile = file;
		abandonInFlightSearch();
		mHighlightLine = lineIndex;
		mIndex = null;
		mFileHits = null;
		mFileHitAt = -1;
		showFileWidgets();
		mTitle.setText(file.getName());
		mSubtitle.setText("Reading…");
		mFileText.setText("Reading…");
		final int gen = ++mPageGen;
		SessionLogSearch.runIo(new Runnable() {
			@Override
			public void run() {
				LineIndex index = null;
				String error = null;
				try {
					index = LineIndex.build(file);
				} catch (IOException e) {
					error = e.getMessage();
				}
				final LineIndex built = index;
				final String fail = error;
				mMain.post(new Runnable() {
					@Override
					public void run() {
						if (!mAlive || gen != mPageGen) {
							return;
						}
						if (built == null) {
							mSubtitle.setText("Could not read: "
									+ (fail == null ? file.getAbsolutePath() : fail));
							mFileText.setText("");
							return;
						}
						mIndex = built;
						int jump = lineIndex;
						if (jump < 0 || jump >= built.lineCount()) {
							mPageStart = Math.max(0, built.lineCount() - PAGE_LINES);
							mHighlightLine = -1;
						} else {
							int start = jump - PAGE_LINES / 4;
							if (start < 0) {
								start = 0;
							}
							if (start + PAGE_LINES > built.lineCount()) {
								start = Math.max(0, built.lineCount() - PAGE_LINES);
							}
							mPageStart = start;
							mHighlightLine = jump;
						}
						renderPage();
						if (mFindOnOpen) {
							mFindOnOpen = false;
							int prefer = jump < 0 ? 0 : jump;
							collectOpenFileHits(prefer, 0);
						}
					}
				});
			}
		});
	}

	private void turnPage(int direction) {
		if (mIndex == null) {
			return;
		}
		int next = mPageStart + direction * PAGE_LINES;
		if (next < 0) {
			next = 0;
		}
		int maxStart = Math.max(0, mIndex.lineCount() - PAGE_LINES);
		if (next > maxStart) {
			next = maxStart;
		}
		if (next == mPageStart) {
			return;
		}
		mPageStart = next;
		renderPage();
	}

	private void renderPage() {
		if (mOpenFile == null || mIndex == null) {
			return;
		}
		final File file = mOpenFile;
		final LineIndex index = mIndex;
		final int from = mPageStart;
		final int gen = ++mPageGen;
		SessionLogSearch.runIo(new Runnable() {
			@Override
			public void run() {
				String body;
				try {
					body = index.readPage(file, from, PAGE_LINES);
				} catch (IOException e) {
					body = "Could not read this page: " + e.getMessage();
				}
				final String text = body;
				mMain.post(new Runnable() {
					@Override
					public void run() {
						if (!mAlive || gen != mPageGen || mIndex != index) {
							return;
						}
						int to = Math.min(from + PAGE_LINES, index.lineCount());
						int shownFrom = index.lineCount() == 0 ? 0 : from + 1;
						String page = file.getAbsolutePath() + "\n"
								+ "lines " + shownFrom + "–" + to
								+ " of " + index.lineCount()
								+ "  (" + formatSize(file.length()) + ")";
						if (mSearchStatus != null && mSearchStatus.length() > 0) {
							page = mSearchStatus + "\n" + page;
						}
						mSubtitle.setText(page);
						int rel = (mHighlightLine >= from && mHighlightLine < to)
								? mHighlightLine - from : -1;
						mFileText.setText(highlight(text, rel, mQuery, mCaseSensitive));
						mOlder.setEnabled(from > 0);
						mNewer.setEnabled(to < index.lineCount());
						if (rel >= 0) {
							scrollPageLineIntoView(rel);
						} else {
							mFileScroll.scrollTo(0, 0);
						}
					}
				});
			}
		});
	}

	private CharSequence highlight(String text, int highlightRel, String query,
			boolean caseSensitive) {
		if (text == null) {
			return "";
		}
		SpannableStringBuilder sb = new SpannableStringBuilder(text);
		if (query != null && query.length() > 0) {
			String hay = caseSensitive ? text : text.toLowerCase(Locale.US);
			String needle = caseSensitive ? query : query.toLowerCase(Locale.US);
			int from = 0;
			while (from < hay.length()) {
				int at = hay.indexOf(needle, from);
				if (at < 0) {
					break;
				}
				sb.setSpan(new BackgroundColorSpan(HIGHLIGHT_QUERY), at,
						at + needle.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				from = at + Math.max(needle.length(), 1);
			}
		}
		if (highlightRel >= 0) {
			int start = offsetOfPageLine(text, highlightRel);
			int end = text.indexOf('\n', start);
			if (end < 0) {
				end = text.length();
			}
			if (end > start) {
				sb.setSpan(new BackgroundColorSpan(HIGHLIGHT_LINE), start, end,
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
		}
		return sb;
	}

	/**
	 * Character offset of 0-based page line {@code rel} in {@code text}.
	 * Same walk {@link #highlight} uses, so the yellow line and the scroll
	 * target stay the same line when the TextView wraps.
	 */
	static int offsetOfPageLine(String text, int rel) {
		if (text == null || rel <= 0) {
			return 0;
		}
		int start = 0;
		for (int i = 0; i < rel; i++) {
			int nl = text.indexOf('\n', start);
			if (nl < 0) {
				return text.length();
			}
			start = nl + 1;
		}
		return start;
	}

	/**
	 * Put the highlighted page line near the top of the file pane, not under
	 * Back/Older/Newer/Close. {@link TextView#getLineHeight()} counts wrapped
	 * visual rows wrongly; {@link Layout#getLineForOffset} is the laid-out line.
	 */
	private void scrollPageLineIntoView(final int lineRel) {
		if (mFileScroll == null || mFileText == null || lineRel < 0) {
			return;
		}
		mFileText.post(new Runnable() {
			@Override
			public void run() {
				if (!mAlive || mFileText == null) {
					return;
				}
				Layout layout = mFileText.getLayout();
				CharSequence cs = mFileText.getText();
				String text = cs == null ? "" : cs.toString();
				int y;
				if (layout != null && text.length() > 0) {
					int off = offsetOfPageLine(text, lineRel);
					if (off >= text.length()) {
						off = text.length() - 1;
					}
					int vis = layout.getLineForOffset(off);
					y = layout.getLineTop(vis);
				} else {
					y = lineRel * mFileText.getLineHeight();
				}
				mFileScroll.scrollTo(0, Math.max(0, y - 24));
			}
		});
	}

	private TextView chromeTitle(String text, float density) {
		TextView title = new TextView(getContext());
		title.setText(text);
		title.setTextColor(color(R.color.chrome_title_text));
		title.setBackgroundColor(color(R.color.chrome_title_bar));
		title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setAllCaps(true);
		title.setGravity(Gravity.CENTER);
		title.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, (int) (42 * density + 0.5f)));
		return title;
	}

	private LinearLayout chromeFooter(float density) {
		LinearLayout footer = new LinearLayout(getContext());
		footer.setOrientation(LinearLayout.HORIZONTAL);
		footer.setBackgroundColor(color(R.color.chrome_title_bar));
		int pad = (int) (6 * density + 0.5f);
		footer.setPadding(pad, pad, pad, pad);
		return footer;
	}

	private Button chromeFooterButton(String label) {
		Button b = new Button(getContext());
		b.setText(label);
		b.setMinHeight((int) (44 * getContext().getResources().getDisplayMetrics().density + 0.5f));
		return b;
	}

	private Button searchBarButton(String label, float density) {
		Button b = chromeFooterButton(label);
		b.setMinWidth(0);
		b.setMinimumWidth(0);
		int p = (int) (8 * density + 0.5f);
		b.setPadding(p, 0, p, 0);
		return b;
	}

	private LinearLayout.LayoutParams footerButtonParams(float density) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		lp.height = (int) (44 * density + 0.5f);
		return lp;
	}

	private LinearLayout buildFilterBar(float density, int pad) {
		LinearLayout bar = new LinearLayout(getContext());
		bar.setOrientation(LinearLayout.VERTICAL);
		bar.setPadding(pad, 0, pad, pad / 2);

		mFolderView = new TextView(getContext());
		mFolderView.setTextColor(color(R.color.chrome_description));
		mFolderView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
		mFolderView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				askFolder();
			}
		});
		mListFilters = new LinearLayout(getContext());
		mListFilters.setOrientation(LinearLayout.VERTICAL);
		mListFilters.addView(mFolderView);

		LinearLayout dates = new LinearLayout(getContext());
		dates.setOrientation(LinearLayout.HORIZONTAL);
		dates.setGravity(Gravity.CENTER_VERTICAL);
		mFromBtn = chromeChip("From");
		mFromBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				pickDate(true);
			}
		});
		mToBtn = chromeChip("To");
		mToBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				pickDate(false);
			}
		});
		TextView d7 = chromeChip("7d");
		d7.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				applySevenDays();
				paintDateChips();
				filtersChanged("Last 7 days. Tap Load.");
			}
		});
		TextView all = chromeChip("All");
		all.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mFromMs = null;
				mUntilExclusiveMs = null;
				paintDateChips();
				filtersChanged("All dates. Tap Load — a large folder can take a while.");
			}
		});
		mLoadBtn = chromeFooterButton("Load");
		mLoadBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				loadFileList();
			}
		});
		LinearLayout.LayoutParams chip = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		int gap = (int) (4 * density + 0.5f);
		chip.rightMargin = gap;
		dates.addView(mFromBtn, chip);
		dates.addView(mToBtn, chip);
		dates.addView(d7, chip);
		dates.addView(all, chip);
		LinearLayout.LayoutParams loadLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		dates.addView(mLoadBtn, loadLp);
		mListFilters.addView(dates);
		bar.addView(mListFilters);

		LinearLayout searchRow = new LinearLayout(getContext());
		searchRow.setOrientation(LinearLayout.HORIZONTAL);
		searchRow.setGravity(Gravity.CENTER_VERTICAL);

		mSearch = new EditText(getContext());
		mSearch.setHint(HINT_LIST);
		mSearch.setTextColor(color(R.color.chrome_title_text));
		mSearch.setHintTextColor(color(R.color.chrome_description));
		mSearch.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
		mSearch.setSingleLine(true);
		mSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
		mSearch.setInputType(InputType.TYPE_CLASS_TEXT);
		mSearch.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				if (mSearchBusy) {
					abandonInFlightSearch();
					if (mOpenFile != null && mIndex != null) {
						mSearchStatus = "";
						renderPage();
					} else if (mOpenFile == null && mHitsMode) {
						paintHitsList();
					} else if (mOpenFile == null && !mLoaded.isEmpty()) {
						mSubtitle.setText(listSummary());
					}
				}
				mFreezeNameFilter = false;
				mNameFilter = s == null ? "" : s.toString();
				if (mOpenFile != null) {
					mFileHits = null;
					mFileHitAt = -1;
					paintSearchChrome();
					return;
				}
				if (mHitsMode) {
					paintSearchChrome();
					return;
				}
				if (mLoaded.isEmpty()) {
					paintSearchChrome();
					return;
				}
				applyNameFilter();
				if (mFiles.isEmpty()) {
					mEmpty.setText("No names match \"" + mNameFilter.trim() + "\".");
					mEmpty.setVisibility(View.VISIBLE);
					mList.setVisibility(View.GONE);
				} else {
					mEmpty.setVisibility(View.GONE);
					mList.setVisibility(View.VISIBLE);
				}
				mSubtitle.setText(listSummary());
				paintSearchChrome();
			}
		});
		mSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_SEARCH
						|| (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
								&& event.getAction() == KeyEvent.ACTION_DOWN)) {
					runSearch();
					return true;
				}
				return false;
			}
		});
		LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		searchRow.addView(mSearch, searchLp);

		int navW = (int) (44 * density + 0.5f);
		LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(navW,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		navLp.leftMargin = gap;
		mClearBtn = searchBarButton("✕", density);
		mClearBtn.setContentDescription("Close search");
		mClearBtn.setVisibility(View.GONE);
		mClearBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onClearSearch();
			}
		});
		mSearchBtn = searchBarButton("Search", density);
		mSearchBtn.setContentDescription("Find in logs");
		mSearchBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				runSearch();
			}
		});
		LinearLayout.LayoutParams searchBtnLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		searchBtnLp.leftMargin = gap;
		searchRow.addView(mClearBtn, navLp);
		searchRow.addView(mSearchBtn, searchBtnLp);
		bar.addView(searchRow);

		mFindRow = new LinearLayout(getContext());
		mFindRow.setOrientation(LinearLayout.HORIZONTAL);
		mFindRow.setGravity(Gravity.CENTER);
		mFindRow.setVisibility(View.GONE);
		mFindRow.setPadding(0, gap, 0, 0);
		mFindPrev = searchBarButton("‹", density);
		mFindPrev.setContentDescription("Previous match");
		mFindPrev.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				findInOpenFile(-1);
			}
		});
		mFindCount = new TextView(getContext());
		mFindCount.setTextColor(color(R.color.chrome_title_text));
		mFindCount.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
		mFindCount.setGravity(Gravity.CENTER);
		mFindCount.setMinWidth((int) (64 * density + 0.5f));
		mFindNext = searchBarButton("›", density);
		mFindNext.setContentDescription("Next match");
		mFindNext.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				findInOpenFile(1);
			}
		});
		LinearLayout.LayoutParams findBtnLp = new LinearLayout.LayoutParams(navW,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		LinearLayout.LayoutParams findCountLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		findCountLp.leftMargin = gap;
		findCountLp.rightMargin = gap;
		mFindRow.addView(mFindPrev, findBtnLp);
		mFindRow.addView(mFindCount, findCountLp);
		mFindRow.addView(mFindNext, findBtnLp);
		bar.addView(mFindRow);
		return bar;
	}

	private TextView chromeChip(String label) {
		TextView t = new TextView(getContext());
		t.setText(label);
		t.setTextColor(color(R.color.chrome_title_text));
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
		t.setGravity(Gravity.CENTER);
		int p = (int) (6 * getContext().getResources().getDisplayMetrics().density);
		t.setPadding(p, p, p, p);
		t.setBackgroundColor(0x3322AACC);
		return t;
	}

	/**
	 * Date chips and folder change the query, not the loaded list. Drop the
	 * previous Load so the subtitle cannot name a new range over old files.
	 * Bumping {@code mListGen} abandons an in-flight Load; {@code mSearchGen}
	 * abandons an in-flight content Search that still holds the discarded
	 * file list. Re-enable Load in case that in-flight callback would have
	 * left it disabled.
	 */
	private void filtersChanged(String subtitle) {
		mListGen++;
		mHitsMode = false;
		mHitFiles.clear();
		mFreezeNameFilter = false;
		abandonInFlightSearch();
		if (mLoadBtn != null) {
			mLoadBtn.setEnabled(true);
		}
		mLoaded.clear();
		mFiles.clear();
		if (mAdapter != null) {
			mAdapter.notifyDataSetChanged();
		}
		if (mOpenFile != null) {
			return;
		}
		if (mEmpty != null) {
			mEmpty.setText(idleHint());
			mEmpty.setVisibility(View.VISIBLE);
		}
		if (mList != null) {
			mList.setVisibility(View.GONE);
		}
		if (mSubtitle != null && subtitle != null) {
			mSubtitle.setText(subtitle);
		}
	}

	private void paintFolder() {
		if (mFolderView == null) {
			return;
		}
		String path = mLookDir == null ? "(no folder)" : mLookDir.getAbsolutePath();
		mFolderView.setText("Folder (tap to change): " + path);
	}

	private void paintDateChips() {
		if (mFromBtn != null) {
			mFromBtn.setText(mFromMs == null ? "From" : "From " + dayLabel(mFromMs.longValue()));
		}
		if (mToBtn != null) {
			long to = mUntilExclusiveMs == null ? 0L
					: mUntilExclusiveMs.longValue() - 1L;
			mToBtn.setText(mUntilExclusiveMs == null ? "To" : "To " + dayLabel(to));
		}
	}

	private void applySevenDays() {
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		cal.add(Calendar.DAY_OF_MONTH, 1);
		mUntilExclusiveMs = Long.valueOf(cal.getTimeInMillis());
		cal.add(Calendar.DAY_OF_MONTH, -7);
		mFromMs = Long.valueOf(cal.getTimeInMillis());
	}

	private static String dayLabel(long ms) {
		return new SimpleDateFormat("dd MMM", Locale.US).format(new Date(ms));
	}

	private void pickDate(final boolean from) {
		Calendar cal = Calendar.getInstance();
		if (from && mFromMs != null) {
			cal.setTimeInMillis(mFromMs.longValue());
		} else if (!from && mUntilExclusiveMs != null) {
			cal.setTimeInMillis(mUntilExclusiveMs.longValue() - 1L);
		}
		DatePickerDialog dlg = new DatePickerDialog(getContext(),
				new DatePickerDialog.OnDateSetListener() {
					@Override
					public void onDateSet(DatePicker view, int year, int month, int day) {
						Calendar picked = Calendar.getInstance();
						picked.clear();
						picked.set(Calendar.YEAR, year);
						picked.set(Calendar.MONTH, month);
						picked.set(Calendar.DAY_OF_MONTH, day);
						long start = picked.getTimeInMillis();
						if (from) {
							mFromMs = Long.valueOf(start);
						} else {
							picked.add(Calendar.DAY_OF_MONTH, 1);
							mUntilExclusiveMs = Long.valueOf(picked.getTimeInMillis());
						}
						paintDateChips();
						filtersChanged("Date or folder changed. Tap Load.");
					}
				},
				cal.get(Calendar.YEAR),
				cal.get(Calendar.MONTH),
				cal.get(Calendar.DAY_OF_MONTH));
		dlg.show();
	}

	private void askFolder() {
		final EditText box = new EditText(getContext());
		box.setText(mLookDir == null ? "" : mLookDir.getAbsolutePath());
		box.setHint("/BlowTorch/session_logs/");
		box.setSingleLine(true);
		box.setTextColor(color(R.color.chrome_title_text));
		AlertDialog.Builder b = new AlertDialog.Builder(getContext());
		b.setTitle("Look in folder");
		b.setMessage("Where this dialog lists files. Does not change Options → Session Log Directory (that is where new logs are written). Blank uses the Options folder.");
		b.setView(box);
		b.setPositiveButton("Use this", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String raw = box.getText() == null ? "" : box.getText().toString().trim();
				if (raw.length() == 0) {
					mLookDir = SessionLogger.getLogDirectory(
							getContext().getApplicationContext());
				} else {
					mLookDir = new File(raw);
				}
				paintFolder();
				filtersChanged("Date or folder changed. Tap Load.");
			}
		});
		b.setNeutralButton("Default", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				mLookDir = SessionLogger.getLogDirectory(
						getContext().getApplicationContext());
				paintFolder();
				filtersChanged("Date or folder changed. Tap Load.");
			}
		});
		b.setNegativeButton("Cancel", null);
		b.show();
	}

	private void runSearch() {
		hideIme();
		if (boxText().length() == 0) {
			mSubtitle.setText("Type a word, then Search.");
			return;
		}
		if (mOpenFile != null) {
			findInOpenFile(1);
		} else {
			searchLoadedContents();
		}
	}

	/**
	 * ✕ never dismisses. File → list (keep the query). List → full Load list
	 * and empty box.
	 */
	private void onClearSearch() {
		hideIme();
		abandonInFlightSearch();
		if (mOpenFile != null) {
			leaveOpenFileToList();
			return;
		}
		clearSearchOnList();
	}

	private void clearSearchOnList() {
		abandonInFlightSearch();
		mHitsMode = false;
		mHitFiles.clear();
		mFreezeNameFilter = false;
		mSearchStatus = "";
		mQuery = "";
		if (mSearch != null) {
			mSearch.setText("");
		}
		mNameFilter = "";
		if (mLoaded.isEmpty()) {
			mSubtitle.setText(idleHint());
			mEmpty.setText(idleHint());
			mEmpty.setVisibility(View.VISIBLE);
			mList.setVisibility(View.GONE);
		} else {
			refreshListFromLoaded();
			if (mFiles.isEmpty()) {
				mEmpty.setVisibility(View.VISIBLE);
				mList.setVisibility(View.GONE);
			} else {
				mEmpty.setVisibility(View.GONE);
				mList.setVisibility(View.VISIBLE);
			}
			mSubtitle.setText(listSummary());
		}
		paintSearchChrome();
	}

	private void searchLoadedContents() {
		final String query = boxText();
		if (query.length() == 0) {
			mSubtitle.setText("Type a word, then Search.");
			return;
		}
		if (mLoaded.isEmpty()) {
			mSubtitle.setText("Tap Load, then Search.");
			return;
		}
		final ArrayList<File> files = new ArrayList<File>(
				mHitsMode ? mLoaded : mFiles);
		if (files.isEmpty()) {
			mSubtitle.setText("No files to search. ✕ the name filter, or Load.");
			return;
		}
		mSearchStatus = "";
		mSubtitle.setText("Searching " + files.size() + " file"
				+ (files.size() == 1 ? "" : "s") + "…");
		setSearchBusy(true);
		final int gen = ++mSearchGen;
		SessionLogSearch.runIo(new Runnable() {
			@Override
			public void run() {
				SessionLogSearch.FilesScan scan;
				try {
					scan = SessionLogSearch.searchFiles(files, query, false,
							SessionLogSearch.MAX_HITS, SessionLogSearch.MAX_HITS,
							SessionLogSearch.MAX_FILES_PER_SEARCH,
							SessionLogSearch.Budget.ofDefault(),
							new SessionLogSearch.Cancel() {
								@Override
								public boolean get() {
									return !mAlive || gen != mSearchGen;
								}
							});
				} catch (IOException e) {
					scan = new SessionLogSearch.FilesScan();
				}
				final SessionLogSearch.FilesScan result = scan;
				mMain.post(new Runnable() {
					@Override
					public void run() {
						if (!mAlive || gen != mSearchGen) {
							return;
						}
						setSearchBusy(false);
						String stop = "";
						if (result.stopped) {
							stop = SessionLogSearch.budgetStopMessage(
									SessionLogSearch.SEARCH_BYTE_BUDGET);
							if (result.filesLeft > 0) {
								stop = stop + " " + result.filesLeft
										+ " files not opened.";
							}
						}
						if (result.matches.isEmpty()) {
							mHitsMode = false;
							mHitFiles.clear();
							refreshListFromLoaded();
							if (stop.length() > 0) {
								mSubtitle.setText("No \"" + query + "\". " + stop);
							} else {
								mSubtitle.setText("No \"" + query + "\" in "
										+ files.size() + " files.");
							}
							paintSearchChrome();
							return;
						}
						mHitsMode = true;
						mFreezeNameFilter = true;
						mHitFiles.clear();
						mHitFiles.addAll(result.matches);
						mSearchStatus = stop;
						paintHitsList();
						paintSearchChrome();
					}
				});
			}
		});
	}

	/**
	 * Next/previous in the open file. First press scans once into
	 * {@link #mFileHits}; later presses only walk that array.
	 */
	private void findInOpenFile(int direction) {
		final File file = mOpenFile;
		final String query = boxText();
		if (file == null || query.length() == 0) {
			return;
		}
		if (mIndex == null) {
			mSubtitle.setText("Still reading…");
			return;
		}
		if (mFileHits != null && query.equals(mQuery) && mFileHits.size > 0) {
			int fromLine = direction < 0
					? mHighlightLine
					: (mHighlightLine < 0 ? 0 : mHighlightLine + 1);
			int at = direction < 0
					? SessionLogSearch.prevHitIndex(mFileHits.lines, mFileHits.size,
							fromLine, true)
					: SessionLogSearch.nextHitIndex(mFileHits.lines, mFileHits.size,
							fromLine, true);
			if (at >= 0) {
				mFileHitAt = at;
				jumpToLine(mFileHits.lines[at]);
				paintSearchChrome();
			}
			return;
		}
		collectOpenFileHits(mHighlightLine < 0 ? 0 : mHighlightLine, direction);
	}

	private void collectOpenFileHits(final int preferLine, final int direction) {
		final File file = mOpenFile;
		final String query = boxText();
		if (file == null || query.length() == 0) {
			return;
		}
		mSubtitle.setText("Searching this file…");
		setSearchBusy(true);
		final int gen = ++mSearchGen;
		SessionLogSearch.runIo(new Runnable() {
			@Override
			public void run() {
				SessionLogSearch.LineHits hits;
				try {
					hits = SessionLogSearch.collectLineHits(file, query, false,
							SessionLogSearch.MAX_LINE_HITS, null,
							new SessionLogSearch.Cancel() {
								@Override
								public boolean get() {
									return !mAlive || gen != mSearchGen;
								}
							});
				} catch (IOException e) {
					hits = SessionLogSearch.LineHits.empty();
				}
				final SessionLogSearch.LineHits found = hits;
				mMain.post(new Runnable() {
					@Override
					public void run() {
						if (!mAlive || gen != mSearchGen || mOpenFile != file) {
							return;
						}
						setSearchBusy(false);
						mFileHits = found;
						mQuery = query;
						mCaseSensitive = false;
						if (found.size == 0) {
							mFileHitAt = -1;
							mSearchStatus = "No \"" + query + "\" in this file.";
							mSubtitle.setText(mSearchStatus);
							paintSearchChrome();
							renderPage();
							return;
						}
						int at;
						if (direction < 0) {
							at = SessionLogSearch.prevHitIndex(found.lines,
									found.size, preferLine, true);
						} else if (direction > 0) {
							int from = preferLine < 0 ? 0 : preferLine;
							if (mHighlightLine >= 0) {
								from = preferLine + 1;
							}
							at = SessionLogSearch.nextHitIndex(found.lines,
									found.size, from, true);
						} else {
							at = SessionLogSearch.nextHitIndex(found.lines,
									found.size, preferLine, true);
						}
						if (at < 0) {
							at = 0;
						}
						mFileHitAt = at;
						mSearchStatus = found.truncated
								? "First " + found.size + " matches."
								: "";
						jumpToLine(found.lines[at]);
						paintSearchChrome();
					}
				});
			}
		});
	}

	private void jumpToLine(int lineIndex) {
		if (mIndex == null) {
			return;
		}
		int jump = lineIndex;
		if (jump < 0 || jump >= mIndex.lineCount()) {
			return;
		}
		int start = jump - PAGE_LINES / 4;
		if (start < 0) {
			start = 0;
		}
		if (start + PAGE_LINES > mIndex.lineCount()) {
			start = Math.max(0, mIndex.lineCount() - PAGE_LINES);
		}
		mPageStart = start;
		mHighlightLine = jump;
		renderPage();
	}

	private int color(int id) {
		return getContext().getResources().getColor(id);
	}

	static String formatSize(long bytes) {
		if (bytes < 1024L) {
			return bytes + " B";
		}
		if (bytes < 1024L * 1024L) {
			return (bytes / 1024L) + " KB";
		}
		return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
	}

	static String formatDate(long mtime) {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(mtime));
	}

	/**
	 * Byte offsets of each line start so a page can be seeked without holding
	 * the whole file as a String.
	 */
	static final class LineIndex {
		final long[] starts;
		final long fileLength;

		LineIndex(long[] starts, long fileLength) {
			this.starts = starts;
			this.fileLength = fileLength;
		}

		int lineCount() {
			return starts.length;
		}

		static LineIndex build(File file) throws IOException {
			ArrayList<Long> tmp = new ArrayList<Long>(4096);
			tmp.add(Long.valueOf(0L));
			FileInputStream in = new FileInputStream(file);
			long pos = 0L;
			try {
				byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) > 0) {
					for (int i = 0; i < n; i++) {
						if (buf[i] == (byte) '\n') {
							tmp.add(Long.valueOf(pos + i + 1));
						}
					}
					pos += n;
				}
			} finally {
				in.close();
			}
			if (tmp.size() > 0 && tmp.get(tmp.size() - 1).longValue() == pos) {
				tmp.remove(tmp.size() - 1);
			}
			long[] starts = new long[tmp.size()];
			for (int i = 0; i < tmp.size(); i++) {
				starts[i] = tmp.get(i).longValue();
			}
			return new LineIndex(starts, pos);
		}

		String readPage(File file, int fromLine, int count) throws IOException {
			if (fromLine < 0 || fromLine >= starts.length || count <= 0) {
				return "";
			}
			int to = fromLine + count;
			if (to > starts.length) {
				to = starts.length;
			}
			long start = starts[fromLine];
			long end = to < starts.length ? starts[to] : fileLength;
			int len = (int) (end - start);
			if (len <= 0) {
				return "";
			}
			RandomAccessFile raf = new RandomAccessFile(file, "r");
			try {
				raf.seek(start);
				byte[] raw = new byte[len];
				raf.readFully(raw);
				String s = new String(raw, StandardCharsets.UTF_8);
				if (s.endsWith("\n")) {
					s = s.substring(0, s.length() - 1);
				}
				return s.replace("\r", "");
			} finally {
				raf.close();
			}
		}
	}

	private final class FileAdapter extends BaseAdapter {
		@Override
		public int getCount() {
			return mFiles.size();
		}

		@Override
		public Object getItem(int position) {
			return mFiles.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TextView tv;
			if (convertView instanceof TextView) {
				tv = (TextView) convertView;
			} else {
				tv = new TextView(getContext());
				int pad = (int) (10 * getContext().getResources().getDisplayMetrics().density);
				tv.setPadding(pad, pad, pad, pad);
				tv.setTextColor(color(R.color.chrome_title_text));
				tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
			}
			File f = mFiles.get(position);
			String extra;
			if (mHitsMode) {
				SessionLogSearch.FileMatch hit = hitFor(f);
				if (hit != null) {
					extra = hit.matchCount + " hit" + (hit.matchCount == 1 ? "" : "s");
				} else {
					extra = "";
				}
			} else {
				extra = formatDate(f.lastModified()) + "   " + formatSize(f.length());
			}
			tv.setText(f.getName() + "\n" + extra);
			return tv;
		}
	}
}

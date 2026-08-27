package com.resurrection.blowtorch2.lib.window;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
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

	private boolean mAlive = true;
	private int mListGen;
	private int mPageGen;

	private File mOpenFile;
	private LineIndex mIndex;
	private int mPageStart;
	private int mHighlightLine = -1;
	private String mQuery = "";
	private boolean mCaseSensitive;
	private File mPendingFile;
	private int mPendingLine = -1;

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
		mSubtitle.setText("Loading…");
		root.addView(mSubtitle);

		mEmpty = new TextView(getContext());
		mEmpty.setTextColor(color(R.color.chrome_description));
		mEmpty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
		mEmpty.setPadding(pad, pad, pad, pad);
		mEmpty.setText("Loading…");
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
				openFile(mFiles.get(position), -1);
			}
		});

		mFileText = new TextView(getContext());
		mFileText.setTypeface(Typeface.MONOSPACE);
		mFileText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
		mFileText.setTextColor(color(R.color.chrome_title_text));
		mFileText.setTextIsSelectable(true);
		mFileText.setPadding(pad, pad / 2, pad, pad / 2);
		mFileScroll = new ScrollView(getContext());
		mFileScroll.addView(mFileText, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		mFileScroll.setVisibility(View.GONE);

		LinearLayout.LayoutParams fill = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
		root.addView(mEmpty, fill);
		root.addView(mList, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
		root.addView(mFileScroll, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

		mFileFooter = chromeFooter(density);
		mBack = chromeFooterButton("Back");
		mBack.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showListScreen();
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
		root.addView(mFileFooter);

		setContentView(root);

		Window window = getWindow();
		if (window != null) {
			int width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.94f);
			int height = (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.88f);
			window.setLayout(width, height);
			window.setGravity(Gravity.CENTER);
		}

		showListWidgets();
		loadFileList();
		if (mPendingFile != null) {
			openFile(mPendingFile, mPendingLine);
			mPendingFile = null;
			mPendingLine = -1;
		}
	}

	@Override
	public void dismiss() {
		mAlive = false;
		mListGen++;
		mPageGen++;
		super.dismiss();
	}

	/** Return to the file list (from a search jump, or Back). */
	public void showListScreen() {
		mOpenFile = null;
		mIndex = null;
		mHighlightLine = -1;
		showListWidgets();
		mTitle.setText("Session logs");
		if (mFiles.isEmpty()) {
			mSubtitle.setText("Loading…");
			loadFileList();
		} else {
			mSubtitle.setText(mFiles.size() + " file"
					+ (mFiles.size() == 1 ? "" : "s") + " for " + mDisplay);
		}
	}

	/**
	 * Jump to a match. Safe to call while the dialog is already showing.
	 */
	public void openFileAtLine(File file, int lineIndex, String query, boolean caseSensitive) {
		mQuery = query == null ? "" : query;
		mCaseSensitive = caseSensitive;
		if (file == null) {
			showListScreen();
			return;
		}
		openFile(file, lineIndex);
	}

	private void loadFileList() {
		final int gen = ++mListGen;
		final Context app = getContext().getApplicationContext();
		final String display = mDisplay;
		SessionLogSearch.runIo(new Runnable() {
			@Override
			public void run() {
				final List<File> files = SessionLogger.listLogFiles(app, display);
				mMain.post(new Runnable() {
					@Override
					public void run() {
						if (!mAlive || gen != mListGen) {
							return;
						}
						mFiles.clear();
						mFiles.addAll(files);
						mAdapter.notifyDataSetChanged();
						if (mOpenFile == null) {
							if (mFiles.isEmpty()) {
								mEmpty.setText(emptyHint());
								mEmpty.setVisibility(View.VISIBLE);
								mList.setVisibility(View.GONE);
								mSubtitle.setText(emptyHint());
							} else {
								mEmpty.setVisibility(View.GONE);
								mList.setVisibility(View.VISIBLE);
								mSubtitle.setText(mFiles.size() + " file"
										+ (mFiles.size() == 1 ? "" : "s")
										+ " for " + mDisplay + "\n"
										+ mFiles.get(0).getParent());
							}
						}
					}
				});
			}
		});
	}

	private String emptyHint() {
		return "No session logs for this world yet.\n"
				+ "Enable Options → Service → Log Session to File?\n"
				+ "Files are {world}_{date}.txt under /BlowTorch/session_logs/.";
	}

	private void showListWidgets() {
		mList.setVisibility(mFiles.isEmpty() ? View.GONE : View.VISIBLE);
		mEmpty.setVisibility(mFiles.isEmpty() ? View.VISIBLE : View.GONE);
		mFileScroll.setVisibility(View.GONE);
		mBack.setEnabled(false);
		mOlder.setEnabled(false);
		mNewer.setEnabled(false);
	}

	private void showFileWidgets() {
		mList.setVisibility(View.GONE);
		mEmpty.setVisibility(View.GONE);
		mFileScroll.setVisibility(View.VISIBLE);
		mBack.setEnabled(true);
	}

	private void openFile(final File file, final int lineIndex) {
		if (file == null) {
			return;
		}
		mOpenFile = file;
		mHighlightLine = lineIndex;
		mIndex = null;
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
						mSubtitle.setText(file.getAbsolutePath() + "\n"
								+ "lines " + shownFrom + "–" + to
								+ " of " + index.lineCount()
								+ "  (" + formatSize(file.length()) + ")");
						int rel = (mHighlightLine >= from && mHighlightLine < to)
								? mHighlightLine - from : -1;
						mFileText.setText(highlight(text, rel, mQuery, mCaseSensitive));
						mOlder.setEnabled(from > 0);
						mNewer.setEnabled(to < index.lineCount());
						if (rel >= 0) {
							final int lineRel = rel;
							mFileScroll.post(new Runnable() {
								@Override
								public void run() {
									int y = lineRel * mFileText.getLineHeight();
									mFileScroll.scrollTo(0, Math.max(0, y - 24));
								}
							});
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
			int start = 0;
			for (int i = 0; i < highlightRel; i++) {
				int nl = text.indexOf('\n', start);
				if (nl < 0) {
					start = text.length();
					break;
				}
				start = nl + 1;
			}
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

	private LinearLayout.LayoutParams footerButtonParams(float density) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		lp.height = (int) (44 * density + 0.5f);
		return lp;
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
			tv.setText(f.getName() + "\n" + formatDate(f.lastModified())
					+ "   " + formatSize(f.length()));
			return tv;
		}
	}
}

package com.resurrection.blowtorch2.lib.window;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.util.UserManualIndex;
import com.resurrection.blowtorch2.lib.util.UserManualMarkdown;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Overflow → Help: the packaged user manual, split on {@code ##} headings into
 * expandable categories with a search bar. Content from {@code R.raw.user_manual};
 * keep in sync with docs/user-manual.md.
 *
 * <p>Search lists matching categories (with hit counts) first; the player
 * expands a category, then a section. Bodies are filled only when opened, so
 * typing {@code trigger} does not spannable-highlight every hit in the file.
 *
 * <p>The two-argument constructor still dumps any other shipped plain-text
 * document as one scroll — there is no second accordion. That constructor is
 * unused today (plugin authoring is a short AlertDialog).
 */
public class HelpDialog extends Dialog {

	private static final int HIGHLIGHT_QUERY = 0x4433AADD;
	private static final long SEARCH_DEBOUNCE_MS = 280;

	private final int mRawResource;
	private final String mTitle;
	private final boolean mAccordion;
	private final Handler mSearchHandler = new Handler(Looper.getMainLooper());

	private List<UserManualIndex.Section> mAll = new ArrayList<UserManualIndex.Section>();
	private LinearLayout mList;
	private TextView mEmpty;
	private TextView mHitSummary;
	private EditText mSearch;
	private Button mPrev;
	private Button mNext;
	private ScrollView mScroll;
	private String mQuery = "";
	private Runnable mSearchApply;
	private final Set<String> mOpenCategories = new HashSet<String>();
	private final Set<String> mOpenLeaves = new HashSet<String>();
	private final List<UserManualIndex.Section> mMatchLeaves =
			new ArrayList<UserManualIndex.Section>();
	private int mMatchIndex = -1;

	public HelpDialog(Context context) {
		this(context, R.raw.user_manual, "Help");
	}

	/**
	 * @param rawResource plain-text document in {@code res/raw}
	 * @param title       header text for this document
	 */
	public HelpDialog(Context context, int rawResource, String title) {
		super(context, R.style.BlowTorch_Dialog);
		mRawResource = rawResource;
		mTitle = title;
		mAccordion = rawResource == R.raw.user_manual;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);

		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (12 * density + 0.5f);

		LinearLayout root = new LinearLayout(getContext());
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(color(R.color.chrome_body));

		TextView title = new TextView(getContext());
		title.setText(mTitle);
		title.setTextColor(color(R.color.chrome_title_text));
		title.setBackgroundColor(color(R.color.chrome_title_bar));
		title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setAllCaps(true);
		title.setGravity(Gravity.CENTER);
		root.addView(title, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, (int) (42 * density + 0.5f)));

		if (mAccordion) {
			root.addView(buildSearchBar(density, pad));
		}

		mScroll = new ScrollView(getContext());
		mScroll.setFillViewport(true);
		if (mAccordion) {
			LinearLayout body = new LinearLayout(getContext());
			body.setOrientation(LinearLayout.VERTICAL);
			mEmpty = new TextView(getContext());
			mEmpty.setTextColor(color(R.color.chrome_description));
			mEmpty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
			mEmpty.setPadding(pad, pad, pad, pad);
			mEmpty.setVisibility(View.GONE);
			body.addView(mEmpty);
			mList = new LinearLayout(getContext());
			mList.setOrientation(LinearLayout.VERTICAL);
			body.addView(mList, new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT));
			mScroll.addView(body);
			mAll = UserManualIndex.parse(loadManualText());
			resetOpenStateForQuery();
			rebuildList();
		} else {
			TextView body = new TextView(getContext());
			body.setText(loadManualText());
			body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
			body.setTypeface(Typeface.MONOSPACE);
			body.setTextColor(color(R.color.chrome_title_text));
			body.setTextIsSelectable(true);
			body.setPadding(pad, pad, pad, pad);
			mScroll.addView(body);
		}
		root.addView(mScroll, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

		LinearLayout footer = new LinearLayout(getContext());
		footer.setOrientation(LinearLayout.HORIZONTAL);
		footer.setBackgroundColor(color(R.color.chrome_title_bar));
		int footPad = (int) (6 * density + 0.5f);
		footer.setPadding(footPad, footPad, footPad, footPad);
		Button close = new Button(getContext());
		close.setText("Close");
		close.setMinHeight((int) (44 * density + 0.5f));
		close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
		footer.addView(close, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				(int) (44 * density + 0.5f)));
		root.addView(footer);

		setContentView(root);

		Window window = getWindow();
		if (window != null) {
			int width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.92f);
			int height = (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.88f);
			window.setLayout(width, height);
			window.setGravity(Gravity.CENTER);
			window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
					| WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		}
	}

	@Override
	protected void onStop() {
		if (mSearchApply != null) {
			mSearchHandler.removeCallbacks(mSearchApply);
			mSearchApply = null;
		}
		super.onStop();
	}

	private LinearLayout buildSearchBar(float density, int pad) {
		LinearLayout col = new LinearLayout(getContext());
		col.setOrientation(LinearLayout.VERTICAL);
		col.setBackgroundColor(color(R.color.chrome_title_bar));
		col.setPadding(pad, pad / 2, pad, pad / 2);

		LinearLayout row = new LinearLayout(getContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);

		mSearch = new EditText(getContext());
		mSearch.setHint("Find in guide");
		mSearch.setSingleLine(true);
		mSearch.setInputType(InputType.TYPE_CLASS_TEXT
				| InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		mSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
		mSearch.setTextColor(color(R.color.chrome_title_text));
		mSearch.setHintTextColor(color(R.color.chrome_hint));
		mSearch.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		mSearch.setBackgroundColor(color(R.color.chrome_body));
		int boxPad = (int) (8 * density + 0.5f);
		mSearch.setPadding(boxPad, boxPad, boxPad, boxPad);
		mSearch.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				mQuery = s == null ? "" : s.toString();
				scheduleSearchApply();
			}
		});
		mSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_SEARCH
						|| actionId == EditorInfo.IME_ACTION_DONE) {
					flushSearchNow();
					return true;
				}
				return false;
			}
		});
		row.addView(mSearch, new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

		int btnH = (int) (44 * density + 0.5f);
		int btnW = (int) (48 * density + 0.5f);
		int gap = (int) (4 * density + 0.5f);

		mPrev = navButton("◀", density, btnW, btnH);
		mPrev.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				stepMatch(-1);
			}
		});
		LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(btnW, btnH);
		prevLp.leftMargin = gap;
		row.addView(mPrev, prevLp);

		mNext = navButton("▶", density, btnW, btnH);
		mNext.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				stepMatch(1);
			}
		});
		LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(btnW, btnH);
		nextLp.leftMargin = gap;
		row.addView(mNext, nextLp);

		Button clear = navButton("✕", density, btnW, btnH);
		clear.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mSearch != null) {
					mSearch.setText("");
					flushSearchNow();
				}
			}
		});
		LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(btnW, btnH);
		clearLp.leftMargin = gap;
		row.addView(clear, clearLp);

		col.addView(row, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));

		mHitSummary = new TextView(getContext());
		mHitSummary.setTextColor(color(R.color.chrome_description));
		mHitSummary.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
		mHitSummary.setPadding(0, (int) (4 * density + 0.5f), 0, 0);
		mHitSummary.setVisibility(View.GONE);
		col.addView(mHitSummary);
		return col;
	}

	private Button navButton(String label, float density, int w, int h) {
		Button b = new Button(getContext());
		b.setText(label);
		b.setMinWidth(0);
		b.setMinimumWidth(0);
		b.setMinHeight(h);
		b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		return b;
	}

	private void scheduleSearchApply() {
		if (mSearchApply != null) {
			mSearchHandler.removeCallbacks(mSearchApply);
		}
		mSearchApply = new Runnable() {
			@Override
			public void run() {
				mSearchApply = null;
				resetOpenStateForQuery();
				rebuildList();
			}
		};
		mSearchHandler.postDelayed(mSearchApply, SEARCH_DEBOUNCE_MS);
	}

	/** Apply a pending query immediately (Next, or the keyboard Search key). */
	private void flushSearchNow() {
		if (mSearchApply == null) {
			return;
		}
		mSearchHandler.removeCallbacks(mSearchApply);
		mSearchApply = null;
		resetOpenStateForQuery();
		rebuildList();
	}

	private boolean isSearching() {
		return mQuery != null && mQuery.trim().length() >= 2;
	}

	private void resetOpenStateForQuery() {
		mOpenCategories.clear();
		mOpenLeaves.clear();
		mMatchIndex = -1;
		if (!isSearching()) {
			UserManualIndex.seedFirstOpen(mOpenCategories, mOpenLeaves);
		}
	}

	private void rebuildList() {
		if (mList == null) {
			return;
		}
		mList.removeAllViews();
		mMatchLeaves.clear();
		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (12 * density + 0.5f);
		List<UserManualIndex.Section> shown = UserManualIndex.filter(mAll, mQuery);
		if (shown.isEmpty()) {
			if (mEmpty != null) {
				mEmpty.setVisibility(View.VISIBLE);
				String q = mQuery == null ? "" : mQuery.trim();
				mEmpty.setText("No sections match \"" + q + "\".");
			}
			paintHitNav(0, 0);
			return;
		}
		if (mEmpty != null) {
			mEmpty.setVisibility(View.GONE);
		}
		boolean searching = isSearching();
		LinkedHashMap<String, List<UserManualIndex.Section>> byCat =
				UserManualIndex.groupByCategory(shown);
		int catHits = 0;
		for (Map.Entry<String, List<UserManualIndex.Section>> e : byCat.entrySet()) {
			List<UserManualIndex.Section> leaves = e.getValue();
			if (leaves == null || leaves.isEmpty()) {
				continue;
			}
			catHits++;
			int hitsInCat = 0;
			if (searching) {
				for (int i = 0; i < leaves.size(); i++) {
					mMatchLeaves.add(leaves.get(i));
					hitsInCat += UserManualIndex.hitCount(leaves.get(i), mQuery);
				}
			}
			mList.addView(categoryBlock(e.getKey(), leaves, hitsInCat, searching,
					density, pad));
		}
		paintHitNav(catHits, mMatchLeaves.size());
	}

	private LinearLayout categoryBlock(final String category,
			final List<UserManualIndex.Section> leaves, final int hitsInCat,
			final boolean searching, float density, int pad) {
		final LinearLayout box = new LinearLayout(getContext());
		box.setOrientation(LinearLayout.VERTICAL);

		final boolean[] open = new boolean[] { mOpenCategories.contains(category) };
		final TextView head = new TextView(getContext());
		head.setAllCaps(true);
		head.setTypeface(Typeface.DEFAULT_BOLD);
		head.setTextColor(color(R.color.chrome_description));
		head.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
		head.setMinHeight((int) (44 * density + 0.5f));
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.setPadding(pad, (int) (8 * density + 0.5f), pad, (int) (8 * density + 0.5f));
		head.setBackgroundColor(color(R.color.chrome_title_bar));
		paintCategoryHead(head, category, leaves.size(), hitsInCat, searching, open[0]);

		final LinearLayout kids = new LinearLayout(getContext());
		kids.setOrientation(LinearLayout.VERTICAL);
		kids.setVisibility(open[0] ? View.VISIBLE : View.GONE);
		for (int i = 0; i < leaves.size(); i++) {
			kids.addView(leafRow(leaves.get(i), searching, density, pad));
		}

		head.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				open[0] = !open[0];
				if (open[0]) {
					mOpenCategories.add(category);
				} else {
					mOpenCategories.remove(category);
				}
				paintCategoryHead(head, category, leaves.size(), hitsInCat, searching,
						open[0]);
				kids.setVisibility(open[0] ? View.VISIBLE : View.GONE);
			}
		});
		box.addView(head);
		box.addView(kids);
		return box;
	}

	private void paintCategoryHead(TextView head, String category, int leafCount,
			int hitsInCat, boolean searching, boolean open) {
		String arrow = open ? "▾  " : "▸  ";
		if (searching) {
			head.setText(arrow + category + "  ·  " + hitsInCat
					+ (hitsInCat == 1 ? " hit" : " hits") + " in " + leafCount
					+ (leafCount == 1 ? " section" : " sections"));
		} else {
			head.setText(arrow + category);
		}
	}

	private LinearLayout leafRow(final UserManualIndex.Section section,
			final boolean searching, float density, int pad) {
		final LinearLayout box = new LinearLayout(getContext());
		box.setOrientation(LinearLayout.VERTICAL);
		box.setTag(section.title);

		final boolean[] open = new boolean[] { mOpenLeaves.contains(section.title) };
		final TextView head = new TextView(getContext());
		head.setTextColor(color(R.color.chrome_title_text));
		head.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		head.setMinHeight((int) (44 * density + 0.5f));
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.setPadding(pad, (int) (8 * density + 0.5f), pad, (int) (8 * density + 0.5f));
		head.setBackgroundColor(color(R.color.chrome_body));
		final int hits = searching ? UserManualIndex.hitCount(section, mQuery) : 0;
		final String displayTitle;
		String renderedTitle = UserManualMarkdown.render(section.title).text;
		if (renderedTitle.length() == 0) {
			displayTitle = section.title;
		} else {
			displayTitle = renderedTitle;
		}
		paintLeafChrome(head, displayTitle, hits, searching, open[0]);

		final TextView body = new TextView(getContext());
		body.setTypeface(Typeface.SANS_SERIF);
		body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
		body.setTextColor(color(R.color.chrome_title_text));
		body.setTextIsSelectable(true);
		body.setMovementMethod(LinkMovementMethod.getInstance());
		body.setPadding(pad, 0, pad, pad);
		body.setVisibility(open[0] ? View.VISIBLE : View.GONE);
		if (open[0]) {
			applyBody(body, section, searching);
		}

		head.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				open[0] = !open[0];
				if (open[0]) {
					mOpenLeaves.add(section.title);
					if (body.getText() == null || body.getText().length() == 0) {
						applyBody(body, section, searching);
					}
				} else {
					mOpenLeaves.remove(section.title);
				}
				paintLeafChrome(head, displayTitle, hits, searching, open[0]);
				body.setVisibility(open[0] ? View.VISIBLE : View.GONE);
			}
		});
		box.addView(head);
		box.addView(body);
		return box;
	}

	private void paintLeafChrome(TextView head, String title, int hits,
			boolean searching, boolean open) {
		String arrow = open ? "▾  " : "▸  ";
		if (searching && hits > 0) {
			head.setText(arrow + title + "  ·  " + hits
					+ (hits == 1 ? " hit" : " hits"));
		} else {
			head.setText(arrow + title);
		}
	}

	private void applyBody(TextView body, UserManualIndex.Section section,
			boolean highlight) {
		UserManualMarkdown.Result rendered = UserManualMarkdown.render(section.body);
		SpannableStringBuilder sb = new SpannableStringBuilder(rendered.text);
		for (int i = 0; i < rendered.runs.size(); i++) {
			UserManualMarkdown.Run run = rendered.runs.get(i);
			if (run.start < 0 || run.end > sb.length() || run.start >= run.end) {
				continue;
			}
			if (run.kind == UserManualMarkdown.KIND_BOLD) {
				sb.setSpan(new StyleSpan(Typeface.BOLD), run.start, run.end, 0);
			} else if (run.kind == UserManualMarkdown.KIND_CODE) {
				sb.setSpan(new TypefaceSpan("monospace"), run.start, run.end, 0);
			} else if (run.kind == UserManualMarkdown.KIND_LINK && run.href != null) {
				sb.setSpan(new URLSpan(run.href), run.start, run.end, 0);
			}
		}
		if (highlight) {
			List<UserManualIndex.Hit> hits =
					UserManualIndex.highlightRanges(rendered.text, mQuery);
			for (int i = 0; i < hits.size(); i++) {
				UserManualIndex.Hit hit = hits.get(i);
				if (hit.start >= 0 && hit.end <= sb.length() && hit.start < hit.end) {
					sb.setSpan(new BackgroundColorSpan(HIGHLIGHT_QUERY),
							hit.start, hit.end, 0);
				}
			}
		}
		body.setText(sb);
	}

	private void stepMatch(int delta) {
		flushSearchNow();
		if (mMatchLeaves.isEmpty()) {
			return;
		}
		if (mMatchIndex < 0) {
			mMatchIndex = delta > 0 ? 0 : mMatchLeaves.size() - 1;
		} else {
			mMatchIndex = (mMatchIndex + delta + mMatchLeaves.size()) % mMatchLeaves.size();
		}
		UserManualIndex.Section s = mMatchLeaves.get(mMatchIndex);
		mOpenCategories.clear();
		mOpenLeaves.clear();
		mOpenCategories.add(s.category);
		mOpenLeaves.add(s.title);
		rebuildList();
		scrollToLeaf(s.title);
	}

	private void paintHitNav(int categories, int sections) {
		boolean searching = isSearching();
		if (mPrev != null) {
			mPrev.setVisibility(searching && sections > 0 ? View.VISIBLE : View.GONE);
			mNext.setVisibility(searching && sections > 0 ? View.VISIBLE : View.GONE);
		}
		if (mHitSummary == null) {
			return;
		}
		if (!searching) {
			mHitSummary.setVisibility(View.GONE);
			return;
		}
		mHitSummary.setVisibility(View.VISIBLE);
		if (sections == 0) {
			mHitSummary.setText("No matches");
			return;
		}
		StringBuilder sb = new StringBuilder();
		if (mMatchIndex >= 0 && mMatchIndex < sections) {
			sb.append("Section ").append(mMatchIndex + 1).append(" of ").append(sections);
		} else {
			sb.append(sections).append(sections == 1 ? " section" : " sections");
		}
		if (categories > 0) {
			sb.append(" in ").append(categories)
					.append(categories == 1 ? " topic" : " topics");
		}
		sb.append(". Expand a topic, then a heading. ◀ ▶ jumps to the next heading.");
		mHitSummary.setText(sb.toString());
	}

	private void scrollToLeaf(final String title) {
		if (mScroll == null || mList == null || title == null) {
			return;
		}
		mScroll.post(new Runnable() {
			@Override
			public void run() {
				View leaf = mList.findViewWithTag(title);
				if (leaf == null) {
					return;
				}
				int y = 0;
				View v = leaf;
				while (v != null && v != mScroll) {
					y += v.getTop();
					if (!(v.getParent() instanceof View)) {
						break;
					}
					v = (View) v.getParent();
				}
				mScroll.smoothScrollTo(0, Math.max(0, y));
			}
		});
	}

	private int color(int id) {
		return getContext().getResources().getColor(id);
	}

	private String loadManualText() {
		InputStream in = null;
		try {
			in = getContext().getResources().openRawResource(mRawResource);
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append('\n');
			}
			return sb.toString();
		} catch (Exception e) {
			return mTitle + " could not be loaded.\nSee docs/ in the source tree.";
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (Exception ignored) {
				}
			}
		}
	}
}

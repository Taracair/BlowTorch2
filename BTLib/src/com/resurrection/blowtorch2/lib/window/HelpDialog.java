package com.resurrection.blowtorch2.lib.window;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.util.UserManualIndex;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
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
 * <p>The two-argument constructor still dumps any other shipped plain-text
 * document as one scroll — there is no second accordion. That constructor is
 * unused today (plugin authoring is a short AlertDialog).
 */
public class HelpDialog extends Dialog {

	private static final int HIGHLIGHT_QUERY = 0x4433AADD;

	private final int mRawResource;
	private final String mTitle;
	private final boolean mAccordion;

	private List<UserManualIndex.Section> mAll = new ArrayList<UserManualIndex.Section>();
	private LinearLayout mList;
	private TextView mEmpty;
	private EditText mSearch;
	private String mQuery = "";

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

		ScrollView scroll = new ScrollView(getContext());
		scroll.setFillViewport(true);
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
			scroll.addView(body);
			mAll = UserManualIndex.parse(loadManualText());
			rebuildList();
		} else {
			TextView body = new TextView(getContext());
			body.setText(loadManualText());
			body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
			body.setTypeface(Typeface.MONOSPACE);
			body.setTextColor(color(R.color.chrome_title_text));
			body.setTextIsSelectable(true);
			body.setPadding(pad, pad, pad, pad);
			scroll.addView(body);
		}
		root.addView(scroll, new LinearLayout.LayoutParams(
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

	private LinearLayout buildSearchBar(float density, int pad) {
		LinearLayout row = new LinearLayout(getContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setBackgroundColor(color(R.color.chrome_title_bar));
		row.setPadding(pad, pad / 2, pad, pad / 2);

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
				rebuildList();
			}
		});
		row.addView(mSearch, new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

		Button clear = new Button(getContext());
		clear.setText("✕");
		clear.setMinWidth(0);
		clear.setMinimumWidth(0);
		int clearH = (int) (44 * density + 0.5f);
		clear.setMinHeight(clearH);
		clear.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mSearch != null) {
					mSearch.setText("");
				}
			}
		});
		LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
				(int) (48 * density + 0.5f), clearH);
		clearLp.leftMargin = (int) (6 * density + 0.5f);
		row.addView(clear, clearLp);
		return row;
	}

	private void rebuildList() {
		if (mList == null) {
			return;
		}
		mList.removeAllViews();
		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (12 * density + 0.5f);
		List<UserManualIndex.Section> shown = UserManualIndex.filter(mAll, mQuery);
		if (shown.isEmpty()) {
			if (mEmpty != null) {
				mEmpty.setVisibility(View.VISIBLE);
				String q = mQuery == null ? "" : mQuery.trim();
				mEmpty.setText("No sections match \"" + q + "\".");
			}
			return;
		}
		if (mEmpty != null) {
			mEmpty.setVisibility(View.GONE);
		}
		boolean searching = mQuery != null && mQuery.trim().length() >= 2;
		LinkedHashMap<String, List<UserManualIndex.Section>> byCat =
				new LinkedHashMap<String, List<UserManualIndex.Section>>();
		for (int i = 0; i < UserManualIndex.CATEGORY_ORDER.length; i++) {
			byCat.put(UserManualIndex.CATEGORY_ORDER[i],
					new ArrayList<UserManualIndex.Section>());
		}
		for (int i = 0; i < shown.size(); i++) {
			UserManualIndex.Section s = shown.get(i);
			List<UserManualIndex.Section> bucket = byCat.get(s.category);
			if (bucket == null) {
				bucket = new ArrayList<UserManualIndex.Section>();
				byCat.put(s.category, bucket);
			}
			bucket.add(s);
		}
		boolean firstCategory = true;
		for (Map.Entry<String, List<UserManualIndex.Section>> e : byCat.entrySet()) {
			List<UserManualIndex.Section> leaves = e.getValue();
			if (leaves == null || leaves.isEmpty()) {
				continue;
			}
			mList.addView(categoryHeader(e.getKey(), density, pad));
			for (int i = 0; i < leaves.size(); i++) {
				boolean open = searching || (firstCategory && i == 0 && !searching);
				mList.addView(leafRow(leaves.get(i), open, searching, density, pad));
			}
			firstCategory = false;
		}
	}

	private TextView categoryHeader(String label, float density, int pad) {
		TextView h = new TextView(getContext());
		h.setText(label);
		h.setAllCaps(true);
		h.setTypeface(Typeface.DEFAULT_BOLD);
		h.setTextColor(color(R.color.chrome_description));
		h.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
		h.setPadding(pad, (int) (10 * density + 0.5f), pad, (int) (4 * density + 0.5f));
		h.setBackgroundColor(color(R.color.chrome_title_bar));
		return h;
	}

	private LinearLayout leafRow(final UserManualIndex.Section section, boolean startOpen,
			final boolean highlight, float density, int pad) {
		final LinearLayout box = new LinearLayout(getContext());
		box.setOrientation(LinearLayout.VERTICAL);

		final TextView head = new TextView(getContext());
		head.setTextColor(color(R.color.chrome_title_text));
		head.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		head.setMinHeight((int) (44 * density + 0.5f));
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.setPadding(pad, (int) (8 * density + 0.5f), pad, (int) (8 * density + 0.5f));
		head.setBackgroundColor(color(R.color.chrome_body));

		final TextView body = new TextView(getContext());
		body.setTypeface(Typeface.MONOSPACE);
		body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
		body.setTextColor(color(R.color.chrome_title_text));
		body.setTextIsSelectable(true);
		body.setPadding(pad, 0, pad, pad);
		body.setText(highlighted(section.body, highlight));

		final boolean[] open = new boolean[] { startOpen };
		paintLeafChrome(head, section.title, open[0]);
		body.setVisibility(open[0] ? View.VISIBLE : View.GONE);
		head.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				open[0] = !open[0];
				paintLeafChrome(head, section.title, open[0]);
				body.setVisibility(open[0] ? View.VISIBLE : View.GONE);
			}
		});
		box.addView(head);
		box.addView(body);
		return box;
	}

	private void paintLeafChrome(TextView head, String title, boolean open) {
		head.setText((open ? "▾  " : "▸  ") + title);
	}

	private CharSequence highlighted(String body, boolean doHighlight) {
		if (body == null) {
			return "";
		}
		if (!doHighlight) {
			return body;
		}
		List<UserManualIndex.Hit> hits = UserManualIndex.highlightRanges(body, mQuery);
		if (hits.isEmpty()) {
			return body;
		}
		SpannableStringBuilder sb = new SpannableStringBuilder(body);
		for (int i = 0; i < hits.size(); i++) {
			UserManualIndex.Hit hit = hits.get(i);
			sb.setSpan(new BackgroundColorSpan(HIGHLIGHT_QUERY), hit.start, hit.end, 0);
		}
		return sb;
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

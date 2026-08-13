package com.resurrection.blowtorch2.lib.window;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.resurrection.blowtorch2.lib.R;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Overflow → Help: compact crawler user manual (dot commands).
 * Content from {@code R.raw.user_manual}; keep in sync with docs/user-manual.md.
 *
 * <p>Also serves any other shipped plain-text document — the Plugins screen
 * shows {@code R.raw.plugin_authoring} through the two-argument constructor.
 * Those raw files are hand-adapted copies of the markdown in {@code docs/};
 * nothing syncs them, so a change to the markdown means a change here too.
 */
public class HelpDialog extends Dialog {

	private final int mRawResource;
	private final String mTitle;

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

		ScrollView scroll = new ScrollView(getContext());
		scroll.setFillViewport(true);
		TextView body = new TextView(getContext());
		body.setText(loadManualText());
		body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
		body.setTypeface(Typeface.MONOSPACE);
		body.setTextColor(color(R.color.chrome_title_text));
		body.setTextIsSelectable(true);
		body.setPadding(pad, pad, pad, pad);
		scroll.addView(body);
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
		}
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

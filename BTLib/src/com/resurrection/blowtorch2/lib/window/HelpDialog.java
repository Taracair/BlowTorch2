package com.resurrection.blowtorch2.lib.window;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.resurrection.blowtorch2.lib.R;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Overflow → Help: nearly full-screen user manual (dot commands).
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
		super(context, R.style.BlowTorch_Dialog_FullScreen);
		mRawResource = rawResource;
		mTitle = title;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);

		if (getContext() instanceof MainWindow) {
			MainWindow w = (MainWindow) getContext();
			if (w.isStatusBarHidden()) {
				getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
						WindowManager.LayoutParams.FLAG_FULLSCREEN);
			}
		}

		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (12 * density);

		LinearLayout root = new LinearLayout(getContext());
		root.setOrientation(LinearLayout.VERTICAL);

		LinearLayout header = new LinearLayout(getContext());
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setPadding(pad, pad, pad, pad / 2);

		TextView title = new TextView(getContext());
		title.setText(mTitle);
		title.setTextSize(22f);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		header.addView(title, titleLp);

		Button close = new Button(getContext());
		close.setText("Close");
		close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
		header.addView(close);
		root.addView(header);

		ScrollView scroll = new ScrollView(getContext());
		scroll.setFillViewport(true);
		TextView body = new TextView(getContext());
		body.setText(loadManualText());
		body.setTextSize(13f);
		body.setTypeface(Typeface.MONOSPACE);
		body.setTextIsSelectable(true);
		body.setPadding(pad, 0, pad, pad);
		scroll.addView(body);
		root.addView(scroll, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

		setContentView(root);

		Window window = getWindow();
		if (window != null) {
			window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
					WindowManager.LayoutParams.MATCH_PARENT);
			WindowManager.LayoutParams attrs = window.getAttributes();
			attrs.width = WindowManager.LayoutParams.MATCH_PARENT;
			attrs.height = WindowManager.LayoutParams.MATCH_PARENT;
			attrs.gravity = Gravity.FILL;
			window.setAttributes(attrs);
		}

		androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
			androidx.core.graphics.Insets sys =
					insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
			view.setPadding(sys.left, sys.top, sys.right, sys.bottom);
			return insets;
		});
		androidx.core.view.ViewCompat.requestApplyInsets(root);
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

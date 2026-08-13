package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;
import com.resurrection.blowtorch2.lib.util.SessionLogger;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
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
import android.widget.Toast;

/**
 * Overflow → Crash report: show / share the app error log.
 */
public class CrashReportDialog extends Dialog {

	public CrashReportDialog(Context context) {
		super(context, R.style.BlowTorch_Dialog);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);

		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (16 * density + 0.5f);

		LinearLayout root = new LinearLayout(getContext());
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(color(R.color.chrome_body));

		root.addView(chromeTitle("Crash report", density));

		ScrollView scroll = new ScrollView(getContext());
		LinearLayout body = new LinearLayout(getContext());
		body.setOrientation(LinearLayout.VERTICAL);
		body.setPadding(pad, pad, pad, pad);

		TextView logInfo = new TextView(getContext());
		logInfo.setText("Error log:\n" + BlowTorchLogger.getLogFile(getContext()).getAbsolutePath());
		logInfo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
		logInfo.setTextColor(color(R.color.chrome_description));
		logInfo.setPadding(0, 0, 0, pad / 2);
		body.addView(logInfo);

		if (SessionLogger.isEnabled(getContext())) {
			String path = SessionLogger.getLogLocationLabel(getContext());
			TextView sessionInfo = new TextView(getContext());
			sessionInfo.setText("Session log (txt):\n" + path);
			sessionInfo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
			sessionInfo.setTextColor(color(R.color.chrome_description));
			sessionInfo.setPadding(0, 0, 0, pad / 2);
			body.addView(sessionInfo);
		}

		scroll.addView(body);
		root.addView(scroll, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));

		LinearLayout footer = chromeFooter(density);
		Button show = chromeFooterButton("Show log");
		show.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showLogViewer();
			}
		});
		Button share = chromeFooterButton("Share log");
		share.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				shareLog();
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
		footer.addView(show, lp);
		LinearLayout.LayoutParams shareLp = footerButtonParams(density);
		shareLp.leftMargin = (int) (6 * density + 0.5f);
		footer.addView(share, shareLp);
		LinearLayout.LayoutParams closeLp = footerButtonParams(density);
		closeLp.leftMargin = (int) (6 * density + 0.5f);
		footer.addView(close, closeLp);
		root.addView(footer);

		setContentView(root);

		Window window = getWindow();
		if (window != null) {
			int width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.92f);
			window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
			window.setGravity(Gravity.CENTER);
		}
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

	private void showLogViewer() {
		String body = BlowTorchLogger.readLogTail(getContext(), 48 * 1024);
		TextView tv = new TextView(getContext());
		tv.setTypeface(Typeface.MONOSPACE);
		tv.setTextSize(11f);
		tv.setText(body);
		tv.setTextIsSelectable(true);
		tv.setPadding(24, 16, 24, 16);
		ScrollView scroller = new ScrollView(getContext());
		scroller.addView(tv);
		new AlertDialog.Builder(getContext())
				.setTitle("Error log")
				.setView(scroller)
				.setPositiveButton("Close", null)
				.setNeutralButton("Share", new android.content.DialogInterface.OnClickListener() {
					@Override
					public void onClick(android.content.DialogInterface dialog, int which) {
						shareLog();
					}
				})
				.show();
	}

	private void shareLog() {
		String body = BlowTorchLogger.readEntireLog(getContext());
		if (body == null || body.length() == 0) {
			Toast.makeText(getContext(), "Log is empty.", Toast.LENGTH_SHORT).show();
			return;
		}
		Intent send = new Intent(Intent.ACTION_SEND);
		send.setType("text/plain");
		send.putExtra(Intent.EXTRA_SUBJECT, "BlowTorch error log");
		send.putExtra(Intent.EXTRA_TEXT, body);
		try {
			getContext().startActivity(Intent.createChooser(send, "Share log"));
		} catch (Exception e) {
			Toast.makeText(getContext(), "No app available to share the log.", Toast.LENGTH_SHORT).show();
		}
	}
}

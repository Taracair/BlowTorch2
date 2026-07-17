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
		int pad = (int) (16 * density);

		LinearLayout root = new LinearLayout(getContext());
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(pad, pad, pad, pad);

		TextView title = new TextView(getContext());
		title.setText("Crash report");
		title.setTextSize(20f);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setPadding(0, 0, 0, pad / 2);
		root.addView(title);

		TextView logInfo = new TextView(getContext());
		logInfo.setText("Error log:\n" + BlowTorchLogger.getLogFile(getContext()).getAbsolutePath());
		logInfo.setTextSize(12f);
		logInfo.setPadding(0, 0, 0, pad / 2);
		root.addView(logInfo);

		if (SessionLogger.isEnabled(getContext())) {
			java.io.File current = SessionLogger.getCurrentLogFile();
			String path = current != null ? current.getAbsolutePath()
					: SessionLogger.getLogDirectory(getContext()).getAbsolutePath();
			TextView sessionInfo = new TextView(getContext());
			sessionInfo.setText("Session log (txt):\n" + path);
			sessionInfo.setTextSize(12f);
			sessionInfo.setPadding(0, 0, 0, pad / 2);
			root.addView(sessionInfo);
		}

		LinearLayout buttons = new LinearLayout(getContext());
		buttons.setOrientation(LinearLayout.HORIZONTAL);

		Button show = new Button(getContext());
		show.setText("Show log");
		show.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showLogViewer();
			}
		});

		Button share = new Button(getContext());
		share.setText("Share log");
		share.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				shareLog();
			}
		});

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		lp.setMargins((int) (4 * density), 0, (int) (4 * density), 0);
		buttons.addView(show, lp);
		buttons.addView(share, lp);
		root.addView(buttons);

		Button close = new Button(getContext());
		close.setText("Close");
		close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
		root.addView(close);

		setContentView(root);

		Window window = getWindow();
		if (window != null) {
			int width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.92f);
			window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
			window.setGravity(Gravity.CENTER);
		}
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

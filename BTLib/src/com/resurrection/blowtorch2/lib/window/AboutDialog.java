package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.settings.ConfigurationLoader;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;
import com.resurrection.blowtorch2.lib.util.SessionLogger;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class AboutDialog extends Dialog {

	public AboutDialog(Context context) {
		super(context);
	}
	
	public void onCreate(Bundle b) {
		super.onCreate(b);
		
		this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		
		this.setContentView(ConfigurationLoader.getAboutDialogResource(this.getContext()));

		try {
			String str = this.getContext().getPackageManager().getPackageInfo(this.getContext().getPackageName(), Context.CONTEXT_INCLUDE_CODE).versionName;
			int abtid = this.getContext().getResources().getIdentifier("blowtorch_about", "id", this.getContext().getPackageName());
			TextView v = (TextView) this.findViewById(abtid);
			v.setText("BlowTorch " + str);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		
		int aardid = this.getContext().getResources().getIdentifier("aardwolf_button", "id", this.getContext().getPackageName());
		if(aardid != 0) {
		this.findViewById(aardid).setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				Intent web_help = new Intent(Intent.ACTION_VIEW,Uri.parse("http://www.aardmud.org/"));
				AboutDialog.this.getContext().startActivity(web_help);
			}
		});
		}
		
		int btid = this.getContext().getResources().getIdentifier("blowtorch_button", "id", this.getContext().getPackageName());
		if (btid != 0) {
			View websiteButton = this.findViewById(btid);
			if (websiteButton != null) {
				websiteButton.setVisibility(View.GONE);
			}
		}
		
		appendLogActions();
	}
	
	private void appendLogActions() {
		ScrollView scroll = findScrollView((ViewGroup) getWindow().getDecorView());
		if (scroll == null || scroll.getChildCount() == 0 || !(scroll.getChildAt(0) instanceof LinearLayout)) {
			return;
		}
		LinearLayout root = (LinearLayout) scroll.getChildAt(0);

		TextView logInfo = new TextView(getContext());
		logInfo.setText("Error log:\n" + BlowTorchLogger.getLogFile(getContext()).getAbsolutePath());
		logInfo.setTextSize(12f);
		logInfo.setPadding(20, 16, 20, 8);
		root.addView(logInfo);

		LinearLayout buttons = new LinearLayout(getContext());
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		buttons.setPadding(16, 4, 16, 16);

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

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		lp.setMargins(4, 0, 4, 0);
		buttons.addView(show, lp);
		buttons.addView(share, lp);
		root.addView(buttons);

		if (SessionLogger.isEnabled(getContext())) {
			TextView sessionInfo = new TextView(getContext());
			java.io.File current = SessionLogger.getCurrentLogFile();
			String path = current != null ? current.getAbsolutePath()
					: SessionLogger.getLogDirectory(getContext()).getAbsolutePath();
			sessionInfo.setText("Session log (txt):\n" + path);
			sessionInfo.setTextSize(12f);
			sessionInfo.setPadding(20, 0, 20, 12);
			root.addView(sessionInfo);
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
	
	private ScrollView findScrollView(ViewGroup parent) {
		for (int i = 0; i < parent.getChildCount(); i++) {
			View child = parent.getChildAt(i);
			if (child instanceof ScrollView) {
				return (ScrollView) child;
			}
			if (child instanceof ViewGroup) {
				ScrollView found = findScrollView((ViewGroup) child);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

}

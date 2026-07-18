package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.settings.ConfigurationLoader;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * About dialog (launcher + session overflow).
 * Crash log actions live in {@link CrashReportDialog}.
 */
public class AboutDialog extends Dialog {

	private static final String PROJECT_URL = "https://github.com/Taracair/BlowTorch2";

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
		
		int btid = this.getContext().getResources().getIdentifier("blowtorch_button", "id", this.getContext().getPackageName());
		if (btid != 0) {
			View websiteButton = this.findViewById(btid);
			if (websiteButton != null) {
				websiteButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL));
						AboutDialog.this.getContext().startActivity(web);
					}
				});
			}
		}

		appendProjectLink();
	}

	private void appendProjectLink() {
		ScrollView scroll = findScrollView((ViewGroup) getWindow().getDecorView());
		if (scroll == null || scroll.getChildCount() == 0 || !(scroll.getChildAt(0) instanceof LinearLayout)) {
			return;
		}
		LinearLayout root = (LinearLayout) scroll.getChildAt(0);
		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (16 * density);

		Button github = new Button(getContext());
		github.setText("Project on GitHub");
		github.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL));
				AboutDialog.this.getContext().startActivity(web);
			}
		});
		LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		btnLp.setMargins(pad / 2, (int) (8 * density), pad / 2, pad);
		root.addView(github, btnLp);
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

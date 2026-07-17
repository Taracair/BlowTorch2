package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.R;
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
import android.widget.Toast;

/**
 * About / donate dialog (launcher + session overflow).
 * Crash log actions live in {@link CrashReportDialog}.
 */
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
		
		appendDonateSection();
	}

	private void appendDonateSection() {
		ScrollView scroll = findScrollView((ViewGroup) getWindow().getDecorView());
		if (scroll == null || scroll.getChildCount() == 0 || !(scroll.getChildAt(0) instanceof LinearLayout)) {
			return;
		}
		LinearLayout root = (LinearLayout) scroll.getChildAt(0);
		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (16 * density);

		TextView section = new TextView(getContext());
		section.setText("Donate");
		section.setTextSize(16f);
		section.setPadding(pad, pad, pad, (int) (4 * density));
		root.addView(section);

		Button donate = new Button(getContext());
		donate.setText("Donate (coming soon)");
		donate.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Toast.makeText(getContext(),
						"Donate link not configured yet.", Toast.LENGTH_SHORT).show();
			}
		});
		LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		btnLp.setMargins(pad / 2, 0, pad / 2, pad);
		root.addView(donate, btnLp);
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

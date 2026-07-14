package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.settings.ConfigurationLoader;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
			// TODO Auto-generated catch block
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
		
		appendLogPath();
	}
	
	private void appendLogPath() {
		ScrollView scroll = findScrollView((ViewGroup) getWindow().getDecorView());
		if (scroll == null || scroll.getChildCount() == 0 || !(scroll.getChildAt(0) instanceof LinearLayout)) {
			return;
		}
		TextView logInfo = new TextView(getContext());
		logInfo.setText("Error log:\n" + BlowTorchLogger.getLogFile(getContext()).getAbsolutePath());
		logInfo.setTextSize(12f);
		logInfo.setPadding(20, 16, 20, 8);
		((LinearLayout) scroll.getChildAt(0)).addView(logInfo);
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

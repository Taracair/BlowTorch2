package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.settings.ConfigurationLoader;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * About dialog (launcher + session overflow).
 * Crash log actions live in {@link CrashReportDialog}.
 */
public class AboutDialog extends Dialog {

	private static final String PROJECT_URL = "https://github.com/Taracair/BlowTorch2";

	public AboutDialog(Context context) {
		super(context, R.style.BlowTorch_Dialog);
	}

	public void onCreate(Bundle b) {
		super.onCreate(b);

		this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);

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

		int githubId = this.getContext().getResources().getIdentifier(
				"blowtorch_github", "id", this.getContext().getPackageName());
		if (githubId != 0) {
			View github = this.findViewById(githubId);
			if (github != null) {
				github.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL));
						AboutDialog.this.getContext().startActivity(web);
					}
				});
			}
		}

		Window window = getWindow();
		if (window != null) {
			int width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.92f);
			window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
			window.setGravity(Gravity.CENTER);
		}
	}

}

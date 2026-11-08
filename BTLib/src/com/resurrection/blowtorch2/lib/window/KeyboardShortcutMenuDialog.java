package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.R;

import android.app.Activity;
import android.app.Dialog;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

/** Keyboard behaviour that is not one shortcut. Number pad stays its own form. */
public final class KeyboardShortcutMenuDialog extends Dialog {

	public interface Host {
		void openPad();
	}

	private KeyboardShortcutMenuDialog(final Activity activity, final Host host) {
		super(activity, EditorDialogChrome.fullScreenTheme());
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		Window window = getWindow();
		if (window != null) {
			window.setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		}
		float density = activity.getResources().getDisplayMetrics().density;
		int pad = (int) (16 * density);
		LinearLayout shell = new LinearLayout(activity);
		shell.setOrientation(LinearLayout.VERTICAL);
		shell.setBackgroundColor(ContextCompat.getColor(activity, R.color.chrome_body));

		TextView title = new TextView(activity);
		title.setText("Keyboard");
		title.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		title.setBackgroundColor(ContextCompat.getColor(activity, R.color.chrome_title_bar));
		title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
		title.setGravity(Gravity.CENTER);
		title.setLayoutParams(new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, (int) (42 * density)));
		shell.addView(title);

		TextView body = new TextView(activity);
		body.setText(EditorHelp.emphasize(EditorHelp.KEYBOARD));
		body.setTextColor(ContextCompat.getColor(activity, R.color.chrome_title_text));
		body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
		body.setPadding(pad, pad, pad, pad);
		ScrollView scroll = new ScrollView(activity);
		scroll.addView(body, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		shell.addView(scroll, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		Button padButton = new Button(activity);
		padButton.setText("Number pad");
		padButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				dismiss();
				if (host != null) {
					host.openPad();
				}
			}
		});
		Button close = new Button(activity);
		close.setText("Close");
		close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				dismiss();
			}
		});
		LinearLayout footer = new LinearLayout(activity);
		footer.setOrientation(LinearLayout.HORIZONTAL);
		footer.setGravity(Gravity.CENTER);
		int barPad = (int) (6 * density);
		footer.setPadding(barPad, barPad, barPad, barPad);
		footer.setBackgroundColor(ContextCompat.getColor(activity, R.color.chrome_title_bar));
		footer.addView(padButton, new LinearLayout.LayoutParams(0,
				ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		footer.addView(close, new LinearLayout.LayoutParams(0,
				ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		shell.addView(footer);
		setContentView(shell, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		EditorDialogChrome.applyFullScreen(this);
	}

	public static void show(final Activity activity, final Host host) {
		if (activity == null) {
			return;
		}
		new KeyboardShortcutMenuDialog(activity, host).show();
	}
}

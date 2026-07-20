package com.resurrection.blowtorch2.lib.service.plugin.settings;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.window.EditorDialogChrome;

/**
 * MCP dns-org-mud-moo-simpleedit content editor. Save sends simpleedit-set.
 */
public final class McpSimpleEditDialog {

	private McpSimpleEditDialog() {
	}

	public static void show(final Context context, final IConnectionBinder service,
			final String reference, final String title, final String type, final String content) {
		if (context == null) {
			return;
		}
		final Dialog dialog = new Dialog(context, EditorDialogChrome.dialogTheme());
		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
				context.getResources().getDisplayMetrics());
		root.setPadding(pad, pad, pad, pad);

		TextView header = new TextView(context);
		header.setText(title != null && title.length() > 0 ? title : "MCP Edit");
		header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
		header.setPadding(0, 0, 0, pad / 2);
		root.addView(header);

		TextView meta = new TextView(context);
		meta.setText("ref: " + (reference != null ? reference : "")
				+ "   type: " + (type != null ? type : "string-list"));
		meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		meta.setPadding(0, 0, 0, pad / 2);
		root.addView(meta);

		final EditText body = new EditText(context);
		body.setTypeface(Typeface.MONOSPACE);
		body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		body.setMinLines(12);
		body.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
		body.setText(content != null ? content : "");
		LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
		ScrollView scroll = new ScrollView(context);
		scroll.addView(body, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		root.addView(scroll, bodyLp);

		LinearLayout buttons = new LinearLayout(context);
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		Button cancel = new Button(context);
		cancel.setText("Cancel");
		Button save = new Button(context);
		save.setText("Send");
		LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0,
				ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		buttons.addView(cancel, btnLp);
		buttons.addView(save, btnLp);
		root.addView(buttons);

		cancel.setOnClickListener(new android.view.View.OnClickListener() {
			@Override
			public void onClick(android.view.View v) {
				dialog.dismiss();
			}
		});
		save.setOnClickListener(new android.view.View.OnClickListener() {
			@Override
			public void onClick(android.view.View v) {
				if (service != null) {
					try {
						service.sendMcpSimpleEditSet(reference != null ? reference : "",
								type != null ? type : "string-list",
								body.getText() != null ? body.getText().toString() : "");
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				dialog.dismiss();
			}
		});

		dialog.setContentView(root);
		EditorDialogChrome.applyNearlyFullScreen(dialog);
		dialog.setCancelable(true);
		dialog.show();
	}
}

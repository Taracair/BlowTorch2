package com.resurrection.blowtorch2.lib.mapper;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.window.EditorDialogChrome;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Preview title/exits regex against the last N lines of buffer text, then Apply.
 */
public class MapperCapturePreviewDialog extends Dialog {

	private final MapperController controller;
	private final String bufferText;
	private EditText titleRegex;
	private EditText exitsRegex;
	private TextView previewView;
	private String previewTitle;
	private String previewExits;
	private String previewMatched = "";

	public MapperCapturePreviewDialog(Context context, MapperController controller,
			String bufferText) {
		super(context, EditorDialogChrome.dialogTheme());
		this.controller = controller;
		this.bufferText = bufferText != null ? bufferText : "";
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);

		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (12 * density);

		ScrollView scroll = new ScrollView(getContext());
		LinearLayout root = new LinearLayout(getContext());
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(pad, pad, pad, pad);
		scroll.addView(root);

		TextView heading = new TextView(getContext());
		heading.setText("Capture preview");
		heading.setTextSize(18f);
		heading.setTextColor(0xFFEEEEEE);
		root.addView(heading);

		TextView titleLabel = new TextView(getContext());
		titleLabel.setText("Title regex (group 1 optional)");
		titleLabel.setTextColor(0xFFBBBBBB);
		root.addView(titleLabel);

		titleRegex = new EditText(getContext());
		titleRegex.setHint("^([A-Z].*)$");
		titleRegex.setSingleLine(true);
		root.addView(titleRegex);

		TextView exitsLabel = new TextView(getContext());
		exitsLabel.setText("Exits regex");
		exitsLabel.setTextColor(0xFFBBBBBB);
		root.addView(exitsLabel);

		exitsRegex = new EditText(getContext());
		exitsRegex.setHint("(?i)exits?:\\s*(.*)");
		exitsRegex.setSingleLine(true);
		root.addView(exitsRegex);

		Button previewBtn = new Button(getContext());
		previewBtn.setText("Preview");
		previewBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				runPreview();
			}
		});
		root.addView(previewBtn);

		previewView = new TextView(getContext());
		previewView.setTextColor(0xFFCCCCCC);
		previewView.setTextSize(13f);
		previewView.setPadding(0, (int) (8 * density), 0, (int) (8 * density));
		previewView.setText("(run Preview on last buffer lines)");
		root.addView(previewView);

		LinearLayout actions = new LinearLayout(getContext());
		actions.setOrientation(LinearLayout.HORIZONTAL);

		Button apply = new Button(getContext());
		apply.setText("Apply");
		apply.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (previewTitle == null && previewExits == null) {
					runPreview();
				}
				if (previewTitle != null) {
					controller.setTitle(previewTitle);
				}
				if (previewExits != null) {
					MapTile cur = controller.currentTile();
					String notes = cur != null && cur.getNotes() != null ? cur.getNotes() : "";
					if (notes.length() > 0) {
						notes = notes + "\n";
					}
					controller.setNotes(notes + "Exits: " + previewExits);
				}
				Toast.makeText(getContext(), "Applied to current tile", Toast.LENGTH_SHORT).show();
				dismiss();
			}
		});
		actions.addView(apply);

		Button close = new Button(getContext());
		close.setText("Close");
		close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
		actions.addView(close);
		root.addView(actions);

		setContentView(scroll);
		EditorDialogChrome.applyNearlyFullScreen(this);
	}

	private void runPreview() {
		String[] lines = bufferText.split("\n", -1);
		int start = Math.max(0, lines.length - 40);
		StringBuilder shown = new StringBuilder();
		for (int i = start; i < lines.length; i++) {
			if (shown.length() > 0) {
				shown.append('\n');
			}
			shown.append(lines[i]);
		}
		previewMatched = shown.toString();
		previewTitle = null;
		previewExits = null;
		try {
			String tr = titleRegex.getText().toString();
			if (tr.length() > 0) {
				java.util.regex.Pattern p = java.util.regex.Pattern.compile(tr);
				for (int i = lines.length - 1; i >= start; i--) {
					java.util.regex.Matcher m = p.matcher(lines[i]);
					if (m.find()) {
						previewTitle = m.groupCount() >= 1 ? m.group(1) : m.group();
						break;
					}
				}
			}
		} catch (Throwable t) {
			previewTitle = "(bad title regex)";
		}
		try {
			String er = exitsRegex.getText().toString();
			if (er.length() > 0) {
				java.util.regex.Pattern p = java.util.regex.Pattern.compile(er);
				for (int i = lines.length - 1; i >= start; i--) {
					java.util.regex.Matcher m = p.matcher(lines[i]);
					if (m.find()) {
						previewExits = m.groupCount() >= 1 ? m.group(1) : m.group();
						break;
					}
				}
			}
		} catch (Throwable t) {
			previewExits = "(bad exits regex)";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("Matched buffer:\n").append(previewMatched);
		sb.append("\n\nTitle: ").append(previewTitle != null ? previewTitle : "(none)");
		sb.append("\nExits: ").append(previewExits != null ? previewExits : "(none)");
		previewView.setText(sb.toString());
	}
}

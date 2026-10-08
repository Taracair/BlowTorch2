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
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

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
		super(context, EditorDialogChrome.fullScreenTheme());
		this.controller = controller;
		this.bufferText = bufferText != null ? bufferText : "";
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setCanceledOnTouchOutside(false);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		setContentView(R.layout.mapper_form_shell);

		((TextView) findViewById(R.id.titlebar)).setText("Capture preview");
		LinearLayout root = (LinearLayout) findViewById(R.id.mapper_shell_body);

		TextView titleLabel = new TextView(getContext());
		titleLabel.setText("Title regex (group 1 optional)");
		titleLabel.setTextColor(ContextCompat.getColor(getContext(), R.color.chrome_description));
		root.addView(titleLabel);

		titleRegex = new EditText(getContext());
		titleRegex.setHint(MapperController.DEFAULT_CAPTURE_TITLE_REGEX);
		titleRegex.setSingleLine(true);
		String titlePrefill = controller != null ? controller.getCaptureTitleRegex() : null;
		titleRegex.setText(titlePrefill != null ? titlePrefill
				: MapperController.DEFAULT_CAPTURE_TITLE_REGEX);
		root.addView(titleRegex);

		TextView exitsLabel = new TextView(getContext());
		exitsLabel.setText("Exits regex");
		exitsLabel.setTextColor(ContextCompat.getColor(getContext(), R.color.chrome_description));
		root.addView(exitsLabel);

		exitsRegex = new EditText(getContext());
		exitsRegex.setHint(MapperController.DEFAULT_CAPTURE_EXITS_REGEX);
		exitsRegex.setSingleLine(true);
		String exitsPrefill = controller != null ? controller.getCaptureExitsRegex() : null;
		exitsRegex.setText(exitsPrefill != null ? exitsPrefill
				: MapperController.DEFAULT_CAPTURE_EXITS_REGEX);
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
		previewView.setTextColor(ContextCompat.getColor(getContext(), R.color.chrome_title_text));
		previewView.setTextSize(13f);
		previewView.setPadding(0, dip(getContext(), 8), 0, dip(getContext(), 8));
		previewView.setText("(run Preview on last buffer lines)");
		root.addView(previewView);

		LinearLayout footer = (LinearLayout) findViewById(R.id.button_row);
		Button cancel = barButton(getContext(), "Cancel", false);
		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
		footer.addView(cancel);

		Button done = barButton(getContext(), "Done", true);
		done.setOnClickListener(new View.OnClickListener() {
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
		footer.addView(done);

		EditorDialogChrome.applyFullScreen(this);
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

	private static int dip(Context context, int dips) {
		return Math.round(dips * context.getResources().getDisplayMetrics().density);
	}

	private static Button barButton(Context context, String label, boolean gap) {
		Button button = new Button(context);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		if (gap) {
			lp.leftMargin = dip(context, 6);
		}
		button.setMinHeight(dip(context, 44));
		button.setSingleLine(false);
		button.setMaxLines(2);
		button.setLayoutParams(lp);
		button.setText(label);
		return button;
	}
}

package com.resurrection.blowtorch2.lib.mapper;

import java.util.LinkedHashMap;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.window.EditorDialogChrome;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Edit the mapper movement lexicon: what each typed command does on the map
 * ({@code grid}, {@code level}, or {@code special}).
 */
public class MapMoveEffectsDialog extends Dialog {

	public interface Listener {
		/** Persist combined table on the connection mapper (service). */
		void onSaveCombinedTable(String combinedTable);
	}

	private final MapperController controller;
	private final Listener listener;
	private EditText tableEdit;

	public MapMoveEffectsDialog(Context context, MapperController controller,
			Listener listener) {
		super(context, EditorDialogChrome.dialogTheme());
		this.controller = controller;
		this.listener = listener;
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
		heading.setText("Mapper moves");
		heading.setTextSize(18f);
		heading.setTextColor(0xFFEEEEEE);
		root.addView(heading);

		TextView help = new TextView(getContext());
		help.setText("One command per line:\n"
				+ "  n=grid:0:-1   (+x east, +y south)\n"
				+ "  u=level:1     (floor +1 / −1)\n"
				+ "  out=special   (off-grid neighbor)\n"
				+ "Also: Options → Mapper, or .map moves");
		help.setTextSize(12f);
		help.setTextColor(0xFFBBBBBB);
		help.setPadding(0, pad / 2, 0, pad);
		root.addView(help);

		tableEdit = new EditText(getContext());
		tableEdit.setText(controller != null
				? controller.getCombinedMoveEffectsDisplay()
				: MapDirections.serializeMoveEffects(
						MapDirections.defaultMoveEffects(), false));
		tableEdit.setMinLines(14);
		tableEdit.setGravity(Gravity.TOP | Gravity.START);
		tableEdit.setTextSize(13f);
		tableEdit.setTypeface(android.graphics.Typeface.MONOSPACE);
		root.addView(tableEdit);

		LinearLayout buttons = new LinearLayout(getContext());
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		buttons.setPadding(0, pad, 0, 0);

		Button reset = new Button(getContext());
		reset.setText("Reset");
		reset.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				LinkedHashMap<String, MapMoveEffect> map =
						MapDirections.defaultMoveEffects();
				MapDirections.applyLevelCommands(map,
						MapDirections.DEFAULT_LEVEL_UP_COMMANDS,
						MapDirections.DEFAULT_LEVEL_DOWN_COMMANDS);
				tableEdit.setText(MapDirections.serializeMoveEffects(map, false));
			}
		});
		buttons.addView(reset);

		Button cancel = new Button(getContext());
		cancel.setText("Cancel");
		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
		buttons.addView(cancel);

		Button save = new Button(getContext());
		save.setText("Save");
		save.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String text = tableEdit.getText() != null
						? tableEdit.getText().toString() : "";
				if (MapDirections.parseMoveEffects(text).isEmpty()
						&& text.trim().length() > 0) {
					Toast.makeText(getContext(),
							"No valid lines — use cmd=grid:dx:dy|level:N|special",
							Toast.LENGTH_LONG).show();
					return;
				}
				if (listener != null) {
					listener.onSaveCombinedTable(text);
				}
				dismiss();
			}
		});
		buttons.addView(save);
		root.addView(buttons);

		setContentView(scroll);
	}
}

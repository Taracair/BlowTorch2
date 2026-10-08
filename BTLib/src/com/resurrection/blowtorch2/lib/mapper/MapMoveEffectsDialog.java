package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.window.EditorDialogChrome;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

/**
 * Friendly editor for the mapper movement lexicon: list of commands with
 * tap-to-edit, add, delete, and reset — no raw {@code n=grid:0:-1} syntax
 * required.
 */
public class MapMoveEffectsDialog extends Dialog {

	public interface Listener {
		/** Persist combined table on the connection mapper (service). */
		void onSaveCombinedTable(String combinedTable);
	}

	private static final class Row {
		String command;
		MapMoveEffect effect;

		Row(String command, MapMoveEffect effect) {
			this.command = command;
			this.effect = effect;
		}
	}

	/** Preset compass steps shown in the editor spinner. */
	private static final String[] COMPASS_LABELS = {
			"North", "South", "East", "West",
			"Northeast", "Northwest", "Southeast", "Southwest"
	};
	private static final int[][] COMPASS_DELTAS = {
			{ 0, -1 }, { 0, 1 }, { 1, 0 }, { -1, 0 },
			{ 1, -1 }, { -1, -1 }, { 1, 1 }, { -1, 1 }
	};

	private static final String[] KIND_LABELS = {
			"Compass (same floor)",
			"Up one floor",
			"Down one floor",
			"Special (off-grid / portal)",
			"Custom grid step"
	};

	private final Listener listener;
	private final ArrayList<Row> rows = new ArrayList<Row>();
	private ArrayAdapter<String> listAdapter;
	private ListView listView;
	private TextView emptyHint;
	/** Combined table text from snapshot / controller (may be empty → defaults). */
	private final String initialTable;

	public MapMoveEffectsDialog(Context context, MapperController controller,
			Listener listener) {
		this(context,
				controller != null ? controller.getCombinedMoveEffectsDisplay() : null,
				listener);
	}

	/**
	 * @param initialCombinedTable serialized effects (one per line or {@code ;}),
	 *        or null/empty to load built-in defaults
	 */
	public MapMoveEffectsDialog(Context context, String initialCombinedTable,
			Listener listener) {
		super(context, EditorDialogChrome.fullScreenTheme());
		this.listener = listener;
		this.initialTable = initialCombinedTable;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setCanceledOnTouchOutside(false);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		setContentView(R.layout.mapper_list_shell);

		((TextView) findViewById(R.id.titlebar)).setText("Mapper moves");
		LinearLayout body = (LinearLayout) findViewById(R.id.mapper_shell_body);
		final int pad = dip(getContext(), 12);

		TextView help = new TextView(getContext());
		help.setText("What each typed command does while recording.\n"
				+ "Tap a row to edit · long-press to delete.");
		help.setTextSize(12f);
		help.setTextColor(ContextCompat.getColor(getContext(), R.color.chrome_description));
		help.setPadding(pad, pad, pad, pad / 2);
		body.addView(help);

		emptyHint = new TextView(getContext());
		emptyHint.setText("No moves yet — tap Add or Reset to defaults.");
		emptyHint.setTextColor(ContextCompat.getColor(getContext(), R.color.chrome_hint));
		emptyHint.setPadding(pad, pad, pad, pad);
		emptyHint.setVisibility(View.GONE);
		body.addView(emptyHint);

		listView = new ListView(getContext());
		styleList(listView);
		body.addView(listView, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		listAdapter = new ArrayAdapter<String>(getContext(),
				android.R.layout.simple_list_item_1, new ArrayList<String>()) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				TextView tv = (TextView) super.getView(position, convertView, parent);
				tv.setTextColor(ContextCompat.getColor(getContext(), R.color.chrome_title_text));
				tv.setTextSize(14f);
				tv.setPadding(pad, pad, pad, pad);
				return tv;
			}
		};
		listView.setAdapter(listAdapter);
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position,
					long id) {
				if (position >= 0 && position < rows.size()) {
					openRowEditor(rows.get(position), position);
				}
			}
		});
		listView.setOnItemLongClickListener(
				new AdapterView.OnItemLongClickListener() {
					@Override
					public boolean onItemLongClick(AdapterView<?> parent, View view,
							int position, long id) {
						confirmDelete(position);
						return true;
					}
				});

		LinearLayout topBar = new LinearLayout(getContext());
		topBar.setOrientation(LinearLayout.HORIZONTAL);
		topBar.setPadding(pad, pad / 2, pad, pad / 2);

		Button add = new Button(getContext());
		add.setText("Add");
		add.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				openRowEditor(null, -1);
			}
		});
		topBar.addView(add);

		Button reset = new Button(getContext());
		reset.setText("Reset");
		reset.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				confirmReset();
			}
		});
		topBar.addView(reset);
		body.addView(topBar);

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
				saveAndClose();
			}
		});
		footer.addView(done);

		EditorDialogChrome.applyFullScreen(this);

		loadFromController();
		refreshList();
	}

	private void loadFromController() {
		rows.clear();
		LinkedHashMap<String, MapMoveEffect> source =
				MapDirections.parseMoveEffects(initialTable);
		if (source.isEmpty()) {
			source = MapDirections.defaultMoveEffects();
			MapDirections.applyLevelCommands(source,
					MapDirections.DEFAULT_LEVEL_UP_COMMANDS,
					MapDirections.DEFAULT_LEVEL_DOWN_COMMANDS);
		}
		for (Map.Entry<String, MapMoveEffect> e : source.entrySet()) {
			if (e.getKey() == null || e.getValue() == null) {
				continue;
			}
			rows.add(new Row(e.getKey(), e.getValue()));
		}
	}

	private void refreshList() {
		ArrayList<String> labels = new ArrayList<String>();
		for (Row row : rows) {
			labels.add(formatRow(row));
		}
		listAdapter.clear();
		listAdapter.addAll(labels);
		listAdapter.notifyDataSetChanged();
		boolean empty = rows.isEmpty();
		emptyHint.setVisibility(empty ? View.VISIBLE : View.GONE);
		listView.setVisibility(empty ? View.GONE : View.VISIBLE);
	}

	private static String formatRow(Row row) {
		String cmd = row.command != null ? row.command : "?";
		return cmd + "  →  " + humanEffect(row.effect);
	}

	static String humanEffect(MapMoveEffect fx) {
		if (fx == null) {
			return "?";
		}
		if (fx.kind == MapMoveEffect.Kind.SPECIAL) {
			return "Special (off-grid)";
		}
		if (fx.kind == MapMoveEffect.Kind.LEVEL) {
			if (fx.levelDelta > 0) {
				return "Up " + fx.levelDelta + " floor"
						+ (fx.levelDelta == 1 ? "" : "s");
			}
			if (fx.levelDelta < 0) {
				int n = -fx.levelDelta;
				return "Down " + n + " floor" + (n == 1 ? "" : "s");
			}
			return "Level 0 (no change)";
		}
		String compass = compassName(fx.dx, fx.dy);
		if (compass != null) {
			return compass + " (same floor)";
		}
		return "Grid +" + fx.dx + " east, +" + fx.dy + " south";
	}

	private static String compassName(int dx, int dy) {
		for (int i = 0; i < COMPASS_DELTAS.length; i++) {
			if (COMPASS_DELTAS[i][0] == dx && COMPASS_DELTAS[i][1] == dy) {
				return COMPASS_LABELS[i];
			}
		}
		return null;
	}

	private static int compassIndex(int dx, int dy) {
		for (int i = 0; i < COMPASS_DELTAS.length; i++) {
			if (COMPASS_DELTAS[i][0] == dx && COMPASS_DELTAS[i][1] == dy) {
				return i;
			}
		}
		return 0;
	}

	private void confirmDelete(final int position) {
		if (position < 0 || position >= rows.size()) {
			return;
		}
		final Row row = rows.get(position);
		showConfirm("Delete move?",
				"Remove \"" + row.command + "\" → " + humanEffect(row.effect) + "?",
				"Delete", new Runnable() {
					@Override
					public void run() {
						rows.remove(position);
						refreshList();
					}
				});
	}

	private void confirmReset() {
		showConfirm("Reset to defaults?",
				"Replace the list with the built-in compass, "
						+ "up/down, and specials (in/out).",
				"Reset", new Runnable() {
					@Override
					public void run() {
						rows.clear();
						LinkedHashMap<String, MapMoveEffect> def =
								MapDirections.defaultMoveEffects();
						MapDirections.applyLevelCommands(def,
								MapDirections.DEFAULT_LEVEL_UP_COMMANDS,
								MapDirections.DEFAULT_LEVEL_DOWN_COMMANDS);
						for (Map.Entry<String, MapMoveEffect> e : def.entrySet()) {
							rows.add(new Row(e.getKey(), e.getValue()));
						}
						refreshList();
					}
				});
	}

	private void openRowEditor(final Row existing, final int editIndex) {
		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (10 * density);

		LinearLayout form = new LinearLayout(getContext());
		form.setOrientation(LinearLayout.VERTICAL);
		form.setPadding(0, 0, 0, 0);

		TextView cmdLabel = new TextView(getContext());
		cmdLabel.setText("Command you type (e.g. n, north, out, climb)");
		cmdLabel.setTextColor(0xFFCCCCCC);
		form.addView(cmdLabel);

		final EditText cmdEdit = new EditText(getContext());
		cmdEdit.setSingleLine(true);
		cmdEdit.setHint("command");
		if (existing != null && existing.command != null) {
			cmdEdit.setText(existing.command);
		}
		form.addView(cmdEdit);

		TextView kindLabel = new TextView(getContext());
		kindLabel.setText("What it does on the map");
		kindLabel.setTextColor(0xFFCCCCCC);
		kindLabel.setPadding(0, pad, 0, 0);
		form.addView(kindLabel);

		final Spinner kindSpinner = new Spinner(getContext());
		kindSpinner.setAdapter(new ArrayAdapter<String>(getContext(),
				android.R.layout.simple_spinner_dropdown_item, KIND_LABELS));
		form.addView(kindSpinner);

		final LinearLayout compassBox = new LinearLayout(getContext());
		compassBox.setOrientation(LinearLayout.VERTICAL);
		TextView compassLabel = new TextView(getContext());
		compassLabel.setText("Direction");
		compassLabel.setTextColor(0xFFCCCCCC);
		compassBox.addView(compassLabel);
		final Spinner compassSpinner = new Spinner(getContext());
		compassSpinner.setAdapter(new ArrayAdapter<String>(getContext(),
				android.R.layout.simple_spinner_dropdown_item, COMPASS_LABELS));
		compassBox.addView(compassSpinner);
		form.addView(compassBox);

		final LinearLayout customBox = new LinearLayout(getContext());
		customBox.setOrientation(LinearLayout.VERTICAL);
		TextView customHelp = new TextView(getContext());
		customHelp.setText("Grid step: +X = east, +Y = south");
		customHelp.setTextColor(0xFFCCCCCC);
		customBox.addView(customHelp);
		LinearLayout xy = new LinearLayout(getContext());
		xy.setOrientation(LinearLayout.HORIZONTAL);
		final EditText dxEdit = new EditText(getContext());
		dxEdit.setHint("dx");
		dxEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
				| android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
		dxEdit.setEms(4);
		final EditText dyEdit = new EditText(getContext());
		dyEdit.setHint("dy");
		dyEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
				| android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
		dyEdit.setEms(4);
		xy.addView(dxEdit);
		xy.addView(dyEdit);
		customBox.addView(xy);
		form.addView(customBox);

		// Initial kind / fields from existing row
		int kindSel = 0;
		if (existing != null && existing.effect != null) {
			MapMoveEffect fx = existing.effect;
			if (fx.kind == MapMoveEffect.Kind.LEVEL) {
				kindSel = fx.levelDelta >= 0 ? 1 : 2;
			} else if (fx.kind == MapMoveEffect.Kind.SPECIAL) {
				kindSel = 3;
			} else if (fx.kind == MapMoveEffect.Kind.GRID) {
				if (compassName(fx.dx, fx.dy) != null) {
					kindSel = 0;
					compassSpinner.setSelection(compassIndex(fx.dx, fx.dy));
				} else {
					kindSel = 4;
					dxEdit.setText(Integer.toString(fx.dx));
					dyEdit.setText(Integer.toString(fx.dy));
				}
			}
		}
		kindSpinner.setSelection(kindSel);
		Runnable syncVis = new Runnable() {
			@Override
			public void run() {
				int k = kindSpinner.getSelectedItemPosition();
				compassBox.setVisibility(k == 0 ? View.VISIBLE : View.GONE);
				customBox.setVisibility(k == 4 ? View.VISIBLE : View.GONE);
			}
		};
		syncVis.run();
		kindSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				syncVis.run();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		final Dialog dlg = new Dialog(getContext(), EditorDialogChrome.dialogTheme());
		dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
		Window window = dlg.getWindow();
		if (window != null) {
			window.setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		}
		dlg.setContentView(R.layout.mapper_form_shell);
		((TextView) dlg.findViewById(R.id.titlebar)).setText(
				existing == null ? "Add move" : "Edit move");
		((LinearLayout) dlg.findViewById(R.id.mapper_shell_body)).addView(form);
		LinearLayout footer = (LinearLayout) dlg.findViewById(R.id.button_row);
		Button cancel = barButton(getContext(), "Cancel", false);
		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dlg.dismiss();
			}
		});
		Button commit = barButton(getContext(),
				existing == null ? "Add" : "Apply", true);
		commit.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String cmd = cmdEdit.getText() != null
						? cmdEdit.getText().toString().trim()
								.toLowerCase(Locale.US)
						: "";
				if (cmd.length() == 0) {
					Toast.makeText(getContext(), "Enter a command",
							Toast.LENGTH_SHORT).show();
					return;
				}
				MapMoveEffect fx = effectFromForm(
						kindSpinner.getSelectedItemPosition(),
						compassSpinner.getSelectedItemPosition(),
						dxEdit, dyEdit);
				if (fx == null) {
					Toast.makeText(getContext(),
							"Enter valid dx / dy numbers",
							Toast.LENGTH_SHORT).show();
					return;
				}
				// Replace duplicate command (case-insensitive)
				int dup = -1;
				for (int i = 0; i < rows.size(); i++) {
					if (i == editIndex) {
						continue;
					}
					if (cmd.equalsIgnoreCase(rows.get(i).command)) {
						dup = i;
						break;
					}
				}
				if (dup >= 0) {
					rows.set(dup, new Row(cmd, fx));
					if (editIndex >= 0 && editIndex != dup) {
						rows.remove(editIndex);
					}
				} else if (editIndex >= 0 && editIndex < rows.size()) {
					rows.set(editIndex, new Row(cmd, fx));
				} else {
					rows.add(new Row(cmd, fx));
				}
				refreshList();
				dlg.dismiss();
			}
		});
		footer.addView(cancel);
		footer.addView(commit);
		EditorDialogChrome.applyFloatingWrapContentHeight(dlg);
		dlg.show();
	}

	private MapMoveEffect effectFromForm(int kindPos, int compassPos,
			EditText dxEdit, EditText dyEdit) {
		switch (kindPos) {
		case 1:
			return MapMoveEffect.level(1);
		case 2:
			return MapMoveEffect.level(-1);
		case 3:
			return MapMoveEffect.special();
		case 4:
			try {
				int dx = Integer.parseInt(dxEdit.getText().toString().trim());
				int dy = Integer.parseInt(dyEdit.getText().toString().trim());
				return MapMoveEffect.grid(dx, dy);
			} catch (Exception e) {
				return null;
			}
		case 0:
		default:
			if (compassPos < 0 || compassPos >= COMPASS_DELTAS.length) {
				compassPos = 0;
			}
			return MapMoveEffect.grid(COMPASS_DELTAS[compassPos][0],
					COMPASS_DELTAS[compassPos][1]);
		}
	}

	private void saveAndClose() {
		LinkedHashMap<String, MapMoveEffect> map =
				new LinkedHashMap<String, MapMoveEffect>();
		for (Row row : rows) {
			if (row.command != null && row.effect != null) {
				map.put(row.command.trim().toLowerCase(Locale.US), row.effect);
			}
		}
		String combined = MapDirections.serializeMoveEffects(map, false);
		if (listener != null) {
			listener.onSaveCombinedTable(combined);
		}
		dismiss();
	}

	private void showConfirm(String title, String message, String positive,
			final Runnable onPositive) {
		final Dialog dialog = new Dialog(getContext(), EditorDialogChrome.dialogTheme());
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		Window window = dialog.getWindow();
		if (window != null) {
			window.setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		}
		dialog.setContentView(R.layout.mapper_form_shell);
		((TextView) dialog.findViewById(R.id.titlebar)).setText(title);
		TextView msg = new TextView(getContext());
		msg.setText(message);
		msg.setTextColor(ContextCompat.getColor(getContext(), R.color.chrome_title_text));
		msg.setTextSize(15f);
		((LinearLayout) dialog.findViewById(R.id.mapper_shell_body)).addView(msg);
		LinearLayout footer = (LinearLayout) dialog.findViewById(R.id.button_row);
		Button cancel = barButton(getContext(), "Cancel", false);
		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		Button ok = barButton(getContext(), positive, true);
		ok.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (onPositive != null) {
					onPositive.run();
				}
				dialog.dismiss();
			}
		});
		footer.addView(cancel);
		footer.addView(ok);
		EditorDialogChrome.applyFloatingWrapContentHeight(dialog);
		dialog.show();
	}

	private void styleList(ListView list) {
		list.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.chrome_body));
		list.setCacheColorHint(0x00000000);
		list.setDivider(ContextCompat.getDrawable(getContext(), R.drawable.editor_row_divider));
		list.setDividerHeight(Math.max(1, dip(getContext(), 1)));
		list.setSelector(R.drawable.blue_frame_nomargin_nobackground);
		list.setScrollbarFadingEnabled(false);
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

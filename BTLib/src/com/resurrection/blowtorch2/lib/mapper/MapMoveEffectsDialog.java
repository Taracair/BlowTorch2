package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.window.EditorDialogChrome;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
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
		super(context, EditorDialogChrome.dialogTheme());
		this.listener = listener;
		this.initialTable = initialCombinedTable;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);

		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (12 * density);

		LinearLayout root = new LinearLayout(getContext());
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(pad, pad, pad, pad);

		TextView heading = new TextView(getContext());
		heading.setText("Mapper moves");
		heading.setTextSize(18f);
		heading.setTextColor(0xFFEEEEEE);
		root.addView(heading);

		TextView help = new TextView(getContext());
		help.setText("What each typed command does while recording.\n"
				+ "Tap a row to edit · long-press to delete.");
		help.setTextSize(12f);
		help.setTextColor(0xFFBBBBBB);
		help.setPadding(0, pad / 2, 0, pad / 2);
		root.addView(help);

		emptyHint = new TextView(getContext());
		emptyHint.setText("No moves yet — tap Add or Reset to defaults.");
		emptyHint.setTextColor(0xFF888888);
		emptyHint.setPadding(0, pad, 0, pad);
		emptyHint.setVisibility(View.GONE);
		root.addView(emptyHint);

		listView = new ListView(getContext());
		listView.setDividerHeight((int) (1 * density));
		LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
		root.addView(listView, listLp);

		listAdapter = new ArrayAdapter<String>(getContext(),
				android.R.layout.simple_list_item_1, new ArrayList<String>()) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				TextView tv = (TextView) super.getView(position, convertView, parent);
				tv.setTextColor(0xFFEEEEEE);
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
		topBar.setPadding(0, pad / 2, 0, 0);

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
		root.addView(topBar);

		LinearLayout bottom = new LinearLayout(getContext());
		bottom.setOrientation(LinearLayout.HORIZONTAL);
		bottom.setPadding(0, pad / 2, 0, 0);
		bottom.setGravity(Gravity.END);

		Button cancel = new Button(getContext());
		cancel.setText("Cancel");
		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
		bottom.addView(cancel);

		Button save = new Button(getContext());
		save.setText("Save");
		save.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				saveAndClose();
			}
		});
		bottom.addView(save);
		root.addView(bottom);

		setContentView(root);
		EditorDialogChrome.applyNearlyFullScreen(this);

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
		new AlertDialog.Builder(getContext())
				.setTitle("Delete move?")
				.setMessage("Remove \"" + row.command + "\" → "
						+ humanEffect(row.effect) + "?")
				.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						rows.remove(position);
						refreshList();
					}
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void confirmReset() {
		new AlertDialog.Builder(getContext())
				.setTitle("Reset to defaults?")
				.setMessage("Replace the list with the built-in compass, "
						+ "up/down, and specials (in/out).")
				.setPositiveButton("Reset", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
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
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void openRowEditor(final Row existing, final int editIndex) {
		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (10 * density);

		LinearLayout form = new LinearLayout(getContext());
		form.setOrientation(LinearLayout.VERTICAL);
		form.setPadding(pad, pad, pad, pad);

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

		AlertDialog.Builder b = new AlertDialog.Builder(getContext());
		b.setTitle(existing == null ? "Add move" : "Edit move");
		b.setView(form);
		b.setPositiveButton(existing == null ? "Add" : "Apply", null);
		b.setNegativeButton("Cancel", null);
		final AlertDialog dlg = b.create();
		dlg.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override
			public void onShow(DialogInterface dialog) {
				Button ok = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
				ok.setOnClickListener(new View.OnClickListener() {
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
			}
		});
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
}

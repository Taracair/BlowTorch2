/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.service.plugin.settings;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.resurrection.blowtorch2.lib.button.ColorPickerDialog;
import com.resurrection.blowtorch2.lib.gauge.GaugeSpawnPlacement;
import com.resurrection.blowtorch2.lib.gauge.GaugeWidget;
import com.resurrection.blowtorch2.lib.gauge.GaugeWidgetsStore;

/**
 * Manage overlay gauges: list / add / delete / edit. Persist via
 * {@link Host#applyJson(String, boolean)}. Widgets are player-created; this
 * dialog does not mint them from GMCP.
 */
public final class GaugeWidgetsDialog {

	public interface Host {
		String getJson();

		boolean isEnabled();

		void applyJson(String json, boolean enabled);
	}

	private static final String[] SHAPES = new String[] { "hbar", "vbar", "ring", "timer" };
	private static final String[] SOURCES = new String[] {
			"manual", "gmcp", "mcp", "var", "timer", "regex"
	};
	private static final String[] IME_MODES = new String[] { "stay", "hide", "overlay" };

	static final String EDIT_HELP =
			"Where the numbers come from\n\n"
			+ "GMCP: out-of-band vitals. Path is a dotted key, e.g. Char.Vitals.hp "
			+ "and Char.Vitals.maxhp. Options → Service → Protocols → Use GMCP?.\n\n"
			+ "MCP: in-band #$# messages (MOOs). Keys are names in the status cache, "
			+ "not the protocol line — a line-regex will not see #$#dns-org-hellmoo-status-update "
			+ "when Use MCP? is on. HellMOO status-update keys: hp, maxhp, thirst, hunger, stress. "
			+ "Example: .widget source hp mcp hp maxhp. Options → Service → Protocols / .mcp.\n\n"
			+ "Variable: session names from Set Variable (or Lua SetVariable). "
			+ "Example: .widget source hp var hp maxhp.\n\n"
			+ "Regex: matches visible game text (prompts, score lines). Capture group 1 "
			+ "must parse as a number. Optional max regex, or two groups in the value regex "
			+ "for value/max (HP: 80/100). Quote regexes that contain spaces:\n"
			+ "  .widget source hp regex \"HP: (\\d+)/(\\d+)\"\n\n"
			+ "Timer: a client .timer by name (path only). The timer editor can mint this "
			+ "with Show as overlay widget.\n\n"
			+ "Manual: type .widget set <id> 80 100 (or 80/100).\n\n"
			+ "Long-press a gauge to enter edit mode (yellow border, resize corner). "
			+ "Drag to move, corner to resize, tap to leave edit. Tap and swipe still run "
			+ "commands when you are not editing; long-press does not fire hold.";

	private GaugeWidgetsDialog() {
	}

	public static void show(final Context context, final Host host) {
		if (context == null || host == null) {
			return;
		}

		int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
				context.getResources().getDisplayMetrics());

		final ArrayList<GaugeWidget> gauges = GaugeWidgetsStore.parse(host.getJson());
		GaugeWidgetsStore.validate(gauges);

		ScrollView scroll = new ScrollView(context);
		final LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(pad, pad, pad, pad);
		scroll.addView(root);

		TextView intro = new TextView(context);
		intro.setText("Overlay gauges (max " + GaugeWidgetsStore.MAX
				+ "). Ids: lowercase a-z, 0-9, _. "
				+ "Shapes: hbar, vbar, ring, timer. "
				+ "Sources: manual, gmcp, mcp, var, timer, regex. "
				+ "Long-press a gauge to move/resize it.");
		intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		intro.setPadding(0, 0, 0, pad);
		root.addView(intro);

		final LinearLayout list = new LinearLayout(context);
		list.setOrientation(LinearLayout.VERTICAL);
		root.addView(list);

		final Runnable[] refreshHolder = new Runnable[1];
		refreshHolder[0] = new Runnable() {
			@Override
			public void run() {
				list.removeAllViews();
				if (gauges.isEmpty()) {
					TextView empty = new TextView(context);
					empty.setText("(no widgets yet)");
					list.addView(empty);
					return;
				}
				for (int i = 0; i < gauges.size(); i++) {
					final GaugeWidget widget = gauges.get(i);
					if (widget == null) {
						continue;
					}
					LinearLayout row = new LinearLayout(context);
					row.setOrientation(LinearLayout.VERTICAL);
					row.setPadding(0, 0, 0, pad / 2);

					TextView title = new TextView(context);
					title.setText(widget.getId()
							+ " — " + widget.getShape().toJsonValue()
							+ " [" + widget.getSource().toJsonValue() + "]"
							+ " " + widget.getOpacity() + "%"
							+ (widget.isVisible() ? "" : " (hidden)"));
					title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
					row.addView(title);

					LinearLayout buttons = new LinearLayout(context);
					buttons.setOrientation(LinearLayout.HORIZONTAL);

					Button edit = new Button(context);
					edit.setText("Edit");
					edit.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							editWidget(context, gauges, widget, refreshHolder[0]);
						}
					});
					buttons.addView(edit);

					Button del = new Button(context);
					del.setText("Delete");
					del.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							confirmDelete(context, gauges, widget, refreshHolder[0]);
						}
					});
					buttons.addView(del);

					row.addView(buttons);
					list.addView(row);
				}
			}
		};
		refreshHolder[0].run();

		Button add = new Button(context);
		add.setText("Add widget…");
		add.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (gauges.size() >= GaugeWidgetsStore.MAX) {
					Toast.makeText(context, "Maximum " + GaugeWidgetsStore.MAX
							+ " widgets.", Toast.LENGTH_SHORT).show();
					return;
				}
				editWidget(context, gauges, null, refreshHolder[0]);
			}
		});
		root.addView(add);

		AlertDialog.Builder b = new AlertDialog.Builder(context);
		b.setTitle("Manage widgets");
		b.setView(scroll);
		b.setNegativeButton("Cancel", null);
		b.setPositiveButton("Apply", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				GaugeWidgetsStore.validate(gauges);
				String json = GaugeWidgetsStore.toJson(gauges);
				host.applyJson(json, host.isEnabled());
			}
		});
		b.show();
	}

	private static void confirmDelete(final Context context,
			final ArrayList<GaugeWidget> gauges, final GaugeWidget widget,
			final Runnable onDone) {
		if (widget == null) {
			return;
		}
		String id = widget.getId() != null ? widget.getId() : "";
		new AlertDialog.Builder(context)
				.setMessage("Delete widget '" + id + "'?")
				.setNegativeButton("Cancel", null)
				.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						gauges.remove(widget);
						onDone.run();
					}
				})
				.show();
	}

	private static void editWidget(final Context context,
			final ArrayList<GaugeWidget> gauges, final GaugeWidget existing,
			final Runnable onDone) {
		int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
				context.getResources().getDisplayMetrics());
		ScrollView scroll = new ScrollView(context);
		LinearLayout form = new LinearLayout(context);
		form.setOrientation(LinearLayout.VERTICAL);
		form.setPadding(pad, pad, pad, pad);
		scroll.addView(form);

		Button helpBtn = new Button(context);
		helpBtn.setText("?");
		helpBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				new AlertDialog.Builder(context)
						.setTitle("Widget sources")
						.setMessage(EDIT_HELP)
						.setPositiveButton("OK", null)
						.show();
			}
		});
		form.addView(helpBtn);

		final EditText idField = new EditText(context);
		idField.setHint("id (hp, mana, …)");
		idField.setSingleLine(true);
		if (existing != null) {
			idField.setText(existing.getId());
		}
		form.addView(label(context, "Id"));
		form.addView(idField);

		final Spinner shape = spinner(context, SHAPES);
		selectValue(shape, SHAPES, existing != null
				? existing.getShape().toJsonValue() : "hbar");
		form.addView(label(context, "Shape"));
		form.addView(shape);

		final Spinner source = spinner(context, SOURCES);
		selectValue(source, SOURCES, existing != null
				? existing.getSource().toJsonValue() : "manual");
		form.addView(label(context, "Source"));
		form.addView(source);

		final TextView pathLabel = label(context, "Path");
		final EditText path = new EditText(context);
		path.setSingleLine(true);
		if (existing != null) {
			path.setText(existing.getPath());
		}
		form.addView(pathLabel);
		form.addView(path);

		final TextView maxPathLabel = label(context, "Max path");
		final EditText maxPath = new EditText(context);
		maxPath.setSingleLine(true);
		if (existing != null) {
			maxPath.setText(existing.getMaxPath());
		}
		form.addView(maxPathLabel);
		form.addView(maxPath);
		source.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position,
					long id) {
				applySourceFieldLabels((String) source.getSelectedItem(),
						pathLabel, path, maxPathLabel, maxPath);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
		applySourceFieldLabels((String) source.getSelectedItem(),
				pathLabel, path, maxPathLabel, maxPath);

		final EditText color = new EditText(context);
		color.setHint("#CC2222");
		color.setSingleLine(true);
		color.setText(GaugeWidget.formatColor(existing != null
				? existing.getColorFill() : GaugeWidget.DEFAULT_COLOR_FILL));
		form.addView(label(context, "Color"));
		LinearLayout colorRow = new LinearLayout(context);
		colorRow.setOrientation(LinearLayout.HORIZONTAL);
		LinearLayout.LayoutParams colorLp = new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		colorRow.addView(color, colorLp);
		Button pick = new Button(context);
		pick.setText("Pick…");
		pick.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String raw = color.getText() != null ? color.getText().toString() : "";
				int current = GaugeWidget.parseColor(raw, GaugeWidget.DEFAULT_COLOR_FILL);
				ColorPickerDialog d = new ColorPickerDialog(context,
						new ColorPickerDialog.OnColorChangedListener() {
							@Override
							public void colorChanged(int c) {
								color.setText(GaugeWidget.formatColor(c));
							}
						}, current);
				d.show();
			}
		});
		colorRow.addView(pick);
		form.addView(colorRow);

		final EditText opacity = new EditText(context);
		opacity.setHint("opacity 10–100");
		opacity.setSingleLine(true);
		opacity.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
		opacity.setText(Integer.toString(existing != null
				? existing.getOpacity() : GaugeWidget.DEFAULT_OPACITY));
		form.addView(label(context, "Opacity % (10–100)"));
		form.addView(opacity);

		final EditText warnPct = new EditText(context);
		warnPct.setHint("warn %");
		warnPct.setSingleLine(true);
		warnPct.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
		warnPct.setText(Integer.toString(existing != null
				? existing.getWarnPct() : GaugeWidget.DEFAULT_WARN_PCT));
		form.addView(label(context, "Warn %"));
		form.addView(warnPct);

		final Spinner ime = spinner(context, IME_MODES);
		selectValue(ime, IME_MODES, existing != null
				? existing.getImeMode().toJsonValue() : "stay");
		form.addView(label(context, "IME (stay / hide / overlay)"));
		form.addView(ime);

		final CheckBox showValue = new CheckBox(context);
		showValue.setText("Show value");
		showValue.setChecked(existing == null || existing.isShowValue());
		final CheckBox showLabel = new CheckBox(context);
		showLabel.setText("Show label");
		showLabel.setChecked(existing == null || existing.isShowLabel());
		LinearLayout flags = new LinearLayout(context);
		flags.setOrientation(LinearLayout.HORIZONTAL);
		flags.addView(showValue);
		flags.addView(showLabel);
		form.addView(flags);

		final CheckBox visible = new CheckBox(context);
		visible.setText("Visible");
		visible.setChecked(existing == null || existing.isVisible());
		form.addView(visible);

		AlertDialog.Builder b = new AlertDialog.Builder(context);
		b.setTitle(existing == null ? "Add widget" : "Edit widget");
		b.setView(scroll);
		b.setNegativeButton("Cancel", null);
		b.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String rawId = idField.getText() != null ? idField.getText().toString() : "";
				String normalized = GaugeWidgetsStore.normalizeName(rawId);
				if (normalized == null) {
					Toast.makeText(context,
							"Invalid id (lowercase a-z0-9_, 1–24; not reserved).",
							Toast.LENGTH_LONG).show();
					return;
				}
				for (int i = 0; i < gauges.size(); i++) {
					GaugeWidget g = gauges.get(i);
					if (g == null || g == existing) {
						continue;
					}
					if (normalized.equals(g.getId())) {
						Toast.makeText(context, "Id already used.", Toast.LENGTH_SHORT).show();
						return;
					}
				}
				GaugeWidget widget = existing != null ? existing : new GaugeWidget(normalized);
				widget.setId(normalized);
				if (existing == null || widget.getLabel().length() == 0) {
					widget.setLabel(normalized);
				}
				widget.setShape(GaugeWidget.Shape.fromJsonValue(
						(String) shape.getSelectedItem()));
				widget.setSource(GaugeWidget.Source.fromJsonValue(
						(String) source.getSelectedItem()));
				widget.setPath(path.getText() != null ? path.getText().toString().trim() : "");
				widget.setMaxPath(maxPath.getText() != null
						? maxPath.getText().toString().trim() : "");
				String colorRaw = color.getText() != null ? color.getText().toString() : "";
				widget.setColorFill(GaugeWidget.parseColor(colorRaw,
						GaugeWidget.DEFAULT_COLOR_FILL));
				try {
					widget.setOpacity(Integer.parseInt(opacity.getText().toString().trim()));
				} catch (Exception e) {
					widget.setOpacity(GaugeWidget.DEFAULT_OPACITY);
				}
				try {
					widget.setWarnPct(Integer.parseInt(warnPct.getText().toString().trim()));
				} catch (Exception e) {
					widget.setWarnPct(GaugeWidget.DEFAULT_WARN_PCT);
				}
				widget.setImeMode(GaugeWidget.ImeMode.fromJsonValue(
						(String) ime.getSelectedItem()));
				widget.setShowValue(showValue.isChecked());
				widget.setShowLabel(showLabel.isChecked());
				widget.setVisible(visible.isChecked());
				if (widget.getSource() == GaugeWidget.Source.TIMER
						&& widget.getPath().length() > 0
						&& widget.getTimerName().length() == 0) {
					widget.setTimerName(widget.getPath());
				}
				if (existing == null) {
					if (gauges.size() >= GaugeWidgetsStore.MAX) {
						Toast.makeText(context, "Maximum widgets reached.",
								Toast.LENGTH_SHORT).show();
						return;
					}
					widget.setX(GaugeSpawnPlacement.UNPLACED);
					widget.setY(GaugeSpawnPlacement.UNPLACED);
					gauges.add(widget);
				}
				onDone.run();
			}
		});
		b.show();
	}

	private static void applySourceFieldLabels(final String source,
			final TextView pathLabel, final EditText path,
			final TextView maxPathLabel, final EditText maxPath) {
		String src = source != null ? source : "manual";
		maxPathLabel.setVisibility(View.VISIBLE);
		maxPath.setVisibility(View.VISIBLE);
		if ("gmcp".equals(src)) {
			pathLabel.setText("GMCP path");
			path.setHint("Char.Vitals.hp");
			maxPathLabel.setText("GMCP max path");
			maxPath.setHint("Char.Vitals.maxhp");
		} else if ("mcp".equals(src)) {
			pathLabel.setText("MCP key");
			path.setHint("hp");
			maxPathLabel.setText("MCP max key");
			maxPath.setHint("maxhp");
		} else if ("var".equals(src)) {
			pathLabel.setText("Variable");
			path.setHint("hp");
			maxPathLabel.setText("Max variable");
			maxPath.setHint("maxhp");
		} else if ("timer".equals(src)) {
			pathLabel.setText("Timer name");
			path.setHint("stunwait");
			maxPathLabel.setVisibility(View.GONE);
			maxPath.setVisibility(View.GONE);
		} else if ("regex".equals(src)) {
			pathLabel.setText("Value regex (group 1)");
			path.setHint("HP:\\s*([\\d.]+)");
			maxPathLabel.setText("Max regex (optional)");
			maxPath.setHint("or two groups in the value regex");
		} else {
			pathLabel.setText("Path (unused — .widget set id 80 100)");
			path.setHint(".widget set id 80 100");
			maxPathLabel.setText("Max path (unused)");
			maxPath.setHint("");
		}
	}

	private static Spinner spinner(Context context, String[] items) {
		Spinner s = new Spinner(context);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(context,
				android.R.layout.simple_spinner_item, items);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		s.setAdapter(adapter);
		return s;
	}

	private static void selectValue(Spinner spinner, String[] items, String value) {
		if (value == null) {
			return;
		}
		for (int i = 0; i < items.length; i++) {
			if (items[i].equals(value)) {
				spinner.setSelection(i);
				return;
			}
		}
	}

	private static TextView label(Context context, String text) {
		TextView tv = new TextView(context);
		tv.setText(text);
		tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		return tv;
	}
}

package com.resurrection.blowtorch2.lib.service.plugin.settings;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.window.ExtraTextSlot;
import com.resurrection.blowtorch2.lib.window.ExtraTextSlotsStore;

/**
 * Manage extra text window slots: list / add / delete / rename / mode / height / show.
 */
public final class ExtraTextWindowsDialog {

	public interface Host {
		IConnectionBinder getService();

		String getSlotsJson();

		boolean isEnabled();

		void applySlotsJson(String json, boolean enabled);

		/** Optional: force overlay refresh after apply. */
		void onSlotsChanged();
	}

	private ExtraTextWindowsDialog() {
	}

	public static void show(final Context context, final Host host) {
		if (context == null || host == null) {
			return;
		}

		int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
				context.getResources().getDisplayMetrics());

		final ArrayList<ExtraTextSlot> slots = ExtraTextSlotsStore.parse(host.getSlotsJson());
		ExtraTextSlotsStore.validate(slots);

		ScrollView scroll = new ScrollView(context);
		final LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(pad, pad, pad, pad);
		scroll.addView(root);

		TextView intro = new TextView(context);
		intro.setText("Extra text windows (max " + ExtraTextSlotsStore.MAX_SLOTS
				+ "). Names: lowercase a-z, 0-9, _. Used by gag/replace retarget, "
				+ "AppendLineToWindow, and .window.");
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
				if (slots.isEmpty()) {
					TextView empty = new TextView(context);
					empty.setText("(no windows yet)");
					list.addView(empty);
					return;
				}
				for (int i = 0; i < slots.size(); i++) {
					final ExtraTextSlot slot = slots.get(i);
					if (slot == null) {
						continue;
					}
					LinearLayout row = new LinearLayout(context);
					row.setOrientation(LinearLayout.VERTICAL);
					row.setPadding(0, 0, 0, pad / 2);

					TextView title = new TextView(context);
					title.setText(slot.getName() + " — " + slot.getTitle()
							+ " [" + slot.getMode().toJsonValue() + "]"
							+ (slot.isVisible() ? "" : " (hidden)"));
					title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
					row.addView(title);

					LinearLayout buttons = new LinearLayout(context);
					buttons.setOrientation(LinearLayout.HORIZONTAL);

					Button edit = new Button(context);
					edit.setText("Edit");
					edit.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							editSlot(context, slots, slot, refreshHolder[0]);
						}
					});
					buttons.addView(edit);

					Button del = new Button(context);
					del.setText("Delete");
					del.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							slots.remove(slot);
							refreshHolder[0].run();
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
		add.setText("Add window…");
		add.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (slots.size() >= ExtraTextSlotsStore.MAX_SLOTS) {
					Toast.makeText(context, "Maximum " + ExtraTextSlotsStore.MAX_SLOTS
							+ " windows.", Toast.LENGTH_SHORT).show();
					return;
				}
				editSlot(context, slots, null, refreshHolder[0]);
			}
		});
		root.addView(add);

		AlertDialog.Builder b = new AlertDialog.Builder(context);
		b.setTitle("Manage Extra Text Windows");
		b.setView(scroll);
		b.setNegativeButton("Cancel", null);
		b.setPositiveButton("Apply", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				ExtraTextSlotsStore.validate(slots);
				String json = ExtraTextSlotsStore.toJson(slots);
				host.applySlotsJson(json, host.isEnabled());
				host.onSlotsChanged();
			}
		});
		b.show();
	}

	private static void editSlot(final Context context, final ArrayList<ExtraTextSlot> slots,
			final ExtraTextSlot existing, final Runnable onDone) {
		int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
				context.getResources().getDisplayMetrics());
		LinearLayout form = new LinearLayout(context);
		form.setOrientation(LinearLayout.VERTICAL);
		form.setPadding(pad, pad, pad, pad);

		final EditText name = new EditText(context);
		name.setHint("name (chat, tells, …)");
		name.setSingleLine(true);
		if (existing != null) {
			name.setText(existing.getName());
		}
		form.addView(label(context, "Name"));
		form.addView(name);

		final EditText title = new EditText(context);
		title.setHint("title");
		title.setSingleLine(true);
		if (existing != null) {
			title.setText(existing.getTitle());
		}
		form.addView(label(context, "Title"));
		form.addView(title);

		final Spinner mode = new Spinner(context);
		String[] modes = new String[] { "drawer_bottom", "drawer_top", "float" };
		ArrayAdapter<String> modeAdapter = new ArrayAdapter<String>(context,
				android.R.layout.simple_spinner_item, modes);
		modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mode.setAdapter(modeAdapter);
		if (existing != null) {
			String m = existing.getMode().toJsonValue();
			for (int i = 0; i < modes.length; i++) {
				if (modes[i].equals(m)) {
					mode.setSelection(i);
					break;
				}
			}
		}
		form.addView(label(context, "Mode"));
		form.addView(mode);

		final EditText height = new EditText(context);
		height.setHint("height_dp (drawer)");
		height.setSingleLine(true);
		height.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
		height.setText(Integer.toString(existing != null ? existing.getHeightDp() : 160));
		form.addView(label(context, "Drawer height (dp)"));
		form.addView(height);

		final Spinner visible = new Spinner(context);
		String[] visItems = new String[] { "Visible", "Hidden" };
		ArrayAdapter<String> visAdapter = new ArrayAdapter<String>(context,
				android.R.layout.simple_spinner_item, visItems);
		visAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		visible.setAdapter(visAdapter);
		visible.setSelection(existing != null && !existing.isVisible() ? 1 : 0);
		form.addView(label(context, "Visibility"));
		form.addView(visible);

		AlertDialog.Builder b = new AlertDialog.Builder(context);
		b.setTitle(existing == null ? "Add window" : "Edit window");
		b.setView(form);
		b.setNegativeButton("Cancel", null);
		b.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String rawName = name.getText() != null ? name.getText().toString() : "";
				String normalized = ExtraTextSlotsStore.normalizeName(rawName);
				if (normalized == null) {
					Toast.makeText(context,
							"Invalid name (lowercase a-z0-9_, 1–24; not reserved).",
							Toast.LENGTH_LONG).show();
					return;
				}
				// Duplicate check (allow same slot when editing).
				for (int i = 0; i < slots.size(); i++) {
					ExtraTextSlot s = slots.get(i);
					if (s == null || s == existing) {
						continue;
					}
					if (normalized.equals(s.getName())) {
						Toast.makeText(context, "Name already used.", Toast.LENGTH_SHORT).show();
						return;
					}
				}
				ExtraTextSlot slot = existing != null ? existing : new ExtraTextSlot(normalized);
				slot.setName(normalized);
				String t = title.getText() != null ? title.getText().toString().trim() : "";
				slot.setTitle(t.length() > 0 ? t : normalized);
				slot.setMode(ExtraTextSlot.Mode.fromJsonValue(
						(String) mode.getSelectedItem()));
				try {
					int h = Integer.parseInt(height.getText().toString().trim());
					slot.setHeightDp(h);
				} catch (Exception e) {
					slot.setHeightDp(160);
				}
				slot.setVisible(visible.getSelectedItemPosition() == 0);
				if (existing == null) {
					if (slots.size() >= ExtraTextSlotsStore.MAX_SLOTS) {
						Toast.makeText(context, "Maximum slots reached.", Toast.LENGTH_SHORT).show();
						return;
					}
					slots.add(slot);
				}
				onDone.run();
			}
		});
		b.show();
	}

	private static TextView label(Context context, String text) {
		TextView tv = new TextView(context);
		tv.setText(text);
		tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		return tv;
	}
}

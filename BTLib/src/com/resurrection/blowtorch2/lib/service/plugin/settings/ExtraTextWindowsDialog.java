package com.resurrection.blowtorch2.lib.service.plugin.settings;

import java.util.ArrayList;
import java.util.LinkedHashSet;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.resurrection.blowtorch2.lib.service.GmcpModuleRegistry;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.window.ExtraTextSlot;
import com.resurrection.blowtorch2.lib.window.ExtraTextSlotsStore;

/**
 * Manage extra text window slots: list / add / delete / rename / mode / height / show / GMCP.
 */
public final class ExtraTextWindowsDialog {

	public interface Host {
		IConnectionBinder getService();

		String getSlotsJson();

		boolean isEnabled();

		void applySlotsJson(String json, boolean enabled);

		/** Optional: force overlay refresh after apply. */
		void onSlotsChanged();

		/** Options → GMCP → Use GMCP? */
		boolean isGmcpEnabled();

		/** Session-seen GMCP module families (may be empty). */
		ArrayList<String> getSeenGmcpModules();
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

		if (!host.isGmcpEnabled()) {
			TextView warn = new TextView(context);
			warn.setText("GMCP is off — enable Options → Service → GMCP Options → "
					+ "Use GMCP? before module routes into these windows will receive data.");
			warn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
			warn.setTextColor(Color.parseColor("#C62828"));
			warn.setPadding(0, 0, 0, pad);
			root.addView(warn);
		}

		TextView intro = new TextView(context);
		intro.setText("Extra text windows (max " + ExtraTextSlotsStore.MAX_SLOTS
				+ "). Modes: drawer_top or float. Names: lowercase a-z, 0-9, _. "
				+ "Used by gag/replace retarget, AppendLineToWindow, GMCP routes, and .window.");
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
					String gmcpHint = slot.getGmcpModulesCsv();
					title.setText(slot.getName() + " — " + slot.getTitle()
							+ " [" + slot.getMode().toJsonValue() + "]"
							+ " " + slot.getOpacity() + "%"
							+ (slot.isVisible() ? "" : " (hidden)")
							+ (gmcpHint.length() > 0 ? ("\nGMCP: " + gmcpHint) : ""));
					title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
					row.addView(title);

					LinearLayout buttons = new LinearLayout(context);
					buttons.setOrientation(LinearLayout.HORIZONTAL);

					Button edit = new Button(context);
					edit.setText("Edit");
					edit.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							editSlot(context, host, slots, slot, refreshHolder[0]);
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
				editSlot(context, host, slots, null, refreshHolder[0]);
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

	private static void editSlot(final Context context, final Host host,
			final ArrayList<ExtraTextSlot> slots, final ExtraTextSlot existing,
			final Runnable onDone) {
		int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
				context.getResources().getDisplayMetrics());
		ScrollView scroll = new ScrollView(context);
		LinearLayout form = new LinearLayout(context);
		form.setOrientation(LinearLayout.VERTICAL);
		form.setPadding(pad, pad, pad, pad);
		scroll.addView(form);

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
		String[] modes = new String[] { "drawer_top", "float" };
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
		} else {
			mode.setSelection(0); // drawer_top
		}
		form.addView(label(context, "Mode (top drawer or floating)"));
		form.addView(mode);

		final EditText height = new EditText(context);
		height.setHint("height_dp (drawer)");
		height.setSingleLine(true);
		height.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
		height.setText(Integer.toString(existing != null ? existing.getHeightDp() : 160));
		form.addView(label(context, "Drawer height (dp)"));
		form.addView(height);

		final EditText opacity = new EditText(context);
		opacity.setHint("opacity 40–100 (float / overlay)");
		opacity.setSingleLine(true);
		opacity.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
		opacity.setText(Integer.toString(existing != null ? existing.getOpacity() : 85));
		form.addView(label(context, "Opacity % (40–100)"));
		form.addView(opacity);

		final Spinner visible = new Spinner(context);
		String[] visItems = new String[] { "Visible", "Hidden" };
		ArrayAdapter<String> visAdapter = new ArrayAdapter<String>(context,
				android.R.layout.simple_spinner_item, visItems);
		visAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		visible.setAdapter(visAdapter);
		visible.setSelection(existing != null && !existing.isVisible() ? 1 : 0);
		form.addView(label(context, "Show window (or use .window show/hide)"));
		form.addView(visible);

		form.addView(label(context, "GMCP modules to dump into this window"));
		if (!host.isGmcpEnabled()) {
			TextView warn = new TextView(context);
			warn.setText("GMCP is disabled — turn on Use GMCP? under Service → GMCP Options.");
			warn.setTextColor(Color.parseColor("#C62828"));
			warn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
			warn.setPadding(0, 0, 0, pad / 2);
			form.addView(warn);
		}

		final LinkedHashSet<String> selected = new LinkedHashSet<String>();
		if (existing != null) {
			selected.addAll(existing.getGmcpModules());
		}

		final EditText gmcpAdvanced = new EditText(context);
		gmcpAdvanced.setHint("Char.Vitals, Comm.*, Char.");
		gmcpAdvanced.setSingleLine(false);
		gmcpAdvanced.setMinLines(2);
		gmcpAdvanced.setText(csvFromSet(selected));

		final boolean[] syncing = new boolean[] { false };
		final Runnable pushAdvanced = new Runnable() {
			@Override
			public void run() {
				syncing[0] = true;
				gmcpAdvanced.setText(csvFromSet(selected));
				syncing[0] = false;
			}
		};

		GmcpModuleRegistry reg = new GmcpModuleRegistry();
		ArrayList<String> seen = host.getSeenGmcpModules();
		if (seen != null) {
			for (int i = 0; i < seen.size(); i++) {
				reg.noteSeen(seen.get(i));
			}
		}

		addGmcpCheckSection(context, form, "Built-in", reg.nativeModules(), selected, pushAdvanced);
		ArrayList<GmcpModuleRegistry.ModuleInfo> seenRows =
				new ArrayList<GmcpModuleRegistry.ModuleInfo>();
		for (String s : reg.seenModules()) {
			boolean isNative = false;
			for (GmcpModuleRegistry.ModuleInfo n : reg.nativeModules()) {
				if (GmcpModuleRegistry.normKey(n.id).equals(GmcpModuleRegistry.normKey(s))) {
					isNative = true;
					break;
				}
			}
			if (isNative) {
				continue;
			}
			seenRows.add(new GmcpModuleRegistry.ModuleInfo(
					reg.canonicalId(s), 1, "Seen from this server",
					GmcpModuleRegistry.Kind.SEEN, false));
		}
		if (!seenRows.isEmpty()) {
			addGmcpCheckSection(context, form, "Seen this session", seenRows, selected, pushAdvanced);
		}
		addGmcpCheckSection(context, form, "Catalog", reg.catalogModules(), selected, pushAdvanced);

		// Patterns already in the slot that are not plain catalog ids (e.g. Char.*)
		ArrayList<String> customOnly = new ArrayList<String>();
		for (String p : selected) {
			if (!isKnownModuleId(reg, p)) {
				customOnly.add(p);
			}
		}
		if (!customOnly.isEmpty()) {
			TextView customLabel = label(context, "Custom patterns (from advanced string)");
			form.addView(customLabel);
			for (int i = 0; i < customOnly.size(); i++) {
				TextView tv = new TextView(context);
				tv.setText("• " + customOnly.get(i));
				tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
				form.addView(tv);
			}
		}

		final TextView advLabel = label(context, "Advanced patterns (CSV)");
		advLabel.setVisibility(View.GONE);
		gmcpAdvanced.setVisibility(View.GONE);
		form.addView(advLabel);
		form.addView(gmcpAdvanced);

		Button toggleAdv = new Button(context);
		toggleAdv.setText("Show advanced string…");
		toggleAdv.setOnClickListener(new View.OnClickListener() {
			boolean open;
			@Override
			public void onClick(View v) {
				open = !open;
				int vis = open ? View.VISIBLE : View.GONE;
				advLabel.setVisibility(vis);
				gmcpAdvanced.setVisibility(vis);
				toggleAdv.setText(open ? "Hide advanced string" : "Show advanced string…");
				if (open) {
					pushAdvanced.run();
				}
			}
		});
		form.addView(toggleAdv);

		gmcpAdvanced.addTextChangedListener(new android.text.TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(android.text.Editable s) {
				if (syncing[0]) {
					return;
				}
				selected.clear();
				ExtraTextSlot tmp = new ExtraTextSlot("tmp");
				tmp.setGmcpModulesCsv(s != null ? s.toString() : "");
				selected.addAll(tmp.getGmcpModules());
			}
		});

		AlertDialog.Builder b = new AlertDialog.Builder(context);
		b.setTitle(existing == null ? "Add window" : "Edit window");
		b.setView(scroll);
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
				try {
					int op = Integer.parseInt(opacity.getText().toString().trim());
					slot.setOpacity(op);
				} catch (Exception e) {
					slot.setOpacity(85);
				}
				slot.setVisible(visible.getSelectedItemPosition() == 0);
				if (gmcpAdvanced.getVisibility() == View.VISIBLE) {
					slot.setGmcpModulesCsv(gmcpAdvanced.getText() != null
							? gmcpAdvanced.getText().toString() : "");
				} else {
					slot.setGmcpModules(new ArrayList<String>(selected));
				}
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

	private static void addGmcpCheckSection(Context context, LinearLayout form, String title,
			ArrayList<GmcpModuleRegistry.ModuleInfo> modules,
			final LinkedHashSet<String> selected, final Runnable onChanged) {
		if (modules == null || modules.isEmpty()) {
			return;
		}
		TextView h = new TextView(context);
		h.setText(title);
		h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		h.setTypeface(null, android.graphics.Typeface.BOLD);
		h.setPadding(0, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8,
				context.getResources().getDisplayMetrics()), 0, 2);
		form.addView(h);

		for (final GmcpModuleRegistry.ModuleInfo m : modules) {
			CheckBox cb = new CheckBox(context);
			cb.setText(m.id + (m.summary.length() > 0 ? (" — " + m.summary) : ""));
			cb.setChecked(setContainsIgnoreCase(selected, m.id)
					|| setContainsPrefixFamily(selected, m.id));
			cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					removeIgnoreCase(selected, m.id);
					if (isChecked) {
						selected.add(m.id);
					}
					onChanged.run();
				}
			});
			form.addView(cb);
		}
	}

	private static boolean isKnownModuleId(GmcpModuleRegistry reg, String pattern) {
		if (pattern == null) {
			return false;
		}
		String p = pattern.trim();
		if (p.endsWith(".*") || p.endsWith("*") || p.endsWith(".")) {
			return false;
		}
		for (GmcpModuleRegistry.ModuleInfo m : reg.allKnownModules()) {
			if (m.id.equalsIgnoreCase(p)) {
				return true;
			}
		}
		return false;
	}

	private static boolean setContainsIgnoreCase(LinkedHashSet<String> set, String id) {
		for (String s : set) {
			if (s != null && s.equalsIgnoreCase(id)) {
				return true;
			}
		}
		return false;
	}

	/** Char. matches / Char.* counts as selecting family Char for checkbox UI. */
	private static boolean setContainsPrefixFamily(LinkedHashSet<String> set, String id) {
		if (id == null) {
			return false;
		}
		String fam = id + ".";
		String famStar = id + ".*";
		String famStar2 = id + "*";
		for (String s : set) {
			if (s == null) {
				continue;
			}
			if (s.equalsIgnoreCase(fam) || s.equalsIgnoreCase(famStar)
					|| s.equalsIgnoreCase(famStar2)) {
				return true;
			}
		}
		return false;
	}

	private static void removeIgnoreCase(LinkedHashSet<String> set, String id) {
		ArrayList<String> drop = new ArrayList<String>();
		for (String s : set) {
			if (s != null && s.equalsIgnoreCase(id)) {
				drop.add(s);
			}
		}
		set.removeAll(drop);
	}

	private static String csvFromSet(LinkedHashSet<String> set) {
		if (set == null || set.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (String s : set) {
			if (s == null || s.trim().length() == 0) {
				continue;
			}
			if (!first) {
				sb.append(", ");
			}
			first = false;
			sb.append(s.trim());
		}
		return sb.toString();
	}

	private static TextView label(Context context, String text) {
		TextView tv = new TextView(context);
		tv.setText(text);
		tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		return tv;
	}
}

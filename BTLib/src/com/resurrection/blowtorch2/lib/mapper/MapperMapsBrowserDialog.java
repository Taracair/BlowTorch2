package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Pick a saved map from {@link MapStore} — tap to open, long-press to delete.
 */
public final class MapperMapsBrowserDialog {

	public interface Host {
		/** Currently loaded map name (may be null). */
		String getCurrentMapName();
		/** Open / load a map by name. */
		void openMap(String name);
		/** Create a new empty map (host shows name prompt). */
		void createNewMap();
		/** Delete a saved map (host confirms / refreshes). */
		void deleteMap(String name);
		Context getContext();
	}

	private MapperMapsBrowserDialog() {
	}

	public static void show(final Host host) {
		if (host == null || host.getContext() == null) {
			return;
		}
		final Context context = host.getContext();
		final List<String> names = new ArrayList<String>(
				MapStore.listMaps(context));
		final String current = host.getCurrentMapName();

		float density = context.getResources().getDisplayMetrics().density;
		int pad = (int) (12 * density);

		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(pad, pad, pad, pad);

		TextView title = new TextView(context);
		title.setText("Maps");
		title.setTextSize(18f);
		title.setTextColor(0xFFEEEEEE);
		root.addView(title);

		TextView help = new TextView(context);
		String curLabel = current != null && current.length() > 0
				? current : "(unnamed)";
		help.setText("Current: " + curLabel
				+ "\nTap a map to open · long-press to delete.");
		help.setTextSize(12f);
		help.setTextColor(0xFFBBBBBB);
		help.setPadding(0, pad / 2, 0, pad / 2);
		root.addView(help);

		final List<String> labels = new ArrayList<String>();
		for (String n : names) {
			boolean isCur = current != null && current.equals(n);
			labels.add((isCur ? "● " : "    ") + n);
		}

		if (names.isEmpty()) {
			TextView empty = new TextView(context);
			empty.setText("No saved maps yet.\nUse New to create one.");
			empty.setTextColor(0xFF888888);
			empty.setPadding(0, pad, 0, pad);
			root.addView(empty);
		}

		final ListView list = new ListView(context);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(context,
				android.R.layout.simple_list_item_1, labels) {
			@Override
			public View getView(int position, View convertView,
					android.view.ViewGroup parent) {
				TextView tv = (TextView) super.getView(position, convertView,
						parent);
				tv.setTextColor(0xFFEEEEEE);
				tv.setTextSize(15f);
				return tv;
			}
		};
		list.setAdapter(adapter);
		root.addView(list, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				(int) (280 * density)));

		LinearLayout buttons = new LinearLayout(context);
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		buttons.setGravity(Gravity.END);
		buttons.setPadding(0, pad / 2, 0, 0);

		Button newBtn = new Button(context);
		newBtn.setText("New");
		buttons.addView(newBtn);

		Button closeBtn = new Button(context);
		closeBtn.setText("Close");
		buttons.addView(closeBtn);
		root.addView(buttons);

		final AlertDialog dlg = new AlertDialog.Builder(context)
				.setView(root)
				.create();

		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (position < 0 || position >= names.size()) {
					return;
				}
				String name = names.get(position);
				dlg.dismiss();
				host.openMap(name);
			}
		});

		list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (position < 0 || position >= names.size()) {
					return true;
				}
				final String name = names.get(position);
				new AlertDialog.Builder(context)
						.setTitle("Delete map?")
						.setMessage("Delete \"" + name + "\" from disk?\n"
								+ "This cannot be undone.")
						.setPositiveButton("Delete",
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface d,
											int which) {
										dlg.dismiss();
										host.deleteMap(name);
									}
								})
						.setNegativeButton("Cancel", null)
						.show();
				return true;
			}
		});

		newBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dlg.dismiss();
				host.createNewMap();
			}
		});
		closeBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dlg.dismiss();
			}
		});

		if (names.isEmpty()) {
			Toast.makeText(context, "No maps on disk yet", Toast.LENGTH_SHORT)
					.show();
		}
		dlg.show();
	}
}

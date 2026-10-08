package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.List;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.window.EditorDialogChrome;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

/**
 * Pick a saved map from {@link MapStore} — tap to open, long-press to delete.
 */
public final class MapperMapsBrowserDialog {

	public interface Host {
		/** Currently loaded map name (may be null). */
		String getCurrentMapName();
		/** Active MUD host; maps from other worlds are hidden. May be null. */
		String getWorldHost();
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
		final String worldHost = host.getWorldHost();
		final List<String> names = new ArrayList<String>();
		if (worldHost != null && worldHost.trim().length() > 0) {
			names.addAll(MapStore.listMapsForHost(context, worldHost));
		} else {
			names.addAll(MapStore.listMaps(context));
		}
		final String current = host.getCurrentMapName();

		final Dialog dialog = openList(context);
		((TextView) dialog.findViewById(R.id.titlebar)).setText("Maps");
		LinearLayout body = (LinearLayout) dialog.findViewById(R.id.mapper_shell_body);
		int pad = dip(context, 12);

		TextView help = new TextView(context);
		String curLabel = current != null && current.length() > 0
				? current : "(unnamed)";
		help.setText("Current: " + curLabel
				+ (worldHost != null && worldHost.length() > 0
						? "\nWorld: " + worldHost : "")
				+ "\nTap a map to open · long-press to delete.");
		help.setTextSize(12f);
		help.setTextColor(ContextCompat.getColor(context, R.color.chrome_description));
		help.setPadding(pad, pad, pad, pad / 2);
		body.addView(help);

		final List<String> labels = new ArrayList<String>();
		for (String n : names) {
			boolean isCur = current != null && current.equals(n);
			labels.add((isCur ? "● " : "    ") + n);
		}

		if (names.isEmpty()) {
			TextView empty = new TextView(context);
			empty.setText(worldHost != null && worldHost.length() > 0
					? "No saved maps for this world yet.\nUse New to create one."
					: "No saved maps yet.\nUse New to create one.");
			empty.setTextColor(ContextCompat.getColor(context, R.color.chrome_hint));
			empty.setPadding(pad, pad, pad, pad);
			body.addView(empty);
		}

		final ListView list = new ListView(context);
		styleList(context, list);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(context,
				android.R.layout.simple_list_item_1, labels) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				TextView tv = (TextView) super.getView(position, convertView, parent);
				tv.setTextColor(ContextCompat.getColor(context, R.color.chrome_title_text));
				tv.setTextSize(15f);
				return tv;
			}
		};
		list.setAdapter(adapter);
		body.addView(list, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

		LinearLayout footer = (LinearLayout) dialog.findViewById(R.id.button_row);
		Button newBtn = barButton(context, "New", false);
		Button closeBtn = barButton(context, "Close", true);
		footer.addView(newBtn);
		footer.addView(closeBtn);

		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (position < 0 || position >= names.size()) {
					return;
				}
				String name = names.get(position);
				dialog.dismiss();
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
				showConfirm(context, "Delete map?",
						"Delete \"" + name + "\" from disk?\n"
								+ "This cannot be undone.",
						"Delete", new Runnable() {
							@Override
							public void run() {
								dialog.dismiss();
								host.deleteMap(name);
							}
						});
				return true;
			}
		});

		newBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
				host.createNewMap();
			}
		});
		closeBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});

		if (names.isEmpty()) {
			Toast.makeText(context, "No maps on disk yet", Toast.LENGTH_SHORT)
					.show();
		}
		EditorDialogChrome.applyFullScreen(dialog);
		dialog.show();
	}

	private static Dialog openList(Context context) {
		Dialog dialog = new Dialog(context, EditorDialogChrome.fullScreenTheme());
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setCanceledOnTouchOutside(false);
		Window window = dialog.getWindow();
		if (window != null) {
			window.setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		}
		dialog.setContentView(R.layout.mapper_list_shell);
		return dialog;
	}

	private static void showConfirm(Context context, String title, String message,
			String positive, final Runnable onPositive) {
		final Dialog dialog = openForm(context, false);
		((TextView) dialog.findViewById(R.id.titlebar)).setText(title);
		TextView msg = new TextView(context);
		msg.setText(message);
		msg.setTextColor(ContextCompat.getColor(context, R.color.chrome_title_text));
		msg.setTextSize(15f);
		((LinearLayout) dialog.findViewById(R.id.mapper_shell_body)).addView(msg);
		LinearLayout footer = (LinearLayout) dialog.findViewById(R.id.button_row);
		Button cancel = barButton(context, "Cancel", false);
		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		Button ok = barButton(context, positive, true);
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

	private static Dialog openForm(Context context, boolean fullScreen) {
		Dialog dialog = new Dialog(context, fullScreen
				? EditorDialogChrome.fullScreenTheme()
				: EditorDialogChrome.dialogTheme());
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		Window window = dialog.getWindow();
		if (window != null) {
			window.setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		}
		dialog.setContentView(R.layout.mapper_form_shell);
		return dialog;
	}

	private static void styleList(Context context, ListView list) {
		list.setBackgroundColor(ContextCompat.getColor(context, R.color.chrome_body));
		list.setCacheColorHint(0x00000000);
		list.setDivider(ContextCompat.getDrawable(context, R.drawable.editor_row_divider));
		list.setDividerHeight(Math.max(1, dip(context, 1)));
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

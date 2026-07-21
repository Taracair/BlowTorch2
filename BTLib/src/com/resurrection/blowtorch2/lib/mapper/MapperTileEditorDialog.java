package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.List;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.window.EditorDialogChrome;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Edit tile title, notes, level, exits; add link command; move-to-level.
 */
public class MapperTileEditorDialog extends Dialog {

	private final MapperController controller;
	private final MapTile tile;
	private final String previousCurrentId;
	private EditText titleEdit;
	private EditText notesEdit;
	private Spinner levelSpinner;
	private EditText linkCmdEdit;
	private EditText linkToEdit;
	private LinearLayout exitsList;
	private List<MapLevel> levels = new ArrayList<MapLevel>();

	public MapperTileEditorDialog(Context context, MapperController controller, MapTile tile) {
		super(context, EditorDialogChrome.dialogTheme());
		this.controller = controller;
		this.tile = tile;
		MudMap map = controller.getMap();
		this.previousCurrentId = map != null ? map.getCurrentTileId() : null;
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
		heading.setText("Edit tile");
		heading.setTextSize(18f);
		heading.setTextColor(0xFFEEEEEE);
		root.addView(heading);

		root.addView(label("Title"));
		titleEdit = new EditText(getContext());
		titleEdit.setText(tile.getTitle() != null ? tile.getTitle() : "");
		titleEdit.setSingleLine(true);
		root.addView(titleEdit);

		root.addView(label("Notes"));
		notesEdit = new EditText(getContext());
		notesEdit.setText(tile.getNotes() != null ? tile.getNotes() : "");
		notesEdit.setMinLines(3);
		notesEdit.setGravity(Gravity.TOP | Gravity.START);
		root.addView(notesEdit);

		root.addView(label("Level"));
		levelSpinner = new Spinner(getContext());
		levels.clear();
		if (controller.getMap() != null) {
			levels.addAll(controller.getMap().getLevels());
		}
		List<String> names = new ArrayList<String>();
		int selected = 0;
		for (int i = 0; i < levels.size(); i++) {
			MapLevel l = levels.get(i);
			names.add(l.getName() != null ? l.getName() : l.getId());
			if (l.getId() != null && l.getId().equals(tile.getLevelId())) {
				selected = i;
			}
		}
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(
				getContext(), android.R.layout.simple_spinner_dropdown_item, names);
		levelSpinner.setAdapter(adapter);
		if (!names.isEmpty()) {
			levelSpinner.setSelection(selected);
		}
		root.addView(levelSpinner);

		root.addView(label("Exits"));
		exitsList = new LinearLayout(getContext());
		exitsList.setOrientation(LinearLayout.VERTICAL);
		root.addView(exitsList);
		refreshExits();

		root.addView(label("Add link (command)"));
		linkCmdEdit = new EditText(getContext());
		linkCmdEdit.setHint("n");
		linkCmdEdit.setSingleLine(true);
		root.addView(linkCmdEdit);

		root.addView(label("To tile id"));
		linkToEdit = new EditText(getContext());
		linkToEdit.setHint("target tile uuid");
		linkToEdit.setSingleLine(true);
		root.addView(linkToEdit);

		Button addLink = new Button(getContext());
		addLink.setText("Add link");
		addLink.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String cmd = linkCmdEdit.getText().toString().trim();
				String to = linkToEdit.getText().toString().trim();
				if (cmd.length() == 0 || to.length() == 0) {
					Toast.makeText(getContext(), "Command and target id required",
							Toast.LENGTH_SHORT).show();
					return;
				}
				withCurrentTile(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(getContext(), controller.link(cmd, to),
								Toast.LENGTH_SHORT).show();
					}
				});
				MapTile fresh = controller.getMap() != null
						? controller.getMap().findTile(tile.getId()) : null;
				if (fresh != null) {
					tile.setExits(fresh.getExits());
				}
				linkCmdEdit.setText("");
				linkToEdit.setText("");
				refreshExits();
			}
		});
		root.addView(addLink);

		LinearLayout buttons = new LinearLayout(getContext());
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		buttons.setGravity(Gravity.END);

		Button cancel = new Button(getContext());
		cancel.setText("Cancel");
		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				restoreCurrent();
				dismiss();
			}
		});
		buttons.addView(cancel);

		Button save = new Button(getContext());
		save.setText("Save");
		save.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				withCurrentTile(new Runnable() {
					@Override
					public void run() {
						controller.setTitle(titleEdit.getText().toString());
						controller.setNotes(notesEdit.getText().toString());
					}
				});
				int idx = levelSpinner.getSelectedItemPosition();
				if (idx >= 0 && idx < levels.size()) {
					MapLevel level = levels.get(idx);
					String name = level.getName() != null ? level.getName() : level.getId();
					if (level.getId() != null && !level.getId().equals(tile.getLevelId())) {
						controller.moveTileLevel(tile.getId(), name);
					}
				}
				restoreCurrent();
				dismiss();
			}
		});
		buttons.addView(save);
		root.addView(buttons);

		setContentView(scroll);
		EditorDialogChrome.applyNearlyFullScreen(this);
	}

	private void withCurrentTile(Runnable action) {
		MudMap map = controller.getMap();
		if (map != null) {
			map.setCurrentTileId(tile.getId());
		}
		action.run();
	}

	private void restoreCurrent() {
		MudMap map = controller.getMap();
		if (map != null && previousCurrentId != null) {
			map.setCurrentTileId(previousCurrentId);
			controller.fireChanged();
		}
	}

	private TextView label(String text) {
		TextView tv = new TextView(getContext());
		tv.setText(text);
		tv.setTextColor(0xFFBBBBBB);
		tv.setPadding(0, (int) (8 * getContext().getResources().getDisplayMetrics().density), 0, 2);
		return tv;
	}

	private void refreshExits() {
		exitsList.removeAllViews();
		for (final MapExit exit : new ArrayList<MapExit>(tile.getExits())) {
			if (exit == null) {
				continue;
			}
			LinearLayout row = new LinearLayout(getContext());
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setGravity(Gravity.CENTER_VERTICAL);
			TextView info = new TextView(getContext());
			String cmd = exit.getCommand() != null ? exit.getCommand() : "?";
			String to = exit.getToId() != null ? exit.getToId() : "(none)";
			String spec = exit.isSpecial() ? " ★" : "";
			info.setText(cmd + " → " + shortId(to) + spec);
			info.setTextColor(0xFFDDDDDD);
			info.setLayoutParams(new LinearLayout.LayoutParams(0,
					LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
			row.addView(info);
			Button unlink = new Button(getContext());
			unlink.setText("Unlink");
			unlink.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					withCurrentTile(new Runnable() {
						@Override
						public void run() {
							controller.unlink(exit.getCommand());
						}
					});
					tile.getExits().remove(exit);
					refreshExits();
				}
			});
			row.addView(unlink);
			exitsList.addView(row);
		}
		if (tile.getExits().isEmpty()) {
			TextView empty = new TextView(getContext());
			empty.setText("(no exits)");
			empty.setTextColor(0xFF888888);
			exitsList.addView(empty);
		}
	}

	private static String shortId(String id) {
		if (id == null) {
			return "?";
		}
		return id.length() > 8 ? id.substring(0, 8) + "…" : id;
	}
}

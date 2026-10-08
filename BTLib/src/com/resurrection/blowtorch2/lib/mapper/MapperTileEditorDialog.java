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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

/**
 * Edit tile title, notes, GMCP locks, level, exits; add link command; move-to-level.
 * Always targets the {@link MapTile} passed in — never silently the "Here" tile.
 */
public class MapperTileEditorDialog extends Dialog {

	private final MapperController controller;
	private final MapTile tile;
	private EditText titleEdit;
	private EditText notesEdit;
	private CheckBox lockTitleCheck;
	private CheckBox lockPositionCheck;
	private Spinner levelSpinner;
	private EditText linkCmdEdit;
	private EditText linkToEdit;
	private LinearLayout exitsList;
	private List<MapLevel> levels = new ArrayList<MapLevel>();

	public MapperTileEditorDialog(Context context, MapperController controller, MapTile tile) {
		super(context, EditorDialogChrome.fullScreenTheme());
		this.controller = controller;
		this.tile = tile;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setCanceledOnTouchOutside(false);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		setContentView(R.layout.mapper_form_shell);

		((TextView) findViewById(R.id.titlebar)).setText("Edit tile");
		LinearLayout root = (LinearLayout) findViewById(R.id.mapper_shell_body);

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

		root.addView(label("GMCP locks"));
		lockTitleCheck = new CheckBox(getContext());
		lockTitleCheck.setText("Lock title (GMCP won't overwrite)");
		lockTitleCheck.setTextColor(ContextCompat.getColor(getContext(), R.color.chrome_title_text));
		lockTitleCheck.setChecked(tile.isLockTitle());
		root.addView(lockTitleCheck);

		lockPositionCheck = new CheckBox(getContext());
		lockPositionCheck.setText("Lock position (GMCP won't move)");
		lockPositionCheck.setTextColor(ContextCompat.getColor(getContext(), R.color.chrome_title_text));
		lockPositionCheck.setChecked(tile.isLockPosition());
		root.addView(lockPositionCheck);

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
				Toast.makeText(getContext(),
						controller.linkBetween(tile.getId(), cmd, to),
						Toast.LENGTH_SHORT).show();
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
				controller.setTitle(tile.getId(), titleEdit.getText().toString());
				controller.setNotes(tile.getId(), notesEdit.getText().toString());
				controller.setLockTitle(tile.getId(), lockTitleCheck.isChecked());
				controller.setLockPosition(tile.getId(), lockPositionCheck.isChecked());
				int idx = levelSpinner.getSelectedItemPosition();
				if (idx >= 0 && idx < levels.size()) {
					MapLevel level = levels.get(idx);
					String name = level.getName() != null ? level.getName() : level.getId();
					if (level.getId() != null && !level.getId().equals(tile.getLevelId())) {
						controller.moveTileLevel(tile.getId(), name);
					}
				}
				Toast.makeText(getContext(), "Saved", Toast.LENGTH_SHORT).show();
				dismiss();
			}
		});
		footer.addView(done);

		EditorDialogChrome.applyFullScreen(this);
	}

	private TextView label(String text) {
		TextView tv = new TextView(getContext());
		tv.setText(text);
		tv.setTextColor(ContextCompat.getColor(getContext(), R.color.chrome_description));
		tv.setPadding(0, dip(getContext(), 8), 0, 2);
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
			info.setTextColor(ContextCompat.getColor(getContext(), R.color.chrome_title_text));
			info.setLayoutParams(new LinearLayout.LayoutParams(0,
					LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
			row.addView(info);
			Button unlink = new Button(getContext());
			unlink.setText("Unlink");
			unlink.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					controller.unlinkBetween(tile.getId(), exit.getCommand());
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
			empty.setTextColor(ContextCompat.getColor(getContext(), R.color.chrome_hint));
			exitsList.addView(empty);
		}
	}

	private static String shortId(String id) {
		if (id == null) {
			return "?";
		}
		return id.length() > 8 ? id.substring(0, 8) + "…" : id;
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

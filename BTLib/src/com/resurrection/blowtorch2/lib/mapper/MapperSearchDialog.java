package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.List;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.window.EditorDialogChrome;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Search tiles by title/notes; Path / Go actions on a selected hit.
 */
public class MapperSearchDialog extends Dialog {

	public interface Callback {
		void onPath(MapTile tile, List<String> path);
		void onGo(MapTile tile, List<String> path);
	}

	private final MapperController controller;
	private final Callback callback;
	private EditText queryEdit;
	private ListView resultsList;
	private List<MapTile> results = new ArrayList<MapTile>();
	private MapTile selected;

	public MapperSearchDialog(Context context, MapperController controller, Callback callback) {
		super(context, EditorDialogChrome.dialogTheme());
		this.controller = controller;
		this.callback = callback;
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
		heading.setText("Find on map");
		heading.setTextSize(18f);
		heading.setTextColor(0xFFEEEEEE);
		root.addView(heading);

		queryEdit = new EditText(getContext());
		queryEdit.setHint("Title or notes");
		queryEdit.setSingleLine(true);
		root.addView(queryEdit);

		Button searchBtn = new Button(getContext());
		searchBtn.setText("Search");
		searchBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				doSearch();
			}
		});
		root.addView(searchBtn);

		resultsList = new ListView(getContext());
		LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
		resultsList.setLayoutParams(listLp);
		root.addView(resultsList);
		resultsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (position >= 0 && position < results.size()) {
					selected = results.get(position);
				}
			}
		});

		LinearLayout actions = new LinearLayout(getContext());
		actions.setOrientation(LinearLayout.HORIZONTAL);

		Button pathBtn = new Button(getContext());
		pathBtn.setText("Path");
		pathBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				MapTile tile = selectedOrFirst();
				if (tile == null) {
					Toast.makeText(getContext(), "No result", Toast.LENGTH_SHORT).show();
					return;
				}
				List<String> path = pathTo(tile);
				if (callback != null) {
					callback.onPath(tile, path);
				}
			}
		});
		actions.addView(pathBtn);

		Button goBtn = new Button(getContext());
		goBtn.setText("Go");
		goBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				MapTile tile = selectedOrFirst();
				if (tile == null) {
					Toast.makeText(getContext(), "No result", Toast.LENGTH_SHORT).show();
					return;
				}
				List<String> path = pathTo(tile);
				if (callback != null) {
					callback.onGo(tile, path);
				}
				dismiss();
			}
		});
		actions.addView(goBtn);

		Button closeBtn = new Button(getContext());
		closeBtn.setText("Close");
		closeBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
		actions.addView(closeBtn);
		root.addView(actions);

		setContentView(root);
		EditorDialogChrome.applyNearlyFullScreen(this);
	}

	private List<String> pathTo(MapTile tile) {
		MudMap map = controller.getMap();
		if (map == null || tile == null) {
			return new ArrayList<String>();
		}
		return MapPathfinder.findCommands(map, map.getCurrentTileId(), tile.getId());
	}

	private void doSearch() {
		results = controller.search(queryEdit.getText().toString());
		selected = results.isEmpty() ? null : results.get(0);
		List<String> labels = new ArrayList<String>();
		for (MapTile t : results) {
			String title = t.getTitle() != null && t.getTitle().length() > 0
					? t.getTitle() : "(untitled)";
			String notes = t.getNotes() != null ? t.getNotes() : "";
			if (notes.length() > 40) {
				notes = notes.substring(0, 40) + "…";
			}
			labels.add(title + (notes.length() > 0 ? " — " + notes : ""));
		}
		resultsList.setAdapter(new ArrayAdapter<String>(
				getContext(), android.R.layout.simple_list_item_1, labels));
		if (results.isEmpty()) {
			Toast.makeText(getContext(), "No matches", Toast.LENGTH_SHORT).show();
		}
	}

	private MapTile selectedOrFirst() {
		if (selected != null) {
			return selected;
		}
		return results.isEmpty() ? null : results.get(0);
	}
}

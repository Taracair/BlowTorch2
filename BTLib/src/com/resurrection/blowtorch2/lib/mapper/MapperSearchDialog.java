package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
 * Works from a {@link MudMap} snapshot (UI process) or a live controller.
 */
public class MapperSearchDialog extends Dialog {

	public interface Callback {
		void onPath(MapTile tile, List<String> path);
		void onGo(MapTile tile, List<String> path);
	}

	private final MudMap map;
	private final Callback callback;
	private EditText queryEdit;
	private ListView resultsList;
	private List<MapTile> results = new ArrayList<MapTile>();
	private MapTile selected;

	public MapperSearchDialog(Context context, MapperController controller,
			Callback callback) {
		this(context, controller != null ? controller.getMap() : null, callback);
	}

	public MapperSearchDialog(Context context, MudMap map, Callback callback) {
		super(context, EditorDialogChrome.dialogTheme());
		this.map = map;
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
			public void onItemClick(AdapterView<?> parent, View view, int position,
					long id) {
				if (position >= 0 && position < results.size()) {
					selected = results.get(position);
				}
			}
		});

		LinearLayout actions = new LinearLayout(getContext());
		actions.setOrientation(LinearLayout.HORIZONTAL);

		Button pathBtn = new Button(getContext());
		pathBtn.setText("Show path");
		pathBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				MapTile tile = selectedOrFirst();
				if (tile == null) {
					Toast.makeText(getContext(), "No result", Toast.LENGTH_SHORT).show();
					return;
				}
				if (callback != null) {
					callback.onPath(tile, pathTo(tile));
				}
			}
		});
		actions.addView(pathBtn);

		Button goBtn = new Button(getContext());
		goBtn.setText("Go there");
		goBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				MapTile tile = selectedOrFirst();
				if (tile == null) {
					Toast.makeText(getContext(), "No result", Toast.LENGTH_SHORT).show();
					return;
				}
				if (callback != null) {
					callback.onGo(tile, pathTo(tile));
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
		if (map == null || tile == null) {
			return new ArrayList<String>();
		}
		return MapPathfinder.findCommands(map, map.getCurrentTileId(), tile.getId());
	}

	private void doSearch() {
		String q = queryEdit.getText() != null
				? queryEdit.getText().toString().trim() : "";
		results = searchMap(map, q);
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
			Toast.makeText(getContext(),
					map == null ? "No map loaded" : "No matches",
					Toast.LENGTH_SHORT).show();
		}
	}

	static List<MapTile> searchMap(MudMap map, String query) {
		ArrayList<MapTile> out = new ArrayList<MapTile>();
		if (map == null) {
			return out;
		}
		String q = query != null ? query.trim().toLowerCase(Locale.US) : "";
		for (MapTile t : map.getTiles()) {
			if (t == null) {
				continue;
			}
			if (q.length() == 0) {
				out.add(t);
				continue;
			}
			String title = t.getTitle() != null ? t.getTitle().toLowerCase(Locale.US) : "";
			String notes = t.getNotes() != null ? t.getNotes().toLowerCase(Locale.US) : "";
			if (title.contains(q) || notes.contains(q)) {
				out.add(t);
			}
		}
		return out;
	}

	private MapTile selectedOrFirst() {
		if (selected != null) {
			return selected;
		}
		return results.isEmpty() ? null : results.get(0);
	}
}

package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.window.EditorDialogChrome;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

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
		super(context, EditorDialogChrome.fullScreenTheme());
		this.map = map;
		this.callback = callback;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setCanceledOnTouchOutside(false);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		setContentView(R.layout.mapper_list_shell);

		((TextView) findViewById(R.id.titlebar)).setText("Find on map");
		LinearLayout body = (LinearLayout) findViewById(R.id.mapper_shell_body);

		resultsList = new ListView(getContext());
		resultsList.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
		styleList(resultsList);
		body.addView(resultsList);
		resultsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position,
					long id) {
				if (position >= 0 && position < results.size()) {
					selected = results.get(position);
				}
			}
		});

		LinearLayout searchStrip = new LinearLayout(getContext());
		searchStrip.setOrientation(LinearLayout.VERTICAL);
		searchStrip.setBackgroundResource(R.drawable.editor_bottom_bar_bg);
		int stripPad = dip(getContext(), 6);
		searchStrip.setPadding(stripPad, stripPad, stripPad, dip(getContext(), 2));

		queryEdit = new EditText(getContext());
		queryEdit.setHint("Title or notes");
		queryEdit.setSingleLine(true);
		queryEdit.setMinHeight(dip(getContext(), 42));
		queryEdit.setBackgroundResource(R.drawable.editor_search_field_bg);
		queryEdit.setTextColor(ContextCompat.getColor(getContext(), R.color.chrome_title_text));
		queryEdit.setHintTextColor(ContextCompat.getColor(getContext(), R.color.chrome_hint));
		int fieldPadH = dip(getContext(), 10);
		int fieldPadV = dip(getContext(), 8);
		queryEdit.setPadding(fieldPadH, fieldPadV, fieldPadH, fieldPadV);
		queryEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
		searchStrip.addView(queryEdit, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));
		body.addView(searchStrip);

		LinearLayout footer = (LinearLayout) findViewById(R.id.button_row);

		Button searchBtn = barButton(getContext(), "Search", false);
		searchBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				doSearch();
			}
		});
		footer.addView(searchBtn);

		Button pathBtn = barButton(getContext(), "Show path", true);
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
		footer.addView(pathBtn);

		Button goBtn = barButton(getContext(), "Go there", true);
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
		footer.addView(goBtn);

		Button closeBtn = barButton(getContext(), "Close", true);
		closeBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
		footer.addView(closeBtn);

		EditorDialogChrome.applyFullScreen(this);
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
				getContext(), android.R.layout.simple_list_item_1, labels) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				TextView tv = (TextView) super.getView(position, convertView, parent);
				tv.setTextColor(ContextCompat.getColor(getContext(),
						R.color.chrome_title_text));
				return tv;
			}
		});
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

	private void styleList(ListView list) {
		list.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.chrome_body));
		list.setCacheColorHint(0x00000000);
		list.setDivider(ContextCompat.getDrawable(getContext(), R.drawable.editor_row_divider));
		list.setDividerHeight(Math.max(1, dip(getContext(), 1)));
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

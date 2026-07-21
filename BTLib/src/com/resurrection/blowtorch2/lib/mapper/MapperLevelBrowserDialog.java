package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Lists all {@link MapLevel}s: name, tile count, optional “via &lt;anchor&gt;”.
 * Tap = view level (keeps dialog open); long-press = Go Here on that level.
 * Nest create and Delete are Edit-mode only.
 */
public final class MapperLevelBrowserDialog {

	public interface Host {
		MudMap getMap();
		String getCurrentLevelId();
		/** True when mapper is in Edit mode (not Browse). */
		boolean isEditMode();
		/** Browse / view a level by id (no create, no move Here). */
		void browseLevel(String levelId);
		/**
		 * View level and set Here on a tile there when possible
		 * (prefer same grid, else first tile).
		 */
		void goHereOnLevel(String levelId);
		/** Create / follow nest up from Here. */
		void floorUp();
		/** Create / follow nest down from Here. */
		void floorDown();
		/** After a browse, center camera on a tile if available. */
		void centerOnTile(MapTile tile);
		/**
		 * Delete a level by id. Host shows confirmation, then removes it.
		 */
		void deleteLevel(String levelId);
	}

	private MapperLevelBrowserDialog() {
	}

	public static void show(Context context, final Host host) {
		if (context == null || host == null) {
			return;
		}
		MudMap map = host.getMap();
		if (map == null) {
			Toast.makeText(context, "No map loaded", Toast.LENGTH_SHORT).show();
			return;
		}

		final boolean editMode = host.isEditMode();
		final List<MapLevel> levels = sortedLevels(map);
		final String currentId = host.getCurrentLevelId();
		final List<String> labels = new ArrayList<String>();
		for (MapLevel level : levels) {
			labels.add(formatRow(map, level, currentId));
		}

		final int[] selectedIndex = new int[] { -1 };
		for (int i = 0; i < levels.size(); i++) {
			MapLevel level = levels.get(i);
			if (level != null && currentId != null
					&& currentId.equals(level.getId())) {
				selectedIndex[0] = i;
				break;
			}
		}

		float density = context.getResources().getDisplayMetrics().density;
		int pad = (int) (12 * density);

		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(pad, pad, pad, pad);

		TextView subtitleView = new TextView(context);
		subtitleView.setText(editMode ? "Edit mode" : "Browse mode");
		subtitleView.setTextSize(13f);
		subtitleView.setTextColor(0xFFAAAAAA);
		subtitleView.setPadding(0, 0, 0, pad / 2);
		root.addView(subtitleView);

		final TextView hint = new TextView(context);
		hint.setTextSize(12f);
		hint.setTextColor(0xFFAAAAAA);
		hint.setPadding(0, 0, 0, pad / 2);
		root.addView(hint);

		final ListView list = new ListView(context);
		final ArrayAdapter<String> adapter = new ArrayAdapter<String>(context,
				android.R.layout.simple_list_item_1, labels);
		list.setAdapter(adapter);
		LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				(int) (320 * density));
		list.setLayoutParams(listLp);
		root.addView(list);

		final Button deleteBtn;
		if (editMode) {
			deleteBtn = new Button(context);
			deleteBtn.setText("Delete…");
			LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			delLp.topMargin = pad / 2;
			deleteBtn.setLayoutParams(delLp);
			root.addView(deleteBtn);
		} else {
			deleteBtn = null;
		}

		updateHint(hint, editMode, levels, selectedIndex[0]);
		updateDeleteEnabled(deleteBtn, levels, selectedIndex[0]);

		AlertDialog.Builder builder = new AlertDialog.Builder(context)
				.setTitle("Levels")
				.setView(root)
				.setNegativeButton("Close", null);
		if (editMode) {
			builder.setNeutralButton("↑ nest", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface d, int which) {
					host.floorUp();
				}
			});
			builder.setPositiveButton("↓ nest", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface d, int which) {
					host.floorDown();
				}
			});
		}

		final AlertDialog dialog = builder.create();

		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (position < 0 || position >= levels.size()) {
					return;
				}
				selectedIndex[0] = position;
				MapLevel level = levels.get(position);
				host.browseLevel(level.getId());
				MapTile first = firstTileOnLevel(map, level.getId());
				if (first != null) {
					host.centerOnTile(first);
				}
				updateHint(hint, editMode, levels, selectedIndex[0]);
				updateDeleteEnabled(deleteBtn, levels, selectedIndex[0]);
			}
		});
		list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (position < 0 || position >= levels.size()) {
					return true;
				}
				selectedIndex[0] = position;
				MapLevel level = levels.get(position);
				MapTile first = firstTileOnLevel(map, level.getId());
				if (first == null) {
					Toast.makeText(context, "No tiles on this level",
							Toast.LENGTH_SHORT).show();
					updateHint(hint, editMode, levels, selectedIndex[0]);
					updateDeleteEnabled(deleteBtn, levels, selectedIndex[0]);
					return true;
				}
				host.goHereOnLevel(level.getId());
				host.centerOnTile(first);
				Toast.makeText(context, "Here → " + shortTileLabel(first),
						Toast.LENGTH_SHORT).show();
				dialog.dismiss();
				return true;
			}
		});

		if (deleteBtn != null) {
			deleteBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					int idx = selectedIndex[0];
					if (idx < 0 || idx >= levels.size()) {
						Toast.makeText(context, "Select a level first",
								Toast.LENGTH_SHORT).show();
						return;
					}
					if (levels.size() <= 1) {
						Toast.makeText(context, "Cannot delete the last level",
								Toast.LENGTH_SHORT).show();
						return;
					}
					MapLevel level = levels.get(idx);
					dialog.dismiss();
					host.deleteLevel(level.getId());
				}
			});
		}

		dialog.show();
	}

	private static void updateHint(TextView hint, boolean editMode,
			List<MapLevel> levels, int selectedIndex) {
		if (hint == null) {
			return;
		}
		String base;
		if (editMode) {
			base = "Tap = view · Long-press = Go Here · Use Delete on a selected row (Edit only).";
		} else {
			base = "Tap = view floor · Long-press = Go Here. Creating nests requires Edit mode.";
		}
		String selectedLabel = selectedLabel(levels, selectedIndex);
		if (selectedLabel != null) {
			hint.setText(base + "\nSelected: " + selectedLabel);
		} else {
			hint.setText(base);
		}
	}

	private static void updateDeleteEnabled(Button deleteBtn,
			List<MapLevel> levels, int selectedIndex) {
		if (deleteBtn == null) {
			return;
		}
		boolean canDelete = levels != null && levels.size() > 1
				&& selectedIndex >= 0
				&& selectedIndex < levels.size();
		deleteBtn.setEnabled(canDelete);
	}

	private static String selectedLabel(List<MapLevel> levels, int selectedIndex) {
		if (levels == null || selectedIndex < 0 || selectedIndex >= levels.size()) {
			return null;
		}
		MapLevel level = levels.get(selectedIndex);
		if (level == null) {
			return null;
		}
		if (level.getName() != null && level.getName().length() > 0) {
			return level.getName();
		}
		return String.valueOf(level.getIndex());
	}

	private static List<MapLevel> sortedLevels(MudMap map) {
		List<MapLevel> list = new ArrayList<MapLevel>(map.getLevels());
		Collections.sort(list, new Comparator<MapLevel>() {
			@Override
			public int compare(MapLevel a, MapLevel b) {
				int ai = a != null ? a.getIndex() : 0;
				int bi = b != null ? b.getIndex() : 0;
				if (ai != bi) {
					return ai < bi ? -1 : 1;
				}
				String an = a != null && a.getName() != null ? a.getName() : "";
				String bn = b != null && b.getName() != null ? b.getName() : "";
				return an.compareToIgnoreCase(bn);
			}
		});
		return list;
	}

	private static String formatRow(MudMap map, MapLevel level, String currentId) {
		if (level == null) {
			return "?";
		}
		boolean cur = currentId != null && currentId.equals(level.getId());
		String name = level.getName() != null && level.getName().length() > 0
				? level.getName()
				: String.valueOf(level.getIndex());
		int tiles = countTiles(map, level.getId());
		StringBuilder sb = new StringBuilder();
		if (cur) {
			sb.append("* ");
		} else {
			sb.append("  ");
		}
		sb.append(name);
		sb.append("  ·  ");
		if (tiles <= 0) {
			sb.append("empty");
		} else {
			sb.append(tiles).append(tiles == 1 ? " tile" : " tiles");
		}
		String via = viaLabel(map, level);
		if (via != null) {
			sb.append("  ·  via ").append(via);
		}
		return sb.toString();
	}

	private static String viaLabel(MudMap map, MapLevel level) {
		if (level == null) {
			return null;
		}
		String anchorId = level.getAnchorTileId();
		if (anchorId == null || anchorId.length() == 0 || map == null) {
			return null;
		}
		MapTile anchor = map.findTile(anchorId);
		String via;
		if (anchor != null) {
			via = shortTileLabel(anchor);
		} else {
			via = anchorId.length() > 8 ? anchorId.substring(0, 8) : anchorId;
		}
		String dir = level.getAnchorDir();
		if (dir != null && dir.length() > 0) {
			return via + " (" + dir + ")";
		}
		return via;
	}

	private static int countTiles(MudMap map, String levelId) {
		if (map == null || levelId == null) {
			return 0;
		}
		int n = 0;
		for (MapTile t : map.getTiles()) {
			if (t != null && levelId.equals(t.getLevelId())) {
				n++;
			}
		}
		return n;
	}

	private static MapTile firstTileOnLevel(MudMap map, String levelId) {
		if (map == null || levelId == null) {
			return null;
		}
		MapTile best = null;
		for (MapTile t : map.getTiles()) {
			if (t == null || !levelId.equals(t.getLevelId())) {
				continue;
			}
			if (best == null
					|| t.getGridY() < best.getGridY()
					|| (t.getGridY() == best.getGridY() && t.getGridX() < best.getGridX())) {
				best = t;
			}
		}
		return best;
	}

	private static String shortTileLabel(MapTile tile) {
		if (tile == null) {
			return "?";
		}
		if (tile.getTitle() != null && tile.getTitle().length() > 0) {
			return tile.getTitle();
		}
		String id = tile.getId();
		if (id == null || id.length() == 0) {
			return "?";
		}
		return id.length() > 6 ? id.substring(0, 6) : id;
	}
}

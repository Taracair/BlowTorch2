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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Lists all {@link MapLevel}s: name, index, tile count, optional “via &lt;anchor&gt;”.
 * Tap = view level; long-press = set Here on first tile of that level.
 */
public final class MapperLevelBrowserDialog {

	public interface Host {
		MudMap getMap();
		String getCurrentLevelId();
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

		final List<MapLevel> levels = sortedLevels(map);
		final String currentId = host.getCurrentLevelId();
		final List<String> labels = new ArrayList<String>();
		for (MapLevel level : levels) {
			labels.add(formatRow(map, level, currentId));
		}

		float density = context.getResources().getDisplayMetrics().density;
		int pad = (int) (12 * density);

		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(pad, pad, pad, pad);

		TextView hint = new TextView(context);
		hint.setText("Tap = View · Long-press = Go Here on first tile");
		hint.setTextSize(12f);
		hint.setTextColor(0xFFAAAAAA);
		hint.setPadding(0, 0, 0, pad / 2);
		root.addView(hint);

		final ListView list = new ListView(context);
		list.setAdapter(new ArrayAdapter<String>(context,
				android.R.layout.simple_list_item_1, labels));
		LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				(int) (320 * density));
		list.setLayoutParams(listLp);
		root.addView(list);

		final AlertDialog dialog = new AlertDialog.Builder(context)
				.setTitle("Levels")
				.setView(root)
				.setNeutralButton("Floor ↑", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface d, int which) {
						host.floorUp();
					}
				})
				.setPositiveButton("Floor ↓", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface d, int which) {
						host.floorDown();
					}
				})
				.setNegativeButton("Close", null)
				.create();

		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (position < 0 || position >= levels.size()) {
					return;
				}
				MapLevel level = levels.get(position);
				host.browseLevel(level.getId());
				MapTile first = firstTileOnLevel(map, level.getId());
				if (first != null) {
					host.centerOnTile(first);
				}
				dialog.dismiss();
			}
		});
		list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (position < 0 || position >= levels.size()) {
					return true;
				}
				MapLevel level = levels.get(position);
				MapTile first = firstTileOnLevel(map, level.getId());
				if (first == null) {
					Toast.makeText(context, "No tiles on this level",
							Toast.LENGTH_SHORT).show();
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

		dialog.show();
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
		String name = level.getName() != null ? level.getName() : level.getId();
		int tiles = countTiles(map, level.getId());
		StringBuilder sb = new StringBuilder();
		if (cur) {
			sb.append("* ");
		} else {
			sb.append("  ");
		}
		sb.append(name).append("  [").append(level.getIndex()).append("]");
		sb.append("  ·  ").append(tiles).append(tiles == 1 ? " tile" : " tiles");
		String via = viaLabel(map, level);
		if (via != null) {
			sb.append("\n    via ").append(via);
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

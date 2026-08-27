package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.resurrection.blowtorch2.lib.R;

/**
 * The ⋮ list: section headers plus one row per {@link MenuItem}.
 *
 * <p>Button Sets is Lua {@code PopulateMenu}, id 401, order 401. In the old
 * flat list that put it between Options and Edit buttons. Grouping is by
 * item id, not by that Menu order, so Aliases stay with Button Sets and
 * Options stay with Plugins.
 */
public class GameplayMenuAdapter extends BaseAdapter {

	public static final int VIEW_TYPE_HEADER = 0;
	public static final int VIEW_TYPE_ITEM = 1;

	public enum Section {
		EDITORS("EDITORS"),
		SESSION("SESSION"),
		CONNECTION("CONNECTION"),
		TOOLS("TOOLS"),
		ABOUT("ABOUT"),
		MORE("MORE");

		public final String label;

		Section(String label) {
			this.label = label;
		}
	}

	public static final class Row {
		public final boolean header;
		public final String text;
		public final int itemId;
		/** Index in the visible-item list passed to the adapter; -1 for headers. */
		public final int sourceIndex;

		private Row(boolean header, String text, int itemId, int sourceIndex) {
			this.header = header;
			this.text = text;
			this.itemId = itemId;
			this.sourceIndex = sourceIndex;
		}

		static Row header(String text) {
			return new Row(true, text, 0, -1);
		}

		static Row item(int itemId, String text, int sourceIndex) {
			return new Row(false, text != null ? text : "", itemId, sourceIndex);
		}
	}

	private final LayoutInflater mInflater;
	private final List<MenuItem> mItems;
	private final ArrayList<Row> mRows;

	public GameplayMenuAdapter(Context context, List<MenuItem> items) {
		mInflater = LayoutInflater.from(context);
		mItems = items;
		int n = items.size();
		int[] ids = new int[n];
		String[] titles = new String[n];
		for (int i = 0; i < n; i++) {
			MenuItem item = items.get(i);
			ids[i] = item.getItemId();
			CharSequence title = item.getTitle();
			titles[i] = title != null ? title.toString() : "";
		}
		mRows = buildRows(ids, titles);
	}

	/**
	 * Which section a visible item belongs in. Known ids are the Java menu;
	 * 401 is Lua Button Sets. Anything else from {@code PopulateMenu} goes
	 * under EDITORS if the title looks like a button entry, otherwise MORE
	 * so a plugin item cannot vanish.
	 */
	public static Section sectionFor(int itemId, String title) {
		switch (itemId) {
			case 100:
			case 200:
			case 300:
			case 401:
			case 450:
				return Section.EDITORS;
			case 400:
			case 500:
			case 520:
			case 600:
				return Section.SESSION;
			case 700:
			case 800:
			case 900:
				return Section.CONNECTION;
			case 1040:
			case 1050:
			case 1060:
			case 1100:
				return Section.TOOLS;
			case 1500:
			case 1600:
			case 1700:
				return Section.ABOUT;
			default:
				if (titleLooksButtonRelated(title)) {
					return Section.EDITORS;
				}
				return Section.MORE;
		}
	}

	public static boolean titleLooksButtonRelated(String title) {
		if (title == null) {
			return false;
		}
		return title.toLowerCase(Locale.US).contains("button");
	}

	/**
	 * Headers in enum order, items in the order they arrived within each
	 * section. Empty sections are omitted.
	 */
	public static ArrayList<Row> buildRows(int[] ids, String[] titles) {
		if (ids == null || titles == null || ids.length != titles.length) {
			throw new IllegalArgumentException("ids and titles must be the same length");
		}
		Section[] sections = Section.values();
		ArrayList<ArrayList<Integer>> buckets = new ArrayList<ArrayList<Integer>>(sections.length);
		for (int i = 0; i < sections.length; i++) {
			buckets.add(new ArrayList<Integer>());
		}
		for (int i = 0; i < ids.length; i++) {
			Section section = sectionFor(ids[i], titles[i]);
			buckets.get(section.ordinal()).add(i);
		}
		ArrayList<Row> rows = new ArrayList<Row>();
		for (Section section : sections) {
			ArrayList<Integer> group = buckets.get(section.ordinal());
			if (group.isEmpty()) {
				continue;
			}
			rows.add(Row.header(section.label));
			for (int sourceIndex : group) {
				rows.add(Row.item(ids[sourceIndex], titles[sourceIndex], sourceIndex));
			}
		}
		return rows;
	}

	/** The MenuItem for a list position, or null if that row is a header. */
	public MenuItem getMenuItemAt(int position) {
		if (position < 0 || position >= mRows.size()) {
			return null;
		}
		int sourceIndex = mRows.get(position).sourceIndex;
		if (sourceIndex < 0 || sourceIndex >= mItems.size()) {
			return null;
		}
		return mItems.get(sourceIndex);
	}

	@Override
	public int getCount() {
		return mRows.size();
	}

	@Override
	public Object getItem(int position) {
		return mRows.get(position);
	}

	@Override
	public long getItemId(int position) {
		Row row = mRows.get(position);
		if (row.header) {
			return Long.MIN_VALUE + position;
		}
		return row.itemId;
	}

	@Override
	public int getViewTypeCount() {
		return 2;
	}

	@Override
	public int getItemViewType(int position) {
		return mRows.get(position).header ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
	}

	@Override
	public boolean areAllItemsEnabled() {
		return false;
	}

	@Override
	public boolean isEnabled(int position) {
		return !mRows.get(position).header;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		Row row = mRows.get(position);
		TextView tv;
		if (convertView == null) {
			int layout = row.header ? R.layout.gameplay_menu_header : R.layout.gameplay_menu_row;
			tv = (TextView) mInflater.inflate(layout, parent, false);
		} else {
			tv = (TextView) convertView;
		}
		tv.setText(row.text);
		return tv;
	}
}

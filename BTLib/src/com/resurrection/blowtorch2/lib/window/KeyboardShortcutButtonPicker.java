package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.List;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcutButtons.Button;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcutButtons.Group;

import android.app.Dialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

/** Every button set, full screen, same chrome as the shortcut form. */
public final class KeyboardShortcutButtonPicker extends Dialog {

	public interface Choice {
		void picked(Button button);
	}

	private final List<Group> groups;
	private final List<Row> rows = new ArrayList<Row>();
	private final Choice choice;
	private final TextView empty;
	private final ListView list;
	private final RowAdapter adapter;

	private KeyboardShortcutButtonPicker(final Context context, final String raw, final Choice choice) {
		super(context, EditorDialogChrome.fullScreenTheme());
		this.choice = choice;
		this.groups = KeyboardShortcutButtons.groups(raw);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		Window window = getWindow();
		if (window != null) {
			window.setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		}

		float density = context.getResources().getDisplayMetrics().density;
		int body = ContextCompat.getColor(context, R.color.chrome_body);
		int ink = ContextCompat.getColor(context, R.color.chrome_title_text);
		int hint = ContextCompat.getColor(context, R.color.chrome_hint);
		int bar = ContextCompat.getColor(context, R.color.chrome_title_bar);

		LinearLayout shell = new LinearLayout(context);
		shell.setOrientation(LinearLayout.VERTICAL);
		shell.setBackgroundColor(body);
		shell.addView(title(context, "Use a button", density, ink, bar));

		empty = new TextView(context);
		empty.setText("This set has no buttons.");
		empty.setTextColor(ink);
		empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
		empty.setGravity(Gravity.CENTER_HORIZONTAL);
		int pad = (int) (16 * density);
		empty.setPadding(pad, pad, pad, pad);

		adapter = new RowAdapter(context, rows, ink, bar, body, density);
		list = new ListView(context);
		list.setAdapter(adapter);
		list.setBackgroundColor(body);
		list.setCacheColorHint(body);
		list.setDivider(null);
		list.setDividerHeight(0);
		list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(final android.widget.AdapterView<?> parent, final View view,
					final int position, final long id) {
				if (position < 0 || position >= rows.size()) {
					return;
				}
				Button button = rows.get(position).button;
				if (button == null) {
					return;
				}
				KeyboardShortcutButtonPicker.this.choice.picked(button);
				dismiss();
			}
		});

		FrameLayout middle = new FrameLayout(context);
		middle.addView(list, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		middle.addView(empty, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
				Gravity.TOP));
		shell.addView(middle, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		EditText query = new EditText(context);
		query.setHint("Search");
		query.setSingleLine(true);
		query.setTextColor(ink);
		query.setHintTextColor(hint);
		query.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
		query.setBackgroundResource(R.drawable.editor_search_field_bg);
		int fieldPad = (int) (10 * density);
		query.setPadding(fieldPad, (int) (8 * density), fieldPad, (int) (8 * density));
		query.setMinHeight((int) (42 * density));
		LinearLayout search = new LinearLayout(context);
		search.setOrientation(LinearLayout.VERTICAL);
		search.setBackgroundColor(bar);
		int strip = (int) (6 * density);
		search.setPadding(strip, strip, strip, (int) (2 * density));
		search.addView(query, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		shell.addView(search);

		android.widget.Button close = new android.widget.Button(context);
		close.setText("Close");
		close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				dismiss();
			}
		});
		shell.addView(buttonBar(context, density, bar, close));

		query.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(final CharSequence s, final int start, final int count,
					final int after) {
			}

			@Override
			public void onTextChanged(final CharSequence s, final int start, final int before,
					final int count) {
				show(s == null ? "" : s.toString());
			}

			@Override
			public void afterTextChanged(final Editable s) {
			}
		});

		setContentView(shell, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		EditorDialogChrome.applyFullScreen(this);
		show("");
	}

	public static void show(final Context context, final String raw, final Choice choice) {
		if (context == null || choice == null) {
			return;
		}
		new KeyboardShortcutButtonPicker(context, raw, choice).show();
	}

	private void show(final String query) {
		List<Group> matched = KeyboardShortcutButtons.matchingGroups(groups, query);
		rows.clear();
		for (int i = 0; i < matched.size(); i++) {
			Group group = matched.get(i);
			if (group.name.length() > 0) {
				rows.add(Row.header(group.name));
			}
			for (int j = 0; j < group.buttons.size(); j++) {
				rows.add(Row.button(group.buttons.get(j)));
			}
		}
		boolean none = groups.isEmpty();
		empty.setVisibility(none ? View.VISIBLE : View.GONE);
		list.setVisibility(none ? View.GONE : View.VISIBLE);
		adapter.notifyDataSetChanged();
	}

	private static TextView title(final Context context, final String text, final float density,
			final int ink, final int bar) {
		TextView titleView = new TextView(context);
		titleView.setText(text);
		titleView.setTextColor(ink);
		titleView.setBackgroundColor(bar);
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
		titleView.setGravity(Gravity.CENTER);
		titleView.setLayoutParams(new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, (int) (42 * density)));
		return titleView;
	}

	private static LinearLayout buttonBar(final Context context, final float density, final int bar,
			final android.widget.Button button) {
		LinearLayout footer = new LinearLayout(context);
		footer.setOrientation(LinearLayout.HORIZONTAL);
		footer.setGravity(Gravity.CENTER);
		int barPad = (int) (6 * density);
		footer.setPadding(barPad, barPad, barPad, barPad);
		footer.setBackgroundColor(bar);
		button.setMinHeight((int) (44 * density));
		footer.addView(button, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return footer;
	}

	private static final class Row {
		final String header;
		final Button button;

		private Row(final String header, final Button button) {
			this.header = header;
			this.button = button;
		}

		static Row header(final String name) {
			return new Row(name, null);
		}

		static Row button(final Button button) {
			return new Row(null, button);
		}
	}

	private static final class RowAdapter extends BaseAdapter {
		private final Context context;
		private final List<Row> rows;
		private final int ink;
		private final int bar;
		private final int body;
		private final float density;

		RowAdapter(final Context context, final List<Row> rows, final int ink, final int bar,
				final int body, final float density) {
			this.context = context;
			this.rows = rows;
			this.ink = ink;
			this.bar = bar;
			this.body = body;
			this.density = density;
		}

		@Override
		public int getCount() {
			return rows.size();
		}

		@Override
		public Object getItem(final int position) {
			return rows.get(position);
		}

		@Override
		public long getItemId(final int position) {
			return position;
		}

		@Override
		public int getViewTypeCount() {
			return 2;
		}

		@Override
		public int getItemViewType(final int position) {
			return rows.get(position).button == null ? 0 : 1;
		}

		@Override
		public boolean isEnabled(final int position) {
			return rows.get(position).button != null;
		}

		@Override
		public View getView(final int position, final View convert, final ViewGroup parent) {
			Row row = rows.get(position);
			TextView view = convert instanceof TextView ? (TextView) convert : new TextView(context);
			int h = (int) (16 * density);
			if (row.button == null) {
				view.setText(row.header);
				view.setTextColor(ink);
				view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
				view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
				view.setBackgroundColor(bar);
				view.setPadding(h, (int) (10 * density), h, (int) (6 * density));
				return view;
			}
			view.setText(row.button.listLabel());
			view.setTextColor(ink);
			view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
			view.setTypeface(android.graphics.Typeface.DEFAULT);
			view.setSingleLine(true);
			view.setEllipsize(android.text.TextUtils.TruncateAt.END);
			view.setBackgroundColor(body);
			view.setPadding(h, (int) (8 * density), h, (int) (8 * density));
			return view;
		}
	}
}

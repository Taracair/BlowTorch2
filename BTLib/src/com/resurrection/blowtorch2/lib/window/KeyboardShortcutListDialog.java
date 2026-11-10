package com.resurrection.blowtorch2.lib.window;

import java.util.HashMap;
import java.util.Map;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsGroup;
import com.resurrection.blowtorch2.lib.speedwalk.DirectionData;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcuts.ListText;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcuts.Row;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

/**
 * The shortcut list. Same footer as the alias list: New, ?, More, Close.
 * Number pad is the pinned row at the top.
 */
public class KeyboardShortcutListDialog extends BaseSelectionDialog implements
		BaseSelectionDialog.UtilityToolbarListener, BaseSelectionDialog.OptionItemClickListener {

	private final MainWindow activity;
	private final IConnectionBinder service;
	private KeyboardShortcuts stored = KeyboardShortcuts.empty();
	private static final String PAD_KEY = "number-pad";
	private static final String[] GLOBAL_KEYS = {
			"shortcuts_on",
			"shortcut_pad",
			"shortcut_toprow",
			"shortcut_idle",
			"shortcut_ctrl_edit",
			"shortcut_prefix"
	};
	private boolean switchLock;

	public KeyboardShortcutListDialog(final MainWindow activity, final IConnectionBinder service) {
		super(activity);
		this.activity = activity;
		this.service = service;
		setToolbarListener(this);
		setOptionItemClickListener(this);
		setTitle("Keyboard shortcuts");
		addOptionItem("Delete all", true);
		stored = load();
		fill();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		showGlobalSwitches();
	}

	/** Close writes the switches. Five of them have no other save. */
	@Override
	public void dismiss() {
		com.resurrection.blowtorch2.lib.util.SettingsSaver.saveInBackground(service);
		super.dismiss();
	}

	@Override
	protected String getHelpTitle() {
		return "Keyboard shortcuts";
	}

	@Override
	protected String getHelpBody() {
		return EditorHelp.SHORTCUTS;
	}

	@Override
	public boolean dispatchKeyEvent(final KeyEvent event) {
		if (getCurrentFocus() instanceof EditText) {
			return super.dispatchKeyEvent(event);
		}
		if (event != null && event.getAction() == KeyEvent.ACTION_DOWN
				&& event.getRepeatCount() == 0
				&& event.getDeviceId() != -1 && event.getScanCode() != 0
				&& !isModifier(event.getKeyCode())) {
			Row row = stored.row(event.getKeyCode(), event.getMetaState());
			if (row != null) {
				openEditor(row.keyCode, row.modifiers);
				return true;
			}
		}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public void onButtonPressed(final android.view.View v, final int row, final int index) {
		if (PAD_KEY.equals(getItemKey(row))) {
			openPad();
			return;
		}
		Row data = rowFor(row);
		if (data == null) {
			return;
		}
		openEditor(data.keyCode, data.modifiers);
	}

	@Override
	public void onButtonStateChanged(final ImageButton v, final int row, final int index,
			final boolean state) {
		Row data = rowFor(row);
		if (data == null) {
			return;
		}
		boolean enabled = !data.enabled;
		stored = stored.withEnabled(data.keyCode, data.modifiers, enabled);
		write(stored);
		setItemEnabled(row, enabled);
		applyToggleTint(v, enabled);
	}

	@Override
	public void onItemDeleted(final int row) {
		String key = getItemKey(row);
		int[] chord = parseKey(key);
		if (chord == null) {
			return;
		}
		stored = stored.withoutRow(chord[0], chord[1]);
		write(stored);
	}

	@Override
	public void onNewPressed(final android.view.View v) {
		openEditor(-1, 0);
	}

	@Override
	public void onDonePressed(final android.view.View v) {
	}

	@Override
	public void willShowToolbar(final LinearLayout toolbar, final int row) {
		if (PAD_KEY.equals(getItemKey(row))) {
			for (int i = 0; i < toolbar.getChildCount(); i++) {
				if (i != 1) {
					toolbar.getChildAt(i).setVisibility(android.view.View.GONE);
				}
			}
			return;
		}
		Row data = rowFor(row);
		if (data == null || toolbar.getChildCount() == 0) {
			return;
		}
		android.view.View child = toolbar.getChildAt(0);
		if (child instanceof ImageButton) {
			applyToggleTint((ImageButton) child, data.enabled);
		}
	}

	@Override
	public void willHideToolbar(final LinearLayout v, final int row) {
	}

	@Override
	public void onOptionItemClicked(final int row) {
		if (row != 0) {
			return;
		}
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle("Delete all");
		builder.setMessage("Delete every shortcut in this list?");
		builder.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				dialog.dismiss();
				stored = stored.withoutRows().withPad(null, 0, -1);
				write(stored);
				fill();
			}
		});
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				dialog.dismiss();
			}
		});
		builder.create().show();
	}

	private void openEditor(final int keyCode, final int modifiers) {
		KeyboardShortcutEditorDialog.show(activity, stored, keyCode, modifiers,
				new KeyboardShortcutEditorDialog.Host() {
					@Override
					public void save(final KeyboardShortcuts next) {
						stored = next;
						write(stored);
						fill();
					}

					@Override
					public String buttonLines() {
						return activity.shortcutButtonLines();
					}
				});
	}

	private void openPad() {
		KeyboardShortcutPadDialog.show(activity, stored, new KeyboardShortcutPadDialog.Host() {
			@Override
			public void save(final KeyboardShortcuts next) {
				stored = next;
				write(stored);
				fill();
			}

			@Override
			public Map<String, DirectionData> directions() {
				return directionMap();
			}
		});
	}

	private Map<String, DirectionData> directionMap() {
		Map<String, DirectionData> out = new HashMap<String, DirectionData>();
		if (service == null) {
			return out;
		}
		try {
			Map raw = service.getDirectionData();
			if (raw == null) {
				return out;
			}
			for (Object item : raw.entrySet()) {
				Map.Entry entry = (Map.Entry) item;
				if (entry.getKey() != null && entry.getValue() instanceof DirectionData) {
					out.put(String.valueOf(entry.getKey()), (DirectionData) entry.getValue());
				}
			}
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"KeyboardShortcutListDialog", e);
		}
		return out;
	}

	private void fill() {
		clearListItems();
		addListItem(PAD_KEY, "Number pad", "Presets for the number pad", 0, true);
		setLastItemPinned();
		for (int i = 0; i < stored.rows().size(); i++) {
			Row row = stored.rows().get(i);
			ListText text = KeyboardShortcuts.listText(row);
			addListItem(KeyboardShortcuts.listKey(row.keyCode, row.modifiers), text.title,
					text.detail, 0, row.enabled);
			setLastItemSearch(text.search);
		}
		if (isShowing()) {
			invalidateList();
		}
	}

	/** Lower half. The six switches used to sit under Options → Typing. */
	private void showGlobalSwitches() {
		ScrollView panel = (ScrollView) findViewById(R.id.shortcut_globals);
		LinearLayout body = (LinearLayout) findViewById(R.id.shortcut_globals_body);
		if (panel == null || body == null || service == null) {
			return;
		}
		SettingsGroup group = settingsGroup();
		if (group == null) {
			return;
		}
		body.removeAllViews();
		int added = 0;
		for (int i = 0; i < GLOBAL_KEYS.length; i++) {
			if (addSwitch(body, group, GLOBAL_KEYS[i])) {
				added++;
			}
		}
		if (added == 0) {
			return;
		}
		TextView header = new TextView(getContext());
		header.setText("Keyboard");
		header.setTextColor(ContextCompat.getColor(getContext(), R.color.chrome_title_text));
		header.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.chrome_title_bar));
		header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		header.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
		header.setGravity(Gravity.CENTER_VERTICAL);
		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (10 * density);
		header.setPadding(pad, 0, pad, 0);
		header.setMinHeight((int) (34 * density));
		body.addView(header, 0);
		panel.setVisibility(View.VISIBLE);
		LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) panel.getLayoutParams();
		lp.height = 0;
		lp.weight = 1f;
		panel.setLayoutParams(lp);
	}

	private boolean addSwitch(final LinearLayout body, final SettingsGroup group, final String key) {
		Object found = group.findOptionByKey(key);
		if (!(found instanceof BooleanOption)) {
			return false;
		}
		final BooleanOption option = (BooleanOption) found;
		LayoutInflater inflater = LayoutInflater.from(getContext());
		final View row = inflater.inflate(R.layout.options_list_row, body, false);
		TextView title = (TextView) row.findViewById(R.id.infoTitle);
		TextView detail = (TextView) row.findViewById(R.id.infoExtended);
		title.setText(option.getTitle());
		detail.setText(option.getDescription());
		LinearLayout widget = (LinearLayout) row.findViewById(R.id.widget_frame);
		final CheckBox box = new CheckBox(getContext());
		box.setFocusable(false);
		box.setFocusableInTouchMode(false);
		Object value = option.getValue();
		box.setChecked(value instanceof Boolean && ((Boolean) value).booleanValue());
		widget.addView(box, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final CompoundButton button, final boolean checked) {
				if (switchLock) {
					return;
				}
				try {
					service.updateBooleanSetting(key, checked);
					if ("shortcuts_on".equals(key)) {
						stored = load();
						fill();
					}
				} catch (RemoteException e) {
					switchLock = true;
					box.setChecked(!checked);
					switchLock = false;
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
							"KeyboardShortcutListDialog", e);
				}
			}
		});
		row.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				box.setChecked(!box.isChecked());
			}
		});
		body.addView(row);
		View line = new View(getContext());
		line.setBackgroundResource(R.drawable.editor_row_divider);
		body.addView(line, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 1));
		return true;
	}

	private SettingsGroup settingsGroup() {
		if (service == null) {
			return null;
		}
		try {
			return service.getSettings();
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"KeyboardShortcutListDialog", e);
			return null;
		}
	}

	private void write(final KeyboardShortcuts next) {
		stored = next;
		if (service == null) {
			return;
		}
		try {
			service.updateStringSetting(KeyboardShortcuts.SETTING_KEY, next.format());
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"KeyboardShortcutListDialog", e);
		}
	}

	private KeyboardShortcuts load() {
		if (service == null) {
			return KeyboardShortcuts.empty();
		}
		try {
			SettingsGroup group = service.getSettings();
			if (group == null) {
				return KeyboardShortcuts.empty();
			}
			Object found = group.findOptionByKey(KeyboardShortcuts.SETTING_KEY);
			if (!(found instanceof BaseOption)) {
				return KeyboardShortcuts.empty();
			}
			Object value = ((BaseOption) found).getValue();
			return KeyboardShortcuts.parse(value == null ? "" : String.valueOf(value));
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"KeyboardShortcutListDialog", e);
			return KeyboardShortcuts.empty();
		}
	}

	private Row rowFor(final int row) {
		return rowFromKey(getItemKey(row));
	}

	private Row rowFromKey(final String key) {
		int[] chord = parseKey(key);
		if (chord == null) {
			return null;
		}
		return stored.row(chord[0], chord[1]);
	}

	private static int[] parseKey(final String key) {
		if (key == null) {
			return null;
		}
		int bar = key.indexOf('|');
		if (bar <= 0) {
			return null;
		}
		try {
			int code = Integer.parseInt(key.substring(0, bar));
			int mods = Integer.parseInt(key.substring(bar + 1));
			return new int[] { code, mods };
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static boolean isModifier(final int keyCode) {
		return keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT
				|| keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
				|| keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
				|| keyCode == KeyEvent.KEYCODE_META_LEFT || keyCode == KeyEvent.KEYCODE_META_RIGHT;
	}
}

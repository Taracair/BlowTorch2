package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsGroup;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcuts.ListText;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcuts.Row;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.RemoteException;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

/**
 * The shortcut list. Same footer as the alias list: New, ?, More, Close.
 * The number-pad form is not this screen.
 */
public class KeyboardShortcutListDialog extends BaseSelectionDialog implements
		BaseSelectionDialog.UtilityToolbarListener, BaseSelectionDialog.OptionItemClickListener {

	private final MainWindow activity;
	private final IConnectionBinder service;
	private KeyboardShortcuts stored = KeyboardShortcuts.empty();

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
				stored = stored.withoutRows();
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

	private void fill() {
		clearListItems();
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

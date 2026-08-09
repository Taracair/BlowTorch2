package com.resurrection.blowtorch2.lib.trigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.ack.AckResponder;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.window.EditorHelp;
import com.resurrection.blowtorch2.lib.window.MainWindow;
import com.resurrection.blowtorch2.lib.window.PluginFilterSelectionDialog;
import com.resurrection.blowtorch2.lib.service.sensor.GestureAvailability;
import com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog;
import com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Every sensor reading this phone can deliver, and what each one drives.
 *
 * <p><b>Why this exists.</b> A sensor trigger is stored as an ordinary trigger,
 * which is right for the engine and wrong for finding it afterwards: a player
 * who set one up with {@code .sensor facedown afk} had nowhere to look except a
 * list of dozens of triggers watching for game text. This is the place to look.
 * Options → Device → Sensors opens it.
 *
 * <p>It also answers what happens when a gesture has <em>more</em> than one
 * thing hanging off it. Every trigger with that gesture's pattern fires, so this
 * lists them all and says so, rather than pretending a gesture owns exactly one
 * action. Editing goes to the ordinary trigger editor, because that is where
 * scripts, sounds, speech and conditions already live.
 *
 * <p><b>What the screen is for.</b> Sixteen readings, of which a player wants
 * one. So they are grouped under the four headings in {@code GestureCatalog},
 * the ones this handset cannot provide are folded away at the bottom rather
 * than greyed in place, and a row is two lines: what it is, and one line of
 * state. The resolved sensor's model name is not on the row — it answers a
 * question nobody asked while scrolling, and {@code .sensor caps} still prints
 * it for the one person who wants it.
 *
 * <p>The chrome is the Alias / Trigger / Timer chooser's, down to the row
 * selector and the play / pencil icons, because this is another list of things
 * that fire and it should not be a screen of its own invention. What the list
 * is for lives behind the {@code ?} in the button bar, which is where those
 * three keep it.
 */
public class GestureListDialog extends Dialog {

	/** Left edge of a row that has something answering it. */
	private static final int ACCENT_LIVE = 0xFF9999FF;
	/** The same, on a reading this phone cannot provide. */
	private static final int ACCENT_LIVE_DEAD = 0xFF4C4C77;
	private static final int ACCENT_NONE = 0x00000000;

	private static final int TEXT_PRIMARY = 0xFFF2F4F6;
	private static final int TEXT_PRIMARY_DEAD = 0xFF6E7680;
	private static final int TEXT_SECONDARY = 0xFF9AA3AD;
	private static final int TEXT_CONFIGURED = 0xFF8FC9A0;

	/**
	 * Behind the {@code ?}. Everything the screen used to say in a paragraph
	 * above the list, plus the two things that surprise people, said once here
	 * instead of on every row.
	 */
	private static final String HELP =
			"Every reading this phone can deliver, and what each one drives.\n\n"
			+ "SETTING ONE UP\n"
			+ "Tap a row. It opens the ordinary trigger editor with the reading "
			+ "already chosen, so a sensor can do anything a trigger can: send a "
			+ "command, run a script, speak, play a sound, set a variable, or gate "
			+ "itself on a condition first.\n\n"
			+ "    Put the phone face down  ->  Ack  afk\n"
			+ "    Turn it face up again    ->  Ack  afk off\n\n"
			+ "TEST\n"
			+ "Fires the reading here and now, without moving the phone, so the "
			+ "actions run and you can see whether they were what you meant. It is "
			+ "not a test of the sensor itself: it proves what is set up, not that "
			+ "the phone can see you wave. The same thing from the input bar is\n"
			+ "    .sensor fire facedown\n\n"
			+ "NOT AVAILABLE ON THIS PHONE\n"
			+ "Sensor hardware differs between handsets, so readings this one cannot "
			+ "provide are folded away at the bottom. They are still tappable: a "
			+ "profile you export is played on somebody else's phone, which may well "
			+ "have the sensor. Which chip provides which reading here is\n"
			+ "    .sensor caps\n\n"
			+ "TWO THINGS TO KNOW\n"
			+ "A sensor trigger is not aimed at one world. It fires in every world "
			+ "you have open, so with two MUDs connected one shake sends its command "
			+ "twice.\n\n"
			+ "Movement readings are held back while the screen is off or another app "
			+ "is on top, so a phone in a pocket cannot send commands. Both switches "
			+ "are in Options -> Device, next to Calibrate shake and Calibrate light. "
			+ "Headphone, charger and screen readings are not affected by either.";

	private final IConnectionBinder service;
	private final boolean showRegexWarning;
	private LinearLayout rows;
	/** Whether the readings this phone cannot provide are folded open. */
	private boolean showUnavailable;

	public GestureListDialog(final Context context, final IConnectionBinder service,
			final boolean showRegexWarning) {
		super(context, R.style.BlowTorch_Dialog_FullScreen);
		this.service = service;
		this.showRegexWarning = showRegexWarning;
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		if (getContext() instanceof MainWindow
				&& ((MainWindow) getContext()).isStatusBarHidden()) {
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		setContentView(R.layout.sensor_list_dialog);
		fillTheScreen();

		rows = (LinearLayout) findViewById(R.id.rows);
		Button close = (Button) findViewById(R.id.close);
		close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				dismiss();
			}
		});
		Button help = (Button) findViewById(R.id.helpbutton);
		help.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				EditorHelp.show(getContext(), "Sensors", HELP);
			}
		});
		build();
	}

	/**
	 * Edge to edge, with the title strip below the status bar and the buttons
	 * above the navigation bar.
	 *
	 * <p>The same two steps {@code BaseSelectionDialog} takes, and for the same
	 * reason: the theme alone leaves the window its default size, and padding
	 * the root rather than the list keeps the title and the buttons out of the
	 * system bars while the list still scrolls the full height between them.
	 */
	private void fillTheScreen() {
		Window window = getWindow();
		if (window == null) {
			return;
		}
		window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.MATCH_PARENT);
		WindowManager.LayoutParams attrs = window.getAttributes();
		attrs.width = WindowManager.LayoutParams.MATCH_PARENT;
		attrs.height = WindowManager.LayoutParams.MATCH_PARENT;
		attrs.gravity = Gravity.FILL;
		window.setAttributes(attrs);

		final View root = findViewById(R.id.root);
		if (root == null) {
			return;
		}
		androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,
				(view, insets) -> {
					androidx.core.graphics.Insets bars = insets.getInsets(
							androidx.core.view.WindowInsetsCompat.Type.systemBars());
					view.setPadding(view.getPaddingLeft(), bars.top,
							view.getPaddingRight(), bars.bottom);
					return insets;
				});
		androidx.core.view.ViewCompat.requestApplyInsets(root);
	}

	/** Rebuilt rather than patched, so it cannot drift from the settings. */
	private void build() {
		rows.removeAllViews();
		HashMap<String, List<TriggerData>> byGesture = readTriggers();
		List<Gesture> unavailable = new ArrayList<Gesture>();
		String group = null;

		for (Gesture g : GestureCatalog.all()) {
			GestureAvailability.Resolution r =
					GestureAvailability.resolve(getContext(), g);
			if (!r.isAvailable()) {
				unavailable.add(g);
				continue;
			}
			if (!g.getGroup().equals(group)) {
				group = g.getGroup();
				addSection(group, null);
			}
			addRow(g, r, byGesture.get(g.getId()));
		}

		if (unavailable.isEmpty()) {
			return;
		}
		final String heading = (showUnavailable ? "\u25BE  " : "\u25B8  ")
				+ "Not available on this phone (" + unavailable.size() + ")";
		addSection(heading, new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				showUnavailable = !showUnavailable;
				build();
			}
		});
		if (!showUnavailable) {
			return;
		}
		for (Gesture g : unavailable) {
			addRow(g, GestureAvailability.resolve(getContext(), g),
					byGesture.get(g.getId()));
		}
	}

	private void addSection(final String title, final View.OnClickListener onClick) {
		TextView view = (TextView) LayoutInflater.from(getContext())
				.inflate(R.layout.sensor_list_section, rows, false);
		view.setText(title);
		if (onClick != null) {
			view.setOnClickListener(onClick);
			view.setBackgroundResource(R.drawable.editor_row_selector);
		}
		rows.addView(view);
	}

	private void addRow(final Gesture g, final GestureAvailability.Resolution r,
			final List<TriggerData> bound) {
		final boolean configured = bound != null && !bound.isEmpty();
		View row = LayoutInflater.from(getContext())
				.inflate(R.layout.sensor_list_row, rows, false);

		TextView label = (TextView) row.findViewById(R.id.label);
		label.setText(g.getLabel());
		label.setTextColor(r.isAvailable() ? TEXT_PRIMARY : TEXT_PRIMARY_DEAD);

		TextView status = (TextView) row.findViewById(R.id.status);
		status.setText(describe(g, r, bound));
		status.setTextColor(configured && r.isAvailable()
				? TEXT_CONFIGURED : TEXT_SECONDARY);

		View accent = row.findViewById(R.id.accent);
		accent.setBackgroundColor(!configured ? ACCENT_NONE
				: (r.isAvailable() ? ACCENT_LIVE : ACCENT_LIVE_DEAD));

		// Deliberately still tappable when the sensor is missing: profiles are
		// shared, and one built here should be buildable for a phone that does
		// have the sensor. The row already says it will not fire on this one.
		row.findViewById(R.id.row).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				openEditor(g, bound);
			}
		});

		Button test = (Button) row.findViewById(R.id.test);
		test.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				fire(g);
			}
		});

		rows.addView(row);
	}

	/**
	 * Fire a reading from the list, and say here what happened.
	 *
	 * <p>The reply {@code .sensor fire} prints goes to the game window, which is
	 * under this dialog <em>and</em> under Options. Pressing Test therefore
	 * looked like a button that did nothing, whatever it had done. So the
	 * answer is a toast, over the dialog the player is looking at.
	 *
	 * <p>What the toast can honestly say is what the UI already knows: whether
	 * any trigger answers this reading, and whether it is switched on. Whether
	 * the actions then worked is the game window's business, and the count is
	 * counted in the service, on the connection's own thread.
	 *
	 * <p>Asked again here rather than trusting what the list was built from.
	 * {@code sendData} returns quietly when the binder has no active connection
	 * — during a world switch, or after this one was dropped — and the same
	 * miss makes the trigger data come back empty, so reading it now is what
	 * stops Test claiming it fired something into a connection that is gone.
	 */
	private void fire(final Gesture g) {
		List<TriggerData> bound = readTriggers().get(g.getId());
		if (bound == null || bound.isEmpty()) {
			say("Nothing answers " + g.getLabel().toLowerCase(java.util.Locale.US)
					+ " yet. Tap the row to give it something to do.");
			return;
		}
		boolean anyEnabled = false;
		for (TriggerData t : bound) {
			anyEnabled = anyEnabled || t.isEnabled();
		}
		if (!anyEnabled) {
			say("That reading is turned off, so firing it does nothing.");
			return;
		}
		try {
			// The same path .sensor fire uses, which runs the actions on the
			// connection's own thread rather than on this one.
			service.sendData((".sensor fire " + g.getId() + "\r\n").getBytes("UTF-8"));
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"GestureListDialog.test", e);
			say("Could not reach the connection to fire that.");
			return;
		}
		say("Fired " + g.getId() + ". What it does shows in the game window.");
		// Left open on purpose. Closing this would leave the Options dialog on
		// top of the reply anyway.
	}

	private void say(final String text) {
		android.widget.Toast.makeText(getContext(), text,
				android.widget.Toast.LENGTH_SHORT).show();
	}

	/** The row's one line of state: why it cannot fire, what it does, or what it is. */
	private String describe(final Gesture g, final GestureAvailability.Resolution r,
			final List<TriggerData> bound) {
		StringBuilder out = new StringBuilder();
		if (!r.isAvailable()) {
			out.append(r.missingReason()).append(' ');
		}
		if (bound == null || bound.isEmpty()) {
			// With nothing set up, what the reading means is the useful thing to
			// say. Once something answers it, what that is matters more.
			out.append(g.getHelp());
			return out.toString();
		}
		TriggerData first = bound.get(0);
		if (!first.isEnabled()) {
			out.append("Turned off \u2014 ");
		}
		out.append(describeActions(first));
		if (bound.size() > 1) {
			// Not an error, and worth saying plainly: all of them run.
			out.append(" \u00b7 ").append(bound.size())
				.append(" triggers answer this, and all of them run");
		}
		return capitalised(out.toString());
	}

	private static String capitalised(final String text) {
		if (text.length() == 0) {
			return text;
		}
		return Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private String describeActions(final TriggerData t) {
		List<TriggerResponder> responders = t.getResponders();
		if (responders == null || responders.isEmpty()) {
			return "set up, but with no actions yet";
		}
		String command = null;
		int others = 0;
		for (TriggerResponder responder : responders) {
			if (command == null && responder instanceof AckResponder) {
				command = ((AckResponder) responder).getAckWith();
			} else {
				others++;
			}
		}
		StringBuilder out = new StringBuilder();
		if (command != null && command.length() > 0) {
			out.append("sends \"").append(command).append('"');
		} else {
			out.append("no command");
		}
		if (others > 0) {
			out.append(", plus ").append(others)
				.append(others == 1 ? " other action" : " other actions");
		}
		return out.toString();
	}

	/** Open the trigger editor on this gesture, making one if there is none. */
	private void openEditor(final Gesture g, final List<TriggerData> bound) {
		TriggerData target;
		boolean isEdit = bound != null && !bound.isEmpty();
		if (isEdit) {
			target = bound.get(0);
		} else {
			target = new TriggerData();
			target.setName(g.getId());
			target.setPattern(g.getPattern());
			target.setInterpretAsRegex(false);
			target.setEnabled(true);
			target.setSave(true);
		}
		TriggerEditorDialog editor = new TriggerEditorDialog(getContext(),
				isEdit ? target : null, service, new Handler() {
					@Override
					public void handleMessage(final Message msg) {
						build();
					}
				}, PluginFilterSelectionDialog.MAIN_SETTINGS, showRegexWarning);
		if (!isEdit) {
			editor.presetGesture(g);
		}
		editor.show();
	}

	/** Main-settings triggers, grouped by the gesture they answer to. */
	private HashMap<String, List<TriggerData>> readTriggers() {
		HashMap<String, List<TriggerData>> out = new HashMap<String, List<TriggerData>>();
		try {
			java.util.Map<?, ?> all = service.getTriggerData();
			if (all == null) {
				return out;
			}
			for (Object value : all.values()) {
				if (!(value instanceof TriggerData)) {
					continue;
				}
				TriggerData t = (TriggerData) value;
				Gesture g = GestureCatalog.fromPattern(t.getPattern(),
						!t.isInterpretAsRegex());
				if (g == null) {
					continue;
				}
				List<TriggerData> list = out.get(g.getId());
				if (list == null) {
					list = new ArrayList<TriggerData>();
					out.put(g.getId(), list);
				}
				list.add(t);
			}
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"GestureListDialog.readTriggers", e);
		}
		return out;
	}
}

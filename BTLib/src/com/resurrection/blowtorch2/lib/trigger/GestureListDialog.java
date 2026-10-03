package com.resurrection.blowtorch2.lib.trigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.ack.AckResponder;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.window.BaseSelectionDialog;
import com.resurrection.blowtorch2.lib.window.EditorHelp;
import com.resurrection.blowtorch2.lib.window.MainWindow;
import com.resurrection.blowtorch2.lib.window.PluginFilterSelectionDialog;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.Option;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsGroup;
import com.resurrection.blowtorch2.lib.service.sensor.GestureGate;
import com.resurrection.blowtorch2.lib.service.sensor.CustomShakeLibrary;
import com.resurrection.blowtorch2.lib.service.sensor.CustomShakeNames;
import com.resurrection.blowtorch2.lib.service.sensor.GestureAvailability;
import com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog;
import com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture;
import com.resurrection.blowtorch2.lib.service.sensor.SensorWorldFlags;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Options → Device → Sensors. One gesture can have several triggers; edit goes
 * to the ordinary trigger editor. Unavailable readings are folded away.
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
			+ "Watches the sensor while you do the gesture, and says whether the "
			+ "phone saw it. Works on every reading, including ones with nothing "
			+ "set up yet -- \"can this phone see me wave\" is worth knowing before "
			+ "you build anything on it.\n\n"
			+ "Where something is set up, the same screen has a button that runs "
			+ "the actions without moving the phone. That answers the other "
			+ "question: not whether the phone sees you, but whether what you set "
			+ "up is what you meant. From the input bar that one is\n"
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
			+ "are in Options -> Device, next to Calibrate shake, Calibrate light "
			+ "and Battery low threshold. "
			+ "Headphone, charger, battery, screen and rotation readings are not affected by either.\n\n"
			+ "landscape / portrait: the orientation the phone already has when you "
			+ "bind the reading is not a fire. Turn it the other way, then back.\n\n"
			+ "ON AND OFF\n"
			+ "The power button at the top silences every reading in this world. "
			+ "The power buttons on the rows stay as they were, and other worlds "
			+ "are not touched. From the input bar that is\n"
			+ "    .sensor all off\n\n"
			+ "A row's own power button turns that reading off without opening "
			+ "it. It is there once something is set up, the same button as on "
			+ "a trigger. Several triggers on one reading all follow it.\n\n"
			+ "MY SHAKES\n"
			+ "Record a shake draws each try as a line, on the screen and toward "
			+ "you, so you can see how far apart they are. Three lines before it "
			+ "saves; the slider says how close a later shake has to be. The "
			+ "shape stays on this phone. Use my shakes, on that screen, greys "
			+ "left, right, up and down in this world and they stay quiet. Shake "
			+ "the phone keeps its own button.";

	private final IConnectionBinder service;
	private final boolean showRegexWarning;
	private LinearLayout rows;
	/** Whether the readings this phone cannot provide are folded open. */
	private boolean showUnavailable;
	/** Shapes recorded on this phone. Empty when the service cannot be asked. */
	private CustomShakeLibrary shapes = CustomShakeLibrary.empty();
	/** This world's switch. Directions stay listed, grey, and quiet. */
	private boolean myShakesOn;

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
		shapes = loadShapes();
		myShakesOn = readFlag(SensorWorldFlags.MY_SHAKES, false);
		HashMap<String, List<TriggerData>> byGesture = readTriggers();
		addMasterRow();
		addMyShakes(byGesture);
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

	private void addMasterRow() {
		View row = LayoutInflater.from(getContext())
				.inflate(R.layout.sensor_list_row, rows, false);
		TextView label = (TextView) row.findViewById(R.id.label);
		label.setText("Sensors in this world");
		final TextView status = (TextView) row.findViewById(R.id.status);
		final boolean on = readFlag(SensorWorldFlags.ENABLED, true);
		status.setText(on
				? "On. Every reading below can fire here. Other worlds have their own."
				: "Off. Nothing on this list fires here. The power buttons stay as they were.");
		status.setTextColor(on ? TEXT_CONFIGURED : TEXT_SECONDARY);
		row.findViewById(R.id.test).setVisibility(View.GONE);
		row.findViewById(R.id.accent).setBackgroundColor(on ? ACCENT_LIVE : ACCENT_NONE);
		final ImageButton power = (ImageButton) row.findViewById(R.id.enabled);
		final boolean[] armed = new boolean[] { on };
		showPower(power, armed[0]);
		power.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				armed[0] = !armed[0];
				showPower(power, armed[0]);
				writeFlag(SensorWorldFlags.ENABLED, armed[0]);
				status.setText(armed[0]
						? "On. Every reading below can fire here. Other worlds have their own."
						: "Off. Nothing on this list fires here. The power buttons stay as they were.");
				status.setTextColor(armed[0] ? TEXT_CONFIGURED : TEXT_SECONDARY);
				row.findViewById(R.id.accent).setBackgroundColor(
						armed[0] ? ACCENT_LIVE : ACCENT_NONE);
			}
		});
		rows.addView(row);
	}

	private void addMyShakes(final HashMap<String, List<TriggerData>> byGesture) {
		addSection("My shakes", null);
		Button open = new Button(getContext());
		open.setText("Record a shake");
		open.setAllCaps(false);
		open.setTextColor(0xFF66CCFF);
		open.setTextSize(15);
		open.setTypeface(Typeface.DEFAULT_BOLD);
		open.setBackgroundResource(R.drawable.editor_more_button_bg);
		open.setMinHeight((int) (44 * getContext().getResources().getDisplayMetrics().density));
		open.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				new MyShakesDialog(getContext(), service, showRegexWarning,
						new MyShakesDialog.Closed() {
							@Override
							public void onClosed() {
								build();
							}
						}).show();
			}
		});
		int inset = (int) (10 * getContext().getResources().getDisplayMetrics().density);
		LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		openParams.setMargins(inset, inset / 2, inset, inset / 2);
		rows.addView(open, openParams);

		java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<String>();
		for (int i = 0; i < shapes.entries().size(); i++) {
			names.add(shapes.entries().get(i).getName());
		}
		for (String id : byGesture.keySet()) {
			if (id != null && id.startsWith(CustomShakeNames.PREFIX)) {
				names.add(id.substring(CustomShakeNames.PREFIX.length()));
			}
		}
		for (String name : names) {
			Gesture g = CustomShakeNames.gesture(name);
			if (g == null) {
				continue;
			}
			addRow(g, GestureAvailability.resolve(getContext(), g), byGesture.get(g.getId()));
		}
	}

	private CustomShakeLibrary loadShapes() {
		try {
			return CustomShakeLibrary.decode(service.getCustomShakeLibrary());
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"GestureListDialog.shapes", e);
			return CustomShakeLibrary.empty();
		}
	}

	private static void showPower(final ImageButton power, final boolean on) {
		power.setVisibility(View.VISIBLE);
		BaseSelectionDialog.applyToggleTint(power, on);
		power.setContentDescription(on ? "On" : "Off");
	}

	private boolean quieted(final Gesture g) {
		return myShakesOn && (GestureGate.isBuiltinDirection(g.getId())
				|| GestureGate.isShakePattern(g.getId()));
	}

	private void paintRow(final TextView label, final TextView status, final View accent,
			final Gesture g, final GestureAvailability.Resolution r, final boolean configured) {
		if (quieted(g)) {
			label.setTextColor(BaseSelectionDialog.ROW_TITLE_COLOR_OFF);
			status.setTextColor(BaseSelectionDialog.ROW_EXTRA_COLOR_OFF);
			accent.setBackgroundColor(configured ? ACCENT_LIVE_DEAD : ACCENT_NONE);
			return;
		}
		label.setTextColor(r.isAvailable() ? TEXT_PRIMARY : TEXT_PRIMARY_DEAD);
		status.setTextColor(configured && r.isAvailable() ? TEXT_CONFIGURED : TEXT_SECONDARY);
		accent.setBackgroundColor(!configured ? ACCENT_NONE
				: (r.isAvailable() ? ACCENT_LIVE : ACCENT_LIVE_DEAD));
	}

	private void setTriggersEnabled(final List<TriggerData> bound, final boolean on) {
		try {
			for (int i = 0; i < bound.size(); i++) {
				TriggerData trigger = bound.get(i);
				trigger.setEnabled(on);
				service.setTriggerEnabled(on, trigger.getName());
			}
			service.saveSettings();
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"GestureListDialog.switch", e);
		}
	}

	private boolean readFlag(final String key, final boolean fallback) {
		try {
			SettingsGroup root = service.getSettings();
			if (root == null) {
				return fallback;
			}
			Option opt = root.findOptionByKey(key);
			if (opt instanceof BooleanOption) {
				Object value = ((BooleanOption) opt).getValue();
				if (value instanceof Boolean) {
					return ((Boolean) value).booleanValue();
				}
			}
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"GestureListDialog.read", e);
		}
		return fallback;
	}

	private void writeFlag(final String key, final boolean on) {
		try {
			service.updateBooleanSetting(key, on);
			service.saveSettings();
		} catch (RemoteException e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"GestureListDialog.write", e);
		}
	}

	private void addRow(final Gesture g, final GestureAvailability.Resolution r,
			final List<TriggerData> bound) {
		final boolean configured = bound != null && !bound.isEmpty();
		View row = LayoutInflater.from(getContext())
				.inflate(R.layout.sensor_list_row, rows, false);

		TextView label = (TextView) row.findViewById(R.id.label);
		label.setText(g.getLabel());

		final TextView status = (TextView) row.findViewById(R.id.status);
		status.setText(describe(g, r, bound));

		final View accent = row.findViewById(R.id.accent);
		paintRow(label, status, accent, g, r, configured);

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
				probe(g);
			}
		});

		final ImageButton power = (ImageButton) row.findViewById(R.id.enabled);
		if (configured) {
			boolean anyOn = false;
			for (int i = 0; i < bound.size(); i++) {
				anyOn = anyOn || bound.get(i).isEnabled();
			}
			final boolean[] armed = new boolean[] { anyOn };
			showPower(power, armed[0]);
			power.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(final View v) {
					armed[0] = !armed[0];
					setTriggersEnabled(bound, armed[0]);
					showPower(power, armed[0]);
					status.setText(describe(g, r, bound));
					paintRow(label, status, accent, g, r, true);
				}
			});
		}

		rows.addView(row);
	}

	/**
	 * Watch the sensor live, so the player can see whether the phone sees them.
	 *
	 * <p>Test used to mean "run the actions", which is a different question and
	 * not the one being asked here: on a reading with nothing set up it did
	 * nothing at all, and even when it worked, the reply went to the game window
	 * under this dialog and under Options, so it looked like a dead button.
	 *
	 * <p>Running the actions is still worth doing and lives inside the probe,
	 * where the reply lands over the screen being looked at. The button is only
	 * offered where something is enabled to run, which is asked here rather than
	 * trusted from when the list was built — a world switch or a dropped
	 * connection between the two would otherwise offer a button that quietly
	 * does nothing.
	 */
	private void probe(final Gesture g) {
		List<TriggerData> live = readTriggers().get(g.getId());
		boolean canRun = false;
		if (live != null) {
			for (TriggerData t : live) {
				canRun = canRun || t.isEnabled();
			}
		}
		new SensorProbeDialog(getContext(), g, service, canRun).show();
	}

	/** The row's one line of state: why it cannot fire, what it does, or what it is. */
	private String describe(final Gesture g, final GestureAvailability.Resolution r,
			final List<TriggerData> bound) {
		StringBuilder out = new StringBuilder();
		if (!r.isAvailable()) {
			out.append(r.missingReason()).append(' ');
		}
		if (myShakesOn && (GestureGate.isBuiltinDirection(g.getId())
				|| GestureGate.isShakePattern(g.getId()))) {
			out.append("Quiet while Use my shakes is on. ");
		}
		String shape = shapeNote(g);
		if (bound == null || bound.isEmpty()) {
			// With nothing set up, what the reading means is the useful thing to
			// say. Once something answers it, what that is matters more.
			if (shape != null && shape.startsWith("Not recorded")) {
				out.append(shape);
				return out.toString();
			}
			if (shape != null) {
				out.append(shape).append(' ');
			}
			out.append(g.getHelp());
			return out.toString();
		}
		boolean anyOn = false;
		for (int i = 0; i < bound.size(); i++) {
			anyOn = anyOn || bound.get(i).isEnabled();
		}
		if (!anyOn) {
			out.append("Turned off \u2014 ");
		}
		out.append(describeActions(bound.get(0)));
		if (bound.size() > 1) {
			// Not an error, and worth saying plainly: all of them run.
			out.append(" \u00b7 ").append(bound.size())
				.append(" triggers answer this, and all of them run");
		}
		if (shape != null) {
			out.append(" \u00b7 ").append(shape);
		}
		return capitalised(out.toString());
	}

	/** Stroke count for a recorded shake, or null for a built-in reading. */
	private String shapeNote(final Gesture g) {
		if (g == null || !g.getId().startsWith(CustomShakeNames.PREFIX)) {
			return null;
		}
		CustomShakeLibrary.Entry entry = shapes.byName(
				g.getId().substring(CustomShakeNames.PREFIX.length()));
		if (entry == null || !entry.canMatch()) {
			return "Not recorded on this phone yet.";
		}
		return entry.getTraces().size() + " lines, match " + entry.getTolerance();
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

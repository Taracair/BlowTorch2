package com.resurrection.blowtorch2.lib.trigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.ack.AckResponder;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.window.PluginFilterSelectionDialog;
import com.resurrection.blowtorch2.lib.service.sensor.GestureAvailability;
import com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog;
import com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
 */
public class GestureListDialog extends Dialog {

	private final IConnectionBinder service;
	private final boolean showRegexWarning;
	private LinearLayout rows;

	public GestureListDialog(final Context context, final IConnectionBinder service,
			final boolean showRegexWarning) {
		super(context);
		this.service = service;
		this.showRegexWarning = showRegexWarning;
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle("Sensors");
		ScrollView scroll = new ScrollView(getContext());
		rows = new LinearLayout(getContext());
		rows.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (8 * getContext().getResources().getDisplayMetrics().density);
		rows.setPadding(pad, pad, pad, pad);
		scroll.addView(rows, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		setContentView(scroll);
		build();
	}

	/** Rebuilt rather than patched, so it cannot drift from the settings. */
	private void build() {
		rows.removeAllViews();
		TextView intro = new TextView(getContext());
		intro.setText("What this phone can measure, and which triggers answer each reading. "
				+ "Tap one to set it up — it opens the ordinary trigger editor, so a sensor "
				+ "can send a command, run a script, speak, gate on a condition, or anything "
				+ "else a trigger does.\n\n"
				+ "A sensor trigger fires in every world you have open, not only this one. "
				+ "Movement readings are held back while the screen is off or the app "
				+ "is in the background unless you allow them in Options → Device.");
		intro.setTextColor(Color.LTGRAY);
		intro.setTextSize(13f);
		rows.addView(intro);

		HashMap<String, List<TriggerData>> byGesture = readTriggers();
		for (Gesture g : GestureCatalog.all()) {
			addRow(g, byGesture.get(g.getId()));
		}

		Button close = new Button(getContext());
		close.setText("Close");
		close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				dismiss();
			}
		});
		rows.addView(close);
	}

	private void addRow(final Gesture g, final List<TriggerData> bound) {
		GestureAvailability.Resolution r =
				GestureAvailability.resolve(getContext(), g);
		LinearLayout row = new LinearLayout(getContext());
		row.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (6 * getContext().getResources().getDisplayMetrics().density);
		row.setPadding(0, pad, 0, pad);

		TextView label = new TextView(getContext());
		label.setText(g.getLabel());
		label.setTextColor(r.isAvailable() ? Color.WHITE : Color.GRAY);
		label.setTextSize(15f);
		row.addView(label);

		TextView detail = new TextView(getContext());
		detail.setTextSize(12f);
		detail.setTextColor(Color.LTGRAY);
		detail.setText(describe(g, r, bound));
		row.addView(detail);

		LinearLayout buttons = new LinearLayout(getContext());
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		Button edit = new Button(getContext());
		edit.setText(bound == null || bound.isEmpty() ? "Set up" : "Edit");
		// Deliberately not disabled when the sensor is missing: profiles are
		// shared, and one built here should be buildable for a phone that does
		// have the sensor. The row above already says it will not fire on this one.
		edit.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				openEditor(g, bound);
			}
		});
		buttons.addView(edit);

		Button test = new Button(getContext());
		test.setText("Test");
		test.setEnabled(bound != null && !bound.isEmpty());
		test.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				try {
					// The same path .sensor fire uses: it proves the actions, not
					// the sensor, and the reply in the game window says which of
					// the two it proved.
					service.sendData((".sensor fire " + g.getId() + "\r\n").getBytes("UTF-8"));
				} catch (Exception e) {
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
							"GestureListDialog.test", e);
				}
				// Left open on purpose. Closing this would leave the Options
				// dialog on top of the reply, and a test whose answer you cannot
				// see reads as a test that did nothing.
			}
		});
		buttons.addView(test);
		row.addView(buttons);
		rows.addView(row);
	}

	private String describe(final Gesture g, final GestureAvailability.Resolution r,
			final List<TriggerData> bound) {
		StringBuilder out = new StringBuilder();
		out.append(g.getHelp()).append('\n');
		out.append("Sensor: ").append(r.describe()).append('\n');
		if (bound == null || bound.isEmpty()) {
			out.append("Nothing set up yet.");
			return out.toString();
		}
		if (bound.size() > 1) {
			// Not an error, and worth saying plainly: all of them run.
			out.append(bound.size()).append(" triggers answer this reading, and all")
				.append(" of them run. Edit opens the first.\n");
		}
		TriggerData first = bound.get(0);
		out.append(first.isEnabled() ? "On" : "Off").append(": ")
			.append(describeActions(first));
		return out.toString();
	}

	private String describeActions(final TriggerData t) {
		List<TriggerResponder> responders = t.getResponders();
		if (responders == null || responders.isEmpty()) {
			return "no actions yet";
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

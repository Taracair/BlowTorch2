package com.resurrection.blowtorch2.lib.responder.sound;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.responder.TriggerResponderEditorDoneListener;
import com.resurrection.blowtorch2.lib.util.NotificationSounds;
import com.resurrection.blowtorch2.lib.util.TriggerSounds;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Choosing the sound a trigger makes.
 *
 * <p>Three sources, in the order a player is likely to want them: the sounds
 * that ship with the app, the ones in their own folder, and anything else on
 * the phone through the system picker.
 *
 * <p>Their own folder is {@link #userSoundsDir} — {@code /BlowTorch/sounds} on
 * shared storage — and it is named in the dialog on purpose. A sound chosen
 * from there is remembered by its path, so a file that later moves or is
 * deleted leaves a trigger that has gone quiet with nothing on screen to say
 * why. The dialog says so next to the name, and this is the screen where it is
 * fixed.
 */
public class SoundResponderEditor extends Dialog {

	/** Same shape as the notification picker's, and a different number. */
	public static final int REQUEST_PICK_SOUND = 0x5453; // 'TS'

	private static SoundResponderEditor sPendingPicker;

	private SoundResponder the_responder;
	private SoundResponder original;

	boolean isEditor = false;

	TriggerResponderEditorDoneListener finish_with;

	public SoundResponderEditor(Context context, SoundResponder input,
			TriggerResponderEditorDoneListener doneListener) {
		super(context);
		finish_with = doneListener;
		if (input != null) {
			original = input.copy();
			the_responder = input.copy();
			isEditor = true;
		} else {
			the_responder = new SoundResponder();
		}
	}

	/**
	 * Where a player's own sounds are meant to live.
	 *
	 * <p>One named folder rather than "wherever you picked it from", because a
	 * path is all that is stored and a path that moves is a trigger that stops
	 * making a noise. Named in the dialog, in the help and in the manual.
	 *
	 * @return the folder, whether or not it exists yet.
	 */
	public static File userSoundsDir() {
		return new File(new File(Environment.getExternalStorageDirectory(), "BlowTorch"),
				"sounds");
	}

	/** Take the result of the system file picker. */
	public static void onSoundPicked(Uri uri) {
		SoundResponderEditor editor = sPendingPicker;
		sPendingPicker = null;
		if (editor == null || uri == null) {
			return;
		}
		editor.the_responder.setSoundPath(uri.toString());
		// The path changed, so a previous failure to load it is stale.
		TriggerSounds.forget(uri.toString());
		editor.refreshSoundButton();
		editor.preview();
	}

	public void onCreate(Bundle b) {
		this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		setContentView(R.layout.responder_sound_dialog);

		EditText volume = (EditText) findViewById(R.id.responder_sound_volume);
		EditText gap = (EditText) findViewById(R.id.responder_sound_gap);
		volume.setText(Integer.toString(the_responder.getVolumePercent()));
		gap.setText(Integer.toString(the_responder.getMinGapMs()));
		android.widget.CheckBox warn =
				(android.widget.CheckBox) findViewById(R.id.responder_sound_warn);
		warn.setChecked(the_responder.getWarnWhenSilent());

		refreshSoundButton();

		Button pick = (Button) findViewById(R.id.responder_sound_pick);
		pick.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				showSoundPicker();
			}
		});

		Button test = (Button) findViewById(R.id.responder_sound_test);
		test.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				preview();
			}
		});

		Button help = (Button) findViewById(R.id.responder_sound_help);
		help.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				showHelp();
			}
		});

		Button done = (Button) findViewById(R.id.responder_sound_done_button);
		done.setOnClickListener(new DoneListener());

		Button cancel = (Button) findViewById(R.id.responder_sound_cancel);
		cancel.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				SoundResponderEditor.this.dismiss();
			}
		});
	}

	/** Play it now, at the volume currently typed in. */
	private void preview() {
		String path = the_responder.getSoundPath();
		if (path == null || path.length() == 0) {
			Toast.makeText(getContext(), "No sound chosen yet.", Toast.LENGTH_SHORT).show();
			return;
		}
		if (!TriggerSounds.isAvailable(path)) {
			Toast.makeText(getContext(),
					"That file is not there any more. Pick the sound again.",
					Toast.LENGTH_LONG).show();
			refreshSoundButton();
			return;
		}
		// No rate key: the test button is pressed on purpose and must answer
		// every time, even twice in a row.
		TriggerSounds.play(getContext(), path, readVolume() / 100f, null, 0);
	}

	private int readVolume() {
		EditText volume = (EditText) findViewById(R.id.responder_sound_volume);
		int v = intOr(volume == null || volume.getText() == null
				? null : volume.getText().toString(),
				SoundResponder.DEFAULT_VOLUME_PERCENT);
		if (v < 0) {
			v = 0;
		}
		if (v > 100) {
			v = 100;
		}
		return v;
	}

	/** Say which sound is chosen, and whether it is still there. */
	private void refreshSoundButton() {
		Button pick = (Button) findViewById(R.id.responder_sound_pick);
		TextView status = (TextView) findViewById(R.id.responder_sound_status);
		if (pick == null) {
			return;
		}
		String path = the_responder.getSoundPath();
		if (path == null || path.length() == 0) {
			pick.setText("Sound: (none chosen)");
			if (status != null) {
				status.setText("Pick a sound. Your own go in "
						+ userSoundsDir().getAbsolutePath() + ".");
			}
			return;
		}
		String label = NotificationSounds.displayLabel(path);
		if (!TriggerSounds.isAvailable(path)) {
			pick.setText("Sound: " + label + " — MISSING");
			if (status != null) {
				status.setText("That file has been moved or deleted. Put it back in "
						+ userSoundsDir().getAbsolutePath() + ", or pick another.");
			}
			return;
		}
		pick.setText("Sound: " + label);
		if (status != null) {
			status.setText("Gap is the shortest time between two of this trigger's"
					+ " sounds. 0 turns it off.");
		}
	}

	/**
	 * The list of sounds to choose from.
	 *
	 * <p>Bundled first — they are the ones guaranteed to be there — then the
	 * player's own folder, then the phone's notification sounds, then the system
	 * picker for anything else.
	 */
	private void showSoundPicker() {
		final LinkedHashMap<String, String> choices = new LinkedHashMap<String, String>();
		for (NotificationSounds.SoundPreset preset : NotificationSounds.BUNDLED) {
			choices.put(preset.label, NotificationSounds.bundledPath(preset.key));
		}
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)
				|| Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			// Made here rather than at install: a folder the player can see is
			// half of "put your sounds in one place and leave them there". If
			// storage access has not been granted this quietly does nothing, and
			// the list is still useful.
			File own = userSoundsDir();
			if (!own.isDirectory()) {
				own.mkdirs();
			}
			addAudioFiles(choices, own);
			// The folder the notification action has always read, so a sound
			// already dropped there is not suddenly invisible here.
			addAudioFiles(choices,
					new File(Environment.getExternalStorageDirectory(), "BlowTorch"));
			addAudioFiles(choices, new File("/system/media/audio/notifications/"));
		}
		choices.put("Pick from storage…", "__PICK__");

		final ArrayList<String> labels = new ArrayList<String>(choices.keySet());
		int selected = -1;
		String current = the_responder.getSoundPath();
		if (current != null && current.length() > 0) {
			for (int i = 0; i < labels.size(); i++) {
				if (current.equals(choices.get(labels.get(i)))) {
					selected = i;
					break;
				}
			}
		}

		AlertDialog.Builder b = new AlertDialog.Builder(getContext());
		b.setTitle("Sound");
		b.setSingleChoiceItems(labels.toArray(new String[labels.size()]), selected,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						String label = labels.get(which);
						String path = choices.get(label);
						dialog.dismiss();
						if ("__PICK__".equals(path)) {
							launchStoragePicker();
							return;
						}
						the_responder.setSoundPath(path);
						TriggerSounds.forget(path);
						refreshSoundButton();
						preview();
					}
				});
		b.setNegativeButton("Cancel", null);
		b.create().show();
	}

	/** Add every playable file in a folder, newest name wins on a clash. */
	private static void addAudioFiles(final LinkedHashMap<String, String> into,
			final File dir) {
		if (dir == null || !dir.isDirectory()) {
			return;
		}
		File[] files = dir.listFiles(new FilenameFilter() {
			public boolean accept(File d, String name) {
				String lower = name.toLowerCase(java.util.Locale.US);
				return lower.endsWith(".mp3") || lower.endsWith(".ogg")
						|| lower.endsWith(".wav") || lower.endsWith(".m4a");
			}
		});
		if (files == null) {
			return;
		}
		for (File f : files) {
			if (f.isFile()) {
				into.put(f.getName(), f.getAbsolutePath());
			}
		}
	}

	private void launchStoragePicker() {
		Activity activity = findActivity(getContext());
		if (activity == null) {
			Toast.makeText(getContext(), "Cannot open the file picker from here.",
					Toast.LENGTH_SHORT).show();
			return;
		}
		sPendingPicker = this;
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("audio/*");
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
				| Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
		try {
			activity.startActivityForResult(intent, REQUEST_PICK_SOUND);
		} catch (Exception e) {
			sPendingPicker = null;
			Toast.makeText(getContext(), "No file picker available.",
					Toast.LENGTH_SHORT).show();
		}
	}

	private static Activity findActivity(Context context) {
		Context c = context;
		while (c instanceof android.content.ContextWrapper) {
			if (c instanceof Activity) {
				return (Activity) c;
			}
			c = ((android.content.ContextWrapper) c).getBaseContext();
		}
		return null;
	}

	private void showHelp() {
		StringBuilder text = new StringBuilder();
		text.append("This action plays a short sound when the trigger fires. In a"
				+ " fight that beats speaking: a ping is over in a fifth of a second"
				+ " where a sentence takes two.\n\n");
		text.append("Where the sound comes from:\n\n"
				+ "1. The five sounds that ship with BlowTorch. These can never go"
				+ " missing.\n"
				+ "2. Your own files, from ")
			.append(userSoundsDir().getAbsolutePath())
			.append(". Put .wav, .ogg, .mp3 or .m4a files there and they appear in"
				+ " the list.\n"
				+ "3. Anything else on the phone, through Pick from storage.\n\n");
		text.append("Your own sound is remembered by where it is, not by copying it"
				+ " into the app. If you move or delete the file, this trigger goes"
				+ " quiet — the dialog will say MISSING next to the name, and the"
				+ " error log will say so too. Put the file back, or open this"
				+ " screen and pick another.\n\n");
		text.append("Gap is the shortest time between two of this trigger's sounds,"
				+ " in milliseconds. It stops a trigger that matches every line from"
				+ " turning into a buzz. 0 turns it off. Each trigger counts its own"
				+ " gap, so one loud trigger does not silence another.\n\n");
		text.append("Sound plays on the notification stream, so a phone on silent"
				+ " stays silent. Fire-when still applies: an action set to fire only"
				+ " while the game window is closed makes no noise while you are"
				+ " looking at it.");

		AlertDialog.Builder b = new AlertDialog.Builder(getContext());
		b.setTitle("Playing a sound");
		b.setMessage(text.toString());
		b.setPositiveButton("Close", null);
		b.create().show();
	}

	private static int intOr(final String raw, final int fallback) {
		if (raw == null || raw.trim().length() == 0) {
			return fallback;
		}
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private class DoneListener implements View.OnClickListener {

		public void onClick(View arg0) {
			if (the_responder.getSoundPath() == null
					|| the_responder.getSoundPath().length() == 0) {
				Toast.makeText(getContext(), "Pick a sound first.",
						Toast.LENGTH_SHORT).show();
				return;
			}
			EditText gap = (EditText) findViewById(R.id.responder_sound_gap);
			android.widget.CheckBox warn =
					(android.widget.CheckBox) findViewById(R.id.responder_sound_warn);
			the_responder.setWarnWhenSilent(warn == null || warn.isChecked());
			the_responder.setVolumePercent(readVolume());
			the_responder.setMinGapMs(intOr(gap == null || gap.getText() == null
					? null : gap.getText().toString(),
					SoundResponder.DEFAULT_MIN_GAP_MS));

			// Nothing is played here. Closing an editor is not a reason to make a
			// noise in whatever room you are standing in — same rule as Speak.
			if (isEditor) {
				finish_with.editTriggerResponder(the_responder, original);
			} else {
				finish_with.newTriggerResponder(the_responder);
			}

			SoundResponderEditor.this.dismiss();
		}
	};
}

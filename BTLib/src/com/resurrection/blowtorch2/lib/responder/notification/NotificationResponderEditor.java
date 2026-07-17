package com.resurrection.blowtorch2.lib.responder.notification;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.responder.TriggerResponderEditorDoneListener;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder.FIRE_WHEN;
import com.resurrection.blowtorch2.lib.util.NotificationSounds;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class NotificationResponderEditor extends Dialog {

	public static final int REQUEST_PICK_SOUND = 0x4E53; // 'NS'

	private static NotificationResponderEditor sPendingPicker;

	CheckBox lights;
	CheckBox vibrate;
	CheckBox sound;
	CheckBox spawnnew;
	CheckBox useongoing;

	TextView lights_extra;
	TextView vibrate_extra;
	TextView sound_extra;

	EditText title;
	EditText message;

	NotificationResponder the_responder;
	NotificationResponder original;
	TriggerResponderEditorDoneListener finish_with;
	boolean isEditor = false;

	MediaPlayer mp = new MediaPlayer();

	public NotificationResponderEditor(Context context, NotificationResponder input, TriggerResponderEditorDoneListener listener) {
		super(context);
		finish_with = listener;
		if (input == null) {
			the_responder = new NotificationResponder();
			the_responder.setFireType(FIRE_WHEN.WINDOW_BOTH);
		} else {
			the_responder = input;
			original = input.copy();
			isEditor = true;
		}
	}

	/** Called from MainWindow.onActivityResult when a custom sound was picked. */
	public static void onSoundPicked(Uri uri) {
		NotificationResponderEditor editor = sPendingPicker;
		sPendingPicker = null;
		if (editor == null || uri == null) {
			return;
		}
		String path = uri.toString();
		try {
			Context ctx = editor.getContext();
			if (ctx instanceof Activity) {
				ctx.getContentResolver().takePersistableUriPermission(uri,
						Intent.FLAG_GRANT_READ_URI_PERMISSION);
			}
		} catch (Exception ignored) {
		}
		editor.the_responder.setUseDefaultSound(true);
		editor.the_responder.setSoundPath(path);
		editor.sound.setChecked(true);
		editor.sound_extra.setText("Currently using: " + NotificationSounds.displayLabel(path));
		editor.previewSound(path);
	}

	public void onCreate(Bundle b) {
		this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		setContentView(R.layout.responder_notification_dialog);

		ScrollView sv = (ScrollView) findViewById(R.id.trigger_notification_responder_scroll_container);
		sv.setScrollbarFadingEnabled(false);

		title = (EditText) findViewById(R.id.responder_notification_title);
		message = (EditText) findViewById(R.id.responder_notification_extra);

		lights = (CheckBox) findViewById(R.id.responder_notification_lights_check);
		vibrate = (CheckBox) findViewById(R.id.responder_notification_vibrate_check);
		sound = (CheckBox) findViewById(R.id.responder_notification_sound_check);
		spawnnew = (CheckBox) findViewById(R.id.responder_notification_spawnnew_check);

		lights_extra = (TextView) findViewById(R.id.responder_notification_lights_extra);
		sound_extra = (TextView) findViewById(R.id.responder_notification_sound_extra);
		vibrate_extra = (TextView) findViewById(R.id.responder_notification_vibrate_extra);

		sound_extra.setMaxWidth((int) (50 * this.getContext().getResources().getDisplayMetrics().density));
		sound_extra.setSingleLine(true);

		title.setText(the_responder.getTitle());
		message.setText(the_responder.getMessage());

		if (the_responder.isUseDefaultSound()) {
			sound.setChecked(true);
			if (the_responder.getSoundPath().equals("")) {
				sound_extra.setText("Currently using default sound.");
			} else {
				sound_extra.setText("Currently using: " + NotificationSounds.displayLabel(the_responder.getSoundPath()));
			}
		} else {
			sound.setChecked(false);
			sound_extra.setText("Currently disabled.");
		}

		if (the_responder.isUseDefaultLight()) {
			lights.setChecked(true);
			if (the_responder.getColorToUse() != 0) {
				lights_extra.setText("Currently Using: " + lookupRawColor(the_responder.getColorToUse()));
			} else {
				lights_extra.setText("Currently Using: default");
			}
		} else {
			lights.setChecked(false);
			lights_extra.setText("Currently disabled.");
		}

		if (the_responder.isUseDefaultVibrate()) {
			vibrate.setChecked(true);
			if (the_responder.getVibrateLength() != 0) {
				vibrate_extra.setText("Currently using: " + lookupVibrateLength(the_responder.getVibrateLength()));
			} else {
				vibrate_extra.setText("Currently using: default");
			}
		} else {
			vibrate.setChecked(false);
			vibrate_extra.setText("Currently disabled.");
		}

		if (the_responder.isSpawnNewNotification()) {
			spawnnew.setChecked(true);
		} else {
			spawnnew.setChecked(false);
		}

		lights.setOnCheckedChangeListener(new CheckChangedListener(CHECK_TYPE.LIGHTS));
		vibrate.setOnCheckedChangeListener(new CheckChangedListener(CHECK_TYPE.VIBRATE));
		sound.setOnCheckedChangeListener(new CheckChangedListener(CHECK_TYPE.SOUND));
		spawnnew.setOnCheckedChangeListener(new CheckChangedListener(CHECK_TYPE.SPAWNNEW));

		Button done = (Button) findViewById(R.id.responder_notification_done_button);
		done.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				doFinish();
			}
		});

		Button cancel = (Button) findViewById(R.id.responder_notification_cancel);
		cancel.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				NotificationResponderEditor.this.dismiss();
			}
		});
	}

	private void doFinish() {
		EditText titleField = (EditText) findViewById(R.id.responder_notification_title);
		EditText extra = (EditText) findViewById(R.id.responder_notification_extra);

		the_responder.setTitle(titleField.getText().toString());
		the_responder.setMessage(extra.getText().toString());

		if (isEditor) {
			finish_with.editTriggerResponder(the_responder, original);
		} else {
			finish_with.newTriggerResponder(the_responder);
		}
		this.dismiss();
	}

	public static enum CHECK_TYPE {
		LIGHTS,
		VIBRATE,
		SOUND,
		SPAWNNEW,
		USEONGOING;
	}

	private String lookupColor(int input) {
		switch (input) {
		case 0: return "default";
		case 1: return "Blue";
		case 2: return "Green";
		case 3: return "Red";
		case 4: return "Magenta";
		case 5: return "Cyan";
		case 6: return "White";
		default: return "default";
		}
	}

	private String lookupRawColor(int rawColor) {
		switch (rawColor) {
		case 0x00000000: return "default";
		case 0xFF0000FF: return "Blue";
		case 0xFF00FF00: return "Green";
		case 0xFFFF0000: return "Red";
		case 0xFFFF00FF: return "Magenta";
		case 0xFF00FFFF: return "Cyan";
		case 0xFFFFFFFF: return "White";
		default: return "default";
		}
	}

	private String lookupVibrateLength(int i) {
		switch (i) {
		case 0: return "default";
		case 1: return "Very Short";
		case 2: return "Short";
		case 3: return "Long";
		case 4: return "Suuper Long";
		default: return "default";
		}
	}

	private void previewSound(String path) {
		try {
			mp.stop();
		} catch (Exception ignored) {
		}
		try {
			mp.reset();
			Uri uri = NotificationSounds.resolveUri(getContext(), path);
			if (uri == null) {
				return;
			}
			mp.setDataSource(getContext(), uri);
			mp.prepare();
			mp.start();
		} catch (Exception e) {
			Toast.makeText(getContext(), "Could not preview sound", Toast.LENGTH_SHORT).show();
		}
	}

	private void launchStoragePicker() {
		Activity activity = findActivity(getContext());
		if (activity == null) {
			Toast.makeText(getContext(), "Cannot open file picker here.", Toast.LENGTH_SHORT).show();
			return;
		}
		sPendingPicker = this;
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("audio/*");
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
		try {
			activity.startActivityForResult(intent, REQUEST_PICK_SOUND);
		} catch (Exception e) {
			sPendingPicker = null;
			Toast.makeText(getContext(), "No file picker available.", Toast.LENGTH_SHORT).show();
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

	private static final String DISABLED_MSG = "Currently disabled.";
	private static final String DEFAULT_MSG = "Currently using: default";

	private class CheckChangedListener implements CompoundButton.OnCheckedChangeListener {

		private CHECK_TYPE type;

		public CheckChangedListener(CHECK_TYPE iType) {
			type = iType;
		}

		public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
			switch (type) {
			case LIGHTS:
				if (arg1) {
					AlertDialog.Builder light_builder = new AlertDialog.Builder(NotificationResponderEditor.this.getContext());
					CharSequence[] light_types = {"Default", "Blue", "Green", "Red", "Magenta", "Cyan", "White"};
					light_builder.setTitle("Select Color");
					light_builder.setItems(light_types, new LightListReturnListener());
					AlertDialog light_picker = light_builder.create();
					light_picker.setOnCancelListener(new DialogInterface.OnCancelListener() {
						public void onCancel(DialogInterface dialog) {
							lights.setChecked(false);
							the_responder.setUseDefaultLight(false);
							the_responder.setColorToUse(0);
							lights_extra.setText(DISABLED_MSG);
						}
					});
					light_picker.show();
				} else {
					the_responder.setUseDefaultLight(false);
					the_responder.setColorToUse(0);
					lights_extra.setText(DISABLED_MSG);
				}
				break;
			case VIBRATE:
				if (arg1) {
					AlertDialog.Builder vibrate_builder = new AlertDialog.Builder(NotificationResponderEditor.this.getContext());
					CharSequence[] vibrate_types = {"Default", "Very Short", "Short", "Long", "Suuuper Long"};
					vibrate_builder.setTitle("Select Sequence:");
					vibrate_builder.setItems(vibrate_types, new VibrateListReturnListener());
					AlertDialog vibrate_dialog = vibrate_builder.create();
					vibrate_dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
						public void onCancel(DialogInterface dialog) {
							vibrate.setChecked(false);
							the_responder.setUseDefaultVibrate(false);
							the_responder.setVibrateLength(0);
							vibrate_extra.setText(DISABLED_MSG);
						}
					});
					vibrate_dialog.show();
				} else {
					the_responder.setUseDefaultVibrate(false);
					the_responder.setVibrateLength(0);
					vibrate_extra.setText(DISABLED_MSG);
				}
				break;
			case SOUND:
				if (arg1) {
					showSoundPicker();
				} else {
					the_responder.setUseDefaultSound(false);
					the_responder.setSoundPath("");
					sound_extra.setText(DISABLED_MSG);
				}
				break;
			case SPAWNNEW:
				the_responder.setSpawnNewNotification(arg1);
				break;
			case USEONGOING:
				the_responder.setUseOnGoingNotification(arg1);
				break;
			default:
				break;
			}
		}

		private void showSoundPicker() {
			final LinkedHashMap<String, String> choices = new LinkedHashMap<String, String>();
			choices.put("Disabled", "");
			choices.put("Default", "");
			for (NotificationSounds.SoundPreset preset : NotificationSounds.BUNDLED) {
				choices.put(preset.label, NotificationSounds.bundledPath(preset.key));
			}
			String state = Environment.getExternalStorageState();
			if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
				String[] system_paths = {
						"/system/media/audio/notifications/",
						"/system/media/audio/ringtones/",
						"/system/media/audio/alarms/"
				};
				for (String path : system_paths) {
					File tmp = new File(path);
					if (tmp.isDirectory()) {
						File[] files = tmp.listFiles();
						if (files == null) {
							continue;
						}
						for (File file : files) {
							if (file.isFile()) {
								choices.put(file.getName(), file.getAbsolutePath());
							}
						}
					}
				}
				File btermdir = new File(Environment.getExternalStorageDirectory(), "BlowTorch");
				if (btermdir.isDirectory()) {
					File[] mp3s = btermdir.listFiles(new FilenameFilter() {
						public boolean accept(File dir, String name) {
							String lower = name.toLowerCase();
							return lower.endsWith(".mp3") || lower.endsWith(".ogg")
									|| lower.endsWith(".wav") || lower.endsWith(".m4a");
						}
					});
					if (mp3s != null) {
						for (File path : mp3s) {
							choices.put(path.getName(), path.getAbsolutePath());
						}
					}
				}
			}
			choices.put("Pick from storage…", "__PICK__");

			final ArrayList<String> labels = new ArrayList<String>(choices.keySet());
			int selected = 1; // Default
			String current = the_responder.getSoundPath();
			if (!the_responder.isUseDefaultSound()) {
				selected = 0;
			} else if (current != null && current.length() > 0) {
				for (int i = 0; i < labels.size(); i++) {
					String key = labels.get(i);
					String path = choices.get(key);
					if (current.equals(path) || (NotificationSounds.isBundled(current)
							&& current.equals(path))) {
						selected = i;
						break;
					}
				}
			}

			AlertDialog.Builder sound_list = new AlertDialog.Builder(NotificationResponderEditor.this.getContext());
			sound_list.setTitle("Pick Sound");
			sound_list.setSingleChoiceItems(labels.toArray(new String[0]), selected,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							String label = labels.get(which);
							String path = choices.get(label);
							if ("__PICK__".equals(path)) {
								dialog.dismiss();
								launchStoragePicker();
								return;
							}
							if (which == 0) {
								the_responder.setUseDefaultSound(false);
								the_responder.setSoundPath("");
								sound.setChecked(false);
								sound_extra.setText(DISABLED_MSG);
								dialog.dismiss();
								return;
							}
							the_responder.setUseDefaultSound(true);
							if (which == 1) {
								the_responder.setSoundPath("");
								sound_extra.setText(DEFAULT_MSG);
							} else {
								the_responder.setSoundPath(path);
								sound_extra.setText("Currently using: " + label);
								previewSound(path);
							}
							sound.setChecked(true);
						}
					});
			AlertDialog sound_picker = sound_list.create();
			sound_picker.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					try {
						mp.stop();
					} catch (Exception ignored) {
					}
				}
			});
			sound_picker.show();
		}

		private class LightListReturnListener implements DialogInterface.OnClickListener {
			public void onClick(DialogInterface arg0, int arg1) {
				switch (arg1) {
				case 0:
					the_responder.setUseDefaultLight(true);
					the_responder.setColorToUse(0);
					break;
				case 1:
					the_responder.setUseDefaultLight(true);
					the_responder.setColorToUse(0xFF0000FF);
					break;
				case 2:
					the_responder.setUseDefaultLight(true);
					the_responder.setColorToUse(0xFF00FF00);
					break;
				case 3:
					the_responder.setUseDefaultLight(true);
					the_responder.setColorToUse(0xFFFF0000);
					break;
				case 4:
					the_responder.setUseDefaultLight(true);
					the_responder.setColorToUse(0xFFFF00FF);
					break;
				case 5:
					the_responder.setUseDefaultLight(true);
					the_responder.setColorToUse(0xFF00FFFF);
					break;
				case 6:
					the_responder.setUseDefaultLight(true);
					the_responder.setColorToUse(0xFFFFFFFF);
					break;
				default:
					break;
				}
				if (arg1 != 0) {
					lights_extra.setText("Currently using: " + lookupColor(arg1));
				} else {
					lights_extra.setText(DEFAULT_MSG);
				}
			}
		}

		private class VibrateListReturnListener implements DialogInterface.OnClickListener {
			public void onClick(DialogInterface arg0, int arg1) {
				the_responder.setUseDefaultVibrate(true);
				the_responder.setVibrateLength(arg1);
				if (arg1 != 0) {
					vibrate_extra.setText("Currently using: " + lookupVibrateLength(arg1));
				} else {
					vibrate_extra.setText(DEFAULT_MSG);
				}
			}
		}
	}
}

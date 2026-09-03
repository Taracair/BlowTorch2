package com.resurrection.blowtorch2.lib.trigger;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.service.sensor.GestureTuning;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Battery low/ok percents for this device ({@code bt_gesture_tuning}, never
 * exported). Do not read {@link GestureTuning} from the UI — that process has
 * a stale prefs cache. Fields start at shipped defaults; Save sends a command.
 */
public class BatteryThresholdDialog extends Dialog {

	private final IConnectionBinder service;
	private EditText lowField;
	private EditText recoverField;
	private TextView verdict;

	public BatteryThresholdDialog(final Context context, final IConnectionBinder service) {
		super(context, R.style.BlowTorch_Dialog);
		this.service = service;
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);

		float density = getContext().getResources().getDisplayMetrics().density;
		int pad = (int) (14 * density + 0.5f);

		LinearLayout root = new LinearLayout(getContext());
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(color(R.color.chrome_body));

		TextView title = new TextView(getContext());
		title.setText("Battery low threshold");
		title.setTextColor(color(R.color.chrome_title_text));
		title.setBackgroundColor(color(R.color.chrome_title_bar));
		title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setAllCaps(true);
		title.setGravity(Gravity.CENTER);
		root.addView(title, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, (int) (42 * density + 0.5f)));

		LinearLayout body = new LinearLayout(getContext());
		body.setOrientation(LinearLayout.VERTICAL);
		body.setPadding(pad, pad, pad, pad);

		TextView instruction = new TextView(getContext());
		instruction.setTextSize(15f);
		instruction.setTextColor(Color.WHITE);
		instruction.setText("batterylow fires once when charge crosses down through"
				+ " Low. batteryok fires once when it crosses back up through Recover."
				+ " Recover must sit at least five points above Low, so 19–21% cannot"
				+ " flap. Kept with this phone, not the world profile. The boxes start"
				+ " at the shipped 20 and 35 — type the pair you want. Save sends"
				+ " .sensor threshold battery; .sensor threshold in the game window"
				+ " shows the pair actually in force.");
		body.addView(instruction);

		lowField = numberField("Low %", GestureTuning.DEFAULT_BATTERY_LOW);
		recoverField = numberField("Recover %",
				GestureTuning.DEFAULT_BATTERY_RECOVER);
		body.addView(labelled(lowField, "Low % (batterylow)"));
		body.addView(labelled(recoverField, "Recover % (batteryok)"));

		verdict = new TextView(getContext());
		verdict.setTextSize(13f);
		verdict.setTextColor(color(R.color.chrome_description));
		verdict.setPadding(0, pad / 2, 0, 0);
		verdict.setText("Default is 20 and 35. .sensor fire batterylow tests the"
				+ " trigger without waiting for the charge to move.");
		body.addView(verdict);

		root.addView(body, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));

		LinearLayout footer = new LinearLayout(getContext());
		footer.setOrientation(LinearLayout.HORIZONTAL);
		footer.setBackgroundColor(color(R.color.chrome_title_bar));
		int footPad = (int) (6 * density + 0.5f);
		footer.setPadding(footPad, footPad, footPad, footPad);

		Button save = new Button(getContext());
		save.setText("Save");
		save.setMinHeight((int) (44 * density + 0.5f));
		save.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				store();
			}
		});

		Button close = new Button(getContext());
		close.setText("Close");
		close.setMinHeight((int) (44 * density + 0.5f));
		close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				dismiss();
			}
		});

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
				(int) (44 * density + 0.5f), 1f);
		footer.addView(save, lp);
		LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(0,
				(int) (44 * density + 0.5f), 1f);
		closeLp.leftMargin = footPad;
		footer.addView(close, closeLp);
		root.addView(footer);

		setContentView(root);
		Window window = getWindow();
		if (window != null) {
			int width = (int) (getContext().getResources().getDisplayMetrics().widthPixels
					* 0.92f);
			window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
			window.setGravity(Gravity.CENTER);
		}
	}

	private LinearLayout labelled(final EditText field, final String caption) {
		LinearLayout row = new LinearLayout(getContext());
		row.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (8 * getContext().getResources().getDisplayMetrics().density
				+ 0.5f);
		row.setPadding(0, pad, 0, 0);
		TextView label = new TextView(getContext());
		label.setText(caption);
		label.setTextColor(color(R.color.chrome_description));
		label.setTextSize(13f);
		row.addView(label);
		row.addView(field);
		return row;
	}

	private EditText numberField(final String hint, final int value) {
		EditText field = new EditText(getContext());
		field.setHint(hint);
		field.setText(Integer.toString(value));
		field.setInputType(InputType.TYPE_CLASS_NUMBER);
		field.setTextColor(Color.WHITE);
		field.setTextSize(22f);
		field.setSelectAllOnFocus(true);
		return field;
	}

	private void store() {
		int low;
		int recover;
		try {
			low = Integer.parseInt(lowField.getText().toString().trim());
			recover = Integer.parseInt(recoverField.getText().toString().trim());
		} catch (NumberFormatException bad) {
			verdict.setText("Two whole numbers, 1–99. Recover must be at least Low+5.");
			return;
		}
		int[] stored = GestureTuning.clampBatteryThresholds(low, recover);
		lowField.setText(Integer.toString(stored[0]));
		recoverField.setText(Integer.toString(stored[1]));
		if (service == null) {
			verdict.setText("No world open to send the pair to. Type"
					+ " .sensor threshold battery " + stored[0] + " " + stored[1]
					+ " in the game window.");
			return;
		}
		try {
			String command = String.format(Locale.US,
					".sensor threshold battery %d %d\r\n", stored[0], stored[1]);
			service.sendData(command.getBytes("UTF-8"));
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
					"BatteryThresholdDialog.store", e);
		}
		dismiss();
	}

	private int color(int id) {
		return getContext().getResources().getColor(id);
	}
}

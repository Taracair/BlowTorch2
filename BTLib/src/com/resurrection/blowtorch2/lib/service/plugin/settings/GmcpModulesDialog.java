package com.resurrection.blowtorch2.lib.service.plugin.settings;

import java.util.ArrayList;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.GmcpModuleRegistry;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.window.EditorDialogChrome;

import androidx.core.content.ContextCompat;

/**
 * Checkbox UI for GMCP modules: built-in, seen this session, catalog.
 * Apply writes gmcp_supports and optionally asks the service to renegotiate.
 */
public final class GmcpModulesDialog {

	public interface Host {
		IConnectionBinder getService();
		String getSupportsString();
		void applySupportsString(String supports, boolean renegotiate);
		ArrayList<String> getSeenModules();
		String getStatusHint();
	}

	private GmcpModulesDialog() {
	}

	public static void show(final Context context, final Host host) {
		if (context == null || host == null) {
			return;
		}
		final GmcpModuleRegistry reg = GmcpModuleRegistry.fromSupportsOption(host.getSupportsString());
		ArrayList<String> seen = host.getSeenModules();
		if (seen != null) {
			for (String s : seen) {
				reg.noteSeen(s);
			}
		}

		int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
				context.getResources().getDisplayMetrics());

		final Dialog dialog = OptionsSubdialog.open(context, "GMCP Modules");
		LinearLayout root = OptionsSubdialog.body(dialog);

		TextView intro = new TextView(context);
		intro.setText("Choose modules for Core.Supports.Set. Nothing is auto-enabled from "
				+ "\"seen\" — you decide. Apply renegotiates if connected.");
		intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		intro.setPadding(0, 0, 0, pad);
		OptionsSubdialog.ink(intro);
		root.addView(intro);

		String hint = host.getStatusHint();
		if (hint != null && hint.length() > 0) {
			TextView status = new TextView(context);
			status.setText(hint);
			status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
			status.setPadding(0, 0, 0, pad);
			OptionsSubdialog.muted(status);
			root.addView(status);
		}

		addSection(context, root, "Built-in (BlowTorch handles these)", reg.nativeModules(), reg);
		ArrayList<GmcpModuleRegistry.ModuleInfo> seenRows = new ArrayList<GmcpModuleRegistry.ModuleInfo>();
		for (String s : reg.seenModules()) {
			if (s.indexOf('.') < 0 && reg.isEnabled(s)) {
				continue;
			}
			boolean isNative = false;
			for (GmcpModuleRegistry.ModuleInfo n : reg.nativeModules()) {
				if (GmcpModuleRegistry.normKey(n.id).equals(GmcpModuleRegistry.normKey(s))) {
					isNative = true;
					break;
				}
			}
			if (isNative) {
				continue;
			}
			seenRows.add(new GmcpModuleRegistry.ModuleInfo(
					reg.canonicalId(s), 1, "Seen from this server",
					GmcpModuleRegistry.Kind.SEEN, false));
		}
		if (!seenRows.isEmpty()) {
			addSection(context, root, "Seen this session", seenRows, reg);
		}
		addSection(context, root, "Catalog (optional)", reg.catalogModules(), reg);

		final EditText advanced = new EditText(context);
		advanced.setSingleLine(false);
		advanced.setMinLines(2);
		advanced.setText(reg.toSupportsString());
		advanced.setVisibility(View.GONE);
		OptionsSubdialog.field(advanced);
		TextView advLabel = new TextView(context);
		advLabel.setText("Advanced Supports String");
		advLabel.setPadding(0, pad, 0, 4);
		advLabel.setVisibility(View.GONE);
		OptionsSubdialog.ink(advLabel);
		root.addView(advLabel);
		root.addView(advanced);

		Button toggleAdv = new Button(context);
		toggleAdv.setText("Show advanced string…");
		toggleAdv.setOnClickListener(new View.OnClickListener() {
			boolean open;
			@Override
			public void onClick(View v) {
				open = !open;
				int vis = open ? View.VISIBLE : View.GONE;
				advLabel.setVisibility(vis);
				advanced.setVisibility(vis);
				toggleAdv.setText(open ? "Hide advanced string" : "Show advanced string…");
				if (open) {
					advanced.setText(reg.toSupportsString());
				}
			}
		});
		root.addView(toggleAdv);

		Button renegotiate = new Button(context);
		renegotiate.setText("Apply & renegotiate");
		renegotiate.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				host.applySupportsString(supportsFrom(reg, advanced), true);
				Toast.makeText(context, "GMCP modules applied", Toast.LENGTH_SHORT).show();
				dialog.dismiss();
			}
		});
		Button cancel = new Button(context);
		cancel.setText("Cancel");
		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		Button done = new Button(context);
		done.setText("Done");
		done.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				host.applySupportsString(supportsFrom(reg, advanced), false);
				Toast.makeText(context, "GMCP modules saved (reconnect or Apply & renegotiate to push)",
						Toast.LENGTH_SHORT).show();
				dialog.dismiss();
			}
		});
		OptionsSubdialog.buttons(dialog, renegotiate, cancel, done);
		OptionsSubdialog.show(dialog);
	}

	private static String supportsFrom(GmcpModuleRegistry reg, EditText advanced) {
		String supports = advanced.getVisibility() == View.VISIBLE
				? advanced.getText().toString().trim()
				: reg.toSupportsString();
		if (supports.length() == 0) {
			supports = GmcpModuleRegistry.DEFAULT_SUPPORTS;
		}
		return supports;
	}

	private static void addSection(Context context, LinearLayout root, String title,
			ArrayList<GmcpModuleRegistry.ModuleInfo> modules, final GmcpModuleRegistry reg) {
		TextView h = new TextView(context);
		h.setText(title);
		h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		h.setPadding(0, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10,
				context.getResources().getDisplayMetrics()), 0, 4);
		h.setTypeface(null, android.graphics.Typeface.BOLD);
		OptionsSubdialog.ink(h);
		root.addView(h);

		for (final GmcpModuleRegistry.ModuleInfo m : modules) {
			CheckBox cb = new CheckBox(context);
			String badge = m.nativeHandler ? " · built-in" : (m.kind == GmcpModuleRegistry.Kind.SEEN ? " · seen" : "");
			cb.setText(m.id + "  v" + m.version + badge + "\n" + m.summary);
			cb.setChecked(reg.isEnabled(m.id));
			OptionsSubdialog.check(cb);
			cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					reg.setEnabled(m.id, isChecked);
				}
			});
			root.addView(cb);
		}
	}
}

final class OptionsSubdialog {

	private OptionsSubdialog() {
	}

	static Dialog open(Context context, String title) {
		Dialog dialog = new Dialog(context, EditorDialogChrome.dialogTheme());
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		Window window = dialog.getWindow();
		if (window != null) {
			window.setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
			window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
					| WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		}
		View root = LayoutInflater.from(context).inflate(R.layout.options_subdialog, null);
		TextView titleView = (TextView) root.findViewById(R.id.options_subdialog_title);
		titleView.setText(title);
		dialog.setContentView(root, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		dialog.setCanceledOnTouchOutside(true);
		return dialog;
	}

	static LinearLayout body(Dialog dialog) {
		return (LinearLayout) dialog.findViewById(R.id.options_subdialog_body);
	}

	static void buttons(Dialog dialog, Button... row) {
		LinearLayout bar = (LinearLayout) dialog.findViewById(R.id.options_subdialog_buttons);
		float density = dialog.getContext().getResources().getDisplayMetrics().density;
		int min = (int) (44 * density + 0.5f);
		int gap = (int) (6 * density + 0.5f);
		for (int i = 0; i < row.length; i++) {
			Button b = row[i];
			b.setMinHeight(min);
			b.setSingleLine(false);
			b.setMaxLines(2);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
			if (i > 0) {
				lp.leftMargin = gap;
			}
			bar.addView(b, lp);
		}
	}

	static void show(Dialog dialog) {
		EditorDialogChrome.applyFloatingWrapContentHeight(dialog);
		Window window = dialog.getWindow();
		if (window != null) {
			window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
					| WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		}
		dialog.show();
	}

	static void ink(TextView view) {
		view.setTextColor(ContextCompat.getColor(view.getContext(), R.color.chrome_title_text));
	}

	static void muted(TextView view) {
		view.setTextColor(ContextCompat.getColor(view.getContext(), R.color.chrome_description));
	}

	static void field(EditText field) {
		Context context = field.getContext();
		int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8,
				context.getResources().getDisplayMetrics());
		field.setTextColor(ContextCompat.getColor(context, R.color.chrome_title_text));
		field.setHintTextColor(ContextCompat.getColor(context, R.color.chrome_hint));
		field.setBackgroundResource(R.drawable.editor_search_field_bg);
		field.setPadding(pad, pad, pad, pad);
	}

	static void check(CheckBox box) {
		Context context = box.getContext();
		box.setTextColor(ContextCompat.getColor(context, R.color.chrome_title_text));
		box.setButtonTintList(new ColorStateList(
				new int[][] {
					new int[] { android.R.attr.state_checked },
					new int[] {}
				},
				new int[] {
					ContextCompat.getColor(context, R.color.chrome_accent),
					ContextCompat.getColor(context, R.color.chrome_chip_text)
				}));
	}
}

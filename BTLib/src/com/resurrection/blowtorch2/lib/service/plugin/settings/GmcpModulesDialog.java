package com.resurrection.blowtorch2.lib.service.plugin.settings;

import java.util.ArrayList;
import java.util.Locale;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.RemoteException;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.resurrection.blowtorch2.lib.service.GmcpModuleRegistry;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;

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

	public static void show(Context context, final Host host) {
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

		ScrollView scroll = new ScrollView(context);
		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(pad, pad, pad, pad);
		scroll.addView(root);

		TextView intro = new TextView(context);
		intro.setText("Choose modules for Core.Supports.Set. Nothing is auto-enabled from "
				+ "\"seen\" — you decide. Apply renegotiates if connected.");
		intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		intro.setPadding(0, 0, 0, pad);
		root.addView(intro);

		String hint = host.getStatusHint();
		if (hint != null && hint.length() > 0) {
			TextView status = new TextView(context);
			status.setText(hint);
			status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
			status.setPadding(0, 0, 0, pad);
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
		TextView advLabel = new TextView(context);
		advLabel.setText("Advanced Supports String");
		advLabel.setPadding(0, pad, 0, 4);
		advLabel.setVisibility(View.GONE);
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

		AlertDialog.Builder b = new AlertDialog.Builder(context);
		b.setTitle("GMCP Modules");
		b.setView(scroll);
		b.setNegativeButton(android.R.string.cancel, null);
		b.setNeutralButton("Apply", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String supports = advanced.getVisibility() == View.VISIBLE
						? advanced.getText().toString().trim()
						: reg.toSupportsString();
				if (supports.length() == 0) {
					supports = GmcpModuleRegistry.DEFAULT_SUPPORTS;
				}
				host.applySupportsString(supports, false);
				Toast.makeText(context, "GMCP modules saved (reconnect or Apply & renegotiate to push)",
						Toast.LENGTH_SHORT).show();
			}
		});
		b.setPositiveButton("Apply & renegotiate", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String supports = advanced.getVisibility() == View.VISIBLE
						? advanced.getText().toString().trim()
						: reg.toSupportsString();
				if (supports.length() == 0) {
					supports = GmcpModuleRegistry.DEFAULT_SUPPORTS;
				}
				host.applySupportsString(supports, true);
				Toast.makeText(context, "GMCP modules applied", Toast.LENGTH_SHORT).show();
			}
		});
		b.show();
	}

	private static void addSection(Context context, LinearLayout root, String title,
			ArrayList<GmcpModuleRegistry.ModuleInfo> modules, final GmcpModuleRegistry reg) {
		TextView h = new TextView(context);
		h.setText(title);
		h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		h.setPadding(0, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10,
				context.getResources().getDisplayMetrics()), 0, 4);
		h.setTypeface(null, android.graphics.Typeface.BOLD);
		root.addView(h);

		for (final GmcpModuleRegistry.ModuleInfo m : modules) {
			CheckBox cb = new CheckBox(context);
			String badge = m.nativeHandler ? " · built-in" : (m.kind == GmcpModuleRegistry.Kind.SEEN ? " · seen" : "");
			cb.setText(m.id + "  v" + m.version + badge + "\n" + m.summary);
			cb.setChecked(reg.isEnabled(m.id));
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

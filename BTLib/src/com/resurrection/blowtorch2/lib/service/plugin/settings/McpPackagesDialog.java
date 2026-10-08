package com.resurrection.blowtorch2.lib.service.plugin.settings;

import java.util.ArrayList;

import android.app.Dialog;
import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.resurrection.blowtorch2.lib.service.McpPackageRegistry;

/**
 * Checkbox UI for MCP packages (negotiate list). Apply writes mcp_packages.
 */
public final class McpPackagesDialog {

	public interface Host {
		String getPackagesString();
		void applyPackagesString(String packages, boolean renegotiate);
		ArrayList<String> getSeenPackages();
		String getStatusHint();
	}

	private McpPackagesDialog() {
	}

	public static void show(final Context context, final Host host) {
		if (context == null || host == null) {
			return;
		}
		final McpPackageRegistry reg = McpPackageRegistry.fromPackagesOption(host.getPackagesString());
		ArrayList<String> seen = host.getSeenPackages();
		if (seen != null) {
			for (String s : seen) {
				reg.noteSeen(s);
			}
		}

		int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
				context.getResources().getDisplayMetrics());
		final Dialog dialog = OptionsSubdialog.open(context, "MCP packages");
		LinearLayout root = OptionsSubdialog.body(dialog);

		TextView intro = new TextView(context);
		intro.setText("Choose MCP packages to advertise in mcp-negotiate-can. "
				+ "Nothing auto-enables from traffic. Apply can re-negotiate if connected.");
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

		addSection(context, root, "Built-in", reg.nativePackages(), reg);
		ArrayList<McpPackageRegistry.PackageInfo> seenRows =
				new ArrayList<McpPackageRegistry.PackageInfo>();
		for (String s : reg.seenPackages()) {
			boolean nativePkg = false;
			for (McpPackageRegistry.PackageInfo n : reg.nativePackages()) {
				if (McpPackageRegistry.normKey(n.id).equals(McpPackageRegistry.normKey(s))) {
					nativePkg = true;
					break;
				}
			}
			if (!nativePkg) {
				seenRows.add(new McpPackageRegistry.PackageInfo(s, "1.0", "1.0",
						"Seen this session", McpPackageRegistry.Kind.SEEN, false));
			}
		}
		if (!seenRows.isEmpty()) {
			addSection(context, root, "Seen this session", seenRows, reg);
		}
		addSection(context, root, "Catalog", reg.catalogPackages(), reg);

		final EditText advanced = new EditText(context);
		advanced.setMinLines(2);
		advanced.setText(reg.toPackagesString());
		advanced.setVisibility(View.GONE);
		OptionsSubdialog.field(advanced);
		root.addView(advanced);
		Button toggleAdv = new Button(context);
		toggleAdv.setText("Show advanced packages string…");
		toggleAdv.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				boolean show = advanced.getVisibility() != View.VISIBLE;
				advanced.setVisibility(show ? View.VISIBLE : View.GONE);
				toggleAdv.setText(show ? "Hide advanced string" : "Show advanced packages string…");
				if (show) {
					advanced.setText(reg.toPackagesString());
				}
			}
		});
		root.addView(toggleAdv);

		Button renegotiate = new Button(context);
		renegotiate.setText("Apply + renegotiate");
		renegotiate.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				host.applyPackagesString(packagesFrom(reg, advanced), true);
				Toast.makeText(context, "MCP packages saved + renegotiate", Toast.LENGTH_SHORT).show();
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
				host.applyPackagesString(packagesFrom(reg, advanced), false);
				Toast.makeText(context, "MCP packages saved", Toast.LENGTH_SHORT).show();
				dialog.dismiss();
			}
		});
		OptionsSubdialog.buttons(dialog, renegotiate, cancel, done);
		OptionsSubdialog.show(dialog);
	}

	private static String packagesFrom(McpPackageRegistry reg, EditText advanced) {
		return advanced.getVisibility() == View.VISIBLE
				? advanced.getText().toString()
				: reg.toPackagesString();
	}

	private static void addSection(Context context, LinearLayout root, String title,
			ArrayList<McpPackageRegistry.PackageInfo> rows, final McpPackageRegistry reg) {
		if (rows == null || rows.isEmpty()) {
			return;
		}
		TextView h = new TextView(context);
		h.setText(title);
		h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		h.setPadding(0, 16, 0, 8);
		OptionsSubdialog.ink(h);
		root.addView(h);
		for (final McpPackageRegistry.PackageInfo p : rows) {
			CheckBox cb = new CheckBox(context);
			cb.setText(p.id + "  (" + p.minVersion
					+ (p.minVersion.equals(p.maxVersion) ? "" : ("–" + p.maxVersion)) + ")\n"
					+ p.summary);
			cb.setChecked(reg.isEnabled(p.id));
			OptionsSubdialog.check(cb);
			cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					reg.setEnabled(p.id, isChecked);
				}
			});
			root.addView(cb);
		}
	}
}

package com.resurrection.blowtorch2.lib.service.plugin.settings;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

	public static void show(Context context, final Host host) {
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
		ScrollView scroll = new ScrollView(context);
		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(pad, pad, pad, pad);
		scroll.addView(root);

		TextView intro = new TextView(context);
		intro.setText("Choose MCP packages to advertise in mcp-negotiate-can. "
				+ "Nothing auto-enables from traffic. Apply can re-negotiate if connected.");
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

		AlertDialog.Builder b = new AlertDialog.Builder(context);
		b.setTitle("MCP packages");
		b.setView(scroll);
		b.setNegativeButton("Cancel", null);
		b.setNeutralButton("Apply", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String pkgs = advanced.getVisibility() == View.VISIBLE
						? advanced.getText().toString()
						: reg.toPackagesString();
				host.applyPackagesString(pkgs, false);
				Toast.makeText(context, "MCP packages saved", Toast.LENGTH_SHORT).show();
			}
		});
		b.setPositiveButton("Apply + renegotiate", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String pkgs = advanced.getVisibility() == View.VISIBLE
						? advanced.getText().toString()
						: reg.toPackagesString();
				host.applyPackagesString(pkgs, true);
				Toast.makeText(context, "MCP packages saved + renegotiate", Toast.LENGTH_SHORT).show();
			}
		});
		b.show();
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
		root.addView(h);
		for (final McpPackageRegistry.PackageInfo p : rows) {
			CheckBox cb = new CheckBox(context);
			cb.setText(p.id + "  (" + p.minVersion
					+ (p.minVersion.equals(p.maxVersion) ? "" : ("–" + p.maxVersion)) + ")\n"
					+ p.summary);
			cb.setChecked(reg.isEnabled(p.id));
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

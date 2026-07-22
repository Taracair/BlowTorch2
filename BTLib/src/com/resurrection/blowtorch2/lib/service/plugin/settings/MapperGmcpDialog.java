package com.resurrection.blowtorch2.lib.service.plugin.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Field-based UI for mapper GMCP Room sync (not raw strings).
 * Writes mapper_use_gmcp / mapper_gmcp_use_num / mapper_gmcp_use_coords /
 * mapper_gmcp_create_exits.
 */
public final class MapperGmcpDialog {

	public interface Host {
		boolean getUseGmcp();
		boolean getUseNum();
		boolean getUseCoords();
		boolean getCreateExits();
		void apply(boolean useGmcp, boolean useNum, boolean useCoords, boolean createExits);
	}

	private MapperGmcpDialog() {
	}

	public static void show(Context context, final Host host) {
		if (context == null || host == null) {
			return;
		}
		int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
				context.getResources().getDisplayMetrics());

		ScrollView scroll = new ScrollView(context);
		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(pad, pad, pad, pad);
		scroll.addView(root);

		TextView intro = new TextView(context);
		intro.setText("How the mapper uses GMCP Room.* (Eden / IRE / Forsaken / …).\n\n"
				+ "Requires Options → GMCP → Use GMCP? and Room in Manage modules…. "
				+ "ASCII maps in game text are not read — only Room.Info JSON.\n\n"
				+ "Typical Eden/IRE: Room.Info with num, coords/coord, exits {dir:vnum}.");
		intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		intro.setPadding(0, 0, 0, pad);
		root.addView(intro);

		final CheckBox useGmcp = new CheckBox(context);
		useGmcp.setText("Use GMCP Room Sync");
		useGmcp.setChecked(host.getUseGmcp());
		root.addView(useGmcp);

		final CheckBox useNum = new CheckBox(context);
		useNum.setText("Match rooms by number (num / id / vnum)");
		useNum.setChecked(host.getUseNum());
		root.addView(useNum);

		TextView numHint = new TextView(context);
		numHint.setText("Best for Eden-style MUDs: stable room id across revisits.");
		numHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		numHint.setPadding(pad * 2, 0, 0, pad / 2);
		root.addView(numHint);

		final CheckBox useCoords = new CheckBox(context);
		useCoords.setText("Place rooms at absolute coordinates");
		useCoords.setChecked(host.getUseCoords());
		root.addView(useCoords);

		TextView coordsHint = new TextView(context);
		coordsHint.setText("Uses coords/coord x,y (and z as floor). Off = update title/exits on the current tile only (relative / walk maps).");
		coordsHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		coordsHint.setPadding(pad * 2, 0, 0, pad / 2);
		root.addView(coordsHint);

		final CheckBox createExits = new CheckBox(context);
		createExits.setText("Create missing exit neighbors");
		createExits.setChecked(host.getCreateExits());
		root.addView(createExits);

		TextView exitsHint = new TextView(context);
		exitsHint.setText("From Room exits. Destination vnums become stubs until you visit them. Does not delete exits absent from GMCP.");
		exitsHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		exitsHint.setPadding(pad * 2, 0, 0, pad);
		root.addView(exitsHint);

		new AlertDialog.Builder(context)
				.setTitle("Mapper GMCP Room Sync")
				.setView(scroll)
				.setPositiveButton("Apply", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						host.apply(useGmcp.isChecked(), useNum.isChecked(),
								useCoords.isChecked(), createExits.isChecked());
						Toast.makeText(context, "Mapper GMCP settings saved",
								Toast.LENGTH_SHORT).show();
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}
}

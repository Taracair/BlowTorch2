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
 */
public final class MapperGmcpDialog {

	public interface Host {
		boolean getUseGmcp();
		boolean getGrow();
		boolean getUseNum();
		boolean getUseCoords();
		boolean getCreateExits();
		void apply(boolean useGmcp, boolean grow, boolean useNum, boolean useCoords,
				boolean createExits);
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
		intro.setText("GMCP Room sync is independent of Record/Draw.\n\n"
				+ "Requires Options → GMCP → Use GMCP? and Room in Manage modules….\n"
				+ "ASCII maps in game text are not read — only Room.Info JSON.\n\n"
				+ "Eden tip: leave absolute coordinates OFF (coords skip cells between exits).");
		intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		intro.setPadding(0, 0, 0, pad);
		root.addView(intro);

		final CheckBox useGmcp = new CheckBox(context);
		useGmcp.setText("Use GMCP Room Sync");
		useGmcp.setChecked(host.getUseGmcp());
		root.addView(useGmcp);

		final CheckBox grow = new CheckBox(context);
		grow.setText("Auto-grow map (create rooms & exits)");
		grow.setChecked(host.getGrow());
		root.addView(grow);

		TextView growHint = new TextView(context);
		growHint.setText("Off = follow only: jump to rooms that already exist (by number), update title — no new tiles. Useful for hand-drawn / imported maps.");
		growHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		growHint.setPadding(pad * 2, 0, 0, pad / 2);
		root.addView(growHint);

		final CheckBox useNum = new CheckBox(context);
		useNum.setText("Match rooms by number (num / id / vnum)");
		useNum.setChecked(host.getUseNum());
		root.addView(useNum);

		final CheckBox useCoords = new CheckBox(context);
		useCoords.setText("Place rooms at absolute coordinates");
		useCoords.setChecked(host.getUseCoords());
		root.addView(useCoords);

		TextView coordsHint = new TextView(context);
		coordsHint.setText("ON only when the MUD uses a true 1-step grid (many IRE rooms). Off = grow beside the previous room (recommended for Eden). Long arrows usually mean coords are on.");
		coordsHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		coordsHint.setPadding(pad * 2, 0, 0, pad / 2);
		root.addView(coordsHint);

		final CheckBox createExits = new CheckBox(context);
		createExits.setText("Create missing exit neighbors");
		createExits.setChecked(host.getCreateExits());
		root.addView(createExits);

		TextView exitsHint = new TextView(context);
		exitsHint.setText("Needs Auto-grow. Destination vnums become stubs until you visit them. Does not delete exits absent from GMCP. Does not remove your manual links.");
		exitsHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		exitsHint.setPadding(pad * 2, 0, 0, pad);
		root.addView(exitsHint);

		new AlertDialog.Builder(context)
				.setTitle("Mapper GMCP Room Sync")
				.setView(scroll)
				.setPositiveButton("Apply", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						host.apply(useGmcp.isChecked(), grow.isChecked(), useNum.isChecked(),
								useCoords.isChecked(), createExits.isChecked());
						Toast.makeText(context, "Mapper GMCP settings saved",
								Toast.LENGTH_SHORT).show();
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}
}

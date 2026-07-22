package com.resurrection.blowtorch2.lib.service.plugin.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.resurrection.blowtorch2.lib.mapper.MapperController;
import com.resurrection.blowtorch2.lib.mapper.MapperGmcpProfiles;

/**
 * Field-based UI for mapper GMCP Room sync (policy, coords, per-host profile).
 */
public final class MapperGmcpDialog {

	public interface Host {
		boolean getUseGmcp();
		String getPolicy();
		boolean getGrow();
		boolean getUseNum();
		boolean getUseCoords();
		boolean getCreateExits();
		/** Connection host hint for per-host profile save/load (may be null). */
		String getHostHint();
		Context getAppContext();
		void apply(boolean useGmcp, String policy, boolean useNum, boolean useCoords,
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
				+ "ASCII maps in game text are not read — only Room.Info JSON.");
		intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		intro.setPadding(0, 0, 0, pad);
		root.addView(intro);

		final CheckBox useGmcp = new CheckBox(context);
		useGmcp.setText("Use GMCP Room Sync");
		useGmcp.setChecked(host.getUseGmcp());
		root.addView(useGmcp);

		TextView policyLabel = new TextView(context);
		policyLabel.setText("Sync policy");
		policyLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		policyLabel.setPadding(0, pad / 2, 0, pad / 4);
		root.addView(policyLabel);

		final RadioGroup policyGroup = new RadioGroup(context);
		policyGroup.setOrientation(RadioGroup.VERTICAL);
		final RadioButton follow = new RadioButton(context);
		follow.setText("Follow only — jump by room number; no new rooms/exits");
		follow.setId(1001);
		final RadioButton sync = new RadioButton(context);
		sync.setText("Sync (default) — create/grow; prompt on title conflicts");
		sync.setId(1002);
		final RadioButton strict = new RadioButton(context);
		strict.setText("Strict — like Sync, always overwrite unlocked titles");
		strict.setId(1003);
		policyGroup.addView(follow);
		policyGroup.addView(sync);
		policyGroup.addView(strict);
		String policy = MapperController.normalizeGmcpPolicy(host.getPolicy());
		if (MapperController.GMCP_POLICY_FOLLOW.equals(policy)
				|| (!host.getGrow() && host.getPolicy() == null)) {
			follow.setChecked(true);
		} else if (MapperController.GMCP_POLICY_STRICT.equals(policy)) {
			strict.setChecked(true);
		} else {
			sync.setChecked(true);
		}
		root.addView(policyGroup);

		TextView policyHint = new TextView(context);
		policyHint.setText("Locked tile titles/positions are never changed by GMCP.");
		policyHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		policyHint.setPadding(pad * 2, 0, 0, pad / 2);
		root.addView(policyHint);

		final CheckBox useNum = new CheckBox(context);
		useNum.setText("Match rooms by number (num / id / vnum)");
		useNum.setChecked(host.getUseNum());
		root.addView(useNum);

		final CheckBox useCoords = new CheckBox(context);
		useCoords.setText("Place rooms at absolute coordinates");
		useCoords.setChecked(host.getUseCoords());
		root.addView(useCoords);

		TextView coordsHint = new TextView(context);
		coordsHint.setText("ON only when the MUD uses a true 1-step grid. Off = grow beside the previous room (recommended for sparse world coordinates).");
		coordsHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		coordsHint.setPadding(pad * 2, 0, 0, pad / 2);
		root.addView(coordsHint);

		final CheckBox createExits = new CheckBox(context);
		createExits.setText("Create missing exit neighbors");
		createExits.setChecked(host.getCreateExits());
		root.addView(createExits);

		TextView exitsHint = new TextView(context);
		exitsHint.setText("Needs Sync/Strict policy. Destination vnums become stubs until you visit them. Does not delete exits.");
		exitsHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		exitsHint.setPadding(pad * 2, 0, 0, pad);
		root.addView(exitsHint);

		TextView profileLabel = new TextView(context);
		profileLabel.setText("Host profile presets");
		profileLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		profileLabel.setPadding(0, pad / 2, 0, pad / 4);
		root.addView(profileLabel);

		final String hostHint = host.getHostHint();
		TextView hostLine = new TextView(context);
		hostLine.setText(hostHint != null && hostHint.length() > 0
				? ("Current host: " + hostHint)
				: "Current host: (unknown)");
		hostLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		root.addView(hostLine);

		android.widget.Button loadSparse = new android.widget.Button(context);
		loadSparse.setText("Apply: " + MapperGmcpProfiles.LABEL_SPARSE);
		loadSparse.setOnClickListener(new android.view.View.OnClickListener() {
			@Override
			public void onClick(android.view.View v) {
				applyProfileToWidgets(MapperGmcpProfiles.defaultsFor(
						MapperGmcpProfiles.PROFILE_SPARSE),
						useGmcp, follow, sync, strict, useNum, useCoords, createExits);
			}
		});
		root.addView(loadSparse);

		android.widget.Button loadGrid = new android.widget.Button(context);
		loadGrid.setText("Apply: " + MapperGmcpProfiles.LABEL_UNIT_GRID);
		loadGrid.setOnClickListener(new android.view.View.OnClickListener() {
			@Override
			public void onClick(android.view.View v) {
				applyProfileToWidgets(MapperGmcpProfiles.defaultsFor(
						MapperGmcpProfiles.PROFILE_UNIT_GRID),
						useGmcp, follow, sync, strict, useNum, useCoords, createExits);
			}
		});
		root.addView(loadGrid);

		android.widget.Button loadSaved = new android.widget.Button(context);
		loadSaved.setText("Load saved profile for this host");
		loadSaved.setOnClickListener(new android.view.View.OnClickListener() {
			@Override
			public void onClick(android.view.View v) {
				MapperGmcpProfiles.Profile p = MapperGmcpProfiles.loadForHost(
						host.getAppContext(), hostHint);
				if (p == null) {
					Toast.makeText(context, "No saved profile for this host",
							Toast.LENGTH_SHORT).show();
					return;
				}
				applyProfileToWidgets(p, useGmcp, follow, sync, strict, useNum,
						useCoords, createExits);
				Toast.makeText(context, "Loaded " + MapperGmcpProfiles.labelFor(p.id),
						Toast.LENGTH_SHORT).show();
			}
		});
		root.addView(loadSaved);

		new AlertDialog.Builder(context)
				.setTitle("Mapper GMCP Room Sync")
				.setView(scroll)
				.setPositiveButton("Apply", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String pol = MapperController.GMCP_POLICY_SYNC;
						int checked = policyGroup.getCheckedRadioButtonId();
						if (checked == follow.getId()) {
							pol = MapperController.GMCP_POLICY_FOLLOW;
						} else if (checked == strict.getId()) {
							pol = MapperController.GMCP_POLICY_STRICT;
						}
						host.apply(useGmcp.isChecked(), pol, useNum.isChecked(),
								useCoords.isChecked(), createExits.isChecked());
						Toast.makeText(context, "Mapper GMCP settings saved",
								Toast.LENGTH_SHORT).show();
					}
				})
				.setNeutralButton("Save for host", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String pol = MapperController.GMCP_POLICY_SYNC;
						int checked = policyGroup.getCheckedRadioButtonId();
						if (checked == follow.getId()) {
							pol = MapperController.GMCP_POLICY_FOLLOW;
						} else if (checked == strict.getId()) {
							pol = MapperController.GMCP_POLICY_STRICT;
						}
						host.apply(useGmcp.isChecked(), pol, useNum.isChecked(),
								useCoords.isChecked(), createExits.isChecked());
						MapperGmcpProfiles.Profile p = new MapperGmcpProfiles.Profile();
						p.id = useCoords.isChecked()
								? MapperGmcpProfiles.PROFILE_UNIT_GRID
								: MapperGmcpProfiles.PROFILE_SPARSE;
						p.policy = pol;
						p.useGmcp = useGmcp.isChecked();
						p.useNum = useNum.isChecked();
						p.useCoords = useCoords.isChecked();
						p.createExits = createExits.isChecked();
						MapperGmcpProfiles.saveForHost(host.getAppContext(), hostHint, p);
						Toast.makeText(context,
								hostHint != null && hostHint.length() > 0
										? ("Saved profile for " + hostHint)
										: "Saved profile (no host hint)",
								Toast.LENGTH_SHORT).show();
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private static void applyProfileToWidgets(MapperGmcpProfiles.Profile p,
			CheckBox useGmcp, RadioButton follow, RadioButton sync, RadioButton strict,
			CheckBox useNum, CheckBox useCoords, CheckBox createExits) {
		if (p == null) {
			return;
		}
		useGmcp.setChecked(p.useGmcp);
		useNum.setChecked(p.useNum);
		useCoords.setChecked(p.useCoords);
		createExits.setChecked(p.createExits);
		String pol = MapperController.normalizeGmcpPolicy(p.policy);
		if (MapperController.GMCP_POLICY_FOLLOW.equals(pol)) {
			follow.setChecked(true);
		} else if (MapperController.GMCP_POLICY_STRICT.equals(pol)) {
			strict.setChecked(true);
		} else {
			sync.setChecked(true);
		}
	}
}

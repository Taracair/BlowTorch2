package com.resurrection.blowtorch2.lib.service.plugin.settings;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.mapper.MapperController;
import com.resurrection.blowtorch2.lib.mapper.MapperGmcpProfiles;
import com.resurrection.blowtorch2.lib.window.EditorDialogChrome;

import android.app.Dialog;
import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

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

		final Dialog dialog = new Dialog(context, EditorDialogChrome.fullScreenTheme());
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setCanceledOnTouchOutside(false);
		Window window = dialog.getWindow();
		if (window != null) {
			window.setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		}
		dialog.setContentView(R.layout.mapper_form_shell);
		((TextView) dialog.findViewById(R.id.titlebar)).setText("Mapper GMCP Room Sync");
		LinearLayout root = (LinearLayout) dialog.findViewById(R.id.mapper_shell_body);

		TextView intro = new TextView(context);
		intro.setText("GMCP Room sync is independent of Record/Draw.\n\n"
				+ "Requires Options → Protocols → Use GMCP? and Room in Manage modules….\n"
				+ "ASCII maps in game text are not read — only Room.Info JSON.");
		intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		intro.setTextColor(ContextCompat.getColor(context, R.color.chrome_title_text));
		intro.setPadding(0, 0, 0, pad);
		root.addView(intro);

		final CheckBox useGmcp = new CheckBox(context);
		useGmcp.setText("Use GMCP Room Sync");
		useGmcp.setTextColor(ContextCompat.getColor(context, R.color.chrome_title_text));
		useGmcp.setChecked(host.getUseGmcp());
		root.addView(useGmcp);

		TextView policyLabel = new TextView(context);
		policyLabel.setText("Sync policy");
		policyLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		policyLabel.setTextColor(ContextCompat.getColor(context, R.color.chrome_title_text));
		policyLabel.setPadding(0, pad / 2, 0, pad / 4);
		root.addView(policyLabel);

		final RadioGroup policyGroup = new RadioGroup(context);
		policyGroup.setOrientation(RadioGroup.VERTICAL);
		final RadioButton follow = new RadioButton(context);
		follow.setText("Follow only — jump by room number; no new rooms/exits");
		follow.setTextColor(ContextCompat.getColor(context, R.color.chrome_title_text));
		follow.setId(1001);
		final RadioButton sync = new RadioButton(context);
		sync.setText("Sync (default) — create/grow; prompt on title conflicts");
		sync.setTextColor(ContextCompat.getColor(context, R.color.chrome_title_text));
		sync.setId(1002);
		final RadioButton strict = new RadioButton(context);
		strict.setText("Strict — like Sync, always overwrite unlocked titles");
		strict.setTextColor(ContextCompat.getColor(context, R.color.chrome_title_text));
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
		policyHint.setTextColor(ContextCompat.getColor(context, R.color.chrome_description));
		policyHint.setPadding(pad * 2, 0, 0, pad / 2);
		root.addView(policyHint);

		final CheckBox useNum = new CheckBox(context);
		useNum.setText("Match rooms by number (num / id / vnum)");
		useNum.setTextColor(ContextCompat.getColor(context, R.color.chrome_title_text));
		useNum.setChecked(host.getUseNum());
		root.addView(useNum);

		final CheckBox useCoords = new CheckBox(context);
		useCoords.setText("Place rooms at absolute coordinates");
		useCoords.setTextColor(ContextCompat.getColor(context, R.color.chrome_title_text));
		useCoords.setChecked(host.getUseCoords());
		root.addView(useCoords);

		TextView coordsHint = new TextView(context);
		coordsHint.setText("ON only when the MUD uses a true 1-step grid. Off = grow beside the previous room (recommended for sparse world coordinates).");
		coordsHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		coordsHint.setTextColor(ContextCompat.getColor(context, R.color.chrome_description));
		coordsHint.setPadding(pad * 2, 0, 0, pad / 2);
		root.addView(coordsHint);

		final CheckBox createExits = new CheckBox(context);
		createExits.setText("Create missing exit neighbors");
		createExits.setTextColor(ContextCompat.getColor(context, R.color.chrome_title_text));
		createExits.setChecked(host.getCreateExits());
		root.addView(createExits);

		TextView exitsHint = new TextView(context);
		exitsHint.setText("Needs Sync/Strict policy. Destination vnums become stubs until you visit them. Does not delete exits.");
		exitsHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		exitsHint.setTextColor(ContextCompat.getColor(context, R.color.chrome_description));
		exitsHint.setPadding(pad * 2, 0, 0, pad);
		root.addView(exitsHint);

		TextView profileLabel = new TextView(context);
		profileLabel.setText("Host profile presets");
		profileLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		profileLabel.setTextColor(ContextCompat.getColor(context, R.color.chrome_title_text));
		profileLabel.setPadding(0, pad / 2, 0, pad / 4);
		root.addView(profileLabel);

		final String hostHint = host.getHostHint();
		TextView hostLine = new TextView(context);
		hostLine.setText(hostHint != null && hostHint.length() > 0
				? ("Current host: " + hostHint)
				: "Current host: (unknown)");
		hostLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		hostLine.setTextColor(ContextCompat.getColor(context, R.color.chrome_description));
		root.addView(hostLine);

		Button loadSparse = new Button(context);
		loadSparse.setText("Apply: " + MapperGmcpProfiles.LABEL_SPARSE);
		loadSparse.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				applyProfileToWidgets(MapperGmcpProfiles.defaultsFor(
						MapperGmcpProfiles.PROFILE_SPARSE),
						useGmcp, follow, sync, strict, useNum, useCoords, createExits);
			}
		});
		root.addView(loadSparse);

		Button loadGrid = new Button(context);
		loadGrid.setText("Apply: " + MapperGmcpProfiles.LABEL_UNIT_GRID);
		loadGrid.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				applyProfileToWidgets(MapperGmcpProfiles.defaultsFor(
						MapperGmcpProfiles.PROFILE_UNIT_GRID),
						useGmcp, follow, sync, strict, useNum, useCoords, createExits);
			}
		});
		root.addView(loadGrid);

		Button loadSaved = new Button(context);
		loadSaved.setText("Load saved profile for this host");
		loadSaved.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
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

		LinearLayout footer = (LinearLayout) dialog.findViewById(R.id.button_row);
		Button cancel = barButton(context, "Cancel", false);
		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		footer.addView(cancel);

		Button saveHost = barButton(context, "Save for host", true);
		saveHost.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String pol = selectedPolicy(policyGroup, follow, strict);
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
				dialog.dismiss();
			}
		});
		footer.addView(saveHost);

		Button done = barButton(context, "Done", true);
		done.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String pol = selectedPolicy(policyGroup, follow, strict);
				host.apply(useGmcp.isChecked(), pol, useNum.isChecked(),
						useCoords.isChecked(), createExits.isChecked());
				Toast.makeText(context, "Mapper GMCP settings saved",
						Toast.LENGTH_SHORT).show();
				dialog.dismiss();
			}
		});
		footer.addView(done);

		EditorDialogChrome.applyFullScreen(dialog);
		dialog.show();
	}

	private static String selectedPolicy(RadioGroup policyGroup, RadioButton follow,
			RadioButton strict) {
		int checked = policyGroup.getCheckedRadioButtonId();
		if (checked == follow.getId()) {
			return MapperController.GMCP_POLICY_FOLLOW;
		}
		if (checked == strict.getId()) {
			return MapperController.GMCP_POLICY_STRICT;
		}
		return MapperController.GMCP_POLICY_SYNC;
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

	private static int dip(Context context, int dips) {
		return Math.round(dips * context.getResources().getDisplayMetrics().density);
	}

	private static Button barButton(Context context, String label, boolean gap) {
		Button button = new Button(context);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		if (gap) {
			lp.leftMargin = dip(context, 6);
		}
		button.setMinHeight(dip(context, 44));
		button.setSingleLine(false);
		button.setMaxLines(2);
		button.setLayoutParams(lp);
		button.setText(label);
		return button;
	}
}

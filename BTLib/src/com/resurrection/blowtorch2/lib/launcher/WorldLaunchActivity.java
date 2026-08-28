package com.resurrection.blowtorch2.lib.launcher;

import android.app.Activity;
import android.os.Bundle;

/**
 * Exported pin target. Must not carry {@code MAIN}/{@code LAUNCHER}: home
 * screens keyed on that component drop the world extras and open the server
 * list. {@link com.resurrection.blowtorch2.FreeLauncher} stays the app icon.
 *
 * <p>{@code BlowTorch.Invisible} / {@code windowNoDisplay} is only safe
 * because {@link WorldLaunch#handoff} finishes inside {@code onCreate}.
 */
public class WorldLaunchActivity extends Activity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		WorldLaunch.handoff(this);
	}
}

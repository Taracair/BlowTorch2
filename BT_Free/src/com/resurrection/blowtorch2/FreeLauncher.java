package com.resurrection.blowtorch2;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.resurrection.blowtorch2.lib.launcher.LauncherShortcutExtras;
import com.resurrection.blowtorch2.lib.launcher.WorldLaunch;

public class FreeLauncher extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent incoming = getIntent();
        if (WorldLaunch.startFromIntent(this, incoming)) {
            this.overridePendingTransition(0, 0);
            this.finish();
            return;
        }

        Intent launch = new Intent(this, com.resurrection.blowtorch2.lib.launcher.Launcher.class);
        LauncherShortcutExtras.copy(incoming, launch);
        launch.putExtra("LAUNCH_MODE", getPackageName());
        this.startActivity(launch);
        this.overridePendingTransition(0, 0);
        this.finish();
    }
}

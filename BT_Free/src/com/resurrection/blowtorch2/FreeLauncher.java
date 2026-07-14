package com.resurrection.blowtorch2;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class FreeLauncher extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent launch = new Intent(this, com.resurrection.blowtorch2.lib.launcher.Launcher.class);
        launch.putExtra("LAUNCH_MODE", getPackageName());
        this.startActivity(launch);
        this.overridePendingTransition(0, 0);
        this.finish();
    }
}
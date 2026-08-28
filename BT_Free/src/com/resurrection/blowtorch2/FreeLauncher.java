package com.resurrection.blowtorch2;

import android.app.Activity;
import android.os.Bundle;

import com.resurrection.blowtorch2.lib.launcher.WorldLaunch;

public class FreeLauncher extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WorldLaunch.handoff(this);
    }
}

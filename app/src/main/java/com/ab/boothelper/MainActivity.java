package com.ab.boothelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

public class MainActivity extends Activity {
    private static final String TARGET_PKG = "com.V2.blb5";
    private static final long BOOT_WINDOW_MS = 120_000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) {
            // Fresh boot: launch BBLL
            Intent launch = getPackageManager().getLaunchIntentForPackage(TARGET_PKG);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
            }
            // Hand HOME back to monet automatically
            restoreMonetHome();
        }
        finish();
    }

    private void restoreMonetHome() {
        try {
            Runtime.getRuntime().exec(new String[]{
                "cmd", "package", "set-home-activity",
                "com.klevico.monet/.HomeActivity"
            });
        } catch (Exception e) { }
    }
}

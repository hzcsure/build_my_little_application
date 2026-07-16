package com.ab.boothelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

public class MainActivity extends Activity {
    private static final String TARGET_PKG = "com.V2.blb5";
    private static final String MONET_PKG = "com.klevico.monet";
    private static final String MONET_ACT = "com.klevico.monet.HomeActivity";
    private static final long BOOT_WINDOW_MS = 120_000; // 2 min after boot = fresh launch

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Only launch BBLL if this is a fresh boot (within 2 min of system start)
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(TARGET_PKG);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
            }
        }

        // Redirect to monet so the TV has a working HOME
        // Monet is the actual launcher, BootHelper is just a boot-time proxy
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setClassName(MONET_PKG, MONET_ACT);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }
}

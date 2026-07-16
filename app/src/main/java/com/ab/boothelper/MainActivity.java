package com.ab.boothelper;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;

public class MainActivity extends Activity {
    private static final String TARGET_PKG = "com.V2.blb5";
    private static final String MONET_PKG = "com.klevico.monet";
    private static final String MONET_ACT = "com.klevico.monet.HomeActivity";
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
        }

        // Disable our HOME alias -> monet becomes sole HOME automatically
        disableHomeAlias();

        // Redirect to monet
        Intent monet = new Intent(Intent.ACTION_MAIN);
        monet.setClassName(MONET_PKG, MONET_ACT);
        monet.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(monet);
        finish();
    }

    private void disableHomeAlias() {
        try {
            PackageManager pm = getPackageManager();
            ComponentName homeAlias = new ComponentName(
                "com.ab.boothelper",
                "com.ab.boothelper.HomeAlias");
            pm.setComponentEnabledSetting(homeAlias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        } catch (Exception e) { }
}

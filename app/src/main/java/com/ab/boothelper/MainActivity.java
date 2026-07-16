package com.ab.boothelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity {
    private static final String TARGET_PKG = "com.V2.blb5";
    private static final String MONET_PKG = "com.klevico.monet";
    private static final String MONET_HOME = "com.klevico.monet.HomeActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Try to launch BBLL first
        Intent launch = getPackageManager().getLaunchIntentForPackage(TARGET_PKG);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        }
        // Then redirect to monet so the TV has a working HOME
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setClassName(MONET_PKG, MONET_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }
}

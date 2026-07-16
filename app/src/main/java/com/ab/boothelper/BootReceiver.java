package com.ab.boothelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

public class BootReceiver extends BroadcastReceiver {
    private static final String TARGET_PKG = "com.V2.blb5";
    private static final long DELAY_MS = 30000; // 30 second delay

    @Override
    public void onReceive(final Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) 
                || Intent.ACTION_USER_PRESENT.equals(action)) {
            
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent launch = context.getPackageManager()
                                .getLaunchIntentForPackage(TARGET_PKG);
                        if (launch != null) {
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(launch);
                        }
                    } catch (Exception e) {
                        // Silently ignore - app may not be installed
                    }
                }
            }, DELAY_MS);
        }
    }
}

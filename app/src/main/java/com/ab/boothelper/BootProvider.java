package com.ab.boothelper;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

public class BootProvider extends ContentProvider {
    private static final String TARGET_PKG = "com.V2.blb5";
    private static final long DELAY_MS = 30000;

    @Override
    public boolean onCreate() {
        // ContentProvider.onCreate() runs during boot, before any BroadcastReceiver
        // Xiaomi does NOT block this unlike boot broadcasts
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent launch = getContext().getPackageManager()
                            .getLaunchIntentForPackage(TARGET_PKG);
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getContext().startActivity(launch);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }, DELAY_MS);
        return true;
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}

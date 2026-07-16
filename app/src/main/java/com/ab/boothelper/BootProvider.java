package com.ab.boothelper;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

public class BootProvider extends ContentProvider {
    private static final String TARGET_PKG = "com.V2.blb5";

    @Override
    public boolean onCreate() {
        // fire-and-forget in background thread so we don't block app init
        new Thread() {
            @Override
            public void run() {
                try { Thread.sleep(5000); } catch (Exception e) { }
                try {
                    Intent launch = getContext().getPackageManager()
                            .getLaunchIntentForPackage(TARGET_PKG);
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getContext().startActivity(launch);
                    }
                } catch (Exception e) { }
            }
        }.start();
        return true;
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}

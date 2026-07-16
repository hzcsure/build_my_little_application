package com.ab.boothelper;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/**
 * ContentProvider to ensure app process starts on boot.
 * Xiaomi doesn't send boot broadcasts to non-system apps,
 * but as a HOME app, the system must start this process.
 */
public class BootProvider extends ContentProvider {
    @Override
    public boolean onCreate() { return true; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}

package com.ab.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomeActivity extends Activity {
    private static final String BBLL_PKG = "com.V2.blb5";
    private static final String PREFS = "launcher_prefs";
    private static final String KEY_HIDDEN = "hidden_packages";
    private static final long BOOT_WINDOW = 120_000;
    private static final int COLS = 5;

    private GridView mainGrid;
    private AppAdapter mainAdapter;
    private LinearLayout bottomBar;
    private ImageView bottomIcon;
    private TextView bottomLabel;
    private PackageManager pm;
    private Set<String> hiddenPkgs;
    private List<AppInfo> allApps, shownApps;

    private int iconSize, gridPad, tilePad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Cold boot: launch BBLL first
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW) {
            launchBBLL();
        }

        pm = getPackageManager();
        hiddenPkgs = getSharedPreferences(PREFS, 0).getStringSet(KEY_HIDDEN, new HashSet<>());
        iconSize = dp(80);
        gridPad = dp(24);
        tilePad = dp(8);

        buildUI();
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
    }

    // ====== Boot BBLL ======
    private void launchBBLL() {
        try {
            Intent i = pm.getLaunchIntentForPackage(BBLL_PKG);
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
        } catch (Exception e) { }
    }

    // ====== UI ======
    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        mainGrid = new GridView(this);
        mainGrid.setNumColumns(COLS);
        mainGrid.setHorizontalSpacing(dp(4));
        mainGrid.setVerticalSpacing(dp(12));
        mainGrid.setPadding(gridPad, gridPad, gridPad, gridPad);
        mainGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        mainGrid.setColumnWidth(iconSize + tilePad);
        mainGrid.setSelector(new ColorDrawable(Color.TRANSPARENT));

        mainAdapter = new AppAdapter();
        mainGrid.setAdapter(mainAdapter);
        mainGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                AppInfo app = mainAdapter.getItem(pos);
                if (app != null && app.intent != null) {
                    app.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(app.intent);
                }
            }
        });

        // Bottom action bar (hidden by default)
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(dp(16), dp(8), dp(16), dp(8));
        bottomBar.setBackgroundColor(0xCC222222);
        bottomBar.setVisibility(View.GONE);
        bottomBar.setFocusable(true);

        bottomIcon = new ImageView(this);
        bottomIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        bottomBar.addView(bottomIcon);

        bottomLabel = new TextView(this);
        bottomLabel.setTextColor(Color.WHITE);
        bottomLabel.setTextSize(14);
        bottomLabel.setPadding(dp(12), 0, dp(12), 0);
        bottomBar.addView(bottomLabel);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        btnParams.setMargins(dp(8), 0, dp(8), 0);

        addButton(bottomBar, "Hide", new Runnable() { public void run() { hideSelected(); } });
        addButton(bottomBar, "Uninstall", new Runnable() { public void run() { uninstallSelected(); } });
        addButton(bottomBar, "Restore All", new Runnable() { public void run() { restoreAll(); } });

        root.addView(mainGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(bottomBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        setContentView(root);
    }

    private void addButton(LinearLayout bar, String text, final Runnable action) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(16), 0, dp(16), 0);
        btn.setBackground(rectBg(Color.GRAY));
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        lp.setMargins(dp(6), 0, dp(6), 0);
        btn.setLayoutParams(lp);
        bar.addView(btn);
    }

    private GradientDrawable rectBg(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(6));
        g.setColor(color);
        return g;
    }

    // ====== Menu key ======
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
            toggleBottomBar();
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            if (bottomBar.getVisibility() == View.VISIBLE) {
                bottomBar.setVisibility(View.GONE);
                mainGrid.requestFocus();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void toggleBottomBar() {
        if (bottomBar.getVisibility() == View.GONE) {
            AppInfo sel = (AppInfo) mainGrid.getSelectedItem();
            if (sel != null) {
                bottomIcon.setImageDrawable(sel.icon);
                bottomLabel.setText(sel.label);
            }
            bottomBar.setVisibility(View.VISIBLE);
            bottomBar.requestFocus();
        } else {
            bottomBar.setVisibility(View.GONE);
            mainGrid.requestFocus();
        }
    }

    // ====== Actions ======
    private void hideSelected() {
        AppInfo sel = (AppInfo) mainGrid.getSelectedItem();
        if (sel != null) {
            hiddenPkgs.add(sel.pkg);
            saveHidden();
            loadApps();
            closeBar();
        }
    }

    private void uninstallSelected() {
        AppInfo sel = (AppInfo) mainGrid.getSelectedItem();
        if (sel != null) {
            Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + sel.pkg));
            startActivity(i);
            closeBar();
        }
    }

    private void restoreAll() {
        hiddenPkgs.clear();
        saveHidden();
        loadApps();
        closeBar();
    }

    private void closeBar() {
        bottomBar.setVisibility(View.GONE);
        mainGrid.requestFocus();
    }

    private void saveHidden() {
        getSharedPreferences(PREFS, 0).edit().putStringSet(KEY_HIDDEN, new HashSet<>(hiddenPkgs)).apply();
    }

    // ====== App loading ======
    private void loadApps() {
        allApps = new ArrayList<>();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);

        for (ResolveInfo ri : resolved) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            AppInfo info = new AppInfo();
            info.pkg = ri.activityInfo.packageName;
            info.label = ri.loadLabel(pm).toString();
            info.icon = ri.loadIcon(pm);
            info.intent = new Intent(Intent.ACTION_MAIN);
            info.intent.setComponent(new ComponentName(info.pkg, ri.activityInfo.name));
            info.intent.addCategory(Intent.CATEGORY_LAUNCHER);
            allApps.add(info);
        }

        Collections.sort(allApps, new Comparator<AppInfo>() {
            public int compare(AppInfo a, AppInfo b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        // Split into shown/hidden
        shownApps = new ArrayList<>();
        for (AppInfo a : allApps) {
            if (!hiddenPkgs.contains(a.pkg)) shownApps.add(a);
        }

        mainAdapter.notifyDataSetChanged();
    }

    // ====== Adapter ======
    class AppInfo {
        String pkg, label;
        Drawable icon;
        Intent intent;
    }

    class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return shownApps != null ? shownApps.size() : 0; }
        @Override public AppInfo getItem(int pos) { return shownApps.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            if (convert == null) {
                convert = newTile();
            }
            AppInfo app = getItem(pos);
            ImageView icon = (ImageView) convert.findViewWithTag("icon");
            TextView label = (TextView) convert.findViewWithTag("label");
            icon.setImageDrawable(app.icon);
            label.setText(app.label);
            return convert;
        }
    }

    private View newTile() {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(tilePad, tilePad, tilePad, tilePad);
        tile.setBackground(rectBg(0x22FFFFFF));
        tile.setFocusable(true);

        ImageView icon = new ImageView(this);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setTag("icon");
        tile.addView(icon);

        TextView label = new TextView(this);
        label.setTextSize(10);
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setTag("label");
        tile.addView(label);

        return tile;
    }

    private int dp(int px) {
        return (int)(px * getResources().getDisplayMetrics().density + 0.5f);
    }
}

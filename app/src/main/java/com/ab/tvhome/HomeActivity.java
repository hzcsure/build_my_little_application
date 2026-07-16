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
import android.graphics.drawable.StateListDrawable;
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
    private StateListDrawable tileBg, tileBgFocused;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (SystemClock.elapsedRealtime() < BOOT_WINDOW) {
            launchBBLL();
        }

        pm = getPackageManager();
        hiddenPkgs = getSharedPreferences(PREFS, 0).getStringSet(KEY_HIDDEN, new HashSet<>());
        iconSize = dp(80);
        gridPad = dp(24);
        tilePad = dp(8);

        // Focus & unfocused backgrounds
        tileBg = newStateDrawable(0x22FFFFFF, 0);
        tileBgFocused = newStateDrawable(0x4400BFFF, dp(4)); // blue glow border when focused

        buildUI();
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
    }

    private void launchBBLL() {
        try {
            Intent i = pm.getLaunchIntentForPackage(BBLL_PKG);
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
        } catch (Exception e) { }
    }

    // ====== state-list drawable ======
    private StateListDrawable newStateDrawable(int color, int radius) {
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setCornerRadius(radius);
        normal.setColor(color);

        GradientDrawable focused = new GradientDrawable();
        focused.setShape(GradientDrawable.RECTANGLE);
        focused.setCornerRadius(radius);
        focused.setColor(0x4400BFFF);
        focused.setStroke(dp(2), 0xFF00BFFF);

        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_focused}, focused);
        sld.addState(new int[]{}, normal);
        return sld;
    }

    // ====== UI ======
    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        mainGrid = new GridView(this);
        mainGrid.setNumColumns(COLS);
        mainGrid.setHorizontalSpacing(dp(8));
        mainGrid.setVerticalSpacing(dp(16));
        mainGrid.setPadding(gridPad, gridPad, gridPad, gridPad);
        mainGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        mainGrid.setColumnWidth(iconSize + tilePad * 2);
        mainGrid.setFocusable(true);
        mainGrid.setFocusableInTouchMode(true);

        // Visible focus selector on GridView
        GradientDrawable selNormal = new GradientDrawable();
        selNormal.setColor(Color.TRANSPARENT);
        GradientDrawable selFocus = new GradientDrawable();
        selFocus.setShape(GradientDrawable.RECTANGLE);
        selFocus.setCornerRadius(dp(8));
        selFocus.setStroke(dp(3), 0xFF00BFFF);
        selFocus.setColor(0x2200BFFF);
        StateListDrawable listSel = new StateListDrawable();
        listSel.addState(new int[]{android.R.attr.state_focused}, selFocus);
        listSel.addState(new int[]{}, selNormal);
        mainGrid.setSelector(listSel);
        mainGrid.setDrawSelectorOnTop(true);
        mainGrid.requestFocus();

        mainAdapter = new AppAdapter();
        mainGrid.setAdapter(mainAdapter);
        mainGrid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {}
            public void onNothingSelected(AdapterView<?> p) {}
        });

        // bottom bar
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

        addBtn("Hide", new Runnable() { public void run() { hideSelected(); } });
        addBtn("Uninstall", new Runnable() { public void run() { uninstallSelected(); } });
        addBtn("Restore", new Runnable() { public void run() { restoreAll(); } });

        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));

        root.addView(mainGrid, gridLp);
        root.addView(bottomBar, barLp);
        setContentView(root);
    }

    private void addBtn(String text, final Runnable action) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(6));
        g.setColor(0xFF444444);

        GradientDrawable gf = new GradientDrawable();
        gf.setShape(GradientDrawable.RECTANGLE);
        gf.setCornerRadius(dp(6));
        gf.setColor(0xFF00BFFF);
        gf.setStroke(dp(2), 0xFFFFFFFF);

        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, gf);
        bg.addState(new int[]{}, g);

        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(16), 0, dp(16), 0);
        btn.setBackground(bg);
        btn.setFocusable(true);
        btn.setClickable(true);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        lp.setMargins(dp(6), 0, dp(6), 0);
        btn.setLayoutParams(lp);
        bottomBar.addView(btn);
    }

    // ====== Key events ======
    private long dpadDownTime = 0;
    private static final long LONG_PRESS = 500;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            dpadDownTime = System.currentTimeMillis();
            return true; // consume DOWN so GridView doesn't act on it
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (System.currentTimeMillis() - dpadDownTime >= LONG_PRESS) {
                toggleBottomBar();
            } else {
                int pos = mainGrid.getSelectedItemPosition();
                if (pos >= 0) launchApp(pos);
            }
            dpadDownTime = 0;
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (bottomBar.getVisibility() == View.VISIBLE) {
                bottomBar.setVisibility(View.GONE);
                mainGrid.requestFocus();
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    private void launchApp(int pos) {
        AppInfo app = mainAdapter.getItem(pos);
        if (app != null && app.intent != null) {
            app.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(app.intent);
        }
    }

    // ====== Bottom bar ======
    private void toggleBottomBar() {
        if (bottomBar.getVisibility() == View.GONE) {
            AppInfo sel = (AppInfo) mainGrid.getSelectedItem();
            if (sel != null) {
                bottomIcon.setImageDrawable(sel.icon);
                bottomLabel.setText(sel.label);
            }
            bottomBar.setVisibility(View.VISIBLE);
            bottomBar.getChildAt(2).requestFocus(); // first button
        } else {
            bottomBar.setVisibility(View.GONE);
            mainGrid.requestFocus();
        }
    }

    private void hideSelected() {
        AppInfo sel = (AppInfo) mainGrid.getSelectedItem();
        if (sel != null) { hiddenPkgs.add(sel.pkg); saveHidden(); loadApps(); closeBar(); }
    }
    private void uninstallSelected() {
        AppInfo sel = (AppInfo) mainGrid.getSelectedItem();
        if (sel != null) {
            startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + sel.pkg)));
            closeBar();
        }
    }
    private void restoreAll() { hiddenPkgs.clear(); saveHidden(); loadApps(); closeBar(); }
    private void closeBar() { bottomBar.setVisibility(View.GONE); mainGrid.requestFocus(); }
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

        shownApps = new ArrayList<>();
        for (AppInfo a : allApps) {
            if (!hiddenPkgs.contains(a.pkg)) shownApps.add(a);
        }
        mainAdapter.notifyDataSetChanged();
    }

    // ====== Adapter ======
    static class AppInfo {
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
            if (convert == null) convert = newTile();
            AppInfo app = getItem(pos);
            ImageView icon = (ImageView) convert.findViewWithTag("I");
            TextView label = (TextView) convert.findViewWithTag("L");
            icon.setImageDrawable(app.icon);
            label.setText(app.label);
            return convert;
        }
    }

    private View newTile() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(8));
        bg.setColor(0x22FFFFFF);

        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(tilePad, tilePad, tilePad, tilePad);
        tile.setBackground(bg);
        tile.setFocusable(false);
        tile.setClickable(false);

        ImageView icon = new ImageView(this);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setTag("I");
        icon.setFocusable(false);
        tile.addView(icon);

        TextView label = new TextView(this);
        label.setTextSize(10);
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setTag("L");
        label.setFocusable(false);
        tile.addView(label);

        return tile;
    }

    private int dp(int px) {
        return (int)(px * getResources().getDisplayMetrics().density + 0.5f);
    }
}

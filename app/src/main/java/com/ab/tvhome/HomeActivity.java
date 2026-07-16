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
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeActivity extends Activity {
    private static final String BBLL_PKG = "com.V2.blb5";
    private static final String PREFS = "launcher_prefs";
    private static final String KEY_HIDDEN = "hidden_packages";
    private static final String KEY_ORDER = "app_order";
    private static final long BOOT_WINDOW = 120_000;
    private static final long LONG_PRESS = 500;
    private static final int COLS = 5;

    // === Colors ===
    private static final int BG_COLOR       = 0xFF1A1A2E;
    private static final int TILE_COLOR     = 0xFF2D2D44;
    private static final int TILE_EDIT      = 0xFF3D3D5C;
    private static final int FOCUS_BORDER   = 0xFFFF7043;
    private static final int FOCUS_BG       = 0x22FF7043;
    private static final int BAR_BG         = 0xEE1A1A2E;
    private static final int BTN_COLOR      = 0xFF3D3D5C;
    private static final int BTN_FOCUS_BG   = 0xFFFF7043;
    private static final int BTN_FOCUS_STROKE = 0xFFFFFFFF;
    private static final int TEXT_COLOR     = 0xFFCCCCCC;
    private static final int TEXT_SIZE      = 11;

    private GridView mainGrid;
    private AppAdapter mainAdapter;
    private LinearLayout bottomBar;
    private ImageView bottomIcon;
    private TextView bottomLabel;
    private PackageManager pm;
    private Set<String> hiddenPkgs;
    private Map<String, Integer> sortOrder; // pkg -> position
    private List<AppInfo> allApps, shownApps;

    // Edit mode
    private boolean editMode = false;
    private int editPos = 0;

    private int iconSize, gridPad, tilePad;
    private long dpadDownTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW) launchBBLL();
        pm = getPackageManager();
        SharedPreferences prefs = getSharedPreferences(PREFS, 0);
        hiddenPkgs = prefs.getStringSet(KEY_HIDDEN, new HashSet<>());
        sortOrder = loadOrder(prefs);
        iconSize = dp(80);
        gridPad = dp(24);
        tilePad = dp(8);
        buildUI();
        loadApps();
    }

    @Override protected void onResume() { super.onResume(); loadApps(); }

    private Map<String, Integer> loadOrder(SharedPreferences prefs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        String s = prefs.getString(KEY_ORDER, "");
        if (!s.isEmpty()) {
            String[] pkgs = s.split(",");
            for (int i = 0; i < pkgs.length; i++) {
                if (!pkgs[i].isEmpty()) map.put(pkgs[i], i);
            }
        }
        return map;
    }

    private void saveOrder() {
        StringBuilder sb = new StringBuilder();
        for (AppInfo a : shownApps) {
            if (sb.length() > 0) sb.append(",");
            sb.append(a.pkg);
        }
        getSharedPreferences(PREFS, 0).edit().putString(KEY_ORDER, sb.toString()).apply();
    }

    private void launchBBLL() {
        try {
            Intent i = pm.getLaunchIntentForPackage(BBLL_PKG);
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
        } catch (Exception e) { }
    }

    // ============== UI ==============
    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG_COLOR);
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

        GradientDrawable selN = new GradientDrawable(); selN.setColor(Color.TRANSPARENT);
        GradientDrawable selF = new GradientDrawable(); selF.setShape(GradientDrawable.RECTANGLE);
        selF.setCornerRadius(dp(8)); selF.setStroke(dp(3), FOCUS_BORDER); selF.setColor(FOCUS_BG);
        StateListDrawable listSel = new StateListDrawable();
        listSel.addState(new int[]{android.R.attr.state_focused}, selF);
        listSel.addState(new int[]{}, selN);
        mainGrid.setSelector(listSel);
        mainGrid.setDrawSelectorOnTop(true);
        mainGrid.requestFocus();

        mainAdapter = new AppAdapter();
        mainGrid.setAdapter(mainAdapter);

        // bottom bar
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(dp(16), dp(8), dp(16), dp(8));
        bottomBar.setBackgroundColor(BAR_BG);
        bottomBar.setVisibility(View.GONE);
        bottomBar.setFocusable(true);

        bottomIcon = new ImageView(this);
        bottomIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        bottomBar.addView(bottomIcon);

        bottomLabel = new TextView(this);
        bottomLabel.setTextColor(TEXT_COLOR);
        bottomLabel.setTextSize(14);
        bottomLabel.setPadding(dp(12), 0, dp(12), 0);
        bottomBar.addView(bottomLabel);

        addBtn("Hide", new Runnable() { public void run() { hideSelected(); } });
        addBtn("Uninstall", new Runnable() { public void run() { uninstallSelected(); } });
        addBtn("Move", new Runnable() { public void run() { enterEditMode(); } });
        addBtn("Restore", new Runnable() { public void run() { restoreAll(); } });

        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        root.addView(mainGrid, gridLp);
        root.addView(bottomBar, barLp);
        setContentView(root);
    }

    private void addBtn(String text, final Runnable action) {
        GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(6)); g.setColor(BTN_COLOR);
        GradientDrawable gf = new GradientDrawable(); gf.setShape(GradientDrawable.RECTANGLE);
        gf.setCornerRadius(dp(6)); gf.setColor(BTN_FOCUS_BG); gf.setStroke(dp(2), BTN_FOCUS_STROKE);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, gf);
        bg.addState(new int[]{}, g);

        TextView btn = new TextView(this);
        btn.setText(text); btn.setTextColor(TEXT_COLOR); btn.setTextSize(14);
        btn.setGravity(Gravity.CENTER); btn.setPadding(dp(16), 0, dp(16), 0);
        btn.setBackground(bg); btn.setFocusable(true); btn.setClickable(true);
        btn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { action.run(); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        lp.setMargins(dp(6), 0, dp(6), 0);
        btn.setLayoutParams(lp);
        bottomBar.addView(btn);
    }

    // ============== Key events ==============
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Edit mode: direction keys move the selected app
        if (editMode) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT)  { moveApp(-1); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { moveApp(1); return true; }
            if (keyCode == KeyEvent.KEYCODE_BACK) { exitEditMode(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                dpadDownTime = System.currentTimeMillis();
                return true;
            }
            return true; // block all other keys in edit mode
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            dpadDownTime = System.currentTimeMillis();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Edit mode: short press OK = confirm placement
        if (editMode) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                long held = System.currentTimeMillis() - dpadDownTime;
                dpadDownTime = 0;
                if (held < LONG_PRESS) exitEditMode();
                return true;
            }
            return true;
        }

        // Normal mode: short press = launch, long press = menu
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            boolean isLong = (System.currentTimeMillis() - dpadDownTime >= LONG_PRESS);
            dpadDownTime = 0;
            if (isLong) toggleBottomBar();
            else { int pos = mainGrid.getSelectedItemPosition(); if (pos >= 0) launchApp(pos); }
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

    // ============== Edit mode ==============
    private void enterEditMode() {
        editMode = true;
        editPos = mainGrid.getSelectedItemPosition();
        closeBar();
        mainAdapter.notifyDataSetChanged(); // re-render with edit style
        // Blink animation on the selected tile
        View sel = mainGrid.getSelectedView();
        if (sel != null) {
            AlphaAnimation blink = new AlphaAnimation(1.0f, 0.3f);
            blink.setDuration(300); blink.setRepeatMode(Animation.REVERSE); blink.setRepeatCount(Animation.INFINITE);
            sel.startAnimation(blink);
        }
    }

    private void exitEditMode() {
        editMode = false;
        // Clear all animations
        for (int i = 0; i < mainGrid.getChildCount(); i++) {
            mainGrid.getChildAt(i).clearAnimation();
        }
        saveOrder();
        mainGrid.requestFocus();
    }

    private void moveApp(int delta) {
        int newPos = editPos + delta;
        if (newPos < 0 || newPos >= shownApps.size()) return;
        AppInfo moving = shownApps.remove(editPos);
        shownApps.add(newPos, moving);
        editPos = newPos;
        mainAdapter.notifyDataSetChanged();
        mainGrid.setSelection(editPos);
        // Blink the new position
        View sel = mainGrid.getSelectedView();
        if (sel != null) {
            sel.clearAnimation();
            AlphaAnimation blink = new AlphaAnimation(1.0f, 0.3f);
            blink.setDuration(300); blink.setRepeatMode(Animation.REVERSE); blink.setRepeatCount(Animation.INFINITE);
            sel.startAnimation(blink);
        }
    }

    // ============== App launching ==============
    private void launchApp(int pos) {
        AppInfo app = shownApps.get(pos);
        if (app != null && app.intent != null) {
            app.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(app.intent);
        }
    }

    // ============== Bottom bar ==============
    private void toggleBottomBar() {
        if (editMode) return;
        if (bottomBar.getVisibility() == View.GONE) {
            AppInfo sel = shownApps.get(mainGrid.getSelectedItemPosition());
            if (sel != null) { bottomIcon.setImageDrawable(sel.icon); bottomLabel.setText(sel.label); }
            bottomBar.setVisibility(View.VISIBLE);
            bottomBar.getChildAt(2).requestFocus();
        } else { closeBar(); }
    }

    private void hideSelected() {
        AppInfo sel = shownApps.get(mainGrid.getSelectedItemPosition());
        if (sel != null) { hiddenPkgs.add(sel.pkg); saveHidden(); loadApps(); closeBar(); }
    }
    private void uninstallSelected() {
        AppInfo sel = shownApps.get(mainGrid.getSelectedItemPosition());
        if (sel != null) { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:"+sel.pkg))); closeBar(); }
    }
    private void restoreAll() { hiddenPkgs.clear(); saveHidden(); loadApps(); closeBar(); }
    private void closeBar() { bottomBar.setVisibility(View.GONE); mainGrid.requestFocus(); }
    private void saveHidden() {
        getSharedPreferences(PREFS,0).edit().putStringSet(KEY_HIDDEN,new HashSet<>(hiddenPkgs)).apply();
    }

    // ============== App loading ==============
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

        String orderRaw = getSharedPreferences(PREFS, 0).getString(KEY_ORDER, "");
        if (!orderRaw.isEmpty()) { // sort by saved order, new apps appended at end
            final LinkedHashMap<String, Integer> order = new LinkedHashMap<>();
            String[] pkgs = orderRaw.split(",");
            for (int i = 0; i < pkgs.length; i++) order.put(pkgs[i], i);
            Collections.sort(allApps, new Comparator<AppInfo>() {
                public int compare(AppInfo a, AppInfo b) {
                    int oa = order.containsKey(a.pkg) ? order.get(a.pkg) : Integer.MAX_VALUE;
                    int ob = order.containsKey(b.pkg) ? order.get(b.pkg) : Integer.MAX_VALUE;
                    if (oa != ob) return oa - ob;
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
        } else {
            Collections.sort(allApps, new Comparator<AppInfo>() {
                public int compare(AppInfo a, AppInfo b) { return a.label.compareToIgnoreCase(b.label); }
            });
        }

        shownApps = new ArrayList<>();
        for (AppInfo a : allApps) {
            if (!hiddenPkgs.contains(a.pkg)) shownApps.add(a);
        }
        mainAdapter.notifyDataSetChanged();
    }

    // ============== Adapter ==============
    static class AppInfo { String pkg, label; Drawable icon; Intent intent; }

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
            // Edit mode: dim non-selected tiles
            boolean isEditing = editMode && pos == editPos;
            label.setTextColor(isEditing ? 0xFFFFCC80 : TEXT_COLOR);
            GradientDrawable bg = (GradientDrawable) convert.getBackground();
            bg.setColor(isEditing ? TILE_EDIT : TILE_COLOR);
            return convert;
        }
    }

    private View newTile() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(8));
        bg.setColor(TILE_COLOR);

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
        icon.setTag("I"); icon.setFocusable(false);
        tile.addView(icon);

        TextView label = new TextView(this);
        label.setTextSize(TEXT_SIZE);
        label.setTextColor(TEXT_COLOR);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setTag("L"); label.setFocusable(false);
        tile.addView(label);

        return tile;
    }

    private int dp(int px) {
        return (int)(px * getResources().getDisplayMetrics().density + 0.5f);
    }
}

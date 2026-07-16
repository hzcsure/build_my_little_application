package com.ab.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
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
    private static final String PREFS = "launcher_prefs";
    private static final String KEY_HIDDEN = "hidden_packages";
    private static final String KEY_ORDER = "app_order";
    private static final String KEY_AUTOBOOT = "autoboot_pkg";
    private static final long BOOT_WINDOW = 120_000;
    private static final long LONG_PRESS = 500;
    private static final int COLS = 5;

    // Colors
    private static final int BG_COLOR       = 0xFF000000;
    private static final int TILE_COLOR     = 0xFF1F1F1F;
    private static final int TILE_EDIT      = 0xFF2A2A2A;
    private static final int FOCUS_BORDER   = 0xFF1A8FFF;
    private static final int BAR_BG         = 0xEE000000;
    private static final int BTN_COLOR      = 0xFF2A2A2A;
    private static final int BTN_FOCUS_BG   = 0xFF1A8FFF;
    private static final int BTN_FOCUS_STROKE = 0xFFFFFFFF;
    private static final int TEXT_COLOR     = 0xFFCCCCCC;
    private static final int TEXT_SIZE      = 12;

    private GridView mainGrid;
    private AppAdapter mainAdapter;
    private LinearLayout bottomBar, bootBar;
    private ImageView bottomIcon, bootIcon;
    private TextView bottomLabel, bootLabel;
    private PackageManager pm;
    private Set<String> hiddenPkgs;
    private Map<String, Integer> sortOrder;
    private List<AppInfo> allApps, shownApps;
    private String autobootPkg;

    private boolean editMode = false;
    private int editPos = 0;
    private int iconSize, gridPad, tilePad;
    private long dpadDownTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        SharedPreferences prefs = getSharedPreferences(PREFS, 0);
        hiddenPkgs = prefs.getStringSet(KEY_HIDDEN, new HashSet<>());
        sortOrder = loadOrder(prefs);
        autobootPkg = prefs.getString(KEY_AUTOBOOT, "");
        iconSize = dp(100); gridPad = dp(32); tilePad = dp(8);

        // Boot: launch configured auto-start app
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW && !autobootPkg.isEmpty()) {
            launchPkg(autobootPkg);
        }

        buildUI();
        loadApps();
    }

    @Override protected void onResume() { super.onResume(); loadApps(); }

    private Map<String, Integer> loadOrder(SharedPreferences prefs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        String s = prefs.getString(KEY_ORDER, "");
        if (!s.isEmpty()) for (String pkg : s.split(",")) if (!pkg.isEmpty()) map.put(pkg, map.size());
        return map;
    }

    private void saveOrder() {
        StringBuilder sb = new StringBuilder();
        for (AppInfo a : shownApps) { if (sb.length() > 0) sb.append(","); sb.append(a.pkg); }
        getSharedPreferences(PREFS, 0).edit().putString(KEY_ORDER, sb.toString()).apply();
    }

    private void launchPkg(String pkg) {
        try {
            Intent i = pm.getLaunchIntentForPackage(pkg);
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
        mainGrid.setHorizontalSpacing(dp(16));
        mainGrid.setVerticalSpacing(dp(20));
        mainGrid.setPadding(dp(20), dp(0), dp(20), dp(20));
        mainGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        mainGrid.setColumnWidth(iconSize + tilePad * 2);
        mainGrid.setFocusable(true);
        mainGrid.setFocusableInTouchMode(true);
        mainGrid.setSmoothScrollbarEnabled(false);

        GradientDrawable selN = new GradientDrawable(); selN.setShape(GradientDrawable.RECTANGLE);
        selN.setColor(Color.TRANSPARENT);
        GradientDrawable selF = new GradientDrawable(); selF.setShape(GradientDrawable.RECTANGLE);
        selF.setCornerRadius(dp(10)); selF.setColor(Color.TRANSPARENT);
        selF.setStroke(dp(3), FOCUS_BORDER);
        StateListDrawable listSel = new StateListDrawable();
        listSel.addState(new int[]{android.R.attr.state_focused}, selF);
        listSel.addState(new int[]{}, selN);
        mainGrid.setSelector(listSel);
        mainGrid.setDrawSelectorOnTop(true);
        mainGrid.requestFocus();

        mainAdapter = new AppAdapter();
        mainGrid.setAdapter(mainAdapter);

        mainGrid.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (editMode) {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT)  { moveApp(-1); return true; }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { moveApp(1); return true; }
                    if (keyCode == KeyEvent.KEYCODE_BACK) { exitEditMode(); return true; }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) { dpadDownTime = event.getDownTime(); return true; }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) { dpadDownTime = event.getDownTime(); return true; }
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        long held = event.getEventTime() - dpadDownTime;
                        dpadDownTime = 0;
                        if (held >= LONG_PRESS) toggleBottomBar();
                        else { int pos = mainGrid.getSelectedItemPosition(); if (pos >= 0) launchPkg(shownApps.get(pos).pkg); }
                        return true;
                    }
                }
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    if (bottomBar.getVisibility() == View.VISIBLE) { bottomBar.setVisibility(View.GONE); mainGrid.requestFocus(); return true; }
                }
                return false;
            }
        });

        // Bottom bar
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
        bottomLabel.setTextColor(TEXT_COLOR); bottomLabel.setTextSize(14);
        bottomLabel.setPadding(dp(12), 0, dp(12), 0);
        bottomBar.addView(bottomLabel);

        addBtn("隐藏", new Runnable() { public void run() { hideSelected(); } });
        addBtn("卸载", new Runnable() { public void run() { uninstallSelected(); } });
        addBtn("移动", new Runnable() { public void run() { enterEditMode(); } });
        addBtn("恢复", new Runnable() { public void run() { restoreAll(); } });
        addBtn("自启", new Runnable() { public void run() { setAutoboot(); } });

        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(370));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        root.addView(new View(this), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
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
        btn.setGravity(Gravity.CENTER); btn.setPadding(dp(14), 0, dp(14), 0);
        btn.setBackground(bg); btn.setFocusable(true); btn.setClickable(true);
        btn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { action.run(); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        lp.setMargins(dp(4), 0, dp(4), 0);
        btn.setLayoutParams(lp);
        bottomBar.addView(btn);
    }

    // ============== Edit mode ==============
    private void enterEditMode() {
        editMode = true; editPos = mainGrid.getSelectedItemPosition(); closeBar();
        mainAdapter.notifyDataSetChanged();
        View sel = mainGrid.getSelectedView();
        if (sel != null) {
            AlphaAnimation blink = new AlphaAnimation(1.0f, 0.3f);
            blink.setDuration(300); blink.setRepeatMode(Animation.REVERSE); blink.setRepeatCount(Animation.INFINITE);
            sel.startAnimation(blink);
        }
    }
    private void exitEditMode() {
        editMode = false;
        for (int i = 0; i < mainGrid.getChildCount(); i++) mainGrid.getChildAt(i).clearAnimation();
        saveOrder(); mainGrid.requestFocus();
    }
    private void moveApp(int delta) {
        int newPos = editPos + delta;
        if (newPos < 0 || newPos >= shownApps.size()) return;
        AppInfo moving = shownApps.remove(editPos);
        shownApps.add(newPos, moving);
        editPos = newPos;
        mainAdapter.notifyDataSetChanged();
        mainGrid.setSelection(editPos);
        View sel = mainGrid.getSelectedView();
        if (sel != null) { sel.clearAnimation(); sel.startAnimation(new AlphaAnimation(1.0f, 0.3f){{
            setDuration(300); setRepeatMode(Animation.REVERSE); setRepeatCount(Animation.INFINITE);
        }}); }
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
    private void hideSelected() { AppInfo s=shownApps.get(mainGrid.getSelectedItemPosition());
        if(s!=null){hiddenPkgs.add(s.pkg);saveHidden();loadApps();closeBar();}}
    private void uninstallSelected() { AppInfo s=shownApps.get(mainGrid.getSelectedItemPosition());
        if(s!=null){startActivity(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+s.pkg)));closeBar();}}
    private void restoreAll() { hiddenPkgs.clear(); saveHidden(); loadApps(); closeBar(); }
    private void setAutoboot() {
        AppInfo s = shownApps.get(mainGrid.getSelectedItemPosition());
        if (s == null) return;
        if (autobootPkg.equals(s.pkg)) {
            autobootPkg = ""; // clear
        } else {
            autobootPkg = s.pkg;
        }
        getSharedPreferences(PREFS,0).edit().putString(KEY_AUTOBOOT, autobootPkg).apply();
        closeBar();
    }
    private void closeBar() { bottomBar.setVisibility(View.GONE); mainGrid.requestFocus(); }
    private void saveHidden() { getSharedPreferences(PREFS,0).edit().putStringSet(KEY_HIDDEN,new HashSet<>(hiddenPkgs)).apply(); }

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
        String orderRaw = getSharedPreferences(PREFS,0).getString(KEY_ORDER,"");
        if (!orderRaw.isEmpty()) {
            final LinkedHashMap<String,Integer> o = new LinkedHashMap<>();
            for (String p : orderRaw.split(",")) if(!p.isEmpty()) o.put(p, o.size());
            Collections.sort(allApps, new Comparator<AppInfo>() {
                public int compare(AppInfo a, AppInfo b) {
                    int oa=o.containsKey(a.pkg)?o.get(a.pkg):Integer.MAX_VALUE;
                    int ob=o.containsKey(b.pkg)?o.get(b.pkg):Integer.MAX_VALUE;
                    return oa!=ob?oa-ob:a.label.compareToIgnoreCase(b.label);
                }
            });
        } else {
            Collections.sort(allApps, new Comparator<AppInfo>() { 
                public int compare(AppInfo a, AppInfo b) { return a.label.compareToIgnoreCase(b.label); }
            });
        }
        shownApps = new ArrayList<>();
        for (AppInfo a : allApps) if (!hiddenPkgs.contains(a.pkg)) shownApps.add(a);
        mainAdapter.notifyDataSetChanged();
    }

    // ============== Adapter ==============
    static class AppInfo { String pkg, label; Drawable icon; Intent intent; }
    
    class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return shownApps!=null?shownApps.size():0; }
        @Override public AppInfo getItem(int pos) { return shownApps.get(pos); }
        @Override public long getItemId(int pos) { return pos; }
        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            if (convert == null) convert = newTile();
            AppInfo app = getItem(pos);
            ImageView icon = (ImageView) convert.findViewWithTag("I");
            TextView label = (TextView) convert.findViewWithTag("L");
            icon.setImageDrawable(app.icon);
            // Show auto-boot indicator
            boolean isAutoboot = app.pkg.equals(autobootPkg);
            label.setText(isAutoboot ? "▶ " + app.label : app.label);
            label.setTextColor(isAutoboot ? 0xFF80D8FF : TEXT_COLOR);
            boolean isEditing = editMode && pos == editPos;
            if (isEditing) label.setTextColor(0xFFFFCC80);
            GradientDrawable bg = (GradientDrawable) convert.getBackground();
            bg.setColor(isEditing ? TILE_EDIT : TILE_COLOR);
            return convert;
        }
    }

    private View newTile() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE); bg.setCornerRadius(dp(10)); bg.setColor(TILE_COLOR);
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(4), dp(8), dp(4), dp(10));
        tile.setBackground(bg); tile.setFocusable(false); tile.setClickable(false);
        ImageView icon = new ImageView(this);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize+dp(16), iconSize+dp(16)));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER); icon.setTag("I"); icon.setFocusable(false);
        tile.addView(icon);
        TextView label = new TextView(this);
        label.setTextSize(TEXT_SIZE); label.setTextColor(TEXT_COLOR);
        label.setGravity(Gravity.CENTER); label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setTag("L"); label.setFocusable(false); label.setPadding(0, dp(6), 0, 0);
        tile.addView(label);
        return tile;
    }

    private int dp(int px) { return (int)(px * getResources().getDisplayMetrics().density + 0.5f); }
}

package com.ab.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
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
import java.util.List;

public class HomeActivity extends Activity {
    private static final String BBLL_PKG = "com.V2.blb5";
    private static final long BOOT_WINDOW = 120_000; // 2 min
    private static final int GRID_COLS = 5;
    private static final int ICON_SIZE = 80; // dp

    private AppAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Cold boot: launch BBLL first
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW) {
            launchBBLL();
        }

        // Build launcher UI
        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh app list (in case apps installed/uninstalled)
        if (adapter != null) adapter.reload();
    }

    private void launchBBLL() {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(BBLL_PKG);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        } catch (Exception ignored) {}
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF222222); // dark background for TV

        GridView grid = new GridView(this);
        grid.setNumColumns(GRID_COLS);
        grid.setHorizontalSpacing(dp(8));
        grid.setVerticalSpacing(dp(16));
        grid.setPadding(dp(24), dp(24), dp(24), dp(24));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setColumnWidth(dp(ICON_SIZE + 16));
        grid.setVerticalScrollBarEnabled(false);

        adapter = new AppAdapter();
        grid.setAdapter(adapter);

        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                AppInfo app = adapter.getItem(pos);
                if (app != null && app.intent != null) {
                    app.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(app.intent);
                }
            }
        });

        root.addView(grid);
        setContentView(root);
    }

    private int dp(int px) {
        return (int) (px * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ---- App data ----
    static class AppInfo {
        String label;
        Drawable icon;
        Intent intent;
    }

    class AppAdapter extends BaseAdapter {
        private List<AppInfo> apps = new ArrayList<>();

        AppAdapter() { reload(); }

        void reload() {
            apps.clear();
            PackageManager pm = getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN, null);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolved = pm.queryIntentActivities(main, 0);

            for (ResolveInfo ri : resolved) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue; // skip self
                AppInfo info = new AppInfo();
                info.label = ri.loadLabel(pm).toString();
                info.icon = ri.loadIcon(pm);
                Intent launch = new Intent(Intent.ACTION_MAIN);
                launch.setClassName(pkg, ri.activityInfo.name);
                launch.addCategory(Intent.CATEGORY_LAUNCHER);
                info.intent = launch;
                apps.add(info);
            }
            Collections.sort(apps, new Comparator<AppInfo>() {
                public int compare(AppInfo a, AppInfo b) {
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
            notifyDataSetChanged();
        }

        @Override public int getCount() { return apps.size(); }
        @Override public AppInfo getItem(int pos) { return apps.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            if (convert == null) {
                LinearLayout tile = new LinearLayout(HomeActivity.this);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(android.view.Gravity.CENTER);
                tile.setPadding(dp(4), dp(4), dp(4), dp(4));

                ImageView icon = new ImageView(HomeActivity.this);
                icon.setLayoutParams(new LinearLayout.LayoutParams(dp(ICON_SIZE), dp(ICON_SIZE)));
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                icon.setTag("icon");
                tile.addView(icon);

                TextView label = new TextView(HomeActivity.this);
                label.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                label.setTextSize(10);
                label.setTextColor(0xFFFFFFFF);
                label.setGravity(android.view.Gravity.CENTER);
                label.setMaxLines(2);
                label.setTag("label");
                tile.addView(label);

                convert = tile;
            }

            AppInfo app = getItem(pos);
            ImageView icon = (ImageView) convert.findViewWithTag("icon");
            TextView label = (TextView) convert.findViewWithTag("label");
            icon.setImageDrawable(app.icon);
            label.setText(app.label);

            return convert;
        }
    }
}

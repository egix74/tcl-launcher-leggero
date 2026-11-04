package it.leggero.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Launcher minimale per Android TV: una sola griglia di icone, nessun servizio,
 * nessuna libreria esterna, nessun accesso alla rete.
 *
 * OK sul telecomando    -> avvia l'app
 * MENU / INFO / tasto * -> apre le informazioni dell'app (disinstalla, cancella cache)
 * Indietro              -> ignorato (siamo la schermata Home)
 */
public class MainActivity extends Activity {

    private static final class AppEntry {
        final String label;
        final String pkg;
        final Intent launch;
        final ResolveInfo info;
        Drawable icon;

        AppEntry(String label, String pkg, Intent launch, ResolveInfo info) {
            this.label = label;
            this.pkg = pkg;
            this.launch = launch;
            this.info = info;
        }
    }

    private final List<AppEntry> apps = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private PackageManager pm;
    private GridView grid;
    private AppAdapter adapter;
    private int iconSize;
    private boolean loading;
    private boolean reloadPending;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reload();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        iconSize = dp(72);

        grid = new GridView(this);
        grid.setBackgroundColor(Color.BLACK);
        grid.setColumnWidth(dp(170));
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(8));
        grid.setVerticalSpacing(dp(8));
        grid.setPadding(dp(48), dp(40), dp(48), dp(40)); // margini di overscan
        grid.setClipToPadding(false);
        grid.setSelector(new ColorDrawable(0x55FFFFFF));
        grid.setSmoothScrollbarEnabled(false);
        grid.setVerticalScrollBarEnabled(false);

        adapter = new AppAdapter();
        grid.setAdapter(adapter);

        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                launch(apps.get(position));
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                openAppInfo(apps.get(position));
                return true;
            }
        });

        setContentView(grid);
        grid.requestFocus();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);

        reload();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home premuto mentre siamo gia' aperti: torna in cima alla griglia
        if (grid != null && apps.size() > 0) {
            grid.setSelection(0);
        }
    }

    @Override
    public void onBackPressed() {
        // Un launcher non deve chiudersi con Indietro
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_INFO
                || keyCode == KeyEvent.KEYCODE_STAR) {
            int pos = grid.getSelectedItemPosition();
            if (pos >= 0 && pos < apps.size()) {
                openAppInfo(apps.get(pos));
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void launch(AppEntry e) {
        try {
            startActivity(e.launch);
        } catch (Exception ex) {
            Toast.makeText(this, "Impossibile avviare " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppInfo(AppEntry e) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + e.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(this, "Informazioni app non disponibili", Toast.LENGTH_SHORT).show();
        }
    }

    /** Ricarica l'elenco in un thread separato per non bloccare l'interfaccia. */
    private void reload() {
        if (loading) {
            reloadPending = true;
            return;
        }
        loading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppEntry> result = queryApps();
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        apps.clear();
                        apps.addAll(result);
                        adapter.notifyDataSetChanged();
                        loading = false;
                        if (reloadPending) {
                            reloadPending = false;
                            reload();
                        }
                    }
                });
            }
        }).start();
    }

    private List<AppEntry> queryApps() {
        List<AppEntry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(getPackageName()); // escludi il launcher stesso

        // Prima le app per TV, poi quelle "normali" non ancora viste
        addApps(result, seen, Intent.CATEGORY_LEANBACK_LAUNCHER);
        addApps(result, seen, Intent.CATEGORY_LAUNCHER);

        Collections.sort(result, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label);
            }
        });
        return result;
    }

    private void addApps(List<AppEntry> out, Set<String> seen, String category) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> list = pm.queryIntentActivities(query, 0);
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (!seen.add(pkg)) continue;
            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setClassName(pkg, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            out.add(new AppEntry(String.valueOf(ri.loadLabel(pm)), pkg, launch, ri));
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView t = (TextView) convertView;
            if (t == null) {
                t = new TextView(MainActivity.this);
                t.setGravity(Gravity.CENTER);
                t.setTextColor(Color.WHITE);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setCompoundDrawablePadding(dp(8));
                t.setPadding(dp(8), dp(12), dp(8), dp(12));
                t.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(130)));
            }
            AppEntry e = apps.get(position);
            if (e.icon == null) {
                e.icon = e.info.loadIcon(pm); // caricata solo quando serve
                e.icon.setBounds(0, 0, iconSize, iconSize);
            }
            t.setText(e.label);
            t.setCompoundDrawables(null, e.icon, null, null);
            return t;
        }
    }
}

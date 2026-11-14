package com.andrey.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class AndreyWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_PREV = "com.andrey.widget.ACTION_PREV";
    public static final String ACTION_NEXT = "com.andrey.widget.ACTION_NEXT";
    public static final String ACTION_REFRESH = "com.andrey.widget.ACTION_REFRESH";
    public static final String ACTION_CLICK_1 = "com.andrey.widget.ACTION_CLICK_1";
    public static final String ACTION_CLICK_2 = "com.andrey.widget.ACTION_CLICK_2";
    public static final String ACTION_CLICK_3 = "com.andrey.widget.ACTION_CLICK_3";
    public static final String ACTION_CLICK_4 = "com.andrey.widget.ACTION_CLICK_4";
    public static final String ACTION_UPDATE_LIST = "com.andrey.widget.UPDATE_LIST";

    // Bento Grid 2x2: 4 items por página para tableta
    private static final int ITEMS_PER_PAGE = 4;
    private static final long COOLDOWN_MS = 1000;
    private static long lastActionTime = 0;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        requestShortcutsFromTermux(context);
        for (int id : appWidgetIds) {
            updateWidget(context, appWidgetManager, id);
        }
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        requestShortcutsFromTermux(context);
    }

    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE);
        List<String> shortcuts = getShortcuts(prefs);
        int totalItems = shortcuts.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));

        int page = prefs.getInt("page_" + appWidgetId, 0);
        if (page >= totalPages) {
            page = 0;
            prefs.edit().putInt("page_" + appWidgetId, page).apply();
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        views.setTextViewText(R.id.txt_page_indicator, (page + 1) + "/" + totalPages);
        views.setTextViewText(R.id.txt_category, "⚡ " + totalItems + " SCRIPTS EN ~/.shortcuts/");

        // Telemetría Server Matrix (Concepto 4)
        int p8081 = prefs.getInt("port_8081", 0);
        int p3000 = prefs.getInt("port_3000", 0);

        if (p8081 == 1) {
            views.setTextViewText(R.id.chip_nginx, "● Nginx :8081");
            views.setTextColor(R.id.chip_nginx, 0xFF3FB950);
        } else {
            views.setTextViewText(R.id.chip_nginx, "○ Nginx :8081");
            views.setTextColor(R.id.chip_nginx, 0xFF70798C);
        }

        if (p3000 == 1) {
            views.setTextViewText(R.id.chip_node, "● Node :3000");
            views.setTextColor(R.id.chip_node, 0xFF3FB950);
        } else {
            views.setTextViewText(R.id.chip_node, "○ Node :3000");
            views.setTextColor(R.id.chip_node, 0xFF70798C);
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        // Flecha Anterior
        Intent prevIntent = new Intent(context, AndreyWidgetProvider.class);
        prevIntent.setAction(ACTION_PREV);
        prevIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickPendingIntent(R.id.btn_prev, PendingIntent.getBroadcast(context, appWidgetId * 10 + 1, prevIntent, flags));

        // Flecha Siguiente
        Intent nextIntent = new Intent(context, AndreyWidgetProvider.class);
        nextIntent.setAction(ACTION_NEXT);
        nextIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickPendingIntent(R.id.btn_next, PendingIntent.getBroadcast(context, appWidgetId * 10 + 2, nextIntent, flags));

        // Botón Refrescar Dedicado (🔄)
        Intent refreshIntent = new Intent(context, AndreyWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent refreshPending = PendingIntent.getBroadcast(context, appWidgetId * 10 + 5, refreshIntent, flags);
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPending);
        views.setOnClickPendingIntent(R.id.txt_title, refreshPending);
        views.setOnClickPendingIntent(R.id.status_matrix, refreshPending);

        if (totalItems == 0) {
            views.setViewVisibility(R.id.layout_empty_state, View.VISIBLE);
            views.setViewVisibility(R.id.grid_container, View.GONE);
            views.setOnClickPendingIntent(R.id.btn_action_empty, refreshPending);
        } else {
            views.setViewVisibility(R.id.layout_empty_state, View.GONE);
            views.setViewVisibility(R.id.grid_container, View.VISIBLE);

            int base = page * ITEMS_PER_PAGE;
            int idx1 = base;
            int idx2 = base + 1;
            int idx3 = base + 2;
            int idx4 = base + 3;

            // Configurar Card 1
            setupCard(context, views, appWidgetId, R.id.card_1, R.id.card_icon_1, R.id.card_title_1, R.id.card_sub_1,
                    ACTION_CLICK_1, 1, (idx1 < totalItems ? shortcuts.get(idx1) : null), flags);

            // Configurar Card 2
            setupCard(context, views, appWidgetId, R.id.card_2, R.id.card_icon_2, R.id.card_title_2, R.id.card_sub_2,
                    ACTION_CLICK_2, 2, (idx2 < totalItems ? shortcuts.get(idx2) : null), flags);

            // Row 2: si no hay ni idx3 ni idx4, se oculta para aprovechar el espacio
            if (idx3 >= totalItems && idx4 >= totalItems) {
                views.setViewVisibility(R.id.row_2, View.GONE);
            } else {
                views.setViewVisibility(R.id.row_2, View.VISIBLE);
                setupCard(context, views, appWidgetId, R.id.card_3, R.id.card_icon_3, R.id.card_title_3, R.id.card_sub_3,
                        ACTION_CLICK_3, 3, (idx3 < totalItems ? shortcuts.get(idx3) : null), flags);

                setupCard(context, views, appWidgetId, R.id.card_4, R.id.card_icon_4, R.id.card_title_4, R.id.card_sub_4,
                        ACTION_CLICK_4, 4, (idx4 < totalItems ? shortcuts.get(idx4) : null), flags);
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static void setupCard(Context context, RemoteViews views, int appWidgetId,
                                  int cardId, int iconId, int titleId, int subId,
                                  String action, int clickCode, String item, int flags) {
        if (item == null) {
            views.setViewVisibility(cardId, View.INVISIBLE);
            return;
        }

        views.setViewVisibility(cardId, View.VISIBLE);
        String clean = item.replace(".sh", "").replace("_", " ").trim();
        String lower = clean.toLowerCase();

        String icon = "⚡";
        String subtitle = "Acción rápida";
        int bgRes = R.drawable.btn_card_bg;
        int titleColor = 0xFFEEF1F8;
        int subColor = 0xFF8FA0FF;

        if (lower.contains("encender") || lower.contains("start") || lower.contains("iniciar")) {
            icon = "🚀";
            subtitle = "Servicio activo";
            bgRes = R.drawable.btn_card_start_bg;
            titleColor = 0xFFFFFFFF;
            subColor = 0xFF3FB950;
        } else if (lower.contains("apagar") || lower.contains("stop") || lower.contains("detener")) {
            icon = "🛑";
            subtitle = "Terminar proceso";
            bgRes = R.drawable.btn_card_stop_bg;
            titleColor = 0xFFFFB4AB;
            subColor = 0xFFFF5F56;
        } else if (lower.contains("reiniciar") || lower.contains("restart") || lower.contains("actualizar") || lower.contains("update")) {
            icon = "🔄";
            subtitle = "Recargar";
            bgRes = R.drawable.btn_card_bg;
            titleColor = 0xFFD8E2FF;
            subColor = 0xFF8FA0FF;
        } else if (lower.contains("opencode") || lower.contains("code") || lower.contains("editor")) {
            icon = "💻";
            subtitle = "IDE & Editor";
            bgRes = R.drawable.btn_card_bg;
            titleColor = 0xFFFFFFFF;
            subColor = 0xFF8FA0FF;
        } else if (lower.contains("ubuntu") || lower.contains("linux") || lower.contains("proot")) {
            icon = "🐧";
            subtitle = "PRoot Linux";
            bgRes = R.drawable.btn_card_bg;
            titleColor = 0xFFFFFFFF;
            subColor = 0xFFFFAA44;
        } else if (lower.contains("termux") || lower.contains("bash")) {
            icon = "📟";
            subtitle = "Script Bash";
            bgRes = R.drawable.btn_card_bg;
            titleColor = 0xFFFFFFFF;
            subColor = 0xFF9AA5BD;
        } else if (lower.contains("python") || lower.contains("py")) {
            icon = "🐍";
            subtitle = "Python Engine";
            bgRes = R.drawable.btn_card_bg;
            titleColor = 0xFFFFFFFF;
            subColor = 0xFFFFD43B;
        } else if (lower.contains("nginx") || lower.contains("web") || lower.contains("portal") || lower.contains("portafolio")) {
            icon = "🌐";
            subtitle = "Web :8081";
            bgRes = R.drawable.btn_card_bg;
            titleColor = 0xFFFFFFFF;
            subColor = 0xFF3FB950;
        }

        views.setTextViewText(iconId, icon);
        views.setTextViewText(titleId, clean);
        views.setTextColor(titleId, titleColor);
        views.setTextViewText(subId, subtitle);
        views.setTextColor(subId, subColor);
        views.setInt(cardId, "setBackgroundResource", bgRes);

        Intent clickIntent = new Intent(context, AndreyWidgetProvider.class);
        clickIntent.setAction(action);
        clickIntent.putExtra("item", item);
        clickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent pi = PendingIntent.getBroadcast(context, appWidgetId * 10 + clickCode, clickIntent, flags);
        views.setOnClickPendingIntent(cardId, pi);
    }

    private static List<String> getShortcuts(SharedPreferences prefs) {
        List<String> list = new ArrayList<>();
        String json = prefs.getString("shortcuts_json", null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    String item = arr.getString(i).trim();
                    if (!item.isEmpty() && !item.startsWith(".") && !item.endsWith("~")) {
                        list.add(item);
                    }
                }
            } catch (JSONException ignored) {}
        }
        return list;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        SharedPreferences prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE);

        if (ACTION_PREV.equals(action) && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            requestShortcutsFromTermux(context);
            List<String> shortcuts = getShortcuts(prefs);
            int totalPages = Math.max(1, (int) Math.ceil((double) shortcuts.size() / ITEMS_PER_PAGE));
            int page = prefs.getInt("page_" + appWidgetId, 0);
            page = (page - 1 + totalPages) % totalPages;
            prefs.edit().putInt("page_" + appWidgetId, page).apply();
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            updateWidget(context, mgr, appWidgetId);
        } else if (ACTION_NEXT.equals(action) && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            requestShortcutsFromTermux(context);
            List<String> shortcuts = getShortcuts(prefs);
            int totalPages = Math.max(1, (int) Math.ceil((double) shortcuts.size() / ITEMS_PER_PAGE));
            int page = prefs.getInt("page_" + appWidgetId, 0);
            page = (page + 1) % totalPages;
            prefs.edit().putInt("page_" + appWidgetId, page).apply();
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            updateWidget(context, mgr, appWidgetId);
        } else if (ACTION_REFRESH.equals(action)) {
            Toast.makeText(context, "🔄 Comprobando servidores y shortcuts...", Toast.LENGTH_SHORT).show();
            requestShortcutsFromTermux(context);
        } else if (ACTION_UPDATE_LIST.equals(action)) {
            String listStr = intent.getStringExtra("shortcuts");
            int p3000 = intent.getIntExtra("port3000", 0);
            int p8081 = intent.getIntExtra("port8081", 0);
            prefs.edit()
                .putInt("port_3000", p3000)
                .putInt("port_8081", p8081)
                .apply();

            if (listStr != null) {
                String[] lines = listStr.split("\n");
                JSONArray arr = new JSONArray();
                for (String l : lines) {
                    String trim = l.trim();
                    if (!trim.isEmpty() && !trim.endsWith("~") && !trim.startsWith(".")) {
                        arr.put(trim);
                    }
                }
                prefs.edit().putString("shortcuts_json", arr.toString()).apply();
                refreshAllWidgets(context);
                if (arr.length() > 0) {
                    Toast.makeText(context, "✔ " + arr.length() + " shortcuts actualizados", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "ℹ️ No se encontraron archivos en ~/.shortcuts/", Toast.LENGTH_SHORT).show();
                }
            } else {
                refreshAllWidgets(context);
            }
        } else if (ACTION_CLICK_1.equals(action) || ACTION_CLICK_2.equals(action)
                || ACTION_CLICK_3.equals(action) || ACTION_CLICK_4.equals(action)) {
            String item = intent.getStringExtra("item");
            if (item != null) {
                handleItemClick(context, item);
            }
        }
    }

    private static void handleItemClick(Context context, String item) {
        long now = System.currentTimeMillis();
        if (now - lastActionTime < COOLDOWN_MS) {
            return;
        }
        lastActionTime = now;

        copyToClipboard(context, "bash \"$HOME/.shortcuts/" + item + "\"");
        Toast.makeText(context, "⚡ " + item, Toast.LENGTH_SHORT).show();

        // Ejecutar shortcut en Termux
        String cmd = "f=\"$HOME/.shortcuts/" + item + "\"; [ -f \"$f\" ] && bash \"$f\"";
        runInTermuxCommand(context, cmd);

        // Actualizar lista y estado de servidores
        requestShortcutsFromTermux(context);
    }

    private static void refreshAllWidgets(Context context) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            ComponentName cn = new ComponentName(context, AndreyWidgetProvider.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            for (int id : ids) {
                updateWidget(context, mgr, id);
            }
        } catch (Exception ignored) {}
    }

    private static void copyToClipboard(Context context, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip = ClipData.newPlainText("Termux Command", text);
                cm.setPrimaryClip(clip);
            }
        } catch (Exception ignored) {}
    }

    public static void requestShortcutsFromTermux(Context context) {
        // Escaneo de shortcuts y ping a puertos locales con bash puro
        String cmd = "list=$(cd \"$HOME/.shortcuts\" 2>/dev/null && find . -maxdepth 2 -type f ! -name \".*\" ! -name \"*~\" | sed 's|^\\./||' | sort); "
                   + "p3000=0; (echo > /dev/tcp/127.0.0.1/3000) 2>/dev/null && p3000=1; "
                   + "p8081=0; (echo > /dev/tcp/127.0.0.1/8081) 2>/dev/null && p8081=1; "
                   + "am broadcast -p com.andrey.widget -n com.andrey.widget/.AndreyWidgetProvider -a com.andrey.widget.UPDATE_LIST --es shortcuts \"$list\" --ei port3000 $p3000 --ei port8081 $p8081 >/dev/null 2>&1";
        runInTermuxCommand(context, cmd);
    }

    private static void runInTermuxCommand(Context context, String command) {
        try {
            Intent intent = new Intent();
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.setAction("com.termux.RUN_COMMAND");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", command});
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}

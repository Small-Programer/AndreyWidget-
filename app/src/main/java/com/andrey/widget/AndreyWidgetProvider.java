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
    public static final String ACTION_UPDATE_LIST = "com.andrey.widget.UPDATE_LIST";

    private static final int ITEMS_PER_PAGE = 2;
    private static final long COOLDOWN_MS = 1200;
    private static long lastActionTime = 0;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        requestShortcutsFromTermux(context);
        for (int id : appWidgetIds) {
            updateWidget(context, appWidgetManager, id);
        }
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
        views.setTextViewText(R.id.txt_category, "TERMUX SHORTCUTS (" + totalItems + ")");

        // Ocultar badge de status secundario para un diseño limpio y directo
        views.setViewVisibility(R.id.txt_status, View.GONE);

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

        // Botón Refrescar / Título
        Intent refreshIntent = new Intent(context, AndreyWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickPendingIntent(R.id.txt_title, PendingIntent.getBroadcast(context, appWidgetId * 10 + 5, refreshIntent, flags));

        // Items para la página actual
        int index1 = page * ITEMS_PER_PAGE;
        int index2 = index1 + 1;

        if (totalItems == 0) {
            // Estado vacío / inicial: invita a sincronizar
            views.setTextViewText(R.id.btn_action_1, "🔄 Sincronizar con Termux...");
            views.setInt(R.id.btn_action_1, "setBackgroundResource", R.drawable.btn_start_bg);
            views.setTextColor(R.id.btn_action_1, 0xFFFFFFFF);
            views.setViewVisibility(R.id.btn_action_1, View.VISIBLE);
            views.setOnClickPendingIntent(R.id.btn_action_1, PendingIntent.getBroadcast(context, appWidgetId * 10 + 5, refreshIntent, flags));
            views.setViewVisibility(R.id.btn_action_2, View.GONE);
        } else {
            if (index1 < totalItems) {
                String item1 = shortcuts.get(index1);
                setupButton(views, R.id.btn_action_1, item1);
                views.setViewVisibility(R.id.btn_action_1, View.VISIBLE);

                Intent click1 = new Intent(context, AndreyWidgetProvider.class);
                click1.setAction(ACTION_CLICK_1);
                click1.putExtra("item", item1);
                click1.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                views.setOnClickPendingIntent(R.id.btn_action_1, PendingIntent.getBroadcast(context, appWidgetId * 10 + 3, click1, flags));
            } else {
                views.setViewVisibility(R.id.btn_action_1, View.GONE);
            }

            if (index2 < totalItems) {
                String item2 = shortcuts.get(index2);
                setupButton(views, R.id.btn_action_2, item2);
                views.setViewVisibility(R.id.btn_action_2, View.VISIBLE);

                Intent click2 = new Intent(context, AndreyWidgetProvider.class);
                click2.setAction(ACTION_CLICK_2);
                click2.putExtra("item", item2);
                click2.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                views.setOnClickPendingIntent(R.id.btn_action_2, PendingIntent.getBroadcast(context, appWidgetId * 10 + 4, click2, flags));
            } else {
                views.setViewVisibility(R.id.btn_action_2, View.GONE);
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static void setupButton(RemoteViews views, int btnId, String item) {
        String clean = item.replace(".sh", "").replace("_", " ").trim();
        String lower = item.toLowerCase();

        if (lower.contains("encender") || lower.contains("start") || lower.contains("iniciar")) {
            views.setTextViewText(btnId, "🚀 " + clean);
            views.setInt(btnId, "setBackgroundResource", R.drawable.btn_start_bg);
            views.setTextColor(btnId, 0xFFFFFFFF);
        } else if (lower.contains("apagar") || lower.contains("stop") || lower.contains("detener")) {
            views.setTextViewText(btnId, "🛑 " + clean);
            views.setInt(btnId, "setBackgroundResource", R.drawable.btn_stop_bg);
            views.setTextColor(btnId, 0xFFFF5F56);
        } else if (lower.contains("reiniciar") || lower.contains("restart") || lower.contains("actualizar") || lower.contains("update")) {
            views.setTextViewText(btnId, "🔄 " + clean);
            views.setInt(btnId, "setBackgroundResource", R.drawable.btn_start_bg);
            views.setTextColor(btnId, 0xFF8FA0FF);
        } else if (lower.contains("opencode") || lower.contains("code") || lower.contains("editor")) {
            views.setTextViewText(btnId, "💻 " + clean);
            views.setInt(btnId, "setBackgroundResource", R.drawable.btn_neutral_bg);
            views.setTextColor(btnId, 0xFF8FA0FF);
        } else if (lower.contains("ubuntu") || lower.contains("linux")) {
            views.setTextViewText(btnId, "🐧 " + clean);
            views.setInt(btnId, "setBackgroundResource", R.drawable.btn_neutral_bg);
            views.setTextColor(btnId, 0xFFEEF1F8);
        } else if (lower.contains("termux") || lower.contains("bash") || lower.contains("sh")) {
            views.setTextViewText(btnId, "📟 " + clean);
            views.setInt(btnId, "setBackgroundResource", R.drawable.btn_neutral_bg);
            views.setTextColor(btnId, 0xFFEEF1F8);
        } else if (lower.contains("python") || lower.contains("py")) {
            views.setTextViewText(btnId, "🐍 " + clean);
            views.setInt(btnId, "setBackgroundResource", R.drawable.btn_neutral_bg);
            views.setTextColor(btnId, 0xFFEEF1F8);
        } else {
            views.setTextViewText(btnId, "⚡ " + clean);
            views.setInt(btnId, "setBackgroundResource", R.drawable.btn_neutral_bg);
            views.setTextColor(btnId, 0xFFEEF1F8);
        }
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
            Toast.makeText(context, "🔄 Sincronizando con Termux:Widget...", Toast.LENGTH_SHORT).show();
            requestShortcutsFromTermux(context);
        } else if (ACTION_UPDATE_LIST.equals(action)) {
            String listStr = intent.getStringExtra("shortcuts");
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
            }
        } else if (ACTION_CLICK_1.equals(action) || ACTION_CLICK_2.equals(action)) {
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

        // Solicitar refresco automático
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
        // Lista todos los archivos ejecutables de ~/.shortcuts/ y los envía al widget
        String cmd = "list=$(cd \"$HOME/.shortcuts\" 2>/dev/null && ls -1 2>/dev/null); am broadcast -a com.andrey.widget.UPDATE_LIST --es shortcuts \"$list\" >/dev/null 2>&1";
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

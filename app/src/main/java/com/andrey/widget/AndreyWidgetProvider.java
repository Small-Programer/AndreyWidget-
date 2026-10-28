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
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AndreyWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_PREV = "com.andrey.widget.ACTION_PREV";
    public static final String ACTION_NEXT = "com.andrey.widget.ACTION_NEXT";
    public static final String ACTION_REFRESH = "com.andrey.widget.ACTION_REFRESH";
    public static final String ACTION_CLICK_1 = "com.andrey.widget.ACTION_CLICK_1";
    public static final String ACTION_CLICK_2 = "com.andrey.widget.ACTION_CLICK_2";
    public static final String ACTION_UPDATE_LIST = "com.andrey.widget.UPDATE_LIST";

    private static final int ITEMS_PER_PAGE = 2;
    private static final long COOLDOWN_MS = 2000;
    private static long lastActionTime = 0;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

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

        String item1 = (index1 < totalItems) ? shortcuts.get(index1) : null;
        String item2 = (index2 < totalItems) ? shortcuts.get(index2) : null;

        boolean isAntigravityPage = (item1 != null && item1.toLowerCase().contains("antigravity")) ||
                                   (item2 != null && item2.toLowerCase().contains("antigravity"));
        boolean isPortafolioPage = (item1 != null && item1.toLowerCase().contains("portafolio")) ||
                                  (item2 != null && item2.toLowerCase().contains("portafolio"));

        if (isAntigravityPage) {
            views.setTextViewText(R.id.txt_category, "🪐 ANTIGRAVITY (Puerto 3000)");
            views.setViewVisibility(R.id.txt_status, View.VISIBLE);
            String agState = prefs.getString("antigravity_status", "UNKNOWN");
            renderStatusBadge(views, agState);
            checkAntigravityStatus(context);
        } else if (isPortafolioPage) {
            views.setTextViewText(R.id.txt_category, "🌐 PORTAFOLIO WEB");
            views.setViewVisibility(R.id.txt_status, View.VISIBLE);
            String pfState = prefs.getString("portafolio_status", "UNKNOWN");
            renderStatusBadge(views, pfState);
            checkPortafolioStatus(context);
        } else {
            views.setTextViewText(R.id.txt_category, "⚡ SCRIPTS ACTIVOS (" + totalItems + ")");
            views.setViewVisibility(R.id.txt_status, View.GONE);
        }

        if (item1 != null) {
            setupButton(context, prefs, views, R.id.btn_action_1, item1);
            views.setViewVisibility(R.id.btn_action_1, View.VISIBLE);

            Intent click1 = new Intent(context, AndreyWidgetProvider.class);
            click1.setAction(ACTION_CLICK_1);
            click1.putExtra("item", item1);
            click1.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            views.setOnClickPendingIntent(R.id.btn_action_1, PendingIntent.getBroadcast(context, appWidgetId * 10 + 3, click1, flags));
        } else {
            views.setViewVisibility(R.id.btn_action_1, View.GONE);
        }

        if (item2 != null) {
            setupButton(context, prefs, views, R.id.btn_action_2, item2);
            views.setViewVisibility(R.id.btn_action_2, View.VISIBLE);

            Intent click2 = new Intent(context, AndreyWidgetProvider.class);
            click2.setAction(ACTION_CLICK_2);
            click2.putExtra("item", item2);
            click2.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            views.setOnClickPendingIntent(R.id.btn_action_2, PendingIntent.getBroadcast(context, appWidgetId * 10 + 4, click2, flags));
        } else {
            views.setViewVisibility(R.id.btn_action_2, View.GONE);
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static void renderStatusBadge(RemoteViews views, String state) {
        if ("ONLINE".equals(state)) {
            views.setTextViewText(R.id.txt_status, "🟢 Activo");
            views.setTextColor(R.id.txt_status, 0xFF10B981);
        } else if ("OFFLINE".equals(state)) {
            views.setTextViewText(R.id.txt_status, "🔴 Inactivo");
            views.setTextColor(R.id.txt_status, 0xFFFF5F56);
        } else if ("STARTING".equals(state) || "RESTARTING".equals(state)) {
            views.setTextViewText(R.id.txt_status, "🟡 Actualizando...");
            views.setTextColor(R.id.txt_status, 0xFFF59E0B);
        } else if ("STOPPING".equals(state)) {
            views.setTextViewText(R.id.txt_status, "🟡 Apagando...");
            views.setTextColor(R.id.txt_status, 0xFFF59E0B);
        } else {
            views.setTextViewText(R.id.txt_status, "🟡 Verificando...");
            views.setTextColor(R.id.txt_status, 0xFFF59E0B);
        }
    }

    private static void setupButton(Context context, SharedPreferences prefs, RemoteViews views, int btnId, String item) {
        String clean = item.replace(".sh", "");
        String lower = clean.toLowerCase();

        if (lower.contains("antigravity")) {
            String state = prefs.getString("antigravity_status", "UNKNOWN");
            if (lower.contains("encender") || lower.contains("start") || lower.contains("reiniciar")) {
                if ("ONLINE".equals(state)) {
                    views.setTextViewText(btnId, "🔄 Reiniciar / Actualizar");
                    views.setInt(btnId, "setBackgroundResource", R.drawable.btn_start_bg);
                    views.setTextColor(btnId, 0xFFFFFFFF);
                } else if ("STARTING".equals(state) || "RESTARTING".equals(state)) {
                    views.setTextViewText(btnId, "⏳ Actualizando Antigravity...");
                    views.setInt(btnId, "setBackgroundResource", R.drawable.btn_neutral_bg);
                    views.setTextColor(btnId, 0xFFF59E0B);
                } else {
                    views.setTextViewText(btnId, "🚀 Iniciar Antigravity");
                    views.setInt(btnId, "setBackgroundResource", R.drawable.btn_start_bg);
                    views.setTextColor(btnId, 0xFFFFFFFF);
                }
            } else if (lower.contains("apagar") || lower.contains("stop")) {
                if ("OFFLINE".equals(state)) {
                    views.setTextViewText(btnId, "✔ Antigravity Detenido");
                    views.setInt(btnId, "setBackgroundResource", R.drawable.btn_neutral_bg);
                    views.setTextColor(btnId, 0xFF7A889B);
                } else if ("STOPPING".equals(state)) {
                    views.setTextViewText(btnId, "⏳ Apagando Antigravity...");
                    views.setInt(btnId, "setBackgroundResource", R.drawable.btn_neutral_bg);
                    views.setTextColor(btnId, 0xFFF59E0B);
                } else {
                    views.setTextViewText(btnId, "🛑 Apagar Antigravity");
                    views.setInt(btnId, "setBackgroundResource", R.drawable.btn_stop_bg);
                    views.setTextColor(btnId, 0xFFFF5F56);
                }
            }
        } else if (lower.contains("portafolio")) {
            String state = prefs.getString("portafolio_status", "UNKNOWN");
            if (lower.contains("encender") || lower.contains("start") || lower.contains("reiniciar")) {
                if ("ONLINE".equals(state)) {
                    views.setTextViewText(btnId, "🔄 Reiniciar Portafolio");
                    views.setInt(btnId, "setBackgroundResource", R.drawable.btn_start_bg);
                    views.setTextColor(btnId, 0xFFFFFFFF);
                } else if ("STARTING".equals(state) || "RESTARTING".equals(state)) {
                    views.setTextViewText(btnId, "⏳ Reiniciando Portafolio...");
                    views.setInt(btnId, "setBackgroundResource", R.drawable.btn_neutral_bg);
                    views.setTextColor(btnId, 0xFFF59E0B);
                } else {
                    views.setTextViewText(btnId, "🚀 Iniciar Portafolio");
                    views.setInt(btnId, "setBackgroundResource", R.drawable.btn_start_bg);
                    views.setTextColor(btnId, 0xFFFFFFFF);
                }
            } else if (lower.contains("apagar") || lower.contains("stop")) {
                if ("OFFLINE".equals(state)) {
                    views.setTextViewText(btnId, "✔ Portafolio Detenido");
                    views.setInt(btnId, "setBackgroundResource", R.drawable.btn_neutral_bg);
                    views.setTextColor(btnId, 0xFF7A889B);
                } else if ("STOPPING".equals(state)) {
                    views.setTextViewText(btnId, "⏳ Apagando Portafolio...");
                    views.setInt(btnId, "setBackgroundResource", R.drawable.btn_neutral_bg);
                    views.setTextColor(btnId, 0xFFF59E0B);
                } else {
                    views.setTextViewText(btnId, "🛑 Apagar Portafolio");
                    views.setInt(btnId, "setBackgroundResource", R.drawable.btn_stop_bg);
                    views.setTextColor(btnId, 0xFFFF5F56);
                }
            }
        } else if (lower.contains("encender") || lower.contains("start")) {
            views.setTextViewText(btnId, "🚀 " + clean);
            views.setInt(btnId, "setBackgroundResource", R.drawable.btn_start_bg);
            views.setTextColor(btnId, 0xFFFFFFFF);
        } else if (lower.contains("apagar") || lower.contains("stop")) {
            views.setTextViewText(btnId, "🛑 " + clean);
            views.setInt(btnId, "setBackgroundResource", R.drawable.btn_stop_bg);
            views.setTextColor(btnId, 0xFFFF5F56);
        } else if (lower.contains("opencode")) {
            views.setTextViewText(btnId, "💻 OpenCode");
            views.setInt(btnId, "setBackgroundResource", R.drawable.btn_neutral_bg);
            views.setTextColor(btnId, 0xFF8FA0FF);
        } else if (lower.contains("ubuntu")) {
            views.setTextViewText(btnId, "🐧 Ubuntu Code");
            views.setInt(btnId, "setBackgroundResource", R.drawable.btn_neutral_bg);
            views.setTextColor(btnId, 0xFFEEF1F8);
        } else if (lower.contains("termux")) {
            views.setTextViewText(btnId, "📟 Termux Main");
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
                    list.add(arr.getString(i));
                }
            } catch (JSONException ignored) {}
        }
        if (list.isEmpty()) {
            list.add("encender_portafolio.sh");
            list.add("apagar_portafolio.sh");
            list.add("encender_antigravity.sh");
            list.add("apagar_antigravity.sh");
            list.add("OpenCode");
            list.add("TermuxMain");
            list.add("UbuntuCode");
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
            List<String> shortcuts = getShortcuts(prefs);
            int totalPages = Math.max(1, (int) Math.ceil((double) shortcuts.size() / ITEMS_PER_PAGE));
            int page = prefs.getInt("page_" + appWidgetId, 0);
            page = (page - 1 + totalPages) % totalPages;
            prefs.edit().putInt("page_" + appWidgetId, page).apply();
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            updateWidget(context, mgr, appWidgetId);
        } else if (ACTION_NEXT.equals(action) && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            List<String> shortcuts = getShortcuts(prefs);
            int totalPages = Math.max(1, (int) Math.ceil((double) shortcuts.size() / ITEMS_PER_PAGE));
            int page = prefs.getInt("page_" + appWidgetId, 0);
            page = (page + 1) % totalPages;
            prefs.edit().putInt("page_" + appWidgetId, page).apply();
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            updateWidget(context, mgr, appWidgetId);
        } else if (ACTION_REFRESH.equals(action)) {
            Toast.makeText(context, "🔄 Sincronizando scripts con Termux...", Toast.LENGTH_SHORT).show();
            requestShortcutsFromTermux(context);
            checkAntigravityStatus(context);
            checkPortafolioStatus(context);
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
                Toast.makeText(context, "✔ " + arr.length() + " scripts sincronizados", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(context, "⏳ Procesando... espera un momento", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE);
        String lower = item.toLowerCase();
        copyToClipboard(context, "bash ~/.shortcuts/" + item);

        if (lower.contains("antigravity")) {
            String currentState = prefs.getString("antigravity_status", "UNKNOWN");
            if (lower.contains("encender") || lower.contains("start") || lower.contains("reiniciar")) {
                if ("ONLINE".equals(currentState)) {
                    // ¡Antigravity ya está encendido! Tocar el botón fuerza REINICIO Y ACTUALIZACIÓN
                    lastActionTime = now;
                    prefs.edit().putString("antigravity_status", "RESTARTING").apply();
                    refreshAllWidgets(context);
                    sendPostShutdown();
                    copyToClipboard(context, "bash ~/.shortcuts/reiniciar_antigravity.sh");
                    runRestartAntigravity(context);
                    Toast.makeText(context, "🔄 Reiniciando y actualizando Antigravity...\n(Copiado a portapapeles)", Toast.LENGTH_SHORT).show();
                    scheduleStatusCheck(context, 2200, true);
                    return;
                }
                if ("STARTING".equals(currentState) || "RESTARTING".equals(currentState)) {
                    Toast.makeText(context, "⏳ Antigravity ya está iniciando...", Toast.LENGTH_SHORT).show();
                    return;
                }
                lastActionTime = now;
                prefs.edit().putString("antigravity_status", "STARTING").apply();
                refreshAllWidgets(context);
                runShortcutInTermux(context, item);
                Toast.makeText(context, "🚀 Iniciando Antigravity...\n(Copiado a portapapeles)", Toast.LENGTH_SHORT).show();
                scheduleStatusCheck(context, 1800, true);
            } else if (lower.contains("apagar") || lower.contains("stop")) {
                if ("OFFLINE".equals(currentState)) {
                    Toast.makeText(context, "ℹ️ Antigravity ya está apagado 🔴", Toast.LENGTH_SHORT).show();
                    return;
                }
                if ("STOPPING".equals(currentState)) {
                    Toast.makeText(context, "⏳ Antigravity ya está apagándose...", Toast.LENGTH_SHORT).show();
                    return;
                }
                lastActionTime = now;
                prefs.edit().putString("antigravity_status", "STOPPING").apply();
                refreshAllWidgets(context);
                sendPostShutdown();
                runShortcutInTermux(context, item);
                Toast.makeText(context, "🛑 Apagando Antigravity...\n(Copiado a portapapeles)", Toast.LENGTH_SHORT).show();
                scheduleStatusCheck(context, 1800, true);
            } else {
                lastActionTime = now;
                runShortcutInTermux(context, item);
                Toast.makeText(context, "⚡ " + item, Toast.LENGTH_SHORT).show();
            }
        } else if (lower.contains("portafolio")) {
            String currentState = prefs.getString("portafolio_status", "UNKNOWN");
            if (lower.contains("encender") || lower.contains("start") || lower.contains("reiniciar")) {
                if ("ONLINE".equals(currentState)) {
                    lastActionTime = now;
                    prefs.edit().putString("portafolio_status", "RESTARTING").apply();
                    refreshAllWidgets(context);
                    runShortcutInTermux(context, item);
                    Toast.makeText(context, "🔄 Reiniciando Portafolio Web...", Toast.LENGTH_SHORT).show();
                    scheduleStatusCheck(context, 1800, false);
                    return;
                }
                if ("STARTING".equals(currentState) || "RESTARTING".equals(currentState)) {
                    Toast.makeText(context, "⏳ Portafolio ya está iniciando...", Toast.LENGTH_SHORT).show();
                    return;
                }
                lastActionTime = now;
                prefs.edit().putString("portafolio_status", "STARTING").apply();
                refreshAllWidgets(context);
                runShortcutInTermux(context, item);
                Toast.makeText(context, "🚀 Iniciando Portafolio Web...", Toast.LENGTH_SHORT).show();
                scheduleStatusCheck(context, 1800, false);
            } else if (lower.contains("apagar") || lower.contains("stop")) {
                if ("OFFLINE".equals(currentState)) {
                    Toast.makeText(context, "ℹ️ Portafolio ya está apagado 🔴", Toast.LENGTH_SHORT).show();
                    return;
                }
                if ("STOPPING".equals(currentState)) {
                    Toast.makeText(context, "⏳ Portafolio ya está apagándose...", Toast.LENGTH_SHORT).show();
                    return;
                }
                lastActionTime = now;
                prefs.edit().putString("portafolio_status", "STOPPING").apply();
                refreshAllWidgets(context);
                runShortcutInTermux(context, item);
                Toast.makeText(context, "🛑 Deteniendo Portafolio...", Toast.LENGTH_SHORT).show();
                scheduleStatusCheck(context, 1800, false);
            } else {
                lastActionTime = now;
                runShortcutInTermux(context, item);
                Toast.makeText(context, "⚡ " + item, Toast.LENGTH_SHORT).show();
            }
        } else {
            lastActionTime = now;
            runShortcutInTermux(context, item);
            Toast.makeText(context, "⚡ " + item + "\n(Copiado a portapapeles)", Toast.LENGTH_SHORT).show();
        }
    }

    private static void runRestartAntigravity(Context context) {
        String fallbackCmd = "f=\"$HOME/.shortcuts/reiniciar_antigravity.sh\"; if [ -f \"$f\" ]; then bash \"$f\"; else curl -s -X POST http://127.0.0.1:3000/api/shutdown >/dev/null 2>&1 || true; sleep 0.4; pkill -f \"start.sh\" 2>/dev/null || true; pkill -f \"node server.js\" 2>/dev/null || true; sleep 0.4; if [ -f \"/sdcard/Download/antigravity_linux_server.zip\" ]; then unzip -uo \"/sdcard/Download/antigravity_linux_server.zip\" -d \"$HOME/antigravity_linux_server\" >/dev/null 2>&1; fi; if [ -d \"/sdcard/Download/antigravity_linux_server\" ]; then cp -ru /sdcard/Download/antigravity_linux_server/* \"$HOME/antigravity_linux_server/\" 2>/dev/null || true; fi; cd \"$HOME/antigravity_linux_server\" 2>/dev/null || exit 1; chmod +x start.sh stop.sh 2>/dev/null || true; nohup node server.js > \"$HOME/antigravity_linux_server/server.log\" 2>&1 & fi";
        try {
            Intent intent = new Intent();
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.setAction("com.termux.RUN_COMMAND");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/home/.shortcuts/reiniciar_antigravity.sh");
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            runInTermuxCommand(context, fallbackCmd);
        }
    }

    private static void scheduleStatusCheck(Context context, long delayMs, boolean isAntigravity) {
        mainHandler.postDelayed(() -> {
            if (isAntigravity) {
                checkAntigravityStatus(context);
            } else {
                checkPortafolioStatus(context);
            }
        }, delayMs);
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

    private static void sendPostShutdown() {
        executor.execute(() -> {
            try {
                URL url = new URL("http://127.0.0.1:3000/api/shutdown");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                conn.setDoOutput(true);
                conn.getOutputStream().write(new byte[0]);
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        });
    }

    private static void checkAntigravityStatus(Context context) {
        executor.execute(() -> {
            boolean active = false;
            try {
                URL url = new URL("http://127.0.0.1:3000/api/status");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(1200);
                conn.setReadTimeout(1200);
                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    String res = sb.toString();
                    if (res.contains("\"ok\":true") || res.contains("\"status\":\"online\"") || res.contains("true")) {
                        active = true;
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                active = false;
            }

            final String state = active ? "ONLINE" : "OFFLINE";
            mainHandler.post(() -> {
                SharedPreferences prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE);
                String oldState = prefs.getString("antigravity_status", "UNKNOWN");
                prefs.edit().putString("antigravity_status", state).apply();
                if (!state.equals(oldState)) {
                    refreshAllWidgets(context);
                } else {
                    updateStatusBadgeOnly(context, state);
                }
            });
        });
    }

    private static void checkPortafolioStatus(Context context) {
        executor.execute(() -> {
            boolean active = false;
            try {
                URL url = new URL("http://127.0.0.1:8081");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                int code = conn.getResponseCode();
                if (code > 0) {
                    active = true;
                }
                conn.disconnect();
            } catch (Exception e) {
                active = false;
            }

            final String state = active ? "ONLINE" : "OFFLINE";
            mainHandler.post(() -> {
                SharedPreferences prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE);
                String oldState = prefs.getString("portafolio_status", "UNKNOWN");
                prefs.edit().putString("portafolio_status", state).apply();
                if (!state.equals(oldState)) {
                    refreshAllWidgets(context);
                } else {
                    updateStatusBadgeOnly(context, state);
                }
            });
        });
    }

    private static void updateStatusBadgeOnly(Context context, String state) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            ComponentName cn = new ComponentName(context, AndreyWidgetProvider.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            for (int id : ids) {
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
                views.setViewVisibility(R.id.txt_status, View.VISIBLE);
                renderStatusBadge(views, state);
                mgr.partiallyUpdateAppWidget(id, views);
            }
        } catch (Exception ignored) {}
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

    private static void requestShortcutsFromTermux(Context context) {
        String cmd = "list=$(ls -1 \"$HOME/.shortcuts\" 2>/dev/null); am broadcast -a com.andrey.widget.UPDATE_LIST --es shortcuts \"$list\"";
        runInTermuxCommand(context, cmd);
    }

    private static void runShortcutInTermux(Context context, String shortcutName) {
        try {
            Intent intent = new Intent();
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.setAction("com.termux.RUN_COMMAND");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/home/.shortcuts/" + shortcutName);
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            runInTermuxCommand(context, "bash \"$HOME/.shortcuts/" + shortcutName + "\"");
        }
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

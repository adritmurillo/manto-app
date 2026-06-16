package com.guardianapp.mobile.data.appcontrol;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class BlockedAppsStore {

    private static final String PREF_NAME = "manto_app_control_prefs";
    private static final String KEY_BLOCKED_APPS = "blocked_apps_list";
    private static final String KEY_LAST_SYNC = "last_sync_timestamp";

    // Guarda la lista que descargamos de Retrofit en el celular
    public static void save(Context context, List<BlockedApp> apps) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = new Gson().toJson(apps);
        prefs.edit()
                .putString(KEY_BLOCKED_APPS, json)
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply();
    }

    // Lee la lista guardada de forma instantánea (offline)
    public static List<BlockedApp> getAll(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_BLOCKED_APPS, null);

        if (json == null) {
            return new ArrayList<>();
        }

        Type type = new TypeToken<List<BlockedApp>>(){}.getType();
        return new Gson().fromJson(json, type);
    }

    // El servicio de accesibilidad usará esto para saber si debe lanzar el bloqueo
    public static boolean isBlocked(Context context, String packageName) {
        List<BlockedApp> apps = getAll(context);
        for (BlockedApp app : apps) {
            if (app.getPackageName().equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    // Por si necesitamos mostrar el nombre de la app en la pantalla roja
    public static BlockedApp getBlockedInfo(Context context, String packageName) {
        List<BlockedApp> apps = getAll(context);
        for (BlockedApp app : apps) {
            if (app.getPackageName().equals(packageName)) {
                return app;
            }
        }
        return null;
    }

    // Método para evitar sincronizar a cada rato (Eficiencia)
    public static boolean shouldSync(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastSync = prefs.getLong(KEY_LAST_SYNC, 0);
        // Sincroniza si han pasado más de 24 horas (86400000 milisegundos) o si nunca ha sincronizado
        return (System.currentTimeMillis() - lastSync) > 86400000;
    }
}
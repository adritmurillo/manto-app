package com.guardianapp.mobile.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast; // ¡Importante para los Rayos X!

import com.guardianapp.mobile.ui.protecteduser.AppBlockedActivity;
import com.guardianapp.mobile.data.appcontrol.BlockedAppsStore;

public class AppBlockerService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.getPackageName() != null) {
                String packageName = event.getPackageName().toString();

                if (packageName.equals(getPackageName()) || packageName.contains("com.android.systemui")) {
                    return;
                }

                // 🚨 RAYOS X 1: ¿Cuántas apps conoce el vigilante en este instante?
                int cantidadEnCache = BlockedAppsStore.getAll(this).size();
                Log.d("AppBlocker", "Vigilante vio: " + packageName + " | Apps prohibidas en su memoria: " + cantidadEnCache);

                boolean isBlocked = BlockedAppsStore.isBlocked(this, packageName);

                if (isBlocked) {
                    // 🚨 RAYOS X 2: El vigilante lo atrapó y va a lanzar la pantalla
                    Toast.makeText(this, "🚨 VIGILANTE: ¡Ataque interceptado! Bloqueando " + packageName, Toast.LENGTH_LONG).show();

                    Intent intent = new Intent(this, AppBlockedActivity.class);
                    intent.putExtra("BLOCKED_PACKAGE", packageName);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(this, "❌ ERROR: La pantalla roja se estrelló (Crash)", Toast.LENGTH_LONG).show();
                    }
                } else {
                    // 🚨 RAYOS X 3: Si abres Zedge específicamente, nos chismeará por qué no lo bloquea
                    if (packageName.contains("zedge")) {
                        Toast.makeText(this, "👀 VIGILANTE: Vi a Zedge, pero en mi memoria dice que es legal. (Tengo " + cantidadEnCache + " apps en lista negra)", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
    }
}
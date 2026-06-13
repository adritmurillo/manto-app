package com.guardianapp.mobile.sms;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

public class RespondViaMessageService extends Service {

    private static final String TAG = "RespondViaMessageSvc";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Quick reply requested. SMS compose flow will be completed in the next module.");
        stopSelf(startId);
        return START_NOT_STICKY;
    }
}

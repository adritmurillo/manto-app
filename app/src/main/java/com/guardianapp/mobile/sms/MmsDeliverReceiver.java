package com.guardianapp.mobile.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MmsDeliverReceiver extends BroadcastReceiver {

    private static final String TAG = "MmsDeliverReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "MMS deliver broadcast received. Full MMS handling will be added in a later module.");
    }
}

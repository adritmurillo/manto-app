package com.guardianapp.mobile.data.api;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Registers device FCM token in backend.
 */
public final class NotificationRegistrar {

    private static final String TAG = "NotificationRegistrar";

    private NotificationRegistrar() {
    }

    public static void registerToken(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    RegisterDeviceTokenRequest request = new RegisterDeviceTokenRequest(token, "android");
                    RetrofitClient.getApiService().registerDeviceToken(userId, request)
                            .enqueue(new Callback<Void>() {
                                @Override
                                public void onResponse(Call<Void> call, Response<Void> response) {
                                    Log.d(TAG, "Token registration status: " + response.code());
                                }

                                @Override
                                public void onFailure(Call<Void> call, Throwable t) {
                                    Log.w(TAG, "Token registration failed", t);
                                }
                            });
                })
                .addOnFailureListener(ex -> Log.w(TAG, "Unable to get FCM token", ex));
    }
}

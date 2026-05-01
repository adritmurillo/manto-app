package com.guardianapp.mobile.api;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    // URL pública de Ngrok apuntando a tu Spring Boot en localhost:8080
    // IMPORTANTE: ¡Nunca olvides el slash "/" al final!
    private static final String BASE_URL = "https://quaking-tablet-spectator.ngrok-free.dev/";

    private static Retrofit retrofit = null;

    public static GuardianApiService getApiService() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(GuardianApiService.class);
    }

    public static String getBaseUrl() {
        return BASE_URL;
    }

    public static String getWebSocketUrl() {
        String url = BASE_URL;
        if (url.startsWith("https://")) {
            url = "wss://" + url.substring("https://".length());
        } else if (url.startsWith("http://")) {
            url = "ws://" + url.substring("http://".length());
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url + "/ws";
    }

    public static String getWebSocketBaseUrl() {
        String url = BASE_URL;
        if (url.startsWith("https://")) {
            url = "wss://" + url.substring("https://".length());
        } else if (url.startsWith("http://")) {
            url = "ws://" + url.substring("http://".length());
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}

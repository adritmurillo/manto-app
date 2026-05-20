package com.guardianapp.mobile.data.security;

import com.guardianapp.mobile.data.api.AnalyzeSingleUrlRequest;
import com.guardianapp.mobile.data.api.AnalyzeSingleUrlResponse;
import com.guardianapp.mobile.data.api.RegisterBlacklistUrlRequest;
import com.guardianapp.mobile.data.api.RegisterBlacklistUrlResponse;
import com.guardianapp.mobile.data.api.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LinkShieldRepository {

    public static class ApiFailure extends RuntimeException {
        private final int statusCode;

        public ApiFailure(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    public interface ResultCallback<T> {
        void onSuccess(T data);

        void onError(Throwable error);
    }

    public void analyzeUrl(String url, ResultCallback<AnalyzeSingleUrlResponse> callback) {
        AnalyzeSingleUrlRequest request = new AnalyzeSingleUrlRequest(url);
        RetrofitClient.getApiService().analyzeSingleUrl(request).enqueue(new Callback<AnalyzeSingleUrlResponse>() {
            @Override
            public void onResponse(Call<AnalyzeSingleUrlResponse> call, Response<AnalyzeSingleUrlResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError(new ApiFailure(response.code(), "Analyze URL API error: " + response.code()));
                    return;
                }
                callback.onSuccess(response.body());
            }

            @Override
            public void onFailure(Call<AnalyzeSingleUrlResponse> call, Throwable t) {
                callback.onError(t);
            }
        });
    }

    public void registerBlacklistUrl(String url, ResultCallback<RegisterBlacklistUrlResponse> callback) {
        RegisterBlacklistUrlRequest request = new RegisterBlacklistUrlRequest(url);
        RetrofitClient.getApiService().registerBlacklistUrl(request).enqueue(new Callback<RegisterBlacklistUrlResponse>() {
            @Override
            public void onResponse(Call<RegisterBlacklistUrlResponse> call, Response<RegisterBlacklistUrlResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError(new ApiFailure(response.code(), "Blacklist API error: " + response.code()));
                    return;
                }
                callback.onSuccess(response.body());
            }

            @Override
            public void onFailure(Call<RegisterBlacklistUrlResponse> call, Throwable t) {
                callback.onError(t);
            }
        });
    }
}

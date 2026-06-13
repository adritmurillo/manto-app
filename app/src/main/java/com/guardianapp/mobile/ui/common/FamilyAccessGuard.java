package com.guardianapp.mobile.ui.common;

import android.app.Activity;

import com.guardianapp.mobile.data.api.FamilyGroupResponse;
import com.guardianapp.mobile.data.api.LinkResponse;
import com.guardianapp.mobile.data.api.RetrofitClient;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class FamilyAccessGuard {

    private FamilyAccessGuard() {
    }

    public static void ensureInFamily(Activity activity, String userId, Runnable onAllowed) {
        if (activity == null || userId == null || userId.isBlank()) {
            if (activity != null) {
                AppNavigator.goToDeviceSetup(activity, userId);
            }
            return;
        }

        RetrofitClient.getApiService().getMyFamilyGroups(userId).enqueue(new Callback<List<FamilyGroupResponse>>() {
            @Override
            public void onResponse(Call<List<FamilyGroupResponse>> call, Response<List<FamilyGroupResponse>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    AppNavigator.goToDeviceSetup(activity, userId);
                    return;
                }
                if (onAllowed != null) {
                    onAllowed.run();
                }
            }

            @Override
            public void onFailure(Call<List<FamilyGroupResponse>> call, Throwable t) {
                // If API is temporarily down, do not kick user out.
                if (onAllowed != null) {
                    onAllowed.run();
                }
            }
        });
    }

    public static void ensureProtectedLinked(Activity activity, String protectedUserId, Runnable onAllowed) {
        if (activity == null || protectedUserId == null || protectedUserId.isBlank()) {
            if (activity != null) {
                AppNavigator.goToDeviceSetup(activity, protectedUserId);
            }
            return;
        }

        RetrofitClient.getApiService().getMyLinks(protectedUserId).enqueue(new Callback<List<LinkResponse>>() {
            @Override
            public void onResponse(Call<List<LinkResponse>> call, Response<List<LinkResponse>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    AppNavigator.goToDeviceSetup(activity, protectedUserId);
                    return;
                }

                for (LinkResponse link : response.body()) {
                    if (link == null) {
                        continue;
                    }
                    if (protectedUserId.equals(link.getProtectedUserId())
                            && "ACTIVE".equalsIgnoreCase(link.getStatus())) {
                        if (onAllowed != null) {
                            onAllowed.run();
                        }
                        return;
                    }
                }

                AppNavigator.goToDeviceSetup(activity, protectedUserId);
            }

            @Override
            public void onFailure(Call<List<LinkResponse>> call, Throwable t) {
                if (onAllowed != null) {
                    onAllowed.run();
                }
            }
        });
    }
}

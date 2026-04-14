package com.guardianapp.mobile.api;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Body;

public interface GuardianApiService {

    // Registro (El que ya tenías)
    @POST("api/v1/users")
    Call<UserResponse> registerUser(@Body RegisterUserRequest request);

    // 1. Buscar el UUID del usuario por su correo
    @GET("api/v1/users/search")
    Call<UserResponse> getUserByEmail(@Query("email") String email);

    // 2. Anfitrión: Generar código de invitación
    @POST("api/v1/invitations")
    Call<InvitationResponse> createInvitation(@Header("X-User-Id") String hostId);

    // 3. Protegido: Aceptar el código de invitación
    @POST("api/v1/invitations/{token}/accept")
    Call<Object> acceptInvitation(@Path("token") String token, @Header("X-User-Id") String protectedUserId);

    // Obtener los vínculos activos del usuario
    // Obtener TODOS los vínculos del usuario (Activos y Pendientes)
    @GET("api/v1/links")
    Call<List<LinkResponse>> getMyLinks(@Header("X-User-Id") String userId);


    @POST("api/v1/alerts")
    Call<AlertResponse> createAlert(@Body CreateAlertRequest request);

    @GET("api/v1/alerts/{id}")
    Call<AlertResponse> getAlert(@Path("id") String alertId);

    @GET("api/v1/alerts/pending")
    Call<List<AlertResponse>> getPendingAlerts(@Header("X-User-Id") String hostId);

    @PUT("api/v1/alerts/{id}/resolve")
    Call<AlertResponse> resolveAlert(@Path("id") String alertId, @Body ResolveAlertRequest request);

    @POST("api/v1/identity-verifications")
    Call<IdentityVerificationResponse> createIdentityVerification(@Body CreateIdentityVerificationRequest request);

    @PUT("api/v1/identity-verifications/{id}/respond")
    Call<IdentityVerificationResponse> respondIdentityVerification(
            @Path("id") String verificationId,
            @Body RespondIdentityVerificationRequest request
    );

    @GET("api/v1/identity-verifications/{id}")
    Call<IdentityVerificationResponse> getIdentityVerification(@Path("id") String verificationId);

    @GET("api/v1/identity-verifications/pending")
    Call<List<IdentityVerificationResponse>> getPendingIdentityVerifications(
            @Header("X-User-Id") String hostId
    );

    @POST("api/v1/notifications/token")
    Call<Void> registerDeviceToken(
            @Header("X-User-Id") String userId,
            @Body RegisterDeviceTokenRequest request
    );

    // El Protegido envía el PIN para activar el vínculo
    // El Protegido envía el código de 6 dígitos para activar el vínculo
    @POST("api/v1/links/{linkId}/confirm")
    Call<LinkResponse> confirmLink(
            @Path("linkId") String linkId,
            @Header("X-User-Id") String protectedId,
            @Body ConfirmLinkRequest request
    );
}

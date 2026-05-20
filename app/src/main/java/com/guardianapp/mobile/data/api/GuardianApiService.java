package com.guardianapp.mobile.data.api;

import com.guardianapp.mobile.data.threat.AnalyzeThreatRequest;
import com.guardianapp.mobile.data.threat.ThreatAnalysisResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Body;
import retrofit2.http.Part;
import retrofit2.http.PartMap;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import java.util.Map;

public interface GuardianApiService {

    @POST("api/v1/threats/analyze")
    Call<ThreatAnalysisResponse> analyzeThreat(@Body AnalyzeThreatRequest request);

    @POST("api/v1/threats/url/analyze")
    Call<AnalyzeSingleUrlResponse> analyzeSingleUrl(@Body AnalyzeSingleUrlRequest request);

    @POST("api/v1/blacklist/urls")
    Call<RegisterBlacklistUrlResponse> registerBlacklistUrl(@Body RegisterBlacklistUrlRequest request);


    // Registro (El que ya tenías)
    @POST("api/v1/users")
    Call<UserResponse> registerUser(@Body RegisterUserRequest request);

    // 1. Buscar el UUID del usuario por su correo
    @GET("api/v1/users/search")
    Call<UserResponse> getUserByEmail(@Query("email") String email);

    @GET("api/v1/users/{id}")
    Call<UserResponse> getUserById(@Path("id") String userId);

    // 2. Anfitrión: Generar código de invitación
    @POST("api/v1/invitations")
    Call<InvitationResponse> createInvitation(@Header("X-User-Id") String hostId);

    @POST("api/v1/families")
    Call<FamilyGroupResponse> createFamilyGroup(
            @Header("X-User-Id") String requesterUserId,
            @Body CreateFamilyGroupRequest request
    );

    @GET("api/v1/families/mine")
    Call<List<FamilyGroupResponse>> getMyFamilyGroups(@Header("X-User-Id") String requesterUserId);

    @POST("api/v1/families/{familyId}/members")
    Call<FamilyGroupResponse> addFamilyMember(
            @Path("familyId") String familyId,
            @Header("X-User-Id") String requesterUserId,
            @Body AddFamilyMemberRequest request
    );

    @POST("api/v1/family-invitations/families/{familyId}")
    Call<FamilyInvitationResponse> createFamilyInvitation(
            @Path("familyId") String familyId,
            @Header("X-User-Id") String requesterUserId,
            @Body CreateFamilyInvitationRequest request
    );

    @POST("api/v1/family-invitations/{token}/accept")
    Call<FamilyGroupResponse> acceptFamilyInvitation(
            @Path("token") String token,
            @Header("X-User-Id") String acceptedByUserId
    );

    // 3. Protegido: Aceptar el código de invitación
    @POST("api/v1/invitations/{token}/accept")
    Call<LinkResponse> acceptInvitation(@Path("token") String token, @Header("X-User-Id") String protectedUserId);

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

    @POST("api/v1/sms-threat-alerts")
    Call<SmsThreatAlertResponse> createSmsThreatAlert(@Body CreateSmsThreatAlertRequest request);

    @GET("api/v1/sms-threat-alerts/pending")
    Call<List<SmsThreatAlertResponse>> getPendingSmsThreatAlerts(@Header("X-User-Id") String hostId);

    @PUT("api/v1/sms-threat-alerts/{id}/resolve")
    Call<SmsThreatAlertResponse> resolveSmsThreatAlert(
            @Path("id") String alertId,
            @Body ResolveSmsThreatAlertRequest request
    );

    @POST("api/v1/emergencies")
    Call<EmergencyAlertResponse> triggerEmergency(@Body TriggerEmergencyAlertRequest request);

    @GET("api/v1/emergencies/active")
    Call<List<EmergencyAlertResponse>> getActiveEmergencies(@Header("X-User-Id") String hostId);

    @GET("api/v1/emergencies/active/protected")
    Call<List<EmergencyAlertResponse>> getActiveEmergenciesForProtected(@Header("X-User-Id") String protectedUserId);

    @GET("api/v1/emergencies/history")
    Call<List<EmergencyAlertResponse>> getEmergencyHistory(@Header("X-User-Id") String hostId);

    @PUT("api/v1/emergencies/{id}/resolve")
    Call<EmergencyAlertResponse> resolveEmergency(
            @Path("id") String emergencyId,
            @Body ResolveEmergencyAlertRequest request
    );

    @Multipart
    @POST("api/v1/emergencies/{id}/audio")
    Call<EmergencyAudioRecordingResponse> uploadEmergencyAudio(
            @Path("id") String emergencyId,
            @Header("X-User-Id") String protectedUserId,
            @Part MultipartBody.Part audio,
            @PartMap Map<String, RequestBody> params
    );

    @GET("api/v1/emergencies/{id}/audio")
    Call<List<EmergencyAudioRecordingResponse>> getEmergencyAudioHistory(
            @Path("id") String emergencyId,
            @Header("X-User-Id") String requesterId
    );

    @GET("api/v1/emergencies/{id}/audio/latest")
    Call<EmergencyAudioRecordingResponse> getLatestEmergencyAudio(@Path("id") String emergencyId);

    @POST("api/v1/notifications/token")
    Call<Void> registerDeviceToken(
            @Header("X-User-Id") String userId,
            @Body RegisterDeviceTokenRequest request
    );

}

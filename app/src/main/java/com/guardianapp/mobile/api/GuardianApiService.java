package com.guardianapp.mobile.api;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
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
    @GET("api/v1/links/active")
    Call<List<LinkResponse>> getActiveLinks(@Header("X-User-Id") String userId);
}
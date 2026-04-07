package com.guardianapp.mobile.api;

public class RegisterUserRequest {
    private String name;
    private String email;
    private String phone;

    public RegisterUserRequest(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    // Retrofit (usando Gson) usa estos atributos para generar el JSON automáticamente
}
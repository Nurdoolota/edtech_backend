package com.lms.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(
        String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {

    public static TokenResponse of(String accessToken, String refreshToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }

    public static TokenResponse accessOnly(String accessToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, null, "Bearer", expiresInSeconds);
    }
}

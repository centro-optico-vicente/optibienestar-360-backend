package com.fenixcore.optisaludplus.modules.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserDto user
) {
    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn, UserDto user) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}

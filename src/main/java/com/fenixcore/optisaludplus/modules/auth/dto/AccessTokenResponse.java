package com.fenixcore.optisaludplus.modules.auth.dto;

/**
 * Response for endpoints that reissue an access token without rotating the
 * refresh token (e.g. POST /v1/me/locale). The client keeps using its
 * existing refresh token; only the access token is replaced.
 */
public record AccessTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserDto user
) {
    public static AccessTokenResponse of(String accessToken, long expiresIn, UserDto user) {
        return new AccessTokenResponse(accessToken, "Bearer", expiresIn, user);
    }
}

package com.fenixcore.optisaludplus.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final String issuer;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.access-expiration-minutes}") int accessExpirationMinutes,
            @Value("${jwt.refresh-expiration-days}") int refreshExpirationDays) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessExpirationMs = (long) accessExpirationMinutes * 60 * 1_000;
        this.refreshExpirationMs = (long) refreshExpirationDays * 24 * 60 * 60 * 1_000;
    }

    public String generateAccessToken(String subject, List<String> permissions, String locale) {
        return buildToken(subject, permissions, accessExpirationMs, "access", locale);
    }

    public String generateRefreshToken(String subject) {
        return buildToken(subject, List.of(), refreshExpirationMs, "refresh", null);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractSubject(String token) {
        return extractAllClaims(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(String token) {
        Object perms = extractAllClaims(token).get("permissions");
        return (perms instanceof List<?>) ? (List<String>) perms : List.of();
    }

    public String extractLocale(String token) {
        Object locale = extractAllClaims(token).get("locale");
        return (locale instanceof String s) ? s : null;
    }

    public boolean isValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return !claims.getExpiration().before(new Date())
                    && issuer.equals(claims.getIssuer());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        return "access".equals(extractAllClaims(token).get("type"));
    }

    public String extractJti(String token) {
        return extractAllClaims(token).getId();
    }

    public long getRemainingTtlSeconds(String token) {
        Date expiration = extractAllClaims(token).getExpiration();
        long remaining = expiration.getTime() - System.currentTimeMillis();
        return Math.max(0L, remaining / 1_000);
    }

    private String buildToken(String subject, List<String> roles, long ttlMs, String type, String locale) {
        Date now = new Date();
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .claim("permissions", roles)
                .claim("type", type);
        if (locale != null && !locale.isBlank()) {
            builder.claim("locale", locale);
        }
        return builder.signWith(secretKey).compact();
    }
}

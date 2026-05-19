# 04 — Spring Security + JWT

> Implementación local. Cross-stack: [hub `03-security.md`](../../../centro-optico-vicente/.ai/specs/03-security.md).

## Componentes

### `security/SecurityConfig.java`

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // habilita @PreAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final JwtAuthenticationEntryPoint authEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(c -> c.configurationSource(corsConfigurationSource))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/v1/public/**",
                    "/v1/auth/login",
                    "/v1/auth/register",
                    "/v1/auth/refresh",
                    "/v1/auth/recover-password",
                    "/v1/auth/reset-password",
                    "/actuator/health/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint(authEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            );
        return http.build();
    }
}
```

### `security/jwt/JwtService.java`

```java
@Service
public class JwtService {

    @Value("${jwt.secret}") private String secret;
    @Value("${jwt.access-expiration-minutes:15}") private long accessExpirationMinutes;
    @Value("${jwt.refresh-expiration-days:30}") private long refreshExpirationDays;
    @Value("${jwt.issuer}") private String issuer;

    public String generateAccessToken(User user) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("roles", user.getRoles().stream().map(Role::getCode).toList())
            .claim("permissions", user.getAllPermissions())
            .issuer(issuer)
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plus(accessExpirationMinutes, ChronoUnit.MINUTES)))
            .signWith(getSigningKey(), Jwts.SIG.HS256)
            .compact();
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .requireIssuer(issuer)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
```

### `security/jwt/JwtAuthenticationFilter.java`

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }
        try {
            String token = header.substring(7);
            Claims claims = jwtService.parseAndValidate(token);
            UUID userId = UUID.fromString(claims.getSubject());

            CustomUserDetails userDetails = userDetailsService.loadByUserId(userId);

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException e) {
            // 401 será manejado por entry point
        }
        chain.doFilter(req, res);
    }
}
```

### `security/PasswordEncoderConfig.java`

```java
@Configuration
public class PasswordEncoderConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);  // strength 12
    }
}
```

## Refresh tokens

- Persistidos en Redis con TTL = expiración del token
- Blacklist al logout: mover refresh token a clave `blacklist:{tokenHash}` con TTL = remaining lifetime
- Endpoint refresh verifica que NO esté en blacklist

```java
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redis;

    public void invalidate(String refreshToken) {
        long ttlSec = remainingLifetimeSec(refreshToken);
        redis.opsForValue().set("blacklist:" + sha256(refreshToken), "1", Duration.ofSeconds(ttlSec));
    }

    public boolean isBlacklisted(String refreshToken) {
        return Boolean.TRUE.equals(redis.hasKey("blacklist:" + sha256(refreshToken)));
    }
}
```

## CORS

`core/config/CorsConfig.java`:

```java
@Configuration
public class CorsConfig {
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "https://centro-optico-vicente.com",
            "https://www.centro-optico-vicente.com",
            "https://app.dominio.com",
            "http://localhost:3000",  // dev
            "http://localhost:3001"   // dev
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "X-Idempotency-Key"));
        config.setExposedHeaders(List.of("X-Total-Count", "Link"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

## Endpoints públicos vs protegidos

| Endpoint | Auth | Permiso |
|---|---|---|
| `/v1/public/*` | No | — |
| `/v1/auth/login`, `/refresh`, `/recover-password`, `/reset-password` | No | — |
| `/actuator/health/**` | No | — |
| `/v1/me/*` | Sí (cualquier rol) | varía |
| `/v1/admin/*` | Sí | `ADMIN` u `OPERADOR` |
| `/v1/ally/*` | Sí | `ALIADO_USER` |
| `/v1/promoter/*` | Sí | `PROMOTOR` |
| `/swagger-ui/**`, `/v3/api-docs/**` | No (en dev) / Basic auth Traefik (en prod) | — |

## Audit log

Ver [ADR 0003 audit columns](../decisions/0003-audit-columns.md) + tabla `audit_log` en [02-database.md](02-database.md).

Eventos críticos a auditar:
- Login (success/fail)
- Logout
- Cambio password
- Cambio rol
- Aprobación/rechazo pago
- Acceso `MedicalRecord`
- Cambio status membresía
- Soft delete

Implementación: AOP aspect `@AuditAction`.

## Variables de entorno requeridas

```
JWT_SECRET=<32+ random bytes base64>
JWT_ISSUER=optisalud-plus
JWT_ACCESS_EXPIRATION_MINUTES=15
JWT_REFRESH_EXPIRATION_DAYS=30
```

## Referencias

- [hub `03-security.md`](../../../centro-optico-vicente/.ai/specs/03-security.md)
- [05-roles-permissions.md](05-roles-permissions.md)
- [`../decisions/0003-audit-columns.md`](../decisions/0003-audit-columns.md)

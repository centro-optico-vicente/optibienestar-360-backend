# Alcance 11 — Internacionalización (i18n) y Localización

> Spring i18n + bundles `messages_es` / `messages_en` (sin variantes regionales, principio "base + extends"). Locale resolver híbrido: **claim JWT `locale` gana** sobre `Accept-Language` header; default app `es-VE` ([ADR 0010](../decisions/0010-localization-venezuela.md), mirror del hub).
>
> Cross-cutting: retrofitea ~50 strings hardcoded en `auth`, `catalog`, `core/exception`, `common`. Sin tareas de servicio nuevas — solo se cambia el origen del texto, no la semántica de los endpoints existentes.
>
> Plan detallado: `/home/edwin/.claude/plans/vivid-wandering-dijkstra.md`.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Fase 1 — Bootstrap Spring i18n (foundation)

- [x] [P1/C1] Espejar [ADR 0010 del hub](../../../centro-optico-vicente/.ai/decisions/0010-localization-venezuela.md) → `.ai/decisions/0010-localization-venezuela.md` con encabezado "Espejo local — fuente canónica en el hub" (mismo patrón que ADR 0009).
- [x] [P1/C2] `core/config/I18nConfig.java` — `@Bean LocaleResolver` con `HybridLocaleResolver extends AcceptHeaderLocaleResolver` (placeholder de claim JWT a poblar en Fase 4) configurado con `supportedLocales=[es,en]` + `defaultLocale=es-VE`; `@Bean LocalValidatorFactoryBean` con el `MessageSource` autoconfigurado por Spring Boot vía `spring.messages.basename` — los `@Pattern(message="{code}")` ya resuelven contra los bundles. El `MessageSource` reloadable lo aporta Spring Boot directamente, sin tener que declararlo a mano.
- [x] [P1/C1] `OptiSaludPlusApplication.main` — `Locale.setDefault(Locale.forLanguageTag("es-VE"))` + `TimeZone.setDefault(TimeZone.getTimeZone("America/Caracas"))` antes de `SpringApplication.run`. Pinea defaults JVM-wide para que librerías que no consultan locale del request (Hibernate, MessageFormat, java.time sin Locale explícito) se alineen al país operativo.
- [x] [P1/C1] `application.properties` — agregadas `spring.messages.basename=messages,ValidationMessages`, `spring.messages.encoding=UTF-8`, `spring.messages.fallback-to-system-locale=false`, `spring.web.locale=es-VE`, `spring.jackson.time-zone=America/Caracas`.
- [x] [P1/C1] Bundles vacíos creados con comentario explicando el orden de llenado: `messages.properties` (fallback canónico EN), `messages_es.properties`, `messages_en.properties`, `ValidationMessages.properties`, `ValidationMessages_es.properties`, `ValidationMessages_en.properties`. **No** se crearon variantes `_es_VE` — ResourceBundle hace fallback automático `es_VE → es → default`.

## Fase 2 — Convención de claves + retrofit de excepciones y `GlobalExceptionHandler`

- [x] [P1/C2] `core/exception/LocalizedBusinessException.java` — clase abstracta con `String messageCode` + `Object[] args`. Heredan: `AuthenticationException`, `AccountLockedException`. Las que el JDK/Spring proveen (`IllegalArgumentException`, `NoSuchElementException`, `AccessDeniedException`) se siguen lanzando con el **code** como mensaje, y el handler lo detecta + resuelve vía `resolveCodeOrLiteral` (devuelve la traducción si el code está en el bundle, o el literal si no — graceful para mensajes legacy).
- [x] [P1/C3] `core/exception/GlobalExceptionHandler.java` — inyecta `MessageSource` por constructor; todos los `ProblemDetail` resolver `title` y `detail` por código contra `LocaleContextHolder.getLocale()`. Mapeo de status existente intacto (400/401/403/404/409/422/423/500). Helpers privados: `resolve(code, locale)` para claves fijas (titles + details genéricos), `resolveLocalized(LBE, locale)` para excepciones con code + args, `resolveCodeOrLiteral(msg, locale)` para JDK exceptions cuyo `getMessage()` se intenta tratar como code.
- [x] [P1/C2] `modules/auth/service/AuthService.java` — reemplazadas 7 strings por codes: `auth.credentials.invalid` (GENERIC_AUTH_ERROR), `auth.token.refresh.invalid` (×2), `auth.user.not_found` (×2 en refresh + changePassword), `auth.token.invalid_or_expired` (×2 en resetPassword), `auth.password.current.wrong`, `auth.password.history.repeat`.
- [x] [P1/C1] `modules/auth/service/UserService.java` — 7 occurrences reemplazadas a 4 codes: `user.not_found` (×4 en getMe/getUser/updateUser/deleteUser), `user.filter.field_not_allowed`, `user.email.exists`, `role.not_found` (en assignRoles).
- [x] [P1/C1] `modules/auth/service/RoleService.java` — 4 strings ingleses reemplazados: `role.system.not_editable`, `role.not_found`, `role.permission.uuid.unknown`, `role.auto_lockout`. Los args (uuid del rol, lista de UUIDs faltantes) sobreviven en el stack trace del throw para logs/debug; el mensaje al usuario queda genérico pero localizado.
- [x] [P1/C2] Llenados los 3 bundles con 24 claves cada uno (`auth.*` ×7, `user.*` ×3, `role.*` ×4, `error.title.*` ×8, `error.detail.*` ×4). `messages.properties` mantiene texto en inglés como fallback canónico (sirve cuando el locale del request no matchea `es*` ni `en*`).

## Fase 3 — Retrofit de validación Jakarta Bean

- [ ] [P1/C1] DTOs auth: `AdminCreateUserRequest.java`, `AdminUpdateUserRequest.java` — `@Pattern(message="{validation.document_type.format}")` + `@Pattern(message="{validation.user_status.allowed_values}")`.
- [ ] [P1/C1] DTOs catálogo (×7): `ServiceCategoryCreateRequest`, `GenderCreateRequest`, `DocumentTypeCreateRequest`, `AllyTypeCreateRequest`, `MedicalSpecialtyCreateRequest`, `MaritalStatusCreateRequest`, `CountryCreateRequest` — `@Pattern(message="{validation.code.*}")`.
- [ ] [P1/C1] DTO `UpdateRolePermissionsRequest.java` — `@NotNull` ya está sin message custom (usa default Jakarta); agregar `message="{validation.update_role_permissions.permission_uuids.required}"` si se quiere localizar también ese mensaje.
- [ ] [P1/C1] Llenar `ValidationMessages_es.properties` + `ValidationMessages_en.properties` con todas las claves `validation.*`.

## Fase 4 — Locale por usuario (persistente) + audit log

- [ ] [P1/C2] `V11__users_and_sessions_locale.sql` — `ALTER TABLE users ADD COLUMN locale VARCHAR(10)`; `ALTER TABLE user_sessions_log ADD COLUMN login_locale VARCHAR(10)` (snapshot inmutable per-sesión, sirve para analytics y forensics).
- [ ] [P1/C1] `modules/auth/entity/User.java` — `@Column(length=10) private String locale`.
- [ ] [P1/C1] `modules/auth/entity/UserSessionLog.java` — `@Column(name="login_locale", length=10) private String loginLocale` (insert-only).
- [ ] [P1/C1] `modules/auth/dto/UserDto.java` — campo `String locale` para que `/v1/me` lo retorne.
- [ ] [P1/C1] `modules/auth/dto/AdminUpdateUserRequest.java` — campo `String locale` con `@Pattern(regexp="^(es|es-VE|en)$", message="{validation.locale.allowed}")`.
- [ ] [P1/C2] `security/jwt/JwtService.java` — `generateAccessToken` acepta `String locale`, lo mete como claim `"locale"`. Refresh token NO necesita.
- [ ] [P1/C1] `security/jwt/JwtAuthenticationFilter.java` — extraer claim `locale`, pasarlo a `CustomUserDetails`.
- [ ] [P1/C1] `security/CustomUserDetails.java` — `private final String locale` con getter.
- [ ] [P1/C2] `modules/auth/service/AuthService.java` — `login` y `refresh` leen `user.getLocale()` y lo pasan a `generateAccessToken`. `logSession` recibe el `effectiveLocale` (el que terminó en el JWT) y lo persiste en `UserSessionLog.loginLocale`.
- [ ] [P1/C1] `HybridLocaleResolver` (de Fase 1) — activar el paso "claim JWT" ahora que `CustomUserDetails.locale` está poblado.

## Fase 5 — Templates email localizados

- [ ] [P2/C1] Renombrar `templates/email/password-recovery.html` → mantener como fallback (en inglés) + crear `password-recovery_es.html` (español base, cubre es/es-VE/es-MX/es-AR) + `password-recovery_en.html`. Mismo trato para `contact-form-received.html`.
- [ ] [P2/C1] `common/service/EmailService.java` — `sendTemplated(...)` acepta `Locale` parámetro, pasa al `Context.setLocale(locale)` de Thymeleaf, resuelve `subject` vía `messageSource.getMessage("email.<name>.subject", null, locale)`.
- [ ] [P2/C1] Llenar claves `email.recovery.*`, `email.contact.*` en bundles.
- [ ] [P2/C1] Callers que disparan emails (`AuthService.recoverPassword`, `ContactService.notify*`) — usar el `locale` del **destinatario** (no del request HTTP). Para recovery: `user.getLocale()` con fallback a `es-VE`. Para contacto público: el del request (es el único disponible).

## Fase 6 — Documentación

- [ ] [P2/C1] `.ai/specs/14-i18n.md` — nuevo spec con: convención naming dot-separated, cadena de fallback del resolver, patrón base+extends para bundles y templates, cómo agregar un idioma nuevo, cuándo crear `LocalizedBusinessException` nuevo vs reusar, locale del email siempre del destinatario.
- [ ] [P2/C1] `.ai/CLAUDE.md` — regla #9 "Mensajes user-facing van vía MessageSource code, nunca string literal".
- [ ] [P2/C1] `README.md` root — línea bajo "Local clone setup" sobre `Accept-Language` en requests de desarrollo.

## Tests

- [ ] [P2/C2] `I18nConfigTest` — el bean `MessageSource` resuelve `auth.credentials.invalid` en `es` y `en`. Verifica que `Locale("es","VE")` cae a `messages_es.properties`.
- [ ] [P2/C2] `HybridLocaleResolverTest` — los 3 niveles del fallback (claim JWT > Accept-Language > default) se respetan en ese orden; locale fuera de la whitelist (`pt-BR`) cae al siguiente nivel.
- [ ] [P2/C3] `GlobalExceptionHandlerIT` — 401/403/422 con `Accept-Language` distintos devuelven payload localizado; user con `users.locale=en` recibe inglés aunque el header sea `es-VE`.

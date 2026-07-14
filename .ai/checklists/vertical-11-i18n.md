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
- [x] [P1/C1] `OptiBienestar360Application.main` — `Locale.setDefault(Locale.forLanguageTag("es-VE"))` + `TimeZone.setDefault(TimeZone.getTimeZone("America/Caracas"))` antes de `SpringApplication.run`. Pinea defaults JVM-wide para que librerías que no consultan locale del request (Hibernate, MessageFormat, java.time sin Locale explícito) se alineen al país operativo.
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

- [x] [P1/C1] DTOs auth: `AdminCreateUserRequest.java`, `AdminUpdateUserRequest.java` — `@Pattern(message="{validation.document_type.format}")` (×2 archivos) + `@Pattern(message="{validation.user_status.allowed_values}")` (Update).
- [x] [P1/C1] DTOs catálogo (×7): mensajes mapeados a 5 claves compartidas según longitud de la regex — `{validation.code.uppercase.single}` (Gender), `{validation.code.uppercase.short}` (DocumentType, 1-3), `{validation.code.uppercase.medium}` (MaritalStatus, 1-20), `{validation.code.uppercase.long}` (ServiceCategory + AllyType + MedicalSpecialty, 1-40), `{validation.iso_code.alpha2}` (Country). Agrupar claves por longitud evita duplicar mensajes idénticos.
- [ ] [P2/C1] DTO `UpdateRolePermissionsRequest.java` — `@NotNull` sigue sin message custom (usa default Jakarta, que ya viene localizado en español/inglés por Hibernate Validator). Optativo, no se aplicó en este PR.
- [x] [P1/C1] Bundles llenos: `ValidationMessages.properties` (canónico EN), `ValidationMessages_es.properties` y `ValidationMessages_en.properties` con 7 claves cada uno (`validation.document_type.format`, `validation.user_status.allowed_values`, `validation.code.uppercase.{single,short,medium,long}`, `validation.iso_code.alpha2`).

## Fase 4 — Locale por usuario (persistente) + audit log

- [x] [P1/C2] V5/V8 modificados en sitio (BD se recrea, no se agrega V11 nueva): `users.locale VARCHAR(10)`, `user_sessions_log.login_locale VARCHAR(10)` (snapshot inmutable per-sesión, sirve para analytics y forensics); además `countries.locale VARCHAR(10)` con seed VE='es-VE' para pre-poblar preferencia al registrar usuarios.
- [x] [P1/C1] `modules/auth/entity/User.java` — `@Column(length=10) private String locale`.
- [x] [P1/C1] `modules/auth/entity/UserSessionLog.java` — `@Column(name="login_locale", length=10) private String loginLocale` (insert-only, set en constructor).
- [x] [P1/C1] `modules/catalog/entity/Country.java` + `CountryDto` + `CountryService.toDto` — campo `locale` propagado.
- [x] [P1/C1] `modules/auth/dto/UserDto.java` — campo `String locale` para que `/v1/me` lo retorne.
- [x] [P1/C1] `modules/auth/dto/AdminUpdateUserRequest.java` — campo `String locale` con `@Pattern(regexp="^(es|es-VE|en)$", message="{validation.locale.allowed}")`.
- [x] [P1/C1] `modules/auth/mapper/UserMapper.java` — mapeo del campo `locale`.
- [x] [P1/C2] `security/jwt/JwtService.java` — `generateAccessToken(subject, permissions, locale)` mete claim `"locale"` solo si no es null/blank; `extractLocale(token)` añadido. Refresh token NO lleva claim.
- [x] [P1/C1] `security/jwt/JwtAuthenticationFilter.java` — extrae claim `locale`, lo pasa a `CustomUserDetails`.
- [x] [P1/C1] `security/CustomUserDetails.java` — `private final String locale` con getter; `fromJwt` y constructor actualizados; `UserDetailsServiceImpl` lee de `user.getLocale()`.
- [x] [P1/C2] `modules/auth/service/AuthService.java` — `login` y `refresh` leen `user.getLocale()` y lo pasan a `generateAccessToken`. `logSession(user, jti, req, effectiveLocale)` persiste el locale efectivo (user.locale si existe, sino `LocaleContextHolder.getLocale().toLanguageTag()`) en `UserSessionLog.loginLocale`.
- [x] [P1/C2] **Option C — cambio de locale mid-session:** `AuthService.updateMyLocale(uuid, locale, currentJti)` actualiza `users.locale`, reemite access token con claim nuevo, blacklistea el access anterior (refresh NO rota — preferencia no es evento de seguridad). Nuevos DTOs `LocalePreferenceRequest` (con `@NotBlank @Pattern`) y `AccessTokenResponse`.
- [x] [P1/C1] `MeController.POST /v1/me/locale` — endpoint dedicado que recibe `LocalePreferenceRequest` y retorna `AccessTokenResponse` con token nuevo (efecto inmediato).
- [x] [P1/C1] `UserService.updateUser` propaga `request.locale()` para el flujo admin.
- [x] [P1/C1] `HybridLocaleResolver` (de Fase 1) — activado el paso "claim JWT": lee `SecurityContextHolder` → `CustomUserDetails.getLocale()` y prevalece sobre `Accept-Language`. Fallback al header (whitelist + base lang) cuando no hay principal o claim.
- [x] [P1/C1] `ValidationMessages*.properties` — clave `validation.locale.allowed` agregada en fallback + `_es` + `_en`.

## Fase 5 — Templates email localizados

- [x] [P2/C1] Templates renombrados in-place (siguen en `templates/`, no `templates/email/`): `password-recovery.html` (fallback EN, lang="en") + `password-recovery_es.html` (renombrado del actual, español) + `password-recovery_en.html`. Mismo trato para `contact-form-received.html`.
- [x] [P2/C1] `common/service/EmailService.java` — nueva firma `sendTemplated(to, subject, template, Locale, variables)`: resuelve `<template>_<lang>.html` vía whitelist `{es,en}` (cae al base si fuera de whitelist), instancia `new Context(locale)` para que cualquier `#{}` en el template resuelva vía MessageSource. El subject se pasa pre-resuelto (el caller conoce los args específicos del template).
- [x] [P2/C1] Claves `email.recovery.subject` y `email.contact.subject` (con `{0}` = subject del usuario) agregadas a los 3 bundles.
- [x] [P2/C1] `AuthService.recoverPassword` — inyecta `MessageSource`, resuelve `Locale recipientLocale` desde `user.getLocale()` con fallback a `es-VE` (helper `resolveRecipientLocale`), resuelve subject via MessageSource. Email va en el idioma del **destinatario** aunque admin dispare el flujo desde otra locale.
- [x] [P2/C1] `ContactService.submit` — inyecta `MessageSource`, usa `LocaleContextHolder.getLocale()` (request locale — único disponible en endpoint público sin auth), resuelve subject + dispara email con esa locale.

## Fase 6 — Documentación

- [x] [P2/C1] `.ai/specs/14-i18n.md` — nuevo spec con: convención naming dot-separated, cadena de fallback del resolver, patrón base+extends para bundles y templates, cómo agregar un idioma nuevo, cuándo crear `LocalizedBusinessException` nuevo vs reusar, locale del email siempre del destinatario.
- [x] [P2/C1] `.ai/CLAUDE.md` — regla #9 "Mensajes user-facing van vía MessageSource code, nunca string literal".
- [x] [P2/C1] `README.md` root — línea bajo "Local clone setup" sobre `Accept-Language` en requests de desarrollo.

## Tests

- [x] [P2/C2] `I18nConfigTest` — el bean `MessageSource` resuelve `auth.credentials.invalid` en `es` y `en`. Verifica que `Locale("es","VE")` cae a `messages_es.properties`. _(5 tests, incluye también ResourceBundle fallback para locales fuera de whitelist y validación de que `ValidationMessages_*` está en el basename)_
- [x] [P2/C2] `HybridLocaleResolverTest` — los 3 niveles del fallback (claim JWT > Accept-Language > default) se respetan en ese orden; locale fuera de la whitelist (`pt-BR`) cae al siguiente nivel. _(8 tests, incluye claim null/blank/empty, y documenta la asimetría: el claim NO se filtra por la whitelist)_
- [x] [P2/C3] `GlobalExceptionHandlerIT` — 401/403/422 con `Accept-Language` distintos devuelven payload localizado; user con `users.locale=en` recibe inglés aunque el header sea `es-VE`. _(7 tests sobre 404/403/422; controller de test que tira excepciones a demanda; @SpringBootTest + MockMvc construido manualmente — Spring Boot 4 eliminó @WebMvcTest y @AutoConfigureMockMvc)_

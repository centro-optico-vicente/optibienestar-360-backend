# 01 — Estructura de paquetes

## Objetivo

Estructura modular del backend para facilitar:
- Localización rápida de código (un módulo = un dominio de negocio)
- Tests independientes por módulo
- Permisos granulares por módulo (Spring Security)
- Refactor a microservicios si en el futuro hace falta (no es prioridad)

## Estructura

```
com.fenixcore.optibienestar360/
│
├── OptiBienestar360Application.java       # Bootstrap @SpringBootApplication
│
├── core/                               # CROSS-CUTTING (config, infra interna)
│   ├── audit/
│   │   ├── JpaAuditingConfig.java     # @EnableJpaAuditing
│   │   └── ApplicationAuditorAware.java  # AuditorAware<UUID> que lee SecurityContext
│   ├── config/
│   │   ├── CorsConfig.java
│   │   ├── JacksonConfig.java         # snake_case, ISO 8601
│   │   ├── OpenApiConfig.java         # Swagger config
│   │   ├── RedisCacheConfig.java      # @EnableCaching, key serializers
│   │   ├── S3Config.java              # R2 endpoint
│   │   ├── AsyncConfig.java           # @EnableAsync, thread pool
│   │   └── SchedulingConfig.java      # @EnableScheduling
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java  # @ControllerAdvice → RFC 7807
│   │   ├── BusinessRuleException.java
│   │   ├── ResourceNotFoundException.java
│   │   └── ProblemDetail.java         # DTO RFC 7807
│   └── persistence/
│       └── BaseEntity.java            # @MappedSuperclass abstract
│
├── security/                           # Auth + AuthZ
│   ├── SecurityConfig.java            # SecurityFilterChain
│   ├── PasswordEncoderConfig.java     # BCrypt strength 12
│   ├── jwt/
│   │   ├── JwtService.java
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── JwtAuthenticationEntryPoint.java
│   │   ├── JwtAccessDeniedHandler.java
│   │   └── JwtProperties.java         # @ConfigurationProperties
│   ├── authentication/
│   │   └── CustomUserDetailsService.java
│   └── ratelimit/
│       └── RedisRateLimiter.java
│
├── common/                             # Servicios reutilizables cross-módulo
│   ├── service/
│   │   ├── StorageService.java        # Upload/download a R2
│   │   ├── EmailService.java          # SMTP + Thymeleaf
│   │   └── IdempotencyService.java    # X-Idempotency-Key handling
│   ├── model/
│   │   └── PageDTO.java               # Wrapper paginación uniforme
│   ├── validator/
│   │   ├── VenezuelanID.java          # @VenezuelanID annotation
│   │   ├── VenezuelanIDValidator.java
│   │   └── PhoneNumberValidator.java
│   └── util/
│       ├── QrCodeGenerator.java       # Para carnet digital
│       └── DateUtils.java
│
└── modules/                            # MÓDULOS DE NEGOCIO
    │
    ├── users/                          # Usuarios + roles + permisos
    │   ├── entity/
    │   │   ├── User.java
    │   │   ├── Role.java
    │   │   ├── Permission.java
    │   │   ├── UserRole.java
    │   │   └── RolePermission.java
    │   ├── repository/
    │   │   ├── UserRepository.java
    │   │   ├── RoleRepository.java
    │   │   └── PermissionRepository.java
    │   ├── service/
    │   │   ├── UserService.java
    │   │   ├── AuthService.java
    │   │   └── PasswordResetService.java
    │   ├── controller/
    │   │   ├── AuthController.java     # /v1/auth/*
    │   │   ├── MeController.java       # /v1/me
    │   │   └── UserController.java     # /v1/admin/users
    │   ├── dto/
    │   │   ├── UserCreateDTO.java
    │   │   ├── UserUpdateDTO.java
    │   │   ├── UserDetailDTO.java
    │   │   ├── UserListItemDTO.java
    │   │   ├── LoginRequestDTO.java
    │   │   ├── LoginResponseDTO.java
    │   │   ├── RefreshTokenRequestDTO.java
    │   │   └── ...
    │   └── mapper/
    │       └── UserMapper.java         # MapStruct
    │
    ├── catalogs/                       # Tablas de catálogo
    │   ├── entity/
    │   │   ├── Country.java
    │   │   ├── State.java
    │   │   ├── City.java
    │   │   ├── MedicalSpecialty.java
    │   │   ├── ServiceCategory.java
    │   │   ├── AllyType.java
    │   │   └── ...
    │   ├── repository/
    │   ├── service/
    │   ├── controller/                 # /v1/catalogs/*
    │   └── dto/
    │
    ├── allies/                         # Aliados
    │   ├── entity/
    │   │   ├── Ally.java
    │   │   ├── AllySpecialty.java
    │   │   ├── AllyService.java
    │   │   ├── AllyAgreement.java
    │   │   └── AllyUser.java
    │   ├── repository/
    │   ├── service/
    │   ├── controller/
    │   │   ├── AllyController.java     # /v1/admin/allies/*
    │   │   └── PublicAllyController.java  # /v1/public/allies
    │   ├── dto/
    │   └── mapper/
    │
    ├── members/                        # Afiliados
    │   ├── entity/
    │   │   ├── Member.java
    │   │   ├── Beneficiary.java
    │   │   ├── MemberDocument.java
    │   │   └── MedicalRecord.java
    │   ├── repository/
    │   ├── service/
    │   │   ├── MemberService.java
    │   │   └── MedicalRecordService.java   # con @PreAuthorize estricto
    │   ├── controller/
    │   │   ├── MemberController.java       # /v1/admin/members/*
    │   │   └── MyMemberController.java     # /v1/me/member
    │   ├── dto/
    │   └── mapper/
    │
    ├── memberships/                    # Membresías + planes
    │   ├── entity/
    │   │   ├── Plan.java
    │   │   └── Membership.java
    │   ├── repository/
    │   ├── service/
    │   │   ├── PlanService.java
    │   │   ├── MembershipService.java
    │   │   └── MembershipStatusService.java   # job diario
    │   ├── controller/
    │   ├── dto/
    │   └── mapper/
    │
    ├── payments/                       # Pagos manuales
    │   ├── entity/
    │   │   └── Payment.java
    │   ├── repository/
    │   ├── service/
    │   │   ├── PaymentService.java
    │   │   └── PaymentWorkflowService.java
    │   ├── controller/
    │   ├── dto/
    │   └── mapper/
    │
    ├── promoters/                      # Promotores + comisiones + referidos
    │   ├── entity/
    │   │   ├── Promoter.java
    │   │   ├── Commission.java
    │   │   └── Referral.java
    │   ├── repository/
    │   ├── service/
    │   │   ├── PromoterService.java
    │   │   ├── CommissionService.java
    │   │   └── ReferralService.java
    │   ├── controller/
    │   ├── dto/
    │   └── mapper/
    │
    ├── validator/                      # Validador en tiempo real
    │   ├── service/
    │   │   ├── MemberValidatorService.java       # endpoint /v1/ally/validate/{doc}
    │   │   ├── BenefitUsageService.java
    │   │   └── ValidatorCacheService.java         # Redis layer
    │   ├── controller/
    │   │   └── AllyValidatorController.java       # /v1/ally/*
    │   ├── dto/
    │   │   ├── ValidationResultDTO.java
    │   │   └── BenefitUsageCreateDTO.java
    │   └── mapper/
    │
    ├── notifications/                  # Notificaciones email
    │   ├── entity/
    │   │   └── Notification.java
    │   ├── repository/
    │   ├── service/
    │   │   ├── NotificationService.java
    │   │   ├── EmailTemplateService.java
    │   │   └── ReminderJobService.java   # @Scheduled diario
    │   ├── controller/                   # admin de cola
    │   └── dto/
    │
    ├── reports/                        # Reportes
    │   ├── service/
    │   ├── controller/                   # /v1/admin/reports/*
    │   └── dto/
    │
    ├── contact/                        # Form contacto landing
    │   ├── entity/
    │   │   └── ContactMessage.java
    │   ├── repository/
    │   ├── service/
    │   ├── controller/
    │   │   └── PublicContactController.java   # POST /v1/public/contact
    │   ├── dto/
    │   └── mapper/
    │
    └── audit/                          # Audit log
        ├── entity/
        │   └── AuditLog.java
        ├── repository/
        ├── service/
        └── aspect/                      # @AuditAction AOP
```

## Reglas

1. **Un módulo = un dominio de negocio.** No mezclar entidades de dominios distintos en el mismo módulo.
2. **Dependencias entre módulos:** ok via service interface. Si un módulo importa entity directamente de otro → considerar si es señal de mala separación.
3. **`common/`** es para servicios compartidos por > 1 módulo. No es un "drawer" de cosas misceláneas.
4. **`core/`** es infra del framework (config, exception handling). No lógica de negocio.
5. **Modular tests:** cada `modules/X/` tiene `src/test/java/.../modules/X/` con tests aislados.

## Naming

- **Packages:** `lowercase.dotted`
- **Clases:** `PascalCase`
- **Métodos/variables:** `camelCase`
- **Constantes:** `UPPER_SNAKE_CASE`
- **Enum values:** `UPPER_SNAKE_CASE`

Ver [ADR 0009 cross-stack](../../../centro-optico-vicente/.ai/decisions/0009-code-conventions.md).

## Convenciones específicas por capa

### Entity
- Extiende `BaseEntity` siempre
- PK con nombre específico (`memberId`, no `id`)
- Bidireccional sólo si justifica (default unidireccional)
- `FetchType.LAZY` por default

### Repository
- Extiende `JpaRepository<Entity, UUID>` + `JpaSpecificationExecutor<Entity>` (para RSQL)
- Métodos custom anotados `@Query` cuando hace falta lógica especial

### Service
- Interface + impl? Solo si tiene sentido (ej. múltiples implementaciones). Por default: clase directa.
- `@Transactional` a nivel clase con `readOnly = true`, sobreescribir en métodos de escritura
- Inyección por constructor (no `@Autowired` en field)

### DTO
- Records si Java 17+ permite (`record FooDTO(...)`) — más conciso
- Validación con Bean Validation
- No exponer entidades JPA directo en controllers

### Mapper
- MapStruct con `@Mapper(componentModel = "spring")`
- Métodos: `toDTO`, `toEntity`, `updateEntity`

### Controller
- `@RestController` + `@RequestMapping("/v1/...")`
- Anotaciones Swagger: `@Operation`, `@ApiResponses`
- `@PreAuthorize` para permisos
- Documentar todas las request bodies y responses

## Referencias

- [02-database.md](02-database.md) — schema
- [03-jpa-flyway.md](03-jpa-flyway.md) — convenciones JPA y migraciones
- [06-rest-api.md](06-rest-api.md) — convenciones REST

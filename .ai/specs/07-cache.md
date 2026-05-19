# 07 — Cache estratégico con Redis

## Backends

| Profile | Cache backend |
|---|---|
| `dev` | Simple (in-memory) |
| `test` | Simple |
| `prod` | Redis |

## Configuración

`core/config/RedisCacheConfig.java`:

```java
@Configuration
@EnableCaching
@Profile("prod")
public class RedisCacheConfig {

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .computePrefixWith(CacheKeyPrefix.simple())
            .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(SerializationPair.fromSerializer(
                new GenericJackson2JsonRedisSerializer(jacksonObjectMapper())
            ));

        Map<String, RedisCacheConfiguration> custom = Map.of(
            "validator", defaultConfig.entryTtl(Duration.ofSeconds(60)),
            "catalogs", defaultConfig.entryTtl(Duration.ofHours(1)),
            "user-permissions", defaultConfig.entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(cf)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(custom)
            .build();
    }
}
```

## Usos clave

### Cache `validator` (CRÍTICO)

- Clave: `validator:{document}`
- TTL: 60 segundos
- Invalidación inmediata al:
  - Aprobar/rechazar un payment de la membership
  - Cambiar status de la membership
  - Cancelar/reactivar membership
  - Cambiar beneficiarios

```java
@Service
public class MemberValidatorService {

    @Cacheable(value = "validator", key = "#document")
    public ValidationResultDTO validate(String document) {
        // Query DB + construir ValidationResultDTO
    }

    @CacheEvict(value = "validator", key = "#document")
    public void invalidateValidatorCache(String document) {}
}

// En PaymentService:
@CacheEvict(value = "validator", key = "#payment.member.documentNumber")
public PaymentDTO approve(UUID paymentId) { /* ... */ }
```

### Cache `catalogs`

Catálogos estáticos (especialidades médicas, tipos de servicio, países, ciudades):

```java
@Cacheable(value = "catalogs", key = "'medical-specialties'")
public List<MedicalSpecialty> findAllMedicalSpecialties() { /* ... */ }
```

### Cache `user-permissions`

Permisos extraídos de DB al login (evita query repetida en cada request):

```java
@Cacheable(value = "user-permissions", key = "#userId")
public Set<String> getUserPermissions(UUID userId) { /* ... */ }
```

Invalidación al cambiar rol/permiso del usuario.

### Refresh tokens (no es cache, es storage TTL)

```java
StringRedisTemplate redis;

redis.opsForValue().set(
    "refresh:" + tokenHash,
    userId.toString(),
    Duration.ofDays(30)
);
```

### Blacklist JWT (logout)

```java
redis.opsForValue().set(
    "blacklist:" + tokenHash,
    "1",
    Duration.ofSeconds(remainingLifetimeSec)
);
```

### Rate limiting

```java
String key = "ratelimit:" + userId + ":" + bucketName;
Long count = redis.opsForValue().increment(key);
if (count == 1L) redis.expire(key, Duration.ofMinutes(1));
if (count > limit) throw new TooManyRequestsException();
```

### Idempotency keys

```java
String key = "idempotency:" + idempotencyKey;
String existing = redis.opsForValue().get(key);
if (existing != null) return deserializeResult(existing);
T result = action.execute();
redis.opsForValue().set(key, serialize(result), Duration.ofHours(24));
return result;
```

## Estrategias TTL

| Tipo | TTL |
|---|---|
| Cache de hot reads (validator) | 60s |
| Cache de catálogos | 1h |
| User permissions | 5min |
| Refresh tokens | 30d |
| Blacklist JWT | remaining lifetime del token (max 30d) |
| Rate limit buckets | 1min/1h/1d según bucket |
| Idempotency keys | 24h |

## Invalidación

Reglas:
- Mejor invalidar puntualmente (`@CacheEvict` con key específica) que limpiar todo
- En operaciones que afectan múltiples caches, usar `@Caching` con varios `@CacheEvict`
- Considerar invalidación basada en eventos (Spring Application Events) para desacoplar

## Multi-instancia (futuro)

Cuando se habiliten réplicas backend (Tarea 1.10):
- Redis compartido entre réplicas (vía `database_network`)
- Cache distribuído consistente
- Sin necesidad de stickiness en Traefik

## Métricas

Monitorear con Actuator (`/actuator/metrics`):
- `cache.gets` / `cache.misses` / `cache.puts` (hit rate)
- `redis.commands.duration` (latencia)
- Configurar alerta si hit rate < 80% en producción

## Referencias

- [`../specs/10-validators.md`](10-validators.md) — uso específico en el validador
- [hub `03-security.md`](../../../centro-optico-vicente/.ai/specs/03-security.md) — blacklist tokens
- Spring Cache Abstraction docs

# 13 — Observability (logs, métricas, health)

## Stack

| Capa | Tool | Status |
|---|---|---|
| Logs estructurados | Logback JSON | Configurar en Tarea 1.7 |
| Métricas básicas | Spring Boot Actuator + Micrometer | Built-in |
| Métricas Prometheus | Actuator `/actuator/prometheus` | Habilitar en prod |
| Tracing | OpenTelemetry | Futuro (Fase 6) |
| Centralización | Loki + Grafana + Tempo (self-hosted) | Futuro (Fase 6) |

## Actuator endpoints

`application.properties`:

```properties
management.endpoints.web.exposure.include=health,info,metrics,prometheus,loggers
management.endpoint.health.show-details=when-authorized
management.endpoint.health.probes.enabled=true
management.health.livenessstate.enabled=true
management.health.readinessstate.enabled=true
management.info.env.enabled=true
management.info.git.mode=full
management.metrics.tags.application=optisalud-plus-backend
management.metrics.tags.environment=${SPRING_PROFILES_ACTIVE}
```

### Endpoints expuestos

| Endpoint | Auth | Uso |
|---|---|---|
| `/actuator/health` | público | Healthcheck Docker/Traefik |
| `/actuator/health/liveness` | público | K8s liveness (futuro) |
| `/actuator/health/readiness` | público | K8s readiness |
| `/actuator/info` | auth ADMIN | Versión, build info |
| `/actuator/metrics` | auth ADMIN | Métricas |
| `/actuator/prometheus` | auth ADMIN o IP allowlist | Scrape Prometheus |
| `/actuator/loggers` | auth ADMIN | Cambiar nivel de log en runtime |

## Health checks

Custom health indicators:

```java
@Component
public class RedisHealthIndicator implements HealthIndicator {
    private final StringRedisTemplate redis;

    public Health health() {
        try {
            redis.execute(connection -> connection.ping());
            return Health.up().build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}

@Component
public class StorageR2HealthIndicator implements HealthIndicator {
    private final S3Client s3;

    public Health health() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return Health.up().build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

DB health ya está provisto por Spring Boot Actuator.

## Logging estructurado JSON

`src/main/resources/logback-spring.xml`:

```xml
<configuration>
    <springProfile name="dev">
        <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="STDOUT"/>
        </root>
    </springProfile>

    <springProfile name="prod">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <customFields>{"application":"optisalud-plus-backend"}</customFields>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON"/>
        </root>
        <logger name="com.fenixcore" level="INFO"/>
        <logger name="org.hibernate.SQL" level="WARN"/>
    </springProfile>
</configuration>
```

Dependencia:
```groovy
implementation 'net.logstash.logback:logstash-logback-encoder:8.0'
```

## MDC para trace ID

Agregar trace ID a cada request para correlación:

```java
@Component
public class TraceIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String traceId = req.getHeader("X-Trace-Id");
        if (traceId == null) traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        try {
            res.setHeader("X-Trace-Id", traceId);
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }
}
```

## Métricas custom

```java
@Component
@RequiredArgsConstructor
public class ValidatorMetrics {
    private final MeterRegistry registry;

    public void recordValidation(String result, long durationMs) {
        registry.counter("validator.requests", "result", result).increment();
        registry.timer("validator.duration").record(Duration.ofMillis(durationMs));
    }
}
```

Métricas clave a registrar:
- `validator.requests` (counter por result: valid/invalid/notfound)
- `validator.duration` (timer)
- `payments.registered` (counter por method)
- `payments.approved` / `payments.rejected` (counter)
- `commissions.created` (counter por plan_type)
- `notifications.sent` / `notifications.failed` (counter)

## Alertas (futuro con Grafana)

| Métrica | Threshold | Severidad |
|---|---|---|
| p95 latencia validator | > 200ms 5min | warning |
| p95 latencia validator | > 500ms 1min | critical |
| Tasa de error 5xx | > 1% 5min | warning |
| Tasa de error 5xx | > 5% 1min | critical |
| Cache hit rate validator | < 70% 30min | info |
| Notificaciones FAILED | > 10 en 1h | warning |
| Disk usage | > 80% | warning |
| Memory usage JVM | > 90% | warning |

## Performance budget

- Endpoint validator: p50 < 50ms, p95 < 200ms, p99 < 500ms
- Endpoints CRUD admin: p95 < 500ms
- Endpoints listado paginado: p95 < 800ms
- Endpoints reportes: p95 < 3s (acceptable, son menos frecuentes)

Si una métrica supera estos límites consistently, abrir issue de optimización.

## Tracing distribuido (futuro)

Cuando se implemente (Fase 6+):

```groovy
implementation 'io.micrometer:micrometer-tracing-bridge-otel'
implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
```

```properties
management.tracing.sampling.probability=0.1  # 10% sampling
management.otlp.tracing.endpoint=http://tempo:4317
```

Headers W3C Trace Context se propagan automáticamente entre servicios.

## Referencias

- Spring Boot Actuator docs
- Micrometer docs
- [hub `02-infrastructure.md`](../../../centro-optico-vicente/.ai/specs/02-infrastructure.md) — monitoring stack futuro

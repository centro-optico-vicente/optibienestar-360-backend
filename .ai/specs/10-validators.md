# 10 — Validador en tiempo real (CRÍTICO p95 < 200ms)

> **Es el endpoint más importante del sistema** desde la perspectiva del aliado. Un mal performance acá rompe el producto.

## Objetivo

El recepcionista de una clínica/farmacia/ambulancia ingresa la cédula del afiliado y obtiene en < 200ms (p95):

- ¿Está vigente?
- ¿Qué plan tiene?
- ¿Qué descuentos aplican según las especialidades del aliado?
- ¿Quiénes son sus beneficiarios cubiertos?

## Endpoint

`GET /v1/ally/validate/{document}`

### Request

```
GET /v1/ally/validate/V-12345678
Authorization: Bearer <jwt_ally_user>
```

### Response 200

```json
{
  "document": "V-12345678",
  "is_valid": true,
  "status": "ACTIVE",
  "member": {
    "full_name": "Juan Pérez",
    "is_holder": true,
    "plan_name": "Plan Personal"
  },
  "beneficiaries": [
    {
      "document": "V-87654321",
      "full_name": "María Pérez",
      "relationship": "ESPOSA"
    }
  ],
  "agreements": [
    { "service_category": "CONSULTA", "discount_percent": 30 },
    { "service_category": "EXAMEN", "discount_percent": 25 }
  ],
  "warnings": []
}
```

### Response 200 con membresía vencida

```json
{
  "document": "V-12345678",
  "is_valid": false,
  "status": "SUSPENDED",
  "member": {
    "full_name": "Juan Pérez",
    "is_holder": true,
    "plan_name": "Plan Personal"
  },
  "agreements": [],
  "warnings": [
    "Membresía suspendida desde 2026-05-10 por impago"
  ]
}
```

### Response 404 (cédula no afiliada)

```json
{
  "type": "https://api.dominio.com/errors/not-found",
  "title": "Resource not found",
  "status": 404,
  "detail": "No member found with document V-12345678",
  "instance": "/v1/ally/validate/V-12345678"
}
```

## Implementación

### Controller

```java
@RestController
@RequestMapping("/v1/ally")
@RequiredArgsConstructor
@Tag(name = "Ally Validator")
public class AllyValidatorController {

    private final MemberValidatorService validatorService;
    private final BenefitUsageService usageService;

    @GetMapping("/validate/{document}")
    @PreAuthorize("hasAuthority('ALLY_VALIDATE_MEMBER')")
    @Operation(
        summary = "Validate member by document number",
        description = "Returns validity, plan, applicable discounts. Cached 60s. Target p95 < 200ms."
    )
    public ValidationResultDTO validate(
        @PathVariable @VenezuelanID String document,
        Authentication auth
    ) {
        UUID allyId = extractAllyId(auth);
        rateLimitGuard.checkAllyValidations(allyId);
        return validatorService.validate(document, allyId);
    }

    @PostMapping("/benefit-usage")
    @PreAuthorize("hasAuthority('ALLY_REGISTER_USAGE')")
    public BenefitUsageDTO registerUsage(@Valid @RequestBody BenefitUsageCreateDTO dto, Authentication auth) {
        UUID allyUserId = extractAllyUserId(auth);
        return usageService.register(dto, allyUserId);
    }
}
```

### Service

```java
@Service
@RequiredArgsConstructor
public class MemberValidatorService {

    private final MemberRepository memberRepository;
    private final AllyAgreementRepository agreementRepository;
    private final RedisTemplate<String, ValidationResultDTO> redis;

    public ValidationResultDTO validate(String document, UUID allyId) {
        String cacheKey = "validator:" + document + ":ally:" + allyId;
        ValidationResultDTO cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        ValidationResultDTO result = computeValidation(document, allyId);
        redis.opsForValue().set(cacheKey, result, Duration.ofSeconds(60));
        return result;
    }

    private ValidationResultDTO computeValidation(String document, UUID allyId) {
        Member member = memberRepository.findByDocumentNumberWithMembershipAndPlan(document)
            .orElseThrow(() -> new ResourceNotFoundException("Member", document));

        Membership active = member.getActiveMembership()
            .orElse(null);

        if (active == null) {
            return ValidationResultDTO.notValid(document, member, "No active membership");
        }

        List<AllyAgreement> agreements = agreementRepository.findActiveByAllyId(allyId);

        return ValidationResultDTO.builder()
            .document(document)
            .isValid(active.getStatus().equals("ACTIVE"))
            .status(active.getStatus())
            .member(toMemberDTO(member, active))
            .beneficiaries(toBeneficiaryDTOs(member))
            .agreements(toAgreementDTOs(agreements))
            .warnings(buildWarnings(active))
            .build();
    }
}
```

### Invalidación de cache

```java
// En PaymentService.approve():
@CacheEvict(value = "validator", key = "#payment.member.documentNumber")
public PaymentDTO approve(UUID paymentId) {
    // ...
}

// En MembershipService.cancel() / reactivate() / changeStatus():
// Similar @CacheEvict

// En BeneficiaryService.add() / remove():
// Invalidar también el cache del titular
```

## Optimización para performance

### Query óptima

```java
@Query("""
    SELECT m FROM Member m
    LEFT JOIN FETCH m.beneficiaries b
    LEFT JOIN FETCH m.activeMemberships am
    LEFT JOIN FETCH am.plan p
    WHERE m.documentNumber = :document
      AND m.isActive = true
""")
Optional<Member> findByDocumentNumberWithMembershipAndPlan(String document);
```

### Índices DB

```sql
CREATE INDEX idx_members_document_active ON members(document_number) WHERE is_active = true;
CREATE INDEX idx_memberships_member_status ON memberships(member_id, status) WHERE is_active = true;
CREATE INDEX idx_ally_agreements_ally_active ON ally_agreements(ally_id, valid_from, valid_to) WHERE is_active = true;
```

### Cache hot path

- Cache key incluye `allyId` porque los descuentos varían por aliado
- TTL 60s — balance entre frescura y performance
- Invalidación inmediata en eventos críticos

## Rate limiting

Por aliado:
- Default: 1.000 validaciones/día
- Configurable por aliado (campo `Ally.max_validations_per_day`)
- Implementación: Redis counter con TTL 24h

```java
@Component
@RequiredArgsConstructor
public class AllyValidationRateLimit {

    private final StringRedisTemplate redis;
    private final AllyRepository allyRepository;

    public void check(UUID allyId) {
        Ally ally = allyRepository.findById(allyId)
            .orElseThrow(() -> new ResourceNotFoundException("Ally", allyId));
        Long limit = ally.getMaxValidationsPerDay();
        if (limit == null) limit = 1000L;

        String key = "ratelimit:ally:" + allyId + ":validations:" + LocalDate.now();
        Long count = redis.opsForValue().increment(key);
        if (count == 1L) redis.expire(key, Duration.ofDays(1));

        if (count > limit) {
            throw new TooManyRequestsException(
                "Ally " + ally.getName() + " exceeded daily validation limit of " + limit
            );
        }
    }
}
```

## Métricas

Exponer en Actuator + custom metrics:
- `validator.requests.count` (counter por aliado)
- `validator.cache.hits` / `.misses` (cache hit rate)
- `validator.latency` (histogram p50/p95/p99)
- `validator.404` (counter de documentos no encontrados — útil detectar issues)

Alertas:
- p95 > 200ms sostenido 5min → notificar
- Cache hit rate < 70% → investigar

## Auditoría

Cada validación se loguea en `audit_log` con `action = 'VALIDATE_MEMBER'`:

```java
@AuditAction(action = "VALIDATE_MEMBER", entity = "Member", entityIdExpression = "#member.id")
public ValidationResultDTO validate(String document, UUID allyId) { /* ... */ }
```

Importante para:
- Detectar abuso (aliado validando masivamente)
- Trazabilidad si el cliente reclama "este aliado nos cobró indebidamente"

## Tests

### Performance

```java
@SpringBootTest
class ValidatorPerformanceTest {

    @Test
    void validate_p95Under200ms() {
        // Precargar 1K membresías
        List<Long> latencies = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            long start = System.nanoTime();
            validatorService.validate("V-" + i, allyId);
            latencies.add((System.nanoTime() - start) / 1_000_000);
        }
        Collections.sort(latencies);
        long p95 = latencies.get(95);
        assertThat(p95).isLessThan(200);
    }
}
```

### Carga con k6 (externo)

```javascript
// k6 script
import http from 'k6/http';
export const options = { vus: 100, duration: '30s' };
export default function() {
  http.get(`https://api.dominio.com/v1/ally/validate/V-${Math.floor(Math.random() * 1000)}`,
    { headers: { Authorization: 'Bearer ' + __ENV.TOKEN }}
  );
}
// Objetivo: p95 < 200ms a 1000 RPS
```

## Privacidad

**NUNCA** exponer:
- Antecedentes médicos / preexistencias / alergias
- Historial de pagos
- Comisiones del promotor
- Datos del cónyuge si no es beneficiario activo

DTO `ValidationResultDTO` cuidadosamente diseñada para incluir SÓLO lo necesario para validar atención.

## Referencias

- [hub `business-rules.md` sección "Validador"](../../../centro-optico-vicente/.ai/context/business-rules.md)
- [07-cache.md](07-cache.md)
- [04-security.md](04-security.md)
- [05-roles-permissions.md](05-roles-permissions.md)

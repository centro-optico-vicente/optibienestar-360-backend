# 11 — Workflow de pagos manuales

> Implementa [ADR 0008 cross-stack](../../../centro-optico-vicente/.ai/decisions/0008-manual-payments.md).
>
> Reglas de negocio en [hub `business-rules.md` sección "Workflow"](../../../centro-optico-vicente/.ai/context/business-rules.md).

## Estados de Payment

```
PENDING_REVIEW ──aprobar──► APPROVED
              │
              └──rechazar──► REJECTED
```

Una vez en APPROVED o REJECTED, no se puede cambiar (immutable). Si hay error, crear nuevo payment.

## Workflow completo

### 1. Registro inicial

`POST /v1/admin/payments` (multipart):

```json
{
  "membership_id": "uuid",
  "amount": 5.00,
  "method": "ZELLE",
  "reference": "1234",
  "notes": "Pago de Mayo"
}
+ file: support (multipart)
```

```java
@PostMapping(consumes = MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasAuthority('PAYMENT_REGISTER')")
public PaymentDTO register(
    @Valid @RequestPart("data") PaymentCreateDTO dto,
    @RequestPart("file") @ValidatedFile(maxSizeMB = 3) MultipartFile supportFile
) {
    String key = storageService.upload(
        "payments/" + dto.membershipId() + "/" + UUID.randomUUID() + "_" + supportFile.getOriginalFilename(),
        supportFile
    );
    return service.register(dto, key);
}
```

Service:
```java
@Transactional
public PaymentDTO register(PaymentCreateDTO dto, String supportFileKey) {
    Membership ms = membershipRepository.findById(dto.membershipId())
        .orElseThrow(() -> new ResourceNotFoundException("Membership", dto.membershipId()));

    Payment p = new Payment();
    p.setMembership(ms);
    p.setAmount(dto.amount());
    p.setMethod(PaymentMethod.valueOf(dto.method()));
    p.setReference(dto.reference());
    p.setSupportFileKey(supportFileKey);
    p.setStatus("PENDING_REVIEW");
    p.setRegisteredBy(currentUserId());
    p.setNotes(dto.notes());
    p = paymentRepository.save(p);

    // Email al afiliado: "recibimos tu pago, lo revisaremos pronto"
    notificationService.enqueue(/* template "payment-received" */);

    return mapper.toDTO(p);
}
```

### 2. Vista del soporte

`GET /v1/admin/payments/{id}/support`:

```java
@GetMapping("/{id}/support")
@PreAuthorize("hasAnyAuthority('PAYMENT_VIEW_ALL') or @paymentSecurity.canRead(#id, authentication)")
public Map<String, Object> getSupportUrl(@PathVariable UUID id) {
    Payment p = paymentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
    URL url = storageService.generatePresignedUrl(p.getSupportFileKey(), Duration.ofMinutes(5));
    return Map.of("url", url.toString(), "expires_in", 300);
}
```

### 3. Aprobación

`PUT /v1/admin/payments/{id}/approve`:

```java
@PutMapping("/{id}/approve")
@PreAuthorize("hasAuthority('PAYMENT_APPROVE')")
@AuditAction(action = "PAYMENT_APPROVED", entity = "Payment", entityIdExpression = "#id")
public PaymentDTO approve(@PathVariable UUID id, @Valid @RequestBody ApprovalDTO dto) {
    return service.approve(id, dto);
}
```

```java
@Transactional
public PaymentDTO approve(UUID paymentId, ApprovalDTO dto) {
    Payment p = paymentRepository.findById(paymentId)
        .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

    if (!p.getStatus().equals("PENDING_REVIEW")) {
        throw new BusinessRuleException("Cannot approve payment in status " + p.getStatus());
    }

    p.setStatus("APPROVED");
    p.setReviewedBy(currentUserId());
    p.setReviewedAt(Instant.now());
    p.setNotes(dto.notes());

    Membership ms = p.getMembership();
    ms.setLastPaymentDate(LocalDate.now());
    ms.setNextDueDate(ms.getNextDueDate().plusMonths(1));
    if (ms.getStatus().equals("SUSPENDED")) {
        ms.setStatus("ACTIVE");
    }

    // Crear comisión si aplica (sólo pago inicial de membresía nueva)
    boolean isInitialPayment = paymentRepository.countApprovedForMembership(ms.getId()) == 0;
    if (isInitialPayment && ms.getMember().getPromoter() != null) {
        commissionService.createForInitialPayment(p);
    }

    // Aplicar referido si tiene
    if (isInitialPayment && ms.getMember().getReferral() != null) {
        referralService.applyReward(ms.getMember().getReferral());
    }

    // Invalidar cache validador
    redis.delete("validator:" + ms.getMember().getDocumentNumber() + "*");

    // Email recibo
    notificationService.enqueue(/* template "payment-approved" */);

    return mapper.toDTO(p);
}
```

### 4. Rechazo

`PUT /v1/admin/payments/{id}/reject`:

```java
@Transactional
public PaymentDTO reject(UUID paymentId, RejectionDTO dto) {
    Payment p = paymentRepository.findById(paymentId)
        .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

    if (!p.getStatus().equals("PENDING_REVIEW")) {
        throw new BusinessRuleException("Cannot reject payment in status " + p.getStatus());
    }

    p.setStatus("REJECTED");
    p.setReviewedBy(currentUserId());
    p.setReviewedAt(Instant.now());
    p.setNotes(dto.reason());

    // Email al afiliado
    notificationService.enqueue(/* template "payment-rejected" con motivo */);

    return mapper.toDTO(p);
}
```

## Listado con filtros

`GET /v1/admin/payments?filter=status==PENDING_REVIEW;method==ZELLE&page=0&size=20&sort=createdAt,desc`

Filtros típicos:
- `status==PENDING_REVIEW` (cola)
- `membership.member.documentNumber==V-12345678`
- `paymentDate=ge=2026-05-01;paymentDate=le=2026-05-31` (mes específico)
- `amount=ge=10` (importes grandes)
- `reviewedBy==<userId>` (auditoría por operador)

## Reportes

Endpoints adicionales para reportes:
- `GET /v1/admin/payments/summary?period=monthly` (totales por mes)
- `GET /v1/admin/payments/export?format=csv&filter=...`

## Endpoint para el afiliado

`GET /v1/me/payments?page=0&size=10` — afiliado ve sus propios pagos (de todas sus membresías).

## Reglas críticas

1. **Idempotency obligatoria** en POST (con `X-Idempotency-Key`) para evitar duplicar pagos si la red falla.
2. **Soporte file obligatorio** excepto para método EFECTIVO (configurable).
3. **Monto > 0** validado.
4. **Membership debe existir y no estar CANCELLED** (puede estar SUSPENDED — un pago la reactivaría).
5. **Comisión sólo en pago inicial** (`paymentRepository.countApprovedForMembership(ms.getId()) == 0`).

## Concurrencia

Si dos operadores intentan aprobar el mismo pago simultáneamente:
- Versión optimista (`@Version` en Payment entity) → uno gana, otro recibe `OptimisticLockException` → mostrar mensaje "ya fue aprobado por otro operador"

```java
@Version
@Column(name = "version")
private Long version;
```

## Casos especiales

### Pago a una membresía CANCELLED

No permitido. Operador debe crear nueva membresía si el afiliado quiere reanudar.

### Pago parcial

NO soportado en FASE 5 — el monto debe ser exactamente la mensualidad (o múltiplo si paga adelantado). Si el cliente quiere pagos parciales, abrir ADR.

### Pago adelantado (varios meses)

Soportado: `amount = N * monthly_fee`. El servicio calcula `next_due_date = currentNextDue.plusMonths(N)`.

### Pago para reactivar suspended

Aplicar normal: aprobar suma 1 mes desde la fecha actual.

### Membresía EXPIRED

NO se puede pagar para reactivarla directamente. Hay que crear nueva membresía (paga inscripción $10 nuevamente). Validar en service.

## Referencias

- [ADR 0008 Manual payments](../../../centro-optico-vicente/.ai/decisions/0008-manual-payments.md)
- [hub `business-rules.md`](../../../centro-optico-vicente/.ai/context/business-rules.md)
- [12-commissions.md](12-commissions.md)
- [10-validators.md](10-validators.md) — invalidación cache

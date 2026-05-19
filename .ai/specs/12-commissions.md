# 12 — Comisiones de promotores + referidos

> Reglas de negocio en [hub `business-rules.md` sección "Comisiones"](../../../centro-optico-vicente/.ai/context/business-rules.md).

## Modelo

### Promoter

```java
@Entity
@Table(name = "promoters")
public class Promoter extends BaseEntity {
    @Id
    @Column(name = "promoter_id")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    private PromoterType type;  // USUARIO_FINAL, EMPRESARIAL, COMUNITARIO

    @Column(name = "zone", length = 100)
    private String zone;

    @Column(name = "commission_rules", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> commissionRules;

    // ...
}
```

### Estructura JSON de `commission_rules`

```json
{
  "by_plan_type": {
    "PERSONAL":     { "type": "PERCENT", "value": 20 },
    "EMPRESARIAL":  { "type": "PERCENT", "value": 15 },
    "UNIVERSITARIO":{ "type": "FLAT", "value": 1.50 },
    "COMUNITARIO":  { "type": "FLAT", "value": 1.50 }
  }
}
```

Si falta una clave para algún tipo de plan, default `{ "type": "PERCENT", "value": 0 }` (no genera comisión).

### Commission

```java
@Entity
@Table(name = "commissions")
public class Commission extends BaseEntity {
    @Id
    @Column(name = "commission_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoter_id", nullable = false)
    private Promoter promoter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "cycle_end")
    private LocalDate cycleEnd;

    @Column(name = "paid_at")
    private Instant paidAt;

    // status: PENDING, PAID, CANCELLED
}
```

## CommissionService

```java
@Service
@RequiredArgsConstructor
public class CommissionService {

    private final CommissionRepository commissionRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public Commission createForInitialPayment(Payment payment) {
        Member member = payment.getMembership().getMember();
        Promoter promoter = member.getPromoter();
        if (promoter == null) return null;

        Plan plan = payment.getMembership().getPlan();
        Map<String, Object> rules = promoter.getCommissionRules();
        Map<String, Object> rule = (Map<String, Object>) ((Map) rules.get("by_plan_type")).get(plan.getType().name());
        if (rule == null) return null;

        BigDecimal amount = calculateAmount(payment.getAmount(), rule);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return null;

        Commission c = new Commission();
        c.setPromoter(promoter);
        c.setPayment(payment);
        c.setMembership(payment.getMembership());
        c.setAmount(amount);
        c.setStatus("PENDING");
        return commissionRepository.save(c);
    }

    private BigDecimal calculateAmount(BigDecimal paymentAmount, Map<String, Object> rule) {
        String type = (String) rule.get("type");
        BigDecimal value = new BigDecimal(rule.get("value").toString());
        return switch (type) {
            case "PERCENT" -> paymentAmount.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case "FLAT" -> value;
            default -> BigDecimal.ZERO;
        };
    }
}
```

## Cierre de ciclo (payout)

`POST /v1/admin/commissions/payout?cycle_end=2026-05-31`:

```java
@PostMapping("/payout")
@PreAuthorize("hasAuthority('COMMISSION_PAYOUT')")
public PayoutReportDTO payout(@RequestParam @FutureOrPresent LocalDate cycleEnd) {
    return service.executePayout(cycleEnd);
}
```

```java
@Transactional
public PayoutReportDTO executePayout(LocalDate cycleEnd) {
    List<Commission> pending = commissionRepository.findByStatusAndCreatedAtLessThanEqual(
        "PENDING", cycleEnd.atTime(23, 59, 59).toInstant(ZoneOffset.UTC)
    );

    Instant now = Instant.now();
    for (Commission c : pending) {
        c.setStatus("PAID");
        c.setCycleEnd(cycleEnd);
        c.setPaidAt(now);
    }

    // Generar reporte por promotor
    Map<Promoter, List<Commission>> byPromoter = pending.stream()
        .collect(Collectors.groupingBy(Commission::getPromoter));

    List<PayoutReportItem> items = byPromoter.entrySet().stream()
        .map(e -> new PayoutReportItem(
            e.getKey().getId(),
            e.getKey().getUser().getFullName(),
            e.getValue().stream().map(Commission::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
            e.getValue().size()
        ))
        .toList();

    // Enviar emails con resumen a cada promotor
    for (PayoutReportItem item : items) {
        notificationService.enqueue(/* template "commission-payout" */);
    }

    return new PayoutReportDTO(cycleEnd, items, pending.size());
}
```

## Endpoints

| Endpoint | Método | Rol | Descripción |
|---|---|---|---|
| `/v1/admin/promoters` | GET, POST | OPERADOR | CRUD promotores |
| `/v1/admin/promoters/{id}` | GET, PUT, DELETE | OPERADOR | CRUD |
| `/v1/admin/commissions` | GET | OPERADOR | Listado con RSQL |
| `/v1/admin/commissions/payout` | POST | ADMIN | Cierre de ciclo |
| `/v1/admin/commissions/export?cycle_end=...` | GET | OPERADOR | CSV/XLSX |
| `/v1/promoter/dashboard` | GET | PROMOTOR | Vista propia |
| `/v1/promoter/commissions` | GET | PROMOTOR | Comisiones propias |
| `/v1/promoter/members` | GET | PROMOTOR | Sus afiliados |

## Referrals (códigos de referido)

### Referral entity

```java
@Entity
@Table(name = "referrals")
public class Referral extends BaseEntity {
    @Id
    @Column(name = "referral_id")
    private UUID id;

    @Column(name = "referral_code", nullable = false, unique = true, length = 20)
    private String referralCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_member_id", nullable = false)
    private Member referrerMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_member_id")
    private Member referredMember;  // null hasta que se use

    @Column(name = "reward_type", nullable = false, length = 30)
    private String rewardType;  // DISCOUNT_PERCENT, FLAT_AMOUNT

    @Column(name = "reward_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal rewardValue;

    @Column(name = "applied")
    private Boolean applied = false;

    @Column(name = "applied_at")
    private Instant appliedAt;
}
```

### ReferralService

```java
@Service
@RequiredArgsConstructor
public class ReferralService {

    private final ReferralRepository repository;

    public Referral generateCodeForMember(Member member) {
        String code = generateUniqueCode(member);
        Referral r = new Referral();
        r.setReferralCode(code);
        r.setReferrerMember(member);
        r.setRewardType("DISCOUNT_PERCENT");
        r.setRewardValue(BigDecimal.valueOf(10));  // 10% descuento default
        r.setApplied(false);
        return repository.save(r);
    }

    @Transactional
    public void linkReferralOnSignup(Member newMember, String referralCode) {
        Referral r = repository.findByReferralCodeAndAppliedFalse(referralCode)
            .orElseThrow(() -> new BusinessRuleException("Referral code not valid"));
        r.setReferredMember(newMember);
    }

    @Transactional
    public void applyReward(Referral referral) {
        if (referral.getApplied()) return;
        // aplicar el premio al referrer:
        // si DISCOUNT_PERCENT: aplicar descuento en próxima mensualidad
        // si FLAT_AMOUNT: acreditar saldo o pagar en próximo payout
        referral.setApplied(true);
        referral.setAppliedAt(Instant.now());

        notificationService.enqueue(/* template "referral-reward" */);
    }

    private String generateUniqueCode(Member member) {
        // ej. primer apellido + 4 dígitos random únicos
        String prefix = member.getFullName().split(" ")[0].toUpperCase().substring(0, Math.min(5, member.getFullName().length()));
        for (int i = 0; i < 10; i++) {
            String candidate = prefix + ThreadLocalRandom.current().nextInt(1000, 9999);
            if (!repository.existsByReferralCode(candidate)) return candidate;
        }
        throw new IllegalStateException("Cannot generate unique referral code");
    }
}
```

## Reportes

- Por promotor: total comisiones (PENDING vs PAID), número de afiliados captados
- Por mes: total comisiones generadas, total pagado
- Top 10 promotores del mes

## Referencias

- [hub `business-rules.md`](../../../centro-optico-vicente/.ai/context/business-rules.md)
- [11-billing-manual.md](11-billing-manual.md) — cuándo se crea la comisión

# Alcance 7 — Validador en tiempo real

> **CRÍTICO: p95 < 200ms.** El aliado verifica vigencia del afiliado antes de aplicar descuento.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [x] [P0/C3] `V24__benefit_usages.sql` _(Schema: `membership_id` FK + `ally_id` FK + `ally_service_id` FK NULL (catálogo opcional) + `ally_user_id` FK NULL (operador ally que registró) + dual time (`usage_date` calendar + `usage_datetime` precise instant) + **copay** (`copay_amount`+`copay_currency` pareados via CHECK) + **`metadata JSONB`** (per-ally type payload: doctorName, diagnosis, prescriptionRx, frameModel, medication, etc — sin tabla por tipo, mismo precedente de `scheduled_job_runs.summary`) + `notes` TEXT + status CHECK (REGISTERED/REVERSED/DISPUTED, REGISTERED default) + audit BaseEntity. **No snapshot de status/pricing** — la membership row es source of truth; el service refusa si no es ACTIVE (no quota table, las caps del plan son agregación service-side). **CHECK constraints**: (1) copay paired (NULL/NULL ó valor+currency), (2) datetime aligned con usage_date ±1-2 días (anti drift), (3) not future (≤ today+1d). **4 índices**: `(membership_id, usage_date DESC)` historial afiliado; `(ally_id, usage_date DESC)` dashboards aliado; partial `(ally_service_id, usage_date DESC) WHERE ally_service_id IS NOT NULL` analytics cross-ally por categoría; partial `(status, usage_date DESC) WHERE is_active` para el queue de admin filtrado. contextLoads V1..V24 limpio.)_
- [ ] [P0/C2] Índices en `benefit_usages` según EXPLAIN ANALYZE

## Código

- [ ] [P0/C3] `GET /v1/ally/validate/{document}` — cache Redis `validator:{document}` TTL 60s
- [ ] [P0/C3] Invalidación de cache al cambiar status de membresía o aprobar/rechazar pago
- [ ] [P0/C2] `POST /v1/ally/benefit-usage`
- [ ] [P0/C2] `GET /v1/ally/usage-history`
- [ ] [P0/C2] Métricas Redis: contador validaciones/día por aliado
- [ ] [P1/C2] Rate limit por aliado (1 000 validaciones/día default)
- [ ] [P1/C3] `GET /v1/admin/usage-metrics`

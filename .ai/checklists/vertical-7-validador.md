# Alcance 7 — Validador en tiempo real

> **CRÍTICO: p95 < 200ms.** El aliado verifica vigencia del afiliado antes de aplicar descuento.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [ ] [P0/C3] `V24__benefit_usages.sql`
- [ ] [P0/C2] Índices en `benefit_usages` según EXPLAIN ANALYZE

## Código

- [ ] [P0/C3] `GET /v1/ally/validate/{document}` — cache Redis `validator:{document}` TTL 60s
- [ ] [P0/C3] Invalidación de cache al cambiar status de membresía o aprobar/rechazar pago
- [ ] [P0/C2] `POST /v1/ally/benefit-usage`
- [ ] [P0/C2] `GET /v1/ally/usage-history`
- [ ] [P0/C2] Métricas Redis: contador validaciones/día por aliado
- [ ] [P1/C2] Rate limit por aliado (1 000 validaciones/día default)
- [ ] [P1/C3] `GET /v1/admin/usage-metrics`

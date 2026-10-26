# Playbook — Verificar el FK `commissions.commission_tier_id`

> Contexto: el FK `fk_commissions_tier` (→ `commission_tiers`, NO ACTION) lo creó **V42** ya validado; `V193` solo agregó el índice `idx_commissions_tier`. Como el FK nació validado, no pueden existir huérfanos. Este playbook es una verificación de respaldo (test/prod), solo lectura salvo el paso 3.

## 1. Estado del constraint e índice

```sql
SELECT conname, convalidated, confdeltype   -- esperado: true, 'a' (NO ACTION)
FROM pg_constraint
WHERE conname = 'fk_commissions_tier' AND conrelid = 'app.commissions'::regclass;

SELECT indexname FROM pg_indexes
WHERE schemaname = 'app' AND tablename = 'commissions' AND indexname = 'idx_commissions_tier';
```

## 2. Diagnóstico de huérfanos (esperado: 0 filas)

```sql
SELECT c.commissions_id, c.uuid, c.status, c.commission_tier_id
FROM commissions c
LEFT JOIN commission_tiers t ON t.commission_tiers_id = c.commission_tier_id
WHERE c.commission_tier_id IS NOT NULL AND t.commission_tiers_id IS NULL;
```

## 3. Solo si `convalidated = false` (no debería ocurrir)

1. Si el paso 2 devuelve filas, **no borrar ni anular comisiones**: decidir caso por caso (restaurar el tier borrado o reasignar a un tier existente con aprobación del dueño).
2. Con 0 huérfanos: `ALTER TABLE commissions VALIDATE CONSTRAINT fk_commissions_tier;`

## Borrado de tiers

`CommissionTiersService.delete`: soft-delete por defecto; borrado físico solo si `countUsages == 0`. Un tier con comisiones asociadas nunca se puede borrar físicamente (el FK lo rechaza).

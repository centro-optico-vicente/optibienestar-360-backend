SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V82: global default-sort fallback for list endpoints. Sits between an
-- entity's own entity_config.default_sort (V80) and the hard-coded
-- createdAt DESC fallback in code — used when an entity has no
-- entity_config row (or none with default_sort set) at all.
--
-- Unlike entity_config.default_sort (validated against that specific
-- entity's own sortable-fields map), this one applies generically across
-- every entity, so it's restricted at the application layer to columns
-- common to (almost) every table: id, uuid, createdAt, updatedAt,
-- createdBy, updatedBy. See CommonSortFields in the backend.
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE app.system_configs ADD COLUMN default_sort JSONB;

COMMENT ON COLUMN app.system_configs.default_sort IS
  'Global fallback default sort, same shape as entity_config.default_sort: '
  '[{"field":"createdAt","direction":"DESC"}]. Restricted to common columns '
  '(id, uuid, createdAt, updatedAt, createdBy, updatedBy) since it is not '
  'validated against any single entity''s field set. NULL means "no global '
  'default configured" — callers fall back to createdAt DESC in code.';

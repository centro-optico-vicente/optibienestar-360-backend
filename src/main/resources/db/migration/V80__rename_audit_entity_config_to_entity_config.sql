-- Renames audit_entity_config (V60) to entity_config: it now covers more
-- than audit toggles — the default sort order applied when a list endpoint
-- gets no explicit ?sort=. V60 itself is left untouched (already applied in
-- other environments; Flyway rejects a changed checksum on an applied
-- migration).
ALTER TABLE app.audit_entity_config RENAME TO entity_config;
ALTER TABLE app.entity_config RENAME COLUMN audit_entity_config_id TO entity_config_id;

ALTER TABLE app.entity_config ADD COLUMN default_sort JSONB;

COMMENT ON TABLE app.entity_config IS
  'Per-entity behavior config: audit toggles (original V60 scope) + default '
  'list sort (V80). entity_key must match the "entity" attribute of the '
  '@Auditable annotation on the corresponding service method.';

COMMENT ON COLUMN app.entity_config.default_sort IS
  'Ordered array of {"field": "<sortable key>", "direction": "ASC"|"DESC"}, '
  'e.g. [{"field":"name","direction":"ASC"},{"field":"allyTypeName","direction":"ASC"}]. '
  'NULL falls back to the hard default createdAt DESC. "field" is the key '
  'the frontend table sends (matches SortFieldValidator.SORTABLE_FIELDS), '
  'not a raw JPA path.';

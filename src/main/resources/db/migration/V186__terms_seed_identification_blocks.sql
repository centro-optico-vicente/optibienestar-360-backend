SET search_path TO app, public;

-- ============================================================================
-- V186: restore the identification blocks ("anverso") of the seeded contract
-- templates, now that TermsTemplateRenderer resolves per-entity variables
-- (member.*, membership.*, beneficiaries.*, advisor.*, ally.*, agreement.*).
--
-- V181 seeded version 1 of each term_type with the client drafts minus their
-- HTML comment blocks (the anverso and the signature block), because the
-- per-person data could not be rendered yet. This migration prepends an
-- identification block and appends a signature block, both built only from
-- variables of that type; the body of the contract is kept byte for byte.
--
-- Safety: a version is only rewritten when ALL of these hold —
--   * it is version 1 of its type (earliest unscoped row);
--   * its content still equals the V181 seed (md5 of the seed text);
--   * nobody has accepted it yet (no terms_acceptances row).
-- A text someone already accepted is never changed. In any other case the
-- row is left as is and a NOTICE says why: the admin publishes a new version
-- with the identification block instead (the editor lists the variables).
-- ============================================================================

DO $do$
DECLARE
    r          RECORD;
    v_id       BIGINT;
    v_md5      TEXT;
    v_restored INT := 0;
BEGIN
    FOR r IN
        SELECT *
        FROM (VALUES
            ('AFILIADO',
             '85956527b818761304bccf3534bfafde',
             $h$**I. REGISTRO OPERATIVO**

- **Fecha de activación:** {{membership.activatedAt}}
- **Plan seleccionado:** {{membership.planName}}
- **Beneficiarios registrados:** {{beneficiaries.count}}

**II. DATOS DEL AFILIADO TITULAR**

- **Nombre completo:** {{member.fullName}}
- **Cédula de identidad:** {{member.document}}
- **Fecha de nacimiento:** {{member.birthDate}}

---

$h$,
             $f$

---

**Por EL AFILIADO TITULAR:** {{member.fullName}} · C.I. {{member.document}} · Fecha de activación: {{membership.activatedAt}}$f$),
            ('ASESOR',
             '08439564d1efa7173a9d061842d34f77',
             $h$**Nº de expediente:** {{advisor.code}}

**I. REGISTRO**

- **Fecha de ingreso:** {{advisor.joinedAt}}
- **Código del asesor comercial:** {{advisor.code}}
- **Región / zona asignada:** {{advisor.zone}}
- **Rango:** {{advisor.rank}}

**II. IDENTIDAD PERSONAL**

- **Nombre completo:** {{advisor.fullName}}
- **Cédula de identidad:** {{advisor.document}}

---

$h$,
             $f$

---

**Por EL ASESOR COMERCIAL:** {{advisor.fullName}} · C.I. {{advisor.document}}$f$),
            ('ALIADO',
             'eb24de24904f1e70b6fc9530ac80297f',
             $h$**Nº de expediente:** {{ally.fileNumber}}

**DATOS DEL ALIADO**

- **Nombre o razón social:** {{ally.name}}
- **RIF:** {{ally.taxId}}
- **Representante:** {{ally.representativeName}} · C.I./RIF {{ally.representativeDocument}}
- **Inicio del convenio:** {{agreement.startDate}}
- **Renovación automática:** {{agreement.autoRenew}}
- **Preaviso de rescisión:** {{agreement.noticeDays}} días

---

$h$,
             $f$

---

**Por EL ALIADO COMERCIAL / PROFESIONAL:** {{ally.representativeName}} · C.I./RIF {{ally.representativeDocument}}$f$)
        ) AS t(term_type, seed_md5, header, footer)
    LOOP
        v_id := NULL;
        SELECT tv.terms_versions_id, md5(tv.content_markdown)
        INTO v_id, v_md5
        FROM terms_versions tv
        WHERE tv.term_type = r.term_type
          AND tv.ally_type_id IS NULL
          AND tv.segment IS NULL
          AND tv.ally_id IS NULL
        ORDER BY tv.valid_from, tv.terms_versions_id
        LIMIT 1;

        IF v_id IS NULL THEN
            RAISE NOTICE 'V186: % has no version; nothing to restore', r.term_type;
            CONTINUE;
        END IF;

        IF v_md5 <> r.seed_md5 THEN
            RAISE NOTICE 'V186: % version 1 (id %) no longer equals the V181 seed; left as is — publish a new version to add the identification block',
                r.term_type, v_id;
            CONTINUE;
        END IF;

        IF EXISTS (SELECT 1 FROM terms_acceptances ta WHERE ta.terms_versions_id = v_id) THEN
            RAISE NOTICE 'V186: % version 1 (id %) already has acceptances; left as is — publish a new version to add the identification block',
                r.term_type, v_id;
            CONTINUE;
        END IF;

        UPDATE terms_versions
        SET content_markdown = r.header || content_markdown || r.footer
        WHERE terms_versions_id = v_id;

        v_restored := v_restored + 1;
        RAISE NOTICE 'V186: % version 1 (id %) identification and signature blocks restored', r.term_type, v_id;
    END LOOP;

    RAISE NOTICE 'V186: % of 3 seeded templates restored', v_restored;
END $do$;

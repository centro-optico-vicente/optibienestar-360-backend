SET search_path TO app, public;

-- ============================================================================
-- V264: accept office documents and plain-text tables in the upload policies.
-- CONFIDENTIAL (payment proofs, member documents, affiliation / ally request
-- supports) and TEMPORARY now also take Word, Excel, LibreOffice, RTF, CSV,
-- TSV and TXT. Every extension listed here has a content signature in
-- FileValidationService: an ALLOWLIST extension without one is refused.
-- PUBLIC stays images-only (rendered on public pages) and INTERNAL keeps its
-- DENYLIST. Sizes are unchanged.
-- ============================================================================

UPDATE file_type_policies
SET extensions = ARRAY['pdf', 'jpg', 'jpeg', 'png',
                       'doc', 'docx', 'odt', 'rtf',
                       'xls', 'xlsx', 'ods', 'csv', 'tsv', 'txt']
WHERE visibility = 'CONFIDENTIAL';

UPDATE file_type_policies
SET extensions = ARRAY['pdf',
                       'doc', 'docx', 'odt', 'rtf',
                       'xls', 'xlsx', 'ods', 'csv', 'tsv', 'txt']
WHERE visibility = 'TEMPORARY';

-- Fixes the "DOCUMENTS DE BORD" checklist on generated contract PDFs, which
-- previously rendered five hardcoded, always-unchecked boxes (Carte grise,
-- Assurance, Vignette, Visite technique, Autorisation de circulation) with
-- no data behind them at all.
--
-- 1. vehicles.vignette_expiration — the one document of the five with no
--    existing column anywhere (insurance/technical-inspection/circulation-
--    authorization/registration already had expiry-date columns, just never
--    surfaced in the vehicle form or read by the PDF).
--
-- 2. contracts.document_* — a snapshot of "does this vehicle have this
--    document on file" (expiry date present = present), captured once at
--    contract-creation time and never silently changed afterward. This is
--    what the PDF actually renders, so a later edit to the vehicle record
--    can never retroactively alter an already-generated/signed contract's
--    checklist. Booleans, not the vehicle's dates directly, because a
--    contract's checklist must survive the vehicle being edited or even
--    deleted later.
--
-- Safe defaults: NOT NULL DEFAULT FALSE for the new contract columns (no
-- existing contract claims to have a document it never recorded), NULL-able
-- for the new vehicle column (unknown, not "expired"). Existing contracts
-- are backfilled best-effort from their currently-linked vehicle so the
-- feature is useful immediately, not just for contracts created after this
-- migration — this only affects the new column, never the already-generated
-- PDF file on disk for a signed contract (regeneration for signed contracts
-- was already refused before this migration — see ContractService#isFullySigned).

ALTER TABLE vehicles
    ADD COLUMN vignette_expiration DATE;

ALTER TABLE contracts
    ADD COLUMN document_carte_grise BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN document_assurance BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN document_vignette BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN document_visite_technique BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN document_autorisation_circulation BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE contracts c
SET document_carte_grise = (v.license_expiry_date IS NOT NULL),
    document_assurance = (v.insurance_expiration IS NOT NULL),
    document_vignette = (v.vignette_expiration IS NOT NULL),
    document_visite_technique = (v.technical_inspection_expiration IS NOT NULL),
    document_autorisation_circulation = (v.circulation_authorization_expiry_date IS NOT NULL)
FROM vehicles v
WHERE c.vehicle_id = v.id;

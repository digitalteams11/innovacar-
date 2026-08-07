-- Adds a real expiry to the public client-signature link (contracts.qr_token).
-- Previously the token had no expiry/revocation concept at all — any link
-- ever generated stayed valid forever, and there was no way for the backend
-- to tell "expired" apart from "never existed" (both surfaced as a generic
-- lookup failure). This closes that gap so the public signing page
-- (/contract-sign/*) can show a precise "this link has expired" message
-- instead of a misleading one.
--
-- Nullable + safely backfilled: existing signed/unsigned contracts that
-- already have a qr_token keep working — they get a fresh 30-day window
-- from the moment this migration runs, rather than instantly expiring.
-- Contracts with no qr_token yet are untouched (NULL stays NULL; the expiry
-- is set the moment ContractService.generateQrToken actually issues one).

ALTER TABLE contracts
    ADD COLUMN qr_token_expires_at TIMESTAMP;

UPDATE contracts
SET qr_token_expires_at = CURRENT_TIMESTAMP + INTERVAL '30 days'
WHERE qr_token IS NOT NULL
  AND qr_token_expires_at IS NULL;

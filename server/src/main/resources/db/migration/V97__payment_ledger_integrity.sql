-- Financial Control System, Phase 1: correctness fixes to the existing
-- payment/refund/invoice-cancellation flow (see architecture audit).
--
-- refunded_amount: tracks how much of a Payment has actually been refunded
-- so a PARTIAL refund no longer removes the payment's full amount from
-- "collected" sums (previously PaymentService#refundPayment flipped status
-- straight to REFUNDED regardless of the amount requested, discarding the
-- refund amount entirely and silently over-reducing the contract's paid
-- total).
--
-- idempotency_key: optional, unique per tenant when present — lets a
-- payment-recording client (retry-safe frontend, or a future webhook)
-- prevent the same payment being recorded twice on request retry, using
-- the same unique-constraint + existence-check pattern already used by
-- SubscriptionEvent.whopEventId. NULL for every payment recorded before
-- this migration and for any caller that doesn't yet supply one.
--
-- created_by / created_by_id: who recorded the payment — Payment had no
-- actor column at all before this (ContractAuditLog already captures this
-- for contract-level actions; Payment itself never did).

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS refunded_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS created_by_id BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_tenant_idempotency_key
    ON payments (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

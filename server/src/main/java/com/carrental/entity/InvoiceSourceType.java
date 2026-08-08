package com.carrental.entity;

/**
 * Where an {@link Invoice}'s figures come from.
 *
 * <p>CONTRACT_LINKED: amount/lines are derived from a {@link Contract} and
 * kept in sync by {@code InvoiceService#syncInvoiceForContract} — never
 * hand-edited into disagreeing with the contract's own totals.
 *
 * <p>MANUAL: freely entered by a user (ad-hoc billing), never touched by
 * contract-side recalculation even if a {@code contractId} is later linked
 * for reference (see CreateInvoiceRequest).
 */
public enum InvoiceSourceType {
    CONTRACT_LINKED,
    MANUAL
}

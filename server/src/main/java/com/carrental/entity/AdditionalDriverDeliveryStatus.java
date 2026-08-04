package com.carrental.entity;

/**
 * Real email/WhatsApp delivery outcome for an additional driver's signing
 * link — deliberately separate from {@link SignatureStatus}. SignatureStatus
 * LINK_SENT only means a token/link exists and the signing workflow is
 * pending; it is never proof the email actually reached the provider, let
 * alone the driver's inbox. This is that proof.
 *
 * <p>Named distinctly from the existing {@link DeliveryStatus} (used by
 * {@code ClientInformationRequest}) rather than reusing it — that enum's
 * NOT_REQUESTED/NOT_CONFIGURED values don't apply here, and this workflow
 * needs QUEUED/DELIVERED/BOUNCED states that enum doesn't have.
 */
public enum AdditionalDriverDeliveryStatus {
    NOT_SENT,
    QUEUED,
    SENT,
    DELIVERED,
    BOUNCED,
    FAILED
}

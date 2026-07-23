package com.carrental.entity;

/** Per-channel (email/WhatsApp) delivery outcome for a ClientInformationRequest. */
public enum DeliveryStatus {
    NOT_REQUESTED,   // Channel was not selected/available for this request.
    SENT,            // Provider accepted the message.
    FAILED,          // Provider rejected it or a network/config error occurred.
    NOT_CONFIGURED   // The channel has no working provider configured (e.g. WhatsApp credentials missing).
}

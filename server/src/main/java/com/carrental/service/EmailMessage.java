package com.carrental.service;

/** Provider-agnostic email payload — the same shape SMTP and any HTTP API provider send. */
public record EmailMessage(
        String to,
        String subject,
        String htmlBody,
        String plainBody,
        String attachmentName,
        byte[] attachmentBytes,
        String attachmentContentType) {

    public boolean hasAttachment() {
        return attachmentBytes != null && attachmentBytes.length > 0
                && attachmentName != null && !attachmentName.isBlank();
    }
}

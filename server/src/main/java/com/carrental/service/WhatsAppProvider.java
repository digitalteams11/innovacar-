package com.carrental.service;

/** A WhatsApp delivery backend (Cloud API today; swappable without touching call sites). */
public interface WhatsAppProvider {

    WhatsAppMessagingService.WhatsAppResult send(WhatsAppMessage message);

    boolean isConfigured();

    String label();
}

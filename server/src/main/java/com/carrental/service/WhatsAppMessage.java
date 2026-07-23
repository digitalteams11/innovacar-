package com.carrental.service;

/** Provider-agnostic WhatsApp message payload. */
public record WhatsAppMessage(String toE164Phone, String body) {
}

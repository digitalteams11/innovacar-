package com.carrental.exception;

/**
 * Wraps an internal failure while rendering a PDF (font/layout/IO error) — a genuine
 * server-side fault, never a business conflict. Previously such failures were wrapped
 * in {@link IllegalStateException}, which {@code GlobalExceptionHandler} maps globally
 * to HTTP 409 CONFLICT — so a completely unrelated rendering bug surfaced to the client
 * as a misleading "conflict" (e.g. GET /invoices/{id}/pdf returning 409 for a reason that
 * has nothing to do with duplicate/contested data). This type is mapped to 500 instead.
 */
public class PdfGenerationException extends RuntimeException {
    public PdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

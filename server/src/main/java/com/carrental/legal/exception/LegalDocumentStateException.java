package com.carrental.legal.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Invalid version-state transition — e.g. publishing an already-published/archived version, or editing a non-draft. */
@ResponseStatus(HttpStatus.CONFLICT)
public class LegalDocumentStateException extends RuntimeException {
    public LegalDocumentStateException(String message) {
        super(message);
    }
}

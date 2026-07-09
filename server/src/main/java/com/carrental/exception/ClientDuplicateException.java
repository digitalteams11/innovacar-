package com.carrental.exception;

public class ClientDuplicateException extends RuntimeException {
    private final String errorCode;
    private final String field;
    private final Long existingClientId;
    private final String existingClientName;
    private final String existingClientPhone;
    private final java.util.List<String> matchedFields;

    public ClientDuplicateException(String message) {
        this(message, "CLIENT_DUPLICATE", null, null, null, null, null);
    }

    public ClientDuplicateException(String message, String errorCode, String field) {
        this(message, errorCode, field, null, null, null, null);
    }

    public ClientDuplicateException(String message, String errorCode, String field, Long existingClientId) {
        this(message, errorCode, field, existingClientId, null, null, null);
    }

    public ClientDuplicateException(String message, String errorCode, String field,
                                    Long existingClientId, String existingClientName, String existingClientPhone) {
        this(message, errorCode, field, existingClientId, existingClientName, existingClientPhone, null);
    }

    public ClientDuplicateException(String message, String errorCode, String field,
                                    Long existingClientId, String existingClientName, String existingClientPhone,
                                    java.util.List<String> matchedFields) {
        super(message);
        this.errorCode = errorCode;
        this.field = field;
        this.existingClientId = existingClientId;
        this.existingClientName = existingClientName;
        this.existingClientPhone = existingClientPhone;
        this.matchedFields = matchedFields != null ? matchedFields : (field != null ? java.util.List.of(field) : java.util.List.of());
    }

    public String getErrorCode() { return errorCode; }
    public String getField() { return field; }
    public Long getExistingClientId() { return existingClientId; }
    public String getExistingClientName() { return existingClientName; }
    public String getExistingClientPhone() { return existingClientPhone; }
    public java.util.List<String> getMatchedFields() { return matchedFields; }
}

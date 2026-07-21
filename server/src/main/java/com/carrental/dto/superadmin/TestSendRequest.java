package com.carrental.dto.superadmin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** Multi-recipient Super Admin test-send request. */
@Data
public class TestSendRequest {
    @NotEmpty(message = "At least one recipient is required")
    @Size(max = 10, message = "A test send may target at most 10 recipients")
    @Valid
    private List<TestSendRecipient> recipients;

    private String locale;

    private Map<String, String> variables;
}

package com.carrental.dto.ai;

import lombok.Data;

import java.util.Map;

/** Free-text variables only — never a system prompt override, per platform AI data rules. */
@Data
public class AiExecuteRequest {
    private Map<String, String> variables;
    private String userMessage;
}

package com.carrental.dto.rbac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** One permission's current state for a given role, as rendered in a module accordion card. */
@Getter
@Builder
@AllArgsConstructor
public class PermissionStateDto {
    private String code;
    private String module;
    private String resource;
    private String action;
    private String labelKey;
    private String descriptionKey;
    private String riskLevel;
    private List<String> dependencies;
    private boolean enabled;
    private boolean isNew;
}

package com.carrental.dto.rbac;

import com.carrental.entity.PermissionDefinition;
import com.carrental.entity.PermissionRiskLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Public, DTO-shaped view of a {@link PermissionDefinition} — the entity is
 * never exposed directly over the API (spec: "Do not expose entity graphs
 * directly"). Powers {@code GET /api/permissions/catalog}, the data source
 * for the new role-editor UI's module/action cards.
 */
@Getter
@AllArgsConstructor
public class PermissionCatalogEntryDto {
    private final String code;
    private final String module;
    private final String resource;
    private final String action;
    private final String labelKey;
    private final String descriptionKey;
    private final PermissionRiskLevel riskLevel;
    private final List<String> dependencies;
    private final int sortOrder;
    private final boolean deprecated;
    private final String aliasOf;
    /** True for roughly the first two weeks after a permission is first synced — drives the "New" badge (spec section 22/23). */
    private final boolean isNew;

    public static PermissionCatalogEntryDto from(PermissionDefinition d) {
        List<String> deps = (d.getDependencies() == null || d.getDependencies().isBlank())
                ? List.of()
                : Arrays.stream(d.getDependencies().split(",")).filter(s -> !s.isBlank()).toList();
        boolean isNew = d.getRegisteredAt() != null && d.getRegisteredAt().isAfter(LocalDateTime.now().minusDays(14));
        return new PermissionCatalogEntryDto(
                d.getCode(), d.getModule(), d.getResource(), d.getAction(),
                d.getLabelKey(), d.getDescriptionKey(), d.getRiskLevel(),
                deps, d.getSortOrder() == null ? 0 : d.getSortOrder(),
                Boolean.TRUE.equals(d.getDeprecated()), d.getAliasOf(), isNew);
    }
}

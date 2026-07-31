package com.carrental.service;

import com.carrental.entity.PermissionDefinition;
import com.carrental.repository.PermissionDefinitionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Reconciles {@link PermissionCatalog#ENTRIES} (the code-defined registry)
 * against the {@code permission_definitions} table. Runs once per application
 * lifetime — at startup via {@link #PostConstruct}, and defensively again (as
 * a fast no-op after the first run) from {@link RolePermissionService#ensureTenantDefaults}
 * for environments where the very first tenant request races application
 * startup.
 *
 * <p>Contract (spec section 4/25): insert missing definitions, update
 * non-security metadata (label/description keys, module/resource/action,
 * risk level, dependencies, sort order) on existing rows, and mark a
 * definition inactive the moment its code disappears from the catalog —
 * NEVER delete a row and NEVER touch any existing role_permissions or
 * user_permission_overrides grant. A single bad entry must never take down
 * application startup, so every failure is caught and logged, not thrown.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionSyncService {

    private final PermissionDefinitionRepository definitionRepository;

    private final AtomicBoolean synced = new AtomicBoolean(false);

    public record SyncResult(int added, int updated, int deactivated) {}

    @PostConstruct
    public void syncOnStartup() {
        try {
            SyncResult result = sync();
            log.info("[PERMISSION_SYNC] added={} updated={} deactivated={}",
                    result.added(), result.updated(), result.deactivated());
        } catch (Exception ex) {
            // Per spec: a broken sync must never fail application startup.
            log.error("[PERMISSION_SYNC] failed — application will continue starting up: {}", ex.getMessage(), ex);
        }
    }

    /** Idempotent no-op after the first successful run within this process. */
    public SyncResult sync() {
        if (synced.get()) return new SyncResult(0, 0, 0);
        SyncResult result = doSync();
        synced.set(true);
        return result;
    }

    @Transactional
    protected SyncResult doSync() {
        Map<String, PermissionCatalog.Entry> catalogByCode = PermissionCatalog.ENTRIES.stream()
                .collect(Collectors.toMap(PermissionCatalog.Entry::code, e -> e, (a, b) -> a));

        Map<String, PermissionDefinition> existingByCode = definitionRepository.findAll().stream()
                .collect(Collectors.toMap(PermissionDefinition::getCode, d -> d, (a, b) -> a));

        int added = 0;
        int updated = 0;

        for (PermissionCatalog.Entry entry : PermissionCatalog.ENTRIES) {
            PermissionDefinition existing = existingByCode.get(entry.code());
            if (existing == null) {
                definitionRepository.save(toNewDefinition(entry));
                added++;
                continue;
            }
            if (applyMetadata(existing, entry)) {
                definitionRepository.save(existing);
                updated++;
            }
        }

        int deactivated = 0;
        for (PermissionDefinition existing : existingByCode.values()) {
            if (!catalogByCode.containsKey(existing.getCode()) && Boolean.TRUE.equals(existing.getActive())) {
                existing.setActive(false);
                definitionRepository.save(existing);
                deactivated++;
            }
        }

        return new SyncResult(added, updated, deactivated);
    }

    private PermissionDefinition toNewDefinition(PermissionCatalog.Entry entry) {
        return PermissionDefinition.builder()
                .code(entry.code())
                .name(entry.code())
                .description(entry.descriptionKey())
                .category(entry.module() == null ? "GENERAL" : entry.module())
                .module(entry.module())
                .resource(entry.resource())
                .action(entry.action())
                .labelKey(entry.labelKey())
                .descriptionKey(entry.descriptionKey())
                .riskLevel(entry.riskLevel())
                .dependencies(String.join(",", entry.dependencies()))
                .sortOrder(entry.sortOrder())
                .active(true)
                .deprecated(entry.deprecated())
                .aliasOf(entry.aliasOf())
                .registeredAt(java.time.LocalDateTime.now())
                .build();
    }

    /** @return true if any field actually changed (so callers can count real updates, not no-op saves). */
    private boolean applyMetadata(PermissionDefinition existing, PermissionCatalog.Entry entry) {
        boolean changed = false;
        String dependenciesCsv = String.join(",", entry.dependencies());

        changed |= setIfChanged(existing::getModule, existing::setModule, entry.module());
        changed |= setIfChanged(existing::getResource, existing::setResource, entry.resource());
        changed |= setIfChanged(existing::getAction, existing::setAction, entry.action());
        changed |= setIfChanged(existing::getLabelKey, existing::setLabelKey, entry.labelKey());
        changed |= setIfChanged(existing::getDescriptionKey, existing::setDescriptionKey, entry.descriptionKey());
        changed |= setIfChanged(existing::getRiskLevel, existing::setRiskLevel, entry.riskLevel());
        changed |= setIfChanged(existing::getDependencies, existing::setDependencies, dependenciesCsv);
        changed |= setIfChanged(existing::getSortOrder, existing::setSortOrder, entry.sortOrder());
        changed |= setIfChanged(existing::getDeprecated, existing::setDeprecated, entry.deprecated());
        changed |= setIfChanged(existing::getAliasOf, existing::setAliasOf, entry.aliasOf());
        if (entry.module() != null) {
            changed |= setIfChanged(existing::getCategory, existing::setCategory, entry.module());
        }
        // Reactivate a definition that came back into the catalog after having
        // been deactivated in a previous release — never leave a currently-valid
        // permission stuck inactive.
        if (!Boolean.TRUE.equals(existing.getActive())) {
            existing.setActive(true);
            changed = true;
        }
        return changed;
    }

    private <T> boolean setIfChanged(java.util.function.Supplier<T> getter, java.util.function.Consumer<T> setter, T newValue) {
        T current = getter.get();
        if (Objects.equals(current, newValue)) return false;
        setter.accept(newValue);
        return true;
    }

    /** All codes currently recognized by the catalog (modern + legacy aliases) — used to seed per-tenant role_permissions rows. */
    public List<String> allCodes() {
        return PermissionCatalog.ENTRIES.stream().map(PermissionCatalog.Entry::code).toList();
    }
}

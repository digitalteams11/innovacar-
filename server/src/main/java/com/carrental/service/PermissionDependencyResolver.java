package com.carrental.service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Small static utility over {@link PermissionCatalog}'s dependency graph
 * (spec section 12: "enabling a child permission must auto-enable its
 * parent(s); disabling a parent must warn which dependents will stop
 * working"). Stateless — reads {@link PermissionCatalog#ENTRIES} fresh on
 * every call, since the catalog only changes at compile time.
 */
public final class PermissionDependencyResolver {

    private PermissionDependencyResolver() {}

    private static Map<String, Set<String>> dependenciesByCode() {
        return PermissionCatalog.ENTRIES.stream()
                .collect(Collectors.toMap(
                        PermissionCatalog.Entry::code,
                        e -> new LinkedHashSet<>(e.dependencies())));
    }

    /**
     * Given the set of codes a caller wants enabled, returns that set plus
     * every transitive dependency (parent) required for them to make sense —
     * e.g. requesting VEHICLE_UPDATE alone also enables VEHICLE_VIEW.
     */
    public static Set<String> expandWithDependencies(Set<String> requested) {
        Map<String, Set<String>> deps = dependenciesByCode();
        Set<String> result = new LinkedHashSet<>(requested);
        java.util.Deque<String> queue = new java.util.ArrayDeque<>(requested);
        while (!queue.isEmpty()) {
            String code = queue.poll();
            for (String parent : deps.getOrDefault(code, Set.of())) {
                if (result.add(parent)) queue.add(parent);
            }
        }
        return result;
    }

    /**
     * Given the full previously-enabled set and one code about to be
     * disabled, returns every other currently-enabled code that depends
     * (directly or transitively) on it — the caller should surface these as
     * a cascade warning ("Disabling VEHICLE_VIEW will also disable: VEHICLE_UPDATE,
     * VEHICLE_DELETE") rather than silently leaving a dangling dependency.
     */
    public static Set<String> dependentsOf(String codeBeingDisabled, Set<String> currentlyEnabled) {
        Map<String, Set<String>> deps = dependenciesByCode();
        Set<String> result = new LinkedHashSet<>();
        boolean changed = true;
        Set<String> frontier = new LinkedHashSet<>(Set.of(codeBeingDisabled));
        while (changed) {
            changed = false;
            for (String candidate : currentlyEnabled) {
                if (result.contains(candidate) || candidate.equals(codeBeingDisabled)) continue;
                Set<String> candidateDeps = deps.getOrDefault(candidate, Set.of());
                boolean dependsOnFrontier = candidateDeps.stream().anyMatch(frontier::contains);
                if (dependsOnFrontier) {
                    result.add(candidate);
                    frontier.add(candidate);
                    changed = true;
                }
            }
        }
        return result;
    }
}

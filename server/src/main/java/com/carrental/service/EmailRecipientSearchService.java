package com.carrental.service;

import com.carrental.dto.superadmin.EmailRecipientDto;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Backs the Super Admin Email Center's recipient directory search
 * (GET /api/super-admin/email-recipients) — the searchable combobox behind
 * the test-send modal. Never returns passwords/tokens; only display-safe
 * fields (see {@link EmailRecipientDto}).
 *
 * <p>Agencies and users are two separate tables with no shared primary key,
 * so true single-offset pagination across both isn't meaningful — instead
 * each type is paginated independently with the same page/size, then merged
 * and re-sorted for the combined ("ALL") view. In practice this endpoint
 * backs a live-search-as-you-type combobox where users almost always stay
 * on page 0, so this is a deliberate, documented simplification rather than
 * building a cross-entity keyset pagination scheme nothing here needs.
 */
@Service
@RequiredArgsConstructor
public class EmailRecipientSearchService {

    private static final Set<String> BLOCKED_TENANT_STATUSES = Set.of("BLOCKED", "SUSPENDED", "INACTIVE");

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    public record SearchResult(List<EmailRecipientDto> items, int page, int size, long totalElements, boolean hasMore) {}

    public SearchResult search(String q, String type, String status, Boolean verifiedOnly, String plan,
                                boolean includeBlocked, int page, int size) {
        String normalizedQuery = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        int safeSize = Math.max(1, Math.min(size, 50));
        int safePage = Math.max(0, page);

        boolean wantAgencies = matchesType(type, "AGENCY");
        boolean wantUsers = matchesType(type, "USER");

        Page<Tenant> agencyPage = wantAgencies
                ? tenantRepository.searchForEmailRecipients(normalizedQuery, PageRequest.of(safePage, safeSize))
                : Page.empty();
        List<EmailRecipientDto> agencies = filterAndMapAgencies(agencyPage.getContent(), status, plan, includeBlocked);
        long agencyTotal = agencyPage.getTotalElements();

        Page<User> userPage = wantUsers
                ? userRepository.searchForEmailRecipients(normalizedQuery, PageRequest.of(safePage, safeSize))
                : Page.empty();
        List<EmailRecipientDto> users = filterAndMapUsers(userPage.getContent(), status, verifiedOnly, includeBlocked);
        long userTotal = userPage.getTotalElements();

        // Deduplicate by normalized email — a named user wins over the
        // generic agency contact address when both resolve to the same email.
        Map<String, EmailRecipientDto> byEmail = new LinkedHashMap<>();
        for (EmailRecipientDto dto : agencies) {
            byEmail.put(dto.getEmail().toLowerCase(Locale.ROOT), dto);
        }
        for (EmailRecipientDto dto : users) {
            byEmail.put(dto.getEmail().toLowerCase(Locale.ROOT), dto);
        }

        List<EmailRecipientDto> combined = new ArrayList<>(byEmail.values());
        combined.sort(Comparator.comparing(EmailRecipientDto::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        if (combined.size() > safeSize) {
            combined = combined.subList(0, safeSize);
        }

        boolean hasMore = (wantAgencies && (long) (safePage + 1) * safeSize < agencyTotal)
                || (wantUsers && (long) (safePage + 1) * safeSize < userTotal);

        return new SearchResult(combined, safePage, safeSize, agencyTotal + userTotal, hasMore);
    }

    private boolean matchesType(String type, String candidate) {
        return type == null || type.isBlank() || "ALL".equalsIgnoreCase(type) || candidate.equalsIgnoreCase(type);
    }

    private List<EmailRecipientDto> filterAndMapAgencies(List<Tenant> tenants, String status, String plan,
                                                            boolean includeBlocked) {
        return tenants.stream()
                .filter(t -> StringUtils.hasText(t.getEmail()))
                .filter(t -> includeBlocked || !BLOCKED_TENANT_STATUSES.contains(normalizedStatus(t.getStatus())))
                .filter(t -> status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                        || status.equalsIgnoreCase(t.getStatus()))
                .filter(t -> plan == null || plan.isBlank() || "ALL".equalsIgnoreCase(plan)
                        || plan.equalsIgnoreCase(t.getPlanName()))
                .map(this::toAgencyDto)
                .toList();
    }

    private List<EmailRecipientDto> filterAndMapUsers(List<User> users, String status, Boolean verifiedOnly,
                                                         boolean includeBlocked) {
        return users.stream()
                .filter(u -> StringUtils.hasText(u.getEmail()))
                .filter(u -> includeBlocked || u.isEnabled())
                .filter(u -> !Boolean.TRUE.equals(verifiedOnly) || Boolean.TRUE.equals(u.getEmailVerified()))
                .filter(u -> status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                        || (u.getTenant() != null && status.equalsIgnoreCase(u.getTenant().getStatus())))
                .map(this::toUserDto)
                .toList();
    }

    private String normalizedStatus(String status) {
        return status == null ? "" : status.toUpperCase(Locale.ROOT);
    }

    private EmailRecipientDto toAgencyDto(Tenant t) {
        return EmailRecipientDto.builder()
                .id(t.getId())
                .type("AGENCY")
                .displayName(t.getName())
                .email(t.getEmail().trim().toLowerCase(Locale.ROOT))
                .role(null)
                .agencyId(t.getId())
                .agencyName(t.getName())
                .status(t.getStatus())
                .verified("ACTIVE".equalsIgnoreCase(t.getStatus()) || "TRIAL".equalsIgnoreCase(t.getStatus()))
                .plan(t.getPlanName())
                .build();
    }

    private EmailRecipientDto toUserDto(User u) {
        String displayName = displayName(u);
        Tenant tenant = u.getTenant();
        return EmailRecipientDto.builder()
                .id(u.getId())
                .type("USER")
                .displayName(displayName)
                .email(u.getEmail().trim().toLowerCase(Locale.ROOT))
                .role(u.getRole() != null ? u.getRole().name() : null)
                .agencyId(tenant != null ? tenant.getId() : null)
                .agencyName(tenant != null ? tenant.getName() : null)
                .status(u.isEnabled() ? "ACTIVE" : "BLOCKED")
                .verified(Boolean.TRUE.equals(u.getEmailVerified()))
                .plan(tenant != null ? tenant.getPlanName() : null)
                .build();
    }

    private String displayName(User u) {
        String full = (StringUtils.hasText(u.getFirstName()) ? u.getFirstName() : "")
                + " " + (StringUtils.hasText(u.getLastName()) ? u.getLastName() : "");
        full = full.trim();
        return full.isBlank() ? u.getEmail() : full;
    }
}

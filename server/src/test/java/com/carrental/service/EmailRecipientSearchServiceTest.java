package com.carrental.service;

import com.carrental.entity.Role;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Covers the Super Admin email-recipient directory search: excludes
 * blocked/suspended tenants and disabled users by default (unless
 * includeBlocked is set), excludes empty emails, and deduplicates by
 * normalized email across the agency and user result sets (a user wins
 * over the agency's generic contact address for the same email).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailRecipientSearchServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;

    private EmailRecipientSearchService service;

    private EmailRecipientSearchService newService() {
        return new EmailRecipientSearchService(tenantRepository, userRepository);
    }

    private Page<Tenant> pageOf(Tenant... tenants) {
        return new PageImpl<>(List.of(tenants), PageRequest.of(0, 20), tenants.length);
    }

    private Page<User> pageOf(User... users) {
        return new PageImpl<>(List.of(users), PageRequest.of(0, 20), users.length);
    }

    @Test
    void excludesBlockedAndSuspendedAgenciesByDefault() {
        service = newService();
        when(tenantRepository.searchForEmailRecipients(anyString(), any())).thenReturn(pageOf(
                Tenant.builder().id(1L).name("Active Co").email("active@co.test").status("ACTIVE").build(),
                Tenant.builder().id(2L).name("Suspended Co").email("suspended@co.test").status("SUSPENDED").build(),
                Tenant.builder().id(3L).name("Blocked Co").email("blocked@co.test").status("BLOCKED").build()
        ));
        when(userRepository.searchForEmailRecipients(anyString(), any())).thenReturn(Page.empty());

        var result = service.search("", "AGENCY", null, null, null, false, 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getEmail()).isEqualTo("active@co.test");
    }

    @Test
    void includeBlockedTrue_returnsBlockedAgenciesToo() {
        service = newService();
        when(tenantRepository.searchForEmailRecipients(anyString(), any())).thenReturn(pageOf(
                Tenant.builder().id(1L).name("Blocked Co").email("blocked@co.test").status("BLOCKED").build()
        ));
        when(userRepository.searchForEmailRecipients(anyString(), any())).thenReturn(Page.empty());

        var result = service.search("", "AGENCY", null, null, null, true, 0, 20);

        assertThat(result.items()).hasSize(1);
    }

    @Test
    void excludesDisabledUsersByDefault() {
        service = newService();
        when(tenantRepository.searchForEmailRecipients(anyString(), any())).thenReturn(Page.empty());
        Tenant tenant = Tenant.builder().id(1L).name("Acme").status("ACTIVE").build();
        when(userRepository.searchForEmailRecipients(anyString(), any())).thenReturn(pageOf(
                User.builder().id(1L).email("enabled@acme.test").role(Role.EMPLOYEE).tenant(tenant).accountEnabled(true).build(),
                User.builder().id(2L).email("disabled@acme.test").role(Role.EMPLOYEE).tenant(tenant).accountEnabled(false).build()
        ));

        var result = service.search("", "USER", null, null, null, false, 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getEmail()).isEqualTo("enabled@acme.test");
    }

    @Test
    void excludesEmptyEmails() {
        service = newService();
        when(tenantRepository.searchForEmailRecipients(anyString(), any())).thenReturn(Page.empty());
        Tenant tenant = Tenant.builder().id(1L).name("Acme").status("ACTIVE").build();
        when(userRepository.searchForEmailRecipients(anyString(), any())).thenReturn(pageOf(
                User.builder().id(1L).email("").role(Role.EMPLOYEE).tenant(tenant).accountEnabled(true).build(),
                User.builder().id(2L).email("real@acme.test").role(Role.EMPLOYEE).tenant(tenant).accountEnabled(true).build()
        ));

        var result = service.search("", "USER", null, null, null, false, 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getEmail()).isEqualTo("real@acme.test");
    }

    @Test
    void deduplicatesByNormalizedEmail_userWinsOverAgency() {
        service = newService();
        when(tenantRepository.searchForEmailRecipients(anyString(), any())).thenReturn(pageOf(
                Tenant.builder().id(1L).name("Acme").email("Shared@Acme.test").status("ACTIVE").build()
        ));
        Tenant tenant = Tenant.builder().id(1L).name("Acme").status("ACTIVE").build();
        when(userRepository.searchForEmailRecipients(anyString(), any())).thenReturn(pageOf(
                User.builder().id(1L).email("shared@acme.test").firstName("Jane").lastName("Doe")
                        .role(Role.AGENCY_OWNER).tenant(tenant).accountEnabled(true).build()
        ));

        var result = service.search("", "ALL", null, null, null, false, 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getType()).isEqualTo("USER");
        assertThat(result.items().get(0).getDisplayName()).isEqualTo("Jane Doe");
    }

    @Test
    void verifiedOnly_excludesUnverifiedUsers() {
        service = newService();
        when(tenantRepository.searchForEmailRecipients(anyString(), any())).thenReturn(Page.empty());
        Tenant tenant = Tenant.builder().id(1L).name("Acme").status("ACTIVE").build();
        when(userRepository.searchForEmailRecipients(anyString(), any())).thenReturn(pageOf(
                User.builder().id(1L).email("verified@acme.test").role(Role.EMPLOYEE).tenant(tenant)
                        .accountEnabled(true).emailVerified(true).build(),
                User.builder().id(2L).email("unverified@acme.test").role(Role.EMPLOYEE).tenant(tenant)
                        .accountEnabled(true).emailVerified(false).build()
        ));

        var result = service.search("", "USER", null, true, null, false, 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getEmail()).isEqualTo("verified@acme.test");
    }
}

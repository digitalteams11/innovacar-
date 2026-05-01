package com.carrental.service;

import com.carrental.entity.Tenant;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private TenantRepository tenantRepository;
    @InjectMocks private SubscriptionService subscriptionService;

    private static final Long TENANT_ID = 1L;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        tenant = Tenant.builder()
                .id(TENANT_ID)
                .subscriptionActive(false)
                .subscriptionEndDate(LocalDate.now().minusDays(5)) // Expired
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void activateSubscription_success() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        subscriptionService.activateSubscription();

        assertThat(tenant.isSubscriptionActive()).isTrue();
        verify(tenantRepository).save(tenant);
    }

    @Test
    void extendSubscription_fromExpired_success() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        subscriptionService.extendSubscription(30);

        assertThat(tenant.isSubscriptionActive()).isTrue();
        // Should start from today since it was expired
        assertThat(tenant.getSubscriptionEndDate()).isEqualTo(LocalDate.now().plusDays(30));
        verify(tenantRepository).save(tenant);
    }

    @Test
    void extendSubscription_fromActive_success() {
        tenant.setSubscriptionActive(true);
        LocalDate futureDate = LocalDate.now().plusDays(10);
        tenant.setSubscriptionEndDate(futureDate);
        
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        subscriptionService.extendSubscription(30);

        assertThat(tenant.isSubscriptionActive()).isTrue();
        // Should append to existing future date
        assertThat(tenant.getSubscriptionEndDate()).isEqualTo(futureDate.plusDays(30));
        verify(tenantRepository).save(tenant);
    }
}

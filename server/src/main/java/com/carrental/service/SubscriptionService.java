package com.carrental.service;

import com.carrental.entity.Tenant;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Service to manage tenant subscriptions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final TenantRepository tenantRepository;

    /**
     * Activates a tenant's subscription.
     */
    @Transactional
    public void activateSubscription() {
        Tenant tenant = getTenant();
        tenant.setSubscriptionActive(true);
        tenantRepository.save(tenant);
        log.info("Subscription activated for tenant [{}]", tenant.getId());
    }

    /**
     * Extends a tenant's subscription by a given number of days.
     * Also automatically activates it if it was inactive.
     */
    @Transactional
    public void extendSubscription(int daysToAdd) {
        Tenant tenant = getTenant();
        
        LocalDate currentDate = tenant.getSubscriptionEndDate();
        if (currentDate == null || currentDate.isBefore(LocalDate.now())) {
            currentDate = LocalDate.now();
        }
        
        tenant.setSubscriptionEndDate(currentDate.plusDays(daysToAdd));
        tenant.setSubscriptionActive(true);
        
        tenantRepository.save(tenant);
        log.info("Subscription for tenant [{}] extended to {}", tenant.getId(), tenant.getSubscriptionEndDate());
    }

    private Tenant getTenant() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));
    }
}

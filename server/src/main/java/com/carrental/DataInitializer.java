package com.carrental;

import com.carrental.entity.Role;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (tenantRepository.count() == 0) {
            log.info("Seeding default data...");

            Tenant tenant = tenantRepository.save(Tenant.builder()
                    .name("Premium Rentals")
                    .email("contact@premium-rentals.com")
                    .subscriptionActive(true)
                    .subscriptionEndDate(LocalDate.now().plusYears(1))
                    .build());

            userRepository.save(User.builder()
                    .email("admin@test.com")
                    .password(passwordEncoder.encode("admin123"))
                    .role(Role.ADMIN)
                    .tenant(tenant)
                    .build());

            log.info("Default data seeded: email=admin@test.com, password=admin123");
        }
    }
}

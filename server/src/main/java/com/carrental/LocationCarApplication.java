package com.carrental;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

// com.carrental.legal.* is an in-progress, not-yet-wired compliance module whose
// entity.LegalAcceptance collides with the existing com.carrental.entity.LegalAcceptance
// (same JPA entity name, same table, different columns) and stops the whole app from
// booting. Excluded from scanning until that module is reconciled with the legacy entity.
@SpringBootApplication
@ComponentScan(basePackages = "com.carrental",
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.carrental\\.legal\\..*"))
@EntityScan(basePackages = "com.carrental.entity")
@EnableJpaRepositories(basePackages = "com.carrental",
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.carrental\\.legal\\.repository\\..*"))
@EnableScheduling
public class LocationCarApplication {
    public static void main(String[] args) {
        SpringApplication.run(LocationCarApplication.class, args);
    }
}

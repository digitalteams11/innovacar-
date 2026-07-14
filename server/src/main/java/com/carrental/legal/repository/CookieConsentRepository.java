package com.carrental.legal.repository;

import com.carrental.legal.entity.CookieConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CookieConsentRepository extends JpaRepository<CookieConsent, Long> {

    Optional<CookieConsent> findByUserId(Long userId);

    Optional<CookieConsent> findByAnonymousId(String anonymousId);
}

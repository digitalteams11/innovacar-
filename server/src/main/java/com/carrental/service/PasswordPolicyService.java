package com.carrental.service;

import com.carrental.entity.PasswordHistory;
import com.carrental.entity.User;
import com.carrental.repository.PasswordHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PasswordPolicyService {
    private final PasswordHistoryRepository historyRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.password.expiration-days:90}")
    private long expirationDays;

    public void validate(String password) {
        if (password == null || password.length() < 8
                || !password.matches(".*[A-Z].*")
                || !password.matches(".*[a-z].*")
                || !password.matches(".*\\d.*")
                || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalArgumentException(
                    "WEAK_PASSWORD");
        }
    }

    public void validateNotReused(User user, String password) {
        if (passwordEncoder.matches(password, user.getPassword())
                || historyRepository.findTop5ByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .anyMatch(item -> passwordEncoder.matches(password, item.getPasswordHash()))) {
            throw new IllegalArgumentException("Choose a password that was not used recently.");
        }
    }

    @Transactional
    public void replacePassword(User user, String password) {
        validate(password);
        // Must not contain the user's email
        if (user.getEmail() != null
                && password.toLowerCase().contains(user.getEmail().toLowerCase())) {
            throw new IllegalArgumentException("Password must not contain your email address.");
        }
        if (user.getId() != null) {
            validateNotReused(user, password);
            historyRepository.save(PasswordHistory.builder()
                    .userId(user.getId())
                    .passwordHash(user.getPassword())
                    .build());
        }
        user.setPassword(passwordEncoder.encode(password));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setPasswordExpiresAt(LocalDateTime.now().plusDays(expirationDays));
    }
}

package com.carrental.repository;

import com.carrental.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    long countByEmailAndSuccessfulFalseAndAttemptedAtAfter(String email, LocalDateTime since);

    long countByIpAddressAndSuccessfulFalseAndAttemptedAtAfter(String ipAddress, LocalDateTime since);

    List<LoginAttempt> findByEmailAndSuccessfulFalseAndAttemptedAtAfterOrderByAttemptedAtDesc(
            String email, LocalDateTime since);

    List<LoginAttempt> findTop100ByOrderByAttemptedAtDesc();
    List<LoginAttempt> findByUserIdOrderByAttemptedAtDesc(Long userId);
    long countBySuccessfulFalseAndAttemptedAtAfter(LocalDateTime since);
    long countBySuspiciousTrue();

    void deleteByAttemptedAtBefore(LocalDateTime date);
}

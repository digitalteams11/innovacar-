package com.carrental.repository;

import com.carrental.entity.PhoneOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PhoneOtpRepository extends JpaRepository<PhoneOtp, Long> {

    Optional<PhoneOtp> findByPhoneNumberAndOtpCode(String phoneNumber, String otpCode);

    void deleteByPhoneNumber(String phoneNumber);

    void deleteByExpiresAtBefore(LocalDateTime date);
}

package com.carrental.repository;

import com.carrental.entity.AnnouncementDismissal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnnouncementDismissalRepository extends JpaRepository<AnnouncementDismissal, Long> {
    List<AnnouncementDismissal> findByUserId(Long userId);
    Optional<AnnouncementDismissal> findByAnnouncementIdAndUserId(Long announcementId, Long userId);
}

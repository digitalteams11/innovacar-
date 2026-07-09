package com.carrental.repository;

import com.carrental.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findAllByOrderByCreatedAtDesc();

    @Query("SELECT a FROM Announcement a WHERE a.active = true " +
           "AND (a.startsAt IS NULL OR a.startsAt <= :now) " +
           "AND (a.endsAt IS NULL OR a.endsAt >= :now) " +
           "ORDER BY a.priority DESC, a.createdAt DESC")
    List<Announcement> findActive(@Param("now") LocalDateTime now);
}

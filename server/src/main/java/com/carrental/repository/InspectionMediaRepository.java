package com.carrental.repository;

import com.carrental.entity.InspectionMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InspectionMediaRepository extends JpaRepository<InspectionMedia, Long> {
    Optional<InspectionMedia> findByIdAndAccessToken(Long id, String accessToken);
}

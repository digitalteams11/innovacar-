package com.carrental.repository;

import com.carrental.entity.DesktopRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DesktopReleaseRepository extends JpaRepository<DesktopRelease, Long> {

    List<DesktopRelease> findAllByOrderByCreatedAtDesc();

    @Query("SELECT r FROM DesktopRelease r WHERE r.status = 'PUBLISHED' " +
           "AND r.platform = :platform AND r.architecture = :architecture " +
           "AND r.channel = :channel ORDER BY r.publishedAt DESC")
    List<DesktopRelease> findPublished(@Param("platform") DesktopRelease.Platform platform,
                                        @Param("architecture") DesktopRelease.Architecture architecture,
                                        @Param("channel") DesktopRelease.Channel channel);

    default Optional<DesktopRelease> findLatestPublished(DesktopRelease.Platform platform,
                                                          DesktopRelease.Architecture architecture,
                                                          DesktopRelease.Channel channel) {
        return findPublished(platform, architecture, channel).stream().findFirst();
    }
}

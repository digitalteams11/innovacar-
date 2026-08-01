package com.carrental.repository;

import com.carrental.entity.DesktopDownloadEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DesktopDownloadEventRepository extends JpaRepository<DesktopDownloadEvent, Long> {

    @Query("SELECT e.version AS version, COUNT(e) AS total FROM DesktopDownloadEvent e " +
           "WHERE e.status = 'STARTED' GROUP BY e.version ORDER BY e.version DESC")
    List<VersionCount> countByVersion();

    @Query("SELECT e.source AS source, COUNT(e) AS total FROM DesktopDownloadEvent e " +
           "WHERE e.status = 'STARTED' GROUP BY e.source")
    List<SourceCount> countBySource();

    interface VersionCount {
        String getVersion();
        Long getTotal();
    }

    interface SourceCount {
        DesktopDownloadEvent.Source getSource();
        Long getTotal();
    }
}

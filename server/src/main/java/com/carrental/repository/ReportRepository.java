package com.carrental.repository;

import com.carrental.entity.Report;
import com.carrental.entity.ReportStatus;
import com.carrental.entity.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findByIdAndTenantId(Long id, Long tenantId);

    List<Report> findAllByTenantIdOrderByPeriodStartDesc(Long tenantId);

    List<Report> findAllByTenantIdAndReportTypeOrderByPeriodStartDesc(Long tenantId, ReportType reportType);

    List<Report> findAllByTenantIdAndStatusOrderByPeriodStartDesc(Long tenantId, ReportStatus status);

    Optional<Report> findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(
            Long tenantId, ReportType reportType, LocalDateTime periodStart, LocalDateTime periodEnd);

    boolean existsByTenantIdAndReportTypeAndPeriodStartAndPeriodEndAndStatusIn(
            Long tenantId, ReportType reportType, LocalDateTime periodStart, LocalDateTime periodEnd,
            List<ReportStatus> statuses);
}

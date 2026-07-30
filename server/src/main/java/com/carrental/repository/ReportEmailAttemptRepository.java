package com.carrental.repository;

import com.carrental.entity.ReportEmailAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportEmailAttemptRepository extends JpaRepository<ReportEmailAttempt, Long> {
    List<ReportEmailAttempt> findAllByReportIdOrderByAttemptNoDesc(Long reportId);
    int countByReportId(Long reportId);
}

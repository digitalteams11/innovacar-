package com.carrental.legal.repository;

import com.carrental.legal.entity.DataRetentionPolicyEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataRetentionPolicyEntryRepository extends JpaRepository<DataRetentionPolicyEntry, Long> {

    List<DataRetentionPolicyEntry> findAllByOrderByDisplayOrderAscIdAsc();
}

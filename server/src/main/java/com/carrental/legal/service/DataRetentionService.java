package com.carrental.legal.service;

import com.carrental.legal.dto.DataRetentionEntryDto;
import com.carrental.legal.dto.UpsertDataRetentionEntryRequest;
import com.carrental.legal.entity.DataRetentionPolicyEntry;
import com.carrental.legal.mapper.DataRetentionMapper;
import com.carrental.legal.repository.DataRetentionPolicyEntryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages the published data-retention schedule (data category -> how long
 * it's kept -> legal basis). Informational/config data backing the Data
 * Retention Policy document and the account-deletion/export UI — this
 * module does not itself run automated purge jobs against business tables.
 */
@Service
@RequiredArgsConstructor
public class DataRetentionService {

    private final DataRetentionPolicyEntryRepository repository;

    @Transactional(readOnly = true)
    public List<DataRetentionEntryDto> listAll() {
        return repository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(DataRetentionMapper::toDto).toList();
    }

    @Transactional
    public DataRetentionEntryDto create(UpsertDataRetentionEntryRequest request) {
        DataRetentionPolicyEntry saved = repository.save(DataRetentionPolicyEntry.builder()
                .dataCategory(request.getDataCategory())
                .retentionPeriod(request.getRetentionPeriod())
                .legalBasis(request.getLegalBasis())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build());
        return DataRetentionMapper.toDto(saved);
    }

    @Transactional
    public DataRetentionEntryDto update(Long id, UpsertDataRetentionEntryRequest request) {
        DataRetentionPolicyEntry entry = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Data retention entry not found: " + id));
        entry.setDataCategory(request.getDataCategory());
        entry.setRetentionPeriod(request.getRetentionPeriod());
        entry.setLegalBasis(request.getLegalBasis());
        if (request.getDisplayOrder() != null) entry.setDisplayOrder(request.getDisplayOrder());
        return DataRetentionMapper.toDto(repository.save(entry));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Data retention entry not found: " + id);
        }
        repository.deleteById(id);
    }
}

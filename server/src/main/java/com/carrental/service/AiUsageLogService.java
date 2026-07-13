package com.carrental.service;

import com.carrental.dto.ai.AiUsageLogDto;
import com.carrental.dto.ai.AiUsageSummaryDto;
import com.carrental.entity.AiProvider;
import com.carrental.entity.AiUsageLog;
import com.carrental.repository.AiProviderRepository;
import com.carrental.repository.AiSettingsRepository;
import com.carrental.repository.AiUsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.OptionalDouble;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageLogService {

    private final AiUsageLogRepository aiUsageLogRepository;
    private final AiProviderRepository aiProviderRepository;
    private final AiSettingsRepository aiSettingsRepository;

    public long countSince(Long userId, LocalDateTime since) {
        return aiUsageLogRepository.countByUserIdAndCreatedAtAfter(userId, since);
    }

    public long countForAgencySince(Long agencyId, LocalDateTime since) {
        return aiUsageLogRepository.countByAgencyIdAndCreatedAtAfter(agencyId, since);
    }

    @Transactional(readOnly = true)
    public Page<AiUsageLogDto> listLogs(Pageable pageable) {
        return aiUsageLogRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<AiUsageLogDto> listErrors(Pageable pageable) {
        return aiUsageLogRepository.findAllByStatusOrderByCreatedAtDesc("FAILED", pageable).map(this::toDto);
    }

    @Transactional
    public void deleteById(Long id) {
        if (!aiUsageLogRepository.existsById(id)) {
            throw new com.carrental.exception.ResourceNotFoundException("AI usage log not found: " + id);
        }
        aiUsageLogRepository.deleteById(id);
    }

    @Transactional
    public long clearAll() {
        long count = aiUsageLogRepository.count();
        aiUsageLogRepository.deleteAll();
        return count;
    }

    @Transactional(readOnly = true)
    public AiUsageSummaryDto summary() {
        var settings = aiSettingsRepository.findAll().stream().findFirst().orElse(null);
        AiProvider activeProvider = settings != null && settings.getActiveProviderId() != null
                ? aiProviderRepository.findById(settings.getActiveProviderId()).orElse(null)
                : null;

        LocalDateTime startOfDay = LocalDateTime.of(java.time.LocalDate.now(), LocalTime.MIDNIGHT);
        long requestsToday = aiUsageLogRepository.countByCreatedAtAfter(startOfDay);
        long successfulToday = aiUsageLogRepository.countByStatusAndCreatedAtAfter("SUCCESS", startOfDay);
        long failedToday = aiUsageLogRepository.countByStatusAndCreatedAtAfter("FAILED", startOfDay);

        List<AiUsageLog> recent = aiUsageLogRepository.findAllByOrderByCreatedAtDesc(
                org.springframework.data.domain.PageRequest.of(0, 200)).getContent();
        OptionalDouble avgLatency = recent.stream()
                .filter(l -> l.getLatencyMs() != null)
                .mapToLong(AiUsageLog::getLatencyMs)
                .average();

        return AiUsageSummaryDto.builder()
                .globalEnabled(settings != null && Boolean.TRUE.equals(settings.getGlobalEnabled()))
                .activeProviderName(activeProvider != null ? activeProvider.getName() : null)
                .activeProviderType(activeProvider != null ? activeProvider.getProviderType().name() : null)
                .connectionStatus(activeProvider != null ? activeProvider.getConnectionStatus().name() : "NOT_TESTED")
                .requestsToday(requestsToday)
                .successfulToday(successfulToday)
                .failedToday(failedToday)
                .averageLatencyMs(avgLatency.isPresent() ? avgLatency.getAsDouble() : null)
                .build();
    }

    private AiUsageLogDto toDto(AiUsageLog log) {
        return AiUsageLogDto.builder()
                .id(log.getId())
                .providerId(log.getProviderId())
                .modelId(log.getModelId())
                .automationCode(log.getAutomationCode())
                .agencyId(log.getAgencyId())
                .userId(log.getUserId())
                .role(log.getRole())
                .status(log.getStatus())
                .inputTokens(log.getInputTokens())
                .outputTokens(log.getOutputTokens())
                .totalTokens(log.getTotalTokens())
                .estimatedCost(log.getEstimatedCost())
                .latencyMs(log.getLatencyMs())
                .errorCode(log.getErrorCode())
                .createdAt(log.getCreatedAt())
                .build();
    }
}

package com.carrental.repository;

import com.carrental.entity.AiModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiModelRepository extends JpaRepository<AiModel, Long> {

    List<AiModel> findAllByProviderId(Long providerId);

    Optional<AiModel> findByProviderIdAndModelId(Long providerId, String modelId);

    Optional<AiModel> findByProviderIdAndDefaultModelTrue(Long providerId);

    long countByProviderId(Long providerId);
}

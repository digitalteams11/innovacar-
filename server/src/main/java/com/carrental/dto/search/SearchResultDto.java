package com.carrental.dto.search;

public record SearchResultDto(
        String id,
        String type,
        Long entityId,
        String title,
        String subtitle,
        String status,
        String route,
        String icon,
        int score
) {
}

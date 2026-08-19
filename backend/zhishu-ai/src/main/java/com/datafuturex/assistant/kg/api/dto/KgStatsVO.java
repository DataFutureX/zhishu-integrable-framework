package com.datafuturex.assistant.kg.api.dto;

import java.util.Map;

public record KgStatsVO(
        boolean connected,
        long nodeCount,
        long edgeCount,
        Map<String, Long> nodesByLabel,
        Map<String, Long> edgesByType,
        String message
) {
}

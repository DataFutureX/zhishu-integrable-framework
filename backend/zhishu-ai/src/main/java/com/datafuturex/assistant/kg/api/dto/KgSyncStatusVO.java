package com.datafuturex.assistant.kg.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record KgSyncStatusVO(
        boolean enabled,
        boolean neo4jConnected,
        LocalDateTime lastSuccessAt,
        String lastMessage,
        List<Watermark> watermarks
) {
    public record Watermark(
            String sourceTable,
            LocalDateTime lastSyncAt,
            LocalDateTime maxSourceTime,
            String lastStatus,
            String lastMessage
    ) {
    }
}

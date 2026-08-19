package com.datafuturex.assistant.kg.api.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record KgSyncResult(
        boolean success,
        boolean full,
        String message,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Map<String, Integer> upserted,
        int deleted
) {
}

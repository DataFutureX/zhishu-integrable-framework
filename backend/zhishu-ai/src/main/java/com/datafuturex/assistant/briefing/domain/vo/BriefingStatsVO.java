package com.datafuturex.assistant.briefing.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "简报投递统计")
public record BriefingStatsVO(
        long total,
        long success,
        long failed,
        long unread,
        long pendingOrRunning
) {
}

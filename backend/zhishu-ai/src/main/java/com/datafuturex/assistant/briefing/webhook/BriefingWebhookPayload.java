package com.datafuturex.assistant.briefing.webhook;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "简报投递 Webhook 载荷")
public record BriefingWebhookPayload(
        String deliveryId,
        String scheduleId,
        String username,
        String title,
        String contentMarkdown,
        OffsetDateTime generatedAt,
        String channel
) {
}

package com.datafuturex.assistant.briefing.webhook;

import com.datafuturex.assistant.briefing.domain.entity.AiBriefingDeliveryEntity;
import com.datafuturex.assistant.briefing.service.BriefingDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Component
@Slf4j
public class BriefingWebhookPublisher {

    private final BriefingWebhookProperties properties;
    private final BriefingDeliveryService deliveryService;
    private final RestClient restClient;

    public BriefingWebhookPublisher(BriefingWebhookProperties properties,
                                    BriefingDeliveryService deliveryService) {
        this.properties = properties;
        this.deliveryService = deliveryService;
        this.restClient = RestClient.builder().build();
    }

    public void publish(AiBriefingDeliveryEntity delivery, String username) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getUrl())) {
            if (delivery != null && delivery.getId() != null) {
                deliveryService.updateWebhookStatus(delivery.getId(), "SKIPPED", "Webhook 未启用");
            }
            return;
        }
        if (delivery == null || delivery.getId() == null || !StringUtils.hasText(username)) {
            log.warn("简报 Webhook 跳过：缺少投递或用户名");
            return;
        }
        deliveryService.updateWebhookStatus(delivery.getId(), "PENDING", null);
        BriefingWebhookPayload payload = new BriefingWebhookPayload(
                String.valueOf(delivery.getId()),
                delivery.getScheduleId() == null ? null : String.valueOf(delivery.getScheduleId()),
                username,
                delivery.getTitle(),
                delivery.getContentMd(),
                delivery.getFinishedAt() != null
                        ? delivery.getFinishedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime()
                        : OffsetDateTime.now(),
                "WEBHOOK");
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restClient.post()
                        .uri(properties.getUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Wanxiang-Internal-Key", properties.getApiKey() == null ? "" : properties.getApiKey())
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();
                deliveryService.updateWebhookStatus(delivery.getId(), "SENT", null);
                log.info("简报 Webhook 已发送 deliveryId={}, username={}, attempt={}",
                        delivery.getId(), username, attempt);
                return;
            } catch (Exception e) {
                if (attempt >= maxAttempts) {
                    deliveryService.updateWebhookStatus(delivery.getId(), "FAILED", e.getMessage());
                    log.error("简报 Webhook 失败 deliveryId={}, attempts={}: {}",
                            delivery.getId(), attempt, e.getMessage(), e);
                    return;
                }
                log.warn("简报 Webhook 第 {} 次失败 deliveryId={}: {}，将重试",
                        attempt, delivery.getId(), e.getMessage());
                sleepBackoff(attempt);
            }
        }
    }

    private void sleepBackoff(int attempt) {
        long ms = Math.max(0L, properties.getRetryBackoffMs()) * attempt;
        if (ms <= 0L) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}

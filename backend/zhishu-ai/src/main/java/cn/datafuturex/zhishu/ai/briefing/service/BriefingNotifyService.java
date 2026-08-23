package cn.datafuturex.zhishu.ai.briefing.service;

import cn.datafuturex.zhishu.ai.briefing.domain.entity.AiBriefingDeliveryEntity;
import cn.datafuturex.zhishu.ai.briefing.domain.entity.AiBriefingScheduleEntity;
import cn.datafuturex.zhishu.ai.briefing.mail.BriefingMailSender;
import cn.datafuturex.zhishu.ai.briefing.webhook.BriefingWebhookPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 简报成功后的站内铃 / 邮件通知（异步）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BriefingNotifyService {

    private final BriefingDeliveryService deliveryService;
    private final BriefingSseService briefingSseService;
    private final BriefingWebhookPublisher webhookPublisher;
    private final ObjectProvider<BriefingMailSender> briefingMailSender;

    @Async
    public void notifyAfterSuccess(Long deliveryId, AiBriefingScheduleEntity schedule, String username,
                                   String userEmail) {
        if (deliveryId == null || schedule == null) {
            return;
        }
        AiBriefingDeliveryEntity delivery;
        try {
            delivery = deliveryService.requireEntity(deliveryId);
        } catch (Exception e) {
            log.warn("简报通知跳过，投递不存在 deliveryId={}", deliveryId);
            return;
        }

        webhookPublisher.publish(delivery, username);

        if (Boolean.TRUE.equals(schedule.getNotifyBell())) {
            try {
                briefingSseService.pushToUser(delivery.getUserId(), Map.of(
                        "id", delivery.getId(),
                        "title", delivery.getTitle() != null ? delivery.getTitle() : "",
                        "createTime", delivery.getCreateTime() != null
                                ? delivery.getCreateTime().toString() : ""));
            } catch (Exception e) {
                log.warn("简报 SSE 推送失败 deliveryId={}: {}", deliveryId, e.getMessage());
            }
        }

        BriefingMailSender mailSender = briefingMailSender.getIfAvailable();
        if (mailSender == null || !Boolean.TRUE.equals(schedule.getNotifyEmail())) {
            return;
        }

        String recipients = resolveRecipients(schedule, userEmail);
        if (!StringUtils.hasText(recipients)) {
            deliveryService.updateEmailStatus(deliveryId, "SKIPPED", null, "无可用收件人");
            return;
        }

        deliveryService.updateEmailStatus(deliveryId, "PENDING", recipients, null);
        String subject = buildSubject(schedule, delivery.getTitle());
        String body = StringUtils.hasText(delivery.getContentMd())
                ? delivery.getContentMd()
                : (delivery.getTitle() != null ? delivery.getTitle() : "AI简报");
        try {
            mailSender.send(recipients, subject, body);
            deliveryService.updateEmailStatus(deliveryId, "SENT", recipients, null);
        } catch (Exception e) {
            log.error("简报邮件通知失败 deliveryId={}: {}", deliveryId, e.getMessage(), e);
            deliveryService.updateEmailStatus(deliveryId, "FAILED", recipients, e.getMessage());
        }
    }

    static String resolveRecipients(AiBriefingScheduleEntity schedule, String userEmail) {
        String mode = StringUtils.hasText(schedule.getEmailToMode())
                ? schedule.getEmailToMode().trim().toUpperCase()
                : "USER_PROFILE";
        Set<String> emails = new LinkedHashSet<>();
        if ("USER_PROFILE".equals(mode) || "BOTH".equals(mode)) {
            addEmails(emails, userEmail);
        }
        if ("CUSTOM_LIST".equals(mode) || "BOTH".equals(mode)) {
            addEmails(emails, schedule.getEmailExtraTo());
        }
        return emails.isEmpty() ? null : String.join(",", emails);
    }

    private static void addEmails(Set<String> target, String raw) {
        if (!StringUtils.hasText(raw)) {
            return;
        }
        for (String part : raw.split("[,;\\s]+")) {
            String email = part.trim();
            if (StringUtils.hasText(email) && email.contains("@")) {
                target.add(email);
            }
        }
    }

    static String buildSubject(AiBriefingScheduleEntity schedule, String title) {
        String t = title != null ? title : "";
        if (StringUtils.hasText(schedule.getEmailSubjectTemplate())) {
            return schedule.getEmailSubjectTemplate().replace("{title}", t);
        }
        return "【AI简报】" + t;
    }
}

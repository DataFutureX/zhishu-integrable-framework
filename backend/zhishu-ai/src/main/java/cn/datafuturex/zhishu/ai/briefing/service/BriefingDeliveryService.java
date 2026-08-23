package cn.datafuturex.zhishu.ai.briefing.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.datafuturex.zhishu.ai.briefing.domain.entity.AiBriefingDeliveryEntity;
import cn.datafuturex.zhishu.ai.briefing.domain.vo.BriefingDeliveryVO;
import cn.datafuturex.zhishu.ai.briefing.domain.vo.BriefingStatsVO;

import java.util.List;

public interface BriefingDeliveryService {

    AiBriefingDeliveryEntity createPending(Long scheduleId, String triggerType, String triggerRef,
                                           String userId, Long agentId, String title);

    void markRunning(Long deliveryId);

    void markSuccess(Long deliveryId, Long runId, String contentMd);

    void markFailed(Long deliveryId, String errorMessage);

    /**
     * 更新邮件投递状态：NONE / PENDING / SENT / SKIPPED / FAILED
     */
    void updateEmailStatus(Long deliveryId, String status, String emailTo, String error);

    /**
     * 更新 Webhook 投递状态：NONE / PENDING / SENT / SKIPPED / FAILED
     */
    void updateWebhookStatus(Long deliveryId, String status, String error);

    AiBriefingDeliveryEntity requireEntity(Long id);

    BriefingDeliveryVO getForUser(Long id, String userId);

    Page<BriefingDeliveryVO> pageByUser(String userId, String status, int pageNum, int pageSize);

    BriefingDeliveryVO latestSuccess(String userId);

    List<BriefingDeliveryVO> recent(String userId, int limit);

    void markRead(Long id, String userId);

    long unreadCount(String userId);

    BriefingStatsVO stats(String userId);
}

package cn.datafuturex.zhishu.ai.briefing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.datafuturex.zhishu.ai.agent.api.AgentChatPort;
import cn.datafuturex.zhishu.ai.agent.domain.entity.AiAgentRunEntity;
import cn.datafuturex.zhishu.ai.agent.mapper.AiAgentRunMapper;
import cn.datafuturex.zhishu.ai.briefing.domain.entity.AiBriefingDeliveryEntity;
import cn.datafuturex.zhishu.ai.briefing.domain.entity.AiBriefingScheduleEntity;
import cn.datafuturex.zhishu.ai.briefing.service.BriefingDeliveryService;
import cn.datafuturex.zhishu.ai.briefing.service.BriefingJobService;
import cn.datafuturex.zhishu.ai.briefing.service.BriefingNotifyService;
import cn.datafuturex.zhishu.ai.modelconfig.api.ModelConfigPort;
import cn.datafuturex.zhishu.ai.shared.UserContext;
import cn.datafuturex.zhishu.ai.shared.vo.ChatResponseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BriefingJobServiceImpl implements BriefingJobService {

    private final BriefingDeliveryService deliveryService;
    private final BriefingNotifyService notifyService;
    private final AgentChatPort agentChatPort;
    private final AiAgentRunMapper aiAgentRunMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ModelConfigPort modelConfigPort;

    @Override
    public int runSchedule(AiBriefingScheduleEntity schedule, String triggerType) {
        return runSchedule(schedule, triggerType, null);
    }

    @Override
    public int runSchedule(AiBriefingScheduleEntity schedule, String triggerType, String triggerRef) {
        if (!llmConfigured()) {
            log.warn("未在模型设置中配置 API Key，跳过简报生成 scheduleId={}",
                    schedule == null ? null : schedule.getId());
            return 0;
        }
        if (schedule == null || schedule.getAgentId() == null) {
            log.warn("简报调度缺少 agentId，跳过 scheduleId={}", schedule == null ? null : schedule.getId());
            return 0;
        }
        List<Map<String, Object>> users = listEnabledUsers();
        if (users.isEmpty()) {
            log.info("简报调度无启用用户，跳过 scheduleId={}", schedule.getId());
            return 0;
        }
        String prompt = StringUtils.hasText(schedule.getPromptTemplate())
                ? schedule.getPromptTemplate()
                : "请生成今日监测简报。";
        String title = schedule.getName();
        String ref = StringUtils.hasText(triggerRef) ? triggerRef.trim() : String.valueOf(schedule.getId());
        int ok = 0;
        for (Map<String, Object> user : users) {
            String userId = stringVal(user.get("id"));
            String username = stringVal(user.get("username"));
            String email = stringVal(user.get("email"));
            if (!StringUtils.hasText(userId)) {
                continue;
            }
            try {
                runForUser(schedule, triggerType, ref, userId, username, email, title, prompt);
                ok++;
            } catch (Exception e) {
                log.error("简报生成失败 scheduleId={}, userId={}: {}",
                        schedule.getId(), userId, e.getMessage(), e);
            }
        }
        return ok;
    }

    @Override
    public int runForUsername(AiBriefingScheduleEntity schedule, String triggerType, String username,
                              String promptOverride) {
        if (!llmConfigured()) {
            log.warn("未在模型设置中配置 API Key，跳过简报代调 scheduleId={}",
                    schedule == null ? null : schedule.getId());
            return 0;
        }
        if (schedule == null || schedule.getAgentId() == null) {
            log.warn("简报调度缺少 agentId，跳过");
            return 0;
        }
        if (!StringUtils.hasText(username)) {
            log.warn("简报代调缺少 username，跳过 scheduleId={}", schedule.getId());
            return 0;
        }
        Map<String, Object> user = findUserByUsername(username.trim());
        String userId = user != null ? stringVal(user.get("id")) : username.trim();
        String email = user != null ? stringVal(user.get("email")) : null;
        String prompt = StringUtils.hasText(promptOverride)
                ? promptOverride
                : (StringUtils.hasText(schedule.getPromptTemplate())
                ? schedule.getPromptTemplate()
                : "请生成今日监测简报。");
        String title = schedule.getName();
        String ref = String.valueOf(schedule.getId());
        runForUser(schedule, triggerType, ref, userId, username.trim(), email, title, prompt);
        return 1;
    }

    private void runForUser(AiBriefingScheduleEntity schedule, String triggerType, String triggerRef,
                            String userId, String username, String userEmail,
                            String title, String prompt) {
        AiBriefingDeliveryEntity delivery = deliveryService.createPending(
                schedule.getId(),
                triggerType,
                triggerRef,
                userId,
                schedule.getAgentId(),
                title);

        UserContext.Snapshot previous = UserContext.snapshot();
        try {
            UserContext.restore(new UserContext.Snapshot(userId, username));
            deliveryService.markRunning(delivery.getId());

            String conversationId = "briefing-" + delivery.getId();
            ChatResponseVO response = agentChatPort.run(
                    schedule.getAgentId(),
                    prompt,
                    conversationId,
                    false,
                    false,
                    null,
                    null);

            Long runId = findRunId(schedule.getAgentId(), conversationId);
            deliveryService.markSuccess(delivery.getId(), runId,
                    response != null ? response.content() : "");
            notifyService.notifyAfterSuccess(delivery.getId(), schedule, username, userEmail);
            log.info("简报生成成功 deliveryId={}, userId={}, agentId={}",
                    delivery.getId(), userId, schedule.getAgentId());
        } catch (Exception e) {
            deliveryService.markFailed(delivery.getId(), e.getMessage());
            throw e;
        } finally {
            UserContext.clear();
            if (!previous.isEmpty()) {
                UserContext.restore(previous);
            }
        }
    }

    private Long findRunId(Long agentId, String conversationId) {
        AiAgentRunEntity run = aiAgentRunMapper.selectOne(new LambdaQueryWrapper<AiAgentRunEntity>()
                .eq(AiAgentRunEntity::getAgentId, agentId)
                .eq(AiAgentRunEntity::getConversationId, conversationId)
                .orderByDesc(AiAgentRunEntity::getId)
                .last("LIMIT 1"));
        return run == null ? null : run.getId();
    }

    private Map<String, Object> findUserByUsername(String username) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id::text AS id, username, email FROM sys_user WHERE username = ? LIMIT 1",
                    username);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.debug("按用户名查询 sys_user 失败 username={}: {}", username, e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> listEnabledUsers() {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT id::text AS id, username, email, role FROM sys_user WHERE status = 1");
        } catch (Exception e) {
            log.warn("查询 sys_user 失败，尝试 status='1': {}", e.getMessage());
            return jdbcTemplate.queryForList(
                    "SELECT id::text AS id, username, email, role FROM sys_user WHERE status::text = '1'");
        }
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean llmConfigured() {
        return modelConfigPort.hasApiKey();
    }

    @Async
    @Override
    public void runNowAsync(AiBriefingScheduleEntity schedule) {
        if (!llmConfigured()) {
            log.warn("[run-now] 未在模型设置中配置 API Key，跳过 scheduleId={}", schedule.getId());
            return;
        }
        try {
            int count = runSchedule(schedule, "RUN_NOW");
            log.info("[run-now] 简报异步生成完成 scheduleId={}, generated={}", schedule.getId(), count);
        } catch (Exception e) {
            log.error("[run-now] 简报异步生成异常 scheduleId={}: {}", schedule.getId(), e.getMessage(), e);
        }
    }
}

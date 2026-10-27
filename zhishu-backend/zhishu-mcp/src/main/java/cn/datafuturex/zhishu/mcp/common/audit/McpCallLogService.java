package cn.datafuturex.zhishu.mcp.common.audit;

import cn.datafuturex.zhishu.mcp.common.audit.entity.AiMcpCallLogEntity;
import cn.datafuturex.zhishu.mcp.common.audit.mapper.AiMcpCallLogMapper;
import cn.datafuturex.zhishu.mcp.common.audit.vo.McpCallLogVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP 调用审计写入器（两个平面共用的底层设施，方向由 {@link McpCallDirection} 区分）。
 * <p>
 * provide 平面只调 {@link #recordServe}，consume 平面只调 {@link #recordConsume}；
 * 二者互不引用，从而保持平面不交叉。
 * <p>
 * 注意：写入是 {@code @Async}，执行时已离开请求线程，因此调用方身份必须由调用侧
 * <b>显式传入</b>，不能在本类里读 {@code UserContext} ThreadLocal（否则 {@code user_id} 永远为空）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpCallLogService {

    private final AiMcpCallLogMapper callLogMapper;

    /**
     * 记录外部 Client 调用本平台 Tool（provide 平面）。
     *
     * @param userId 请求线程上的调用方身份（Client 绑定用户或 JWT 用户名），由调用侧捕获后传入
     */
    @Async
    public void recordServe(Long clientId, String userId, String toolName, boolean success,
                            String error, int durationMs) {
        insert(McpCallDirection.SERVE, clientId, null, null, userId, toolName, success, error, durationMs);
    }

    /**
     * 记录本平台调用上游 Tool（consume 平面）。
     *
     * @param userId 请求线程上的操作用户，由调用侧捕获后传入
     */
    @Async
    public void recordConsume(Long upstreamId, Long agentId, String userId, String toolName, boolean success,
                              String error, int durationMs) {
        insert(McpCallDirection.CONSUME, null, upstreamId, agentId, userId, toolName, success, error, durationMs);
    }

    /**
     * 审计查询；{@code direction} 兼容历史值 {@code IN}/{@code OUT}，无法识别时不过滤。
     */
    public List<McpCallLogVO> list(String direction, int limit) {
        int lim = Math.min(Math.max(limit, 1), 200);
        LambdaQueryWrapper<AiMcpCallLogEntity> qw = new LambdaQueryWrapper<AiMcpCallLogEntity>()
                .orderByDesc(AiMcpCallLogEntity::getId)
                .last("LIMIT " + lim);
        McpCallDirection normalized = McpCallDirection.fromFilter(direction);
        if (normalized != null) {
            qw.eq(AiMcpCallLogEntity::getDirection, normalized.name());
        }
        return callLogMapper.selectList(qw).stream().map(this::toVo).toList();
    }

    private void insert(
            McpCallDirection direction,
            Long clientId,
            Long upstreamId,
            Long agentId,
            String userId,
            String toolName,
            boolean success,
            String error,
            int durationMs) {
        try {
            AiMcpCallLogEntity entity = new AiMcpCallLogEntity();
            entity.setDirection(direction.name());
            entity.setClientId(clientId);
            entity.setUpstreamId(upstreamId);
            entity.setAgentId(agentId);
            entity.setToolName(toolName);
            entity.setSuccess(success);
            entity.setErrorMessage(truncate(error, 500));
            entity.setDurationMs(durationMs);
            entity.setUserId(truncate(userId, 64));
            entity.setCreateTime(LocalDateTime.now());
            callLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("写入 MCP 调用日志失败: {}", e.getMessage());
        }
    }

    private McpCallLogVO toVo(AiMcpCallLogEntity e) {
        return new McpCallLogVO(
                e.getId(),
                e.getDirection(),
                e.getClientId(),
                e.getUpstreamId(),
                e.getAgentId(),
                e.getToolName(),
                Boolean.TRUE.equals(e.getSuccess()),
                e.getErrorMessage(),
                e.getDurationMs(),
                e.getUserId(),
                e.getCreateTime());
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}

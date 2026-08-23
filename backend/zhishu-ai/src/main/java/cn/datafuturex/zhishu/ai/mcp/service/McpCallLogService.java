package cn.datafuturex.zhishu.ai.mcp.service;

import cn.datafuturex.zhishu.ai.mcp.domain.entity.AiMcpCallLogEntity;
import cn.datafuturex.zhishu.ai.mcp.domain.vo.McpCallLogVO;
import cn.datafuturex.zhishu.ai.mcp.mapper.AiMcpCallLogMapper;
import cn.datafuturex.zhishu.ai.shared.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class McpCallLogService {

    private final AiMcpCallLogMapper callLogMapper;

    @Async
    public void recordOut(Long clientId, String toolName, boolean success, String error, int durationMs) {
        insert("OUT", clientId, null, null, toolName, success, error, durationMs);
    }

    @Async
    public void recordIn(Long upstreamId, Long agentId, String toolName, boolean success, String error, int durationMs) {
        insert("IN", null, upstreamId, agentId, toolName, success, error, durationMs);
    }

    public List<McpCallLogVO> list(String direction, int limit) {
        int lim = Math.min(Math.max(limit, 1), 200);
        LambdaQueryWrapper<AiMcpCallLogEntity> qw = new LambdaQueryWrapper<AiMcpCallLogEntity>()
                .orderByDesc(AiMcpCallLogEntity::getId)
                .last("LIMIT " + lim);
        if (StringUtils.hasText(direction)) {
            qw.eq(AiMcpCallLogEntity::getDirection, direction.trim().toUpperCase());
        }
        return callLogMapper.selectList(qw).stream().map(this::toVo).toList();
    }

    private void insert(
            String direction,
            Long clientId,
            Long upstreamId,
            Long agentId,
            String toolName,
            boolean success,
            String error,
            int durationMs) {
        try {
            AiMcpCallLogEntity entity = new AiMcpCallLogEntity();
            entity.setDirection(direction);
            entity.setClientId(clientId);
            entity.setUpstreamId(upstreamId);
            entity.setAgentId(agentId);
            entity.setToolName(toolName);
            entity.setSuccess(success);
            entity.setErrorMessage(truncate(error, 500));
            entity.setDurationMs(durationMs);
            entity.setUserId(UserContext.getUserId());
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

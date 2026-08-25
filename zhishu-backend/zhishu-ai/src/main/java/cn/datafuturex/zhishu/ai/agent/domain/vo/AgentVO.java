package cn.datafuturex.zhishu.ai.agent.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "智能体详情")
public record AgentVO(
        Long id,
        String code,
        String name,
        String description,
        String systemPrompt,
        String model,
        Double temperature,
        Integer maxTokens,
        List<String> capabilities,
        String workflowType,
        String workflowConfig,
        List<Long> documentIds,
        Boolean enableMemory,
        String status,
        Boolean builtin,
        Boolean defaultAgent,
        String createdBy,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        List<Long> mcpUpstreamIds
) {
}

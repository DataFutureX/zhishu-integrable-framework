package cn.datafuturex.zhishu.ai.agent.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "根据表单字段生成系统提示词初稿")
public record AgentPromptDraftDTO(
        @Size(max = 128)
        String name,

        @Size(max = 500)
        String description,

        List<String> capabilities,

        @Schema(description = "REACT | SEQUENTIAL | ROUTING | GRAPH")
        String workflowType,

        Boolean enableMemory,

        @Schema(description = "已勾选的上游 MCP 显示名")
        List<String> mcpUpstreamNames,

        @Schema(description = "已绑定知识库文档名；空表示全部或未绑")
        List<String> documentNames,

        @Size(max = 8000)
        @Schema(description = "当前提示词，若有则在其基础上按新表单改写")
        String existingPrompt
) {
}

package cn.datafuturex.zhishu.ai.agent.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "更新智能体")
public record AgentUpdateDTO(
        @NotBlank
        @Size(max = 128)
        String name,

        @Size(max = 500)
        String description,

        @NotBlank
        String systemPrompt,

        String model,

        Double temperature,

        Integer maxTokens,

        @NotEmpty
        List<String> capabilities,

        @NotBlank
        String workflowType,

        String workflowConfig,

        @Schema(description = "绑定知识库文档 ID，空=全部")
        List<Long> documentIds,

        @Schema(description = "绑定的上游 MCP ID")
        List<Long> mcpUpstreamIds,

        @NotNull
        Boolean enableMemory,

        @NotBlank
        String status
) {
}

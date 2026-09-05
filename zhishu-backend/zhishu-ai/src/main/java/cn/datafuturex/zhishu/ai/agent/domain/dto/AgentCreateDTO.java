package cn.datafuturex.zhishu.ai.agent.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "创建智能体")
public record AgentCreateDTO(
        @NotBlank
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{1,62}$", message = "编码需以字母开头，仅含字母数字下划线")
        @Schema(description = "唯一编码", example = "alert_helper")
        String code,

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
        @Schema(description = "REACT | SEQUENTIAL | ROUTING | GRAPH")
        String workflowType,

        String workflowConfig,

        @Schema(description = "绑定知识库文档 ID，空=全部")
        List<Long> documentIds,

        @Schema(description = "绑定的上游 MCP ID")
        List<Long> mcpUpstreamIds,

        @Schema(description = "绑定的模型设置 ID，空=使用默认")
        Long modelProviderId,

        @NotNull
        Boolean enableMemory,

        @Schema(description = "ENABLED | DISABLED，默认 ENABLED")
        String status
) {
}

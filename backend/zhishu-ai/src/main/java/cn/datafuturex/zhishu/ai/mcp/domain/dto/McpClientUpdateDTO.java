package cn.datafuturex.zhishu.ai.mcp.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "更新对外 MCP Client")
public record McpClientUpdateDTO(
        @NotBlank @Size(max = 128) String name,
        @NotNull Long boundUserId,
        String boundUsername,
        List<String> capabilities,
        Integer rpmLimit,
        @NotBlank String status,
        @Size(max = 500) String remark
) {
}

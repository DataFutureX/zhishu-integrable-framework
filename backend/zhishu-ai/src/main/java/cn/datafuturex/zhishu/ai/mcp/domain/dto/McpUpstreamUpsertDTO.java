package cn.datafuturex.zhishu.ai.mcp.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "登记他方 MCP Server")
public record McpUpstreamUpsertDTO(
        @NotBlank
        @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$", message = "编码需小写字母开头，仅含小写字母、数字、下划线和连字符")
        String code,
        @NotBlank @Size(max = 128) String name,
        String protocol,
        @NotBlank @Size(max = 512) String baseUrl,
        String endpoint,
        String authHeader,
        Integer requestTimeoutMs,
        String status,
        @Size(max = 500) String remark
) {
}

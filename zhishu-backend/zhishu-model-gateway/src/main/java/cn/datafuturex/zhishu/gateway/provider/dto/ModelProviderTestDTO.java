package cn.datafuturex.zhishu.gateway.provider.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 供应商连通性测试（保存前试跑）请求体。
 * <p>
 * 用于「新建/编辑供应商」弹窗中不落库直接测试：
 * <ul>
 *     <li>{@code apiKey} 有值：用它发起探测（不会入库）；</li>
 *     <li>{@code apiKey} 为空且带 {@code providerId}：回落到库里已保存的 Key（编辑态留空表示不修改）；</li>
 *     <li>都没有：按匿名方式探测，适配 Ollama / vLLM / LM Studio 等无需鉴权的自建上游。</li>
 * </ul>
 */
@Schema(description = "供应商连通性测试（保存前试跑）")
public record ModelProviderTestDTO(

        @Schema(description = "供应商 ID（编辑态传，用于回落到已保存的 Key）")
        Long providerId,

        @Size(max = 64)
        @Schema(description = "程序标识（如 dashscope / ollama；留空按 OpenAI 兼容处理）")
        String providerKey,

        @NotBlank
        @Size(max = 512)
        @Schema(description = "OpenAI 兼容 Base URL", requiredMode = Schema.RequiredMode.REQUIRED)
        String baseUrl,

        @Size(max = 512)
        @Schema(description = "API Key（可为空：留空时回落到已保存 Key 或匿名探测）")
        String apiKey
) {
}

package cn.datafuturex.zhishu.ai.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 文档上传请求 DTO
 */
@Schema(description = "文档上传请求对象")
public record DocumentUploadDTO(
        @NotBlank(message = "文档标题不能为空")
        @Schema(description = "文档标题或描述", example = "水文监测报告2024",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "所属知识库分类 ID", example = "1")
        Long categoryId) {
}

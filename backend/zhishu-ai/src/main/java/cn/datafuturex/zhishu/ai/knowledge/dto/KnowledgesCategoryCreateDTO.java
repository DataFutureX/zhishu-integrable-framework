package cn.datafuturex.zhishu.ai.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "创建知识库分类")
public record KnowledgesCategoryCreateDTO(
        @NotBlank(message = "编码不能为空")
        @Size(max = 64)
        @Schema(description = "唯一编码", example = "hydrology", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,

        @NotBlank(message = "名称不能为空")
        @Size(max = 128)
        @Schema(description = "知识库名称", example = "水文监测", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Size(max = 500)
        @Schema(description = "描述")
        String description,

        @Schema(description = "排序，越小越靠前", example = "30")
        Integer sortOrder
) {
}

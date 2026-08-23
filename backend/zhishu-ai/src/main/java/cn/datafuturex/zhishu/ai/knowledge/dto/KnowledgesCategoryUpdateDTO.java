package cn.datafuturex.zhishu.ai.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "更新知识库分类")
public record KnowledgesCategoryUpdateDTO(
        @Size(max = 128)
        @Schema(description = "知识库名称")
        String name,

        @Size(max = 500)
        @Schema(description = "描述")
        String description,

        @Schema(description = "排序")
        Integer sortOrder,

        @Schema(description = "状态 ENABLED/DISABLED")
        String status
) {
}

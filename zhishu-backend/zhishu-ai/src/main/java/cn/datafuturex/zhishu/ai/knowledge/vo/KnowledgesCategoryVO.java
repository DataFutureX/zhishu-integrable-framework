package cn.datafuturex.zhishu.ai.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "知识库分类（一类知识库）")
public record KnowledgesCategoryVO(
        @Schema(description = "分类 ID") String id,
        @Schema(description = "唯一编码") String code,
        @Schema(description = "知识库名称") String name,
        @Schema(description = "描述") String description,
        @Schema(description = "排序") Integer sortOrder,
        @Schema(description = "状态 ENABLED/DISABLED") String status,
        @Schema(description = "文档数量") Long documentCount,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime
) {
}

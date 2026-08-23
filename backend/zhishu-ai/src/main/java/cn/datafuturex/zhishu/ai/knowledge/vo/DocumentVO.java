package cn.datafuturex.zhishu.ai.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 文档信息响应 VO
 */
@Schema(description = "文档信息响应对象")
public record DocumentVO(
        @Schema(description = "文档唯一标识", example = "1") String id,

        @Schema(description = "文档文件名", example = "水文监测报告.pdf") String fileName,

        @Schema(description = "文档类型", example = "pdf") String fileType,

        @Schema(description = "文档大小（字节）", example = "1024000") Long fileSize,

        @Schema(description = "文档上传时间", example = "2026-06-12T10:30:00") LocalDateTime uploadTime,

        @Schema(description = "是否已完成向量化处理", example = "true") Boolean processed,

        @Schema(description = "所属知识库分类 ID") String categoryId,

        @Schema(description = "所属知识库名称") String categoryName,

        @Schema(description = "文档解析后的文本内容") String content) {
}

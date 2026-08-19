package com.datafuturex.assistant.knowledge.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识文档实体 —— 表 knowledges
 */
@TableName("knowledges")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("file_name")
    private String fileName;

    /** 所属知识库分类 */
    @TableField("category_id")
    private Long categoryId;

    @TableField("file_type")
    private String fileType;

    @TableField("file_path")
    private String filePath;

    @TableField("file_size")
    private Long fileSize;

    @TableField("content")
    private String content;

    @TableField("upload_time")
    private LocalDateTime uploadTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableField("processed")
    private Boolean processed = false;
}

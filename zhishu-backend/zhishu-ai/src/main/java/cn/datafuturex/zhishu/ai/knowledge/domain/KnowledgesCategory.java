package cn.datafuturex.zhishu.ai.knowledge.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledges_category")
public class KnowledgesCategory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String description;

    @TableField("sort_order")
    private Integer sortOrder;

    private String status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}

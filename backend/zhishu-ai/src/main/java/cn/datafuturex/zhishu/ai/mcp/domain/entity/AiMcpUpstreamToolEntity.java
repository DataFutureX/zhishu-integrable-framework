package cn.datafuturex.zhishu.ai.mcp.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_mcp_upstream_tool")
public class AiMcpUpstreamToolEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("upstream_id")
    private Long upstreamId;

    @TableField("original_name")
    private String originalName;

    @TableField("exposed_name")
    private String exposedName;

    private String description;

    private Boolean enabled;

    @TableField("update_time")
    private LocalDateTime updateTime;
}

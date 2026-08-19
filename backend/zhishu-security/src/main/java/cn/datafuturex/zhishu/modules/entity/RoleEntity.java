package cn.datafuturex.zhishu.modules.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统角色实体
 */
@Data
@TableName("sys_role")
public class RoleEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String roleCode;

    private String roleName;

    private String description;

    private Integer status;

    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

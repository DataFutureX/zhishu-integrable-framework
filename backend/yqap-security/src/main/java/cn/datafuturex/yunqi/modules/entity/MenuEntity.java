package cn.datafuturex.yunqi.modules.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统菜单实体
 */
@Data
@TableName("sys_menu")
public class MenuEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long parentId;

    private String title;

    private String path;

    private String routeName;

    private String redirect;

    private String icon;

    /** DIRECTORY / MENU / PAGE / BUTTON */
    private String menuType;

    private Integer visible;

    private Integer requiresAuth;

    private Integer sort;

    private String component;

    private String meta;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

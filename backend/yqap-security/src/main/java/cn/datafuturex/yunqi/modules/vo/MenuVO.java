package cn.datafuturex.yunqi.modules.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树形视图对象
 */
@Data
public class MenuVO {

    private Long id;

    private Long parentId;

    private String title;

    private String path;

    private String routeName;

    private String redirect;

    private String icon;

    private String menuType;

    private Integer visible;

    private Integer requiresAuth;

    private Integer sort;

    private String component;

    private String meta;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private List<MenuVO> children = new ArrayList<>();
}

package cn.datafuturex.zhishu.modules.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色视图对象
 */
public record RoleVO(
        Long id,
        String roleCode,
        String roleName,
        String description,
        Integer status,
        Integer sort,
        List<Long> menuIds,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}

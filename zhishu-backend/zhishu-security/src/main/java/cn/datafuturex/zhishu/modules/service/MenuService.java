package cn.datafuturex.zhishu.modules.service;

import cn.datafuturex.zhishu.modules.dto.MenuCreateDTO;
import cn.datafuturex.zhishu.modules.dto.MenuUpdateDTO;
import cn.datafuturex.zhishu.modules.entity.MenuEntity;
import cn.datafuturex.zhishu.modules.vo.MenuVO;

import java.util.List;
import java.util.Optional;

/**
 * 菜单管理服务
 */
public interface MenuService {

    MenuEntity create(MenuCreateDTO dto);

    MenuEntity update(MenuUpdateDTO dto);

    void delete(Long id);

    Optional<MenuEntity> findById(Long id);

    List<MenuVO> getMenuTree();

    List<MenuVO> getMenuTreeByRoleCode(String roleCode);

    /**
     * 当前用户侧栏菜单树（不含 BUTTON）
     */
    List<MenuVO> getCurrentUserMenuTree(String username);

    /**
     * 当前用户已授权的按钮权限值列表
     */
    List<String> getCurrentUserPermissions(String username);
}

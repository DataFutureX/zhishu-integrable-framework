package cn.datafuturex.yunqi.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.datafuturex.yunqi.modules.constant.PermissionConstants;
import cn.datafuturex.yunqi.modules.dto.MenuCreateDTO;
import cn.datafuturex.yunqi.modules.dto.MenuUpdateDTO;
import cn.datafuturex.yunqi.modules.entity.MenuEntity;
import cn.datafuturex.yunqi.modules.entity.RoleEntity;
import cn.datafuturex.yunqi.modules.entity.RoleMenuEntity;
import cn.datafuturex.yunqi.modules.entity.UserEntity;
import cn.datafuturex.yunqi.modules.mapper.MenuMapper;
import cn.datafuturex.yunqi.modules.mapper.RoleMapper;
import cn.datafuturex.yunqi.modules.mapper.RoleMenuMapper;
import cn.datafuturex.yunqi.modules.mapper.UserMapper;
import cn.datafuturex.yunqi.modules.service.MenuService;
import cn.datafuturex.yunqi.modules.service.PermissionService;
import cn.datafuturex.yunqi.modules.vo.MenuVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜单管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private final MenuMapper menuMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final UserMapper userMapper;
    private final PermissionService permissionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MenuEntity create(MenuCreateDTO dto) {
        validateButtonParent(dto.menuType(), dto.parentId());

        MenuEntity menu = new MenuEntity();
        if (dto.id() != null) {
            if (menuMapper.selectById(dto.id()) != null) {
                throw new RuntimeException("菜单ID已存在: id=" + dto.id());
            }
            menu.setId(dto.id());
        } else {
            menu.setId(generateMenuId());
        }
        fillMenuFromCreateDto(menu, dto);
        applyButtonDefaults(menu);
        menu.setCreateTime(LocalDateTime.now());
        menu.setUpdateTime(LocalDateTime.now());

        menuMapper.insert(menu);
        permissionService.evictAllCache();
        log.info("创建菜单成功: id={}, title={}, type={}", menu.getId(), menu.getTitle(), menu.getMenuType());
        return menu;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MenuEntity update(MenuUpdateDTO dto) {
        MenuEntity menu = menuMapper.selectById(dto.id());
        if (menu == null) {
            throw new RuntimeException("菜单不存在: id=" + dto.id());
        }

        String targetType = StringUtils.hasText(dto.menuType()) ? dto.menuType() : menu.getMenuType();
        Long targetParentId = dto.parentId() != null ? dto.parentId() : menu.getParentId();
        validateButtonParent(targetType, targetParentId);

        if (dto.parentId() != null) {
            if (dto.parentId().equals(dto.id())) {
                throw new RuntimeException("父菜单不能是自身");
            }
            if (dto.parentId() > 0 && menuMapper.selectById(dto.parentId()) == null) {
                throw new RuntimeException("父菜单不存在: id=" + dto.parentId());
            }
            menu.setParentId(dto.parentId());
        }

        menu.setTitle(dto.title());
        if (dto.path() != null) {
            menu.setPath(dto.path());
        }
        if (dto.routeName() != null) {
            menu.setRouteName(dto.routeName());
        }
        if (dto.redirect() != null) {
            menu.setRedirect(dto.redirect());
        }
        if (dto.icon() != null) {
            menu.setIcon(dto.icon());
        }
        if (StringUtils.hasText(dto.menuType())) {
            menu.setMenuType(dto.menuType());
        }
        if (dto.visible() != null) {
            menu.setVisible(dto.visible());
        }
        if (dto.requiresAuth() != null) {
            menu.setRequiresAuth(dto.requiresAuth());
        }
        if (dto.sort() != null) {
            menu.setSort(dto.sort());
        }
        if (dto.component() != null) {
            menu.setComponent(dto.component());
        }
        if (dto.meta() != null) {
            menu.setMeta(dto.meta());
        }
        if (dto.status() != null) {
            menu.setStatus(dto.status());
        }
        applyButtonDefaults(menu);
        menu.setUpdateTime(LocalDateTime.now());

        menuMapper.updateById(menu);
        permissionService.evictAllCache();
        log.info("更新菜单成功: id={}, title={}", menu.getId(), menu.getTitle());
        return menu;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MenuEntity menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new RuntimeException("菜单不存在: id=" + id);
        }

        LambdaQueryWrapper<MenuEntity> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(MenuEntity::getParentId, id);
        if (menuMapper.selectCount(childWrapper) > 0) {
            throw new RuntimeException("存在子菜单，无法删除");
        }

        LambdaQueryWrapper<RoleMenuEntity> roleMenuWrapper = new LambdaQueryWrapper<>();
        roleMenuWrapper.eq(RoleMenuEntity::getMenuId, id);
        roleMenuMapper.delete(roleMenuWrapper);

        menuMapper.deleteById(id);
        permissionService.evictAllCache();
        log.info("删除菜单成功: id={}, title={}", id, menu.getTitle());
    }

    @Override
    public Optional<MenuEntity> findById(Long id) {
        return Optional.ofNullable(menuMapper.selectById(id));
    }

    @Override
    public List<MenuVO> getMenuTree() {
        List<MenuEntity> allMenus = listEnabledMenus();
        return buildTree(allMenus, 0L);
    }

    @Override
    public List<MenuVO> getMenuTreeByRoleCode(String roleCode) {
        Set<Long> menuIds = getMenuIdsByRoleCode(roleCode);
        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, MenuEntity> menuMap = listEnabledMenus().stream()
                .collect(Collectors.toMap(MenuEntity::getId, m -> m));

        Set<Long> requiredIds = new HashSet<>(menuIds);
        for (Long menuId : menuIds) {
            Long parentId = menuMap.containsKey(menuId) ? menuMap.get(menuId).getParentId() : null;
            while (parentId != null && parentId > 0) {
                requiredIds.add(parentId);
                MenuEntity parent = menuMap.get(parentId);
                parentId = parent != null ? parent.getParentId() : null;
            }
        }

        List<MenuEntity> menusWithParents = listEnabledMenus().stream()
                .filter(m -> requiredIds.contains(m.getId()))
                .collect(Collectors.toList());

        return buildTree(menusWithParents, 0L);
    }

    @Override
    public List<MenuVO> getCurrentUserMenuTree(String username) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getUsername, username);
        UserEntity user = userMapper.selectOne(wrapper);
        if (user == null || user.getStatus() == null || user.getStatus() != 1
                || !StringUtils.hasText(user.getRole())) {
            return Collections.emptyList();
        }
        return filterOutButtons(getMenuTreeByRoleCode(user.getRole()));
    }

    @Override
    public List<String> getCurrentUserPermissions(String username) {
        List<String> codes = permissionService.listPermissionCodesByUsername(username);
        return codes != null ? codes : Collections.emptyList();
    }

    private void validateButtonParent(String menuType, Long parentId) {
        if (!PermissionConstants.MENU_TYPE_BUTTON.equals(menuType)) {
            if (parentId != null && parentId > 0) {
                MenuEntity parent = menuMapper.selectById(parentId);
                if (parent == null) {
                    throw new RuntimeException("父菜单不存在: id=" + parentId);
                }
            }
            return;
        }
        if (parentId == null || parentId <= 0) {
            throw new RuntimeException("按钮权限必须挂载在菜单下");
        }
        MenuEntity parent = menuMapper.selectById(parentId);
        if (parent == null) {
            throw new RuntimeException("父菜单不存在: id=" + parentId);
        }
        if (!PermissionConstants.MENU_TYPE_MENU.equals(parent.getMenuType())) {
            throw new RuntimeException("按钮权限只能挂载在菜单类型节点下");
        }
    }

    private void applyButtonDefaults(MenuEntity menu) {
        if (!PermissionConstants.MENU_TYPE_BUTTON.equals(menu.getMenuType())) {
            return;
        }
        if (!StringUtils.hasText(menu.getRouteName())) {
            throw new RuntimeException("按钮权限必须填写权限标识（routeName）");
        }
        menu.setVisible(0);
        menu.setPath(null);
        menu.setComponent(null);
        menu.setRedirect(null);
    }

    private List<MenuVO> filterOutButtons(List<MenuVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        return nodes.stream()
                .filter(node -> !PermissionConstants.MENU_TYPE_BUTTON.equals(node.getMenuType()))
                .peek(node -> {
                    if (node.getChildren() != null) {
                        node.setChildren(filterOutButtons(node.getChildren()));
                    }
                })
                .collect(Collectors.toList());
    }

    private List<MenuEntity> listEnabledMenus() {
        LambdaQueryWrapper<MenuEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MenuEntity::getStatus, 1)
                .orderByAsc(MenuEntity::getSort)
                .orderByAsc(MenuEntity::getId);
        return menuMapper.selectList(wrapper);
    }

    private Set<Long> getMenuIdsByRoleCode(String roleCode) {
        LambdaQueryWrapper<RoleEntity> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq(RoleEntity::getRoleCode, roleCode)
                .eq(RoleEntity::getStatus, 1);
        RoleEntity role = roleMapper.selectOne(roleWrapper);
        if (role == null) {
            return Collections.emptySet();
        }

        LambdaQueryWrapper<RoleMenuEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoleMenuEntity::getRoleId, role.getId());
        return roleMenuMapper.selectList(wrapper).stream()
                .map(RoleMenuEntity::getMenuId)
                .collect(Collectors.toSet());
    }

    private void fillMenuFromCreateDto(MenuEntity menu, MenuCreateDTO dto) {
        menu.setParentId(dto.parentId() != null ? dto.parentId() : 0L);
        menu.setTitle(dto.title());
        menu.setPath(dto.path());
        menu.setRouteName(dto.routeName());
        menu.setRedirect(dto.redirect());
        menu.setIcon(dto.icon());
        menu.setMenuType(dto.menuType());
        menu.setVisible(dto.visible() != null ? dto.visible() : 1);
        menu.setRequiresAuth(dto.requiresAuth() != null ? dto.requiresAuth() : 1);
        menu.setSort(dto.sort() != null ? dto.sort() : 0);
        menu.setComponent(dto.component());
        menu.setMeta(dto.meta());
        menu.setStatus(dto.status() != null ? dto.status() : 1);
    }

    private Long generateMenuId() {
        LambdaQueryWrapper<MenuEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MenuEntity::getId).last("LIMIT 1");
        MenuEntity latest = menuMapper.selectOne(wrapper);
        if (latest == null) {
            return 100L;
        }
        return latest.getId() >= 9000 ? latest.getId() + 1 : latest.getId() + 1;
    }

    private List<MenuVO> buildTree(List<MenuEntity> menus, Long parentId) {
        return menus.stream()
                .filter(m -> Objects.equals(m.getParentId(), parentId))
                .map(this::toVO)
                .peek(vo -> vo.setChildren(buildTree(menus, vo.getId())))
                .collect(Collectors.toList());
    }

    private MenuVO toVO(MenuEntity entity) {
        MenuVO vo = new MenuVO();
        vo.setId(entity.getId());
        vo.setParentId(entity.getParentId());
        vo.setTitle(entity.getTitle());
        vo.setPath(entity.getPath());
        vo.setRouteName(entity.getRouteName());
        vo.setRedirect(entity.getRedirect());
        vo.setIcon(entity.getIcon());
        vo.setMenuType(entity.getMenuType());
        vo.setVisible(entity.getVisible());
        vo.setRequiresAuth(entity.getRequiresAuth());
        vo.setSort(entity.getSort());
        vo.setComponent(entity.getComponent());
        vo.setMeta(entity.getMeta());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}

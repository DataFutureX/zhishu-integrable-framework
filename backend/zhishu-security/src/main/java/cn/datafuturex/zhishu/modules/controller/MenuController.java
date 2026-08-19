package cn.datafuturex.zhishu.modules.controller;

import cn.datafuturex.zhishu.common.Result;
import cn.datafuturex.zhishu.modules.constant.PermissionConstants;
import cn.datafuturex.zhishu.modules.dto.MenuCreateDTO;
import cn.datafuturex.zhishu.modules.dto.MenuUpdateDTO;
import cn.datafuturex.zhishu.modules.entity.MenuEntity;
import cn.datafuturex.zhishu.modules.service.MenuService;
import cn.datafuturex.zhishu.modules.vo.MenuVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
@Tag(name = "菜单管理", description = "系统菜单增删改查及当前用户菜单树")
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_MENU_QUERY + "') or hasAuthority('" + PermissionConstants.SYSTEM_ROLE_ASSIGN_MENU + "')")
    @Operation(summary = "查询完整菜单树", description = "返回全部启用菜单的树形结构（含按钮权限），供菜单管理与角色授权使用")
    public Result<List<MenuVO>> getMenuTree() {
        try {
            return Result.success(menuService.getMenuTree());
        } catch (Exception e) {
            log.error("查询菜单树失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/current-user")
    @Operation(summary = "查询当前用户菜单树", description = "根据登录用户角色返回已授权的侧边栏菜单树（不含按钮）")
    public Result<List<MenuVO>> getCurrentUserMenus() {
        try {
            String username = requireUsername();
            if (username == null) {
                return Result.error(401, "未登录");
            }
            return Result.success(menuService.getCurrentUserMenuTree(username));
        } catch (Exception e) {
            log.error("查询当前用户菜单失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/current-user/permissions")
    @Operation(summary = "查询当前用户权限值", description = "返回已授权的按钮权限标识列表")
    public Result<List<String>> getCurrentUserPermissions() {
        try {
            String username = requireUsername();
            if (username == null) {
                return Result.error(401, "未登录");
            }
            return Result.success(menuService.getCurrentUserPermissions(username));
        } catch (Exception e) {
            log.error("查询当前用户权限失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/role/{roleCode}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_MENU_QUERY + "') or hasAuthority('" + PermissionConstants.SYSTEM_ROLE_QUERY + "')")
    @Operation(summary = "按角色查询菜单树", description = "根据角色编码返回已授权菜单树（含父级目录）")
    public Result<List<MenuVO>> getMenuTreeByRole(@PathVariable String roleCode) {
        try {
            return Result.success(menuService.getMenuTreeByRoleCode(roleCode));
        } catch (Exception e) {
            log.error("按角色查询菜单树失败: roleCode={}", roleCode, e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_MENU_QUERY + "')")
    @Operation(summary = "查询菜单详情")
    public Result<MenuEntity> findById(@PathVariable Long id) {
        try {
            return menuService.findById(id)
                    .map(Result::success)
                    .orElse(Result.error("菜单不存在"));
        } catch (Exception e) {
            log.error("查询菜单失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_MENU_ADD + "')")
    @Operation(summary = "创建菜单")
    public Result<MenuEntity> create(@Valid @RequestBody MenuCreateDTO dto) {
        try {
            return Result.success(menuService.create(dto));
        } catch (Exception e) {
            log.error("创建菜单失败", e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_MENU_EDIT + "')")
    @Operation(summary = "更新菜单")
    public Result<MenuEntity> update(@Valid @RequestBody MenuUpdateDTO dto) {
        try {
            return Result.success(menuService.update(dto));
        } catch (Exception e) {
            log.error("更新菜单失败", e);
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_MENU_REMOVE + "')")
    @Operation(summary = "删除菜单")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            menuService.delete(id);
            return Result.success();
        } catch (Exception e) {
            log.error("删除菜单失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    private String requireUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return null;
        }
        return auth.getName();
    }
}

package cn.datafuturex.yunqi.modules.controller;

import cn.datafuturex.yunqi.common.PageResult;
import cn.datafuturex.yunqi.common.Result;
import cn.datafuturex.yunqi.modules.constant.PermissionConstants;
import cn.datafuturex.yunqi.modules.dto.RoleCreateDTO;
import cn.datafuturex.yunqi.modules.dto.RoleMenuAssignDTO;
import cn.datafuturex.yunqi.modules.dto.RoleQueryDTO;
import cn.datafuturex.yunqi.modules.dto.RoleUpdateDTO;
import cn.datafuturex.yunqi.modules.entity.RoleEntity;
import cn.datafuturex.yunqi.modules.service.RoleService;
import cn.datafuturex.yunqi.modules.vo.RoleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "系统角色增删改查及菜单授权")
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_ROLE_QUERY + "')")
    @Operation(summary = "分页查询角色")
    public Result<PageResult<RoleVO>> pageQuery(RoleQueryDTO queryDTO) {
        try {
            return Result.success(roleService.pageQuery(queryDTO));
        } catch (Exception e) {
            log.error("分页查询角色失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_ROLE_QUERY + "') or hasAuthority('" + PermissionConstants.SYSTEM_USER_ASSIGN_ROLE + "') or hasAuthority('" + PermissionConstants.SYSTEM_USER_QUERY + "')")
    @Operation(summary = "查询全部启用角色", description = "下拉选择等场景使用")
    public Result<List<RoleVO>> listAll() {
        try {
            return Result.success(roleService.listAll());
        } catch (Exception e) {
            log.error("查询角色列表失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_ROLE_QUERY + "')")
    @Operation(summary = "查询角色详情", description = "包含已授权菜单ID列表")
    public Result<RoleVO> findById(@PathVariable Long id) {
        try {
            return roleService.findById(id)
                    .map(Result::success)
                    .orElse(Result.error("角色不存在"));
        } catch (Exception e) {
            log.error("查询角色失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_ROLE_QUERY + "') or hasAuthority('" + PermissionConstants.SYSTEM_ROLE_ASSIGN_MENU + "')")
    @Operation(summary = "查询角色已授权菜单ID")
    public Result<List<Long>> getMenuIds(@PathVariable Long id) {
        try {
            return Result.success(roleService.getMenuIdsByRoleId(id));
        } catch (Exception e) {
            log.error("查询角色菜单失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_ROLE_ASSIGN_MENU + "')")
    @Operation(summary = "为角色分配菜单", description = "全量覆盖式授权，传入菜单ID列表")
    public Result<Void> assignMenus(@PathVariable Long id, @Valid @RequestBody RoleMenuAssignDTO dto) {
        try {
            roleService.assignMenus(id, dto);
            return Result.success();
        } catch (Exception e) {
            log.error("角色菜单授权失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_ROLE_ADD + "')")
    @Operation(summary = "创建角色")
    public Result<RoleEntity> create(@Valid @RequestBody RoleCreateDTO dto) {
        try {
            return Result.success(roleService.create(dto));
        } catch (Exception e) {
            log.error("创建角色失败", e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_ROLE_EDIT + "')")
    @Operation(summary = "更新角色")
    public Result<RoleEntity> update(@Valid @RequestBody RoleUpdateDTO dto) {
        try {
            return Result.success(roleService.update(dto));
        } catch (Exception e) {
            log.error("更新角色失败", e);
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_ROLE_REMOVE + "')")
    @Operation(summary = "删除角色")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            roleService.delete(id);
            return Result.success();
        } catch (Exception e) {
            log.error("删除角色失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }
}

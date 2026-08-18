package cn.datafuturex.yunqi.modules.controller;

import cn.datafuturex.yunqi.common.PageResult;
import cn.datafuturex.yunqi.common.Result;
import cn.datafuturex.yunqi.modules.constant.PermissionConstants;
import cn.datafuturex.yunqi.modules.dto.UserCreateDTO;
import cn.datafuturex.yunqi.modules.dto.UserPasswordChangeDTO;
import cn.datafuturex.yunqi.modules.dto.UserPasswordResetDTO;
import cn.datafuturex.yunqi.modules.dto.UserProfileUpdateDTO;
import cn.datafuturex.yunqi.modules.dto.UserQueryDTO;
import cn.datafuturex.yunqi.modules.dto.UserRoleAssignDTO;
import cn.datafuturex.yunqi.modules.dto.UserStatusUpdateDTO;
import cn.datafuturex.yunqi.modules.dto.UserUpdateDTO;
import cn.datafuturex.yunqi.modules.entity.UserEntity;
import cn.datafuturex.yunqi.modules.service.UserService;
import cn.datafuturex.yunqi.modules.vo.UserRoleVO;
import cn.datafuturex.yunqi.modules.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器
 *
 * @author YunQi Application Platform Team
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户增删改查及角色分配")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "获取当前登录用户信息", description = "个人中心查询接口，对应 profile:info:query")
    public Result<UserVO> getCurrentUser() {
        try {
            return Result.success(userService.getCurrentUser());
        } catch (Exception e) {
            log.error("获取当前用户失败", e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/me")
    @Operation(summary = "更新当前用户个人资料", description = "仅可修改真实姓名、邮箱、手机号")
    public Result<UserVO> updateCurrentProfile(@Valid @RequestBody UserProfileUpdateDTO dto) {
        try {
            return Result.success(userService.updateCurrentProfile(dto));
        } catch (Exception e) {
            log.error("更新个人资料失败", e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/me/password")
    @Operation(summary = "修改当前用户密码", description = "当前登录用户修改自己的密码，须验证原密码；成功后旧 Token 失效")
    public Result<Void> changePassword(@Valid @RequestBody UserPasswordChangeDTO dto) {
        try {
            userService.changePassword(dto);
            return Result.success();
        } catch (Exception e) {
            log.error("修改密码失败", e);
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_USER_ADD + "')")
    @Operation(summary = "创建用户", description = "新增系统用户，需指定角色ID")
    public Result<UserEntity> create(@Valid @RequestBody UserCreateDTO dto) {
        try {
            return Result.success(userService.create(dto));
        } catch (Exception e) {
            log.error("创建用户失败", e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_USER_EDIT + "')")
    @Operation(summary = "更新用户", description = "修改用户信息，可通过 roleId 变更角色")
    public Result<UserEntity> update(@Valid @RequestBody UserUpdateDTO dto) {
        try {
            return Result.success(userService.update(dto));
        } catch (Exception e) {
            log.error("更新用户失败", e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_USER_EDIT + "')")
    @Operation(summary = "启用/禁用用户", description = "禁用后立即吊销该用户全部 Token")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusUpdateDTO dto) {
        try {
            userService.updateStatus(id, dto);
            return Result.success();
        } catch (Exception e) {
            log.error("更新用户状态失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}/password/reset")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_USER_EDIT + "')")
    @Operation(summary = "管理员重置用户密码", description = "重置后该用户全部 Token 立即失效")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody UserPasswordResetDTO dto) {
        try {
            userService.resetPassword(id, dto);
            return Result.success();
        } catch (Exception e) {
            log.error("重置用户密码失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_USER_REMOVE + "')")
    @Operation(summary = "删除用户", description = "根据ID删除用户")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            userService.delete(id);
            return Result.success();
        } catch (Exception e) {
            log.error("删除用户失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_USER_QUERY + "')")
    @Operation(summary = "查询用户详情", description = "根据ID获取用户信息，含角色名称")
    public Result<UserVO> findById(@PathVariable Long id) {
        try {
            return userService.findVOById(id)
                    .map(Result::success)
                    .orElse(Result.error("用户不存在"));
        } catch (Exception e) {
            log.error("查询用户失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}/role")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_USER_QUERY + "') or hasAuthority('" + PermissionConstants.SYSTEM_USER_ASSIGN_ROLE + "')")
    @Operation(summary = "查询用户已分配角色")
    public Result<UserRoleVO> getUserRole(@PathVariable Long id) {
        try {
            return Result.success(userService.getUserRole(id));
        } catch (Exception e) {
            log.error("查询用户角色失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_USER_ASSIGN_ROLE + "')")
    @Operation(summary = "为用户分配角色", description = "指定角色ID，覆盖用户当前角色")
    public Result<Void> assignRole(@PathVariable Long id, @Valid @RequestBody UserRoleAssignDTO dto) {
        try {
            userService.assignRole(id, dto);
            return Result.success();
        } catch (Exception e) {
            log.error("用户角色分配失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/username/{username}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_USER_QUERY + "')")
    @Operation(summary = "根据用户名查询用户", description = "需 system:user:query 权限；个人中心请使用 GET /users/me")
    public Result<UserVO> findByUsername(@PathVariable String username) {
        try {
            return userService.findVOByUsername(username)
                    .map(Result::success)
                    .orElse(Result.error("用户不存在"));
        } catch (Exception e) {
            log.error("根据用户名查询用户失败: username={}", username, e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_USER_QUERY + "')")
    @Operation(summary = "分页查询用户", description = "支持按角色ID或角色编码过滤")
    public Result<PageResult<UserVO>> pageQuery(UserQueryDTO queryDTO) {
        try {
            return Result.success(userService.pageQuery(queryDTO));
        } catch (Exception e) {
            log.error("分页查询用户失败", e);
            return Result.error(e.getMessage());
        }
    }
}

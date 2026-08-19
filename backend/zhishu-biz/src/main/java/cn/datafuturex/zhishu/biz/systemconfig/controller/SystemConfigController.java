package cn.datafuturex.zhishu.biz.systemconfig.controller;

import cn.datafuturex.zhishu.common.Result;
import cn.datafuturex.zhishu.biz.systemconfig.dto.SystemConfigUpdateDTO;
import cn.datafuturex.zhishu.biz.systemconfig.service.SystemConfigService;
import cn.datafuturex.zhishu.biz.systemconfig.vo.SystemConfigVO;
import cn.datafuturex.zhishu.modules.constant.PermissionConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 系统配置控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system-config")
@RequiredArgsConstructor
@Tag(name = "系统配置", description = "系统名称、英文标题、图标、版权信息、项目地等配置")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping
    @Operation(summary = "获取系统配置", description = "登录页、布局等场景使用，无需认证")
    public Result<SystemConfigVO> getConfig() {
        try {
            return Result.success(systemConfigService.getConfig());
        } catch (Exception e) {
            log.error("获取系统配置失败", e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_CONFIG_EDIT + "')")
    @Operation(summary = "更新系统配置")
    public Result<SystemConfigVO> update(@Valid @RequestBody SystemConfigUpdateDTO dto) {
        try {
            return Result.success(systemConfigService.update(dto));
        } catch (Exception e) {
            log.error("更新系统配置失败", e);
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/icon")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_CONFIG_EDIT + "')")
    @Operation(summary = "上传系统图标", description = "支持 png/jpg/jpeg/gif/svg/ico/webp，最大 2MB")
    public Result<String> uploadIcon(@RequestParam("file") MultipartFile file) {
        try {
            return Result.success(systemConfigService.uploadIcon(file));
        } catch (Exception e) {
            log.error("上传系统图标失败", e);
            return Result.error(e.getMessage());
        }
    }
}

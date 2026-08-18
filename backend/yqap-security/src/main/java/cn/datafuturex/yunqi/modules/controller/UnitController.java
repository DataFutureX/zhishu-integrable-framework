package cn.datafuturex.yunqi.modules.controller;

import cn.datafuturex.yunqi.common.PageResult;
import cn.datafuturex.yunqi.common.Result;
import cn.datafuturex.yunqi.modules.constant.PermissionConstants;
import cn.datafuturex.yunqi.modules.dto.UnitCreateDTO;
import cn.datafuturex.yunqi.modules.dto.UnitQueryDTO;
import cn.datafuturex.yunqi.modules.dto.UnitUpdateDTO;
import cn.datafuturex.yunqi.modules.entity.UnitEntity;
import cn.datafuturex.yunqi.modules.service.UnitService;
import cn.datafuturex.yunqi.modules.vo.UnitVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 单位管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/units")
@RequiredArgsConstructor
@Tag(name = "单位管理", description = "系统单位增删改查及树形结构查询")
public class UnitController {

    private final UnitService unitService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_UNIT_QUERY + "')")
    @Operation(summary = "分页查询单位", description = "平铺列表，支持编码、名称、类型、状态、父单位筛选")
    public Result<PageResult<UnitVO>> pageQuery(UnitQueryDTO query) {
        try {
            return Result.success(unitService.pageQuery(query));
        } catch (Exception e) {
            log.error("分页查询单位失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_UNIT_QUERY + "')")
    @Operation(summary = "查询单位树", description = "返回树形结构，status 可选（1-启用，0-停用）")
    public Result<List<UnitVO>> listTree(@RequestParam(required = false) Integer status) {
        try {
            return Result.success(unitService.listTree(status));
        } catch (Exception e) {
            log.error("查询单位树失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_UNIT_QUERY + "')")
    @Operation(summary = "查询全部启用单位", description = "下拉选择等场景使用")
    public Result<List<UnitVO>> listAllEnabled() {
        try {
            return Result.success(unitService.listAllEnabled());
        } catch (Exception e) {
            log.error("查询单位列表失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_UNIT_QUERY + "')")
    @Operation(summary = "查询单位详情")
    public Result<UnitVO> findById(@PathVariable Long id) {
        try {
            return unitService.findById(id)
                    .map(Result::success)
                    .orElse(Result.error("单位不存在"));
        } catch (Exception e) {
            log.error("查询单位失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_UNIT_ADD + "')")
    @Operation(summary = "创建单位")
    public Result<UnitEntity> create(@Valid @RequestBody UnitCreateDTO dto) {
        try {
            return Result.success(unitService.create(dto));
        } catch (Exception e) {
            log.error("创建单位失败", e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_UNIT_EDIT + "')")
    @Operation(summary = "更新单位")
    public Result<UnitEntity> update(@Valid @RequestBody UnitUpdateDTO dto) {
        try {
            return Result.success(unitService.update(dto));
        } catch (Exception e) {
            log.error("更新单位失败", e);
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_UNIT_REMOVE + "')")
    @Operation(summary = "删除单位")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            unitService.delete(id);
            return Result.success();
        } catch (Exception e) {
            log.error("删除单位失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }
}

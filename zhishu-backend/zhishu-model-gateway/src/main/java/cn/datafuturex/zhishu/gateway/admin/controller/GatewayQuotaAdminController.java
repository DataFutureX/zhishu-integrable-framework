package cn.datafuturex.zhishu.gateway.admin.controller;

import cn.datafuturex.zhishu.common.Result;
import cn.datafuturex.zhishu.gateway.admin.dto.GatewayQuotaUpsertDTO;
import cn.datafuturex.zhishu.gateway.admin.service.GatewayQuotaAdminService;
import cn.datafuturex.zhishu.gateway.admin.vo.GatewayQuotaVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型网关 · 额度策略管理。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/model-gateway/quotas")
@RequiredArgsConstructor
@Tag(name = "模型网关 · 额度策略", description = "角色默认额度与用户覆盖额度的维护")
public class GatewayQuotaAdminController {

    private final GatewayQuotaAdminService gatewayQuotaAdminService;

    /**
     * 列出额度策略。
     *
     * @param subjectType 主体类型过滤
     * @return 额度列表
     */
    @GetMapping
    @Operation(summary = "额度策略列表")
    public Result<List<GatewayQuotaVO>> list(@RequestParam(required = false) String subjectType) {
        return Result.success(gatewayQuotaAdminService.list(subjectType));
    }

    /**
     * 新建额度策略。
     *
     * @param dto 参数
     * @return 新建后的策略
     */
    @PostMapping
    @Operation(summary = "新建额度策略")
    public Result<GatewayQuotaVO> create(@Valid @RequestBody GatewayQuotaUpsertDTO dto) {
        log.info("[网关管理] 新建额度策略 {}:{}", dto.subjectType(), dto.normalizedSubjectKey());
        return Result.success(gatewayQuotaAdminService.create(dto));
    }

    /**
     * 修改额度策略。
     *
     * @param id  主键
     * @param dto 参数
     * @return 修改后的策略
     */
    @PutMapping("/{id}")
    @Operation(summary = "修改额度策略", description = "主体不可变更")
    public Result<GatewayQuotaVO> update(@PathVariable Long id,
                                         @Valid @RequestBody GatewayQuotaUpsertDTO dto) {
        return Result.success(gatewayQuotaAdminService.update(id, dto));
    }

    /**
     * 删除额度策略。
     *
     * @param id 主键
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除额度策略", description = "DEFAULT 主体不可删除")
    public Result<Void> delete(@PathVariable Long id) {
        gatewayQuotaAdminService.delete(id);
        return Result.success(null);
    }

    /**
     * 清零当前周期用量。
     *
     * @param id 主键
     * @return 修改后的策略
     */
    @PostMapping("/{id}/reset")
    @Operation(summary = "清零已用量", description = "运维/测试用，不影响周期窗口")
    public Result<GatewayQuotaVO> reset(@PathVariable Long id) {
        return Result.success(gatewayQuotaAdminService.reset(id));
    }
}

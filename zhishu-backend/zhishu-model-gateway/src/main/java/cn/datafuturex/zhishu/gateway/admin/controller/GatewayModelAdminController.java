package cn.datafuturex.zhishu.gateway.admin.controller;

import cn.datafuturex.zhishu.common.Result;
import cn.datafuturex.zhishu.gateway.admin.dto.GatewayModelBatchImportDTO;
import cn.datafuturex.zhishu.gateway.admin.dto.GatewayModelUpsertDTO;
import cn.datafuturex.zhishu.gateway.admin.service.GatewayModelAdminService;
import cn.datafuturex.zhishu.gateway.admin.vo.GatewayModelAdminVO;
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
 * 模型网关 · 模型目录管理。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/model-gateway/models")
@RequiredArgsConstructor
@Tag(name = "模型网关 · 模型目录", description = "对外模型目录的增删改查、默认设置与连通性测试")
public class GatewayModelAdminController {

    private final GatewayModelAdminService gatewayModelAdminService;

    /**
     * 列出模型（可按类型/供应商过滤）。
     *
     * @param type       类型：CHAT / EMBEDDING
     * @param providerId 供应商 ID
     * @return 模型列表
     */
    @GetMapping
    @Operation(summary = "模型列表", description = "按类型与供应商过滤")
    public Result<List<GatewayModelAdminVO>> list(@RequestParam(required = false) String type,
                                                  @RequestParam(required = false) Long providerId) {
        return Result.success(gatewayModelAdminService.list(type, providerId));
    }

    /**
     * 列出可供 Agent 绑定的模型。
     *
     * @return 模型列表
     */
    @GetMapping("/available")
    @Operation(summary = "可绑定模型", description = "启用且上游可用的模型，供 Agent 编辑器下拉使用")
    public Result<List<GatewayModelAdminVO>> available() {
        return Result.success(gatewayModelAdminService.listAvailable());
    }

    /**
     * 查询单个网关模型。
     *
     * @param id 主键
     * @return 模型
     */
    @GetMapping("/{id}")
    @Operation(summary = "网关模型详情")
    public Result<GatewayModelAdminVO> get(@PathVariable Long id) {
        return Result.success(gatewayModelAdminService.get(id));
    }

    /**
     * 新建网关模型。
     *
     * @param dto 参数
     * @return 新建后的模型
     */
    @PostMapping
    @Operation(summary = "新建网关模型")
    public Result<GatewayModelAdminVO> create(@Valid @RequestBody GatewayModelUpsertDTO dto) {
        log.info("[网关管理] 新建模型 model_id={}, provider#{}", dto.modelId(), dto.providerId());
        return Result.success(gatewayModelAdminService.create(dto));
    }

    /**
     * 修改网关模型。
     *
     * @param id  主键
     * @param dto 参数
     * @return 修改后的模型
     */
    @PutMapping("/{id}")
    @Operation(summary = "修改网关模型", description = "model_id 创建后不可修改")
    public Result<GatewayModelAdminVO> update(@PathVariable Long id,
                                              @Valid @RequestBody GatewayModelUpsertDTO dto) {
        log.info("[网关管理] 修改模型 id={}", id);
        return Result.success(gatewayModelAdminService.update(id, dto));
    }

    /**
     * 删除网关模型。
     *
     * @param id 主键
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除网关模型", description = "被 Agent 引用时拒绝删除")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("[网关管理] 删除模型 id={}", id);
        gatewayModelAdminService.delete(id);
        return Result.success(null);
    }

    /**
     * 设为网关默认模型。
     *
     * @param id 主键
     * @return 修改后的模型
     */
    @PostMapping("/{id}/default")
    @Operation(summary = "设为默认模型")
    public Result<GatewayModelAdminVO> markDefault(@PathVariable Long id) {
        return Result.success(gatewayModelAdminService.markDefault(id));
    }

    /**
     * 启用模型。
     *
     * @param id 主键
     * @return 修改后的模型
     */
    @PostMapping("/{id}/enable")
    @Operation(summary = "启用模型")
    public Result<GatewayModelAdminVO> enable(@PathVariable Long id) {
        return Result.success(gatewayModelAdminService.changeStatus(id, true));
    }

    /**
     * 停用模型。
     *
     * @param id 主键
     * @return 修改后的模型
     */
    @PostMapping("/{id}/disable")
    @Operation(summary = "停用模型")
    public Result<GatewayModelAdminVO> disable(@PathVariable Long id) {
        return Result.success(gatewayModelAdminService.changeStatus(id, false));
    }

    /**
     * 端到端连通性测试。
     *
     * @param id 主键
     * @return 测试结果说明
     */
    @PostMapping("/{id}/test")
    @Operation(summary = "端到端连通测试", description = "经网关向上游发一条最小请求")
    public Result<String> test(@PathVariable Long id) {
        return Result.success(gatewayModelAdminService.test(id));
    }

    /**
     * 从上游模型列表批量导入。
     *
     * @param dto 导入参数
     * @return 新建成功的模型
     */
    @PostMapping("/batch")
    @Operation(summary = "批量导入", description = "按「供应商 + 上游模型名」批量创建模型行")
    public Result<List<GatewayModelAdminVO>> batchImport(@Valid @RequestBody GatewayModelBatchImportDTO dto) {
        log.info("[网关管理] 批量导入 provider#{} type={} count={}",
                dto.providerId(), dto.modelType(), dto.upstreamModels().size());
        return Result.success(gatewayModelAdminService.batchImport(dto));
    }
}

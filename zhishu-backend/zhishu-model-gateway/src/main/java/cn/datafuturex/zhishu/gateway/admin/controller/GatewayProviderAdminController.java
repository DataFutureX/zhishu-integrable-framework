package cn.datafuturex.zhishu.gateway.admin.controller;

import cn.datafuturex.zhishu.gateway.provider.dto.ModelProviderCreateDTO;
import cn.datafuturex.zhishu.gateway.provider.dto.ModelProviderUpdateDTO;
import cn.datafuturex.zhishu.gateway.provider.vo.UpstreamModelInfo;
import cn.datafuturex.zhishu.common.Result;
import cn.datafuturex.zhishu.modules.constant.PermissionConstants;
import cn.datafuturex.zhishu.gateway.admin.service.GatewayProviderAdminService;
import cn.datafuturex.zhishu.gateway.admin.vo.GatewayProviderAdminVO;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

/**
 * 模型网关 · 供应商管理（独立模块，只管连接与凭据）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/model-gateway/providers")
@RequiredArgsConstructor
@Tag(name = "模型网关 · 供应商", description = "上游供应商连接与密钥维护、连通性测试、上游模型拉取")
public class GatewayProviderAdminController {

    private final GatewayProviderAdminService gatewayProviderAdminService;

    /**
     * 供应商列表。
     *
     * @return 供应商列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.GATEWAY_PROVIDER_LIST + "')")
    @Operation(summary = "供应商列表", description = "含对话/向量模型数量与 Key 配置状态")
    public Result<List<GatewayProviderAdminVO>> list() {
        return Result.success(gatewayProviderAdminService.list());
    }

    /**
     * 新建供应商。
     *
     * @param dto 参数
     * @return 新建后的供应商
     */
    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.GATEWAY_PROVIDER_UPDATE + "')")
    @Operation(summary = "新建供应商")
    public Result<GatewayProviderAdminVO> create(@Valid @RequestBody ModelProviderCreateDTO dto) {
        log.info("[网关管理] 新建供应商 name={}, providerKey={}", dto.name(), dto.providerKey());
        return Result.success(gatewayProviderAdminService.create(dto));
    }

    /**
     * 修改供应商。
     *
     * @param id  主键
     * @param dto 参数
     * @return 修改后的供应商
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.GATEWAY_PROVIDER_UPDATE + "')")
    @Operation(summary = "修改供应商")
    public Result<GatewayProviderAdminVO> update(@PathVariable Long id,
                                                 @Valid @RequestBody ModelProviderUpdateDTO dto) {
        return Result.success(gatewayProviderAdminService.update(id, dto));
    }

    /**
     * 删除供应商。
     *
     * @param id 主键
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.GATEWAY_PROVIDER_UPDATE + "')")
    @Operation(summary = "删除供应商", description = "被模型引用或为默认供应商时拒绝")
    public Result<Void> delete(@PathVariable Long id) {
        gatewayProviderAdminService.delete(id);
        return Result.success(null);
    }

    /**
     * 设为默认供应商。
     *
     * @param id 主键
     * @return 修改后的供应商
     */
    @PostMapping("/{id}/default")
    @PreAuthorize("hasAuthority('" + PermissionConstants.GATEWAY_PROVIDER_UPDATE + "')")
    @Operation(summary = "设为默认供应商")
    public Result<GatewayProviderAdminVO> markDefault(@PathVariable Long id) {
        return Result.success(gatewayProviderAdminService.markDefault(id));
    }

    /**
     * 连通性测试。
     *
     * @param id 主键
     * @return 测试结果说明
     */
    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('" + PermissionConstants.GATEWAY_PROVIDER_LIST + "')")
    @Operation(summary = "连通性测试", description = "调用上游 GET /models，返回可用模型数与耗时")
    public Result<String> test(@PathVariable Long id) {
        return Result.success(gatewayProviderAdminService.test(id));
    }

    /**
     * 拉取上游模型列表。
     *
     * @param id 主键
     * @return 模型 id 列表
     */
    @GetMapping("/{id}/upstream-models")
    @PreAuthorize("hasAuthority('" + PermissionConstants.GATEWAY_PROVIDER_LIST + "')")
    @Operation(summary = "上游模型列表", description = "供「模型设置」批量导入使用，含上下文窗口、输出上限、模态与推理档位元数据")
    public Result<List<UpstreamModelInfo>> upstreamModels(@PathVariable Long id) {
        return Result.success(gatewayProviderAdminService.upstreamModels(id));
    }
}

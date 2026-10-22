package cn.datafuturex.zhishu.ai.modelconfig.controller;

import cn.datafuturex.zhishu.ai.modelconfig.api.ModelProviderPort;
import cn.datafuturex.zhishu.ai.modelconfig.dto.ModelProviderCreateDTO;
import cn.datafuturex.zhishu.ai.modelconfig.dto.ModelProviderUpdateDTO;
import cn.datafuturex.zhishu.ai.modelconfig.vo.ModelProviderVO;
import cn.datafuturex.zhishu.ai.shared.Result;
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

import java.util.List;

/**
 * 供应商管理（旧接口，已弃用）。
 * <p>
 * v2 将供应商与模型设置拆分：新接口为 {@code /api/v1/model-gateway/providers} 与
 * {@code /api/v1/model-gateway/models}；本控制器保留一个版本供旧客户端过渡。
 */
@Deprecated(since = "v2", forRemoval = false)
@RestController
@RequestMapping("/api/v1/model-settings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "供应商管理（已弃用）", description = "已由 /api/v1/model-gateway/providers 替代")
public class ModelSettingController {

    private final ModelProviderPort modelProviderPort;

    /**
     * 获取所有模型设置列表
     *
     * @return 模型设置列表
     */
    @GetMapping
    @Operation(summary = "获取模型设置列表")
    public Result<List<ModelProviderVO>> list() {
        return Result.success(modelProviderPort.list());
    }

    /**
     * 获取单个模型设置详情
     *
     * @param id 模型设置 ID
     * @return 模型设置详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取模型设置详情")
    public Result<ModelProviderVO> get(@PathVariable Long id) {
        return Result.success(modelProviderPort.get(id));
    }

    /**
     * 新建模型设置
     *
     * @param dto 创建参数
     * @return 新建后的模型设置
     */
    @PostMapping
    @Operation(summary = "新建模型设置")
    public Result<ModelProviderVO> create(@Valid @RequestBody ModelProviderCreateDTO dto) {
        log.info("新建模型设置: name={}, providerKey={}", dto.name(), dto.providerKey());
        return Result.success(modelProviderPort.create(dto));
    }

    /**
     * 更新模型设置
     *
     * @param id  模型设置 ID
     * @param dto 更新参数
     * @return 更新后的模型设置
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新模型设置")
    public Result<ModelProviderVO> update(@PathVariable Long id,
                                          @Valid @RequestBody ModelProviderUpdateDTO dto) {
        log.info("更新模型设置: id={}, name={}", id, dto.name());
        return Result.success(modelProviderPort.update(id, dto));
    }

    /**
     * 删除模型设置（默认不可删）
     *
     * @param id 模型设置 ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除模型设置")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("删除模型设置: id={}", id);
        modelProviderPort.delete(id);
        return Result.success(null);
    }

    /**
     * 连通性测试
     *
     * @param id 模型设置 ID
     * @return 测试结果消息
     */
    @PostMapping("/{id}/test")
    @Operation(summary = "连通性测试")
    public Result<String> testConnection(@PathVariable Long id) {
        log.info("连通性测试: id={}", id);
        return Result.success(modelProviderPort.testConnection(id));
    }
}

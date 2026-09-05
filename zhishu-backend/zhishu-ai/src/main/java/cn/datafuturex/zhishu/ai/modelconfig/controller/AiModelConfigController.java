package cn.datafuturex.zhishu.ai.modelconfig.controller;

import cn.datafuturex.zhishu.ai.shared.Result;
import cn.datafuturex.zhishu.ai.modelconfig.dto.AiModelConfigUpdateDTO;
import cn.datafuturex.zhishu.ai.modelconfig.vo.AiModelConfigVO;
import cn.datafuturex.zhishu.ai.modelconfig.api.ModelConfigPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 模型配置
 * @deprecated 已被 {@link cn.datafuturex.zhishu.ai.modelconfig.controller.ModelSettingController} 替代
 */
@Deprecated
@RestController
@RequestMapping("/api/v1/model-config")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "模型配置", description = "对话/向量模型与生成参数运行时配置")
public class AiModelConfigController {

    private final ModelConfigPort aiModelConfigService;

    @GetMapping
    @Operation(summary = "获取模型配置")
    public Result<AiModelConfigVO> get() {
        return Result.success(aiModelConfigService.getConfig());
    }

    @PutMapping
    @Operation(summary = "更新模型配置")
    public Result<AiModelConfigVO> update(@Valid @RequestBody AiModelConfigUpdateDTO dto) {
        log.info("更新模型配置: chatModel={}", dto.chatModel());
        return Result.success(aiModelConfigService.updateConfig(dto));
    }
}

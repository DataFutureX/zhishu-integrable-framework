package cn.datafuturex.zhishu.ai.openapi.controller;

import cn.datafuturex.zhishu.ai.openapi.dto.GenerateAkSkResult;
import cn.datafuturex.zhishu.ai.openapi.dto.OpenAppUpsertDTO;
import cn.datafuturex.zhishu.ai.openapi.dto.OpenAppVO;
import cn.datafuturex.zhishu.ai.openapi.service.OpenAppAdminService;
import cn.datafuturex.zhishu.ai.shared.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 开放应用管理接口（需认证，管理员操作）。
 */
@RestController
@RequestMapping("/api/v1/open-apps")
@RequiredArgsConstructor
@Tag(name = "开放应用管理", description = "AK/SK 凭证管理与调用范围配置")
public class OpenAppAdminController {

    private final OpenAppAdminService openAppAdminService;

    @GetMapping
    @Operation(summary = "开放应用列表")
    public Result<List<OpenAppVO>> list() {
        return Result.success(openAppAdminService.listApps());
    }

    @PostMapping
    @Operation(summary = "新建开放应用")
    public Result<OpenAppVO> create(@Valid @RequestBody OpenAppUpsertDTO dto) {
        return Result.success(openAppAdminService.createApp(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑开放应用")
    public Result<OpenAppVO> update(@PathVariable Long id, @Valid @RequestBody OpenAppUpsertDTO dto) {
        return Result.success(openAppAdminService.updateApp(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "移除开放应用")
    public Result<Void> delete(@PathVariable Long id) {
        openAppAdminService.deleteApp(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "开放应用详情")
    public Result<OpenAppVO> get(@PathVariable Long id) {
        return Result.success(openAppAdminService.getApp(id));
    }

    @PostMapping("/{id}/generate-aksk")
    @Operation(summary = "生成或重新生成 AK/SK（SK 仅返回一次）")
    public Result<GenerateAkSkResult> generateAkSk(@PathVariable Long id) {
        return Result.success(openAppAdminService.generateAkSk(id));
    }

    @PostMapping("/{id}/regenerate-sk")
    @Operation(summary = "仅重新生成 SK（AK 不变，旧 SK 立即失效）")
    public Result<GenerateAkSkResult> regenerateSk(@PathVariable Long id) {
        return Result.success(openAppAdminService.regenerateSk(id));
    }

    @PutMapping("/{id}/scopes")
    @Operation(summary = "更新调用范围（scopes）")
    public Result<OpenAppVO> updateScopes(@PathVariable Long id,
                                          @RequestBody Map<String, List<String>> body) {
        List<String> scopes = body.getOrDefault("scopes", List.of());
        return Result.success(openAppAdminService.updateScopes(id, scopes));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "启用 / 停用应用")
    public Result<OpenAppVO> updateStatus(@PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "ENABLED");
        return Result.success(openAppAdminService.updateStatus(id, status));
    }
}

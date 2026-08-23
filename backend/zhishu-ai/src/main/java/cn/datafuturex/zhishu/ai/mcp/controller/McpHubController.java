package cn.datafuturex.zhishu.ai.mcp.controller;

import cn.datafuturex.zhishu.ai.mcp.config.McpProperties;
import cn.datafuturex.zhishu.ai.mcp.domain.dto.McpClientCreateDTO;
import cn.datafuturex.zhishu.ai.mcp.domain.dto.McpClientUpdateDTO;
import cn.datafuturex.zhishu.ai.mcp.domain.dto.McpUpstreamToolPatchDTO;
import cn.datafuturex.zhishu.ai.mcp.domain.dto.McpUpstreamUpsertDTO;
import cn.datafuturex.zhishu.ai.mcp.domain.vo.McpCallLogVO;
import cn.datafuturex.zhishu.ai.mcp.domain.vo.McpClientVO;
import cn.datafuturex.zhishu.ai.mcp.domain.vo.McpOutboundCatalogVO;
import cn.datafuturex.zhishu.ai.mcp.domain.vo.McpOverviewVO;
import cn.datafuturex.zhishu.ai.mcp.domain.vo.McpUpstreamToolVO;
import cn.datafuturex.zhishu.ai.mcp.domain.vo.McpUpstreamVO;
import cn.datafuturex.zhishu.ai.mcp.mapper.AiMcpClientMapper;
import cn.datafuturex.zhishu.ai.mcp.mapper.AiMcpUpstreamMapper;
import cn.datafuturex.zhishu.ai.mcp.service.McpCallLogService;
import cn.datafuturex.zhishu.ai.mcp.service.McpClientAdminService;
import cn.datafuturex.zhishu.ai.mcp.service.McpUpstreamAdminService;
import cn.datafuturex.zhishu.ai.mcp.support.McpOutboundCatalog;
import cn.datafuturex.zhishu.ai.shared.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/v1/mcp")
@RequiredArgsConstructor
@Tag(name = "MCP 中枢", description = "对外提供 MCP 服务、接入他方 MCP")
public class McpHubController {

    private final McpProperties properties;
    private final AiMcpClientMapper clientMapper;
    private final AiMcpUpstreamMapper upstreamMapper;
    private final McpClientAdminService clientAdminService;
    private final McpUpstreamAdminService upstreamAdminService;
    private final McpCallLogService callLogService;

    @GetMapping("/overview")
    @Operation(summary = "MCP 中枢概览")
    public Result<McpOverviewVO> overview() {
        long clients = clientMapper.selectCount(null);
        long upstreams = upstreamMapper.selectCount(null);
        long enabled = upstreamMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                        cn.datafuturex.zhishu.ai.mcp.domain.entity.AiMcpUpstreamEntity>()
                        .eq(cn.datafuturex.zhishu.ai.mcp.domain.entity.AiMcpUpstreamEntity::getStatus, "ENABLED"));
        return Result.success(new McpOverviewVO(
                properties.isEnabled() && properties.isServerEnabled(),
                "/mcp",
                clients,
                upstreams,
                enabled,
                org.springframework.util.StringUtils.hasText(properties.getCryptoKey())));
    }

    @GetMapping("/catalog")
    @Operation(summary = "对外默认能力与 Tool 目录")
    public Result<McpOutboundCatalogVO> catalog() {
        return Result.success(new McpOutboundCatalogVO(
                McpOutboundCatalog.DEFAULT_CAPABILITIES,
                List.copyOf(McpOutboundCatalog.DEFAULT_TOOL_NAMES)));
    }

    @GetMapping("/clients")
    @Operation(summary = "对外 MCP Client 列表")
    public Result<List<McpClientVO>> clients() {
        return Result.success(clientAdminService.list());
    }

    @PostMapping("/clients")
    @Operation(summary = "创建对外 MCP Client（apiKey 仅返回一次）")
    public Result<McpClientVO> createClient(@Valid @RequestBody McpClientCreateDTO dto) {
        return Result.success(clientAdminService.create(dto));
    }

    @PutMapping("/clients/{id}")
    @Operation(summary = "更新对外 MCP Client")
    public Result<McpClientVO> updateClient(@PathVariable Long id, @Valid @RequestBody McpClientUpdateDTO dto) {
        return Result.success(clientAdminService.update(id, dto));
    }

    @PostMapping("/clients/{id}/rotate-key")
    @Operation(summary = "轮换 API Key")
    public Result<McpClientVO> rotateKey(@PathVariable Long id) {
        return Result.success(clientAdminService.rotateKey(id));
    }

    @DeleteMapping("/clients/{id}")
    @Operation(summary = "删除对外 MCP Client")
    public Result<Void> deleteClient(@PathVariable Long id) {
        clientAdminService.delete(id);
        return Result.success();
    }

    @GetMapping("/upstreams")
    @Operation(summary = "接入的他方 MCP 列表")
    public Result<List<McpUpstreamVO>> upstreams() {
        return Result.success(upstreamAdminService.list());
    }

    @PostMapping("/upstreams")
    @Operation(summary = "登记他方 MCP")
    public Result<McpUpstreamVO> createUpstream(@Valid @RequestBody McpUpstreamUpsertDTO dto) {
        return Result.success(upstreamAdminService.create(dto));
    }

    @PutMapping("/upstreams/{id}")
    @Operation(summary = "更新他方 MCP")
    public Result<McpUpstreamVO> updateUpstream(@PathVariable Long id, @Valid @RequestBody McpUpstreamUpsertDTO dto) {
        return Result.success(upstreamAdminService.update(id, dto));
    }

    @DeleteMapping("/upstreams/{id}")
    @Operation(summary = "删除他方 MCP")
    public Result<Void> deleteUpstream(@PathVariable Long id) {
        upstreamAdminService.delete(id);
        return Result.success();
    }

    @PostMapping("/upstreams/{id}/probe")
    @Operation(summary = "探活并刷新 tools/list")
    public Result<McpUpstreamVO> probe(@PathVariable Long id) {
        return Result.success(upstreamAdminService.probe(id));
    }

    @GetMapping("/upstreams/{id}/tools")
    @Operation(summary = "上游 Tool 缓存")
    public Result<List<McpUpstreamToolVO>> upstreamTools(@PathVariable Long id) {
        return Result.success(upstreamAdminService.listTools(id));
    }

    @PutMapping("/upstreams/{id}/tools")
    @Operation(summary = "启用或停用上游 Tool")
    public Result<McpUpstreamToolVO> patchUpstreamTool(
            @PathVariable Long id, @Valid @RequestBody McpUpstreamToolPatchDTO dto) {
        return Result.success(upstreamAdminService.setToolEnabled(id, dto.originalName(), dto.enabled()));
    }

    @GetMapping("/calls")
    @Operation(summary = "调用审计")
    public Result<List<McpCallLogVO>> calls(
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(callLogService.list(direction, limit));
    }
}

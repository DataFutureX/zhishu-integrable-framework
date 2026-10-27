package cn.datafuturex.zhishu.mcp.web;

import cn.datafuturex.zhishu.common.Result;
import cn.datafuturex.zhishu.mcp.consume.upstream.McpUpstreamAdminService;
import cn.datafuturex.zhishu.mcp.consume.upstream.dto.McpUpstreamToolPatchDTO;
import cn.datafuturex.zhishu.mcp.consume.upstream.dto.McpUpstreamUpsertDTO;
import cn.datafuturex.zhishu.mcp.consume.upstream.vo.McpUpstreamToolVO;
import cn.datafuturex.zhishu.mcp.consume.upstream.vo.McpUpstreamVO;
import cn.datafuturex.zhishu.modules.constant.PermissionConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 控制面：consume 平面（接入他方提供的 MCP 服务）的上游与工具管理。
 */
@RestController
@RequestMapping("/api/v1/mcp")
@RequiredArgsConstructor
@Tag(name = "MCP 中枢 · 接入上游", description = "他方 MCP Server 登记、探活与工具启停")
public class McpConsumeAdminController {

    private final McpUpstreamAdminService upstreamAdminService;

    @GetMapping("/upstreams")
    @Operation(summary = "接入的他方 MCP 列表")
    public Result<List<McpUpstreamVO>> upstreams() {
        return Result.success(upstreamAdminService.list());
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.AI_MCP_EDIT + "')")
    @PostMapping("/upstreams")
    @Operation(summary = "登记他方 MCP")
    public Result<McpUpstreamVO> createUpstream(@Valid @RequestBody McpUpstreamUpsertDTO dto) {
        return Result.success(upstreamAdminService.create(dto));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.AI_MCP_EDIT + "')")
    @PutMapping("/upstreams/{id}")
    @Operation(summary = "更新他方 MCP")
    public Result<McpUpstreamVO> updateUpstream(@PathVariable Long id,
                                                @Valid @RequestBody McpUpstreamUpsertDTO dto) {
        return Result.success(upstreamAdminService.update(id, dto));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.AI_MCP_EDIT + "')")
    @DeleteMapping("/upstreams/{id}")
    @Operation(summary = "删除他方 MCP")
    public Result<Void> deleteUpstream(@PathVariable Long id) {
        upstreamAdminService.delete(id);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.AI_MCP_EDIT + "')")
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

    @PreAuthorize("hasAuthority('" + PermissionConstants.AI_MCP_EDIT + "')")
    @PutMapping("/upstreams/{id}/tools")
    @Operation(summary = "启用或停用上游 Tool")
    public Result<McpUpstreamToolVO> patchUpstreamTool(
            @PathVariable Long id, @Valid @RequestBody McpUpstreamToolPatchDTO dto) {
        return Result.success(upstreamAdminService.setToolEnabled(id, dto.originalName(), dto.enabled()));
    }
}

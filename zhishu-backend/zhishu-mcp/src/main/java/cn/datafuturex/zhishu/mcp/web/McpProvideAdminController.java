package cn.datafuturex.zhishu.mcp.web;

import cn.datafuturex.zhishu.common.Result;
import cn.datafuturex.zhishu.mcp.provide.catalog.McpProvidedToolCatalog;
import cn.datafuturex.zhishu.mcp.provide.client.McpClientAdminService;
import cn.datafuturex.zhishu.mcp.provide.client.dto.McpClientCreateDTO;
import cn.datafuturex.zhishu.mcp.provide.client.dto.McpClientUpdateDTO;
import cn.datafuturex.zhishu.mcp.provide.client.vo.McpClientVO;
import cn.datafuturex.zhishu.mcp.web.vo.McpProvideCatalogVO;
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
 * 控制面：provide 平面（我方对外提供 MCP 服务）的 Client 与目录管理。
 * <p>
 * 与 consume 平面互不引用；聚合读接口在 {@link McpOverviewController}。
 */
@RestController
@RequestMapping("/api/v1/mcp")
@RequiredArgsConstructor
@Tag(name = "MCP 中枢 · 对外提供", description = "对外 MCP Client、能力目录")
public class McpProvideAdminController {

    private final McpClientAdminService clientAdminService;

    @GetMapping("/catalog")
    @Operation(summary = "对外默认能力与 Tool 目录")
    public Result<McpProvideCatalogVO> catalog() {
        return Result.success(new McpProvideCatalogVO(
                McpProvidedToolCatalog.DEFAULT_CAPABILITIES,
                List.copyOf(McpProvidedToolCatalog.PUBLIC_TOOLS)));
    }

    @GetMapping("/clients")
    @Operation(summary = "对外 MCP Client 列表")
    public Result<List<McpClientVO>> clients() {
        return Result.success(clientAdminService.list());
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.AI_MCP_EDIT + "')")
    @PostMapping("/clients")
    @Operation(summary = "创建对外 MCP Client（apiKey 仅返回一次）")
    public Result<McpClientVO> createClient(@Valid @RequestBody McpClientCreateDTO dto) {
        return Result.success(clientAdminService.create(dto));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.AI_MCP_EDIT + "')")
    @PutMapping("/clients/{id}")
    @Operation(summary = "更新对外 MCP Client")
    public Result<McpClientVO> updateClient(@PathVariable Long id, @Valid @RequestBody McpClientUpdateDTO dto) {
        return Result.success(clientAdminService.update(id, dto));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.AI_MCP_EDIT + "')")
    @PostMapping("/clients/{id}/rotate-key")
    @Operation(summary = "轮换 API Key")
    public Result<McpClientVO> rotateKey(@PathVariable Long id) {
        return Result.success(clientAdminService.rotateKey(id));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.AI_MCP_EDIT + "')")
    @DeleteMapping("/clients/{id}")
    @Operation(summary = "删除对外 MCP Client")
    public Result<Void> deleteClient(@PathVariable Long id) {
        clientAdminService.delete(id);
        return Result.success();
    }
}

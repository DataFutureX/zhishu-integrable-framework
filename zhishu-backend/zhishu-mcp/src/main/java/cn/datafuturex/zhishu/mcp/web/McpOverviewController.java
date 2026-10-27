package cn.datafuturex.zhishu.mcp.web;

import cn.datafuturex.zhishu.common.Result;
import cn.datafuturex.zhishu.mcp.common.audit.McpCallLogService;
import cn.datafuturex.zhishu.mcp.common.audit.vo.McpCallLogVO;
import cn.datafuturex.zhishu.mcp.common.config.McpProperties;
import cn.datafuturex.zhishu.mcp.consume.upstream.entity.AiMcpUpstreamEntity;
import cn.datafuturex.zhishu.mcp.consume.upstream.mapper.AiMcpUpstreamMapper;
import cn.datafuturex.zhishu.mcp.provide.client.mapper.AiMcpClientMapper;
import cn.datafuturex.zhishu.mcp.web.vo.McpOverviewVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 控制面：跨平面<b>只读</b>聚合（概览与调用审计）。
 * <p>
 * 仅读取两侧 Mapper / 审计服务，不承载业务规则，因此不构成 provide 与 consume 的耦合点。
 */
@RestController
@RequestMapping("/api/v1/mcp")
@RequiredArgsConstructor
@Tag(name = "MCP 中枢 · 概览与审计", description = "双平面聚合读接口")
public class McpOverviewController {

    private final McpProperties properties;
    private final AiMcpClientMapper clientMapper;
    private final AiMcpUpstreamMapper upstreamMapper;
    private final McpCallLogService callLogService;

    @GetMapping("/overview")
    @Operation(summary = "MCP 中枢概览")
    public Result<McpOverviewVO> overview() {
        long clients = clientMapper.selectCount(null);
        long upstreams = upstreamMapper.selectCount(null);
        long enabled = upstreamMapper.selectCount(
                new LambdaQueryWrapper<AiMcpUpstreamEntity>()
                        .eq(AiMcpUpstreamEntity::getStatus, "ENABLED"));
        return Result.success(new McpOverviewVO(
                properties.provideEnabled(),
                properties.getProvide().getEndpoint(),
                clients,
                upstreams,
                enabled,
                org.springframework.util.StringUtils.hasText(properties.getCryptoKey())));
    }

    @GetMapping("/calls")
    @Operation(summary = "调用审计（direction 兼容历史 IN/OUT）")
    public Result<List<McpCallLogVO>> calls(
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(callLogService.list(direction, limit));
    }
}

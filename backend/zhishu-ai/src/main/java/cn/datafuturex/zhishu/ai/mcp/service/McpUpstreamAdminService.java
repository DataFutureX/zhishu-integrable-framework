package cn.datafuturex.zhishu.ai.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.datafuturex.zhishu.ai.mcp.client.McpUpstreamConnectionManager;
import cn.datafuturex.zhishu.ai.mcp.config.McpProperties;
import cn.datafuturex.zhishu.ai.mcp.domain.dto.McpUpstreamUpsertDTO;
import cn.datafuturex.zhishu.ai.mcp.domain.entity.AiMcpUpstreamEntity;
import cn.datafuturex.zhishu.ai.mcp.domain.entity.AiMcpUpstreamToolEntity;
import cn.datafuturex.zhishu.ai.mcp.domain.vo.McpUpstreamToolVO;
import cn.datafuturex.zhishu.ai.mcp.domain.vo.McpUpstreamVO;
import cn.datafuturex.zhishu.ai.mcp.mapper.AiAgentMcpUpstreamMapper;
import cn.datafuturex.zhishu.ai.mcp.mapper.AiMcpUpstreamMapper;
import cn.datafuturex.zhishu.ai.mcp.mapper.AiMcpUpstreamToolMapper;
import cn.datafuturex.zhishu.ai.mcp.support.McpCrypto;
import cn.datafuturex.zhishu.ai.shared.UserContext;
import cn.datafuturex.zhishu.ai.shared.exception.BusinessException;
import cn.datafuturex.zhishu.ai.mcp.domain.entity.AiAgentMcpUpstreamEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class McpUpstreamAdminService {

    private final AiMcpUpstreamMapper upstreamMapper;
    private final AiMcpUpstreamToolMapper toolMapper;
    private final AiAgentMcpUpstreamMapper agentBindMapper;
    private final McpUpstreamConnectionManager connectionManager;
    private final McpProperties properties;

    @Value("${server.port:8180}")
    private int serverPort;

    public List<McpUpstreamVO> list() {
        return upstreamMapper.selectList(new LambdaQueryWrapper<AiMcpUpstreamEntity>()
                        .orderByAsc(AiMcpUpstreamEntity::getId))
                .stream()
                .map(this::toVo)
                .toList();
    }

    public McpUpstreamVO get(Long id) {
        return toVo(require(id));
    }

    @Transactional
    public McpUpstreamVO create(McpUpstreamUpsertDTO dto) {
        assertCodeFree(dto.code(), null);
        rejectSelfLoop(dto.baseUrl(), dto.endpoint());
        AiMcpUpstreamEntity entity = new AiMcpUpstreamEntity();
        apply(entity, dto, true);
        entity.setCreatedBy(UserContext.getUserId());
        entity.setCreateTime(LocalDateTime.now());
        upstreamMapper.insert(entity);
        probeQuietly(entity);
        return toVo(upstreamMapper.selectById(entity.getId()));
    }

    @Transactional
    public McpUpstreamVO update(Long id, McpUpstreamUpsertDTO dto) {
        AiMcpUpstreamEntity entity = require(id);
        if (!entity.getCode().equalsIgnoreCase(dto.code())) {
            assertCodeFree(dto.code(), id);
        }
        rejectSelfLoop(dto.baseUrl(), dto.endpoint());
        apply(entity, dto, false);
        upstreamMapper.updateById(entity);
        connectionManager.disconnect(id);
        probeQuietly(upstreamMapper.selectById(id));
        return toVo(upstreamMapper.selectById(id));
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        connectionManager.disconnect(id);
        toolMapper.delete(new LambdaQueryWrapper<AiMcpUpstreamToolEntity>()
                .eq(AiMcpUpstreamToolEntity::getUpstreamId, id));
        agentBindMapper.delete(new LambdaQueryWrapper<AiAgentMcpUpstreamEntity>()
                .eq(AiAgentMcpUpstreamEntity::getUpstreamId, id));
        upstreamMapper.deleteById(id);
    }

    public McpUpstreamVO probe(Long id) {
        AiMcpUpstreamEntity entity = require(id);
        connectionManager.connectAndList(entity);
        return toVo(upstreamMapper.selectById(id));
    }

    public List<McpUpstreamToolVO> listTools(Long id) {
        require(id);
        return toolMapper.selectList(new LambdaQueryWrapper<AiMcpUpstreamToolEntity>()
                        .eq(AiMcpUpstreamToolEntity::getUpstreamId, id)
                        .orderByAsc(AiMcpUpstreamToolEntity::getId))
                .stream()
                .map(this::toToolVo)
                .toList();
    }

    @Transactional
    public McpUpstreamToolVO setToolEnabled(Long upstreamId, String originalName, boolean enabled) {
        require(upstreamId);
        AiMcpUpstreamToolEntity row = toolMapper.selectOne(new LambdaQueryWrapper<AiMcpUpstreamToolEntity>()
                .eq(AiMcpUpstreamToolEntity::getUpstreamId, upstreamId)
                .eq(AiMcpUpstreamToolEntity::getOriginalName, originalName)
                .last("LIMIT 1"));
        if (row == null) {
            throw new BusinessException(404, "上游 Tool 不存在: " + originalName);
        }
        row.setEnabled(enabled);
        row.setUpdateTime(LocalDateTime.now());
        toolMapper.updateById(row);
        return toToolVo(row);
    }

    public AiMcpUpstreamEntity require(Long id) {
        AiMcpUpstreamEntity entity = upstreamMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "上游 MCP 不存在: " + id);
        }
        return entity;
    }

    private void apply(AiMcpUpstreamEntity entity, McpUpstreamUpsertDTO dto, boolean creating) {
        entity.setCode(dto.code().trim().toLowerCase(Locale.ROOT));
        entity.setName(dto.name().trim());
        String protocol = StringUtils.hasText(dto.protocol()) ? dto.protocol().trim().toUpperCase() : "STREAMABLE_HTTP";
        McpUpstreamConnectionManager.rejectStdio(protocol);
        entity.setProtocol(protocol);
        entity.setBaseUrl(trimSlash(dto.baseUrl()));
        entity.setEndpoint(StringUtils.hasText(dto.endpoint()) ? dto.endpoint().trim() : "/mcp");
        if (creating || StringUtils.hasText(dto.authHeader())) {
            entity.setAuthHeaderEnc(McpCrypto.encrypt(blankToNull(dto.authHeader()), properties.getCryptoKey()));
        }
        entity.setRequestTimeoutMs(dto.requestTimeoutMs() == null ? 20_000 : dto.requestTimeoutMs());
        entity.setStatus(StringUtils.hasText(dto.status()) ? dto.status().trim().toUpperCase() : "ENABLED");
        entity.setRemark(dto.remark());
        entity.setUpdateTime(LocalDateTime.now());
        if (creating) {
            entity.setHealthStatus("UNKNOWN");
        }
    }

    private void probeQuietly(AiMcpUpstreamEntity entity) {
        if (entity == null || !"ENABLED".equalsIgnoreCase(entity.getStatus()) || !properties.isClientEnabled()) {
            return;
        }
        try {
            connectionManager.connectAndList(entity);
        } catch (Exception e) {
            // 健康字段已由 connectionManager 写入
        }
    }

    private void assertCodeFree(String code, Long excludeId) {
        AiMcpUpstreamEntity exists = upstreamMapper.selectOne(
                new LambdaQueryWrapper<AiMcpUpstreamEntity>()
                        .eq(AiMcpUpstreamEntity::getCode, code.trim().toLowerCase(Locale.ROOT))
                        .last("LIMIT 1"));
        if (exists != null && (excludeId == null || !exists.getId().equals(excludeId))) {
            throw new BusinessException("上游编码已存在: " + code);
        }
    }

    void rejectSelfLoop(String baseUrl, String endpoint) {
        try {
            URI uri = URI.create(trimSlash(baseUrl) + (StringUtils.hasText(endpoint) ? endpoint : "/mcp"));
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
            String path = uri.getPath() == null ? "" : uri.getPath();
            boolean local = "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host);
            if (local && port == serverPort && path.startsWith("/mcp")) {
                throw new BusinessException("禁止把本平台 /mcp 配成上游（防止工具环回）");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("上游 URL 无效");
        }
    }

    private McpUpstreamToolVO toToolVo(AiMcpUpstreamToolEntity t) {
        return new McpUpstreamToolVO(
                t.getOriginalName(),
                t.getExposedName(),
                t.getDescription(),
                Boolean.TRUE.equals(t.getEnabled()));
    }

    private McpUpstreamVO toVo(AiMcpUpstreamEntity e) {
        Long count = toolMapper.selectCount(new LambdaQueryWrapper<AiMcpUpstreamToolEntity>()
                .eq(AiMcpUpstreamToolEntity::getUpstreamId, e.getId()));
        return new McpUpstreamVO(
                e.getId(),
                e.getCode(),
                e.getName(),
                e.getProtocol(),
                e.getBaseUrl(),
                e.getEndpoint(),
                StringUtils.hasText(e.getAuthHeaderEnc()),
                e.getRequestTimeoutMs(),
                e.getStatus(),
                e.getHealthStatus(),
                e.getHealthMessage(),
                e.getLastProbeAt(),
                e.getRemark(),
                count == null ? 0 : count.intValue(),
                e.getCreateTime());
    }

    private static String trimSlash(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

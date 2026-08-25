package cn.datafuturex.zhishu.ai.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.datafuturex.zhishu.ai.mcp.config.McpProperties;
import cn.datafuturex.zhishu.ai.mcp.domain.dto.McpClientCreateDTO;
import cn.datafuturex.zhishu.ai.mcp.domain.dto.McpClientUpdateDTO;
import cn.datafuturex.zhishu.ai.mcp.domain.entity.AiMcpClientEntity;
import cn.datafuturex.zhishu.ai.mcp.domain.vo.McpClientVO;
import cn.datafuturex.zhishu.ai.mcp.mapper.AiMcpClientMapper;
import cn.datafuturex.zhishu.ai.mcp.support.McpCrypto;
import cn.datafuturex.zhishu.ai.mcp.support.McpJson;
import cn.datafuturex.zhishu.ai.mcp.support.McpOutboundCatalog;
import cn.datafuturex.zhishu.ai.shared.UserContext;
import cn.datafuturex.zhishu.ai.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class McpClientAdminService {

    private final AiMcpClientMapper clientMapper;
    private final McpProperties properties;

    public List<McpClientVO> list() {
        return clientMapper.selectList(new LambdaQueryWrapper<AiMcpClientEntity>()
                        .orderByDesc(AiMcpClientEntity::getId))
                .stream()
                .map(e -> toVo(e, null))
                .toList();
    }

    @Transactional
    public McpClientVO create(McpClientCreateDTO dto) {
        String apiKey = McpCrypto.newApiKey();
        AiMcpClientEntity entity = new AiMcpClientEntity();
        entity.setName(dto.name().trim());
        entity.setKeyPrefix(McpCrypto.keyPrefix(apiKey));
        entity.setSecretHash(McpCrypto.sha256Hex(apiKey));
        entity.setBoundUserId(dto.boundUserId());
        entity.setBoundUsername(dto.boundUsername());
        entity.setCapabilities(McpJson.toJson(
                dto.capabilities() == null || dto.capabilities().isEmpty()
                        ? McpOutboundCatalog.DEFAULT_CAPABILITIES
                        : dto.capabilities()));
        entity.setRpmLimit(dto.rpmLimit() == null ? properties.getDefaultRpm() : dto.rpmLimit());
        entity.setStatus("ENABLED");
        entity.setRemark(dto.remark());
        entity.setCreatedBy(UserContext.getUserId());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        clientMapper.insert(entity);
        return toVo(entity, apiKey);
    }

    @Transactional
    public McpClientVO update(Long id, McpClientUpdateDTO dto) {
        AiMcpClientEntity entity = require(id);
        entity.setName(dto.name().trim());
        entity.setBoundUserId(dto.boundUserId());
        entity.setBoundUsername(dto.boundUsername());
        entity.setCapabilities(McpJson.toJson(dto.capabilities()));
        entity.setRpmLimit(dto.rpmLimit() == null ? properties.getDefaultRpm() : dto.rpmLimit());
        entity.setStatus(normalizeStatus(dto.status()));
        entity.setRemark(dto.remark());
        entity.setUpdateTime(LocalDateTime.now());
        clientMapper.updateById(entity);
        return toVo(entity, null);
    }

    @Transactional
    public McpClientVO rotateKey(Long id) {
        AiMcpClientEntity entity = require(id);
        String apiKey = McpCrypto.newApiKey();
        entity.setKeyPrefix(McpCrypto.keyPrefix(apiKey));
        entity.setSecretHash(McpCrypto.sha256Hex(apiKey));
        entity.setUpdateTime(LocalDateTime.now());
        clientMapper.updateById(entity);
        return toVo(entity, apiKey);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        clientMapper.deleteById(id);
    }

    public AiMcpClientEntity require(Long id) {
        AiMcpClientEntity entity = clientMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "MCP Client 不存在: " + id);
        }
        return entity;
    }

    public void touchLastUsed(Long id) {
        AiMcpClientEntity patch = new AiMcpClientEntity();
        patch.setId(id);
        patch.setLastUsedAt(LocalDateTime.now());
        clientMapper.updateById(patch);
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "ENABLED";
        }
        String s = status.trim().toUpperCase();
        if (!"ENABLED".equals(s) && !"DISABLED".equals(s)) {
            throw new BusinessException("status 仅支持 ENABLED / DISABLED");
        }
        return s;
    }

    private static McpClientVO toVo(AiMcpClientEntity e, String apiKey) {
        return new McpClientVO(
                e.getId(),
                e.getName(),
                e.getKeyPrefix(),
                e.getBoundUserId(),
                e.getBoundUsername(),
                McpJson.parseStringList(e.getCapabilities()),
                e.getRpmLimit(),
                e.getStatus(),
                e.getRemark(),
                e.getLastUsedAt(),
                e.getCreatedBy(),
                e.getCreateTime(),
                apiKey);
    }
}

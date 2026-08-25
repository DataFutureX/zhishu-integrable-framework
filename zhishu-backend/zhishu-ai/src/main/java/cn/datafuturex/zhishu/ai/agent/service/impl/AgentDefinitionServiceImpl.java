package cn.datafuturex.zhishu.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.datafuturex.zhishu.ai.agent.domain.dto.AgentCreateDTO;
import cn.datafuturex.zhishu.ai.agent.domain.dto.AgentUpdateDTO;
import cn.datafuturex.zhishu.ai.agent.domain.entity.AiAgentEntity;
import cn.datafuturex.zhishu.ai.agent.domain.vo.AgentVO;
import cn.datafuturex.zhishu.ai.agent.domain.vo.CapabilityVO;
import cn.datafuturex.zhishu.ai.agent.domain.vo.WorkflowTemplateVO;
import cn.datafuturex.zhishu.ai.agent.enums.AgentCapability;
import cn.datafuturex.zhishu.ai.agent.enums.WorkflowType;
import cn.datafuturex.zhishu.ai.agent.mapper.AiAgentMapper;
import cn.datafuturex.zhishu.ai.agent.registry.ToolCapabilityRegistry;
import cn.datafuturex.zhishu.ai.agent.service.AgentDefinitionService;
import cn.datafuturex.zhishu.ai.agent.support.AgentJsonUtils;
import cn.datafuturex.zhishu.ai.shared.mcp.ExternalToolPort;
import cn.datafuturex.zhishu.ai.shared.UserContext;
import cn.datafuturex.zhishu.ai.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentDefinitionServiceImpl implements AgentDefinitionService {

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    private final AiAgentMapper aiAgentMapper;
    private final ToolCapabilityRegistry toolCapabilityRegistry;
    private final ExternalToolPort externalToolPort;

    @Override
    public List<AgentVO> list(String status) {
        LambdaQueryWrapper<AiAgentEntity> qw = new LambdaQueryWrapper<AiAgentEntity>()
                .orderByDesc(AiAgentEntity::getDefaultAgent)
                .orderByAsc(AiAgentEntity::getId);
        if (StringUtils.hasText(status)) {
            qw.eq(AiAgentEntity::getStatus, status.trim().toUpperCase());
        }
        return aiAgentMapper.selectList(qw).stream().map(this::toVo).toList();
    }

    @Override
    public AgentVO get(Long id) {
        return toVo(requireEntity(id));
    }

    @Override
    public AiAgentEntity requireEntity(Long id) {
        AiAgentEntity entity = aiAgentMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "智能体不存在: " + id);
        }
        return entity;
    }

    @Override
    public AiAgentEntity requireEnabled(Long id) {
        AiAgentEntity entity = requireEntity(id);
        if (!STATUS_ENABLED.equalsIgnoreCase(entity.getStatus())) {
            throw new BusinessException("智能体已禁用: " + entity.getName());
        }
        return entity;
    }

    @Override
    public AiAgentEntity requireDefault() {
        AiAgentEntity entity = aiAgentMapper.selectOne(
                new LambdaQueryWrapper<AiAgentEntity>()
                        .eq(AiAgentEntity::getDefaultAgent, true)
                        .eq(AiAgentEntity::getStatus, STATUS_ENABLED)
                        .last("LIMIT 1"));
        if (entity != null) {
            return entity;
        }
        entity = aiAgentMapper.selectOne(
                new LambdaQueryWrapper<AiAgentEntity>()
                        .eq(AiAgentEntity::getStatus, STATUS_ENABLED)
                        .orderByAsc(AiAgentEntity::getId)
                        .last("LIMIT 1"));
        if (entity == null) {
            throw new BusinessException("未配置可用智能体，请先在「智能体管理」中创建");
        }
        return entity;
    }

    @Override
    @Transactional
    public AgentVO create(AgentCreateDTO dto) {
        WorkflowType.require(dto.workflowType());
        String capsJson = AgentJsonUtils.toCapabilitiesJson(dto.capabilities());
        Long exists = aiAgentMapper.selectCount(
                new LambdaQueryWrapper<AiAgentEntity>().eq(AiAgentEntity::getCode, dto.code().trim()));
        if (exists != null && exists > 0) {
            throw new BusinessException("智能体编码已存在: " + dto.code());
        }

        AiAgentEntity entity = new AiAgentEntity();
        entity.setCode(dto.code().trim());
        applyMutableFields(entity, dto.name(), dto.description(), dto.systemPrompt(), dto.model(),
                dto.temperature(), dto.maxTokens(), capsJson, dto.workflowType(), dto.workflowConfig(),
                dto.documentIds(), dto.enableMemory(), normalizeStatus(dto.status()));
        entity.setBuiltin(false);
        entity.setDefaultAgent(false);
        entity.setCreatedBy(StringUtils.hasText(UserContext.getUserId()) ? UserContext.getUserId() : "unknown");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        aiAgentMapper.insert(entity);
        externalToolPort.bindAgentUpstreams(entity.getId(), dto.mcpUpstreamIds());
        log.info("创建智能体 id={}, code={}", entity.getId(), entity.getCode());
        return toVo(entity);
    }

    @Override
    @Transactional
    public AgentVO update(Long id, AgentUpdateDTO dto) {
        AiAgentEntity entity = requireEntity(id);
        WorkflowType.require(dto.workflowType());
        String capsJson = AgentJsonUtils.toCapabilitiesJson(dto.capabilities());
        applyMutableFields(entity, dto.name(), dto.description(), dto.systemPrompt(), dto.model(),
                dto.temperature(), dto.maxTokens(), capsJson, dto.workflowType(), dto.workflowConfig(),
                dto.documentIds(), dto.enableMemory(), normalizeStatus(dto.status()));
        entity.setUpdateTime(LocalDateTime.now());
        aiAgentMapper.updateById(entity);
        if (dto.mcpUpstreamIds() != null) {
            externalToolPort.bindAgentUpstreams(entity.getId(), dto.mcpUpstreamIds());
        }
        return toVo(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AiAgentEntity entity = requireEntity(id);
        if (Boolean.TRUE.equals(entity.getBuiltin())) {
            throw new BusinessException("内置智能体不可删除");
        }
        if (Boolean.TRUE.equals(entity.getDefaultAgent())) {
            throw new BusinessException("默认智能体不可删除，请先指定其他默认智能体");
        }
        aiAgentMapper.deleteById(id);
        externalToolPort.bindAgentUpstreams(id, List.of());
    }

    @Override
    @Transactional
    public void setDefault(Long id) {
        AiAgentEntity entity = requireEnabled(id);
        aiAgentMapper.update(null, new LambdaUpdateWrapper<AiAgentEntity>()
                .set(AiAgentEntity::getDefaultAgent, false)
                .eq(AiAgentEntity::getDefaultAgent, true));
        entity.setDefaultAgent(true);
        entity.setUpdateTime(LocalDateTime.now());
        aiAgentMapper.updateById(entity);
    }

    @Override
    public List<CapabilityVO> listCapabilities() {
        return Arrays.stream(AgentCapability.values())
                .map(c -> new CapabilityVO(
                        c.name(),
                        c.getLabel(),
                        c.getDescription(),
                        c.isToolBased(),
                        c.getToolNames(),
                        toolCapabilityRegistry.describeTools(c.getToolNames())))
                .toList();
    }

    @Override
    public List<WorkflowTemplateVO> listWorkflowTemplates() {
        return Arrays.stream(WorkflowType.values())
                .map(t -> new WorkflowTemplateVO(t.name(), t.getLabel(), t.getDescription()))
                .toList();
    }

    private void applyMutableFields(
            AiAgentEntity entity,
            String name,
            String description,
            String systemPrompt,
            String model,
            Double temperature,
            Integer maxTokens,
            String capsJson,
            String workflowType,
            String workflowConfig,
            List<Long> documentIds,
            Boolean enableMemory,
            String status) {
        entity.setName(name.trim());
        entity.setDescription(description);
        entity.setSystemPrompt(systemPrompt);
        entity.setModel(StringUtils.hasText(model) ? model.trim() : null);
        entity.setTemperature(temperature == null ? null
                : BigDecimal.valueOf(temperature).setScale(2, RoundingMode.HALF_UP));
        entity.setMaxTokens(maxTokens);
        entity.setCapabilities(capsJson);
        entity.setWorkflowType(workflowType.trim().toUpperCase());
        entity.setWorkflowConfig(workflowConfig);
        entity.setDocumentIds(AgentJsonUtils.toDocumentIdsJson(documentIds));
        entity.setEnableMemory(enableMemory);
        entity.setStatus(status);
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return STATUS_ENABLED;
        }
        String value = status.trim().toUpperCase();
        if (!STATUS_ENABLED.equals(value) && !STATUS_DISABLED.equals(value)) {
            throw new BusinessException("无效状态: " + status);
        }
        return value;
    }

    private AgentVO toVo(AiAgentEntity entity) {
        return new AgentVO(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getSystemPrompt(),
                entity.getModel(),
                entity.getTemperature() == null ? null : entity.getTemperature().doubleValue(),
                entity.getMaxTokens(),
                AgentJsonUtils.parseCapabilities(entity.getCapabilities()),
                entity.getWorkflowType(),
                entity.getWorkflowConfig(),
                AgentJsonUtils.parseDocumentIds(entity.getDocumentIds()),
                entity.getEnableMemory(),
                entity.getStatus(),
                entity.getBuiltin(),
                entity.getDefaultAgent(),
                entity.getCreatedBy(),
                entity.getCreateTime(),
                entity.getUpdateTime(),
                externalToolPort.listBoundUpstreamIds(entity.getId()));
    }
}

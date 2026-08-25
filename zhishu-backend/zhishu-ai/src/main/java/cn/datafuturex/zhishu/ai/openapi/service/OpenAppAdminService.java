package cn.datafuturex.zhishu.ai.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.datafuturex.zhishu.ai.mcp.support.McpCrypto;
import cn.datafuturex.zhishu.ai.modelconfig.config.ModelConfigProperties;
import cn.datafuturex.zhishu.ai.openapi.domain.entity.OpenAppEntity;
import cn.datafuturex.zhishu.ai.openapi.dto.GenerateAkSkResult;
import cn.datafuturex.zhishu.ai.openapi.dto.OpenAppUpsertDTO;
import cn.datafuturex.zhishu.ai.openapi.dto.OpenAppVO;
import cn.datafuturex.zhishu.ai.openapi.mapper.OpenAppMapper;
import cn.datafuturex.zhishu.ai.openapi.support.OpenApiCrypto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 开放应用管理服务（管理端使用，需认证）。
 */
@Service
@RequiredArgsConstructor
public class OpenAppAdminService {

    private final OpenAppMapper openAppMapper;
    private final ModelConfigProperties modelConfigProperties;
    private final ObjectMapper objectMapper;

    /** 查询全部开放应用（不含 SK） */
    public List<OpenAppVO> listApps() {
        return openAppMapper.selectList(new LambdaQueryWrapper<OpenAppEntity>()
                        .orderByAsc(OpenAppEntity::getId))
                .stream()
                .map(this::toVo)
                .toList();
    }

    /** 查询单个应用详情 */
    public OpenAppVO getApp(Long id) {
        return toVo(require(id));
    }

    /** 创建新应用 */
    @Transactional
    public OpenAppVO createApp(OpenAppUpsertDTO dto) {
        assertCodeFree(dto.code(), null);
        OpenAppEntity entity = new OpenAppEntity();
        entity.setCode(dto.code().trim());
        entity.setName(dto.name().trim());
        entity.setRemark(dto.remark());
        entity.setStatus("ENABLED");
        entity.setAllowedScopes(serializeScopes(dto.allowedScopes()));
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        openAppMapper.insert(entity);
        return toVo(openAppMapper.selectById(entity.getId()));
    }

    /** 更新应用基本信息（名称、备注、调用范围） */
    @Transactional
    public OpenAppVO updateApp(Long id, OpenAppUpsertDTO dto) {
        OpenAppEntity entity = require(id);
        if (!entity.getCode().equalsIgnoreCase(dto.code())) {
            assertCodeFree(dto.code(), id);
            entity.setCode(dto.code().trim());
        }
        entity.setName(dto.name().trim());
        entity.setRemark(dto.remark());
        entity.setAllowedScopes(serializeScopes(dto.allowedScopes()));
        entity.setUpdateTime(LocalDateTime.now());
        openAppMapper.updateById(entity);
        return toVo(openAppMapper.selectById(id));
    }

    /** 移除应用（同时清除 AK/SK） */
    @Transactional
    public void deleteApp(Long id) {
        require(id);
        openAppMapper.deleteById(id);
    }

    /** 生成或重新生成 AK/SK 对。SK 明文仅返回一次。 */
    @Transactional
    public GenerateAkSkResult generateAkSk(Long id) {
        OpenAppEntity entity = require(id);
        String ak = OpenApiCrypto.generateAccessKey();
        String sk = OpenApiCrypto.generateSecretKey();
        String skEnc = McpCrypto.encrypt(sk, modelConfigProperties.getCryptoKey());

        entity.setAccessKey(ak);
        entity.setSecretKeyEnc(skEnc);
        entity.setAkskGeneratedAt(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        openAppMapper.updateById(entity);

        return new GenerateAkSkResult(ak, sk);
    }

    /** 仅重新生成 SK（AK 不变，旧 SK 立即失效） */
    @Transactional
    public GenerateAkSkResult regenerateSk(Long id) {
        OpenAppEntity entity = require(id);
        if (entity.getAccessKey() == null) {
            throw new IllegalArgumentException("该应用尚未生成 AK/SK，请先生成");
        }
        String sk = OpenApiCrypto.generateSecretKey();
        String skEnc = McpCrypto.encrypt(sk, modelConfigProperties.getCryptoKey());

        entity.setSecretKeyEnc(skEnc);
        entity.setAkskGeneratedAt(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        openAppMapper.updateById(entity);

        return new GenerateAkSkResult(entity.getAccessKey(), sk);
    }

    /** 更新应用调用范围（scopes） */
    @Transactional
    public OpenAppVO updateScopes(Long id, List<String> scopes) {
        OpenAppEntity entity = require(id);
        String json = serializeScopes(scopes);
        entity.setAllowedScopes(json);
        entity.setUpdateTime(LocalDateTime.now());
        openAppMapper.updateById(entity);
        return toVo(openAppMapper.selectById(id));
    }

    /** 启用 / 停用应用 */
    @Transactional
    public OpenAppVO updateStatus(Long id, String status) {
        OpenAppEntity entity = require(id);
        entity.setStatus(normalizeStatus(status));
        entity.setUpdateTime(LocalDateTime.now());
        openAppMapper.updateById(entity);
        return toVo(openAppMapper.selectById(id));
    }

    /* ── 内部方法 ──────────────────────────────────────────── */

    private OpenAppEntity require(Long id) {
        OpenAppEntity entity = openAppMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("开放应用不存在: id=" + id);
        }
        return entity;
    }

    private void assertCodeFree(String code, Long excludeId) {
        LambdaQueryWrapper<OpenAppEntity> qw = new LambdaQueryWrapper<OpenAppEntity>()
                .eq(OpenAppEntity::getCode, code);
        if (excludeId != null) {
            qw.ne(OpenAppEntity::getId, excludeId);
        }
        Long count = openAppMapper.selectCount(qw);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("编码已存在: " + code);
        }
    }

    private OpenAppVO toVo(OpenAppEntity entity) {
        OpenAppVO vo = new OpenAppVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setStatus(entity.getStatus());
        vo.setAllowedScopes(entity.getAllowedScopes());
        vo.setRemark(entity.getRemark());
        vo.setAccessKey(entity.getAccessKey());
        vo.setAkskGeneratedAt(entity.getAkskGeneratedAt());
        vo.setLastUsedAt(entity.getLastUsedAt());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private String serializeScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(scopes);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return "ENABLED";
        }
        return "DISABLED".equalsIgnoreCase(status) ? "DISABLED" : "ENABLED";
    }
}

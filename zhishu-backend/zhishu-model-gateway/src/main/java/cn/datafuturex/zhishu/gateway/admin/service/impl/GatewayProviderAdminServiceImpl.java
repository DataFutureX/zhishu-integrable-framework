package cn.datafuturex.zhishu.gateway.admin.service.impl;

import cn.datafuturex.zhishu.gateway.provider.api.ModelProviderPort;
import cn.datafuturex.zhishu.gateway.provider.dto.ModelProviderCreateDTO;
import cn.datafuturex.zhishu.gateway.provider.dto.ModelProviderUpdateDTO;
import cn.datafuturex.zhishu.gateway.provider.vo.ModelProviderVO;
import cn.datafuturex.zhishu.gateway.provider.vo.UpstreamModelInfo;
import cn.datafuturex.zhishu.ai.shared.exception.BusinessException;
import cn.datafuturex.zhishu.gateway.admin.service.GatewayProviderAdminService;
import cn.datafuturex.zhishu.gateway.admin.vo.GatewayProviderAdminVO;
import cn.datafuturex.zhishu.gateway.catalog.GatewayCatalogService;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelEntity;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 供应商管理实现：委托 {@link ModelProviderPort} 维护连接，同时在删除前校验模型引用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayProviderAdminServiceImpl implements GatewayProviderAdminService {

    private static final String TYPE_CHAT = "CHAT";
    private static final String TYPE_EMBEDDING = "EMBEDDING";

    private final ModelProviderPort modelProviderPort;
    private final GatewayModelMapper gatewayModelMapper;
    private final GatewayCatalogService catalogService;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<GatewayProviderAdminVO> list() {
        List<ModelProviderVO> providers = modelProviderPort.list();
        List<GatewayProviderAdminVO> result = new ArrayList<>(providers.size());
        for (ModelProviderVO provider : providers) {
            result.add(toVO(provider));
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GatewayProviderAdminVO create(ModelProviderCreateDTO dto) {
        return toVO(modelProviderPort.create(dto));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GatewayProviderAdminVO update(Long id, ModelProviderUpdateDTO dto) {
        GatewayProviderAdminVO vo = toVO(modelProviderPort.update(id, dto));
        catalogService.invalidate();
        return vo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        long refs = countModels(id);
        if (refs > 0) {
            throw new BusinessException(400, "该供应商下仍有 " + refs + " 个模型，请先在「模型设置」中删除");
        }
        modelProviderPort.delete(id);
        catalogService.invalidate();
        log.info("[网关管理] 删除供应商 id={}", id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GatewayProviderAdminVO markDefault(Long id) {
        return toVO(modelProviderPort.setDefault(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String test(Long id) {
        return modelProviderPort.testConnection(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UpstreamModelInfo> upstreamModels(Long id) {
        return modelProviderPort.listUpstreamModels(id);
    }

    private long countModels(Long providerId) {
        Long total = gatewayModelMapper.selectCount(new LambdaQueryWrapper<GatewayModelEntity>()
                .eq(GatewayModelEntity::getProviderId, providerId));
        return total == null ? 0L : total;
    }

    private long countModels(Long providerId, String modelType) {
        Long total = gatewayModelMapper.selectCount(new LambdaQueryWrapper<GatewayModelEntity>()
                .eq(GatewayModelEntity::getProviderId, providerId)
                .eq(GatewayModelEntity::getModelType, modelType));
        return total == null ? 0L : total;
    }

    private GatewayProviderAdminVO toVO(ModelProviderVO provider) {
        return new GatewayProviderAdminVO(
                provider.id(),
                provider.name(),
                provider.providerKey(),
                provider.baseUrl(),
                provider.apiKeyMasked(),
                provider.apiKeyConfigured(),
                provider.isDefault(),
                provider.status(),
                provider.sortOrder(),
                provider.remark(),
                countModels(provider.id(), TYPE_CHAT),
                countModels(provider.id(), TYPE_EMBEDDING),
                provider.updateTime());
    }
}

package cn.datafuturex.zhishu.gateway.catalog.impl;

import cn.datafuturex.zhishu.gateway.provider.api.ModelUpstreamPort;
import cn.datafuturex.zhishu.gateway.catalog.GatewayCatalogService;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelEntity;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelMapper;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelView;
import cn.datafuturex.zhishu.gateway.config.GatewayProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 模型目录实现：数据库为源，进程内按 TTL 缓存；上游不可用的模型对外不可见。
 */
@Slf4j
@Service
public class GatewayCatalogServiceImpl implements GatewayCatalogService {

    private static final String CACHE_KEY = "enabled";
    private static final String MODEL_TYPE_CHAT = "CHAT";

    private final GatewayModelMapper gatewayModelMapper;
    private final ModelUpstreamPort modelUpstreamPort;
    private final Cache<String, List<GatewayModelView>> cache;

    /**
     * 构造目录服务（缓存 TTL 取配置）。
     *
     * @param gatewayModelMapper 目录 Mapper
     * @param modelUpstreamPort  上游端点端口
     * @param properties         网关配置
     */
    public GatewayCatalogServiceImpl(GatewayModelMapper gatewayModelMapper,
                                     ModelUpstreamPort modelUpstreamPort,
                                     GatewayProperties properties) {
        this.gatewayModelMapper = gatewayModelMapper;
        this.modelUpstreamPort = modelUpstreamPort;
        int ttlSeconds = Math.max(1, properties.getCatalog().getCacheTtlSeconds());
        this.cache = Caffeine.newBuilder()
                .maximumSize(4)
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<GatewayModelView> listEnabled() {
        List<GatewayModelView> cached = cache.getIfPresent(CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        List<GatewayModelView> loaded = load();
        cache.put(CACHE_KEY, loaded);
        return loaded;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<GatewayModelView> find(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return Optional.empty();
        }
        return listEnabled().stream()
                .filter(view -> view.modelId().equals(modelId.trim()))
                .findFirst();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<GatewayModelView> findDefault() {
        return listEnabled().stream()
                .filter(GatewayModelView::isDefault)
                .findFirst();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invalidate() {
        cache.invalidateAll();
    }

    private List<GatewayModelView> load() {
        List<GatewayModelEntity> rows = gatewayModelMapper.selectList(
                new LambdaQueryWrapper<GatewayModelEntity>()
                        .eq(GatewayModelEntity::getStatus, "ENABLED")
                        .eq(GatewayModelEntity::getModelType, MODEL_TYPE_CHAT)
                        .orderByAsc(GatewayModelEntity::getSortOrder)
                        .orderByAsc(GatewayModelEntity::getId));
        List<GatewayModelView> views = new ArrayList<>(rows.size());
        for (GatewayModelEntity row : rows) {
            Optional<ModelUpstreamPort.UpstreamEndpoint> upstream =
                    modelUpstreamPort.resolve(row.getProviderId());
            if (upstream.isEmpty()) {
                log.warn("[网关目录] model_id={} 的上游 provider#{} 不存在或已禁用，暂不对外",
                        row.getModelId(), row.getProviderId());
                continue;
            }
            if (!upstream.get().usable()) {
                log.warn("[网关目录] model_id={} 的上游 provider#{} 未配置 API Key，暂不对外",
                        row.getModelId(), row.getProviderId());
                continue;
            }
            views.add(new GatewayModelView(row, upstream.get()));
        }
        log.debug("[网关目录] 可用模型 {} 个", views.size());
        return List.copyOf(views);
    }
}

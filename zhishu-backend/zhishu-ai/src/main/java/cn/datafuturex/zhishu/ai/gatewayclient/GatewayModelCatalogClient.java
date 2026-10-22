package cn.datafuturex.zhishu.ai.gatewayclient;

import cn.datafuturex.zhishu.ai.gatewayclient.config.GatewayInternalProperties;
import cn.datafuturex.zhishu.ai.modelconfig.api.GatewayDefaultModelPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 网关模型目录客户端：拉取 {@code /v1/models}，供内部默认模型解析与绑定校验。
 * <p>
 * 使用服务账号 token 调用（目录对所有已认证用户一致）；结果缓存 60 秒。
 */
@Slf4j
@Component
public class GatewayModelCatalogClient implements GatewayDefaultModelPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CACHE_KEY = "catalog";

    private final GatewayInternalProperties properties;
    private final GatewayServiceTokenProvider tokenProvider;
    private final HttpClient httpClient;
    private final Cache<String, Catalog> cache = Caffeine.newBuilder()
            .maximumSize(2)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();

    /**
     * 构造目录客户端。
     *
     * @param properties    内部调用参数
     * @param tokenProvider 服务账号 token
     */
    public GatewayModelCatalogClient(GatewayInternalProperties properties,
                                     GatewayServiceTokenProvider tokenProvider) {
        this.properties = properties;
        this.tokenProvider = tokenProvider;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, properties.getCatalogTimeoutMs())))
                .build();
    }

    /**
     * 是否启用内部统一（与 {@code zhishu.gateway.internal.enabled} 对齐）。
     *
     * @return true=经网关
     */
    @Override
    public boolean enabled() {
        return properties.isEnabled();
    }

    /**
     * 网关默认模型 id。
     *
     * @return 默认模型，未配置或拉取失败返回空
     */
    @Override
    public Optional<String> defaultModelId() {
        return Optional.ofNullable(catalog().defaultModel());
    }

    /**
     * 目录中是否包含指定模型。
     *
     * @param modelId 模型 id
     * @return 存在返回 true；目录不可用时按「存在」放行，交给网关裁决
     */
    public boolean contains(String modelId) {
        Catalog catalog = catalog();
        if (!catalog.available()) {
            return true;
        }
        return catalog.models().contains(modelId);
    }

    /**
     * 目录中的模型 id 列表。
     *
     * @return 模型 id 列表
     */
    public List<String> modelIds() {
        return catalog().models();
    }

    private Catalog catalog() {
        Catalog cached = cache.getIfPresent(CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        Catalog loaded = load();
        cache.put(CACHE_KEY, loaded);
        return loaded;
    }

    private Catalog load() {
        String url = trimTrailingSlash(properties.getBaseUrl()) + "/v1/models";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(500, properties.getCatalogTimeoutMs())))
                    .header("Authorization", "Bearer " + tokenProvider.token())
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                log.warn("[网关客户端] 拉取模型目录失败 HTTP {}: {}", response.statusCode(), url);
                return Catalog.unavailable();
            }
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode data = root.path("data");
            List<String> models = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode item : data) {
                    String id = item.path("id").asText(null);
                    if (StringUtils.hasText(id)) {
                        models.add(id);
                    }
                }
            }
            String defaultModel = root.path("default").path("model").asText(null);
            return new Catalog(StringUtils.hasText(defaultModel) ? defaultModel : null,
                    List.copyOf(models), true);
        } catch (Exception e) {
            log.warn("[网关客户端] 拉取模型目录异常 url={}: {}", url, e.toString());
            return Catalog.unavailable();
        }
    }

    private static String trimTrailingSlash(String url) {
        String trimmed = url == null ? "" : url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * 目录快照。
     *
     * @param defaultModel 默认模型 id
     * @param models       模型 id 列表
     * @param available    本次拉取是否成功
     */
    record Catalog(String defaultModel, List<String> models, boolean available) {

        static Catalog unavailable() {
            return new Catalog(null, List.of(), false);
        }
    }
}

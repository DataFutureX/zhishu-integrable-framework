package cn.datafuturex.zhishu.openapi.sdk;

import cn.datafuturex.zhishu.openapi.sdk.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 知枢 Open API 客户端。
 * <p>
 * 使用 AK/SK 签名鉴权，内置 HTTP 客户端与 JSON 序列化。
 * </p>
 *
 * <pre>{@code
 * ZhishuOpenApiClient client = ZhishuOpenApiClient.builder()
 *     .baseUrl("https://zhishu.example.com")
 *     .accessKey("zsak_xxxx")
 *     .secretKey("zssk_xxxx")
 *     .build();
 *
 * // 同步对话
 * ChatResponse resp = client.chat(ChatRequest.of("你好"));
 * System.out.println(resp.content());
 *
 * // 查询智能体列表
 * List<AgentInfo> agents = client.listAgents();
 *
 * // 通用调用
 * Map<String, Object> result = client.get("/open/v1/kg/stats", Map.class);
 * }</pre>
 */
public final class ZhishuOpenApiClient {

    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String baseUrl;
    private final String accessKey;
    private final String secretKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    private ZhishuOpenApiClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.accessKey = builder.accessKey;
        this.secretKey = builder.secretKey;
        this.httpClient = builder.httpClient != null ? builder.httpClient : HttpClient.newHttpClient();
        this.objectMapper = builder.objectMapper != null ? builder.objectMapper : DEFAULT_MAPPER;
        this.timeout = builder.timeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    /* ── 便捷方法 ──────────────────────────────────────────── */

    /**
     * 同步对话 {@code POST /open/v1/chat}
     */
    public ChatResponse chat(ChatRequest request) {
        return post("/open/v1/chat", request, ChatResponse.class);
    }

    /**
     * 查询智能体列表 {@code GET /open/v1/agents}
     */
    public List<AgentInfo> listAgents() {
        return get("/open/v1/agents", new TypeReference<>() {});
    }

    /**
     * 知识图谱推送 {@code POST /open/v1/kg/upsert}
     */
    public KgSyncResult kgUpsert(KgUpsertRequest request) {
        return post("/open/v1/kg/upsert", request, KgSyncResult.class);
    }

    /* ── 通用 HTTP 方法 ───────────────────────────────────── */

    /**
     * 通用 GET 请求。
     *
     * @param path         接口路径（如 /open/v1/agents）
     * @param responseType 响应类型
     * @return 反序列化后的对象
     */
    public <T> T get(String path, Class<T> responseType) {
        HttpRequest request = buildRequest(path)
                .GET()
                .build();
        return execute(request, responseType);
    }

    /**
     * 通用 GET 请求（支持泛型类型）。
     */
    public <T> T get(String path, TypeReference<T> typeRef) {
        HttpRequest request = buildRequest(path)
                .GET()
                .build();
        return execute(request, typeRef);
    }

    /**
     * 通用 POST 请求。
     *
     * @param path         接口路径
     * @param body         请求体对象（自动 JSON 序列化）
     * @param responseType 响应类型
     * @return 反序列化后的对象
     */
    public <T> T post(String path, Object body, Class<T> responseType) {
        byte[] json = toJson(body);
        HttpRequest request = buildRequest(path)
                .POST(HttpRequest.BodyPublishers.ofByteArray(json))
                .build();
        return execute(request, responseType);
    }

    /**
     * 通用 POST 请求（支持泛型类型）。
     */
    public <T> T post(String path, Object body, TypeReference<T> typeRef) {
        byte[] json = toJson(body);
        HttpRequest request = buildRequest(path)
                .POST(HttpRequest.BodyPublishers.ofByteArray(json))
                .build();
        return execute(request, typeRef);
    }

    /**
     * 通用 PUT 请求。
     */
    public <T> T put(String path, Object body, Class<T> responseType) {
        byte[] json = toJson(body);
        HttpRequest request = buildRequest(path)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(json))
                .build();
        return execute(request, responseType);
    }

    /**
     * 通用 DELETE 请求。
     */
    public void delete(String path) {
        HttpRequest request = buildRequest(path)
                .DELETE()
                .build();
        executeRaw(request);
    }

    /* ── 底层方法 ──────────────────────────────────────────── */

    /**
     * 仅生成签名 Token（不发送请求），供自行封装 HTTP 调用的场景。
     */
    public String signToken() {
        return ZhishuOpenApiSigner.sign(accessKey, secretKey);
    }

    /**
     * 生成 Authorization 头值。
     */
    public String authorizationHeader() {
        return ZhishuOpenApiSigner.authorizationHeader(accessKey, secretKey);
    }

    /* ── 内部方法 ──────────────────────────────────────────── */

    private HttpRequest.Builder buildRequest(String path) {
        String url = baseUrl + (path.startsWith("/") ? path : "/" + path);
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authorizationHeader())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(timeout);
    }

    private <T> T execute(HttpRequest request, Class<T> responseType) {
        HttpResponse<String> response = executeRaw(request);
        return fromJson(response.body(), responseType);
    }

    private <T> T execute(HttpRequest request, TypeReference<T> typeRef) {
        HttpResponse<String> response = executeRaw(request);
        return fromJson(response.body(), typeRef);
    }

    private HttpResponse<String> executeRaw(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new OpenApiException(
                        response.statusCode(),
                        response.body(),
                        "Open API 请求失败: HTTP " + response.statusCode());
            }
            return response;
        } catch (OpenApiException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenApiException("Open API 请求异常: " + e.getMessage(), e);
        }
    }

    private byte[] toJson(Object obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (Exception e) {
            throw new OpenApiException("JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new OpenApiException("JSON 反序列化失败: " + e.getMessage(), e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            throw new OpenApiException("JSON 反序列化失败: " + e.getMessage(), e);
        }
    }

    /* ── Builder ───────────────────────────────────────────── */

    public static final class Builder {
        private String baseUrl;
        private String accessKey;
        private String secretKey;
        private HttpClient httpClient;
        private ObjectMapper objectMapper;
        private Duration timeout = Duration.ofSeconds(30);

        private Builder() {
        }

        /** 知枢平台地址，如 {@code https://zhishu.example.com} */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl == null ? null : baseUrl.trim();
            return this;
        }

        /** Access Key */
        public Builder accessKey(String accessKey) {
            this.accessKey = accessKey;
            return this;
        }

        /** Secret Key（明文） */
        public Builder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        /** 自定义 HttpClient（可选） */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /** 自定义 ObjectMapper（可选） */
        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        /** 请求超时时间（默认 30 秒） */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public ZhishuOpenApiClient build() {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalStateException("baseUrl 不能为空");
            }
            if (accessKey == null || accessKey.isBlank()) {
                throw new IllegalStateException("accessKey 不能为空");
            }
            if (secretKey == null || secretKey.isBlank()) {
                throw new IllegalStateException("secretKey 不能为空");
            }
            Objects.requireNonNull(timeout, "timeout");
            return new ZhishuOpenApiClient(this);
        }
    }
}

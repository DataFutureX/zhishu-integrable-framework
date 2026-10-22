package cn.datafuturex.zhishu.gateway.upstream;

import cn.datafuturex.zhishu.gateway.compat.GatewayException;
import cn.datafuturex.zhishu.gateway.config.GatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

/**
 * 上游 HTTP 客户端（JDK HttpClient，零额外依赖）。
 * <p>
 * 非流式：整体超时；流式：不设整体超时，改用「两帧间空闲超时」，避免长答案被切断。
 */
@Slf4j
@Component
public class UpstreamHttpClient {

    private static final String USER_AGENT = "zhishu-model-gateway/1.0";

    private final HttpClient httpClient;
    private final int requestTimeoutMs;
    private final int streamIdleTimeoutMs;

    /**
     * 构造客户端。
     *
     * @param properties 网关配置
     */
    public UpstreamHttpClient(GatewayProperties properties) {
        this.requestTimeoutMs = properties.getUpstream().getRequestTimeoutMs();
        this.streamIdleTimeoutMs = properties.getUpstream().getStreamIdleTimeoutMs();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getUpstream().getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * 非流式 POST。
     *
     * @param url    完整上游地址
     * @param apiKey 供应商 Key
     * @param body   请求体
     * @return 上游响应（字节）
     */
    public HttpResponse<byte[]> post(String url, String apiKey, byte[] body) {
        HttpRequest request = build(url, apiKey, MediaType.APPLICATION_JSON_VALUE, body, requestTimeoutMs);
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException e) {
            throw GatewayException.upstreamTimeout("上游响应超时");
        } catch (IOException e) {
            log.warn("[网关上游] 连接失败 url={}: {}", url, e.toString());
            throw GatewayException.upstreamUnavailable("上游不可达");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw GatewayException.upstreamUnavailable("上游调用被中断");
        }
    }

    /**
     * 流式 POST：返回状态码与响应体发布者。
     *
     * @param url    完整上游地址
     * @param apiKey 供应商 Key
     * @param body   请求体
     * @return 流式响应
     */
    public StreamResponse postStream(String url, String apiKey, byte[] body) {
        HttpRequest request = build(url, apiKey, MediaType.TEXT_EVENT_STREAM_VALUE, body, -1);
        try {
            HttpResponse<Flow.Publisher<List<ByteBuffer>>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofPublisher());
            return new StreamResponse(response.statusCode(), response.body());
        } catch (HttpTimeoutException e) {
            throw GatewayException.upstreamTimeout("上游响应超时");
        } catch (IOException e) {
            log.warn("[网关上游] 流式连接失败 url={}: {}", url, e.toString());
            throw GatewayException.upstreamUnavailable("上游不可达");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw GatewayException.upstreamUnavailable("上游调用被中断");
        }
    }

    /**
     * 把上游字节流切成 SSE 行（空闲超时 + 订阅取消即断开上游）。
     *
     * @param publisher 上游响应体
     * @return SSE 行
     */
    public Flux<String> lines(Flow.Publisher<List<ByteBuffer>> publisher) {
        return Flux.defer(() -> {
            SseLineSplitter splitter = new SseLineSplitter();
            return JdkFlowAdapter.flowPublisherToFlux(publisher)
                    .timeout(Duration.ofMillis(streamIdleTimeoutMs))
                    .flatMapIterable(splitter::add);
        });
    }

    /**
     * 一次性读取上游响应体（错误体）。
     *
     * @param publisher 上游响应体
     * @return 文本
     */
    public String bodyAsString(Flow.Publisher<List<ByteBuffer>> publisher) {
        ByteArrayOutputStream collector = new ByteArrayOutputStream();
        try {
            for (List<ByteBuffer> chunk : JdkFlowAdapter.flowPublisherToFlux(publisher).toIterable()) {
                for (ByteBuffer buffer : chunk) {
                    ByteBuffer copy = buffer.duplicate();
                    byte[] bytes = new byte[copy.remaining()];
                    copy.get(bytes);
                    collector.write(bytes, 0, bytes.length);
                }
            }
        } catch (Exception e) {
            log.debug("[网关上游] 读取错误体失败: {}", e.getMessage());
        }
        return collector.toString(StandardCharsets.UTF_8);
    }

    private HttpRequest build(String url, String apiKey, String accept, byte[] body, int timeoutMs) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, accept)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (timeoutMs > 0) {
            builder.timeout(Duration.ofMillis(timeoutMs));
        }
        return builder.build();
    }

    /**
     * 流式响应（状态码 + 响应体发布者）。
     *
     * @param status 上游状态码
     * @param body   响应体
     */
    public record StreamResponse(int status, Flow.Publisher<List<ByteBuffer>> body) {
    }

    /**
     * SSE 行切分器：按字节找 {@code \n}，仅在完整行上做 UTF-8 解码，避免多字节截断。
     */
    private static final class SseLineSplitter {

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        List<String> add(List<ByteBuffer> chunks) {
            List<String> lines = new ArrayList<>();
            for (ByteBuffer chunk : chunks) {
                ByteBuffer copy = chunk.duplicate();
                byte[] bytes = new byte[copy.remaining()];
                copy.get(bytes);
                lines.addAll(add(bytes));
            }
            return lines;
        }

        List<String> add(byte[] bytes) {
            buffer.write(bytes, 0, bytes.length);
            byte[] all = buffer.toByteArray();
            List<String> lines = new ArrayList<>();
            int lineStart = 0;
            for (int i = 0; i < all.length; i++) {
                if (all[i] != '\n') {
                    continue;
                }
                int lineEnd = i;
                if (lineEnd > lineStart && all[lineEnd - 1] == '\r') {
                    lineEnd--;
                }
                lines.add(new String(all, lineStart, lineEnd - lineStart, StandardCharsets.UTF_8));
                lineStart = i + 1;
            }
            buffer.reset();
            buffer.write(all, lineStart, all.length - lineStart);
            return lines;
        }
    }
}

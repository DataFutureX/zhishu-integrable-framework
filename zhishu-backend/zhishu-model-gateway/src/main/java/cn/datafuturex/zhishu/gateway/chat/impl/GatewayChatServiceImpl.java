package cn.datafuturex.zhishu.gateway.chat.impl;

import cn.datafuturex.zhishu.gateway.auth.GatewayPrincipal;
import cn.datafuturex.zhishu.gateway.catalog.GatewayCatalogService;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelView;
import cn.datafuturex.zhishu.gateway.chat.GatewayChatService;
import cn.datafuturex.zhishu.gateway.compat.GatewayException;
import cn.datafuturex.zhishu.gateway.compat.TokenEstimator;
import cn.datafuturex.zhishu.gateway.config.GatewayProperties;
import cn.datafuturex.zhishu.gateway.quota.GatewayQuotaService;
import cn.datafuturex.zhishu.gateway.quota.GatewayQuotaService.QuotaReservation;
import cn.datafuturex.zhishu.gateway.upstream.UpstreamHttpClient;
import cn.datafuturex.zhishu.gateway.usage.GatewayUsageService;
import cn.datafuturex.zhishu.gateway.usage.GatewayUsageService.Outcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.OutputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 推理端点实现：OpenAI 兼容透传 + 用户额度预留/结算 + 台账。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayChatServiceImpl implements GatewayChatService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DONE_PAYLOAD = "[DONE]";
    private static final String DATA_PREFIX = "data:";

    private final GatewayCatalogService catalogService;
    private final GatewayQuotaService quotaService;
    private final GatewayUsageService usageService;
    private final TokenEstimator tokenEstimator;
    private final UpstreamHttpClient upstreamHttpClient;
    private final GatewayProperties properties;

    /**
     * {@inheritDoc}
     */
    @Override
    public void handle(GatewayPrincipal principal, String channel, String rawBody,
                       GatewayUsageService.ClientMeta meta, HttpServletResponse response) throws IOException {
        byte[] rawBytes = rawBody == null ? new byte[0] : rawBody.getBytes(StandardCharsets.UTF_8);
        int maxBytes = properties.getUpstream().getMaxRequestBytes();
        if (rawBytes.length > maxBytes) {
            throw GatewayException.invalidRequest("请求体超过上限 " + maxBytes + " 字节");
        }
        ObjectNode request = readRequest(rawBody);
        String modelId = text(request, "model");
        if (!StringUtils.hasText(modelId)) {
            throw GatewayException.invalidRequest("缺少 model 字段");
        }
        boolean stream = request.path("stream").asBoolean(false);
        GatewayModelView view = catalogService.find(modelId)
                .orElseThrow(() -> GatewayException.modelNotFound(modelId));

        long promptEstimate = tokenEstimator.estimateRequest(request);
        long reserve = promptEstimate + reserveOutput(view, request);
        QuotaReservation reservation = quotaService.reserve(principal, reserve);

        String requestId = UUID.randomUUID().toString();
        long usageId = usageService.begin(new GatewayUsageService.BeginCommand(
                requestId, channel, principal, view, stream, reservation.reservedTokens(), meta));
        long startNanos = System.nanoTime();
        byte[] upstreamBody = buildUpstreamBody(request, view, stream);

        log.info("[网关推理] requestId={}, user={}, model={} -> {}/{}, stream={}, 预留={}",
                requestId, principal.username(), modelId, view.upstream().providerKey(),
                view.upstreamModel(), stream, reserve);

        if (stream) {
            forwardStream(view, upstreamBody, modelId, promptEstimate, reservation, usageId, startNanos, response);
        } else {
            forwardJson(view, upstreamBody, modelId, promptEstimate, reservation, usageId, startNanos, response);
        }
    }

    private void forwardJson(GatewayModelView view, byte[] upstreamBody, String modelId, long promptEstimate,
                             QuotaReservation reservation, long usageId, long startNanos,
                             HttpServletResponse response) throws IOException {
        HttpResponse<byte[]> upstream;
        try {
            upstream = upstreamHttpClient.post(completionsUrl(view), view.upstream().apiKey(), upstreamBody);
            if (upstream.statusCode() >= 400) {
                throw GatewayException.upstreamError(upstream.statusCode(), summarize(upstream.body()));
            }
        } catch (GatewayException e) {
            long latency = elapsedMs(startNanos);
            quotaService.release(reservation.quotaId(), reservation.reservedTokens());
            usageService.finish(usageId, failure(e, latency));
            throw e;
        }

        JsonNode json;
        try {
            json = MAPPER.readTree(upstream.body());
        } catch (Exception e) {
            long latency = elapsedMs(startNanos);
            quotaService.release(reservation.quotaId(), reservation.reservedTokens());
            usageService.finish(usageId, failure(GatewayException.upstreamUnavailable("上游返回非 JSON 响应"), latency));
            throw GatewayException.upstreamUnavailable("上游返回非 JSON 响应");
        }
        ObjectNode rewritten = (ObjectNode) rewriteModel(json, modelId);
        byte[] payload = MAPPER.writeValueAsBytes(rewritten);

        long latency = elapsedMs(startNanos);
        UsageNumbers numbers = extractUsage(json, promptEstimate, estimateCompletion(json));
        settleQuota(reservation, numbers.total());
        usageService.finish(usageId, Outcome.success(numbers.prompt(), numbers.completion(), numbers.total(),
                numbers.cached(), numbers.reasoning(), numbers.estimated(), 200, latency, null));

        writeJson(response, payload);
    }

    private void forwardStream(GatewayModelView view, byte[] upstreamBody, String modelId, long promptEstimate,
                               QuotaReservation reservation, long usageId, long startNanos,
                               HttpServletResponse response) throws IOException {
        UpstreamHttpClient.StreamResponse upstream;
        try {
            upstream = upstreamHttpClient.postStream(completionsUrl(view), view.upstream().apiKey(), upstreamBody);
            if (upstream.status() >= 400) {
                throw GatewayException.upstreamError(upstream.status(),
                        summarize(upstreamHttpClient.bodyAsString(upstream.body()).getBytes(StandardCharsets.UTF_8)));
            }
        } catch (GatewayException e) {
            long latency = elapsedMs(startNanos);
            quotaService.release(reservation.quotaId(), reservation.reservedTokens());
            usageService.finish(usageId, failure(e, latency));
            throw e;
        }

        prepareSse(response);
        OutputStream out = response.getOutputStream();
        StringBuilder completion = new StringBuilder();
        JsonNode usageNode = null;
        Long ttftMs = null;
        String status = "SUCCESS";
        String errorCode = null;
        String errorMessage = null;

        Flux<String> lines = upstreamHttpClient.lines(upstream.body());
        try (Stream<String> blocking = lines.toStream()) {
            java.util.Iterator<String> iterator = blocking.iterator();
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (!line.startsWith(DATA_PREFIX)) {
                    continue;
                }
                String payload = line.substring(DATA_PREFIX.length()).trim();
                if (payload.isEmpty()) {
                    continue;
                }
                if (DONE_PAYLOAD.equals(payload)) {
                    writeSse(out, DONE_PAYLOAD);
                    break;
                }
                JsonNode frame;
                try {
                    frame = MAPPER.readTree(payload);
                } catch (Exception e) {
                    writeSse(out, payload);
                    continue;
                }
                if (ttftMs == null) {
                    ttftMs = elapsedMs(startNanos);
                }
                usageNode = frame.hasNonNull("usage") ? frame.get("usage") : usageNode;
                appendContent(frame, completion);
                writeSse(out, MAPPER.writeValueAsString(rewriteModel(frame, modelId)));
            }
        } catch (IOException e) {
            status = "ABORTED";
            errorCode = "client_disconnected";
            errorMessage = "客户端断开或响应写出失败";
            log.info("[网关推理] 客户端断开 model={}: {}", modelId, e.getMessage());
        } catch (GatewayException e) {
            status = "TIMEOUT".equals(e.getCode()) ? "TIMEOUT" : "ERROR";
            errorCode = e.getCode();
            errorMessage = e.getMessage();
            log.warn("[网关推理] 流式上游异常 model={}: {}", modelId, e.getMessage());
        } catch (Exception e) {
            status = "ERROR";
            errorCode = "stream_error";
            errorMessage = e.getMessage();
            log.warn("[网关推理] 流式处理异常 model={}: {}", modelId, e.toString());
        }

        long latency = elapsedMs(startNanos);
        if ("SUCCESS".equals(status)) {
            UsageNumbers numbers = extractUsage(usageNode, promptEstimate,
                    tokenEstimator.estimateText(completion.toString()));
            settleQuota(reservation, numbers.total());
            usageService.finish(usageId, Outcome.success(numbers.prompt(), numbers.completion(), numbers.total(),
                    numbers.cached(), numbers.reasoning(), numbers.estimated(), 200, latency, ttftMs));
        } else {
            long actual = promptEstimate + tokenEstimator.estimateText(completion.toString());
            settleQuota(reservation, actual);
            usageService.finish(usageId, new Outcome(status, 200, (int) promptEstimate,
                    (int) Math.max(actual - promptEstimate, 0), (int) actual, null, null,
                    true, latency, ttftMs, errorCode, errorMessage));
        }
    }

    private ObjectNode readRequest(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            throw GatewayException.invalidRequest("请求体不能为空");
        }
        try {
            JsonNode node = MAPPER.readTree(rawBody);
            if (!(node instanceof ObjectNode objectNode)) {
                throw GatewayException.invalidRequest("请求体必须是 JSON 对象");
            }
            return objectNode;
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw GatewayException.invalidRequest("请求体不是合法 JSON");
        }
    }

    private byte[] buildUpstreamBody(ObjectNode request, GatewayModelView view, boolean stream) {
        ObjectNode copy = request.deepCopy();
        copy.put("model", view.upstreamModel());
        applyModelDefaults(copy, view);
        if (stream) {
            ObjectNode streamOptions = copy.hasNonNull("stream_options") && copy.get("stream_options").isObject()
                    ? (ObjectNode) copy.get("stream_options")
                    : copy.putObject("stream_options");
            streamOptions.put("include_usage", true);
        }
        String extraParams = view.entity().getExtraParams();
        if (StringUtils.hasText(extraParams)) {
            try {
                JsonNode extra = MAPPER.readTree(extraParams);
                if (extra.isObject()) {
                    java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = extra.fields();
                    while (fields.hasNext()) {
                        java.util.Map.Entry<String, JsonNode> entry = fields.next();
                        copy.set(entry.getKey(), entry.getValue());
                    }
                }
            } catch (Exception e) {
                log.warn("[网关推理] 目录 extra_params 非合法 JSON，已忽略 model={}", view.modelId());
            }
        }
        try {
            return MAPPER.writeValueAsBytes(copy);
        } catch (Exception e) {
            throw GatewayException.invalidRequest("无法序列化上游请求体");
        }
    }

    /**
     * 用模型行的默认生成参数补齐请求（客户端显式传值优先）。
     *
     * @param body 上游请求体（已深拷贝）
     * @param view 模型视图
     */
    private void applyModelDefaults(ObjectNode body, GatewayModelView view) {
        if (!body.hasNonNull("temperature") && view.entity().getTemperature() != null) {
            body.put("temperature", view.entity().getTemperature().doubleValue());
        }
        if (!body.hasNonNull("top_p") && view.entity().getTopP() != null) {
            body.put("top_p", view.entity().getTopP().doubleValue());
        }
        if (!body.hasNonNull("max_tokens") && view.entity().getMaxTokens() != null) {
            body.put("max_tokens", view.entity().getMaxTokens());
        }
    }

    private JsonNode rewriteModel(JsonNode node, String modelId) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.put("model", modelId);
        }
        return node;
    }

    private void appendContent(JsonNode frame, StringBuilder completion) {
        JsonNode choices = frame.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return;
        }
        JsonNode delta = choices.get(0).path("delta");
        JsonNode content = delta.path("content");
        if (content.isTextual()) {
            completion.append(content.asText());
        }
        JsonNode reasoning = delta.path("reasoning_content");
        if (reasoning.isTextual()) {
            completion.append(reasoning.asText());
        }
    }

    private long estimateCompletion(JsonNode response) {
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return 0L;
        }
        JsonNode content = choices.get(0).path("message").path("content");
        return tokenEstimator.estimateText(content.isTextual() ? content.asText() : "");
    }

    private UsageNumbers extractUsage(JsonNode node, long promptEstimate, long completionEstimate) {
        if (node != null && node.hasNonNull("usage") && node.get("usage").isObject()) {
            JsonNode usage = node.get("usage");
            Integer prompt = intOrNull(usage, "prompt_tokens");
            Integer completion = intOrNull(usage, "completion_tokens");
            Integer total = intOrNull(usage, "total_tokens");
            if (total == null && prompt != null && completion != null) {
                total = prompt + completion;
            }
            if (total != null) {
                return new UsageNumbers(prompt, completion, total,
                        intOrNull(usage, "prompt_tokens_details", "cached_tokens"),
                        intOrNull(usage, "completion_tokens_details", "reasoning_tokens"), false);
            }
        }
        int prompt = (int) promptEstimate;
        int completion = (int) completionEstimate;
        return new UsageNumbers(prompt, completion, prompt + completion, null, null, true);
    }

    private long reserveOutput(GatewayModelView view, ObjectNode request) {
        int cap = properties.getQuota().getReserveMaxOutputTokens();
        long output = cap;
        Integer maxOutput = view.entity().getMaxOutputTokens();
        if (maxOutput != null && maxOutput > 0) {
            output = Math.min(output, maxOutput);
        }
        JsonNode maxTokens = request.path("max_tokens");
        if (maxTokens.isNumber() && maxTokens.asInt() > 0) {
            output = Math.min(output, maxTokens.asInt());
        } else if (view.entity().getMaxTokens() != null && view.entity().getMaxTokens() > 0) {
            output = Math.min(output, view.entity().getMaxTokens());
        }
        return Math.max(output, 1L);
    }

    private void settleQuota(QuotaReservation reservation, long actual) {
        if (!reservation.tracked()) {
            return;
        }
        quotaService.settle(reservation.quotaId(), actual - reservation.reservedTokens());
    }

    private Outcome failure(GatewayException e, long latency) {
        return new Outcome("ERROR", e.getStatus(), null, null, null, null, null,
                false, latency, null, e.getCode(), truncate(e.getMessage(), 500));
    }

    private void writeJson(HttpServletResponse response, byte[] payload) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(200);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.getOutputStream().write(payload);
        response.flushBuffer();
    }

    private void prepareSse(HttpServletResponse response) {
        response.setStatus(200);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
    }

    private void writeSse(OutputStream out, String payload) throws IOException {
        out.write((DATA_PREFIX + " " + payload + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private String completionsUrl(GatewayModelView view) {
        return view.upstream().baseUrl() + "/chat/completions";
    }

    private String summarize(byte[] body) {
        if (body == null || body.length == 0) {
            return "上游返回错误（无响应体）";
        }
        String text = new String(body, StandardCharsets.UTF_8);
        try {
            JsonNode node = MAPPER.readTree(text);
            String message = node.path("error").path("message").asText(null);
            if (StringUtils.hasText(message)) {
                return truncate(message, 500);
            }
        } catch (Exception ignored) {
            // 非 JSON 错误体，退化为截断文本
        }
        return truncate(text, 500);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private static Integer intOrNull(JsonNode node, String parent, String field) {
        return intOrNull(node.path(parent), field);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * 用量三元组。
     *
     * @param prompt      输入 token
     * @param completion  输出 token
     * @param total       总 token
     * @param cached      缓存命中 token
     * @param reasoning   推理 token
     * @param estimated   是否估算
     */
    private record UsageNumbers(Integer prompt, Integer completion, Integer total,
                                Integer cached, Integer reasoning, boolean estimated) {
    }
}

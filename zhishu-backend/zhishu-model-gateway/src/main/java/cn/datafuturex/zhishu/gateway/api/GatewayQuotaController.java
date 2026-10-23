package cn.datafuturex.zhishu.gateway.controller;

import cn.datafuturex.zhishu.gateway.auth.GatewayAuthFilter;
import cn.datafuturex.zhishu.gateway.auth.GatewayPrincipal;
import cn.datafuturex.zhishu.gateway.quota.GatewayQuotaService;
import cn.datafuturex.zhishu.gateway.quota.GatewayQuotaService.QuotaSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 额度端点：给客户端展示「已用 / 上限 / 剩余 / 重置时间」。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zhishu.gateway.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "模型网关 · 额度", description = "当前账号的周期 token 额度与用量")
public class GatewayQuotaController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GatewayQuotaService quotaService;

    /**
     * 查询当前账号额度。
     *
     * @param request 请求（携带网关注入的主体）
     * @return 额度快照
     */
    @GetMapping("${zhishu.gateway.quota-path:/v1/quota}")
    @Operation(summary = "额度查询", description = "只读展示，不参与推理拦截决策")
    public ResponseEntity<String> quota(HttpServletRequest request) {
        GatewayPrincipal principal = (GatewayPrincipal) request.getAttribute(GatewayAuthFilter.PRINCIPAL_ATTRIBUTE);
        QuotaSnapshot snapshot = quotaService.snapshot(principal);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("unit", "tokens");

        Map<String, Object> period = new LinkedHashMap<>();
        period.put("kind", snapshot.periodKind() == null ? "monthly" : snapshot.periodKind().toLowerCase());
        if (snapshot.resetAtEpochSecond() != null) {
            period.put("reset_at", Instant.ofEpochSecond(snapshot.resetAtEpochSecond()).toString());
        }
        root.put("period", period);

        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put("type", snapshot.subjectType());
        subject.put("key", snapshot.subjectKey());
        root.put("subject", subject);

        root.put("limit", snapshot.limit());
        root.put("used", snapshot.used());
        root.put("remaining", snapshot.remaining() < 0 ? null : snapshot.remaining());

        String body;
        try {
            body = MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("序列化额度响应失败", e);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}

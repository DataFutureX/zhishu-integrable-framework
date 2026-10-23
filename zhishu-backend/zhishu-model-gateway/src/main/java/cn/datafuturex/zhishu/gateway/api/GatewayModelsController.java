package cn.datafuturex.zhishu.gateway.api;

import cn.datafuturex.zhishu.gateway.catalog.GatewayCatalogService;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelView;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型目录端点（OpenAI 兼容 + 能力元数据增强）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zhishu.gateway.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "模型网关 · 目录", description = "下发当前账号可见的模型清单与能力元数据")
public class GatewayModelsController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GatewayCatalogService catalogService;

    /**
     * 列出可用模型。
     *
     * @return OpenAI 兼容的模型列表
     */
    @GetMapping("${zhishu.gateway.models-path:/v1/models}")
    @Operation(summary = "模型目录", description = "字段名遵循桌面端契约（snake_case）")
    public ResponseEntity<String> models() {
        List<GatewayModelView> views = catalogService.listEnabled();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("object", "list");
        catalogService.findDefault().ifPresent(view -> root.put("default", Map.of("model", view.modelId())));
        List<Map<String, Object>> data = new ArrayList<>(views.size());
        for (GatewayModelView view : views) {
            data.add(toModelEntry(view));
        }
        root.put("data", data);
        String body;
        try {
            body = MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("序列化模型目录失败", e);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private Map<String, Object> toModelEntry(GatewayModelView view) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", view.modelId());
        entry.put("object", "model");
        entry.put("name", view.entity().getDisplayName());
        entry.put("owned_by", view.upstream().providerKey());
        if (view.entity().getCreateTime() != null) {
            entry.put("created", view.entity().getCreateTime()
                    .atZone(ZoneId.systemDefault()).toEpochSecond());
        }
        if (view.entity().getContextWindow() != null) {
            entry.put("context_window", view.entity().getContextWindow());
        }
        if (view.entity().getMaxOutputTokens() != null) {
            entry.put("max_output_tokens", view.entity().getMaxOutputTokens());
        }
        List<String> modalities = splitCsv(view.entity().getInputModalities());
        if (!modalities.isEmpty()) {
            entry.put("input_modalities", modalities);
        }
        if (StringUtils.hasText(view.entity().getSystemPromptUpdate())) {
            entry.put("system_prompt_update", view.entity().getSystemPromptUpdate());
        }
        List<String> efforts = splitCsv(view.entity().getReasoningEfforts());
        if (!efforts.isEmpty()) {
            Map<String, Object> reasoning = new LinkedHashMap<>();
            reasoning.put("efforts", efforts);
            if (StringUtils.hasText(view.entity().getDefaultReasoningEffort())) {
                reasoning.put("default_effort", view.entity().getDefaultReasoningEffort());
            }
            entry.put("reasoning", reasoning);
        }
        if (StringUtils.hasText(view.entity().getClientRequirements())) {
            try {
                entry.put("client_requirements", MAPPER.readTree(view.entity().getClientRequirements()));
            } catch (Exception e) {
                log.warn("[网关目录] model_id={} 的 client_requirements 非合法 JSON，已忽略", view.modelId());
            }
        }
        return entry;
    }

    private static List<String> splitCsv(String raw) {
        List<String> values = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return values;
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }
}

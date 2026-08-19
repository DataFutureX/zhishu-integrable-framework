package com.datafuturex.assistant.agent.support;

import com.datafuturex.assistant.agent.enums.AgentCapability;
import com.datafuturex.assistant.shared.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AgentJsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentJsonUtils() {
    }

    public static List<String> parseCapabilities(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> raw = MAPPER.readValue(json, new TypeReference<>() {
            });
            Set<String> normalized = new LinkedHashSet<>();
            for (String item : raw) {
                AgentCapability.fromCode(item).ifPresent(cap -> normalized.add(cap.name()));
            }
            return new ArrayList<>(normalized);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("capabilities JSON 无效: " + e.getMessage());
        }
    }

    public static String toCapabilitiesJson(List<String> capabilities) {
        List<String> normalized = normalizeCapabilities(capabilities);
        try {
            return MAPPER.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new BusinessException("序列化 capabilities 失败: " + e.getMessage());
        }
    }

    public static List<String> normalizeCapabilities(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            throw new BusinessException("至少勾选一项能力");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String item : capabilities) {
            AgentCapability cap = AgentCapability.fromCode(item)
                    .orElseThrow(() -> new BusinessException("未知能力: " + item));
            normalized.add(cap.name());
        }
        return new ArrayList<>(normalized);
    }

    public static boolean hasCapability(List<String> capabilities, AgentCapability capability) {
        return capabilities != null && capabilities.stream()
                .anyMatch(c -> capability.name().equalsIgnoreCase(c));
    }

    public static List<Long> parseDocumentIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Long> ids = MAPPER.readValue(json, new TypeReference<>() {
            });
            return ids == null ? List.of() : ids.stream().filter(id -> id != null && id > 0).distinct().toList();
        } catch (Exception e) {
            throw new BusinessException("document_ids JSON 无效: " + e.getMessage());
        }
    }

    public static String toDocumentIdsJson(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(documentIds.stream()
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .toList());
        } catch (Exception e) {
            throw new BusinessException("序列化 document_ids 失败: " + e.getMessage());
        }
    }
}

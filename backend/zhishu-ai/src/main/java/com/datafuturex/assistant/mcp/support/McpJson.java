package com.datafuturex.assistant.mcp.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class McpJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpJson() {
    }

    public static List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> raw = MAPPER.readValue(json, new TypeReference<>() {
            });
            LinkedHashSet<String> set = new LinkedHashSet<>();
            if (raw != null) {
                for (String item : raw) {
                    if (StringUtils.hasText(item)) {
                        set.add(item.trim());
                    }
                }
            }
            return new ArrayList<>(set);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String toJson(List<String> values) {
        try {
            return MAPPER.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            return "[]";
        }
    }
}

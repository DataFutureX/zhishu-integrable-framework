package cn.datafuturex.zhishu.ai.kg.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.Map;

public record KgNodeVO(
        String id,
        String label,
        @JsonSerialize(using = ToStringSerializer.class)
        Long bizId,
        String name,
        @JsonSerialize(using = ToStringSerializer.class)
        Long projectId,
        Map<String, Object> properties
) {
}

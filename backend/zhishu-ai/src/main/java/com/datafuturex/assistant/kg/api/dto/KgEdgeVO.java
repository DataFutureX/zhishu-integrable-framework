package com.datafuturex.assistant.kg.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record KgEdgeVO(
        String id,
        String type,
        String source,
        String target,
        @JsonSerialize(using = ToStringSerializer.class)
        Long projectId
) {
}

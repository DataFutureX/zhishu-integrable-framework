package cn.datafuturex.zhishu.ai.kg.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record KgSearchHit(
        String label,
        @JsonSerialize(using = ToStringSerializer.class)
        Long bizId,
        String name,
        @JsonSerialize(using = ToStringSerializer.class)
        Long projectId,
        String code
) {
}

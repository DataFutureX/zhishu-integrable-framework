package com.datafuturex.assistant.agent.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "能力目录项")
public record CapabilityVO(
        String code,
        String label,
        String description,
        boolean toolBased,
        List<String> toolNames,
        @Schema(description = "绑定的 Tools（含描述）") List<ToolInfoVO> tools
) {
}

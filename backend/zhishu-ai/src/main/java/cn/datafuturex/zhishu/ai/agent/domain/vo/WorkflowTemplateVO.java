package cn.datafuturex.zhishu.ai.agent.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "工作流模板说明")
public record WorkflowTemplateVO(
        String code,
        String label,
        String description
) {
}

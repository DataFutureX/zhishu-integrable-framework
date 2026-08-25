package cn.datafuturex.zhishu.ai.agent.domain.dto;

import cn.datafuturex.zhishu.ai.agent.graph.GraphEdge;
import cn.datafuturex.zhishu.ai.agent.graph.GraphNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "保存 Graph")
public record GraphSaveDTO(
        @NotNull
        Integer version,
        @NotEmpty
        List<GraphNode> nodes,
        @NotNull
        List<GraphEdge> edges
) {
}

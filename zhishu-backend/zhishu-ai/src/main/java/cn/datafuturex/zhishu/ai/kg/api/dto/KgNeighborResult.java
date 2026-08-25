package cn.datafuturex.zhishu.ai.kg.api.dto;

public record KgNeighborResult(
        boolean found,
        String message,
        KgSubgraphVO subgraph
) {
}

package com.datafuturex.assistant.kg.api.dto;

public record KgNeighborResult(
        boolean found,
        String message,
        KgSubgraphVO subgraph
) {
}

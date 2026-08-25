package cn.datafuturex.zhishu.ai.kg.api.dto;

import java.util.List;

public record KgSubgraphVO(
        List<KgNodeVO> nodes,
        List<KgEdgeVO> edges
) {
}

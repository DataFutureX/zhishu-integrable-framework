package cn.datafuturex.zhishu.ai.kg.api.dto;

import java.util.List;

public record KgPathResult(
        boolean found,
        String message,
        List<KgNodeVO> nodes,
        List<KgEdgeVO> edges
) {
}

package cn.datafuturex.zhishu.ai.kg.api.dto;

import java.util.List;
import java.util.Map;

/**
 * 万象推送的图谱 upsert 载荷（知枢不再扫描万象业务表）。
 */
public record KgUpsertRequest(
        Boolean full,
        List<Map<String, Object>> projects,
        List<Map<String, Object>> terminals,
        List<Map<String, Object>> alerts
) {
}

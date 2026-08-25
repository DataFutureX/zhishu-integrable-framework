package cn.datafuturex.zhishu.openapi.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱推送请求。
 *
 * @param dryRun  是否试运行（不实际入库）
 * @param nodes   节点列表
 * @param edges   边列表
 * @param options 额外选项
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KgUpsertRequest(
        Boolean dryRun,
        List<Map<String, Object>> nodes,
        List<Map<String, Object>> edges,
        Map<String, Object> options
) {
}

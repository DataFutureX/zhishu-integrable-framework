package cn.datafuturex.zhishu.ai.biztools.api;

/**
 * 将知识图谱实体提示拼入用户消息（与文档 Hybrid RAG 互补）。
 */
public interface GraphContextEnrichPort {

    String GRAPH_CONTEXT_HEADER = "【知识图谱实体提示】";

    /**
     * 若图谱可用则追加相关实体摘要；失败时原样返回。
     */
    String enrich(String message);
}

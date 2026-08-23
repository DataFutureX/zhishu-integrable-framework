package cn.datafuturex.zhishu.ai.knowledge.api;

import java.util.List;

/**
 * 混合检索：向量召回 + 文档关键词补充。
 * <p>
 * Agent 路径与文档 QA 共用同一上下文标记与拼装约定，避免两套 RAG 话术不一致。
 */
public interface HybridRetrievalPort {

    /** 与文档 QA / Agent 共用的知识库上下文标题 */
    String KNOWLEDGE_CONTEXT_HEADER = "【知识库检索片段】";

    /**
     * 基于关键字从 documents 表补充上下文文本
     */
    String buildHybridContext(String question, int limit);

    /**
     * 按文档 ID 过滤的混合检索；documentIds 为空表示不限制。
     */
    String buildHybridContext(String question, int limit, List<Long> documentIds);

    /**
     * 将检索片段拼入用户消息（统一标记 {@link #KNOWLEDGE_CONTEXT_HEADER}）。
     * 无命中时追加提示语。
     */
    default String enrichUserMessage(String message, int limit, List<Long> documentIds) {
        String hybrid = buildHybridContext(message, limit, documentIds);
        if (hybrid == null || hybrid.isBlank()) {
            return message + "\n\n【提示】已开启知识库增强，但未检索到相关片段。";
        }
        return message + "\n\n" + KNOWLEDGE_CONTEXT_HEADER + "\n" + hybrid;
    }
}

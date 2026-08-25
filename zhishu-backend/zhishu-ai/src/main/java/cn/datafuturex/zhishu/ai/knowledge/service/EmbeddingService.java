package cn.datafuturex.zhishu.ai.knowledge.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 嵌入服务接口 - 负责文本向量化
 * 
 * @author Qoder
 * @since 1.0.0
 */
public interface EmbeddingService {

    /**
     * 将文档列表转换为向量并存储
     *
     * @param documents Spring AI Document列表
     */
    void embedAndStore(List<Document> documents);

    /**
     * 删除指定ID的向量
     *
     * @param documentIds 文档ID列表
     */
    void deleteEmbeddings(List<String> documentIds);

    /**
     * 相似性搜索
     *
     * @param query 查询文本
     * @param topK  返回最相关的K个结果
     * @return 相关文档列表
     */
    List<Document> similaritySearch(String query, int topK);

    /**
     * 相似性搜索（带过滤条件）
     *
     * @param query            查询文本
     * @param filterExpression 过滤表达式
     * @param topK             返回最相关的K个结果
     * @return 相关文档列表
     */
    List<Document> similaritySearchWithFilter(String query, String filterExpression, int topK);

    /**
     * 默认相似性搜索
     *
     * @param query 查询文本
     * @return 相关文档列表
     */
    List<Document> similaritySearch(String query);
}

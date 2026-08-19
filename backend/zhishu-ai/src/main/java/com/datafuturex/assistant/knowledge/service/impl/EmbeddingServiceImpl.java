package com.datafuturex.assistant.knowledge.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.datafuturex.assistant.shared.exception.BusinessException;
import com.datafuturex.assistant.knowledge.service.EmbeddingService;

import java.util.List;

/**
 * 嵌入服务实现类 - 负责文本向量化和向量存储
 * 
 * @author Qoder
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingServiceImpl implements EmbeddingService {

    private final VectorStore vectorStore;

    /**
     * 将文档列表转换为向量并存储
     *
     * @param documents Spring AI Document列表
     */
    @Override
    public void embedAndStore(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new BusinessException("文档列表不能为空");
        }

        try {
            // 阿里云 Embedding API 限制：批量大小不能超过 10
            int batchSize = 10;
            int totalSize = documents.size();
            int processedCount = 0;

            log.info("开始向量化存储，总文档数: {}，批次大小: {}", totalSize, batchSize);

            for (int i = 0; i < totalSize; i += batchSize) {
                int endIndex = Math.min(i + batchSize, totalSize);
                List<Document> batch = documents.subList(i, endIndex);

                vectorStore.add(batch);
                processedCount += batch.size();

                log.info("批次处理进度: {}/{} (本批次: {} 个)",
                        processedCount, totalSize, batch.size());
            }

            log.info("成功向量化并存储 {} 个文档片段", processedCount);
        } catch (Exception e) {
            log.error("向量化存储失败: {}", e.getMessage(), e);
            throw new BusinessException("向量化存储失败: " + e.getMessage());
        }
    }

    /**
     * 删除指定ID的向量
     *
     * @param documentIds 文档ID列表
     */
    @Override
    public void deleteEmbeddings(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            throw new BusinessException("文档ID列表不能为空");
        }

        try {
            vectorStore.delete(documentIds);
            log.info("成功删除 {} 个文档的向量数据", documentIds.size());
        } catch (Exception e) {
            log.error("删除向量数据失败: {}", e.getMessage(), e);
            throw new BusinessException("删除向量数据失败: " + e.getMessage());
        }
    }

    /**
     * 相似性搜索
     *
     * @param query 查询文本
     * @param topK  返回最相关的K个结果
     * @return 相关文档列表
     */
    @Override
    public List<Document> similaritySearch(String query, int topK) {
        if (query == null || query.isEmpty()) {
            throw new BusinessException("查询文本不能为空");
        }

        if (topK <= 0) {
            throw new BusinessException("topK必须大于0");
        }

        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .build();

            List<Document> results = vectorStore.similaritySearch(searchRequest);
            log.info("相似性搜索完成: 查询={}, 返回结果数={}", query, results.size());
            return results;
        } catch (Exception e) {
            log.error("相似性搜索失败: {}", e.getMessage(), e);
            throw new BusinessException("向量检索失败: " + e.getMessage());
        }
    }

    /**
     * 相似性搜索（带过滤条件）
     *
     * @param query            查询文本
     * @param filterExpression 过滤表达式
     * @param topK             返回最相关的K个结果
     * @return 相关文档列表
     */
    @Override
    public List<Document> similaritySearchWithFilter(String query, String filterExpression, int topK) {
        if (query == null || query.isEmpty()) {
            throw new BusinessException("查询文本不能为空");
        }

        if (filterExpression == null || filterExpression.isEmpty()) {
            throw new BusinessException("过滤表达式不能为空");
        }

        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .filterExpression(filterExpression)
                    .build();

            List<Document> results = vectorStore.similaritySearch(searchRequest);
            log.info("带过滤的相似性搜索完成: 查询={}, 过滤={}, 返回结果数={}",
                    query, filterExpression, results.size());
            return results;
        } catch (Exception e) {
            log.error("带过滤的相似性搜索失败: {}", e.getMessage(), e);
            throw new BusinessException("向量检索失败: " + e.getMessage());
        }
    }

    /**
     * 默认相似性搜索
     *
     * @param query 查询文本
     * @return 相关文档列表
     */
    @Override
    public List<Document> similaritySearch(String query) {
        return similaritySearch(query, 5);
    }
}

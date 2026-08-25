package cn.datafuturex.zhishu.ai.knowledge.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档切片服务接口
 * 
 * @author Qoder
 * @since 1.0.0
 */
public interface DocumentChunkService {

    /**
     * 将文本切分为多个片段
     *
     * @param text      原始文本
     * @param chunkSize 每个片段的大小（字符数）
     * @param overlap   片段之间的重叠字符数
     * @return 切分后的文本片段列表
     */
    List<String> chunkText(String text, int chunkSize, int overlap);

    /**
     * 将文本转换为Spring AI Document对象列表
     *
     * @param text       原始文本
     * @param documentId 文档ID
     * @param fileName   文件名
     * @param chunkSize  每个片段的大小
     * @param overlap    片段之间的重叠字符数
     * @return Spring AI Document对象列表
     */
    List<Document> convertToAiDocuments(String text, Long documentId, String fileName,
            Long categoryId, int chunkSize, int overlap);

    /**
     * 默认切片（使用默认参数）
     *
     * @param text 原始文本
     * @return 切分后的文本片段列表
     */
    List<String> chunkText(String text);
}

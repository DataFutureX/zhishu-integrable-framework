package com.datafuturex.assistant.knowledge.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import com.datafuturex.assistant.shared.exception.BusinessException;
import com.datafuturex.assistant.knowledge.service.DocumentChunkService;

import java.util.*;

/**
 * 文档切片服务实现类
 * 
 * @author Qoder
 * @since 1.0.0
 */
@Service
@Slf4j
public class DocumentChunkServiceImpl implements DocumentChunkService {

    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_OVERLAP = 200;

    /**
     * 将文本切分为多个片段
     *
     * @param text      原始文本
     * @param chunkSize 每个片段的大小（字符数）
     * @param overlap   片段之间的重叠字符数
     * @return 切分后的文本片段列表
     */
    @Override
    public List<String> chunkText(String text, int chunkSize, int overlap) {
        if (text == null || text.isEmpty()) {
            throw new BusinessException("文本内容不能为空");
        }

        if (chunkSize <= 0) {
            throw new BusinessException("切片大小必须大于0");
        }

        if (overlap < 0 || overlap >= chunkSize) {
            throw new BusinessException("重叠大小必须在0和切片大小之间");
        }

        // 安全检查：限制最大文本长度（防止内存溢出）
        int maxLength = 10_000_000; // 10MB
        if (text.length() > maxLength) {
            log.warn("文本过长: {} 字符，截断为 {} 字符", text.length(), maxLength);
            text = text.substring(0, maxLength);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        int textLength = text.length();
        int maxChunks = 10000; // 最多 10000 个片段
        int chunkCount = 0;

        while (start < textLength && chunkCount < maxChunks) {
            // 计算结束位置
            int end = Math.min(start + chunkSize, textLength);

            // 如果不是最后一个片段，尝试在句子边界处切割
            if (end < textLength) {
                // 查找最近的句子结束符
                int sentenceEnd = findSentenceBoundary(text, start, end);
                if (sentenceEnd > start) {
                    end = sentenceEnd;
                }
            }

            // 提取片段并去除首尾空白
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
                chunkCount++;
            }

            // 移动起始位置，考虑重叠（修复死循环bug）
            int nextStart = end - overlap;
            if (nextStart <= start) {
                // 确保至少前进一个字符，避免死循环
                start = end;
            } else {
                start = nextStart;
            }
        }

        if (chunkCount >= maxChunks) {
            log.warn("达到最大切片数量限制: {}, 原文长度: {}", maxChunks, textLength);
        }

        log.info("文本切片完成: 原文长度={}, 切片数量={}, 切片大小={}, 重叠={}",
                textLength, chunks.size(), chunkSize, overlap);
        return chunks;
    }

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
    @Override
    public List<Document> convertToAiDocuments(String text, Long documentId, String fileName,
            Long categoryId, int chunkSize, int overlap) {
        List<String> chunks = chunkText(text, chunkSize, overlap);
        List<Document> aiDocuments = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("documentId", documentId);
            metadata.put("fileName", fileName);
            if (categoryId != null) {
                metadata.put("categoryId", categoryId);
            }
            metadata.put("chunkIndex", i);
            metadata.put("totalChunks", chunks.size());

            Document aiDoc = new Document(chunks.get(i), metadata);
            aiDocuments.add(aiDoc);
        }

        log.info("转换为AI Document完成: 文档ID={}, 文件名={}, 片段数量={}",
                documentId, fileName, aiDocuments.size());
        return aiDocuments;
    }

    /**
     * 默认切片（使用默认参数）
     *
     * @param text 原始文本
     * @return 切分后的文本片段列表
     */
    @Override
    public List<String> chunkText(String text) {
        return chunkText(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * 查找句子边界
     *
     * @param text  文本
     * @param start 起始位置
     * @param end   结束位置
     * @return 句子边界位置，如果未找到返回-1
     */
    private int findSentenceBoundary(String text, int start, int end) {
        // 从后往前查找句子结束符
        for (int i = end - 1; i >= start; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                return i + 1;
            }
        }

        // 如果没找到句子边界，尝试在空格或换行处切割
        for (int i = end - 1; i >= start; i--) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r') {
                return i + 1;
            }
        }

        return -1;
    }
}

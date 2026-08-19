package com.datafuturex.assistant.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datafuturex.assistant.knowledge.domain.Document;
import com.datafuturex.assistant.knowledge.mapper.DocumentMapper;
import com.datafuturex.assistant.knowledge.service.EmbeddingService;
import com.datafuturex.assistant.knowledge.api.HybridRetrievalPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hybrid：向量相似度 + documents.content / file_name 关键词
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridRetrievalServiceImpl implements HybridRetrievalPort {

    private final DocumentMapper documentMapper;
    private final EmbeddingService embeddingService;

    @Override
    public String buildHybridContext(String question, int limit) {
        return buildHybridContext(question, limit, null);
    }

    @Override
    public String buildHybridContext(String question, int limit, List<Long> documentIds) {
        if (!StringUtils.hasText(question)) {
            return "";
        }
        int lim = Math.max(1, Math.min(limit, 10));
        Set<String> snippets = new LinkedHashSet<>();
        boolean filterDocs = documentIds != null && !documentIds.isEmpty();

        try {
            List<org.springframework.ai.document.Document> vectorHits;
            if (filterDocs) {
                String filterExpression = documentIds.stream()
                        .map(id -> "documentId == '" + id + "'")
                        .collect(Collectors.joining(" || "));
                vectorHits = embeddingService.similaritySearchWithFilter(question, filterExpression, lim);
            } else {
                vectorHits = embeddingService.similaritySearch(question, lim);
            }
            for (org.springframework.ai.document.Document doc : vectorHits) {
                if (StringUtils.hasText(doc.getText())) {
                    snippets.add(trim(doc.getText(), 500));
                }
            }
        } catch (Exception e) {
            log.warn("向量检索补充失败: {}", e.getMessage());
        }

        String keyword = extractKeyword(question);
        if (StringUtils.hasText(keyword)) {
            try {
                LambdaQueryWrapper<Document> qw = new LambdaQueryWrapper<Document>()
                        .and(w -> w.like(Document::getFileName, keyword)
                                .or()
                                .like(Document::getContent, keyword))
                        .eq(Document::getProcessed, true);
                if (filterDocs) {
                    qw.in(Document::getId, documentIds);
                }
                qw.last("LIMIT " + lim);
                List<Document> rows = documentMapper.selectList(qw);
                for (Document row : rows) {
                    String text = StringUtils.hasText(row.getContent()) ? row.getContent() : row.getFileName();
                    snippets.add("[" + row.getFileName() + "] " + trim(text, 400));
                }
            } catch (Exception e) {
                log.warn("关键词检索失败: {}", e.getMessage());
            }
        }

        if (snippets.isEmpty()) {
            return "";
        }
        return snippets.stream().limit(lim).collect(Collectors.joining("\n---\n"));
    }

    private static String extractKeyword(String question) {
        String cleaned = question.replaceAll("[\\p{Punct}\\s]+", " ").trim();
        if (cleaned.length() <= 2) {
            return cleaned;
        }
        String[] parts = cleaned.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String p : parts) {
            if (p.length() >= 2) {
                tokens.add(p);
            }
        }
        if (tokens.isEmpty()) {
            return cleaned.substring(0, Math.min(8, cleaned.length()));
        }
        return tokens.get(tokens.size() - 1);
    }

    private static String trim(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.strip();
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }
}

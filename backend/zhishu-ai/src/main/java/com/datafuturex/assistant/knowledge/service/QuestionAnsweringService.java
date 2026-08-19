package com.datafuturex.assistant.knowledge.service;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 问答服务接口 - 负责基于文档的智能问答
 */
public interface QuestionAnsweringService {

    String answerFromAllDocuments(String question, Integer topK, String conversationId);

    String answerFromDocument(String question, Long documentId, Integer topK, String conversationId);

    /** 在给定文档 ID 集合内检索（用于按知识库过滤） */
    String answerFromDocuments(String question, List<Long> documentIds, Integer topK, String conversationId);

    Flux<ServerSentEvent<String>> streamFromAllDocuments(String question, Integer topK, String conversationId);

    Flux<ServerSentEvent<String>> streamFromDocument(String question, Long documentId, Integer topK,
                                                     String conversationId);

    Flux<ServerSentEvent<String>> streamFromDocuments(String question, List<Long> documentIds, Integer topK,
                                                      String conversationId);
}

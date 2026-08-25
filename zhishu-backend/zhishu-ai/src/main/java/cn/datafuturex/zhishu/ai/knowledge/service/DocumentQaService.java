package cn.datafuturex.zhishu.ai.knowledge.service;

import cn.datafuturex.zhishu.ai.knowledge.dto.DocumentQueryDTO;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 文档问答服务
 */
public interface DocumentQaService {

    /** @return [answer, conversationId] */
    String[] answerQuestion(DocumentQueryDTO queryDTO);

    String answerFromAllDocuments(String question, Integer topK);

    String answerFromDocument(String question, Long documentId, Integer topK);

    Flux<ServerSentEvent<String>> streamAnswerQuestion(DocumentQueryDTO queryDTO);
}

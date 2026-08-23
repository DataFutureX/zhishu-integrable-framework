package cn.datafuturex.zhishu.ai.knowledge.service.impl;

import cn.datafuturex.zhishu.ai.knowledge.dto.DocumentQueryDTO;
import cn.datafuturex.zhishu.ai.knowledge.vo.DocumentVO;
import cn.datafuturex.zhishu.ai.shared.exception.BusinessException;
import cn.datafuturex.zhishu.ai.knowledge.service.KnowledgesCategoryService;
import cn.datafuturex.zhishu.ai.knowledge.service.DocumentManagementService;
import cn.datafuturex.zhishu.ai.knowledge.service.DocumentQaService;
import cn.datafuturex.zhishu.ai.knowledge.service.QuestionAnsweringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * 文档问答服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentQaServiceImpl implements DocumentQaService {

    private final QuestionAnsweringService qaService;
    private final DocumentManagementService documentManagementService;
    private final KnowledgesCategoryService knowledgesCategoryService;

    @Override
    public String[] answerQuestion(DocumentQueryDTO queryDTO) {
        String conversationId = resolveConversationId(queryDTO.conversationId());
        int topK = queryDTO.topK() != null ? queryDTO.topK() : 5;
        String answer = dispatchAnswer(queryDTO, topK, conversationId);
        return new String[]{answer, conversationId};
    }

    @Override
    public String answerFromAllDocuments(String question, Integer topK) {
        return qaService.answerFromAllDocuments(question, topK, UUID.randomUUID().toString());
    }

    @Override
    public String answerFromDocument(String question, Long documentId, Integer topK) {
        return qaService.answerFromDocument(question, documentId, topK, UUID.randomUUID().toString());
    }

    @Override
    public Flux<ServerSentEvent<String>> streamAnswerQuestion(DocumentQueryDTO queryDTO) {
        String conversationId = resolveConversationId(queryDTO.conversationId());
        int topK = queryDTO.topK() != null ? queryDTO.topK() : 5;
        return dispatchStream(queryDTO, topK, conversationId);
    }

    private String dispatchAnswer(DocumentQueryDTO queryDTO, int topK, String conversationId) {
        if (queryDTO.documentId() != null) {
            assertDocumentInCategory(queryDTO.documentId(), queryDTO.categoryId());
            return qaService.answerFromDocument(queryDTO.question(), queryDTO.documentId(), topK, conversationId);
        }
        if (queryDTO.categoryId() != null) {
            knowledgesCategoryService.requireEnabledCategoryId(queryDTO.categoryId());
            List<Long> ids = documentManagementService.listDocumentIdsByCategory(queryDTO.categoryId());
            return qaService.answerFromDocuments(queryDTO.question(), ids, topK, conversationId);
        }
        return qaService.answerFromAllDocuments(queryDTO.question(), topK, conversationId);
    }

    private Flux<ServerSentEvent<String>> dispatchStream(DocumentQueryDTO queryDTO, int topK, String conversationId) {
        if (queryDTO.documentId() != null) {
            assertDocumentInCategory(queryDTO.documentId(), queryDTO.categoryId());
            return qaService.streamFromDocument(queryDTO.question(), queryDTO.documentId(), topK, conversationId);
        }
        if (queryDTO.categoryId() != null) {
            knowledgesCategoryService.requireEnabledCategoryId(queryDTO.categoryId());
            List<Long> ids = documentManagementService.listDocumentIdsByCategory(queryDTO.categoryId());
            return qaService.streamFromDocuments(queryDTO.question(), ids, topK, conversationId);
        }
        return qaService.streamFromAllDocuments(queryDTO.question(), topK, conversationId);
    }

    private void assertDocumentInCategory(Long documentId, Long categoryId) {
        if (categoryId == null) {
            return;
        }
        DocumentVO doc = documentManagementService.getDocumentById(documentId);
        if (doc.categoryId() == null || !String.valueOf(categoryId).equals(doc.categoryId())) {
            throw new BusinessException("所选文档不属于该知识库分类");
        }
    }

    private String resolveConversationId(String conversationId) {
        return StringUtils.hasText(conversationId) ? conversationId.trim() : UUID.randomUUID().toString();
    }
}

package cn.datafuturex.zhishu.ai.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.datafuturex.zhishu.ai.shared.UserContext;
import cn.datafuturex.zhishu.ai.shared.chat.ChatScenes;
import cn.datafuturex.zhishu.ai.chat.domain.QaHistory;
import cn.datafuturex.zhishu.ai.chat.vo.QaHistoryVO;
import cn.datafuturex.zhishu.ai.chat.mapper.QaHistoryMapper;
import cn.datafuturex.zhishu.ai.chat.service.ChatSessionService;
import cn.datafuturex.zhishu.ai.chat.service.QaHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class QaHistoryServiceImpl implements QaHistoryService {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 500;

    private final QaHistoryMapper qaHistoryMapper;
    private final ChatSessionService chatSessionService;

    public QaHistoryServiceImpl(QaHistoryMapper qaHistoryMapper, @Lazy ChatSessionService chatSessionService) {
        this.qaHistoryMapper = qaHistoryMapper;
        this.chatSessionService = chatSessionService;
    }

    @Override
    public void save(String scene, String question, String answer, String model, Long documentId) {
        save(UserContext.getUserId(), scene, question, answer, model, documentId, null);
    }

    @Override
    public void save(String userId, String scene, String question, String answer, String model, Long documentId) {
        save(userId, scene, question, answer, model, documentId, null);
    }

    @Override
    public void save(String scene, String question, String answer, String model, Long documentId,
                     String conversationId) {
        save(UserContext.getUserId(), scene, question, answer, model, documentId, conversationId);
    }

    @Override
    public void save(String userId, String scene, String question, String answer, String model, Long documentId,
                     String conversationId) {
        save(userId, scene, question, answer, model, documentId, conversationId, null);
    }

    @Override
    public void save(String userId, String scene, String question, String answer, String model, Long documentId,
                     String conversationId, Long agentId) {
        if (!StringUtils.hasText(userId)) {
            log.warn("跳过问答历史保存：缺少用户标识(X-User-Id)");
            return;
        }
        if (!StringUtils.hasText(question) || !StringUtils.hasText(answer)) {
            return;
        }

        QaHistory entity = new QaHistory();
        entity.setUserId(userId.trim());
        entity.setScene(normalizeScene(scene));
        entity.setQuestion(question);
        entity.setAnswer(answer);
        entity.setModel(model);
        entity.setDocumentId(documentId);
        entity.setConversationId(conversationId);
        entity.setAgentId(agentId);
        entity.setCreateTime(LocalDateTime.now());

        qaHistoryMapper.insert(entity);
        if (StringUtils.hasText(conversationId)) {
            try {
                chatSessionService.touchOnMessage(
                        userId.trim(), entity.getScene(), conversationId, question, agentId);
            } catch (Exception e) {
                log.warn("同步会话元数据失败: conversationId={}, err={}", conversationId, e.getMessage());
            }
        }
        log.debug("已保存问答历史: userId={}, scene={}, conversationId={}, agentId={}, id={}",
                userId, entity.getScene(), conversationId, agentId, entity.getId());
    }

    @Override
    public List<QaHistoryVO> listCurrentUser(String scene, Integer limit) {
        String userId = UserContext.getUserId();
        if (!StringUtils.hasText(userId)) {
            return Collections.emptyList();
        }

        int size = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        String normalizedScene = normalizeScene(scene);

        List<QaHistory> rows = qaHistoryMapper.selectList(
                new LambdaQueryWrapper<QaHistory>()
                        .eq(QaHistory::getUserId, userId.trim())
                        .eq(QaHistory::getScene, normalizedScene)
                        .orderByDesc(QaHistory::getCreateTime)
                        .last("LIMIT " + size));

        Collections.reverse(rows);
        return rows.stream().map(this::toVo).toList();
    }

    @Override
    public List<QaHistoryVO> listLatestForPortal(String scene, Integer limit) {
        int size = limit == null ? 2 : Math.min(Math.max(limit, 1), 10);
        String normalizedScene = normalizeScene(scene);
        List<QaHistory> rows = qaHistoryMapper.selectList(
                new LambdaQueryWrapper<QaHistory>()
                        .eq(QaHistory::getScene, normalizedScene)
                        .orderByDesc(QaHistory::getCreateTime)
                        .last("LIMIT " + size));
        Collections.reverse(rows);
        return rows.stream().map(this::toVo).toList();
    }

    @Override
    public void clearCurrentUser(String scene) {
        String userId = UserContext.getUserId();
        if (!StringUtils.hasText(userId)) {
            return;
        }
        String normalizedScene = normalizeScene(scene);
        qaHistoryMapper.delete(
                new LambdaQueryWrapper<QaHistory>()
                        .eq(QaHistory::getUserId, userId.trim())
                        .eq(QaHistory::getScene, normalizedScene));
        try {
            chatSessionService.clearCurrentUser(normalizedScene);
        } catch (Exception e) {
            log.warn("清空会话列表失败: {}", e.getMessage());
        }
    }

    private String normalizeScene(String scene) {
        if (!StringUtils.hasText(scene)) {
            return ChatScenes.CHAT;
        }
        String value = scene.trim().toUpperCase();
        if (ChatScenes.DOCUMENT_QA.equals(value)) {
            return ChatScenes.DOCUMENT_QA;
        }
        return ChatScenes.CHAT;
    }

    private QaHistoryVO toVo(QaHistory entity) {
        return new QaHistoryVO(
                entity.getId() == null ? null : String.valueOf(entity.getId()),
                entity.getScene(),
                entity.getQuestion(),
                entity.getAnswer(),
                entity.getModel(),
                entity.getDocumentId() == null ? null : String.valueOf(entity.getDocumentId()),
                entity.getConversationId(),
                entity.getCreateTime());
    }
}

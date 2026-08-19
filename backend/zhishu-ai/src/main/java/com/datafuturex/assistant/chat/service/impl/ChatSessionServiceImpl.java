package com.datafuturex.assistant.chat.service.impl;

import com.datafuturex.assistant.shared.chat.ChatScenes;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datafuturex.assistant.shared.UserContext;
import com.datafuturex.assistant.chat.domain.ChatSession;
import com.datafuturex.assistant.chat.domain.QaHistory;
import com.datafuturex.assistant.chat.dto.ChatSessionCreateDTO;
import com.datafuturex.assistant.chat.dto.ChatSessionTitleDTO;
import com.datafuturex.assistant.chat.vo.ChatSessionVO;
import com.datafuturex.assistant.chat.vo.QaHistoryVO;
import com.datafuturex.assistant.chat.mapper.ChatSessionMapper;
import com.datafuturex.assistant.chat.mapper.QaHistoryMapper;
import com.datafuturex.assistant.chat.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSessionServiceImpl implements ChatSessionService {

    private static final int DEFAULT_MSG_LIMIT = 200;
    private static final int MAX_MSG_LIMIT = 500;
    private static final int TITLE_MAX = 40;

    private final ChatSessionMapper chatSessionMapper;
    private final QaHistoryMapper qaHistoryMapper;
    private final ChatMemory chatMemory;

    @Override
    public List<ChatSessionVO> listCurrentUser(String scene) {
        String userId = requireUserId();
        if (userId == null) {
            return Collections.emptyList();
        }
        String normalizedScene = normalizeScene(scene);
        List<ChatSession> rows = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getScene, normalizedScene)
                        .orderByDesc(ChatSession::getUpdateTime)
                        .orderByDesc(ChatSession::getCreateTime));
        return rows.stream().map(this::toVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionVO create(ChatSessionCreateDTO dto) {
        String userId = requireUserId();
        if (userId == null) {
            throw new IllegalStateException("缺少用户标识(X-User-Id)");
        }
        LocalDateTime now = LocalDateTime.now();
        ChatSession entity = new ChatSession();
        entity.setConversationId(UUID.randomUUID().toString().replace("-", ""));
        entity.setUserId(userId);
        entity.setScene(normalizeScene(dto == null ? null : dto.scene()));
        entity.setTitle(normalizeTitle(dto == null ? null : dto.title(), "新会话"));
        entity.setAgentId(dto == null ? null : dto.agentId());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        chatSessionMapper.insert(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionVO rename(String conversationId, ChatSessionTitleDTO dto) {
        ChatSession entity = requireOwnedSession(conversationId);
        entity.setTitle(normalizeTitle(dto.title(), entity.getTitle()));
        entity.setUpdateTime(LocalDateTime.now());
        chatSessionMapper.updateById(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String conversationId) {
        ChatSession entity = requireOwnedSession(conversationId);
        qaHistoryMapper.delete(
                new LambdaQueryWrapper<QaHistory>()
                        .eq(QaHistory::getUserId, entity.getUserId())
                        .eq(QaHistory::getConversationId, entity.getConversationId()));
        chatMemory.clear(entity.getConversationId());
        chatSessionMapper.deleteById(entity.getId());
    }

    @Override
    public List<QaHistoryVO> listMessages(String conversationId, Integer limit) {
        ChatSession entity = requireOwnedSession(conversationId);
        int size = limit == null ? DEFAULT_MSG_LIMIT : Math.min(Math.max(limit, 1), MAX_MSG_LIMIT);
        List<QaHistory> rows = qaHistoryMapper.selectList(
                new LambdaQueryWrapper<QaHistory>()
                        .eq(QaHistory::getUserId, entity.getUserId())
                        .eq(QaHistory::getConversationId, entity.getConversationId())
                        .orderByAsc(QaHistory::getCreateTime)
                        .last("LIMIT " + size));
        return rows.stream().map(this::toHistoryVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void truncateAfterTurns(String conversationId, int keepUserTurns) {
        ChatSession entity = requireOwnedSession(conversationId);
        int keep = Math.max(keepUserTurns, 0);
        List<QaHistory> rows = qaHistoryMapper.selectList(
                new LambdaQueryWrapper<QaHistory>()
                        .eq(QaHistory::getUserId, entity.getUserId())
                        .eq(QaHistory::getConversationId, entity.getConversationId())
                        .orderByAsc(QaHistory::getCreateTime)
                        .orderByAsc(QaHistory::getId));
        if (keep < rows.size()) {
            List<Long> removeIds = rows.subList(keep, rows.size()).stream()
                    .map(QaHistory::getId)
                    .filter(id -> id != null)
                    .toList();
            if (!removeIds.isEmpty()) {
                qaHistoryMapper.delete(
                        new LambdaQueryWrapper<QaHistory>().in(QaHistory::getId, removeIds));
            }
            rows = rows.subList(0, keep);
        }
        rebuildMemory(entity.getConversationId(), rows);
        entity.setUpdateTime(LocalDateTime.now());
        chatSessionMapper.updateById(entity);
    }

    private void rebuildMemory(String conversationId, List<QaHistory> kept) {
        chatMemory.clear(conversationId);
        if (kept == null || kept.isEmpty()) {
            return;
        }
        List<Message> restored = new ArrayList<>();
        for (QaHistory row : kept) {
            if (StringUtils.hasText(row.getQuestion())) {
                restored.add(new UserMessage(row.getQuestion()));
            }
            if (StringUtils.hasText(row.getAnswer())) {
                restored.add(new AssistantMessage(row.getAnswer()));
            }
        }
        if (!restored.isEmpty()) {
            chatMemory.add(conversationId, restored);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void touchOnMessage(String userId, String scene, String conversationId, String question, Long agentId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(conversationId)) {
            return;
        }
        String cid = conversationId.trim();
        ChatSession existing = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getConversationId, cid)
                        .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            ChatSession entity = new ChatSession();
            entity.setConversationId(cid);
            entity.setUserId(userId.trim());
            entity.setScene(normalizeScene(scene));
            entity.setTitle(normalizeTitle(question, "新会话"));
            entity.setAgentId(agentId);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            try {
                chatSessionMapper.insert(entity);
            } catch (Exception e) {
                // 并发下可能已被插入，改为更新时间
                log.debug("chat_session 插入冲突，改为 touch: {}", cid);
                touchExisting(cid, agentId, now);
            }
            return;
        }
        if (!userId.trim().equals(existing.getUserId())) {
            log.warn("忽略会话 touch：conversationId={} 不属于当前用户", cid);
            return;
        }
        boolean titleIsDefault = !StringUtils.hasText(existing.getTitle())
                || "新会话".equals(existing.getTitle().trim());
        if (titleIsDefault && StringUtils.hasText(question)) {
            existing.setTitle(normalizeTitle(question, existing.getTitle()));
        }
        if (agentId != null) {
            existing.setAgentId(agentId);
        }
        existing.setUpdateTime(now);
        chatSessionMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCurrentUser(String scene) {
        String userId = requireUserId();
        if (userId == null) {
            return;
        }
        chatSessionMapper.delete(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getScene, normalizeScene(scene)));
    }

    private void touchExisting(String conversationId, Long agentId, LocalDateTime now) {
        ChatSession existing = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getConversationId, conversationId)
                        .last("LIMIT 1"));
        if (existing == null) {
            return;
        }
        if (agentId != null) {
            existing.setAgentId(agentId);
        }
        existing.setUpdateTime(now);
        chatSessionMapper.updateById(existing);
    }

    private ChatSession requireOwnedSession(String conversationId) {
        String userId = requireUserId();
        if (userId == null) {
            throw new IllegalStateException("缺少用户标识(X-User-Id)");
        }
        if (!StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        ChatSession entity = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getConversationId, conversationId.trim())
                        .eq(ChatSession::getUserId, userId)
                        .last("LIMIT 1"));
        if (entity == null) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }
        return entity;
    }

    private String requireUserId() {
        String userId = UserContext.getUserId();
        return StringUtils.hasText(userId) ? userId.trim() : null;
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

    private String normalizeTitle(String title, String fallback) {
        String value = StringUtils.hasText(title) ? title.trim() : null;
        if (!StringUtils.hasText(value)) {
            value = StringUtils.hasText(fallback) ? fallback.trim() : "新会话";
        }
        value = value.replaceAll("\\s+", " ");
        if (value.length() > TITLE_MAX) {
            return value.substring(0, TITLE_MAX);
        }
        return value;
    }

    private ChatSessionVO toVo(ChatSession entity) {
        return new ChatSessionVO(
                entity.getConversationId(),
                entity.getScene(),
                entity.getTitle(),
                entity.getAgentId(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    private QaHistoryVO toHistoryVo(QaHistory entity) {
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

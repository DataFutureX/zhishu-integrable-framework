package com.datafuturex.assistant.shared.port;

/**
 * 问答历史写入端口（实现位于 chat 模块）。
 */
public interface QaHistoryPort {

    void save(String scene, String question, String answer, String model, Long documentId);

    void save(String userId, String scene, String question, String answer, String model, Long documentId);

    void save(String scene, String question, String answer, String model, Long documentId, String conversationId);

    void save(String userId, String scene, String question, String answer, String model, Long documentId,
              String conversationId);

    void save(String userId, String scene, String question, String answer, String model, Long documentId,
              String conversationId, Long agentId);
}

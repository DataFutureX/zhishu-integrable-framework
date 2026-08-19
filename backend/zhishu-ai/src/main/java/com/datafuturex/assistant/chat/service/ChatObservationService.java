package com.datafuturex.assistant.chat.service;

import java.util.Map;

/**
 * 对话可观测性与轻量评测
 */
public interface ChatObservationService {

    void recordChat(String mode, boolean success, long durationMs);

    Map<String, Object> metricsSnapshot();

    Map<String, Object> runSmokeEval();
}

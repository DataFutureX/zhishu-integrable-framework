package com.datafuturex.assistant.briefing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 简报站内铃 SSE（按 userId 推送）。
 */
@Service
@Slf4j
public class BriefingSseService {

    private static final long SSE_TIMEOUT_MS = 0L;

    private final List<EmitterEntry> emitters = new CopyOnWriteArrayList<>();

    private record EmitterEntry(String userId, SseEmitter emitter) {
    }

    public SseEmitter subscribe(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        EmitterEntry entry = new EmitterEntry(userId, emitter);
        emitters.add(entry);

        emitter.onCompletion(() -> removeEmitter(entry));
        emitter.onTimeout(() -> removeEmitter(entry));
        emitter.onError(ex -> removeEmitter(entry));

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            removeEmitter(entry);
            log.warn("简报 SSE 连接初始化失败 userId={}", userId, e);
        }

        log.debug("简报 SSE 订阅建立 userId={}, 当前连接数={}", userId, emitters.size());
        return emitter;
    }

    public void pushToUser(String userId, Map<String, Object> payload) {
        if (!StringUtils.hasText(userId) || payload == null || emitters.isEmpty()) {
            return;
        }
        for (EmitterEntry entry : emitters) {
            if (!userId.equals(entry.userId())) {
                continue;
            }
            try {
                entry.emitter().send(SseEmitter.event().name("briefing").data(payload));
            } catch (Exception e) {
                removeEmitter(entry);
                log.debug("简报 SSE 推送失败，移除失效连接 userId={}", userId, e);
            }
        }
        log.info("简报 SSE 已推送 userId={}, id={}", userId, payload.get("id"));
    }

    private void removeEmitter(EmitterEntry entry) {
        emitters.remove(entry);
        log.debug("简报 SSE 连接已移除，当前连接数={}", emitters.size());
    }
}

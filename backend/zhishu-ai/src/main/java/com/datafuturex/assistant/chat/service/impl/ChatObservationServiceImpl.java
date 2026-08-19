package com.datafuturex.assistant.chat.service.impl;

import com.datafuturex.assistant.chat.service.ChatObservationService;
import com.datafuturex.assistant.knowledge.api.HybridRetrievalPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer 计数 + Hybrid/Memory/Vector 冒烟评测
 */
@Service
@Slf4j
public class ChatObservationServiceImpl implements ChatObservationService {

    private final MeterRegistry meterRegistry;
    private final HybridRetrievalPort hybridRetrievalService;
    private final VectorStore vectorStore;
    private final ChatMemory chatMemory;

    private final AtomicLong totalCalls = new AtomicLong();
    private final AtomicLong successCalls = new AtomicLong();
    private final AtomicLong failCalls = new AtomicLong();

    public ChatObservationServiceImpl(
            MeterRegistry meterRegistry,
            HybridRetrievalPort hybridRetrievalService,
            VectorStore vectorStore,
            ChatMemory chatMemory) {
        this.meterRegistry = meterRegistry;
        this.hybridRetrievalService = hybridRetrievalService;
        this.vectorStore = vectorStore;
        this.chatMemory = chatMemory;
        Counter.builder("wanxiang.ai.chat.calls").description("AI chat call count").register(meterRegistry);
        Timer.builder("wanxiang.ai.chat.duration").description("AI chat duration").register(meterRegistry);
    }

    @Override
    public void recordChat(String mode, boolean success, long durationMs) {
        totalCalls.incrementAndGet();
        if (success) {
            successCalls.incrementAndGet();
        } else {
            failCalls.incrementAndGet();
        }
        String safeMode = StringUtils.hasText(mode) ? mode : "unknown";
        meterRegistry.counter("wanxiang.ai.chat.calls", "mode", safeMode, "success", String.valueOf(success))
                .increment();
        meterRegistry.timer("wanxiang.ai.chat.duration", "mode", safeMode)
                .record(Math.max(durationMs, 0), TimeUnit.MILLISECONDS);
    }

    @Override
    public Map<String, Object> metricsSnapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalCalls", totalCalls.get());
        map.put("successCalls", successCalls.get());
        map.put("failCalls", failCalls.get());
        return map;
    }

    @Override
    public Map<String, Object> runSmokeEval() {
        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(check("chatMemoryBean", chatMemory != null, "Jdbc MessageWindowChatMemory 可用"));
        checks.add(check("vectorStoreBean", vectorStore != null, "PgVectorStore 可用"));
        boolean hybridOk = false;
        String hybridDetail = "hybrid 检索调用异常";
        try {
            String ctx = hybridRetrievalService.buildHybridContext("水位 监测", 3);
            hybridOk = true;
            hybridDetail = StringUtils.hasText(ctx) ? "返回片段长度=" + ctx.length() : "无命中（库空亦可）";
        } catch (Exception e) {
            hybridDetail = e.getMessage();
            log.warn("Hybrid 评测失败: {}", e.getMessage());
        }
        checks.add(check("hybridRetrieval", hybridOk, hybridDetail));

        long passed = checks.stream().filter(c -> Boolean.TRUE.equals(c.get("passed"))).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", passed == checks.size());
        result.put("passedCount", passed);
        result.put("totalCount", checks.size());
        result.put("checks", checks);
        result.put("runtimeMetrics", metricsSnapshot());
        return result;
    }

    private static Map<String, Object> check(String name, boolean passed, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("passed", passed);
        m.put("detail", detail);
        return m;
    }
}

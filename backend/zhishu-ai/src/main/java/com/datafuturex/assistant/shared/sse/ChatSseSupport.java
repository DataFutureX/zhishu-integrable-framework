package com.datafuturex.assistant.shared.sse;

import com.datafuturex.assistant.shared.trace.AgentTraceEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天 / Agent 试运行共用的 SSE 分片工具（伪流式）。
 */
public final class ChatSseSupport {

    private static final int SSE_CHUNK_SIZE = 48;
    private static final ObjectMapper TRACE_MAPPER = new ObjectMapper();

    private ChatSseSupport() {
    }

    public static Flux<ServerSentEvent<String>> toSseFlux(String content, String conversationId) {
        return toSseFluxWithTraces(content, conversationId, null);
    }

    public static Flux<ServerSentEvent<String>> toSseFluxWithTraces(
            String content,
            String conversationId,
            List<AgentTraceEvent> traces) {
        Flux<ServerSentEvent<String>> head = Flux.empty();
        if (traces != null && !traces.isEmpty()) {
            try {
                String json = TRACE_MAPPER.writeValueAsString(traces);
                head = Flux.just(ServerSentEvent.<String>builder()
                        .event("trace")
                        .data(json)
                        .build());
            } catch (Exception ignored) {
                // ignore serialize failure
            }
        }
        List<String> parts = splitForSse(content);
        Flux<ServerSentEvent<String>> messages = Flux.fromIterable(parts)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(chunk)
                        .build());
        return head.concatWith(messages).concatWith(Flux.just(ServerSentEvent.<String>builder()
                .event("done")
                .data(conversationId)
                .build()));
    }

    public static List<String> splitForSse(String content) {
        List<String> parts = new ArrayList<>();
        if (!StringUtils.hasText(content)) {
            return parts;
        }
        String text = content;
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(i + SSE_CHUNK_SIZE, text.length());
            if (end < text.length()) {
                int soft = Math.max(
                        text.lastIndexOf('。', end),
                        Math.max(text.lastIndexOf('\n', end), text.lastIndexOf('，', end)));
                if (soft > i + SSE_CHUNK_SIZE / 3) {
                    end = soft + 1;
                }
            }
            parts.add(text.substring(i, end));
            i = end;
        }
        return parts;
    }
}

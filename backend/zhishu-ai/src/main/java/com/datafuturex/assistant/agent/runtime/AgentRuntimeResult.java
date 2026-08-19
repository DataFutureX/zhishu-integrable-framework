package com.datafuturex.assistant.agent.runtime;

import com.datafuturex.assistant.shared.trace.AgentTraceEvent;

import java.util.List;

public record AgentRuntimeResult(
        String content,
        String model,
        String conversationId,
        List<AgentTraceEvent> traces,
        String engine,
        Long runId
) {
}

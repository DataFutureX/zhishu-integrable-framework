package cn.datafuturex.zhishu.ai.agent.runtime;

import cn.datafuturex.zhishu.ai.shared.trace.AgentTraceEvent;

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

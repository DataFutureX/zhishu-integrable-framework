package com.datafuturex.assistant.agent.runtime;

import com.datafuturex.assistant.shared.UserContext;
import com.datafuturex.assistant.shared.trace.AgentTraceEvent;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.function.Consumer;

/**
 * 包装 ToolCallback，记录 TOOL_CALL / TOOL_RESULT 轨迹，并可实时推送 progress。
 * <p>
 * 构造时快照 {@link UserContext}，调用时恢复，避免异步线程丢失用户身份。
 */
public class TracingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final List<AgentTraceEvent> traces;
    private final Consumer<AgentTraceEvent> onProgress;
    private final UserContext.Snapshot userSnapshot;

    public TracingToolCallback(ToolCallback delegate, List<AgentTraceEvent> traces) {
        this(delegate, traces, null);
    }

    public TracingToolCallback(
            ToolCallback delegate,
            List<AgentTraceEvent> traces,
            Consumer<AgentTraceEvent> onProgress) {
        this.delegate = delegate;
        this.traces = traces;
        this.onProgress = onProgress;
        this.userSnapshot = UserContext.snapshot();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        UserContext.Snapshot previous = UserContext.snapshot();
        UserContext.restore(userSnapshot);
        String name = delegate.getToolDefinition().name();
        long start = System.currentTimeMillis();
        emit(AgentTraceEvent.of("TOOL_CALL", name, truncate(toolInput, 400), null));
        try {
            String result = toolContext == null
                    ? delegate.call(toolInput)
                    : delegate.call(toolInput, toolContext);
            emit(AgentTraceEvent.of("TOOL_RESULT", name, truncate(result, 400),
                    System.currentTimeMillis() - start));
            return result;
        } catch (RuntimeException e) {
            emit(AgentTraceEvent.of("TOOL_RESULT", name, "ERROR: " + e.getMessage(),
                    System.currentTimeMillis() - start));
            throw e;
        } finally {
            UserContext.restore(previous);
        }
    }

    private void emit(AgentTraceEvent event) {
        traces.add(event);
        if (onProgress != null) {
            onProgress.accept(event);
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}

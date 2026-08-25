package cn.datafuturex.zhishu.ai.mcp.support;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 上游 MCP 简易熔断：连续失败后短时摘除，不影响本地监测 Tool。
 */
@Component
public class McpUpstreamCircuit {

    private static final int THRESHOLD = 3;
    private static final long OPEN_MS = 60_000L;

    private static final class State {
        int fails;
        long openUntil;
    }

    private final Map<Long, State> states = new ConcurrentHashMap<>();

    public boolean allow(Long id) {
        if (id == null) {
            return false;
        }
        State state = states.get(id);
        return state == null || state.openUntil <= System.currentTimeMillis();
    }

    public void success(Long id) {
        if (id != null) {
            states.remove(id);
        }
    }

    public void failure(Long id) {
        if (id == null) {
            return;
        }
        states.compute(id, (key, current) -> {
            State next = current == null ? new State() : current;
            next.fails++;
            if (next.fails >= THRESHOLD) {
                next.openUntil = System.currentTimeMillis() + OPEN_MS;
            }
            return next;
        });
    }
}

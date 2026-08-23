package cn.datafuturex.zhishu.ai.agent.runtime;

import cn.datafuturex.zhishu.ai.agent.config.AgentEngineProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AgentEngineSelector {

    private final AgentEngineProperties properties;
    private final Map<String, AgentEngine> engines;
    private final ChatClientAgentEngine chatClientAgentEngine;

    public AgentEngineSelector(
            AgentEngineProperties properties,
            List<AgentEngine> engineList,
            ChatClientAgentEngine chatClientAgentEngine) {
        this.properties = properties;
        this.engines = engineList.stream()
                .collect(Collectors.toMap(e -> e.name().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        this.chatClientAgentEngine = chatClientAgentEngine;
    }

    public AgentEngine select() {
        String preferred = properties.getEngine() == null
                ? ChatClientAgentEngine.NAME
                : properties.getEngine().trim().toLowerCase(Locale.ROOT);
        AgentEngine engine = engines.get(preferred);
        if (engine != null && engine.available()) {
            return engine;
        }
        if (engine != null && !engine.available()) {
            log.warn("引擎 {} 不可用，回退 {}", preferred, ChatClientAgentEngine.NAME);
        }
        return chatClientAgentEngine;
    }

    public Map<String, Object> health() {
        AgentEngine selected = select();
        return Map.of(
                "configured", properties.getEngine() == null ? ChatClientAgentEngine.NAME : properties.getEngine(),
                "active", selected.name(),
                "engines", engines.values().stream()
                        .map(e -> Map.of(
                                "name", e.name(),
                                "available", e.available()))
                        .toList());
    }
}

package com.datafuturex.assistant.agent.runtime;

/**
 * 智能体执行引擎 SPI。
 */
public interface AgentEngine {

    String name();

    boolean available();

    AgentRuntimeResult execute(AgentRuntimeRequest request);
}

package cn.datafuturex.zhishu.ai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wanxiang.agent")
public class AgentEngineProperties {

    /** chatclient | alibaba */
    private String engine = "chatclient";

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }
}

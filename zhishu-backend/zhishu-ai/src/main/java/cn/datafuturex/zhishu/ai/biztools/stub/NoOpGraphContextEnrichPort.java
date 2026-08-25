package cn.datafuturex.zhishu.ai.biztools.stub;

import cn.datafuturex.zhishu.ai.biztools.api.GraphContextEnrichPort;
import org.springframework.stereotype.Component;

@Component
public class NoOpGraphContextEnrichPort implements GraphContextEnrichPort {

    @Override
    public String enrich(String message) {
        return message;
    }
}

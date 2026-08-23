package cn.datafuturex.zhishu.ai.biztools.stub;

import cn.datafuturex.zhishu.ai.biztools.api.BizToolProviderPort;
import org.springframework.stereotype.Component;

/**
 * 阶段 2/3 占位：监测 Tool 改由万象 wanxiang-mcp upstream 提供。
 */
@Component
public class EmptyBizToolProviderPort implements BizToolProviderPort {

    @Override
    public Object[] toolBeans() {
        return new Object[0];
    }
}

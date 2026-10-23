package cn.datafuturex.zhishu.biz.systemconfig.spi;

import cn.datafuturex.zhishu.api.spi.ChatSettingsApi;
import cn.datafuturex.zhishu.biz.systemconfig.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 对话行为设置查询适配（供 ai 模块调用，落库在 sys_config）。
 */
@Service
@RequiredArgsConstructor
public class ChatSettingsApiImpl implements ChatSettingsApi {

    private final SystemConfigService systemConfigService;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean defaultRagEnabled() {
        return Boolean.TRUE.equals(systemConfigService.getConfig().chatEnableRagDefault());
    }
}

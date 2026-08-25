package cn.datafuturex.zhishu.api.spi;

import cn.datafuturex.zhishu.api.dto.LoginSecuritySettingsDTO;

/**
 * 登录安全策略查询（由业务模块实现）
 */
public interface LoginSecuritySettingsApi {

    LoginSecuritySettingsDTO getSettings();
}

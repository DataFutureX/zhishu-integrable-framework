package cn.datafuturex.zhishu.biz.systemconfig.service;

import cn.datafuturex.zhishu.biz.systemconfig.dto.SystemConfigUpdateDTO;
import cn.datafuturex.zhishu.biz.systemconfig.vo.SystemConfigVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 系统配置服务
 */
public interface SystemConfigService {
    SystemConfigVO getConfig();

    SystemConfigVO update(SystemConfigUpdateDTO dto);

    String uploadIcon(MultipartFile file);
}

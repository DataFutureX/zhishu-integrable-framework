package cn.datafuturex.zhishu.gateway.admin.service;

import cn.datafuturex.zhishu.gateway.admin.dto.GatewayQuotaUpsertDTO;
import cn.datafuturex.zhishu.gateway.admin.vo.GatewayQuotaVO;

import java.util.List;

/**
 * 额度策略管理服务。
 */
public interface GatewayQuotaAdminService {

    /**
     * 列出额度策略。
     *
     * @param subjectType 主体类型过滤，空=全部
     * @return 额度列表
     */
    List<GatewayQuotaVO> list(String subjectType);

    /**
     * 新建额度策略。
     *
     * @param dto 参数
     * @return 新建后的策略
     */
    GatewayQuotaVO create(GatewayQuotaUpsertDTO dto);

    /**
     * 修改额度策略（主体不可变更）。
     *
     * @param id  主键
     * @param dto 参数
     * @return 修改后的策略
     */
    GatewayQuotaVO update(Long id, GatewayQuotaUpsertDTO dto);

    /**
     * 删除额度策略（DEFAULT 主体不可删）。
     *
     * @param id 主键
     */
    void delete(Long id);

    /**
     * 清零当前周期已用量（运维/测试用）。
     *
     * @param id 主键
     * @return 修改后的策略
     */
    GatewayQuotaVO reset(Long id);
}

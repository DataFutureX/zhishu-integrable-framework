package cn.datafuturex.zhishu.gateway.admin.service;

import cn.datafuturex.zhishu.gateway.provider.dto.ModelProviderCreateDTO;
import cn.datafuturex.zhishu.gateway.provider.dto.ModelProviderUpdateDTO;
import cn.datafuturex.zhishu.gateway.provider.vo.UpstreamModelInfo;
import cn.datafuturex.zhishu.gateway.admin.vo.GatewayProviderAdminVO;

import java.util.List;

/**
 * 供应商管理服务（独立模块）：只维护连接与凭据，模型由模型设置维护。
 */
public interface GatewayProviderAdminService {

    /**
     * 供应商列表（含模型数量）。
     *
     * @return 供应商列表
     */
    List<GatewayProviderAdminVO> list();

    /**
     * 新建供应商。
     *
     * @param dto 参数
     * @return 新建后的供应商
     */
    GatewayProviderAdminVO create(ModelProviderCreateDTO dto);

    /**
     * 修改供应商。
     *
     * @param id  主键
     * @param dto 参数
     * @return 修改后的供应商
     */
    GatewayProviderAdminVO update(Long id, ModelProviderUpdateDTO dto);

    /**
     * 删除供应商（被模型引用或为默认时拒绝）。
     *
     * @param id 主键
     */
    void delete(Long id);

    /**
     * 设为默认供应商。
     *
     * @param id 主键
     * @return 修改后的供应商
     */
    GatewayProviderAdminVO markDefault(Long id);

    /**
     * 连通性测试（GET 上游 /models）。
     *
     * @param id 主键
     * @return 测试结果说明
     */
    String test(Long id);

    /**
     * 拉取上游模型列表（含 {@code GET /models} 元数据）。
     *
     * @param id 主键
     * @return 上游模型元数据列表
     */
    List<UpstreamModelInfo> upstreamModels(Long id);
}
